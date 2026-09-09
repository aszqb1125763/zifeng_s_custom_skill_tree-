package org.zifeng.skilltree.network;


import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * 子枫的搬运术·手动触发（客户端 → 服务端，功能触发键按下时发送，2026-09-07）：
 * 空负载——服务端把玩家当前打开的容器物品搬进绑定容器（手动模式）。
 * ⚠️ 该包在「容器 GUI 打开时」也会发送（功能触发键独立于普通快捷键检测）。
 */
public class ContainerHaulC2SPacket {
    public ContainerHaulC2SPacket() {
    }

    public void encode(FriendlyByteBuf buf) {
    }

    public static ContainerHaulC2SPacket decode(FriendlyByteBuf buf) {
        return new ContainerHaulC2SPacket();
    }

    public static void handle(ContainerHaulC2SPacket packet, java.util.function.Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }
            org.zifeng.skilltree.event.ContainerHaulEvents.triggerManual(player);
        });
        ctx.setPacketHandled(true);
    }
}
