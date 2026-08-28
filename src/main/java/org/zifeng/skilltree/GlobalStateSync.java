package org.zifeng.skilltree;

import java.util.Map;
import java.util.UUID;

/**
 * 服务器全局状态推送主系统（2026-08-28 架构升级：订阅位图 + 快照去重 + 增量推送 + Tick 末合并）：
 * <p>主系统负责【全局类】状态推送（AE 频道/天气/时间锁定），玩家级状态（技能点/阶梯进度）走
 * {@link org.zifeng.skilltree.PlayerPushState} 子系统（每玩家独立，防数据串）。
 * <p>性能设计（事件驱动，零轮询）：
 * <ul>
 *   <li><b>订阅位图</b>：每玩家用 int 位图声明订阅哪些状态（bit0=AE bit1=天气 bit2=时间），只推订阅的</li>
 *   <li><b>快照去重</b>：记录每玩家上次推送值，状态没变不重复发包</li>
 *   <li><b>增量包</b>：GlobalStateS2CPacket 每字段 -1=无变化，客户端合并更新（只发变化的字段）</li>
 *   <li><b>Tick 末合并</b>：一 tick 内多次 markDirty() → ServerTickEvent 末尾合并成一次推送</li>
 * </ul>
 */
public final class GlobalStateSync {
    private GlobalStateSync() {
    }

    /** 订阅位图：bit0=AE 频道 bit1=晴空环天气 bit2=时间锁定 */
    public static final int SUB_AE = 1;
    public static final int SUB_WEATHER = 2;
    public static final int SUB_TIME = 4;
    public static final int SUB_ALL = SUB_AE | SUB_WEATHER | SUB_TIME;

    /** 玩家 UUID → 订阅位图（只推订阅的状态） */
    private static final Map<UUID, Integer> SUBSCRIPTIONS = new java.util.HashMap<>();
    /** 玩家 UUID → 上次推送快照 [aeMode, weatherMode, timeLocked]（去重用） */
    private static final Map<UUID, int[]> LAST_SNAPSHOT = new java.util.HashMap<>();
    /** 全局状态脏标记（tick 末合并推送） */
    private static volatile boolean dirty = false;
    /** 当前服务器引用 */
    private static volatile net.minecraft.server.MinecraftServer cachedServer = null;

    /** 玩家注册/更新订阅（bits=0 表示取消全部订阅）。订阅变化 → 立即校准一次全量推送。 */
    public static synchronized void setSubscription(UUID playerId, int bits) {
        if (playerId == null) {
            return;
        }
        if (bits == 0) {
            SUBSCRIPTIONS.remove(playerId);
            LAST_SNAPSHOT.remove(playerId);
        } else {
            Integer oldBits = SUBSCRIPTIONS.put(playerId, bits);
            if (cachedServer == null) {
                cachedServer = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            }
            // ⚠️ 2026-08-28 性能：仅订阅位图变化时才强制全量校准（客户端打开界面/订阅变化时）；
            //    界面打开期间每 40 tick 轮询位图相同 → 跳过，避免每 2 秒重复全量推送（P2）
            if (oldBits == null || oldBits != bits) {
                forcePush(playerId);
            }
        }
    }

    /** 玩家登出：移除订阅与快照（防泄漏） */
    public static synchronized void removeSubscription(UUID playerId) {
        if (playerId != null) {
            SUBSCRIPTIONS.remove(playerId);
            LAST_SNAPSHOT.remove(playerId);
        }
    }

    /** 全局状态变化标记（tick 末合并推送；高频繁调用点用这个，避免每次发包） */
    public static void markDirty() {
        dirty = true;
    }

    /** 服务器停止清理 */
    public static synchronized void clear() {
        SUBSCRIPTIONS.clear();
        LAST_SNAPSHOT.clear();
        dirty = false;
        cachedServer = null;
    }

    /** 是否有关注者（触发点可先查，零开销跳过） */
    public static synchronized boolean hasSubscribers() {
        return !SUBSCRIPTIONS.isEmpty();
    }

    /** 每 tick 末尾调用：合并推送（ServerTickEvent.End 挂载） */
    public static void onServerTickEnd() {
        if (dirty) {
            dirty = false;
            pushChanged();
        }
    }

