package org.zifeng.skilltree.data;

/**
 * 木棍工具·操作区（2026-09-08，单人生效，存玩家 PlayerSkillRecord）：
 * 机械共鸣区块技能（放置/挖掘/攻击/防护）各自框选的一块区域。
 * 维度 + AABB 两角（a/b 不要求顺序，读取用 min/max 归一化）；单边 ≤ {@link #MAX_SIDE} 格。
 */
public record OperZone(String dim, int ax, int ay, int az, int bx, int by, int bz) {
    /**
     * 选区单边最大跨度（相邻两角坐标差上限：{@code max - min <= MAX_SIDE}）。
     * <p><b>区块技能与磁铁屏蔽区共用本常量</b>——两处客户端本地校验 + 服务端权威校验都必须用它，
     * 避免改一处漏一处导致「客户端能框、服务端拒绝」的分歧。
     * <p>⚠️ 2026-09-12（1.4.1）由 <b>64 提升到 256</b>（用户需求）。
     * 注意：大选区由 {@code ZoneSkillEvents} 分 tick 处理（不再单帧卡死），
     * 远超阈值时客户端会提示可能卡顿（见 {@code ZoneSelectionInputHandler}）。
     */
    public static final int MAX_SIDE = 256;

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

    /**
     * 按「面 + 带符号步进」微调选区（2026-09-12 1.4.1，木棍滚轮微调用）。
     *
     * <p><b>面语义 = 该面朝【外】移动</b>（区域变大）：
     * {@code WEST→minX 减小}、{@code EAST→maxX 增大}、{@code DOWN→minY 减小}、
     * {@code UP→maxY 增大}、{@code NORTH→minZ 减小}、{@code SOUTH→maxZ 增大}。
     * <p>例：站在区域西侧看向东侧 → 射线命中 {@code WEST} 面（正对你的近面）→
     * 滚轮向上一格 = {@code delta=+1} = {@code minX -= 1}（该面远离你，区域变大）。
     *
     * @param face  要调整的面（由射线命中判定；传 null 返回 null）
     * @param delta 带符号步进格数：正 = 向外扩，负 = 向内缩（0 也返回 null）
     * @return 调整后的新区（已归一化）；<b>null = 非法</b>：会翻转（min>max）或单边超 {@link #MAX_SIDE}
     */
    public OperZone adjust(net.minecraft.core.Direction face, int delta) {
        if (face == null || delta == 0) {
            return null;
        }
        int nMinX = minX(), nMinY = minY(), nMinZ = minZ();
        int nMaxX = maxX(), nMaxY = maxY(), nMaxZ = maxZ();
        switch (face) {
            case WEST -> nMinX -= delta;
            case EAST -> nMaxX += delta;
            case DOWN -> nMinY -= delta;
            case UP -> nMaxY += delta;
            case NORTH -> nMinZ -= delta;
            case SOUTH -> nMaxZ += delta;
            default -> {
                return null;
            }
        }
        if (nMinX > nMaxX || nMinY > nMaxY || nMinZ > nMaxZ) {
            return null; // 会翻转 → 至少保留 1 格
        }
        if (nMaxX - nMinX > MAX_SIDE || nMaxY - nMinY > MAX_SIDE || nMaxZ - nMinZ > MAX_SIDE) {
            return null; // 超单边上限
        }
        return new OperZone(dim, nMinX, nMinY, nMinZ, nMaxX, nMaxY, nMaxZ);
    }

    /** 实体碰撞箱 AABB（区域外扩 1 格，实体中心/体积容差） */
    public net.minecraft.world.phys.AABB aabb() {
        return new net.minecraft.world.phys.AABB(minX() - 1.0, minY() - 1.0, minZ() - 1.0,
                maxX() + 2.0, maxY() + 2.0, maxZ() + 2.0);
    }
}
