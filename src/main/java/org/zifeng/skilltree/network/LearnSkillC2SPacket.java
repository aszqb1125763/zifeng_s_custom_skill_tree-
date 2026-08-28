package org.zifeng.skilltree.network;
import net.minecraft.network.chat.Component;


import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;
import org.zifeng.skilltree.skill.SkillEffects;
import org.zifeng.skilltree.skill.Skills;

import java.util.Map;
import java.util.function.Supplier;

/**
 * 学习技能请求（客户端 → 服务端）：携带技能 ID 与学习级数，服务端校验后扣技能点并应用效果。
 * levels ≥ 1；Shift+点击一次加 10 级时 levels=10（服务端逐级校验，点数/上限不足自动停）。
 */
public class LearnSkillC2SPacket {
    private final String skillId;
    private final int levels;

    public LearnSkillC2SPacket(String skillId, int levels) {
        this.skillId = skillId;
        this.levels = levels;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(skillId);
        buf.writeVarInt(levels);
    }

    public static LearnSkillC2SPacket decode(FriendlyByteBuf buf) {
        return new LearnSkillC2SPacket(buf.readUtf(), buf.readVarInt());
    }

    public static void handle(LearnSkillC2SPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }
            PlayerSkillSavedData data = PlayerSkillSavedData.get(player.serverLevel());
            PlayerSkillRecord record = data.getOrCreatePlayer(player.getUUID());
            String skillId = packet.skillId;
            // 校验技能 ID 合法 + 级数有效（防刷包）
            int levels = Math.max(1, Math.min(100, packet.levels));
            if (Skills.ALL_SKILLS.contains(skillId) && checkUltimateRequirements(record, skillId)) {
                // 其余模组兼容技能：对应模组未安装 → 拒绝学习 + 红字提示（防刷包/误点）
                if (!checkModLoaded(skillId, player)) {
                    ModNetwork.sendToPlayer(player,
                            SkillTreeDataS2CPacket.from(record));
                    return;
                }
                // ⚠️ 子枫的馈赠（2026-08-25）：仅【时间系列】按游戏时长激活（不消耗技能点）；
                //    移动/飞行/挖掘洗礼与增幅消耗技能点（走正常 learnSkill 扣点），无时间门槛
                if (Skills.GIFT_TIME_BAPTISM.equals(skillId) || Skills.GIFT_TIME_STORM.equals(skillId)
                        || Skills.GIFT_TIME_FLOOD.equals(skillId)) {
                    long need = Skills.getGiftRequirementTicks(skillId);
                    long have = player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM, net.minecraft.stats.Stats.PLAY_TIME);
                    if (have < need) {
                        long remainSec = (need - have) / 20;
                        player.sendSystemMessage(Component.literal("⇄ ")
                                .append(Skills.getDisplayNameComponent(skillId))
                                .append(" 需要游戏时长 " + (need / 72000) + " 小时（当前 " + (have / 72000)
                                        + " 小时，还需约 " + (remainSec / 60) + " 分钟）")
                                .withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GOLD)));
                        ModNetwork.sendToPlayer(player, SkillTreeDataS2CPacket.from(record));
                        return;
                    }
                }
                boolean learned = false;
                for (int i = 0; i < levels; i++) {
                    if (record.learnSkill(skillId)) {
                        learned = true;
                        // 修复：升级后自动生效最高等级（否则 activeLevels 残留旧值导致加成一直是 0）
                        record.setActiveLevel(skillId, record.getLearnedPoints(skillId));
                    } else {
                        break; // 点数/上限不足停止
                    }
                }
                if (learned) {
                    data.setDirty();
                    SkillEffects.applyAll(player, record);
                    // 子枫的馈赠激活成功提示（不消耗技能点）
                    if (Skills.isGiftSkill(skillId)) {
                        player.sendSystemMessage(Component.translatable(
                                "chat.zifeng_s_custom_skill_tree.gift_activated",
                                Skills.getDisplayNameComponent(skillId)));
                    }
                }
            }
            ModNetwork.sendToPlayer(player,
                    SkillTreeDataS2CPacket.from(record));
        });
        ctx.setPacketHandled(true);
    }

    /** 其余模组兼容技能：对应模组未安装 → 拒绝学习并红字提示（保证整合包无该模组时不崩溃） */
    private static boolean checkModLoaded(String skillId, ServerPlayer player) {
        String missing = missingModName(skillId);
        if (missing != null) {
            player.sendSystemMessage(Component.translatable(
                            "chat.zifeng_s_custom_skill_tree.no_mod_learn",
                            missing, Skills.getDisplayNameComponent(skillId))
                            .withStyle(s -> s.withColor(net.minecraft.ChatFormatting.RED)));
            return false;
        }
        return true;
    }

    /** 其余模组兼容技能：返回缺失模组的中文名；模组已装或非兼容技能 → null */
    private static String missingModName(String skillId) {
        if (Skills.MANA_AMP.equals(skillId) || Skills.ARS_MANA_REGEN.equals(skillId)) {
            return org.zifeng.skilltree.compat.ArsNouveauCompat.isLoaded() ? null : Component.translatable("ui.zifeng_s_custom_skill_tree.mod_ars").getString();
        }
        if (Skills.IRON_MANA_AMP.equals(skillId) || Skills.IRON_MANA_REGEN.equals(skillId)
                || Skills.IRON_CAST_TIME.equals(skillId) || Skills.IRON_COOLDOWN.equals(skillId)
                || Skills.IRON_FIRE.equals(skillId) || Skills.IRON_ICE.equals(skillId) || Skills.IRON_LIGHTNING.equals(skillId)
                || Skills.IRON_HOLY.equals(skillId) || Skills.IRON_ENDER.equals(skillId)
                || Skills.IRON_BLOOD.equals(skillId) || Skills.IRON_EVOCATION.equals(skillId)
                || Skills.IRON_NATURE.equals(skillId) || Skills.IRON_ELDRITCH.equals(skillId)) {
            return org.zifeng.skilltree.compat.IronSpellsCompat.isLoaded() ? null : Component.translatable("ui.zifeng_s_custom_skill_tree.mod_iron").getString();
        }
        return null;
    }

    /** 前置校验（终极/光环通用）：前置技能 → 所需等级 */
    private static boolean checkUltimateRequirements(PlayerSkillRecord record, String skillId) {
        for (Map.Entry<String, Integer> entry : Skills.getPrerequisites(skillId)) {
            if (record.getLearnedPoints(entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }
}
