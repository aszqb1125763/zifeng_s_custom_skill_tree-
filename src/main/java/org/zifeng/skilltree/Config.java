package org.zifeng.skilltree;

import net.minecraftforge.common.ForgeConfigSpec;
import org.zifeng.skilltree.skill.Skills;

/**
 * 通用配置（COMMON，可在游戏内热重载）。
 */
public class Config {
    public static final ForgeConfigSpec SPEC;

    /** 每转换 1 点技能点需要消耗的能量（FE），默认 1 亿 */
    public static final ForgeConfigSpec.LongValue ENERGY_PER_SKILL_POINT;

    /** 阶梯消耗：起始消耗（FE），默认 1 万 */
    public static final ForgeConfigSpec.LongValue ENERGY_START_COST;

    /** 阶梯消耗：前多少点内从起始消耗线性递增到最终消耗，默认 2000 */
    public static final ForgeConfigSpec.IntValue ENERGY_STEP_POINTS;

    /** 技能点转换机：每 tick 最大输入 FE 上限（默认 10 万） */
    public static final ForgeConfigSpec.LongValue MACHINE_MAX_INPUT_RATE;

    /** 技能点转换机：能量缓冲上限（FE，64 位 Long.MAX_VALUE = 9223372036854775807），达到上限后停止接收 */
    public static final ForgeConfigSpec.LongValue MACHINE_ENERGY_CAPACITY;

    /** 技能树界面背景色（淡灰，ARGB） */
    public static final ForgeConfigSpec.IntValue SKILL_TREE_BACKGROUND_COLOR;

    /** 技能树界面边框色（淡蓝，ARGB） */
    public static final ForgeConfigSpec.IntValue SKILL_TREE_BORDER_COLOR;

    /** 属性面板是否显示（技能树界面） */
    public static final ForgeConfigSpec.BooleanValue PANEL_VISIBLE;

    /** 属性面板位置：0=右侧 1=底部 */
    public static final ForgeConfigSpec.IntValue PANEL_POSITION;

    /** 机器界面进度条颜色（星辉蓝，ARGB） */
    public static final ForgeConfigSpec.IntValue MACHINE_PROGRESS_COLOR;

    // ============ 技能树经济数值（可热重载） ============

    /** 基础技能每级技能点消耗 */
    public static final ForgeConfigSpec.DoubleValue BASE_POINT_COST;

    /** 特殊增幅每级技能点消耗 */
    public static final ForgeConfigSpec.DoubleValue AMPLIFY_POINT_COST;

    /** 终极节点前置：基础/增幅技能各需投入点数 */
    public static final ForgeConfigSpec.IntValue ULTIMATE_REQUIRE_POINTS;

    /** 宇宙的青睐：一次性消耗技能点数 */
    public static final ForgeConfigSpec.LongValue ULT_FAVOR_COST;

    /** 夜视/饱食：一次性消耗技能点数 */
    public static final ForgeConfigSpec.LongValue MINOR_ULT_COST;

    /** 杀戮光环基础消耗（每级） */
    public static final ForgeConfigSpec.LongValue AURA_BASE_COST;

    /** 杀戮光环每级消耗递增倍率 */
    public static final ForgeConfigSpec.DoubleValue AURA_COST_MULTIPLIER;

    // ============ 磁铁效果（可热重载） ============

    /** 开启磁铁消耗的技能点（默认 10） */
    public static final ForgeConfigSpec.DoubleValue MAGNET_COST;

    /** 磁铁吸取掉落物半径（格，默认 8，最大 32） */
    public static final ForgeConfigSpec.DoubleValue MAGNET_ITEM_RADIUS;

    /** 磁铁吸取经验半径（格，默认 4，最大 32） */
    public static final ForgeConfigSpec.DoubleValue MAGNET_XP_RADIUS;

    /** 虚空之矛：磁铁吸取范围放大（格，默认 55，经验和掉落物都生效） */
    public static final ForgeConfigSpec.DoubleValue VOID_MAGNET_RADIUS;

    /** 光环锁定：一次性消耗技能点（默认 1000） */
    public static final ForgeConfigSpec.DoubleValue LOCK_COST;

    /** 杀戮光环·虚空之矛：一次性消耗技能点（默认 5000） */
    public static final ForgeConfigSpec.DoubleValue VOID_AURA_COST;

    /** 虚空之躯：一次性消耗技能点（默认 5000） */
    public static final ForgeConfigSpec.DoubleValue VOID_BODY_COST;

    /** 发光（节点类终极）：给周围生物施加发光的半径（格，默认 35） */
    public static final ForgeConfigSpec.DoubleValue GLOW_RADIUS;

    // ============ 杀戮光环（可热重载） ============

