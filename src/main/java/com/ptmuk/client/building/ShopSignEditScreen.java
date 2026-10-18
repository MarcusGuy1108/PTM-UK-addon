package com.ptmuk.client.building;

import com.ptmuk.building.ShopSignBlock;
import com.ptmuk.building.ShopSignBlockEntity;
import com.ptmuk.network.EditBlockDataMessage;
import com.ptmuk.network.PtmUkNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/** Editor for a shop fascia: name, second line, a picture (your own logo), board and lettering colours, lighting. */
public class ShopSignEditScreen extends Screen {
    /** Board colours often seen on British high streets. */
    private static final int[] BOARDS = {0xFF1C2E4A, 0xFF121214, 0xFF1E4D2B, 0xFF6B1F2A, 0xFFB3202A, 0xFFF2F0EA,
            0xFFF2C21B, 0xFF1F4FA3, 0xFFE0701E, 0xFF5B2C83, 0xFF6E7378, 0xFF1F7A78, 0xFF7A4A2A, 0xFFD8457A};
    private static final String[] BOARD_NAMES = {"Navy", "Black", "Racing green", "Burgundy", "Pillar-box red", "White",
            "Yellow", "Royal blue", "Orange", "Purple", "Grey", "Teal", "Brown", "Pink"};
    private static final int[] INKS = {0xFFFFFFFF, 0xFF121214, 0xFFD9B44A, 0xFFF2C21B, 0xFFF0E6C8, 0xFFB3202A, 0xFF1C2E4A};
    private static final String[] INK_NAMES = {"White", "Black", "Gold", "Yellow", "Cream", "Red", "Navy"};

    private final ShopSignBlockEntity sign;
    private EditBox text;
    private EditBox sub;
    private int board;
    private int ink;
    private boolean lit;
    private Button boardButton;
    private Button inkButton;
    private Button litButton;
    private Button logoButton;
    private final java.util.List<String> logos = new java.util.ArrayList<>();
    private int logo;

    public ShopSignEditScreen(ShopSignBlockEntity sign) {
        super(Component.literal("Edit Shop Sign"));
        this.sign = sign;
        this.board = sign.getBlockState().getValue(ShopSignBlock.BOARD) ? indexOf(BOARDS, sign.board) : BOARDS.length;
        listLogos();
        this.ink = indexOf(INKS, sign.ink);
        this.lit = sign.lit;
    }

    /** No picture, the built-in made-up logos, then the PNGs in the images folder. */
    private void listLogos() {
        String current = logos.isEmpty() ? sign.logo : logos.get(logo);
        logos.clear();
        logos.add("");
        CustomImages.files().forEach(n -> logos.add("file:" + n));
        CustomImages.BUILT_IN.forEach(n -> logos.add("builtin:" + n));
        if (!logos.contains(current)) {
            logos.add(current);
        }
        logo = logos.indexOf(current);
    }

    private static int indexOf(int[] list, int value) {
        for (int i = 0; i < list.length; i++) {
            if (list[i] == value) {
                return i;
            }
        }
        return 0;
    }

