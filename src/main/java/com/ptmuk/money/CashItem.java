package com.ptmuk.money;

import com.ptmuk.registry.ModItems;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * A note or coin: pounds and pence you carry, worth the same as PTM2 money (£1 is 1 of it). Cash
 * machines give it out of your PTM2 bank account and take it back in; shops take it and give change.
 */
public class CashItem extends Item {
    /** Every note and coin, in pence, biggest first. */
    public static final int[] DENOMINATIONS = {5000, 2000, 1000, 500, 200, 100, 50, 20, 10, 5, 2, 1};
    public final int pence;

    public CashItem(int pence) {
        super(new Item.Properties().stacksTo(64));
        this.pence = pence;
    }

    /** The item's name: note_20, coin_2_pound, coin_50p... */
    public static String id(int pence) {
        return pence >= 500 ? "note_" + pence / 100 : pence >= 100 ? "coin_" + pence / 100 + "_pound" : "coin_" + pence + "p";
    }

    public static String money(long pence) {
        return String.format(Locale.ROOT, "£%d.%02d", pence / 100, pence % 100);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        if (stack.getCount() > 1) {
            lines.add(Component.literal(money((long) pence * stack.getCount()) + " in all").withStyle(ChatFormatting.GRAY));
        }
    }

    /** How much cash the player has on them (pence). */
    public static long carried(Player player) {
        long total = 0;
        for (ItemStack s : player.getInventory().items) {
            total += value(s);
        }
        for (ItemStack s : player.getInventory().offhand) {
            total += value(s);
        }
        return total;
    }

    public static long value(ItemStack s) {
        return s.getItem() instanceof CashItem c ? (long) c.pence * s.getCount() : 0;
    }

    /** All the player's cash taken off them; how much it was. */
    public static long takeAll(Player player) {
        long total = 0;
        for (List<ItemStack> list : List.of(player.getInventory().items, player.getInventory().offhand)) {
            for (int i = 0; i < list.size(); i++) {
                total += value(list.get(i));
                if (list.get(i).getItem() instanceof CashItem) {
                    list.set(i, ItemStack.EMPTY);
                }
            }
        }
        return total;
    }

    /**
     * Pays price (pence) out of the player's cash as a person would: the biggest notes and coins
     * that fit first, then the smallest one that covers what's left; the change comes back in the
     * fewest notes and coins. Returns the change, or -1 (and takes nothing) if they haven't enough.
     */
    public static long pay(Player player, long price) {
        if (carried(player) < price) {
            return -1;
        }
        int n = DENOMINATIONS.length;
        long[] held = new long[n], use = new long[n];
        for (List<ItemStack> list : List.of(player.getInventory().items, player.getInventory().offhand)) {
            for (ItemStack s : list) {
                if (s.getItem() instanceof CashItem c) {
                    held[index(c.pence)] += s.getCount();
                }
            }
        }
        long left = price;
        for (int i = 0; i < n && left > 0; i++) {
            use[i] = Math.min(held[i], left / DENOMINATIONS[i]);
            left -= use[i] * DENOMINATIONS[i];
        }
        for (int i = n - 1; i >= 0 && left > 0; i--) {   // the smallest one left that covers the rest
            if (held[i] > use[i] && DENOMINATIONS[i] >= left) {
                use[i]++;
                left -= DENOMINATIONS[i];
            }
        }
        for (int i = n - 1; i >= 0 && left > 0; i--) {   // else small ones until it's covered
            while (held[i] > use[i] && left > 0) {
                use[i]++;
                left -= DENOMINATIONS[i];
            }
        }
        for (int i = 0; i < n; i++) {
            long take = use[i];
            for (List<ItemStack> list : List.of(player.getInventory().items, player.getInventory().offhand)) {
                for (int k = 0; k < list.size() && take > 0; k++) {
                    ItemStack s = list.get(k);
                    if (s.getItem() instanceof CashItem c && c.pence == DENOMINATIONS[i]) {
                        int t = (int) Math.min(take, s.getCount());
                        s.shrink(t);
                        take -= t;
                    }
                }
            }
        }
        give(player, -left, DENOMINATIONS);
        return -left;
    }

    /** Gives the player pence in the fewest of these notes and coins (into their pockets, or at their feet). */
    public static void give(Player player, long pence, int... allowed) {
        for (int d : allowed) {
            long count = pence / d;
            pence -= count * d;
            Item item = ModItems.CASH.get(d).get();
            while (count > 0) {
                int c = (int) Math.min(64, count);
                count -= c;
                ItemStack stack = new ItemStack(item, c);
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
            }
        }
    }

    private static int index(int pence) {
        for (int i = 0; i < DENOMINATIONS.length; i++) {
            if (DENOMINATIONS[i] == pence) {
                return i;
            }
        }
        throw new IllegalArgumentException("no such note or coin: " + pence);
    }
}
