package org.zifeng.skilltree.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;

import java.util.function.Supplier;

/**
 * 打开技能树请求（客户端 → 服务端）。
 * 服务端校验后回发 {@link SkillTreeDataS2CPacket}，客户端据此打开技能树界面。
 */
public class OpenSkillTreeC2SPacket {
    public OpenSkillTreeC2SPacket() {
    }

    public void encode(FriendlyByteBuf buf) {
    }

    public static OpenSkillTreeC2SPacket decode(FriendlyByteBuf buf) {
        return new OpenSkillTreeC2SPacket();
    }

    public static void handle(OpenSkillTreeC2SPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }
            PlayerSkillSavedData data = PlayerSkillSavedData.get(player.serverLevel());
            PlayerSkillRecord record = data.getOrCreatePlayer(player.getUUID());
            ModNetwork.sendToPlayer(player, SkillTreeDataS2CPacket.from(record));
        });
        ctx.setPacketHandled(true);
    }
}
