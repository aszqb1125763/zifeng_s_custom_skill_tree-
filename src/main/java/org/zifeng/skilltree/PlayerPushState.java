package org.zifeng.skilltree;

import java.util.Map;
import java.util.UUID;

/**
 * 玩家推送子系统（2026-08-28 架构升级）：每玩家独立状态对象，负责【玩家级】状态推送。
 * <p>与主系统 {@link GlobalStateSync}（全局类）分工：子系统防数据串（UUID 隔离），
 * 主/子系统各自独立，互不干扰。
 * <p>职责：
 * <ul>
 *   <li><b>转换机阶梯进度缓存</b>：convertedPoints（前 2000 点打折进度，跨机器共享），
 *       机器 getThreshold() 直接读缓存（不再每 tick 查 SavedData），20 tick 刷新</li>
 *   <li><b>技能点增量推送</b>：skillPointDelta（tick 内合并），发 SkillPointDeltaS2CPacket
 *       （2 个 double = 16 字节），替代每 10 tick 全量 SkillTreeDataS2CPacket</li>
 *   <li><b>折扣跨档推送</b>：阶梯档位变化（每点消耗改变）时通知 GUI 刷新</li>
 * </ul>
 */
public final class PlayerPushState {
    /** 阶梯缓存刷新间隔（tick）：20 = 1 秒 */
    private static final long CACHE_REFRESH_TICKS = 20;
    /** 技能点增量合并间隔（tick）：10 = 0.5 秒 */
    private static final long SKILL_POINT_SYNC_TICKS = 10;

    private final UUID owner;

    // ===== 转换机阶梯进度缓存 =====
    private long convertedPointsCache = -1;
    private long convertedCacheTick = -1;
    /** 上次推送的阶梯档位（每点消耗；-1=未推送）——折扣跨档去重 */
    private long lastThreshold = -1;

    // ===== 技能点增量 =====
    private double pendingSkillDelta = 0;
    private long lastSkillSyncTick = -1;

    // ===== 子系统注册表（每玩家一个） =====
    private static final Map<UUID, PlayerPushState> STATES = new java.util.HashMap<>();

    private PlayerPushState(UUID owner) {
        this.owner = owner;
    }

    /** 获取玩家子系统（不存在则创建；懒加载） */
    public static PlayerPushState get(UUID playerId) {
        return STATES.computeIfAbsent(playerId, PlayerPushState::new);
    }

    /** 玩家登出：销毁子系统（防泄漏/防数据串） */
    public static void remove(UUID playerId) {
        if (playerId != null) {
            STATES.remove(playerId);
        }
    }

    /** 服务器停止：全部销毁 */
    public static void clearAll() {
        STATES.clear();
    }

    /** 当前在线子系统数量（调试/性能观察用） */
    public static int activeCount() {
        return STATES.size();
    }

    // ===== 转换机阶梯进度缓存（前 2000 点打折进度，玩家级共享，跨机器） =====

    /**
     * 读取玩家整体累计转换点（阶梯打折依据）。20 tick 缓存刷新，
     * 替代原来每 tick 每台机器查 SavedData（挂机 20 台机器 = 每 tick 1 次而非 20 次）。
     */
    public long getConvertedPoints(net.minecraft.server.level.ServerLevel level) {
        long gameTime = level.getGameTime();
        if (convertedPointsCache < 0 || gameTime - convertedCacheTick >= CACHE_REFRESH_TICKS) {
            convertedPointsCache = org.zifeng.skilltree.data.PlayerSkillSavedData.get(level)
                    .getOrCreatePlayer(owner).getTotalConvertedPoints();
            convertedCacheTick = gameTime;
        }
        return convertedPointsCache;
    }

    /** 手动失效阶梯缓存（技能点重洗等场景，下一 tick 自动重读） */
    public void invalidateConvertedCache() {
        convertedPointsCache = -1;
    }

    /**
     * ⭐ 玩家级共享折扣（2026-08-28 核心）：当前每点消耗（FE）。
     * <p>折扣逻辑收进玩家子系统——所有该玩家的转换机共用同一个值：
     * 前 {@link org.zifeng.skilltree.Config#ENERGY_STEP_POINTS}（2000）点，
     * 每点消耗从 ENERGY_START_COST（1万）线性递增到 ENERGY_PER_SKILL_POINT（1亿）；
     * 超过 2000 点后固定为最终消耗。
     * <p>⚠️ 防漏洞：2000 点是【玩家所有机器合计】的前 2000 点才有折扣——
     * 不可能出现"第 1 台机器用完 2000 点折扣，放第 2 台又有折扣"。
     * 机器只查询本方法，不各自计算。
     */
    public long getCurrentCostPerPoint(net.minecraft.server.level.ServerLevel level) {
        long finalCost = Math.max(1, org.zifeng.skilltree.Config.ENERGY_PER_SKILL_POINT.get());
        long startCost = Math.max(1, Math.min(finalCost, org.zifeng.skilltree.Config.ENERGY_START_COST.get()));
        int step = Math.max(1, org.zifeng.skilltree.Config.ENERGY_STEP_POINTS.get());
        long converted = Math.min(getConvertedPoints(level), step); // 玩家整体进度（跨机器共享）
        long increment = (finalCost - startCost) / step; // 每点增量（线性递增）
        return startCost + converted * increment;
    }

    /**
     * 折扣跨档检测：机器把当前每点消耗传入，档位变化 → 返回 true（调用方推送 GUI）。
     * 配合快照去重：档位没变返回 false，零开销。
     */
    public boolean thresholdChanged(long newThreshold) {
        if (newThreshold != lastThreshold) {
            lastThreshold = newThreshold;
            return true;
        }
        return false;
    }

    // ===== 技能点增量推送 =====

    /**
     * 记录技能点增量并合并发送（替代全量回发）。
     * 转换机 grantSkillPoints 调用：增量累计 + 每 10 tick 合并发一次 SkillPointDeltaS2CPacket。
     * ⚠️ 2026-08-28 内存修复：owner 离线时不累计 pending（直接清零）——
     * 挂机转换只写 SavedData，下次进服全量校准，避免离线 pending 无限涨大。
     *
     * @return 是否已发包（供调用方判断是否还需额外全量同步）
     */
    public boolean addSkillPointDelta(double delta, net.minecraft.server.level.ServerLevel level) {
        if (delta == 0) {
            return false;
        }
        // ⚠️ owner 离线：不累计 pending（挂机只写存档，进服全量校准）
        if (level.getServer().getPlayerList().getPlayer(owner) == null) {
            pendingSkillDelta = 0;
            return false;
        }
        pendingSkillDelta += delta;
        long gameTime = level.getGameTime();
        if (lastSkillSyncTick < 0 || gameTime - lastSkillSyncTick >= SKILL_POINT_SYNC_TICKS) {
            lastSkillSyncTick = gameTime;
            double total = org.zifeng.skilltree.data.PlayerSkillSavedData.get(level)
                    .getOrCreatePlayer(owner).getSkillPoints();
            flushSkillPoints(level, total);
            return true;
        }
        return false;
    }

    /** 立即发送累积的技能点增量（含最新总额；调用方在玩家在线时调用） */
    public void flushSkillPoints(net.minecraft.server.level.ServerLevel level, double total) {
        double delta = pendingSkillDelta;
        pendingSkillDelta = 0;
        net.minecraft.server.level.ServerPlayer ownerPlayer = level.getServer().getPlayerList().getPlayer(owner);
        if (ownerPlayer != null) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(ownerPlayer,
                    new org.zifeng.skilltree.network.SkillPointDeltaS2CPacket(delta, total));
        }
    }
}
