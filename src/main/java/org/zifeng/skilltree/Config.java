package org.zifeng.skilltree;

import net.minecraftforge.common.ForgeConfigSpec;
import org.zifeng.skilltree.skill.Skills;

/**
 * Common config (COMMON, hot-reloadable in-game).
 * 通用配置（可在游戏内热重载）。
 */
public class Config {
    public static final ForgeConfigSpec SPEC;

    /** FE consumed per 1 skill point (default 100M). 每 1 技能点所需能量 */
    public static final ForgeConfigSpec.LongValue ENERGY_PER_SKILL_POINT;

    /** Staircase cost: starting cost per point (FE, default 10K). 阶梯消耗起始值 */
    public static final ForgeConfigSpec.LongValue ENERGY_START_COST;

    /** Staircase cost: linear ramp from start to final cost within first N points (default 2000). 阶梯点数 */
    public static final ForgeConfigSpec.IntValue ENERGY_STEP_POINTS;

    /** Converter: max FE/t input (default 100K). 技能点转换机每 tick 输入上限 */
    public static final ForgeConfigSpec.LongValue MACHINE_MAX_INPUT_RATE;

    /** Converter: energy buffer cap (FE, 64-bit Long.MAX_VALUE); stops receiving at cap. 能量缓冲上限 */
    public static final ForgeConfigSpec.LongValue MACHINE_ENERGY_CAPACITY;

    /** Skill tree screen background color (light gray, ARGB). 技能树界面背景色 */
    public static final ForgeConfigSpec.IntValue SKILL_TREE_BACKGROUND_COLOR;

    /** Skill tree screen border color (light blue, ARGB). 技能树界面边框色 */
    public static final ForgeConfigSpec.IntValue SKILL_TREE_BORDER_COLOR;

    /** Whether the attribute panel is visible (skill tree screen). 属性面板显示开关 */
    public static final ForgeConfigSpec.BooleanValue PANEL_VISIBLE;

    /** Attribute panel position: 0=right, 1=bottom. 属性面板位置 */
    public static final ForgeConfigSpec.IntValue PANEL_POSITION;

    /** Converter GUI progress bar color (starlight blue, ARGB). 机器进度条颜色 */
    public static final ForgeConfigSpec.IntValue MACHINE_PROGRESS_COLOR;

    // ============ Economy values (hot-reloadable) / 技能树经济数值（可热重载） ============

    /** Skill points per level for base skills. 基础技能每级消耗 */
    public static final ForgeConfigSpec.DoubleValue BASE_POINT_COST;

    /** Skill points per level for amplify skills. 特殊增幅每级消耗 */
    public static final ForgeConfigSpec.DoubleValue AMPLIFY_POINT_COST;

    /** Ultimate prerequisite: points required in base/amplify columns. 终极前置需求 */
    public static final ForgeConfigSpec.IntValue ULTIMATE_REQUIRE_POINTS;

    /** Favor of the Universe: one-time point cost. 宇宙的青睐消耗 */
    public static final ForgeConfigSpec.LongValue ULT_FAVOR_COST;

    /** Night Vision / Saturation: one-time point cost. 夜视饱食消耗 */
    public static final ForgeConfigSpec.LongValue MINOR_ULT_COST;

    /** Aura base cost (per level). 杀戮光环基础消耗 */
    public static final ForgeConfigSpec.LongValue AURA_BASE_COST;

    /** Aura cost multiplier per level. 光环消耗递增倍率 */
    public static final ForgeConfigSpec.DoubleValue AURA_COST_MULTIPLIER;

    // ============ Magnet effects (hot-reloadable) / 磁铁效果（可热重载） ============

    /** Skill points to enable magnet (default 10). 开启磁铁消耗 */
    public static final ForgeConfigSpec.DoubleValue MAGNET_COST;

    /** Magnet item pickup radius (blocks, default 8, max 32). 磁铁物品半径 */
    public static final ForgeConfigSpec.DoubleValue MAGNET_ITEM_RADIUS;

    /** Magnet XP pickup radius (blocks, default 4, max 32). 磁铁经验半径 */
    public static final ForgeConfigSpec.DoubleValue MAGNET_XP_RADIUS;

    /** Void Spear: magnet radius boost (blocks, default 55, both XP & items). 虚空磁铁范围 */
    public static final ForgeConfigSpec.DoubleValue VOID_MAGNET_RADIUS;

    /** Aura Lock: one-time point cost (default 1000). 光环锁定消耗 */
    public static final ForgeConfigSpec.DoubleValue LOCK_COST;

    /** Aura Void Spear: one-time point cost (default 5000). 虚空之矛消耗 */
    public static final ForgeConfigSpec.DoubleValue VOID_AURA_COST;

    /** Void Body: one-time point cost (default 5000). 虚空之躯消耗 */
    public static final ForgeConfigSpec.DoubleValue VOID_BODY_COST;

    /** Glowing (node ultimate): radius to apply Glowing to nearby mobs (blocks, default 35). 发光半径 */
    public static final ForgeConfigSpec.DoubleValue GLOW_RADIUS;

    // ============ Slaughter Aura (hot-reloadable) / 杀戮光环（可热重载） ============

    /** Aura auto-attack radius (blocks). 光环攻击半径 */
    public static final ForgeConfigSpec.DoubleValue AURA_ATTACK_RADIUS;

