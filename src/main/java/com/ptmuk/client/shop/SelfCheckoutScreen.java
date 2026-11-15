package com.ptmuk.client.shop;

import com.ptmuk.money.CashItem;
import com.ptmuk.network.PtmUkNetwork;
import com.ptmuk.network.SelfCheckoutMessage;
import com.ptmuk.network.SelfCheckoutMessage.Action;
import com.ptmuk.network.SelfCheckoutReplyMessage;
import com.ptmuk.shop.Product;
import com.ptmuk.shop.ProductItem;
import com.ptmuk.shop.SelfCheckoutBlockEntity;
import com.ptmuk.shop.ShopBlockEntity;
import com.ptmuk.shop.ShopRegistry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * A self-checkout's touch screen, close up: what's in your basket (click a thing to scan it), what's
 * been scanned with the total, and the buttons: scan all, look up a loose item, key in a code,
 * remove, bags, receipt, help, finish and pay (card, phone or cash). The shopping is kept in the
 * machine, so closing this (Escape) loses nothing: its screen in the world shows it too, and you
 * can scan by clicking the machine with a thing in your hand, and pay with your card or cash.
 */
public class SelfCheckoutScreen extends Screen {
    private static final int W = 380, H = 230;
    private static final int BRAND = 0xFF0B7A45, BRAND_DARK = 0xFF075C33, INK = 0xFF16222C, SOFT = 0xFF3E4C58, PAPER = 0xFFF6F8F9,
            LINE = 0xFFCDD5DB, BLUE = 0xFF1F5FAF, GREY = 0xFF6A7681, RED = 0xFFC0392B, PURPLE = 0xFF8E44AD;

    private enum Stage { MAIN, LOOKUP, LOOKUP_ITEMS, CODE, PAY, CARD, PIN, CASH, WAIT, DONE, PROBLEM }

    private final BlockPos pos;
    private Stage stage = Stage.MAIN;
    private Product.Shelf aisle;
    private Product selected;
    private boolean card;
    private String entry = "", message = "", message2 = "";
    private long since = Util.getMillis();
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

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private SelfCheckoutBlockEntity till() {
        var level = Minecraft.getInstance().level;
        return level != null && level.getBlockEntity(pos) instanceof SelfCheckoutBlockEntity t ? t : null;
    }

    private void send(Action action, int arg) {
        PtmUkNetwork.CHANNEL.sendToServer(new SelfCheckoutMessage(pos, action, arg));
    }

    /** This shop's price for a loose thing (as the machine will charge it). */
    private int price(Product p) {
        var level = Minecraft.getInstance().level;
        ShopBlockEntity best = null;
        double bestD = Double.MAX_VALUE;
        if (level != null) {
            for (BlockPos q : BlockPos.betweenClosed(pos.offset(-16, -2, -16), pos.offset(16, 3, 16))) {
                if (level.getBlockEntity(q) instanceof ShopBlockEntity s && q.distSqr(pos) < bestD) {
                    bestD = q.distSqr(pos);
                    best = s;
                }
            }
        }
        return best != null ? best.price(p) : p.pence;
    }

    /** The unpaid things from here in the player's inventory: {slot, stack}. */
    private List<Object[]> basket() {
        List<Object[]> out = new ArrayList<>();
        var p = Minecraft.getInstance().player;
        SelfCheckoutBlockEntity t = till();
        if (p != null && t != null) {
            for (int i = 0; i < p.getInventory().items.size(); i++) {
                ItemStack s = p.getInventory().items.get(i);
                if (t.fromHere(s)) {
                    out.add(new Object[]{i, s});
                }
            }
        }
        return out;
    }

    private void go(Stage s) {
        stage = s;
        since = Util.getMillis();
        if (s == Stage.CODE || s == Stage.PIN) {
            entry = "";
            message = "";
        }
    }

