package com.ptmuk.gametest;

import com.ptmuk.PtmUk;
import com.ptmuk.money.Atm;
import com.ptmuk.money.CashItem;
import com.ptmuk.registry.ModItems;
import com.ptmuk.money.Bank;
import com.rinventor.ptm2.objects.items.BankCard;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** Cash and the cash machine: paying with change, and the machine's PIN, limits, paying in and kept cards. */
@GameTestHolder(PtmUk.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UkMoneyGameTests {
    private UkMoneyGameTests() {
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        return net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), name));
    }

    /** £20 and three 10p coins pay £1.45: the 10ps and the £20 go, £18.85 comes back. */
    @GameTest(template = "empty5x4x5")
    public static void cashPaysAndGivesChange(GameTestHelper helper) {
        ServerPlayer p = player(helper, "cash-test");
        p.getInventory().clearContent();
        p.getInventory().add(new ItemStack(ModItems.CASH.get(2000).get()));
        p.getInventory().add(new ItemStack(ModItems.CASH.get(10).get(), 3));
        helper.assertTrue(CashItem.carried(p) == 2030, "should carry £20.30, has " + CashItem.carried(p));
        long change = CashItem.pay(p, 145);
        helper.assertTrue(change == 1885 && CashItem.carried(p) == 1885, "change " + change + ", carrying " + CashItem.carried(p));
        helper.assertTrue(CashItem.pay(p, 5000) == -1 && CashItem.carried(p) == 1885, "can't pay £50 with £18.85, and nothing's taken");
        helper.succeed();
    }

    @GameTest(template = "empty5x4x5")
    public static void cashMachineWorks(GameTestHelper helper) {
        ServerPlayer p = player(helper, "atm-test");
        p.getInventory().clearContent();
        BlockPos at = new BlockPos(1, 1, 1);
        helper.assertTrue(Atm.handle(p, at, Atm.Action.START, "", 0, false).status() == Atm.Status.NO_CARD, "no card, no service");
        Item card = ForgeRegistries.ITEMS.getValues().stream().filter(i -> i instanceof BankCard).findFirst().orElseThrow();
        p.getInventory().add(new ItemStack(card));
        Bank.add(p, 10000, "test");
        boolean limitless = Bank.balance(p) == Long.MAX_VALUE;
        long before = Atm.handle(p, at, Atm.Action.START, "", 0, false).balance();
        helper.assertTrue(Atm.handle(p, at, Atm.Action.START, "", 0, false).status() == Atm.Status.SET_PIN, "first time: choose a PIN");
        helper.assertTrue(Atm.handle(p, at, Atm.Action.WITHDRAW, "", 2000, false).status() == Atm.Status.ENTER_PIN, "no cash before the PIN");
        helper.assertTrue(Atm.handle(p, at, Atm.Action.PIN, "1234", 0, false).status() == Atm.Status.OK, "the PIN should be set");
        Atm.Reply r = Atm.handle(p, at, Atm.Action.WITHDRAW, "", 4000, true);
        helper.assertTrue(r.status() == Atm.Status.OK && CashItem.carried(p) == 4000, "£40 out: " + r + ", carrying " + CashItem.carried(p));
        helper.assertTrue(limitless || r.balance() == before - 4000, "the bank should be £40 down: " + before + " -> " + r.balance());
        helper.assertTrue(p.getInventory().items.stream().anyMatch(s -> s.is(ModItems.RECEIPT.get())), "a receipt should come out");
        helper.assertTrue(Atm.handle(p, at, Atm.Action.WITHDRAW, "", 2500, false).status() == Atm.Status.BAD_AMOUNT, "£25 isn't a multiple of £10");
        if (!limitless && r.balance() < 25000) {
            helper.assertTrue(Atm.handle(p, at, Atm.Action.WITHDRAW, "", 25000, false).status() == Atm.Status.NO_MONEY, "not £250 in the bank");
        }
        if (!limitless) {
            Bank.add(p, 100000, "test");
        }
        helper.assertTrue(Atm.handle(p, at, Atm.Action.WITHDRAW, "", 25000, false).status() == Atm.Status.OK, "£250 out");
        Atm.Reply limit = Atm.handle(p, at, Atm.Action.WITHDRAW, "", 25000, false);
        helper.assertTrue(limit.status() == Atm.Status.LIMIT && limit.n() == 21000, "£500 a day: £210 left, " + limit);
        Atm.Reply in = Atm.handle(p, at, Atm.Action.PAY_IN, "", 0, false);
        helper.assertTrue(in.status() == Atm.Status.OK && in.n() == 29000 && CashItem.carried(p) == 0, "£290 paid in: " + in);
        Atm.handle(p, at, Atm.Action.END, "", 0, false);
        helper.assertTrue(Atm.handle(p, at, Atm.Action.BALANCE, "", 0, false).status() == Atm.Status.ENTER_PIN, "card out: PIN again");
        helper.assertTrue(Atm.handle(p, at, Atm.Action.START, "", 0, false).status() == Atm.Status.ENTER_PIN, "the PIN's remembered");
        Atm.Reply wrong = Atm.handle(p, at, Atm.Action.PIN, "0000", 0, false);
        helper.assertTrue(wrong.status() == Atm.Status.WRONG_PIN && wrong.n() == 2, "two tries left: " + wrong);
        Atm.handle(p, at, Atm.Action.PIN, "0000", 0, false);
        helper.assertTrue(Atm.handle(p, at, Atm.Action.PIN, "0000", 0, false).status() == Atm.Status.KEPT, "three wrong: card kept");
        helper.assertTrue(Atm.handle(p, at, Atm.Action.START, "", 0, false).status() == Atm.Status.KEPT, "kept until tomorrow");
        helper.succeed();
    }
}
