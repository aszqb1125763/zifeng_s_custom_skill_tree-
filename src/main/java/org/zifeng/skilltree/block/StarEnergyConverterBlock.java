package org.zifeng.skilltree.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import org.zifeng.skilltree.blockentity.StarEnergyConverterBlockEntity;
import org.zifeng.skilltree.init.ModBlockEntities;

/**
 * 星能转换机：可输入 FE 能量，每消耗 1 亿能量转换 1 点技能点。
 * 模型/贴图参考原版熔炉 + 红石与铁块细节。
 */
public class StarEnergyConverterBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<StarEnergyConverterBlock> CODEC = simpleCodec(StarEnergyConverterBlock::new);

    public StarEnergyConverterBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public MapCodec<StarEnergyConverterBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StarEnergyConverterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return blockEntityType == ModBlockEntities.STAR_ENERGY_CONVERTER.get()
                ? (lvl, pos, st, be) -> StarEnergyConverterBlockEntity.serverTick(lvl, pos, st, (StarEnergyConverterBlockEntity) be)
                : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide) {
            if (level.getBlockEntity(pos) instanceof StarEnergyConverterBlockEntity converter) {
                player.openMenu(converter);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
