package org.zifeng.skilltree.client;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 客户端：杀戮光环·飞剑视觉状态（2026-10-21，支持多把飞剑同时飞行）。
 *
 * <p>服务端每派出一把飞剑就发一条 {@code AuraSwordLaunchS2CPacket}，这里按列表保存；
 * 飞剑命中时再发 {@code AuraBurstS2CPacket}，同样按列表保存（可同时有多个爆炸点）。
 */
public final class AuraSwordClientState {

    /** 红环动画时长（毫秒） */
    public static final float BLAST_DURATION_MS = 1200f;
    /** 实体飞行速度（格/tick），与服务端 AuraSwordEntity.SPEED 保持一致 */
    /** 平均飞行速度（格/tick） */
    public static final double SWORD_SPEED_PER_TICK = 0.85;
    /** 最多同时存在的飞剑视觉（= 剑阵 8 把） */
    public static final int MAX_LAUNCHES = 8;

    /** 一把飞剑的飞行记录 */
    public record Launch(Vec3 start, Vec3 target, long startMillis, float durationMs, float speed) {
    }

    /** 一个爆炸点的红环记录 */
    public record Blast(Vec3 center, long startMillis) {
    }

    private static final List<Launch> LAUNCHES = new ArrayList<>();
    private static final List<Blast> BLASTS = new ArrayList<>();

    private AuraSwordClientState() {
    }

    /** 飞剑发射：按距离估算飞行时长（与实体速度同步） */
    public static void onLaunch(Vec3 start, Vec3 target, float speed) {
        final double dist = start.distanceTo(target);
        final float s = Math.max(0.2f, speed);
        final float duration = (float) Math.max(120.0, Math.min(1600.0, dist / (SWORD_SPEED_PER_TICK * s) * 50.0));
        synchronized (LAUNCHES) {
            LAUNCHES.add(new Launch(start, target, System.currentTimeMillis(), duration, s));
            while (LAUNCHES.size() > MAX_LAUNCHES) {
                LAUNCHES.remove(0);
            }
        }
    }

    /** @return 0~1 的飞行进度；-1 = 该飞剑已结束 */
    public static float launchProgress(Launch launch) {
        final long age = System.currentTimeMillis() - launch.startMillis();
        if (age >= launch.durationMs()) {
            return -1f;
        }
        return age / launch.durationMs();
    }

    /** 当前所有还在飞的飞剑（顺带清理过期的） */
    public static List<Launch> launches() {
        final List<Launch> out = new ArrayList<>(4);
        synchronized (LAUNCHES) {
            final Iterator<Launch> it = LAUNCHES.iterator();
            while (it.hasNext()) {
                final Launch l = it.next();
                if (launchProgress(l) < 0.0f) {
                    it.remove();
                } else {
                    out.add(l);
                }
            }
        }
        return out;
    }

    public static void onBlast(Vec3 center) {
        synchronized (BLASTS) {
            BLASTS.add(new Blast(center, System.currentTimeMillis()));
            while (BLASTS.size() > MAX_LAUNCHES) {
                BLASTS.remove(0);
            }
        }
    }

    /** @return 0~1 的爆炸进度；-1 = 已结束 */
    public static float blastProgress(Blast blast) {
        final long age = System.currentTimeMillis() - blast.startMillis();
        if (age >= BLAST_DURATION_MS) {
            return -1f;
        }
        return age / BLAST_DURATION_MS;
    }

    /** 当前所有还在播放的红环（顺带清理过期的） */
    public static List<Blast> blasts() {
        final List<Blast> out = new ArrayList<>(4);
        synchronized (BLASTS) {
            final Iterator<Blast> it = BLASTS.iterator();
            while (it.hasNext()) {
                final Blast b = it.next();
                if (blastProgress(b) < 0.0f) {
                    it.remove();
                } else {
                    out.add(b);
                }
            }
        }
        return out;
    }

    public static void clear() {
        synchronized (LAUNCHES) {
            LAUNCHES.clear();
        }
        synchronized (BLASTS) {
            BLASTS.clear();
        }
    }
}
