package org.zifeng.skilltree.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.zifeng.skilltree.SkillTreeMod;

/**
 * 子枫的搬运术·手动触发（客户端 → 服务端，功能触发键按下时发送，2026-09-07）：
 * 空负载——服务端把玩家当前打开的容器物品搬进绑定容器（手动模式）。
 * ⚠️ 该包在「容器 GUI 打开时」也会发送（功能触发键独立于普通快捷键检测）。
 */
public record ContainerHaulC2SPacket() implements CustomPacketPayload {
    public static final Type<ContainerHaulC2SPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "container_haul"));
    public static final StreamCodec<FriendlyByteBuf, ContainerHaulC2SPacket> STREAM_CODEC = StreamCodec.unit(new ContainerHaulC2SPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ContainerHaulC2SPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.flow().isServerbound() && ctx.player() instanceof ServerPlayer player) {
                org.zifeng.skilltree.event.ContainerHaulEvents.triggerManual(player);
            }
        });
    }
}
