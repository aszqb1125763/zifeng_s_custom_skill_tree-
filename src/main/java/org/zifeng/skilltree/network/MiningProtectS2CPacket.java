package org.zifeng.skilltree.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.zifeng.skilltree.data.OperZone;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 挖掘防护区全局列表（服务端 → 客户端，★ 2026-10-11，1.20.1）。
 *
 * <h2>为什么需要它</h2>
 * 挖掘防护是<b>全服生效</b>的（任何人在区内都挖不动），而玩家自己的
 * {@code operZones} 只含<b>自己</b>的区 —— 客户端据此无法判断"别人布的区"。
 * 没有这份全局列表，客户端就会照常显示<b>方块破坏进度条</b>（虽然服务端已拦下破坏），
 * 观感上像"没生效"。
 *
 * <p>用途：客户端在 {@code LeftClickBlock} 里命中区内方块时取消事件 →
 * 原版不会开始破坏 → <b>进度条/裂纹动画都不出现</b>。
 */
public class MiningProtectS2CPacket {
    /** 不可变快照（encode 在网络线程执行，服务端线程可能同时在增删区） */
    private final List<OperZone> zones;

    public MiningProtectS2CPacket(List<OperZone> zones) {
        this.zones = zones == null ? List.of() : List.copyOf(zones);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(zones.size());
        for (OperZone z : zones) {
            buf.writeUtf(z.dim());
            buf.writeVarInt(z.minX());
            buf.writeVarInt(z.minY());
            buf.writeVarInt(z.minZ());
            buf.writeVarInt(z.maxX());
            buf.writeVarInt(z.maxY());
            buf.writeVarInt(z.maxZ());
        }
    }

    public static MiningProtectS2CPacket decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<OperZone> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String dim = buf.readUtf();
            int ax = buf.readVarInt(), ay = buf.readVarInt(), az = buf.readVarInt();
            int bx = buf.readVarInt(), by = buf.readVarInt(), bz = buf.readVarInt();
            list.add(new OperZone(dim, ax, ay, az, bx, by, bz));
        }
        return new MiningProtectS2CPacket(list);
    }

    public static void handle(MiningProtectS2CPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> org.zifeng.skilltree.client.MiningProtectClientState.setZones(packet.zones)));
        ctx.setPacketHandled(true);
    }
}
