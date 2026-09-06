package org.zifeng.skilltree.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.zifeng.skilltree.SkillTreeMod;

/**
 * 闪现请求（客户端 → 服务端，技能第一快捷键触发，2026-09-06）：
 * 空负载——服务端按玩家当前视线方向执行一次闪现传送（冷却 2 tick 在服务端控制）。
 */
public record BlinkC2SPacket() implements CustomPacketPayload {
    public static final Type<BlinkC2SPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "blink"));
    public static final StreamCodec<FriendlyByteBuf, BlinkC2SPacket> STREAM_CODEC = StreamCodec.unit(new BlinkC2SPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BlinkC2SPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.flow().isServerbound() && ctx.player() instanceof ServerPlayer player) {
                org.zifeng.skilltree.event.SkillEvents.blink(player);
            }
        });
    }
}
