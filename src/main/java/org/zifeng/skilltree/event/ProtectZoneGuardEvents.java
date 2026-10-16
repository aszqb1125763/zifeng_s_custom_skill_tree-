package org.zifeng.skilltree.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.zifeng.skilltree.data.PlayerSkillRecord;

import java.util.List;

/**
 * 防护区「完全免伤」兜底 —— 区域内生物不受<b>任何</b>伤害（★ 2026-10-16）。
 *
 * <p><b>为什么需要这一层</b>：{@code MACHINE_ZONE_PROTECT}（庇护领域·防护选区）原本只在
 * 「光环 / 选区攻击的目标扫描」里剔除区内生物（源头筛选）。那只能覆盖走过扫描的那两条通道，
 * 下面这些都会绕过：
 * <ul>
 *   <li><b>横扫千军（{@code ULT_SWEEP}）</b>：以主目标为中心、对周围 ±50° 扇形内的实体
 *       逐个 {@code hurt()} 直伤 —— 攻击者在区外打怪，横扫会把区内生物一起打；</li>
 *   <li><b>枪械杀戮弹幕（TaCZ，仅 1.20.1）</b>：子弹是「已经命中了」才在事件里加伤害，
 *       没有扫描这一步；且虚空诛灭的 {@code forceFinish} 走 {@code setHealth + die}，
 *       连 {@code hurt()} 都不经过；</li>
 *   <li>其它任何模组 / 原版对区内生物造成的伤害。</li>
 * </ul>
 * 用户 2026-10-16 明确要求「区内生物不受任何伤害」（含怪物攻击、摔落、岩浆、窒息），
 * 因此改为在<b>受击事件入口</b>统一清零 —— 一处拦住全部来源，将来新增伤害通道也不会再漏。
 *
 * <p><b>与源头筛选的分工</b>：源头筛选保留（那是优化，少算一遍伤害/粒子/死亡结算），
 * 本类负责正确性。去掉源头筛选只会变慢，去掉本类就会重新漏伤害。
 *
 * <p><b>拦不到的两种情况</b>（原版本身就不走 {@code LivingEntity#hurt}，属已知边界）：
 * 虚空伤害（掉出世界）与 {@code /kill} 指令。
 *
 * <p><b>为什么用 {@code LivingIncomingDamageEvent}（1.21.1 的 {@code LivingHurtEvent} 等价物）</b>：
 * <ul>
 *   <li>本工程既有的「全伤害完全免疫」（虚空之躯 / 全能精通，见 {@code UltimateEvents}）
 *       用的就是这个事件 + {@code setAmount(0)}，<b>是已被实测验证过的通道</b>；</li>
 *   <li>它同时对玩家与生物触发（1.20.1 的 {@code LivingAttackEvent} 不覆盖玩家，
 *       见 {@code ForgeHooks.onLivingAttack}）—— 防护区要连区内玩家一起保护。</li>
 * </ul>
 * 这里同时 {@code setAmount(0)} <b>和</b> {@code setCanceled(true)}：
 * 前者是已验证有效的数值归零，后者让伤害干脆不发生（无扣血、无击退、无受击动画）；
 * 两者都做，任一条生效都不会漏。
 *
 * <p><b>拦不到的两种情况</b>（原版本身就不走 {@code LivingEntity#hurt}，属已知边界）：
 * 虚空伤害（掉出世界）与 {@code /kill} 指令。按用户 2026-10-16 的验收标准
 * （「能防住原版和技能树的伤害就行，漏一点也没问题」）可接受。
 *
 * <p><b>性能</b>：本方法在<b>每一次伤害</b>时触发，因此先取带 10 tick 缓存的防护者列表
 * （见 {@link ZoneSkillEvents#protectorsCached}），列表为空时直接返回 ——
 * 没开防护区的存档（绝大多数）只在缓存过期时才有一次全服遍历。
 *
 * <p>⚠️ 本类注册在 {@code UltimateEvents} <b>之后</b>：其它监听器（荆棘反伤、减伤计算等）
 * 先跑完，我们最后清零，不会因取消事件而让它们的副作用逻辑失效。
 */
public final class ProtectZoneGuardEvents {

    private ProtectZoneGuardEvents() {
    }

    @SubscribeEvent
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        // 只在服务端判定：客户端跑的是伤害预测，取消它拦不住服务端的真实结算。
        LivingEntity victim = event.getEntity();
        if (victim == null || !(victim.level() instanceof ServerLevel level)) {
            return;
        }
        List<PlayerSkillRecord> protectors = ZoneSkillEvents.protectorsCached(level);
        if (protectors.isEmpty()) {
            return; // 全服没有任何启用中的防护区 → 零成本退出（最常见的情况）
        }
        String dim = level.dimension().identifier().toString();
        // 按碰撞箱相交判定：生物站在区边缘 / 跳起时也算在区内（见 OperZone#intersects）
        if (ZoneSkillEvents.inAnyProtectOf(protectors, dim, victim)) {
            event.setAmount(0.0F);   // 已验证有效的数值归零（与虚空之躯同款通道）
            event.setCanceled(true); // 让伤害完全不发生：无扣血、无击退、无受击动画
        }
    }
}
