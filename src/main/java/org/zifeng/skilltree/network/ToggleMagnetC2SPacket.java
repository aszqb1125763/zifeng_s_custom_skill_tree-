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
 * 切换磁力光环（客户端 → 服务端，快捷键 H 触发）：
 * <ul>
 *   <li>未学习：消耗技能点（Config.MAGNET_COST，默认 10）学习并开启</li>
 *   <li>已学习：切换技能开关（与杀戮光环技能开关一致）</li>
 * </ul>
 */
public class ToggleMagnetC2SPacket {
    public ToggleMagnetC2SPacket() { }
    

    public void encode(FriendlyByteBuf buf) {
    }

    public static ToggleMagnetC2SPacket decode(FriendlyByteBuf buf) {
        return new ToggleMagnetC2SPacket();
    }

    public static void handle(ToggleMagnetC2SPacket packet, java.util.function.Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
                if (player == null) { return; }
                PlayerSkillSavedData data = PlayerSkillSavedData.get(player.serverLevel());
                PlayerSkillRecord record = data.getOrCreatePlayer(player.getUUID());
                if (record.getLearnedPoints(Skills.AURA_MAGNET) <= 0) {
                    // 未学习：消耗技能点学习并开启
                    double cost = Config.MAGNET_COST.get();
                    String name = Skills.getDisplayName(Skills.AURA_MAGNET);
                    if (record.getSkillPoints() < cost - 1e-9) {
                        player.sendSystemMessage(Component.literal("⚠ 技能点不足，学习" + name + "需要 " + String.format("%.0f", cost) + " 技能点"));
                        return;
                    }
                    if (!record.learnSkill(Skills.AURA_MAGNET)) {
                        player.sendSystemMessage(Component.literal("⚠ 无法学习" + name));
                        return;
                    }
                    record.setEnabled(Skills.AURA_MAGNET, true);
                    data.setDirty();
                    player.sendSystemMessage(Component.literal("🧲 " + name + "已学习并开启（消耗 " + String.format("%.0f", cost) + " 技能点；潜行时暂停吸取）"));
                } else {
                    // 已学习：切换开关
                    boolean now = !record.isEnabled(Skills.AURA_MAGNET);
                    record.setEnabled(Skills.AURA_MAGNET, now);
                    data.setDirty();
                    player.sendSystemMessage(Component.literal(now
                            ? "🧲 " + Skills.getDisplayName(Skills.AURA_MAGNET) + "已开启（潜行时暂停吸取）"
                            : "🧲 " + Skills.getDisplayName(Skills.AURA_MAGNET) + "已关闭"));
                }
                ModNetwork.sendToPlayer(player, SkillTreeDataS2CPacket.from(record));
        });
        ctx.setPacketHandled(true);
    }
}
