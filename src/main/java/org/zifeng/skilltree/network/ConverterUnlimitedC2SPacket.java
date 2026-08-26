package org.zifeng.skilltree.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.zifeng.skilltree.blockentity.SkillPointConverterBlockEntity;
import org.zifeng.skilltree.menu.SkillPointConverterMenu;

import java.util.function.Supplier;

/**
 * 技能点转换机：GUI 无限制输入开关（客户端 → 服务端）。
 * 双保险定位机器：①服务端从玩家当前打开的容器菜单拿引用；②客户端传 BlockPos 兜底。
 * 开启后忽略输入速率限制（无限制输入 FE），仍受能量缓冲上限约束。
 */
public class ConverterUnlimitedC2SPacket {
    private final BlockPos pos;
    private final boolean unlimited;

    public ConverterUnlimitedC2SPacket(BlockPos pos, boolean unlimited) {
        this.pos = pos;
        this.unlimited = unlimited;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeBoolean(unlimited);
    }

    public static ConverterUnlimitedC2SPacket decode(FriendlyByteBuf buf) {
        return new ConverterUnlimitedC2SPacket(buf.readBlockPos(), buf.readBoolean());
    }

    public static void handle(ConverterUnlimitedC2SPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }
            SkillPointConverterBlockEntity converter = null;
            // ① 从玩家当前打开的容器菜单拿机器引用（最可靠）
            if (player.containerMenu instanceof SkillPointConverterMenu menu) {
                converter = menu.getBlockEntity();
            }
            // ② BlockPos 兜底（限距离校验，防远程改他人机器）
            if (converter == null) {
                if (player.distanceToSqr(packet.pos.getCenter()) <= 25 * 25
                        && player.level().getBlockEntity(packet.pos) instanceof SkillPointConverterBlockEntity be) {
                    converter = be;
                }
            }
            if (converter != null) {
                converter.setUnlimitedInput(packet.unlimited);
            }
        });
        ctx.setPacketHandled(true);
    }
}
