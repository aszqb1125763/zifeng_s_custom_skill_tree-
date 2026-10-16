package org.zifeng.skilltree.mixin;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 突破原版属性上限：RangedAttribute.sanitizeValue 原本会 clamp 到 [min, max]。
 * 只对本模组技能可超过原版上限的属性（白名单）移除上限 → 生命/攻速/挖速等属性可以无限叠加。
 * 其余属性（含其他模组属性）保持原版 clamp，避免影响原版与其他模组的属性行为。
 * <p>白名单按<b>属性实例引用</b>比较（不受注册名/语言变更影响）；
 * 其他模组注册的属性不在集合内 → 不会被误解除上限 → 多模组零误伤。
 * <p><b>修复历史</b>：初版按注册表 key 字符串匹配但 ID 写错（{@code "minecraft:max_health"}
 * 而非 {@code "minecraft:generic.max_health"}）→ 从未生效；2026-09-11 改为引用比较。
 * <p><b>⚠️ 2026-09-12（1.4.1）</b>：1.20.1 端查明最终根因是「<b>反射字段名字符串不会被 refmap
 * 重映射</b>」——Forge 生产环境字段名是 SRG 名（{@code MAX_HEALTH -> f_22276_}）→ 反射必失败且静默。
 * 本版（NeoForge）运行时不改字段名、未受影响，但为<b>消除同类隐患 + 两版本实现一致</b>，
 * 一并发为<b>编译期字段引用</b>。
 * <p>注：1.21.1 的 {@code Attributes.XXX} 类型是 {@code Holder<Attribute>}（取值需 {@code .value()}）；
 * 1.20.1 是 {@code Attribute} 直接可用——故两版本此文件实现不同，属预期。
 * <p>
 * 护甲（armor）/韧性（armor_toughness）故意不在白名单：护甲减伤原版封顶 80%，
 * 超出部分由自定义物理减伤属性 damage_reduction 承接（见 ModAttributes），零冲突。
 */
@Mixin(RangedAttribute.class)
public abstract class RangedAttributeMixin {

    @Shadow
    @Final
    private double minValue;


    @Inject(method = "sanitizeValue", at = @At("HEAD"), cancellable = true, require = 0)
    private void zifeng$removeMaxLimit(double value, CallbackInfoReturnable<Double> cir) {
        if (isSkillAttribute((Attribute) (Object) this)) {
            // 只保留下限（防止负数），移除上限限制
            cir.setReturnValue(Math.max(minValue, value));
        }
    }

    /**
     * 判断属性是否需要解除原版上限。
     * <p>白名单按<b>属性实例引用</b>比较（不受注册名/语言变更影响，跨版本稳定）；
     * 其他模组注册的属性不在集合内 → 不会被误解除上限 → 多模组零误伤。
     * <p><b>修复历史</b>：初版按注册表 key 字符串匹配但 ID 写错（{@code "minecraft:max_health"}
     * 而非 {@code "minecraft:generic.max_health"}）→ 从未生效；2026-09-11 改为引用比较。
     * <p><b>⚠️ 2026-09-12（1.4.1）</b>：1.20.1 端发现「<b>反射字段名字符串不会被 refmap 重映射</b>」
     * ——Forge 生产环境字段名是 SRG 名（{@code MAX_HEALTH -> f_22276_}），反射必然失败且静默
     * → 白名单恒为空。本版（NeoForge）虽因运行时不改字段名而未受影响，
     * 但为**消除同类隐患 + 两版本实现一致**，一并改为<b>编译期字段引用</b>。
     * <p>注：1.21.1 的 {@code Attributes.XXX} 类型是 {@code Holder<Attribute>}，取值需 {@code .value()}；
     * 1.20.1 类型是 {@code Attribute} 直接可用（故两版本此文件实现不同，属预期）。
     */
    private static volatile java.util.Set<Attribute> UNBOUNDED_CACHE;
    /** 白名单应有条目数（与下方 addAttr 调用数一致，用于完整性断言） */
    private static final int EXPECTED_ATTR_COUNT = 8;

    private static java.util.Set<Attribute> unboundedAttributes() {
        java.util.Set<Attribute> s = UNBOUNDED_CACHE;
        if (s != null) {
            return s;
        }
        java.util.Set<Attribute> t = new java.util.HashSet<>();
        // ⚠️ 用【编译期字段引用】（不能用反射字符串——理由见上方 Javadoc）
        addAttr(t, net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        addAttr(t, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        addAttr(t, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED);
        addAttr(t, net.minecraft.world.entity.ai.attributes.Attributes.MINING_EFFICIENCY);
        addAttr(t, net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        addAttr(t, net.minecraft.world.entity.ai.attributes.Attributes.LUCK);
        addAttr(t, net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH);
        addAttr(t, net.minecraft.world.entity.ai.attributes.Attributes.FLYING_SPEED);
        // ⚠️ 只缓存「集齐」的结果：首次调用可能发生在 Attributes 类初始化过程中
        //    （其 static 字段构造 RangedAttribute 时可能间接触发 sanitizeValue），
        //    此时读到的 Attributes.XXX 仍是 null；若把不完整集合缓存下来，白名单将永久缺项。
        if (t.size() >= EXPECTED_ATTR_COUNT) {
            UNBOUNDED_CACHE = t;
        }
        return t;
    }

    /** 加入白名单（编译期引用；1.21.1 字段是 Holder<Attribute>，故取 .value()） */
    private static void addAttr(java.util.Set<Attribute> out,
                                net.minecraft.core.Holder<Attribute> holder) {
        if (holder != null) {
            Attribute a = holder.value();
            if (a != null) {
                out.add(a);
            }
        }
    }

    private static boolean isSkillAttribute(Attribute attribute) {
        return unboundedAttributes().contains(attribute);
    }
}
