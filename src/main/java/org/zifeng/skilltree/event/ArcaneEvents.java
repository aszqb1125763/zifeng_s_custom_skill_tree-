package org.zifeng.skilltree.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import org.zifeng.skilltree.Config;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.skill.SkillEffects;
import org.zifeng.skilltree.skill.Skills;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 奥术防护事件（2026-09-14 新增）—— 魔法减伤与反制。
 *
 * <h2>为什么单独成类</h2>
 * {@link UltimateEvents} 已近 1800 行且承担"终极节点"职责；本次新增的是一套**独立主题**
 * （奥术防护），单独成类便于阅读、调试与将来扩展。
 *
 * <h2>魔法伤害的定义（关键设计）</h2>
 * MC 里"护甲无效"的伤害（魔法/凋零/龙息/虚空伤害等）都带 {@code BYPASSES_ARMOR} 标签，
 * 而现有物理减伤只对 {@code !BYPASSES_ARMOR} 生效 —— 这正是用户指出的空缺。
 * 因此本类把"魔法伤害"定义为：
 * <pre>
 *   BYPASSES_ARMOR 且 !BYPASSES_INVULNERABILITY
 * </pre>
 * 后半句用于排除 {@code /kill}、虚空这种"本就该无法减免"的伤害。
 *
 * <h2>参考（只学原理，自行实现）</h2>
 * <ul>
 *   <li>L2Hostility —— 魔法/物理伤害分类 + {@code BYPASS_ARMOR}/{@code BYPASS_MAGIC} 双穿透状态</li>
 *   <li>Champions —— Adaptable（同类型递减 + 硬上限）、Reflective（反射 + 防递归 + 不致死）</li>
 *   <li>Enigmatic Legacy —— 免疫白名单 + 抗性带符号倍率；伤害转回血（虚空珍珠）</li>
 * </ul>
 *
 * <h2>减伤公式（解决"1000 级但效果封顶 100%"的颗粒度问题）</h2>
 * 采用 MOBA 通用的 {@code reduction = D/(D+K)}：减伤率**永远小于 1**，
 * 而有效生命 EHP 随 D 线性增长 → 每级价值恒定、天然不无敌、可无限扩级。
 *
 * <h2>多层减伤的叠加</h2>
 * 各层用 {@code 1-(1-a)(1-b)}（独立乘算），**不能直接相加**（相加会在高等级溢出成 100%）。
 */
public final class ArcaneEvents {

    private ArcaneEvents() {
    }

    /** 法术反射的递归深度（服务器单线程，静态计数足够；防止"两个玩家互相反射"无限循环） */
    private static int reflectDepth;

    /** 驱法破咒冷却：玩家 → 可再次触发的世界时间 */
    private static final Map<UUID, Long> PURGE_UNTIL = new HashMap<>();
    /** 适应之躯：玩家 → 上次受到的伤害类型 */
    private static final Map<UUID, String> ADAPT_TYPE = new HashMap<>();
    /** 适应之躯：玩家 → 同类型连续受击层数 */
    private static final Map<UUID, Integer> ADAPT_STACK = new HashMap<>();
    /** 净化领域间隔缓存：玩家 → [生效等级, 间隔 tick]（等级不变则复用，免每 tick 调 Math.pow） */
    private static final Map<UUID, int[]> PURIFY_INTERVAL = new HashMap<>();

    /** 登出/换存档清理（防跨会话残留） */
    public static void clearPlayer(UUID id) {
        if (id == null) {
            return;
        }
        PURGE_UNTIL.remove(id);
        ADAPT_TYPE.remove(id);
        ADAPT_STACK.remove(id);
        PURIFY_INTERVAL.remove(id);
    }

