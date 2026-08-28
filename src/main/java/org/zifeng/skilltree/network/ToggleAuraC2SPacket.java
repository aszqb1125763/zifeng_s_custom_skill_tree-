package org.zifeng.skilltree.network;


import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;
import org.zifeng.skilltree.skill.Skills;

import java.util.function.Supplier;

/**
 * 切换杀戮光环·伤害开关（客户端 → 服务端）：
 * ⚠️ 2026-08-15 需求：开关分离——本包只控制【伤害光环】，
 * 速度光环用独立快捷键/技能树开关控制（互不影响）。
 * 原 K 键"总开关"逻辑已废弃（ModKeyBindings 仅保留打开技能树）。
 */
public class ToggleAuraC2SPacket {
    public ToggleAuraC2SPacket() {
    }

    public void encode(FriendlyByteBuf buf) {
        // 无字段
    }

    public static ToggleAuraC2SPacket decode(FriendlyByteBuf buf) {
        return new ToggleAuraC2SPacket();
    }

    public static void handle(ToggleAuraC2SPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }
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
            player.sendSystemMessage(Component.translatable(
                    now ? "chat.zifeng_s_custom_skill_tree.aura_on" : "chat.zifeng_s_custom_skill_tree.aura_off",
                    now ? "⚔" : "×", Skills.getDisplayNameComponent(Skills.AURA_DAMAGE)));
            ModNetwork.sendToPlayer(player, SkillTreeDataS2CPacket.from(record));
        });
        ctx.setPacketHandled(true);
    }
}
