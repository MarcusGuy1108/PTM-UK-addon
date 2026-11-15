package com.ptmuk.client.shop;

import com.ptmuk.money.CashItem;
import com.ptmuk.network.PtmUkNetwork;
import com.ptmuk.network.SelfCheckoutMessage;
import com.ptmuk.network.SelfCheckoutReplyMessage;
import com.ptmuk.registry.ModItems;
import com.ptmuk.shop.Product;
import com.ptmuk.shop.ProductItem;
import com.ptmuk.shop.SelfCheckout;
import com.ptmuk.shop.ShopBlockEntity;
import com.ptmuk.shop.ShopRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * A supermarket self-checkout, as at the big chains: touch to start, your own bags or not, your
 * rewards card, then the scanning screen (scan, look up loose things, key in a code, remove, help,
 * bags) with the list and total, then pay: card (tap, or chip and PIN), phone, or cash, a receipt
 * or not. Now and then an unexpected item in the bagging area, or an age check, waits for a
 * colleague. The paying itself happens on the server.
 */
public class SelfCheckoutScreen extends Screen {
    private static final int W = 340, H = 224;
    private static final int BRAND = 0xFF0B7A45, BRAND_DARK = 0xFF075C33, INK = 0xFF16222C, SOFT = 0xFF3E4C58, PAPER = 0xFFF6F8F9,
            LINE = 0xFFCDD5DB, BLUE = 0xFF1F5FAF, GREY = 0xFF6A7681, RED = 0xFFC0392B;

    private enum Stage { WELCOME, BAGS, REWARDS, SCAN, LOOKUP, LOOKUP_ITEMS, CODE, PAY, CARD, PIN, CASH, WAIT, DONE, PROBLEM }

    private final BlockPos pos;
    private final ShopBlockEntity till;
    private final String shop;
    private final List<ItemStack> items = new ArrayList<>();
    private final Map<Product, Integer> extras = new LinkedHashMap<>();
    private Stage stage = Stage.WELCOME;
    private Product.Shelf aisle;
    private int scanned, bags, selected = -1;
    private boolean rewards, receipt = true, card, bagging, ageChecked;
    private String assist, entry = "", message = "", message2 = "";
    private long assistAt, since = Util.getMillis();
    private int left, top;

