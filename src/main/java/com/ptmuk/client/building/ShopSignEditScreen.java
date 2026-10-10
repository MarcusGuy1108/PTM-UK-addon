package com.ptmuk.client.building;

import com.ptmuk.building.ShopSignBlockEntity;
import com.ptmuk.network.EditBlockDataMessage;
import com.ptmuk.network.PtmUkNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/** Editor for a shop fascia: name, second line, board and lettering colours, lighting. */
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

    public ShopSignEditScreen(ShopSignBlockEntity sign) {
        super(Component.literal("Edit Shop Sign"));
        this.sign = sign;
        this.board = indexOf(BOARDS, sign.board);
        this.ink = indexOf(INKS, sign.ink);
        this.lit = sign.lit;
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
        int x0 = width / 2 - 130, y = 92;
        text = box(x0, y, 260, sign.text);
        y += 34;
        sub = box(x0, y, 260, sign.sub);
        y += 28;
        boardButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            board = (board + (hasShiftDown() ? BOARDS.length - 1 : 1)) % BOARDS.length;
            refresh();
        }).bounds(x0, y, 126, 20).build());
        inkButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            ink = (ink + (hasShiftDown() ? INKS.length - 1 : 1)) % INKS.length;
            refresh();
        }).bounds(x0 + 134, y, 126, 20).build());
        y += 24;
        litButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            lit = !lit;
            refresh();
        }).bounds(x0, y, 260, 20).build());
        y += 30;
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
        boardButton.setMessage(Component.literal("Board: " + BOARD_NAMES[board]));
        inkButton.setMessage(Component.literal("Letters: " + INK_NAMES[ink]));
        litButton.setMessage(Component.literal(lit ? "Lit at night: Yes" : "Lit at night: No"));
    }

    private void save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("text", text.getValue());
        tag.putString("sub", sub.getValue());
        tag.putInt("board", BOARDS[board]);
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
        g.fill(px0, py0, px1, py1, BOARDS[board]);
        int edge = (BOARDS[board] & 0xFEFEFE) >> 1 | 0xFF000000;
        g.fill(px0, py0, px1, py0 + 2, edge);
        g.fill(px0, py1 - 2, px1, py1, edge);
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
        g.drawString(font, "Shop name", x0, 81, 0xA0A0A0);
        g.drawString(font, "Second line (optional, e.g. \"Newsagent & Off Licence\")", x0, 115, 0xA0A0A0);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
