package org.zifeng.skilltree.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.zifeng.skilltree.blockentity.SkillPointConverterBlockEntity;
import org.zifeng.skilltree.menu.SkillPointConverterMenu;

import java.util.function.Supplier;

/**
 * 技能点转换机：GUI 设置输入速率（客户端 → 服务端，每机器独立，NBT 持久化）。
 * 双保险定位机器：①服务端从玩家当前打开的容器菜单拿引用；②客户端传 BlockPos 兜底。
 */
public class ConverterRateC2SPacket {
    private final BlockPos pos;
    private final long rate;

    public ConverterRateC2SPacket(BlockPos pos, long rate) {
        this.pos = pos;
        this.rate = rate;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarLong(rate);
    }

    public static ConverterRateC2SPacket decode(FriendlyByteBuf buf) {
        return new ConverterRateC2SPacket(buf.readBlockPos(), buf.readVarLong());
    }

    public static void handle(ConverterRateC2SPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }
            if (packet.rate < 1 || packet.rate > 1_000_000_000_000L) {
                return; // 非法值拒绝
            }
            SkillPointConverterBlockEntity converter = null;
            // ① 从玩家当前打开的容器菜单拿机器引用（最可靠）
            if (player.containerMenu instanceof SkillPointConverterMenu menu) {
                converter = menu.getBlockEntity();
            }
            // ② BlockPos 兜底（限距离 + owner 校验，防远程/他人改机器）
            if (converter == null) {
                if (player.distanceToSqr(packet.pos.getCenter()) <= 25 * 25
                        && player.level().getBlockEntity(packet.pos) instanceof SkillPointConverterBlockEntity be
                        && (be.getOwnerUUID() == null || be.getOwnerUUID().equals(player.getUUID()))) {
                    converter = be;
                }
            }
            if (converter != null) {
                converter.setInputRate(packet.rate);
            }
        });
        ctx.setPacketHandled(true);
    }
}
