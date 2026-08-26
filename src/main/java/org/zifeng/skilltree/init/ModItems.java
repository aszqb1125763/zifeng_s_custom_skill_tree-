package org.zifeng.skilltree.init;

import net.minecraft.world.item.BlockItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.zifeng.skilltree.SkillTreeMod;

public class ModItems {
    public static final DeferredRegister<net.minecraft.world.item.Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, SkillTreeMod.MOD_ID);

    public static final RegistryObject<BlockItem> SKILL_POINT_CONVERTER_ITEM = ITEMS.register("star_energy_converter",
            () -> new BlockItem(ModBlocks.STAR_ENERGY_CONVERTER.get(), new net.minecraft.world.item.Item.Properties()));
    public static final RegistryObject<BlockItem> CREATIVE_ENERGY_ITEM = ITEMS.register("creative_energy_block",
            () -> new BlockItem(ModBlocks.CREATIVE_ENERGY.get(), new net.minecraft.world.item.Item.Properties()));
}
