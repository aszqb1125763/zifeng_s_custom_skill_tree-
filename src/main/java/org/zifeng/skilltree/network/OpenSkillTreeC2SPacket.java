package org.zifeng.skilltree.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.zifeng.skilltree.SkillTreeMod;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;

import java.util.Map;

/**
 * 技能树界面开/关请求（客户端 → 服务端）。
 * 打开界面（subscribe=true）→ 订阅全部全局状态（SUB_ALL，tooltip 显示服务器当前 AE/天气/时间）；
 * 关闭界面（subscribe=false）→ 取消订阅（不再推送全局状态，省流量）。
 * 服务端同时回发 {@link SkillTreeDataS2CPacket}，客户端据此打开/刷新技能树界面。
 */
public record OpenSkillTreeC2SPacket(boolean subscribe) implements CustomPacketPayload {
    public static final Type<OpenSkillTreeC2SPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "open_skill_tree"));
    public static final StreamCodec<FriendlyByteBuf, OpenSkillTreeC2SPacket> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, OpenSkillTreeC2SPacket::subscribe, OpenSkillTreeC2SPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenSkillTreeC2SPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.flow().isServerbound() && ctx.player() instanceof ServerPlayer player) {
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
                PacketDistributor.sendToPlayer(player,
                        SkillTreeDataS2CPacket.from(record));
            }
        });
    }
}
