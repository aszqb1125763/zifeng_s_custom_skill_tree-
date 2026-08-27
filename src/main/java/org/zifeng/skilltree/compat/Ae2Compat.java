package org.zifeng.skilltree.compat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Applied Energistics 2 兼容（2026-08-27）：
 * <p>「无限回路」终极节点：把 AE2 频道模式设为对应等级（1=X2 2=X3 3=X4 4=INFINITE）。
 * 反射调用 {@code AEConfig.instance().setChannelModel(ChannelMode.X2/X3/X4/INFINITE)} 并遍历所有 Grid 强制
 * repath（与 AE2 官方指令 ChannelModeCommand 相同的逻辑）。
 * <p>⚠️ 软引用：未装 AE2 时 isAe2Loaded() 返回 false，类加载安全降级，不影响模组其他功能。
 * <p>⚠️ 全局生效：AE2 频道模式是服务器全局配置。用玩家集合管理：还有玩家开启该技能 → 应用
 * 所有开启玩家中的最高等级；最后一个开启者关闭/登出 → 恢复应用前的原模式。
 */
public final class Ae2Compat {
    private Ae2Compat() {
    }

    /** AE2 是否已安装（懒检查） */
    private static boolean ae2Loaded = false;
    private static boolean ae2Checked = false;

    /** 当前开启「无限回路」技能的玩家（服务端）：玩家 UUID → 等级（1=X2 2=X3 3=X4 4=INFINITE） */
    private static final java.util.Map<UUID, Integer> ACTIVE_PLAYERS = new java.util.HashMap<>();

    /** 应用频道模式前的原模式（首次应用时记录；全部关闭时恢复） */
    private static volatile Object previousMode = null;

    /** 当前生效频道模式码（0=默认 1=X2 2=X3 3=X4 4=无限；-1=未应用）——供服务器全局状态提示同步 */
    private static volatile int currentModeCode = -1;
    /** 已恢复默认标记（2026-08-27 性能：集合空且已恢复后，disable 每 tick 调用直接幂等返回，不重复 repath/推送） */
    private static boolean restoredDefault = false;

    /** 当前生效频道模式码（供 GlobalStateS2CPacket 同步给客户端显示） */
    public static int getCurrentModeCode() {
        return currentModeCode;
    }

    /** 模式对象 → 模式码 */
    private static int codeOf(Object mode) {
        if (mode == null) return -1;
        if (mode == X2_MODE) return 1;
        if (mode == X3_MODE) return 2;
        if (mode == X4_MODE) return 3;
        if (mode == INFINITE_MODE) return 4;
        if (mode == DEFAULT_MODE) return 0;
        return -1;
    }

    // ===== 反射成员缓存（2026-08-27 v3 性能优化：首次解析后复用，避免每 tick Class.forName/getMethod/invoke 开销） =====
    private static java.lang.reflect.Method INSTANCE_METHOD;
    private static java.lang.reflect.Method GET_CHANNEL_MODE;
    private static java.lang.reflect.Method SET_CHANNEL_MODEL;
    private static java.lang.reflect.Method SAVE;
    private static Object X2_MODE;
    private static Object X3_MODE;
    private static Object X4_MODE;
    private static Object INFINITE_MODE;
    private static Object DEFAULT_MODE;
    private static java.lang.reflect.Method TICK_HANDLER_INSTANCE;
    private static java.lang.reflect.Method GET_GRID_LIST;
    private static java.lang.reflect.Method GET_PATHING_SERVICE;
    private static java.lang.reflect.Method REPATH;

