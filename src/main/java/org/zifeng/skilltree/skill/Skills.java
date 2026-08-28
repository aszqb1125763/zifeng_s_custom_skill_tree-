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
            case ENCHANT_RANDOM -> 100L;  // 随机附魔：一次性 100 点
            case ENCHANT_BREAK -> 1000L;  // 附魔突破：一次性 1000 点
            case ENCHANT_OVER -> 10000L;  // 超限附魔：一次性 10000 点
            case UNLIMITED_TRADES -> 100L;   // 无限交易：一次性 100 点
            case VILLAGER_MASTER -> 1000L;   // 村民大师：一次性 1000 点
            case TREASURE_HUNTER -> 500L;    // 寻宝大师：一次性 500 点
            // 终极节点·生存辅助（2026-08-27）：100 点
            case FLY_NO_INERTIA, FLY_MINING, FIRE_PROTECT, WATER_BREATH, DARK_VISION, UNDERWATER_VISION -> 100L;
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
        /** 特殊被动（2026-08-25 新增：终极节点右边新列，原终极列中不加玩家属性的被动/掉落/行为类技能） */
        SPECIAL,
        /** 杀戮光环（独立系统，不受属性加成） */
        AURA,
        /** 寰宇法则（2026-08-27 新增：全局更改类技能，时之环/晴空环/无限回路——服务器全局生效） */
        GLOBAL,
        /** 机械共鸣（模拟玩家机器继承开关，独立列，前置=机械之星+对应原技能） */
        MACHINE,
        /** 子枫的馈赠（2026-08-25 新增：最右列，按游戏时长激活，免费获得技能点的新途径） */
        GIFT
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
    // ============ 特殊被动（纵列4，2026-08-25：从终极列拆出，不加玩家属性的被动/掉落/行为类） ============
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
    public static final String AUTO_SMELT = "auto_smelt";   // 自动熔炼（挖掘自动熔炼矿物，1级，消耗30）
    public static final String ULT_BREAK_ALL = "ult_break_all"; // 万物挖掘（可挖任何方块含基岩，1级，消耗100）
    public static final String ULT_UNBREAK_TAG = "ult_unbreak_tag"; // 不毁词条（铁砧合成Unbreakable工具，1级，消耗100）
    public static final String ULT_SWEEP = "ult_sweep";       // 横扫范围（每级+1格攻击范围，上限10，线性2）
    public static final String ULT_KB_RESIST = "ult_kb_resist"; // 击退抗性（每级+10%，满10级免疫击退，线性2）
    // 2026-08-26 新增：铁砧随机附魔系列（特殊被动）
    public static final String ENCHANT_RANDOM = "enchant_random";     // 随机附魔（100点，铁砧+4青金石+1级经验，随机正面附魔）
    public static final String ENCHANT_BREAK = "enchant_break";       // 附魔突破（1000点，铁砧+2青金石块+4级经验，已有附魔+1级，上限20）
    public static final String ENCHANT_OVER = "enchant_over";         // 超限附魔（10000点，铁砧+2下界之星+10级经验，已有附魔+1级，上限100）
    // 2026-08-27 新增：村民交易系列（特殊被动）
    public static final String UNLIMITED_TRADES = "unlimited_trades"; // 无限交易（100点，村民不用补货，交易次数不减少）
    public static final String VILLAGER_MASTER = "villager_master";   // 村民大师（1000点，交易后村民直接满级）
    public static final String TREASURE_HUNTER = "treasure_hunter";   // 寻宝大师（500点，64格内战利品容器/考古刷扫点发光）

    // ============ 终极节点·生存辅助（纵列3，2026-08-27 新增：飞行/火焰/呼吸/视野/AE兼容） ============
    public static final String FLY_NO_INERTIA = "fly_no_inertia";           // 御风止步：飞行无惯性，松空格即停（1级，100点）
    public static final String FLY_MINING = "fly_mining";                   // 凌空采掘：飞行中挖掘无视原版 5 倍惩罚（1级，100点）
    public static final String FIRE_PROTECT = "fire_protect";               // 烈焰不侵：不着火/无火焰视觉/免疫火焰伤害（1级，100点）
    public static final String WATER_BREATH = "water_breath";               // 鲛人之息：水下无限呼吸（1级，100点）
    public static final String DARK_VISION = "dark_vision";                 // 破暗之瞳：免疫黑暗效果视觉影响（1级，100点）
    public static final String UNDERWATER_VISION = "underwater_vision";     // 碧波清眸：水下/岩浆清晰视野（1级，100点）

    // ============ 子枫的馈赠（纵列7，2026-08-25 新增：按游戏时长/移动/飞行/挖掘激活，免费获得技能点） ============
    public static final String GIFT_TIME_BAPTISM = "gift_time_baptism"; // 时间洗礼：游戏时长≥1小时可激活，每10分钟+1技能点
    public static final String GIFT_TIME_STORM = "gift_time_storm";     // 时间风暴：游戏时长≥5小时可激活，每5分钟+1技能点（与洗礼叠加）
    public static final String GIFT_TIME_FLOOD = "gift_time_flood";     // 时间洪流：游戏时长≥10小时可激活，每1分钟+1技能点（与洗礼叠加）
    // 2026-08-25 新增：移动/飞行/挖掘洗礼（1-3级，消耗技能点升级，统计原版数据）+ 各增幅（上限20级，指数消耗）
    public static final String GIFT_MOVE_BAPTISM = "gift_move_baptism"; // 移动洗礼：统计行走+疾跑距离，1级1000米/2级500米/3级100米 得1技能点
    public static final String GIFT_MOVE_AMP = "gift_move_amp";         // 移动洗礼增幅：每级每次+1技能点获取，上限20级
    public static final String GIFT_FLY_BAPTISM = "gift_fly_baptism";   // 飞行洗礼：统计飞行距离，1级1000米/2级500米/3级100米 得1技能点
    public static final String GIFT_FLY_AMP = "gift_fly_amp";           // 飞行洗礼增幅：每级每次+1技能点获取，上限20级
    public static final String GIFT_MINE_BAPTISM = "gift_mine_baptism"; // 挖掘洗礼：统计挖掘方块数，1级1000块/2级500块/3级100块 得1技能点
    public static final String GIFT_MINE_AMP = "gift_mine_amp";         // 挖掘洗礼增幅：每级每次+1技能点获取，上限20级
    public static final String GIFT_KILL_BAPTISM = "gift_kill_baptism"; // 击杀馈赠：统计击杀生物数，1级1000杀/2级500杀/3级100杀 得1技能点
    public static final String GIFT_KILL_AMP = "gift_kill_amp";         // 击杀馈赠增幅：每级每次+1技能点获取，上限20级

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

    // ============ 杀戮光环（AURA，独立系统） ============
    public static final String AURA_DAMAGE = "aura_damage";   // 杀戮光环·伤害
    public static final String AURA_SPEED = "aura_speed";     // 杀戮光环·速度
    public static final String AURA_HEAL = "aura_heal";       // 治愈光环（群体治疗）
    public static final String AURA_MAGNET = "aura_magnet";   // 磁力光环（吸取经验/掉落物）
    public static final String AURA_LOCK = "aura_lock";       // 光环锁定（免疫TP/击退）
    public static final String AURA_EMPOWER = "aura_empower"; // 杀戮光环·强化（混沌/Boss伤害，拆自光环，虚空之矛上方）
    public static final String AURA_VOID = "aura_void";       // 杀戮光环·虚空之矛（虚空伤害/秒杀）
    public static final String AURA_LOOT_VACUUM = "aura_loot_vacuum"; // 凋落物挪移（木棍绑定容器，掉落直传容器不生成实体）

    // ============ 寰宇法则（GLOBAL，纵列6，2026-08-27 新增：全局更改类技能，服务器全局生效，光环右侧） ============
    public static final String AURA_TIME = "aura_time";       // 时之环·时间停止（锁定开启时的时间，全局 gamerule）
    public static final String AURA_WEATHER = "aura_weather"; // 晴空环·永恒晴天（锁定天气，全局 gamerule）
    public static final String AE_INFINITE_CHANNEL = "ae_infinite_channel"; // 无限回路：AE2 频道翻倍/无限（4级，全局 AE 配置）

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
    /** 所有终极节点（纵列3）：只保留加玩家属性/防御的终极（2026-08-25：不加属性的被动/掉落类已拆到 SPECIAL 纵列4） */
    public static final List<String> ULTIMATE_SKILLS = List.of(ULT_BLOOD, ULT_GOLDEN, ULT_MASTER, ULT_FAVOR, NIGHT_VISION, SATURATION, ULT_REVIVE, ULT_REAPER, ULT_VOID_BODY,
            FLY_NO_INERTIA, FLY_MINING, FIRE_PROTECT, WATER_BREATH, DARK_VISION, UNDERWATER_VISION);
    /** 所有特殊被动（纵列4，2026-08-25 新增）：原终极列中不加玩家属性的被动/掉落/行为类技能 */
    public static final List<String> SPECIAL_SKILLS = List.of(
            VILLAGE_HERO, REACH, GLOW, LOOT_BOMB, UNBREAKABLE, MOB_DROP, BLOCK_DROP, XP_GAIN,
            MOB_SPAWN_EGG, MOB_HEAD, AUTO_SMELT, ULT_BREAK_ALL, ULT_UNBREAK_TAG, ULT_SWEEP, ULT_KB_RESIST,
            ENCHANT_RANDOM, ENCHANT_BREAK, ENCHANT_OVER,
            UNLIMITED_TRADES, VILLAGER_MASTER, TREASURE_HUNTER);
    /** 所有杀戮光环（纵列5）：杀戮光环·强化 在 虚空之矛 上方；时之环/晴空环已移至寰宇法则列（2026-08-27） */
    public static final List<String> AURA_SKILLS = List.of(AURA_DAMAGE, AURA_SPEED, AURA_HEAL, AURA_MAGNET, AURA_LOCK, AURA_EMPOWER, AURA_VOID, AURA_LOOT_VACUUM);
    /** 所有寰宇法则（纵列6，2026-08-27 新增）：全局更改类技能（服务器全局生效，无法单人隔离） */
    public static final List<String> GLOBAL_SKILLS = List.of(AURA_TIME, AURA_WEATHER, AE_INFINITE_CHANNEL);
    /** 所有魔法增幅（纵列0）：其余模组兼容技能（新生魔艺/铁魔法等），不作为任何前置 */
    public static final List<String> MAGIC_SKILLS = List.of(
            // 新生魔艺
            MANA_AMP, ARS_MANA_REGEN,
            // 铁魔法
            IRON_MANA_AMP, IRON_MANA_REGEN, IRON_CAST_TIME, IRON_COOLDOWN,
            IRON_FIRE, IRON_ICE, IRON_LIGHTNING, IRON_HOLY, IRON_ENDER,
            IRON_BLOOD, IRON_EVOCATION, IRON_NATURE, IRON_ELDRITCH);
    /** 所有机械共鸣（纵列6）：机械之星在最上，其余共鸣技能在前置原技能下方 */
    public static final List<String> MACHINE_SKILLS = List.of(
            MACHINE_STAR,
            MACHINE_LOOT_BOMB, MACHINE_UNBREAKABLE, MACHINE_MOB_DROP, MACHINE_BLOCK_DROP,
            MACHINE_XP_GAIN, MACHINE_SPAWN_EGG, MACHINE_MOB_HEAD, MACHINE_AUTO_SMELT);
    /** 所有子枫的馈赠（纵列7，2026-08-25 新增）：时间/移动/飞行/挖掘/击杀洗礼 + 增幅 */
    public static final List<String> GIFT_SKILLS = List.of(
            GIFT_TIME_BAPTISM, GIFT_TIME_STORM, GIFT_TIME_FLOOD,
            GIFT_MOVE_BAPTISM, GIFT_MOVE_AMP,
            GIFT_FLY_BAPTISM, GIFT_FLY_AMP,
            GIFT_MINE_BAPTISM, GIFT_MINE_AMP,
            GIFT_KILL_BAPTISM, GIFT_KILL_AMP);

    public static final List<String> ALL_SKILLS = new ArrayList<>() {{
        addAll(MAGIC_SKILLS);
        addAll(BASE_SKILLS);
        addAll(AMPLIFY_SKILLS);
        addAll(ULTIMATE_SKILLS);
        addAll(SPECIAL_SKILLS);
        addAll(AURA_SKILLS);
        addAll(GLOBAL_SKILLS);
        addAll(MACHINE_SKILLS);
        addAll(GIFT_SKILLS);
    }};

    public static SkillType getType(String skillId) {
        if (MAGIC_SKILLS.contains(skillId)) return SkillType.MAGIC;
        if (MACHINE_SKILLS.contains(skillId)) return SkillType.MACHINE;
        if (GIFT_SKILLS.contains(skillId)) return SkillType.GIFT;
        if (GLOBAL_SKILLS.contains(skillId)) return SkillType.GLOBAL;
        if (BASE_SKILLS.contains(skillId)) return SkillType.BASE;
        if (AMPLIFY_SKILLS.contains(skillId)) return SkillType.AMPLIFY;
        if (ULTIMATE_SKILLS.contains(skillId)) return SkillType.ULTIMATE;
        if (SPECIAL_SKILLS.contains(skillId)) return SkillType.SPECIAL;
        if (AURA_SKILLS.contains(skillId)) return SkillType.AURA;
        return SkillType.BASE;
    }

    /**
     * 技能是否需要开关（2026-08-13 需求）：全部技能都可开关（含时之环/晴空环，
     * 用户要求保留开关快捷键——关闭即停止锁定时间/天气）。
     */
    public static boolean isTogglable(String skillId) {
        return true;
    }

    /** 杀戮光环：每项上限 */
    public static int getAuraMaxPoints(String skillId) {
        return switch (skillId) {
            case AURA_DAMAGE -> 1000;
            case AURA_SPEED -> 20;
            case AURA_HEAL -> 50;
            case AURA_MAGNET -> 1;
            case AURA_LOCK -> 1; // 一次性解锁（1000 技能点）
            case AURA_EMPOWER -> 1; // 一次性解锁（1000 技能点）
            case AURA_VOID -> 1; // 一次性解锁（5000 技能点）
            case AURA_LOOT_VACUUM -> 1; // 一次性解锁（10 技能点）
            default -> 0;
        };
    }

    /** 寰宇法则（全局更改类）：时之环/晴空环 1 级；无限回路 4 级（X2→X3→X4→无限，2026-08-27） */
    public static int getGlobalMaxPoints(String skillId) {
        return switch (skillId) {
            case AURA_TIME, AURA_WEATHER -> 1; // 一次性解锁（100 技能点）
            case AE_INFINITE_CHANNEL -> 4;     // 4 级：1=X2 2=X3 3=X4 4=无限
            default -> 1;
        };
    }

    /** 寰宇法则：第 n 级消耗（循序渐进）：时之环/晴空环 100；无限回路 200/500/1000/2000 */
    public static long getGlobalCost(String skillId, int currentLevel) {
        if (AE_INFINITE_CHANNEL.equals(skillId)) {
            return switch (currentLevel) {
                case 0 -> 200L;  // 1级 = X2（2倍频道）
                case 1 -> 500L;  // 2级 = X3（3倍频道）
                case 2 -> 1000L; // 3级 = X4（4倍频道）
                default -> 2000L; // 4级 = INFINITE（无限频道）
            };
        }
        return minorUltCost(); // 时之环/晴空环：100 点一次性
    }

    /** 各技能等级上限（按钮第2行显示用）：基础 1000 / 增幅 500 / 终极 1（多级终极各自上限） / 光环各自上限 / 魔法增幅各自上限 / 机械共鸣 1 */
    public static int getMaxPoints(String skillId) {
        return switch (getType(skillId)) {
            case BASE -> BASE_MAX_POINTS;
            case AMPLIFY -> AMPLIFY_MAX_POINTS;
            case ULTIMATE -> getUltimateMaxPoints(skillId);
            case SPECIAL -> getUltimateMaxPoints(skillId); // 特殊被动：复用终极的等级上限（多级节点类）
            case AURA -> getAuraMaxPoints(skillId);
            case GLOBAL -> getGlobalMaxPoints(skillId);
            case MAGIC -> getMagicMaxPoints(skillId);
            case MACHINE -> getMachineMaxPoints(skillId);
            case GIFT -> getGiftMaxPoints(skillId);
        };
    }

    /** 机械共鸣：全部单级解锁（上限 1） */
    public static int getMachineMaxPoints(String skillId) {
        return 1;
    }

    /** 子枫的馈赠：洗礼 1-5 级 / 增幅 100 级 / 时间系列单级（2026-08-25） */
    public static int getGiftMaxPoints(String skillId) {
        return switch (skillId) {
            case GIFT_MOVE_BAPTISM, GIFT_FLY_BAPTISM, GIFT_MINE_BAPTISM, GIFT_KILL_BAPTISM -> 5; // 移动/飞行/挖掘/击杀洗礼：5 级
            case GIFT_MOVE_AMP, GIFT_FLY_AMP, GIFT_MINE_AMP, GIFT_KILL_AMP -> 5;               // 增幅：上限 5 级（2026-08-27 从 100 下调）
            default -> 1; // 时间洗礼/风暴/洪流：单级
        };
    }

    /**
     * 子枫的馈赠激活/升级消耗（技能点）：
     * 时间系列 0（按游戏时长激活）；移动/飞行/挖掘/击杀洗礼 10/1000/10000/50000/100000；增幅 1000 × 1.3^等级（指数增长 30%）。
     * @param currentLevel 当前已学等级（0=学第1级）
     */
    public static long getGiftCost(String skillId, int currentLevel) {
        return switch (skillId) {
            case GIFT_MOVE_BAPTISM, GIFT_FLY_BAPTISM, GIFT_MINE_BAPTISM, GIFT_KILL_BAPTISM -> // 洗礼：10 / 1000 / 10000 / 50000 / 100000
                    switch (currentLevel) {
                        case 0 -> 10L;
                        case 1 -> 1000L;
                        case 2 -> 10000L;
                        case 3 -> 50000L;
                        default -> 100000L;
                    };
            case GIFT_MOVE_AMP, GIFT_FLY_AMP, GIFT_MINE_AMP, GIFT_KILL_AMP -> // 增幅：1000 × 1.3^等级（30% 指数）
                    Math.round(1000 * Math.pow(1.3, currentLevel));
            default -> 0L; // 时间系列：不消耗
        };
    }

    /** 子枫的馈赠激活门槛（游戏时长，tick）：时间洗礼 1 小时 / 时间风暴 5 小时 / 时间洪流 10 小时。原版 play_time 统计单位 = tick。 */
    public static long getGiftRequirementTicks(String skillId) {
        return switch (skillId) {
            case GIFT_TIME_BAPTISM -> 72000L;  // 1 小时 = 3600 秒 × 20
            case GIFT_TIME_STORM -> 360000L;   // 5 小时
            case GIFT_TIME_FLOOD -> 720000L;   // 10 小时
            default -> Long.MAX_VALUE;
        };
    }

    /**
     * 子枫的馈赠获得技能点间隔（tick）：时间洗礼 10 分钟 / 时间风暴 5 分钟 / 时间洪流 1 分钟。
     */
    public static long getGiftIntervalTicks(String skillId) {
        return switch (skillId) {
            case GIFT_TIME_BAPTISM -> 12000L; // 10 分钟 = 600 秒 × 20
            case GIFT_TIME_STORM -> 6000L;    // 5 分钟
            case GIFT_TIME_FLOOD -> 1200L;    // 1 分钟
            default -> Long.MAX_VALUE;
        };
    }

    /**
     * 移动/飞行/挖掘/击杀洗礼的每次触发需求（当前等级 1-5）：
     * 移动/飞行单位 = 米（原版统计是 cm，需 ×100 换算）；挖掘单位 = 方块数；击杀单位 = 个。
     */
    public static long getGiftDistanceRequirement(String skillId, int level) {
        return switch (level) {
            case 1 -> 1000L;  // 1000 米 / 1000 块 / 1000 杀
            case 2 -> 500L;   // 500
            case 3 -> 100L;   // 100
            case 4 -> 50L;    // 50（2026-08-25 新增 4 级）
            default -> 10L;   // 10（2026-08-25 新增 5 级）
        };
    }

    /** 该洗礼技能对应的增幅技能 ID（移动↔移动增幅，飞行↔飞行增幅，挖掘↔挖掘增幅，击杀↔击杀增幅） */
    public static String getGiftAmpSkill(String baptismSkillId) {
        return switch (baptismSkillId) {
            case GIFT_MOVE_BAPTISM -> GIFT_MOVE_AMP;
            case GIFT_FLY_BAPTISM -> GIFT_FLY_AMP;
            case GIFT_MINE_BAPTISM -> GIFT_MINE_AMP;
            case GIFT_KILL_BAPTISM -> GIFT_KILL_AMP;
            default -> null;
        };
    }

    /** 判断是否为子枫的馈赠技能 */
    public static boolean isGiftSkill(String skillId) {
        return GIFT_SKILLS.contains(skillId);
    }

    /** 判断是否为寰宇法则（全局更改）技能 */
    public static boolean isGlobalSkill(String skillId) {
        return GLOBAL_SKILLS.contains(skillId);
    }

    /** 判断是否为移动/飞行/挖掘/击杀洗礼（统计类，非时间类） */
    public static boolean isGiftDistanceBaptism(String skillId) {
        return GIFT_MOVE_BAPTISM.equals(skillId) || GIFT_FLY_BAPTISM.equals(skillId)
                || GIFT_MINE_BAPTISM.equals(skillId) || GIFT_KILL_BAPTISM.equals(skillId);
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
     * 子枫的馈赠激活消耗：不消耗技能点（按游戏时长条件激活），恒 0。
     */
    public static long getGiftCost(String skillId) {
        return 0L;
    }

    /**
     * 杀戮光环：下一级消耗 = 1000 × 1.05^当前等级（数值均走 Config）。
     * ⚠️ 64 位返回（2026-08-12 修复）：原 int 在约 295 级后 1.05^等级 超过 Integer.MAX_VALUE（21.4 亿）
     *    溢出成负数 → 扣点变加点（技能点越点越多）。现返回 long，double 计算 + clamp 到 Long.MAX_VALUE，
     *    技能点本身是 double（无 64 位上限问题）。
     */
    public static long getAuraCost(String skillId, int currentLevel) {
        if (AURA_LOOT_VACUUM.equals(skillId)) {
            return 10L; // 凋落物挪移：固定 10 技能点一次性解锁（2026-08-24）
        }
        double raw = auraBaseCost() * Math.pow(auraCostMultiplier(), currentLevel);
        if (raw >= Long.MAX_VALUE) {
            return Long.MAX_VALUE; // 极端高等级 clamp，防溢出
        }
        return Math.round(raw);
    }

    public static String getDisplayName(String skillId) {
        return switch (skillId) {
            // ===== 魔法增幅（纵列0，法术流派，轻度中二） =====
            case MANA_AMP -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case ARS_MANA_REGEN -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case IRON_MANA_AMP -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case IRON_MANA_REGEN -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case IRON_CAST_TIME -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case IRON_COOLDOWN -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case IRON_FIRE -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case IRON_ICE -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case IRON_LIGHTNING -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case IRON_HOLY -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case IRON_ENDER -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case IRON_BLOOD -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case IRON_EVOCATION -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case IRON_NATURE -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case IRON_ELDRITCH -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            // ===== 基础属性（纵列1，直白易懂 + 趣味） =====
            case BODY_HP -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case BODY -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case TOUGH -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case BLADE -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case ATTACK_SPEED -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case MINING -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case MOVE -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case REGEN -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case LUCK -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case JUMP -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case FLY -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case SWIM -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case CRIT -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case LIFESTEAL -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case THORNS -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case ARMOR_PEN -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            // ===== 特殊增幅（纵列2，真解系列，与基础对应） =====
            case AMP_HP -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case AMP_DAMAGE -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case AMP_ATTACK_SPEED -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case AMP_MINING -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case AMP_REGEN -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case AMP_ARMOR -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case AMP_MOVE -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case AMP_JUMP -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case AMP_FLY -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case AMP_SWIM -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case AMP_TOUGH -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case AMP_LUCK -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case AMP_CRIT -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case AMP_LIFESTEAL -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case AMP_THORNS -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case AMP_ARMOR_PEN -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            // ===== 终极节点（纵列3，子枫招牌技） =====
            case ULT_BLOOD -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case ULT_GOLDEN -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case ULT_MASTER -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case ULT_FAVOR -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case NIGHT_VISION -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case SATURATION -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case ULT_REVIVE -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case ULT_REAPER -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case ULT_VOID_BODY -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            // 终极节点·生存辅助（2026-08-27）
            case FLY_NO_INERTIA -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case FLY_MINING -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case FIRE_PROTECT -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case WATER_BREATH -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case DARK_VISION -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case UNDERWATER_VISION -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case AE_INFINITE_CHANNEL -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case AUTO_SMELT -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case ULT_BREAK_ALL -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case ULT_UNBREAK_TAG -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case ULT_SWEEP -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case ULT_KB_RESIST -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case VILLAGE_HERO -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case REACH -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case GLOW -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case LOOT_BOMB -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case UNBREAKABLE -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case MOB_DROP -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case BLOCK_DROP -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case XP_GAIN -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case MOB_SPAWN_EGG -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case MOB_HEAD -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case AURA_EMPOWER -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            // ===== 机械共鸣（纵列5） =====
            case MACHINE_STAR -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case MACHINE_LOOT_BOMB -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case MACHINE_UNBREAKABLE -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case MACHINE_MOB_DROP -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case MACHINE_BLOCK_DROP -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case MACHINE_XP_GAIN -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case MACHINE_SPAWN_EGG -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case MACHINE_MOB_HEAD -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case MACHINE_AUTO_SMELT -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            // ===== 光环（纵列4，领域神通风） =====
            case AURA_DAMAGE -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case AURA_SPEED -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case AURA_HEAL -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case AURA_MAGNET -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case AURA_TIME -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case AURA_WEATHER -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case AURA_LOCK -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case AURA_VOID -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case AURA_LOOT_VACUUM -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            // ===== 子枫的馈赠（纵列7，2026-08-25 新增） =====
            case GIFT_TIME_BAPTISM -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case GIFT_TIME_STORM -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case GIFT_TIME_FLOOD -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case GIFT_MOVE_BAPTISM -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case GIFT_MOVE_AMP -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case GIFT_FLY_BAPTISM -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case GIFT_FLY_AMP -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case GIFT_MINE_BAPTISM -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case GIFT_MINE_AMP -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case GIFT_KILL_BAPTISM -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case GIFT_KILL_AMP -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            // ===== 铁砧附魔（纵列4，2026-08-27 新增） =====
            case ENCHANT_RANDOM -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case ENCHANT_BREAK -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case ENCHANT_OVER -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case UNLIMITED_TRADES -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case VILLAGER_MASTER -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case TREASURE_HUNTER -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            default -> "skill.zifeng_s_custom_skill_tree.unknown.name";
        };
    }

    public static String getDescription(String skillId) {
        return switch (skillId) {
            case MANA_AMP -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case ARS_MANA_REGEN -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case IRON_MANA_AMP -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case IRON_MANA_REGEN -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case IRON_CAST_TIME -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case IRON_COOLDOWN -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case IRON_FIRE -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case IRON_ICE -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case IRON_LIGHTNING -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case IRON_HOLY -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case IRON_ENDER -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case IRON_BLOOD -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case IRON_EVOCATION -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case IRON_NATURE -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case IRON_ELDRITCH -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case BODY_HP -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case BODY -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case TOUGH -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case BLADE -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case ATTACK_SPEED -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case MINING -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case MOVE -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case REGEN -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case LUCK -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case JUMP -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case FLY -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case SWIM -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case CRIT -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case LIFESTEAL -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case THORNS -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case ARMOR_PEN -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case VILLAGE_HERO -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case REACH -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case GLOW -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case LOOT_BOMB -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case UNBREAKABLE -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case MOB_DROP -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case BLOCK_DROP -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case XP_GAIN -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case MOB_SPAWN_EGG -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case MOB_HEAD -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case AURA_EMPOWER -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case AMP_HP -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case AMP_DAMAGE -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case AMP_ATTACK_SPEED -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case AMP_MINING -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case AMP_REGEN -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case AMP_ARMOR -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case AMP_MOVE -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case AMP_JUMP -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case AMP_FLY -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case AMP_SWIM -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case AMP_TOUGH -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case AMP_LUCK -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case AMP_CRIT -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case AMP_LIFESTEAL -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case AMP_THORNS -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case AMP_ARMOR_PEN -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case ULT_BLOOD -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case ULT_GOLDEN -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case ULT_MASTER -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case ULT_FAVOR -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case NIGHT_VISION -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case SATURATION -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case ULT_REVIVE -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case ULT_REAPER -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case ULT_VOID_BODY -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            // ===== 终极节点·生存辅助（2026-08-27） =====
            case FLY_NO_INERTIA -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case FLY_MINING -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case FIRE_PROTECT -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case WATER_BREATH -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case DARK_VISION -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case UNDERWATER_VISION -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case AE_INFINITE_CHANNEL -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case AUTO_SMELT -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case ULT_BREAK_ALL -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case ULT_UNBREAK_TAG -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case ULT_SWEEP -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case ULT_KB_RESIST -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case MACHINE_STAR -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case MACHINE_LOOT_BOMB -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case MACHINE_UNBREAKABLE -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case MACHINE_MOB_DROP -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case MACHINE_BLOCK_DROP -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case MACHINE_XP_GAIN -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case MACHINE_SPAWN_EGG -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case MACHINE_MOB_HEAD -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case MACHINE_AUTO_SMELT -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case AURA_DAMAGE -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case AURA_SPEED -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case AURA_HEAL -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case AURA_MAGNET -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case AURA_TIME -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case AURA_WEATHER -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case AURA_LOCK -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case AURA_VOID -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case AURA_LOOT_VACUUM -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            // ===== 子枫的馈赠（纵列7，按游戏时长激活，免费获得技能点） =====
            case GIFT_TIME_BAPTISM -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case GIFT_TIME_STORM -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case GIFT_TIME_FLOOD -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case GIFT_MOVE_BAPTISM -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case GIFT_MOVE_AMP -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case GIFT_FLY_BAPTISM -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case GIFT_FLY_AMP -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case GIFT_MINE_BAPTISM -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case GIFT_MINE_AMP -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case GIFT_KILL_BAPTISM -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case GIFT_KILL_AMP -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            // ===== 铁砧附魔（2026-08-27） =====
            case ENCHANT_RANDOM -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case ENCHANT_BREAK -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case ENCHANT_OVER -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case UNLIMITED_TRADES -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case VILLAGER_MASTER -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case TREASURE_HUNTER -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            default -> "skill.zifeng_s_custom_skill_tree.unknown.desc";
        };
    }

    /** 技能显示名（聊天/提示用，按客户端语言渲染） */
    public static net.minecraft.network.chat.Component getDisplayNameComponent(String skillId) {
        return net.minecraft.network.chat.Component.translatable(getDisplayName(skillId));
    }

    /** 数字格式化：去掉无意义尾零且不用科学计数法（2.0 → "2"，0.005 → "0.005"，0.05 → "0.05"） */
    private static String fmt(double v) {
        return java.math.BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();
    }

    /**
     * 技能描述（按客户端语言渲染）。
     * 2026-08-29：基础/增幅/多级终极的"每点数值"全部从 Config 动态读取（P1 全量可配置），
     * lang 模板里的 %s 按配置填充，改配置后无需改翻译文件。
     */
    public static net.minecraft.network.chat.Component getDescriptionComponent(String skillId) {
        return switch (skillId) {
            // ===== 基础属性（每点数值走 Config） =====
            case BODY_HP -> net.minecraft.network.chat.Component.translatable(getDescription(skillId),
                    fmt(org.zifeng.skilltree.Config.BODY_HP_PER_POINT.get()));
            case BODY -> net.minecraft.network.chat.Component.translatable(getDescription(skillId),
                    fmt(org.zifeng.skilltree.Config.BODY_ARMOR_PER_POINT.get()),
                    fmt(org.zifeng.skilltree.Config.BODY_DR_PER_POINT.get() * 100) + "%");
            case TOUGH -> net.minecraft.network.chat.Component.translatable(getDescription(skillId),
                    fmt(org.zifeng.skilltree.Config.TOUGH_TOUGHNESS_PER_POINT.get()),
                    fmt(org.zifeng.skilltree.Config.TOUGH_KB_PER_POINT.get() * 100) + "%");
            case BLADE -> net.minecraft.network.chat.Component.translatable(getDescription(skillId),
                    fmt(org.zifeng.skilltree.Config.BLADE_DAMAGE_PER_POINT.get()));
            case ATTACK_SPEED -> net.minecraft.network.chat.Component.translatable(getDescription(skillId),
                    fmt(org.zifeng.skilltree.Config.ATTACK_SPEED_PER_POINT.get()));
            case MINING -> net.minecraft.network.chat.Component.translatable(getDescription(skillId),
                    fmt(org.zifeng.skilltree.Config.MINING_SPEED_PER_POINT.get()));
            case MOVE -> net.minecraft.network.chat.Component.translatable(getDescription(skillId),
                    fmt(org.zifeng.skilltree.Config.MOVE_SPEED_PER_POINT.get()));
            case LUCK -> net.minecraft.network.chat.Component.translatable(getDescription(skillId),
                    fmt(org.zifeng.skilltree.Config.LUCK_PER_POINT.get()));
            case JUMP -> net.minecraft.network.chat.Component.translatable(getDescription(skillId),
                    fmt(org.zifeng.skilltree.Config.JUMP_PER_POINT.get()));
            case FLY -> net.minecraft.network.chat.Component.translatable(getDescription(skillId),
                    fmt(org.zifeng.skilltree.Config.FLY_SPEED_PER_POINT.get()));
            case SWIM -> net.minecraft.network.chat.Component.translatable(getDescription(skillId),
                    fmt(org.zifeng.skilltree.Config.SWIM_SPEED_PER_POINT.get()));
            // ===== 增幅属性（每点百分比走 Config） =====
            case AMP_HP -> net.minecraft.network.chat.Component.translatable(getDescription(skillId),
                    fmt(org.zifeng.skilltree.Config.AMP_HP_PER_POINT.get() * 100) + "%");
            case AMP_TOUGH -> net.minecraft.network.chat.Component.translatable(getDescription(skillId),
                    fmt(org.zifeng.skilltree.Config.AMP_TOUGH_PER_POINT.get() * 100) + "%");
            case AMP_LUCK -> net.minecraft.network.chat.Component.translatable(getDescription(skillId),
                    fmt(org.zifeng.skilltree.Config.AMP_LUCK_PER_POINT.get() * 100) + "%");
            case AMP_DAMAGE -> net.minecraft.network.chat.Component.translatable(getDescription(skillId),
                    fmt(org.zifeng.skilltree.Config.AMP_DAMAGE_PER_POINT.get() * 100) + "%");
            case AMP_ATTACK_SPEED -> net.minecraft.network.chat.Component.translatable(getDescription(skillId),
                    fmt(org.zifeng.skilltree.Config.AMP_ATTACK_SPEED_PER_POINT.get() * 100) + "%");
            case AMP_MINING -> net.minecraft.network.chat.Component.translatable(getDescription(skillId),
                    fmt(org.zifeng.skilltree.Config.AMP_MINING_PER_POINT.get() * 100) + "%");
            case AMP_MOVE -> net.minecraft.network.chat.Component.translatable(getDescription(skillId),
                    fmt(org.zifeng.skilltree.Config.AMP_MOVE_PER_POINT.get() * 100) + "%");
            case AMP_JUMP -> net.minecraft.network.chat.Component.translatable(getDescription(skillId),
                    fmt(org.zifeng.skilltree.Config.AMP_JUMP_PER_POINT.get() * 100) + "%");
            case AMP_FLY -> net.minecraft.network.chat.Component.translatable(getDescription(skillId),
                    fmt(org.zifeng.skilltree.Config.AMP_FLY_PER_POINT.get() * 100) + "%");
            case AMP_SWIM -> net.minecraft.network.chat.Component.translatable(getDescription(skillId),
                    fmt(org.zifeng.skilltree.Config.AMP_SWIM_PER_POINT.get() * 100) + "%");
            // 防御强化（金身真解）：物理减伤百分比（Config 是小数 0.005 = 0.5%）
            case AMP_ARMOR -> net.minecraft.network.chat.Component.translatable(getDescription(skillId),
                    fmt(org.zifeng.skilltree.Config.AMP_ARMOR_DR_PER_POINT.get() * 100) + "%");
            // ===== 多级终极（每级数值走 Config） =====
            case REACH -> net.minecraft.network.chat.Component.translatable(getDescription(skillId),
                    fmt(org.zifeng.skilltree.Config.REACH_PER_LEVEL.get()));
            case ULT_KB_RESIST -> net.minecraft.network.chat.Component.translatable(getDescription(skillId),
                    fmt(org.zifeng.skilltree.Config.KB_RESIST_PER_LEVEL.get() * 100) + "%");
            // ===== 其余技能：静态描述 =====
            default -> net.minecraft.network.chat.Component.translatable(getDescription(skillId));
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
            // 铁砧附魔（2026-08-27）：附魔突破/超限附魔 前置 = 随机附魔
            case ENCHANT_BREAK, ENCHANT_OVER -> List.of(Map.entry(ENCHANT_RANDOM, 1));
            // 子枫的馈赠增幅：需对应洗礼已学（2026-08-25）
            case GIFT_MOVE_AMP -> List.of(Map.entry(GIFT_MOVE_BAPTISM, 1));
            case GIFT_FLY_AMP -> List.of(Map.entry(GIFT_FLY_BAPTISM, 1));
            case GIFT_MINE_AMP -> List.of(Map.entry(GIFT_MINE_BAPTISM, 1));
            case GIFT_KILL_AMP -> List.of(Map.entry(GIFT_KILL_BAPTISM, 1));
            default -> List.of(); // 机械之星/宇宙的青睐/夜视/饱食/村庄英雄/接触距离/发光/战利品爆炸/工具不毁/掉落/经验/时间洗礼无前置
        };
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, path);
    }

    /**
     * 自定义技能图标贴图（2026-08-27）：返回非 null 时技能树用自定义贴图渲染（blit），
     * 返回 null 用 {@link #getIcon} 的原版物品图标。当前仅「无限回路」用自绘贴图
     * （AE2 主题：紫水晶能量 + ∞ 无限回路符号，无合适原版物品图标）。
     */
    public static ResourceLocation getIconTexture(String skillId) {
        return switch (skillId) {
            case AE_INFINITE_CHANNEL -> id("textures/skill/ae_infinite_channel.png");
            default -> null;
        };
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
            // ===== 终极节点·生存辅助（2026-08-27） =====
            case FLY_NO_INERTIA -> Items.FEATHER;             // 御风止步：羽毛（轻盈无惯性）
            case FLY_MINING -> Items.NETHERITE_PICKAXE;       // 凌空采掘：下界合金镐（飞行挖掘）
            case FIRE_PROTECT -> Items.MAGMA_CREAM;           // 烈焰不侵：岩浆膏（火焰）
            case WATER_BREATH -> Items.HEART_OF_THE_SEA;      // 鲛人之息：海洋之心（水下呼吸）
            case DARK_VISION -> Items.SCULK_SENSOR;           // 破暗之瞳：幽匿感测体（黑暗来源）
            case UNDERWATER_VISION -> Items.PRISMARINE_CRYSTALS; // 碧波清眸：海晶碎片（晶莹视野）
            case AE_INFINITE_CHANNEL -> Items.AMETHYST_SHARD; // 无限回路：紫水晶碎片（AE能量）
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
            case AURA_LOOT_VACUUM -> Items.STICK;             // 凋落物挪移：木棍（绑定容器的工具）
            // ===== 子枫的馈赠（纵列7） =====
            case GIFT_TIME_BAPTISM -> Items.CLOCK;            // 时间洗礼：时钟（时间）
            case GIFT_TIME_STORM -> Items.LIGHTNING_ROD;      // 时间风暴：避雷针（风暴）
            case GIFT_TIME_FLOOD -> Items.WATER_BUCKET;       // 时间洪流：水桶（洪流）
            case GIFT_MOVE_BAPTISM -> Items.LEATHER_BOOTS;    // 移动洗礼：皮靴（行走）
            case GIFT_MOVE_AMP -> Items.DIAMOND_BOOTS;        // 移动洗礼增幅：钻石靴（进阶）
            case GIFT_FLY_BAPTISM -> Items.ELYTRA;            // 飞行洗礼：鞘翅（飞行）
            case GIFT_FLY_AMP -> Items.PHANTOM_MEMBRANE;      // 飞行洗礼增幅：幻翼膜（飞行进阶）
            case GIFT_MINE_BAPTISM -> Items.IRON_PICKAXE;     // 挖掘洗礼：铁镐（挖掘）
            case GIFT_MINE_AMP -> Items.DIAMOND_PICKAXE;      // 挖掘洗礼增幅：钻石镐（挖掘进阶）
            case GIFT_KILL_BAPTISM -> Items.IRON_SWORD;       // 击杀馈赠：铁剑（击杀）
            case GIFT_KILL_AMP -> Items.DIAMOND_SWORD;        // 击杀馈赠增幅：钻石剑（击杀进阶）
            // ===== 铁砧附魔（2026-08-27）：青金石/青金石块/下界之星 =====
            case ENCHANT_RANDOM -> Items.LAPIS_LAZULI;        // 随机附魔：青金石（附魔材料）
            case ENCHANT_BREAK -> Items.LAPIS_BLOCK;          // 附魔突破：青金石块（附魔进阶）
            case ENCHANT_OVER -> Items.NETHER_STAR;           // 超限附魔：下界之星（极限力量）
            case UNLIMITED_TRADES -> Items.EMERALD;           // 无限交易：绿宝石（交易货币）
            case VILLAGER_MASTER -> Items.EMERALD_BLOCK;      // 村民大师：绿宝石块（满级大师）
            case TREASURE_HUNTER -> Items.GOLD_NUGGET;        // 寻宝大师：金粒（宝箱宝藏）
            default -> Items.BARRIER;
        };
    }
}
