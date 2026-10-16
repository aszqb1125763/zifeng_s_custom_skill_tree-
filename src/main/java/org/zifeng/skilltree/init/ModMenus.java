package org.zifeng.skilltree.init;

import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.zifeng.skilltree.SkillTreeMod;
import org.zifeng.skilltree.menu.SkillPointConverterMenu;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(net.minecraft.core.registries.Registries.MENU, SkillTreeMod.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<SkillPointConverterMenu>> SKILL_POINT_CONVERTER =
            MENUS.register("star_energy_converter",
                    () -> new MenuType<>(SkillPointConverterMenu::new, FeatureFlags.DEFAULT_FLAGS));
}
