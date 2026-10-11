package org.zifeng.skilltree.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;
import org.zifeng.skilltree.skill.Skills;

/**
 * 枪械技能（纵列11，★ 2026-10-11）的加成读取层。
 *
 * <h2>为什么单独一层</h2>
 * TaCZ 的换弹/开火逻辑在<b>双端</b>都会跑（客户端做预测、服务端做权威）。
 * 技能等级只存在<b>服务端</b>的 {@link PlayerSkillRecord} 里。
 *
 * <p>★ 2026-10-11 修正（用户实测"技能关闭后效果还在"）：
 * 早先对「无限子弹」做了两端各自的判定，而<b>加入/补弹的客户端是主动方</b> ——
 * 关掉技能后客户端仍按"无限"把弹匣填满，表现就是"技能关了但还在生效"。
 * <p>现在统一口径：<b>只认服务端玩家</b>（该判断本身就是 {@link ServerPlayer} 检查），
 * 客户端一律返回 0 / false（保守），因此客户端只做"服务端也会认可"的操作。
 *
 * <p>⚠️ 未装 TaCZ 时本类不会被调用（mixin 配置 required=false，目标类缺失自动跳过）。
 */
public final class TaczSkills {
    private TaczSkills() {
    }

    /** TaCZ 是否已安装（懒检查 + 缓存；未装时枪械技能不可学、mixin 也不会生效） */
    private static Boolean taczLoaded = null;

    public static boolean isTaczLoaded() {
        if (taczLoaded == null) {
            try {
                taczLoaded = net.minecraftforge.fml.ModList.get().isLoaded("tacz");
            } catch (Throwable ignored) {
                taczLoaded = false;
            }
        }
        return taczLoaded;
    }

    /** 一次性日志去重表。用并发集合：客户端线程与服务端线程都可能触发。 */
    private static final java.util.Set<String> LOGGED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger("zifeng_s_custom_skill_tree");

    /** 一次性日志（同一 key 只打印第一次，避免刷屏） */
    private static void logOnce(String key, String message) {
        if (LOGGED.add(key)) {
            LOG.info(message);
        }
    }

    /**
     * mixin 生效自证（★ 2026-10-11 诊断）。
     *
     * <p>为什么需要它：mixin 用 {@code require = 0}（宁可静默失效也不崩游戏），
     * 注入点没匹配上时<b>没有任何日志</b>。这行日志能立刻区分：
     * <ul>
     *   <li>日志出现 → 该 injector 已匹配，问题在取值/数值；</li>
     *   <li>日志不出现 → 该 injector 没匹配（配置或注入点问题），改数值无用。</li>
     * </ul>
     *
     * @param which 注入点标识（区分三个 mixin，便于定位）
     */
    public static void logActiveOnce(String which) {
        logOnce("mixin:" + which, "[zifeng] TaCZ mixin 注入成功: " + which);
    }

    /**
     * 取该射击者的枪械技能「生效等级」（未学 / 已关闭 / 非玩家 → 0）。
     *
     * <p>★ 2026-10-11 关键修复（用户实测"换弹速度不生效"）：
     * TaCZ 的换弹倒计时（{@code ModernKineticGunItem.defaultTickReload}）、
     * 弹匣容量（{@code getNeededAmmoAmount} / {@code putAmmoInMagazine}）和 HUD
     * <b>都同时在客户端计算</b>（客户端做预测与显示），而那时的 {@code shooter} 是
     * {@code LocalPlayer}。早先这里对非 {@code ServerPlayer} 一律返回 0 →
     * 客户端永远按<b>基础值</b>算，把服务端的加成整体覆盖掉，看起来就是"完全不生效"。
     *
     * <p>现在：服务端查存档，客户端查已同步的技能缓存
     * （{@link org.zifeng.skilltree.client.ModKeyBindingEvents#effectiveLevelClient}），
     * 两端口径一致（都用「生效等级」，且都要求技能开关为开）。
     *
     * <p>⚠️ 专用服务器上 {@code level().isClientSide()} 恒为 false →
     * 绝不会触碰客户端缓存类（类加载安全）。
     */
    private static int levelOf(LivingEntity shooter, String skillId) {
        int level = resolveLevel(shooter, skillId);
        if (level > 0) {
            // 自证：客户端(C)/服务端(S) 各自读到等级 —— 客户端不打印即说明客户端取值路径没走通
            logOnce("lv:" + (shooter.level().isClientSide() ? "C" : "S") + ":" + skillId,
                    "[zifeng] TaCZ[" + (shooter.level().isClientSide() ? "客户端" : "服务端")
                            + "] 技能等级 " + skillId + " = " + level);
        }
        return level;
    }

