package org.zifeng.skilltree.client;

import net.minecraft.core.BlockPos;
import org.zifeng.skilltree.data.OperZone;

import java.util.ArrayList;
import java.util.List;

/**
 * 挖掘防护区客户端缓存（★ 2026-10-11）。
 *
 * <p>保存服务端广播的<b>全服</b>挖掘防护区（含别人的），供客户端在
 * {@code LeftClickBlock} 里判断"这个方块受保护" → 取消事件 →
 * 原版不会开始破坏 → <b>不出现方块破坏进度条</b>。
 *
 * <p>⚠️ 这里只做<b>观感</b>层拦截；真正的禁止破坏由服务端 {@code MiningProtectEvents} 权威判定。
 */
public final class MiningProtectClientState {
    private MiningProtectClientState() {
    }

    private static List<OperZone> zones = List.of();

    /** 服务端广播校准（做成不可变副本，避免跨线程看到半更新列表） */
    public static void setZones(List<OperZone> newZones) {
        zones = newZones == null ? List.of() : new ArrayList<>(newZones);
    }

    /** 该位置是否受挖掘防护（全服任一区命中即 true） */
    public static boolean isProtected(String dim, BlockPos pos) {
        if (zones.isEmpty() || pos == null) {
            return false;
        }
        for (OperZone z : zones) {
            // ⚠️ 列表可能在网络线程被整体替换 → 取本地引用遍历，天然线程安全
            if (z.dim().equals(dim) && z.contains(pos)) {
                return true;
            }
        }
        return false;
    }

    /** 会话重置（进服/换维度/重连）：换服后旧服务器的区不能残留 */
    public static void resetSession() {
        zones = List.of();
    }
}
