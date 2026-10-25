package com.ptmuk.building;

import com.ptmuk.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A car on display (showrooms, forecourts, car parks): its block is the middle of the car, which
 * reaches about two and a half blocks each way along it and a block each side (3 x 5 in all). Its
 * nose points at whoever placed it. The car is drawn by its block entity's renderer.
 */
public class DisplayCarBlock extends HorizontalDirectionalBlock implements EntityBlock {
    // shortcut: solid over the middle 3 blocks of its length only (block collisions reach a block past their own), so you can step into the nose or tail; give it part blocks if that matters
    private static final VoxelShape ALONG_Z = Block.box(-8, 0, -16, 24, 14, 32), ALONG_X = Block.box(-16, 0, -8, 32, 14, 24);

    /** The paint, as 0xRRGGBB. */
    public final int colour;

    public DisplayCarBlock(int colour) {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.0F, 6.0F).sound(SoundType.METAL).noOcclusion());
        this.colour = colour;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
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
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? ALONG_Z : ALONG_X;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DisplayCarBlockEntity(pos, state);
    }

    /** Nothing to keep: the block entity is only there to draw the car. */
    public static class DisplayCarBlockEntity extends BlockEntity {
        public DisplayCarBlockEntity(BlockPos pos, BlockState state) {
            super(ModBlockEntities.DISPLAY_CAR.get(), pos, state);
        }

        @Override
        public net.minecraft.world.phys.AABB getRenderBoundingBox() {
            return new net.minecraft.world.phys.AABB(worldPosition).inflate(3, 0, 3).expandTowards(0, 1, 0);
        }
    }
}
