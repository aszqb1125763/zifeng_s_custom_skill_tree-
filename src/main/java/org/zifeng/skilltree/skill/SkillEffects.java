package org.zifeng.skilltree.skill;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.zifeng.skilltree.SkillTreeMod;
import org.zifeng.skilltree.data.PlayerSkillRecord;

import java.util.List;

/**
 * 技能效果应用（统一公式）：
 * <pre>
 *   最终属性 = 全部基础固定数值总和 × (1 + 全部特殊百分比增幅总和)
 * </pre>
 * 实现方式（利用原版 AttributeModifier 计算顺序，天然符合统一公式）：
 * <ul>
 *   <li>基础技能固定值 → ADD_VALUE 修饰符（叠加到 baseValue 上）</li>
 *   <li>增幅技能百分比 → ADD_MULTIPLIED_TOTAL 修饰符（乘算 1+Σ增幅）</li>
 *   <li>全能精通 → 额外给所有属性一个 ADD_MULTIPLIED_TOTAL +25%</li>
 * </ul>
 * 原版计算顺序：base + ADD_VALUE，再 ×(1+ADD_MULTIPLIED_BASE)，再 ×(1+ADD_MULTIPLIED_TOTAL)
 * 恰好实现：基础总和 × (1+增幅总和)。
 */
public final class SkillEffects {
    private SkillEffects() {
    }

