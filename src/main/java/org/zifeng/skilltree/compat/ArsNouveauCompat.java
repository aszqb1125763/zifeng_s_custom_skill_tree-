package org.zifeng.skilltree.compat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.util.UUID;

/**
 * 新生魔艺（Ars Nouveau）兼容层：全部通过反射访问，编译期零依赖。
 * <p>
 * 设计目标：
 * <ul>
 *   <li>整合包 <b>没装</b> 新生魔艺 → 所有方法安全返回默认值，绝不崩溃</li>
 *   <li>整合包 <b>装了</b> 新生魔艺 → 通过 {@link #applyManaAmp} 给玩家最大魔力加修饰符</li>
 * </ul>
 * 新生魔艺魔力机制（1.20.1 4.12.7 源码/字节码确认）：
 * <pre>
 * PerkAttributes.MAX_MANA / MANA_REGEN_BONUS（注册名 ars_nouveau.perk.max_mana 等）
 *   = RegistryObject&lt;Attribute&gt;（2026-09-05 修复：1.20.1 静态字段是 RegistryObject 包装，
 *     不是裸 Attribute，也不是 1.21.1 的 Holder&lt;Attribute&gt;；反射必须 get() 解包！）
 * ManaUtil.calcMaxMana()：基础+雕纹+书等级 → 写入固定 UUID transient modifier → 读 getValue()
 *   → 任何外部 modifier 都会自动计入最大魔力（字节码 m_22135_ 确认）
 * </pre>
 */
public final class ArsNouveauCompat {
    private ArsNouveauCompat() {
    }

    /** 新生魔艺 mod id */
    public static final String MOD_ID = "ars_nouveau";

    /** 本模组给新生魔艺 MAX_MANA 属性加的修饰符 id（固定，可重复移除） */
    private static final ResourceLocation MANA_AMP_MOD = new ResourceLocation("zifeng_s_custom_skill_tree", "mana_amp");
    /** 本模组给新生魔艺 MANA_REGEN_BONUS 属性加的修饰符 id（固定，可重复移除） */
    private static final ResourceLocation MANA_REGEN_MOD = new ResourceLocation("zifeng_s_custom_skill_tree", "mana_regen");

    /** 修饰符 UUID（1.20.1 AttributeInstance.removeModifier 只接受 UUID；由 id 派生保证确定性） */
    private static final UUID MANA_AMP_UUID = UUID.nameUUIDFromBytes(MANA_AMP_MOD.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private static final UUID MANA_REGEN_UUID = UUID.nameUUIDFromBytes(MANA_REGEN_MOD.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));

    /** 缓存：PerkAttributes.MAX_MANA 静态字段解包后的 Attribute（首次反射成功后缓存）；1.20.1 需 RegistryObject.get() */
    private static volatile Attribute cachedMaxManaAttr;
    /** 缓存：PerkAttributes.MANA_REGEN_BONUS 静态字段解包后的 Attribute（首次反射成功后缓存） */
    private static volatile Attribute cachedManaRegenAttr;

    /** 模组是否已加载 */
    public static boolean isLoaded() {
        return ModList.get() != null && ModList.get().isLoaded(MOD_ID);
    }

    /**
     * 反射读取 PerkAttributes 的 RegistryObject&lt;Attribute&gt; 静态字段并解包（成功后缓存）。
     * 1.20.1 静态字段类型为 RegistryObject&lt;Attribute&gt;，直接强转 Attribute 会 ClassCastException
     * （被 catch 吞掉导致功能静默失效——2026-09-05 已修复）。
     *
     * @return 解包后的 Attribute；模组未加载/字段缺失/注册未就绪 → null
     */
    private static Attribute resolveAttribute(String fieldName) {
        if (!isLoaded()) {
            return null;
        }
        try {
            Class<?> clazz = Class.forName("com.hollingsworth.arsnouveau.api.perk.PerkAttributes");
            Field field = clazz.getField(fieldName);
            Object raw = field.get(null);
            if (raw instanceof RegistryObject<?> registryObject) {
                Object value = registryObject.get();
                if (value instanceof Attribute attr) {
                    return attr;
                }
            }
            return null;
        } catch (Throwable t) {
            // 反射失败（类名变动/注册未就绪抛异常）→ 保守返回 null，不影响游戏
            return null;
        }
    }

    /**
     * 获取新生魔艺最大魔力属性（反射解包，成功后缓存）。
     *
     * @return 玩家最大魔力属性；模组未加载/类不存在 → null
     */
    public static Attribute getMaxManaAttribute() {
        if (cachedMaxManaAttr == null) {
            cachedMaxManaAttr = resolveAttribute("MAX_MANA");
        }
        return cachedMaxManaAttr;
    }

    /**
     * 应用魔力增幅修饰符：最大魔力 × (1 + bonusRatio)。
     * 新生魔艺 calcMaxMana 每次读 getValue()，MULTIPLY_TOTAL 会自动计入；
     * 属性上限 10000（含模组自身 base），超过会被 RangedAttribute clamp，属正常保护。
     *
     * @param player     玩家实体
     * @param bonusRatio 增幅比例（0.5 = +50%）
     */
    public static void applyManaAmp(LivingEntity player, double bonusRatio) {
        Attribute attr = getMaxManaAttribute();
        if (attr == null) {
            return;
        }
        AttributeInstance instance = player.getAttribute(attr);
        if (instance == null) {
            return;
        }
        instance.removeModifier(MANA_AMP_UUID);
        if (bonusRatio > 0) {
            instance.addTransientModifier(new AttributeModifier(
                    MANA_AMP_UUID, "mana_amp", bonusRatio, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    /**
     * 获取新生魔艺魔力恢复属性（反射解包，成功后缓存）。
     *
     * @return 玩家魔力恢复属性；模组未加载/类不存在 → null
     */
    public static Attribute getManaRegenAttribute() {
        if (cachedManaRegenAttr == null) {
            cachedManaRegenAttr = resolveAttribute("MANA_REGEN_BONUS");
        }
        return cachedManaRegenAttr;
    }

    /**
     * 应用魔力恢复增幅修饰符：魔力恢复 × (1 + bonusRatio)。
     * 新生魔艺 getManaRegen 把基础恢复值写入 ADD_NUMBER modifier 后读 getValue()，
     * 外部 MULTIPLY_TOTAL 修饰符会乘算其上；属性上限 2000，超过被 clamp 属正常保护。
     *
     * @param player     玩家实体
     * @param bonusRatio 增幅比例（0.4 = +40%）
     */
    public static void applyManaRegenAmp(LivingEntity player, double bonusRatio) {
        Attribute attr = getManaRegenAttribute();
        if (attr == null) {
            return;
        }
        AttributeInstance instance = player.getAttribute(attr);
        if (instance == null) {
            return;
        }
        instance.removeModifier(MANA_REGEN_UUID);
        if (bonusRatio > 0) {
            instance.addTransientModifier(new AttributeModifier(
                    MANA_REGEN_UUID, "mana_regen", bonusRatio, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }
}
