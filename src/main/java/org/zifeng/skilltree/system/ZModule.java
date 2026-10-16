package org.zifeng.skilltree.system;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Z-Link 模块描述（2026-09-09 骨架 v1）。
 * <p>任何"功能模块"登记成 {@link ZModule} 后，由 {@link ZModules} 统一调度：
 * 每 tick 先轻量评估 {@link #activeCondition}（不满足 = 冬眠，跳过 body，零开销）；
 * 满足才唤醒并执行 {@link #onTick}。玩家登出/服务器停止也由此接口通知模块清理。
 * <p>设计目标（呼应 zl 系统理念）：模块只声明"我什么时候需要跑 + 跑什么"，
 * 不自己挂 tick、不自己轮询；调度与生命周期全交给注册表 —— 新增技能/功能 = 登记一个模块即可。
 */
public interface ZModule {
    /** 模块唯一 id（如 "aura_damage"/"magnet_zone"），用于日志与去重 */
    String id();

    /**
     * 活跃条件（每 tick 轻量评估；请只做 map 读/布尔判断，勿重活）。
     * false = 冬眠：onTick 不会被执行，模块零开销。
     */
    boolean activeCondition(ServerPlayer player);

    /** 活跃时的 tick 执行（玩家维度；由注册表按条件驱动，模块勿自行挂事件） */
    default void onTick(ServerPlayer player) {
    }

    /** 玩家登出/换服：模块清理该玩家的临时状态（默认空） */
    default void onPlayerLogout(ServerPlayer player) {
    }

    /** 服务器停止：模块清理全局状态（默认空） */
    default void onServerStop() {
    }
}
