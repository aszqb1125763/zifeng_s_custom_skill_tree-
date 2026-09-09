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
import org.zifeng.skilltree.skill.Skills;

/**
 * 技能模式切换（客户端 → 服务端）：
 * 杀戮光环三兄弟（伤害/速度/治愈）= 敌我目标模式 0=敌对 1=友好 2=所有（isAuraTargetSkill）；
 * 子枫的搬运术（CONTAINER_HAUL）= 搬运模式 0=自动 1=手动（2026-09-07）。
 * ⚠️ 2026-09-07 修复：其余 AURA 技能（磁力/锁定/强化/虚空/挪移/汲灵等）没有模式概念，
 *    即使收到切换也忽略（原来统一按"生物目标"处理导致提示错乱）。
 */
public record AuraTargetC2SPacket(String skillId, int mode) implements CustomPacketPayload {
    public static final Type<AuraTargetC2SPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "aura_target"));
    public static final StreamCodec<FriendlyByteBuf, AuraTargetC2SPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, AuraTargetC2SPacket::skillId,
            ByteBufCodecs.VAR_INT, AuraTargetC2SPacket::mode,
            AuraTargetC2SPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AuraTargetC2SPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.flow().isServerbound() && ctx.player() instanceof ServerPlayer player) {
                String skillId = packet.skillId();
                // 仅两类技能有模式：杀戮光环敌我目标三兄弟 / 搬运术（自动⇄手动）
                boolean haul = Skills.isContainerHaul(skillId);
                boolean creatureTarget = Skills.isAuraTargetSkill(skillId);
                if (!haul && !creatureTarget) {
                    return; // 其他技能无模式概念，忽略（不发提示不存档）
                }
                PlayerSkillSavedData data = PlayerSkillSavedData.get(player.serverLevel());
                PlayerSkillRecord record = data.getOrCreatePlayer(player.getUUID());
                record.setAuraTargetMode(skillId, packet.mode()); // 内部按技能 clamp（搬运 0-1 / 目标 0-2）
                data.setDirty();
                if (haul) {
                    // 搬运术：自动/手动（图标 📦，文案区分于生物目标）
                    String modeKey = packet.mode() == 1 ? "haul_mode_manual" : "haul_mode_auto";
                    player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                            "chat.zifeng_s_custom_skill_tree.haul_mode_switch", "📦",
                            Skills.getDisplayNameComponent(skillId),
                            net.minecraft.network.chat.Component.translatable("ui.zifeng_s_custom_skill_tree." + modeKey)));
                } else {
                    // 杀戮光环敌我目标（图标区分：敌对💀 / 友好🐑 / 所有🌍）
                    String[] icons = {"💀", "🐑", "🌍"};
                    String modeKey = switch (packet.mode()) {
                        case 1 -> "mode_friendly";
                        case 2 -> "mode_all";
                        default -> "mode_hostile";
                    };
                    String icon = icons[Math.max(0, Math.min(2, packet.mode()))];
                    player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                            "chat.zifeng_s_custom_skill_tree.aura_target", icon,
                            Skills.getDisplayNameComponent(skillId),
                            net.minecraft.network.chat.Component.translatable("ui.zifeng_s_custom_skill_tree." + modeKey)));
                }
                org.zifeng.skilltree.network.ModNetwork.sendToPlayer(player,
                        SkillTreeDataS2CPacket.from(record));
            }
        });
    }
}
