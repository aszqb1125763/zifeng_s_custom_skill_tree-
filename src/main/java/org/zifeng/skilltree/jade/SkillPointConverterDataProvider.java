package org.zifeng.skilltree.jade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.zifeng.skilltree.SkillTreeMod;
import org.zifeng.skilltree.blockentity.SkillPointConverterBlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * 技能点转换机 Jade 服务端数据提供器：
 * 把 64 位能量缓冲/上限等数据写入 NBT，供客户端组件显示（突破 IEnergyStorage int 上限 2.1G）。
 * 仅在 Jade 安装时被加载（services 文件注册），未安装绝不加载、绝不崩溃。
 */
public enum SkillPointConverterDataProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getTarget() instanceof SkillPointConverterBlockEntity be) {
            data.putLong("Energy", be.getProgress());
            data.putLong("MaxEnergy", Math.max(1, org.zifeng.skilltree.Config.MACHINE_ENERGY_CAPACITY.get()));
            data.putBoolean("Unlimited", be.isUnlimitedInput());
            data.putBoolean("Redstone", be.isRedstoneBlocked());
            data.putLong("InputRate", be.getInputRate()); // 机器实际输入速率（含 GUI 自设值）
        }
    }

    @Override
    public ResourceLocation getUid() {
        return new ResourceLocation(SkillTreeMod.MOD_ID, "converter_data");
    }
}
