package org.zifeng.skilltree.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.zifeng.skilltree.SkillTreeMod;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;
import org.zifeng.skilltree.event.AuraEvents;
import org.zifeng.skilltree.skill.Skills;

/**
 * 晴空环天气模式切换（客户端 → 服务端）：0=晴天 1=雨天 2=雷暴。
 * 2026-08-27：晴空环保持 1 级，激活后玩家用第二快捷键循环切换天气。
 */
public record WeatherModeC2SPacket(int mode) implements CustomPacketPayload {
    public static final Type<WeatherModeC2SPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "weather_mode"));
    public static final StreamCodec<FriendlyByteBuf, WeatherModeC2SPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, WeatherModeC2SPacket::mode,
            WeatherModeC2SPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WeatherModeC2SPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.flow().isServerbound() && ctx.player() instanceof ServerPlayer player) {
                // 校验：需已学且开启晴空环
                PlayerSkillSavedData data = PlayerSkillSavedData.get(player.serverLevel());
                PlayerSkillRecord record = data.getOrCreatePlayer(player.getUUID());
                if (record.getLearnedPoints(Skills.AURA_WEATHER) <= 0 || !record.isEnabled(Skills.AURA_WEATHER)) {
                    return;
                }
                int mode = Math.max(0, Math.min(2, packet.mode()));
                record.setWeatherMode(mode);
                data.setDirty();
                // 应用到服务器全局天气（最后切换者生效）
                AuraEvents.setPlayerWeatherMode(player, mode);
                // 聊天提示（图标区分天气）
                String[] icons = {"☀", "🌧", "⛈"};
                String modeText = switch (mode) {
                    case 1 -> "雨天";
                    case 2 -> "雷暴";
                    default -> "晴天";
                };
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        icons[mode] + " 晴空环天气：" + modeText));
            }
        });
    }
}
