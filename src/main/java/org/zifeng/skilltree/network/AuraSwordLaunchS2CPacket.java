package org.zifeng.skilltree.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * 杀戮光环·飞剑发射（服务端 → 客户端，2026-10-21）。
 *
 * <p>飞剑实体生成时发一次：客户端据此播放「平铺飞剑沿贝塞尔弧线飞向目标 + 青色拖尾」的动画。
 *
 * <p>⚠️ 视觉与判定分离：实体的碰撞仍是唯一的伤害来源（服务端），客户端动画只负责好看；
 * 飞行时长按「起点到目标距离 / 实体速度」估算，保证画面与判定的到达时间基本一致。
 */
public class AuraSwordLaunchS2CPacket {

    /** 最多同时派出的飞剑数（= 剑阵 8 把） */
    public static final int MAX_SWORDS = 8;

    private final Vec3 start;
    private final Vec3 target;
    /** 飞行速度倍率（速度光环等级越高越大） */
    private final float speed;

    public AuraSwordLaunchS2CPacket(Vec3 start, Vec3 target, float speed) {
        this.start = start;
        this.target = target;
        this.speed = speed;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(start.x);
        buf.writeDouble(start.y);
        buf.writeDouble(start.z);
        buf.writeDouble(target.x);
        buf.writeDouble(target.y);
        buf.writeDouble(target.z);
        buf.writeFloat(speed);
    }

    public static AuraSwordLaunchS2CPacket decode(FriendlyByteBuf buf) {
        return new AuraSwordLaunchS2CPacket(
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                buf.readFloat());
    }

    public static void handle(AuraSwordLaunchS2CPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> org.zifeng.skilltree.client.AuraSwordClientState.onLaunch(packet.start, packet.target, packet.speed));
        ctx.setPacketHandled(true);
    }

    /** 发给该玩家所在维度的所有玩家（含自己） */
    public static void sendNear(ServerPlayer owner, Vec3 start, Vec3 target, float speed) {
        if (owner == null || owner.serverLevel() == null || start == null || target == null) {
            return;
        }
        AuraSwordLaunchS2CPacket packet = new AuraSwordLaunchS2CPacket(start, target, speed);
        ModNetwork.CHANNEL.send(
                PacketDistributor.DIMENSION.with(() -> owner.serverLevel().dimension()), packet);
    }
}
