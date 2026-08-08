package org.zifeng.skilltree;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.zifeng.skilltree.skill.Skills;

/**
 * 通用配置（COMMON，可在游戏内热重载）。
 */
public class Config {
    public static final ModConfigSpec SPEC;

    /** 每转换 1 点技能点需要消耗的能量（FE），默认 1 亿 */
    public static final ModConfigSpec.LongValue ENERGY_PER_SKILL_POINT;

    /** 阶梯消耗：起始消耗（FE），默认 1 万 */
    public static final ModConfigSpec.LongValue ENERGY_START_COST;

    /** 阶梯消耗：前多少点内从起始消耗线性递增到最终消耗，默认 2000 */
    public static final ModConfigSpec.IntValue ENERGY_STEP_POINTS;

    /** 技能树界面背景色（淡灰，ARGB） */
    public static final ModConfigSpec.IntValue SKILL_TREE_BACKGROUND_COLOR;

    /** 技能树界面边框色（淡蓝，ARGB） */
    public static final ModConfigSpec.IntValue SKILL_TREE_BORDER_COLOR;

    /** 属性面板是否显示（技能树界面） */
    public static final ModConfigSpec.BooleanValue PANEL_VISIBLE;

    /** 属性面板位置：0=右侧 1=底部 */
    public static final ModConfigSpec.IntValue PANEL_POSITION;

    /** 机器界面进度条颜色（星辉蓝，ARGB） */
    public static final ModConfigSpec.IntValue MACHINE_PROGRESS_COLOR;

    // ============ 技能树经济数值（可热重载） ============

    /** 基础技能每级技能点消耗 */
    public static final ModConfigSpec.DoubleValue BASE_POINT_COST;

    /** 特殊增幅每级技能点消耗 */
    public static final ModConfigSpec.DoubleValue AMPLIFY_POINT_COST;

    /** 终极节点前置：基础/增幅技能各需投入点数 */
    public static final ModConfigSpec.IntValue ULTIMATE_REQUIRE_POINTS;

    /** 宇宙的青睐：一次性消耗技能点数 */
    public static final ModConfigSpec.IntValue ULT_FAVOR_COST;

    /** 夜视/饱食：一次性消耗技能点数 */
    public static final ModConfigSpec.IntValue MINOR_ULT_COST;

    /** 杀戮光环基础消耗（每级） */
    public static final ModConfigSpec.IntValue AURA_BASE_COST;

    /** 杀戮光环每级消耗递增倍率 */
    public static final ModConfigSpec.DoubleValue AURA_COST_MULTIPLIER;

    // ============ 磁铁效果（可热重载） ============

    /** 开启磁铁消耗的技能点（默认 10） */
    public static final ModConfigSpec.DoubleValue MAGNET_COST;

    /** 磁铁吸取掉落物半径（格，默认 8，最大 32） */
    public static final ModConfigSpec.DoubleValue MAGNET_ITEM_RADIUS;

    /** 磁铁吸取经验半径（格，默认 4，最大 32） */
    public static final ModConfigSpec.DoubleValue MAGNET_XP_RADIUS;

    // ============ 杀戮光环（可热重载） ============

    /** 自动攻击半径（格） */
    public static final ModConfigSpec.DoubleValue AURA_ATTACK_RADIUS;

    /** 攻击频率基准偏移：实际频率 = 玩家攻速属性 - 该值（基础 4.0 攻速 → 1 次/秒） */
    public static final ModConfigSpec.DoubleValue AURA_FREQUENCY_BASE_OFFSET;

    /** 杀戮光环·伤害：每级伤害倍率（小数，0.05 = 每级 +5%，ADD_MULTIPLIED_TOTAL 乘算） */
    public static final ModConfigSpec.DoubleValue AURA_DAMAGE_MULTIPLIER_PER_LEVEL;

    /** 守卫光环：每级全伤害防护（小数，0.01 = 每级 1%，100 级 = 100% 免疫） */
    public static final ModConfigSpec.DoubleValue AURA_GUARD_REDUCTION_PER_LEVEL;

    // ============ 终极节点（可热重载） ============

