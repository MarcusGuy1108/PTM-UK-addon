package com.ptmuk.motorway;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A motorway lane signal (MS4) that hangs under a gantry beam, one per lane.
 *
 * <p>Right-click sets the next aspect on the whole row of signals on the gantry (speeds, arrows
 * and messages apply to every lane); sneak + right-click changes just this lane, e.g. for a red X.
 * A redstone signal forces a red X, so lanes can be closed from a control room.
 */
public class LaneSignalBlock extends HorizontalDirectionalBlock {
    public static final EnumProperty<LaneAspect> ASPECT = EnumProperty.create("aspect", LaneAspect.class);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final VoxelShape NS = Block.box(1, 0, 5, 15, 16, 11);
    private static final VoxelShape EW = Block.box(5, 0, 1, 11, 16, 15);
    private static final int MAX_ROW = 10;

    public LaneSignalBlock() {
        super(Properties.of().strength(2.0F, 6.0F).sound(SoundType.METAL).noOcclusion().lightLevel(s -> lit(s) ? 6 : 0));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ASPECT, LaneAspect.OFF)
                .setValue(POWERED, false));
    }

    private static boolean lit(BlockState s) {
        return s.getValue(POWERED) || s.getValue(ASPECT) != LaneAspect.OFF;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ASPECT, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? NS : EW;
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!player.getItemInHand(hand).isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            LaneAspect next = state.getValue(ASPECT).next(false);
            level.setBlock(pos, state.setValue(ASPECT, next), Block.UPDATE_ALL);
            if (!player.isShiftKeyDown()) {
                Direction facing = state.getValue(FACING);
                for (Direction side : new Direction[]{facing.getClockWise(), facing.getCounterClockWise()}) {
                    BlockPos.MutableBlockPos p = pos.mutable();
                    for (int i = 0; i < MAX_ROW; i++) {
                        p.move(side);
                        BlockState other = level.getBlockState(p);
                        if (!(other.getBlock() instanceof LaneSignalBlock) || other.getValue(FACING) != facing) {
                            break;
                        }
                        level.setBlock(p, other.setValue(ASPECT, next), Block.UPDATE_ALL);
                    }
                }
            }
            level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.4F, 1.6F);
            player.displayClientMessage(Component.literal("Lane signal: " + next.label
                    + (player.isShiftKeyDown() ? " (this lane)" : " (whole gantry)")), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
