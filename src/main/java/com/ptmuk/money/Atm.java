package com.ptmuk.money;

import com.ptmuk.registry.ModItems;
import com.rinventor.ptm2.objects.items.BankCard;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * What a cash machine does, on the server: your PTM2 bank card in, a PIN you choose the first time
 * (three wrong and the machine keeps your card until tomorrow), then cash out of your PTM2 bank
 * account (£10 to £250 at a time, £500 a day) in £20s and £10s, your balance, paying cash in, and
 * a new PIN. Each step after the PIN needs it to have been right, at this machine, a moment ago.
 */
public final class Atm {
    public enum Action { START, PIN, NEW_PIN, WITHDRAW, BALANCE, PAY_IN, END }

    public enum Status { OK, NO_CARD, KEPT, SET_PIN, ENTER_PIN, WRONG_PIN, NO_MONEY, LIMIT, BAD_AMOUNT, NO_CASH, ERROR }

    /** A reply: how it went, the balance after (pence; Long.MAX_VALUE: no limit), and a number for it (tries left, what's left today, cash paid in). */
    public record Reply(Action action, Status status, long balance, long n) {
    }

    public static final long DAILY_LIMIT = 50_000, MAX_ONCE = 25_000;
    private static final String[] BANKS = {"Albion Bank", "Shires Bank", "Thames & County", "Northgate Building Society", "Kingsmead Bank",
            "Severn Savings"};
    /** Who has put their PIN in right, at which machine, until when (game time). */
    private static final Map<UUID, long[]> SESSIONS = new HashMap<>();

    private Atm() {
    }

    /** The made-up bank whose machine this is. */
    public static String bank(BlockPos pos) {
        return BANKS[Math.floorMod(pos.hashCode() * 31 + 7, BANKS.length)];
    }

    public static Reply handle(ServerPlayer player, BlockPos pos, Action action, String pin, long amount, boolean receipt) {
        CompoundTag data = data(player);
        long day = player.level().getDayTime() / 24000, now = player.level().getGameTime();
        if (data.getLong("day") != day) {
            data.putLong("day", day);
            data.putLong("out", 0);
        }
        try {
            if (action == Action.END) {
                SESSIONS.remove(player.getUUID());
                return reply(action, Status.OK, player, 0);
            }
            if (action == Action.START || action == Action.PIN) {
                if (!hasCard(player)) {
                    return reply(action, Status.NO_CARD, player, 0);
                }
                if (data.contains("kept") && data.getLong("kept") >= day) {
                    return reply(action, Status.KEPT, player, 0);
                }
            }
            if (action == Action.START) {
                SESSIONS.remove(player.getUUID());
                return reply(action, data.contains("pin") ? Status.ENTER_PIN : Status.SET_PIN, player, 0);
            }
            if (action == Action.PIN) {
                if (!pin.matches("\\d{4}")) {
                    return reply(action, Status.ERROR, player, 0);
                }
                if (!data.contains("pin")) {
                    data.putString("pin", hash(player, pin));
                } else if (!data.getString("pin").equals(hash(player, pin))) {
                    int tries = data.getInt("tries") + 1;
                    if (tries >= 3) {
                        data.putInt("tries", 0);
                        data.putLong("kept", day);
                        return reply(action, Status.KEPT, player, 0);
                    }
                    data.putInt("tries", tries);
                    return reply(action, Status.WRONG_PIN, player, 3 - tries);
                }
                data.putInt("tries", 0);
                SESSIONS.put(player.getUUID(), new long[]{pos.asLong(), now + 20 * 60 * 5});
                return reply(action, Status.OK, player, 0);
            }
            long[] session = SESSIONS.get(player.getUUID());
            if (session == null || session[0] != pos.asLong() || session[1] < now) {
                return reply(action, Status.ENTER_PIN, player, 0);
            }
            session[1] = now + 20 * 60 * 5;
            switch (action) {
                case NEW_PIN -> {
                    if (!pin.matches("\\d{4}")) {
                        return reply(action, Status.ERROR, player, 0);
                    }
                    data.putString("pin", hash(player, pin));
                    return reply(action, Status.OK, player, 0);
                }
                case WITHDRAW -> {
                    long out = data.getLong("out");
                    if (amount < 1000 || amount > MAX_ONCE || amount % 1000 != 0) {
                        return reply(action, Status.BAD_AMOUNT, player, 0);
                    }
                    if (out + amount > DAILY_LIMIT) {
                        return reply(action, Status.LIMIT, player, DAILY_LIMIT - out);
                    }
                    if (!Bank.canPay(player, amount)) {
                        return reply(action, Status.NO_MONEY, player, 0);
                    }
                    Bank.pay(player, amount, "Cash withdrawal, " + bank(pos));
                    data.putLong("out", out + amount);
                    CashItem.give(player, amount, 2000, 1000);
                    Reply r = reply(action, Status.OK, player, amount);
                    if (receipt) {
                        receipt(player, pos, List.of("CASH WITHDRAWAL  " + CashItem.money(amount), "AVAILABLE BALANCE  " + balanceText(r.balance())));
                    }
                    return r;
                }
                case BALANCE -> {
                    Reply r = reply(action, Status.OK, player, 0);
                    if (receipt) {
                        receipt(player, pos, List.of("BALANCE ENQUIRY", "AVAILABLE BALANCE  " + balanceText(r.balance())));
                    }
                    return r;
                }
                case PAY_IN -> {
                    long cash = CashItem.takeAll(player);
                    if (cash <= 0) {
                        return reply(action, Status.NO_CASH, player, 0);
                    }
                    Bank.add(player, cash, "Cash paid in, " + bank(pos));
                    return reply(action, Status.OK, player, cash);
                }
                default -> {
                    return reply(action, Status.ERROR, player, 0);
                }
            }
        } finally {
            save(player, data);
        }
    }

    public static String balanceText(long balance) {
        return balance == Long.MAX_VALUE ? "UNLIMITED" : CashItem.money(balance);
    }

    private static Reply reply(Action action, Status status, Player player, long n) {
        return new Reply(action, status, Bank.balance(player), n);
    }

    private static boolean hasCard(Player player) {
        for (ItemStack s : player.getInventory().items) {
            if (s.getItem() instanceof BankCard) {
                return true;
            }
        }
        return player.getOffhandItem().getItem() instanceof BankCard;
    }

    private static void receipt(ServerPlayer player, BlockPos pos, List<String> body) {
        long t = player.level().getDayTime();
        List<String> lines = new ArrayList<>();
        lines.add(bank(pos).toUpperCase(java.util.Locale.ROOT));
        lines.add(String.format(java.util.Locale.ROOT, "DAY %d  %02d:%02d", t / 24000 + 1, (t / 1000 + 6) % 24, (t % 1000) * 60 / 1000));
        lines.add(String.format(java.util.Locale.ROOT, "CARD ************%04d", Math.floorMod(player.getUUID().hashCode(), 10000)));
        lines.addAll(body);
        lines.add("THANK YOU");
        ItemStack r = ReceiptItem.make(ModItems.RECEIPT.get(), bank(pos), lines);
        if (!player.getInventory().add(r)) {
            player.drop(r, false);
        }
    }

    private static String hash(Player player, String pin) {
        return Integer.toHexString((player.getUUID() + ":" + pin).hashCode());
    }

    private static CompoundTag data(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound("ptmuk_atm");
    }

    private static void save(Player player, CompoundTag data) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        persisted.put("ptmuk_atm", data);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }
}
