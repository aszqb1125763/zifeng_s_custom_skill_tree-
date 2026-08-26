package org.zifeng.skilltree.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.zifeng.skilltree.blockentity.CreativeEnergyBlockEntity;
import org.zifeng.skilltree.init.ModBlockEntities;

/**
 * 创造能量方块（测试用无限能源）：持续向相邻方块/机器输出海量 FE，灵感来自 Mekanism 的创造能源方块。
 */
public class CreativeEnergyBlock extends Block implements EntityBlock {

    public CreativeEnergyBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CreativeEnergyBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return blockEntityType == ModBlockEntities.CREATIVE_ENERGY.get()
                ? (lvl, pos, st, be) -> CreativeEnergyBlockEntity.serverTick(lvl, pos, st, (CreativeEnergyBlockEntity) be)
                : null;
    }
}
