package com.ptmuk.client.motorway;

import com.ptmuk.motorway.VmsBlockEntity;
import com.ptmuk.network.EditBlockDataMessage;
import com.ptmuk.network.PtmUkNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/** Editor for a variable message sign: three lines of text, width and lanterns. */
public class VmsEditScreen extends Screen {
    private static final String[][] PRESETS = {
            {"QUEUE", "CAUTION", ""}, {"CONGESTION", "AFTER J12", ""}, {"ACCIDENT", "LANE 1 CLOSED", "SLOW DOWN"},
            {"FOG", "SLOW DOWN", ""}, {"THINK!", "TIREDNESS KILLS", "TAKE A BREAK"}, {"", "", ""}};
    private final VmsBlockEntity vms;
    private int signWidth;
    private boolean lanterns;
    private final EditBox[] boxes = new EditBox[VmsBlockEntity.LINES];
    private Button sizeButton;
    private int preset;

    public VmsEditScreen(VmsBlockEntity vms) {
        super(Component.literal("Edit Matrix Sign"));
        this.vms = vms;
        this.signWidth = vms.width;
        this.lanterns = vms.lanterns;
    }

    @Override
    protected void init() {
        int cx = this.width / 2, x0 = cx - 120;
        int y = 40;
        for (int i = 0; i < boxes.length; i++) {
            boxes[i] = addRenderableWidget(new EditBox(font, x0, y, 240, 18, Component.literal("Line " + (i + 1))));
            boxes[i].setMaxLength(VmsBlockEntity.MAX_CHARS);
            boxes[i].setValue(vms.lines[i]);
            y += 24;
        }
        y += 6;
        addRenderableWidget(Button.builder(Component.literal("W -"), b -> resize(-1)).bounds(x0, y, 36, 20).build());
        addRenderableWidget(Button.builder(Component.literal("W +"), b -> resize(1)).bounds(x0 + 40, y, 36, 20).build());
        sizeButton = addRenderableWidget(Button.builder(Component.literal(""), b -> {
            lanterns = !lanterns;
            refresh();
        }).bounds(x0 + 80, y, 160, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.literal("Preset message"), b -> {
            String[] p = PRESETS[preset++ % PRESETS.length];
            for (int i = 0; i < boxes.length; i++) {
                boxes[i].setValue(p[i]);
            }
        }).bounds(x0, y, 240, 20).build());
        y += 30;
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> save()).bounds(cx - 100, y, 96, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(cx + 4, y, 96, 20).build());
        refresh();
    }

    private void resize(int d) {
        signWidth = Math.max(VmsBlockEntity.MIN_WIDTH, Math.min(VmsBlockEntity.MAX_WIDTH, signWidth + d));
        refresh();
    }

    private void refresh() {
        sizeButton.setMessage(Component.literal("Width " + signWidth + "   Lanterns: " + (lanterns ? "on" : "off")));
    }

    private void save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("width", signWidth);
        tag.putBoolean("lanterns", lanterns);
        for (int i = 0; i < boxes.length; i++) {
            tag.putString("line" + i, boxes[i].getValue());
        }
        vms.read(tag);
        PtmUkNetwork.CHANNEL.sendToServer(new EditBlockDataMessage(vms.getBlockPos(), tag));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.drawCenteredString(font, title, this.width / 2, 12, 0xFFFFFF);
        g.drawCenteredString(font, "Up to " + vms.charsPerLine() + " characters per line at the current width",
                this.width / 2, 26, 0xA0A0A0);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
