package org.zifeng.skilltree.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.zifeng.skilltree.client.SkillPointHudRenderer;

import java.util.function.Supplier;

/**
 * 技能点增量推送（服务端 → 客户端，2026-08-28 子系统专用）：
 * <p>替代原来每 10 tick 全量回发 SkillTreeDataS2CPacket（含所有技能 Map），
 * 只发增量 delta + 最新总额 total（2 个 double = 16 字节）。
 * 客户端 HUD 技能点实时刷新；技能树数据在打开时才全量拉取。
 */
public class SkillPointDeltaS2CPacket {
    private final double delta;
    private final double total;

    public SkillPointDeltaS2CPacket(double delta, double total) {
        this.delta = delta;
        this.total = total;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(delta);
        buf.writeDouble(total);
    }

    public static SkillPointDeltaS2CPacket decode(FriendlyByteBuf buf) {
        return new SkillPointDeltaS2CPacket(buf.readDouble(), buf.readDouble());
    }

    public static void handle(SkillPointDeltaS2CPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                // HUD 技能点常驻显示更新（updateTotal 内部按 delta 正负显示增减行）
                SkillPointHudRenderer.updateTotal(packet.total);
            }
        });
        ctx.setPacketHandled(true);
    }
}