    /** 全能精通：所有基础属性额外增幅（小数） */
    public static final ModConfigSpec.DoubleValue MASTER_BONUS;

    /** 全能精通：技能点获取速度倍率（0.8 = -20%） */
    public static final ModConfigSpec.DoubleValue MASTER_SKILL_POINT_RATE;

    /** 浴血奋战：生命低于该比例触发增伤 */
    public static final ModConfigSpec.DoubleValue BLOOD_THRESHOLD;

    /** 浴血奋战：触发时近战伤害增幅（小数） */
    public static final ModConfigSpec.DoubleValue BLOOD_DAMAGE_BONUS;

    /** 浴血奋战：受到伤害倍率（常驻） */
    public static final ModConfigSpec.DoubleValue BLOOD_INCOMING_MULTIPLIER;

    /** 疾风连斩：连击≥3 时的攻速增幅（小数） */
    public static final ModConfigSpec.DoubleValue COMBO_SPEED_BONUS;

    /** 疾风连斩：连击重置间隔（tick，20=1秒） */
    public static final ModConfigSpec.IntValue COMBO_RESET_TICKS;

    /** 不坏金身：致命伤害后的无敌时长（tick） */
    public static final ModConfigSpec.IntValue GOLDEN_INVULNERABILITY_TICKS;

    /** 不坏金身：冷却时长（tick，3600=180秒） */
    public static final ModConfigSpec.IntValue GOLDEN_COOLDOWN_TICKS;

    /** 不坏金身：触发后禁回血时长（tick） */
    public static final ModConfigSpec.IntValue GOLDEN_NO_REGEN_TICKS;

    /** 万物皆可挖：瞬间完成概率（小数） */
    public static final ModConfigSpec.DoubleValue DIG_INSTANT_CHANCE;

    /** 万物皆可挖：仅对基础挖速 ≥ 该值的方块生效 */
    public static final ModConfigSpec.DoubleValue DIG_MIN_BASE_SPEED;

    // ============ 新增技能数值（可热重载） ============

    /** 暴击精通：每点暴击几率（小数） */
    public static final ModConfigSpec.DoubleValue CRIT_CHANCE_PER_POINT;

    /** 暴击基础伤害倍率 */
    public static final ModConfigSpec.DoubleValue CRIT_DAMAGE_BASE;

    /** 暴击增幅：每点暴击伤害倍率（小数） */
    public static final ModConfigSpec.DoubleValue CRIT_DAMAGE_PER_POINT;

    /** 生命汲取：每点吸血比例（小数） */
    public static final ModConfigSpec.DoubleValue LIFESTEAL_PER_POINT;

    /** 吸血增幅：每点吸血量倍率（小数） */
    public static final ModConfigSpec.DoubleValue LIFESTEAL_AMP_PER_POINT;

    /** 凤凰涅槃：冷却时长（tick，12000 = 10 分钟） */
    public static final ModConfigSpec.IntValue REVIVE_COOLDOWN_TICKS;

    /** 凤凰涅槃：复活时生命比例（小数，0.5 = 50%） */
    public static final ModConfigSpec.DoubleValue REVIVE_HEALTH_RATIO;

    /** 死神凝视：处决生命阈值（小数，0.15 = 目标血量低于 15% 可处决） */
    public static final ModConfigSpec.DoubleValue REAPER_THRESHOLD;

    /** 死神凝视：处决触发概率（小数，0.3 = 30%） */
    public static final ModConfigSpec.DoubleValue REAPER_CHANCE;

    /** 死神凝视：处决伤害（巨额伤害直接击杀，护甲减伤后仍足以秒杀） */
    public static final ModConfigSpec.DoubleValue REAPER_DAMAGE;

    /** 治愈光环：作用半径（格） */
    public static final ModConfigSpec.DoubleValue AURA_HEAL_RADIUS;

    /** 治愈光环：每级每秒治疗量 */
    public static final ModConfigSpec.DoubleValue AURA_HEAL_PER_LEVEL;