    public SelfCheckoutScreen(BlockPos pos) {
        super(Component.literal("Self-checkout"));
        this.pos = pos;
        ShopBlockEntity best = null;
        var level = Minecraft.getInstance().level;
        double bestD = Double.MAX_VALUE;
        if (level != null) {
            for (BlockPos p : BlockPos.betweenClosed(pos.offset(-16, -2, -16), pos.offset(16, 3, 16))) {
                if (level.getBlockEntity(p) instanceof ShopBlockEntity s && p.distSqr(pos) < bestD) {
                    bestD = p.distSqr(pos);
                    best = s;
                }
            }
        }
        till = best;
        shop = till != null && !till.shopName().isBlank() ? till.shopName() : "Supermarket";
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

    // ------------------------------------------------------------------ what's being bought

    private int price(Product p) {
        return till != null ? till.price(p) : p.pence;
    }

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

    /** The lines on the screen: scanned things, then looked-up ones, then bags: {title, count, pence}. */
    private List<Object[]> lines() {
        List<Object[]> out = new ArrayList<>();
        for (int i = 0; i < Math.min(scanned, items.size()); i++) {
            ItemStack s = items.get(i);
            out.add(new Object[]{s, s.getCount(), (long) ProductItem.price(s) * s.getCount(), null});
        }
        extras.forEach((p, n) -> out.add(new Object[]{new ItemStack(ShopRegistry.item(p)), n, (long) price(p) * n, p}));
        if (bags > 0) {
            out.add(new Object[]{new ItemStack(net.minecraft.world.item.Items.PAPER), bags, (long) bags * SelfCheckout.BAG, "bags"});
        }
        return out;
    }

    private long total() {
        long t = 0;
        for (Object[] l : lines()) {
            t += (long) l[2];
        }
        return t;
    }

    private int count() {
        int n = 0;
        for (Object[] l : lines()) {
            n += (int) l[1];
        }
        return n;
    }

    // ------------------------------------------------------------------ what happens

    private void go(Stage s) {
        stage = s;
        since = Util.getMillis();
        if (s == Stage.CODE || s == Stage.PIN) {
            entry = "";
        }
    }

    private static void play(SoundEvent s, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, 0.5f));
    }

    private void beep() {
        play(SoundEvents.NOTE_BLOCK_BIT.value(), 2.0f);
    }

    private void wait(String why) {
        assist = why;
        assistAt = Util.getMillis();
        play(SoundEvents.NOTE_BLOCK_BASS.value(), 1.0f);
    }

    private void scanOne() {
        if (scanned >= items.size()) {
            return;
        }
        ItemStack s = items.get(scanned++);
        beep();
        if (!ageChecked && s.getItem() instanceof ProductItem pi && (pi.product == Product.ENERGY_DRINK || pi.product == Product.PARACETAMOL)) {
            ageChecked = true;
            wait("Age-restricted item: a colleague needs to approve it");
        } else if (!bagging && scanned >= 2 && Minecraft.getInstance().level != null && Minecraft.getInstance().level.random.nextInt(5) == 0) {
            bagging = true;
            wait("Unexpected item in the bagging area");
        }
    }

    private void addExtra(Product p) {
        extras.merge(p, 1, Integer::sum);
        beep();
        go(Stage.SCAN);
    }

    private void pay(boolean byCard) {
        card = byCard;
        int[] ids = new int[extras.size()], counts = new int[extras.size()];
        int i = 0;
        for (Map.Entry<Product, Integer> e : extras.entrySet()) {
            ids[i] = e.getKey().ordinal();
            counts[i++] = e.getValue();
        }
        PtmUkNetwork.CHANNEL.sendToServer(new SelfCheckoutMessage(pos, byCard, ids, counts, bags, receipt));
        go(Stage.WAIT);
    }

    private void onReply(SelfCheckoutReplyMessage msg) {
        switch (msg.result()) {
            case OK -> {
                message = "Thank you for shopping at " + shop;
                message2 = (msg.change() > 0 ? "Please take your change (" + CashItem.money(msg.change()) + ")" + (receipt ? ", " : " and ") : "Please take ")
                        + (receipt ? "your receipt and your shopping" : "your shopping");
                play(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.4f);
                go(Stage.DONE);
            }
            case NO_CARD -> problem("No bank card found", "Pay with cash, or bring your PTM2 bank card");
            case DECLINED -> problem("Card declined", "Please choose another way to pay");
            case SHORT -> problem("Not enough cash", "The total is " + CashItem.money(msg.total()));
            case NOTHING -> problem("Nothing to pay for", "Scan or look up an item first");
        }
    }

    private void problem(String a, String b) {
        message = a;
        message2 = b;
        play(SoundEvents.NOTE_BLOCK_BASS.value(), 0.8f);
        go(Stage.PROBLEM);
    }

    @Override
    public void tick() {
        long a = Util.getMillis() - since;
        if (assist != null && Util.getMillis() - assistAt > 3200) {
            assist = null;
            play(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.6f);
        }
        if (stage == Stage.DONE && a > 4000) {
            onClose();
        }
        if (stage == Stage.PROBLEM && a > 3000) {
            go(count() == 0 ? Stage.SCAN : Stage.PAY);
        }
    }

    // ------------------------------------------------------------------ buttons

    private record Pad(int x, int y, int w, int h, String label, int colour, Runnable action) {
    }

    private List<Pad> pads() {
        List<Pad> out = new ArrayList<>();
        if (assist != null) {
            return out;
        }
        int rx = left + W - 118, rw = 110;
        out.add(new Pad(left + W - 58, top + 4, 52, 15, "Help", 0xFF2E86DE, () -> wait("A colleague is on their way to help")));
        switch (stage) {
            case WELCOME -> out.add(new Pad(left + 95, top + 120, 150, 36, "Touch to start", BRAND, () -> {
                collect();
                go(Stage.BAGS);
            }));
            case BAGS -> {
                out.add(new Pad(left + 40, top + 90, 120, 50, "I've brought my own", BRAND, () -> go(Stage.REWARDS)));
                out.add(new Pad(left + 180, top + 90, 120, 50, "I need a bag (10p)", BLUE, () -> {
                    bags = 1;
                    go(Stage.REWARDS);
                }));
            }
            case REWARDS -> {
                out.add(new Pad(left + 40, top + 90, 120, 50, "Scan rewards card", 0xFFB9770E, () -> {
                    rewards = true;
                    beep();
                    go(Stage.SCAN);
                }));
                out.add(new Pad(left + 180, top + 90, 120, 50, "Not today", GREY, () -> go(Stage.SCAN)));
            }
            case SCAN -> {
                int y = top + 28;
                if (scanned < items.size()) {
                    out.add(new Pad(rx, y, rw, 20, "Scan next item", BRAND, this::scanOne));
                    out.add(new Pad(rx, y + 23, rw, 20, "Scan all (" + (items.size() - scanned) + ")", BRAND, () -> {
                        while (scanned < items.size() && assist == null) {
                            scanOne();
                        }
                    }));
                    y += 46;
                }
                out.add(new Pad(rx, y, rw, 20, "Look up item", BLUE, () -> go(Stage.LOOKUP)));
                out.add(new Pad(rx, y + 23, rw, 20, "Enter code", BLUE, () -> go(Stage.CODE)));
                Object[] sel = selected >= 0 && selected < lines().size() ? lines().get(selected) : null;
                if (sel != null && sel[3] != null) {
                    out.add(new Pad(rx, y + 46, rw, 20, "Remove item", RED, () -> {
                        if (sel[3] instanceof Product p) {
                            extras.computeIfPresent(p, (k, n) -> n > 1 ? n - 1 : null);
                        } else {
                            bags = Math.max(0, bags - 1);
                        }
                        selected = -1;
                        wait("Removing an item: a colleague is approving it");
                    }));
                }
                out.add(new Pad(rx, top + H - 92, 52, 18, "Bag -", GREY, () -> bags = Math.max(0, bags - 1)));
                out.add(new Pad(rx + 58, top + H - 92, 52, 18, "Bag +", GREY, () -> bags = Math.min(9, bags + 1)));
                if (count() > 0) {
                    out.add(new Pad(rx, top + H - 68, rw, 34, "Finish & Pay", BRAND, () -> {
                        if (scanned < items.size()) {
                            problem("Please scan all your items", (items.size() - scanned) + " still to scan");
                        } else {
                            go(Stage.PAY);
                        }
                    }));
                }
            }
            case LOOKUP -> {
                Product.Shelf[] aisles = {Product.Shelf.FRESH, Product.Shelf.GROCERIES, Product.Shelf.CHILLED, Product.Shelf.SNACKS,
                        Product.Shelf.DRINKS, Product.Shelf.HOUSEHOLD};
                String[] names = {"Fruit & veg", "Bakery & grocery", "Chilled", "Snacks & sweets", "Drinks", "Household"};
                for (int i = 0; i < aisles.length; i++) {
                    Product.Shelf a = aisles[i];
                    out.add(new Pad(left + 14 + (i % 3) * 106, top + 50 + (i / 3) * 52, 100, 44, names[i], BLUE, () -> {
                        aisle = a;
                        go(Stage.LOOKUP_ITEMS);
                    }));
                }
                out.add(new Pad(left + 14, top + H - 34, 80, 22, "Back", GREY, () -> go(Stage.SCAN)));
            }
            case LOOKUP_ITEMS -> {
                int i = 0;
                for (Product p : Product.values()) {
                    if (p.shelf == aisle && i < 8) {
                        out.add(new Pad(left + 14 + (i % 4) * 79, top + 50 + (i / 4) * 64, 74, 58, "", 0xFFFFFFFF, () -> addExtra(p)));
                        i++;
                    }
                }
                out.add(new Pad(left + 14, top + H - 34, 80, 22, "Back", GREY, () -> go(Stage.LOOKUP)));
            }
            case CODE, PIN -> {
                for (int k = 0; k < 12; k++) {
                    int c = k % 3, r = k / 3;
                    String label = k < 9 ? String.valueOf(k + 1) : k == 9 ? "Clear" : k == 10 ? "0" : "Enter";
                    int key = k;
                    out.add(new Pad(left + 180 + c * 46, top + 42 + r * 40, 42, 36, label, k == 11 ? BRAND : k == 9 ? 0xFFB9770E : 0xFF465563,
                            () -> keypad(key)));
                }
                out.add(new Pad(left + 14, top + H - 34, 80, 22, "Back", GREY, () -> go(stage == Stage.PIN ? Stage.CARD : Stage.SCAN)));
            }
            case PAY -> {
                out.add(new Pad(left + 14, top + 92, 100, 54, "Card", BRAND, () -> go(Stage.CARD)));
                out.add(new Pad(left + 120, top + 92, 100, 54, "Phone or watch", BLUE, () -> pay(true)));
                out.add(new Pad(left + 226, top + 92, 100, 54, "Cash", 0xFF8E44AD, () -> go(Stage.CASH)));
                out.add(new Pad(left + 14, top + 156, 150, 22, "Receipt: " + (receipt ? "yes" : "no"), GREY, () -> receipt = !receipt));
                out.add(new Pad(left + 14, top + H - 34, 120, 22, "Back to scanning", GREY, () -> go(Stage.SCAN)));
            }
            case CARD -> {
                out.add(new Pad(left + 40, top + 96, 120, 54, total() <= 10000 ? "Tap card" : "Tap (over £100)", BRAND, () -> {
                    if (total() > 10000) {
                        problem("Contactless limit is £100", "Please insert your card");
                    } else {
                        pay(true);
                    }
                }));
                out.add(new Pad(left + 180, top + 96, 120, 54, "Insert card (PIN)", BLUE, () -> go(Stage.PIN)));
                out.add(new Pad(left + 14, top + H - 34, 80, 22, "Back", GREY, () -> go(Stage.PAY)));
            }
            case CASH -> {
                out.add(new Pad(left + 110, top + 120, 120, 40, "Insert cash", 0xFF8E44AD, () -> pay(false)));
                out.add(new Pad(left + 14, top + H - 34, 80, 22, "Back", GREY, () -> go(Stage.PAY)));
            }
            default -> {
            }
        }
        return out;
    }

    private void keypad(int key) {
        beep();
        if (key == 9) {
            entry = "";
        } else if (key == 11) {
            if (stage == Stage.PIN) {
                if (entry.length() == 4) {
                    pay(true);
                }
            } else {
                Product found = null;
                for (Product p : Product.values()) {
                    if (p.onShelf() && String.valueOf(p.code()).equals(entry)) {
                        found = p;
                    }
                }
                if (found != null) {
                    addExtra(found);
                } else {
                    play(SoundEvents.NOTE_BLOCK_BASS.value(), 0.8f);
                    entry = "";
                    message = "Code not found";
                }
            }
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
        if (stage == Stage.SCAN && mx >= left + 8 && mx <= left + W - 126 && my >= top + 28) {
            int row = (int) ((my - top - 28) / 18);
            int first = Math.max(0, lines().size() - 8);
            selected = first + row < lines().size() ? first + row : -1;
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
        g.fill(left - 7, top - 7, left + W + 7, top + H + 7, 0xFF23272B);       // the terminal's bezel
        g.fill(left, top, left + W, top + H, PAPER);
        g.fill(left, top, left + W, top + 23, BRAND);
        g.fill(left, top + 23, left + W, top + 25, BRAND_DARK);
        g.drawString(font, shop, left + 8, top + 8, 0xFFFFFFFF, true);
        String self = "Self-checkout";
        g.drawString(font, self, left + W - 64 - font.width(self), top + 8, 0xFFFFFFFF, true);
        int cx = left + W / 2;
        switch (stage) {
            case WELCOME -> {
                big(g, "Welcome to " + shop, cx, top + 52, INK, 1.4f);
                g.drawCenteredString(font, "Touch the screen to start, then scan your items", cx, top + 80, SOFT);
                g.drawCenteredString(font, "We accept card, phone, watch and cash", cx, top + 96, SOFT);
            }
            case BAGS -> big(g, "Have you brought your own bags?", cx, top + 52, INK, 1.2f);
            case REWARDS -> {
                big(g, "Do you have a rewards card?", cx, top + 48, INK, 1.2f);
                g.drawCenteredString(font, "Scan it now to collect points on today's shop", cx, top + 70, SOFT);
            }
            case SCAN -> basket(g, mx, my);
            case LOOKUP -> {
                big(g, "Look up an item", cx, top + 32, INK, 1.2f);
                g.drawCenteredString(font, "For loose items without a barcode", cx, top + H - 52, SOFT);
            }
            case LOOKUP_ITEMS -> big(g, "Choose your item", cx, top + 32, INK, 1.2f);
            case CODE -> {
                big(g, "Enter the item's code", left + 90, top + 40, INK, 1.1f);
                codeBox(g, left + 30, top + 66);
                g.drawString(font, "Bananas 4011", left + 20, top + 104, SOFT, false);
                g.drawString(font, "Apples 4131", left + 20, top + 116, SOFT, false);
                g.drawString(font, "(codes are on the shelf labels)", left + 20, top + 128, SOFT, false);
                if (!message.isEmpty()) {
                    g.drawString(font, message, left + 20, top + 146, RED, false);
                }
            }
            case PIN -> {
                big(g, "Enter your PIN", left + 90, top + 40, INK, 1.2f);
                g.drawString(font, "Total " + CashItem.money(total()), left + 20, top + 104, INK, false);
                for (int i = 0; i < 4; i++) {
                    int x = left + 34 + i * 26;
                    g.fill(x, top + 66, x + 20, top + 88, 0xFFFFFFFF);
                    g.renderOutline(x, top + 66, 20, 22, SOFT);
                    if (i < entry.length()) {
                        g.fill(x + 7, top + 74, x + 13, top + 80, INK);
                    }
                }
            }
            case PAY -> {
                big(g, "How would you like to pay?", cx, top + 36, INK, 1.2f);
                big(g, "To pay  " + CashItem.money(total()), cx, top + 60, BRAND_DARK, 1.4f);
                if (rewards) {
                    g.drawString(font, "Rewards points today: " + total() / 100, left + W - 14 - font.width("Rewards points today: " + total() / 100),
                            top + 162, SOFT, false);
                }
            }
            case CARD -> {
                big(g, "Tap, insert or swipe your card", cx, top + 44, INK, 1.2f);
                big(g, CashItem.money(total()), cx, top + 66, BRAND_DARK, 1.4f);
            }
            case CASH -> {
                big(g, "Insert notes and coins", cx, top + 44, INK, 1.2f);
                big(g, "To pay  " + CashItem.money(total()), cx, top + 66, BRAND_DARK, 1.3f);
                var p = Minecraft.getInstance().player;
                g.drawCenteredString(font, "You have " + CashItem.money(p == null ? 0 : CashItem.carried(p)) + " in cash. Change comes out below.", cx,
                        top + 96, SOFT);
            }
            case WAIT -> big(g, (card ? "Processing your card" : "Counting your cash") + ".".repeat((int) (Util.getMillis() / 400 % 4)), cx,
                    top + 100, INK, 1.2f);
            case DONE, PROBLEM -> {
                big(g, message, cx, top + 86, stage == Stage.DONE ? BRAND_DARK : RED, 1.2f);
                g.drawCenteredString(font, message2, cx, top + 110, INK);
            }
        }
        for (Pad p : pads()) {
            boolean over = mx >= p.x() && mx <= p.x() + p.w() && my >= p.y() && my <= p.y() + p.h();
            boolean tile = p.colour() == 0xFFFFFFFF;
            g.fill(p.x(), p.y() + 2, p.x() + p.w(), p.y() + p.h() + 2, 0x33000000);           // its shadow
            g.fill(p.x(), p.y(), p.x() + p.w(), p.y() + p.h(), over ? lighter(p.colour()) : p.colour());
            if (tile) {
                g.renderOutline(p.x(), p.y(), p.w(), p.h(), LINE);
            } else {
                g.drawCenteredString(font, p.label(), p.x() + p.w() / 2, p.y() + (p.h() - 8) / 2, 0xFFFFFFFF);
            }
        }
        if (stage == Stage.LOOKUP_ITEMS) {
            int i = 0;
            for (Product p : Product.values()) {
                if (p.shelf == aisle && i < 8) {
                    int x = left + 14 + (i % 4) * 79, y = top + 50 + (i / 4) * 64;
                    g.renderItem(new ItemStack(ShopRegistry.item(p)), x + 29, y + 6);
                    g.drawCenteredString(font, font.plainSubstrByWidth(p.title, 70), x + 37, y + 28, INK);
                    g.drawCenteredString(font, CashItem.money(price(p)), x + 37, y + 42, BRAND_DARK);
                    i++;
                }
            }
        }
        if (assist != null) {
            int x0 = left + 30, y0 = top + 70;
            g.fill(x0, y0, left + W - 30, y0 + 74, 0xFFFFC94D);
            g.renderOutline(x0, y0, W - 60, 74, 0xFF7A5300);
            big(g, assist, cx, y0 + 18, INK, 1.0f);
            g.drawCenteredString(font, "Please wait" + ".".repeat((int) (Util.getMillis() / 400 % 4)), cx, y0 + 46, INK);
        }
        super.render(g, mx, my, pt);
    }

    private static int lighter(int c) {
        int r = Math.min(255, ((c >> 16) & 255) + 30), gr = Math.min(255, ((c >> 8) & 255) + 30), b = Math.min(255, (c & 255) + 30);
        return 0xFF000000 | r << 16 | gr << 8 | b;
    }

    private void big(GuiGraphics g, String s, int cx, int y, int colour, float size) {
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(size, size, 1);
        g.drawString(font, s, -font.width(s) / 2, 0, colour, false);
        g.pose().popPose();
    }

    private void codeBox(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 110, y + 26, 0xFFFFFFFF);
        g.renderOutline(x, y, 110, 26, SOFT);
        big(g, entry + ((Util.getMillis() / 500) % 2 == 0 ? "_" : " "), x + 55, y + 7, INK, 1.4f);
    }

    /** The list of what's being bought, and the total. */
    private void basket(GuiGraphics g, int mx, int my) {
        int x = left + 8, y = top + 28, w = W - 134, h = H - 62;
        g.fill(x, y, x + w, y + h, 0xFFFFFFFF);
        g.renderOutline(x, y, w, h, LINE);
        List<Object[]> lines = lines();
        if (lines.isEmpty()) {
            g.drawCenteredString(font, "Scan your first item", x + w / 2, y + h / 2 - 10, SOFT);
            g.drawCenteredString(font, "or look it up", x + w / 2, y + h / 2 + 4, SOFT);
        }
        int first = Math.max(0, lines.size() - 8);
        for (int i = first; i < lines.size(); i++) {
            Object[] l = lines.get(i);
            int ry = y + 2 + (i - first) * 18;
            if (i == selected) {
                g.fill(x + 1, ry - 1, x + w - 1, ry + 17, 0xFFDDEBF7);
            }
            g.renderItem((ItemStack) l[0], x + 3, ry);
            String name = "bags".equals(l[3]) ? "Carrier bag" : ((ItemStack) l[0]).getHoverName().getString();
            if (l[3] instanceof Product p) {
                name = p.title;
            }
            int n = (int) l[1];
            g.drawString(font, font.plainSubstrByWidth((n > 1 ? n + " x " : "") + name, w - 70), x + 22, ry + 5, INK, false);
            String price = CashItem.money((long) l[2]);
            g.drawString(font, price, x + w - 5 - font.width(price), ry + 5, INK, false);
        }
        g.fill(left, top + H - 30, left + W - 126, top + H, 0xFFE7ECEF);
        g.drawString(font, count() + (count() == 1 ? " item" : " items") + (bags > 0 ? "  (" + bags + (bags == 1 ? " bag)" : " bags)") : ""),
                left + 10, top + H - 19, SOFT, false);
        String total = "Total " + CashItem.money(total());
        big(g, total, left + W - 126 - 8 - (int) (font.width(total) * 1.3f / 2), top + H - 21, INK, 1.3f);
        if (scanned < items.size()) {
            g.drawString(font, (items.size() - scanned) + " to scan", x + 4, y + h - 12, BLUE, false);
        }
    }
}
