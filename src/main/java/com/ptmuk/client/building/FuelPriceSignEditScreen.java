package com.ptmuk.client.building;

import com.ptmuk.building.FuelPriceSignBlockEntity;
import com.ptmuk.network.EditBlockDataMessage;
import com.ptmuk.network.PtmUkNetwork;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * Editor for a fuel price sign. The logo can be one of the made-up built-in designs or any PNG
 * in .minecraft/ptmuk/images (the "Images folder" button opens it).
 */
public class FuelPriceSignEditScreen extends Screen {
    private static final int[] PANELS = {0xFFF2F0EA, 0xFFB3202A, 0xFF1E7A3C, 0xFF1F4FA3, 0xFF1C2E4A, 0xFFF2C21B, 0xFF121214,
            0xFF6E7378, 0xFFE0701E, 0xFF5B2C83};
    private static final String[] PANEL_NAMES = {"White", "Red", "Green", "Blue", "Navy", "Yellow", "Black", "Grey", "Orange",
            "Purple"};
    private static final int[] INKS = {0xFFB3202A, 0xFFFFFFFF, 0xFF121214, 0xFF1C2E4A, 0xFF1E7A3C, 0xFF1F4FA3, 0xFFD9B44A,
            0xFFF2C21B};
    private static final String[] INK_NAMES = {"Red", "White", "Black", "Navy", "Green", "Blue", "Gold", "Yellow"};
    private static final int[] DIGITS = {0xFFFFB21E, 0xFFFF3A2E, 0xFF42F26A, 0xFFFFFFFF, 0xFF4FB4FF};
    private static final String[] DIGIT_NAMES = {"Amber", "Red", "Green", "White", "Blue"};
    private static final int[] PLATES = {0xFF1E7A3C, 0xFF121214, 0xFFB3202A, 0xFF4A4F57, 0xFF1F4FA3, 0xFF5B2C83, 0xFFF2C21B,
            0xFFE0701E, 0xFFF2F0EA};
    private static final String[] PLATE_NAMES = {"Green", "Black", "Red", "Grey", "Blue", "Purple", "Yellow", "Orange", "White"};

    private final FuelPriceSignBlockEntity sign;
    private final List<String> logos = new ArrayList<>();
    private int logo;
    private int panel;
    private int ink;
    private int digits;
    private boolean lit;
    private final int[] plates = new int[FuelPriceSignBlockEntity.ROWS];
    private EditBox brand;
    private EditBox info;
    private final EditBox[] labels = new EditBox[FuelPriceSignBlockEntity.ROWS];
    private final EditBox[] prices = new EditBox[FuelPriceSignBlockEntity.ROWS];
    private Button logoButton;
    private Button panelButton;
    private Button inkButton;
    private Button digitButton;
    private Button litButton;
    private final Button[] plateButtons = new Button[FuelPriceSignBlockEntity.ROWS];

    public FuelPriceSignEditScreen(FuelPriceSignBlockEntity sign) {
        super(Component.literal("Edit Fuel Price Sign"));
        this.sign = sign;
        logos.add("");
        CustomImages.BUILT_IN.forEach(n -> logos.add("builtin:" + n));
        CustomImages.files().forEach(n -> logos.add("file:" + n));
        if (!logos.contains(sign.logo)) {
            logos.add(sign.logo);
        }
        logo = logos.indexOf(sign.logo);
        panel = indexOf(PANELS, sign.panel);
        ink = indexOf(INKS, sign.ink);
        digits = indexOf(DIGITS, sign.digits);
        lit = sign.lit;
        for (int i = 0; i < plates.length; i++) {
            plates[i] = indexOf(PLATES, sign.plates[i]);
        }
    }

    private static int indexOf(int[] list, int value) {
        for (int i = 0; i < list.length; i++) {
            if (list[i] == value) {
                return i;
            }
        }
        return 0;
    }

    private int step() {
        return hasShiftDown() ? -1 : 1;
    }

    private static int cycle(int i, int n, int step) {
        return Math.floorMod(i + step, n);
    }

