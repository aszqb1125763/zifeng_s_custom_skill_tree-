package org.zifeng.skilltree.system;

import net.minecraft.server.level.ServerPlayer;
import org.zifeng.skilltree.event.AuraEvents;
import org.zifeng.skilltree.skill.Skills;

/**
 * 杀戮光环·伤害模块（Z-Link 门面迁移，2026-09-09）。
 * <p>原 {@code AuraEvents.onPlayerTick} 里对 auraAttack 的每 tick 调用迁入本模块：
 * 未学 伤害光环/虚空之矛 的玩家 → 冬眠（不进入 auraAttack，省去方法进入与内部早退）。
 * 光环本体逻辑仍在 AuraEvents.auraAttack（一字未改），本模块只做"调度门"。
 */
public final class AuraDamageModule implements ZModule {
    public static final AuraDamageModule INSTANCE = new AuraDamageModule();

    private AuraDamageModule() {
    }

    @Override
    public String id() {
        return "aura_damage";
    }

    /** 活跃条件：学了伤害光环 或 虚空之矛（与 auraAttack 首个早退一致；未学 = 冬眠零开销） */
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
        return record.getLearnedPoints(Skills.AURA_DAMAGE) > 0
                || record.getLearnedPoints(Skills.AURA_VOID) > 0;
    }

    @Override
    public void onTick(ServerPlayer player) {
        AuraEvents.auraAttack(player, AuraEvents.getRecord(player));
    }
}
