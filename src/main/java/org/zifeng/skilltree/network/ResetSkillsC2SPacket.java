package org.zifeng.skilltree.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.zifeng.skilltree.SkillTreeMod;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;
import org.zifeng.skilltree.event.AuraEvents;
import org.zifeng.skilltree.event.UltimateEvents;
import org.zifeng.skilltree.skill.SkillEffects;

/**
 * 技能重洗请求（客户端 → 服务端，技能树界面 Ctrl+R 触发）：
 * 服务端按总消耗 × 返还率（Config）加回技能点并清空全部技能，移除属性修饰符/环绕剑/终极状态。
 */
public record ResetSkillsC2SPacket() implements CustomPacketPayload {
    public static final Type<ResetSkillsC2SPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "reset_skills"));
    public static final StreamCodec<FriendlyByteBuf, ResetSkillsC2SPacket> STREAM_CODEC = StreamCodec.unit(new ResetSkillsC2SPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ResetSkillsC2SPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.flow().isServerbound() && ctx.player() instanceof ServerPlayer player) {
                PlayerSkillSavedData data = PlayerSkillSavedData.get(player.serverLevel());
                PlayerSkillRecord record = data.getOrCreatePlayer(player.getUUID());
                double refund = record.resetAll();
                data.setDirty();
                // 清空属性修饰符 + 终极被动临时状态（光环无实体，无需清理）
                SkillEffects.applyAll(player, record);
                UltimateEvents.clearPlayer(player);
                // 提示返还（含返还率显示）
                double rate = org.zifeng.skilltree.Config.RESET_REFUND_RATE.get();
                if (rate >= 1.0) {
                    player.sendSystemMessage(Component.literal("♻ 技能已重洗，全额返还 " + String.format("%.1f", refund) + " 技能点"));
                } else {
                    player.sendSystemMessage(Component.literal("♻ 技能已重洗，按 " + String.format("%.0f", rate * 100) + "% 返还 " + String.format("%.1f", refund) + " 技能点"));
                }
                PacketDistributor.sendToPlayer(player,
                        SkillTreeDataS2CPacket.from(record));
            }
        });
    }
}
