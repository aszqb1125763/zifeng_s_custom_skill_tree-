package org.zifeng.skilltree.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
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
 * 技能开关设置（客户端 → 服务端）：启用/禁用某技能，关闭后该技能加成不生效。
 */
public record SetSkillToggleC2SPacket(String skillId, boolean enabled) implements CustomPacketPayload {
    public static final Type<SetSkillToggleC2SPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "set_skill_toggle"));
    public static final StreamCodec<FriendlyByteBuf, SetSkillToggleC2SPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SetSkillToggleC2SPacket::skillId,
            ByteBufCodecs.BOOL, SetSkillToggleC2SPacket::enabled,
            SetSkillToggleC2SPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetSkillToggleC2SPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.flow().isServerbound() && ctx.player() instanceof ServerPlayer player) {
                if (!Skills.ALL_SKILLS.contains(packet.skillId())) {
                    return;
                }
                PlayerSkillSavedData data = PlayerSkillSavedData.get(player.serverLevel());
                PlayerSkillRecord record = data.getOrCreatePlayer(player.getUUID());
                // ⚠️ 未学习该技能时忽略（防止快捷键把未学技能 toggle 置 false，导致 UI 永远显示"关闭"）
                if (record.getLearnedPoints(packet.skillId()) <= 0) {
                    // 回发当前真实状态校准客户端缓存（未学的技能恢复默认开启显示）
                    PacketDistributor.sendToPlayer(player, SkillTreeDataS2CPacket.from(record));
                    return;
                }
                record.setEnabled(packet.skillId(), packet.enabled());
                data.setDirty();
                // 重挂属性使开关生效
                SkillEffects.applyAll(player, record);
                // 开关提示（2026-08-13 需求：所有技能快捷键切换开关时都有提示，带图标）
                String icon = switch (packet.skillId()) {
                    case Skills.AURA_MAGNET -> "🧲";
                    case Skills.AURA_TIME -> "⏰";
                    case Skills.AURA_WEATHER -> "☀";
                    case Skills.AURA_LOCK -> "🛡";
                    case Skills.AUTO_SMELT -> "🔥";
                    case Skills.MACHINE_AUTO_SMELT -> "🔥";
                    default -> "⚙";
                };
                player.sendSystemMessage(Component.translatable(
                        packet.enabled()
                                ? "chat.zifeng_s_custom_skill_tree.skill_on"
                                : "chat.zifeng_s_custom_skill_tree.skill_off",
                        icon, Skills.getDisplayNameComponent(packet.skillId())));
                // 回发最新状态
                PacketDistributor.sendToPlayer(player,
                        SkillTreeDataS2CPacket.from(record));
            }
        });
    }
}
