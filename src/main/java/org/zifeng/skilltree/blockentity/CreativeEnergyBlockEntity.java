package org.zifeng.skilltree.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.zifeng.skilltree.init.ModBlockEntities;

/**
 * 创造能量方块（测试用无限能源，参考 Mekanism 创造能量立方）：
 * 每个 tick 向 6 个方向的相邻能量存储循环灌满 FE。
 * ⚠️ NeoForge 1.21.1 的 IEnergyStorage 是 int 接口（单次上限 21.4 亿），
 * 通过【循环灌直到目标拒绝】等效实现 64 位无限输出（每次灌满 int 上限，循环累积）。
 */
public class CreativeEnergyBlockEntity extends BlockEntity {

    private static final int OUTPUT_PER_TICK = Integer.MAX_VALUE;
    /** 每方向每 tick 循环灌上限（备选安全上限，防止无限循环） */
    private static final int MAX_LOOPS_PER_TICK = 100_000;
    /**
     * 单方向单 tick 时间预算（纳秒）。
     * <p>⚠️ 2026-09-11 性能修复：原实现硬跑 {@code MAX_LOOPS_PER_TICK} 次循环，
     * 目标无速率限制时每个方块每 tick 最多 6×100000 = <b>60 万次</b>跨模组虚接口调用，
     * 多方块叠加直接卡服务器。改为「循环到目标拒绝 <b>或</b> 超出本预算为止」：
     * 正常目标（有速率限制/会灌满）行为完全不变，极端情况也不再挂住 tick。
     */
    private static final long DIR_TIME_BUDGET_NANOS = 1_000_000L; // 1ms/方向，6 方向共 6ms 上限

    private final IEnergyStorage energyStorage = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return 0; // 只出不进
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return Math.max(0, maxExtract); // 无限
        }

        @Override
        public int getEnergyStored() {
            return Integer.MAX_VALUE;
        }

        @Override
        public int getMaxEnergyStored() {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    };

    public CreativeEnergyBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CREATIVE_ENERGY.get(), pos, state);
    }

    public IEnergyStorage getEnergyStorage() {
        return energyStorage;
    }

    /** 由方块 getTicker 驱动的服务端每 tick 逻辑：向 6 方向邻居循环灌满 FE（64 位无限输出） */
    public static void serverTick(Level level, BlockPos pos, BlockState state, CreativeEnergyBlockEntity be) {
        if (level == null || level.isClientSide) {
            return;
        }
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            IEnergyStorage cap = level.getCapability(Capabilities.EnergyStorage.BLOCK, neighborPos, direction.getOpposite());
            if (cap != null && cap.canReceive()) {
                // 64 位无限输出：单次 int 接口上限 21.4 亿 → 循环灌直到目标拒绝（满/速率限制）。
                // 目标有速率限制时第一次就返回剩余额度、第二次返回 0 立即 break（不浪费）；
                // 目标无限制（如转换机开无限制输入）时持续灌，累积远超 21.4 亿。
                // ⚠️ 2026-09-11：加时间预算，避免无限制目标把 tick 卡死（见常量注释）。
                long deadline = System.nanoTime() + DIR_TIME_BUDGET_NANOS;
                for (int i = 0; i < MAX_LOOPS_PER_TICK; i++) {
                    if (cap.receiveEnergy(OUTPUT_PER_TICK, false) <= 0) {
                        break; // 目标已满 / 速率限制拒绝
                    }
                    if (System.nanoTime() >= deadline) {
                        break; // 本方向时间预算用尽，让出 tick（下一 tick 继续灌）
                    }
                }
            }
        }
    }
}
