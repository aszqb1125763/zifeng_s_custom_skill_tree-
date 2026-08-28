package org.zifeng.skilltree.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;

import java.util.function.Supplier;

/**
 * 技能树界面开/关请求（客户端 → 服务端）。
 * 打开界面（subscribe=true）→ 订阅全部全局状态（SUB_ALL，tooltip 显示服务器当前 AE/天气/时间）；
 * 关闭界面（subscribe=false）→ 取消订阅（不再推送全局状态，省流量）。
 * 服务端同时回发 {@link SkillTreeDataS2CPacket}，客户端据此打开/刷新技能树界面。
 */
public class OpenSkillTreeC2SPacket {
    private final boolean subscribe;

    public OpenSkillTreeC2SPacket() {
        this(true);
    }

    public OpenSkillTreeC2SPacket(boolean subscribe) {
        this.subscribe = subscribe;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(subscribe);
    }

    public static OpenSkillTreeC2SPacket decode(FriendlyByteBuf buf) {
        return new OpenSkillTreeC2SPacket(buf.readBoolean());
    }

    public static void handle(OpenSkillTreeC2SPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }
            // 界面驱动订阅：打开技能树 → 订阅全部全局状态（tooltip 显示所有全局技能当前状态）
            // ⚠️ 2026-08-28 修复：改为界面驱动（原先按技能开关订阅，关闭/重置后订阅被清 → 不再推送）
            if (packet.subscribe) {
                org.zifeng.skilltree.GlobalStateSync.setSubscription(player.getUUID(),
                        org.zifeng.skilltree.GlobalStateSync.SUB_ALL);
            } else {
                org.zifeng.skilltree.GlobalStateSync.setSubscription(player.getUUID(), 0);
            }
            PlayerSkillSavedData data = PlayerSkillSavedData.get(player.serverLevel());
            PlayerSkillRecord record = data.getOrCreatePlayer(player.getUUID());
            ModNetwork.sendToPlayer(player, SkillTreeDataS2CPacket.from(record));
        });
        ctx.setPacketHandled(true);
    }
}