    /** Void Spear: aura attack radius boost (blocks, ~50 for one-shot range). 虚空光环半径 */
    public static final ForgeConfigSpec.DoubleValue VOID_AURA_RADIUS;

    /** Aura base attack interval (ticks, 200 = once per 10s, without speed aura). 光环基础间隔 */
    public static final ForgeConfigSpec.IntValue AURA_BASE_INTERVAL_TICKS;

    /** Aura Speed: interval reduction per level (ticks; 20 levels -> 10 ticks = 2/s). 光环速度缩减 */
    public static final ForgeConfigSpec.DoubleValue AURA_SPEED_INTERVAL_REDUCTION;

    /** 杀戮光环·伤害：每级伤害倍率（小数，0.10 = 每级 +10%，MULTIPLY_TOTAL 乘算） */
    public static final ForgeConfigSpec.DoubleValue AURA_DAMAGE_MULTIPLIER_PER_LEVEL;

    /** Chaos damage: armor-piercing true damage ratio on aura hits (0.2 = 20% of main damage). 混沌伤害比例 */
    public static final ForgeConfigSpec.DoubleValue AURA_CHAOS_DAMAGE_RATIO;


    // ============ Ultimate nodes (hot-reloadable) / 终极节点（可热重载） ============

    /** Blood Rage / Golden Body / Rebirth: one-time cost (default 500). 终极基础消耗 */
    public static final ForgeConfigSpec.LongValue ULT_BASE_COST;

    /** Death's Gaze: one-time cost (default 1000). 死神凝视消耗 */
    public static final ForgeConfigSpec.LongValue ULT_REAPER_COST;

    /** All-round Mastery: one-time cost (default 5000). 全能精通消耗 */
    public static final ForgeConfigSpec.LongValue ULT_MASTER_COST;

    /** Node-type multi-level ultimate: cost step-up ratio per level (0.1 = +10%). 终极阶梯比率 */
    public static final ForgeConfigSpec.DoubleValue ULTIMATE_STEP_RATE;

    /** Loot Bomb: drop multiplier cap (level 1 = 1x, linear 1+level; default cap 101). 战利品爆炸封顶 */
    public static final ForgeConfigSpec.IntValue LOOT_BOMB_MAX_MULTIPLIER;

    /** Loot Bomb: max drop copies per kill (perf guard vs entity explosion on mass kills). 单次掉落副本上限 */
    public static final ForgeConfigSpec.IntValue LOOT_BOMB_MAX_COPIES_PER_KILL;

    /** Magnet aura: max items+XP orbs processed per tick (perf guard). 磁力每 tick 上限 */
    public static final ForgeConfigSpec.IntValue MAGNET_MAX_PER_TICK;

    /** Mastery: extra multiplier on all base attributes (fraction). 全能精通增幅 */
    public static final ForgeConfigSpec.DoubleValue MASTER_BONUS;

    /** Mastery: skill point gain rate (0.8 = -20%). 全能精通技能点倍率 */
    public static final ForgeConfigSpec.DoubleValue MASTER_SKILL_POINT_RATE;

    /** Mastery: full damage reduction (1.0 = 100% immune; all damage types incl. true/chaos/command). 全能精通免伤 */
    public static final ForgeConfigSpec.DoubleValue MASTER_DAMAGE_REDUCTION;

    /** Mastery: undying cooldown (ticks, 1200 = 1 min). 全能精通免死冷却 */
    public static final ForgeConfigSpec.IntValue MASTER_UNDYING_COOLDOWN;

    /** Mastery: undying invulnerability duration (ticks, 60 = 3s). 全能精通免死无敌时长 */
    public static final ForgeConfigSpec.IntValue MASTER_UNDYING_INVULN;

    /** Mastery: undying heal ratio (0.5 = 50%). 全能精通免死回血 */
    public static final ForgeConfigSpec.DoubleValue MASTER_UNDYING_HEALTH;

    /** Blood Rage: permanent attack bonus (0.5 = +50%). 浴血奋战攻击 */
    public static final ForgeConfigSpec.DoubleValue BLOOD_ATTACK_BONUS;

    /** Blood Rage: permanent max health bonus (0.5 = +50%). 浴血奋战生命 */
    public static final ForgeConfigSpec.DoubleValue BLOOD_HEALTH_BONUS;

    /** Golden Body: permanent Resistance level (0 = off). 金身抗性等级 */
    public static final ForgeConfigSpec.IntValue GOLDEN_RESISTANCE_LEVEL;

    /** Golden Body: permanent Absorption level (0 = off). 金身吸收等级 */
    public static final ForgeConfigSpec.IntValue GOLDEN_ABSORPTION_LEVEL;

    /** Golden Body: permanent Fire Resistance level (0 = off). 金身抗火等级 */
    public static final ForgeConfigSpec.IntValue GOLDEN_FIRE_RESISTANCE_LEVEL;

    // ============ New skill values (hot-reloadable) / 新增技能数值（可热重载） ============

    /** Crit: crit chance per point (fraction). 暴击几率每点 */
    public static final ForgeConfigSpec.DoubleValue CRIT_CHANCE_PER_POINT;

    /** Crit base damage multiplier. 暴击基础倍率 */
    public static final ForgeConfigSpec.DoubleValue CRIT_DAMAGE_BASE;

