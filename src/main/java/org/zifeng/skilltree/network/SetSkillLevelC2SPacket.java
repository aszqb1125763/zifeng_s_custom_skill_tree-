package org.zifeng.skilltree.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.zifeng.skilltree.SkillTreeMod;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;
import org.zifeng.skilltree.skill.SkillEffects;
import org.zifeng.skilltree.skill.Skills;

/**
 * 设置技能生效等级（客户端 → 服务端）：独立控制每个技能启用几级加成（<= 已学等级）。
 */
public record SetSkillLevelC2SPacket(String skillId, int level) implements CustomPacketPayload {
    public static final Type<SetSkillLevelC2SPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "set_skill_level"));
    public static final StreamCodec<FriendlyByteBuf, SetSkillLevelC2SPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SetSkillLevelC2SPacket::skillId,
            ByteBufCodecs.VAR_INT, SetSkillLevelC2SPacket::level,
            SetSkillLevelC2SPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetSkillLevelC2SPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.flow().isServerbound() && ctx.player() instanceof ServerPlayer player) {
                if (!Skills.ALL_SKILLS.contains(packet.skillId())) {
                    return;
                }
                PlayerSkillSavedData data = PlayerSkillSavedData.get(player.serverLevel());
                PlayerSkillRecord record = data.getOrCreatePlayer(player.getUUID());
                record.setActiveLevel(packet.skillId(), packet.level());
                data.setDirty();
                SkillEffects.applyAll(player, record);
                // 聊天提示（2026-08-13 需求：等级循环快捷键调整时显示当前生效等级）
                int learned = record.getLearnedPoints(packet.skillId());
                int active = packet.level();
                var msgComp = net.minecraft.network.chat.Component.translatable(
                        "chat.zifeng_s_custom_skill_tree.level_set",
                        Skills.getDisplayNameComponent(packet.skillId()), active, learned);
                // 无限回路：附加当前频道倍率提示（2026-08-27：0=默认 1=X2 2=X3 3=X4 4=无限）
                if (Skills.AE_INFINITE_CHANNEL.equals(packet.skillId())) {
                    String modeKey = switch (active) {
                        case 1 -> "ae_x2";
                        case 2 -> "ae_x3";
                        case 3 -> "ae_x4";
                        case 4 -> "ae_infinite";
                        default -> "ae_default";
                    };
                    msgComp = msgComp.copy().append(" → ")
                            .append(net.minecraft.network.chat.Component.translatable("ui.zifeng_s_custom_skill_tree." + modeKey));
                }
                player.sendSystemMessage(msgComp);
                PacketDistributor.sendToPlayer(player,
                        SkillTreeDataS2CPacket.from(record));
            }
        });
    }
}
