package org.zifeng.skilltree.data;

/**
 * 木棍工具·操作区（2026-09-08，单人生效，存玩家 PlayerSkillRecord）：
 * 机械共鸣区块技能（放置/挖掘/攻击/防护）各自框选的一块区域。
 * 维度 + AABB 两角（a/b 不要求顺序，读取用 min/max 归一化）；单边 ≤64 格。
 */
public record OperZone(String dim, int ax, int ay, int az, int bx, int by, int bz) {
    public int minX() {
        return Math.min(ax, bx);
    }

    public int minY() {
        return Math.min(ay, by);
    }

    public int minZ() {
        return Math.min(az, bz);
    }

    public int maxX() {
        return Math.max(ax, bx);
    }

    public int maxY() {
        return Math.max(ay, by);
    }

    public int maxZ() {
        return Math.max(az, bz);
    }

    /** 点是否落在区内（含边界） */
    public boolean contains(String zoneDim, double x, double y, double z) {
        return dim.equals(zoneDim)
                && x >= minX() && x <= maxX() + 1
                && y >= minY() && y <= maxY() + 1
                && z >= minZ() && z <= maxZ() + 1;
    }

    /** 方块格是否落在区内（含边界；调用方自行校验维度） */
    public boolean contains(net.minecraft.core.BlockPos pos) {
        return pos.getX() >= minX() && pos.getX() <= maxX()
                && pos.getY() >= minY() && pos.getY() <= maxY()
                && pos.getZ() >= minZ() && pos.getZ() <= maxZ();
    }

    /** 遍历区内全部方块格（含边界两角；先 X 后 Y 后 Z） */
    public Iterable<net.minecraft.core.BlockPos> blockPositions() {
        return net.minecraft.core.BlockPos.betweenClosed(minX(), minY(), minZ(), maxX(), maxY(), maxZ());
    }

    /** 实体碰撞箱 AABB（区域外扩 1 格，实体中心/体积容差） */
    public net.minecraft.world.phys.AABB aabb() {
        return new net.minecraft.world.phys.AABB(minX() - 1.0, minY() - 1.0, minZ() - 1.0,
                maxX() + 2.0, maxY() + 2.0, maxZ() + 2.0);
    }
}
