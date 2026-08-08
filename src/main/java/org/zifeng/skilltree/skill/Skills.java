package org.zifeng.skilltree.skill;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.zifeng.skilltree.Config;
import org.zifeng.skilltree.SkillTreeMod;

import java.util.ArrayList;
import java.util.List;

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
    /** 宇宙的青睐：一次性消耗技能点数（默认值，可被 Config 覆盖） */
    public static final int ULT_FAVOR_COST = 1000;
    /** 杀戮光环基础消耗（每级，默认值，可被 Config 覆盖） */
    public static final int AURA_BASE_COST = 1000;
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

    public static int ultFavorCost() {
        return Config.ULT_FAVOR_COST.get();
    }

    /** 夜视/饱食一次性消耗（Config 可调） */
    public static int minorUltCost() {
        return Config.MINOR_ULT_COST.get();
    }

    public static int auraBaseCost() {
        return Config.AURA_BASE_COST.get();
    }

    public static double auraCostMultiplier() {
        return Config.AURA_COST_MULTIPLIER.get();
    }

    /**
     * 终极节点一次性消耗技能点（用户指定，可被 Config 覆盖）：
     * 浴血奋战/不坏金身/凤凰涅槃 = 500，死神凝视 = 1000，全能精通 = 5000，其余普通终极 = 1
     */
    public static int ultimateCost(String skillId) {
        return switch (skillId) {
            case ULT_BLOOD, ULT_GOLDEN, ULT_REVIVE -> Config.ULT_BASE_COST.get();
            case ULT_REAPER -> Config.ULT_REAPER_COST.get();
            case ULT_MASTER -> Config.ULT_MASTER_COST.get();
            case ULT_VOID_BODY -> (int) Math.round(Config.VOID_BODY_COST.get());
            default -> 1; // 普通终极 1 点
        };
    }

    public enum SkillType {
        /** 基础属性（固定数值） */
        BASE,
        /** 特殊增幅（百分比） */
        AMPLIFY,
        /** 终极节点（单次解锁） */
        ULTIMATE,
        /** 杀戮光环（独立系统，不受属性加成） */
        AURA
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
    public static final String AMP_DROP = "amp_drop";                 // 掉落增幅（独立：掉落/经验）

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
    public static final String AURA_TIME = "aura_time";       // 时之环·永恒正午（锁定世界时间）
    public static final String AURA_WEATHER = "aura_weather"; // 晴空环·永恒晴天（锁定天气）
    public static final String AURA_LOCK = "aura_lock";       // 光环锁定（免疫TP/击退）
    public static final String AURA_VOID = "aura_void";       // 杀戮光环·虚空之矛（虚空伤害/秒杀）

    /** 所有基础技能（纵列1） */
    public static final List<String> BASE_SKILLS = List.of(BODY_HP, BODY, TOUGH, BLADE, ATTACK_SPEED, MINING, MOVE, REGEN, LUCK, JUMP, FLY, SWIM, CRIT, LIFESTEAL, THORNS, ARMOR_PEN);
    /** 所有增幅技能（纵列2）：与基础技能一一对应，顺序与纵列1相同；掉落增幅独立放末尾 */
    public static final List<String> AMPLIFY_SKILLS = List.of(
            AMP_HP, AMP_ARMOR, AMP_TOUGH, AMP_DAMAGE, AMP_ATTACK_SPEED, AMP_MINING, AMP_MOVE,
            AMP_REGEN, AMP_LUCK, AMP_JUMP, AMP_FLY, AMP_SWIM,
            AMP_CRIT, AMP_LIFESTEAL, AMP_THORNS, AMP_ARMOR_PEN, AMP_DROP);
    /** 所有终极节点（纵列3） */
    public static final List<String> ULTIMATE_SKILLS = List.of(ULT_BLOOD, ULT_GOLDEN, ULT_MASTER, ULT_FAVOR, NIGHT_VISION, SATURATION, ULT_REVIVE, ULT_REAPER, ULT_VOID_BODY, VILLAGE_HERO, REACH, GLOW, LOOT_BOMB);
    /** 所有杀戮光环（纵列4） */
    public static final List<String> AURA_SKILLS = List.of(AURA_DAMAGE, AURA_SPEED, AURA_HEAL, AURA_MAGNET, AURA_TIME, AURA_WEATHER, AURA_LOCK, AURA_VOID);

    public static final List<String> ALL_SKILLS = new ArrayList<>() {{
        addAll(BASE_SKILLS);
        addAll(AMPLIFY_SKILLS);
        addAll(ULTIMATE_SKILLS);
        addAll(AURA_SKILLS);
    }};

    public static SkillType getType(String skillId) {
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
            case AURA_VOID -> 1; // 一次性解锁（5000 技能点）
            default -> 0;
        };
    }

    /** 各技能等级上限（按钮第2行显示用）：基础 1000 / 增幅 500 / 终极 1（多级终极各自上限） / 光环各自上限 */
    public static int getMaxPoints(String skillId) {
        return switch (getType(skillId)) {
            case BASE -> BASE_MAX_POINTS;
            case AMPLIFY -> AMPLIFY_MAX_POINTS;
            case ULTIMATE -> getUltimateMaxPoints(skillId);
            case AURA -> getAuraMaxPoints(skillId);
        };
    }

    /**
     * 终极节点等级上限：默认单次解锁（1）；多级终极节点（节点类，无前置）各自上限：
     * 村庄英雄 10 / 接触距离 50 / 发光 1 / 战利品爆炸 100
     */
    public static int getUltimateMaxPoints(String skillId) {
        return switch (skillId) {
            case VILLAGE_HERO -> 10;
            case REACH -> 50;
            case GLOW -> 1;
            case LOOT_BOMB -> 100;
            default -> 1;
        };
    }

    /**
     * 终极节点每级消耗（节点类）：阶梯递增——每级消耗在上一级基础上增加 10%（Config 可调）。
     * <pre>cost(第n级) = round(base × 1.1^n)</pre>
     * 基础值：村庄英雄/战利品爆炸 10、接触距离 1、发光 1；普通单次终极走 ultimateCost。
     * @param currentLevel 当前已学等级（第 0 级 = 学第 1 级的消耗）
     */
    public static double getUltimateLevelCost(String skillId, int currentLevel) {
        double base = switch (skillId) {
            case VILLAGE_HERO, LOOT_BOMB -> 10.0;
            case REACH, GLOW -> 1.0;
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

    /** 杀戮光环：下一级消耗 = 1000 × 1.05^当前等级（数值均走 Config） */
    public static int getAuraCost(String skillId, int currentLevel) {
        return (int) Math.round(auraBaseCost() * Math.pow(auraCostMultiplier(), currentLevel));
    }

    public static String getDisplayName(String skillId) {
        return switch (skillId) {
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
            case AMP_HP -> "生命增幅";
            case AMP_DAMAGE -> "锋刃增幅";
            case AMP_ATTACK_SPEED -> "疾攻增幅";
            case AMP_MINING -> "采掘增幅";
            case AMP_REGEN -> "再生增幅";
            case AMP_ARMOR -> "防御强化";
            case AMP_MOVE -> "疾行增幅";
            case AMP_DROP -> "掉落增幅";
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
            case AURA_DAMAGE -> "杀戮光环·伤害";
            case AURA_SPEED -> "杀戮光环·速度";
            case AURA_HEAL -> "治愈光环";
            case AURA_MAGNET -> "磁力光环";
            case AURA_TIME -> "时之环·永恒正午";
            case AURA_WEATHER -> "晴空环·永恒晴天";
            case AURA_LOCK -> "光环锁定";
            case AURA_VOID -> "杀戮光环·虚空之矛";
            default -> "未知技能";
        };
    }

    public static String getDescription(String skillId) {
        return switch (skillId) {
            case BODY_HP -> "生命强化：每点 +2 最大生命\n（拆分自原体魄强化，纯生命成长）";
            case BODY -> "体魄强化：每点 +0.2 护甲、\n+0.05% 物理减伤（超原版 80% 护甲上限后继续成长）";
            case TOUGH -> "坚韧之躯：每点 +0.3 护甲韧性、\n+0.1% 击退抗性";
            case BLADE -> "锋刃精通：每点 +0.4 近战攻击伤害";
            case ATTACK_SPEED -> "疾攻术：每点 +0.02 攻击速度";
            case MINING -> "采掘熟稔：每点 +0.3 挖掘速度、\n+20% 工具耐久损耗减免（5级封顶：工具不毁）";
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
            case AMP_HP -> "生命增幅：每点 +0.5% 最大生命倍率";
            case AMP_DAMAGE -> "锋刃增幅：每点 +0.5% 近战伤害倍率";
            case AMP_ATTACK_SPEED -> "疾攻增幅：每点 +0.4% 攻击速度倍率";
            case AMP_MINING -> "采掘增幅：每点 +0.6% 挖掘速度倍率";
            case AMP_REGEN -> "再生增幅：每点 +0.8% 生命恢复倍率";
            case AMP_ARMOR -> "防御强化：每点 +0.05% 物理减伤\n（独立减伤层，护甲 80% 封顶后继续防护）";
            case AMP_MOVE -> "疾行增幅：每点 +0.5% 移动速度倍率";
            case AMP_DROP -> "掉落增幅：每点 +4% 生物掉落、\n+4% 方块掉落、+5% 经验获取\n（仅对可受时运/抢夺的方块和生物生效）";
            case AMP_JUMP -> "跃升增幅：每点 +0.5% 跳跃高度倍率";
            case AMP_FLY -> "御空增幅：每点 +0.5% 飞行速度倍率";
            case AMP_SWIM -> "潜游增幅：每点 +0.5% 游泳速度倍率";
            case AMP_TOUGH -> "坚韧增幅：每点 +0.5% 护甲韧性与击退抗性倍率";
            case AMP_LUCK -> "幸运增幅：每点 +0.5% 幸运值倍率";
            case AMP_CRIT -> "暴击增幅：每点 +0.5% 暴击伤害倍率\n（在暴击 1.5 倍基础上叠加）";
            case AMP_LIFESTEAL -> "吸血增幅：每点 +0.4% 吸血量倍率";
            case AMP_THORNS -> "荆棘增幅：每点 +0.4% 反伤倍率";
            case AMP_ARMOR_PEN -> "破甲增幅：每点 +0.4% 破甲增伤倍率";
            case ULT_BLOOD -> "浴血奋战：消耗 500 技能点，\n常驻攻击力 +50%、最大生命 +50%\n（燃血强化）\n前置：体魄500 + 锋刃500";
            case ULT_GOLDEN -> "不坏金身：消耗 500 技能点，\n常驻抗性提升10级、伤害吸收100级、\n抗火5级\n前置：坚韧之躯500 + 防御强化500";
            case ULT_MASTER -> "全能精通：消耗 5000 技能点，\n全方位防御（全伤害减免/护盾/免死/\n负面免疫），保证玩家不死\n前置：需解锁浴血奋战/不坏金身/凤凰涅槃/死神凝视";
            case ULT_FAVOR -> "宇宙的青睐：消耗 1000 技能点\n一次性点亮，解锁真正的创造飞行";
            case NIGHT_VISION -> "星瞳·夜视：消耗 100 技能点\n一次性点亮，永久夜视（不闪烁）";
            case SATURATION -> "星食·饱腹：消耗 100 技能点\n一次性点亮，饱食度永远满值";
            case ULT_REVIVE -> "凤凰涅槃：消耗 500 技能点，\n死亡原地复活一次，回复50%生命\n并清除负面效果，冷却1分钟\n前置：生命汲取500 + 暴击精通500";
            case ULT_REAPER -> "死神凝视：消耗 1000 技能点，\n攻击生命<15%的非玩家生物时\n30%概率直接处决\n前置：破甲精通500 + 锋刃精通500";
            case ULT_VOID_BODY -> "虚空之躯：消耗 5000 技能点，\n三层无敌：免伤/免死/血量只增不减\n抗击退/免摔落/免火焰/清负面\n前置：全能精通";
            case AURA_DAMAGE -> "杀戮光环·伤害：每级+10%伤害倍率，\n360°范围伤害，附带混沌伤害\n（无视护甲真实伤害）\n上限1000级，默认10秒攻击一次";
            case AURA_SPEED -> "杀戮光环·速度：提高光环攻击频率\n（每级减少攻击间隔，20级=每秒2次）\n未点亮默认10秒攻击一次\n上限20级";
            case AURA_HEAL -> "治愈光环：每级给周围10格内\n友方单位对应等级的生命回复效果\n（50级 = 生命回复50级）\n上限50级，消耗随等级递增";
            case AURA_MAGNET -> "磁力光环：一次性解锁（消耗 " + String.format("%.0f", org.zifeng.skilltree.Config.MAGNET_COST.get()) + " 技能点）\n按 H 键开关，自动吸取经验与掉落物\n（潜行时暂停）";
            case AURA_TIME -> "时之环·永恒正午：消耗 " + Skills.minorUltCost() + " 技能点\n一次性点亮，开启后锁定世界时间\n为正午，不被睡觉/时间命令影响\n关闭后立即恢复正常时间流动";
            case AURA_WEATHER -> "晴空环·永恒晴天：消耗 " + Skills.minorUltCost() + " 技能点\n一次性点亮，开启后锁定晴天\n不被下雨/天气命令影响\n关闭后立即恢复正常天气";
            case AURA_LOCK -> "光环锁定：消耗 1000 技能点\n一次性点亮，开启后免疫 TP 与击退\n（传送/瞬移/击退均无效）\n只有自己移动/飞行才能真正移动";
            case AURA_VOID -> "杀戮光环·虚空之矛：消耗 5000 技能点\n一次性点亮，杀戮光环获得虚空之矛力量\n（绝对秒杀+范围扩大至50格，K键控制）\n（磁铁范围扩至55格，H键控制）";
            default -> "";
        };
    }

    /** 终极节点前置：两个技能各需投入的指定点数 */
    public static List<String> getUltimateRequirements(String skillId) {
        return switch (skillId) {
            case ULT_BLOOD -> List.of(BODY, BLADE);
            case ULT_GOLDEN -> List.of(TOUGH, AMP_ARMOR);
            case ULT_MASTER -> List.of(ULT_BLOOD, ULT_GOLDEN, ULT_REVIVE, ULT_REAPER);
            case ULT_REVIVE -> List.of(LIFESTEAL, CRIT);
            case ULT_REAPER -> List.of(ARMOR_PEN, BLADE);
            case ULT_VOID_BODY -> List.of(ULT_MASTER); // 前置：全能精通
            default -> List.of(); // 宇宙的青睐/夜视/饱食/村庄英雄/接触距离/发光/战利品爆炸无前置
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
            case AMP_DROP -> Items.GOLD_BLOCK;                 // 掉落增幅：金块（宝物）
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