    /** 技能重洗：返还技能点比例（小数，1.0 = 全额返还） */
    public static final ModConfigSpec.DoubleValue RESET_REFUND_RATE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("星能转换机：每消耗多少 FE 能量转换 1 点技能点（1 亿 = 100000000）")
                .push("machine");
        ENERGY_PER_SKILL_POINT = builder
                .comment("每 1 点技能点所需能量（FE）。进度中断（停止输入能量超过 1 秒）会清空重算")
                .defineInRange("energyPerSkillPoint", 100_000_000L, 1L, Long.MAX_VALUE);
        ENERGY_START_COST = builder
                .comment("阶梯消耗：起始每点消耗（FE，默认 1 万）。前期升级快，鼓励入门")
                .defineInRange("energyStartCost", 10_000L, 1L, Long.MAX_VALUE);
        ENERGY_STEP_POINTS = builder
                .comment("阶梯消耗：前 N 点内每点消耗从起始值线性递增到最终值（默认 2000），之后固定最终消耗")
                .defineInRange("energyStepPoints", 2000, 1, 1000000);
        MACHINE_PROGRESS_COLOR = builder
                .comment("机器界面进度条颜色（ARGB，默认星辉蓝 0xFF4FC3F7）")
                .defineInRange("machineProgressColor", 0xFF4FC3F7, Integer.MIN_VALUE, Integer.MAX_VALUE);
        builder.pop();

        builder.comment("技能树界面样式").push("skillTree");
        SKILL_TREE_BACKGROUND_COLOR = builder
                .comment("技能树界面背景色（ARGB，淡灰色 0xFFBEBEBE，必须完全不透明否则文字被底层叠加变色）")
                .defineInRange("skillTreeBackgroundColor", 0xFFBEBEBE, Integer.MIN_VALUE, Integer.MAX_VALUE);
        SKILL_TREE_BORDER_COLOR = builder
                .comment("技能树界面边框色（ARGB，淡蓝色 0xFF87CEEB）")
                .defineInRange("skillTreeBorderColor", 0xFF87CEEB, Integer.MIN_VALUE, Integer.MAX_VALUE);
        PANEL_VISIBLE = builder
                .comment("属性面板是否显示（可在技能树界面点面板右上角开关切换）")
                .define("panelVisible", true);
        PANEL_POSITION = builder
                .comment("属性面板位置（0=右侧 1=底部，Shift+点击面板开关切换）")
                .defineInRange("panelPosition", 0, 0, 1);
        builder.pop();

        builder.comment("技能树经济数值：加点/终极解锁/杀戮光环的消耗。改动后需重新打开界面或按 N 刷新显示")
                .push("economy");
        BASE_POINT_COST = builder
                .comment("基础技能每级消耗技能点（默认 1.0）")
                .defineInRange("basePointCost", Skills.BASE_POINT_COST, 0.01, 100.0);
        AMPLIFY_POINT_COST = builder
                .comment("特殊增幅每级消耗技能点（默认 2.0）")
                .defineInRange("amplifyPointCost", Skills.AMPLIFY_POINT_COST, 0.01, 100.0);
        ULTIMATE_REQUIRE_POINTS = builder
                .comment("终极节点前置：基础/增幅技能各需投入的点数（默认 500）")
                .defineInRange("ultimateRequirePoints", Skills.ULTIMATE_REQUIRE_POINTS, 1, 10000);
        ULT_FAVOR_COST = builder
                .comment("宇宙的青睐：一次性消耗技能点（默认 1000）")
                .defineInRange("ultFavorCost", Skills.ULT_FAVOR_COST, 1, 1000000);
        MINOR_ULT_COST = builder
                .comment("夜视/饱食：一次性消耗技能点（默认 100）")
                .defineInRange("minorUltCost", 100, 1, 1000000);
        AURA_BASE_COST = builder
                .comment("杀戮光环基础消耗（每级，默认 1000）")
                .defineInRange("auraBaseCost", Skills.AURA_BASE_COST, 1, 10000000);
        AURA_COST_MULTIPLIER = builder
                .comment("杀戮光环每级消耗递增倍率（默认 1.05，下一级消耗 = 基础 × 倍率^当前等级）")
                .defineInRange("auraCostMultiplier", Skills.AURA_COST_MULTIPLIER, 1.0, 10.0);
        builder.pop();

