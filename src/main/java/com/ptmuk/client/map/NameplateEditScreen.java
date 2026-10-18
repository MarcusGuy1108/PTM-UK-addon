package com.ptmuk.client.map;

import com.ptmuk.map.NameplateBlockEntity;
import com.ptmuk.network.EditBlockDataMessage;
import com.ptmuk.network.PtmUkNetwork;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/** The street name and the line under it; saving names the road beside the plate on the map. */
public class NameplateEditScreen extends Screen {
    private final NameplateBlockEntity plate;
    private EditBox name, area;
    private boolean red;

    public NameplateEditScreen(NameplateBlockEntity plate) {
        super(Component.literal("Street Nameplate"));
        this.plate = plate;
        this.red = plate.red;
    }

    @Override
    protected void init() {
        int x0 = width / 2 - 110, y = 70;
        name = addRenderableWidget(new EditBox(font, x0, y, 220, 18, Component.literal("Street name")));
        name.setMaxLength(32);
        name.setValue(plate.name);
        setInitialFocus(name);
        y += 34;
        area = addRenderableWidget(new EditBox(font, x0, y, 220, 18, Component.literal("District and postcode")));
        area.setMaxLength(32);
        area.setValue(plate.area);
        y += 24;
        addRenderableWidget(Button.builder(Component.literal(red ? "Second line: red (London)" : "Second line: black"), b -> {
            red = !red;
            b.setMessage(Component.literal(red ? "Second line: red (London)" : "Second line: black"));
        }).bounds(x0, y, 220, 20).build());
        y += 30;
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> save()).bounds(width / 2 - 100, y, 96, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(width / 2 + 4, y, 96, 20).build());
    }

    private void save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name.getValue().trim());
        tag.putString("area", area.getValue().trim());
        tag.putBoolean("red", red);
        plate.read(tag);
        PtmUkNetwork.CHANNEL.sendToServer(new EditBlockDataMessage(plate.getBlockPos(), tag));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        // a preview of the plate
        String n = name == null || name.getValue().isBlank() ? "STREET NAME" : name.getValue().toUpperCase(Locale.ROOT);
        String a = area == null ? "" : area.getValue().toUpperCase(Locale.ROOT);
        int w = Math.max(120, Math.max(font.width(n) * 2, font.width(a)) + 20), x0 = width / 2 - w / 2;
        g.fill(x0, 26, x0 + w, 58, 0xFF141414);
        g.fill(x0 + 2, 28, x0 + w - 2, 56, 0xFFF4F4F2);
        g.pose().pushPose();
        g.pose().translate(width / 2f, a.isBlank() ? 35 : 31, 0);
        g.pose().scale(2, 2, 1);
        g.drawString(font, n, -font.width(n) / 2, 0, 0x141414, false);
        g.pose().popPose();
        if (!a.isBlank()) {
            g.drawString(font, a, width / 2 - font.width(a) / 2, 46, red ? 0xC8102E : 0x141414, false);
        }
        g.drawString(font, "Street name (names the road beside it on the map)", width / 2 - 110, 60, 0xA0A0A0);
        g.drawString(font, "District and postcode (optional)", width / 2 - 110, 94, 0xA0A0A0);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