    /** 自动攻击半径（格） */
    public static final ForgeConfigSpec.DoubleValue AURA_ATTACK_RADIUS;

    /** 虚空之矛：杀戮光环攻击半径放大（格，参考虚空之矛模组 50 格范围秒杀） */
    public static final ForgeConfigSpec.DoubleValue VOID_AURA_RADIUS;

    /** 光环基础攻击间隔（tick，200 = 10 秒一次，未学速度光环时） */
    public static final ForgeConfigSpec.IntValue AURA_BASE_INTERVAL_TICKS;

    /** 光环速度：每级减少的攻击间隔（tick，20 级 → 间隔 10 tick = 每秒 2 次） */
    public static final ForgeConfigSpec.DoubleValue AURA_SPEED_INTERVAL_REDUCTION;

    /** 杀戮光环·伤害：每级伤害倍率（小数，0.10 = 每级 +10%，MULTIPLY_TOTAL 乘算） */
    public static final ForgeConfigSpec.DoubleValue AURA_DAMAGE_MULTIPLIER_PER_LEVEL;

    /** 混沌伤害：光环攻击命中后附加的无视护甲真实伤害比例（小数，0.2 = 主伤害的 20%） */
    public static final ForgeConfigSpec.DoubleValue AURA_CHAOS_DAMAGE_RATIO;


    // ============ 终极节点（可热重载） ============

    /** 浴血奋战/不坏金身/凤凰涅槃：一次性消耗（默认 500） */
    public static final ForgeConfigSpec.LongValue ULT_BASE_COST;

    /** 死神凝视：一次性消耗（默认 1000） */
    public static final ForgeConfigSpec.LongValue ULT_REAPER_COST;

    /** 全能精通：一次性消耗（默认 5000） */
    public static final ForgeConfigSpec.LongValue ULT_MASTER_COST;

    /** 终极节点（节点类多级）：每级消耗阶梯递增比率（0.1 = +10%） */
    public static final ForgeConfigSpec.DoubleValue ULTIMATE_STEP_RATE;

    /** 战利品爆炸：掉落倍率封顶（1级=1倍，线性 1+等级；默认封顶 101） */
    public static final ForgeConfigSpec.IntValue LOOT_BOMB_MAX_MULTIPLIER;

    /** 战利品爆炸：单次击杀最多生成的掉落副本数（2026-08-15 性能优化：光环一次杀大量怪物时防掉落物实体爆炸） */
    public static final ForgeConfigSpec.IntValue LOOT_BOMB_MAX_COPIES_PER_KILL;

    /** 磁力光环：单 tick 最多处理的掉落物/经验球数量（2026-08-15 性能优化：防止几千个同时瞬移卡顿） */
    public static final ForgeConfigSpec.IntValue MAGNET_MAX_PER_TICK;

    /** 全能精通：所有基础属性额外增幅（小数） */
    public static final ForgeConfigSpec.DoubleValue MASTER_BONUS;

    /** 全能精通：技能点获取速度倍率（0.8 = -20%） */
    public static final ForgeConfigSpec.DoubleValue MASTER_SKILL_POINT_RATE;

    /** 全能精通：全伤害减免比例（小数，1.0 = 100%，对所有伤害类型生效含真伤/混沌/指令） */
    public static final ForgeConfigSpec.DoubleValue MASTER_DAMAGE_REDUCTION;

    /** 全能精通：免死保底冷却（tick，1200 = 1 分钟） */
    public static final ForgeConfigSpec.IntValue MASTER_UNDYING_COOLDOWN;

    /** 全能精通：免死保底无敌时长（tick，60 = 3 秒） */
    public static final ForgeConfigSpec.IntValue MASTER_UNDYING_INVULN;

    /** 全能精通：免死保底回复生命比例（小数，0.5 = 50%） */
    public static final ForgeConfigSpec.DoubleValue MASTER_UNDYING_HEALTH;

    /** 浴血奋战：常驻攻击力增幅（小数，0.5 = +50%） */
    public static final ForgeConfigSpec.DoubleValue BLOOD_ATTACK_BONUS;

    /** 浴血奋战：常驻最大生命增幅（小数，0.5 = +50%） */
    public static final ForgeConfigSpec.DoubleValue BLOOD_HEALTH_BONUS;

    /** 不坏金身：常驻抗性提升等级（0 = 不生效） */
    public static final ForgeConfigSpec.IntValue GOLDEN_RESISTANCE_LEVEL;

    /** 不坏金身：常驻伤害吸收等级（0 = 不生效） */
    public static final ForgeConfigSpec.IntValue GOLDEN_ABSORPTION_LEVEL;

    /** 不坏金身：常驻抗火等级（0 = 不生效） */
    public static final ForgeConfigSpec.IntValue GOLDEN_FIRE_RESISTANCE_LEVEL;

