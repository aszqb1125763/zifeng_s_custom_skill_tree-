package org.zifeng.skilltree.compat;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.fml.ModList;

import java.util.HashMap;
import java.util.Map;

/**
 * Goety（诡术）兼容层 —— 让本模组的魔法技能同样加成 Goety 的法术。
 *
 * <h2>一、为什么用「属性注册表按名字查」而不是像铁魔法那样反射字段</h2>
 * 铁魔法兼容层（{@link IronSpellsCompat}）是反射 {@code AttributeRegistry} 的静态字段，
 * 因为铁魔法把属性暴露成了 {@code RegistryObject} 字段。
 * <p><b>但 Goety 1.20.1 的 {@code ModAttributes} 没有任何公开字段</b>
 * （已用 javap 确认：1.20.1 有 0 个公开属性字段，1.21.1 才有 28 个 DeferredHolder 字段）。
 * <p>所以这里统一改为<b>按注册名从属性注册表里查</b> —— 好处是
 * <b>两个版本的属性注册名完全一致</b>（已核对 28 个名字逐字相同），
 * 实现只需在「取注册表」这一行做平台分支，其余逻辑双版本同构，也不需要反射字段。
 *
 * <h2>二、属性语义（Goety 源码确认，不是猜测）</h2>
 * <pre>
 * // 定义（ModAttributes）
 * SPELL_POTENCY     = new RangedAttribute("...spell_potency",     0.0D, 0.0D, 2048.0D)
 * {school}_POTENCY  = SpellAttribute.potency(type,                0.0D, 0.0D, 2048.0D)
 * CASTING_SPEED     = new RangedAttribute("...casting_speed",     0.0D, -1.0D, 1.0D)
 * COOLDOWN_DISCOUNT = new RangedAttribute("...cooldown_discount", 0.0D, -1.0D, 1.0D)
 * SOUL_DISCOUNT     = new RangedAttribute("...soul_discount",     0.0D, -1.0D, 1.0D)
 * {school}_DISCOUNT = SpellAttribute.discount(type,               0.0D, -1.0D, 1.0D)
 *
 * // 用法（ModAttributes / ISpell）
 * getPotency(e)          { return (int) e.getAttributeValue(SPELL_POTENCY); }        // ← flat 整数
 * getCastingSpeed(e)     { return 1.0D - e.getAttributeValue(CASTING_SPEED); }       // ← 1-x
 * getCooldownDiscount(e) { return 1.0D - e.getAttributeValue(COOLDOWN_DISCOUNT); }   // ← 1-x
 * getSoulDiscount(e, s)  { return 1.0D - e.getAttributeValue(attr); }                // ← 1-x
 * </pre>
 * 由此得出两条<b>关键结论</b>：
 * <ol>
 *   <li><b>强度类（potency）是 flat 整数加值</b>，直接加到法术的 potency 上（默认 0，上限 2048）。
 *       ⚠️ 与铁魔法的 {@code {school}_spell_power}（百分比倍率、MULTIPLY_TOTAL）<b>语义完全不同</b>，
 *       所以<b>不能照搬铁魔法「每级 +10%」的写法</b>——那会瞬间顶到 2048。</li>
 *   <li><b>减量类（discount / casting_speed）是「绝对减量比例」</b>，
 *       属性值 0.5 表示吟唱/冷却/消耗变成原来的一半，取值范围被限制在 <b>-1 ~ 1</b>。
 *       ⚠️ 也就是说减量类的<b>硬上限是 1.0 = 100% 减免</b>（灵魂全免 + 瞬发 + 无冷却）。
 *       加过头会被属性系统 clamp 到 1.0 —— 会直接破坏平衡，所以本类对减量类贡献统一封顶
 *       {@link #DISCOUNT_CAP}。</li>
 * </ol>
 * 所有属性一律使用 {@link AttributeModifier.Operation#ADDITION}（对应 Goety 源码里的默认 0 + 直接累加语义）。
 *
 * <h2>三、为什么要封顶</h2>
 * {@code IRON_CAST_TIME} / {@code IRON_COOLDOWN} 是<b>既有的 100 级技能</b>（合并了 Goety 的
 * casting_speed / cooldown_discount）。若按「每级 +0.01」线性给到 100 级就是 1.0 = 100%，
 * 等于满级后所有 Goety 法术瞬发且无冷却。故对外贡献一律 {@code Math.min(0.8, level × 0.01)}。
 */
