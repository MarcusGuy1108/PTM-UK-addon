package com.ptmuk.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One panel of an up-and-over garage door. Build the door from panels across the opening (all
 * facing out); right-clicking any panel, or a redstone signal next to one, opens or shuts the
 * whole door together. Open, the door lies flat under the lintel in the top row and the rest
 * of the opening is clear for a car.
 */
public class GarageDoorBlock extends HorizontalDirectionalBlock {
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /** No panel of this door above this one: an open door lies up here. */
    public static final BooleanProperty TOP = BooleanProperty.create("top");
    private static final int MAX_PANELS = 81;

    // FACING is the outside; the door hangs a little in from the outer face of the wall
    private static final Map<Direction, VoxelShape> SHUT = Map.of(
            Direction.SOUTH, Block.box(0, 0, 12, 16, 16, 14),
            Direction.NORTH, Block.box(0, 0, 2, 16, 16, 4),
            Direction.EAST, Block.box(12, 0, 0, 14, 16, 16),
            Direction.WEST, Block.box(2, 0, 0, 4, 16, 16));
    private static final Map<Direction, VoxelShape> RAISED = Map.of(
            Direction.SOUTH, Block.box(0, 13, 0, 16, 15, 14),
            Direction.NORTH, Block.box(0, 13, 2, 16, 15, 16),
            Direction.EAST, Block.box(0, 13, 0, 14, 15, 16),
            Direction.WEST, Block.box(2, 13, 0, 16, 15, 16));

    public GarageDoorBlock(MapColor colour, SoundType sound) {
        super(BlockBehaviour.Properties.of().mapColor(colour).strength(2.0F, 6.0F).sound(sound).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(OPEN, false)
                .setValue(POWERED, false).setValue(TOP, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, POWERED, TOP);
    }

    private static boolean sameDoor(BlockState a, BlockState b) {
        return b.getBlock() instanceof GarageDoorBlock && b.getValue(FACING) == a.getValue(FACING);
    }

    /** Placed from outside: the outer face turns towards the player. A panel added to an open door opens too. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        state = state.setValue(TOP, !sameDoor(state, level.getBlockState(pos.above())));
        for (Direction d : sideways(state)) {
            BlockState n = level.getBlockState(pos.relative(d));
            if (sameDoor(state, n)) {
                return state.setValue(OPEN, n.getValue(OPEN)).setValue(POWERED, n.getValue(POWERED));
            }
        }
        return state.setValue(POWERED, level.hasNeighborSignal(pos)).setValue(OPEN, level.hasNeighborSignal(pos));
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbour, LevelAccessor level, BlockPos pos,
                                  BlockPos from) {
        if (dir == Direction.UP) {
            return state.setValue(TOP, !sameDoor(state, neighbour));
        }
        return state;
    }

    private static Direction[] sideways(BlockState state) {
        Direction f = state.getValue(FACING);
        return new Direction[]{Direction.UP, Direction.DOWN, f.getClockWise(), f.getCounterClockWise()};
    }

    /** Every panel of the door this one belongs to: joined up, down and along the wall, facing the same way. */
    private static List<BlockPos> door(Level level, BlockPos start, BlockState state) {
        List<BlockPos> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty() && out.size() < MAX_PANELS) {
            BlockPos p = queue.poll();
            out.add(p);
            for (Direction d : sideways(state)) {
                BlockPos n = p.relative(d);
                if (seen.add(n) && sameDoor(state, level.getBlockState(n))) {
                    queue.add(n);
                }
            }
        }
        return out;
    }

    private static void swing(Level level, BlockPos pos, List<BlockPos> panels, boolean open, boolean powered) {
        for (BlockPos p : panels) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof GarageDoorBlock) {
                level.setBlock(p, s.setValue(OPEN, open).setValue(POWERED, powered), Block.UPDATE_CLIENTS);
            }
        }
        level.playSound(null, pos, open ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS,
                1.0F, 0.7F);
        level.gameEvent(null, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            swing(level, pos, door(level, pos, state), !state.getValue(OPEN), state.getValue(POWERED));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        if (level.isClientSide || level.hasNeighborSignal(pos) == state.getValue(POWERED)) {
            return;
        }
        List<BlockPos> panels = door(level, pos, state);
        boolean powered = panels.stream().anyMatch(level::hasNeighborSignal);
        if (powered != state.getValue(POWERED)) {
            swing(level, pos, panels, powered, powered);
        }
    }

    private static VoxelShape shape(BlockState state) {
        if (!state.getValue(OPEN)) {
            return SHUT.get(state.getValue(FACING));
        }
        return state.getValue(TOP) ? RAISED.get(state.getValue(FACING)) : Shapes.empty();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape(state);
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return state.getValue(OPEN) && type == PathComputationType.LAND;
    }
}
