package org.zifeng.skilltree.compat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.fml.ModList;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;

/**
 * 新生魔艺（Ars Nouveau）兼容层：全部通过反射访问，编译期零依赖。
 * <p>
 * 设计目标：
 * <ul>
 *   <li>整合包 <b>没装</b> 新生魔艺 → 所有方法安全返回默认值，绝不崩溃</li>
 *   <li>整合包 <b>装了</b> 新生魔艺 → 通过 {@link #applyManaAmp} 给玩家最大魔力加修饰符</li>
 * </ul>
 * 新生魔艺魔力机制（源码研究结论）：
 * <pre>
 * PerkAttributes.MAX_MANA（注册名 ars_nouveau.perk.max_mana）
 *   = RangedAttribute(0~10000).setSyncable(true)，已加到 Player
 * ManaUtil.calcMaxMana()：基础+雕纹+书等级 → 写入 transient modifier → 读 getValue()
 *   → 任何外部 modifier 都会自动计入最大魔力
 * </pre>
 */
public final class ArsNouveauCompat {
    private ArsNouveauCompat() {
    }

    /** 新生魔艺 mod id */
    public static final String MOD_ID = "ars_nouveau";

    /** 本模组给新生魔艺 MAX_MANA 属性加的修饰符 id（固定，可重复移除） */
    private static final ResourceLocation MANA_AMP_MOD = ResourceLocation.fromNamespaceAndPath("zifeng_s_custom_skill_tree", "mana_amp");
    /** 本模组给新生魔艺 MANA_REGEN_BONUS 属性加的修饰符 id（固定，可重复移除） */
    private static final ResourceLocation MANA_REGEN_MOD = ResourceLocation.fromNamespaceAndPath("zifeng_s_custom_skill_tree", "mana_regen");

    /** 缓存：PerkAttributes.MAX_MANA 静态字段（首次反射成功后缓存） */
    private static volatile Holder<Attribute> cachedMaxManaAttr;
    /** 缓存：PerkAttributes.MANA_REGEN_BONUS 静态字段（首次反射成功后缓存） */
    private static volatile Holder<Attribute> cachedManaRegenAttr;

    /** 模组是否已加载 */
    public static boolean isLoaded() {
        return ModList.get() != null && ModList.get().isLoaded(MOD_ID);
    }

    /**
     * 获取新生魔艺最大魔力属性（反射，成功后缓存）。
     *
     * @return 玩家最大魔力属性 Holder；模组未加载/类不存在 → null
     */
    public static Holder<Attribute> getMaxManaAttribute() {
        if (cachedMaxManaAttr != null) {
            return cachedMaxManaAttr;
        }
        if (!isLoaded()) {
            return null;
        }
        try {
            Class<?> clazz = Class.forName("com.hollingsworth.arsnouveau.api.perk.PerkAttributes");
            Field field = clazz.getField("MAX_MANA");
            @SuppressWarnings("unchecked")
            Holder<Attribute> attr = (Holder<Attribute>) field.get(null);
            cachedMaxManaAttr = attr;
            return attr;
        } catch (Throwable t) {
            // 反射失败（类名变动/加载异常）→ 保守返回 null，不影响游戏
            return null;
        }
    }

    /**
     * 应用魔力增幅修饰符：最大魔力 × (1 + bonusRatio)。
     * 新生魔艺 calcMaxMana 每次读 getValue()，ADD_MULTIPLIED_TOTAL 会自动计入；
     * 属性上限 10000（含模组自身 base），超过会被 RangedAttribute clamp，属正常保护。
     *
     * @param player     玩家实体
     * @param bonusRatio 增幅比例（0.5 = +50%）
     */
    public static void applyManaAmp(LivingEntity player, double bonusRatio) {
        Holder<Attribute> attr = getMaxManaAttribute();
        if (attr == null) {
            return;
        }
        AttributeInstance instance = player.getAttribute(attr);
        if (instance == null) {
            return;
        }
        instance.removeModifier(MANA_AMP_MOD);
        if (bonusRatio > 0) {
            instance.addTransientModifier(new AttributeModifier(
                    MANA_AMP_MOD, bonusRatio, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /**
     * 获取新生魔艺魔力恢复属性（反射，成功后缓存）。
     *
     * @return 玩家魔力恢复属性 Holder；模组未加载/类不存在 → null
     */
    public static Holder<Attribute> getManaRegenAttribute() {
        if (cachedManaRegenAttr != null) {
            return cachedManaRegenAttr;
        }
        if (!isLoaded()) {
            return null;
        }
        try {
            Class<?> clazz = Class.forName("com.hollingsworth.arsnouveau.api.perk.PerkAttributes");
            Field field = clazz.getField("MANA_REGEN_BONUS");
            @SuppressWarnings("unchecked")
            Holder<Attribute> attr = (Holder<Attribute>) field.get(null);
            cachedManaRegenAttr = attr;
            return attr;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 应用魔力恢复增幅修饰符：魔力恢复 × (1 + bonusRatio)。
     * 新生魔艺 getManaRegen 把基础恢复值写入 ADD_VALUE modifier 后读 getValue()，
     * 外部 ADD_MULTIPLIED_TOTAL 修饰符会乘算其上；属性上限 2000，超过被 clamp 属正常保护。
     *
     * @param player     玩家实体
     * @param bonusRatio 增幅比例（0.4 = +40%）
     */
    public static void applyManaRegenAmp(LivingEntity player, double bonusRatio) {
        Holder<Attribute> attr = getManaRegenAttribute();
        if (attr == null) {
            return;
        }
        AttributeInstance instance = player.getAttribute(attr);
        if (instance == null) {
            return;
        }
        instance.removeModifier(MANA_REGEN_MOD);
        if (bonusRatio > 0) {
            instance.addTransientModifier(new AttributeModifier(
                    MANA_REGEN_MOD, bonusRatio, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }
}
