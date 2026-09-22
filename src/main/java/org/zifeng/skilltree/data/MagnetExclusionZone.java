package org.zifeng.skilltree.data;

/**
 * 磁铁屏蔽区（2026-09-07 建 / 2026-09-08 提升为全局共享类型）：
 * <p>⚡ 全服共享：任何一个玩家框选的屏蔽区对【所有玩家】的磁铁技能生效——
 * 区内掉落物任何磁铁都吸不走（经验球不屏蔽）。区不归属于创建者，谁都能删（潜行+左键）。
 * <p>维度 + AABB 两角（内部以 a=最小角 b=最大角归一化存储）；单边 ≤64 格。
 */
public record MagnetExclusionZone(String dim, int ax, int ay, int az, int bx, int by, int bz) {
    /** 最小角 X */
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

    /** 从 NBT 恢复 */
    public static MagnetExclusionZone fromNbt(net.minecraft.nbt.CompoundTag zt) {
        String dim = zt.getString("Dim");
        if (dim.isBlank()) {
            return null;
        }
        return new MagnetExclusionZone(dim,
                zt.getInt("AX"), zt.getInt("AY"), zt.getInt("AZ"),
                zt.getInt("BX"), zt.getInt("BY"), zt.getInt("BZ"));
    }

    /** 写入 NBT */
    public net.minecraft.nbt.CompoundTag toNbt() {
        net.minecraft.nbt.CompoundTag zt = new net.minecraft.nbt.CompoundTag();
        zt.putString("Dim", dim);
        zt.putInt("AX", ax);
        zt.putInt("AY", ay);
        zt.putInt("AZ", az);
        zt.putInt("BX", bx);
        zt.putInt("BY", by);
        zt.putInt("BZ", bz);
        return zt;
    }

    /**
     * 按「面 + 带符号步进」微调屏蔽区（★ 2026-09-22 新增，木棍滚轮微调用）。
     *
     * <p><b>背景</b>：滚轮微调原先只实现在机械共鸣区块技能上
     * （{@code OperZone.adjust}），磁铁屏蔽区没有 —— 用户反馈
     * 「区块拆解的选区各种滚轮操作，没有同步到各种选区操作，比如磁铁这些」。
     * 本方法语义与 {@code OperZone.adjust} <b>逐字对齐</b>，保证两种区手感一致。
     *
     * <p><b>面语义 = 该面朝【外】移动</b>（区域变大）：
     * {@code WEST→minX 减小}、{@code EAST→maxX 增大}、{@code DOWN→minY 减小}、
     * {@code UP→maxY 增大}、{@code NORTH→minZ 减小}、{@code SOUTH→maxZ 增大}。
     * <p>例：站在区域西侧看向东侧 → 射线命中 {@code WEST} 面（正对你的近面）→
     * 滚轮向上一格 = {@code delta=+1} = {@code minX -= 1}（该面远离你，区域变大）。
     *
     * @param face  要调整的面（由射线命中判定；传 null 返回 null）
     * @param delta 带符号步进格数：正 = 向外扩，负 = 向内缩（0 也返回 null）
     * @return 调整后的新区（已归一化）；<b>null = 非法</b>：
     *         会翻转（min &gt; max）或单边超 {@link OperZone#MAX_SIDE}
     */
    public MagnetExclusionZone adjust(net.minecraft.core.Direction face, int delta) {
        if (face == null || delta == 0) {
            return null;
        }
        int nMinX = minX(), nMaxX = maxX();
        int nMinY = minY(), nMaxY = maxY();
        int nMinZ = minZ(), nMaxZ = maxZ();
        switch (face) {
            case WEST -> nMinX -= delta;
            case EAST -> nMaxX += delta;
            case DOWN -> nMinY -= delta;
            case UP -> nMaxY += delta;
            case NORTH -> nMinZ -= delta;
            case SOUTH -> nMaxZ += delta;
        }
        // 非法 1：翻转（向内缩过头）→ 拒绝
        if (nMinX > nMaxX || nMinY > nMaxY || nMinZ > nMaxZ) {
            return null;
        }
        // 非法 2：单边超上限（与客户端本地校验、服务端 ADD 校验同一常量）
        if (nMaxX - nMinX > OperZone.MAX_SIDE
                || nMaxY - nMinY > OperZone.MAX_SIDE
                || nMaxZ - nMinZ > OperZone.MAX_SIDE) {
            return null;
        }
        return new MagnetExclusionZone(dim, nMinX, nMinY, nMinZ, nMaxX, nMaxY, nMaxZ);
    }
}
