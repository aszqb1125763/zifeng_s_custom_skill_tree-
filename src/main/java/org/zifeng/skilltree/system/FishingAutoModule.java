package org.zifeng.skilltree.system;

import net.minecraft.server.level.ServerPlayer;
import org.zifeng.skilltree.event.FishingEvents;

/**
 * 垂钓·全自动垂钓模块（★ 2026-10-03 新增）。
 *
 * <p>活跃条件 = 玩家学了「全自动垂钓」且手上还挂着钩子 → 每 tick 走一次倒计时；
 * 没学该技能的玩家完全不进入本模块（冬眠零开销）。
 *
 * <p>具体产出逻辑全部在 {@link FishingEvents}（与手动收杆共用同一套抽表/交付代码）。
 */
public final class FishingAutoModule implements ZModule {

    public static final FishingAutoModule INSTANCE = new FishingAutoModule();

    private FishingAutoModule() {
    }

    @Override
    public String id() {
        return "fishing_auto";
    }

    @Override
    public boolean activeCondition(ServerPlayer player) {
        return FishingEvents.shouldTick(player);
    }

    @Override
    public void onTick(ServerPlayer player) {
        FishingEvents.tick(player);
    }

    @Override
    public void onPlayerLogout(ServerPlayer player) {
        FishingEvents.clearPlayer(player);
    }

    @Override
    public void onServerStop() {
        FishingEvents.clearAll();
    }
}
