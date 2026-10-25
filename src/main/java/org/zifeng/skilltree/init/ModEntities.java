package org.zifeng.skilltree.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.zifeng.skilltree.SkillTreeMod;
import org.zifeng.skilltree.entity.AuraSwordEntity;

/** 实体注册（2026-10-21：杀戮光环·飞剑实体）。 */
public class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, SkillTreeMod.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<AuraSwordEntity>> AURA_SWORD =
            ENTITIES.register("aura_sword", () -> EntityType.Builder
                    .<AuraSwordEntity>of(AuraSwordEntity::new, MobCategory.MISC)
                    .sized(0.6f, 0.6f)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build("aura_sword"));
}
