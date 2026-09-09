package org.zifeng.skilltree.client;

/**
 * 机械共鸣·操作区 选区本地状态（2026-09-08，1.20.1）：
 * 选区进行中状态（第一角；选第二角成区后清）——纯客户端本地，仅当前工具模式下有效。
 */
public final class ZoneExclusionClientState {
    private ZoneExclusionClientState() {
    }

    /** 选区第一角（null = 未开始选）；仅客户端本地 */
    private static volatile net.minecraft.core.BlockPos firstCorner = null;

    /** 断开连接清空（防跨服残留） */
    public static void onDisconnect() {
        firstCorner = null;
    }

    /** 当前选区第一角（null = 无进行中） */
    public static net.minecraft.core.BlockPos getFirstCorner() {
        return firstCorner;
    }

    public static void setFirstCorner(net.minecraft.core.BlockPos pos) {
        firstCorner = pos;
    }
}
