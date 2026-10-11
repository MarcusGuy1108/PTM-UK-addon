package com.ptmuk.home;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/** One seat of a modular sofa: seats side by side, facing the same way, join into one settee with
 * arms only at the ends. On its own it's an armchair. */
public class SofaBlock extends HomeBlock {
    public enum Shape implements StringRepresentable {
        SINGLE, WEST, EAST, MIDDLE;

        @Override
        public String getSerializedName() {
            return name().toLowerCase();
        }
    }

    public static final EnumProperty<Shape> SHAPE = EnumProperty.create("shape", Shape.class);

    public SofaBlock(HomeFurnitureType type) {
        super(type);
    }

    @Override
    protected BlockState initialState(BlockState state) {
        return super.initialState(state).setValue(SHAPE, Shape.SINGLE);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SHAPE);
    }

    private Shape shapeAt(BlockGetter level, BlockPos pos, Direction facing) {
        boolean w = joins(level.getBlockState(pos.relative(facing.getCounterClockWise())), facing);
        boolean e = joins(level.getBlockState(pos.relative(facing.getClockWise())), facing);
        return w && e ? Shape.MIDDLE : w ? Shape.EAST : e ? Shape.WEST : Shape.SINGLE;
    }

    private boolean joins(BlockState other, Direction facing) {
        return other.getBlock() == this && other.getValue(FACING) == facing;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state.setValue(SHAPE, shapeAt(context.getLevel(), context.getClickedPos(), state.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction dir, BlockState other, LevelAccessor level, BlockPos pos, BlockPos otherPos) {
        if (dir.getAxis().isHorizontal()) {
            return state.setValue(SHAPE, shapeAt(level, pos, state.getValue(FACING)));
        }
        return state;
    }
}
