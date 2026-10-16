package org.zifeng.skilltree.init;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.zifeng.skilltree.blockentity.CreativeEnergyBlockEntity;
import org.zifeng.skilltree.blockentity.SkillPointConverterBlockEntity;

/**
 * 能量能力注册：让技能点转换机 / 创造能量方块暴露 NeoForge 能量能力。
 * <p>⚠️ 26.1.2 API 变更：旧的 {@code Capabilities.EnergyStorage.BLOCK}（{@code IEnergyStorage}，
 * 自 1.21.9 起已标记 forRemoval）已被移除，替换为
 * {@code Capabilities.Energy.BLOCK}（{@code BlockCapability<EnergyHandler, Direction>}）。
 * 依据：{@code net/neoforged/neoforge/capabilities/Capabilities.java:21-24}。
 * <p>由 SkillTreeMod 构造器手动注册（避免 MOD 总线自动扫描的时序风险）。
 */
public class ModCapabilities {

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.SKILL_POINT_CONVERTER.get(),
                (be, side) -> ((SkillPointConverterBlockEntity) be).getEnergyStorage());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.CREATIVE_ENERGY.get(),
                (be, side) -> ((CreativeEnergyBlockEntity) be).getEnergyStorage());
    }
}
