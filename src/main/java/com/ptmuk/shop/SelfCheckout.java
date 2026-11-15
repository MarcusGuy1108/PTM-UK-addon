package com.ptmuk.shop;

import com.ptmuk.money.Bank;
import com.ptmuk.money.CashItem;
import com.ptmuk.money.ReceiptItem;
import com.ptmuk.registry.ModItems;
import com.rinventor.ptm2.objects.items.BankCard;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Paying at a self-checkout, on the server: everything unpaid you've got from the shops round it
 * (its tills within 48 blocks), by card or cash; it's all yours then, with a receipt.
 */
public final class SelfCheckout {
    public enum Result { OK, NOTHING, NO_CARD, DECLINED, SHORT }

    public record Reply(Result result, long total, long change) {
    }

    private SelfCheckout() {
    }

    /** The unpaid things the player has from shops near here. */
    public static List<ItemStack> basket(Player player, BlockPos at) {
        List<ItemStack> out = new ArrayList<>();
        for (List<ItemStack> list : List.of(player.getInventory().items, player.getInventory().offhand)) {
            for (ItemStack s : list) {
                BlockPos from = ProductItem.shop(s);
                if (ProductItem.unpaid(s) && from != null && from.distSqr(at) <= 48 * 48) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    /** Each carrier bag. */
    public static final int BAG = 10;

    /**
     * Pays for everything unpaid on the player from the shops round here, the loose things looked
     * up or keyed in (extras: product ordinal -> how many), and the bags; by card or cash.
     */
    public static Reply pay(ServerPlayer player, BlockPos at, boolean card, int[] extraIds, int[] extraCounts, int bags, boolean receipt) {
        List<ItemStack> basket = basket(player, at);
        ShopBlockEntity till = player.level().getBlockEntity(nearestTill(player, at)) instanceof ShopBlockEntity t ? t : null;
        List<ItemStack> extras = new ArrayList<>();
        long total = 0;
        for (ItemStack s : basket) {
            total += (long) ProductItem.price(s) * s.getCount();
        }
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < Math.min(extraIds.length, extraCounts.length); i++) {
            if (extraIds[i] < 0 || extraIds[i] >= Product.values().length || extraCounts[i] <= 0) {
                continue;
            }
            Product p = Product.values()[extraIds[i]];
            int n = Math.min(20, extraCounts[i]);
            if (!p.onShelf()) {
                continue;
            }
            int each = till != null ? till.price(p) : p.pence;
            total += (long) each * n;
            extras.add(new ItemStack(ShopRegistry.item(p), n));
            lines.add((n > 1 ? n + " x " : "") + p.title + "  " + CashItem.money((long) each * n));
        }
        bags = Math.max(0, Math.min(9, bags));
        total += (long) bags * BAG;
        if (basket.isEmpty() && extras.isEmpty()) {
            return new Reply(Result.NOTHING, 0, 0);
        }
        long change = 0;
        String shop = till != null ? till.shopName() : "Self-checkout";
        if (card) {
            boolean has = player.getInventory().items.stream().anyMatch(s -> s.getItem() instanceof BankCard)
                    || player.getOffhandItem().getItem() instanceof BankCard;
            if (!has) {
                return new Reply(Result.NO_CARD, total, 0);
            }
            if (!Bank.canPay(player, total)) {
                return new Reply(Result.DECLINED, total, 0);
            }
            Bank.pay(player, total, shop);
        } else {
            change = CashItem.pay(player, total);
            if (change < 0) {
                return new Reply(Result.SHORT, total, 0);
            }
        }
        List<String> out = new ArrayList<>();
        out.add(shop.toUpperCase(Locale.ROOT) + "  SELF-CHECKOUT");
        for (ItemStack s : basket) {
            out.add((s.getCount() > 1 ? s.getCount() + " x " : "") + s.getHoverName().getString() + "  "
                    + CashItem.money((long) ProductItem.price(s) * s.getCount()));
            ProductItem.paid(s);
        }
        out.addAll(lines);
        if (bags > 0) {
            out.add((bags > 1 ? bags + " x " : "") + "Carrier bag  " + CashItem.money((long) bags * BAG));
        }
        out.add("TOTAL  " + CashItem.money(total));
        out.add(card ? "CARD (CONTACTLESS)" : "CASH  CHANGE " + CashItem.money(change));
        out.add("THANK YOU FOR SHOPPING WITH US");
        for (ItemStack e : extras) {
            if (!player.getInventory().add(e)) {
                player.drop(e, false);
            }
        }
        if (receipt) {
            ItemStack r = ReceiptItem.make(ModItems.RECEIPT.get(), shop, out);
            if (!player.getInventory().add(r)) {
                player.drop(r, false);
            }
        }
        return new Reply(Result.OK, total, change);
    }

    private static BlockPos nearestTill(Player player, BlockPos at) {
        BlockPos best = at;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-16, -2, -16), at.offset(16, 3, 16))) {
            if (player.level().getBlockEntity(p) instanceof ShopBlockEntity) {
                double d = p.distSqr(at);
                if (d < bestD) {
                    bestD = d;
                    best = p.immutable();
                }
            }
        }
        return best;
    }
}
