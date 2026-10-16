package org.zifeng.skilltree.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.zifeng.skilltree.SkillTreeMod;
import org.zifeng.skilltree.data.OperZone;

import java.util.ArrayList;
import java.util.List;

/**
 * 挖掘防护区全局列表（服务端 → 客户端，★ 2026-10-11）。
 *
 * <h2>为什么需要它</h2>
 * 挖掘防护是<b>全服生效</b>的（任何人在区内都挖不动），而玩家自己的
 * {@code operZones} 只含<b>自己</b>的区 —— 客户端据此无法判断"别人布的区"。
 * 没有这份全局列表，客户端就会照常显示<b>方块破坏进度条</b>（虽然服务端已拦下破坏），
 * 观感上像"没生效"。故仿 {@link MagnetExclusionS2CPacket} 做全服广播。
 *
 * <p>用途：客户端在 {@code LeftClickBlock} 里命中区内方块时取消事件 →
 * 原版不会开始破坏 → <b>进度条/裂纹动画都不出现</b>。
 */
public record MiningProtectS2CPacket(List<OperZone> zones) implements CustomPacketPayload {
    /**
     * ⚠️ 紧凑构造器做【不可变快照】：传入的可能是 SavedData 的不可变视图，
     * 而 encode 在 Netty 网络线程执行、服务端线程同时在增删区 → 遍历 CME → 编码失败踢人
     * （与 {@link MagnetExclusionS2CPacket} 同款修复）。
     */
    public MiningProtectS2CPacket {
        zones = List.copyOf(zones);
    }

    public static final Type<MiningProtectS2CPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "mining_protect_sync"));
    public static final StreamCodec<FriendlyByteBuf, MiningProtectS2CPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public MiningProtectS2CPacket decode(FriendlyByteBuf buf) {
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

        @Override
        public void encode(FriendlyByteBuf buf, MiningProtectS2CPacket p) {
            buf.writeVarInt(p.zones().size());
            for (OperZone z : p.zones()) {
                buf.writeUtf(z.dim());
                buf.writeVarInt(z.minX());
                buf.writeVarInt(z.minY());
                buf.writeVarInt(z.minZ());
                buf.writeVarInt(z.maxX());
                buf.writeVarInt(z.maxY());
                buf.writeVarInt(z.maxZ());
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MiningProtectS2CPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.flow().isClientbound()) {
                org.zifeng.skilltree.client.MiningProtectClientState.setZones(packet.zones());
            }
        });
    }
}
