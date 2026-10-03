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
 *
 * <p>══════════════════════════════════════════════════════════════════════
 * <p><b>Z-Link 技能 → 调用入口登记表（权威总目录，2026-09-09）</b>
 * <p>改技能/加技能先查这里，定位它由哪个模块/事件执行。
 * <p>══════════════════════════════════════════════════════════════════════
 * <p><b>【A 持续型】→ system/ZModules 模块（每玩家每 tick 条件驱动 + 冬眠）</b>
 * <ul>
 *   <li>MagnetModule  → AURA_MAGNET（磁铁吸物/吸经验）</li>
 *   <li>AuraDamageModule → AURA_DAMAGE / AURA_SPEED / AURA_EMPOWER / AURA_VOID（光环攻击链）</li>
 *   <li>AuraHealModule  → AURA_HEAL（治愈光环）</li>
 *   <li>AuraXpModule    → AURA_XP（汲灵之环）</li>
 *   <li>UltimateTickModule → REGEN/ULT_GOLDEN/ULT_FAVOR/FLY/AMP_FLY/NIGHT_VISION/SATURATION/
 *       VILLAGE_HERO/GLOW/ULT_MASTER/ULT_VOID_BODY/WATER_BREATH/AE_INFINITE_CHANNEL/ULT_REVIVE
 *       （常驻 buff + 终极节点 tick 链，内部按块判断）</li>
 *   <li>GiftModule      → GIFT_*（馈赠时间/距离累计发点）</li>
 *   <li>ZoneAttackModule → MACHINE_ZONE_ATTACK（选区攻击）</li>
 *   <li>GlobalRuleModule → AURA_TIME / AURA_WEATHER（时环/晴空环全局锁定）</li>
 * </ul>
 * <p><b>【B 事件型】→ 游戏事件总线触发（触发才执行，零轮询）</b>
 * <ul>
 *   <li>UltimateEvents.onLivingDamage   → 暴击/破甲/死神凝视（玩家攻击时）</li>
 *   <li>UltimateEvents.onSweepAttack    → ULT_SWEEP（横扫，近战击中时）</li>
 *   <li>UltimateEvents.onDamagePost     → LIFESTEAL（吸血，伤害结算后）</li>
 *   <li>UltimateEvents.onLivingIncomingDamage → 荆棘反伤/全能精通减伤/虚空之躯（受击时）</li>
 *   <li>UltimateEvents.onKnockBack      → TOUGH/ULT_KB_RESIST/ULT_GOLDEN/ULT_VOID_BODY（击退免疫）</li>
 *   <li>UltimateEvents.onLivingFall     → 摔落保护相关</li>
 *   <li>UltimateEvents.onLivingDeath    → ULT_MASTER 免死/ULT_REVIVE 凤凰涅槃/ULT_VOID_BODY（死亡时）</li>
 *   <li>UltimateEvents.onLivingDrops    → LOOT_BOMB/MOB_DROP/MOB_SPAWN_EGG/MOB_HEAD/AURA_LOOT_VACUUM（击杀掉落）</li>
 *   <li>UltimateEvents.onBlockDrops(1.21.1 BlockDropsEvent) → 万物挖掘补掉落/AUTO_SMELT/BLOCK_DROP/挪移（挖矿掉落）</li>
 *   <li>UltimateEvents.onExperienceDrop → XP_GAIN（经验倍率）</li>
 *   <li>UltimateEvents.onTradeWithVillager → UNLIMITED_TRADES/VILLAGER_MASTER（交易）</li>
 *   <li>UltimateEvents.onAnvilUpdate    → ENCHANT_RANDOM/ENCHANT_BREAK/ENCHANT_OVER/ULT_UNBREAK_TAG（铁砧）</li>
 *   <li>UltimateEvents.onEffectApplicable → FIRE_PROTECT/DARK_VISION（效果免疫）</li>
 *   <li>MagnetEvents.onLeftClickBlock   → AURA_MAGNET 木棍左键拦截（RANGE 选区）</li>
 *   <li>LockEvents.*                    → AURA_LOCK（TP/击退免疫，零 tick）</li>
 *   <li>LootVacuumEvents.*              → AURA_LOOT_VACUUM / CONTAINER_HAUL 绑定容器/入容器（右键/开箱）</li>
 *   <li>ContainerHaulEvents.*           → CONTAINER_HAUL（开箱搬运）</li>
 *   <li>GiftEvents.onBlockBreak/onLivingDeath → GIFT_MINE_BAPTISM/GIFT_KILL_BAPTISM 计数</li>
 *   <li>ZoneSkillEvents.triggerPlace/triggerExcavate → MACHINE_ZONE_PLACE/MACHINE_ZONE_EXCAVATE（触发键）</li>
 *   <li>SkillEvents（进出世界/放置等）→ 属性重挂/转换机绑定/BLINK/GLUTTONY</li>
 *   <li>LootVacuumEvents 由 GLM(1.20.1 loot包) / BlockDropsEvent(1.21.1) 驱动方块掉落进容器</li>
 * </ul>
 * <p><b>【C 客户端表现】→ Z-UI（纯展示，数据消费者）</b>
 * <ul>
 *   <li>ClientFlightEvents → FLY_NO_INERTIA（飞行无惯性，客户端输入）</li>
 *   <li>ClientTreasureEvents → TREASURE_HUNTER（寻宝发光轮廓，客户端渲染）</li>
 *   <li>ClientVisionEvents → UNDERWATER_VISION/FIRE_PROTECT（视野）</li>
 *   <li>SkillTreeScreen/HUD/选区渲染 → 全部技能展示（Z-UI 层）</li>
 * </ul>
 * <p>══════════════════════════════════════════════════════════════════════
 */
public final class Skills {
    private Skills() {
    }

    /** 基础类每项上限 */
    // ══════════ 等级压缩（2026-09-14）══════════
    /**
     * 等级压缩倍率：原 1000 级 → 100 级、500 级 → 50 级。
     *
     * <p>压缩后 **每级效果自动 ×本倍率**（见 {@code SkillEffects.effLevel}），
     * 因此技能的总效果与原设计完全一致，只是"级数"变少、每级成长更明显。
     */
    public static final int LEVEL_COMPRESSION = 10;

    /**
     * 该技能是否受等级压缩影响（效果需 ×{@link #LEVEL_COMPRESSION}）。
     *
     * <ul>
     *   <li>BASE 15 项：1000 → 100</li>
     *   <li>AMPLIFY 15 项：500 → 50</li>
     *   <li>MAGIC：大上限的（原 1000/500 → 100/50）压缩；吟唱/冷却缩减上限本就是 100 → 不压</li>
     *   <li>光环伤害（原 1000 → 100）压缩；其余光环上限本就较小 → 不压</li>
     * </ul>
     */
    public static boolean isLevelCompressed(String skillId) {
        if (AURA_DAMAGE.equals(skillId)) {
            return true;
        }
        return switch (getType(skillId)) {
            case BASE, AMPLIFY -> true;
            case MAGIC -> !(IRON_CAST_TIME.equals(skillId) || IRON_COOLDOWN.equals(skillId));
            default -> false; // 终极/特殊/光环(除伤害)/寰宇/机械/馈赠/工具：上限未压缩
        };
    }