    public static final ResourceLocation MASTER_MOD = ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "skill_master");

    /** 杀戮光环·伤害：每级 +5% 攻击伤害倍率（ADD_MULTIPLIED_TOTAL，独立于基础/增幅技能） */
    public static final ResourceLocation AURA_DAMAGE_MOD = ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "aura_damage_mult");

    /** 浴血奋战：常驻攻击力增幅（ADD_MULTIPLIED_TOTAL） */
    public static final ResourceLocation BLOOD_ATTACK_MOD = ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "blood_attack_bonus");

    /** 浴血奋战：常驻最大生命增幅（ADD_MULTIPLIED_TOTAL） */
    public static final ResourceLocation BLOOD_HEALTH_MOD = ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "blood_health_bonus");

    // ============ 基础技能：技能ID → [属性, 单点固定值] ============
    private static final List<BaseSkill> BASE_SKILLS = List.of(
            // 生命强化：每点 +2 生命（从原体魄拆出，纯生命成长）
            new BaseSkill(Skills.BODY_HP, Attributes.MAX_HEALTH, 2.0),
            // 体魄强化：护甲 + 物理减伤（生命已拆出，护甲增幅保持原样）
            new BaseSkill(Skills.BODY, Attributes.ARMOR, 0.2),
            // 物理减伤：护甲减伤原版封顶 80% 后继续叠的独立减伤层（替代原 CombatRulesMixin，零冲突）
            new BaseSkill(Skills.BODY, org.zifeng.skilltree.init.ModAttributes.DAMAGE_REDUCTION, 0.0005),
            new BaseSkill(Skills.TOUGH, Attributes.ARMOR_TOUGHNESS, 0.3),
            new BaseSkill(Skills.TOUGH, Attributes.KNOCKBACK_RESISTANCE, 0.001),
            new BaseSkill(Skills.BLADE, Attributes.ATTACK_DAMAGE, 0.4),
            new BaseSkill(Skills.ATTACK_SPEED, Attributes.ATTACK_SPEED, 0.02),
            // 挖掘速度用原版 Attributes.MINING_EFFICIENCY（NeoForge 合入的加数属性，直接加到工具速度上；BLOCK_BREAK_SPEED 是乘数语义不对）
            new BaseSkill(Skills.MINING, Attributes.MINING_EFFICIENCY, 0.3),
            new BaseSkill(Skills.MOVE, Attributes.MOVEMENT_SPEED, 0.005),
            new BaseSkill(Skills.LUCK, Attributes.LUCK, 0.1),
            new BaseSkill(Skills.JUMP, Attributes.JUMP_STRENGTH, 0.01),
            new BaseSkill(Skills.FLY, Attributes.FLYING_SPEED, 0.005),
            new BaseSkill(Skills.SWIM, net.neoforged.neoforge.common.NeoForgeMod.SWIM_SPEED, 0.005), // 游泳用 NeoForge SWIM_SPEED
            // 杀戮光环·速度：不再加成攻速属性（改由 AuraEvents 直接控制光环攻击间隔，性能优化）
            // 防御强化：护甲倍率在护甲原版上限（30）内无意义 → 改为直接加物理减伤（每点 +0.05%），独立乘算层不依赖护甲
            new BaseSkill(Skills.AMP_ARMOR, org.zifeng.skilltree.init.ModAttributes.DAMAGE_REDUCTION, 0.0005)
    );

    // ============ 增幅技能：技能ID → [属性, 单点百分比(小数)]（与基础技能一一对应） ============
    private static final List<BaseSkill> AMPLIFY_SKILLS = List.of(
            new BaseSkill(Skills.AMP_HP, Attributes.MAX_HEALTH, 0.005),
            new BaseSkill(Skills.AMP_TOUGH, Attributes.ARMOR_TOUGHNESS, 0.005),
            new BaseSkill(Skills.AMP_TOUGH, Attributes.KNOCKBACK_RESISTANCE, 0.005),
            new BaseSkill(Skills.AMP_LUCK, Attributes.LUCK, 0.005),
            new BaseSkill(Skills.AMP_DAMAGE, Attributes.ATTACK_DAMAGE, 0.005),
            new BaseSkill(Skills.AMP_ATTACK_SPEED, Attributes.ATTACK_SPEED, 0.004),
            new BaseSkill(Skills.AMP_MINING, Attributes.MINING_EFFICIENCY, 0.006),
            new BaseSkill(Skills.AMP_MOVE, Attributes.MOVEMENT_SPEED, 0.005),
            new BaseSkill(Skills.AMP_JUMP, Attributes.JUMP_STRENGTH, 0.005),
            new BaseSkill(Skills.AMP_FLY, Attributes.FLYING_SPEED, 0.005),
            new BaseSkill(Skills.AMP_SWIM, net.neoforged.neoforge.common.NeoForgeMod.SWIM_SPEED, 0.005)
    );

    private record BaseSkill(String skillId, Holder<Attribute> attribute, double perPoint) {
    }

    /**
     * 本地计算某属性经技能加成后的总值（客户端属性面板用，不依赖服务端属性同步，实时生效）：
     * <pre>(玩家基础值 + Σ基础固定值) × (1 + Σ增幅倍率 + 全能精通25%)</pre>
     * 与 {@link #applyAll} 服务端逻辑保持一致（基础 ADD_VALUE、增幅 ADD_MULTIPLIED_TOTAL）。
     * 注意：不含装备/药水等外部修饰符。
     */
    public static double getComputedValue(net.minecraft.world.entity.player.Player player,
                                          Holder<Attribute> attribute, PlayerSkillRecord record) {
        AttributeInstance instance = player.getAttribute(attribute);
        double base = instance != null ? instance.getBaseValue() : 0;
        double add = 0;
        double mult = 0;
        for (BaseSkill b : BASE_SKILLS) {
            if (b.attribute().equals(attribute)) {
                int points = record.isEnabled(b.skillId()) ? record.getActiveLevel(b.skillId()) : 0;
                add += points * b.perPoint();
            }
        }
        for (BaseSkill a : AMPLIFY_SKILLS) {
            if (a.attribute().equals(attribute)) {
                int points = record.isEnabled(a.skillId()) ? record.getActiveLevel(a.skillId()) : 0;
                mult += points * a.perPoint();
            }
        }
        boolean master = record.getLearnedPoints(Skills.ULT_MASTER) > 0 && record.isEnabled(Skills.ULT_MASTER);
        if (master) {
            mult += org.zifeng.skilltree.Config.MASTER_BONUS.get();
        }
        // 杀戮光环·伤害：每级 +5% 攻击伤害（乘算，独立修饰符；0 级自动归零）
        if (Attributes.ATTACK_DAMAGE.equals(attribute)) {
            int auraDmg = record.isEnabled(Skills.AURA_DAMAGE) ? record.getActiveLevel(Skills.AURA_DAMAGE) : 0;
            mult += auraDmg * org.zifeng.skilltree.Config.AURA_DAMAGE_MULTIPLIER_PER_LEVEL.get();
        }
        // 浴血奋战：常驻攻击/生命 +50%（点亮且启用才加）
        boolean blood = record.getLearnedPoints(Skills.ULT_BLOOD) > 0 && record.isEnabled(Skills.ULT_BLOOD);
        if (blood) {
            if (Attributes.ATTACK_DAMAGE.equals(attribute)) {
                mult += org.zifeng.skilltree.Config.BLOOD_ATTACK_BONUS.get();
            }
            if (Attributes.MAX_HEALTH.equals(attribute)) {
                mult += org.zifeng.skilltree.Config.BLOOD_HEALTH_BONUS.get();
            }
        }
        return (base + add) * (1 + mult);
    }

    /**
     * 重新应用全部属性修饰符（幂等，可安全重挂）。关闭/调低生效等级会立即移除对应修饰符。
     * 每个技能的修饰符 UUID 独立（skillId 区分），避免同属性技能互相覆盖。
     */
    public static void applyAll(ServerPlayer player, PlayerSkillRecord record) {
        // 1. 基础固定值（ADD_VALUE）——关闭的技能 points=0 自动移除残留
        for (BaseSkill base : BASE_SKILLS) {
            int points = record.isEnabled(base.skillId()) ? record.getActiveLevel(base.skillId()) : 0;
            applyAddValue(player, base.attribute(), baseId(base.skillId()), points * base.perPoint());
        }
        // 2. 增幅百分比（ADD_MULTIPLIED_TOTAL）——同上
        for (BaseSkill amp : AMPLIFY_SKILLS) {
            int points = record.isEnabled(amp.skillId()) ? record.getActiveLevel(amp.skillId()) : 0;
            applyMultiplier(player, amp.attribute(), ampId(amp.skillId()), points * amp.perPoint());
        }
        // 3. 全能精通：所有受影响的属性 +增幅（Config 可调，默认 25%；未解锁/关闭 → amount=0 移除）
        boolean master = record.getLearnedPoints(Skills.ULT_MASTER) > 0 && record.isEnabled(Skills.ULT_MASTER);
        double masterAmount = master ? org.zifeng.skilltree.Config.MASTER_BONUS.get() : 0;
        for (BaseSkill base : BASE_SKILLS) {
            applyMultiplier(player, base.attribute(), MASTER_MOD, masterAmount);
        }
        // 3.5 杀戮光环·伤害：每级 +5% 攻击伤害（乘算，独立修饰符；关闭/未学 → amount=0 自动移除）
        int auraDmg = record.isEnabled(Skills.AURA_DAMAGE) ? record.getActiveLevel(Skills.AURA_DAMAGE) : 0;
        applyMultiplier(player, Attributes.ATTACK_DAMAGE, AURA_DAMAGE_MOD,
                auraDmg * org.zifeng.skilltree.Config.AURA_DAMAGE_MULTIPLIER_PER_LEVEL.get());
        // 3.6 浴血奋战：常驻攻击 +50%、生命 +50%（点亮且启用才加；关闭/未学 → amount=0 自动移除）
        boolean blood = record.getLearnedPoints(Skills.ULT_BLOOD) > 0 && record.isEnabled(Skills.ULT_BLOOD);
        applyMultiplier(player, Attributes.ATTACK_DAMAGE, BLOOD_ATTACK_MOD,
                blood ? org.zifeng.skilltree.Config.BLOOD_ATTACK_BONUS.get() : 0);
        applyMultiplier(player, Attributes.MAX_HEALTH, BLOOD_HEALTH_MOD,
                blood ? org.zifeng.skilltree.Config.BLOOD_HEALTH_BONUS.get() : 0);
        // 4. 生命值同步（避免加血上限后血量不涨）
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null && player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    /** 基础技能修饰符 id（按技能区分） */
    private static ResourceLocation baseId(String skillId) {
        return ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "skill_base_" + skillId);
    }

    /** 增幅技能修饰符 id（按技能区分） */
    private static ResourceLocation ampId(String skillId) {
        return ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "skill_amp_" + skillId);
    }

    private static void applyAddValue(ServerPlayer player, Holder<Attribute> attribute, ResourceLocation id, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        instance.removeModifier(id);
        if (amount != 0) {
            instance.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    private static void applyMultiplier(ServerPlayer player, Holder<Attribute> attribute, ResourceLocation id, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        instance.removeModifier(id);
        if (amount != 0) {
            instance.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /** 当前生命恢复速率（基础 + 再生体魄 + 生命涌泉增幅），用于每 tick 回血。尊重生效等级与开关 */
    public static double getRegenPerSecond(PlayerSkillRecord record) {
        double base = record.isEnabled(Skills.REGEN)
                ? record.getActiveLevel(Skills.REGEN) * 0.1 : 0;
        if (base <= 0) {
            return 0;
        }
        double amp = record.isEnabled(Skills.AMP_REGEN)
                ? record.getActiveLevel(Skills.AMP_REGEN) * 0.008 : 0;
        return base * (1 + amp);
    }

    /** 怪物掉落倍率（掉落增幅；尊重生效等级与开关） */
    public static double getDropMultiplier(PlayerSkillRecord record) {
        double points = record.isEnabled(Skills.AMP_DROP)
                ? record.getActiveLevel(Skills.AMP_DROP) : 0;
        return 1 + points * 0.04;
    }

    /** 经验获取倍率（掉落增幅；尊重生效等级与开关） */
    public static double getExperienceMultiplier(PlayerSkillRecord record) {
        double points = record.isEnabled(Skills.AMP_DROP)
                ? record.getActiveLevel(Skills.AMP_DROP) : 0;
        return 1 + points * 0.05;
    }

    /** 工具耐久损耗减免（采掘熟稔；尊重生效等级与开关）：每级 +20%，封顶 100%（达到后工具不消耗耐久） */
    public static double getToolDurabilityReduction(PlayerSkillRecord record) {
        double points = record.isEnabled(Skills.MINING)
                ? record.getActiveLevel(Skills.MINING) : 0;
        return Math.min(1.0, points * 0.2);
    }

    /** 技能点获取速度（全能精通 -20% 默认，Config 可调，需开关启用） */
    public static double getSkillPointRate(PlayerSkillRecord record) {
        return record.getLearnedPoints(Skills.ULT_MASTER) > 0 && record.isEnabled(Skills.ULT_MASTER)
                ? org.zifeng.skilltree.Config.MASTER_SKILL_POINT_RATE.get() : 1.0;
    }

    // ============ 暴击 / 吸血 / 治愈光环（事件驱动，非属性，尊重技能开关） ============

    /** 暴击几率（0~1，暴击精通，100% 封顶；需技能启用） */
    public static double getCritChance(PlayerSkillRecord record) {
        if (!record.isEnabled(Skills.CRIT)) {
            return 0;
        }
        double chance = record.getActiveLevel(Skills.CRIT) * org.zifeng.skilltree.Config.CRIT_CHANCE_PER_POINT.get();
        return Math.min(1.0, chance);
    }

    /** 暴击伤害倍率（暴击基础倍率 × (1 + 暴击增幅)；需增幅启用） */
    public static double getCritMultiplier(PlayerSkillRecord record) {
        double base = org.zifeng.skilltree.Config.CRIT_DAMAGE_BASE.get();
        double amp = record.isEnabled(Skills.AMP_CRIT)
                ? record.getActiveLevel(Skills.AMP_CRIT) * org.zifeng.skilltree.Config.CRIT_DAMAGE_PER_POINT.get() : 0;
        return base * (1 + amp);
    }

    /** 吸血率（生命汲取 × (1 + 吸血增幅)；需技能启用） */
    public static double getLifestealRate(PlayerSkillRecord record) {
        double rate = record.isEnabled(Skills.LIFESTEAL)
                ? Math.min(1.0, record.getActiveLevel(Skills.LIFESTEAL) * org.zifeng.skilltree.Config.LIFESTEAL_PER_POINT.get()) : 0;
        if (rate <= 0) {
            return 0;
        }
        double amp = record.isEnabled(Skills.AMP_LIFESTEAL)
                ? record.getActiveLevel(Skills.AMP_LIFESTEAL) * org.zifeng.skilltree.Config.LIFESTEAL_AMP_PER_POINT.get() : 0;
        return rate * (1 + amp);
    }

    /** 荆棘反伤值（荆棘反伤 × (1 + 荆棘强化)；需技能启用） */
    public static double getThornsDamage(PlayerSkillRecord record) {
        double base = record.isEnabled(Skills.THORNS)
                ? record.getActiveLevel(Skills.THORNS) * 0.05 : 0;
        if (base <= 0) {
            return 0;
        }
        double amp = record.isEnabled(Skills.AMP_THORNS)
                ? record.getActiveLevel(Skills.AMP_THORNS) * 0.004 : 0;
        return base * (1 + amp);
    }

    /** 破甲增伤比例（0~1：破甲精通每点 +0.15% 最终伤害 ×(1 + 破甲增幅)；需技能启用） */
    public static double getArmorPenPercent(PlayerSkillRecord record) {
        double base = record.isEnabled(Skills.ARMOR_PEN)
                ? record.getActiveLevel(Skills.ARMOR_PEN) * 0.0015 : 0;
        if (base <= 0) {
            return 0;
        }
        double amp = record.isEnabled(Skills.AMP_ARMOR_PEN)
                ? record.getActiveLevel(Skills.AMP_ARMOR_PEN) * 0.004 : 0;
        return base * (1 + amp);
    }

    /** 治愈光环：作用半径（格，Config 可调，默认 10 = xyz 三轴全 10） */
    public static double getAuraHealRadius() {
        return org.zifeng.skilltree.Config.AURA_HEAL_RADIUS.get();
    }

    // ============ 技能增幅结果汇总（按钮显示用） ============

    /**
     * 返回某技能在给定点数下的实际增幅文本（如 "+5❤ +2甲" / "+12%攻伤"）。
     */
    public static String getEffectSummary(String skillId, int points) {
        if (points <= 0) {
            return "";
        }
        return switch (skillId) {
            // 基础技能（速度显示为每秒方块数：0.005×43.17≈0.22方/秒；跳跃每点≈0.05格）
            case Skills.BODY_HP -> fmt("+%.1f❤", points * 2.0);
            case Skills.BODY -> fmt("+%.1f甲", points * 0.2) + " " + fmt("+%.1f%%减伤", points * 0.05);
            case Skills.TOUGH -> fmt("+%.1f韧", points * 0.3) + " " + fmt("+%.1f%%击退", points * 0.1);
            case Skills.BLADE -> fmt("+%.1f攻伤", points * 0.4);
            case Skills.ATTACK_SPEED -> fmt("+%.2f攻速", points * 0.02);
            case Skills.MINING -> fmt("+%.1f挖速", points * 0.3) + " " + (points * 0.2 >= 1.0
                    ? "工具不毁"
                    : fmt("+%.0f%%耐久减免", points * 20));
            case Skills.MOVE -> fmt("+%.2f方/秒", points * 0.005 * 43.17);
            case Skills.REGEN -> fmt("+%.1f回血/秒", points * 0.1);
            case Skills.LUCK -> fmt("+%.1f幸运", points * 0.1);
            case Skills.JUMP -> fmt("+%.2f格", points * 0.0525); // 每点 +0.01 跳强 ≈ +0.05 格
            case Skills.FLY -> fmt("+%.2f方/秒", points * 0.005 / 8.0 * 216); // 每点 +0.005 属性 ÷8 对齐 abilities → 实际飞速
            case Skills.SWIM -> fmt("+%.2f方/秒", points * 0.005 * 3.35); // 每点 +0.005 SWIM_SPEED → 实际游泳速度
            case Skills.THORNS -> fmt("+%.1f反伤", points * 0.05);
            case Skills.ARMOR_PEN -> fmt("+%.1f%%破甲", points * 0.15);
            // 增幅技能
            case Skills.AMP_HP -> fmt("+%.1f%%生命", points * 0.5);
            case Skills.AMP_TOUGH -> fmt("+%.1f%%韧性/击退", points * 0.5);
            case Skills.AMP_LUCK -> fmt("+%.1f%%幸运", points * 0.5);
            case Skills.AMP_DAMAGE -> fmt("+%.1f%%攻伤", points * 0.5);
            case Skills.AMP_ATTACK_SPEED -> fmt("+%.1f%%攻速", points * 0.4);
            case Skills.AMP_MINING -> fmt("+%.1f%%挖速", points * 0.6);
            case Skills.AMP_REGEN -> fmt("+%.1f%%回血", points * 0.8);
            case Skills.AMP_ARMOR -> fmt("+%.1f%%减伤", points * 0.05);
            case Skills.AMP_MOVE -> fmt("+%.1f%%移速", points * 0.5);
            case Skills.AMP_DROP -> fmt("+%.1f%%掉落", points * 4) + " " + fmt("+%.1f%%经验", points * 5) + " (生物+方块)";
            case Skills.AMP_JUMP -> fmt("+%.1f%%跳高", points * 0.5);
            case Skills.AMP_FLY -> fmt("+%.1f%%飞速", points * 0.5);
            case Skills.AMP_SWIM -> fmt("+%.1f%%游泳", points * 0.5);
            case Skills.AMP_CRIT -> fmt("+%.1f%%暴击伤害", points * 0.5);
            case Skills.AMP_LIFESTEAL -> fmt("+%.1f%%吸血", points * 0.4);
            case Skills.AMP_THORNS -> fmt("+%.1f%%反伤", points * 0.4);
            case Skills.AMP_ARMOR_PEN -> fmt("+%.1f%%破甲", points * 0.4);
            // 终极节点
            case Skills.ULT_BLOOD -> "攻伤+50% 生命+50%";
            case Skills.ULT_GOLDEN -> "抗性10/吸收100/抗火5";
            default -> "";
        };
    }

    private static String fmt(String format, double value) {
        return String.format(format, value);
    }
}
