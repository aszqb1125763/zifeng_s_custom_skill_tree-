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
 * 创造能量方块（测试用无限能源）：每个 tick 向 6 个方向的相邻能量存储灌满 Integer.MAX_VALUE FE。
 * 灵感来自 Mekanism 的创造能源方块（Creative Energy Cube）。
 */
public class CreativeEnergyBlockEntity extends BlockEntity {

    private static final int OUTPUT_PER_TICK = Integer.MAX_VALUE;

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

    /** 由方块 getTicker 驱动的服务端每 tick 逻辑：向 6 方向邻居灌满 FE */
    public static void serverTick(Level level, BlockPos pos, BlockState state, CreativeEnergyBlockEntity be) {
        if (level == null || level.isClientSide) {
            return;
        }
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            IEnergyStorage cap = level.getCapability(Capabilities.EnergyStorage.BLOCK, neighborPos, direction.getOpposite());
            if (cap != null && cap.canReceive()) {
                cap.receiveEnergy(OUTPUT_PER_TICK, false);
            }
        }
    }
}
