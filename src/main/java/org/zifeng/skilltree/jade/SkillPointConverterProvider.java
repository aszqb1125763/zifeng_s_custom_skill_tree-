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
        tooltip.add(Component.literal("§b能量: §f" + fmt(energy) + " §7/ §f" + fmt(max) + " §bFE"));
        if (data.getBoolean("Unlimited")) {
            tooltip.add(Component.literal("§a输入: §f无限制"));
        } else {
            long rate = data.contains("InputRate") ? data.getLong("InputRate") : org.zifeng.skilltree.Config.MACHINE_MAX_INPUT_RATE.get();
            tooltip.add(Component.literal("§7输入速率: §f" + fmt(rate) + " FE/t"));
        }
        if (data.getBoolean("Redstone")) {
            tooltip.add(Component.literal("§c红石激活: 机器已关闭"));
        } else {
            tooltip.add(Component.literal("§a红石未激活: 运行中"));
        }
    }

    /** 大数字 64 位格式：≥1京→"X.XX京"，≥1万亿→"X.XX万亿"，≥1亿→"X.XX亿"，≥1万→"X.XX万" */
    private static String fmt(long value) {
        if (value >= 1_0000_0000_0000_0000L) { // 1 京 = 1e16
            return String.format("%.2f京", value / 1_0000_0000_0000_0000.0);
        }
        if (value >= 1_000_000_000_000L) {
            return String.format("%.2f万亿", value / 1_000_000_000_000.0);
        }
        if (value >= 100_000_000L) {
            return String.format("%.2f亿", value / 100_000_000.0);
        }
        if (value >= 10_000L) {
            return String.format("%.1f万", value / 10_000.0);
        }
        return String.valueOf(value);
    }

    @Override
    public ResourceLocation getUid() {
        return new ResourceLocation(SkillTreeMod.MOD_ID, "converter");
    }
}
