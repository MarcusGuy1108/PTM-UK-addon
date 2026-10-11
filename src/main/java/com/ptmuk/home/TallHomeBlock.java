package com.ptmuk.home;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Two blocks high (fridge freezer, wardrobe, shower, floor lamp, bookcase), placed and broken
 * together like a door. */
public class TallHomeBlock extends HomeBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    private final Map<Direction, VoxelShape> upper = new EnumMap<>(Direction.class);
    private final boolean walkIn;

    public TallHomeBlock(HomeFurnitureType type) {
        super(type);
        double[] b = type.box;
        // a walk-in piece (the shower) only has its tray to bump into; the rest stands as tall as the lower box
        walkIn = b[4] < 4;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            upper.put(d, rotated(new double[]{b[0], 0, b[2], b[3], walkIn ? 16 : b[4], b[5]}, 0, d));
        }
    }

    @Override
    protected BlockState initialState(BlockState state) {
        return super.initialState(state).setValue(HALF, DoubleBlockHalf.LOWER);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HALF);
    }

    @Override
    protected BlockPos basePos(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    @Override
    protected boolean isBase(BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    @Override
    protected List<BlockPos> parts(BlockState state, BlockPos base) {
        return List.of(base, base.above());
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).canBeReplaced(context)) {
            return null;
        }
        return super.getStateForPlacement(context);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockState below = level.getBlockState(pos.below());
            return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
        }
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction dir, BlockState other, LevelAccessor level, BlockPos pos, BlockPos otherPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (dir.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (dir == Direction.UP)) {
            boolean partner = other.is(this) && other.getValue(HALF) != half;
            return partner ? state : Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos below = pos.below();
            if (level.getBlockState(below).is(this)) {
                level.setBlock(below, Blocks.AIR.defaultBlockState(), 35);   // no drop in creative
                level.levelEvent(player, 2001, below, Block.getId(level.getBlockState(below)));
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? upper.get(state.getValue(FACING)) : super.getShape(state, level, pos, context);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (walkIn && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            return empty();
        }
        return getShape(state, level, pos, context);
    }
}
