package org.zifeng.skilltree.system;

import net.minecraft.server.level.ServerPlayer;
import org.zifeng.skilltree.event.ZoneSkillEvents;

/**
 * 机械共鸣·选区作业模块（2026-09-12 1.4.1 新增，Z-Link 门面）。
 *
 * <p>职责：驱动「选区放置 / 选区挖掘」的<b>分批跨 tick 处理</b>。
 * 用户框选往往远大于单帧可承受的方块数（上限 256³），若一次跑完必卡服；
 * 因此把一次触发拆成作业（快照选区 + 迭代游标），每 tick 只跑 5ms 预算，
 * 直到遍历完（详见 {@link ZoneSkillEvents#advance} / {@link ZoneSkillEvents#tickJobs}）。
 *
 * <p>调度契约（与其它 Z-Link 模块一致）：
 * <ul>
 *   <li>{@link #activeCondition} 只查"该玩家是否有进行中的作业"（map 读，零重活）——
 *       没作业的玩家 → 模块冬眠，完全不产生开销</li>
 *   <li>{@link #onTick} 交回 {@link ZoneSkillEvents#tickJobs} 推进一片</li>
 *   <li>{@link #onPlayerLogout} / {@link #onServerStop} 丢弃遗留作业（不跨会话继续）</li>
 * </ul>
 */
public final class ZoneWorkModule implements ZModule {
    public static final ZoneWorkModule INSTANCE = new ZoneWorkModule();

    private ZoneWorkModule() {
    }

    @Override
    public String id() {
        return "zone_work";
    }

    /** 活跃条件：有进行中的选区作业（无 → 冬眠零开销） */
    @Override
    public boolean activeCondition(ServerPlayer player) {
        return player != null && ZoneSkillEvents.hasJob(player.getUUID());
    }

    @Override
    public void onTick(ServerPlayer player) {
        ZoneSkillEvents.tickJobs(player);
    }

    @Override
    public void onPlayerLogout(ServerPlayer player) {
        ZoneSkillEvents.onPlayerLogout(player);
    }

    @Override
    public void onServerStop() {
        ZoneSkillEvents.onServerStop();
    }
}
