package org.zifeng.skilltree.network;
import net.minecraft.network.chat.Component;


import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;
import org.zifeng.skilltree.skill.SkillEffects;
import org.zifeng.skilltree.skill.Skills;

/**
 * 设置技能生效等级（客户端 → 服务端）：独立控制每个技能启用几级加成（<= 已学等级）。
 */
public class SetSkillLevelC2SPacket {
            private final String skillId;
    private final int level;

    public SetSkillLevelC2SPacket(String skillId, int level) {
        this.skillId = skillId;
        this.level = level;
    }
    

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(skillId);
        buf.writeVarInt(level);
    }

    public static SetSkillLevelC2SPacket decode(FriendlyByteBuf buf) {
        String skillId = buf.readUtf();
        int level = buf.readVarInt();
        return new SetSkillLevelC2SPacket(skillId, level);
    }
    public static void handle(SetSkillLevelC2SPacket packet, java.util.function.Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
                if (player == null) { return; }
                if (!Skills.ALL_SKILLS.contains(packet.skillId)) {
                    return;
                }
                PlayerSkillSavedData data = PlayerSkillSavedData.get(player.serverLevel());
                PlayerSkillRecord record = data.getOrCreatePlayer(player.getUUID());
                record.setActiveLevel(packet.skillId, packet.level);
                data.setDirty();
                SkillEffects.applyAll(player, record);
                // 聊天提示（2026-08-13 需求：等级循环快捷键调整时显示当前生效等级）
                int learned = record.getLearnedPoints(packet.skillId);
                int active = packet.level;
                var msgComp = Component.translatable(
                        "chat.zifeng_s_custom_skill_tree.level_set",
                        Skills.getDisplayNameComponent(packet.skillId), active, learned);
                // 无限回路：附加当前频道倍率提示（2026-08-27：0=默认 1=X2 2=X3 3=X4 4=无限）
                if (Skills.AE_INFINITE_CHANNEL.equals(packet.skillId)) {
                    String modeKey = switch (active) {
                        case 1 -> "ae_x2";
                        case 2 -> "ae_x3";
                        case 3 -> "ae_x4";
                        case 4 -> "ae_infinite";
                        default -> "ae_default";
                    };
                    msgComp = msgComp.copy().append(" → ")
                            .append(Component.translatable("ui.zifeng_s_custom_skill_tree." + modeKey));
                }
                player.sendSystemMessage(msgComp);
                ModNetwork.sendToPlayer(player,
                        SkillTreeDataS2CPacket.from(record));
        });
        ctx.setPacketHandled(true);
    }
}
