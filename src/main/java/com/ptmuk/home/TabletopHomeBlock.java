package com.ptmuk.home;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Something that stands on a surface (a TV, a lamp, a kettle): it sinks down onto the top of the
 * furniture under it, so a TV stands on its TV unit and a kettle on the worktop. */
public class TabletopHomeBlock extends HomeBlock {
    public enum Stand implements StringRepresentable {
        FLOOR(16), LOW(8), TABLE(12), COUNTER(14);

        final int top;

        Stand(int top) {
            this.top = top;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase();
        }
    }

    public static final EnumProperty<Stand> LEVEL = EnumProperty.create("level", Stand.class);

    private final Map<Stand, Map<Direction, VoxelShape>> shapes = new EnumMap<>(Stand.class);

    public TabletopHomeBlock(HomeFurnitureType type) {
        super(type);
        for (Stand s : Stand.values()) {
            Map<Direction, VoxelShape> m = new EnumMap<>(Direction.class);
            for (Direction d : Direction.Plane.HORIZONTAL) {
                m.put(d, rotated(type.box, s.top - 16, d));
            }
            shapes.put(s, m);
        }
    }

    @Override
    protected BlockState initialState(BlockState state) {
        return super.initialState(state).setValue(LEVEL, Stand.FLOOR);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LEVEL);
    }

    static Stand standOn(BlockState below) {
        if (below.getBlock() instanceof HomeBlock home) {
            return switch (home.type().surface) {
                case LOW -> Stand.LOW;
                case TABLE -> Stand.TABLE;
                case COUNTER -> Stand.COUNTER;
                default -> Stand.FLOOR;
            };
        }
        if (below.getBlock() instanceof HomeTableBlock) {
            return Stand.TABLE;
        }
        if (below.getBlock() instanceof SlabBlock && below.getValue(SlabBlock.TYPE) == SlabType.BOTTOM) {
            return Stand.LOW;
        }
        return Stand.FLOOR;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState below = context.getLevel().getBlockState(context.getClickedPos().below());
        return super.getStateForPlacement(context).setValue(LEVEL, standOn(below));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction dir, BlockState other, LevelAccessor level, BlockPos pos, BlockPos otherPos) {
        if (dir == Direction.DOWN) {
            return state.setValue(LEVEL, standOn(other));
        }
        return state;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state.getValue(LEVEL)).get(state.getValue(FACING));
    }

    @Override
    protected double levelDrop(BlockState state) {
        return (state.getValue(LEVEL).top - 16) / 16.0;
    }
}
