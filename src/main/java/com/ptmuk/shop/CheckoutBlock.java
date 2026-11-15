package com.ptmuk.shop;

import com.ptmuk.home.HomeFurnitureType;
import com.ptmuk.home.WideHomeBlock;
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

/** A supermarket checkout: its cashier sits at it (see ShopBlockEntity); the till's in its first half. */
public class CheckoutBlock extends WideHomeBlock {
    public CheckoutBlock(HomeFurnitureType type) {
        super(type);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isBase(state) ? new ShopBlockEntity(pos, state) : null;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return Tills.ticker(level, type);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return Tills.use(level, basePos(state, pos), player, hand);
    }
}
