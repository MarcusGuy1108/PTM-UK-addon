package com.ptmuk.client.shop;

import com.ptmuk.money.CashItem;
import com.ptmuk.network.PtmUkNetwork;
import com.ptmuk.network.SelfCheckoutMessage;
import com.ptmuk.network.SelfCheckoutReplyMessage;
import com.ptmuk.shop.ProductItem;
import com.ptmuk.shop.SelfCheckout;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

/**
 * A self-checkout's touch screen: Start, then each thing you're buying scanned (beep), the
 * running total, now and then an unexpected item in the bagging area (a member of staff comes
 * and sorts it), then Pay: card or cash. The paying itself happens on the server.
 */
public class SelfCheckoutScreen extends Screen {
    private static final int W = 300, H = 200;
    private static final int GREEN = 0xFF00824A, DARK = 0xFF1E2A33, LIGHT = 0xFFF3F5F6;

    private enum Stage { WELCOME, SCANNING, UNEXPECTED, APPROVED, PAY, WAIT, DONE, PROBLEM }

    private final BlockPos pos;
    private final List<ItemStack> items = new ArrayList<>();
    private Stage stage = Stage.WELCOME;
    private long since = Util.getMillis();
    private int scanned;
    private boolean bagging;
    private String message = "", message2 = "";
    private int left, top;

    public SelfCheckoutScreen(BlockPos pos) {
        super(Component.literal("Self-checkout"));
        this.pos = pos;
    }

    public static void reply(SelfCheckoutReplyMessage msg) {
        if (Minecraft.getInstance().screen instanceof SelfCheckoutScreen s) {
            s.onReply(msg);
        }
    }

    private void go(Stage s) {
        stage = s;
        since = Util.getMillis();
    }

    private void onReply(SelfCheckoutReplyMessage msg) {
        switch (msg.result()) {
            case OK -> {
                message = "Thank you for shopping with us";
                message2 = msg.change() > 0 ? "Please take your change: " + CashItem.money(msg.change()) + ", and your receipt" : "Please take your receipt";
                play(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.4f);
                go(Stage.DONE);
            }
            case NO_CARD -> problem("No bank card found", "Pay with cash instead?");
            case DECLINED -> problem("Card declined", "Please try another way to pay");
            case SHORT -> problem("Not enough cash", "Total " + CashItem.money(msg.total()));
            case NOTHING -> problem("Nothing to pay for", "");
        }
    }

    private void problem(String a, String b) {
        message = a;
        message2 = b;
        play(SoundEvents.NOTE_BLOCK_BASS.value(), 0.8f);
        go(Stage.PROBLEM);
    }

