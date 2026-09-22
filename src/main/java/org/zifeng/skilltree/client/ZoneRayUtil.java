package org.zifeng.skilltree.client;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 选区滚轮微调的<b>共享纯几何工具</b>（★ 2026-09-22 提取）。
 *
 * <h2>为什么要提取</h2>
 * 「对准已框选的区 → 滚轮调整正对玩家的那个面」这套逻辑原本只实现在
 * {@link ZoneSelectionInputHandler}（机械共鸣区块技能），
 * 而磁铁屏蔽区（{@link MagnetExclusionInputHandler}）<b>完全没有滚轮微调</b> ——
 * 用户反馈「区块拆解的选区各种滚轮操作，没有同步到各种选区操作，比如磁铁这些」。
 * <p>把「射线与盒求交 → 近面」这段纯几何抽出来，两种区（{@code OperZone} /
 * {@code MagnetExclusionZone}）共用同一份实现，保证行为逐像素一致，
 * 以后再加新的可选区功能直接复用即可。
 */
public final class ZoneRayUtil {
    private ZoneRayUtil() {
    }

    /** 浮点比较阈值（轴对齐判定的零向量判定） */
    private static final double EPS = 1.0E-7;

    /**
     * 由两个端点构造选区 AABB（含边界 → 上界 +1，与渲染/交互口径一致）。
     * <p>{@code min/max} 由调用方保证已归一化。
     */
    public static AABB boxOf(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return new AABB(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D);
    }

    /**
     * 求射线与矩形盒相交时「正对射线起点」的面（近面）。
     * <ul>
     *   <li>起点在盒外 → 返回入射面（标准 slab 法；未命中返回 {@code null}）</li>
     *   <li>起点在盒内 → 返回视线穿出的那个面（此时"近面"退化为视线一侧）</li>
     * </ul>
     *
     * <p>⚠️ 不用 {@code AABB.clip(Iterable, Vec3, Vec3, BlockPos)}：源码里它会先把每个盒
     * <b>平移指定偏移</b>（{@code aabb.move(pos)}，偏移不是盒位置而是位移量）→ 极易误用；
     * 它也不处理起点在盒内（此时返回 null）。本算法对两种情形都确定。
     *
     * @param box  选区盒
     * @param from 射线起点（玩家眼位）
     * @param to   射线终点
     * @return 近面；未命中返回 {@code null}
     */
    public static Direction rayFace(AABB box, Vec3 from, Vec3 to) {
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        boolean inside = from.x > box.minX && from.x < box.maxX
                && from.y > box.minY && from.y < box.maxY
                && from.z > box.minZ && from.z < box.maxZ;
        if (inside) {
            // 起点在盒内：取三轴出口中最近的那个面
            double best = Double.MAX_VALUE;
            Direction face = null;
            if (dx > EPS) {
                best = (box.maxX - from.x) / dx;
                face = Direction.EAST;
            } else if (dx < -EPS) {
                best = (box.minX - from.x) / dx;
                face = Direction.WEST;
            }
            if (dy > EPS) {
                double t = (box.maxY - from.y) / dy;
                if (t < best) {
                    best = t;
                    face = Direction.UP;
                }
            } else if (dy < -EPS) {
                double t = (box.minY - from.y) / dy;
                if (t < best) {
                    best = t;
                    face = Direction.DOWN;
                }
            }
            if (dz > EPS) {
                double t = (box.maxZ - from.z) / dz;
                if (t < best) {
                    face = Direction.SOUTH;
                }
            } else if (dz < -EPS) {
                double t = (box.minZ - from.z) / dz;
                if (t < best) {
                    face = Direction.NORTH;
                }
            }
            return face;
        }
        // 起点在盒外：标准 slab（tMin 所在轴 = 入射面）
        double tMin = 0.0D, tMax = 1.0D;
        Direction face = null;
        if (Math.abs(dx) < EPS) {
            if (from.x < box.minX || from.x > box.maxX) {
                return null;
            }
        } else {
            double t1 = (box.minX - from.x) / dx, t2 = (box.maxX - from.x) / dx;
            double tNear = Math.min(t1, t2), tFar = Math.max(t1, t2);
            if (tNear > tMin) {
                tMin = tNear;
                face = dx > 0 ? Direction.WEST : Direction.EAST;
            }
            tMax = Math.min(tMax, tFar);
        }
        if (Math.abs(dy) < EPS) {
            if (from.y < box.minY || from.y > box.maxY) {
                return null;
            }
        } else {
            double t1 = (box.minY - from.y) / dy, t2 = (box.maxY - from.y) / dy;
            double tNear = Math.min(t1, t2), tFar = Math.max(t1, t2);
            if (tNear > tMin) {
                tMin = tNear;
                face = dy > 0 ? Direction.DOWN : Direction.UP;
            }
            tMax = Math.min(tMax, tFar);
        }
        if (Math.abs(dz) < EPS) {
            if (from.z < box.minZ || from.z > box.maxZ) {
                return null;
            }
        } else {
            double t1 = (box.minZ - from.z) / dz, t2 = (box.maxZ - from.z) / dz;
            double tNear = Math.min(t1, t2), tFar = Math.max(t1, t2);
            if (tNear > tMin) {
                tMin = tNear;
                face = dz > 0 ? Direction.NORTH : Direction.SOUTH;
            }
            tMax = Math.min(tMax, tFar);
        }
        return tMin > tMax ? null : face;
    }
}
