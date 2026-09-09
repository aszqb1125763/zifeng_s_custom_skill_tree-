package org.zifeng.skilltree.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import org.zifeng.skilltree.SkillTreeMod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 磁铁屏蔽区 · 服务器全局数据（2026-09-08 架构升级：从单人私有 → 全服共享，SavedData 存主世界）。
 * <p>⚡ 任何一个玩家框选的屏蔽区对【全服所有玩家】的磁铁生效；增删后向所有在线玩家广播
 * （事件驱动：仅变更时广播全量小包，不做轮询——沿用 GlobalStateSync 推送架构的优化思路）。
 * <p>删除权：谁都能删（RANGE 模块 + 潜行左键对准红框，服务端射线删除）。
 */
public class MagnetZoneGlobalData extends SavedData {
    public static final String DATA_NAME = SkillTreeMod.MOD_ID + "_magnet_zones";

    private final List<MagnetExclusionZone> zones = new ArrayList<>();

    public List<MagnetExclusionZone> getZones() {
        return Collections.unmodifiableList(zones);
    }

    /** 新增屏蔽区（角点自动归一化；单边 ≤64；重复拒绝）。成功 → 持久化 + 全服广播。 */
    public boolean addZone(String dim, int ax, int ay, int az, int bx, int by, int bz) {
        if (dim == null || dim.isBlank()) {
            return false;
        }
        int minX = Math.min(ax, bx), maxX = Math.max(ax, bx);
        int minY = Math.min(ay, by), maxY = Math.max(ay, by);
        int minZ = Math.min(az, bz), maxZ = Math.max(az, bz);
        if (maxX - minX > 64 || maxY - minY > 64 || maxZ - minZ > 64) {
            return false;
        }
        if (zones.stream().anyMatch(z -> z.dim().equals(dim)
                && z.minX() == minX && z.minY() == minY && z.minZ() == minZ
                && z.maxX() == maxX && z.maxY() == maxY && z.maxZ() == maxZ)) {
            return false; // 重复
        }
        zones.add(new MagnetExclusionZone(dim, minX, minY, minZ, maxX, maxY, maxZ));
        afterChange();
        return true;
    }

    /** 射线删除命中区（返回是否删了）。成功 → 持久化 + 全服广播。 */
    public boolean removeZoneAt(String dim, net.minecraft.world.phys.Vec3 rayFrom, net.minecraft.world.phys.Vec3 rayDir) {
        MagnetExclusionZone best = null;
        double bestDist = Double.MAX_VALUE;
        for (MagnetExclusionZone z : zones) {
            if (!z.dim().equals(dim)) {
                continue;
            }
            net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
                    z.minX(), z.minY(), z.minZ(), z.maxX() + 1, z.maxY() + 1, z.maxZ() + 1);
            var hit = box.clip(rayFrom, rayFrom.add(rayDir.scale(200))).orElse(null);
            if (hit != null) {
                double dist = rayFrom.distanceToSqr(hit);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = z;
                }
            }
        }
        if (best != null) {
            zones.remove(best);
            afterChange();
            return true;
        }
        return false;
    }

    /** 增删后：持久化 + 向全服在线玩家广播最新列表（事件驱动，全量小包，无轮询） */
    private void afterChange() {
        setDirty();
        net.minecraft.server.MinecraftServer server = currentServer();
        if (server == null) {
            return;
        }
        var packet = new org.zifeng.skilltree.network.MagnetExclusionS2CPacket(zones);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p != null && p.serverLevel() != null) {
                org.zifeng.skilltree.network.ModNetwork.sendToPlayer(p, packet);
            }
        }
    }

    // ============ 静态获取（主世界 SavedData，服务器生命周期缓存） ============

    private static volatile net.minecraft.server.MinecraftServer cachedServer;
    private static volatile MagnetZoneGlobalData cachedData;

    public static MagnetZoneGlobalData get(ServerLevel level) {
        net.minecraft.server.MinecraftServer server = level.getServer();
        MagnetZoneGlobalData cached = cachedData;
        if (cached != null && cachedServer == server) {
            return cached;
        }
        ServerLevel overworld = server.overworld();
        MagnetZoneGlobalData data = overworld.getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(MagnetZoneGlobalData::new, MagnetZoneGlobalData::load), DATA_NAME);
        cachedServer = server;
        cachedData = data;
        return data;
    }

    public static MagnetZoneGlobalData load(CompoundTag tag, HolderLookup.Provider registries) {
        MagnetZoneGlobalData data = new MagnetZoneGlobalData();
        ListTag zoneList = tag.getList("Zones", Tag.TAG_COMPOUND);
        for (int i = 0; i < zoneList.size(); i++) {
            MagnetExclusionZone z = MagnetExclusionZone.fromNbt(zoneList.getCompound(i));
            if (z != null) {
                data.zones.add(z);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag zoneList = new ListTag();
        for (MagnetExclusionZone z : zones) {
            zoneList.add(z.toNbt());
        }
        tag.put("Zones", zoneList);
        return tag;
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
