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
 * 技能点转换机：GUI 无限制输入开关（客户端 → 服务端）。
 * 双保险定位机器：①服务端从玩家当前打开的容器菜单拿引用；②客户端传 BlockPos 兜底。
 * 开启后忽略输入速率限制（无限制输入 FE），仍受能量缓冲上限约束。
 */
public record ConverterUnlimitedC2SPacket(BlockPos pos, boolean unlimited) implements CustomPacketPayload {
    public static final Type<ConverterUnlimitedC2SPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "converter_unlimited"));
    public static final StreamCodec<FriendlyByteBuf, ConverterUnlimitedC2SPacket> STREAM_CODEC = StreamCodec.composite(
            net.minecraft.core.BlockPos.STREAM_CODEC, ConverterUnlimitedC2SPacket::pos,
            ByteBufCodecs.BOOL, ConverterUnlimitedC2SPacket::unlimited,
            ConverterUnlimitedC2SPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ConverterUnlimitedC2SPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.flow().isServerbound() && ctx.player() instanceof ServerPlayer player) {
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
                    converter.setUnlimitedInput(packet.unlimited());
                }
            }
        });
    }
}
