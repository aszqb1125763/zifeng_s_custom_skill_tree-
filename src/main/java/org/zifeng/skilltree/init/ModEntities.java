package org.zifeng.skilltree.init;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.zifeng.skilltree.SkillTreeMod;
import org.zifeng.skilltree.entity.AuraSwordEntity;

/**
 * 实体注册（2026-10-21 新增：杀戮光环·飞剑实体）。
 */
public class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, SkillTreeMod.MOD_ID);

    /**
     * 飞剑实体：服务端逐 tick 追踪目标，与生物碰撞后触发光环球形伤害。
     * <p>体积很小（0.6），updateInterval=1 保证位置同步平滑，客户端渲染由
     * {@code AuraSwordEntityRenderer} 负责。
     */
    public static final RegistryObject<EntityType<AuraSwordEntity>> AURA_SWORD =
            ENTITIES.register("aura_sword", () -> EntityType.Builder
                    .<AuraSwordEntity>of(AuraSwordEntity::new, MobCategory.MISC)
                    .sized(0.6f, 0.6f)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build("aura_sword"));
}
