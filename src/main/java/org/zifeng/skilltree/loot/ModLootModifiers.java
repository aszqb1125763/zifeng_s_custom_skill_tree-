package org.zifeng.skilltree.loot;

import com.mojang.serialization.Codec;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.zifeng.skilltree.SkillTreeMod;

/**
 * 方块掉落修改器注册（Global Loot Modifier，1.20.1 Forge 原生机制，2026-09-01）。
 * <p>
 * 注册 SkillTreeLootModifier 的 codec；实际生效还需 data 目录的
 * global_loot_modifiers.json + loot_modifiers json（见 src/main/resources/data）。
 * <p>
 * 参考：TinkersConstruct（SlimeKnights，MIT）的 GLM 注册方式。
 */
public class ModLootModifiers {

    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> GLM =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, SkillTreeMod.MOD_ID);

    public static final RegistryObject<Codec<SkillTreeLootModifier>> SKILL_TREE =
            GLM.register("skill_tree_block_drops", () -> SkillTreeLootModifier.CODEC);

    public static void register(IEventBus modEventBus) {
        GLM.register(modEventBus);
    }
}
