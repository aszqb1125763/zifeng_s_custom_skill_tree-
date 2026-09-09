package org.zifeng.skilltree.system;

import net.minecraft.server.level.ServerPlayer;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.event.ZoneSkillEvents;
import org.zifeng.skilltree.skill.Skills;

/**
 * 机械共鸣·选区攻击模块（Z-Link 门面迁移，2026-09-09）。
 * <p>原 {@code ZoneSkillEvents.onPlayerTick} 每玩家必进（空跑 record 获取 + 判定）；
 * 现迁入本模块：未学攻击区技能的玩家 → 冬眠。逻辑本体在 ZoneSkillEvents.tickZoneAttack（一字未改）。
 */
public final class ZoneAttackModule implements ZModule {
    public static final ZoneAttackModule INSTANCE = new ZoneAttackModule();

    private ZoneAttackModule() {
    }

    @Override
    public String id() {
        return "zone_attack";
    }

    /** 活跃条件：学了选区攻击技能（未学 → 冬眠零开销） */
    @Override
    public boolean activeCondition(ServerPlayer player) {
        // ⚠️ 2026-09-09 性能审计：只读 getPlayer——无记录（从没学任何技能）→ 冬眠，不建空记录
        if (player.serverLevel() == null) {
            return false;
        }
        PlayerSkillRecord record = org.zifeng.skilltree.data.PlayerSkillSavedData
                .get(player.serverLevel()).getPlayer(player.getUUID());
        if (record == null) {
            return false;
        }
        return record.getLearnedPoints(Skills.MACHINE_ZONE_ATTACK) > 0
                && record.isEnabled(Skills.MACHINE_ZONE_ATTACK);
    }

    @Override
    public void onTick(ServerPlayer player) {
        ZoneSkillEvents.tickZoneAttack(player);
    }
}
