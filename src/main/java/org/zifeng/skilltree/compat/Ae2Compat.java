package org.zifeng.skilltree.compat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.skill.Skills;

/**
 * Applied Energistics 2 兼容（2026-08-27）：
 * <p>「无限回路」终极节点：把 AE2 频道模式设为对应等级。
 * <p><b>等级约定（2026-09-20 补全 0 级）</b>：
 * <pre>
 *   0 = DEFAULT（AE2 原版默认 8 频道）  ← ★ 新增：以前 0 级不生效，与界面/描述不符
 *   1 = X2   2 = X3   3 = X4   4 = INFINITE
 * </pre>
 * 反射调用 {@code AEConfig.instance().setChannelModel(...)} 并遍历所有 Grid 强制
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

    /**
     * 当前开启「无限回路」技能的玩家（服务端）：玩家 UUID → 等级（1=X2 2=X3 3=X4 4=INFINITE）。
     * <p>⚠️ <b>用 LinkedHashMap 保序（2026-09-14）</b>："谁最后激活谁生效"——末尾 entry = 最后激活者，
     * 取他的等级作为生效等级（不再是所有玩家中的最高等级）。
     */
    private static final java.util.Map<UUID, Integer> ACTIVE_PLAYERS = new java.util.LinkedHashMap<>();

    /** 当前生效频道模式码（0=默认 1=X2 2=X3 3=X4 4=无限；-1=未应用）——供服务器全局状态提示同步 */
    private static volatile int currentModeCode = -1;

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
    private static long lastVerifyMillis = 0L;

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

    /**
     * 等级 → 频道模式。
     *
     * <p>★ 2026-09-20：补上 {@code case 0 → DEFAULT_MODE}。
     * 界面进度条 / 聊天提示 / 技能描述都把 0 级叫「默认（原版 8 频道）」，
     * 但旧代码<b>没有任何路径调 {@code setChannelModel(DEFAULT)}</b>，
     * 导致拖到 0 级时文案说“已回默认”、实际 AE 频道模式纹丝不动（INFINITE 仍生效）。
     */
    private static Object modeForLevel(int level) {
        if (X2_MODE == null) {
            try { getAeConfigInstance(); } catch (Throwable ignored) { }
        }
        return switch (level) {
            case 0 -> DEFAULT_MODE;  // 0 级：AE2 默认模式（原版 8 频道）
            case 1 -> X2_MODE;
            case 2 -> X3_MODE;
            case 3 -> X4_MODE;
            default -> INFINITE_MODE; // 4 级及以上：无限
        };
    }

    /**
     * 当前生效等级 = <b>最后激活者</b>的等级（2026-09-14「谁最后激活谁生效」）。
     * <p>LinkedHashMap 保序，末尾 entry 即最后激活者；集合为空时返回 4 兜底（调用方均已判空）。
     */
    private static int lastActivatorLevel() {
        Integer last = null;
        for (Integer v : ACTIVE_PLAYERS.values()) {
            last = v;
        }
        return last != null ? last : 4;
    }

    /** 技能等级对应的 AE2 模式码（0=默认 1=X2 2=X3 3=X4 4=无限）；与 {@link #codeOf(Object)} 保持同一约定。 */
    private static int modeCodeForLevel(int level) {
        return Math.max(0, Math.min(4, level)); // ★ 2026-09-20：下限由 1 改 0（0 级 = 默认）
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
     * 玩家开启技能时调用：注册玩家（记录等级）并应用<b>最后激活者</b>的频道等级。
     * <p>性能（2026-08-27 v3）：①已注册玩家且等级未变直接返回（避免每 tick 反射）②反射成员首次解析后静态缓存。
     *
     * @param level 频道等级（<b>0=默认 1=X2 2=X3 3=X4 4=INFINITE</b>；2026-09-20 起支持 0）
     * @return true 表示频道模式已生效；false 表示未装 AE2 或反射失败
     */
    public static synchronized boolean enable(UUID playerId, int level) {
        int clampedLevel = Math.max(0, Math.min(4, level)); // ★ 下限 0：0 级是合法等级（默认模式）
        if (playerId != null) {
            Integer prev = ACTIVE_PLAYERS.get(playerId);
            long now = System.currentTimeMillis();
            int currentTargetLevel = lastActivatorLevel();
            if (prev != null && prev == clampedLevel
                    && currentModeCode == modeCodeForLevel(currentTargetLevel)
                    && now - lastVerifyMillis < 1000L) {
                return true; // 已注册且等级未变（每 tick 调用，幂等快速返回，零反射）
            }
            // 2026-09-14「谁最后激活谁生效」：重新激活要移到末尾（LinkedHashMap 对已存在 key 的 put 不改顺序）
            if (prev != null) {
                ACTIVE_PLAYERS.remove(playerId);
            }
            ACTIVE_PLAYERS.put(playerId, clampedLevel);
        }
        if (!isAe2Loaded()) {
            return false;
        }
        try {
            lastVerifyMillis = System.currentTimeMillis();
            // 取最后激活者的等级（不再取最高）
            Object target = modeForLevel(lastActivatorLevel());
            Object instance = getAeConfigInstance();
            Object current = GET_CHANNEL_MODE.invoke(instance);
            if (current == target) {
                currentModeCode = codeOf(target); // 已是目标模式：确保模式码已同步
                return true; // 已是目标模式：无需重复设置
            }
            // 设置模式 + 保存配置
            SET_CHANNEL_MODEL.invoke(instance, target);
            SAVE.invoke(instance);
            repathAllGrids();
            currentModeCode = codeOf(target);
            // 事件驱动：状态实际变化 → 标记全局状态变化（tick 末合并推送，2026-08-28）
            org.zifeng.skilltree.GlobalStateSync.markDirty();
            return true;
        } catch (Throwable ignored) {
            // AE2 API 变动等 → 静默跳过（技能仍可学习，只是不生效）
            return false;
        }
    }

    /**
     * 玩家关闭技能/登出时调用：仅解除登记，<b>不修改任何频道模式</b>。
     *
     * <p><b>2026-09-14 用户要求</b>：「关闭时什么状态就什么样，不用再去管玩家自己手动用指令调整的频道数量」——
     * 因此取消了原先的 {@code previousMode} 恢复机制：
     * <ul>
     *   <li>不再恢复"技能生效前的原模式"（那会覆盖玩家在技能生效期间手动用 /ae2 命令改的值）</li>
     *   <li>不再主动 repath / 写配置</li>
     *   <li>只做一件事：只读同步当前真实模式码（供属性面板显示，不修改）</li>
     * </ul>
     * <p>注意：只要还有玩家开启技能，{@link #enable} 仍会把模式设为其指定等级（技能需要生效）。
     */
    public static synchronized void disable(UUID playerId) {
        if (playerId != null) {
            ACTIVE_PLAYERS.remove(playerId);
        }
        if (!isAe2Loaded()) {
            return;
        }
        try {
            // 只读：把实际当前模式同步给 UI（不写入、不 repath）
            currentModeCode = codeOf(GET_CHANNEL_MODE.invoke(getAeConfigInstance()));
        } catch (Throwable ignored) {
            // 读取失败安全降级
        }
    }

    /**
     * 玩家进入存档后按持久化技能状态重新挂载 AE 频道效果。
     * <p>频道模式本身由 AE2 管理，是服务器运行时全局状态；不能只依赖玩家 tick 的首次调度，
     * 因为单人退出/重进时 AE2 与本模组的服务器启动顺序可能不同。
     * <p>★ 2026-09-20：生效等级 0 现在也是<b>合法等级</b>（= 默认模式），
     * 所以改调 {@code enable(uuid, 0)}（会把模式设回默认），不再走“只解登记不改模式”的 disable。
     */
    public static void restoreForPlayer(ServerPlayer player, PlayerSkillRecord record) {
        if (player == null || record == null) {
            return;
        }
        int learned = record.getLearnedPoints(Skills.AE_INFINITE_CHANNEL);
        if (learned > 0 && record.isEnabled(Skills.AE_INFINITE_CHANNEL)) {
            int level = Math.max(0, record.getActiveLevel(Skills.AE_INFINITE_CHANNEL));
            enable(player.getUUID(), level);
            return;
        }
        disable(player.getUUID());
    }

    /**
     * 服务器停止/重启时清理（2026-08-27 v3）：清空玩家集合 + 重置模式码，
     * 防止异常退出/崩溃后跨世界残留。
     * <p>2026-09-14：不再有 {@code previousMode}（已取消恢复机制）。
     */
    public static synchronized void onServerStopped() {
        ACTIVE_PLAYERS.clear();
        currentModeCode = -1;
        lastVerifyMillis = 0L;
    }
}