    public static final int BASE_MAX_POINTS = 100;
    /** 特殊增幅类每项上限 */
    public static final int AMPLIFY_MAX_POINTS = 50;   // 2026-09-14 等级压缩：原 500
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
        // 2026-09-14 等级压缩（1000→100）：二次曲线——前期便宜、后期陡增，
        // 点满总消耗 ≈ 20.3 万（介于"每级×10"的 5 万与不压缩的 50 万之间，由用户授权平衡）
        final double n = currentLevel + 1.0;
        return Math.max(1L, Math.round(0.6 * n * n));
    }

    /**
     * 特殊增幅：第 n 级消耗（线性增长，下一级在上一级基础上 +2）。
     * 第 1 级 = 2 点，第 2 级 = 4 点，第 3 级 = 6 点...
     * ⚠️ 64 位返回（2026-08-12 统一）：所有技能消耗一律 long/double，防高等级 int 溢出。
     * @param currentLevel 当前已学等级（第 0 级 = 学第 1 级的消耗）
     */
    public static long getAmplifyCostAtLevel(int currentLevel) {
        // 2026-09-14 等级压缩（500→50）：二次曲线，点满总消耗 ≈ 10.3 万（与基础列保持 2:1）
        final double n = currentLevel + 1.0;
        return Math.max(1L, Math.round(2.4 * n * n));
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
            case GLUTTONY -> 5L;             // 暴食：一次性 5 点（2026-09-06）
            case BLINK -> 10L;               // 闪现：一次性 10 点（2026-09-06）
            // 奥术防护（2026-09-14）：进阶机制各 1000 点；奥术神体走 Config
            case ARCANE_ADAPT, SPELL_REFLECT, SPELL_PURGE, MANA_SIPHON, SPELLBREAK_BLADE -> 1000L;
            case ULT_ARCANE_BODY -> Math.round(Config.ARCANE_ULT_COST.get());
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
        GIFT,
        /** 垂钓（2026-10-03 新增：第 11 列，钓鱼玩法 + 自动化 + 现有机制联动） */
        FISHING
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
    // ★ 2026-09-30：战利品大爆发（LOOT_BOMB）= 旧「战利品爆炸」+「猎魂丰收」合并；1000 级、4 模式
    public static final String LOOT_BOMB = "loot_bomb";       // 战利品大爆发（掉落爆发，倍率=1+等级，上限1000级，4模式）
    public static final String UNBREAKABLE = "unbreakable";   // 工具不毁（耐久减免，上限5级，拆自采掘熟稔）
    /**
     * ⚠️ LEGACY（2026-09-30 已合并进 {@link #LOOT_BOMB}，技能树中不再出现）：
     * 保留常量与「上限/消耗」规则，<b>仅为旧存档退点迁移</b>能算出真实投入量
     * （{@code PlayerSkillRecord.totalSpentOf} 依赖 {@link #getUltimateLevelCost}）——
     * 千万不要顺手删掉这两个方法里的对应 case，否则退点会算成 0。
     */
    public static final String MOB_DROP = "mob_drop";         // [LEGACY] 原「猎魂丰收」，已合并进战利品大爆发
    public static final String BLOCK_DROP = "block_drop";     // 方块掉落倍率（上限10级，拆自掉落增幅）
    public static final String XP_GAIN = "xp_gain";           // 经验获取倍率（上限10级，拆自掉落增幅）
    public static final String MOB_SPAWN_EGG = "mob_spawn_egg"; // 刷怪蛋掉落（上限5级，每级20%，满级100%，独立不吃增幅）
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
    public static final String GLUTTONY = "gluttony";                 // 暴食（秒吃所有食物，1级，5点，2026-09-06）
    public static final String BLINK = "blink";                       // 闪现（向视线方向传送，1级，10点，2026-09-06）

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
    // 2026-08-25 新增：移动/飞行/挖掘洗礼（消耗技能点升级，统计原版数据）+ 各增幅
    // 2026-09-14：洗礼/增幅 上限 5 级 → 9 级，补齐 10 → 1000 之间的消耗过渡
    public static final String GIFT_MOVE_BAPTISM = "gift_move_baptism"; // 移动洗礼：统计行走+疾跑距离，1级1000米…9级10米 得1技能点
    public static final String GIFT_MOVE_AMP = "gift_move_amp";         // 移动洗礼增幅：每级每次+1技能点获取，上限9级
    public static final String GIFT_FLY_BAPTISM = "gift_fly_baptism";   // 飞行洗礼：统计飞行距离，1级1000米…9级10米 得1技能点
    public static final String GIFT_FLY_AMP = "gift_fly_amp";           // 飞行洗礼增幅：每级每次+1技能点获取，上限9级
    public static final String GIFT_MINE_BAPTISM = "gift_mine_baptism"; // 挖掘洗礼：统计挖掘方块数，1级1000块…9级10块 得1技能点
    public static final String GIFT_MINE_AMP = "gift_mine_amp";         // 挖掘洗礼增幅：每级每次+1技能点获取，上限9级
    public static final String GIFT_KILL_BAPTISM = "gift_kill_baptism"; // 击杀馈赠：统计击杀生物数，1级1000杀…9级10杀 得1技能点
    public static final String GIFT_KILL_AMP = "gift_kill_amp";         // 击杀馈赠增幅：每级每次+1技能点获取，上限9级

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
    public static final String CONTAINER_HAUL = "container_haul";     // 子枫的搬运术（打开容器瞬间/按触发键把容器物品搬进挪移绑定容器，1级10点，2026-09-07）
    public static final String AURA_XP = "aura_xp";                   // 汲灵之环（光环被动：每秒获得经验，上限100级，消耗指数增长 1000×1.05^n，2026-09-06）

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

    // ============ Goety（诡术）兼容技能（2026-09-20 新增） ============
    // 来源：Goety 作者 Polarice3，MIT，github.com/Polarice3/Goety-2（未混淆，有公开 api 包）
    // 它自己就做了铁魔法 + 神化兼容（compat.iron / Vivideru.Goety.compat.apotheosis），
    // 说明其属性（ModAttributes，共 27 个）本就是为外部模组调优准备的。
    //
    // ⚠️ 语义与铁魔法【完全不同】，不能照搬铁魔法的“每级+10%”：
    //   · 强度类 _POTENCY 是 flat 整数加值（默认 0，范围 0~2048），直接加到法术 potency
    //   · 减量类 _DISCOUNT / casting_speed / cooldown_discount 是“绝对减量比例”（范围 -1~1），
    //     属性值 0.8 = 减免 80%；1.0 即 100%（灵魂全免 + 瞬发 + 无冷却）
    //   · 数值与封顶见 {@link org.zifeng.skilltree.compat.GoetyCompat}
    /** Goety 通用法术强度（spell_potency）：每级 +1，上限 100（满级 +100） */
    public static final String GOETY_POTENCY = "goety_potency";
    /** Goety 通用灵魂消耗折扣（soul_discount）：每级 +0.01，上限 80（满级减 80%） */
    public static final String GOETY_SOUL_DISCOUNT = "goety_soul_discount";
    // 9 个流派精通（每个技能同时给该流派的【强度】与【灵魂折扣】）
    // ⚠️ 流派与铁魔法毫无对应关系：铁魔法是 fire/ice/lightning/holy/ender/blood/evocation/nature/eldritch，
    //     Goety 是下面这 9 个，因此无法复用铁魔法的流派技能。
    public static final String GOETY_ABYSS = "goety_abyss";           // 深渊
    public static final String GOETY_FROST = "goety_frost";           // 冰霜
    public static final String GOETY_GEOMANCY = "goety_geomancy";     // 地卜
    public static final String GOETY_NECROMANCY = "goety_necromancy"; // 死灵
    public static final String GOETY_NETHER = "goety_nether";         // 下界
    public static final String GOETY_STORM = "goety_storm";           // 风暴
    public static final String GOETY_VOID = "goety_void";             // 虚空
    public static final String GOETY_WILD = "goety_wild";             // 荒野
    public static final String GOETY_WIND = "goety_wind";             // 风

    // ============ 奥术防护（2026-09-14 新增）：魔法减伤与反制，填补“护甲只减物理”的空缺 ============
    // 核心机制：魔法减伤用 MOBA 公式 reduction = D/(D+K)，**永不达到 100%**，所以每级都有价值（可无限扩级）
    /** 奥术壁垒：魔法防御值（上限1000，吃奥术真解增幅） */
    public static final String ARCANE_BULWARK = "arcane_bulwark";
    /** 奥术真解：追加奥术壁垒的防御值（上限500） */
    public static final String ARCANE_AMP = "arcane_amp";
    /** 法术抑制：只对**间接伤害**（箭矢/法术）生效的额外减伤（上限500） */
    public static final String SPELL_DAMPEN = "spell_dampen";
    /** 适应之躯：同类型伤害连续受击递减，有硬上限，换类型重置（1级） */
    public static final String ARCANE_ADAPT = "arcane_adapt";
    /** 法术反射：概率把魔法伤害反射给施法者（不致死 + 防递归，1级） */
    public static final String SPELL_REFLECT = "spell_reflect";
    /** 驱法破咒：受击时清除自身一个负面效果（带冷却，1级） */
    public static final String SPELL_PURGE = "spell_purge";
    /** 法力虹吸：受到魔法伤害时按比例转为回血（1级） */
    public static final String MANA_SIPHON = "mana_siphon";
    /** 净化领域：光环，周期性清除范围内友方的负面效果（上限100） */
    public static final String PURIFY_FIELD = "purify_field";
    /** 破法之刃：近战命中驱散目标一个增益，并按目标增益数量增伤（1级） */
    public static final String SPELLBREAK_BLADE = "spellbreak_blade";
    /** 子枫的奥术神体：魔法防护终极，是【全能精通】的前置之一（1级） */
    public static final String ULT_ARCANE_BODY = "ult_arcane_body";

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
    // ===== 机械共鸣·木棍工具区块（2026-09-08：解锁木棍工具对应的选区模式；各 50 点，1 级） =====
    /** 选区放置：木棍工具放置模式；触发键把整区填满手上物品（优先扣绑定容器） */
    public static final String MACHINE_ZONE_PLACE = "machine_zone_place";
    /** 选区挖掘：木棍工具挖掘模式；触发键瞬间挖空全区（掉落进绑定容器、不产经验） */
    public static final String MACHINE_ZONE_EXCAVATE = "machine_zone_excavate";
    /** 选区攻击：木棍工具攻击模式；前置杀戮光环·伤害5级；开启后自动攻击区内（复用杀戮光环伤害） */
    public static final String MACHINE_ZONE_ATTACK = "machine_zone_attack";
    /** 杀戮光环·防护选区：木棍工具防护模式；前置已学杀戮光环；开启后区内友好目标免伤 */
    public static final String MACHINE_ZONE_PROTECT = "machine_zone_protect";

    // ============ 垂钓列（纵列10，2026-10-03 新增）============
    //   设计：手动抛竿是玩家唯一动作，其余全部自动化；产出与现有技能联动（熔炼/传送/爆发）。
    //   平衡自检：钓鱼零风险 → 核心倍率上限压到 11×（对比战利品大爆发 1001×），
    //   高收益通路（渔获爆发）必须已经买过战利品大爆发，不重复给高倍率。
    /** 急流垂钓：每级 −4% 咬钩等待时间，上限 20 级（满级 −80%） */
    public static final String FISH_HASTE = "fish_haste";
    /** 海神眷顾：每级 宝藏率 ×1.06 / 垃圾率 ×0.96，上限 25 级 */
    public static final String FISH_FORTUNE = "fish_fortune";
    /** 渔获满仓：渔获份数 ×(1 + 0.2×等级)，上限 50 级（满级 11×；按堆叠上限拆满堆） */
    public static final String FISH_BOUNTY = "fish_bounty";
    /** 全自动垂钓：手动抛竿一次后自动收杆取物并立即重抛（浮标钉回原位），一次性 3000 点 */
    public static final String FISH_AUTO = "fish_auto";
    /** 无界垂钓：解除开放水域限制（小水池/瀑布也算宝藏区），一次性 1000 点 */
    public static final String FISH_OPEN_WATER = "fish_open_water";
    /** 渔获经验：每次钓获额外获得经验，上限 25 级（每级 +2 点/件） */
    public static final String FISH_XP = "fish_xp";
    /** 现钓现炼：钓到的可熔炼物自动熔炼，一次性 200 点；前置=自动熔炼术 */
    public static final String FISH_SMELT = "fish_smelt";
    /** 定点渔获：渔获直传凋落物挪移绑定容器（不生成掉落物实体），一次性 300 点；前置=子枫挪移术 */
    public static final String FISH_VACUUM = "fish_vacuum";
    /** 渔获爆发：渔获参与「战利品大爆发」倍率与 4 模式，一次性 500 点；前置=战利品大爆发 */
    public static final String FISH_BOMB = "fish_bomb";
    /** 所有垂钓技能（纵列10，2026-10-03） */
    public static final List<String> FISHING_SKILLS = List.of(
            FISH_HASTE, FISH_FORTUNE, FISH_BOUNTY, FISH_XP,
            FISH_AUTO, FISH_OPEN_WATER,
            FISH_SMELT, FISH_VACUUM, FISH_BOMB);

    /** 所有基础技能（纵列1） */
    /** 所有基础技能（纵列1）：2026-09-14 起移除「铁壁金身 BODY」（职责拆给磐石之躯 + 金身真解） */
    public static final List<String> BASE_SKILLS = List.of(BODY_HP, TOUGH, BLADE, ATTACK_SPEED, MINING, MOVE, REGEN, LUCK, JUMP, FLY, SWIM, CRIT, LIFESTEAL, THORNS, ARMOR_PEN);
    /** 所有增幅技能（纵列2）：与基础技能一一对应，顺序与纵列1相同 */
    public static final List<String> AMPLIFY_SKILLS = List.of(
            AMP_HP, AMP_TOUGH, AMP_DAMAGE, AMP_ATTACK_SPEED, AMP_MINING, AMP_MOVE,
            AMP_REGEN, AMP_LUCK, AMP_JUMP, AMP_FLY, AMP_SWIM,
            AMP_CRIT, AMP_LIFESTEAL, AMP_THORNS, AMP_ARMOR_PEN);
    /** 所有终极节点（纵列3，2026-09-06 重新划分）：【成长型大招】——战斗质变/成长生产/吃技能点的主力增强 */
    public static final List<String> ULTIMATE_SKILLS = List.of(
            // ── 战斗大招（2026-09-14 重排：严格按前置链自上而下排列 —— 前置在上、终极在下）──
            ULT_BLOOD,       // 浴血奋战（前置：磐石之躯 / 锋刃精通，均在基础列）
            AMP_ARMOR,       // 金身真解（是「不坏金身」的前置 → 必须排在其上方；上限 80、每级 +1% 免伤）
            ULT_GOLDEN,      // 不坏金身（前置：磐石之躯 + 金身真解）
            ULT_REVIVE,      // 凤凰涅槃（前置：生命汲取 / 暴击精通）
            ULT_REAPER,      // 死神凝视（前置：破甲精通 / 锋刃精通）
            ULT_ARCANE_BODY, // 子枫的奥术神体（前置：奥术壁垒 / 奥术真解，在魔法列）
            ULT_MASTER,      // 全能精通（前置：以上 5 个终极 → 必须排在其下方）
            ULT_VOID_BODY,   // 虚空神体（前置：全能精通 → 必须排在其下方）
            ULT_FAVOR,       // 宇宙的青睐（无前置）
            // 掉落/生产成长系（自特殊列上移）：战利品大爆发/万载不磨/点石成金/经验飞涨/妖魂凝卵/斩首夺颅/自动熔炼/万物挖掘/不毁词条/横扫千军/稳如泰山
            // ⚠️ 2026-09-30：MOB_DROP（猎魂丰收）已合并进 LOOT_BOMB，从列表移除（常量保留供退点）
            LOOT_BOMB, UNBREAKABLE, BLOCK_DROP, XP_GAIN,
            MOB_SPAWN_EGG, MOB_HEAD, AUTO_SMELT, ULT_BREAK_ALL, ULT_UNBREAK_TAG,
            ULT_SWEEP, ULT_KB_RESIST);
    /** 所有特殊被动（纵列4，2026-09-06 重新划分）：【一次性奇技】——点一次给固定能力的玩法/工具/生存便利 */
    public static final List<String> SPECIAL_SKILLS = List.of(
            // 生存便利（自终极列下移）：夜视/饱食/御风止步/凌空采掘/烈焰不侵/鲛人之息/破暗之瞳/碧波清眸
            NIGHT_VISION, SATURATION, FLY_NO_INERTIA, FLY_MINING,
            FIRE_PROTECT, WATER_BREATH, DARK_VISION, UNDERWATER_VISION,
            // 玩法/工具
            VILLAGE_HERO, REACH, GLOW,
            ENCHANT_RANDOM, ENCHANT_BREAK, ENCHANT_OVER,
            UNLIMITED_TRADES, VILLAGER_MASTER, TREASURE_HUNTER,
            GLUTTONY, BLINK,
            // 奥术防护（2026-09-14）：一次性奇技
            ARCANE_ADAPT, SPELL_REFLECT, SPELL_PURGE, MANA_SIPHON, SPELLBREAK_BLADE);
    /** 所有杀戮光环（纵列5）：杀戮光环·强化 在 虚空之矛 上方；时之环/晴空环已移至寰宇法则列（2026-08-27）
     *  ⚠️ 2026-09-07：子枫的搬运术（CONTAINER_HAUL）紧随子枫挪移术后（系列，复用其绑定容器）。 */
    public static final List<String> AURA_SKILLS = List.of(AURA_DAMAGE, AURA_SPEED, AURA_HEAL, AURA_MAGNET, AURA_LOCK, AURA_EMPOWER, AURA_VOID, AURA_LOOT_VACUUM, CONTAINER_HAUL, AURA_XP,
            PURIFY_FIELD); // 净化领域（2026-09-14 奥术防护：光环清友方负面）
    /** 所有寰宇法则（纵列6，2026-08-27 新增）：全局更改类技能（服务器全局生效，无法单人隔离） */
    public static final List<String> GLOBAL_SKILLS = List.of(AURA_TIME, AURA_WEATHER, AE_INFINITE_CHANNEL);
    /** 所有魔法增幅（纵列0）：其余模组兼容技能（新生魔艺/铁魔法等），不作为任何前置 */
    public static final List<String> MAGIC_SKILLS = List.of(
            // 新生魔艺
            MANA_AMP, ARS_MANA_REGEN,
            // 铁魔法
            IRON_MANA_AMP, IRON_MANA_REGEN, IRON_CAST_TIME, IRON_COOLDOWN,
            IRON_FIRE, IRON_ICE, IRON_LIGHTNING, IRON_HOLY, IRON_ENDER,
            IRON_BLOOD, IRON_EVOCATION, IRON_NATURE, IRON_ELDRITCH,
            // Goety（诡术，2026-09-20）：通用强度/通用灵魂折扣 + 9 流派精通
            GOETY_POTENCY, GOETY_SOUL_DISCOUNT,
            GOETY_ABYSS, GOETY_FROST, GOETY_GEOMANCY, GOETY_NECROMANCY, GOETY_NETHER,
            GOETY_STORM, GOETY_VOID, GOETY_WILD, GOETY_WIND,
            // 奥术防护（2026-09-14）：自有魔法防护，不受模组依赖限制
            ARCANE_BULWARK, ARCANE_AMP, SPELL_DAMPEN);
    /** 所有机械共鸣（纵列6）：机械之星在最上，其余共鸣技能在前置原技能下方；
     *  2026-09-08 追加 4 个木棍工具区块技能（无机械之星前置） */
    public static final List<String> MACHINE_SKILLS = List.of(
            MACHINE_STAR,
            MACHINE_LOOT_BOMB, MACHINE_UNBREAKABLE, MACHINE_BLOCK_DROP,
            MACHINE_XP_GAIN, MACHINE_SPAWN_EGG, MACHINE_MOB_HEAD, MACHINE_AUTO_SMELT,
            MACHINE_ZONE_PLACE, MACHINE_ZONE_EXCAVATE, MACHINE_ZONE_ATTACK, MACHINE_ZONE_PROTECT);
            // ⚠️ 2026-09-30：MACHINE_MOB_DROP 已合并进 MACHINE_LOOT_BOMB，从列表移除（常量保留供退点）
    /** 所有子枫的馈赠（纵列7，2026-08-25 新增）：时间/移动/飞行/挖掘/击杀洗礼 + 增幅 */
    public static final List<String> GIFT_SKILLS = List.of(
            GIFT_TIME_BAPTISM, GIFT_TIME_STORM, GIFT_TIME_FLOOD,
            GIFT_MOVE_BAPTISM, GIFT_MOVE_AMP,
            GIFT_FLY_BAPTISM, GIFT_FLY_AMP,
            GIFT_MINE_BAPTISM, GIFT_MINE_AMP,
            GIFT_KILL_BAPTISM, GIFT_KILL_AMP);

    // ============ 木棍工具层（TOOL，纵列9，2026-09-08 新增：工具占位——不算技能不耗点） ============
    /** 工具占位 id（技能树第10列显示；不参与学习/技能点体系，只承载木棍工具总开关 + 模式） */
    public static final String STICK_TOOL = "stick_tool";
    /** 工具模式：BIND=潜行右键绑容器（挪移/搬运） */
    public static final int STICK_MODE_BIND = 0;
    /** 工具模式：RANGE=左键框选磁铁屏蔽区（吸星大法） */
    public static final int STICK_MODE_RANGE = 1;
    /** 工具模式：ZONE_PLACE=框选放置区（机械共鸣·选区放置，2026-09-08） */
    public static final int STICK_MODE_ZONE_PLACE = 2;
    /** 工具模式：ZONE_EXCAVATE=框选挖掘区（机械共鸣·选区挖掘，2026-09-08） */
    public static final int STICK_MODE_ZONE_EXCAVATE = 3;
    /** 工具模式：ZONE_ATTACK=框选攻击区（机械共鸣·选区攻击，2026-09-08） */
    public static final int STICK_MODE_ZONE_ATTACK = 4;
    /** 工具模式：ZONE_PROTECT=框选防护区（杀戮光环·防护选区，2026-09-08） */
    public static final int STICK_MODE_ZONE_PROTECT = 5;
    /** 工具占位列（第10列） */
    public static final List<String> TOOL_SKILLS = List.of(STICK_TOOL);

    /** 是否工具占位卡（木棍工具层） */
    public static boolean isStickTool(String skillId) {
        return STICK_TOOL.equals(skillId);
    }

    /** 工具模式总数（BIND/RANGE/放置/挖掘/攻击/防护，2026-09-08 扩展为 6） */
    public static int stickModeCount() {
        return 6;
    }

    /** 木棍工具模式 → 解锁该模式的技能 ID（返回 null=不依赖技能的常驻模式如 BIND/RANGE 由各自绑技能判定） */
    public static String skillForStickMode(int mode) {
        return switch (mode) {
            case STICK_MODE_ZONE_PLACE -> MACHINE_ZONE_PLACE;
            case STICK_MODE_ZONE_EXCAVATE -> MACHINE_ZONE_EXCAVATE;
            case STICK_MODE_ZONE_ATTACK -> MACHINE_ZONE_ATTACK;
            case STICK_MODE_ZONE_PROTECT -> MACHINE_ZONE_PROTECT;
            default -> null;
        };
    }

    /** 是否木棍工具·区块类技能（放置/挖掘/攻击/防护；各自解锁一个工具模式，2026-09-08） */
    public static boolean isStickZoneSkill(String skillId) {
        return MACHINE_ZONE_PLACE.equals(skillId) || MACHINE_ZONE_EXCAVATE.equals(skillId)
                || MACHINE_ZONE_ATTACK.equals(skillId) || MACHINE_ZONE_PROTECT.equals(skillId);
    }

    /** 区块技能 → 对应的木棍工具模式 */
    public static int stickModeOfSkill(String skillId) {
        if (MACHINE_ZONE_PLACE.equals(skillId)) return STICK_MODE_ZONE_PLACE;
        if (MACHINE_ZONE_EXCAVATE.equals(skillId)) return STICK_MODE_ZONE_EXCAVATE;
        if (MACHINE_ZONE_ATTACK.equals(skillId)) return STICK_MODE_ZONE_ATTACK;
        if (MACHINE_ZONE_PROTECT.equals(skillId)) return STICK_MODE_ZONE_PROTECT;
        return -1;
    }

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
        addAll(FISHING_SKILLS);
    }};

    /**
     * 技能类型映射（2026-08-27 性能优化，2026-09-11 与 1.20.1 同步）：
     * 原 getType 用 9 次 List.contains 线性查找，而 {@code SkillTreeScreen} 每帧对
     * 全部 ~122 个技能按钮各调一次 → 每帧上千次字符串线性扫描。改为一次性 HashMap O(1)。
     */
    private static final java.util.Map<String, SkillType> TYPE_MAP = buildTypeMap();

    private static java.util.Map<String, SkillType> buildTypeMap() {
        java.util.Map<String, SkillType> map = new java.util.HashMap<>();
        for (String s : MAGIC_SKILLS) map.put(s, SkillType.MAGIC);
        for (String s : MACHINE_SKILLS) map.put(s, SkillType.MACHINE);
        for (String s : GIFT_SKILLS) map.put(s, SkillType.GIFT);
        for (String s : GLOBAL_SKILLS) map.put(s, SkillType.GLOBAL);
        for (String s : BASE_SKILLS) map.put(s, SkillType.BASE);
        for (String s : AMPLIFY_SKILLS) map.put(s, SkillType.AMPLIFY);
        for (String s : ULTIMATE_SKILLS) map.put(s, SkillType.ULTIMATE);
        for (String s : SPECIAL_SKILLS) map.put(s, SkillType.SPECIAL);
        for (String s : AURA_SKILLS) map.put(s, SkillType.AURA);
        for (String s : FISHING_SKILLS) map.put(s, SkillType.FISHING);
        // ⚠️ LEGACY（2026-09-30 已合并进战利品大爆发）：必须保留类型映射，
        //    否则 getType 回退到 BASE → 退点按"基础线性消耗"算，金额完全错。
        map.put(MOB_DROP, SkillType.ULTIMATE);
        map.put(MACHINE_MOB_DROP, SkillType.MACHINE);
        return map;
    }

    public static SkillType getType(String skillId) {
        return TYPE_MAP.getOrDefault(skillId, SkillType.BASE);
    }

    /**
     * 技能是否需要开关（2026-08-13 需求）：全部技能都可开关（含时之环/晴空环，
     * 用户要求保留开关快捷键——关闭即停止锁定时间/天气）。
     */
    public static boolean isTogglable(String skillId) {
        return true;
    }

    /** 是否为子枫的搬运术（CONTAINER_HAUL，2026-09-07）：打开容器瞬间/按触发键把容器物品搬进绑定容器 */
    public static boolean isContainerHaul(String skillId) {
        return CONTAINER_HAUL.equals(skillId);
    }

    /**
     * 是否为「容器绑定技能」（2026-09-07 架构调整：绑定容器独立为子功能）。
     * 玩家学习任一容器绑定技能后即可用木棍潜行右键绑定容器，绑定数据存在玩家存档
     * （LootVacuum* 字段），供所有容器技能共享——不受单一技能开关影响。
     */
    public static boolean isContainerBindSkill(String skillId) {
        return AURA_LOOT_VACUUM.equals(skillId) || CONTAINER_HAUL.equals(skillId);
    }

    /**
     * 是否有「生物敌我目标模式」（2026-09-07）：模式切换的敌我过滤（0敌对/1友好/2所有）
     * 只属于杀戮光环三兄弟（伤害/速度/治愈）——它们是按生物目标过滤的攻击/治疗光环。
     * ⚠️ 其他光环/容器技能（磁力/锁定/强化/虚空/挪移/搬运术等）没有敌我目标概念，
     * 各自模式（如搬运术自动/手动）由技能自身解释，不走本方法。
     */
    public static boolean isAuraTargetSkill(String skillId) {
        return AURA_DAMAGE.equals(skillId) || AURA_SPEED.equals(skillId) || AURA_HEAL.equals(skillId);
    }

    /**
     * 是否需要「功能触发键」（2026-09-07 第三类快捷键）：主动技在场景内按一下触发一次。
     * 搬运术手动 / 闪现 / 机械共鸣·选区放置、选区挖掘（2026-09-08 手持物品按触发键执行一次）。
     */
    public static boolean isTriggerBindable(String skillId) {
        return CONTAINER_HAUL.equals(skillId) || BLINK.equals(skillId)
                || MACHINE_ZONE_PLACE.equals(skillId) || MACHINE_ZONE_EXCAVATE.equals(skillId);
    }

    // ==================== 第一代快捷键能力注册表（2026-09-07 规范 v1.0） ====================
    // 每个技能在技能树按钮右侧最多三键位槽：
    //   ① 开关键（所有技能都有）
    //   ② 模式/等级键（有模式循环 或 可调等级 才显示）
    //   ③ 功能触发键（hasTrigger 才显示）
    // UI 显隐、按键处理、模式循环全部读下面这组统一方法 —— 禁止散落特判。

    // ============ 战利品大爆发：模式常量（★ 2026-09-30）============
    //   复用 auraTargetModes 存模式（与光环/搬运术同一机制，clamp 上限见 PlayerSkillRecord.setAuraTargetMode）
    /** 模式 0：全模式 —— 所有掉落都参与爆发（旧行为） */
    public static final int LOOT_MODE_ALL = 0;
    /** 模式 1：仅堆叠物品（maxStackSize > 1） */
    public static final int LOOT_MODE_STACKABLE = 1;
    /** 模式 2：仅不堆叠物品（maxStackSize == 1，即装备/工具类） */
    public static final int LOOT_MODE_UNSTACKABLE = 2;
    /** 模式 3：自定义黑名单 —— 命中黑名单（物品 id 或其标签）的掉落不参与 */
    public static final int LOOT_MODE_BLACKLIST = 3;

    /**
     * 是否为「已移除/已合并」的历史技能（★ 2026-09-30）。
     * <p>这些技能不再出现在技能树里，但旧存档可能仍有点数 —— 由
     * {@code PlayerSkillRecord} 的一次性迁移按 100% 退还。
     */
    public static boolean isRemovedSkill(String skillId) {
        return MOB_DROP.equals(skillId) || MACHINE_MOB_DROP.equals(skillId);
    }

    /**
     * 是否有「子2 模式循环」（2026-09-07 规范）：
     * 敌我目标（杀戮三兄弟 3 态）/ 天气（晴空环 3 态）/ 搬运（搬运术 2 态）/
     * 工具（木棍 BIND/RANGE 2 态，2026-09-08）/ 战利品大爆发（4 态，2026-09-30）。
     * 纯可调等级技能（无模式语义）返回 false —— 它们的子2是等级循环，见 {@link #hasLevelCycle}。
     */
    public static boolean hasModeCycle(String skillId) {
        return isAuraTargetSkill(skillId)        // 敌我目标 3 态
                || AURA_WEATHER.equals(skillId)  // 晴空环天气 3 态
                || isContainerHaul(skillId)      // 搬运术自动/手动 2 态
                || isStickTool(skillId)          // 木棍工具 BIND/RANGE 2 态（2026-09-08）
                || LOOT_BOMB.equals(skillId);    // 战利品大爆发 4 态（2026-09-30）
    }

    /** 子2 模式循环的态数（敌我/天气=3，搬运=2，工具=stickModeCount，战利品大爆发=4）；无模式返回 0 */
    public static int getModeCount(String skillId) {
        if (isAuraTargetSkill(skillId) || AURA_WEATHER.equals(skillId)) {
            return 3;
        }
        if (isContainerHaul(skillId)) {
            return 2;
        }
        if (LOOT_BOMB.equals(skillId)) {
            return 4; // ★ 2026-09-30：全 / 仅堆叠 / 仅不堆叠 / 黑名单
        }
        if (isStickTool(skillId)) {
            return stickModeCount();
        }
        return 0;
    }

    /** 是否有「子2 等级循环」：可调等级（上限>1，含基础/增幅/魔法/多级终极/汲灵等） */
    public static boolean hasLevelCycle(String skillId) {
        return getMaxPoints(skillId) > 1;
    }

    /** 子2 按键存在 = 有模式循环 或 可调等级（任一即显示第二槽） */
    public static boolean hasSub2(String skillId) {
        return hasModeCycle(skillId) || hasLevelCycle(skillId);
    }

    /** 子3 触发键允许响应的 GUI 场景：true = 任意屏幕（含容器 GUI）可触发；false = 仅无屏幕（正常游戏） */
    public static boolean canTriggerInScreen(String skillId) {
        return isContainerHaul(skillId); // 搬运术：容器 GUI 打开时也可触发；闪现：正常游戏内触发
    }

    /** 杀戮光环：每项上限 */
    public static int getAuraMaxPoints(String skillId) {
        return switch (skillId) {
            case AURA_DAMAGE -> 100; // 2026-09-14 等级压缩：原 1000
            case AURA_SPEED -> 20;
            case AURA_HEAL -> 50;
            case AURA_MAGNET -> 1;
            case AURA_LOCK -> 1; // 一次性解锁（1000 技能点）
            case AURA_EMPOWER -> 1; // 一次性解锁（1000 技能点）
            case AURA_VOID -> 1; // 一次性解锁（5000 技能点）
            case AURA_LOOT_VACUUM -> 1; // 一次性解锁（10 技能点）
            case CONTAINER_HAUL -> 1; // 子枫的搬运术：一次性解锁（10 技能点，2026-09-07）
            case AURA_XP -> 100; // 汲灵之环：上限 100 级（2026-09-06）
            case PURIFY_FIELD -> 100; // 净化领域：上限 100 级（2026-09-14 奥术防护）
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
            case FISHING -> getFishingMaxPoints(skillId);
        };
    }

    /**
     * 垂钓：等级上限（2026-10-03）。
     * <p>急流垂钓 20（−80% 等待）/ 海神眷顾 25 / 渔获满仓 50；其余均一次性解锁。
     */
    public static int getFishingMaxPoints(String skillId) {
        return switch (skillId) {
            case FISH_HASTE -> 20;
            case FISH_FORTUNE -> 20; // ★ 20 级 = +20 幸运 → 鱼 65 / 宝藏 45 / 垃圾 0（鱼 59%）；再高会把鱼权重压没
            case FISH_BOUNTY -> 50;
            case FISH_XP -> 25;
            default -> 1; // 全自动垂钓/无界/现钓现炼/定点/渔获爆发：一次性
        };
    }

    /**
     * 垂钓：第 n 级消耗（2026-10-03）。
     *
     * <p>曲线对齐现有定价（基础列 0.6n²／增幅列 2.4n²）：
     * <ul>
     *   <li>急流垂钓（纯体感便利）→ 0.6n²，满级累计 ≈1,722</li>
     *   <li>海神眷顾（提升产出质量）→ 1.2n²，满级累计 ≈6,630</li>
     *   <li>渔获满仓（直接产出）→ 2.4n²（与增幅列同价），满级累计 ≈103,020</li>
     * </ul>
     * 一次性：全自动垂钓 3000（本包第一个真·全自动产出循环，定价高于其余便利项）。
     *
     * @param currentLevel 当前已学等级（第 0 级 = 学第 1 级的消耗）
     */
    public static long getFishingCost(String skillId, int currentLevel) {
        final double n = currentLevel + 1.0;
        return switch (skillId) {
            case FISH_HASTE, FISH_XP -> Math.max(1L, Math.round(0.6 * n * n));
            case FISH_FORTUNE -> Math.max(1L, Math.round(1.2 * n * n));
            case FISH_BOUNTY -> Math.max(1L, Math.round(2.4 * n * n));
            case FISH_AUTO -> 3000L;                  // 全自动垂钓
            case FISH_OPEN_WATER -> 1000L;            // 无界垂钓
            case FISH_VACUUM -> 300L;                 // 定点渔获
            case FISH_SMELT -> 200L;                  // 现钓现炼
            case FISH_BOMB -> 500L;                   // 渔获爆发
            default -> 1L;
        };
    }

    /** 机械共鸣：全部单级解锁（上限 1） */
    public static int getMachineMaxPoints(String skillId) {
        return 1;
    }

    /** 子枫的馈赠：洗礼 1-9 级 / 增幅 1-9 级 / 时间系列单级 */
    public static int getGiftMaxPoints(String skillId) {
        return switch (skillId) {
            case GIFT_MOVE_BAPTISM, GIFT_FLY_BAPTISM, GIFT_MINE_BAPTISM, GIFT_KILL_BAPTISM -> 9; // 洗礼：9 级（2026-09-14 从 5 级上调，补齐消耗过渡）
            case GIFT_MOVE_AMP, GIFT_FLY_AMP, GIFT_MINE_AMP, GIFT_KILL_AMP -> 9;               // 增幅：9 级（2026-09-14 从 5 级上调，与洗礼对齐）
            default -> 1; // 时间洗礼/风暴/洪流：单级
        };
    }

    /**
     * 子枫的馈赠激活/升级消耗（技能点）：
     * 时间系列 0（按游戏时长激活）；移动/飞行/挖掘/击杀洗礼 10/30/100/300/1千/3千/1万/3万/10万（每级约 ×3，共 14.4 万）；
     * 增幅 1000 × 1.3^等级（指数增长 30%，9 级共约 2.67 万）。
     * @param currentLevel 当前已学等级（0=学第1级）
     */
    public static long getGiftCost(String skillId, int currentLevel) {
        return switch (skillId) {
            // 洗礼：2026-09-14 由 10/1000/10000/50000/100000 改为 9 级平滑曲线，
            // 原因：原第 1 级 10 → 第 2 级 1000 是 100 倍断崖，缺少中间过渡。
            case GIFT_MOVE_BAPTISM, GIFT_FLY_BAPTISM, GIFT_MINE_BAPTISM, GIFT_KILL_BAPTISM ->
                    switch (Math.max(0, Math.min(8, currentLevel))) {
                        case 0 -> 10L;
                        case 1 -> 30L;
                        case 2 -> 100L;
                        case 3 -> 300L;
                        case 4 -> 1000L;
                        case 5 -> 3000L;
                        case 6 -> 10000L;
                        case 7 -> 30000L;
                        default -> 100000L;
                    };
            case GIFT_MOVE_AMP, GIFT_FLY_AMP, GIFT_MINE_AMP, GIFT_KILL_AMP -> // 增幅：1000 × 1.3^等级（30% 指数，曲线本身平滑，仅上调上限）
                    Math.round(1000 * Math.pow(1.3, Math.max(0, Math.min(8, currentLevel))));
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
     * 移动/飞行/挖掘/击杀洗礼的每次触发需求（等级 1-9，随等级递减，满级最快）：
     * 移动/飞行单位 = 米（原版统计是 cm，需 ×100 换算）；挖掘单位 = 方块数；击杀单位 = 个。
     * <p>2026-09-14：由 5 档（1000/500/100/50/10）拆为 9 档，与 9 级消耗曲线对齐，满级仍为 10（强度不变）。
     */
    public static long getGiftDistanceRequirement(String skillId, int level) {
        return switch (Math.max(1, Math.min(9, level))) {
            case 1 -> 1000L; // 1000 米 / 1000 块 / 1000 杀
            case 2 -> 700L;
            case 3 -> 500L;
            case 4 -> 300L;
            case 5 -> 200L;
            case 6 -> 100L;
            case 7 -> 50L;
            case 8 -> 25L;
            default -> 10L;  // 9 级：10 米 / 10 块 / 10 杀
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
     * 判断是否为「时间系列」馈赠技能（★ 2026-09-20 新增）。
     *
     * <p>时间洗礼 / 时间风暴 / 时间洪流这三个技能的「消耗」是<b>游戏时长</b>而非技能点
     * （{@link #getGiftCost} 对它们返回 0），因此界面上不能按技能点消耗来显示，
     * 而要改成显示激活门槛（需1小时 / 需5小时 / 需10小时），否则玩家只看到空白。
     *
     * <p>把三个 ID 收敛到这里：此前它们在 {@code SkillTreeScreen}（写死三元判断）、
     * {@code GiftEvents}（TIME_SKILLS 常量）等处各写了一份。
     */
    public static boolean isGiftTimeSkill(String skillId) {
        return GIFT_TIME_BAPTISM.equals(skillId) || GIFT_TIME_STORM.equals(skillId)
                || GIFT_TIME_FLOOD.equals(skillId);
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
        // 木棍工具区块（2026-09-08）：固定 50 点
        if (MACHINE_ZONE_PLACE.equals(skillId) || MACHINE_ZONE_EXCAVATE.equals(skillId)
                || MACHINE_ZONE_ATTACK.equals(skillId) || MACHINE_ZONE_PROTECT.equals(skillId)) {
            return 50L;
        }
        return Config.MACHINE_RESONANCE_COST.get();
    }

    /**
     * 魔法增幅：每项上限。
     *
     * <p><b>⚠️ 2026-09-20 修正两处「等级上限高于实际封顶点」造成的无效等级</b>
     * （用户报「看着等级很高，但是几十级之后再升就无效」）：
     * <ul>
     *   <li><b>吟唱/冷却 100 → 80</b>：Goety 侧减量类封顶 {@code GoetyCompat.DISCOUNT_CAP} = 0.8，
     *       而每级只贡献 0.01（这两个技能<b>不受</b>等级压缩）→ 80 级正好抵到封顶，
     *       原 100 级的后 20 级毫无收益。</li>
     *   <li><b>灵魂折扣 80 → 8</b>：该技能受等级压缩（MAGIC 列默认压缩 ×10），
     *       生效等级 = 已学等级 × 10，故 8 级时生效等级已到 80 → ×0.01 = 0.8 封顶；
     *       原写 80 级时后 72 级毫无收益。</li>
     * </ul>
     *
     * <p>⚠️ Goety 各项的「每级实际效果」= 显示等级 × 10（受压缩）× 系数，
     * 与技能描述的读数口径不同，描述里已按实际值写明。
     */
    public static int getMagicMaxPoints(String skillId) {
        return switch (skillId) {
            // 未压缩（原本就是 100 级）；80 级即 Goety 侧减量封顶点
            case IRON_CAST_TIME, IRON_COOLDOWN -> 80;
            // 奥术防护（2026-09-14）
            case ARCANE_BULWARK -> 100;                // 等级压缩：原 1000
            case ARCANE_AMP, SPELL_DAMPEN -> 50;       // 等级压缩：原 500
            case MANA_AMP, ARS_MANA_REGEN, IRON_MANA_AMP, IRON_MANA_REGEN,
                    IRON_FIRE, IRON_ICE, IRON_LIGHTNING, IRON_HOLY, IRON_ENDER,
                    IRON_BLOOD, IRON_EVOCATION, IRON_NATURE, IRON_ELDRITCH -> 100; // 等级压缩：原 1000
            // Goety（2026-09-20）：
            //   · 通用强度 100 级：受压缩 ×10 → 实际每级 +10 强度（100 级 +1000，属性上限 2048 不溢出）
            case GOETY_POTENCY -> 100;
            //   · 灵魂折扣 8 级：受压缩 ×10 → 实际每级 -10%%，8 级（生效等级 80）正好抵到 0.8 封顶
            case GOETY_SOUL_DISCOUNT -> 8;
            //   · 9 流派精通 80 级：强度部分线性到 80 级（每级 +10）；折扣部分 8 级即封顶
            case GOETY_ABYSS, GOETY_FROST, GOETY_GEOMANCY,
                    GOETY_NECROMANCY, GOETY_NETHER, GOETY_STORM, GOETY_VOID,
                    GOETY_WILD, GOETY_WIND -> 80;
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
        final double n = currentLevel + 1.0;
        if (IRON_CAST_TIME.equals(skillId) || IRON_COOLDOWN.equals(skillId)) {
            return (long) (n * 5); // 未压缩（上限 100）：保持线性 +5
        }
        if (ARCANE_AMP.equals(skillId) || SPELL_DAMPEN.equals(skillId)) {
            return Math.max(1L, Math.round(2.4 * n * n)); // 等级压缩：原 500 → 50
        }
        return Math.max(1L, Math.round(0.6 * n * n));     // 等级压缩：原 1000 → 100
    }

    /**
     * 终极节点等级上限：默认单次解锁（1）；多级终极节点（节点类）各自上限：
     * 村庄英雄 10 / 接触距离 50 / 发光 1 / 战利品大爆发 1000 / 工具不毁 1（点亮即 100%，故一次性）/ 方块掉落 10 / 经验 50 / 刷怪蛋 5 / 头颅 5
     * <p>⚠️ MOB_DROP 为 LEGACY（已合并），保留其 10 级仅为旧存档退点计算。
     */
    public static int getUltimateMaxPoints(String skillId) {
        return switch (skillId) {
            case AMP_ARMOR -> 80; // 金身真解（2026-09-14 自增幅列移入）：上限 80 级，每级 +1% 免伤
            case VILLAGE_HERO -> 10;
            case REACH -> 50;
            case ULT_SWEEP, ULT_KB_RESIST -> 10; // 横扫范围/击退抗性：上限 10 级
            case GLOW -> 1;
            case LOOT_BOMB -> 1000; // ★ 2026-09-30：合并「猎魂丰收」后由 100 升到 1000 级（倍率=1+等级）
            case UNBREAKABLE -> 1; // 万载不磨（2026-09-14）：点亮即 100% 耐久减免，2~5 级无额外效果，故降为一次性点亮
            case XP_GAIN -> 50; // 经验飞涨：上限 50 级（2026-09-06，原 10 级）
            case MOB_DROP, BLOCK_DROP -> 10;
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
        // 横扫范围/击退抗性/金身真解：线性消耗（每级 2 点，下一级 +2：2,4,6,8...）
        if (ULT_SWEEP.equals(skillId) || ULT_KB_RESIST.equals(skillId) || AMP_ARMOR.equals(skillId)) {
            return (double) (currentLevel + 1) * 2.0;
        }
        // ★ 2026-09-30：战利品大爆发（1000 级）单独用 **1.01^等级**
        //    —— 全局的 1.1^等级 在 1000 级会变成天文数字（1.1^1000）。
        //    base 10 → 100 级≈27、500 级≈1436、1000 级≈208,600（累计约 2100 万）
        //    ⚠️ 倍率=1+等级不缩小（用户明确）：代价靠消耗曲线拉高。
        if (LOOT_BOMB.equals(skillId)) {
            return 10.0 * Math.pow(1.01, currentLevel);
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
        if (CONTAINER_HAUL.equals(skillId)) {
            return 10L; // 子枫的搬运术：固定 10 技能点一次性解锁（2026-09-07）
        }
        // ⚠️ 汲灵之环（AURA_XP）无特判：走统一光环指数增长 auraBaseCost×1.05^等级（用户 2026-09-06 要求）
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
            case CONTAINER_HAUL -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case AURA_XP -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
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
            case GLUTTONY -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case BLINK -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            case MACHINE_ZONE_PLACE, MACHINE_ZONE_EXCAVATE, MACHINE_ZONE_ATTACK, MACHINE_ZONE_PROTECT -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name"; // 木棍工具区块技能（2026-09-08）
            case STICK_TOOL -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name"; // 木棍工具占位（2026-09-08）
            // 奥术防护（2026-09-14）：name/desc 走统一 key 后缀，新增技能自动兼容
            case ARCANE_BULWARK, ARCANE_AMP, SPELL_DAMPEN, ARCANE_ADAPT, SPELL_REFLECT,
                    SPELL_PURGE, MANA_SIPHON, PURIFY_FIELD, SPELLBREAK_BLADE, ULT_ARCANE_BODY ->
                    "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
            default -> "skill.zifeng_s_custom_skill_tree." + skillId + ".name";
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
            case CONTAINER_HAUL -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case AURA_XP -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
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
            case GLUTTONY -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case BLINK -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
            case MACHINE_ZONE_PLACE, MACHINE_ZONE_EXCAVATE, MACHINE_ZONE_ATTACK, MACHINE_ZONE_PROTECT -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc"; // 木棍工具区块技能（2026-09-08）
            case STICK_TOOL -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc"; // 木棍工具占位（2026-09-08）
            // 奥术防护（2026-09-14）：desc 同样走统一 key 后缀，新增技能自动兼容
            default -> "skill.zifeng_s_custom_skill_tree." + skillId + ".desc";
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
                    fmt(org.zifeng.skilltree.Config.BODY_HP_PER_POINT.get() * LEVEL_COMPRESSION));
            case BLADE -> net.minecraft.network.chat.Component.translatable(getDescription(skillId),
                    fmt(org.zifeng.skilltree.Config.BLADE_DAMAGE_PER_POINT.get() * LEVEL_COMPRESSION));
            case ATTACK_SPEED -> net.minecraft.network.chat.Component.translatable(getDescription(skillId),
                    fmt(org.zifeng.skilltree.Config.ATTACK_SPEED_PER_POINT.get() * LEVEL_COMPRESSION));
            case MINING -> net.minecraft.network.chat.Component.translatable(getDescription(skillId),
                    fmt(org.zifeng.skilltree.Config.MINING_SPEED_PER_POINT.get() * LEVEL_COMPRESSION));
            case MOVE -> net.minecraft.network.chat.Component.translatable(getDescription(skillId),
                    fmt(org.zifeng.skilltree.Config.MOVE_SPEED_PER_POINT.get() * LEVEL_COMPRESSION));
            case LUCK -> net.minecraft.network.chat.Component.translatable(getDescription(skillId),
                    fmt(org.zifeng.skilltree.Config.LUCK_PER_POINT.get() * LEVEL_COMPRESSION));
            case JUMP -> net.minecraft.network.chat.Component.translatable(getDescription(skillId),
                    fmt(org.zifeng.skilltree.Config.JUMP_PER_POINT.get() * LEVEL_COMPRESSION));
            case FLY -> net.minecraft.network.chat.Component.translatable(getDescription(skillId),
                    fmt(org.zifeng.skilltree.Config.FLY_SPEED_PER_POINT.get() * LEVEL_COMPRESSION));
            case SWIM -> net.minecraft.network.chat.Component.translatable(getDescription(skillId),
                    fmt(org.zifeng.skilltree.Config.SWIM_SPEED_PER_POINT.get() * LEVEL_COMPRESSION));
            // ===== 增幅属性（每点百分比走 Config） =====
            case AMP_HP -> net.minecraft.network.chat.Component.translatable(getDescription(skillId),
                    fmt(org.zifeng.skilltree.Config.AMP_HP_PER_POINT.get() * LEVEL_COMPRESSION * 100) + "%");
            case AMP_TOUGH -> net.minecraft.network.chat.Component.translatable(getDescription(skillId),
                    fmt(org.zifeng.skilltree.Config.AMP_TOUGH_PER_POINT.get() * LEVEL_COMPRESSION * 100) + "%");
            case AMP_LUCK -> net.minecraft.network.chat.Component.translatable(getDescription(skillId),
                    fmt(org.zifeng.skilltree.Config.AMP_LUCK_PER_POINT.get() * LEVEL_COMPRESSION * 100) + "%");
            case AMP_DAMAGE -> net.minecraft.network.chat.Component.translatable(getDescription(skillId),
                    fmt(org.zifeng.skilltree.Config.AMP_DAMAGE_PER_POINT.get() * LEVEL_COMPRESSION * 100) + "%");
            case AMP_ATTACK_SPEED -> net.minecraft.network.chat.Component.translatable(getDescription(skillId),
                    fmt(org.zifeng.skilltree.Config.AMP_ATTACK_SPEED_PER_POINT.get() * LEVEL_COMPRESSION * 100) + "%");
            case AMP_MINING -> net.minecraft.network.chat.Component.translatable(getDescription(skillId),
                    fmt(org.zifeng.skilltree.Config.AMP_MINING_PER_POINT.get() * LEVEL_COMPRESSION * 100) + "%");
            case AMP_MOVE -> net.minecraft.network.chat.Component.translatable(getDescription(skillId),
                    fmt(org.zifeng.skilltree.Config.AMP_MOVE_PER_POINT.get() * LEVEL_COMPRESSION * 100) + "%");
            case AMP_JUMP -> net.minecraft.network.chat.Component.translatable(getDescription(skillId),
                    fmt(org.zifeng.skilltree.Config.AMP_JUMP_PER_POINT.get() * LEVEL_COMPRESSION * 100) + "%");
            case AMP_FLY -> net.minecraft.network.chat.Component.translatable(getDescription(skillId),
                    fmt(org.zifeng.skilltree.Config.AMP_FLY_PER_POINT.get() * LEVEL_COMPRESSION * 100) + "%");
            case AMP_SWIM -> net.minecraft.network.chat.Component.translatable(getDescription(skillId),
                    fmt(org.zifeng.skilltree.Config.AMP_SWIM_PER_POINT.get() * LEVEL_COMPRESSION * 100) + "%");
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
            // 2026-09-14：前置由「铁壁金身 BODY」改为「磐石之躯 TOUGH」（BODY 已删除）
            case ULT_BLOOD -> List.of(Map.entry(TOUGH, 50), Map.entry(BLADE, 50));
            // 2026-09-14：金身真解上限为 80，前置要求同步下调（原 500 会永远无法满足）
            case ULT_GOLDEN -> List.of(Map.entry(TOUGH, 50), Map.entry(AMP_ARMOR, 80));
            case ULT_MASTER -> List.of(Map.entry(ULT_BLOOD, 1), Map.entry(ULT_GOLDEN, 1), Map.entry(ULT_REVIVE, 1), Map.entry(ULT_REAPER, 1), Map.entry(ULT_ARCANE_BODY, 1));
            // 奥术神体（2026-09-14 奥术防护）：需先点出奥术壁垒与真解，形成“壁垒→真解→神体”链路
            case ULT_ARCANE_BODY -> List.of(Map.entry(ARCANE_BULWARK, 10), Map.entry(ARCANE_AMP, 5));
            case ULT_REVIVE -> List.of(Map.entry(LIFESTEAL, 50), Map.entry(CRIT, 50));
            case ULT_REAPER -> List.of(Map.entry(ARMOR_PEN, 50), Map.entry(BLADE, 50));
            case ULT_VOID_BODY -> List.of(Map.entry(ULT_MASTER, 1)); // 前置：全能精通
            case AURA_DAMAGE, AURA_SPEED -> List.of(Map.entry(BLADE, 10), Map.entry(ATTACK_SPEED, 10)); // 锋刃/疾攻（等级压缩后 10）
            case AURA_EMPOWER -> List.of(Map.entry(AURA_DAMAGE, 5)); // 杀戮伤害（等级压缩后 5）
            case AURA_VOID -> List.of(Map.entry(AURA_DAMAGE, 10)); // 杀戮伤害（等级压缩后 10）
            // 机械共鸣：机械之星无前置；共鸣技能需 机械之星 + 对应原技能已学
            case MACHINE_LOOT_BOMB -> List.of(Map.entry(MACHINE_STAR, 1), Map.entry(LOOT_BOMB, 1));
            case MACHINE_UNBREAKABLE -> List.of(Map.entry(MACHINE_STAR, 1), Map.entry(UNBREAKABLE, 1));
            case MACHINE_MOB_DROP -> List.of(Map.entry(MACHINE_STAR, 1), Map.entry(MOB_DROP, 1));
            case MACHINE_BLOCK_DROP -> List.of(Map.entry(MACHINE_STAR, 1), Map.entry(BLOCK_DROP, 1));
            case MACHINE_XP_GAIN -> List.of(Map.entry(MACHINE_STAR, 1), Map.entry(XP_GAIN, 1));
            case MACHINE_SPAWN_EGG -> List.of(Map.entry(MACHINE_STAR, 1), Map.entry(MOB_SPAWN_EGG, 1));
            case MACHINE_MOB_HEAD -> List.of(Map.entry(MACHINE_STAR, 1), Map.entry(MOB_HEAD, 1));
            case MACHINE_AUTO_SMELT -> List.of(Map.entry(MACHINE_STAR, 1), Map.entry(AUTO_SMELT, 1));
            // 木棍工具区块（2026-09-08，无机械之星前置）：攻击=杀戮伤害5级；防护=已学杀戮伤害；放置/挖掘无前置
            case MACHINE_ZONE_PLACE, MACHINE_ZONE_EXCAVATE -> List.of();
            case MACHINE_ZONE_ATTACK -> List.of(Map.entry(AURA_DAMAGE, 5));
            case MACHINE_ZONE_PROTECT -> List.of(Map.entry(AURA_DAMAGE, 1));
            // 铁砧附魔（2026-08-27）：附魔突破/超限附魔 前置 = 随机附魔
            case ENCHANT_BREAK, ENCHANT_OVER -> List.of(Map.entry(ENCHANT_RANDOM, 1));
            // 子枫的馈赠增幅：需对应洗礼已学（2026-08-25）
            case GIFT_MOVE_AMP -> List.of(Map.entry(GIFT_MOVE_BAPTISM, 1));
            case GIFT_FLY_AMP -> List.of(Map.entry(GIFT_FLY_BAPTISM, 1));
            case GIFT_MINE_AMP -> List.of(Map.entry(GIFT_MINE_BAPTISM, 1));
            case GIFT_KILL_AMP -> List.of(Map.entry(GIFT_KILL_BAPTISM, 1));
            // 垂钓（2026-10-03）：高收益通路必须已有对应现成技能，不重复给高倍率
            case FISH_SMELT -> List.of(Map.entry(AUTO_SMELT, 1));       // 现钓现炼 ← 自动熔炼术
            case FISH_VACUUM -> List.of(Map.entry(AURA_LOOT_VACUUM, 1));// 定点渔获 ← 子枫挪移术
            case FISH_BOMB -> List.of(Map.entry(LOOT_BOMB, 1));         // 渔获爆发 ← 战利品大爆发
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
            // ===== Goety（诡术，2026-09-20）：图标选“灵魂/深暗”系物品，与铁魔法（金属系）区分 =====
            //   注：全部选用 1.20.1 与 1.21.1 都存在的原版物品（避开 WIND_CHARGE/BREEZE_ROD 等 1.20.5+ 新增物）
            case GOETY_POTENCY -> Items.DRAGON_BREATH;         // 诡术法术强度：龙息（魔法精华）
            case GOETY_SOUL_DISCOUNT -> Items.SOUL_SAND;      // 诡术灵魂折扣：灵魂沙（Goety 以“灵魂能量”为施法资源）
            case GOETY_ABYSS -> Items.ECHO_SHARD;             // 深渊精通：回响碎片（深暗之域，呼应“深渊”）
            case GOETY_FROST -> Items.BLUE_ICE;               // 冰霜精通：蓝冰（与铁魔法冰霜的浮冰区分）
            case GOETY_GEOMANCY -> Items.POINTED_DRIPSTONE;   // 地卜精通：滴水石锥（岩石/大地）
            case GOETY_NECROMANCY -> Items.WITHER_ROSE;       // 死灵精通：凋灵玫瑰（死亡）
            case GOETY_NETHER -> Items.MAGMA_BLOCK;           // 下界精通：岩浆块（与铁魔法鲜血的下界疣区分）
            case GOETY_STORM -> Items.LIGHTNING_ROD;          // 风暴精通：避雷针（雷电；与时间风暴同图标，本项目允许此类重复）
            case GOETY_VOID -> Items.OBSIDIAN;                // 虚空精通：黑曜石（虚空/坚硬）
            case GOETY_WILD -> Items.VINE;                    // 荒野精通：藤蔓（荒野蔓生；与铁魔法自然的橡树苗区分）
            case GOETY_WIND -> Items.FIREWORK_ROCKET;         // 风精通：烟花火箭（气流/推进）
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
            case CONTAINER_HAUL -> Items.HOPPER;              // 子枫的搬运术：漏斗（把容器物品吸进绑定容器，2026-09-07）
            case AURA_XP -> Items.EXPERIENCE_BOTTLE;          // 汲灵之环：经验瓶（每级+1000/秒经验）
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
            case GLUTTONY -> Items.COOKED_BEEF;               // 暴食：牛排（大快朵颐秒吃）
            case BLINK -> Items.ENDER_PEARL;                  // 闪现：末影珍珠（瞬移）
            case MACHINE_ZONE_PLACE -> Items.BRICK;           // 选区放置：砖（摆放）
            case MACHINE_ZONE_EXCAVATE -> Items.DIAMOND_PICKAXE; // 选区挖掘：钻镐
            case MACHINE_ZONE_ATTACK -> Items.NETHERITE_SWORD;   // 选区攻击：下界合金剑
            case MACHINE_ZONE_PROTECT -> Items.SHIELD;        // 防护选区：盾牌
            case STICK_TOOL -> Items.STICK;                   // 木棍工具占位：木棍（2026-09-08）
            // ===== 奥术防护（2026-09-14）：图标与语义强关联（魔法/防护类原版物品） =====
            case ARCANE_BULWARK -> Items.ENCHANTING_TABLE;    // 奥术壁垒：附魔台（奥术）
            case ARCANE_AMP -> Items.ENCHANTED_BOOK;          // 奥术真解：附魔书（增幅）
            case SPELL_DAMPEN -> Items.COBWEB;                // 法术抑制：蜘蛛网（拦截弹射物）
            case ARCANE_ADAPT -> Items.TURTLE_HELMET;         // 适应之躯：海龟壳（逐步适应）
            case SPELL_REFLECT -> Items.PRISMARINE_SHARD;     // 法术反射：海晶碎片（棱镜折射）
            case SPELL_PURGE -> Items.MILK_BUCKET;            // 驱法破咒：牛奶桶（清除效果）
            case MANA_SIPHON -> Items.GHAST_TEAR;             // 法力虹吸：恶魂之泪（吸取）
            case PURIFY_FIELD -> Items.CONDUIT;               // 净化领域：潮涌核心（领域）
            case SPELLBREAK_BLADE -> Items.NETHERITE_AXE;     // 破法之刃：下界合金斧（破防）
            case ULT_ARCANE_BODY -> Items.NETHERITE_CHESTPLATE; // 奥术神体：下界合金胸甲（终极防护）
            // ===== 垂钓（2026-10-03）：图标全部用原版鱼类与钓具，语义强关联 =====
            case FISH_HASTE -> Items.COD;                     // 急流垂钓：鳕鱼
            case FISH_FORTUNE -> Items.TROPICAL_FISH;         // 海神眷顾：热带鱼
            case FISH_BOUNTY -> Items.SALMON;                 // 渔获满仓：鲑鱼
            case FISH_XP -> Items.EXPERIENCE_BOTTLE;          // 渔获经验：附魔之瓶
            case FISH_AUTO -> Items.FISHING_ROD;              // 全自动垂钓：钓鱼竿
            case FISH_OPEN_WATER -> Items.PUFFERFISH;         // 无界垂钓：河豚
            case FISH_SMELT -> Items.FURNACE;                 // 现钓现炼：熔炉
            case FISH_VACUUM -> Items.CHEST;                  // 定点渔获：箱子
            case FISH_BOMB -> Items.HEART_OF_THE_SEA;         // 渔获爆发：海洋之心
            default -> Items.BARRIER;
        };
    }
}
