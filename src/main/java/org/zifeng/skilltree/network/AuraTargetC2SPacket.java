package org.zifeng.skilltree.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.zifeng.skilltree.SkillTreeMod;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;

/**
 * 杀戮光环目标模式切换（客户端 → 服务端）：0=敌对 1=友好 2=所有。
 */
public record AuraTargetC2SPacket(int mode) implements CustomPacketPayload {
    public static final Type<AuraTargetC2SPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "aura_target"));
    public static final StreamCodec<FriendlyByteBuf, AuraTargetC2SPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AuraTargetC2SPacket::mode,
            AuraTargetC2SPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AuraTargetC2SPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.flow().isServerbound() && ctx.player() instanceof ServerPlayer player) {
                PlayerSkillSavedData data = PlayerSkillSavedData.get(player.serverLevel());
                PlayerSkillRecord record = data.getOrCreatePlayer(player.getUUID());
                record.setAuraTargetMode(packet.mode());
                data.setDirty();
                // 聊天提示
                String modeText = switch (packet.mode()) {
                    case 1 -> "友好生物";
                    case 2 -> "所有生物";
                    default -> "敌对生物";
                };
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("🎯 杀戮光环目标：" + modeText));
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                        new SkillTreeDataS2CPacket(record.getSkillPoints(), record.getLearnedSkills(), record.getToggles(),
                                record.getActiveLevels(), record.isAuraEnabled(), record.getAuraTargetMode()));
            }
        });
    }
}
