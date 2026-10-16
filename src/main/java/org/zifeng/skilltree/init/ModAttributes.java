package org.zifeng.skilltree.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.zifeng.skilltree.SkillTreeMod;

/**
 * 自定义属性注册。
 * <p>
 * {@link #DAMAGE_REDUCTION} 物理减伤（0~1，100% 封顶）：
 * 替代原 CombatRulesMixin 对原版护甲减伤计算的全局修改（避免与其他模组冲突）。
 * 机制：护甲达到原版减伤上限（80%）后，技能继续投入的点数转化为该属性，
 * 由 {@link org.zifeng.skilltree.event.UltimateEvents} 在伤害事件中按比例减免物理伤害。
 * 与原版护甲减伤为独立乘算层，不改动任何原版代码。
 */
public class ModAttributes {
    public static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(Registries.ATTRIBUTE, SkillTreeMod.MOD_ID);

    /** 物理减伤（0~1）：BODY 体魄强化 / AMP_ARMOR 防御强化 提供 */
    public static final DeferredHolder<Attribute, Attribute> DAMAGE_REDUCTION = ATTRIBUTES.register("damage_reduction",
            () -> new RangedAttribute("attribute." + SkillTreeMod.MOD_ID + ".damage_reduction", 0.0, 0.0, 1.0)
                    .setSyncable(true));
}
