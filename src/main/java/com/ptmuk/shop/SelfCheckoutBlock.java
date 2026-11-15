package com.ptmuk.shop;

import com.ptmuk.client.ClientHooks;
import com.ptmuk.home.HomeFurnitureType;
import com.ptmuk.home.TallHomeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/** A self-checkout: click it to scan what you're buying and pay at its screen. */
public class SelfCheckoutBlock extends TallHomeBlock {
    public SelfCheckoutBlock(HomeFurnitureType type) {
        super(type);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            BlockPos base = basePos(state, pos);
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.openSelfCheckout(base));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
