package org.zifeng.skilltree.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.zifeng.skilltree.client.ReviveHudRenderer;

import java.util.function.Supplier;

/**
 * 凤凰涅槃冷却状态（服务端 → 客户端，每秒同步一次）：
 * <ul>
 *   <li>learned = 是否已学且启用（false 时 HUD 隐藏图标）</li>
 *   <li>remainingTicks = 剩余冷却 tick（0 = 冷却就绪）</li>
 * </ul>
 */
public class ReviveCooldownS2CPacket {
    private final boolean learned;
    private final int remainingTicks;

    public ReviveCooldownS2CPacket(boolean learned, int remainingTicks) {
        this.learned = learned;
        this.remainingTicks = remainingTicks;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(learned);
        buf.writeVarInt(remainingTicks);
    }

    public static ReviveCooldownS2CPacket decode(FriendlyByteBuf buf) {
        return new ReviveCooldownS2CPacket(buf.readBoolean(), buf.readVarInt());
    }

    public static void handle(ReviveCooldownS2CPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                ReviveHudRenderer.setCooldown(packet.learned, packet.remainingTicks);
            }
        });
        ctx.setPacketHandled(true);
    }
}
