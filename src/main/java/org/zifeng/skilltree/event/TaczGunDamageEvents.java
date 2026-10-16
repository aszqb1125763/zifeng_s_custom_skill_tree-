package org.zifeng.skilltree.event;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.GunDamageSourcePart;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ShieldItem;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.zifeng.skilltree.compat.TaczSkills;

/**
 * 枪械技能 · 伤害加成（★ 2026-10-11 第四版，TaCZ 兼容）。
 *
 * <h2>为什么不用 mixin</h2>
 * TaCZ 把「子弹即将造成伤害」做成了公开的 Forge 事件 {@link EntityHurtByGunEvent.Pre}，
 * 它带 {@code getAttacker()}（射击者）、可写 {@code setBaseAmount}（伤害）、
 * 可写 {@code setDamageSource}（伤害类型）。这比注入子弹实体的内部方法稳得多：
 * 事件签名由 TaCZ 承诺稳定，而内部方法/Lambda 名会随版本变动
 * （我们的 mixin 用的是 {@code require=0}，变了只会静默失效、不报错）。
 *
 * <h2>⚠️ 为什么是「一次命中」而不是「命中后再补一刀」</h2>
 * 直觉做法是在 {@code Post} 里对同目标再 {@code hurt()} 一次。但 {@code LivingEntity} 的
 * {@code lastHurt} 是 {@code protected}、{@code lastHurtByMobTimestamp} 是 {@code private}
 * （1.20.1），只有 {@code invulnerableTime} 可写 —— 补刀会把 {@code lastHurt} 改成
 * 「我们补的那一刀的数值」，而这个值正是原版在无敌帧内判断
 * {@code amount > lastHurt} 的基准：
 * <ul>
 *   <li>补刀伤害小 → 后续子弹更容易突破无敌帧 → 连发枪 DPS 暴涨；</li>
 *   <li>补刀伤害大 → 后续子弹全被吞 → 表现成"装了技能反而打不动人"。</li>
 * </ul>
 * 既然无法还原，就<b>根本不要补刀</b>：把追加量并入 {@code setBaseAmount}
 * （一次命中携带全部伤害）。这样命中次数、无敌帧、TaCZ 的射速节流全部保持原样。
 * <p>⚠️ <b>唯一的例外是虚空诛灭</b>（见下），它必须真正击杀、不能靠"加伤害数值"表达。
 *
 * <h2>技能链路（第四版）</h2>
 * 本事件只做<b>一件</b>事：把「杀戮弹幕」及其光环强化态并入子弹这一次命中。
 * ⚠️ 整条链都跟<b>「杀戮弹幕」自己的开关</b>走（{@code TaczSkills.auraLinkActive}）——
 * 关掉它就整套停用，不会出现"附加光环伤害没了、混沌附加和秒杀还在"的怪状态。
 * <ol>
 *   <li><b>杀戮弹幕</b>（{@code gun_aura_link}，前置：杀戮光环 1 级）→ 附加 100% 光环伤害；</li>
 *   <li><b>· 修罗杀域</b>（{@code aura_empower}）→ 再叠加光环同款<b>附加混沌伤害</b>
 *       （主伤害 × {@code AURA_CHAOS_DAMAGE_RATIO}，默认 20%），并把本次命中的伤害类型
 *       换成<b>混沌伤害源</b>（带 DE {@code chaotic} 标签）→ 破盾 / 破守卫水晶 / 挂 Boss 判定；</li>
 *   <li><b>· 虚空诛灭</b>（{@code aura_void}）→ 直接把命中变成<b>绝对秒杀</b>，
 *       走光环自己的 {@code AuraEvents.voidSpearKill}（同款混沌伤害源 + forceFinish 破免疫兜底）。</li>
 * </ol>
 * ★ 2026-10-11 用户反馈「对应的修罗杀域和虚空诛灭没生效」→ 第 2、3 条即为此补上。
 *
 * <h2>刻意不搬的两处（说明原因，避免被当成漏做）</h2>
 * <ul>
 *   <li><b>Boss 混沌连击</b>（光环对 Boss 打 30 次递增混沌伤害削盾）：它是<b>多段 hurt</b>，
 *       搬进这里会与子弹自身那一次命中抢无敌帧（正是上面警告的失败模式）。
 *       枪械已通过「混沌伤害源 + 附加混沌伤害」获得同等的破盾能力，只是不走多段；
 *       <b>学了虚空诛灭时</b>的 Boss 处置不受影响 —— {@code voidSpearKill} 内部已含 Boss 分支。</li>
 *   <li><b>DE 守卫水晶特判</b>：水晶是 {@code Entity} 而非 {@code LivingEntity}（本事件开头就返回），
 *       且它只在光环的<b>范围扫描</b>里才会被选中。枪械命中单体生物不涉及它。</li>
 *   <li><b>范围扩大至 50 格</b>：那是光环自身范围攻击的属性，子弹没有范围的概念。</li>
 * </ul>
 */
public final class TaczGunDamageEvents {

    private TaczGunDamageEvents() {
    }

