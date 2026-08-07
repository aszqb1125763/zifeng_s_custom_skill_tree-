package org.zifeng.skilltree.skill;

import net.minecraft.resources.ResourceLocation;
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
    /** 基础技能每级技能点消耗 */
    public static final double BASE_POINT_COST = 0.2;
    /** 特殊增幅每级技能点消耗 */
    public static final double AMPLIFY_POINT_COST = 0.5;
    /** 终极节点前置：两个指定技能各需投入点数 */
    public static final int ULTIMATE_REQUIRE_POINTS = 500;
    /** 宇宙的青睐：一次性消耗技能点数 */
    public static final int ULT_FAVOR_COST = 1000;
    /** 杀戮光环基础消耗（每级） */
    public static final int AURA_BASE_COST = 1000;
    /** 杀戮光环每级消耗递增倍率（×1.5） */
    public static final double AURA_COST_MULTIPLIER = 1.5;

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
    public static final String BODY = "body";                 // 体魄强化（起始）
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

    // ============ 特殊增幅技能 ============
    public static final String AMP_DAMAGE = "amp_damage";             // 战斗强化
    public static final String AMP_ATTACK_SPEED = "amp_attack_speed"; // 攻速增幅
    public static final String AMP_MINING = "amp_mining";             // 采掘效率
    public static final String AMP_REGEN = "amp_regen";               // 生命涌泉
    public static final String AMP_ARMOR = "amp_armor";               // 防御强化
    public static final String AMP_MOVE = "amp_move";                 // 移速增幅
    public static final String AMP_DROP = "amp_drop";                 // 掉落增幅
    public static final String AMP_JUMP = "amp_jump";                 // 跃升增幅（跳跃高度倍率）
    public static final String AMP_FLY = "amp_fly";                   // 御空增幅（飞行速度倍率）
    public static final String AMP_SWIM = "amp_swim";                 // 潜游增幅（游泳速度倍率）

    // ============ 终极节点 ============
    public static final String ULT_BLOOD = "ult_blood";     // 浴血奋战
    public static final String ULT_COMBO = "ult_combo";     // 疾风连斩
    public static final String ULT_GOLDEN = "ult_golden";   // 不坏金身
    public static final String ULT_DIG = "ult_dig";         // 万物皆可挖
    public static final String ULT_MASTER = "ult_master";   // 全能精通（毕业）
    public static final String ULT_FAVOR = "ult_favor";     // 宇宙的青睐（真创造飞行）
    public static final String NIGHT_VISION = "night_vision"; // 夜视（100点，1级）
    public static final String SATURATION = "saturation";     // 饱食（100点，1级）

    // ============ 杀戮光环（AURA，独立系统） ============
    public static final String AURA_DAMAGE = "aura_damage"; // 杀戮光环·伤害
    public static final String AURA_WEAPON = "aura_weapon"; // 杀戮光环·武器（钻石剑）
    public static final String AURA_SPEED = "aura_speed";   // 杀戮光环·速度

    /** 所有基础技能（纵列1） */
    public static final List<String> BASE_SKILLS = List.of(BODY, TOUGH, BLADE, ATTACK_SPEED, MINING, MOVE, REGEN, LUCK, JUMP, FLY, SWIM);
    /** 所有增幅技能（纵列2） */
    public static final List<String> AMPLIFY_SKILLS = List.of(AMP_DAMAGE, AMP_ATTACK_SPEED, AMP_MINING, AMP_REGEN, AMP_ARMOR, AMP_MOVE, AMP_DROP, AMP_JUMP, AMP_FLY, AMP_SWIM);
    /** 所有终极节点（纵列3） */
    public static final List<String> ULTIMATE_SKILLS = List.of(ULT_BLOOD, ULT_COMBO, ULT_GOLDEN, ULT_DIG, ULT_MASTER, ULT_FAVOR, NIGHT_VISION, SATURATION);
    /** 所有杀戮光环（纵列4） */
    public static final List<String> AURA_SKILLS = List.of(AURA_DAMAGE, AURA_WEAPON, AURA_SPEED);

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
            case AURA_WEAPON -> 10;
            case AURA_SPEED -> 100;
            default -> 0;
        };
    }

    /** 各技能等级上限（按钮第2行显示用）：基础 1000 / 增幅 500 / 终极 1 / 光环各自上限 */
    public static int getMaxPoints(String skillId) {
        return switch (getType(skillId)) {
            case BASE -> BASE_MAX_POINTS;
            case AMPLIFY -> AMPLIFY_MAX_POINTS;
            case ULTIMATE -> 1;
            case AURA -> getAuraMaxPoints(skillId);
        };
    }

    /** 杀戮光环：下一级消耗 = 1000 × 1.5^当前等级 */
    public static int getAuraCost(String skillId, int currentLevel) {
        return (int) Math.round(AURA_BASE_COST * Math.pow(AURA_COST_MULTIPLIER, currentLevel));
    }

    public static String getDisplayName(String skillId) {
        return switch (skillId) {
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
            case AMP_DAMAGE -> "战斗强化";
            case AMP_ATTACK_SPEED -> "攻速增幅";
            case AMP_MINING -> "采掘效率";
            case AMP_REGEN -> "生命涌泉";
            case AMP_ARMOR -> "防御强化";
            case AMP_MOVE -> "移速增幅";
            case AMP_DROP -> "掉落增幅";
            case AMP_JUMP -> "跃升增幅";
            case AMP_FLY -> "御空增幅";
            case AMP_SWIM -> "潜游增幅";
            case ULT_BLOOD -> "浴血奋战";
            case ULT_COMBO -> "疾风连斩";
            case ULT_GOLDEN -> "不坏金身";
            case ULT_DIG -> "万物皆可挖";
            case ULT_MASTER -> "全能精通";
            case ULT_FAVOR -> "宇宙的青睐";
            case NIGHT_VISION -> "星瞳·夜视";
            case SATURATION -> "星食·饱腹";
            case AURA_DAMAGE -> "杀戮光环·伤害";
            case AURA_WEAPON -> "杀戮光环·武器";
            case AURA_SPEED -> "杀戮光环·速度";
            default -> "未知技能";
        };
    }

    public static String getDescription(String skillId) {
        return switch (skillId) {
            case BODY -> "锤炼肉身根基：每点 +0.5 最大生命、+0.2 护甲";
            case TOUGH -> "强化骨骼肌肉：每点 +0.3 护甲韧性、+0.1% 击退抗性";
            case BLADE -> "打磨战斗技巧：每点 +0.4 近战攻击伤害";
            case ATTACK_SPEED -> "淬炼出手速度：每点 +0.02 攻击速度";
            case MINING -> "掌握采掘节奏：每点 +0.3 挖掘速度、+0.5 工具耐久损耗减免";
            case MOVE -> "锻炼下肢力量：每点 +0.005 移动速度";
            case REGEN -> "加速身体自愈：每点 +0.1/秒 生命恢复";
            case LUCK -> "提升稀有掉落：每点 +0.1 幸运值";
            case JUMP -> "锻炼腿部爆发力：每点 +0.01 跳跃高度";
            case FLY -> "掌控气流：每点 +0.005 飞行速度";
            case SWIM -> "精通水性：每点 +0.005 游泳速度";
            case AMP_DAMAGE -> "放大近战基础伤害：每点 +0.5% 近战伤害倍率";
            case AMP_ATTACK_SPEED -> "放大基础攻速：每点 +0.4% 攻击速度倍率";
            case AMP_MINING -> "放大基础挖速：每点 +0.6% 挖掘速度倍率";
            case AMP_REGEN -> "放大基础恢复：每点 +0.8% 生命恢复倍率";
            case AMP_ARMOR -> "放大物理护甲：每点 +0.3% 护甲倍率";
            case AMP_MOVE -> "放大基础移速：每点 +0.5% 移动速度倍率";
            case AMP_DROP -> "提升掉落与经验：每点 +4% 怪物掉落、+5% 经验获取";
            case AMP_JUMP -> "放大跳跃高度：每点 +0.5% 跳跃高度倍率";
            case AMP_FLY -> "放大飞行速度：每点 +0.5% 飞行速度倍率";
            case AMP_SWIM -> "放大游泳速度：每点 +0.5% 游泳速度倍率";
            case ULT_BLOOD -> "生命低于30%时近战伤害+50%，但受到伤害+20%。前置：体魄强化500点 + 锋刃精通500点";
            case ULT_COMBO -> "连续攻击第3次起攻速额外+30%，中断重计。前置：疾攻术500点 + 攻速增幅500点";
            case ULT_GOLDEN -> "致命伤害保1血+3秒无敌，冷却180秒。前置：坚韧之躯500点 + 防御强化500点";
            case ULT_DIG -> "挖掘20%概率瞬间完成不耗耐久，仅限基础挖掘≤1.5秒方块。前置：采掘熟稔500点 + 采掘效率500点";
            case ULT_MASTER -> "所有基础属性效果+25%，技能点获取速度-20%。前置：需解锁全部4个终极节点（浴血奋战/疾风连斩/不坏金身/万物皆可挖）";
            case ULT_FAVOR -> "宇宙的青睐：消耗 1000 技能点一次性点亮，解锁真正的创造飞行";
            case NIGHT_VISION -> "星瞳·夜视：消耗 100 技能点一次性点亮，永久获得夜视效果";
            case SATURATION -> "星食·饱腹：消耗 100 技能点一次性点亮，饱食度与饱和度永远保持满值";
            case AURA_DAMAGE -> "杀戮光环·伤害：每级+0.5攻击伤害，上限1000级，每级消耗×1.5（并入攻伤属性，可被战斗强化/全能精通加成）";
            case AURA_WEAPON -> "杀戮光环·武器：每级增加1把环绕钻石剑自动攻击，上限10级，攻击半径20格";
            case AURA_SPEED -> "杀戮光环·速度：每级+0.19攻速，上限100级，点满每秒攻击20次（并入攻速属性，可被攻速增幅/全能精通加成）";
            default -> "";
        };
    }

    /** 终极节点前置：两个技能各需投入的指定点数 */
    public static List<String> getUltimateRequirements(String skillId) {
        return switch (skillId) {
            case ULT_BLOOD -> List.of(BODY, BLADE);
            case ULT_COMBO -> List.of(ATTACK_SPEED, AMP_ATTACK_SPEED);
            case ULT_GOLDEN -> List.of(TOUGH, AMP_ARMOR);
            case ULT_DIG -> List.of(MINING, AMP_MINING);
            case ULT_MASTER -> List.of(ULT_BLOOD, ULT_COMBO, ULT_GOLDEN, ULT_DIG);
            default -> List.of(); // 宇宙的青睐/夜视/饱食无前置
        };
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, path);
    }
}
