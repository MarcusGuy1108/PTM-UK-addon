package com.ptmuk.money;

import com.rinventor.ptm2.dimension.virtual.util.PaymentUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/**
 * The PTM2 bank account, in pence. PTM2 opens an account when a player joins; one it hasn't (a
 * test's stand-in player) keeps its money on the player instead, so cash machines and tills work
 * the same for it.
 */
public final class Bank {
    private Bank() {
    }

    /** Long.MAX_VALUE: no limit (PTM2's "indefinite money"). */
    public static long balance(Player p) {
        try {
            return PaymentUtils.hasIndefiniteMoney(p) ? Long.MAX_VALUE : Math.max(0, Math.round(PaymentUtils.getBalance(p) * 100));
        } catch (RuntimeException e) {
            return own(p).getLong("pence");
        }
    }

    public static boolean canPay(Player p, long pence) {
        return balance(p) >= pence;
    }

    public static void pay(Player p, long pence, String what) {
        try {
            PaymentUtils.pay(p, pence / 100.0, what);
        } catch (RuntimeException e) {
            own(p).putLong("pence", own(p).getLong("pence") - pence);
        }
    }

    public static void add(Player p, long pence, String what) {
        try {
            PaymentUtils.add(p, pence / 100.0, what);
        } catch (RuntimeException e) {
            own(p).putLong("pence", own(p).getLong("pence") + pence);
        }
    }

    private static CompoundTag own(Player p) {
        CompoundTag data = p.getPersistentData();
        if (!data.contains("ptmuk_bank")) {
            data.put("ptmuk_bank", new CompoundTag());
        }
        return data.getCompound("ptmuk_bank");
    }
}
