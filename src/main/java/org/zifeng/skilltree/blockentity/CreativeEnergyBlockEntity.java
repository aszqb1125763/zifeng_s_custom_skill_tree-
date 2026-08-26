package org.zifeng.skilltree.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.energy.IEnergyStorage;
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
    /** 每方向每 tick 循环灌上限：100000 次 × 21.4亿 ≈ 2140 万亿 FE/t（等效 64 位无限输出） */
    private static final int MAX_LOOPS_PER_TICK = 100_000;

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

    /** 1.20.1 Forge capability：暴露 FE 能量存储（IEnergyStorage） */
    @Override
    public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(
            net.minecraftforge.common.capabilities.Capability<T> cap, net.minecraft.core.Direction side) {
        if (cap == net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY) {
            return net.minecraftforge.common.util.LazyOptional.of(() -> energyStorage).cast();
        }
        return super.getCapability(cap, side);
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
            // 1.20.1：通过相邻 BlockEntity 获取 capability（Level.getCapability(BlockPos) 是 1.21 API）
            BlockEntity neighbor = level.getBlockEntity(neighborPos);
            if (neighbor == null) {
                continue;
            }
            net.minecraftforge.common.util.LazyOptional<IEnergyStorage> capOpt =
                    neighbor.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY, direction.getOpposite());
            if (capOpt.isPresent()) {
                IEnergyStorage cap = capOpt.orElse(null);
                if (cap != null && cap.canReceive()) {
                    // 64 位无限输出：单次 int 接口上限 21.4 亿 → 循环灌直到目标拒绝（满/速率限制）。
                    // 目标有速率限制时第一次就返回剩余额度、第二次返回 0 立即 break（不浪费）；
                    // 目标无限制（如转换机开无限制输入）时持续灌，每 tick 累积远超 21.4 亿。
                    for (int i = 0; i < MAX_LOOPS_PER_TICK; i++) {
                        if (cap.receiveEnergy(OUTPUT_PER_TICK, false) <= 0) {
                            break; // 目标已满 / 速率限制拒绝
                        }
                    }
                }
            }
        }
    }
}
