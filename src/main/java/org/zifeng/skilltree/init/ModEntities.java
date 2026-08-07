package org.zifeng.skilltree.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.zifeng.skilltree.SkillTreeMod;
import org.zifeng.skilltree.entity.AuraSwordEntity;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, SkillTreeMod.MOD_ID);

    /** 杀戮光环·钻石剑（环绕玩家自动攻击的视觉+判定实体） */
    public static final DeferredHolder<EntityType<?>, EntityType<AuraSwordEntity>> AURA_SWORD =
            ENTITIES.register("aura_sword",
                    () -> EntityType.Builder.<AuraSwordEntity>of(AuraSwordEntity::new, MobCategory.MISC)
                            .sized(0.5f, 0.5f)
                            .clientTrackingRange(32)
                            .updateInterval(1)
                            .build(SkillTreeMod.MOD_ID + ":aura_sword"));
}