    // ============ 新增技能数值（可热重载） ============

    /** 暴击精通：每点暴击几率（小数） */
    public static final ForgeConfigSpec.DoubleValue CRIT_CHANCE_PER_POINT;

    /** 暴击基础伤害倍率 */
    public static final ForgeConfigSpec.DoubleValue CRIT_DAMAGE_BASE;

    /** 暴击增幅：每点暴击伤害倍率（小数） */
    public static final ForgeConfigSpec.DoubleValue CRIT_DAMAGE_PER_POINT;

    /** 生命汲取：每点吸血比例（小数） */
    public static final ForgeConfigSpec.DoubleValue LIFESTEAL_PER_POINT;

    /** 吸血增幅：每点吸血量倍率（小数） */
    public static final ForgeConfigSpec.DoubleValue LIFESTEAL_AMP_PER_POINT;

    /** 凤凰涅槃：冷却时长（tick，1200 = 1 分钟） */
    public static final ForgeConfigSpec.IntValue REVIVE_COOLDOWN_TICKS;

    /** 凤凰涅槃：复活时生命比例（小数，0.5 = 50%） */
    public static final ForgeConfigSpec.DoubleValue REVIVE_HEALTH_RATIO;

    /** 死神凝视：处决生命阈值（小数，0.15 = 目标血量低于 15% 可处决） */
    public static final ForgeConfigSpec.DoubleValue REAPER_THRESHOLD;

    /** 死神凝视：处决触发概率（小数，0.3 = 30%） */
    public static final ForgeConfigSpec.DoubleValue REAPER_CHANCE;

    /** 死神凝视：处决伤害（巨额伤害直接击杀，护甲减伤后仍足以秒杀） */
    public static final ForgeConfigSpec.DoubleValue REAPER_DAMAGE;

    /** 治愈光环：作用半径（格） */
    public static final ForgeConfigSpec.DoubleValue AURA_HEAL_RADIUS;

    /** 技能重洗：返还技能点比例（小数，1.0 = 全额返还） */
    public static final ForgeConfigSpec.DoubleValue RESET_REFUND_RATE;

    /** 机械之星：一次性消耗技能点（默认 1000） */
    public static final ForgeConfigSpec.LongValue MACHINE_STAR_COST;

    /** 机械共鸣技能：一次性消耗技能点（默认 5000） */
    public static final ForgeConfigSpec.LongValue MACHINE_RESONANCE_COST;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("技能点转换机：每消耗多少 FE 能量转换 1 点技能点（1 亿 = 100000000）")
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
        MACHINE_MAX_INPUT_RATE = builder
                .comment("技能点转换机：每 tick 最大输入能量（FE/t，默认 100000）。GUI 内可开启\"无限制输入\"忽略此限制")
                .defineInRange("machineMaxInputRate", 100_000L, 1L, Long.MAX_VALUE);
        MACHINE_ENERGY_CAPACITY = builder
                .comment("技能点转换机：能量缓冲上限（FE，64 位上限 Long.MAX_VALUE = 9223372036854775807）。达到上限后停止接收能量输入")
                .defineInRange("machineEnergyCapacity", Long.MAX_VALUE, 1L, Long.MAX_VALUE);
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
                .defineInRange("ultFavorCost", Skills.ULT_FAVOR_COST, 1L, Long.MAX_VALUE);
        MINOR_ULT_COST = builder
                .comment("夜视/饱食：一次性消耗技能点（默认 100）")
                .defineInRange("minorUltCost", 100L, 1L, Long.MAX_VALUE);
        AURA_BASE_COST = builder
                .comment("杀戮光环基础消耗（每级，默认 1000）")
                .defineInRange("auraBaseCost", Skills.AURA_BASE_COST, 1L, Long.MAX_VALUE);
        AURA_COST_MULTIPLIER = builder
                .comment("杀戮光环每级消耗递增倍率（默认 1.05，下一级消耗 = 基础 × 倍率^当前等级）")
                .defineInRange("auraCostMultiplier", Skills.AURA_COST_MULTIPLIER, 1.0, 10.0);
        builder.pop();

