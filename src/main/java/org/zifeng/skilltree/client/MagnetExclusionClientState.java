package org.zifeng.skilltree.client;

import org.zifeng.skilltree.data.MagnetExclusionZone;

import java.util.ArrayList;
import java.util.List;

/**
 * 磁铁屏蔽区客户端状态（2026-09-07 / 2026-09-08 全局共享）：
 * <ul>
 *   <li>服务端广播的全服屏蔽区列表（全服同一份，渲染红框用）</li>
 *   <li>选区进行中状态（第一角；选第二角成区后清）——纯客户端本地</li>
 * </ul>
 */
public final class MagnetExclusionClientState {
    private MagnetExclusionClientState() {
    }

    /** 服务端广播的全服屏蔽区（客户端渲染缓存） */
    private static volatile List<MagnetExclusionZone> zones = new ArrayList<>();

    /** 选区第一角（null = 未开始选）；仅客户端本地 */
    private static volatile net.minecraft.core.BlockPos firstCorner = null;

    /** 断开连接清空（防跨服残留） */
    public static void onDisconnect() {
        synchronized (MagnetExclusionClientState.class) {
            zones = new ArrayList<>();
            firstCorner = null;
        }
    }

    public static List<MagnetExclusionZone> getZones() {
        return zones;
    }

    public static void setZones(List<MagnetExclusionZone> list) {
        if (list == null) {
            return;
        }
        synchronized (MagnetExclusionClientState.class) {
            zones = new ArrayList<>(list);
        }
    }

    /** 当前选区第一角（null = 无进行中） */
    public static net.minecraft.core.BlockPos getFirstCorner() {
        return firstCorner;
    }

    public static void setFirstCorner(net.minecraft.core.BlockPos pos) {
        firstCorner = pos;
    }
}
