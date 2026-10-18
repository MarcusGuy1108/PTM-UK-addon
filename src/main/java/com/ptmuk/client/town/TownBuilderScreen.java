package com.ptmuk.client.town;

import com.ptmuk.network.TownBuilderMessage;
import com.ptmuk.town.StreetItem;
import com.ptmuk.town.TownBuilder;
import com.ptmuk.town.TreeShape;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** Choose a tree, bush, hedge or piece of street furniture, its size or spacing, and undo. */
public class TownBuilderScreen extends Screen {
    private static final String[] TABS = {"Trees", "Bushes & hedges", "Street furniture"};
    private static final String[] HELP = {
            "Click the ground to plant; click a tree you planted to grow it.",
            "Bushes as trees. Hedges: click where it starts, then where it ends.",
            "Click where the row starts, then where it ends. It faces the road."};

    private final InteractionHand hand;
    private String kind;
    private int size;
    private boolean scatter;
    private int spacing;
    private int tab;

    public TownBuilderScreen(InteractionHand hand) {
        super(Component.literal("Town Builder"));
        this.hand = hand;
        ItemStack stack = Minecraft.getInstance().player.getItemInHand(hand);
        kind = TownBuilder.kind(stack);
        size = TownBuilder.size(stack);
        scatter = stack.getOrCreateTag().getBoolean(TownBuilder.TAG_SCATTER);
        spacing = TownBuilder.spacing(stack);
        TreeShape.Species s = TownBuilder.species(kind);
        tab = s == null ? 2 : s.category == TreeShape.Category.TREE ? 0 : 1;
    }

    private record Entry(String kind, String title) {
    }

    private List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        if (tab == 2) {
            for (StreetItem i : StreetItem.values()) {
                out.add(new Entry("street:" + i.name(), i.title));
            }
        } else {
            for (TreeShape.Species s : TreeShape.Species.values()) {
                if ((s.category == TreeShape.Category.TREE) == (tab == 0)) {
                    out.add(new Entry("tree:" + s.name(), s.title));
                }
            }
        }
        return out;
    }

    @Override
    protected void init() {
        int w = 122, x0 = width / 2 - (3 * (w + 4) - 4) / 2, y = 20;
        for (int i = 0; i < TABS.length; i++) {
            int t = i;
            Button b = addRenderableWidget(Button.builder(Component.literal(TABS[i]), x -> {
                tab = t;
                rebuildWidgets();
            }).bounds(x0 + i * (w + 4), y, w, 20).build());
            b.active = tab != i;
        }
        y += 24;
        List<Entry> list = entries();
        for (int i = 0; i < list.size(); i++) {
            Entry e = list.get(i);
            Button b = addRenderableWidget(Button.builder(Component.literal(e.title()), x -> {
                StreetItem item = TownBuilder.street(e.kind());
                if (item != null && TownBuilder.street(kind) != item) {
                    spacing = item.spacing;
                }
                kind = e.kind();
                changed();
            }).bounds(x0 + i % 3 * (w + 4), y + i / 3 * 21, w, 20).build());
            b.active = !e.kind().equals(kind);
        }
        y += (list.size() + 2) / 3 * 21 + 4;
        TreeShape.Species s = TownBuilder.species(kind);
        StreetItem item = TownBuilder.street(kind);
        if (s != null && tab != 2) {
            String[] names = s.category == TreeShape.Category.HEDGE ? TreeShape.HEDGE_SIZES : TreeShape.SIZES;
            addRenderableWidget(Button.builder(Component.literal((s.category == TreeShape.Category.HEDGE ? "Height: " : "Size: ") + names[size - 1]), x -> {
                size = size % 4 + 1;
                changed();
            }).bounds(x0, y, w, 20).build());
            if (s.category != TreeShape.Category.HEDGE) {
                addRenderableWidget(Button.builder(Component.literal(scatter ? "Scatter a few" : "Plant one"), x -> {
                    scatter = !scatter;
                    changed();
                }).bounds(x0 + w + 4, y, w, 20).build());
            }
        } else if (item != null && tab == 2 && item.spacing > 1) {
            addRenderableWidget(Button.builder(Component.literal("-"), x -> {
                spacing = Math.max(1, spacing - (spacing > 10 ? 2 : 1));
                changed();
            }).bounds(x0, y, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal("+"), x -> {
                spacing = Math.min(64, spacing + (spacing >= 10 ? 2 : 1));
                changed();
            }).bounds(x0 + 102, y, 20, 20).build());
        }
        int by = Math.max(y + 38, height - 26);
        addRenderableWidget(Button.builder(Component.literal("Undo last"), x -> TownBuilderClient.send(TownBuilderMessage.Action.UNDO, kind, size, scatter,
                spacing, hand)).bounds(width / 2 - 124, by, 120, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), x -> onClose()).bounds(width / 2 + 4, by, 120, 20).build());
    }

    private void changed() {
        TownBuilderClient.send(TownBuilderMessage.Action.CHOOSE, kind, size, scatter, spacing, hand);
        rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
        int w = 122, x0 = width / 2 - (3 * (w + 4) - 4) / 2;
        int y = 44 + (entries().size() + 2) / 3 * 21 + 4;
        if (tab == 2 && TownBuilder.street(kind) != null && TownBuilder.street(kind).spacing > 1) {
            g.drawCenteredString(font, "Every " + spacing + " blocks", x0 + 61, y + 6, 0xFFFFFF);
        }
        g.drawCenteredString(font, HELP[tab], width / 2, y + 25, 0xA0A0A0);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