    /** Crit Amp: crit damage multiplier per point (fraction). 暴击增幅每点 */
    public static final ForgeConfigSpec.DoubleValue CRIT_DAMAGE_PER_POINT;

    /** Lifesteal: heal ratio per point (fraction). 吸血每点 */
    public static final ForgeConfigSpec.DoubleValue LIFESTEAL_PER_POINT;

    /** Lifesteal Amp: lifesteal amount multiplier per point (fraction). 吸血增幅每点 */
    public static final ForgeConfigSpec.DoubleValue LIFESTEAL_AMP_PER_POINT;

    /** Rebirth: cooldown (ticks, 1200 = 1 min). 凤凰涅槃冷却 */
    public static final ForgeConfigSpec.IntValue REVIVE_COOLDOWN_TICKS;

    /** Rebirth: health ratio on revive (0.5 = 50%). 凤凰涅槃回血 */
    public static final ForgeConfigSpec.DoubleValue REVIVE_HEALTH_RATIO;

    /** Death's Gaze: execute HP threshold (0.15 = below 15%). 处决阈值 */
    public static final ForgeConfigSpec.DoubleValue REAPER_THRESHOLD;

    /** Death's Gaze: execute chance (0.3 = 30%). 处决概率 */
    public static final ForgeConfigSpec.DoubleValue REAPER_CHANCE;

    /** Death's Gaze: execute damage (huge one-shot). 处决伤害 */
    public static final ForgeConfigSpec.DoubleValue REAPER_DAMAGE;

    /** Healing Aura: radius (blocks). 治愈光环半径 */
    public static final ForgeConfigSpec.DoubleValue AURA_HEAL_RADIUS;

    /** Reset (R key): skill point refund ratio (1.0 = full refund). 重洗返还比例 */
    public static final ForgeConfigSpec.DoubleValue RESET_REFUND_RATE;

    /** Machine Star: one-time point cost (default 1000). 机械之星消耗 */
    public static final ForgeConfigSpec.LongValue MACHINE_STAR_COST;

    /** Machine Resonance skill: one-time point cost (default 5000). 机械共鸣消耗 */
    public static final ForgeConfigSpec.LongValue MACHINE_RESONANCE_COST;

    // ============ Per-point attribute bonuses (2026-08-29: base / multi-level ultimate / amplify) / 属性每点加成（全量可配置） ============
    // All per-point values are hot-reloadable. 全部每点数值可热重载

