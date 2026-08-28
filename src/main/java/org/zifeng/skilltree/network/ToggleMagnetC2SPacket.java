package org.zifeng.skilltree.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.zifeng.skilltree.Config;
import org.zifeng.skilltree.SkillTreeMod;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;
import org.zifeng.skilltree.skill.Skills;

/**
 * 切换磁力光环（客户端 → 服务端，快捷键 H 触发）：
 * <ul>
 *   <li>未学习：消耗技能点（Config.MAGNET_COST，默认 10）学习并开启</li>
 *   <li>已学习：切换技能开关（与杀戮光环技能开关一致）</li>
 * </ul>
 */
public record ToggleMagnetC2SPacket() implements CustomPacketPayload {
    public static final Type<ToggleMagnetC2SPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "toggle_magnet"));
    public static final StreamCodec<FriendlyByteBuf, ToggleMagnetC2SPacket> STREAM_CODEC = StreamCodec.unit(new ToggleMagnetC2SPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ToggleMagnetC2SPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.flow().isServerbound() && ctx.player() instanceof ServerPlayer player) {
                PlayerSkillSavedData data = PlayerSkillSavedData.get(player.serverLevel());
                PlayerSkillRecord record = data.getOrCreatePlayer(player.getUUID());
                if (record.getLearnedPoints(Skills.AURA_MAGNET) <= 0) {
                    // 未学习：消耗技能点学习并开启
                    double cost = Config.MAGNET_COST.get();
                    if (record.getSkillPoints() < cost - 1e-9) {
                        player.sendSystemMessage(Component.translatable(
                                "chat.zifeng_s_custom_skill_tree.no_point_learn",
                                Skills.getDisplayNameComponent(Skills.AURA_MAGNET),
                                String.format("%.0f", cost)));
                        return;
                    }
                    if (!record.learnSkill(Skills.AURA_MAGNET)) {
                        player.sendSystemMessage(Component.translatable(
                                "chat.zifeng_s_custom_skill_tree.cannot_learn",
                                Skills.getDisplayNameComponent(Skills.AURA_MAGNET)));
                        return;
                    }
                    record.setEnabled(Skills.AURA_MAGNET, true);
                    data.setDirty();
                    player.sendSystemMessage(Component.translatable(
                            "chat.zifeng_s_custom_skill_tree.magnet_learned",
                            Skills.getDisplayNameComponent(Skills.AURA_MAGNET),
                            String.format("%.0f", cost)));
                } else {
                    // 已学习：切换开关
                    boolean now = !record.isEnabled(Skills.AURA_MAGNET);
                    record.setEnabled(Skills.AURA_MAGNET, now);
                    data.setDirty();
                    player.sendSystemMessage(Component.translatable(
                            now
                                    ? "chat.zifeng_s_custom_skill_tree.magnet_on"
                                    : "chat.zifeng_s_custom_skill_tree.magnet_off",
                            Skills.getDisplayNameComponent(Skills.AURA_MAGNET)));
                }
                PacketDistributor.sendToPlayer(player, SkillTreeDataS2CPacket.from(record));
            }
        });
    }
}
