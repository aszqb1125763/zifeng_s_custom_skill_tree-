package org.zifeng.skilltree.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * 突破原版属性上限：RangedAttribute.sanitizeValue 原本会 clamp 到 [min, max]。
 * 只对本模组技能可超过原版上限的属性（白名单）移除上限 → 生命/攻速/挖速等属性可以无限叠加。
 * 其余属性（含其他模组属性）保持原版 clamp，避免影响原版与其他模组的属性行为。
 * <p>
 * <b>白名单机制（2026-09-11 修复）</b>：按<b>属性实例引用</b>比较（{@code Holder.value()}），
 * 不受注册名/语言变更影响，跨版本稳定；
 * 其他模组注册的属性不在集合内 → 不会被误解除上限 → 多模组零误伤。
 * <p><b>⚠️ 修复历史</b>：旧实现按注册表 key 字符串匹配，但白名单写成了
 * {@code "minecraft:max_health"} 这类<b>错误 ID</b>——真实 ID 带前缀
 * （{@code minecraft:generic.max_health} / {@code minecraft:generic.attack_damage} /
 * {@code minecraft:player.mining_efficiency} …）→ 判定永远为 false →
 * <b>本 Mixin 自上线起从未生效</b>，max_health 一直被 clamp 到原版上限 <b>1024</b>。
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
     * 判断属性是否需要解除原版上限（2026-09-11 修复）。
     * 旧实现按注册表 key 字符串匹配，但白名单写成 "minecraft:max_health" 等错误 ID
     * （真实 ID 带前缀：minecraft:generic.max_health / minecraft:generic.attack_damage /
     * minecraft:player.mining_efficiency ...）-> 永远返回 false -> 本 Mixin 自上线起
     * 从未生效 -> max_health 被 clamp 到原版上限 1024（血条 512 颗心 / 面板与数字显示 1024）。
     * 现改为属性实例引用比较（Holder.value()），不受注册名变更影响，跨版本稳定。
     */
    private static volatile java.util.Set<Attribute> UNBOUNDED_CACHE;

    private static java.util.Set<Attribute> unboundedAttributes() {
        java.util.Set<Attribute> s = UNBOUNDED_CACHE;
        if (s == null) {
            java.util.Set<Attribute> t = new java.util.HashSet<>();
            addAttr(t, "MAX_HEALTH");
            addAttr(t, "ATTACK_DAMAGE");
            addAttr(t, "ATTACK_SPEED");
            addAttr(t, "MINING_EFFICIENCY");
            addAttr(t, "MOVEMENT_SPEED");
            addAttr(t, "LUCK");
            addAttr(t, "JUMP_STRENGTH");
            addAttr(t, "FLYING_SPEED");
            UNBOUNDED_CACHE = t;
            s = t;
        }
        return s;
    }

    /**
     * 反射取 {@code Attributes} 的字段加入白名单。
     * <p>用反射而不用直接引用，是为兼容平台差异：<b>1.20.1 字段类型是 {@code Attribute}</b>，
     * <b>1.21.1 是 {@code Holder<Attribute>}</b>；字段名在本版本不存在时静默跳过。
     */
    private static void addAttr(java.util.Set<Attribute> out, String fieldName) {
        try {
            Object v = net.minecraft.world.entity.ai.attributes.Attributes.class
                    .getField(fieldName).get(null);
            if (v instanceof Attribute a) {
                out.add(a);
            } else if (v instanceof net.minecraft.core.Holder<?> h && h.value() instanceof Attribute a2) {
                out.add(a2);
            }
        } catch (Throwable ignored) {
            // 该字段在本版本不存在 → 跳过
        }
    }

    private static boolean isSkillAttribute(Attribute attribute) {
        return unboundedAttributes().contains(attribute);
    }
}
