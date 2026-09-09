package org.zifeng.skilltree.system;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Z-Link 模块注册表（2026-09-09 骨架 v1，zl 系统核心调度器）。
 * <p>职责：
 * <ul>
 *   <li><b>登记</b>：{@link #register} 把功能模块挂进系统（按 id 去重）</li>
 *   <li><b>冬眠调度</b>：{@link #onServerTick} 对每个模块先评估活跃条件——
 *       无需求（条件不满足）的模块直接跳过（零开销，不执行任何数据计算）；
 *       条件满足才执行该模块的 onTick —— 玩家没需求，相关数据根本不跑</li>
 *   <li><b>生命周期</b>：玩家登出/服务器停止 → 广播给所有模块清理临时状态</li>
 * </ul>
 * 事件挂载由各版本入口完成（本类不 import 平台事件，双版本文件一致）：
 * 服务器每 tick 末调 {@link #onServerTick(ServerLevel)}；玩家登出调 {@link #onPlayerLogout(ServerPlayer)}；
 * 服务器停止调 {@link #onServerStop()}。
 */
public final class ZModules {
    private ZModules() {
    }

    /** 已登记模块（id → 模块）。用并发 Map：模块登记通常发生在构造期，运行时只读。 */
    private static final Map<String, ZModule> REGISTRY = new ConcurrentHashMap<>();

    /** 模块是否已被要求"强制唤醒"（调试/特殊场景用；默认全部条件驱动） */
    private static final Map<String, Boolean> FORCED_AWAKE = new ConcurrentHashMap<>();

    /** 登记模块（重复 id 静默忽略，保持先登记优先） */
    public static void register(ZModule module) {
        if (module == null || module.id() == null) {
            return;
        }
        REGISTRY.putIfAbsent(module.id(), module);
    }

    /** 取模块（未登记返回 null） */
    public static ZModule get(String id) {
        return REGISTRY.get(id);
    }

    /** 是否已登记 */
    public static boolean isRegistered(String id) {
        return REGISTRY.containsKey(id);
    }

    /** 登记总数（调试/性能观察用） */
    public static int registeredCount() {
        return REGISTRY.size();
    }

    /** 强制唤醒/恢复条件驱动（true=无视条件每 tick 执行；默认 false=条件驱动） */
    public static void setForcedAwake(String id, boolean forced) {
        if (forced) {
            FORCED_AWAKE.put(id, Boolean.TRUE);
        } else {
            FORCED_AWAKE.remove(id);
        }
    }

    /**
     * 服务器 tick 末驱动（每 tick 一次，非 per-player）：
     * 对每个在线玩家 × 每个模块，轻量评估条件后决定是否执行。
     * ⚠️ 性能设计：先判条件再执行；条件多为 map 读/布尔，未满足即跳过 body ——
     * 玩家没学对应技能/没开对应功能时，相关模块完全冬眠。
     */
    public static void onServerTick(ServerLevel level) {
        if (REGISTRY.isEmpty()) {
            return; // 没有模块：整个调度零开销
        }
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) {
            return;
        }
        for (ZModule module : REGISTRY.values()) {
            boolean forced = FORCED_AWAKE.containsKey(module.id());
            for (ServerPlayer player : players) {
                try {
                    if (forced || module.activeCondition(player)) {
                        module.onTick(player);
                    }
                } catch (Exception e) {
                    // 单模块异常不拖垮调度（日志由外层统一捕获）
                    org.zifeng.skilltree.SkillTreeMod.LOGGER.warn("[Z-Link] module {} tick error: {}", module.id(), e.toString());
                }
            }
        }
    }

    /** 玩家登出/换服：广播给所有模块清理该玩家临时状态（防跨服残留） */
    public static void onPlayerLogout(ServerPlayer player) {
        if (player == null || REGISTRY.isEmpty()) {
            return;
        }
        for (ZModule module : REGISTRY.values()) {
            try {
                module.onPlayerLogout(player);
            } catch (Exception e) {
                org.zifeng.skilltree.SkillTreeMod.LOGGER.warn("[Z-Link] module {} logout error: {}", module.id(), e.toString());
            }
        }
    }

    /** 服务器停止：广播全部模块清理全局状态 + 清空注册表 */
    public static void onServerStop() {
        for (ZModule module : REGISTRY.values()) {
            try {
                module.onServerStop();
            } catch (Exception e) {
                org.zifeng.skilltree.SkillTreeMod.LOGGER.warn("[Z-Link] module {} stop error: {}", module.id(), e.toString());
            }
        }
        REGISTRY.clear();
        FORCED_AWAKE.clear();
    }

    /** 便捷：登记若干模块（批量） */
    public static void registerAll(ZModule... modules) {
        for (ZModule m : modules) {
            register(m);
        }
    }
}