    private static int resolveLevel(LivingEntity shooter, String skillId) {
        if (shooter instanceof ServerPlayer sp) {
            if (sp.serverLevel() == null) {
                return 0;
            }
            PlayerSkillRecord rec = PlayerSkillSavedData.get(sp.serverLevel()).getPlayer(sp.getUUID());
            if (rec == null) {
                return 0;
            }
            // 与其余技能一致：开关关闭 → 不生效
            return rec.isEnabled(skillId) ? rec.getActiveLevel(skillId) : 0;
        }
        if (shooter instanceof net.minecraft.world.entity.player.Player && shooter.level().isClientSide()) {
            return org.zifeng.skilltree.client.ModKeyBindingEvents.effectiveLevelClient(skillId);
        }
        return 0;
    }

    /**
     * 应用「弹匣增容 / 弹夹扩容」到基础容量。
     *
     * <p>两级加成叠加：先按百分比放大，再加固定发数。
     * <p>⚠️ 只放大不减小；未学技能时原样返回。
     */
    public static int applyCapacity(LivingEntity shooter, int baseCapacity) {
        if (baseCapacity <= 0) {
            return baseCapacity;
        }
        int percentLevel = levelOf(shooter, Skills.GUN_AMMO_CAPACITY);
        int flatLevel = levelOf(shooter, Skills.GUN_MAG_CAPACITY);
        if (percentLevel <= 0 && flatLevel <= 0) {
            return baseCapacity; // 未学 → 零开销返回
        }
        int result = baseCapacity;
        if (percentLevel > 0) {
            result = (int) Math.round(result * (1.0 + percentLevel * Skills.GUN_AMMO_BONUS_PER_LEVEL));
        }
        if (flatLevel > 0) {
            result += flatLevel * Skills.GUN_MAG_BONUS_PER_LEVEL;
        }
        return Math.max(1, result);
    }

    /**
     * 「快速换弹」的时长压缩比例（0 = 无加成）。
     *
     * <p>★ 2026-10-11：技能上限提到 100 级后，<b>线性</b>公式（等级 × 5%）会在 20 级就超过 100%，
     * 且后 80 级全是废点。故改为<b>指数衰减</b>：每级把剩余时长乘 {@code (1 - 5%)}，
     * <pre>
     *   reduction = 1 - 0.95^level
     *   L1 → 5%   L10 → 40%   L20 → 64%   L50 → 92%   L100 → 99.4%
     * </pre>
     * 每一级都有收益（收益递减），再由 {@link Skills#GUN_RELOAD_REDUCTION_CAP} 封顶防「0 秒换弹」。
     */
    public static double reloadReduction(LivingEntity shooter) {
        int level = levelOf(shooter, Skills.GUN_RELOAD_SPEED);
        if (level <= 0) {
            return 0.0;
        }
        double reduction = 1.0 - Math.pow(1.0 - Skills.GUN_RELOAD_REDUCTION_PER_LEVEL, level);
        return Math.min(Skills.GUN_RELOAD_REDUCTION_CAP, Math.max(0.0, reduction));
    }

    /**
     * 「无限子弹」= 持有枪械即可无限换弹/射击（★ 2026-10-11）。
     *
     * <p>判定依据 = TaCZ 的 {@code LivingEntityAmmoCheck#needCheckAmmo()}（创造模式走的就是它）：
     * 返回 false 时 TaCZ <b>完全不检查也不消耗</b>弹药，且 HUD 显示正常上限而非虚拟弹药数。
     *
     * <p>⚠️ <b>必须双端一致</b>（客户端也拿这个判定决定能不能开火/换弹）：
     * <ul>
     *   <li>服务端：查玩家存档（已学 + 开关开启）；</li>
     *   <li>客户端：查已同步的技能缓存（同一份 learned+toggle）→ 关技能立即恢复，无预测偏差。</li>
     * </ul>
     */
    public static boolean hasInfiniteAmmo(LivingEntity shooter) {
        if (!(shooter instanceof net.minecraft.world.entity.player.Player)) {
            return false; // TaCZ 只对玩家做特殊处理
        }
        return levelOf(shooter, Skills.GUN_INFINITE_AMMO) > 0;
    }

    // ══════════════════ 属性强化（★ 2026-10-11 第二版） ══════════════════

