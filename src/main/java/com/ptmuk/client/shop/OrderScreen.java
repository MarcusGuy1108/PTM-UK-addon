package com.ptmuk.client.shop;

import com.ptmuk.money.CashItem;
import com.ptmuk.network.PtmUkNetwork;
import com.ptmuk.network.ShopMenuMessage;
import com.ptmuk.network.ShopOrderMessage;
import com.ptmuk.shop.ShopBlockEntity;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Ordering at a counter (a chippy, a café, a pub, a salon, the rails of a charity shop): the menu
 * board, how many of each, the total, then Order. You pay the shop's person after (hold your bank
 * card or cash and click them).
 */
public class OrderScreen extends Screen {
    private static final int W = 260, ROW = 22;
    private final ShopMenuMessage menu;
    private final int[] qty;
    private int left, top, h;

    private OrderScreen(ShopMenuMessage menu) {
        super(Component.literal(menu.shop()));
        this.menu = menu;
        this.qty = new int[menu.offers().size()];
    }

    public static void open(ShopMenuMessage msg) {
        Minecraft.getInstance().setScreen(new OrderScreen(msg));
    }

    @Override
    protected void init() {
        List<ShopBlockEntity.Line> offers = menu.offers();
        h = 52 + offers.size() * ROW + 44;
        left = (width - W) / 2;
        top = Math.max(4, (height - h) / 2);
        for (int i = 0; i < offers.size(); i++) {
            int k = i, y = top + 40 + i * ROW;
            addRenderableWidget(Button.builder(Component.literal("-"), b -> qty[k] = Math.max(0, qty[k] - 1)).bounds(left + W - 64, y, 16, 16).build());
            addRenderableWidget(Button.builder(Component.literal("+"), b -> qty[k] = Math.min(10, qty[k] + 1)).bounds(left + W - 24, y, 16, 16).build());
        }
        int by = top + h - 26;
        addRenderableWidget(Button.builder(Component.literal("Order"), b -> {
            if (total() > 0) {
                PtmUkNetwork.CHANNEL.sendToServer(new ShopOrderMessage(menu.pos(), qty.clone()));
                onClose();
            }
        }).bounds(left + 16, by, 110, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Never mind"), b -> onClose()).bounds(left + W - 126, by, 110, 20).build());
    }

    private long total() {
        long t = 0;
        for (int i = 0; i < qty.length; i++) {
            t += (long) qty[i] * menu.offers().get(i).pence();
        }
        return t;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        // a chalk menu board in a wooden frame
        g.fill(left - 4, top - 4, left + W + 4, top + h + 4, 0xFF6B4A2B);
        g.fill(left, top, left + W, top + h, 0xFF1F2B24);
        g.drawCenteredString(font, menu.shop(), left + W / 2, top + 8, 0xFFFFD84A);
        g.drawCenteredString(font, "Served by " + menu.keeper(), left + W / 2, top + 22, 0xFFB8C8BC);
        List<ShopBlockEntity.Line> offers = menu.offers();
        for (int i = 0; i < offers.size(); i++) {
            ShopBlockEntity.Line l = offers.get(i);
            int y = top + 40 + i * ROW;
            ItemStack icon = l.give().isEmpty() ? new ItemStack(Items.SHEARS) : l.give();
            g.renderItem(icon, left + 8, y);
            String title = font.plainSubstrByWidth(l.title(), W - 160);
            g.drawString(font, title, left + 30, y + 4, 0xFFF4F1E8, false);
            String price = CashItem.money(l.pence());
            g.drawString(font, price, left + W - 72 - font.width(price), y + 4, 0xFFFFD84A, false);
            g.drawCenteredString(font, String.valueOf(qty[i]), left + W - 36, y + 4, 0xFFFFFFFF);
        }
        g.fill(left + 12, top + h - 36, left + W - 12, top + h - 35, 0xFF8FA396);
        g.drawString(font, "Total", left + 16, top + h - 33 + 0, 0xFFF4F1E8, false);
        String t = CashItem.money(total());
        g.drawString(font, t, left + W - 16 - font.width(t), top + h - 33, 0xFFFFD84A, false);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
