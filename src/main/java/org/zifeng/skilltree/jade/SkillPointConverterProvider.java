package org.zifeng.skilltree.jade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.zifeng.skilltree.SkillTreeMod;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * 技能点转换机 Jade 客户端组件：
 * 显示 64 位能量缓冲 / 上限（读服务端 NBT，不受 int 2.1G 限制）、无限制输入、红石状态。
 */
public enum SkillPointConverterProvider implements IBlockComponentProvider {
    INSTANCE;

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (data == null || !data.contains("MaxEnergy")) {
            return; // 服务端数据未就绪，不显示（避免空行）
        }
        long energy = data.getLong("Energy");
        long max = data.getLong("MaxEnergy");
        tooltip.add(Component.translatable("jade.zifeng_s_custom_skill_tree.energy", fmt(energy), fmt(max)));
        if (data.getBoolean("Unlimited")) {
            tooltip.add(Component.translatable("jade.zifeng_s_custom_skill_tree.input_unlimited"));
        } else {
            long rate = data.contains("InputRate") ? data.getLong("InputRate") : org.zifeng.skilltree.Config.MACHINE_MAX_INPUT_RATE.get();
            tooltip.add(Component.translatable("jade.zifeng_s_custom_skill_tree.input_rate", fmt(rate)));
        }
        if (data.getBoolean("Redstone")) {
            tooltip.add(Component.translatable("jade.zifeng_s_custom_skill_tree.rs_blocked"));
        } else {
            tooltip.add(Component.translatable("jade.zifeng_s_custom_skill_tree.rs_ok"));
        }
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
        return ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "converter");
    }
}
