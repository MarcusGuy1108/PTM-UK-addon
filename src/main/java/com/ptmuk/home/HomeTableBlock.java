package com.ptmuk.home;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A dining table: tables placed next to each other join into one long table, with legs only at
 * its outer corners. */
public class HomeTableBlock extends Block {
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 12, 16);

    private final HomeFurnitureType type;

    public HomeTableBlock(HomeFurnitureType type) {
        super(Properties.of().strength(0.8F, 3.0F).sound(type.sound).noOcclusion());
        this.type = type;
        registerDefaultState(stateDefinition.any().setValue(NORTH, false).setValue(EAST, false)
                .setValue(SOUTH, false).setValue(WEST, false));
    }

    public HomeFurnitureType type() {
        return type;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST);
    }

    private BlockState joined(BlockState state, BlockGetter level, BlockPos pos) {
        return state.setValue(NORTH, level.getBlockState(pos.north()).is(this))
                .setValue(EAST, level.getBlockState(pos.east()).is(this))
                .setValue(SOUTH, level.getBlockState(pos.south()).is(this))
                .setValue(WEST, level.getBlockState(pos.west()).is(this));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return joined(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction dir, BlockState other, LevelAccessor level, BlockPos pos, BlockPos otherPos) {
        return dir.getAxis().isHorizontal() ? joined(state, level, pos) : state;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
}