public final class GoetyCompat {

    private GoetyCompat() {
    }

    /** Goety 的 mod id */
    public static final String MOD_ID = "goety";

    /**
     * 减量类属性的最大贡献值。
     * <p>Goety 的减量属性范围是 -1 ~ 1，1.0 即「100% 减免」= 灵魂全免 / 瞬发 / 无冷却。
     * 封顶 0.8（80% 减免）避免单一模组兼容就把 Goety 的整套法术经济打穿。
     */
    public static final double DISCOUNT_CAP = 0.8;

    /**
     * 每「传入等级」提供的减量比例。
     *
     * <p>⚠️ 这里的 level 由调用方传入，可能是 {@code SkillEffects.effLevel} 的结果 ——
     * 若该技能受等级压缩则为「已学等级 × 10」。所以对外表现为两种口径：
     * <ul>
     *   <li><b>魂能节流 / 9 流派精通</b>（MAGIC 列，受 ×10 压缩）：<b>每显示级 -10%</b>，8 级即抵 0.8 封顶</li>
     *   <li><b>吟唱 / 冷却缩减</b>（不受压缩）：<b>每显示级 -1%</b>，80 级抵 0.8 封顶</li>
     * </ul>
     * 两者的等级上限均已按此对齐，保证「满级刚好抵到封顶、没有无收益的等级」
     * （见 {@code Skills.getMagicMaxPoints}；2026-09-20 修正前分别是 80/100 级，存在大量无效等级）。
     */
    public static final double DISCOUNT_PER_LEVEL = 0.01;

    /**
     * 9 个流派 school 名（对应属性 {@code goety:<school>_potency} / {@code goety:<school>_discount}）。
     * <p>⚠️ 与铁魔法流派<b>毫无对应关系</b>：铁魔法是 fire/ice/lightning/holy/ender/blood/evocation/nature/eldritch，
     * Goety 是下列 9 个，因此不能复用铁魔法的流派技能。
     */
    public static final String[] SCHOOLS = {
            "abyss", "frost", "geomancy", "necromancy", "nether", "storm", "void", "wild", "wind"
    };

    /** 属性缓存：注册名 → Holder（查不到时也会记入 {@link #MISS}，避免反复查） */
    private static final Map<String, Holder<Attribute>> CACHE = new HashMap<>();
    /** 查不到的注册名（负缓存，避免每次 applyAll 都重复查注册表） */
    private static final java.util.Set<String> MISS = new java.util.HashSet<>();

    /** 模组是否已加载 */
    public static boolean isLoaded() {
        return ModList.get() != null && ModList.get().isLoaded(MOD_ID);
    }

    /**
     * 按注册名取 Goety 的属性（{@code goety:<name>}）。
     *
     * <p>1.21.1 的 {@code ModAttributes} 虽然有 28 个公开 {@code DeferredHolder} 字段，
     * 但为了与 1.20.1（那里没有任何公开字段，只能查注册表）保持<b>同构实现</b>，
     * 这里同样按注册名从 {@code BuiltInRegistries.ATTRIBUTE} 查。
     *
     * @return 属性 Holder；未装 Goety 或名字不存在 → {@code null}（调用方直接跳过）
     */
    private static Holder<Attribute> get(String name) {
        Holder<Attribute> cached = CACHE.get(name);
        if (cached != null) {
            return cached;
        }
        if (MISS.contains(name) || !isLoaded()) {
            return null;
        }
        try {
            // ⚠️ 1.21.1 平台差异：这里必须用 getHolder（返回 Optional<Holder.Reference>）。
            //   Registry.get(ResourceLocation) 在 1.21 返回的是【裸类型】而不是 Optional
            //   （已 javap 确认：public abstract T get(ResourceLocation)），直接链 .map 会编译不过。
            var found = BuiltInRegistries.ATTRIBUTE
                    .getHolder(ResourceLocation.fromNamespaceAndPath(MOD_ID, name));
            if (found.isEmpty()) {
                MISS.add(name);
                return null;
            }
            Holder<Attribute> h = found.get();
            CACHE.put(name, h);
            return h;
        } catch (Throwable t) {
            // 注册表尚未就绪等异常 → 记负缓存（不反复重试），返回 null 让功能静默跳过
            MISS.add(name);
            return null;
        }
    }