        builder.comment("杀戮光环：攻击半径与频率。改动即时生效")
                .push("aura");
        AURA_ATTACK_RADIUS = builder
                .comment("自动攻击半径（格，默认 20）")
                .defineInRange("attackRadius", 20.0, 1.0, 128.0);
        AURA_FREQUENCY_BASE_OFFSET = builder
                .comment("攻击频率基准偏移：实际频率 = 玩家攻速属性 - 该值（基础攻速 4.0 → 1 次/秒；100 级光环速度 ≈ 20 次/秒）")
                .defineInRange("frequencyBaseOffset", 3.0, 0.0, 10.0);
        AURA_DAMAGE_MULTIPLIER_PER_LEVEL = builder
                .comment("杀戮光环·伤害：每级伤害倍率（小数，0.05 = 每级 +5%，乘算叠加到攻击伤害属性）")
                .defineInRange("damageMultiplierPerLevel", 0.05, 0.001, 1.0);
        AURA_GUARD_REDUCTION_PER_LEVEL = builder
                .comment("守卫光环：每级全伤害防护（小数，0.01 = 每级 1%；对所有伤害类型生效，含真伤/混沌/指令；100 级 = 100% 免疫）")
                .defineInRange("guardReductionPerLevel", 0.01, 0.001, 0.1);
        builder.pop();

        builder.comment("磁铁效果：吸取经验与掉落物（快捷键 H 切换）。改动即时生效")
                .push("magnet");
        MAGNET_COST = builder
                .comment("开启磁铁消耗的技能点（默认 10）")
                .defineInRange("magnetCost", 10.0, 0.0, 100000.0);
        MAGNET_ITEM_RADIUS = builder
                .comment("吸取掉落物半径（格，默认 21，最大 32）")
                .defineInRange("magnetItemRadius", 21.0, 1.0, 32.0);
        MAGNET_XP_RADIUS = builder
                .comment("吸取经验半径（格，默认 4，最大 32）")
                .defineInRange("magnetXpRadius", 4.0, 1.0, 32.0);
        builder.pop();

        builder.comment("终极节点被动数值。改动即时生效")
                .push("ultimate");
        MASTER_BONUS = builder
                .comment("全能精通：所有基础属性额外增幅（小数，0.25 = +25%）")
                .defineInRange("masterBonus", 0.25, 0.0, 5.0);
        MASTER_SKILL_POINT_RATE = builder
                .comment("全能精通：技能点获取速度倍率（0.8 = -20%）")
                .defineInRange("masterSkillPointRate", 0.8, 0.01, 5.0);
        BLOOD_THRESHOLD = builder
                .comment("浴血奋战：生命低于该比例时近战伤害增幅生效（默认 0.3）")
                .defineInRange("bloodThreshold", 0.3, 0.01, 0.99);
        BLOOD_DAMAGE_BONUS = builder
                .comment("浴血奋战：触发时近战伤害增幅（小数，0.5 = +50%）")
                .defineInRange("bloodDamageBonus", 0.5, 0.0, 10.0);
        BLOOD_INCOMING_MULTIPLIER = builder
                .comment("浴血奋战：受到伤害倍率（常驻，1.2 = +20%）")
                .defineInRange("bloodIncomingMultiplier", 1.2, 1.0, 5.0);
        COMBO_SPEED_BONUS = builder
                .comment("疾风连斩：连击≥3 次时的攻速增幅（小数，0.3 = +30%）")
                .defineInRange("comboSpeedBonus", 0.3, 0.0, 5.0);
        COMBO_RESET_TICKS = builder
                .comment("疾风连斩：连击重置间隔（tick，20 = 1 秒）")
                .defineInRange("comboResetTicks", 20, 1, 200);
        GOLDEN_INVULNERABILITY_TICKS = builder
                .comment("不坏金身：致命伤害后的无敌时长（tick，60 = 3 秒）")
                .defineInRange("goldenInvulnerabilityTicks", 60, 1, 1200);
        GOLDEN_COOLDOWN_TICKS = builder
                .comment("不坏金身：冷却时长（tick，3600 = 180 秒）")
                .defineInRange("goldenCooldownTicks", 3600, 20, 72000);
        GOLDEN_NO_REGEN_TICKS = builder
                .comment("不坏金身：触发后生命恢复禁用时长（tick，200 = 10 秒）")
                .defineInRange("goldenNoRegenTicks", 200, 0, 7200);
        DIG_INSTANT_CHANCE = builder
                .comment("万物皆可挖：瞬间完成采掘的概率（小数，0.2 = 20%）")
                .defineInRange("digInstantChance", 0.2, 0.01, 1.0);
        DIG_MIN_BASE_SPEED = builder
                .comment("万物皆可挖：仅对基础挖速 ≥ 该值的方块生效（8 ≈ 挖掘 ≤1.5 秒的方块）")
                .defineInRange("digMinBaseSpeed", 8.0, 0.0, 100.0);
        builder.pop();

