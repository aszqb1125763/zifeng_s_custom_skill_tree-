package org.zifeng.skilltree.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;
import org.zifeng.skilltree.skill.Skills;

import java.util.function.Supplier;

/**
 * 磁铁屏蔽区操作（客户端 → 服务端，2026-09-07 / 2026-09-08 全局共享，1.20.1）：
 * <ul>
 *   <li>type=0 ADD：客户端已选两角 → 服务端写入【全服全局】屏蔽区（任何磁铁玩家都吸不了区内）</li>
 *   <li>type=1 REMOVE：客户端潜行+左键对着一屏蔽区 → 服务端按玩家视线射线从全局删除命中区</li>
 * </ul>
 * 校验：磁铁已学 + 工具开 + RANGE 模块；增删后 MagnetZoneGlobalData 自动持久化 + 全服广播。
 */
public class MagnetExclusionC2SPacket {
    private final int type;
    private final String dim;
    private final int ax, ay, az;
    private final int bx, by, bz;

    public MagnetExclusionC2SPacket(int type, String dim, int ax, int ay, int az, int bx, int by, int bz) {
        this.type = type;
        this.dim = dim;
        this.ax = ax;
        this.ay = ay;
        this.az = az;
        this.bx = bx;
        this.by = by;
        this.bz = bz;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(type);
        buf.writeUtf(dim);
        buf.writeVarInt(ax);
        buf.writeVarInt(ay);
        buf.writeVarInt(az);
        buf.writeVarInt(bx);
        buf.writeVarInt(by);
        buf.writeVarInt(bz);
    }

    public static MagnetExclusionC2SPacket decode(FriendlyByteBuf buf) {
        return new MagnetExclusionC2SPacket(buf.readVarInt(), buf.readUtf(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(MagnetExclusionC2SPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || player.serverLevel() == null) {
                return;
            }
            PlayerSkillRecord record = PlayerSkillSavedData.get(player.serverLevel()).getOrCreatePlayer(player.getUUID());
            // 技能校验：磁铁需已学 + 工具开 + RANGE 模块（服务端权威）
            if (record.getLearnedPoints(Skills.AURA_MAGNET) <= 0
                    || !record.isStickToolOn()
                    || record.getStickToolMode() != Skills.STICK_MODE_RANGE) {
                return;
            }
            org.zifeng.skilltree.data.MagnetZoneGlobalData global =
                    org.zifeng.skilltree.data.MagnetZoneGlobalData.get(player.serverLevel());
            String currentDim = player.serverLevel().dimension().location().toString();
            if (packet.type == 0) {
                // ADD：维度必须与玩家当前所在维度一致（防作弊乱存他维区）
                if (!currentDim.equals(packet.dim)) {
                    return;
                }
                global.addZone(packet.dim,
                        packet.ax, packet.ay, packet.az,
                        packet.bx, packet.by, packet.bz); // 内部处理持久化+全服广播
            } else if (packet.type == 1) {
                // REMOVE：服务端按玩家当前视线做射线删除（不信任客户端传来的删除对象/维度）
                net.minecraft.world.phys.Vec3 eye = player.getEyePosition();
                net.minecraft.world.phys.Vec3 look = player.getLookAngle();
                global.removeZoneAt(currentDim, eye, look); // 内部处理持久化+全服广播
            }
        });
        ctx.setPacketHandled(true);
    }
}
