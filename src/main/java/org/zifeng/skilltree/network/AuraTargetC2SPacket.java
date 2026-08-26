package org.zifeng.skilltree.network;
import net.minecraft.network.chat.Component;


import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;
import org.zifeng.skilltree.skill.Skills;

/**
 * 杀戮光环目标模式切换（客户端 → 服务端）：0=敌对 1=友好 2=所有。
 * 2026-08-13 需求：每个光环独立目标模式（skillId 指定）。
 */
public class AuraTargetC2SPacket {
            private final String skillId;
    private final int mode;

    public AuraTargetC2SPacket(String skillId, int mode) {
        this.skillId = skillId;
        this.mode = mode;
    }
    

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(skillId);
        buf.writeVarInt(mode);
    }

    public static AuraTargetC2SPacket decode(FriendlyByteBuf buf) {
        String skillId = buf.readUtf();
        int mode = buf.readVarInt();
        return new AuraTargetC2SPacket(skillId, mode);
    }
    public static void handle(AuraTargetC2SPacket packet, java.util.function.Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
                if (player == null) { return; }
                if (!Skills.AURA_SKILLS.contains(packet.skillId)) {
                    return;
                }
                PlayerSkillSavedData data = PlayerSkillSavedData.get(player.serverLevel());
                PlayerSkillRecord record = data.getOrCreatePlayer(player.getUUID());
                record.setAuraTargetMode(packet.skillId, packet.mode);
                data.setDirty();
                // 聊天提示（图标区分模式，与目标类型强相关：敌对💀 / 友好🐑 / 所有🌍）
                String[] icons = {"💀", "🐑", "🌍"};
                String modeText = switch (packet.mode) {
                    case 1 -> "友好生物";
                    case 2 -> "所有生物";
                    default -> "敌对生物";
                };
                String icon = icons[Math.max(0, Math.min(2, packet.mode))];
                player.sendSystemMessage(Component.literal(
                        icon + " " + Skills.getDisplayName(packet.skillId) + "目标：" + modeText));
                ModNetwork.sendToPlayer(player, SkillTreeDataS2CPacket.from(record));
        });
        ctx.setPacketHandled(true);
    }
}
