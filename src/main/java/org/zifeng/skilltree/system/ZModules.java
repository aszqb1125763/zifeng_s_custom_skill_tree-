package org.zifeng.skilltree.system;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
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
 * 服务器每 tick 末调 {@link #onServerTick(MinecraftServer)}；玩家登出调 {@link #onPlayerLogout(ServerPlayer)}；
 * 服务器停止调 {@link #onServerStop()}。
 * <p><b>⚠️ 链接玩家（不分维度，2026-09-10 修复）</b>：驱动源 = 服务器全体在线玩家
 * （{@code server.getPlayerList().getPlayers()}，与原版 tickChildren 同款），<b>不经过任何维度</b>——
 * 玩家在下界/末地/模组维度都照常驱动。（曾是 1.3.9 的严重 bug：旧实现传 {@code server.overworld()}
 * 后取 {@code ServerLevel.players()}，只拿到主世界玩家，离开主世界后 8 个模块全部冬眠。）
 */
public final class ZModules {
    private ZModules() {
    }

    /** 已登记模块（id → 模块）。用并发 Map：模块登记通常发生在构造期，运行时只读。 */
    private static final Map<String, ZModule> REGISTRY = new ConcurrentHashMap<>();

    /** 模块是否已被要求"强制唤醒"（调试/特殊场景用；默认全部条件驱动） */
    private static final Map<String, Boolean> FORCED_AWAKE = new ConcurrentHashMap<>();

    /**
     * 自愈动作（2026-09-10）：由 SkillTreeMod 注入"重新登记全部模块"的可执行体。
     * 作用：若注册表因任何意外路径变空，调度器检测到后自动重注册（而非永久失效）。
     */
    private static volatile Runnable HEAL_ACTION;

    /** "注册表为空"警告是否已输出（防刷屏） */
    private static volatile boolean emptyRegistryWarned = false;

    /** 注入自愈动作（SkillTreeMod 构造时调用一次） */
    public static void setHealAction(Runnable action) {
        HEAL_ACTION = action;
    }

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
     * <p><b>⚠️ 链接玩家（2026-09-10 修复）</b>：驱动源 = 服务器全体在线玩家，<b>不经过任何维度</b>——
     * 玩家在下界/末地/模组维度都照常驱动。（旧实现传 overworld 后取 {@code ServerLevel.players()}
     * 只拿到主世界玩家 → 离开主世界后 8 个模块集体冬眠；飞行/馈赠/磁铁/光环/时晴空环全部失效。）
     * <p>性能设计：先判条件再执行；条件多为 map 读/布尔，未满足即跳过 body ——
     * 玩家没学对应技能/没开对应功能时，相关模块完全冬眠。
     */
    public static void onServerTick(MinecraftServer server) {
        if (server == null) {
            return; // 无服务器：整个调度零开销
        }
        // ⚠️ 健康自检（2026-09-10）：注册表为空 = 异常状态（曾因 onServerStop 误清空导致
        //    模块永久不跑）。只警告一次（防刷屏）+ 触发自愈重注册。
        if (REGISTRY.isEmpty()) {
            if (!emptyRegistryWarned) {
                emptyRegistryWarned = true;
                org.zifeng.skilltree.SkillTreeMod.LOGGER.error(
                        "[Z-Link] ⚠️ 模块注册表为空（正常不应发生）！已尝试自愈重注册。"
                                + "若此消息反复出现请反馈。");
            }
            Runnable heal = HEAL_ACTION;
            if (heal != null) {
                heal.run(); // 自愈：重新登记全部模块（registerAll 为 putIfAbsent，重复安全）
            }
            if (REGISTRY.isEmpty()) {
                return; // 仍为空（未注入自愈动作）→ 本次不调度
            }
        }
        // ⚠️ 链接玩家：取全服在线玩家（与原版 MinecraftServer.tickChildren 同款写法），
        //    绝不使用 ServerLevel.players()（那只返回该维度的玩家）。
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
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
        UUID pid = player.getUUID();
        for (ZModule module : REGISTRY.values()) {
            try {
                module.onPlayerLogout(player);
            } catch (Exception e) {
                org.zifeng.skilltree.SkillTreeMod.LOGGER.warn("[Z-Link] module {} logout error: {}", module.id(), e.toString());
            }
        }
    }

    /**
     * 服务器停止（单人游戏"退出世界"也会触发）：广播全部模块清理<b>临时状态</b>。
     * <p><b>⚠️ 2026-09-10 严重 bug 修复：绝不在此清空 REGISTRY！</b>
     * 模块注册是 <b>mod 生命周期级</b>的（只在 {@code SkillTreeMod} 构造器执行一次）；
     * 旧实现此方法结尾调了 {@code REGISTRY.clear()}——单人游戏退出世界后 REGISTRY 变空，
     * 而 mod 构造器不会再执行 → <b>注册表永远为空 → 8 个模块永久全不跑</b>
     * （表现为：重进世界后飞行/馈赠/磁铁/光环/AE 无限回路全部失效）。
     * 现在只清临时状态（FORCED_AWAKE），注册表保留。
     */
    public static void onServerStop() {
        for (ZModule module : REGISTRY.values()) {
            try {
                module.onServerStop();
            } catch (Exception e) {
                org.zifeng.skilltree.SkillTreeMod.LOGGER.warn("[Z-Link] module {} stop error: {}", module.id(), e.toString());
            }
        }
        // ⚠️ 不清空 REGISTRY（见上方注释），只清临时标记。
        FORCED_AWAKE.clear();
        emptyRegistryWarned = false; // 重置警告标记（下次会话可再警告）
    }

    /** 便捷：登记若干模块（批量） */
    public static void registerAll(ZModule... modules) {
        for (ZModule m : modules) {
            register(m);
        }
    }
}
