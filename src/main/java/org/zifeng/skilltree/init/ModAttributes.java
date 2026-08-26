package org.zifeng.skilltree.init;

import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
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
    public static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(ForgeRegistries.ATTRIBUTES, SkillTreeMod.MOD_ID);

    /** 物理减伤（0~1）：BODY 体魄强化 / AMP_ARMOR 防御强化 提供 */
    public static final RegistryObject<Attribute> DAMAGE_REDUCTION = ATTRIBUTES.register("damage_reduction",
            () -> new RangedAttribute("attribute." + SkillTreeMod.MOD_ID + ".damage_reduction", 0.0, 0.0, 1.0)
                    .setSyncable(true));

    /** 挖掘效率（0~1024）：MINING 采掘熟稔 / AMP_MINING 挖掘强化 提供。
     *  ⚠️ 1.20.1 原版 Attributes 无 MINING_EFFICIENCY（NeoForge 1.21 才合入原版），
     *     故 1.20.1 移植版注册自定义属性替代，语义与效果一致（加数属性直接反映实际挖掘加速）。 */
    public static final RegistryObject<Attribute> MINING_EFFICIENCY = ATTRIBUTES.register("mining_efficiency",
            () -> new RangedAttribute("attribute." + SkillTreeMod.MOD_ID + ".mining_efficiency", 0.0, 0.0, 1024.0)
                    .setSyncable(true));
}
