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

/** The Prefab Tool's catalogue: categories, a list of buildings and a picture of the chosen one,
 * and a Custom tab listing schematic files to import. */
public class PrefabScreen extends Screen {
    private static final int ROW = 14;
    public static final String CUSTOM = "Custom";
    private static final List<String> TABS = new ArrayList<>(PrefabCatalog.CATEGORIES);

    static {
        TABS.add(CUSTOM);
    }

    private static String lastCategory = PrefabCatalog.CATEGORIES.get(0);

    private final InteractionHand hand;
    private String category;
    private String chosen;
    private int scroll;
    private final List<Button> tabs = new ArrayList<>();
    private int tabRows = 1;
    private Button mirrorButton;
    private Button groundButton, folderButton, refreshButton;
    private List<SchematicFiles.FileEntry> files = List.of();
    private SchematicFiles.FileEntry chosenFile;
    /** The chosen file is still to be sent to the server once it has been read. */
    private boolean sendWhenRead;

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
        this.files = SchematicFiles.list();
        if (e != null && CUSTOM.equals(e.category())) {
            String file = stack.getTag().getCompound(PrefabPlacement.TAG_CUSTOM).getString("file");
            chosenFile = files.stream().filter(f -> f.label().equals(file)).findFirst().orElse(null);
            if (chosenFile != null && !SchematicFiles.sent(chosenFile)) {
                SchematicFiles.get(chosenFile);           // the server forgets them when you leave: send it again
                sendWhenRead = true;
            }
        }
    }

    private boolean custom() {
        return CUSTOM.equals(category);
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
        // one row of tabs if they fit, otherwise two
        int gap = 3, room = Math.min(width - 16, 420);
        int[] w = new int[TABS.size()];
        int total = gap * (TABS.size() - 1);
        for (int i = 0; i < w.length; i++) {
            w[i] = font.width(TABS.get(i)) + 12;
            total += w[i];
        }
        tabRows = total <= room ? 1 : 2;
        int perRow = (TABS.size() + tabRows - 1) / tabRows;
        for (int row = 0; row < tabRows; row++) {
            int from = row * perRow, to = Math.min(TABS.size(), from + perRow);
            int rowW = gap * (to - from - 1);
            for (int i = from; i < to; i++) {
                rowW += w[i];
            }
            double squeeze = rowW > room ? (room - gap * (to - from - 1)) / (double) (rowW - gap * (to - from - 1)) : 1;
            int x = width / 2 - (int) Math.min(room, rowW) / 2;
            for (int i = from; i < to; i++) {
                String c = TABS.get(i);
                int tw = (int) (w[i] * squeeze);
                Button b = addRenderableWidget(Button.builder(Component.literal(c), btn -> {
                    category = c;
                    lastCategory = c;
                    scroll = 0;
                    if (CUSTOM.equals(c)) {
                        files = SchematicFiles.list();
                    }
                }).bounds(x, 20 + row * 19, tw, 18).build());
                tabs.add(b);
                x += tw + gap;
            }
        }
        int bx = left() + 210, by = height - 52;
        addRenderableWidget(Button.builder(Component.literal("Turn 90°"), b -> PrefabToolClient.send(PrefabToolMessage.Action.ROTATE, "", hand))
                .bounds(bx, by, 92, 20).build());
        mirrorButton = addRenderableWidget(Button.builder(Component.empty(), b -> PrefabToolClient.send(PrefabToolMessage.Action.MIRROR, "", hand))
                .bounds(bx + 98, by, 92, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Undo last placed"), b -> PrefabToolClient.send(PrefabToolMessage.Action.UNDO, "", hand))
                .bounds(bx, by + 24, 92, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(bx + 98, by + 24, 92, 20).build());
        groundButton = addRenderableWidget(Button.builder(Component.empty(), b -> PrefabToolClient.send(PrefabToolMessage.Action.ON_GROUND, "", hand))
                .bounds(bx, by - 24, 190, 20).build());
        folderButton = addRenderableWidget(Button.builder(Component.literal("Open folder"), b -> {
            try {
                java.nio.file.Files.createDirectories(SchematicFiles.mainFolder());
            } catch (java.io.IOException ignored) {
                // opening it will say so
            }
            net.minecraft.Util.getPlatform().openFile(SchematicFiles.mainFolder().toFile());
        }).bounds(left(), height - 32, 98, 20).build());
        refreshButton = addRenderableWidget(Button.builder(Component.literal("Refresh"), b -> files = SchematicFiles.list())
                .bounds(left() + 102, height - 32, 98, 20).build());
    }

    @Override
    public void tick() {
        ItemStack stack = stack();
        boolean m = stack != null && PrefabPlacement.mirrored(stack);
        mirrorButton.setMessage(Component.literal(m ? "Mirrored: Yes" : "Mirrored: No"));
        boolean ground = stack != null && PrefabPlacement.onGround(stack);
        groundButton.setMessage(Component.literal(ground ? "Bottom layer: sits on the ground" : "Bottom layer: replaces the ground"));
        groundButton.visible = folderButton.visible = refreshButton.visible = custom();
        for (int i = 0; i < tabs.size(); i++) {
            tabs.get(i).active = !TABS.get(i).equals(category);
        }
        if (sendWhenRead && chosenFile != null) {
            SchematicFiles.Result r = SchematicFiles.get(chosenFile);
            if (r.done()) {
                sendWhenRead = false;
                if (r.encoded != null) {
                    SchematicFiles.upload(chosenFile, r, hand);
                    chosen = com.ptmuk.building.schematic.CustomSchematics.ID_PREFIX + r.schematic.name;
                }
            }
        }
    }

    private int listTop() {
        return 46 + (tabRows - 1) * 19;
    }

    private int listRows() {
        return Math.max(1, (height - listTop() - (custom() ? 40 : 12)) / ROW);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x0 = left(), y0 = listTop();
        if (custom() && mx >= x0 && mx < x0 + 200 && my >= y0 && (my - y0) / ROW < listRows()) {
            int i = (int) ((my - y0) / ROW) + scroll;
            if (i >= 0 && i < files.size()) {
                chosenFile = files.get(i);
                SchematicFiles.get(chosenFile);
                sendWhenRead = true;
                return true;
            }
        }
        if (!custom() && mx >= x0 && mx < x0 + 200 && my >= y0) {
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
        int max = Math.max(0, (custom() ? files.size() : shown().size()) - listRows());
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 7, 0xFFFFFF);
        int x0 = left(), y0 = listTop();
        g.fill(x0 - 2, y0 - 2, x0 + 202, height - (custom() ? 38 : 10), 0x90000000);
        if (custom()) {
            renderCustom(g, mouseX, mouseY, x0, y0);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }
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
            String size = e.width() + "x" + e.depth();
            int room = 192 - font.width(size) - 6;
            String label = font.width(e.name()) <= room ? e.name() : font.plainSubstrByWidth(e.name(), room - font.width("...")) + "...";
            g.drawString(font, label, x0 + 4, y + 3, 0xFFFFFF);
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

    private void renderCustom(GuiGraphics g, int mouseX, int mouseY, int x0, int y0) {
        for (int r = 0; r < listRows() && r + scroll < files.size(); r++) {
            SchematicFiles.FileEntry f = files.get(r + scroll);
            int y = y0 + r * ROW;
            boolean hover = mouseX >= x0 && mouseX < x0 + 200 && mouseY >= y && mouseY < y + ROW;
            if (f.equals(chosenFile)) {
                g.fill(x0, y, x0 + 200, y + ROW, 0xA0405A80);
            } else if (hover) {
                g.fill(x0, y, x0 + 200, y + ROW, 0x50FFFFFF);
            }
            String size = f.bytes() < 1024 * 1024 ? (f.bytes() + 1023) / 1024 + " KB" : String.format("%.1f MB", f.bytes() / 1048576.0);
            int room = 192 - font.width(size) - 6;
            String label = font.width(f.label()) <= room ? f.label() : font.plainSubstrByWidth(f.label(), room - font.width("...")) + "...";
            g.drawString(font, label, x0 + 4, y + 3, 0xFFFFFF);
            g.drawString(font, size, x0 + 196 - font.width(size), y + 3, 0xA0A0A0);
        }
        int px = x0 + 210;
        if (files.isEmpty()) {
            String[] help = {"No schematics found.", "", "Put .schem, .schematic, .litematic or", ".nbt files in the schematics folder",
                    "(Open folder), or WorldEdit's", "config/worldedit/schematics, then", "press Refresh."};
            for (int i = 0; i < help.length; i++) {
                g.drawString(font, help[i], i == 0 ? x0 + 4 : px, i == 0 ? y0 + 3 : y0 + 4 + (i - 1) * 12, 0xA0A0A0);
            }
            return;
        }
        if (chosenFile == null) {
            g.drawString(font, "Pick a schematic from the list.", px, y0 + 4, 0xA0A0A0);
            g.drawString(font, "It's read, then sent to the server,", px, y0 + 18, 0xA0A0A0);
            g.drawString(font, "and the tool places it like a prefab.", px, y0 + 30, 0xA0A0A0);
            g.drawString(font, "Its south side is treated as the front", px, y0 + 48, 0xA0A0A0);
            g.drawString(font, "(the bottom of the map): R turns it.", px, y0 + 60, 0xA0A0A0);
            return;
        }
        SchematicFiles.Result r = SchematicFiles.get(chosenFile);
        int pic = Math.max(40, Math.min(150, height - y0 - 136));
        g.fill(px, y0 - 2, px + 190, y0 + pic + 2, 0x60B8CCE0);
        if (r.error != null) {
            int ly = y0 + 4;
            for (var line : font.split(Component.literal("Couldn't read it: " + r.error), 180)) {
                g.drawString(font, line, px + 4, ly, 0xFF8080);
                ly += 10;
            }
            return;
        }
        if (r.schematic == null || r.texture() == null) {
            g.drawCenteredString(font, "Reading...", px + 95, y0 + pic / 2 - 4, 0xFFFFFF);
            return;
        }
        g.blit(r.texture(), px + (190 - pic) / 2, y0, pic, pic, 0, 0, SchematicFiles.THUMB, SchematicFiles.THUMB,
                SchematicFiles.THUMB, SchematicFiles.THUMB);
        var s = r.schematic;
        int ty = y0 + pic + 8;
        g.drawString(font, s.name, px, ty, 0xFFE08A);
        g.drawString(font, s.width + " wide, " + s.depth + " deep, " + s.height + " high", px, ty + 12, 0xC0C0C0);
        g.drawString(font, s.format + ", " + r.solid + " blocks", px, ty + 24, 0xA0A0A0);
        boolean ready = chosen != null && chosen.equals(com.ptmuk.building.schematic.CustomSchematics.ID_PREFIX + s.name)
                && !sendWhenRead;
        g.drawString(font, ready ? "Ready: right-click to place it." : "Sending to the server...", px, ty + 36, ready ? 0x80FF80 : 0xC0C0C0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