    /**
     * 是否为"魔法类"伤害：护甲无效、但并非无视无敌的伤害。
     *
     * <p>这样定义无需自建伤害类型或标签，且自动覆盖其他模组的魔法伤害（只要它带该标签）。
     */
    public static boolean isMagicDamage(DamageSource src) {
        return src != null
                && src.is(DamageTypeTags.BYPASSES_ARMOR)
                && !src.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    // ══════════════════ 受击侧 ══════════════════

    /**
     * 魔法减伤链 + 法力虹吸 + 法术反射 + 驱法破咒。
     *
     * <p>顺序：先算减伤（壁垒 → 抑制 → 适应，逐层乘算），再触发"按原始伤害"的附加效果。
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingHurtEvent event) {
        DamageSource src = event.getSource();

        // ⚠️ 版本适配：1.20.1 的 getEntity() 已返回 LivingEntity（直接 instanceof 会报"子类型"），
        //    故先取出为 Entity 再做模式匹配 —— 两个版本都合法
        Entity hurt = event.getEntity();

        // ── 攻击侧：破法之刃（我方玩家打别人）──
        if (hurt instanceof LivingEntity target
                && !(target instanceof ServerPlayer)
                && src.getDirectEntity() instanceof ServerPlayer attacker) {
            applySpellbreak(attacker, target, event);
            return; // 破法之刃只处理攻击侧，不参与自身受击链
        }

        // ── 受击侧：我方玩家被打 ──
        if (!(hurt instanceof ServerPlayer player)) {
            return;
        }
        if (!isMagicDamage(src)) {
            return;
        }
        float amount = event.getAmount();
        if (amount <= 0) {
            return;
        }

        PlayerSkillRecord record = UltimateEvents.getRecordFor(player);

        // 1) 奥术壁垒（+ 奥术真解 + 奥术神体）
        double reduction = SkillEffects.getMagicReduction(record);
        // 2) 法术抑制：只对间接伤害（箭矢/法术弹射物）
        if (!isDirectDamage(src)) {
            reduction = combine(reduction, SkillEffects.getDampenReduction(record));
        }
        // 3) 适应之躯：同类型连续受击递减
        reduction = combine(reduction, adaptBonus(player, record, src));
        reduction = Math.min(0.9999, reduction);
        if (reduction > 0) {
            event.setAmount(amount * (float) (1.0 - reduction));
        }

        // 4) 法力虹吸：按**原始**魔法伤害比例回血（先算减伤前的量，语义更直观）
        if (learned(record, Skills.MANA_SIPHON)) {
            double ratio = Config.MANA_SIPHON_RATIO.get();
            if (ratio > 0 && player.isAlive()) {
                player.heal((float) (amount * ratio));
            }
        }
        // 5) 法术反射：概率把魔法伤害打回施法者（不致死 + 防递归）
        if (learned(record, Skills.SPELL_REFLECT) && reflectDepth == 0
                && player.getRandom().nextDouble() < Config.SPELL_REFLECT_CHANCE.get()) {
            reflectMagic(player, src, amount * Config.SPELL_REFLECT_RATIO.get().floatValue());
        }
        // 6) 驱法破咒：受击清除自身一个负面效果（带冷却）
        if (learned(record, Skills.SPELL_PURGE) && purgeReady(player) && clearOneHarmful(player)) {
            PURGE_UNTIL.put(player.getUUID(), player.level().getGameTime()
                    + Config.SPELL_PURGE_COOLDOWN.get());
        }
    }

    /** 两个独立减伤层的正确叠加：1-(1-a)(1-b)。直接相加会在高等级溢出成 100%（无敌）。 */
    private static double combine(double a, double b) {
        return 1.0 - (1.0 - a) * (1.0 - b);
    }

    /**
     * 是否直接伤害（近战）。
     *
     * <p>⚠️ {@code DamageSource.isDirect()} 是 1.20.5+ 才有的 API，1.20.1 不可用，
     * 故用"直接实体 == 造成实体"等价实现（近战：两者都是玩家 → true；箭矢/法术：弹射物 ≠ 施法者 → false）。
     */
    private static boolean isDirectDamage(DamageSource src) {
        return src.getDirectEntity() != null && src.getDirectEntity() == src.getEntity();
    }

    private static boolean learned(PlayerSkillRecord record, String skillId) {
        return record.getLearnedPoints(skillId) > 0 && record.isEnabled(skillId);
    }

    private static boolean purgeReady(ServerPlayer player) {
        return PURGE_UNTIL.getOrDefault(player.getUUID(), 0L) <= player.level().getGameTime();
    }

    /** 适应之躯：同类型连续受击每层 +step，换类型清零；总增量受 Config 上限约束 */
    private static double adaptBonus(ServerPlayer player, PlayerSkillRecord record, DamageSource src) {
        if (!learned(record, Skills.ARCANE_ADAPT)) {
            return 0;
        }
        UUID id = player.getUUID();
        String type = src.getMsgId();
        if (!type.equals(ADAPT_TYPE.get(id))) {
            ADAPT_TYPE.put(id, type);
            ADAPT_STACK.put(id, 0);
        }
        int stack = ADAPT_STACK.getOrDefault(id, 0) + 1;
        ADAPT_STACK.put(id, stack);
        return Math.min(Config.ARCANE_ADAPT_MAX.get(), Config.ARCANE_ADAPT_STEP.get() * stack);
    }

    /**
     * 把魔法伤害反射给施法者。
     * <ul>
     *   <li><b>不致死</b>：最多打到目标剩 1 点血（避免玩家被自己的反射害死，参考 Champions）</li>
     *   <li><b>防递归</b>：{@link #reflectDepth} 计数，反射伤害不再触发反射</li>
     * </ul>
     */
    private static void reflectMagic(ServerPlayer player, DamageSource src, float damage) {
        if (damage <= 0) {
            return;
        }
        LivingEntity target = null;
        if (src.getEntity() instanceof LivingEntity le && le != player) {
            target = le;
        } else if (src.getDirectEntity() instanceof LivingEntity le && le != player) {
            target = le;
        }
        if (target == null || !target.isAlive()) {
            return;
        }
        float real = Math.min(damage, Math.max(0.0F, target.getHealth() - 1.0F));
        if (real <= 0) {
            return;
        }
        reflectDepth++;
        try {
            target.hurt(player.damageSources().indirectMagic(player, player), real);
        } finally {
            reflectDepth--;
        }
    }

    /** 清除一个负面效果（返回是否真的清掉了） */
    private static boolean clearOneHarmful(LivingEntity entity) {
        for (var inst : new ArrayList<>(entity.getActiveEffects())) {
            if (inst.getEffect().getCategory() == MobEffectCategory.HARMFUL) {
                entity.removeEffect(inst.getEffect());
                return true;
            }
        }
        return false;
    }

    // ══════════════════ 攻击侧：破法之刃 ══════════════════

    /**
     * 破法之刃：近战命中时
     * <ol>
     *   <li>按目标身上的**增益数量**增伤（每个 +15%，上限 +60%）—— "敌越强、我越强"</li>
     *   <li>驱散目标一个增益效果（参考 L2Hostility 的 Dispell 封印思路）</li>
     * </ol>
     * ⚠️ 对玩家目标不生效（防恶意 PvP 滥用）。
     */
    private static void applySpellbreak(ServerPlayer attacker, LivingEntity target,
                                        LivingHurtEvent event) {
        PlayerSkillRecord record = UltimateEvents.getRecordFor(attacker);
        if (!learned(record, Skills.SPELLBREAK_BLADE)) {
            return;
        }
        int buffs = 0;
        for (var inst : target.getActiveEffects()) {
            if (inst.getEffect().isBeneficial()) {
                buffs++;
            }
        }
        if (buffs <= 0) {
            return;
        }
        float bonus = (float) Math.min(Config.SPELLBREAK_MAX.get(),
                buffs * Config.SPELLBREAK_PER_BUFF.get());
        if (bonus > 0) {
            event.setAmount(event.getAmount() * (1.0F + bonus));
        }
        // 驱散一个增益
        for (var inst : new ArrayList<>(target.getActiveEffects())) {
            if (inst.getEffect().isBeneficial()) {
                target.removeEffect(inst.getEffect());
                break;
            }
        }
    }

    // ══════════════════ 每 tick：净化领域（光环） ══════════════════

    /**
     * 净化领域的作用间隔（tick）——随等级缩短（★ 2026-09-20 修复「100 级里 99 级无效」）。
     *
     * <p><b>原 bug</b>：原实现直接用 {@code Config.PURIFY_FIELD_INTERVAL} 常量，
     * <b>完全不看等级</b>（半径也是常量）→ 1 级与 100 级效果完全相同，99 级全白学。
     * 符合用户报的「看着等级很高，但是几十级之后再升就无效」。
     *
     * <p><b>现公式</b>（乘法递减，与「杀戮光环·速度」同款）：
     * <pre>interval = base × (1 - 0.03)^(生效等级 - 1)</pre>
     * 1 级 = base 原值（保持原有手感，Config 改动仍即时生效）；100 级 ≈ 3 tick。
     *
     * <p>带缓存：等级未变直接复用（本方法每 tick 被调用）。
     */
    private static int purifyInterval(PlayerSkillRecord record) {
        final int base = Config.PURIFY_FIELD_INTERVAL.get();
        final int level = record.isEnabled(Skills.PURIFY_FIELD)
                ? record.getActiveLevel(Skills.PURIFY_FIELD) : 0;
        if (level <= 1) {
            return base; // 未学/1 级：原行为
        }
        final UUID id = record.getOwner();
        final int[] cached = PURIFY_INTERVAL.get(id);
        if (cached != null && cached[0] == level) {
            return cached[1]; // 等级未变 → 复用
        }
        final int interval = (int) Math.max(1L,
                Math.round(base * Math.pow(1.0 - PURIFY_INTERVAL_REDUCTION, level - 1)));
        PURIFY_INTERVAL.put(id, new int[]{level, interval});
        return interval;
    }

    /** 净化领域间隔每级递减率（×0.97/级）：1 级 = Config 原值，100 级 ≈ 3 tick */
    private static final double PURIFY_INTERVAL_REDUCTION = 0.03;

    /**
     * 净化领域：周期性清除**自己与范围内友方**各一个负面效果。
     *
     * <p>由 {@link UltimateEvents#tickPlayer} 调用（与夜视/发光等常驻技能同一条 tick 链）。
     */
    public static void tick(ServerPlayer player) {
        PlayerSkillRecord record = UltimateEvents.getRecordFor(player);
        if (!learned(record, Skills.PURIFY_FIELD)) {
            return;
        }
        int interval = purifyInterval(record);
        if (interval <= 0 || player.tickCount % interval != 0) {
            return;
        }
        clearOneHarmful(player);
        double r = Config.PURIFY_FIELD_RADIUS.get();
        List<LivingEntity> nearby = player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(r),
                e -> e.isAlive() && e != player);
        for (LivingEntity le : nearby) {
            // 只对友方：其他玩家 / 已驯服生物 / 盟友
            if (le instanceof Player || player.isAlliedTo(le)) {
                clearOneHarmful(le);
            }
        }
    }
}