    @Override
    protected void init() {
        int x0 = width / 2 - 205, y = 22;
        brand = box(x0 + 42, y, 140, sign.brand, 32);
        logoButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            logo = cycle(logo, logos.size(), step());
            refresh();
        }).bounds(x0 + 190, y - 1, 130, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Images folder"), b -> openFolder())
                .bounds(x0 + 324, y - 1, 86, 20).build());
        y += 24;
        panelButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            panel = cycle(panel, PANELS.length, step());
            refresh();
        }).bounds(x0, y, 100, 20).build());
        inkButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            ink = cycle(ink, INKS.length, step());
            refresh();
        }).bounds(x0 + 104, y, 100, 20).build());
        digitButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            digits = cycle(digits, DIGITS.length, step());
            refresh();
        }).bounds(x0 + 208, y, 100, 20).build());
        litButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            lit = !lit;
            refresh();
        }).bounds(x0 + 312, y, 98, 20).build());
        y += 26;
        for (int i = 0; i < FuelPriceSignBlockEntity.ROWS; i++) {
            final int row = i;
            labels[i] = box(x0 + 42, y, 150, sign.labels[i], 20);
            plateButtons[i] = addRenderableWidget(Button.builder(Component.empty(), b -> {
                plates[row] = cycle(plates[row], PLATES.length, step());
                refresh();
            }).bounds(x0 + 198, y - 1, 100, 20).build());
            prices[i] = box(x0 + 330, y, 80, sign.prices[i], 6);
            y += 22;
        }
        y += 4;
        info = box(x0 + 42, y, 368, sign.info, 60);
        y += 28;
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> save()).bounds(width / 2 - 100, y, 96, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(width / 2 + 4, y, 96, 20).build());
        refresh();
    }

    private EditBox box(int x, int y, int w, String value, int max) {
        EditBox b = addRenderableWidget(new EditBox(font, x, y, w, 18, Component.empty()));
        b.setMaxLength(max);
        b.setValue(value);
        return b;
    }

    private void refresh() {
        logoButton.setMessage(Component.literal("Logo: " + CustomImages.title(logos.get(logo))));
        panelButton.setMessage(Component.literal("Sign: " + PANEL_NAMES[panel]));
        inkButton.setMessage(Component.literal("Name: " + INK_NAMES[ink]));
        digitButton.setMessage(Component.literal("Digits: " + DIGIT_NAMES[digits]));
        litButton.setMessage(Component.literal(lit ? "Lit: Yes" : "Lit: No"));
        for (int i = 0; i < plateButtons.length; i++) {
            plateButtons[i].setMessage(Component.literal("Plate: " + PLATE_NAMES[plates[i]]));
        }
    }

    private void openFolder() {
        CustomImages.files();                       // makes the folder if it isn't there yet
        Util.getPlatform().openFile(CustomImages.folder().toFile());
    }

    private void save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("brand", brand.getValue());
        tag.putString("logo", logos.get(logo));
        tag.putInt("panel", PANELS[panel]);
        tag.putInt("ink", INKS[ink]);
        tag.putInt("digits", DIGITS[digits]);
        tag.putBoolean("lit", lit);
        tag.putString("info", info.getValue());
        for (int i = 0; i < FuelPriceSignBlockEntity.ROWS; i++) {
            tag.putString("label" + i, labels[i].getValue());
            tag.putString("price" + i, prices[i].getValue());
            tag.putInt("plate" + i, PLATES[plates[i]]);
        }
        sign.read(tag);
        PtmUkNetwork.CHANNEL.sendToServer(new EditBlockDataMessage(sign.getBlockPos(), tag));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int x0 = width / 2 - 205;
        g.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
        g.drawString(font, "Name", x0, 27, 0xA0A0A0);
        for (int i = 0; i < FuelPriceSignBlockEntity.ROWS; i++) {
            g.drawString(font, "Fuel " + (i + 1), x0, 77 + i * 22, 0xA0A0A0);
            g.drawString(font, "Price", x0 + 302, 77 + i * 22, 0xA0A0A0);
        }
        g.drawString(font, "Extras", x0, 169, 0xA0A0A0);
        g.drawCenteredString(font, "Use | to split the extras onto two lines. Shift-click a button to go back.",
                width / 2, height - 22, 0x808080);
        g.drawCenteredString(font, "Your own logos: put PNG files in the images folder, then pick them with Logo.",
                width / 2, height - 11, 0x808080);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
