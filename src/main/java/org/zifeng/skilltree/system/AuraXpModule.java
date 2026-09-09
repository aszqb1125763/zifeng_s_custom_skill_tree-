package org.zifeng.skilltree.system;

import net.minecraft.server.level.ServerPlayer;
import org.zifeng.skilltree.event.AuraEvents;
import org.zifeng.skilltree.skill.Skills;

/**
 * 汲灵之环模块（Z-Link 门面迁移，2026-09-09）。
 * <p>原 {@code AuraEvents.onPlayerTick} 里的 auraXp 调用迁入本模块：
 * 未学汲灵之环的玩家 → 冬眠。光环本体逻辑仍在 AuraEvents.auraXp（一字未改）。
 */
public final class AuraXpModule implements ZModule {
    public static final AuraXpModule INSTANCE = new AuraXpModule();

    private AuraXpModule() {
    }

    @Override
    public String id() {
        return "aura_xp";
    }

    /** 活跃条件：学了汲灵之环（未学 = 冬眠零开销） */
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
        return record.getLearnedPoints(Skills.AURA_XP) > 0;
    }

    @Override
    public void onTick(ServerPlayer player) {
        AuraEvents.auraXp(player, AuraEvents.getRecord(player));
    }
}
