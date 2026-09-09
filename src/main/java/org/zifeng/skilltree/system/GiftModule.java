package org.zifeng.skilltree.system;

import net.minecraft.server.level.ServerPlayer;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.event.GiftEvents;
import org.zifeng.skilltree.skill.Skills;

/**
 * 子枫的馈赠模块（Z-Link 门面迁移，2026-09-09）。
 * <p>原 {@code GiftEvents.onPlayerTick}（每玩家必进）迁入本模块：
 * 未学任何馈赠技能的玩家 → 冬眠（不进入 tickGift，省累计/统计全部开销）。
 * 馈赠累计逻辑本体在 GiftEvents.tickGift（一字未改）。挖掘/击杀统计仍走原事件（非 tick）。
 */
public final class GiftModule implements ZModule {
    public static final GiftModule INSTANCE = new GiftModule();

    private GiftModule() {
    }

    @Override
    public String id() {
        return "gift";
    }

    /** 活跃条件：学了任一馈赠技能（时间/移动/飞行洗礼系列） */
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
        for (String skill : Skills.GIFT_SKILLS) {
            if (r.getLearnedPoints(skill) > 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onTick(ServerPlayer player) {
        GiftEvents.tickGift(player);
    }
}
