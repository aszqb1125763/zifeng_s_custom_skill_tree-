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
 * 寻宝大师·「已开容器过滤」开关（客户端 → 服务端，★ 2026-10-11，1.20.1）。
 *
 * <p>语义：开启时，玩家本次会话内<b>已打开过</b>的战利品容器不再显示金色发光轮廓；
 * 由寻宝大师的<b>触发键</b>切换（子3 键）。
 *
 * <p>⚠️ 为什么不用 {@link SetSkillToggleC2SPacket}：那个包校验 {@code Skills.ALL_SKILLS}
 * 且开关表与技能<b>一一对应</b>（key = 技能 id），把子开关塞进去会污染技能开关表。
 * 本包照<b>木棍工具层</b>（{@link StickToolC2SPacket}）的既有先例：专用字段 + 专用包。
 *
 * <p>服务端权威：未学寻宝大师 → 忽略并回发校准；不信任客户端传来的任何其它参数。
 */
public class TreasureFilterC2SPacket {
    private final boolean enabled;

    public TreasureFilterC2SPacket(boolean enabled) {
        this.enabled = enabled;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(enabled);
    }

    public static TreasureFilterC2SPacket decode(FriendlyByteBuf buf) {
        return new TreasureFilterC2SPacket(buf.readBoolean());
    }

    public static void handle(TreasureFilterC2SPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || player.serverLevel() == null) {
                return; // 防御：登出瞬间
            }
            PlayerSkillSavedData data = PlayerSkillSavedData.get(player.serverLevel());
            PlayerSkillRecord record = data.getOrCreatePlayer(player.getUUID());
            // 服务端权威校验：未学寻宝大师 → 忽略本次切换，并回发真实状态校准客户端缓存
            if (record.getLearnedPoints(Skills.TREASURE_HUNTER) <= 0) {
                ModNetwork.sendToPlayer(player, SkillTreeDataS2CPacket.from(record));
                return;
            }
            record.setTreasureOpenedFilter(packet.enabled);
            data.setDirty();
            player.sendSystemMessage(Component.translatable(
                    packet.enabled
                            ? "chat.zifeng_s_custom_skill_tree.treasure_filter_on"
                            : "chat.zifeng_s_custom_skill_tree.treasure_filter_off",
                    Skills.getDisplayNameComponent(Skills.TREASURE_HUNTER)));
            // 回发最新状态（客户端权威校准）
            ModNetwork.sendToPlayer(player, SkillTreeDataS2CPacket.from(record));
        });
        ctx.setPacketHandled(true);
    }
}
