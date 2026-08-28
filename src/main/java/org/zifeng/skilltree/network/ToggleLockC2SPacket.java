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
 * 切换光环锁定（客户端 → 服务端，快捷键触发）：
 * <ul>
 *   <li>未学习：消耗技能点（Config.LOCK_COST，默认 1000）学习并开启</li>
 *   <li>已学习：切换技能开关</li>
 * </ul>
 */
public record ToggleLockC2SPacket() implements CustomPacketPayload {
    public static final Type<ToggleLockC2SPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "toggle_lock"));
    public static final StreamCodec<FriendlyByteBuf, ToggleLockC2SPacket> STREAM_CODEC = StreamCodec.unit(new ToggleLockC2SPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ToggleLockC2SPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.flow().isServerbound() && ctx.player() instanceof ServerPlayer player) {
                PlayerSkillSavedData data = PlayerSkillSavedData.get(player.serverLevel());
                PlayerSkillRecord record = data.getOrCreatePlayer(player.getUUID());
                if (record.getLearnedPoints(Skills.AURA_LOCK) <= 0) {
                    // 未学习：消耗技能点学习并开启
                    double cost = Config.LOCK_COST.get();
                    if (record.getSkillPoints() < cost - 1e-9) {
                        player.sendSystemMessage(Component.translatable(
                                "chat.zifeng_s_custom_skill_tree.no_point_learn",
                                Skills.getDisplayNameComponent(Skills.AURA_LOCK),
                                String.format("%.0f", cost)));
                        return;
                    }
                    if (!record.learnSkill(Skills.AURA_LOCK)) {
                        player.sendSystemMessage(Component.translatable(
                                "chat.zifeng_s_custom_skill_tree.cannot_learn",
                                Skills.getDisplayNameComponent(Skills.AURA_LOCK)));
                        return;
                    }
                    record.setEnabled(Skills.AURA_LOCK, true);
                    data.setDirty();
                    player.sendSystemMessage(Component.translatable(
                            "chat.zifeng_s_custom_skill_tree.lock_learned",
                            Skills.getDisplayNameComponent(Skills.AURA_LOCK),
                            String.format("%.0f", cost)));
                } else {
                    // 已学习：切换开关
                    boolean now = !record.isEnabled(Skills.AURA_LOCK);
                    record.setEnabled(Skills.AURA_LOCK, now);
                    data.setDirty();
                    player.sendSystemMessage(Component.translatable(
                            now
                                    ? "chat.zifeng_s_custom_skill_tree.lock_on"
                                    : "chat.zifeng_s_custom_skill_tree.lock_off",
                            Skills.getDisplayNameComponent(Skills.AURA_LOCK)));
                }
                PacketDistributor.sendToPlayer(player, SkillTreeDataS2CPacket.from(record));
            }
        });
    }
}
