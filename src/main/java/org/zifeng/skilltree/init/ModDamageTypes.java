package org.zifeng.skilltree.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.zifeng.skilltree.SkillTreeMod;

import java.util.function.Supplier;

/**
 * 伤害类型注册：混沌伤害（模拟龙之研究混沌武器，可击破混沌守卫/水晶护盾）。
 * 通过 data 包给 zifeng:chaos_damage 打上 draconicevolution:chaotic 标签，
 * DE 的 getDamageLevel() 会识别为 TechLevel.CHAOTIC → chaoticBypassCrystalShield 生效。
 */
public class ModDamageTypes {
    public static final DeferredRegister<DamageType> DAMAGE_TYPES =
            DeferredRegister.create(Registries.DAMAGE_TYPE, SkillTreeMod.MOD_ID);

    /** 混沌伤害类型：无视护甲、可破 DE 混沌守卫/水晶护盾 */
    public static final Supplier<DamageType> CHAOS_DAMAGE = DAMAGE_TYPES.register("chaos_damage",
            () -> new DamageType("chaos_damage", 0.1F));

    /** 混沌伤害标签（与 DE 对齐，供 getDamageLevel 判定） */
    public static TagKey<DamageType> chaoticTag() {
        return TagKey.create(Registries.DAMAGE_TYPE,
                ResourceLocation.fromNamespaceAndPath("draconicevolution", "chaotic"));
    }

    /** 混沌伤害类型的 ResourceKey（供构造 DamageSource） */
    public static net.minecraft.resources.ResourceKey<DamageType> chaosDamageKey() {
        return net.minecraft.resources.ResourceKey.create(Registries.DAMAGE_TYPE, chaosDamageId());
    }

    public static ResourceLocation chaosDamageId() {
        return ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "chaos_damage");
    }
}
