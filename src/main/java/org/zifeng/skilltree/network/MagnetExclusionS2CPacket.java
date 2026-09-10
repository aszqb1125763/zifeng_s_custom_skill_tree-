package org.zifeng.skilltree.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.zifeng.skilltree.client.MagnetExclusionClientState;
import org.zifeng.skilltree.data.MagnetExclusionZone;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 磁铁屏蔽区列表同步（服务端 → 客户端，2026-09-07 / 2026-09-08 全局共享，1.20.1）：
 * 服务器全局屏蔽区列表——任何玩家增删后向全服广播；进服时回发当前全量。
 * 客户端用于渲染红框（全服同一份）与删除命中预判。
 */
public class MagnetExclusionS2CPacket {
    private final List<MagnetExclusionZone> zones;

    public MagnetExclusionS2CPacket(List<MagnetExclusionZone> zones) {
        // ⚠️ 2026-09-10 修复：拷贝为不可变快照。传入的可能是 SavedData 的 unmodifiableList 视图，
        //    而 encode 在网络线程执行、服务端线程同时在增删屏蔽区 → 遍历时 CME → 编码失败 → 玩家被踢。
        this.zones = List.copyOf(zones);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(zones.size());
        for (MagnetExclusionZone z : zones) {
            buf.writeUtf(z.dim());
            buf.writeVarInt(z.ax());
            buf.writeVarInt(z.ay());
            buf.writeVarInt(z.az());
            buf.writeVarInt(z.bx());
            buf.writeVarInt(z.by());
            buf.writeVarInt(z.bz());
        }
    }

    public static MagnetExclusionS2CPacket decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<MagnetExclusionZone> list = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            String dim = buf.readUtf();
            int ax = buf.readVarInt(), ay = buf.readVarInt(), az = buf.readVarInt();
            int bx = buf.readVarInt(), by = buf.readVarInt(), bz = buf.readVarInt();
            list.add(new MagnetExclusionZone(dim, ax, ay, az, bx, by, bz));
        }
        return new MagnetExclusionS2CPacket(list);
    }

    public static void handle(MagnetExclusionS2CPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                MagnetExclusionClientState.setZones(packet.zones);
            }
        });
        ctx.setPacketHandled(true);
    }
}
