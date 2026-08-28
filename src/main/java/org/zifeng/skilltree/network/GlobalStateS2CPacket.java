package org.zifeng.skilltree.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.zifeng.skilltree.client.ClientGlobalState;

import java.util.function.Supplier;

/**
 * 服务器全局状态同步（服务端 → 客户端，2026-08-28 增量版）：全局技能（寰宇法则）的服务器当前状态提示。
 * <p>增量设计：每个字段 -1 = 无变化/未订阅（客户端保持当前值），>=0 = 更新该值；
 * weatherMode 特殊：-2 = 天气未锁定（自然循环）→ 客户端清缓存（tooltip 显示未锁定）。
 * 由主系统 GlobalStateSync 按订阅位图 + 快照去重生成，只发变化的字段。
 * 字段：aeChannelMode（0=默认 1=X2 2=X3 3=X4 4=无限）、weatherMode（0=晴 1=雨 2=雷暴）、timeLocked（0=未锁 1=锁定）。
 * <p>性能：事件驱动 + 增量，平时零流量；时之环/晴空环锁定状态也走推送（统一架构）。
 */
public class GlobalStateS2CPacket {
    private final int aeChannelMode;
    private final int weatherMode;
    private final int timeLocked;

    public GlobalStateS2CPacket(int aeChannelMode, int weatherMode, int timeLocked) {
        this.aeChannelMode = aeChannelMode;
        this.weatherMode = weatherMode;
        this.timeLocked = timeLocked;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(aeChannelMode);
        buf.writeVarInt(weatherMode);
        buf.writeVarInt(timeLocked);
    }

    public static GlobalStateS2CPacket decode(FriendlyByteBuf buf) {
        return new GlobalStateS2CPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(GlobalStateS2CPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                // 增量合并：-1 = 无变化，保持当前值；weatherMode=-2 = 天气未锁定（清缓存）
                if (packet.aeChannelMode >= 0) {
                    ClientGlobalState.setAeChannelMode(packet.aeChannelMode);
                }
                if (packet.weatherMode == -2) {
                    ClientGlobalState.setWeatherMode(-1); // 天气未锁定：清缓存，tooltip 显示自然循环
                } else if (packet.weatherMode >= 0) {
                    ClientGlobalState.setWeatherMode(packet.weatherMode);
                }
                if (packet.timeLocked >= 0) {
                    ClientGlobalState.setTimeLocked(packet.timeLocked == 1);
                }
            }
        });
        ctx.setPacketHandled(true);
    }
}
