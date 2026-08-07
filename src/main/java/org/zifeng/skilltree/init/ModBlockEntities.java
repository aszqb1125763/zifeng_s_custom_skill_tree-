package org.zifeng.skilltree.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.zifeng.skilltree.SkillTreeMod;
import org.zifeng.skilltree.blockentity.CreativeEnergyBlockEntity;
import org.zifeng.skilltree.blockentity.StarEnergyConverterBlockEntity;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SkillTreeMod.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StarEnergyConverterBlockEntity>> STAR_ENERGY_CONVERTER =
            BLOCK_ENTITIES.register("star_energy_converter",
                    () -> BlockEntityType.Builder.of(StarEnergyConverterBlockEntity::new, ModBlocks.STAR_ENERGY_CONVERTER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CreativeEnergyBlockEntity>> CREATIVE_ENERGY =
            BLOCK_ENTITIES.register("creative_energy_block",
                    () -> BlockEntityType.Builder.of(CreativeEnergyBlockEntity::new, ModBlocks.CREATIVE_ENERGY.get()).build(null));
}
