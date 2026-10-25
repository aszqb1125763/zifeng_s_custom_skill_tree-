package org.zifeng.skilltree.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * 杀戮光环·飞剑命中爆炸（服务端 → 客户端，2026-10-21）。
 *
 * <p>飞剑实体与生物<b>碰撞</b>的那一刻由服务端发出：客户端以该点为中心画一圈
 * 半径 10 格、向外扩散的红色光环（纯视觉）。
 *
 * <p>伤害不在这里——伤害由飞剑实体的碰撞在服务端直接结算，天然与命中同步。
 */
public class AuraBurstS2CPacket {

    private final Vec3 center;

    public AuraBurstS2CPacket(Vec3 center) {
        this.center = center;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(center.x);
        buf.writeDouble(center.y);
        buf.writeDouble(center.z);
    }

    public static AuraBurstS2CPacket decode(FriendlyByteBuf buf) {
        return new AuraBurstS2CPacket(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
    }

    public static void handle(AuraBurstS2CPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> org.zifeng.skilltree.client.AuraSwordClientState.onBlast(packet.center));
        ctx.setPacketHandled(true);
    }

    /** 发给该玩家所在维度的所有玩家（含自己） */
    public static void sendNear(ServerPlayer owner, Vec3 center) {
        if (owner == null || owner.serverLevel() == null || center == null) {
            return;
        }
        AuraBurstS2CPacket packet = new AuraBurstS2CPacket(center);
        ModNetwork.CHANNEL.send(
                PacketDistributor.DIMENSION.with(() -> owner.serverLevel().dimension()), packet);
    }
}
