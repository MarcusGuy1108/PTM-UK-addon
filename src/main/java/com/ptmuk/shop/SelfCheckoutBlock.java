package com.ptmuk.shop;

import com.ptmuk.client.ClientHooks;
import com.ptmuk.home.HomeFurnitureType;
import com.ptmuk.home.TallHomeBlock;
import com.ptmuk.money.CashItem;
import com.rinventor.ptm2.objects.items.BankCard;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * A self-checkout. Click it with something from the shop in your hand to scan it; with your bank
 * card or cash to pay; with an empty hand for its menu (look up, codes, bags, receipt); sneak and
 * click with an empty hand to cancel. Its screen shows the shopping as you go.
 */
public class SelfCheckoutBlock extends TallHomeBlock {
    public SelfCheckoutBlock(HomeFurnitureType type) {
        super(type);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isBase(state) ? new SelfCheckoutBlockEntity(pos, state) : null;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || type != ShopRegistry.SELF_CHECKOUT_BE.get() ? null : (l, p, s, be) -> ((SelfCheckoutBlockEntity) be).tick();
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        BlockPos base = basePos(state, pos);
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty() && !player.isShiftKeyDown()) {
            if (level.isClientSide) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.openSelfCheckout(base));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide || !(level.getBlockEntity(base) instanceof SelfCheckoutBlockEntity till)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (held.isEmpty()) {
            till.cancel(player);
        } else if (held.getItem() instanceof BankCard || held.getItem() instanceof CashItem) {
            SelfCheckout.Reply r = till.pay(player, held.getItem() instanceof BankCard);
            String said = switch (r.result()) {
                case OK -> "Paid " + CashItem.money(r.total()) + (r.change() > 0 ? ". Your change: " + CashItem.money(r.change()) : "")
                        + ". Thank you for shopping with us!";
                case NOTHING -> "Scan something first: click the checkout with it in your hand";
                case NO_CARD -> "No bank card";
                case DECLINED -> "Card declined";
                case SHORT -> "Not enough cash: the total is " + CashItem.money(r.total());
                case WAIT -> "Please wait for a colleague";
            };
            player.displayClientMessage(Component.literal(said).withStyle(r.result() == SelfCheckout.Result.OK ? ChatFormatting.GREEN : ChatFormatting.GOLD), true);
        } else if (!till.fromHere(held)) {
            player.displayClientMessage(Component.literal(ProductItem.unpaid(held) ? "That's from another shop" : "That's not from this shop's shelves")
                    .withStyle(ChatFormatting.GOLD), true);
        } else {
            till.scan(player, held);
        }
        return InteractionResult.CONSUME;
    }
}
