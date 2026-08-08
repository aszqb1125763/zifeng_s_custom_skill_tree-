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

    /** 光环锁定：一次性消耗技能点（默认 1000） */
    public static final ModConfigSpec.DoubleValue LOCK_COST;

    // ============ 杀戮光环（可热重载） ============

    /** 自动攻击半径（格） */
    public static final ModConfigSpec.DoubleValue AURA_ATTACK_RADIUS;

    /** 光环基础攻击间隔（tick，200 = 10 秒一次，未学速度光环时） */
    public static final ModConfigSpec.IntValue AURA_BASE_INTERVAL_TICKS;

    /** 光环速度：每级减少的攻击间隔（tick，20 级 → 间隔 10 tick = 每秒 2 次） */
    public static final ModConfigSpec.DoubleValue AURA_SPEED_INTERVAL_REDUCTION;

    /** 杀戮光环·伤害：每级伤害倍率（小数，0.10 = 每级 +10%，ADD_MULTIPLIED_TOTAL 乘算） */
    public static final ModConfigSpec.DoubleValue AURA_DAMAGE_MULTIPLIER_PER_LEVEL;

    /** 混沌伤害：光环攻击命中后附加的无视护甲真实伤害比例（小数，0.2 = 主伤害的 20%） */
    public static final ModConfigSpec.DoubleValue AURA_CHAOS_DAMAGE_RATIO;


    // ============ 终极节点（可热重载） ============

    /** 浴血奋战/不坏金身/凤凰涅槃：一次性消耗（默认 500） */
    public static final ModConfigSpec.IntValue ULT_BASE_COST;

    /** 死神凝视：一次性消耗（默认 1000） */
    public static final ModConfigSpec.IntValue ULT_REAPER_COST;

    /** 全能精通：一次性消耗（默认 5000） */
    public static final ModConfigSpec.IntValue ULT_MASTER_COST;

    /** 全能精通：所有基础属性额外增幅（小数） */
    public static final ModConfigSpec.DoubleValue MASTER_BONUS;

    /** 全能精通：技能点获取速度倍率（0.8 = -20%） */
    public static final ModConfigSpec.DoubleValue MASTER_SKILL_POINT_RATE;

    /** 全能精通：全伤害减免比例（小数，1.0 = 100%，对所有伤害类型生效含真伤/混沌/指令） */
    public static final ModConfigSpec.DoubleValue MASTER_DAMAGE_REDUCTION;

    /** 全能精通：免死保底冷却（tick，1200 = 1 分钟） */
    public static final ModConfigSpec.IntValue MASTER_UNDYING_COOLDOWN;

    /** 全能精通：免死保底无敌时长（tick，60 = 3 秒） */
    public static final ModConfigSpec.IntValue MASTER_UNDYING_INVULN;

    /** 全能精通：免死保底回复生命比例（小数，0.5 = 50%） */
    public static final ModConfigSpec.DoubleValue MASTER_UNDYING_HEALTH;

    /** 浴血奋战：常驻攻击力增幅（小数，0.5 = +50%） */
    public static final ModConfigSpec.DoubleValue BLOOD_ATTACK_BONUS;

    /** 浴血奋战：常驻最大生命增幅（小数，0.5 = +50%） */
    public static final ModConfigSpec.DoubleValue BLOOD_HEALTH_BONUS;

    /** 不坏金身：常驻抗性提升等级（0 = 不生效） */
    public static final ModConfigSpec.IntValue GOLDEN_RESISTANCE_LEVEL;

    /** 不坏金身：常驻伤害吸收等级（0 = 不生效） */
    public static final ModConfigSpec.IntValue GOLDEN_ABSORPTION_LEVEL;

    /** 不坏金身：常驻抗火等级（0 = 不生效） */
    public static final ModConfigSpec.IntValue GOLDEN_FIRE_RESISTANCE_LEVEL;

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

    /** 凤凰涅槃：冷却时长（tick，1200 = 1 分钟） */
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
        AURA_BASE_INTERVAL_TICKS = builder
                .comment("光环基础攻击间隔（tick，200 = 10 秒一次；未学/关闭光环速度时）")
                .defineInRange("auraBaseIntervalTicks", 200, 10, 12000);
        AURA_SPEED_INTERVAL_REDUCTION = builder
                .comment("光环速度：每级减少的攻击间隔（tick，默认 9.5；20 级 → 间隔 10 tick = 每秒 2 次）")
                .defineInRange("auraSpeedIntervalReduction", 9.5, 0.0, 20.0);
        AURA_DAMAGE_MULTIPLIER_PER_LEVEL = builder
                .comment("杀戮光环·伤害：每级伤害倍率（小数，0.10 = 每级 +10%，乘算叠加到攻击伤害属性）")
                .defineInRange("damageMultiplierPerLevel", 0.10, 0.001, 1.0);
        AURA_CHAOS_DAMAGE_RATIO = builder
                .comment("混沌伤害：光环攻击命中后附加的无视护甲真实伤害比例（小数，0.2 = 主伤害的 20%；参考龙之研究混沌武器）")
                .defineInRange("auraChaosDamageRatio", 0.2, 0.0, 1.0);
        builder.pop();

        builder.comment("磁铁效果：吸取经验与掉落物（快捷键 H 切换）。改动即时生效")
                .push("magnet");
        MAGNET_COST = builder
                .comment("开启磁铁消耗的技能点（默认 10）")
                .defineInRange("magnetCost", 10.0, 0.0, 100000.0);
        MAGNET_ITEM_RADIUS = builder
                .comment("吸取掉落物半径（格，默认 20，xyz 全方向，最大 32）")
                .defineInRange("magnetItemRadius", 20.0, 1.0, 32.0);
        MAGNET_XP_RADIUS = builder
                .comment("吸取经验半径（格，默认 20，xyz 全方向，最大 32）")
                .defineInRange("magnetXpRadius", 20.0, 1.0, 32.0);
        LOCK_COST = builder
                .comment("光环锁定：一次性消耗技能点（默认 1000）")
                .defineInRange("lockCost", 1000.0, 1.0, 1000000.0);
        builder.pop();

        builder.comment("终极节点被动数值。改动即时生效")
                .push("ultimate");
        MASTER_BONUS = builder
                .comment("全能精通：所有基础属性额外增幅（小数，0.25 = +25%）")
                .defineInRange("masterBonus", 0.25, 0.0, 5.0);
        MASTER_SKILL_POINT_RATE = builder
                .comment("全能精通：技能点获取速度倍率（0.8 = -20%）")
                .defineInRange("masterSkillPointRate", 0.8, 0.01, 5.0);
        MASTER_DAMAGE_REDUCTION = builder
                .comment("全能精通：全伤害减免比例（小数，1.0 = 100% 免疫所有伤害；对所有伤害类型生效，含真伤/混沌/指令）")
                .defineInRange("masterDamageReduction", 1.0, 0.0, 1.0);
        MASTER_UNDYING_COOLDOWN = builder
                .comment("全能精通：免死保底冷却（tick，1200 = 1 分钟）")
                .defineInRange("masterUndyingCooldown", 1200, 100, 240000);
        MASTER_UNDYING_INVULN = builder
                .comment("全能精通：免死保底无敌时长（tick，60 = 3 秒）")
                .defineInRange("masterUndyingInvuln", 60, 10, 1200);
        MASTER_UNDYING_HEALTH = builder
                .comment("全能精通：免死保底回复生命比例（小数，0.5 = 50%）")
                .defineInRange("masterUndyingHealth", 0.5, 0.05, 1.0);
        ULT_BASE_COST = builder
                .comment("浴血奋战/不坏金身/凤凰涅槃：一次性消耗技能点（默认 500）")
                .defineInRange("ultBaseCost", 500, 1, 1000000);
        ULT_REAPER_COST = builder
                .comment("死神凝视：一次性消耗技能点（默认 1000）")
                .defineInRange("ultReaperCost", 1000, 1, 1000000);
        ULT_MASTER_COST = builder
                .comment("全能精通：一次性消耗技能点（默认 5000）")
                .defineInRange("ultMasterCost", 5000, 1, 10000000);
        BLOOD_ATTACK_BONUS = builder
                .comment("浴血奋战：常驻攻击力增幅（小数，0.5 = +50%）")
                .defineInRange("bloodAttackBonus", 0.5, 0.0, 10.0);
        BLOOD_HEALTH_BONUS = builder
                .comment("浴血奋战：常驻最大生命增幅（小数，0.5 = +50%）")
                .defineInRange("bloodHealthBonus", 0.5, 0.0, 10.0);
        GOLDEN_RESISTANCE_LEVEL = builder
                .comment("不坏金身：常驻抗性提升等级（10 = 10级，0 = 不生效）")
                .defineInRange("goldenResistanceLevel", 10, 0, 255);
        GOLDEN_ABSORPTION_LEVEL = builder
                .comment("不坏金身：常驻伤害吸收等级（100 = 100级，0 = 不生效）")
                .defineInRange("goldenAbsorptionLevel", 100, 0, 255);
        GOLDEN_FIRE_RESISTANCE_LEVEL = builder
                .comment("不坏金身：常驻抗火等级（5 = 5级，0 = 不生效）")
                .defineInRange("goldenFireResistanceLevel", 5, 0, 255);
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
                .comment("凤凰涅槃：冷却时长（tick，1200 = 1 分钟）")
                .defineInRange("reviveCooldownTicks", 1200, 100, 240000);
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
        RESET_REFUND_RATE = builder
                .comment("技能重洗（界面按 R 键）：返还技能点比例（小数，1.0 = 全额返还，0.8 = 返还 80%）")
                .defineInRange("resetRefundRate", 1.0, 0.0, 1.0);
        builder.pop();

        SPEC = builder.build();
    }
}
