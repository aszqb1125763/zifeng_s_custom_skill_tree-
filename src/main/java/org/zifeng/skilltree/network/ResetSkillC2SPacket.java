package org.zifeng.skilltree.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.zifeng.skilltree.SkillTreeMod;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;
import org.zifeng.skilltree.event.UltimateEvents;
import org.zifeng.skilltree.skill.SkillEffects;
import org.zifeng.skilltree.skill.Skills;

/**
 * 重置单个技能请求（客户端 → 服务端，技能树界面 Ctrl+R + 鼠标悬停触发）：
 * 服务端按该技能总消耗 × 返还率（Config）加回技能点并移除该技能，更新属性修饰符。
 */
public record ResetSkillC2SPacket(String skillId) implements CustomPacketPayload {
    public static final Type<ResetSkillC2SPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "reset_skill"));
    public static final StreamCodec<FriendlyByteBuf, ResetSkillC2SPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ResetSkillC2SPacket::skillId,
            ResetSkillC2SPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ResetSkillC2SPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.flow().isServerbound() && ctx.player() instanceof ServerPlayer player) {
                String skillId = packet.skillId();
                if (skillId == null || !Skills.ALL_SKILLS.contains(skillId)) {
                    return;
                }
                PlayerSkillSavedData data = PlayerSkillSavedData.get(player.serverLevel());
                PlayerSkillRecord record = data.getOrCreatePlayer(player.getUUID());
                int learned = record.getLearnedPoints(skillId);
                if (learned <= 0) {
                    player.sendSystemMessage(Component.literal("⚠ 该技能未学习，无法重置"));
                    return;
                }
                // 计算该技能总消耗并返还
                double spent = record.totalSpentOf(skillId);
                record.resetSkill(skillId);
                data.setDirty();
                // 更新属性修饰符 + 清理终极被动临时状态
                SkillEffects.applyAll(player, record);
                // ⚠️ 顺序关键：先回收飞行权限（clearPlayerFlight 依赖 SKILL_FLIGHT_GRANTED 集合），
                //    再 clearPlayer 清状态（clearPlayer 会移除 SKILL_FLIGHT_GRANTED 条目，先清会导致无法回收）
                // 重置御空术/御空增幅 → 还原原版飞行速度（防止残留被持久化）
                if (Skills.FLY.equals(skillId) || Skills.AMP_FLY.equals(skillId)) {
                    UltimateEvents.resetFlyingSpeed(player);
                }
                // 重置宇宙的青睐 → 回收技能飞行权限（非创造才关闭）
                if (Skills.ULT_FAVOR.equals(skillId)) {
                    UltimateEvents.clearPlayerFlight(player);
                }
                UltimateEvents.clearPlayer(player);

                // 重置村庄英雄/发光/战利品爆炸/虚空之躯等被动 → 状态由事件每次 tick 按记录重判，无残留
                double rate = org.zifeng.skilltree.Config.RESET_REFUND_RATE.get();
                player.sendSystemMessage(Component.literal("♻ 已重置【" + Skills.getDisplayName(skillId) + "】，"
                        + (rate >= 1.0 ? "全额返还 " : "按 " + String.format("%.0f", rate * 100) + "% 返还 ")
                        + String.format("%.1f", spent * rate) + " 技能点"));
                PacketDistributor.sendToPlayer(player,
                        SkillTreeDataS2CPacket.from(record));
            }
        });
    }
}
