package org.zifeng.skilltree.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.zifeng.skilltree.SkillTreeMod;
import org.zifeng.skilltree.client.ReviveHudRenderer;

/**
 * 凤凰涅槃冷却状态（服务端 → 客户端，每秒同步一次）：
 * <ul>
 *   <li>learned = 是否已学且启用（false 时 HUD 隐藏图标）</li>
 *   <li>remainingTicks = 剩余冷却 tick（0 = 冷却就绪）</li>
 * </ul>
 */
public record ReviveCooldownS2CPacket(boolean learned, int remainingTicks) implements CustomPacketPayload {
    public static final Type<ReviveCooldownS2CPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "revive_cooldown"));
    public static final StreamCodec<FriendlyByteBuf, ReviveCooldownS2CPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ReviveCooldownS2CPacket::learned,
            ByteBufCodecs.VAR_INT, ReviveCooldownS2CPacket::remainingTicks,
            ReviveCooldownS2CPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ReviveCooldownS2CPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.flow().isClientbound()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    ReviveHudRenderer.setCooldown(packet.learned(), packet.remainingTicks());
                }
            }
        });
    }
}
