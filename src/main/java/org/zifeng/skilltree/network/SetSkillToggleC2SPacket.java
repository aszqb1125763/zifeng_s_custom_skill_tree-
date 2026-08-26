package org.zifeng.skilltree.network;


import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;
import org.zifeng.skilltree.skill.SkillEffects;
import org.zifeng.skilltree.skill.Skills;

/**
 * 技能开关设置（客户端 → 服务端）：启用/禁用某技能，关闭后该技能加成不生效。
 */
public class SetSkillToggleC2SPacket {
            private final String skillId;
    private final boolean enabled;

    public SetSkillToggleC2SPacket(String skillId, boolean enabled) {
        this.skillId = skillId;
        this.enabled = enabled;
    }
    

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(skillId);
        buf.writeBoolean(enabled);
    }

    public static SetSkillToggleC2SPacket decode(FriendlyByteBuf buf) {
        String skillId = buf.readUtf();
        boolean enabled = buf.readBoolean();
        return new SetSkillToggleC2SPacket(skillId, enabled);
    }
    public static void handle(SetSkillToggleC2SPacket packet, java.util.function.Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
                if (player == null) { return; }
                if (!Skills.ALL_SKILLS.contains(packet.skillId)) {
                    return;
                }
                PlayerSkillSavedData data = PlayerSkillSavedData.get(player.serverLevel());
                PlayerSkillRecord record = data.getOrCreatePlayer(player.getUUID());
                // ⚠️ 未学习该技能时忽略（防止快捷键把未学技能 toggle 置 false，导致 UI 永远显示"关闭"）
                if (record.getLearnedPoints(packet.skillId) <= 0) {
                    // 回发当前真实状态校准客户端缓存（未学的技能恢复默认开启显示）
                    ModNetwork.sendToPlayer(player, SkillTreeDataS2CPacket.from(record));
                    return;
                }
                record.setEnabled(packet.skillId, packet.enabled);
                data.setDirty();
                // 重挂属性使开关生效
                SkillEffects.applyAll(player, record);
                // 开关提示（2026-08-13 需求：所有技能快捷键切换开关时都有提示，带图标）
                String name = Skills.getDisplayName(packet.skillId);
                String icon = switch (packet.skillId) {
                    case Skills.AURA_MAGNET -> "🧲";
                    case Skills.AURA_TIME -> "⏰";
                    case Skills.AURA_WEATHER -> "☀";
                    case Skills.AURA_LOCK -> "🛡";
                    case Skills.AUTO_SMELT -> "🔥";
                    case Skills.MACHINE_AUTO_SMELT -> "🔥";
                    default -> "⚙";
                };
                player.sendSystemMessage(Component.literal(packet.enabled
                        ? icon + " " + name + "已开启"
                        : icon + " " + name + "已关闭"));
                // 回发最新状态
                ModNetwork.sendToPlayer(player,
                        SkillTreeDataS2CPacket.from(record));
        });
        ctx.setPacketHandled(true);
    }
}