        builder.comment("杀戮光环：攻击半径与频率。改动即时生效")
                .push("aura");
        AURA_ATTACK_RADIUS = builder
                .comment("自动攻击半径（格，默认 20）")
                .defineInRange("attackRadius", 20.0, 1.0, 128.0);
        VOID_AURA_RADIUS = builder
                .comment("虚空之矛：杀戮光环攻击半径放大（格，默认 50，参考虚空之矛模组范围秒杀）")
                .defineInRange("voidAuraRadius", 50.0, 1.0, 128.0);
        AURA_BASE_INTERVAL_TICKS = builder
                .comment("光环基础攻击间隔（tick，200 = 10 秒一次；未学/关闭光环速度时）")
                .defineInRange("auraBaseIntervalTicks", 200, 10, 12000);
        AURA_SPEED_INTERVAL_REDUCTION = builder
                .comment("光环速度：每级攻击间隔缩减比例（乘法递减，0.1 = 每级间隔×0.9；\n前10级从 200 tick 降到 70 tick，20级 ≈ 24 tick = 每秒约1.2次）")
                .defineInRange("auraSpeedIntervalReduction", 0.1, 0.0, 0.5);
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
        VOID_MAGNET_RADIUS = builder
                .comment("虚空之矛：磁铁吸取范围放大（格，默认 55，经验和掉落物都生效；需点亮并开启虚空之矛技能）")
                .defineInRange("voidMagnetRadius", 55.0, 1.0, 128.0);
        LOCK_COST = builder
                .comment("光环锁定：一次性消耗技能点（默认 1000）")
                .defineInRange("lockCost", 1000.0, 1.0, 1000000.0);
        VOID_AURA_COST = builder
                .comment("杀戮光环·虚空之矛：一次性消耗技能点（默认 5000）")
                .defineInRange("voidAuraCost", 5000.0, 1.0, 10000000.0);
        VOID_BODY_COST = builder
                .comment("虚空之躯：一次性消耗技能点（默认 5000）")
                .defineInRange("voidBodyCost", 5000.0, 1.0, 10000000.0);
        GLOW_RADIUS = builder
                .comment("发光（节点类终极）：给周围生物施加发光的半径（格，默认 35）")
                .defineInRange("glowRadius", 35.0, 1.0, 128.0);
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
                .defineInRange("ultBaseCost", 500L, 1L, Long.MAX_VALUE);
        ULT_REAPER_COST = builder
                .comment("死神凝视：一次性消耗技能点（默认 1000）")
                .defineInRange("ultReaperCost", 1000L, 1L, Long.MAX_VALUE);
        ULT_MASTER_COST = builder
                .comment("全能精通：一次性消耗技能点（默认 5000）")
                .defineInRange("ultMasterCost", 5000L, 1L, Long.MAX_VALUE);
        ULTIMATE_STEP_RATE = builder
                .comment("终极节点（节点类多级）：每级消耗在上一级基础上增加的比率（小数，0.1 = +10%）")
                .defineInRange("ultimateStepRate", 0.1, 0.0, 1.0);
        LOOT_BOMB_MAX_MULTIPLIER = builder
                .comment("战利品爆炸：掉落倍率封顶（1级=1倍，线性 1+等级，100级=101倍；默认 101）")
                .defineInRange("lootBombMaxMultiplier", 101, 2, 1000000);
        LOOT_BOMB_MAX_COPIES_PER_KILL = builder
                .comment("战利品爆炸：单次击杀最多生成的掉落副本数（默认 20；光环/范围击杀大量怪物时防掉落物实体爆炸，0=不限制）")
                .defineInRange("lootBombMaxCopiesPerKill", 20, 0, 1000000);
        MAGNET_MAX_PER_TICK = builder
                .comment("磁力光环：单 tick 最多处理的掉落物+经验球数量（默认 64；防止上千掉落物同时瞬移导致卡顿）")
                .defineInRange("magnetMaxPerTick", 64, 1, 1000000);
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
                .comment("暴击增幅：每点暴击伤害倍率（小数，0.05 = +5%/点，1.2.3 ×10）")
                .defineInRange("critDamagePerPoint", 0.05, 0.0, 0.5);
        LIFESTEAL_PER_POINT = builder
                .comment("生命汲取：每点吸血比例（小数，0.001 = 0.1%/点，1000 点 = 100% 吸血）")
                .defineInRange("lifestealPerPoint", 0.001, 0.0001, 0.05);
        LIFESTEAL_AMP_PER_POINT = builder
                .comment("吸血增幅：每点吸血量倍率（小数，0.04 = +4%/点，1.2.3 ×10）")
                .defineInRange("lifestealAmpPerPoint", 0.04, 0.0, 0.5);
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
        MACHINE_STAR_COST = builder
                .comment("机械共鸣·机械之星：一次性消耗技能点（默认 1000）")
                .defineInRange("machineStarCost", 1000L, 1L, Long.MAX_VALUE);
        MACHINE_RESONANCE_COST = builder
                .comment("机械共鸣技能：一次性消耗技能点（默认 5000；学习并开启后模拟玩家机器才能继承对应技能）")
                .defineInRange("machineResonanceCost", 5000L, 1L, Long.MAX_VALUE);
        builder.pop();

        SPEC = builder.build();
    }
}
