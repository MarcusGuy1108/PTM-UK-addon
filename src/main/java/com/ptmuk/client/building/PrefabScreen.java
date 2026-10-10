package com.ptmuk.client.building;

import com.ptmuk.PtmUk;
import com.ptmuk.building.PrefabCatalog;
import com.ptmuk.building.PrefabPlacement;
import com.ptmuk.network.PrefabToolMessage;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** The Prefab Tool's catalogue: categories, a list of buildings and a picture of the chosen one. */
public class PrefabScreen extends Screen {
    private static final int ROW = 14;
    private static String lastCategory = PrefabCatalog.CATEGORIES.get(0);

    private final InteractionHand hand;
    private String category;
    private String chosen;
    private int scroll;
    private final List<Button> tabs = new ArrayList<>();
    private Button mirrorButton;

    public PrefabScreen(InteractionHand hand) {
        super(Component.literal("Prefab Buildings"));
        this.hand = hand;
        this.category = lastCategory;
        ItemStack stack = stack();
        PrefabCatalog.Entry e = stack == null ? null : PrefabPlacement.selected(stack);
        if (e != null) {
            this.chosen = e.id();
            this.category = e.category();
        }
    }

    private ItemStack stack() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : mc.player.getItemInHand(hand);
    }

    private List<PrefabCatalog.Entry> shown() {
        return PrefabCatalog.ENTRIES.stream().filter(e -> e.category().equals(category)).toList();
    }

    private int left() {
        return width / 2 - 200;
    }

    @Override
    protected void init() {
        tabs.clear();
        int x = left();
        for (String c : PrefabCatalog.CATEGORIES) {
            Button b = addRenderableWidget(Button.builder(Component.literal(c), btn -> {
                category = c;
                lastCategory = c;
                scroll = 0;
            }).bounds(x, 22, 76, 18).build());
            tabs.add(b);
            x += 80;
        }
        int bx = left() + 210, by = height - 52;
        addRenderableWidget(Button.builder(Component.literal("Turn 90°"), b -> PrefabToolClient.send(PrefabToolMessage.Action.ROTATE, "", hand))
                .bounds(bx, by, 92, 20).build());
        mirrorButton = addRenderableWidget(Button.builder(Component.empty(), b -> PrefabToolClient.send(PrefabToolMessage.Action.MIRROR, "", hand))
                .bounds(bx + 98, by, 92, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Undo last placed"), b -> PrefabToolClient.send(PrefabToolMessage.Action.UNDO, "", hand))
                .bounds(bx, by + 24, 92, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(bx + 98, by + 24, 92, 20).build());
    }

    @Override
    public void tick() {
        ItemStack stack = stack();
        boolean m = stack != null && PrefabPlacement.mirrored(stack);
        mirrorButton.setMessage(Component.literal(m ? "Mirrored: Yes" : "Mirrored: No"));
        for (int i = 0; i < tabs.size(); i++) {
            tabs.get(i).active = !PrefabCatalog.CATEGORIES.get(i).equals(category);
        }
    }

    private int listTop() {
        return 48;
    }

    private int listRows() {
        return Math.max(1, (height - listTop() - 12) / ROW);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x0 = left(), y0 = listTop();
        if (mx >= x0 && mx < x0 + 200 && my >= y0) {
            int i = (int) ((my - y0) / ROW) + scroll;
            List<PrefabCatalog.Entry> list = shown();
            if (i >= 0 && i < list.size() && (my - y0) / ROW < listRows()) {
                chosen = list.get(i).id();
                PrefabToolClient.send(PrefabToolMessage.Action.SELECT, chosen, hand);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int max = Math.max(0, shown().size() - listRows());
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
        int x0 = left(), y0 = listTop();
        g.fill(x0 - 2, y0 - 2, x0 + 202, height - 10, 0x90000000);
        List<PrefabCatalog.Entry> list = shown();
        for (int r = 0; r < listRows() && r + scroll < list.size(); r++) {
            PrefabCatalog.Entry e = list.get(r + scroll);
            int y = y0 + r * ROW;
            boolean hover = mouseX >= x0 && mouseX < x0 + 200 && mouseY >= y && mouseY < y + ROW;
            if (e.id().equals(chosen)) {
                g.fill(x0, y, x0 + 200, y + ROW, 0xA0405A80);
            } else if (hover) {
                g.fill(x0, y, x0 + 200, y + ROW, 0x50FFFFFF);
            }
            g.drawString(font, font.plainSubstrByWidth(e.name(), 150), x0 + 4, y + 3, 0xFFFFFF);
            String size = e.width() + "x" + e.depth();
            g.drawString(font, size, x0 + 196 - font.width(size), y + 3, 0xA0A0A0);
        }
        if (list.isEmpty()) {
            g.drawString(font, "Nothing here yet", x0 + 4, y0 + 3, 0xA0A0A0);
        }
        // the chosen building
        PrefabCatalog.Entry e = chosen == null ? null : PrefabCatalog.get(chosen);
        int px = x0 + 210;
        if (e != null) {
            int pic = Math.max(60, Math.min(190, height - y0 - 120));
            g.fill(px, y0 - 2, px + 190, y0 + pic + 2, 0x60B8CCE0);
            g.blit(new ResourceLocation(PtmUk.MOD_ID, "textures/gui/prefab/" + e.id() + ".png"), px + (190 - pic) / 2, y0, pic, pic,
                    0, 0, 256, 256, 256, 256);
            int ty = y0 + pic + 8;
            g.drawString(font, e.name(), px, ty, 0xFFE08A);
            g.drawString(font, e.width() + " wide, " + e.depth() + " deep, " + e.height() + " high", px, ty + 12, 0xC0C0C0);
            int ly = ty + 26;
            for (var line : font.split(Component.literal(e.description()), 190)) {
                if (ly > height - 62) {
                    break;
                }
                g.drawString(font, line, px, ly, 0xA0A0A0);
                ly += 10;
            }
        } else {
            g.drawString(font, "Pick a building from the list.", px, y0 + 4, 0xA0A0A0);
            g.drawString(font, "Then right-click where it should go:", px, y0 + 18, 0xA0A0A0);
            g.drawString(font, "the front faces you, its middle on the", px, y0 + 30, 0xA0A0A0);
            g.drawString(font, "block you're looking at.", px, y0 + 42, 0xA0A0A0);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