    /** 对单个玩家强制全量推送（打开技能树/进服/订阅变化时校准） */
    public static synchronized void forcePush(UUID playerId) {
        if (playerId == null) {
            return;
        }
        Integer bits = SUBSCRIPTIONS.get(playerId);
        if (bits == null || bits == 0) {
            return;
        }
        net.minecraft.server.MinecraftServer server = currentServer();
        if (server == null) {
            return;
        }
        var player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            return;
        }
        int[] snapshot = currentSnapshot(server);
        int[] last = LAST_SNAPSHOT.computeIfAbsent(playerId, k -> new int[]{-1, -1, -1});
        int sendAe = (bits & SUB_AE) != 0 ? snapshot[0] : -1;
        int sendWeather = (bits & SUB_WEATHER) != 0 ? snapshot[1] : -1;
        int sendTime = (bits & SUB_TIME) != 0 ? snapshot[2] : -1;
        if (sendAe >= 0) last[0] = snapshot[0];
        if (sendWeather >= 0) last[1] = snapshot[1];
        if (sendTime >= 0) last[2] = snapshot[2];
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new org.zifeng.skilltree.network.GlobalStateS2CPacket(sendAe, sendWeather, sendTime));
    }

    /** 事件驱动推送：按订阅位图过滤 + 快照去重，只发变化的订阅项 */
    public static synchronized void pushChanged() {
        if (SUBSCRIPTIONS.isEmpty()) {
            return;
        }
        net.minecraft.server.MinecraftServer server = currentServer();
        if (server == null) {
            return;
        }
        int[] snapshot = currentSnapshot(server);
        for (Map.Entry<UUID, Integer> entry : SUBSCRIPTIONS.entrySet()) {
            UUID id = entry.getKey();
            int bits = entry.getValue();
            var player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                continue;
            }
            int[] last = LAST_SNAPSHOT.computeIfAbsent(id, k -> new int[]{-1, -1, -1});
            // 按订阅位 + 值变化过滤：-1 表示无变化/未订阅
            int sendAe = (bits & SUB_AE) != 0 && last[0] != snapshot[0] ? snapshot[0] : -1;
            int sendWeather = (bits & SUB_WEATHER) != 0 && last[1] != snapshot[1] ? snapshot[1] : -1;
            int sendTime = (bits & SUB_TIME) != 0 && last[2] != snapshot[2] ? snapshot[2] : -1;
            if (sendAe == -1 && sendWeather == -1 && sendTime == -1) {
                continue; // 无变化：不发包（快照去重）
            }
            if (sendAe >= 0) last[0] = snapshot[0];
            if (sendWeather >= 0) last[1] = snapshot[1];
            if (sendTime >= 0) last[2] = snapshot[2];
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new org.zifeng.skilltree.network.GlobalStateS2CPacket(sendAe, sendWeather, sendTime));
        }
    }

    /** 当前全局快照 [aeMode, weatherMode, timeLocked(0/1)]；weatherMode=-2=天气未锁定（自然循环），-1=未同步 */
    private static int[] currentSnapshot(net.minecraft.server.MinecraftServer server) {
        int timeLocked = 0;
        int weatherMode = -2;
        if (server.overworld() != null) {
            timeLocked = server.overworld().getGameRules().getBoolean(
                    net.minecraft.world.level.GameRules.RULE_DAYLIGHT) ? 0 : 1;
            // 晴空环锁定中（doWeatherCycle=false）→ 当前天气模式；未锁定 → -2（自然循环，客户端显示未锁定）
            weatherMode = server.overworld().getGameRules().getBoolean(
                    net.minecraft.world.level.GameRules.RULE_WEATHER_CYCLE)
                    ? -2 : org.zifeng.skilltree.event.AuraEvents.getCurrentWeatherMode();
        }
        return new int[]{
                org.zifeng.skilltree.compat.Ae2Compat.getCurrentModeCode(),
                weatherMode,
                timeLocked
        };
    }

    private static net.minecraft.server.MinecraftServer currentServer() {
        net.minecraft.server.MinecraftServer server = cachedServer;
        if (server == null) {
            server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            cachedServer = server;
        }
        return server;
    }
}
