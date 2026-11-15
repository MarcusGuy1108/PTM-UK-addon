package com.ptmuk.shop;

import com.ptmuk.client.ClientHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/** What the tills (counter tills and checkouts) share: their ticker, and clicking them. */
final class Tills {
    private Tills() {
    }

    static <T extends BlockEntity> BlockEntityTicker<T> ticker(Level level, BlockEntityType<T> type) {
        if (level.isClientSide || type != ShopRegistry.SHOP_BE.get()) {
            return null;
        }
        return (l, p, s, be) -> ShopBlockEntity.tick(l, p, s, (ShopBlockEntity) be);
    }

    /** Sneak and click with an empty hand: set the shop up; click: talk to whoever's on the till. */
    static InteractionResult use(Level level, BlockPos base, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown() && player.getMainHandItem().isEmpty()) {
            if (level.isClientSide) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.openShopSetup(base));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide && level.getBlockEntity(base) instanceof ShopBlockEntity shop) {
            shop.talk(player, null);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
