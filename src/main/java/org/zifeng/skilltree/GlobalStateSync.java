package org.zifeng.skilltree;

import java.util.Set;
import java.util.UUID;

/**
 * 服务器全局状态推送模块（2026-08-27，事件驱动，性能最优）：
 * <p>全局技能（寰宇法则：时之环/晴空环/无限回路）的服务器状态提示，采用
 * <b>事件驱动推送</b>而非轮询：平时零网络流量，只有状态<b>实际变化</b>时才向
 * "关注玩家"（学了且开启任意全局技能的在线玩家）推送一次 GlobalStateS2CPacket。
 * <p>触发点（服务端）：AE 频道模式应用/恢复、晴空环天气切换、时之环/晴空环开关变化。
 * 客户端收到后更新 ClientGlobalState 缓存，技能树 tooltip 显示当前服务器状态。
 */
public final class GlobalStateSync {
    private GlobalStateSync() {
    }

    /** 关注玩家集合（服务端：学了且开启任意全局技能的在线玩家 UUID） */
    private static final Set<UUID> WATCHERS = new java.util.HashSet<>();
    /** 当前服务器引用（addWatcher 时记录；服务器停止时清空） */
    private static volatile net.minecraft.server.MinecraftServer cachedServer = null;

    /** 玩家注册关注（开启任一全局技能时） */
    public static void addWatcher(UUID playerId) {
        if (playerId != null) {
            WATCHERS.add(playerId);
            if (cachedServer == null) {
                cachedServer = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            }
        }
    }

    /** 玩家取消关注（关闭全部全局技能或登出时） */
    public static void removeWatcher(UUID playerId) {
        if (playerId != null) {
            WATCHERS.remove(playerId);
        }
    }

    /** 服务器停止清理 */
    public static void clear() {
        WATCHERS.clear();
        cachedServer = null;
    }

    /** 是否有关注者（无关注者时触发点可跳过，零开销） */
    public static boolean hasWatchers() {
        return !WATCHERS.isEmpty();
    }

    /**
     * 事件驱动推送（有变化时调用）：向所有关注玩家同步一次当前全局状态。
     * ⚠️ 性能：仅在状态实际变化时调用（AE 模式应用/天气切换/锁定状态变化），
     * 平时零调用零网络流量。服务端线程安全（synchronized 防并发）。对离线玩家自动跳过。
     */
    public static synchronized void pushToWatchers() {
        if (WATCHERS.isEmpty()) {
            return;
        }
        net.minecraft.server.MinecraftServer server = cachedServer;
        if (server == null) {
            server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            cachedServer = server;
        }
        if (server == null) {
            return;
        }
        int aeMode = org.zifeng.skilltree.compat.Ae2Compat.getCurrentModeCode();
        for (UUID id : WATCHERS) {
            var player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                org.zifeng.skilltree.network.ModNetwork.sendToPlayer(player,
                        new org.zifeng.skilltree.network.GlobalStateS2CPacket(aeMode));
            }
        }
    }
}
