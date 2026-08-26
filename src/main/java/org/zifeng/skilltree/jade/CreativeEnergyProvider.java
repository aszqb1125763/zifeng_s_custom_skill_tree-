package org.zifeng.skilltree.jade;


import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.zifeng.skilltree.SkillTreeMod;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * 创造能量方块 Jade 显示（参考 Mekanism 创造能量立方）：
 * 真无限能量，显示 ∞ 无限 + 64 位大数字（Long.MAX_VALUE），不受 int 2.1G 限制。
 */
public enum CreativeEnergyProvider implements IBlockComponentProvider {
    INSTANCE;

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        tooltip.add(Component.literal("§b能量: §f∞ 无限 §bFE"));
        tooltip.add(Component.literal("§7上限: §f" + fmt(Long.MAX_VALUE) + " §bFE"));
        // 64 位输出：每 tick 循环灌直到目标拒绝（单次 int 21.4亿 × 10万次 ≈ 2140万亿 FE/t）
        tooltip.add(Component.literal("§a输出: ∞（每 tick 灌满相邻机器，64 位级）"));
    }

    /** 64 位大数字格式化：≥1万亿亿→"X.XX京"，≥1万亿→"X.XX万亿"，≥1亿→"X.XX亿" */
    private static String fmt(long value) {
        if (value >= 1_0000_0000_0000_0000L) { // 1 京 = 1e16
            return String.format("%.2f京", value / 1_0000_0000_0000_0000.0);
        }
        if (value >= 1_000_000_000_000L) { // 1 万亿
            return String.format("%.2f万亿", value / 1_000_000_000_000.0);
        }
        if (value >= 100_000_000L) { // 1 亿
            return String.format("%.2f亿", value / 100_000_000.0);
        }
        return String.valueOf(value);
    }

    @Override
    public ResourceLocation getUid() {
        return new ResourceLocation(SkillTreeMod.MOD_ID, "creative_energy");
    }
}
