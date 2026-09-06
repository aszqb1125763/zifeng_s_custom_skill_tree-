package org.zifeng.skilltree.network;


import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * 闪现请求（客户端 → 服务端，技能第一快捷键触发，2026-09-06）：
 * 空负载——服务端按玩家当前视线方向执行一次闪现传送（冷却 2 tick 在服务端控制）。
 */
public class BlinkC2SPacket {
    public BlinkC2SPacket() {
    }

    public void encode(FriendlyByteBuf buf) {
    }

    public static BlinkC2SPacket decode(FriendlyByteBuf buf) {
        return new BlinkC2SPacket();
    }

    public static void handle(BlinkC2SPacket packet, java.util.function.Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }
            org.zifeng.skilltree.event.SkillEvents.blink(player);
        });
        ctx.setPacketHandled(true);
    }
}
