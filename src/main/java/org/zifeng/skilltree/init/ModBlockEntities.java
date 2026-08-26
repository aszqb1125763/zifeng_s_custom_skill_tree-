package org.zifeng.skilltree.init;

import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import org.zifeng.skilltree.SkillTreeMod;
import org.zifeng.skilltree.blockentity.CreativeEnergyBlockEntity;
import org.zifeng.skilltree.blockentity.SkillPointConverterBlockEntity;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, SkillTreeMod.MOD_ID);

    /** 技能点转换机方块实体：注册名保持 star_energy_converter（旧注册名，存档兼容，不能改） */
    public static final RegistryObject<BlockEntityType<SkillPointConverterBlockEntity>> SKILL_POINT_CONVERTER =
            BLOCK_ENTITIES.register("star_energy_converter",
                    () -> BlockEntityType.Builder.of(SkillPointConverterBlockEntity::new, ModBlocks.STAR_ENERGY_CONVERTER.get()).build(null));

    public static final RegistryObject<BlockEntityType<CreativeEnergyBlockEntity>> CREATIVE_ENERGY =
            BLOCK_ENTITIES.register("creative_energy_block",
                    () -> BlockEntityType.Builder.of(CreativeEnergyBlockEntity::new, ModBlocks.CREATIVE_ENERGY.get()).build(null));
}
