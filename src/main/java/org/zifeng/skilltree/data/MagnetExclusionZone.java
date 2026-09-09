package org.zifeng.skilltree.data;

/**
 * 磁铁屏蔽区（2026-09-07 建 / 2026-09-08 提升为全局共享类型，1.20.1）：
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
}
