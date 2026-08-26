package org.zifeng.skilltree.network;


import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.zifeng.skilltree.Config;
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
public class ToggleLockC2SPacket {
    public ToggleLockC2SPacket() { }
    

    public void encode(FriendlyByteBuf buf) {
    }

    public static ToggleLockC2SPacket decode(FriendlyByteBuf buf) {
        return new ToggleLockC2SPacket();
    }

    public static void handle(ToggleLockC2SPacket packet, java.util.function.Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
                if (player == null) { return; }
                PlayerSkillSavedData data = PlayerSkillSavedData.get(player.serverLevel());
                PlayerSkillRecord record = data.getOrCreatePlayer(player.getUUID());
                if (record.getLearnedPoints(Skills.AURA_LOCK) <= 0) {
                    // 未学习：消耗技能点学习并开启
                    double cost = Config.LOCK_COST.get();
                    String name = Skills.getDisplayName(Skills.AURA_LOCK);
                    if (record.getSkillPoints() < cost - 1e-9) {
                        player.sendSystemMessage(Component.literal("⚠ 技能点不足，学习" + name + "需要 " + String.format("%.0f", cost) + " 技能点"));
                        return;
                    }
                    if (!record.learnSkill(Skills.AURA_LOCK)) {
                        player.sendSystemMessage(Component.literal("⚠ 无法学习" + name));
                        return;
                    }
                    record.setEnabled(Skills.AURA_LOCK, true);
                    data.setDirty();
                    player.sendSystemMessage(Component.literal("🛡 " + name + "已学习并开启（消耗 " + String.format("%.0f", cost) + " 技能点；免疫 TP 与击退）"));
                } else {
                    // 已学习：切换开关
                    boolean now = !record.isEnabled(Skills.AURA_LOCK);
                    record.setEnabled(Skills.AURA_LOCK, now);
                    data.setDirty();
                    player.sendSystemMessage(Component.literal(now
                            ? "🛡 " + Skills.getDisplayName(Skills.AURA_LOCK) + "已开启（免疫 TP 与击退）"
                            : "🛡 " + Skills.getDisplayName(Skills.AURA_LOCK) + "已关闭"));
                }
                ModNetwork.sendToPlayer(player, SkillTreeDataS2CPacket.from(record));
        });
        ctx.setPacketHandled(true);
    }
}