    @Override
    protected void init() {
        int x0 = width / 2 - 130, y = 84;
        text = box(x0, y, 260, sign.text);
        y += 30;
        sub = box(x0, y, 260, sign.sub);
        y += 24;
        boardButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            board = (board + (hasShiftDown() ? BOARDS.length : 1)) % (BOARDS.length + 1);      // the last is no board
            refresh();
        }).bounds(x0, y, 126, 20).build());
        inkButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            ink = (ink + (hasShiftDown() ? INKS.length - 1 : 1)) % INKS.length;
            refresh();
        }).bounds(x0 + 134, y, 126, 20).build());
        y += 22;
        litButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            lit = !lit;
            refresh();
        }).bounds(x0, y, 260, 20).build());
        y += 22;
        logoButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            logo = (logo + (hasShiftDown() ? logos.size() - 1 : 1)) % logos.size();
            refresh();
        }).bounds(x0, y, 186, 20).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                "Your own logos: put PNG files in the images folder (Open folder), then click through to them here. "
                        + "With no name the picture fills the sign; with a name it sits at the left."))).build());
        addRenderableWidget(Button.builder(Component.literal("Open folder"), b -> {
            CustomImages.files();                                   // makes the folder if it isn't there
            net.minecraft.Util.getPlatform().openFile(CustomImages.folder().toFile());
            listLogos();
            refresh();
        }).bounds(x0 + 190, y, 70, 20).build());
        y += 26;
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> save()).bounds(width / 2 - 100, y, 96, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(width / 2 + 4, y, 96, 20).build());
        refresh();
    }

    private EditBox box(int x, int y, int w, String value) {
        EditBox b = addRenderableWidget(new EditBox(font, x, y, w, 18, Component.empty()));
        b.setMaxLength(40);
        b.setValue(value);
        return b;
    }

    private void refresh() {
        boardButton.setMessage(Component.literal("Board: " + (board == BOARDS.length ? "None (on the wall)" : BOARD_NAMES[board])));
        logoButton.setMessage(Component.literal("Picture: " + CustomImages.title(logos.get(logo))));
        inkButton.setMessage(Component.literal("Letters: " + INK_NAMES[ink]));
        litButton.setMessage(Component.literal(lit ? "Lit at night: Yes" : "Lit at night: No"));
    }

    private void save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("text", text.getValue());
        tag.putString("sub", sub.getValue());
        tag.putInt("board", board == BOARDS.length ? sign.board : BOARDS[board]);
        tag.putBoolean("noBoard", board == BOARDS.length);
        tag.putString("logo", logos.get(logo));
        tag.putInt("ink", INKS[ink]);
        tag.putBoolean("lit", lit);
        sign.read(tag);
        PtmUkNetwork.CHANNEL.sendToServer(new EditBlockDataMessage(sign.getBlockPos(), tag));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int x0 = width / 2 - 130;
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
        // preview of the fascia
        int px0 = width / 2 - 130, py0 = 26, px1 = width / 2 + 130, py1 = 62;
        if (board < BOARDS.length) {
            g.fill(px0, py0, px1, py1, BOARDS[board]);
            int edge = (BOARDS[board] & 0xFEFEFE) >> 1 | 0xFF000000;
            g.fill(px0, py0, px1, py0 + 2, edge);
            g.fill(px0, py1 - 2, px1, py1, edge);
        } else {
            g.fill(px0, py0, px1, py1, 0xFF8A4B3A);                 // a brick wall behind it
        }
        CustomImages.Image img = logos.isEmpty() ? null : CustomImages.get(logos.get(logo));
        if (img != null) {
            int h = py1 - py0 - 6, w = Math.min(px1 - px0 - 6, h * img.width() / Math.max(1, img.height()));
            g.blit(img.texture(), px0 + 3, py0 + 3, w, h, 0, 0, img.width(), img.height(), img.width(), img.height());
        }
        String t = text == null ? "" : text.getValue();
        String s = sub == null ? "" : sub.getValue();
        if (!t.isBlank()) {
            g.pose().pushPose();
            float scale = Math.min(2.0f, 240f / Math.max(1, font.width(t)));
            g.pose().translate(width / 2f, s.isBlank() ? 38 : 31, 0);
            g.pose().scale(scale, scale, 1);
            g.drawString(font, t, -font.width(t) / 2, 0, INKS[ink], false);
            g.pose().popPose();
        }
        if (!s.isBlank()) {
            g.drawString(font, s, width / 2 - font.width(s) / 2, 50, INKS[ink], false);
        }
        g.drawString(font, "Shop name", x0, 74, 0xA0A0A0);
        g.drawString(font, "Second line (optional, e.g. \"Newsagent & Off Licence\")", x0, 104, 0xA0A0A0);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
