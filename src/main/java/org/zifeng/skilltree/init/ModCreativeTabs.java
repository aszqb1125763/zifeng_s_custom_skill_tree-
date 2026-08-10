package org.zifeng.skilltree.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.zifeng.skilltree.SkillTreeMod;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SkillTreeMod.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.zifeng_s_custom_skill_tree"))
                    .icon(() -> new ItemStack(ModItems.SKILL_POINT_CONVERTER_ITEM.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.SKILL_POINT_CONVERTER_ITEM.get());
                        output.accept(ModItems.CREATIVE_ENERGY_ITEM.get());
                    })
                    .build());
}
