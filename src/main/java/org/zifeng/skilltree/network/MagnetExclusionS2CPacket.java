package org.zifeng.skilltree.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.zifeng.skilltree.SkillTreeMod;
import org.zifeng.skilltree.data.MagnetExclusionZone;

import java.util.ArrayList;
import java.util.List;

/**
 * 磁铁屏蔽区列表同步（服务端 → 客户端，2026-09-07 / 2026-09-08 全局共享）：
 * 服务器全局屏蔽区列表——任何玩家增删后向全服广播；进服时回发当前全量。
 * 客户端用于渲染红框（全服同一份）与删除命中预判。
 */
public record MagnetExclusionS2CPacket(List<MagnetExclusionZone> zones) implements CustomPacketPayload {
    /**
     * ⚠️ 2026-09-10 修复：紧凑构造器做【不可变快照】。
     * 传入的 list 可能是 SavedData 的 {@code unmodifiableList} 视图，而 encode 在 Netty
     * 网络线程执行、服务端线程同时在增删屏蔽区 → 遍历时 CME → 编码失败 → 玩家被踢下线。
     */
    public MagnetExclusionS2CPacket {
        zones = List.copyOf(zones);
    }
    public static final Type<MagnetExclusionS2CPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "magnet_exclusion_sync"));
    public static final StreamCodec<FriendlyByteBuf, MagnetExclusionS2CPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public MagnetExclusionS2CPacket decode(FriendlyByteBuf buf) {
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

        @Override
        public void encode(FriendlyByteBuf buf, MagnetExclusionS2CPacket p) {
            buf.writeVarInt(p.zones().size());
            for (MagnetExclusionZone z : p.zones()) {
                buf.writeUtf(z.dim());
                buf.writeVarInt(z.ax());
                buf.writeVarInt(z.ay());
                buf.writeVarInt(z.az());
                buf.writeVarInt(z.bx());
                buf.writeVarInt(z.by());
                buf.writeVarInt(z.bz());
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MagnetExclusionS2CPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.flow().isClientbound()) {
                org.zifeng.skilltree.client.MagnetExclusionClientState.setZones(packet.zones());
            }
        });
    }
}
