package com.ptmuk.client.money;

import com.ptmuk.money.Atm;
import com.ptmuk.money.CashItem;
import com.ptmuk.network.AtmMessage;
import com.ptmuk.network.AtmReplyMessage;
import com.ptmuk.network.PtmUkNetwork;
import java.util.Locale;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

/**
 * A UK cash machine, close up: the bank's lit header, the screen under its hood with four keys
 * down each side, the steel keypad (CANCEL, CLEAR, ENTER), the card slot that flashes green when
 * it wants your card, the receipt slot and the cash slot behind its shutter. Click them (or type
 * the numbers, Enter, Backspace, Escape). Everything that moves money happens on the server.
 */
public class AtmScreen extends Screen {
    private static final int W = 300, H = 236;
    private static final int[] ROWS = {72, 90, 108, 126};

    private enum Stage { INSERT, CARD_IN, WAIT, NO_CARD, KEPT, SET_PIN, CONFIRM_PIN, ENTER_PIN, WRONG, MENU, AMOUNT, OTHER, BALANCE, PAY_IN,
        PAID_IN, NEW_PIN, CONFIRM_NEW_PIN, PIN_CHANGED, PROBLEM, TAKE_CARD, TAKE_CASH, THANKS }

    private final BlockPos pos;
    private final String bank;
    private Stage stage = Stage.INSERT, after = Stage.THANKS;
    private long since = Util.getMillis(), lastBeep;
    private String entry = "", firstPin = "", problem = "", problem2 = "";
    private boolean receipt, cardIn, mismatch, printed;
    private long balance = -1, paidIn, carried, tries;
    private int counted;
    private double scale = 1;
    private int ox, oy;

    public AtmScreen(BlockPos pos) {
        super(Component.literal("Cash machine"));
        this.pos = pos;
        this.bank = Atm.bank(pos);
    }

