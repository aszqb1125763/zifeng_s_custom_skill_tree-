package org.zifeng.skilltree.mixin;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 突破原版属性上限：RangedAttribute.sanitizeValue 原本会 clamp 到 [min, max]。
 * 只对本模组技能可超过原版上限的属性（白名单）移除上限 → 生命/攻速/挖速等属性可以无限叠加。
 * 其余属性（含其他模组属性）保持原版 clamp，避免影响原版与其他模组的属性行为。
 * <p>白名单按<b>属性实例引用</b>比较（不受注册名/语言变更影响）；
 * 其他模组注册的属性不在集合内 → 不会被误解除上限 → 多模组零误伤。
 * <p>
 * <b>⛔ 本 Mixin 失败过三次，最终根因值得长期记住（2026-09-12 定案）：</b>
 * <ol>
 *   <li><b>注册 ID 写错</b>：用 {@code "minecraft:max_health"} 匹配，真实 ID 带前缀
 *       （{@code minecraft:generic.max_health}）→ 判定永远 false，从未生效。</li>
 *   <li><b>改引用比较但取实例仍靠反射字符串</b>（{@code getField("MAX_HEALTH")}）——根因未除。</li>
 *   <li><b>反射字符串不会被 Mixin refmap 重映射</b>（最终根因）：Forge 1.20.1 生产环境
 *       字段名是 SRG 名（{@code Attributes.MAX_HEALTH -> f_22276_}，实测 reobf mappings），
 *       {@code getField("MAX_HEALTH")} 必抛 {@code NoSuchFieldException} → catch 静默吞掉
 *       → <b>白名单恒为空</b> → 全属性仍被 clamp。
 *       <br>⚠️ <b>开发环境字段名是 MCP 名所以表现正常，只在整合包复现</b>——极隐蔽。</li>
 * </ol>
 * 现全部改为<b>编译期字段引用</b>（refmap 自动映射，生产环境有效）。
 * <p>
 * 护甲（armor）/韧性（armor_toughness）故意不在白名单：护甲减伤原版封顶 80%，
 * 超出部分由自定义物理减伤属性 damage_reduction 承接（见 ModAttributes），零冲突。
 */
@Mixin(RangedAttribute.class)
public abstract class RangedAttributeMixin {


    @Inject(method = "sanitizeValue", at = @At("HEAD"), cancellable = true, require = 0)
    private void zifeng$removeMaxLimit(double value, CallbackInfoReturnable<Double> cir) {
        if (isSkillAttribute((Attribute) (Object) this)) {
            // 只保留下限（防止负数），移除上限限制
            // ⚠️ 不用 @Shadow minValue 字段（发布 jar reobf 后字段名混淆，refmap 不映射字段会崩溃）；
            //    直接用公共方法 getMinValue()（1.20.1 存在），reobf 安全
            cir.setReturnValue(Math.max(((net.minecraft.world.entity.ai.attributes.RangedAttribute) (Object) this).getMinValue(), value));
        }
    }

    /**
     * 判断属性是否需要解除原版上限。
     * <p>白名单按<b>属性实例引用</b>比较（不受注册名/语言变更影响，跨版本稳定）；
     * 其他模组注册的属性不在集合内 → 不会被误解除上限 → 多模组零误伤。
     * <p><b>⚠️ 2026-09-12（1.4.1）第三次修复——这次是本 Mixin 真正失效的根因：</b>
     * <ol>
     *   <li>初版：按注册表 key 字符串匹配，但 ID 写成 {@code "minecraft:max_health"}（真实是
     *       {@code minecraft:generic.max_health}）→ 永远 false，从未生效。</li>
     *   <li>2026-09-11：改为「属性实例引用」比较，但<b>取实例这一步仍用反射字段名字符串</b>
     *       {@code Attributes.class.getField("MAX_HEALTH")} → <b>根因未除</b>。</li>
     *   <li><b>2026-09-12（本次）</b>：确认<b>反射字符串不会被 Mixin refmap 重映射</b>，
     *       而 Forge 1.20.1 <b>生产环境</b>字段名是 SRG 名（{@code MAX_HEALTH -> f_22276_}，
     *       实测 reobf mappings）→ {@code getField} 抛 {@code NoSuchFieldException}
     *       → 被 catch 静默吞掉 → <b>白名单恒为空</b> → 所有属性仍被 clamp 到原版上限 1024。
     *       <br>现象：整合包里生命/攻击/攻速/移速/幸运/跳跃/飞行/挖速<b>全部</b>卡在上限；
     *       而开发环境字段名是 MCP 名所以"看着正常" → <b>只在整合包复现，极隐蔽</b>。
     *       <br>现已改用<b>编译期字段引用</b>（refmap 会自动映射，生产环境有效）。
     * </ol>
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
        // ⚠️ 必须用【编译期字段引用】，不能用反射字符串（详见上方 Javadoc 第 3 条）。
        //    ModAttributes.MINING_EFFICIENCY：1.20.1 原版【没有】MINING_EFFICIENCY（1.21 才合入原版），
        //    挖速由本模组自定义属性承接，故这里直接用自定义属性（顺带解决挖速被 clamp 到 1024 的问题）。
        addAttr(t, net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        addAttr(t, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        addAttr(t, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED);
        addAttr(t, org.zifeng.skilltree.init.ModAttributes.MINING_EFFICIENCY.get());
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

    /** 加入白名单（编译期引用属性实例；null 跳过） */
    private static void addAttr(java.util.Set<Attribute> out, Attribute attribute) {
        if (attribute != null) {
            out.add(attribute);
        }
    }

    private static boolean isSkillAttribute(Attribute attribute) {
        return unboundedAttributes().contains(attribute);
    }
}
