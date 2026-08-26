package org.zifeng.skilltree.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * 突破原版属性上限：RangedAttribute.sanitizeValue 原本会 clamp 到 [min, max]。
 * 只对本模组技能可超过原版上限的属性（白名单）移除上限 → 生命/攻速/挖速等属性可以无限叠加。
 * 其余属性（含其他模组属性）保持原版 clamp，避免影响原版与其他模组的属性行为。
 * <p>
 * 白名单按**完整注册表 key**（namespace:path）匹配，只认原版命名空间 minecraft:，
 * 其他模组注册的同 path 属性（如 foo:max_health）不会被误解除上限 → 多模组零误伤。
 * <p>
 * 护甲（armor）/韧性（armor_toughness）故意不在白名单：护甲减伤原版封顶 80%，
 * 超出部分由自定义物理减伤属性 damage_reduction 承接（见 ModAttributes），零冲突。
 */
@Mixin(RangedAttribute.class)
public abstract class RangedAttributeMixin {

    /** 本模组技能可达超过原版上限的属性（完整注册表 key） */
    private static final Set<String> UNBOUNDED_ATTRIBUTES = Set.of(
            "minecraft:max_health",        // 体魄强化：1000点 = +500 生命
            "minecraft:attack_damage",     // 锋刃/光环伤害/战斗强化：叠满可达 3000+（原版上限 2048）
            "minecraft:attack_speed",      // 疾攻术/光环速度/攻速增幅（原版上限 1024）
            "zifeng_s_custom_skill_tree:mining_efficiency", // 采掘熟稔/挖掘强化（1.20.1 无原版 MINING_EFFICIENCY，用自定义属性替代）
            "minecraft:movement_speed",    // 疾行步法/移速增幅
            "minecraft:luck",              // 幸运眷顾
            "minecraft:jump_strength",     // 跃升体术/跃升增幅（原版上限 32）
            "minecraft:flying_speed",      // 御空术/御空增幅
            "forge:swim_speed"             // 潜游术/潜游增幅（1.20.1 的 SWIM_SPEED 是 Forge 属性，forge 命名空间）
            // 注意：armor/armor_toughness 故意不在白名单（保持原版上限）——
            // 护甲减伤原版封顶 80% 后再叠护甲无收益，超出部分的防护由自定义物理减伤属性
            // damage_reduction 承接（见 ModAttributes），这样护甲属性完全原版行为、零冲突。
            // knockback_resistance 也不在白名单：上限 1.0 = 100% 免疫，超过无意义。
    );

    @Inject(method = "sanitizeValue", at = @At("HEAD"), cancellable = true, require = 0)
    private void zifeng$removeMaxLimit(double value, CallbackInfoReturnable<Double> cir) {
        if (isSkillAttribute((Attribute) (Object) this)) {
            // 只保留下限（防止负数），移除上限限制
            // ⚠️ 不用 @Shadow minValue 字段（发布 jar reobf 后字段名混淆，refmap 不映射字段会崩溃）；
            //    直接用公共方法 getMinValue()（1.20.1 存在），reobf 安全
            cir.setReturnValue(Math.max(((net.minecraft.world.entity.ai.attributes.RangedAttribute) (Object) this).getMinValue(), value));
        }
    }

    /** 判断属性是否在本模组技能白名单内（按完整注册表 key 匹配，注册表运行时已填充） */
    private static boolean isSkillAttribute(Attribute attribute) {
        ResourceLocation key = BuiltInRegistries.ATTRIBUTE.getKey(attribute);
        // 白名单含 minecraft:/forge:/zifeng_s_custom_skill_tree: 命名空间（1.20.1 的挖速/游泳是自定义/Forge 属性）→ 直接按完整 key 匹配
        return key != null && UNBOUNDED_ATTRIBUTES.contains(key.toString());
    }
}
