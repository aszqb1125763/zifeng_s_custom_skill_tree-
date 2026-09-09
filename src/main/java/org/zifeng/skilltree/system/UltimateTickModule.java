package org.zifeng.skilltree.system;

import net.minecraft.server.level.ServerPlayer;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.event.UltimateEvents;
import org.zifeng.skilltree.skill.Skills;

/**
 * 终极节点/常驻效果 tick 模块（Z-Link 门面迁移，2026-09-09）。
 * <p>原 {@code UltimateEvents.onPlayerTick}（300+ 行巨型链，每玩家必进）整体迁入：
 * 玩家【未学任何】相关技能 → 冬眠（不再进入巨型链，省 record 获取 + 全部块判断）。
 * 学过任一 → 唤醒并调用原 body（UltimateEvents.tickPlayer，内部逐块判断开关，行为一字未改）。
 * <p>覆盖技能：再生体魄/不坏金身/宇宙的青睐/御空术+增幅/夜视/饱食/村庄英雄/发光/
 * 全能精通/虚空之躯/鲛人之息/无限回路/凤凰涅槃 —— 任一已学即唤醒。
 */
public final class UltimateTickModule implements ZModule {
    public static final UltimateTickModule INSTANCE = new UltimateTickModule();

    private UltimateTickModule() {
    }

    @Override
    public String id() {
        return "ultimate_tick";
    }

    /** 活跃条件：学了任一常驻/终极 tick 技能（未学全部 → 冬眠零开销） */
    @Override
    public boolean activeCondition(ServerPlayer player) {
        // ⚠️ 2026-09-09 性能审计：只读 getPlayer——无记录（从没学任何技能）→ 冬眠，不建空记录
        if (player.serverLevel() == null) {
            return false;
        }
        PlayerSkillRecord r = org.zifeng.skilltree.data.PlayerSkillSavedData
                .get(player.serverLevel()).getPlayer(player.getUUID());
        if (r == null) {
            return false;
        }
        return r.getLearnedPoints(Skills.REGEN) > 0
                || r.getLearnedPoints(Skills.ULT_GOLDEN) > 0
                || r.getLearnedPoints(Skills.ULT_FAVOR) > 0
                || r.getLearnedPoints(Skills.FLY) > 0
                || r.getLearnedPoints(Skills.AMP_FLY) > 0
                || r.getLearnedPoints(Skills.NIGHT_VISION) > 0
                || r.getLearnedPoints(Skills.SATURATION) > 0
                || r.getLearnedPoints(Skills.VILLAGE_HERO) > 0
                || r.getLearnedPoints(Skills.GLOW) > 0
                || r.getLearnedPoints(Skills.ULT_MASTER) > 0
                || r.getLearnedPoints(Skills.ULT_VOID_BODY) > 0
                || r.getLearnedPoints(Skills.WATER_BREATH) > 0
                || r.getLearnedPoints(Skills.AE_INFINITE_CHANNEL) > 0
                || r.getLearnedPoints(Skills.ULT_REVIVE) > 0;
    }

    @Override
    public void onTick(ServerPlayer player) {
        UltimateEvents.tickPlayer(player);
    }
}