    /**
     * 命中前：叠加伤害（含光环强化态）/ 绝对秒杀。
     *
     * <p>只在服务端生效 —— 客户端跑的只是预测，改了会与服务端不一致。
     */
    @SubscribeEvent
    public static void onGunHurtPre(EntityHurtByGunEvent.Pre event) {
        if (!event.getLogicalSide().isServer()) {
            return;
        }
        Entity hurt = event.getHurtEntity();
        if (!(hurt instanceof LivingEntity target)) {
            return; // 只有生物才有护甲/无敌帧概念，其余目标不需要特殊处理
        }
        if (!(event.getAttacker() instanceof Player player)
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }

        float base = event.getBaseAmount();
        if (base <= 0.0F) {
            return;
        }

        // ★ 2026-10-16 防护区完全免伤：目标落在任一防护区内 → 整套联动直接停手。
        //   ⚠️ 必须放在【虚空诛灭秒杀之前】：voidSpearKill 的 forceFinish 走
        //      setHealth + die，绕过 hurt()，受击兜底（ProtectZoneGuardEvents）拦不住它。
        //   · 子弹自身的基础伤害不需要在这里处理 —— TaCZ 的 Pre 事件【不可取消】，
        //     但子弹最终仍走 target.hurt()，由受击兜底统清零。
        if (ZoneSkillEvents.inAnyProtectOf(ZoneSkillEvents.protectorsCached(level),
                level.dimension().location().toString(), target)) {
            return;
        }

        // ★ 整个联动（附加光环伤害 + 修罗杀域的混沌附加 + 虚空诛灭的秒杀）都跟
        //   「杀戮弹幕」自己的开关走：关掉它就整套停用，不给"关了一半还在生效"的怪状态。
        final boolean linkOn = TaczSkills.auraLinkActive(player);
        //   ⚠️ empower / void 的状态判定保持与 AuraEvents 逐字一致（见 TaczSkills 里的说明），
        //      再与杀戮弹幕的开关取与。
        final boolean empower = linkOn && TaczSkills.auraEmpowerActive(player);
        final boolean voidOn = linkOn && TaczSkills.auraVoidActive(player);

        // ── ①② 伤害量：子弹基础伤害 × 枪械伤害提升 + 杀戮弹幕附加光环伤害 + 修罗杀域附加混沌伤害 ──
        //   ⚠️ 放在虚空分支之前算：纯读取、无副作用，这样诊断日志在「秒杀」路径上也能打出来。
        float gunMultiplier = TaczSkills.damageMultiplier(player);
        float auraBonus = TaczSkills.auraLinkedDamage(player);
        float chaosBonus = linkOn && empower ? TaczSkills.auraChaosBonus(player) : 0.0F;
        float total = base * gunMultiplier + auraBonus + chaosBonus;

        // ── ③ 虚空诛灭：把这次命中升级为绝对秒杀（复用光环自己的击杀流程，行为两边一致）──
        //   ⚠️ 必须在最前面：秒杀成功 → 目标已死 → 下面再算数值没有意义，直接返回；
        //      TaCZ 事件回来后再对尸体结算伤害，那是空操作（LivingEntity.hurt 对已死实体早退）。
        //      若秒杀未成功（极端免疫把 forceFinish 也挡住了），不返回 → 子弹伤害照常生效，天然兜底。
        if (voidOn && player instanceof ServerPlayer serverPlayer) {
            AuraEvents.voidSpearKill(level, serverPlayer, target);
            if (!target.isAlive()) {
                return;
            }
        }

        // ── ② 修罗杀域：破盾（与光环的 empower 分支同款）──
        if (empower && target instanceof Player blocker && blocker.isBlocking()
                && blocker.getUseItem().getItem() instanceof ShieldItem) {
            // ⚠️ 1.20.1：Player.disableShield() 是 protected（1.21 才 public）→ 手动实现同等效果
            blocker.getCooldowns().addCooldown(blocker.getUseItem().getItem(), 100);
            blocker.stopUsingItem();
        }

        if (total > base) {
            event.setBaseAmount(total);
        }

        // ── ② 修罗杀域：换上混沌伤害源（带 DE chaotic 标签 → 破盾 / 破守卫水晶 / 挂 Boss 判定）──
        //   ⚠️ 只能整体换源：一次命中只有一种伤害类型。于是「混沌伤害」这部分是通过
        //      「整发子弹都算混沌」来实现的 —— 这是为了让 chaotic 标签真正起作用
        //      （否则破盾/守卫水晶特判对枪械仍是"没生效"）。
        if (chaosBonus > 0.0F) {
            DamageSource chaos = AuraEvents.buildChaosSource(level, player);
            if (chaos != null) {
                // 两个部件都换掉，确保无论 TaCZ 走哪条分支（是否穿甲）都用我们的类型
                event.setDamageSource(GunDamageSourcePart.NON_ARMOR_PIERCING, chaos);
                event.setDamageSource(GunDamageSourcePart.ARMOR_PIERCING, chaos);
            }
        }
    }
}
