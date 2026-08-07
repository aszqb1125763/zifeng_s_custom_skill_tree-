package org.zifeng.skilltree.init;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.zifeng.skilltree.blockentity.CreativeEnergyBlockEntity;
import org.zifeng.skilltree.blockentity.StarEnergyConverterBlockEntity;

/**
 * 能量能力注册：让星能转换机 / 创造能量方块暴露 FE（IEnergyStorage）能力。
 * 由 SkillTreeMod 构造器手动注册（避免 MOD 总线自动扫描的时序风险）。
 */
public class ModCapabilities {

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.STAR_ENERGY_CONVERTER.get(),
                (be, side) -> ((StarEnergyConverterBlockEntity) be).getEnergyStorage());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.CREATIVE_ENERGY.get(),
                (be, side) -> ((CreativeEnergyBlockEntity) be).getEnergyStorage());
    }
}