    /**
     * 通用「指数衰减缩减」：每级把剩余量乘 {@code (1 - perLevel)}，再由 {@link Skills#GUN_REDUCTION_CAP} 封顶。
     *
     * <p>为什么不用线性：这些技能上限是 100 级，线性 {@code level × 3%} 会在 34 级就超过 100%，
     * 之后全是废点。指数衰减让<b>每一级都有收益</b>，且永远不会到 0。
     */
    private static double reduction(LivingEntity shooter, String skillId, double perLevel) {
        int level = levelOf(shooter, skillId);
        if (level <= 0) {
            return 0.0;
        }
        double reduction = 1.0 - Math.pow(1.0 - perLevel, level);
        return Math.min(Skills.GUN_REDUCTION_CAP, Math.max(0.0, reduction));
    }

    /** 枪械伤害提升：返回子弹伤害的倍率（未学 → 1.0） */
    public static float damageMultiplier(LivingEntity shooter) {
        int level = levelOf(shooter, Skills.GUN_DAMAGE);
        return level <= 0 ? 1.0F : (float) (1.0 + level * Skills.GUN_DAMAGE_BONUS_PER_LEVEL);
    }

    /** 弹道穿透强化：返回额外穿透数（未学 → 0） */
    public static int pierceBonus(LivingEntity shooter) {
        int level = levelOf(shooter, Skills.GUN_PIERCE);
        if (level <= 0) {
            return 0;
        }
        return Math.min(Skills.GUN_PIERCE_CAP, level * Skills.GUN_PIERCE_PER_LEVEL);
    }

    /** 精准射击：返回弹道扩散的缩放倍率（未学 → 1.0） */
    public static float inaccuracyMultiplier(LivingEntity shooter) {
        return (float) (1.0 - reduction(shooter, Skills.GUN_ACCURACY, Skills.GUN_ACCURACY_PER_LEVEL));
    }

    /** 后坐力抑制：返回后坐力的缩放倍率（未学 → 1.0） */
    public static float recoilMultiplier(LivingEntity shooter) {
        return (float) (1.0 - reduction(shooter, Skills.GUN_RECOIL, Skills.GUN_RECOIL_PER_LEVEL));
    }

    /** 快速开镜：返回开镜耗时的缩放倍率（未学 → 1.0） */
    public static double adsTimeMultiplier(LivingEntity shooter) {
        return 1.0 - reduction(shooter, Skills.GUN_ADS_SPEED, Skills.GUN_ADS_PER_LEVEL);
    }

    /** 快速切枪：返回举枪/收枪耗时的缩放倍率（未学 → 1.0） */
    public static float drawTimeMultiplier(LivingEntity shooter) {
        return (float) (1.0 - reduction(shooter, Skills.GUN_DRAW_SPEED, Skills.GUN_DRAW_PER_LEVEL));
    }

    /** 持枪疾行：返回移速倍率的<b>加成系数</b>（未学 → 1.0，即倍率不变） */
    public static float moveSpeedMultiplier(LivingEntity shooter) {
        int level = levelOf(shooter, Skills.GUN_MOVE_SPEED);
        if (level <= 0) {
            return 1.0F;
        }
        double bonus = Math.min(Skills.GUN_MOVE_BONUS_CAP, level * Skills.GUN_MOVE_BONUS_PER_LEVEL);
        return (float) (1.0 + bonus);
    }

    // ══════════════════ 杀戮光环联动（★ 2026-10-11 第三版） ══════════════════

    /**
     * 杀戮弹幕：命中时额外附带的伤害量（未学 / 已关闭 → 0）。
     *
     * <h2>取值口径</h2>
     * 直接取玩家的 {@code ATTACK_DAMAGE} 属性值 —— 这正是杀戮光环每次攻击用的那个值
     * （见 {@code AuraEvents.auraAttack}：{@code damage = player.getAttributeValue(ATTACK_DAMAGE)}），
     * 其中已经包含光环等级带来的加成。于是「附带一份杀戮光环伤害」是<b>字面成立</b>的：
     * 玩家攻击力涨、光环等级涨，这份附加伤害自动跟着涨。
     *
     * <p>⚠️ 刻意<b>不</b>复刻光环的两个特殊分支：
     * <ul>
     *   <li>武器附魔加成（光环会按目标生物类型加锋利/亡灵杀手等）—— 这是近战武器的概念，
     *       挂在枪械命中上不合适，且会让伤害随目标类型跳变、难以预期；</li>
     *   <li>光环·强化的混沌伤害、虚空之矛的秒杀 —— 它们属于光环自己的攻击流程，
     *       枪械只是「附带一份基础光环伤害」，不搬这些机制。</li>
     * </ul>
     *
     * <p>⚠️ 也<b>不</b>改伤害类型：附加量并入 TaCZ 本来的命中（一次命中携带全部伤害），
     * 伤害源保持枪械自身的伤害源 —— 与光环本体用的 {@code playerAttack} 同类。
     */
    public static float auraLinkedDamage(LivingEntity shooter) {
        if (!auraLinkActive(shooter)) {
            return 0.0F;
        }
        return Math.max(Skills.GUN_AMMO_MIN_BONUS, auraDamageOf(shooter) * Skills.GUN_AURA_LINK_RATIO);
    }