    /** A reply from the server, to the machine's screen if it's still open. */
    public static void reply(AtmReplyMessage msg) {
        if (Minecraft.getInstance().screen instanceof AtmScreen s) {
            s.onReply(msg);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ what happens

    private void go(Stage s) {
        stage = s;
        since = Util.getMillis();
        if (s == Stage.SET_PIN || s == Stage.CONFIRM_PIN || s == Stage.ENTER_PIN || s == Stage.NEW_PIN || s == Stage.CONFIRM_NEW_PIN
                || s == Stage.OTHER) {
            entry = "";
        }
    }

    private long age() {
        return Util.getMillis() - since;
    }

    private void send(Atm.Action action, String pin, long amount, boolean withReceipt) {
        PtmUkNetwork.CHANNEL.sendToServer(new AtmMessage(pos, action, pin, amount, withReceipt));
    }

    private void onReply(AtmReplyMessage msg) {
        balance = msg.balance();
        switch (msg.action()) {
            case START -> {
                switch (msg.status()) {
                    case NO_CARD -> {
                        cardIn = false;
                        go(Stage.NO_CARD);
                    }
                    case KEPT -> kept();
                    case SET_PIN -> go(Stage.SET_PIN);
                    default -> go(Stage.ENTER_PIN);
                }
            }
            case PIN -> {
                switch (msg.status()) {
                    case OK -> go(Stage.MENU);
                    case WRONG_PIN -> {
                        tries = msg.n();
                        go(Stage.WRONG);
                    }
                    case KEPT -> kept();
                    default -> go(Stage.ENTER_PIN);
                }
            }
            case NEW_PIN -> {
                if (msg.status() == Atm.Status.OK) {
                    go(Stage.PIN_CHANGED);
                } else {
                    problem("Your PIN wasn't changed", "");
                }
            }
            case WITHDRAW -> {
                switch (msg.status()) {
                    case OK -> {
                        printed = receipt;
                        takeCardThen(Stage.TAKE_CASH);
                    }
                    case NO_MONEY -> problem("Insufficient funds", "Please choose a smaller amount");
                    case LIMIT -> problem("Daily limit reached", "You can take out " + CashItem.money(msg.n()) + " more today");
                    case BAD_AMOUNT -> problem("Amount not available", "Multiples of £10, up to £250");
                    case ENTER_PIN -> go(Stage.ENTER_PIN);
                    default -> problem("Sorry, something went wrong", "");
                }
            }
            case BALANCE -> {
                if (msg.status() == Atm.Status.OK) {
                    printed = receipt;
                    go(Stage.BALANCE);
                } else {
                    go(Stage.ENTER_PIN);
                }
            }
            case PAY_IN -> {
                if (msg.status() == Atm.Status.OK) {
                    paidIn = msg.n();
                    play(SoundEvents.BOOK_PAGE_TURN, 1.4f, 0.8f);
                    go(Stage.PAID_IN);
                } else if (msg.status() == Atm.Status.NO_CASH) {
                    problem("No cash to pay in", "");
                } else {
                    go(Stage.ENTER_PIN);
                }
            }
            default -> {
            }
        }
    }

    private void kept() {
        cardIn = false;
        play(SoundEvents.ITEM_FRAME_REMOVE_ITEM, 0.6f, 1);
        go(Stage.KEPT);
    }

    private void problem(String a, String b) {
        problem = a;
        problem2 = b;
        play(SoundEvents.NOTE_BLOCK_BASS.value(), 0.8f, 0.6f);
        go(Stage.PROBLEM);
    }

    private void insert() {
        if (stage == Stage.INSERT) {
            cardIn = true;
            play(SoundEvents.ITEM_FRAME_ADD_ITEM, 0.7f, 1);
            go(Stage.CARD_IN);
        }
    }

    private void takeCardThen(Stage next) {
        after = next;
        play(SoundEvents.ITEM_FRAME_REMOVE_ITEM, 0.8f, 1);
        go(Stage.TAKE_CARD);
    }

    private void cancel() {
        if (cardIn && stage != Stage.TAKE_CARD && stage != Stage.TAKE_CASH && stage != Stage.THANKS) {
            send(Atm.Action.END, "", 0, false);
            takeCardThen(Stage.THANKS);
        } else if (!cardIn) {
            onClose();
        }
    }

    private void takeCard() {
        cardIn = false;
        play(SoundEvents.ITEM_FRAME_ADD_ITEM, 1.2f, 0.8f);
        counted = 0;
        go(after);
    }

    private void withdraw(long pence) {
        send(Atm.Action.WITHDRAW, "", pence, receipt);
        go(Stage.WAIT);
    }

    private void side(int key) {
        beep();
        switch (stage) {
            case MENU -> {
                switch (key) {
                    case 0, 1 -> {
                        receipt = key == 1;
                        go(Stage.AMOUNT);
                    }
                    case 2, 3 -> {
                        receipt = key == 3;
                        send(Atm.Action.BALANCE, "", 0, receipt);
                        go(Stage.WAIT);
                    }
                    case 4 -> {
                        carried = Minecraft.getInstance().player == null ? 0 : CashItem.carried(Minecraft.getInstance().player);
                        go(Stage.PAY_IN);
                    }
                    case 5 -> go(Stage.NEW_PIN);
                    case 7 -> cancel();
                    default -> {
                    }
                }
            }
            case AMOUNT -> {
                long[] amounts = {1000, 2000, 4000, 6000, 10000, 20000, 25000};
                if (key < 7) {
                    withdraw(amounts[key]);
                } else {
                    go(Stage.OTHER);
                }
            }
            case OTHER -> {
                if (key == 7) {
                    go(Stage.AMOUNT);
                }
            }
            case BALANCE, PAID_IN -> {
                if (key == 6) {
                    go(Stage.MENU);
                } else if (key == 7) {
                    cancel();
                }
            }
            case PAY_IN -> {
                if (key == 6 && carried > 0) {
                    send(Atm.Action.PAY_IN, "", 0, false);
                    go(Stage.WAIT);
                } else if (key == 7) {
                    go(Stage.MENU);
                }
            }
            default -> {
            }
        }
    }

    private boolean pinStage() {
        return stage == Stage.SET_PIN || stage == Stage.CONFIRM_PIN || stage == Stage.ENTER_PIN || stage == Stage.NEW_PIN
                || stage == Stage.CONFIRM_NEW_PIN;
    }

    private void digit(int d) {
        beep();
        if (pinStage() && entry.length() < 4 || stage == Stage.OTHER && entry.length() < 3 && !(entry.isEmpty() && d == 0)) {
            entry += d;
        }
    }

    private void clear() {
        beep();
        entry = "";
    }

    private void enter() {
        beep();
        switch (stage) {
            case INSERT -> insert();
            case SET_PIN, NEW_PIN -> {
                if (entry.length() == 4) {
                    firstPin = entry;
                    mismatch = false;
                    go(stage == Stage.SET_PIN ? Stage.CONFIRM_PIN : Stage.CONFIRM_NEW_PIN);
                }
            }
            case CONFIRM_PIN, CONFIRM_NEW_PIN -> {
                if (entry.length() == 4) {
                    if (entry.equals(firstPin)) {
                        send(stage == Stage.CONFIRM_PIN ? Atm.Action.PIN : Atm.Action.NEW_PIN, entry, 0, false);
                        go(Stage.WAIT);
                    } else {
                        mismatch = true;
                        Stage back = stage == Stage.CONFIRM_PIN ? Stage.SET_PIN : Stage.NEW_PIN;
                        go(back);
                    }
                }
            }
            case ENTER_PIN -> {
                if (entry.length() == 4) {
                    send(Atm.Action.PIN, entry, 0, false);
                    go(Stage.WAIT);
                }
            }
            case OTHER -> {
                if (!entry.isEmpty()) {
                    withdraw(Long.parseLong(entry) * 100);
                }
            }
            case TAKE_CARD -> takeCard();
            case TAKE_CASH -> takeCash();
            default -> {
            }
        }
    }

    private void takeCash() {
        if (stage == Stage.TAKE_CASH && age() > 900) {
            play(SoundEvents.IRON_TRAPDOOR_CLOSE, 1.6f, 0.4f);
            go(Stage.THANKS);
        }
    }

    @Override
    public void tick() {
        long a = age();
        switch (stage) {
            case CARD_IN -> {
                if (a > 900) {
                    send(Atm.Action.START, "", 0, false);
                    go(Stage.WAIT);
                }
            }
            case NO_CARD -> {
                if (a > 3500) {
                    go(Stage.INSERT);
                }
            }
            case KEPT -> {
                if (a > 4500) {
                    onClose();
                }
            }
            case WRONG -> {
                if (a > 1700) {
                    go(Stage.ENTER_PIN);
                }
            }
            case PROBLEM -> {
                if (a > 3000) {
                    go(Stage.MENU);
                }
            }
            case PIN_CHANGED -> {
                if (a > 2200) {
                    go(Stage.MENU);
                }
            }
            case THANKS -> {
                if (a > 1800) {
                    onClose();
                }
            }
            case TAKE_CARD -> {
                if (Util.getMillis() - lastBeep > 1000) {
                    lastBeep = Util.getMillis();
                    beep();
                }
            }
            case TAKE_CASH -> {
                if (counted < 7 && a > counted * 110L) {     // the notes counted, then the shutter opens
                    counted++;
                    play(counted < 7 ? SoundEvents.BOOK_PAGE_TURN : SoundEvents.IRON_TRAPDOOR_OPEN, counted < 7 ? 1.7f : 1.6f, 0.5f);
                }
            }
            default -> {
            }
        }
    }

    @Override
    public void onClose() {
        if (cardIn) {
            send(Atm.Action.END, "", 0, false);
        }
        super.onClose();
    }

    private void beep() {
        play(SoundEvents.NOTE_BLOCK_BIT.value(), 2.0f, 0.3f);
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        double x = (mx - ox) / scale, y = (my - oy) / scale;
        int s = sideKey(x, y);
        if (s >= 0) {
            side(s);
            return true;
        }
        int k = padKey(x, y);
        if (k >= 0 && k <= 9) {
            digit(k);
            return true;
        }
        switch (k) {
            case 10 -> {
                beep();
                cancel();
                return true;
            }
            case 11 -> {
                clear();
                return true;
            }
            case 12 -> {
                enter();
                return true;
            }
            default -> {
            }
        }
        if (x >= 208 && x <= 290 && y >= 146 && y <= 172) {
            if (stage == Stage.INSERT) {
                insert();
            } else if (stage == Stage.TAKE_CARD) {
                takeCard();
            }
            return true;
        }
        if (x >= 208 && x <= 290 && y >= 190 && y <= 222) {
            takeCash();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) {
            digit(key - GLFW.GLFW_KEY_0);
            return true;
        }
        if (key >= GLFW.GLFW_KEY_KP_0 && key <= GLFW.GLFW_KEY_KP_9) {
            digit(key - GLFW.GLFW_KEY_KP_0);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            enter();
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            clear();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && cardIn) {
            cancel();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    /** Which side key (0-3 down the left, 4-7 down the right), or -1. */
    private static int sideKey(double x, double y) {
        for (int r = 0; r < 4; r++) {
            if (y >= ROWS[r] - 6 && y <= ROWS[r] + 6) {
                if (x >= 22 && x <= 44) {
                    return r;
                }
                if (x >= 256 && x <= 278) {
                    return 4 + r;
                }
            }
        }
        return -1;
    }

    /** Which keypad key: 0-9, 10 CANCEL, 11 CLEAR, 12 ENTER; -1 none. */
    private static int padKey(double x, double y) {
        for (int r = 0; r < 4; r++) {
            double ky = 154 + r * 19;
            if (y < ky || y > ky + 15) {
                continue;
            }
            for (int c = 0; c < 3; c++) {
                double kx = 72 + c * 26;
                if (x >= kx && x <= kx + 22) {
                    return r < 3 ? r * 3 + c + 1 : c == 1 ? 0 : -1;
                }
            }
            if (x >= 152 && x <= 196 && r < 3) {
                return 10 + r;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        double fit = Math.min(width / (W + 24.0), height / (H + 24.0));
        scale = fit >= 1 ? Math.floor(fit * 2) / 2 : fit;
        ox = (int) Math.round((width - W * scale) / 2);
        oy = (int) Math.round((height - H * scale) / 2);
        g.pose().pushPose();
        g.pose().translate(ox, oy, 0);
        g.pose().scale((float) scale, (float) scale, 1);
        machine(g, (mouseX - ox) / scale, (mouseY - oy) / scale);
        g.pose().popPose();
    }

    private void machine(GuiGraphics g, double mx, double my) {
        // the wall round it, the steel fascia, the lit header
        g.fill(-12, -12, W + 12, H + 12, 0xFF4A3B33);
        for (int y = -12; y < H + 12; y += 6) {
            g.fill(-12, y, W + 12, y + 1, 0xFF3A2D27);
        }
        g.fillGradient(0, 0, W, H, 0xFFD6DADF, 0xFF9AA1A8);
        g.renderOutline(0, 0, W, H, 0xFF5C6268);
        g.fillGradient(0, 0, W, 24, 0xFF123266, 0xFF061733);
        g.fill(0, 24, W, 25, 0xFF4FB0FF);
        text(g, bank.toUpperCase(Locale.ROOT), W / 2.0, 7, 0xFFFFFFFF, 1.3f, true);
        g.fill(0, 25, W, 35, 0xFF1D7A37);
        text(g, "FREE CASH WITHDRAWALS", W / 2.0, 26.8, 0xFFFFFFFF, 0.9f, true);
        // the hood over the screen, the screen
        g.fillGradient(36, 38, 264, 44, 0xFF6F757C, 0xFF9CA3AA);
        g.fill(36, 38, 40, 148, 0xFF8C9399);
        g.fill(260, 38, 264, 148, 0xFF8C9399);
        g.fill(48, 44, 252, 146, 0xFF141618);
        g.fillGradient(55, 50, 245, 140, 0xFF0D4596, 0xFF061F4D);
        // side keys, with their lines to the screen
        for (int r = 0; r < 4; r++) {
            int y = ROWS[r];
            for (int side = 0; side < 2; side++) {
                int x0 = side == 0 ? 22 : 256;
                boolean over = sideKey(mx, my) == side * 4 + r;
                g.fillGradient(x0, y - 6, x0 + 22, y + 6, over ? 0xFFFFFFFF : 0xFFEDEFF2, over ? 0xFFC9CED3 : 0xFFAAB0B6);
                g.renderOutline(x0, y - 6, 22, 12, 0xFF4F555B);
                g.fill(side == 0 ? x0 + 22 : 252, y - 1, side == 0 ? 48 : x0, y + 1, 0xFF3C4147);
            }
        }
        display(g);
        // keypad
        g.fill(66, 150, 200, 232, 0xFF6E757C);
        g.renderOutline(66, 150, 134, 82, 0xFF464C52);
        int over = padKey(mx, my);
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 3; c++) {
                int x = 72 + c * 26, y = 154 + r * 19, k = r < 3 ? r * 3 + c + 1 : c == 1 ? 0 : -1;
                boolean lit = k >= 0 && over == k;
                g.fillGradient(x, y, x + 22, y + 15, lit ? 0xFFFFFFFF : 0xFFF2F4F6, lit ? 0xFFD0D5DA : 0xFFB5BBC1);
                g.renderOutline(x, y, 22, 15, 0xFF41474D);
                if (k >= 0) {
                    text(g, String.valueOf(k), x + 11, y + 4, 0xFF1A1C1E, 1f, false);
                }
                if (k == 5) {
                    g.fill(x + 10, y + 12, x + 12, y + 13, 0xFF1A1C1E);     // the raised dot
                }
            }
            String[] fn = {"CANCEL", "CLEAR", "ENTER", ""};
            int[] col = {0xFFD7352D, 0xFFF0B21C, 0xFF2D9C45, 0xFFB5BBC1};
            int y = 154 + r * 19;
            boolean lit = r < 3 && over == 10 + r;
            g.fillGradient(152, y, 196, y + 15, lit ? 0xFFFFFFFF : col[r], col[r]);
            g.renderOutline(152, y, 44, 15, 0xFF41474D);
            text(g, fn[r], 174, y + 4, r == 1 ? 0xFF1A1C1E : 0xFFFFFFFF, 0.9f, false);
        }
        // card slot: flashing green round it when it wants a card
        boolean wants = stage == Stage.INSERT || stage == Stage.TAKE_CARD;
        boolean blink = (Util.getMillis() / 400) % 2 == 0;
        g.fill(208, 146, 290, 174, 0xFF474D54);
        text(g, "CARD", 249, 147.5, 0xFFF4F6F8, 0.85f, false);
        g.fill(214, 157, 284, 167, wants && blink ? 0xFF3CFF6A : 0xFF1E5A2B);
        g.fill(216, 160, 282, 164, 0xFF050506);
        card(g);
        // receipt slot
        g.fill(208, 176, 290, 189, 0xFF474D54);
        g.fill(216, 178, 282, 180, 0xFF050506);
        if (printed && (stage == Stage.TAKE_CASH || stage == Stage.THANKS || stage == Stage.BALANCE || stage == Stage.TAKE_CARD)) {
            g.fill(228, 170, 270, 179, 0xFFF7F5EE);
            g.fill(232, 172, 266, 173, 0xFFB9B5AA);
            g.fill(232, 175, 258, 176, 0xFFB9B5AA);
        }
        text(g, "RECEIPT", 249, 182, 0xFFF4F6F8, 0.75f, false);
        // cash slot behind its shutter; the notes when it's open
        g.fill(208, 190, 290, 222, 0xFF474D54);
        text(g, "CASH", 249, 212.5, 0xFFF4F6F8, 0.85f, false);
        boolean open = stage == Stage.TAKE_CASH && counted >= 7;
        g.fill(214, 200, 284, 208, 0xFF050506);
        if (open) {
            for (int i = 0; i < 4; i++) {
                int y = 194 + i;
                g.fill(222 + i, y, 276 - i, y + 9, i % 2 == 0 ? 0xFF8E4FB4 : 0xFFA867CC);
                g.fill(226 + i, y + 2, 236 + i, y + 3, 0xFFE6D6F0);
            }
        } else {
            g.fillGradient(214, 200, 284, 208, 0xFF9AA1A8, 0xFF6C737A);
            g.fill(214, 204, 284, 205, 0xFF555B61);
        }
    }

    /** The card: going into the slot, or sticking out of it to be taken. */
    private void card(GuiGraphics g) {
        int top;
        if (stage == Stage.CARD_IN) {
            top = 132 + (int) Math.min(30, age() / 25);     // it slides down into the slot
        } else if (stage == Stage.TAKE_CARD) {
            top = 140 - (int) Math.min(8, age() / 40);
        } else {
            return;
        }
        int bottom = 162;
        if (top >= bottom) {
            return;
        }
        g.fillGradient(228, top, 270, bottom, 0xFF1F5FBF, 0xFF0E2E66);
        if (top + 4 < bottom) {
            g.fill(232, Math.max(top + 4, top), 240, Math.min(top + 10, bottom), 0xFFE2C35A);   // the chip
        }
        g.renderOutline(228, top, 42, bottom - top, 0xFF0A1F40);
    }

    /** What's on the screen. */
    private void display(GuiGraphics g) {
        int white = 0xFFFFFFFF, yellow = 0xFFFFD84A, pale = 0xFFB9D3FF;
        double cx = 150;
        switch (stage) {
            case INSERT -> {
                text(g, "Welcome", cx, 58, yellow, 1.6f, true);
                text(g, "Please insert your card", cx, 84, white, 1f, true);
                if ((Util.getMillis() / 600) % 2 == 0) {
                    text(g, "▼", 249 - 99, 110, pale, 1.2f, true);
                }
                text(g, "Click the card slot or press ENTER", cx, 122, white, 0.9f, true);
            }
            case CARD_IN, WAIT -> {
                text(g, "Please wait", cx, 80, white, 1.4f, true);
                text(g, ".".repeat((int) (Util.getMillis() / 300 % 4)), cx, 100, white, 1.4f, true);
            }
            case NO_CARD -> {
                text(g, "No card found", cx, 64, yellow, 1.3f, true);
                text(g, "You need a PTM2 bank card", cx, 88, white, 1f, true);
                text(g, "to use this machine", cx, 100, white, 1f, true);
            }
            case KEPT -> {
                text(g, "Your card has been retained", cx, 72, yellow, 1.1f, true);
                text(g, "Please contact your bank.", cx, 92, white, 1f, true);
                text(g, "(It'll be back tomorrow.)", cx, 106, white, 0.95f, true);
            }
            case SET_PIN, NEW_PIN -> {
                text(g, stage == Stage.SET_PIN ? "Choose a PIN" : "Enter your new PIN", cx, 58, yellow, 1.3f, true);
                text(g, mismatch ? "Those didn't match. Try again." : "Four numbers you'll remember", cx, 76, mismatch ? 0xFFFF9C8C : white, 0.9f, true);
                stars(g, 94);
                text(g, "then press ENTER", cx, 122, white, 0.95f, true);
            }
            case CONFIRM_PIN, CONFIRM_NEW_PIN -> {
                text(g, "Enter the same PIN again", cx, 62, yellow, 1.2f, true);
                stars(g, 92);
                text(g, "then press ENTER", cx, 122, white, 0.95f, true);
            }
            case ENTER_PIN -> {
                text(g, "Please enter your PIN", cx, 60, yellow, 1.2f, true);
                stars(g, 88);
                text(g, "Shield your PIN from others", cx, 122, white, 0.9f, true);
            }
            case WRONG -> {
                text(g, "Incorrect PIN", cx, 72, yellow, 1.4f, true);
                text(g, tries + (tries == 1 ? " try" : " tries") + " left", cx, 96, white, 1f, true);
            }
            case MENU -> {
                text(g, "Please select a service", cx, 56, yellow, 1.1f, true);
                options(g, new String[]{"Cash withdrawal", "Cash with receipt", "Balance on screen", "Balance with receipt"},
                        new String[]{"Pay in cash", "Change PIN", "", "Return card"});
            }
            case AMOUNT -> {
                text(g, receipt ? "Select amount (with receipt)" : "Select amount", cx, 56, yellow, 1.1f, true);
                options(g, new String[]{"£10", "£20", "£40", "£60"}, new String[]{"£100", "£200", "£250", "Other amount"});
            }
            case OTHER -> {
                text(g, "Enter amount", cx, 56, yellow, 1.2f, true);
                text(g, "In £10s, up to £250", cx, 72, white, 0.85f, true);
                text(g, "£" + entry + ((Util.getMillis() / 500) % 2 == 0 ? "_" : " "), cx, 88, white, 2f, true);
                options(g, new String[]{"", "", "", ""}, new String[]{"", "", "", "Back"});
            }
            case BALANCE -> {
                text(g, "Available balance", cx, 56, yellow, 1.1f, true);
                text(g, Atm.balanceText(balance), cx, 72, white, 2f, true);
                options(g, new String[]{"", "", "", ""}, new String[]{"", "", "Another service", "Return card"});
            }
            case PAY_IN -> {
                text(g, "Pay in cash", cx, 56, yellow, 1.2f, true);
                if (carried > 0) {
                    text(g, "You have " + CashItem.money(carried) + " in cash", cx, 72, white, 1f, true);
                    text(g, "Pay it all in?", cx, 86, white, 1f, true);
                    options(g, new String[]{"", "", "", ""}, new String[]{"", "", "Pay in", "Cancel"});
                } else {
                    text(g, "You haven't any cash on you", cx, 80, white, 1f, true);
                    options(g, new String[]{"", "", "", ""}, new String[]{"", "", "", "Back"});
                }
            }
            case PAID_IN -> {
                text(g, "Thank you", cx, 56, yellow, 1.2f, true);
                text(g, CashItem.money(paidIn) + " paid in", cx, 70, white, 1.2f, true);
                text(g, "Balance " + Atm.balanceText(balance), cx, 84, white, 1f, true);
                options(g, new String[]{"", "", "", ""}, new String[]{"", "", "Another service", "Return card"});
            }
            case PIN_CHANGED -> text(g, "Your PIN has been changed", cx, 86, yellow, 1.1f, true);
            case PROBLEM -> {
                text(g, problem, cx, 74, yellow, 1.2f, true);
                text(g, problem2, cx, 96, white, 0.9f, true);
            }
            case TAKE_CARD -> {
                text(g, "Please take your card", cx, 76, yellow, 1.3f, true);
                if (after == Stage.TAKE_CASH) {
                    text(g, "Your cash is being counted", cx, 98, white, 0.9f, true);
                }
            }
            case TAKE_CASH -> {
                text(g, "Please take your cash", cx, 76, yellow, 1.3f, true);
                if (printed) {
                    text(g, "and your receipt", cx, 96, white, 1f, true);
                }
            }
            case THANKS -> {
                text(g, "Thank you for banking with", cx, 76, white, 1f, true);
                text(g, bank, cx, 92, yellow, 1.2f, true);
            }
        }
    }

    /** The PIN's four boxes, a dot in each number put in. */
    private void stars(GuiGraphics g, int y) {
        for (int i = 0; i < 4; i++) {
            int x = 150 - 38 + i * 20;
            g.renderOutline(x, y, 16, 16, 0xFFFFFFFF);
            if (i < entry.length()) {
                g.fill(x + 5, y + 5, x + 11, y + 11, 0xFFFFFFFF);
            }
        }
    }

    /** The options beside the side keys: left ones by the left edge, right ones by the right. */
    private void options(GuiGraphics g, String[] left, String[] right) {
        for (int r = 0; r < 4; r++) {
            int y = ROWS[r] - 3;
            if (!left[r].isEmpty()) {
                g.fill(55, ROWS[r] - 1, 59, ROWS[r] + 1, 0xFFFFFFFF);
                g.drawString(font, left[r], 62, y, 0xFFFFFFFF, false);
            }
            if (!right[r].isEmpty()) {
                g.fill(241, ROWS[r] - 1, 245, ROWS[r] + 1, 0xFFFFFFFF);
                g.drawString(font, right[r], 238 - font.width(right[r]), y, 0xFFFFFFFF, false);
            }
        }
    }

    /** Text centred on x (or from x), at a size. */
    private void text(GuiGraphics g, String s, double x, double y, int colour, float size, boolean centred) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(size, size, 1);
        g.drawString(font, s, -font.width(s) / 2, 0, colour, false);
        g.pose().popPose();
    }
}
