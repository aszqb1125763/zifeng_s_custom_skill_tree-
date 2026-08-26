package org.zifeng.skilltree.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.zifeng.skilltree.client.SkillPointHudRenderer;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 技能点每秒产量同步（服务端 → 客户端，2026-08-25）：
 * <ul>
 *   <li>服务端每秒统计各来源（时间洗礼/风暴/洪流、移动/飞行/挖掘洗礼、星能转换机）的每秒产点</li>
 *   <li>客户端 HUD 显示各来源速率 + 唯一总技能点数字</li>
 * </ul>
 */
public class SkillPointRateS2CPacket {
    private final double totalSkillPoints;
    private final Map<String, Double> rates;

    public SkillPointRateS2CPacket(double totalSkillPoints, Map<String, Double> rates) {
        this.totalSkillPoints = totalSkillPoints;
        this.rates = rates;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(totalSkillPoints);
        buf.writeVarInt(rates.size());
        for (Map.Entry<String, Double> e : rates.entrySet()) {
            buf.writeUtf(e.getKey());
            buf.writeDouble(e.getValue());
        }
    }

    public static SkillPointRateS2CPacket decode(FriendlyByteBuf buf) {
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

    public static void handle(SkillPointRateS2CPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                SkillPointHudRenderer.updateRates(packet.totalSkillPoints, packet.rates);
            }
        });
        ctx.setPacketHandled(true);
    }
}
