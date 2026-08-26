package org.zifeng.skilltree.compat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Applied Energistics 2 兼容（2026-08-27）：
 * <p>「无限回路」终极节点：把 AE2 频道模式设为 INFINITE（等效 /ae2 channelmode infinite）。
 * 反射调用 {@code AEConfig.instance().setChannelModel(ChannelMode.INFINITE)} 并遍历所有 Grid 强制
 * repath（与 AE2 官方指令 ChannelModeCommand 相同的逻辑）。
 * <p>⚠️ 软引用：未装 AE2 时 isAe2Loaded() 返回 false，类加载安全降级，不影响模组其他功能。
 * <p>⚠️ 全局生效：AE2 频道模式是服务器全局配置。用玩家集合管理：还有玩家开启该技能 → 保持无限；
 * 最后一个开启者关闭/登出 → 恢复应用前的原模式（避免"关不掉"的 bug，2026-08-27 测试反馈）。
 */
public final class Ae2Compat {
    private Ae2Compat() {
    }

    /** AE2 是否已安装（懒检查） */
    private static boolean ae2Loaded = false;
    private static boolean ae2Checked = false;

    /** 当前开启「无限回路」技能的玩家（服务端） */
    private static final Set<UUID> ACTIVE_PLAYERS = new HashSet<>();

    /** 应用无限前的原频道模式（首次应用时记录；恢复时用） */
    private static volatile Object previousMode = null;

    // ===== 反射成员缓存（2026-08-27 v3 性能优化：首次解析后复用，避免每 tick Class.forName/getMethod/invoke 开销） =====
    private static java.lang.reflect.Method INSTANCE_METHOD;
    private static java.lang.reflect.Method GET_CHANNEL_MODE;
    private static java.lang.reflect.Method SET_CHANNEL_MODEL;
    private static java.lang.reflect.Method SAVE;
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
            INFINITE_MODE = Enum.valueOf(enumCls, "INFINITE");
            DEFAULT_MODE = Enum.valueOf(enumCls, "DEFAULT");
            Class<?> tickHandlerCls = Class.forName("appeng.hooks.ticking.TickHandler");
            TICK_HANDLER_INSTANCE = tickHandlerCls.getMethod("instance");
            GET_GRID_LIST = tickHandlerCls.getMethod("getGridList");
        }
        return INSTANCE_METHOD.invoke(null);
    }

    private static Object getInfiniteMode() {
        if (INFINITE_MODE == null) {
            try { getAeConfigInstance(); } catch (Throwable ignored) { }
        }
        return INFINITE_MODE;
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
     * 玩家开启技能时调用：注册玩家并确保 AE 频道无限。
     * <p>性能（2026-08-27 v3）：①已注册玩家直接返回（避免每 tick 反射）②反射成员首次解析后静态缓存。
     *
     * @return true 表示无限已生效；false 表示未装 AE2 或反射失败
     */
    public static synchronized boolean enable(UUID playerId) {
        if (playerId != null && !ACTIVE_PLAYERS.add(playerId)) {
            return true; // 已注册过（每 tick 调用，幂等快速返回，零反射）
        }
        if (!isAe2Loaded()) {
            return false;
        }
        try {
            Object instance = getAeConfigInstance();
            Object infinite = getInfiniteMode();
            Object current = GET_CHANNEL_MODE.invoke(instance);
            if (current == infinite) {
                // 已是无限（管理员设过或之前应用过）：无需重复设置
                return true;
            }
            // 首次应用：记录原模式（仅记录一次，避免循环覆盖）
            if (previousMode == null) {
                previousMode = current;
            }
            // 设置模式 + 保存配置
            SET_CHANNEL_MODEL.invoke(instance, infinite);
            SAVE.invoke(instance);
            repathAllGrids();
            return true;
        } catch (Throwable ignored) {
            // AE2 API 变动等 → 静默跳过（技能仍可学习，只是不生效）
            return false;
        }
    }

    /**
     * 玩家关闭技能/登出时调用：移除玩家；最后一个开启者移除后恢复原频道模式。
     * <p>修复（2026-08-27 v2）：previousMode 为 null（首次开启时 AE 已是 INFINITE 的残留）
     * 也恢复 DEFAULT；且无条件 repath（即使模式未变也重算网络，确保恢复生效）。
     */
    public static synchronized void disable(UUID playerId) {
        if (playerId != null) {
            ACTIVE_PLAYERS.remove(playerId);
        }
        if (!ACTIVE_PLAYERS.isEmpty()) {
            return; // 还有玩家开着 → 保持无限
        }
        if (!isAe2Loaded()) {
            return;
        }
        try {
            Object instance = getAeConfigInstance();
            // 恢复目标：应用前的模式；没有记录（如开启时已无限）→ 恢复 DEFAULT（用户要求的默认频道数量）
            Object target = previousMode != null ? previousMode : getDefaultMode();
            Object current = GET_CHANNEL_MODE.invoke(instance);
            if (current != target) {
                SET_CHANNEL_MODEL.invoke(instance, target);
                SAVE.invoke(instance);
            }
            // 无条件 repath：即使模式未变也强制网络重算（确保频道数生效）
            repathAllGrids();
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
    }
}
