package org.zifeng.skilltree.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.zifeng.skilltree.data.OperZone;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;
import org.zifeng.skilltree.skill.Skills;

import java.util.ArrayList;
import java.util.List;

/**
 * 挖掘防护（MINING_PROTECT，★ 2026-10-11，1.20.1）：选区内的方块<b>全服生效</b>地
 * <b>挖不动 + 炸不坏</b>。
 *
 * <h2>为什么是全服生效</h2>
 * 与现有「防护选区」（{@code ZoneSkillEvents#activeProtectors}）同一套口径：
 * 收集<b>所有在线玩家</b>已学 + 已开启 + 框了区的记录，命中任一区即拦截。
 * 目的是防乱挖/防炸图，所以对<b>任何人</b>（含布防者本人）一视同仁。
 *
 * <h2>三道拦截</h2>
 * <ol>
 *   <li>{@link PlayerInteractEvent.LeftClickBlock} → 取消，方块<b>连挖都不开始</b>（保留原版挖掘手感）</li>
 *   <li>{@link BlockEvent.BreakEvent} → 取消，兜底（创造速破 / 其它 mod 绕过挖掘进度的破坏）</li>
 *   <li>{@link ExplosionEvent.Detonate} → 把区内方块从<b>受影响列表</b>里剔除（TNT/苦力怕也炸不坏）</li>
 * </ol>
 */
public final class MiningProtectEvents {
    private MiningProtectEvents() {
    }

    /**
     * 收集当前所有生效的挖掘防护区（跨维度）。
     *
     * <p>⚠️ 只读 {@code getPlayer}（不创建记录）—— 与 {@code ZoneSkillEvents#activeProtectors}
     * 同款性能考量：本方法在每次左键挖掘/爆炸时都会跑，用 {@code getOrCreatePlayer}
     * 会给没学过技能的玩家白建记录 + 白写存档。
     */
    private static List<OperZone> activeZones(ServerLevel level) {
        return currentZones(level.getServer());
    }

    /**
     * 当前生效的挖掘防护区（<b>全服在线玩家</b>中已学 + 已开启 + 已框区者）。
     *
     * <p>⚠️ 只读 {@code getPlayer}（不创建记录）—— 与 {@code ZoneSkillEvents#activeProtectors}
     * 同款性能考量：本方法在每次左键挖掘/爆炸时都会跑，用 {@code getOrCreatePlayer}
     * 会给没学过技能的玩家白建记录 + 白写存档。
     */
    private static List<OperZone> currentZones(MinecraftServer server) {
        List<OperZone> zones = new ArrayList<>();
        if (server == null) {
            return zones;
        }
        PlayerSkillSavedData data = PlayerSkillSavedData.get(server.overworld());
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            PlayerSkillRecord rec = data.getPlayer(p.getUUID());
            if (rec == null) {
                continue; // 无技能数据 → 不可能有防护区
            }
            if (rec.getLearnedPoints(Skills.MINING_PROTECT) <= 0
                    || !rec.isEnabled(Skills.MINING_PROTECT)) {
                continue; // 未学 / 未开启（技能关闭即不保护）
            }
            // ★ 2026-10-11：方块保护改为多块，把该玩家【所有】区都加进来（不限数量）
            zones.addAll(rec.getMiningProtectZones());
        }
        return zones;
    }

    /**
     * 把当前全服挖掘防护区广播给所有在线玩家（★ 2026-10-11）。
     * <p>触发点：区被设置/清除/微调、技能开关切换、玩家进服/退服。
     * <p>⚠️ 与磁铁屏蔽区的全服广播（MagnetZoneGlobalData）同款做法。
     */
    public static void broadcastZones(MinecraftServer server) {
        if (server == null) {
            return;
        }
        List<OperZone> zones = currentZones(server);
        org.zifeng.skilltree.network.MiningProtectS2CPacket packet =
                new org.zifeng.skilltree.network.MiningProtectS2CPacket(zones);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p != null && p.serverLevel() != null) {
                org.zifeng.skilltree.network.ModNetwork.sendToPlayer(p, packet);
            }
        }
    }

    // ============ 进服/退服：区列表变化（在线集合变了）→ 重新广播 ============

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onPlayerLoggedIn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp && sp.getServer() != null) {
            broadcastZones(sp.getServer()); // 新玩家自己的区（若有）也要让所有人看到
        }
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onPlayerLoggedOut(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp && sp.getServer() != null) {
            broadcastZones(sp.getServer()); // 离线者的区即时失效 → 通知其余玩家
        }
    }

    /** 该位置是否落在任一挖掘防护区内（先按维度过滤，再做 AABB 包含判定） */
    private static boolean isProtected(List<OperZone> zones, String dim, BlockPos pos) {
        for (OperZone z : zones) {
            if (z.dim().equals(dim) && z.contains(pos)) {
                return true;
            }
        }
        return false;
    }

    // ============ ① 左键挖掘：连挖都不开始 ============

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        // ★ 客户端：只为「不出现方块破坏进度条」（真正禁止破坏由服务端权威判定）
        if (event.getLevel().isClientSide()) {
            String dim = event.getLevel().dimension().location().toString();
            if (org.zifeng.skilltree.client.MiningProtectClientState.isProtected(dim, event.getPos())) {
                event.setCanceled(true);
            }
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (isProtected(activeZones(serverLevel),
                serverLevel.dimension().location().toString(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    // ============ ② 破坏兜底（创造速破 / 绕过挖掘进度） ============

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (isProtected(activeZones(serverLevel),
                serverLevel.dimension().location().toString(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    // ============ ③ 爆炸：把区内方块从受影响列表剔除 ============

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (event.getAffectedBlocks().isEmpty()) {
            return; // 纯伤害爆炸（无方块影响）→ 无需处理
        }
        List<OperZone> zones = activeZones(serverLevel);
        if (zones.isEmpty()) {
            return; // 没人在布防 → 直接放行（零开销）
        }
        String dim = serverLevel.dimension().location().toString();
        // 只剔除真正落在区内的方块；爆炸本身照常（伤害/粒子不受影响）
        event.getAffectedBlocks().removeIf(pos -> isProtected(zones, dim, pos));
    }
}
