package org.zifeng.skilltree.init;

import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.zifeng.skilltree.SkillTreeMod;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkillTreeMod.MOD_ID);

    public static final DeferredItem<BlockItem> STAR_ENERGY_CONVERTER_ITEM = ITEMS.registerSimpleBlockItem("star_energy_converter", ModBlocks.STAR_ENERGY_CONVERTER);
    public static final DeferredItem<BlockItem> CREATIVE_ENERGY_ITEM = ITEMS.registerSimpleBlockItem("creative_energy_block", ModBlocks.CREATIVE_ENERGY);
}