    /** Body HP: +X max health per point (default 2.0). 生命强化每点 */
    public static final ForgeConfigSpec.DoubleValue BODY_HP_PER_POINT;
    /** Body: +X armor per point (default 0.2). 体魄护甲每点 */
    public static final ForgeConfigSpec.DoubleValue BODY_ARMOR_PER_POINT;
    /** Body: +X damage reduction per point (default 0.0005 = 0.05%). 体魄减伤每点 */
    public static final ForgeConfigSpec.DoubleValue BODY_DR_PER_POINT;
    /** Tough: +X armor toughness per point (default 0.3). 韧性每点 */
    public static final ForgeConfigSpec.DoubleValue TOUGH_TOUGHNESS_PER_POINT;
    /** Tough: +X knockback resistance per point (default 0.001 = 0.1%). 坚韧击退每点 */
    public static final ForgeConfigSpec.DoubleValue TOUGH_KB_PER_POINT;
    /** Blade: +X attack damage per point (default 0.4). 锋刃伤害每点 */
    public static final ForgeConfigSpec.DoubleValue BLADE_DAMAGE_PER_POINT;
    /** Attack Speed: +X attack speed per point (default 0.02). 疾攻攻速每点 */
    public static final ForgeConfigSpec.DoubleValue ATTACK_SPEED_PER_POINT;
    /** Mining: +X mining speed per point (default 0.3). 采掘挖速每点 */
    public static final ForgeConfigSpec.DoubleValue MINING_SPEED_PER_POINT;
    /** Move: +X movement speed per point (default 0.005). 疾行移速每点 */
    public static final ForgeConfigSpec.DoubleValue MOVE_SPEED_PER_POINT;
    /** Luck: +X luck per point (default 0.1). 幸运每点 */
    public static final ForgeConfigSpec.DoubleValue LUCK_PER_POINT;
    /** Jump: +X jump strength per point (default 0.01). 跳跃每点 */
    public static final ForgeConfigSpec.DoubleValue JUMP_PER_POINT;
    /** Fly: +X fly speed per point (default 0.005). 飞行每点 */
    public static final ForgeConfigSpec.DoubleValue FLY_SPEED_PER_POINT;
    /** Swim: +X swim speed per point (default 0.005). 游泳每点 */
    public static final ForgeConfigSpec.DoubleValue SWIM_SPEED_PER_POINT;
    /** Armor Truth: +X damage reduction per point (default 0.005 = 0.5%). 金身减伤每点 */
    public static final ForgeConfigSpec.DoubleValue AMP_ARMOR_DR_PER_POINT;
    /** Reach: +X blocks reach & attack range per level (default 1.0). 长臂距离每级 */
    public static final ForgeConfigSpec.DoubleValue REACH_PER_LEVEL;
    /** KB Resist: +X knockback resistance per level (default 0.1 = 10%). 稳如泰山每级 */
    public static final ForgeConfigSpec.DoubleValue KB_RESIST_PER_LEVEL;
    // Amplify per-point percentages (10; attack speed / mining speed differ, rest 0.1)
    // 增幅列每点百分比（10 项；攻速/挖速独立，其余 0.1）
    /** Amp HP: +X max health multiplier per point (default 0.1 = +10%). 血魄真解每点 */
    public static final ForgeConfigSpec.DoubleValue AMP_HP_PER_POINT;
    /** Amp Tough: +X toughness/KB multiplier per point (default 0.1 = +10%). 磐石真解每点 */
    public static final ForgeConfigSpec.DoubleValue AMP_TOUGH_PER_POINT;
    /** Amp Luck: +X luck multiplier per point (default 0.1 = +10%). 鸿运真解每点 */
    public static final ForgeConfigSpec.DoubleValue AMP_LUCK_PER_POINT;
    /** Amp Damage: +X damage multiplier per point (default 0.1 = +10%). 剑心真解每点 */
    public static final ForgeConfigSpec.DoubleValue AMP_DAMAGE_PER_POINT;
    /** Amp Attack Speed: +X attack speed multiplier per point (default 0.08 = +8%). 疾风真解每点 */
    public static final ForgeConfigSpec.DoubleValue AMP_ATTACK_SPEED_PER_POINT;
    /** Amp Mining: +X mining speed multiplier per point (default 0.12 = +12%). 破岩真解每点 */
    public static final ForgeConfigSpec.DoubleValue AMP_MINING_PER_POINT;
    /** Amp Move: +X movement multiplier per point (default 0.1 = +10%). 健步真解每点 */
    public static final ForgeConfigSpec.DoubleValue AMP_MOVE_PER_POINT;
    /** Amp Jump: +X jump multiplier per point (default 0.1 = +10%). 蹦跳真解每点 */
    public static final ForgeConfigSpec.DoubleValue AMP_JUMP_PER_POINT;
    /** Amp Fly: +X fly speed multiplier per point (default 0.1 = +10%). 御空真解每点 */
    public static final ForgeConfigSpec.DoubleValue AMP_FLY_PER_POINT;
    /** Amp Swim: +X swim multiplier per point (default 0.1 = +10%). 游鱼真解每点 */
    public static final ForgeConfigSpec.DoubleValue AMP_SWIM_PER_POINT;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Skill Point Converter: FE consumed per 1 skill point (100M = 100000000)\n技能点转换机：每消耗多少 FE 能量转换 1 点技能点")
                .push("machine");
        ENERGY_PER_SKILL_POINT = builder
                .comment("FE per 1 skill point. Progress resets if energy input stops for >1s.\n每 1 点技能点所需能量（FE）")
                .defineInRange("energyPerSkillPoint", 100_000_000L, 1L, Long.MAX_VALUE);
        ENERGY_START_COST = builder
                .comment("Staircase cost: starting cost per point (FE, default 10K) for fast early leveling.\n阶梯消耗：起始每点消耗（FE，默认 1 万）")
                .defineInRange("energyStartCost", 10_000L, 1L, Long.MAX_VALUE);
        ENERGY_STEP_POINTS = builder
                .comment("Staircase cost: ramps linearly from start to final cost within the first N points (default 2000), then stays final.\n阶梯消耗：前 N 点内线性递增到最终值")
                .defineInRange("energyStepPoints", 2000, 1, 1000000);
        MACHINE_MAX_INPUT_RATE = builder
                .comment("Converter: max energy input per tick (FE/t, default 100000). GUI \"Unlimited Input\" ignores this.\n技能点转换机：每 tick 最大输入能量")
                .defineInRange("machineMaxInputRate", 100_000L, 1L, Long.MAX_VALUE);
        MACHINE_ENERGY_CAPACITY = builder
                .comment("Converter: energy buffer cap (FE, 64-bit Long.MAX_VALUE = 9223372036854775807); stops receiving at cap.\n技能点转换机：能量缓冲上限")
                .defineInRange("machineEnergyCapacity", Long.MAX_VALUE, 1L, Long.MAX_VALUE);
        MACHINE_PROGRESS_COLOR = builder
                .comment("Converter GUI progress bar color (ARGB, starlight blue 0xFF4FC3F7 default).\n机器界面进度条颜色")
                .defineInRange("machineProgressColor", 0xFF4FC3F7, Integer.MIN_VALUE, Integer.MAX_VALUE);
        builder.pop();

        builder.comment("Skill tree screen style\n技能树界面样式").push("skillTree");
        SKILL_TREE_BACKGROUND_COLOR = builder
                .comment("Skill tree background (ARGB, light gray 0xFFBEBEBE; must be fully opaque).\n技能树界面背景色")
                .defineInRange("skillTreeBackgroundColor", 0xFFBEBEBE, Integer.MIN_VALUE, Integer.MAX_VALUE);
        SKILL_TREE_BORDER_COLOR = builder
                .comment("Skill tree border color (ARGB, light blue 0xFF87CEEB).\n技能树界面边框色")
                .defineInRange("skillTreeBorderColor", 0xFF87CEEB, Integer.MIN_VALUE, Integer.MAX_VALUE);
        PANEL_VISIBLE = builder
                .comment("Whether the attribute panel is visible (toggle in the panel top-right corner).\n属性面板是否显示")
                .define("panelVisible", true);
        PANEL_POSITION = builder
                .comment("Attribute panel position (0=right, 1=bottom; Shift+click the panel toggle to switch).\n属性面板位置")
                .defineInRange("panelPosition", 0, 0, 1);
        builder.pop();

