package com.ptmuk.fare;

import com.ptmuk.block.FurnitureBlock;
import com.ptmuk.block.FurnitureType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A stand-alone card reader (as at tram stops and bus stations). Tap a card to pay the fare;
 * an accepted tap gives a one second redstone pulse, so it can open doors or gates.
 */
public class CardReaderBlock extends FurnitureBlock {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public CardReaderBlock(FurnitureType type) {
        super(type);
        registerDefaultState(defaultBlockState().setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWERED);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        if (Fares.card(stack) == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && !player.getCooldowns().isOnCooldown(stack.getItem())
                && Fares.tap(player, stack, Vec3.atCenterOf(pos))) {
            level.setBlock(pos, state.setValue(POWERED, true), Block.UPDATE_ALL);
            level.updateNeighborsAt(pos, this);
            level.updateNeighborsAt(pos.below(), this);
            level.scheduleTick(pos, this, 20);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, false), Block.UPDATE_ALL);
            level.updateNeighborsAt(pos, this);
            level.updateNeighborsAt(pos.below(), this);
        }
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) && direction == Direction.UP ? 15 : 0;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!moved && !state.is(newState.getBlock()) && state.getValue(POWERED)) {
            level.updateNeighborsAt(pos, this);
            level.updateNeighborsAt(pos.below(), this);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