    private static void play(net.minecraft.sounds.SoundEvent s, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, 0.5f));
    }

    /** What's to scan: the unpaid things on you from the shops round here. */
    private void collect() {
        items.clear();
        var p = Minecraft.getInstance().player;
        if (p != null) {
            for (ItemStack s : p.getInventory().items) {
                BlockPos from = ProductItem.shop(s);
                if (ProductItem.unpaid(s) && from != null && from.distSqr(pos) <= 48 * 48) {
                    items.add(s.copy());
                }
            }
        }
    }

    private long total(int upTo) {
        long t = 0;
        for (int i = 0; i < Math.min(upTo, items.size()); i++) {
            t += (long) ProductItem.price(items.get(i)) * items.get(i).getCount();
        }
        return t;
    }

    @Override
    public void tick() {
        long a = Util.getMillis() - since;
        switch (stage) {
            case SCANNING -> {
                if (a > 450L * (scanned + 1) && scanned < items.size()) {
                    scanned++;
                    play(SoundEvents.NOTE_BLOCK_BIT.value(), 2.0f);
                    if (!bagging && scanned >= 2 && Minecraft.getInstance().level != null
                            && Minecraft.getInstance().level.random.nextInt(4) == 0) {
                        bagging = true;                 // once a visit, sometimes
                        play(SoundEvents.NOTE_BLOCK_BASS.value(), 1.0f);
                        go(Stage.UNEXPECTED);
                    }
                } else if (scanned >= items.size() && a > 450L * scanned + 300) {
                    go(Stage.PAY);
                }
            }
            case UNEXPECTED -> {
                if (a > 2600) {
                    play(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.6f);
                    go(Stage.APPROVED);
                }
            }
            case APPROVED -> {
                if (a > 1400) {
                    stage = Stage.SCANNING;
                    since = Util.getMillis() - 450L * scanned;
                }
            }
            case DONE -> {
                if (a > 3500) {
                    onClose();
                }
            }
            case PROBLEM -> {
                if (a > 2800) {
                    go(items.isEmpty() ? Stage.WELCOME : Stage.PAY);
                }
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ the screen's buttons

    private record Pad(int x, int y, int w, int h, String label, int colour, Runnable action) {
    }

    private List<Pad> pads() {
        List<Pad> out = new ArrayList<>();
        int bx = left + W - 104;
        switch (stage) {
            case WELCOME -> out.add(new Pad(left + 90, top + 120, 120, 30, "Start", GREEN, () -> {
                collect();
                if (items.isEmpty()) {
                    problem("Nothing to scan", "Take what you're buying off the shelves first");
                } else {
                    scanned = 0;
                    bagging = false;
                    go(Stage.SCANNING);
                }
            }));
            case PAY -> {
                out.add(new Pad(bx, top + 70, 92, 30, "Pay by card", GREEN, () -> {
                    PtmUkNetwork.CHANNEL.sendToServer(new SelfCheckoutMessage(pos, true));
                    go(Stage.WAIT);
                }));
                out.add(new Pad(bx, top + 108, 92, 30, "Pay by cash", 0xFF2D6CB5, () -> {
                    PtmUkNetwork.CHANNEL.sendToServer(new SelfCheckoutMessage(pos, false));
                    go(Stage.WAIT);
                }));
                out.add(new Pad(bx, top + 146, 92, 22, "Cancel", 0xFF8A949C, this::onClose));
            }
            default -> {
            }
        }
        return out;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (Pad p : pads()) {
            if (mx >= p.x() && mx <= p.x() + p.w() && my >= p.y() && my <= p.y() + p.h()) {
                play(SoundEvents.NOTE_BLOCK_HAT.value(), 1.8f);
                p.action().run();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        left = (width - W) / 2;
        top = (height - H) / 2;
        // the terminal: dark bezel, the screen, the shop's green bar
        g.fill(left - 8, top - 8, left + W + 8, top + H + 8, 0xFF2B2F33);
        g.fill(left, top, left + W, top + H, LIGHT);
        g.fill(left, top, left + W, top + 22, GREEN);
        g.drawString(font, "Self-Checkout", left + 8, top + 7, 0xFFFFFFFF, false);
        String help = "Need help? Staff are nearby";
        g.drawString(font, help, left + W - 8 - font.width(help), top + 7, 0xFFD8F0E3, false);
        switch (stage) {
            case WELCOME -> {
                g.drawCenteredString(font, "Welcome", left + W / 2, top + 50, DARK);
                g.drawCenteredString(font, "Touch Start, then scan your first item", left + W / 2, top + 70, 0xFF4A5560);
                g.drawCenteredString(font, "Have your bank card or cash ready to pay", left + W / 2, top + 88, 0xFF4A5560);
            }
            case SCANNING, PAY, WAIT, UNEXPECTED, APPROVED -> basket(g);
            case DONE, PROBLEM -> {
                g.drawCenteredString(font, message, left + W / 2, top + 80, stage == Stage.DONE ? GREEN : 0xFFC0392B);
                g.drawCenteredString(font, message2, left + W / 2, top + 100, DARK);
            }
        }
        for (Pad p : pads()) {
            boolean over = mx >= p.x() && mx <= p.x() + p.w() && my >= p.y() && my <= p.y() + p.h();
            g.fill(p.x(), p.y(), p.x() + p.w(), p.y() + p.h(), over ? (p.colour() & 0x00FFFFFF | 0xFF000000) + 0x00202020 : p.colour());
            g.drawCenteredString(font, p.label(), p.x() + p.w() / 2, p.y() + (p.h() - 8) / 2, 0xFFFFFFFF);
        }
        if (stage == Stage.UNEXPECTED || stage == Stage.APPROVED) {
            int x0 = left + 30, y0 = top + 60;
            g.fill(x0, y0, x0 + W - 60, y0 + 70, stage == Stage.UNEXPECTED ? 0xFFF5B041 : 0xFF58D68D);
            g.renderOutline(x0, y0, W - 60, 70, 0xFF6E4B0C);
            if (stage == Stage.UNEXPECTED) {
                g.drawCenteredString(font, "Unexpected item in the bagging area", left + W / 2, y0 + 20, 0xFF1E1E1E);
                g.drawCenteredString(font, "Please wait for a member of staff" + ".".repeat((int) (Util.getMillis() / 400 % 4)), left + W / 2, y0 + 40, 0xFF1E1E1E);
            } else {
                g.drawCenteredString(font, "Approved by a member of staff", left + W / 2, y0 + 30, 0xFF0B3D20);
            }
        }
        if (stage == Stage.WAIT) {
            g.drawCenteredString(font, "Follow the instructions on the reader" + ".".repeat((int) (Util.getMillis() / 400 % 4)), left + W / 2,
                    top + H - 16, DARK);
        }
        super.render(g, mx, my, pt);
    }

    /** The list of what's been scanned, and the total. */
    private void basket(GuiGraphics g) {
        int x = left + 8, y = top + 30, listW = stage == Stage.PAY || stage == Stage.WAIT ? W - 124 : W - 16;
        g.fill(x, y, x + listW, top + H - 30, 0xFFFFFFFF);
        g.renderOutline(x, y, listW, top + H - 30 - y, 0xFFD0D5D9);
        int shown = Math.min(scanned, items.size()), from = Math.max(0, shown - 7);
        for (int i = from; i < shown; i++) {
            ItemStack s = items.get(i);
            int ry = y + 4 + (i - from) * 18;
            g.renderItem(s, x + 4, ry);
            String name = (s.getCount() > 1 ? s.getCount() + " x " : "") + s.getHoverName().getString();
            g.drawString(font, font.plainSubstrByWidth(name, listW - 80), x + 24, ry + 4, DARK, false);
            String price = CashItem.money((long) ProductItem.price(s) * s.getCount());
            g.drawString(font, price, x + listW - 6 - font.width(price), ry + 4, DARK, false);
        }
        if (stage == Stage.SCANNING && shown < items.size()) {
            g.drawString(font, "Scanning...", x + 6, top + H - 44, 0xFF7A858E, false);
        }
        String total = "Total  " + CashItem.money(total(shown));
        g.fill(left, top + H - 26, left + W, top + H, 0xFFE4E8EB);
        g.drawString(font, shown + (shown == 1 ? " item" : " items"), left + 10, top + H - 17, DARK, false);
        g.drawString(font, total, left + W - 10 - font.width(total), top + H - 17, DARK, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
