package com.ptmuk.block;

import com.rinventor.ptm2.objects.blocks.Streetpost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A round UK pole. It is a PTM2 {@link Streetpost} so PTM2's own lights, signs and lamps, and
 * our signal heads, mount on it exactly as they do on PTM2 posts. It always reports PTM2
 * streetpost state 1 (a plain vertical post) and never runs PTM2's post auto-shaping, so it
 * stays a straight pole. BASE / CAP add the ground collar and top cap at the ends of a run.
 */
public class UkPole extends Streetpost {
    public static final BooleanProperty BASE = BooleanProperty.create("base");
    public static final BooleanProperty CAP = BooleanProperty.create("cap");

    private final PoleType type;
    private final VoxelShape shape;

    public UkPole(PoleType type) {
        super();
        this.type = type;
        double r = type.radius;
        this.shape = Block.box(8 - r, 0, 8 - r, 8 + r, 16, 8 + r);
        registerDefaultState(defaultBlockState().setValue(STATE, 1).setValue(FACING, Direction.NORTH)
                .setValue(BASE, true).setValue(CAP, true));
    }

    public PoleType getType() {
        return type;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BASE, CAP);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        return defaultBlockState()
                .setValue(BASE, !isPole(level.getBlockState(pos.below())))
                .setValue(CAP, !isPole(level.getBlockState(pos.above())) && !isMountedOnTop(level.getBlockState(pos.above())));
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level,
                                  BlockPos pos, BlockPos neighbourPos) {
        if (direction == Direction.DOWN) {
            return state.setValue(BASE, !isPole(neighbour));
        }
        if (direction == Direction.UP) {
            // a head or sign on top carries the pole on up behind itself, cap included
            return state.setValue(CAP, !isPole(neighbour) && !isMountedOnTop(neighbour));
        }
        return state;
    }

    private static boolean isPole(BlockState state) {
        return state.getBlock() instanceof UkPole;
    }

    private static boolean isMountedOnTop(BlockState state) {
        return (state.getBlock() instanceof UkTrafficSignal || state.getBlock() instanceof SignalAccessory)
                && state.getValue(UkTrafficSignal.ATTACHMENT) == com.rinventor.ptm2.core.properties.TrafficLightStates.CENTER;
    }

    // PTM2's streetpost reshapes itself (arms, bends, lamp brackets) on placement and neighbour
    // changes; a UK pole stays a straight pole, so none of that runs here.
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbour, BlockPos fromPos, boolean moving) {
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape;
    }
}
