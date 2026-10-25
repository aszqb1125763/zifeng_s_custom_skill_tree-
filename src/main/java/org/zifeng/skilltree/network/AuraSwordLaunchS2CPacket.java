package org.zifeng.skilltree.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.zifeng.skilltree.SkillTreeMod;

/** 杀戮光环·飞剑发射（服务端 → 客户端，2026-10-21）：客户端播放贝塞尔飞剑动画。 */
public record AuraSwordLaunchS2CPacket(Vec3 start, Vec3 target, float speed) implements CustomPacketPayload {

    /** 最多同时派出的飞剑数（= 剑阵 8 把） */
    public static final int MAX_SWORDS = 8;

    public static final Type<AuraSwordLaunchS2CPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "aura_sword_launch"));

    public static final StreamCodec<FriendlyByteBuf, AuraSwordLaunchS2CPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                writeVec(buf, packet.start());
                writeVec(buf, packet.target());
                buf.writeFloat(packet.speed());
            },
            buf -> new AuraSwordLaunchS2CPacket(readVec(buf), readVec(buf), buf.readFloat()));

    private static void writeVec(FriendlyByteBuf buf, Vec3 v) {
        buf.writeDouble(v.x);
        buf.writeDouble(v.y);
        buf.writeDouble(v.z);
    }

    private static Vec3 readVec(FriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AuraSwordLaunchS2CPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> org.zifeng.skilltree.client.AuraSwordClientState.onLaunch(packet.start(), packet.target(), packet.speed()));
    }

    public static void sendNear(ServerLevel level, Vec3 start, Vec3 target, float speed) {
        if (level == null || start == null || target == null) {
            return;
        }
        PacketDistributor.sendToPlayersNear(level, null, start.x, start.y, start.z, 96.0,
                new AuraSwordLaunchS2CPacket(start, target, speed));
    }
}
