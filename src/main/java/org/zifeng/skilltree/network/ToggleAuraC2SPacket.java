package org.zifeng.skilltree.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.zifeng.skilltree.SkillTreeMod;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;
import org.zifeng.skilltree.skill.Skills;

/**
 * 切换杀戮光环·伤害开关（客户端 → 服务端）：
 * ⚠️ 2026-08-15 需求：开关分离——本包只控制【伤害光环】，
 * 速度光环用独立快捷键/技能树开关控制（互不影响）。
 * 原 K 键"总开关"逻辑已废弃（ModKeyBindings 仅保留打开技能树）。
 */
public record ToggleAuraC2SPacket() implements CustomPacketPayload {
    public static final Type<ToggleAuraC2SPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "toggle_aura"));
    public static final StreamCodec<FriendlyByteBuf, ToggleAuraC2SPacket> STREAM_CODEC = StreamCodec.unit(new ToggleAuraC2SPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ToggleAuraC2SPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.flow().isServerbound() && ctx.player() instanceof ServerPlayer player) {
                PlayerSkillSavedData data = PlayerSkillSavedData.get(player.serverLevel());
                PlayerSkillRecord record = data.getOrCreatePlayer(player.getUUID());
                // 未学伤害光环 → 忽略
                if (record.getLearnedPoints(Skills.AURA_DAMAGE) <= 0) {
                    return;
                }
                // 只切换伤害光环（速度光环不受影响）
                boolean now = !record.isEnabled(Skills.AURA_DAMAGE);
                record.setEnabled(Skills.AURA_DAMAGE, now);
                data.setDirty();
                // 聊天提示
                String name = Skills.getDisplayName(Skills.AURA_DAMAGE);
                player.sendSystemMessage(Component.literal(now
                        ? "⚔ " + name + "已开启"
                        : "✖ " + name + "已关闭"));
                PacketDistributor.sendToPlayer(player,
                        SkillTreeDataS2CPacket.from(record));
            }
        });
    }
}
