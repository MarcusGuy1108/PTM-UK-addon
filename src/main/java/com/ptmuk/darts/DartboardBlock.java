package com.ptmuk.darts;

import com.ptmuk.home.HomeBlock;
import com.ptmuk.home.HomeFurnitureType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** A dartboard that scores: click it to see where you are; sneak and click (empty hand) for a new game. */
public class DartboardBlock extends HomeBlock {
    public DartboardBlock(HomeFurnitureType type) {
        super(type);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DartboardBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || type != DartsRegistry.BOARD_BE.get() ? null : (l, p, s, be) -> ((DartboardBlockEntity) be).tick();
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof DartboardBlockEntity board) {
            if (player.isShiftKeyDown() && player.getMainHandItem().isEmpty()) {
                board.newGame(player);
            } else {
                board.tell(player);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
