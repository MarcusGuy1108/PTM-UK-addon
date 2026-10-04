package com.ptmuk.client.sign;

import com.ptmuk.network.EditBlockDataMessage;
import com.ptmuk.network.PtmUkNetwork;
import com.ptmuk.sign.BusStopBlockEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/** Editor for a bus stop flag. */
public class BusStopEditScreen extends Screen {
    private final BusStopBlockEntity stop;
    private EditBox name;
    private EditBox towards;
    private EditBox routes;
    private EditBox letter;
    private boolean request;
    private Button requestButton;

    public BusStopEditScreen(BusStopBlockEntity stop) {
        super(Component.literal("Edit Bus Stop"));
        this.stop = stop;
        this.request = stop.request;
    }

    @Override
    protected void init() {
        int x0 = width / 2 - 120, y = 44;
        name = box(x0, y, 240, stop.name, 40);
        y += 36;
        towards = box(x0, y, 240, stop.towards, 40);
        y += 36;
        routes = box(x0, y, 240, stop.routes, 40);
        y += 36;
        letter = box(x0, y, 40, stop.letter, 2);
        requestButton = addRenderableWidget(Button.builder(Component.literal(""), b -> {
            request = !request;
            refresh();
        }).bounds(x0 + 60, y - 1, 180, 20).build());
        y += 34;
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
        requestButton.setMessage(Component.literal(request ? "Type: Request Stop" : "Type: Bus Stop"));
    }

    private void save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name.getValue());
        tag.putString("towards", towards.getValue());
        tag.putString("routes", routes.getValue());
        tag.putString("letter", letter.getValue().toUpperCase());
        tag.putBoolean("request", request);
        stop.read(tag);
        PtmUkNetwork.CHANNEL.sendToServer(new EditBlockDataMessage(stop.getBlockPos(), tag));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int x0 = width / 2 - 120;
        g.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        g.drawString(font, "Stop name (blank = PTM2 station name)", x0, 33, 0xA0A0A0);
        g.drawString(font, "Towards", x0, 69, 0xA0A0A0);
        g.drawString(font, "Routes (separated by spaces)", x0, 105, 0xA0A0A0);
        g.drawString(font, "Stop letter", x0, 141, 0xA0A0A0);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
