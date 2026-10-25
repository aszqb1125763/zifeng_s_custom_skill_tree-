package org.zifeng.skilltree.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.zifeng.skilltree.SkillTreeMod;

/** 杀戮光环·飞剑命中爆炸（服务端 → 客户端，2026-10-21）：在命中点画 10 格红环。 */
public record AuraBurstS2CPacket(Vec3 center) implements CustomPacketPayload {

    public static final Type<AuraBurstS2CPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "aura_burst"));

    public static final StreamCodec<FriendlyByteBuf, AuraBurstS2CPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeDouble(packet.center().x);
                buf.writeDouble(packet.center().y);
                buf.writeDouble(packet.center().z);
            },
            buf -> new AuraBurstS2CPacket(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble())));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AuraBurstS2CPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> org.zifeng.skilltree.client.AuraSwordClientState.onBlast(packet.center()));
    }

    public static void sendNear(ServerLevel level, Vec3 center) {
        if (level == null || center == null) {
            return;
        }
        PacketDistributor.sendToPlayersNear(level, null, center.x, center.y, center.z, 96.0,
                new AuraBurstS2CPacket(center));
    }
}
