package org.zifeng.skilltree.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.zifeng.skilltree.SkillTreeMod;
import org.zifeng.skilltree.blockentity.CreativeEnergyBlockEntity;
import org.zifeng.skilltree.blockentity.SkillPointConverterBlockEntity;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SkillTreeMod.MOD_ID);

    /** 技能点转换机方块实体：注册名保持 star_energy_converter（旧注册名，存档兼容，不能改） */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SkillPointConverterBlockEntity>> SKILL_POINT_CONVERTER =
            BLOCK_ENTITIES.register("star_energy_converter",
                    () -> BlockEntityType.Builder.of(SkillPointConverterBlockEntity::new, ModBlocks.STAR_ENERGY_CONVERTER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CreativeEnergyBlockEntity>> CREATIVE_ENERGY =
            BLOCK_ENTITIES.register("creative_energy_block",
                    () -> BlockEntityType.Builder.of(CreativeEnergyBlockEntity::new, ModBlocks.CREATIVE_ENERGY.get()).build(null));
}
