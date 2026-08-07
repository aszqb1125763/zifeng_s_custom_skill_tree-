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

/**
 * 切换杀戮光环总开关（客户端 → 服务端，快捷键触发），并发送聊天提示。
 */
public record ToggleAuraC2SPacket() implements CustomPacketPayload {
    public static final Type<ToggleAuraC2SPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "toggle_aura"));
    public static final StreamCodec<FriendlyByteBuf, ToggleAuraC2SPacket> STREAM_CODEC = StreamCodec.unit(new ToggleAuraC2SPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ToggleAuraC2SPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.flow().isServerbound() && ctx.player() instanceof ServerPlayer player) {
                PlayerSkillSavedData data = PlayerSkillSavedData.get(player.serverLevel());
                PlayerSkillRecord record = data.getOrCreatePlayer(player.getUUID());
                boolean now = !record.isAuraEnabled();
                record.setAuraEnabled(now);
                data.setDirty();
                // 聊天提示
                player.sendSystemMessage(Component.literal(now
                        ? "⚔ 杀戮光环已开启"
                        : "✖ 杀戮光环已关闭（环绕剑已移除）"));
                // 关闭时立即清除环绕剑
                if (!now) {
                    AuraEvents.clearSwords(player);
                }
                PacketDistributor.sendToPlayer(player,
                        new SkillTreeDataS2CPacket(record.getSkillPoints(), record.getLearnedSkills(),
                                record.getToggles(), record.getActiveLevels(), record.isAuraEnabled(), record.getAuraTargetMode()));
            }
        });
    }
}
