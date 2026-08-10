package org.zifeng.skilltree.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.fml.ModList;
import net.minecraft.core.Holder;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * 铁魔法（Iron's Spells 'n Spellbooks）兼容层：全部通过反射访问，编译期零依赖。
 * <p>
 * 设计目标：
 * <ul>
 *   <li>整合包 <b>没装</b> 铁魔法 → 所有方法安全返回默认值，绝不崩溃</li>
 *   <li>整合包 <b>装了</b> 铁魔法 → 通过 attribute modifier 给玩家加最大魔力/魔力恢复/吟唱缩减/流派强度</li>
 * </ul>
 * 铁魔法属性（源码研究结论，AttributeRegistry，全部 setSyncable(true)）：
 * <pre>
 * MAX_MANA         = MagicRangedAttribute(默认100, 0~1,000,000)
 * MANA_REGEN       = MagicPercentAttribute(默认1.0, 0~100)  恢复 = 最大魔力×MANA_REGEN×0.01
 * CAST_TIME_REDUCTION = MagicPercentAttribute(默认1.0, -100~100)  吟唱 = 原始×(2-softCap(x))
 * {school}_spell_power = MagicPercentAttribute(默认1.0, -100~100)  流派伤害倍率
 * school: fire/ice/lightning/holy/ender/blood/evocation/nature/eldritch
 * </pre>
 */
public final class IronSpellsCompat {
    private IronSpellsCompat() {
    }

    /** 铁魔法 mod id */
    public static final String MOD_ID = "irons_spellbooks";

    /** 修饰符 id 前缀（每个技能独立 id，可重复移除） */
    private static ResourceLocation mod(String path) {
        return ResourceLocation.fromNamespaceAndPath("zifeng_s_custom_skill_tree", path);
    }

    /** 9 个流派 school 名（对应 AttributeRegistry 中的 {school}_spell_power） */
    public static final String[] SCHOOLS = {
            "fire", "ice", "lightning", "holy", "ender", "blood", "evocation", "nature", "eldritch"
    };

    /** 缓存：已反射成功的属性（字段名 → Holder） */
    private static final Map<String, Holder<Attribute>> CACHE = new HashMap<>();

    /** 模组是否已加载 */
    public static boolean isLoaded() {
        return ModList.get() != null && ModList.get().isLoaded(MOD_ID);
    }

    /** 反射获取铁魔法 AttributeRegistry 中的属性（成功后缓存） */
    private static Holder<Attribute> getAttribute(String fieldName) {
        Holder<Attribute> cached = CACHE.get(fieldName);
        if (cached != null) {
            return cached;
        }
        if (!isLoaded()) {
            return null;
        }
        try {
            Class<?> clazz = Class.forName("io.redspace.ironsspellbooks.api.registry.AttributeRegistry");
            Field field = clazz.getField(fieldName);
            @SuppressWarnings("unchecked")
            Holder<Attribute> attr = (Holder<Attribute>) field.get(null);
            CACHE.put(fieldName, attr);
            return attr;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 给玩家属性加/更新乘算修饰符（ADD_MULTIPLIED_TOTAL），ratio 为 0 时移除 */
    private static void applyMultiplier(LivingEntity player, String fieldName, String modPath, double ratio) {
        Holder<Attribute> attr = getAttribute(fieldName);
        if (attr == null) {
            return;
        }
        AttributeInstance instance = player.getAttribute(attr);
        if (instance == null) {
            return;
        }
        ResourceLocation id = mod(modPath);
        instance.removeModifier(id);
        if (ratio > 0) {
            instance.addTransientModifier(new AttributeModifier(id, ratio, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /** 铁魔法最大魔力增幅（MAX_MANA，每级 +10% → ratio = level×0.1） */
    public static void applyMaxManaAmp(LivingEntity player, double ratio) {
        applyMultiplier(player, "MAX_MANA", "iron_mana_amp", ratio);
    }

    /** 铁魔法魔力恢复增幅（MANA_REGEN，每级 +40% → ratio = level×0.4） */
    public static void applyManaRegenAmp(LivingEntity player, double ratio) {
        applyMultiplier(player, "MANA_REGEN", "iron_mana_regen", ratio);
    }

    /** 铁魔法吟唱缩减（CAST_TIME_REDUCTION，每级 -10% 吟唱 → ratio = level×0.1） */
    public static void applyCastTimeReduction(LivingEntity player, double ratio) {
        applyMultiplier(player, "CAST_TIME_REDUCTION", "iron_cast_time", ratio);
    }

    /** 铁魔法法术冷却缩减（COOLDOWN_REDUCTION：属性值每级 +0.1 = 冷却每级 -10%；ADD_VALUE 绝对值累加） */
    public static void applyCooldownReduction(LivingEntity player, double perLevelValue) {
        Holder<Attribute> attr = getAttribute("COOLDOWN_REDUCTION");
        if (attr == null) {
            return;
        }
        AttributeInstance instance = player.getAttribute(attr);
        if (instance == null) {
            return;
        }
        ResourceLocation id = mod("iron_cooldown");
        instance.removeModifier(id);
        if (perLevelValue > 0) {
            instance.addTransientModifier(new AttributeModifier(
                    id, perLevelValue, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    /** 铁魔法流派法术强度增幅（{school}_spell_power，每级 +10% → ratio = level×0.1） */
    public static void applySchoolPowerAmp(LivingEntity player, String school, double ratio) {
        applyMultiplier(player, school.toUpperCase(java.util.Locale.ROOT) + "_SPELL_POWER", "iron_school_" + school, ratio);
    }
}