        builder.comment("Economy: costs for leveling / ultimate unlocks / aura. Reopen the screen or press N to refresh after changes.\n技能树经济数值")
                .push("economy");
        BASE_POINT_COST = builder
                .comment("Base skill cost per level (default 1.0).\n基础技能每级消耗")
                .defineInRange("basePointCost", Skills.BASE_POINT_COST, 0.01, 100.0);
        AMPLIFY_POINT_COST = builder
                .comment("Amplify skill cost per level (default 2.0).\n特殊增幅每级消耗")
                .defineInRange("amplifyPointCost", Skills.AMPLIFY_POINT_COST, 0.01, 100.0);
        ULTIMATE_REQUIRE_POINTS = builder
                .comment("Ultimate prerequisite: points required in base/amplify columns (default 500).\n终极节点前置点数")
                .defineInRange("ultimateRequirePoints", Skills.ULTIMATE_REQUIRE_POINTS, 1, 10000);
        ULT_FAVOR_COST = builder
                .comment("Favor of the Universe: one-time point cost (default 1000).\n宇宙的青睐消耗")
                .defineInRange("ultFavorCost", Skills.ULT_FAVOR_COST, 1L, Long.MAX_VALUE);
        MINOR_ULT_COST = builder
                .comment("Night Vision / Saturation: one-time point cost (default 100).\n夜视/饱食消耗")
                .defineInRange("minorUltCost", 100L, 1L, Long.MAX_VALUE);
        AURA_BASE_COST = builder
                .comment("Aura base cost (per level, default 1000).\n杀戮光环基础消耗")
                .defineInRange("auraBaseCost", Skills.AURA_BASE_COST, 1L, Long.MAX_VALUE);
        AURA_COST_MULTIPLIER = builder
                .comment("Aura cost multiplier per level (default 1.05; next cost = base × rate^level).\n杀戮光环消耗递增倍率")
                .defineInRange("auraCostMultiplier", Skills.AURA_COST_MULTIPLIER, 1.0, 10.0);
        builder.pop();

        builder.comment("Slaughter Aura: attack radius & frequency. Changes apply immediately.\n杀戮光环：攻击半径与频率")
                .push("aura");
        AURA_ATTACK_RADIUS = builder
                .comment("Auto-attack radius (blocks, default 20).\n自动攻击半径")
                .defineInRange("attackRadius", 20.0, 1.0, 128.0);
        VOID_AURA_RADIUS = builder
                .comment("Void Spear: aura attack radius boost (blocks, default 50, one-shot range).\n虚空之矛光环半径")
                .defineInRange("voidAuraRadius", 50.0, 1.0, 128.0);
        AURA_BASE_INTERVAL_TICKS = builder
                .comment("Aura base attack interval (ticks, 200 = once per 10s, without Speed aura).\n光环基础攻击间隔")
                .defineInRange("auraBaseIntervalTicks", 200, 10, 12000);
        AURA_SPEED_INTERVAL_REDUCTION = builder
                .comment("Aura Speed: interval reduction per level (multiplicative; 0.1 = ×0.9 per level;\nfirst 10 levels: 200->70 ticks, level 20 ~ 24 ticks = ~1.2 attacks/s).\n光环速度：每级攻击间隔缩减比例")
                .defineInRange("auraSpeedIntervalReduction", 0.1, 0.0, 0.5);
        AURA_DAMAGE_MULTIPLIER_PER_LEVEL = builder
                .comment("Aura Damage: multiplier per level (0.10 = +10%/lvl, stacks multiplicatively on attack damage).\n杀戮光环·伤害每级倍率")
                .defineInRange("damageMultiplierPerLevel", 0.10, 0.001, 1.0);
        AURA_CHAOS_DAMAGE_RATIO = builder
                .comment("Chaos damage: armor-piercing true damage ratio on aura hits (0.2 = 20% of main damage; Draconic Evolution chaos).\n混沌伤害比例")
                .defineInRange("auraChaosDamageRatio", 0.2, 0.0, 1.0);
        builder.pop();

        builder.comment("Magnet: vacuum XP & item drops (toggle with H). Changes apply immediately.\n磁铁效果")
                .push("magnet");
        MAGNET_COST = builder
                .comment("Skill points to enable magnet (default 10).\n开启磁铁消耗")
                .defineInRange("magnetCost", 10.0, 0.0, 100000.0);
        MAGNET_ITEM_RADIUS = builder
                .comment("Item pickup radius (blocks, default 20, all directions, max 32).\n吸取掉落物半径")
                .defineInRange("magnetItemRadius", 20.0, 1.0, 32.0);
        MAGNET_XP_RADIUS = builder
                .comment("XP pickup radius (blocks, default 20, all directions, max 32).\n吸取经验半径")
                .defineInRange("magnetXpRadius", 20.0, 1.0, 32.0);
        VOID_MAGNET_RADIUS = builder
                .comment("Void Spear: magnet radius boost (blocks, default 55, XP & items; requires Void Spear enabled).\n虚空之矛磁铁范围")
                .defineInRange("voidMagnetRadius", 55.0, 1.0, 128.0);
        LOCK_COST = builder
                .comment("Aura Lock: one-time point cost (default 1000).\n光环锁定消耗")
                .defineInRange("lockCost", 1000.0, 1.0, 1000000.0);
        VOID_AURA_COST = builder
                .comment("Aura Void Spear: one-time point cost (default 5000).\n虚空之矛消耗")
                .defineInRange("voidAuraCost", 5000.0, 1.0, 10000000.0);
        VOID_BODY_COST = builder
                .comment("Void Body: one-time point cost (default 5000).\n虚空之躯消耗")
                .defineInRange("voidBodyCost", 5000.0, 1.0, 10000000.0);
        GLOW_RADIUS = builder
                .comment("Glowing (node ultimate): radius to apply Glowing to nearby mobs (blocks, default 35).\n发光半径")
                .defineInRange("glowRadius", 35.0, 1.0, 128.0);
        builder.pop();

