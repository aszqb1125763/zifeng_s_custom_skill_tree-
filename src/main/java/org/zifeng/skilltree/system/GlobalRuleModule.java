package org.zifeng.skilltree.system;

import net.minecraft.server.level.ServerPlayer;
import org.zifeng.skilltree.event.AuraEvents;
import org.zifeng.skilltree.skill.Skills;

/**
 * 寰宇法则·全局锁定模块（时之环/晴空环，Z-Link 门面迁移 2026-09-09）。
 * <p>原 {@code AuraEvents.onPlayerTick} 每玩家必进；现迁入本模块：
 * 未学 时之环/晴空环 的玩家 → 冬眠（不进入 tickGlobalLocks，省 record 获取 + 锁定状态维护）。
 * 全局锁定逻辑本体在 AuraEvents.tickGlobalLocks（一字未改）。
 * <p>⚠️ 效果仍是全局 gamerule（doDaylightCycle/doWeatherCycle），只是驱动方式改为条件调度。
 */
public final class GlobalRuleModule implements ZModule {
    public static final GlobalRuleModule INSTANCE = new GlobalRuleModule();

    private GlobalRuleModule() {
    }

    @Override
    public String id() {
        return "global_rule";
    }

    /** 活跃条件：学了 时之环 或 晴空环（未学 → 冬眠零开销） */
    @Override
    public boolean activeCondition(ServerPlayer player) {
        // ⚠️ 2026-09-09 性能审计：只读 getPlayer——无记录（从没学任何技能）→ 冬眠，不建空记录
        if (player.serverLevel() == null) {
            return false;
        }
        var record = org.zifeng.skilltree.data.PlayerSkillSavedData
                .get(player.serverLevel()).getPlayer(player.getUUID());
        if (record == null) {
            return false;
        }
        return record.getLearnedPoints(Skills.AURA_TIME) > 0
                || record.getLearnedPoints(Skills.AURA_WEATHER) > 0
                // ⚠️ 2026-09-20 兜底：只要该玩家还持有**全局锁定状态**（timeLockState / weatherLockState 里还有他的条目），
                //    就必须保持本模块唤醒。否则一旦出现「已学点数为 0 但锁定未释放」的状态
                //    （重置技能、存档回滚、旧版本遗留…），模块会冬眠 →
                //    updateTimeLock(player,false) 永不执行 → timeLockCount 永远归不了零 →
                //    doDaylightCycle / doWeatherCycle **全服永久锁死**。
                //    有这个兜底，即使某条路径忘了显式释放，下一 tick 也能自行收敛。
                || AuraEvents.hasGlobalLockFor(player.getUUID());
    }

    @Override
    public void onTick(ServerPlayer player) {
        AuraEvents.tickGlobalLocks(player, AuraEvents.getRecord(player));
    }
}