    private static Object getAeConfigInstance() throws Exception {
        if (INSTANCE_METHOD == null) {
            Class<?> aeConfigCls = Class.forName("appeng.core.AEConfig");
            INSTANCE_METHOD = aeConfigCls.getMethod("instance");
            GET_CHANNEL_MODE = aeConfigCls.getMethod("getChannelMode");
            Class<?> channelModeCls = Class.forName("appeng.api.networking.pathing.ChannelMode");
            SET_CHANNEL_MODEL = aeConfigCls.getMethod("setChannelModel", channelModeCls);
            SAVE = aeConfigCls.getMethod("save");
            Class<? extends Enum> enumCls = (Class<? extends Enum>) channelModeCls;
            X2_MODE = Enum.valueOf(enumCls, "X2");
            X3_MODE = Enum.valueOf(enumCls, "X3");
            X4_MODE = Enum.valueOf(enumCls, "X4");
            INFINITE_MODE = Enum.valueOf(enumCls, "INFINITE");
            DEFAULT_MODE = Enum.valueOf(enumCls, "DEFAULT");
            Class<?> tickHandlerCls = Class.forName("appeng.hooks.ticking.TickHandler");
            TICK_HANDLER_INSTANCE = tickHandlerCls.getMethod("instance");
            GET_GRID_LIST = tickHandlerCls.getMethod("getGridList");
        }
        return INSTANCE_METHOD.invoke(null);
    }

    /** 等级 → 频道模式（1=X2 2=X3 3=X4 4+=INFINITE） */
    private static Object modeForLevel(int level) {
        if (X2_MODE == null) {
            try { getAeConfigInstance(); } catch (Throwable ignored) { }
        }
        return switch (level) {
            case 1 -> X2_MODE;
            case 2 -> X3_MODE;
            case 3 -> X4_MODE;
            default -> INFINITE_MODE; // 4 级及以上：无限
        };
    }

    private static Object getDefaultMode() {
        if (DEFAULT_MODE == null) {
            try { getAeConfigInstance(); } catch (Throwable ignored) { }
        }
        return DEFAULT_MODE;
    }

    /** 遍历所有 Grid 强制 repath（参考 AE2 ChannelModeCommand.setChannelMode；复用缓存反射成员） */
    private static void repathAllGrids() throws Exception {
        if (TICK_HANDLER_INSTANCE == null) {
            getAeConfigInstance(); // 初始化缓存
        }
        Object tickHandler = TICK_HANDLER_INSTANCE.invoke(null);
        Iterable<?> grids = (Iterable<?>) GET_GRID_LIST.invoke(tickHandler);
        if (grids != null) {
            for (Object grid : grids) {
                if (GET_PATHING_SERVICE == null) {
                    GET_PATHING_SERVICE = grid.getClass().getMethod("getPathingService");
                    REPATH = GET_PATHING_SERVICE.getReturnType().getMethod("repath");
                }
                Object pathing = GET_PATHING_SERVICE.invoke(grid);
                REPATH.invoke(pathing);
            }
        }
    }

    public static boolean isAe2Loaded() {
        if (!ae2Checked) {
            ae2Checked = true;
            try {
                Class.forName("appeng.core.AEConfig", false, Ae2Compat.class.getClassLoader());
                ae2Loaded = true;
            } catch (Throwable ignored) {
                ae2Loaded = false;
            }
        }
        return ae2Loaded;
    }

