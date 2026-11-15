package com.ptmuk.money;

import com.ptmuk.client.ClientHooks;
import com.ptmuk.home.HomeBlock;
import com.ptmuk.home.HomeFurnitureType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/** The cash machine: click it to use it (its screen, keypad and slots). */
public class AtmBlock extends HomeBlock {
    public AtmBlock(HomeFurnitureType type) {
        super(type);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.openAtm(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