    private static void play(SoundEvent s, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, 0.5f));
    }

    private void pay(boolean byCard) {
        card = byCard;
        send(byCard ? Action.PAY_CARD : Action.PAY_CASH, 0);
        go(Stage.WAIT);
    }

    private void onReply(SelfCheckoutReplyMessage msg) {
        switch (msg.result()) {
            case OK -> {
                message = "Thank you for shopping with us";
                message2 = msg.change() > 0 ? "Please take your change (" + CashItem.money(msg.change()) + ") and your shopping" : "Please take your shopping";
                go(Stage.DONE);
            }
            case NO_CARD -> problem("No bank card found", "Pay with cash, or bring your PTM2 bank card");
            case DECLINED -> problem("Card declined", "Please choose another way to pay");
            case SHORT -> problem("Not enough cash", "The total is " + CashItem.money(msg.total()));
            case NOTHING -> problem("Nothing to pay for", "Scan or look up an item first");
            case WAIT -> problem("Please wait", "A colleague is on their way");
        }
    }

    private void problem(String a, String b) {
        message = a;
        message2 = b;
        go(Stage.PROBLEM);
    }

    @Override
    public void tick() {
        long a = Util.getMillis() - since;
        if (stage == Stage.DONE && a > 3500) {
            onClose();
        }
        if (stage == Stage.PROBLEM && a > 2800) {
            go(Stage.MAIN);
        }
        if (till() == null) {
            onClose();
        }
    }

    // ------------------------------------------------------------------ buttons

    private record Pad(int x, int y, int w, int h, String label, int colour, Runnable action) {
    }

    private List<Pad> pads() {
        List<Pad> out = new ArrayList<>();
        SelfCheckoutBlockEntity t = till();
        if (t == null || !t.assist().isEmpty()) {
            return out;
        }
        int rx = left + W - 104, rw = 96;
        out.add(new Pad(left + W - 58, top + 4, 52, 15, "Help", 0xFF2E86DE, () -> send(Action.HELP, 0)));
        switch (stage) {
            case MAIN -> {
                List<Object[]> basket = basket();
                for (int i = 0; i < Math.min(9, basket.size()); i++) {
                    int slot = (int) basket.get(i)[0];
                    out.add(new Pad(left + 8, top + 42 + i * 18, 118, 17, "", 0x00FFFFFF, () -> send(Action.SCAN_SLOT, slot)));
                }
                int y = top + 30;
                if (!basket.isEmpty()) {
                    out.add(new Pad(rx, y, rw, 20, "Scan all", BRAND, () -> send(Action.SCAN_ALL, 0)));
                    y += 23;
                }
                out.add(new Pad(rx, y, rw, 20, "Look up item", BLUE, () -> go(Stage.LOOKUP)));
                out.add(new Pad(rx, y + 23, rw, 20, "Enter code", BLUE, () -> go(Stage.CODE)));
                if (selected != null) {
                    Product p = selected;
                    out.add(new Pad(rx, y + 46, rw, 20, "Remove item", RED, () -> {
                        send(Action.EXTRA_REMOVE, p.ordinal());
                        selected = null;
                    }));
                }
                out.add(new Pad(rx, top + H - 104, 45, 18, "Bag -", GREY, () -> send(Action.BAGS, -1)));
                out.add(new Pad(rx + 51, top + H - 104, 45, 18, "Bag +", GREY, () -> send(Action.BAGS, 1)));
                out.add(new Pad(rx, top + H - 82, rw, 18, "Receipt: " + (t.receipt() ? "yes" : "no"), GREY, () -> send(Action.RECEIPT, 0)));
                if (t.count() > 0) {
                    out.add(new Pad(rx, top + H - 60, rw, 30, "Finish & Pay", BRAND, () -> go(Stage.PAY)));
                }
                if (t.busy()) {
                    out.add(new Pad(rx, top + H - 26, rw, 18, "Cancel", 0xFF8A949C, () -> send(Action.CANCEL, 0)));
                }
            }
            case LOOKUP -> {
                Product.Shelf[] aisles = {Product.Shelf.FRESH, Product.Shelf.GROCERIES, Product.Shelf.CHILLED, Product.Shelf.SNACKS,
                        Product.Shelf.DRINKS, Product.Shelf.HOUSEHOLD};
                String[] names = {"Fruit & veg", "Bakery & grocery", "Chilled", "Snacks & sweets", "Drinks", "Household"};
                for (int i = 0; i < aisles.length; i++) {
                    Product.Shelf a = aisles[i];
                    out.add(new Pad(left + 30 + (i % 3) * 110, top + 54 + (i / 3) * 54, 104, 46, names[i], BLUE, () -> {
                        aisle = a;
                        go(Stage.LOOKUP_ITEMS);
                    }));
                }
                out.add(new Pad(left + 14, top + H - 32, 80, 20, "Back", GREY, () -> go(Stage.MAIN)));
            }
            case LOOKUP_ITEMS -> {
                int i = 0;
                for (Product p : Product.values()) {
                    if (p.shelf == aisle && i < 10) {
                        out.add(new Pad(left + 14 + (i % 5) * 71, top + 46 + (i / 5) * 66, 66, 60, "", 0xFFFFFFFF, () -> {
                            send(Action.EXTRA_ADD, p.ordinal());
                            go(Stage.MAIN);
                        }));
                        i++;
                    }
                }
                out.add(new Pad(left + 14, top + H - 32, 80, 20, "Back", GREY, () -> go(Stage.LOOKUP)));
            }
            case CODE, PIN -> {
                for (int k = 0; k < 12; k++) {
                    String label = k < 9 ? String.valueOf(k + 1) : k == 9 ? "Clear" : k == 10 ? "0" : "Enter";
                    int key = k;
                    out.add(new Pad(left + 200 + (k % 3) * 50, top + 40 + (k / 3) * 42, 46, 38, label,
                            k == 11 ? BRAND : k == 9 ? 0xFFB9770E : 0xFF465563, () -> keypad(key)));
                }
                out.add(new Pad(left + 14, top + H - 32, 80, 20, "Back", GREY, () -> go(stage == Stage.PIN ? Stage.CARD : Stage.MAIN)));
            }
            case PAY -> {
                out.add(new Pad(left + 24, top + 96, 106, 54, "Card", BRAND, () -> go(Stage.CARD)));
                out.add(new Pad(left + 137, top + 96, 106, 54, "Phone or watch", BLUE, () -> pay(true)));
                out.add(new Pad(left + 250, top + 96, 106, 54, "Cash", PURPLE, () -> go(Stage.CASH)));
                out.add(new Pad(left + 14, top + H - 32, 120, 20, "Back to scanning", GREY, () -> go(Stage.MAIN)));
            }
            case CARD -> {
                out.add(new Pad(left + 50, top + 100, 130, 54, t.total() <= 10000 ? "Tap card" : "Tap (over £100)", BRAND, () -> {
                    if (t.total() > 10000) {
                        problem("Contactless limit is £100", "Please insert your card");
                    } else {
                        pay(true);
                    }
                }));
                out.add(new Pad(left + 200, top + 100, 130, 54, "Insert card (PIN)", BLUE, () -> go(Stage.PIN)));
                out.add(new Pad(left + 14, top + H - 32, 80, 20, "Back", GREY, () -> go(Stage.PAY)));
            }
            case CASH -> {
                out.add(new Pad(left + 125, top + 124, 130, 40, "Insert cash", PURPLE, () -> pay(false)));
                out.add(new Pad(left + 14, top + H - 32, 80, 20, "Back", GREY, () -> go(Stage.PAY)));
            }
            default -> {
            }
        }
        return out;
    }

    private void keypad(int key) {
        play(SoundEvents.NOTE_BLOCK_HAT.value(), 1.8f);
        if (key == 9) {
            entry = "";
        } else if (key == 11) {
            if (stage == Stage.PIN) {
                if (entry.length() == 4) {
                    pay(true);
                }
                return;
            }
            for (Product p : Product.values()) {
                if (p.onShelf() && String.valueOf(p.code()).equals(entry)) {
                    send(Action.EXTRA_ADD, p.ordinal());
                    go(Stage.MAIN);
                    return;
                }
            }
            entry = "";
            message = "Code not found";
        } else if (entry.length() < 4) {
            entry += key == 10 ? "0" : String.valueOf(key + 1);
            message = "";
        }
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
        SelfCheckoutBlockEntity t = till();
        if (stage == Stage.MAIN && t != null && mx >= left + 132 && mx <= left + W - 110 && my >= top + 42) {
            int row = (int) ((my - top - 42) / 16), extrasFrom = t.scanned().size();
            List<SelfCheckoutBlockEntity.Extra> ex = t.extras();
            int first = Math.max(0, extrasFrom + ex.size() + (t.bags() > 0 ? 1 : 0) - 9);
            int i = first + row - extrasFrom;
            selected = i >= 0 && i < ex.size() ? ex.get(i).product() : null;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ((stage == Stage.CODE || stage == Stage.PIN) && key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) {
            keypad(key == GLFW.GLFW_KEY_0 ? 10 : key - GLFW.GLFW_KEY_1);
            return true;
        }
        if ((stage == Stage.CODE || stage == Stage.PIN) && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            keypad(11);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        left = (width - W) / 2;
        top = (height - H) / 2;
        SelfCheckoutBlockEntity t = till();
        if (t == null) {
            return;
        }
        g.fill(left - 7, top - 7, left + W + 7, top + H + 7, 0xFF23272B);
        g.fill(left, top, left + W, top + H, PAPER);
        g.fill(left, top, left + W, top + 23, BRAND);
        g.fill(left, top + 23, left + W, top + 25, BRAND_DARK);
        g.drawString(font, t.shopName(), left + 8, top + 8, 0xFFFFFFFF, true);
        g.drawString(font, "Self-checkout", left + W - 64 - font.width("Self-checkout"), top + 8, 0xFFFFFFFF, true);
        int cx = left + W / 2;
        switch (stage) {
            case MAIN -> main(g, t, mx, my);
            case LOOKUP -> {
                big(g, "Look up an item", cx, top + 32, INK, 1.2f);
                centred(g, "For loose items without a barcode", cx, top + H - 50, SOFT);
            }
            case LOOKUP_ITEMS -> big(g, "Choose your item", cx, top + 31, INK, 1.1f);
            case CODE -> {
                big(g, "Enter the item's code", left + 100, top + 40, INK, 1.1f);
                box(g, left + 40, top + 66, entry + ((Util.getMillis() / 500) % 2 == 0 ? "_" : " "));
                g.drawString(font, "Bananas 4011", left + 30, top + 106, SOFT, false);
                g.drawString(font, "Apples 4131", left + 30, top + 118, SOFT, false);
                g.drawString(font, "(on the shelf labels)", left + 30, top + 130, SOFT, false);
                g.drawString(font, message, left + 30, top + 148, RED, false);
            }
            case PIN -> {
                big(g, "Enter your PIN", left + 100, top + 40, INK, 1.2f);
                for (int i = 0; i < 4; i++) {
                    int x = left + 44 + i * 26;
                    g.fill(x, top + 66, x + 20, top + 88, 0xFFFFFFFF);
                    g.renderOutline(x, top + 66, 20, 22, SOFT);
                    if (i < entry.length()) {
                        g.fill(x + 7, top + 74, x + 13, top + 80, INK);
                    }
                }
                g.drawString(font, "Total " + CashItem.money(t.total()), left + 30, top + 106, INK, false);
            }
            case PAY -> {
                big(g, "How would you like to pay?", cx, top + 38, INK, 1.2f);
                big(g, "To pay  " + CashItem.money(t.total()), cx, top + 62, BRAND_DARK, 1.4f);
                centred(g, "Or click the checkout with your bank card or cash", cx, top + 168, SOFT);
            }
            case CARD -> {
                big(g, "Tap, insert or swipe your card", cx, top + 44, INK, 1.2f);
                big(g, CashItem.money(t.total()), cx, top + 68, BRAND_DARK, 1.4f);
            }
            case CASH -> {
                big(g, "Insert notes and coins", cx, top + 44, INK, 1.2f);
                big(g, "To pay  " + CashItem.money(t.total()), cx, top + 68, BRAND_DARK, 1.3f);
                var p = Minecraft.getInstance().player;
                centred(g, "You have " + CashItem.money(p == null ? 0 : CashItem.carried(p)) + " in cash. Change comes out below.", cx, top + 98, SOFT);
            }
            case WAIT -> big(g, (card ? "Processing your card" : "Counting your cash") + ".".repeat((int) (Util.getMillis() / 400 % 4)), cx,
                    top + 104, INK, 1.2f);
            case DONE, PROBLEM -> {
                big(g, message, cx, top + 90, stage == Stage.DONE ? BRAND_DARK : RED, 1.2f);
                centred(g, message2, cx, top + 114, INK);
            }
        }
        for (Pad p : pads()) {
            if ((p.colour() >>> 24) == 0) {
                continue;                                   // the basket's rows: drawn by main()
            }
            boolean over = mx >= p.x() && mx <= p.x() + p.w() && my >= p.y() && my <= p.y() + p.h();
            g.fill(p.x(), p.y() + 2, p.x() + p.w(), p.y() + p.h() + 2, 0x33000000);
            g.fill(p.x(), p.y(), p.x() + p.w(), p.y() + p.h(), over ? lighter(p.colour()) : p.colour());
            if (p.colour() == 0xFFFFFFFF) {
                g.renderOutline(p.x(), p.y(), p.w(), p.h(), LINE);
            } else {
                g.drawCenteredString(font, p.label(), p.x() + p.w() / 2, p.y() + (p.h() - 8) / 2, 0xFFFFFFFF);
            }
        }
        if (stage == Stage.LOOKUP_ITEMS) {
            int i = 0;
            for (Product p : Product.values()) {
                if (p.shelf == aisle && i < 10) {
                    int x = left + 14 + (i % 5) * 71, y = top + 46 + (i / 5) * 66;
                    g.renderItem(new ItemStack(ShopRegistry.item(p)), x + 25, y + 5);
                    centred(g, font.plainSubstrByWidth(p.title, 62), x + 33, y + 27, INK);
                    centred(g, CashItem.money(price(p)), x + 33, y + 43, BRAND_DARK);
                    i++;
                }
            }
        }
        if (!t.assist().isEmpty()) {
            g.fill(left + 40, top + 76, left + W - 40, top + 150, 0xFFFFC94D);
            g.renderOutline(left + 40, top + 76, W - 80, 74, 0xFF7A5300);
            big(g, t.assist(), cx, top + 96, INK, 1.0f);
            centred(g, "Please wait" + ".".repeat((int) (Util.getMillis() / 400 % 4)), cx, top + 122, INK);
        }
        super.render(g, mx, my, pt);
    }

    /** The scanning screen: your basket (click to scan), what's scanned, the total. */
    private void main(GuiGraphics g, SelfCheckoutBlockEntity t, int mx, int my) {
        // your basket
        int bx = left + 8, by = top + 28;
        g.drawString(font, "Your basket", bx, by + 1, SOFT, false);
        if (!basket().isEmpty()) {
            g.drawString(font, "Click a thing to scan it", bx + 4, top + H - 20, BLUE, false);
        }
        g.fill(bx, by + 12, bx + 118, top + H - 8, 0xFFFFFFFF);
        g.renderOutline(bx, by + 12, 118, H - 48, LINE);
        List<Object[]> basket = basket();
        if (basket.isEmpty()) {
            g.drawString(font, "Nothing from", bx + 6, by + 20, SOFT, false);
            g.drawString(font, "this shop on you.", bx + 6, by + 31, SOFT, false);
            g.drawString(font, "Take things off", bx + 6, by + 48, SOFT, false);
            g.drawString(font, "the shelves first.", bx + 6, by + 59, SOFT, false);
        }
        for (int i = 0; i < Math.min(9, basket.size()); i++) {
            ItemStack s = (ItemStack) basket.get(i)[1];
            int ry = top + 42 + i * 18;
            if (mx >= bx && mx <= bx + 118 && my >= ry && my <= ry + 17) {
                g.fill(bx + 1, ry, bx + 117, ry + 17, 0xFFDDEBF7);
            }
            g.renderItem(s, bx + 2, ry);
            g.drawString(font, font.plainSubstrByWidth((s.getCount() > 1 ? s.getCount() + " x " : "") + s.getHoverName().getString(), 92),
                    bx + 21, ry + 5, INK, false);
        }
        // scanned
        int sx = left + 132, sw = W - 244;
        g.drawString(font, "Scanned", sx, by + 1, SOFT, false);
        g.fill(sx, by + 12, sx + sw, top + H - 34, 0xFFFFFFFF);
        g.renderOutline(sx, by + 12, sw, H - 74, LINE);
        List<Object[]> lines = new ArrayList<>();
        for (ItemStack s : t.scanned()) {
            lines.add(new Object[]{(s.getCount() > 1 ? s.getCount() + " x " : "") + s.getHoverName().getString(),
                    (long) ProductItem.price(s) * s.getCount(), null});
        }
        for (SelfCheckoutBlockEntity.Extra e : t.extras()) {
            lines.add(new Object[]{(e.count() > 1 ? e.count() + " x " : "") + e.product().title, (long) e.each() * e.count(), e.product()});
        }
        if (t.bags() > 0) {
            lines.add(new Object[]{(t.bags() > 1 ? t.bags() + " x " : "") + "Carrier bag", (long) t.bags() * SelfCheckoutBlockEntity.BAG, null});
        }
        if (lines.isEmpty()) {
            centred(g, "Scan your first item", sx + sw / 2, by + 60, SOFT);
        }
        int first = Math.max(0, lines.size() - 9);
        for (int i = first; i < lines.size(); i++) {
            int ry = top + 42 + (i - first) * 16;
            Object[] l = lines.get(i);
            if (l[2] != null && l[2] == selected) {
                g.fill(sx + 1, ry - 2, sx + sw - 1, ry + 12, 0xFFDDEBF7);
            }
            String price = CashItem.money((long) l[1]);
            g.drawString(font, font.plainSubstrByWidth((String) l[0], sw - 14 - font.width(price)), sx + 4, ry, INK, false);
            g.drawString(font, price, sx + sw - 4 - font.width(price), ry, INK, false);
        }
        g.fill(sx, top + H - 32, sx + sw, top + H - 8, 0xFFE7ECEF);
        g.drawString(font, t.count() + (t.count() == 1 ? " item" : " items"), sx + 4, top + H - 24, SOFT, false);
        String total = CashItem.money(t.total());
        big(g, total, sx + sw - 6 - (int) (font.width(total) * 1.3f / 2), top + H - 26, INK, 1.3f);
    }

    private void centred(GuiGraphics g, String text, int x, int y, int colour) {
        g.drawString(font, text, x - font.width(text) / 2, y, colour, false);
    }

    private void big(GuiGraphics g, String s, int cx, int y, int colour, float size) {
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(size, size, 1);
        g.drawString(font, s, -font.width(s) / 2, 0, colour, false);
        g.pose().popPose();
    }

    private void box(GuiGraphics g, int x, int y, String s) {
        g.fill(x, y, x + 110, y + 26, 0xFFFFFFFF);
        g.renderOutline(x, y, 110, 26, SOFT);
        big(g, s, x + 55, y + 7, INK, 1.4f);
    }

    private static int lighter(int c) {
        int r = Math.min(255, ((c >> 16) & 255) + 30), gr = Math.min(255, ((c >> 8) & 255) + 30), b = Math.min(255, (c & 255) + 30);
        return 0xFF000000 | r << 16 | gr << 8 | b;
    }
}
