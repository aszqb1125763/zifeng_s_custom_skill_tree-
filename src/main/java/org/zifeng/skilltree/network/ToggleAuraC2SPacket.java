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
 * 切换杀戮光环（伤害+速度）开关（客户端 → 服务端，快捷键 K 触发）：
 * 不再有"光环总开关"——K 键只控制杀戮光环（伤害/速度）的开启与关闭，
 * 其他光环（治愈/磁力/时环/晴空/锁定）各自独立（技能树右键或独立快捷键）。
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
                // 未学任何杀戮光环 → 忽略（防止未学状态误切换导致 UI 永远显示关闭）
                boolean anyLearned = record.getLearnedPoints(Skills.AURA_DAMAGE) > 0
                        || record.getLearnedPoints(Skills.AURA_SPEED) > 0;
                if (!anyLearned) {
                    return;
                }
                // 判断杀戮光环当前状态（伤害/速度任一开启即视为开）
                boolean now = !(record.isEnabled(Skills.AURA_DAMAGE) || record.isEnabled(Skills.AURA_SPEED));
                // 只切换已学技能（未学的不动）
                if (record.getLearnedPoints(Skills.AURA_DAMAGE) > 0) {
                    record.setEnabled(Skills.AURA_DAMAGE, now);
                }
                if (record.getLearnedPoints(Skills.AURA_SPEED) > 0) {
                    record.setEnabled(Skills.AURA_SPEED, now);
                }
                data.setDirty();
                // 聊天提示
                player.sendSystemMessage(Component.literal(now
                        ? "⚔ 杀戮光环已开启"
                        : "✖ 杀戮光环已关闭"));
                PacketDistributor.sendToPlayer(player,
                        SkillTreeDataS2CPacket.from(record));
            }
        });
    }
}
