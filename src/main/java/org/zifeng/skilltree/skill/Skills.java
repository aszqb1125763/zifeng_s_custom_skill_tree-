package org.zifeng.skilltree.skill;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.zifeng.skilltree.Config;
import org.zifeng.skilltree.SkillTreeMod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 子枫技能树完整技能定义表。
 * <p>
 * 双分类机制：
 * <ul>
 *   <li>基础属性（BASE）：纯固定数值堆叠，每项上限 {@link #BASE_MAX_POINTS} 点</li>
 *   <li>特殊增幅（AMPLIFY）：纯百分比放大基础数值</li>
 *   <li>终极节点（ULTIMATE）：需前置基础/增幅技能各投入 {@link #ULTIMATE_REQUIRE_POINTS} 点，单次解锁</li>
 * </ul>
 * 统一公式：最终属性 = 全部基础固定数值总和 × (1 + 全部特殊百分比增幅总和)
 */
public final class Skills {
    private Skills() {
    }

    /** 基础类每项上限 */
    public static final int BASE_MAX_POINTS = 1000;
    /** 特殊增幅类每项上限 */
    public static final int AMPLIFY_MAX_POINTS = 500;
    /** 基础技能每级技能点消耗（默认值，可被 Config 覆盖） */
    public static final double BASE_POINT_COST = 1.0;
    /** 特殊增幅每级技能点消耗（默认值，可被 Config 覆盖） */
    public static final double AMPLIFY_POINT_COST = 2.0;
    /** 终极节点前置：两个指定技能各需投入点数（默认值，可被 Config 覆盖） */
    public static final int ULTIMATE_REQUIRE_POINTS = 500;
    /** 宇宙的青睐：一次性消耗技能点（默认值，可被 Config 覆盖） */
    public static final long ULT_FAVOR_COST = 1000;
    /** 杀戮光环基础消耗（每级，默认值，可被 Config 覆盖） */
    public static final long AURA_BASE_COST = 1000;
    /** 杀戮光环每级消耗递增倍率（默认值，可被 Config 覆盖） */
    public static final double AURA_COST_MULTIPLIER = 1.05;

    // ============ Config 驱动 getter（游戏内可热重载） ============

    public static double basePointCost() {
        return Config.BASE_POINT_COST.get();
    }

    public static double amplifyPointCost() {
        return Config.AMPLIFY_POINT_COST.get();
    }

    public static int ultimateRequirePoints() {
        return Config.ULTIMATE_REQUIRE_POINTS.get();
    }

    public static long ultFavorCost() {
        return Config.ULT_FAVOR_COST.get();
    }

    /** 夜视/饱食一次性消耗（Config 可调） */
    public static long minorUltCost() {
        return Config.MINOR_ULT_COST.get();
    }

    public static long auraBaseCost() {
        return Config.AURA_BASE_COST.get();
    }

    /**
     * 基础属性：第 n 级消耗（线性增长，下一级在上一级基础上 +1）。
     * 第 1 级 = 1 点，第 2 级 = 2 点，第 3 级 = 3 点...
     * ⚠️ 64 位返回（2026-08-12 统一）：所有技能消耗一律 long/double，防高等级 int 溢出。
     * @param currentLevel 当前已学等级（第 0 级 = 学第 1 级的消耗）
     */
    public static long getBaseCostAtLevel(int currentLevel) {
        return (long) currentLevel + 1;
    }

    /**
     * 特殊增幅：第 n 级消耗（线性增长，下一级在上一级基础上 +2）。
     * 第 1 级 = 2 点，第 2 级 = 4 点，第 3 级 = 6 点...
     * ⚠️ 64 位返回（2026-08-12 统一）：所有技能消耗一律 long/double，防高等级 int 溢出。
     * @param currentLevel 当前已学等级（第 0 级 = 学第 1 级的消耗）
     */
    public static long getAmplifyCostAtLevel(int currentLevel) {
        return (long) (currentLevel + 1) * 2;
    }

    public static double auraCostMultiplier() {
        return Config.AURA_COST_MULTIPLIER.get();
    }

    /**
     * 终极节点一次性消耗技能点（用户指定，可被 Config 覆盖）：
     * 浴血奋战/不坏金身/凤凰涅槃 = 500，死神凝视 = 1000，全能精通 = 5000，其余普通终极 = 1
     * ⚠️ 64 位返回（2026-08-12 统一）：所有技能消耗一律 long/double，防高等级 int 溢出。
     */
    public static long ultimateCost(String skillId) {
        return switch (skillId) {
            case ULT_BLOOD, ULT_GOLDEN, ULT_REVIVE -> Config.ULT_BASE_COST.get();
            case ULT_REAPER -> Config.ULT_REAPER_COST.get();
            case ULT_MASTER -> Config.ULT_MASTER_COST.get();
            case ULT_VOID_BODY -> Math.round(Config.VOID_BODY_COST.get());
            case AUTO_SMELT -> 30L; // 自动熔炼：一次性 30 点
            case ULT_BREAK_ALL, ULT_UNBREAK_TAG -> 100L; // 万物挖掘/不毁词条：一次性 100 点
            default -> 1L; // 普通终极 1 点
        };
    }

    public enum SkillType {
        /** 魔法增幅（其余模组兼容，独立列，不参与任何前置） */
        MAGIC,
        /** 基础属性（固定数值） */
        BASE,
        /** 特殊增幅（百分比） */
        AMPLIFY,
        /** 终极节点（单次解锁） */
        ULTIMATE,
        /** 杀戮光环（独立系统，不受属性加成） */
        AURA,
        /** 机械共鸣（模拟玩家机器继承开关，独立列，前置=机械之星+对应原技能） */
        MACHINE
    }

    // ============ 基础属性技能 ============
    public static final String BODY_HP = "body_hp";           // 生命强化（每级 +2 生命）
    public static final String BODY = "body";                 // 体魄强化（护甲 + 物理减伤）
    public static final String TOUGH = "tough";               // 坚韧之躯
    public static final String BLADE = "blade";               // 锋刃精通
    public static final String ATTACK_SPEED = "attack_speed"; // 疾攻术
    public static final String MINING = "mining";             // 采掘熟稔
    public static final String MOVE = "move";                 // 疾行步法
    public static final String REGEN = "regen";               // 再生体魄
    public static final String LUCK = "luck";                 // 幸运眷顾
    public static final String JUMP = "jump";                 // 跃升体术（跳跃高度）
    public static final String FLY = "fly";                   // 御空术（飞行速度）
    public static final String SWIM = "swim";                 // 潜游术（游泳速度）
    public static final String CRIT = "crit";                 // 暴击精通（暴击几率）
    public static final String LIFESTEAL = "lifesteal";       // 生命汲取（吸血）
    public static final String THORNS = "thorns";             // 荆棘反伤（受击反弹）
    public static final String ARMOR_PEN = "armor_pen";       // 破甲精通（无视护甲增伤）
    public static final String VILLAGE_HERO = "village_hero"; // 村庄英雄（每级1级效果，上限10级）
    public static final String REACH = "reach";               // 接触距离（触摸/攻击距离，每级+1格，上限50级）
    public static final String GLOW = "glow";                 // 发光（35格生物发光，上限1级）
    public static final String LOOT_BOMB = "loot_bomb";       // 战利品爆炸（掉落翻倍，1级翻一倍，上限100级）
    public static final String UNBREAKABLE = "unbreakable";   // 工具不毁（耐久减免，上限5级，拆自采掘熟稔）
    public static final String MOB_DROP = "mob_drop";         // 生物掉落倍率（上限10级，拆自掉落增幅）
    public static final String BLOCK_DROP = "block_drop";     // 方块掉落倍率（上限10级，拆自掉落增幅）
    public static final String XP_GAIN = "xp_gain";           // 经验获取倍率（上限10级，拆自掉落增幅）
    public static final String MOB_SPAWN_EGG = "mob_spawn_egg"; // 刷怪蛋掉落（上限10级，每级10%，独立不吃增幅）
    public static final String MOB_HEAD = "mob_head";           // 头颅掉落（上限5级，每级20%，独立不吃增幅）

    // ============ 特殊增幅技能（与基础技能一一对应，顺序同纵列1） ============
    public static final String AMP_HP = "amp_hp";                     // 生命增幅（对应生命强化）
    public static final String AMP_ARMOR = "amp_armor";               // 防御强化（对应体魄强化）
    public static final String AMP_TOUGH = "amp_tough";               // 坚韧增幅（对应坚韧之躯）
    public static final String AMP_DAMAGE = "amp_damage";             // 锋刃增幅（对应锋刃精通）
    public static final String AMP_ATTACK_SPEED = "amp_attack_speed"; // 疾攻增幅（对应疾攻术）
    public static final String AMP_MINING = "amp_mining";             // 采掘增幅（对应采掘熟稔）
    public static final String AMP_MOVE = "amp_move";                 // 疾行增幅（对应疾行步法）
    public static final String AMP_REGEN = "amp_regen";               // 再生增幅（对应再生体魄）
    public static final String AMP_LUCK = "amp_luck";                 // 幸运增幅（对应幸运眷顾）
    public static final String AMP_JUMP = "amp_jump";                 // 跃升增幅（对应跃升体术）
    public static final String AMP_FLY = "amp_fly";                   // 御空增幅（对应御空术）
    public static final String AMP_SWIM = "amp_swim";                 // 潜游增幅（对应潜游术）
    public static final String AMP_CRIT = "amp_crit";                 // 暴击增幅（对应暴击精通）
    public static final String AMP_LIFESTEAL = "amp_lifesteal";       // 吸血增幅（对应生命汲取）
    public static final String AMP_THORNS = "amp_thorns";             // 荆棘增幅（对应荆棘反伤）
    public static final String AMP_ARMOR_PEN = "amp_armor_pen";       // 破甲增幅（对应破甲精通）

    // ============ 终极节点 ============
    public static final String ULT_BLOOD = "ult_blood";     // 浴血奋战
    public static final String ULT_GOLDEN = "ult_golden";   // 不坏金身
    public static final String ULT_MASTER = "ult_master";   // 全能精通（毕业）
    public static final String ULT_FAVOR = "ult_favor";     // 宇宙的青睐（真创造飞行）
    public static final String NIGHT_VISION = "night_vision"; // 夜视（100点，1级）
    public static final String SATURATION = "saturation";     // 饱食（100点，1级）
    public static final String ULT_REVIVE = "ult_revive";   // 凤凰涅槃（死亡复活）
    public static final String ULT_REAPER = "ult_reaper";   // 死神凝视（处决低血目标）
    public static final String ULT_VOID_BODY = "ult_void_body"; // 虚空之躯（三层无敌防御）
    public static final String AUTO_SMELT = "auto_smelt";   // 自动熔炼（挖掘自动熔炼矿物，1级，消耗30）
    public static final String ULT_BREAK_ALL = "ult_break_all"; // 万物挖掘（可挖任何方块含基岩，1级，消耗100）
    public static final String ULT_UNBREAK_TAG = "ult_unbreak_tag"; // 不毁词条（铁砧合成Unbreakable工具，1级，消耗100）
    public static final String ULT_SWEEP = "ult_sweep";       // 横扫范围（每级+1格攻击范围，上限10，线性2）
    public static final String ULT_KB_RESIST = "ult_kb_resist"; // 击退抗性（每级+10%，满10级免疫击退，线性2）

    // ============ 杀戮光环（AURA，独立系统） ============
    public static final String AURA_DAMAGE = "aura_damage";   // 杀戮光环·伤害
    public static final String AURA_SPEED = "aura_speed";     // 杀戮光环·速度
    public static final String AURA_HEAL = "aura_heal";       // 治愈光环（群体治疗）
    public static final String AURA_MAGNET = "aura_magnet";   // 磁力光环（吸取经验/掉落物）
    public static final String AURA_TIME = "aura_time";       // 时之环·时间停止（锁定开启时的时间）
    public static final String AURA_WEATHER = "aura_weather"; // 晴空环·永恒晴天（锁定天气）
    public static final String AURA_LOCK = "aura_lock";       // 光环锁定（免疫TP/击退）
    public static final String AURA_EMPOWER = "aura_empower"; // 杀戮光环·强化（混沌/Boss伤害，拆自光环，虚空之矛上方）
    public static final String AURA_VOID = "aura_void";       // 杀戮光环·虚空之矛（虚空伤害/秒杀）

    // ============ 魔法增幅（纵列0，其余模组兼容技能，不参与任何前置） ============
    public static final String MANA_AMP = "mana_amp";                 // 新生魔艺魔力增幅（每级+10%魔力，上限1000）
    public static final String ARS_MANA_REGEN = "ars_mana_regen";     // 新生魔艺魔力恢复（每级+40%恢复，上限1000）
    public static final String IRON_MANA_AMP = "iron_mana_amp";       // 铁魔法魔力增幅（每级+10%魔力，上限1000）
    public static final String IRON_MANA_REGEN = "iron_mana_regen";   // 铁魔法魔力恢复（每级+40%恢复，上限1000）
    public static final String IRON_CAST_TIME = "iron_cast_time";     // 铁魔法吟唱缩减（每级-10%吟唱，上限100，消耗5线性+5）
    public static final String IRON_COOLDOWN = "iron_cooldown";       // 铁魔法法术冷却缩减（每级-10%冷却，上限100，消耗5线性+5）
    // 铁魔法流派法术强度（9个，独立技能，每级+10%，上限1000）
    public static final String IRON_FIRE = "iron_fire";               // 火焰
    public static final String IRON_ICE = "iron_ice";                 // 冰霜
    public static final String IRON_LIGHTNING = "iron_lightning";     // 雷电
    public static final String IRON_HOLY = "iron_holy";               // 神圣
    public static final String IRON_ENDER = "iron_ender";             // 末影
    public static final String IRON_BLOOD = "iron_blood";             // 鲜血
    public static final String IRON_EVOCATION = "iron_evocation";     // 召唤
    public static final String IRON_NATURE = "iron_nature";           // 自然
    public static final String IRON_ELDRITCH = "iron_eldritch";       // 异界

    // ============ 机械共鸣（纵列5，模拟玩家机器继承开关） ============
    public static final String MACHINE_STAR = "machine_star";             // 机械之星（前置核心，无前置，1级，消耗1000）
    public static final String MACHINE_LOOT_BOMB = "machine_loot_bomb";   // 战利品爆炸·共鸣（1级，5000）
    public static final String MACHINE_UNBREAKABLE = "machine_unbreakable"; // 工具不毁·共鸣（1级，5000）
    public static final String MACHINE_MOB_DROP = "machine_mob_drop";     // 生物掉落·共鸣（1级，5000）
    public static final String MACHINE_BLOCK_DROP = "machine_block_drop"; // 方块掉落·共鸣（1级，5000）
    public static final String MACHINE_XP_GAIN = "machine_xp_gain";       // 经验获取·共鸣（1级，5000）
    public static final String MACHINE_SPAWN_EGG = "machine_spawn_egg";   // 刷怪蛋掉落·共鸣（1级，5000）
    public static final String MACHINE_MOB_HEAD = "machine_mob_head";     // 头颅掉落·共鸣（1级，5000）
    public static final String MACHINE_AUTO_SMELT = "machine_auto_smelt"; // 自动熔炼·共鸣（1级，5000）

    /** 所有基础技能（纵列1） */
    public static final List<String> BASE_SKILLS = List.of(BODY_HP, BODY, TOUGH, BLADE, ATTACK_SPEED, MINING, MOVE, REGEN, LUCK, JUMP, FLY, SWIM, CRIT, LIFESTEAL, THORNS, ARMOR_PEN);
    /** 所有增幅技能（纵列2）：与基础技能一一对应，顺序与纵列1相同 */
    public static final List<String> AMPLIFY_SKILLS = List.of(
            AMP_HP, AMP_ARMOR, AMP_TOUGH, AMP_DAMAGE, AMP_ATTACK_SPEED, AMP_MINING, AMP_MOVE,
            AMP_REGEN, AMP_LUCK, AMP_JUMP, AMP_FLY, AMP_SWIM,
            AMP_CRIT, AMP_LIFESTEAL, AMP_THORNS, AMP_ARMOR_PEN);
    /** 所有终极节点（纵列3） */
    public static final List<String> ULTIMATE_SKILLS = List.of(ULT_BLOOD, ULT_GOLDEN, ULT_MASTER, ULT_FAVOR, NIGHT_VISION, SATURATION, ULT_REVIVE, ULT_REAPER, ULT_VOID_BODY, VILLAGE_HERO, REACH, GLOW, LOOT_BOMB, UNBREAKABLE, MOB_DROP, BLOCK_DROP, XP_GAIN, MOB_SPAWN_EGG, MOB_HEAD, AUTO_SMELT, ULT_BREAK_ALL, ULT_UNBREAK_TAG, ULT_SWEEP, ULT_KB_RESIST);
    /** 所有杀戮光环（纵列4）：杀戮光环·强化 在 虚空之矛 上方 */
    public static final List<String> AURA_SKILLS = List.of(AURA_DAMAGE, AURA_SPEED, AURA_HEAL, AURA_MAGNET, AURA_TIME, AURA_WEATHER, AURA_LOCK, AURA_EMPOWER, AURA_VOID);
    /** 所有魔法增幅（纵列0）：其余模组兼容技能（新生魔艺/铁魔法等），不作为任何前置 */
    public static final List<String> MAGIC_SKILLS = List.of(
            // 新生魔艺
            MANA_AMP, ARS_MANA_REGEN,
            // 铁魔法
            IRON_MANA_AMP, IRON_MANA_REGEN, IRON_CAST_TIME, IRON_COOLDOWN,
            IRON_FIRE, IRON_ICE, IRON_LIGHTNING, IRON_HOLY, IRON_ENDER,
            IRON_BLOOD, IRON_EVOCATION, IRON_NATURE, IRON_ELDRITCH);
    /** 所有机械共鸣（纵列5）：机械之星在最上，其余共鸣技能在前置原技能下方 */
    public static final List<String> MACHINE_SKILLS = List.of(
            MACHINE_STAR,
            MACHINE_LOOT_BOMB, MACHINE_UNBREAKABLE, MACHINE_MOB_DROP, MACHINE_BLOCK_DROP,
            MACHINE_XP_GAIN, MACHINE_SPAWN_EGG, MACHINE_MOB_HEAD, MACHINE_AUTO_SMELT);

    public static final List<String> ALL_SKILLS = new ArrayList<>() {{
        addAll(MAGIC_SKILLS);
        addAll(BASE_SKILLS);
        addAll(AMPLIFY_SKILLS);
        addAll(ULTIMATE_SKILLS);
        addAll(AURA_SKILLS);
        addAll(MACHINE_SKILLS);
    }};

    public static SkillType getType(String skillId) {
        if (MAGIC_SKILLS.contains(skillId)) return SkillType.MAGIC;
        if (MACHINE_SKILLS.contains(skillId)) return SkillType.MACHINE;
        if (MACHINE_SKILLS.contains(skillId)) return SkillType.MACHINE;
        if (BASE_SKILLS.contains(skillId)) return SkillType.BASE;
        if (AMPLIFY_SKILLS.contains(skillId)) return SkillType.AMPLIFY;
        if (ULTIMATE_SKILLS.contains(skillId)) return SkillType.ULTIMATE;
        if (AURA_SKILLS.contains(skillId)) return SkillType.AURA;
        return SkillType.BASE;
    }

    /** 杀戮光环：每项上限 */
    public static int getAuraMaxPoints(String skillId) {
        return switch (skillId) {
            case AURA_DAMAGE -> 1000;
            case AURA_SPEED -> 20;
            case AURA_HEAL -> 50;
            case AURA_MAGNET -> 1;
            case AURA_TIME -> 1; // 一次性解锁（100 技能点）
            case AURA_WEATHER -> 1; // 一次性解锁（100 技能点）
            case AURA_LOCK -> 1; // 一次性解锁（1000 技能点）
            case AURA_EMPOWER -> 1; // 一次性解锁（1000 技能点）
            case AURA_VOID -> 1; // 一次性解锁（5000 技能点）
            default -> 0;
        };
    }

    /** 各技能等级上限（按钮第2行显示用）：基础 1000 / 增幅 500 / 终极 1（多级终极各自上限） / 光环各自上限 / 魔法增幅各自上限 / 机械共鸣 1 */
    public static int getMaxPoints(String skillId) {
        return switch (getType(skillId)) {
            case BASE -> BASE_MAX_POINTS;
            case AMPLIFY -> AMPLIFY_MAX_POINTS;
            case ULTIMATE -> getUltimateMaxPoints(skillId);
            case AURA -> getAuraMaxPoints(skillId);
            case MAGIC -> getMagicMaxPoints(skillId);
            case MACHINE -> getMachineMaxPoints(skillId);
        };
    }

    /** 机械共鸣：全部单级解锁（上限 1） */
    public static int getMachineMaxPoints(String skillId) {
        return 1;
    }

    /**
     * 机械共鸣一次性消耗技能点：机械之星 1000，其余共鸣技能 5000（Config 可调）。
     * ⚠️ 64 位返回（2026-08-12 统一）：所有技能消耗一律 long/double，防高等级 int 溢出。
     * @param skillId 机械共鸣技能 ID
     */
    public static long getMachineCost(String skillId) {
        if (MACHINE_STAR.equals(skillId)) {
            return Config.MACHINE_STAR_COST.get();
        }
        return Config.MACHINE_RESONANCE_COST.get();
    }

    /** 魔法增幅：每项上限（魔力/恢复/流派 1000 级；吟唱/冷却缩减 100 级） */
    public static int getMagicMaxPoints(String skillId) {
        return switch (skillId) {
            case IRON_CAST_TIME, IRON_COOLDOWN -> 100;
            case MANA_AMP, ARS_MANA_REGEN, IRON_MANA_AMP, IRON_MANA_REGEN,
                    IRON_FIRE, IRON_ICE, IRON_LIGHTNING, IRON_HOLY, IRON_ENDER,
                    IRON_BLOOD, IRON_EVOCATION, IRON_NATURE, IRON_ELDRITCH -> 1000;
            default -> 0;
        };
    }

    /**
     * 魔法增幅：第 n 级消耗（线性增长，下一级在上一级基础上 +2）。
     * 第 1 级 = 2 点，第 2 级 = 4 点，第 3 级 = 6 点...
     * 吟唱/冷却缩减特殊：基础 5 点，线性 +5/级（5,10,15...）
     * ⚠️ 64 位返回（2026-08-12 统一）：所有技能消耗一律 long/double，防高等级 int 溢出。
     * @param currentLevel 当前已学等级（第 0 级 = 学第 1 级的消耗）
     */
    public static long getMagicCostAtLevel(String skillId, int currentLevel) {
        if (IRON_CAST_TIME.equals(skillId) || IRON_COOLDOWN.equals(skillId)) {
            return (long) (currentLevel + 1) * 5;
        }
        return (long) (currentLevel + 1) * 2;
    }

    /**
     * 终极节点等级上限：默认单次解锁（1）；多级终极节点（节点类）各自上限：
     * 村庄英雄 10 / 接触距离 50 / 发光 1 / 战利品爆炸 100 / 工具不毁 5 / 生物掉落 10 / 方块掉落 10 / 经验 10 / 刷怪蛋 10 / 头颅 5
     */
    public static int getUltimateMaxPoints(String skillId) {
        return switch (skillId) {
            case VILLAGE_HERO -> 10;
            case REACH -> 50;
            case ULT_SWEEP, ULT_KB_RESIST -> 10; // 横扫范围/击退抗性：上限 10 级
            case GLOW -> 1;
            case LOOT_BOMB -> 100;
            case UNBREAKABLE -> 5;
            case MOB_DROP, BLOCK_DROP, XP_GAIN -> 10;
            case MOB_SPAWN_EGG, MOB_HEAD -> 5;
            default -> 1;
        };
    }

    /**
     * 终极节点每级消耗（节点类）：阶梯递增——每级消耗在上一级基础上增加 10%（Config 可调）。
     * <pre>cost(第n级) = round(base × 1.1^n)</pre>
     * 基础值：村庄英雄/战利品爆炸 10、接触距离 1、发光 1、工具不毁 1、三掉落 50、刷怪蛋 20、头颅 10；普通单次终极走 ultimateCost。
     * @param currentLevel 当前已学等级（第 0 级 = 学第 1 级的消耗）
     */
    public static double getUltimateLevelCost(String skillId, int currentLevel) {
        // 横扫范围/击退抗性：线性消耗（每级 2 点，下一级 +2：2,4,6,8...）
        if (ULT_SWEEP.equals(skillId) || ULT_KB_RESIST.equals(skillId)) {
            return (double) (currentLevel + 1) * 2.0;
        }
        double base = switch (skillId) {
            case VILLAGE_HERO, LOOT_BOMB -> 10.0;
            case REACH, GLOW -> 1.0;
            case UNBREAKABLE -> 1.0;
            case MOB_DROP, BLOCK_DROP, XP_GAIN -> 50.0;
            case MOB_SPAWN_EGG -> 20.0;
            case MOB_HEAD -> 10.0;
            default -> ultimateCost(skillId); // 单次解锁终极：无阶梯
        };
        if (getUltimateMaxPoints(skillId) <= 1) {
            return base; // 单次解锁终极无阶梯
        }
        // 每级消耗在上一级基础上 +10%（1.1^当前等级）
        double mult = Math.pow(1 + org.zifeng.skilltree.Config.ULTIMATE_STEP_RATE.get(), currentLevel);
        return base * mult;
    }

    /** 节点类终极（多级，可调生效等级）判断 */
    public static boolean isMultiLevelUltimate(String skillId) {
        return getUltimateMaxPoints(skillId) > 1;
    }

    /**
     * 杀戮光环：下一级消耗 = 1000 × 1.05^当前等级（数值均走 Config）。
     * ⚠️ 64 位返回（2026-08-12 修复）：原 int 在约 295 级后 1.05^等级 超过 Integer.MAX_VALUE（21.4 亿）
     *    溢出成负数 → 扣点变加点（技能点越点越多）。现返回 long，double 计算 + clamp 到 Long.MAX_VALUE，
     *    技能点本身是 double（无 64 位上限问题）。
     */
    public static long getAuraCost(String skillId, int currentLevel) {
        double raw = auraBaseCost() * Math.pow(auraCostMultiplier(), currentLevel);
        if (raw >= Long.MAX_VALUE) {
            return Long.MAX_VALUE; // 极端高等级 clamp，防溢出
        }
        return Math.round(raw);
    }

    public static String getDisplayName(String skillId) {
        return switch (skillId) {
            case MANA_AMP -> "新生魔艺魔力增幅";
            case ARS_MANA_REGEN -> "新生魔艺魔力恢复";
            case IRON_MANA_AMP -> "铁魔法魔力增幅";
            case IRON_MANA_REGEN -> "铁魔法魔力恢复";
            case IRON_CAST_TIME -> "铁魔法吟唱缩减";
            case IRON_COOLDOWN -> "铁魔法法术冷却缩减";
            case IRON_FIRE -> "火焰法术强度";
            case IRON_ICE -> "冰霜法术强度";
            case IRON_LIGHTNING -> "雷电法术强度";
            case IRON_HOLY -> "神圣法术强度";
            case IRON_ENDER -> "末影法术强度";
            case IRON_BLOOD -> "鲜血法术强度";
            case IRON_EVOCATION -> "召唤法术强度";
            case IRON_NATURE -> "自然法术强度";
            case IRON_ELDRITCH -> "异界法术强度";
            case BODY_HP -> "生命强化";
            case BODY -> "体魄强化";
            case TOUGH -> "坚韧之躯";
            case BLADE -> "锋刃精通";
            case ATTACK_SPEED -> "疾攻术";
            case MINING -> "采掘熟稔";
            case MOVE -> "疾行步法";
            case REGEN -> "再生体魄";
            case LUCK -> "幸运眷顾";
            case JUMP -> "跃升体术";
            case FLY -> "御空术";
            case SWIM -> "潜游术";
            case CRIT -> "暴击精通";
            case LIFESTEAL -> "生命汲取";
            case THORNS -> "荆棘反伤";
            case ARMOR_PEN -> "破甲精通";
            case VILLAGE_HERO -> "村庄英雄";
            case REACH -> "接触距离";
            case GLOW -> "发光";
            case LOOT_BOMB -> "战利品爆炸";
            case UNBREAKABLE -> "工具不毁";
            case MOB_DROP -> "生物掉落倍率";
            case BLOCK_DROP -> "方块掉落倍率";
            case XP_GAIN -> "经验获取倍率";
            case MOB_SPAWN_EGG -> "刷怪蛋掉落";
            case MOB_HEAD -> "头颅掉落";
            case AURA_EMPOWER -> "杀戮光环·强化";
            case MACHINE_STAR -> "机械之星";
            case MACHINE_LOOT_BOMB -> "战利品爆炸·共鸣";
            case MACHINE_UNBREAKABLE -> "工具不毁·共鸣";
            case MACHINE_MOB_DROP -> "生物掉落·共鸣";
            case MACHINE_BLOCK_DROP -> "方块掉落·共鸣";
            case MACHINE_XP_GAIN -> "经验获取·共鸣";
            case MACHINE_SPAWN_EGG -> "刷怪蛋掉落·共鸣";
            case MACHINE_MOB_HEAD -> "头颅掉落·共鸣";
            case MACHINE_AUTO_SMELT -> "自动熔炼·共鸣";
            case AMP_HP -> "生命增幅";
            case AMP_DAMAGE -> "锋刃增幅";
            case AMP_ATTACK_SPEED -> "疾攻增幅";
            case AMP_MINING -> "采掘增幅";
            case AMP_REGEN -> "再生增幅";
            case AMP_ARMOR -> "防御强化";
            case AMP_MOVE -> "疾行增幅";
            case AMP_JUMP -> "跃升增幅";
            case AMP_FLY -> "御空增幅";
            case AMP_SWIM -> "潜游增幅";
            case AMP_TOUGH -> "坚韧增幅";
            case AMP_LUCK -> "幸运增幅";
            case AMP_CRIT -> "暴击增幅";
            case AMP_LIFESTEAL -> "吸血增幅";
            case AMP_THORNS -> "荆棘增幅";
            case AMP_ARMOR_PEN -> "破甲增幅";
            case ULT_BLOOD -> "浴血奋战";
            case ULT_GOLDEN -> "不坏金身";
            case ULT_MASTER -> "全能精通";
            case ULT_FAVOR -> "宇宙的青睐";
            case NIGHT_VISION -> "星瞳·夜视";
            case SATURATION -> "星食·饱腹";
            case ULT_REVIVE -> "凤凰涅槃";
            case ULT_REAPER -> "死神凝视";
            case ULT_VOID_BODY -> "虚空之躯";
            case AUTO_SMELT -> "自动熔炼";
            case ULT_BREAK_ALL -> "万物挖掘";
            case ULT_UNBREAK_TAG -> "不毁词条";
            case ULT_SWEEP -> "横扫范围";
            case ULT_KB_RESIST -> "击退抗性";
            case AURA_DAMAGE -> "杀戮光环·伤害";
            case AURA_SPEED -> "杀戮光环·速度";
            case AURA_HEAL -> "治愈光环";
            case AURA_MAGNET -> "磁力光环";
            case AURA_TIME -> "时之环·时间停止";
            case AURA_WEATHER -> "晴空环·永恒晴天";
            case AURA_LOCK -> "光环锁定";
            case AURA_VOID -> "杀戮光环·虚空之矛";
            default -> "未知技能";
        };
    }

    public static String getDescription(String skillId) {
        return switch (skillId) {
            case MANA_AMP -> "新生魔艺魔力增幅：\n增幅新生魔艺（Ars Nouveau）最大魔力\n每级 +10%，上限 1000 级\n（1000 级 = 最大魔力 ×101，受模组属性上限保护）\n每级消耗 2 技能点（线性 +2/级）\n⚠ 需安装新生魔艺，未安装时学习无效";
            case ARS_MANA_REGEN -> "新生魔艺魔力恢复：\n增幅新生魔艺（Ars Nouveau）魔力恢复速度\n每级 +40%，上限 1000 级\n（1000 级 = 恢复 ×401，受模组属性上限保护）\n每级消耗 2 技能点（线性 +2/级）\n⚠ 需安装新生魔艺，未安装时学习无效";
            case IRON_MANA_AMP -> "铁魔法魔力增幅：\n增幅铁魔法（Iron's Spells）最大魔力\n每级 +10%，上限 1000 级\n（1000 级 = 最大魔力 ×101，受模组属性上限保护）\n每级消耗 2 技能点（线性 +2/级）\n⚠ 需安装铁魔法，未安装时学习无效";
            case IRON_MANA_REGEN -> "铁魔法魔力恢复：\n增幅铁魔法（Iron's Spells）魔力恢复速度\n每级 +40%，上限 1000 级\n（1000 级 = 恢复 ×401，受模组属性上限保护）\n每级消耗 2 技能点（线性 +2/级）\n⚠ 需安装铁魔法，未安装时学习无效";
            case IRON_CAST_TIME -> "铁魔法吟唱缩减：\n减少铁魔法（Iron's Spells）法术吟唱时间\n每级 -10%，上限 100 级\n（100 级 = 吟唱时间大幅缩短）\n每级消耗 5 技能点（线性 +5/级）\n⚠ 需安装铁魔法，未安装时学习无效";
            case IRON_COOLDOWN -> "铁魔法法术冷却缩减：\n减少铁魔法（Iron's Spells）法术冷却时间\n每级 -10%，上限 100 级\n（100 级 = 冷却时间大幅缩短）\n每级消耗 5 技能点（线性 +5/级）\n⚠ 需安装铁魔法，未安装时学习无效";
            case IRON_FIRE -> "火焰法术强度：\n增幅铁魔法火焰（Fire）流派法术伤害\n每级 +10%，上限 1000 级\n（1000 级 = 火焰法术强度 ×101）\n每级消耗 2 技能点（线性 +2/级）\n⚠ 需安装铁魔法，未安装时学习无效";
            case IRON_ICE -> "冰霜法术强度：\n增幅铁魔法冰霜（Ice）流派法术伤害\n每级 +10%，上限 1000 级\n（1000 级 = 冰霜法术强度 ×101）\n每级消耗 2 技能点（线性 +2/级）\n⚠ 需安装铁魔法，未安装时学习无效";
            case IRON_LIGHTNING -> "雷电法术强度：\n增幅铁魔法雷电（Lightning）流派法术伤害\n每级 +10%，上限 1000 级\n（1000 级 = 雷电法术强度 ×101）\n每级消耗 2 技能点（线性 +2/级）\n⚠ 需安装铁魔法，未安装时学习无效";
            case IRON_HOLY -> "神圣法术强度：\n增幅铁魔法神圣（Holy）流派法术伤害\n每级 +10%，上限 1000 级\n（1000 级 = 神圣法术强度 ×101）\n每级消耗 2 技能点（线性 +2/级）\n⚠ 需安装铁魔法，未安装时学习无效";
            case IRON_ENDER -> "末影法术强度：\n增幅铁魔法末影（Ender）流派法术伤害\n每级 +10%，上限 1000 级\n（1000 级 = 末影法术强度 ×101）\n每级消耗 2 技能点（线性 +2/级）\n⚠ 需安装铁魔法，未安装时学习无效";
            case IRON_BLOOD -> "鲜血法术强度：\n增幅铁魔法鲜血（Blood）流派法术伤害\n每级 +10%，上限 1000 级\n（1000 级 = 鲜血法术强度 ×101）\n每级消耗 2 技能点（线性 +2/级）\n⚠ 需安装铁魔法，未安装时学习无效";
            case IRON_EVOCATION -> "召唤法术强度：\n增幅铁魔法召唤（Evocation）流派法术伤害\n每级 +10%，上限 1000 级\n（1000 级 = 召唤法术强度 ×101）\n每级消耗 2 技能点（线性 +2/级）\n⚠ 需安装铁魔法，未安装时学习无效";
            case IRON_NATURE -> "自然法术强度：\n增幅铁魔法自然（Nature）流派法术伤害\n每级 +10%，上限 1000 级\n（1000 级 = 自然法术强度 ×101）\n每级消耗 2 技能点（线性 +2/级）\n⚠ 需安装铁魔法，未安装时学习无效";
            case IRON_ELDRITCH -> "异界法术强度：\n增幅铁魔法异界（Eldritch）流派法术伤害\n每级 +10%，上限 1000 级\n（1000 级 = 异界法术强度 ×101）\n每级消耗 2 技能点（线性 +2/级）\n⚠ 需安装铁魔法，未安装时学习无效";
            case BODY_HP -> "生命强化：每点 +2 最大生命\n（拆分自原体魄强化，纯生命成长）";
            case BODY -> "体魄强化：每点 +0.2 护甲、\n+0.05% 物理减伤（超原版 80% 护甲上限后继续成长）";
            case TOUGH -> "坚韧之躯：每点 +0.3 护甲韧性、\n+0.1% 击退抗性";
            case BLADE -> "锋刃精通：每点 +0.4 近战攻击伤害";
            case ATTACK_SPEED -> "疾攻术：每点 +0.02 攻击速度";
            case MINING -> "采掘熟稔：每点 +0.3 挖掘速度";
            case MOVE -> "疾行步法：每点 +0.005 移动速度";
            case REGEN -> "再生体魄：每点 +0.1/秒 生命恢复";
            case LUCK -> "幸运眷顾：每点 +0.1 幸运值";
            case JUMP -> "跃升体术：每点 +0.01 跳跃高度";
            case FLY -> "御空术：每点 +0.005 飞行速度";
            case SWIM -> "潜游术：每点 +0.005 游泳速度";
            case CRIT -> "暴击精通：每点 +0.1% 暴击几率\n（上限 100%，暴击造成 1.5 倍伤害）";
            case LIFESTEAL -> "生命汲取：每点 +0.1% 吸血\n（按造成的伤害回复生命）";
            case THORNS -> "荆棘之体：每点 +0.05 反伤\n（受击时反弹伤害给攻击者）";
            case ARMOR_PEN -> "破甲精通：每点 +0.15% 最终伤害\n（无视目标护甲）";
            case VILLAGE_HERO -> "村庄英雄：每级 +4 级村庄英雄效果\n（10级满=村庄英雄40级，交易折扣极大）\n每级消耗 10 技能点";
            case REACH -> "接触距离：每级 +1 格触摸距离\n和攻击距离（上限50级）\n每级消耗 1 技能点";
            case GLOW -> "发光：给 35 格半径内所有生物\n施加发光效果（除玩家自身）\n一次性解锁，消耗 1 技能点";
            case LOOT_BOMB -> "战利品爆炸：击杀所有生物含Boss\n100%触发战利品爆炸，掉落翻倍\n1级1倍，100级=100倍（线性增长）\n每级消耗阶梯递增（10%涨幅）";
            case UNBREAKABLE -> "工具不毁：每级 +20% 工具耐久损耗减免\n（5级封顶=100%，工具不再消耗耐久）\n每级消耗 1 技能点（阶梯递增）";
            case MOB_DROP -> "生物掉落倍率：每级 +1 倍生物掉落\n（1级=2倍，10级=11倍）\n仅对可受抢夺影响的生物生效\n每级消耗 50 技能点（阶梯递增）";
            case BLOCK_DROP -> "方块掉落倍率：每级 +1 倍方块掉落\n（1级=2倍，10级=11倍）\n仅对可受时运影响的方块生效\n每级消耗 50 技能点（阶梯递增）";
            case XP_GAIN -> "经验获取倍率：每级 +2 倍经验获取\n（1级=3倍，10级=21倍）\n杀怪/挖矿/烧炼经验都生效\n每级消耗 50 技能点（阶梯递增）";
            case MOB_SPAWN_EGG -> "刷怪蛋掉落：击杀生物时每级 10% 概率\n掉落对应刷怪蛋（满5级=50%）\n对所有生物生效，固定掉 1 个\n不受任何技能增幅（不吃战利品爆炸/生物掉落倍率）\n每级消耗 20 技能点（阶梯递增）";
            case MOB_HEAD -> "头颅掉落：击杀生物时每级 10% 概率\n掉落对应头颅（满5级=50%）\n僵尸/骷髅/凋灵骷髅/苦力怕/猪灵\n掉对应头颅\n击杀玩家掉对方皮肤的头颅\n无对应头颅的生物不掉\n不受任何技能增幅（不吃战利品爆炸/生物掉落倍率）\n每级消耗 10 技能点（阶梯递增）";
            case AURA_EMPOWER -> "杀戮光环·强化：消耗 1000 技能点\n杀戮光环获得强化伤害：\n混沌伤害（无视护甲）/Boss混沌连击/\n破盾/守卫水晶特判\n前置：杀戮光环·伤害 50 级";
            case AMP_HP -> "生命增幅：每点 +10% 最大生命倍率";
            case AMP_DAMAGE -> "锋刃增幅：每点 +10% 近战伤害倍率";
            case AMP_ATTACK_SPEED -> "疾攻增幅：每点 +8% 攻击速度倍率";
            case AMP_MINING -> "采掘增幅：每点 +12% 挖掘速度倍率";
            case AMP_REGEN -> "再生增幅：每点 +16% 生命恢复倍率";
            case AMP_ARMOR -> "防御强化：每点 +0.5% 物理减伤\n（独立减伤层，护甲 80% 封顶后继续防护）";
            case AMP_MOVE -> "疾行增幅：每点 +10% 移动速度倍率";
            case AMP_JUMP -> "跃升增幅：每点 +10% 跳跃高度倍率";
            case AMP_FLY -> "御空增幅：每点 +10% 飞行速度倍率";
            case AMP_SWIM -> "潜游增幅：每点 +10% 游泳速度倍率";
            case AMP_TOUGH -> "坚韧增幅：每点 +10% 护甲韧性与击退抗性倍率";
            case AMP_LUCK -> "幸运增幅：每点 +10% 幸运值倍率";
            case AMP_CRIT -> "暴击增幅：每点 +5% 暴击伤害倍率\n（在暴击 1.5 倍基础上叠加）";
            case AMP_LIFESTEAL -> "吸血增幅：每点 +8% 吸血量倍率";
            case AMP_THORNS -> "荆棘增幅：每点 +8% 反伤倍率";
            case AMP_ARMOR_PEN -> "破甲增幅：每点 +8% 破甲增伤倍率";
            case ULT_BLOOD -> "浴血奋战：消耗 500 技能点，\n常驻攻击力 +50%、最大生命 +50%\n（燃血强化）\n前置：体魄500 + 锋刃500";
            case ULT_GOLDEN -> "不坏金身：消耗 500 技能点，\n常驻抗性提升10级、伤害吸收100级、\n抗火5级\n前置：坚韧之躯500 + 防御强化500";
            case ULT_MASTER -> "全能精通：消耗 5000 技能点，\n全方位防御（全伤害减免/护盾/免死/\n负面免疫），保证玩家不死\n前置：需解锁浴血奋战/不坏金身/凤凰涅槃/死神凝视";
            case ULT_FAVOR -> "宇宙的青睐：消耗 1000 技能点\n一次性点亮，解锁真正的创造飞行";
            case NIGHT_VISION -> "星瞳·夜视：消耗 100 技能点\n一次性点亮，永久夜视（不闪烁）";
            case SATURATION -> "星食·饱腹：消耗 100 技能点\n一次性点亮，饱食度永远满值";
            case ULT_REVIVE -> "凤凰涅槃：消耗 500 技能点，\n死亡原地复活一次，回复50%生命\n并清除负面效果，冷却1分钟\n前置：生命汲取500 + 暴击精通500";
            case ULT_REAPER -> "死神凝视：消耗 1000 技能点，\n攻击生命<15%的非玩家生物时\n30%概率直接处决\n前置：破甲精通500 + 锋刃精通500";
            case ULT_VOID_BODY -> "虚空之躯：消耗 5000 技能点，\n三层无敌：免伤/免死/血量只增不减\n抗击退/免摔落/免火焰/清负面\n前置：全能精通";
            case AUTO_SMELT -> "自动熔炼：消耗 30 技能点\n挖掘方块时自动熔炼掉落物\n（铁矿石→铁锭、金矿石→金锭等）\n按熔炉配方判断能否熔炼\n判断顺序：先熔炉、再时运、再技能增幅\n一次性点亮，1 级\n\n⚠ 黑名单：\n手持对应矿石的粗矿\n（如挖铁矿石得到的生铁 raw_iron）\n输入 /hmd 加入黑名单\n黑名单中的物品不参与熔炼判定\n输入 /delhmd 可查看并移除";
            case ULT_BREAK_ALL -> "万物挖掘：消耗 100 技能点\n一次性点亮，激活后可以挖掘任何方块\n包括基岩这类无法破坏的方块\n挖掘后会掉落对应方块\n（左键点击即可挖掘，无需等待）";
            case ULT_UNBREAK_TAG -> "不毁词条：消耗 100 技能点\n一次性点亮，激活后\n在铁砧中放入两个相同的物品\n可以合成出带有【无法破坏】词条的工具\n（工具不再消耗耐久）";
            case ULT_SWEEP -> "横扫范围：每级 +1 格攻击范围\n（近战攻击命中时，目标周围\nN 格内其他敌人同受伤害）\n仿龙之研究武器攻击范围升级\n10级 = 横扫半径 10 格\n每级消耗 2 技能点（线性 +2/级）";
            case ULT_KB_RESIST -> "击退抗性：每级 +10% 击退抗性\n10级 = 100%，玩家不会被击退\n每级消耗 2 技能点（线性 +2/级）";
            case MACHINE_STAR -> "机械之星：消耗 " + Config.MACHINE_STAR_COST.get() + " 技能点\n一次性点亮，机械共鸣的核心\n学习后才能学习其他共鸣技能\n（共鸣技能让模拟玩家机器\n如数字采矿机继承对应效果）";
            case MACHINE_LOOT_BOMB -> "战利品爆炸·共鸣：消耗 " + Config.MACHINE_RESONANCE_COST.get() + " 技能点\n一次性点亮，学习并开启后\n模拟玩家机器（假玩家）击杀生物\n才能继承战利品爆炸效果\n关闭/重置后立即失效\n前置：机械之星 + 战利品爆炸";
            case MACHINE_UNBREAKABLE -> "工具不毁·共鸣：消耗 " + Config.MACHINE_RESONANCE_COST.get() + " 技能点\n一次性点亮，学习并开启后\n模拟玩家机器使用工具时\n才能继承工具不毁（耐久减免）效果\n关闭/重置后立即失效\n前置：机械之星 + 工具不毁";
            case MACHINE_MOB_DROP -> "生物掉落·共鸣：消耗 " + Config.MACHINE_RESONANCE_COST.get() + " 技能点\n一次性点亮，学习并开启后\n模拟玩家机器击杀生物\n才能继承生物掉落倍率效果\n关闭/重置后立即失效\n前置：机械之星 + 生物掉落倍率";
            case MACHINE_BLOCK_DROP -> "方块掉落·共鸣：消耗 " + Config.MACHINE_RESONANCE_COST.get() + " 技能点\n一次性点亮，学习并开启后\n模拟玩家机器挖掘方块\n才能继承方块掉落倍率效果\n关闭/重置后立即失效\n前置：机械之星 + 方块掉落倍率";
            case MACHINE_XP_GAIN -> "经验获取·共鸣：消耗 " + Config.MACHINE_RESONANCE_COST.get() + " 技能点\n一次性点亮，学习并开启后\n模拟玩家机器击杀/挖掘产生的经验\n才能继承经验获取倍率效果\n关闭/重置后立即失效\n前置：机械之星 + 经验获取倍率";
            case MACHINE_SPAWN_EGG -> "刷怪蛋掉落·共鸣：消耗 " + Config.MACHINE_RESONANCE_COST.get() + " 技能点\n一次性点亮，学习并开启后\n模拟玩家机器击杀生物\n才能继承刷怪蛋掉落效果\n关闭/重置后立即失效\n前置：机械之星 + 刷怪蛋掉落";
            case MACHINE_MOB_HEAD -> "头颅掉落·共鸣：消耗 " + Config.MACHINE_RESONANCE_COST.get() + " 技能点\n一次性点亮，学习并开启后\n模拟玩家机器击杀生物\n才能继承头颅掉落效果\n关闭/重置后立即失效\n前置：机械之星 + 头颅掉落";
            case MACHINE_AUTO_SMELT -> "自动熔炼·共鸣：消耗 " + Config.MACHINE_RESONANCE_COST.get() + " 技能点\n一次性点亮，学习并开启后\n模拟玩家机器挖掘方块\n才能继承自动熔炼效果\n关闭/重置后立即失效\n前置：机械之星 + 自动熔炼";
            case AURA_DAMAGE -> "杀戮光环·伤害：每级+10%伤害倍率，\n360°范围伤害，默认10秒攻击一次\n上限1000级\n前置：锋刃精通100 + 疾攻术100";
            case AURA_SPEED -> "杀戮光环·速度：提高光环攻击频率\n（每级攻击间隔×0.9，20级=每秒约1.2次）\n上限20级\n前置：锋刃精通100 + 疾攻术100";
            case AURA_HEAL -> "治愈光环：每级给周围10格内\n友方单位对应等级的生命回复效果\n（50级 = 生命回复50级）\n上限50级，消耗随等级递增";
            case AURA_MAGNET -> "磁力光环：一次性解锁（消耗 " + String.format("%.0f", org.zifeng.skilltree.Config.MAGNET_COST.get()) + " 技能点）\n按 H 键开关，自动吸取经验与掉落物\n（潜行时暂停）";
            case AURA_TIME -> "时之环·时间停止：消耗 " + Skills.minorUltCost() + " 技能点\n一次性点亮，开启后锁定世界时间\n为开启瞬间的时间（不再强制正午）\n不被睡觉/时间命令影响\n关闭后立即恢复正常时间流动";
            case AURA_WEATHER -> "晴空环·永恒晴天：消耗 " + Skills.minorUltCost() + " 技能点\n一次性点亮，开启后锁定晴天\n不被下雨/天气命令影响\n关闭后立即恢复正常天气";
            case AURA_LOCK -> "光环锁定：消耗 1000 技能点\n一次性点亮，开启后免疫 TP 与击退\n（传送/瞬移/击退均无效）\n只有自己移动/飞行才能真正移动";
            case AURA_VOID -> "杀戮光环·虚空之矛：消耗 5000 技能点\n一次性点亮，杀戮光环获得虚空之矛力量\n（绝对秒杀+范围扩大至50格，K键控制）\n（磁铁范围扩至55格，H键控制）\n前置：杀戮光环·伤害 100 级";
            default -> "";
        };
    }

    /**
     * 通用前置系统：技能 → [(前置技能, 所需等级), ...]
     * 覆盖终极节点、杀戮光环（锋刃/疾攻前置、强化前置、虚空之矛前置）。
     */
    public static List<Map.Entry<String, Integer>> getPrerequisites(String skillId) {
        return switch (skillId) {
            case ULT_BLOOD -> List.of(Map.entry(BODY, 500), Map.entry(BLADE, 500));
            case ULT_GOLDEN -> List.of(Map.entry(TOUGH, 500), Map.entry(AMP_ARMOR, 500));
            case ULT_MASTER -> List.of(Map.entry(ULT_BLOOD, 1), Map.entry(ULT_GOLDEN, 1), Map.entry(ULT_REVIVE, 1), Map.entry(ULT_REAPER, 1));
            case ULT_REVIVE -> List.of(Map.entry(LIFESTEAL, 500), Map.entry(CRIT, 500));
            case ULT_REAPER -> List.of(Map.entry(ARMOR_PEN, 500), Map.entry(BLADE, 500));
            case ULT_VOID_BODY -> List.of(Map.entry(ULT_MASTER, 1)); // 前置：全能精通
            case AURA_DAMAGE, AURA_SPEED -> List.of(Map.entry(BLADE, 100), Map.entry(ATTACK_SPEED, 100)); // 锋刃/疾攻 100
            case AURA_EMPOWER -> List.of(Map.entry(AURA_DAMAGE, 50)); // 杀戮伤害 50
            case AURA_VOID -> List.of(Map.entry(AURA_DAMAGE, 100)); // 杀戮伤害 100
            // 机械共鸣：机械之星无前置；共鸣技能需 机械之星 + 对应原技能已学
            case MACHINE_LOOT_BOMB -> List.of(Map.entry(MACHINE_STAR, 1), Map.entry(LOOT_BOMB, 1));
            case MACHINE_UNBREAKABLE -> List.of(Map.entry(MACHINE_STAR, 1), Map.entry(UNBREAKABLE, 1));
            case MACHINE_MOB_DROP -> List.of(Map.entry(MACHINE_STAR, 1), Map.entry(MOB_DROP, 1));
            case MACHINE_BLOCK_DROP -> List.of(Map.entry(MACHINE_STAR, 1), Map.entry(BLOCK_DROP, 1));
            case MACHINE_XP_GAIN -> List.of(Map.entry(MACHINE_STAR, 1), Map.entry(XP_GAIN, 1));
            case MACHINE_SPAWN_EGG -> List.of(Map.entry(MACHINE_STAR, 1), Map.entry(MOB_SPAWN_EGG, 1));
            case MACHINE_MOB_HEAD -> List.of(Map.entry(MACHINE_STAR, 1), Map.entry(MOB_HEAD, 1));
            case MACHINE_AUTO_SMELT -> List.of(Map.entry(MACHINE_STAR, 1), Map.entry(AUTO_SMELT, 1));
            default -> List.of(); // 机械之星/宇宙的青睐/夜视/饱食/村庄英雄/接触距离/发光/战利品爆炸/工具不毁/掉落/经验无前置
        };
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, path);
    }

    /**
     * 技能图标：使用原版物品图标（辨识度高、零资源成本）。
     * 设计原则：图标物品与技能主题强关联（剑=伤害、镐=挖掘、靴=移速等）。
     */
    public static Item getIcon(String skillId) {
        return switch (skillId) {
            // ===== 魔法增幅（纵列0，其余模组兼容） =====
            case MANA_AMP -> Items.LAPIS_LAZULI;              // 新生魔艺魔力增幅：青金石（魔法墨水/魔力）
            case ARS_MANA_REGEN -> Items.GLOW_BERRIES;        // 新生魔艺魔力恢复：发光浆果（恢复能量）
            case IRON_MANA_AMP -> Items.IRON_INGOT;           // 铁魔法魔力增幅：铁锭（铁魔法主题）
            case IRON_MANA_REGEN -> Items.GHAST_TEAR;         // 铁魔法魔力恢复：恶魂之泪（魔法恢复）
            case IRON_CAST_TIME -> Items.BLAZE_POWDER;        // 铁魔法吟唱缩减：烈焰粉（快速施法）
            case IRON_COOLDOWN -> Items.FEATHER;              // 铁魔法法术冷却缩减：羽毛（轻盈/更快再施法）
            case IRON_FIRE -> Items.FLINT_AND_STEEL;          // 火焰法术强度：打火石（火焰）
            case IRON_ICE -> Items.PACKED_ICE;                // 冰霜法术强度：浮冰（寒冰）
            case IRON_LIGHTNING -> Items.CONDUIT;             // 雷电法术强度：潮涌核心（电能）
            case IRON_HOLY -> Items.GOLD_BLOCK;               // 神圣法术强度：金块（神圣）
            case IRON_ENDER -> Items.ENDER_EYE;               // 末影法术强度：末影之眼
            case IRON_BLOOD -> Items.NETHER_WART;             // 鲜血法术强度：下界疣（血药）
            case IRON_EVOCATION -> Items.BONE;                // 召唤法术强度：骨头（召唤骷髅）
            case IRON_NATURE -> Items.OAK_SAPLING;            // 自然法术强度：橡树苗（自然）
            case IRON_ELDRITCH -> Items.SHULKER_SHELL;        // 异界法术强度：潜影贝壳（异界）
            // ===== 基础属性（纵列1） =====
            case BODY_HP -> Items.APPLE;                       // 生命强化：苹果（生命）
            case BODY -> Items.IRON_CHESTPLATE;                // 体魄：铁胸甲（护甲）
            case TOUGH -> Items.SHIELD;                        // 坚韧：盾牌（抗击退）
            case BLADE -> Items.IRON_SWORD;                    // 锋刃：铁剑
            case ATTACK_SPEED -> Items.SUGAR;                  // 疾攻：糖（快速）
            case MINING -> Items.IRON_PICKAXE;                 // 采掘：铁镐
            case MOVE -> Items.LEATHER_BOOTS;                  // 疾行：皮靴
            case REGEN -> Items.POTION;                        // 再生：药水（回血）
            case LUCK -> Items.EMERALD;                        // 幸运：绿宝石
            case JUMP -> Items.RABBIT_FOOT;                    // 跃升：兔子脚
            case FLY -> Items.ELYTRA;                          // 御空：鞘翅
            case SWIM -> Items.COD;                            // 潜游：鳕鱼
            case CRIT -> Items.FLINT;                          // 暴击：燧石（尖锐）
            case LIFESTEAL -> Items.ROTTEN_FLESH;               // 吸血：腐肉（血腥，比下界疣直观）
            case THORNS -> Items.CACTUS;                       // 荆棘：仙人掌
            case ARMOR_PEN -> Items.TRIDENT;                   // 破甲：三叉戟（穿透）
            case VILLAGE_HERO -> Items.WHITE_BANNER;            // 村庄英雄：旗帜（英雄荣誉）
            case REACH -> Items.ENDER_PEARL;                   // 接触距离：末影珍珠（远距离）
            case GLOW -> Items.GLOWSTONE_DUST;                 // 发光：萤石粉（发光）
            case LOOT_BOMB -> Items.CREEPER_HEAD;              // 战利品爆炸：苦力怕头（爆炸）
            // ===== 特殊增幅（纵列2，与基础一一对应，用进阶材质） =====
            case AMP_HP -> Items.GOLDEN_APPLE;                 // 生命增幅：金苹果（苹果进阶）
            case AMP_ARMOR -> Items.NETHERITE_CHESTPLATE;      // 防御强化：下界合金胸甲（铁甲进阶，避免与虚空之躯钻石甲重复）
            case AMP_TOUGH -> Items.DIAMOND;                   // 坚韧增幅：钻石（坚硬）
            case AMP_DAMAGE -> Items.NETHERITE_SWORD;          // 锋刃增幅：下界合金剑（铁剑进阶，避免与虚空之矛钻石剑重复）
            case AMP_ATTACK_SPEED -> Items.GOLD_INGOT;         // 疾攻增幅：金锭
            case AMP_MINING -> Items.DIAMOND_PICKAXE;          // 采掘增幅：钻石镐（铁镐进阶）
            case AMP_MOVE -> Items.DIAMOND_BOOTS;              // 疾行增幅：钻石靴（皮靴进阶）
            case AMP_REGEN -> Items.GLISTERING_MELON_SLICE;    // 再生增幅：闪烁西瓜（药水材料）
            case AMP_LUCK -> Items.EMERALD_BLOCK;              // 幸运增幅：绿宝石块（绿宝石进阶）
            case AMP_JUMP -> Items.RABBIT;                     // 跃升增幅：兔肉（兔子脚进阶）
            case AMP_FLY -> Items.PHANTOM_MEMBRANE;            // 御空增幅：幻翼膜（鞘翅材料）
            case AMP_SWIM -> Items.PUFFERFISH;                 // 潜游增幅：河豚（鳕鱼进阶）
            case AMP_CRIT -> Items.QUARTZ;                     // 暴击增幅：下界石英（燧石进阶）
            case AMP_LIFESTEAL -> Items.CRIMSON_FUNGUS;        // 吸血增幅：绯红菌（下界主题）
            case AMP_THORNS -> Items.ROSE_BUSH;                // 荆棘增幅：玫瑰丛（带刺植物）
            case AMP_ARMOR_PEN -> Items.NETHERITE_INGOT;       // 破甲增幅：下界合金锭
            // ===== 终极节点（纵列3） =====
            case ULT_BLOOD -> Items.BLAZE_ROD;                 // 浴血奋战：烈焰棒
            case ULT_GOLDEN -> Items.BEACON;                   // 不坏金身：信标（常驻buff光环）
            case ULT_MASTER -> Items.NETHER_STAR;              // 全能精通：下界之星
            case ULT_FAVOR -> Items.DRAGON_EGG;                // 宇宙的青睐：龙蛋
            case NIGHT_VISION -> Items.GOLDEN_CARROT;          // 星瞳·夜视：金胡萝卜
            case SATURATION -> Items.CAKE;                     // 星食·饱腹：蛋糕
            case ULT_REVIVE -> Items.TOTEM_OF_UNDYING;         // 凤凰涅槃：不死图腾
            case ULT_REAPER -> Items.WITHER_SKELETON_SKULL;    // 死神凝视：凋灵骷髅头
            case ULT_VOID_BODY -> Items.DIAMOND_CHESTPLATE;    // 虚空之躯：原版钻石甲（金边=伤害吸收）
            case AUTO_SMELT -> Items.FURNACE;                  // 自动熔炼：熔炉（熔炼主题）
            case ULT_BREAK_ALL -> Items.BEDROCK;               // 万物挖掘：基岩（挖穿一切）
            case ULT_UNBREAK_TAG -> Items.DIAMOND_PICKAXE;     // 不毁词条：钻石镐（永不损坏）
            case ULT_SWEEP -> Items.IRON_SWORD;                // 横扫范围：铁剑（横扫攻击）
            case ULT_KB_RESIST -> Items.SHIELD;                // 击退抗性：盾牌（防御不动）
            case UNBREAKABLE -> Items.ANVIL;                   // 工具不毁：铁砧（永不损坏）
            case MOB_DROP -> Items.ROTTEN_FLESH;               // 生物掉落：腐肉（战利品）
            case BLOCK_DROP -> Items.DIAMOND_ORE;              // 方块掉落：钻石矿（矿物）
            case XP_GAIN -> Items.EXPERIENCE_BOTTLE;           // 经验获取：经验瓶
            case MOB_SPAWN_EGG -> Items.CREEPER_SPAWN_EGG;     // 刷怪蛋掉落：苦力怕刷怪蛋
            case MOB_HEAD -> Items.SKELETON_SKULL;             // 头颅掉落：骷髅头
            case AURA_EMPOWER -> Items.NETHERITE_SWORD;        // 光环·强化：下界合金剑（强化伤害）
            // ===== 机械共鸣（纵列5）：机械之星用活塞（机械核心）；共鸣技能用【前置原技能图标】，
            //       渲染时由 SkillTreeScreen 叠加机械钢灰边框 + 右下角螺丝角标（与原技能区分，辨识度高） =====
            case MACHINE_STAR -> Items.PISTON;                 // 机械之星：活塞（机械核心）
            case MACHINE_LOOT_BOMB -> Items.CREEPER_HEAD;      // 战利品爆炸·共鸣：前置=战利品爆炸（苦力怕头）
            case MACHINE_UNBREAKABLE -> Items.ANVIL;           // 工具不毁·共鸣：前置=工具不毁（铁砧）
            case MACHINE_MOB_DROP -> Items.ROTTEN_FLESH;       // 生物掉落·共鸣：前置=生物掉落（腐肉）
            case MACHINE_BLOCK_DROP -> Items.DIAMOND_ORE;      // 方块掉落·共鸣：前置=方块掉落（钻石矿）
            case MACHINE_XP_GAIN -> Items.EXPERIENCE_BOTTLE;   // 经验获取·共鸣：前置=经验获取（经验瓶）
            case MACHINE_SPAWN_EGG -> Items.CREEPER_SPAWN_EGG; // 刷怪蛋掉落·共鸣：前置=刷怪蛋（苦力怕蛋）
            case MACHINE_MOB_HEAD -> Items.SKELETON_SKULL;     // 头颅掉落·共鸣：前置=头颅掉落（骷髅头）
            case MACHINE_AUTO_SMELT -> Items.FURNACE;          // 自动熔炼·共鸣：前置=自动熔炼（熔炉）
            // ===== 杀戮光环（纵列4） =====
            case AURA_DAMAGE -> Items.TNT;                     // 光环·伤害：TNT（范围爆炸伤害）
            case AURA_SPEED -> Items.REDSTONE;                 // 光环·速度：红石粉（高频）
            case AURA_HEAL -> Items.HONEY_BOTTLE;              // 治愈光环：蜂蜜瓶
            case AURA_MAGNET -> Items.LODESTONE;               // 磁力光环：磁石（吸铁）
            case AURA_TIME -> Items.CLOCK;                     // 时之环：时钟（锁定时间）
            case AURA_WEATHER -> Items.SUNFLOWER;              // 晴空环：向日葵（面向太阳）
            case AURA_LOCK -> Items.ANVIL;                     // 光环锁定：铁砧（稳固不动）
            case AURA_VOID -> Items.DIAMOND_SWORD;            // 虚空之矛：原版钻石剑（虚空力量，金边=伤害吸收）
            default -> Items.BARRIER;
        };
    }
}
