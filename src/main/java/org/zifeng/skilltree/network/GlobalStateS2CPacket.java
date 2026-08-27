package org.zifeng.skilltree.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.zifeng.skilltree.SkillTreeMod;
import org.zifeng.skilltree.client.ClientGlobalState;

/**
 * 服务器全局状态同步（服务端 → 客户端，2026-08-27）：全局技能（寰宇法则）的服务器当前状态提示。
 * 目前携带 AE2 频道模式码（0=默认 1=X2 2=X3 3=X4 4=无限）；时之环/晴空环状态客户端本地读 gamerule 零开销。
 * <p>性能：仅 1 字节小包；只在模式变化时发送（服务端按玩家上次收到码对比）。
 */
public record GlobalStateS2CPacket(int aeChannelMode) implements CustomPacketPayload {
    public static final Type<GlobalStateS2CPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "global_state"));
    public static final StreamCodec<FriendlyByteBuf, GlobalStateS2CPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GlobalStateS2CPacket::aeChannelMode,
            GlobalStateS2CPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(GlobalStateS2CPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.flow().isClientbound()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    ClientGlobalState.setAeChannelMode(packet.aeChannelMode());
                }
            }
        });
    }
}
