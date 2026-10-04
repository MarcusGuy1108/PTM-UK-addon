package com.ptmuk.block;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A piece of UK street furniture that faces the player who placed it. */
public class FurnitureBlock extends HorizontalDirectionalBlock {
    private final FurnitureType type;
    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

    public FurnitureBlock(FurnitureType type) {
        super(Properties.of().strength(1.5F, 6.0F).sound(type.sound).noOcclusion()
                .lightLevel(state -> type.light));
        this.type = type;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        double[] b = type.box;
        // collision stays inside 1.5 blocks; taller models just render above it
        double top = Math.min(b[4], 24);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            shapes.put(dir, rotated(b[0], b[1], b[2], b[3], top, b[5], dir));
        }
    }

    public FurnitureType getType() {
        return type;
    }

    /** Box defined for a north-facing block, turned to face dir. */
    private static VoxelShape rotated(double x1, double y1, double z1, double x2, double y2, double z2, Direction dir) {
        return switch (dir) {
            case SOUTH -> Block.box(16 - x2, y1, 16 - z2, 16 - x1, y2, 16 - z1);
            case EAST -> Block.box(16 - z2, y1, x1, 16 - z1, y2, x2);
            case WEST -> Block.box(z1, y1, 16 - x2, z2, y2, 16 - x1);
            default -> Block.box(x1, y1, z1, x2, y2, z2);
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state.getValue(FACING));
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
}
