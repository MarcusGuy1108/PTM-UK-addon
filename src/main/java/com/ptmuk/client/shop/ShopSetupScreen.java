package com.ptmuk.client.shop;

import com.ptmuk.network.PtmUkNetwork;
import com.ptmuk.network.ShopSetupMessage;
import com.ptmuk.shop.ShopBlockEntity;
import com.ptmuk.shop.ShopKind;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Setting up a shop of your own at its till: what kind of shop it is (or Work it out, from its
 * fittings and sign), its name, and a new shopkeeper if you'd like someone else.
 */
public class ShopSetupScreen extends Screen {
    private final ShopBlockEntity shop;
    private int kind;                                       // 0: work it out, else ShopKind ordinal + 1
    private boolean newPerson;
    private EditBox name;

    public ShopSetupScreen(ShopBlockEntity shop) {
        super(Component.literal("Set up this shop"));
        this.shop = shop;
        this.kind = shop.chosen() ? shop.kind().ordinal() + 1 : 0;
    }

    private String kindLabel() {
        return "Kind: " + (kind == 0 ? "Work it out (now " + shop.kind().title + ")" : ShopKind.values()[kind - 1].title);
    }

    @Override
    protected void init() {
        int x = width / 2 - 110, y = height / 2 - 50;
        addRenderableWidget(Button.builder(Component.literal(kindLabel()), b -> {
            kind = (kind + 1) % (ShopKind.values().length + 1);
            b.setMessage(Component.literal(kindLabel()));
        }).bounds(x, y, 220, 20).build());
        name = new EditBox(font, x, y + 36, 220, 20, Component.literal("Shop name"));
        name.setMaxLength(40);
        name.setValue(shop.shopName());
        addRenderableWidget(name);
        addRenderableWidget(Button.builder(Component.literal("New shopkeeper: no"), b -> {
            newPerson = !newPerson;
            b.setMessage(Component.literal("New shopkeeper: " + (newPerson ? "yes" : "no")));
        }).bounds(x, y + 62, 220, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> {
            PtmUkNetwork.CHANNEL.sendToServer(new ShopSetupMessage(shop.getBlockPos(), kind == 0 ? "" : ShopKind.values()[kind - 1].name(),
                    name.getValue(), newPerson));
            onClose();
        }).bounds(x, y + 92, 106, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(x + 114, y + 92, 106, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, "Set up this shop", width / 2, height / 2 - 72, 0xFFFFFF);
        g.drawString(font, "Name (blank: from its sign)", width / 2 - 110, height / 2 - 24, 0xA0A0A0, false);
        g.drawCenteredString(font, "Shopkeeper: " + shop.keeperName(), width / 2, height / 2 + 68, 0xA0A0A0);
        super.render(g, mx, my, pt);
    }
}
