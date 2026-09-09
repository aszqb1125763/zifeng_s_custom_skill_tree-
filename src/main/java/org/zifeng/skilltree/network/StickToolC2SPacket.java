package org.zifeng.skilltree.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;

import java.util.function.Supplier;

/**
 * 木棍工具层操作（客户端 → 服务端，2026-09-08，1.20.1）：
 * <ul>
 *   <li>action=0 总开关切换（mode 忽略）：只把木棍手势占用/还原——不影响任何技能被动逻辑与已绑数据</li>
 *   <li>action=1 模式设置：把木棍模式设为 mode（0-5；客户端已按已解锁技能算好目标模式，
 *       服务端照单全收——⚠️ 2026-09-08 修复：旧版服务端固定 1-mode 只允许 BIND↔RANGE，
 *       导致切到 2-5（放置/挖掘/攻击/防护）后回发校准被弹回）</li>
 * </ul>
 */
public class StickToolC2SPacket {
    private final int action;
    private final int mode;

    public StickToolC2SPacket(int action, int mode) {
        this.action = action;
        this.mode = mode;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(action);
        buf.writeVarInt(mode);
    }

    public static StickToolC2SPacket decode(FriendlyByteBuf buf) {
        return new StickToolC2SPacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(StickToolC2SPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || player.serverLevel() == null) {
                return;
            }
            PlayerSkillRecord record = PlayerSkillSavedData.get(player.serverLevel()).getOrCreatePlayer(player.getUUID());
            if (packet.action == 0) {
                record.setStickToolOn(!record.isStickToolOn()); // 总开关取反
            } else if (packet.action == 1) {
                record.setStickToolMode(packet.mode); // 目标模式（record 内部 clamp 0-5）
            } else {
                return;
            }
            PlayerSkillSavedData.get(player.serverLevel()).setDirty();
            // 回发完整技能数据（客户端校准工具状态缓存）
            org.zifeng.skilltree.network.ModNetwork.sendToPlayer(player,
                    SkillTreeDataS2CPacket.from(record));
        });
        ctx.setPacketHandled(true);
    }
}
