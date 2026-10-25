package org.zifeng.skilltree.event;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.zifeng.skilltree.compat.TaczSkills;

/**
 * 枪械技能 · 周期刷新（★ 2026-10-11 第二版）。
 *
 * <p>解决的问题：枪械技能加成写在 TaCZ 的<b>属性缓存</b>里，而该缓存只在
 * TaCZ 认为"属性可能变了"时重算。玩家持枪时加点 / 开关技能不会触发重算 →
 * 「点了没反应」。本类按 {@link #REFRESH_INTERVAL_TICKS} 主动触发一次重算。
 *
 * <p>{@code TickEvent.PlayerTickEvent} 在<b>客户端与服务端都会触发</b>
 * （客户端触发的是 {@code LocalPlayer}），因此一个类即可覆盖双端：
 * <ul>
 *   <li>服务端 → 权威的穿透 / 伤害 / 移速；</li>
 *   <li>客户端 → 开镜耗时 / 后坐力 / 弹道扩散这些<b>客户端自算自显示</b>的值。</li>
 * </ul>
 *
 * <p>⚠️ 类加载安全：未装 TaCZ 时直接 return，<b>绝不会</b>去碰
 * {@link org.zifeng.skilltree.compat.TaczCacheRefresher}（那才会引用 TaCZ 类型）。
 */
public final class TaczGunSkillEvents {

    private TaczGunSkillEvents() {
    }

    /**
     * 刷新间隔（tick）。
     * <p>20 tick（1 秒）是「玩家感知不到延迟」与「几乎零开销」的平衡点：
     * 缓存重算只在手持枪械时才发生，且 TaCZ 自己在每次举枪时也会做同样的事。
     */
    private static final int REFRESH_INTERVAL_TICKS = 20;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return; // ⚠️ Forge 每 tick 触发 START + END 两次，只处理一次
        }
        if (!TaczSkills.isTaczLoaded()) {
            return; // 未装 TaCZ → 连 TaczCacheRefresher 都不加载
        }
        Player player = event.player;
        if (player == null || player.level() == null) {
            return;
        }
        // ★ 2026-10-22 枪械·不过热（不依赖 Mixin 的兜底方案）：
        //   每 tick 把主手枪械的热量归零并解除过热锁定。
        //   热量存在枪的 ItemStack NBT 里（IGun.getHeatAmount/setHeatAmount），
        //   所以这里直接写 NBT，能同时压住服务端判定与客户端热量条（同步覆盖）。
        // ★ 2026-10-22 用户方案：每 10 tick 兜底归零一次（配合开枪事件，双保险）
        if (TaczSkills.hasNoHeat(player) && player.tickCount % 10 == 0) {
            zeroHeat(player.getMainHandItem());
        }
        if (player.tickCount % REFRESH_INTERVAL_TICKS != 0) {
            return; // 错峰：每个玩家按自己的 tickCount 刷新，避免同一 tick 集中重算
        }
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            return;
        }
        org.zifeng.skilltree.compat.TaczCacheRefresher.refresh(player, held);
    }

    /** 把枪械热量归零 + 解除过热锁定（热量存在 ItemStack NBT 里） */
    private static void zeroHeat(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        try {
            final com.tacz.guns.api.item.IGun gun = com.tacz.guns.api.item.IGun.getIGunOrNull(stack);
            if (gun != null) {
                gun.setHeatAmount(stack, 0.0F);
                gun.setOverheatLocked(stack, false);
            }
        } catch (Throwable ignored) {
            // TaCZ 版本差异时静默降级
        }
    }
}
