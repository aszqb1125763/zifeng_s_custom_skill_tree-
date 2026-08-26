package org.zifeng.skilltree.init;


import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import org.zifeng.skilltree.SkillTreeMod;

public class ModCreativeTabs {
    // 1.20.1：CreativeModeTab 是原版注册表（Registries.CREATIVE_MODE_TAB），ForgeRegistries 无 CREATIVE_MODE_TABS
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(
            net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB, SkillTreeMod.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.zifeng_s_custom_skill_tree"))
                    .icon(() -> new ItemStack(ModItems.SKILL_POINT_CONVERTER_ITEM.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.SKILL_POINT_CONVERTER_ITEM.get());
                        output.accept(ModItems.CREATIVE_ENERGY_ITEM.get());
                    })
                    .build());
}