        builder.comment("Ultimate passive values. Changes apply immediately.\n终极节点被动数值")
                .push("ultimate");
        MASTER_BONUS = builder
                .comment("Mastery: extra multiplier on all base attributes (0.25 = +25%).\n全能精通属性增幅")
                .defineInRange("masterBonus", 0.25, 0.0, 5.0);
        MASTER_SKILL_POINT_RATE = builder
                .comment("Mastery: skill point gain rate (0.8 = -20%).\n全能精通技能点倍率")
                .defineInRange("masterSkillPointRate", 0.8, 0.01, 5.0);
        MASTER_DAMAGE_REDUCTION = builder
                .comment("Mastery: full damage reduction (1.0 = 100% immune; all damage types incl. true/chaos/command).\n全能精通全伤害减免")
                .defineInRange("masterDamageReduction", 1.0, 0.0, 1.0);
        MASTER_UNDYING_COOLDOWN = builder
                .comment("Mastery: undying cooldown (ticks, 1200 = 1 min).\n全能精通免死冷却")
                .defineInRange("masterUndyingCooldown", 1200, 100, 240000);
        MASTER_UNDYING_INVULN = builder
                .comment("Mastery: undying invulnerability (ticks, 60 = 3s).\n全能精通免死无敌时长")
                .defineInRange("masterUndyingInvuln", 60, 10, 1200);
        MASTER_UNDYING_HEALTH = builder
                .comment("Mastery: undying heal ratio (0.5 = 50%).\n全能精通免死回血比例")
                .defineInRange("masterUndyingHealth", 0.5, 0.05, 1.0);
        ULT_BASE_COST = builder
                .comment("Blood Rage / Golden Body / Rebirth: one-time point cost (default 500).\n浴血奋战等一次性消耗")
                .defineInRange("ultBaseCost", 500L, 1L, Long.MAX_VALUE);
        ULT_REAPER_COST = builder
                .comment("Death's Gaze: one-time point cost (default 1000).\n死神凝视消耗")
                .defineInRange("ultReaperCost", 1000L, 1L, Long.MAX_VALUE);
        ULT_MASTER_COST = builder
                .comment("All-round Mastery: one-time point cost (default 5000).\n全能精通消耗")
                .defineInRange("ultMasterCost", 5000L, 1L, Long.MAX_VALUE);
        ULTIMATE_STEP_RATE = builder
                .comment("Node-type multi-level ultimate: cost increase ratio per level (0.1 = +10%).\n终极节点阶梯比率")
                .defineInRange("ultimateStepRate", 0.1, 0.0, 1.0);
        LOOT_BOMB_MAX_MULTIPLIER = builder
                .comment("Loot Bomb: drop multiplier cap (level 1 = 1x, linear 1+level, 100 levels = 101x; default 101).\n战利品爆炸封顶")
                .defineInRange("lootBombMaxMultiplier", 101, 2, 1000000);
        LOOT_BOMB_MAX_COPIES_PER_KILL = builder
                .comment("Loot Bomb: max drop copies per kill (default 20; prevents drop entity explosion on mass kills, 0 = unlimited).\n单次掉落副本上限")
                .defineInRange("lootBombMaxCopiesPerKill", 20, 0, 1000000);
        MAGNET_MAX_PER_TICK = builder
                .comment("Magnet aura: max items+XP orbs processed per tick (default 64; prevents lag from mass teleport).\n磁力每 tick 上限")
                .defineInRange("magnetMaxPerTick", 64, 1, 1000000);
        BLOOD_ATTACK_BONUS = builder
                .comment("Blood Rage: permanent attack bonus (0.5 = +50%).\n浴血奋战攻击增幅")
                .defineInRange("bloodAttackBonus", 0.5, 0.0, 10.0);
        BLOOD_HEALTH_BONUS = builder
                .comment("Blood Rage: permanent max health bonus (0.5 = +50%).\n浴血奋战生命增幅")
                .defineInRange("bloodHealthBonus", 0.5, 0.0, 10.0);
        GOLDEN_RESISTANCE_LEVEL = builder
                .comment("Golden Body: permanent Resistance level (10 = level 10, 0 = off).\n金身抗性等级")
                .defineInRange("goldenResistanceLevel", 10, 0, 255);
        GOLDEN_ABSORPTION_LEVEL = builder
                .comment("Golden Body: permanent Absorption level (100 = level 100, 0 = off).\n金身吸收等级")
                .defineInRange("goldenAbsorptionLevel", 100, 0, 255);
        GOLDEN_FIRE_RESISTANCE_LEVEL = builder
                .comment("Golden Body: permanent Fire Resistance level (5 = level 5, 0 = off).\n金身抗火等级")
                .defineInRange("goldenFireResistanceLevel", 5, 0, 255);
        builder.pop();

