package org.zifeng.skilltree.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.zifeng.skilltree.SkillTreeMod;
import org.zifeng.skilltree.blockentity.StarEnergyConverterBlockEntity;
import org.zifeng.skilltree.menu.StarEnergyConverterMenu;

/**
 * 更新星能转换机设置（客户端 → 服务端）。
 * 不携带方块位置：服务端直接取玩家当前打开的转换机菜单，对应该菜单绑定的机器。
 * <ul>
 *   <li>rate &lt;= 0：关闭输入；rate = Long.MAX_VALUE：不限速；其他：每 tick 最大接收 FE</li>
 *   <li>redstoneControlled：是否开启红石控制（开启后仅在有红石信号时接收能量）</li>
 * </ul>
 * 需校验打开菜单的玩家与方块所有者一致，防止他人篡改。
 */
public record SetConverterRateC2SPacket(long rate, boolean redstoneControlled) implements CustomPacketPayload {
    public static final Type<SetConverterRateC2SPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "set_converter_rate"));
    public static final StreamCodec<FriendlyByteBuf, SetConverterRateC2SPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, SetConverterRateC2SPacket::rate,
            ByteBufCodecs.BOOL, SetConverterRateC2SPacket::redstoneControlled,
            SetConverterRateC2SPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetConverterRateC2SPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.flow().isServerbound() && ctx.player() instanceof ServerPlayer player) {
                // 取玩家当前打开的转换机菜单，定位对应的机器
                if (player.containerMenu instanceof StarEnergyConverterMenu menu
                        && menu.getBlockPos() != null
                        && player.level().getBlockEntity(menu.getBlockPos()) instanceof StarEnergyConverterBlockEntity be) {
                    // 权限校验：仅放置者（或创造模式）可修改
                    var owner = be.getOwnerUUID();
                    if (owner != null && !owner.equals(player.getUUID()) && !player.getAbilities().instabuild) {
                        return;
                    }
                    be.setInputRatePerTick(packet.rate());
                    be.setRedstoneControlled(packet.redstoneControlled());
                }
            }
        });
    }
}
