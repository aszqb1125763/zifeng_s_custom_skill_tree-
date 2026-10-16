package org.zifeng.skilltree.system;

/**
 * 服务器主线程 tick 时钟（2026-09-12 1.4.1 新增）。
 *
 * <p><b>用途</b>：让「选区作业」等<b>可分批的工作</b>按「本 tick 还剩多少时间」来分配工作，
 * 而不是用固定预算（固定预算在空闲服上太保守、在忙碌服上会雪上加霜）。
 *
 * <p><b>为什么可行</b>：Z-Link 模块在 {@code ServerTickEvent} 的末尾运行，
 * 此时本 tick 的实体/方块/网络等主要工作都已完成 → {@code now - tickStart} 就是"本 tick 已耗时"，
 * 于是「截止时刻 = tickStart + 目标 MSPT」即可把作业夹到目标之内。
 *
 * <p>实现只用 {@code System.nanoTime()}：无反射、无平台差异（1.20.1 / 1.21.1 同一份代码）。
 */
public final class TickClock {
    private TickClock() {
    }

    /** 本 tick 起始时刻（nanoTime）；0 = 未标记 */
    private static volatile long tickStartNanos = 0L;

    /** tick 开始时调用（Forge：{@code ServerTickEvent} START 阶段；NeoForge：{@code ServerTickEvent.Pre}） */
    public static void markTickStart() {
        tickStartNanos = System.nanoTime();
    }

    /** 服务器停止时清空（避免跨存档残留旧时间戳） */
    public static void reset() {
        tickStartNanos = 0L;
    }

    /**
     * 本 tick 起始时刻（nanoTime）。
     * <p>若从未标记、或时间戳已过旧（&gt;3 秒，说明不在正常 tick 序列内，例如在非主线程路径被调用），
     * 返回 <b>0</b> 表示不可用 —— 调用方应回退为固定预算策略。
     */
    public static long tickStartNanos() {
        long t = tickStartNanos;
        if (t == 0L) {
            return 0L;
        }
        if (System.nanoTime() - t > 3_000_000_000L) {
            return 0L; // 过旧：本 tick 时钟不可信
        }
        return t;
    }
}
