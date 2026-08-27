package org.zifeng.skilltree.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.zifeng.skilltree.client.ClientGlobalState;

import java.util.function.Supplier;

/**
 * 服务器全局状态同步（服务端 → 客户端，2026-08-27）：全局技能（寰宇法则）的服务器当前状态提示。
 * 目前携带 AE2 频道模式码（0=默认 1=X2 2=X3 3=X4 4=无限）；时之环/晴空环状态客户端本地读 gamerule 零开销。
 * <p>性能：仅 1 字节小包；只在模式变化时发送（服务端按玩家上次收到码对比）。
 */
public class GlobalStateS2CPacket {
    private final int aeChannelMode;

    public GlobalStateS2CPacket(int aeChannelMode) {
        this.aeChannelMode = aeChannelMode;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(aeChannelMode);
    }

    public static GlobalStateS2CPacket decode(FriendlyByteBuf buf) {
        return new GlobalStateS2CPacket(buf.readVarInt());
    }

    public static void handle(GlobalStateS2CPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                ClientGlobalState.setAeChannelMode(packet.aeChannelMode);
            }
        });
        ctx.setPacketHandled(true);
    }
}
