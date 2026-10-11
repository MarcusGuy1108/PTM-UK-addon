package com.ptmuk.home;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/** Two blocks long, side by side (the bath): the left half (as you face it) has the taps. */
public class WideHomeBlock extends HomeBlock {
    public enum Part implements StringRepresentable {
        LEFT, RIGHT;

        @Override
        public String getSerializedName() {
            return name().toLowerCase();
        }
    }

    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    public WideHomeBlock(HomeFurnitureType type) {
        super(type);
    }

    @Override
    protected BlockState initialState(BlockState state) {
        return super.initialState(state).setValue(PART, Part.LEFT);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PART);
    }

    /** Which way the other half is from this one. */
    private static Direction toPartner(BlockState state) {
        Direction east = state.getValue(FACING).getClockWise();
        return state.getValue(PART) == Part.LEFT ? east : east.getOpposite();
    }

    @Override
    protected BlockPos basePos(BlockState state, BlockPos pos) {
        return state.getValue(PART) == Part.LEFT ? pos : pos.relative(toPartner(state));
    }

    @Override
    protected boolean isBase(BlockState state) {
        return state.getValue(PART) == Part.LEFT;
    }

    @Override
    protected List<BlockPos> parts(BlockState state, BlockPos base) {
        return List.of(base, base.relative(state.getValue(FACING).getClockWise()));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        BlockPos other = context.getClickedPos().relative(state.getValue(FACING).getClockWise());
        if (!context.getLevel().getBlockState(other).canBeReplaced(context)) {
            return null;
        }
        return state;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.relative(toPartner(state)), state.setValue(PART, Part.RIGHT), 3);
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction dir, BlockState other, LevelAccessor level, BlockPos pos, BlockPos otherPos) {
        if (dir == toPartner(state)) {
            boolean partner = other.is(this) && other.getValue(PART) != state.getValue(PART) && other.getValue(FACING) == state.getValue(FACING);
            return partner ? state : Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && state.getValue(PART) == Part.RIGHT) {
            BlockPos left = pos.relative(toPartner(state));
            if (level.getBlockState(left).is(this)) {
                level.setBlock(left, Blocks.AIR.defaultBlockState(), 35);
                level.levelEvent(player, 2001, left, Block.getId(level.getBlockState(left)));
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }
}