    /**
     * 玩家开启技能时调用：注册玩家（记录等级）并应用所有开启玩家中的最高频道等级。
     * <p>性能（2026-08-27 v3）：①已注册玩家且等级未变直接返回（避免每 tick 反射）②反射成员首次解析后静态缓存。
     *
     * @param level 频道等级（1=X2 2=X3 3=X4 4=INFINITE）
     * @return true 表示频道模式已生效；false 表示未装 AE2 或反射失败
     */
    public static synchronized boolean enable(UUID playerId, int level) {
        if (playerId != null) {
            Integer prev = ACTIVE_PLAYERS.get(playerId);
            if (prev != null && prev == level) {
                return true; // 已注册且等级未变（每 tick 调用，幂等快速返回，零反射）
            }
            ACTIVE_PLAYERS.put(playerId, Math.max(1, Math.min(4, level)));
            restoredDefault = false; // 有新开启者 → 取消已恢复标记
        }
        if (!isAe2Loaded()) {
            return false;
        }
        try {
            // 取所有开启玩家中的最高等级
            int maxLevel = ACTIVE_PLAYERS.values().stream().mapToInt(Integer::intValue).max().orElse(4);
            Object target = modeForLevel(maxLevel);
            Object instance = getAeConfigInstance();
            Object current = GET_CHANNEL_MODE.invoke(instance);
            if (current == target) {
                currentModeCode = codeOf(target); // 已是目标模式：确保模式码已同步
                return true; // 已是目标模式：无需重复设置
            }
            // 首次应用：记录原模式（仅记录一次，避免循环覆盖）
            if (previousMode == null) {
                previousMode = current;
            }
            // 设置模式 + 保存配置
            SET_CHANNEL_MODEL.invoke(instance, target);
            SAVE.invoke(instance);
            repathAllGrids();
            currentModeCode = codeOf(target);
            // 事件驱动：状态实际变化 → 推送全局状态给关注玩家（性能最优，非轮询）
            org.zifeng.skilltree.GlobalStateSync.pushToWatchers();
            return true;
        } catch (Throwable ignored) {
            // AE2 API 变动等 → 静默跳过（技能仍可学习，只是不生效）
            return false;
        }
    }

    /**
     * 玩家关闭技能/登出时调用：移除玩家；重新应用剩余玩家的最高等级；
     * 全部关闭后恢复原频道模式。
     */
    public static synchronized void disable(UUID playerId) {
        if (playerId != null) {
            ACTIVE_PLAYERS.remove(playerId);
        }
        if (!isAe2Loaded()) {
            return;
        }
        try {
            Object instance = getAeConfigInstance();
            if (!ACTIVE_PLAYERS.isEmpty()) {
                // 还有玩家开着 → 应用剩余玩家的最高等级
                restoredDefault = false;
                int maxLevel = ACTIVE_PLAYERS.values().stream().mapToInt(Integer::intValue).max().orElse(4);
                Object target = modeForLevel(maxLevel);
                Object current = GET_CHANNEL_MODE.invoke(instance);
                if (current != target) {
                    SET_CHANNEL_MODEL.invoke(instance, target);
                    SAVE.invoke(instance);
                    repathAllGrids();
                }
                currentModeCode = codeOf(target);
                org.zifeng.skilltree.GlobalStateSync.pushToWatchers();
                return;
            }
            // ⚠️ 幂等（2026-08-27 性能）：集合空且已恢复过 → 每 tick 调用直接返回，不重复恢复/repath/推送
            if (restoredDefault) {
                return;
            }
            // 全部关闭：恢复原模式（无记录 → DEFAULT）
            Object target = previousMode != null ? previousMode : getDefaultMode();
            Object current = GET_CHANNEL_MODE.invoke(instance);
            if (current != target) {
                SET_CHANNEL_MODEL.invoke(instance, target);
                SAVE.invoke(instance);
            }
            // 无条件 repath：即使模式未变也强制网络重算（确保频道数生效）
            repathAllGrids();
            currentModeCode = codeOf(target);
            restoredDefault = true; // 标记已恢复默认
            // 事件驱动：状态实际变化 → 推送全局状态给关注玩家
            org.zifeng.skilltree.GlobalStateSync.pushToWatchers();
        } catch (Throwable ignored) {
            // 恢复失败静默跳过（AE 保持当前模式）
        } finally {
            previousMode = null; // 已处理，重置记录
        }
    }

    /**
     * 服务器停止/重启时清理（2026-08-27 v3）：清空玩家集合 + 重置 previousMode，
     * 防止异常退出/崩溃后跨世界残留导致 AE 频道模式永久锁定 INFINITE。
     * 正常停止时玩家已逐个登出（集合已空），此方法只处理异常残留。
     */
    public static synchronized void onServerStopped() {
        ACTIVE_PLAYERS.clear();
        previousMode = null;
        currentModeCode = -1;
        restoredDefault = false;
    }
}