        builder.comment("New skill values (crit/lifesteal/rebirth/healing aura) & skill reset. Changes apply immediately.\n新增技能数值与技能重洗")
                .push("newSkills");
        CRIT_CHANCE_PER_POINT = builder
                .comment("Crit: chance per point (0.001 = 0.1%/pt, 1000 pts = 100%).\n暴击几率每点")
                .defineInRange("critChancePerPoint", 0.001, 0.0001, 0.1);
        CRIT_DAMAGE_BASE = builder
                .comment("Crit base damage multiplier (1.5 = 1.5x damage on crit).\n暴击基础伤害倍率")
                .defineInRange("critDamageBase", 1.5, 1.0, 10.0);
        CRIT_DAMAGE_PER_POINT = builder
                .comment("Crit Amp: crit damage multiplier per point (0.05 = +5%/pt).\n暴击增幅每点")
                .defineInRange("critDamagePerPoint", 0.05, 0.0, 0.5);
        LIFESTEAL_PER_POINT = builder
                .comment("Lifesteal: heal ratio per point (0.001 = 0.1%/pt, 1000 pts = 100%).\n吸血每点")
                .defineInRange("lifestealPerPoint", 0.001, 0.0001, 0.05);
        LIFESTEAL_AMP_PER_POINT = builder
                .comment("Lifesteal Amp: amount multiplier per point (0.04 = +4%/pt).\n吸血增幅每点")
                .defineInRange("lifestealAmpPerPoint", 0.04, 0.0, 0.5);
        REVIVE_COOLDOWN_TICKS = builder
                .comment("Rebirth: cooldown (ticks, 1200 = 1 min).\n凤凰涅槃冷却")
                .defineInRange("reviveCooldownTicks", 1200, 100, 240000);
        REVIVE_HEALTH_RATIO = builder
                .comment("Rebirth: health ratio on revive (0.5 = 50%).\n凤凰涅槃回血比例")
                .defineInRange("reviveHealthRatio", 0.5, 0.01, 1.0);
        REAPER_THRESHOLD = builder
                .comment("Death's Gaze: execute HP threshold (0.15 = below 15%).\n处决生命阈值")
                .defineInRange("reaperThreshold", 0.15, 0.01, 0.9);
        REAPER_CHANCE = builder
                .comment("Death's Gaze: execute chance (0.3 = 30%).\n处决概率")
                .defineInRange("reaperChance", 0.3, 0.01, 1.0);
        REAPER_DAMAGE = builder
                .comment("Death's Gaze: execute damage (default 99999, instant kill).\n处决伤害")
                .defineInRange("reaperDamage", 99999.0, 100.0, 1_000_000.0);
        AURA_HEAL_RADIUS = builder
                .comment("Healing Aura: radius (blocks, default 10).\n治愈光环半径")
                .defineInRange("auraHealRadius", 10.0, 1.0, 64.0);
        RESET_REFUND_RATE = builder
                .comment("Skill reset (R key in screen): skill point refund ratio (1.0 = full, 0.8 = 80%).\n重洗返还比例")
                .defineInRange("resetRefundRate", 1.0, 0.0, 1.0);
        MACHINE_STAR_COST = builder
                .comment("Machine Resonance - Machine Star: one-time point cost (default 1000).\n机械之星消耗")
                .defineInRange("machineStarCost", 1000L, 1L, Long.MAX_VALUE);
        MACHINE_RESONANCE_COST = builder
                .comment("Machine Resonance skill: one-time point cost (default 5000; fake-player machines inherit the skill only when learned & enabled).\n机械共鸣技能消耗")
                .defineInRange("machineResonanceCost", 5000L, 1L, Long.MAX_VALUE);
        builder.pop();