        builder.comment("新增技能数值（暴击/吸血/凤凰涅槃/治愈光环）与技能重洗。改动即时生效")
                .push("newSkills");
        CRIT_CHANCE_PER_POINT = builder
                .comment("暴击精通：每点暴击几率（小数，0.001 = 0.1%/点，1000 点 = 100%）")
                .defineInRange("critChancePerPoint", 0.001, 0.0001, 0.1);
        CRIT_DAMAGE_BASE = builder
                .comment("暴击基础伤害倍率（1.5 = 暴击造成 1.5 倍伤害）")
                .defineInRange("critDamageBase", 1.5, 1.0, 10.0);
        CRIT_DAMAGE_PER_POINT = builder
                .comment("暴击增幅：每点暴击伤害倍率（小数，0.005 = +0.5%/点）")
                .defineInRange("critDamagePerPoint", 0.005, 0.0, 0.1);
        LIFESTEAL_PER_POINT = builder
                .comment("生命汲取：每点吸血比例（小数，0.001 = 0.1%/点，1000 点 = 100% 吸血）")
                .defineInRange("lifestealPerPoint", 0.001, 0.0001, 0.05);
        LIFESTEAL_AMP_PER_POINT = builder
                .comment("吸血增幅：每点吸血量倍率（小数，0.004 = +0.4%/点）")
                .defineInRange("lifestealAmpPerPoint", 0.004, 0.0, 0.05);
        REVIVE_COOLDOWN_TICKS = builder
                .comment("凤凰涅槃：冷却时长（tick，12000 = 10 分钟）")
                .defineInRange("reviveCooldownTicks", 12000, 100, 240000);
        REVIVE_HEALTH_RATIO = builder
                .comment("凤凰涅槃：复活时生命比例（小数，0.5 = 50%）")
                .defineInRange("reviveHealthRatio", 0.5, 0.01, 1.0);
        REAPER_THRESHOLD = builder
                .comment("死神凝视：处决生命阈值（小数，0.15 = 目标血量低于 15% 可处决）")
                .defineInRange("reaperThreshold", 0.15, 0.01, 0.9);
        REAPER_CHANCE = builder
                .comment("死神凝视：处决触发概率（小数，0.3 = 30%）")
                .defineInRange("reaperChance", 0.3, 0.01, 1.0);
        REAPER_DAMAGE = builder
                .comment("死神凝视：处决伤害（默认 99999，巨额伤害直接击杀）")
                .defineInRange("reaperDamage", 99999.0, 100.0, 1_000_000.0);
        AURA_HEAL_RADIUS = builder
                .comment("治愈光环：作用半径（格，默认 10）")
                .defineInRange("auraHealRadius", 10.0, 1.0, 64.0);
        AURA_HEAL_PER_LEVEL = builder
                .comment("治愈光环：每级每秒治疗量（默认 0.5，100 级 = 50 点/秒）")
                .defineInRange("auraHealPerLevel", 0.5, 0.1, 100.0);
        RESET_REFUND_RATE = builder
                .comment("技能重洗（界面按 R 键）：返还技能点比例（小数，1.0 = 全额返还，0.8 = 返还 80%）")
                .defineInRange("resetRefundRate", 1.0, 0.0, 1.0);
        builder.pop();

        SPEC = builder.build();
    }
}
