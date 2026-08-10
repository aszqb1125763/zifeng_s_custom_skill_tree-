package org.zifeng.skilltree.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.zifeng.skilltree.SkillTreeMod;
import org.zifeng.skilltree.blockentity.SkillPointConverterBlockEntity;
import org.zifeng.skilltree.menu.SkillPointConverterMenu;

/**
 * 技能点转换机：GUI 设置输入速率（客户端 → 服务端，每机器独立，NBT 持久化）。
 * 双保险定位机器：①服务端从玩家当前打开的容器菜单拿引用；②客户端传 BlockPos 兜底。
 */
public record ConverterRateC2SPacket(BlockPos pos, long rate) implements CustomPacketPayload {
    public static final Type<ConverterRateC2SPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "converter_rate"));
    public static final StreamCodec<FriendlyByteBuf, ConverterRateC2SPacket> STREAM_CODEC = StreamCodec.composite(
            net.minecraft.core.BlockPos.STREAM_CODEC, ConverterRateC2SPacket::pos,
            ByteBufCodecs.VAR_LONG, ConverterRateC2SPacket::rate,
            ConverterRateC2SPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ConverterRateC2SPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.flow().isServerbound() && ctx.player() instanceof ServerPlayer player) {
                if (packet.rate() < 1 || packet.rate() > 1_000_000_000_000L) {
                    return; // 非法值拒绝
                }
                SkillPointConverterBlockEntity converter = null;
                // ① 从玩家当前打开的容器菜单拿机器引用（最可靠）
                if (player.containerMenu instanceof SkillPointConverterMenu menu) {
                    converter = menu.getBlockEntity();
                }
                // ② BlockPos 兜底（限距离校验，防远程改他人机器）
                if (converter == null) {
                    if (player.distanceToSqr(packet.pos().getCenter()) <= 25 * 25
                            && player.level().getBlockEntity(packet.pos()) instanceof SkillPointConverterBlockEntity be) {
                        converter = be;
                    }
                }
                if (converter != null) {
                    converter.setInputRate(packet.rate());
                }
            }
        });
    }
}