        builder.comment("属性每点加成（2026-08-29 新增）：14 基础 + 2 多级终极 + 10 增幅的全部每点数值，改动热重载生效")
                .push("attributePerPoint");
        BODY_HP_PER_POINT = builder.comment("Body HP: +X max health per point (default 2.0)\n生命强化每点").defineInRange("bodyHpPerPoint", 2.0, 0.0, 1000.0);
        BODY_ARMOR_PER_POINT = builder.comment("Body: +X armor per point (default 0.2)\n体魄护甲每点").defineInRange("bodyArmorPerPoint", 0.2, 0.0, 100.0);
        BODY_DR_PER_POINT = builder.comment("Body: +X damage reduction per point (default 0.0005 = 0.05%)\n体魄减伤每点").defineInRange("bodyDrPerPoint", 0.0005, 0.0, 1.0);
        TOUGH_TOUGHNESS_PER_POINT = builder.comment("Tough: +X armor toughness per point (default 0.3)\n韧性每点").defineInRange("toughToughnessPerPoint", 0.3, 0.0, 100.0);
        TOUGH_KB_PER_POINT = builder.comment("Tough: +X knockback resistance per point (default 0.001 = 0.1%)\n坚韧击退每点").defineInRange("toughKbPerPoint", 0.001, 0.0, 1.0);
        BLADE_DAMAGE_PER_POINT = builder.comment("Blade: +X attack damage per point (default 0.4)\n锋刃伤害每点").defineInRange("bladeDamagePerPoint", 0.4, 0.0, 100.0);
        ATTACK_SPEED_PER_POINT = builder.comment("Attack Speed: +X attack speed per point (default 0.02)\n疾攻攻速每点").defineInRange("attackSpeedPerPoint", 0.02, 0.0, 100.0);
        MINING_SPEED_PER_POINT = builder.comment("Mining: +X mining speed per point (default 0.3)\n采掘挖速每点").defineInRange("miningSpeedPerPoint", 0.3, 0.0, 100.0);
        MOVE_SPEED_PER_POINT = builder.comment("Move: +X movement speed per point (default 0.005)\n疾行移速每点").defineInRange("moveSpeedPerPoint", 0.005, 0.0, 100.0);
        LUCK_PER_POINT = builder.comment("Luck: +X luck per point (default 0.1)\n幸运每点").defineInRange("luckPerPoint", 0.1, 0.0, 100.0);
        JUMP_PER_POINT = builder.comment("Jump: +X jump strength per point (default 0.01)\n跳跃每点").defineInRange("jumpPerPoint", 0.01, 0.0, 100.0);
        FLY_SPEED_PER_POINT = builder.comment("Fly: +X fly speed per point (default 0.005)\n飞行每点").defineInRange("flySpeedPerPoint", 0.005, 0.0, 100.0);
        SWIM_SPEED_PER_POINT = builder.comment("Swim: +X swim speed per point (default 0.005)\n游泳每点").defineInRange("swimSpeedPerPoint", 0.005, 0.0, 100.0);
        AMP_ARMOR_DR_PER_POINT = builder.comment("Armor Truth: +X damage reduction per point (default 0.005 = 0.5%)\n金身减伤每点").defineInRange("ampArmorDrPerPoint", 0.005, 0.0, 1.0);
        REACH_PER_LEVEL = builder.comment("Reach: +X blocks reach & attack range per level (default 1.0)\n长臂距离每级").defineInRange("reachPerLevel", 1.0, 0.0, 100.0);
        KB_RESIST_PER_LEVEL = builder.comment("KB Resist: +X knockback resistance per level (default 0.1 = 10%)\n稳如泰山每级").defineInRange("kbResistPerLevel", 0.1, 0.0, 1.0);
        AMP_HP_PER_POINT = builder.comment("Amp HP: +X max health multiplier per point (default 0.1 = +10%)\n血魄真解每点").defineInRange("ampHpPerPoint", 0.1, 0.0, 10.0);
        AMP_TOUGH_PER_POINT = builder.comment("Amp Tough: +X toughness/KB multiplier per point (default 0.1 = +10%)\n磐石真解每点").defineInRange("ampToughPerPoint", 0.1, 0.0, 10.0);
        AMP_LUCK_PER_POINT = builder.comment("Amp Luck: +X luck multiplier per point (default 0.1 = +10%)\n鸿运真解每点").defineInRange("ampLuckPerPoint", 0.1, 0.0, 10.0);
        AMP_DAMAGE_PER_POINT = builder.comment("Amp Damage: +X damage multiplier per point (default 0.1 = +10%)\n剑心真解每点").defineInRange("ampDamagePerPoint", 0.1, 0.0, 10.0);
        AMP_ATTACK_SPEED_PER_POINT = builder.comment("Amp Attack Speed: +X attack speed multiplier per point (default 0.08 = +8%)\n疾风真解每点").defineInRange("ampAttackSpeedPerPoint", 0.08, 0.0, 10.0);
        AMP_MINING_PER_POINT = builder.comment("Amp Mining: +X mining speed multiplier per point (default 0.12 = +12%)\n破岩真解每点").defineInRange("ampMiningPerPoint", 0.12, 0.0, 10.0);
        AMP_MOVE_PER_POINT = builder.comment("Amp Move: +X movement multiplier per point (default 0.1 = +10%)\n健步真解每点").defineInRange("ampMovePerPoint", 0.1, 0.0, 10.0);
        AMP_JUMP_PER_POINT = builder.comment("Amp Jump: +X jump multiplier per point (default 0.1 = +10%)\n蹦跳真解每点").defineInRange("ampJumpPerPoint", 0.1, 0.0, 10.0);
        AMP_FLY_PER_POINT = builder.comment("Amp Fly: +X fly speed multiplier per point (default 0.1 = +10%)\n御空真解每点").defineInRange("ampFlyPerPoint", 0.1, 0.0, 10.0);
        AMP_SWIM_PER_POINT = builder.comment("Amp Swim: +X swim multiplier per point (default 0.1 = +10%)\n游鱼真解每点").defineInRange("ampSwimPerPoint", 0.1, 0.0, 10.0);
        builder.pop();

        SPEC = builder.build();
    }

    /**
     * Called on config reload/load: re-applies all attribute modifiers to online players
     * so per-point stat changes take effect immediately (otherwise only after next spend/login).
     * Config 热重载/加载后重挂在线玩家属性。
     */
    public static void onConfigChanged(net.minecraftforge.fml.event.config.ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        for (net.minecraft.server.level.ServerPlayer p : server.getPlayerList().getPlayers()) {
            org.zifeng.skilltree.skill.SkillEffects.applyAll(p, new org.zifeng.skilltree.data.PlayerSkillRecord(p.getUUID()));
        }
    }
}