    /**
     * 「杀戮弹幕」是否生效（已学 <b>且</b> 自己的开关为开 —— 走 {@link #levelOf} 的既有口径）。
     *
     * <p>⚠️ 为什么要单独暴露它：整个联动（附加光环伤害 <b>+</b> 修罗杀域的混沌附加 <b>+</b>
     * 虚空诛灭的秒杀）都必须跟<b>这一个开关</b>走。否则会出现「把杀戮弹幕关掉，附加光环伤害没了、
     * 混沌附加和秒杀却还在」——因为那两个只看光环列自己的技能状态。
     */
    public static boolean auraLinkActive(LivingEntity shooter) {
        return levelOf(shooter, Skills.GUN_AURA_LINK) > 0;
    }

    /**
     * 取光环本体的攻击伤害口径：玩家的 {@code ATTACK_DAMAGE} 属性值。
     * <p>这正是 {@code AuraEvents.auraAttack} 每次攻击用的那个值（光环等级已计入其中），
     * 所以「附带一份杀戮光环伤害」是字面成立的。
     */
    private static float auraDamageOf(LivingEntity shooter) {
        float v = (float) shooter.getAttributeValue(
                net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        return Math.max(0.0F, v);
    }

    // ══════════════════ 杀戮光环的「强化态」是否对该目标生效（★ 2026-10-11 第四版） ══════════════════
    //   用户实测反馈「对应的修罗杀域和虚空诛灭没生效」—— 枪械的附加光环伤害应当连光环的两个
    //   强化分支一起带过来。判定口径【逐字对齐 AuraEvents.auraAttack】，避免两边漂移：
    //     · voidSpear = 学了虚空诛灭 && 学了伤害光环 && 伤害光环开关为开
    //     · empower   = 学了修罗杀域（⚠️ 光环本体此处【不】看开关，这里保持一致）
    //   ⚠️ 这里不复用 levelOf()：那条路要求「该技能自己的开关为开」，
    //     而光环对这两项的判定用的是 getLearnedPoints / 以伤害光环的开关为准，口径不同。

    /** 服务端技能记录（非服务端玩家 / 无记录 → null） */
    private static PlayerSkillRecord serverRecord(LivingEntity shooter) {
        if (!(shooter instanceof ServerPlayer sp) || sp.serverLevel() == null) {
            return null;
        }
        return PlayerSkillSavedData.get(sp.serverLevel()).getPlayer(sp.getUUID());
    }

    /** 修罗杀域（杀戮强化）：学了即生效（与 AuraEvents 一致，不看自身开关） */
    public static boolean auraEmpowerActive(LivingEntity shooter) {
        PlayerSkillRecord rec = serverRecord(shooter);
        return rec != null && rec.getLearnedPoints(Skills.AURA_EMPOWER) > 0;
    }

    /** 虚空诛灭：学了虚空诛灭 + 学了伤害光环 + 伤害光环开关为开（与 AuraEvents 的 voidSpear 一致） */
    public static boolean auraVoidActive(LivingEntity shooter) {
        PlayerSkillRecord rec = serverRecord(shooter);
        return rec != null
                && rec.getLearnedPoints(Skills.AURA_VOID) > 0
                && rec.getLearnedPoints(Skills.AURA_DAMAGE) > 0
                && rec.isEnabled(Skills.AURA_DAMAGE);
    }

    /**
     * 修罗杀域带来的<b>附加混沌伤害</b>（未学修罗杀域 / 比例为 0 → 0）。
     * <p>取光环的同款口径：主伤害 × {@code Config.AURA_CHAOS_DAMAGE_RATIO}（默认 20%）。
     * <p>⚠️ 与光环的一处差异：光环算的是「主伤害 + 武器附魔加成」，枪械没有近战武器附魔的概念，
     * 所以这里用纯光环伤害（= {@code ATTACK_DAMAGE}）。对枪械而言这才是它「该有的主伤害」。
     */
    public static float auraChaosBonus(LivingEntity shooter) {
        if (!auraEmpowerActive(shooter)) {
            return 0.0F;
        }
        double ratio = org.zifeng.skilltree.Config.AURA_CHAOS_DAMAGE_RATIO.get();
        if (ratio <= 0.0) {
            return 0.0F;
        }
        float auraDamage = auraDamageOf(shooter);
        if (auraDamage <= 0.0F) {
            return 0.0F;
        }
        return Math.max(1.0F, auraDamage * (float) ratio);
    }
}
