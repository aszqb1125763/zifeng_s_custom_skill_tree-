package org.zifeng.skilltree.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.zifeng.skilltree.SkillTreeMod;
import org.zifeng.skilltree.client.SkillPointHudRenderer;

/**
 * 技能点增量推送（服务端 → 客户端，2026-08-28 子系统专用）：
 * <p>替代原来每 10 tick 全量回发 SkillTreeDataS2CPacket（含所有技能 Map），
 * 只发增量 delta + 最新总额 total（2 个 double = 16 字节）。
 * 客户端 HUD 技能点实时刷新；技能树数据在打开时才全量拉取。
 */
public record SkillPointDeltaS2CPacket(double delta, double total) implements CustomPacketPayload {
    public static final Type<SkillPointDeltaS2CPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "skill_point_delta"));
    public static final StreamCodec<FriendlyByteBuf, SkillPointDeltaS2CPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, SkillPointDeltaS2CPacket::delta,
            ByteBufCodecs.DOUBLE, SkillPointDeltaS2CPacket::total,
            SkillPointDeltaS2CPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SkillPointDeltaS2CPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.flow().isClientbound()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    // HUD 技能点常驻显示更新（updateTotal 内部按 delta 正负显示增减行）
                    SkillPointHudRenderer.updateTotal(packet.total());
                }
            }
        });
    }
}
