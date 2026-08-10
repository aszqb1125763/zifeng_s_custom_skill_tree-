package org.zifeng.skilltree.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import org.zifeng.skilltree.blockentity.SkillPointConverterBlockEntity;
import org.zifeng.skilltree.init.ModBlockEntities;

/**
 * 技能点转换机：可输入 FE 能量，每消耗 1 亿能量转换 1 点技能点。
 * <ul>
 *   <li>硬度为黑曜石 25%（黑曜石硬度 50 → 12.5），需铁镐以上（needs_iron_tool）才能挖掘</li>
 *   <li>爆炸抗性 1200（与黑曜石同级），不会被爆炸破坏</li>
 *   <li>红石控制：POWERED 状态，红石信号激活 → 机器关闭（停止接收与转换）</li>
 *   <li>发光：LIT 状态，工作中发光（lightLevel 14）</li>
 * </ul>
 */
public class SkillPointConverterBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<SkillPointConverterBlock> CODEC = simpleCodec(SkillPointConverterBlock::new);
    /** 红石激活 → 机器关闭 */
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /** 工作中发光 */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public SkillPointConverterBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(POWERED, false)
                .setValue(LIT, false));
    }

    @Override
    public MapCodec<SkillPointConverterBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, LIT);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(POWERED, level.hasNeighborSignal(pos));
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide) {
            boolean powered = level.hasNeighborSignal(pos);
            if (state.getValue(POWERED) != powered) {
                // 红石信号变化：更新 POWERED（LIT 由方块实体每 tick 维护）
                level.setBlock(pos, state.setValue(POWERED, powered), 3);
            }
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SkillPointConverterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return blockEntityType == ModBlockEntities.SKILL_POINT_CONVERTER.get()
                ? (lvl, pos, st, be) -> SkillPointConverterBlockEntity.serverTick(lvl, pos, st, (SkillPointConverterBlockEntity) be)
                : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide) {
            if (level.getBlockEntity(pos) instanceof SkillPointConverterBlockEntity converter) {
                player.openMenu(converter);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
