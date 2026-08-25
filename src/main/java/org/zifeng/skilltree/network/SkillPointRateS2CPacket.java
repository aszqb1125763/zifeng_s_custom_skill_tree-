package org.zifeng.skilltree.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.zifeng.skilltree.SkillTreeMod;
import org.zifeng.skilltree.client.SkillPointHudRenderer;

import java.util.HashMap;
import java.util.Map;

/**
 * 技能点每秒产量同步（服务端 → 客户端，2026-08-25）：
 * <ul>
 *   <li>服务端每秒统计各来源（时间洗礼/风暴/洪流、移动/飞行/挖掘洗礼、星能转换机）的每秒产点</li>
 *   <li>客户端 HUD 显示各来源速率 + 唯一总技能点数字</li>
 * </ul>
 */
public record SkillPointRateS2CPacket(double totalSkillPoints, Map<String, Double> rates) implements CustomPacketPayload {
    public static final Type<SkillPointRateS2CPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "skill_point_rate"));
    public static final StreamCodec<FriendlyByteBuf, SkillPointRateS2CPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SkillPointRateS2CPacket decode(FriendlyByteBuf buf) {
            double total = buf.readDouble();
            int size = buf.readVarInt();
            Map<String, Double> rates = new HashMap<>();
            for (int i = 0; i < size; i++) {
                String key = buf.readUtf();
                double val = buf.readDouble();
                rates.put(key, val);
            }
            return new SkillPointRateS2CPacket(total, rates);
        }

        @Override
        public void encode(FriendlyByteBuf buf, SkillPointRateS2CPacket p) {
            buf.writeDouble(p.totalSkillPoints());
            buf.writeVarInt(p.rates().size());
            for (Map.Entry<String, Double> e : p.rates().entrySet()) {
                buf.writeUtf(e.getKey());
                buf.writeDouble(e.getValue());
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SkillPointRateS2CPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.flow().isClientbound()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    SkillPointHudRenderer.updateRates(packet.totalSkillPoints(), packet.rates());
                }
            }
        });
    }
}
