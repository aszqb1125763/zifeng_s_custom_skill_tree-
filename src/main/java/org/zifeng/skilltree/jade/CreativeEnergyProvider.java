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
        tooltip.add(Component.translatable("jade.zifeng_s_custom_skill_tree.energy_infinite"));
        tooltip.add(Component.translatable("jade.zifeng_s_custom_skill_tree.energy_cap", fmt(Long.MAX_VALUE)));
        // 64 位输出：每 tick 循环灌直到目标拒绝（单次 int 21.4亿 × 10万次 ≈ 2140万亿 FE/t）
        tooltip.add(Component.translatable("jade.zifeng_s_custom_skill_tree.output_infinite"));
    }

        /** 大数字 64 位格式：中文用 京/万亿/亿/万，英文用 SI 标准 K/M/G/T/P（2026-08-29） */
    private static String fmt(long value) {
        boolean chinese = net.minecraft.client.Minecraft.getInstance().options.languageCode.startsWith("zh");
        if (chinese) {
            if (value >= 1_0000_0000_0000_0000L) { // 1 京 = 1e16
                return String.format("%%.2f%s", value / 1_0000_0000_0000_0000.0, net.minecraft.network.chat.Component.translatable("ui.zifeng_s_custom_skill_tree.unit_jing").getString());
            }
            if (value >= 1_000_000_000_000L) {
                return String.format("%%.2f%s", value / 1_000_000_000_000.0, net.minecraft.network.chat.Component.translatable("ui.zifeng_s_custom_skill_tree.unit_trillion").getString());
            }
            if (value >= 100_000_000L) {
                return String.format("%%.2f%s", value / 100_000_000.0, net.minecraft.network.chat.Component.translatable("ui.zifeng_s_custom_skill_tree.unit_hundred_million").getString());
            }
            if (value >= 10_000L) {
                return String.format("%%.1f%s", value / 10_000.0, net.minecraft.network.chat.Component.translatable("ui.zifeng_s_custom_skill_tree.unit_ten_thousand").getString());
            }
            return String.valueOf(value);
        }
        if (value >= 1_000_000_000_000_000L) { // 1e15
            return String.format("%%.2fP", value / 1_000_000_000_000_000.0);
        }
        if (value >= 1_000_000_000_000L) { // 1e12
            return String.format("%%.2fT", value / 1_000_000_000_000.0);
        }
        if (value >= 1_000_000_000L) { // 1e9
            return String.format("%%.2fG", value / 1_000_000_000.0);
        }
        if (value >= 1_000_000L) { // 1e6
            return String.format("%%.2fM", value / 1_000_000.0);
        }
        if (value >= 1_000L) { // 1e3
            return String.format("%%.1fK", value / 1_000.0);
        }
        return String.valueOf(value);
    }

    @Override
    public ResourceLocation getUid() {
        return ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "creative_energy");
    }
}