    /**
     * 修饰符 id（每个技能独立，可重复移除；与本项目其它属性修饰符同一套命名）。
     *
     * <p>⚠️ 1.21.1 平台差异：1.20.5+ 的 {@code AttributeInstance} 改用
     * {@link ResourceLocation} 标识修饰符（1.20.1 用 {@link java.util.UUID}）。
     * 本项目其它兼容层（如 {@code IronSpellsCompat}）也是同一套区分方式。
     */
    private static ResourceLocation mod(String path) {
        return ResourceLocation.fromNamespaceAndPath("zifeng_s_custom_skill_tree", path);
    }

    /**
     * 加/更新一个「加值」修饰符（value 为 0 时移除）。
     * <p>全部属性都用加值语义：Goety 的属性默认值都是 0，源码里就是直接累加/直接读取。
     *
     * <p>⚠️ 1.21.1 平台差异：修饰符操作枚举做了改名
     * （1.20.1 的 {@code ADDITION} → 1.21.1 的 {@code ADD_VALUE}，语义相同）；
     * 修饰符 id 也从 {@link java.util.UUID} 改为 {@link ResourceLocation}。
     */
    private static void applyAddition(LivingEntity player, String attrName, String modPath, double value) {
        Holder<Attribute> attr = get(attrName);
        if (attr == null) {
            return;
        }
        AttributeInstance instance = player.getAttribute(attr);
        if (instance == null) {
            return; // 该实体没有此属性（如非玩家实体）
        }
        ResourceLocation id = mod(modPath);
        instance.removeModifier(id);
        if (value != 0) {
            instance.addTransientModifier(new AttributeModifier(
                    id, value, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    // =======================================================================
    // 减量类（施法速度 / 冷却缩减 / 灵魂折扣）—— 统一封顶
    // =======================================================================

    /**
     * 施法速度（合并进既有技能 IRON_CAST_TIME）。
     * <p>Goety：{@code 吟唱时长 × (1 - 属性值)}，故属性值即「减免比例」。
     * @param level 该技能的已学等级（按每级 {@link #DISCOUNT_PER_LEVEL} 折算，并封顶）
     */
    public static void applyCastingSpeed(LivingEntity player, int level) {
        applyAddition(player, "casting_speed", "goety_casting_speed", discountOf(level));
    }

    /** 冷却缩减（合并进既有技能 IRON_COOLDOWN）。Goety：{@code 冷却 × (1 - 属性值)} */
    public static void applyCooldownDiscount(LivingEntity player, int level) {
        applyAddition(player, "cooldown_discount", "goety_cooldown", discountOf(level));
    }

    /** 通用灵魂消耗折扣（Goety 全流派通用）。Goety：{@code 消耗 × (1 - 属性值)} */
    public static void applySoulDiscount(LivingEntity player, int level) {
        applyAddition(player, "soul_discount", "goety_soul_discount", discountOf(level));
    }

    /** 某流派的灵魂消耗折扣（{@code goety:<school>_discount}） */
    public static void applySchoolDiscount(LivingEntity player, String school, int level) {
        applyAddition(player, school + "_discount", "goety_" + school + "_discount", discountOf(level));
    }

    /** 等级 → 减量比例（每级 {@link #DISCOUNT_PER_LEVEL}，封顶 {@link #DISCOUNT_CAP}） */
    private static double discountOf(int level) {
        if (level <= 0) {
            return 0;
        }
        return Math.min(DISCOUNT_CAP, level * DISCOUNT_PER_LEVEL);
    }

    // =======================================================================
    // 强度类（flat 整数加值）
    // =======================================================================

    /**
     * 通用法术强度（{@code spell_potency}，所有流派共用）。
     * <p>Goety：{@code (int) getAttributeValue(...)} 直接加到法术的 potency 上。
     * @param level 已学等级（每级 +1 → 满级即等级数）
     */
    public static void applySpellPotency(LivingEntity player, int level) {
        applyAddition(player, "spell_potency", "goety_spell_potency",
                level <= 0 ? 0 : level);
    }

    /** 某流派的法术强度（{@code goety:<school>_potency}） */
    public static void applySchoolPotency(LivingEntity player, String school, int level) {
        applyAddition(player, school + "_potency", "goety_" + school + "_potency",
                level <= 0 ? 0 : level);
    }
}
