package com.ptmuk.client.sign;

import com.ptmuk.network.EditBlockDataMessage;
import com.ptmuk.network.PtmUkNetwork;
import com.ptmuk.sign.DirectionSignBlockEntity;
import com.ptmuk.sign.SignDiagram;
import com.ptmuk.sign.SignScheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/** Editor for a direction sign: scheme, size, diagram, header and destination text. */
public class SignEditScreen extends Screen {
    private final DirectionSignBlockEntity sign;
    private SignScheme scheme;
    private SignDiagram diagram;
    private int signWidth;
    private int signHeight;
    private boolean legs;
    private EditBox header;
    private MultiLineEditBox ahead;
    private MultiLineEditBox left;
    private MultiLineEditBox right;
    private Button schemeButton;
    private Button diagramButton;
    private Button legsButton;

    public SignEditScreen(DirectionSignBlockEntity sign) {
        super(Component.literal("Edit Sign"));
        this.sign = sign;
        this.scheme = sign.scheme;
        this.diagram = sign.diagram;
        this.signWidth = sign.width;
        this.signHeight = sign.height;
        this.legs = sign.legs;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int x0 = cx - 160;
        int y = 22;
        schemeButton = addRenderableWidget(Button.builder(Component.literal(""), b -> {
            scheme = scheme.next();
            refresh();
        }).bounds(x0, y, 155, 20).build());
        diagramButton = addRenderableWidget(Button.builder(Component.literal(""), b -> {
            diagram = diagram.next();
            refresh();
        }).bounds(x0 + 165, y, 155, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.literal("W -"), b -> resize(-1, 0)).bounds(x0, y, 36, 20).build());
        addRenderableWidget(Button.builder(Component.literal("W +"), b -> resize(1, 0)).bounds(x0 + 40, y, 36, 20).build());
        addRenderableWidget(Button.builder(Component.literal("H -"), b -> resize(0, -1)).bounds(x0 + 84, y, 36, 20).build());
        addRenderableWidget(Button.builder(Component.literal("H +"), b -> resize(0, 1)).bounds(x0 + 124, y, 36, 20).build());
        legsButton = addRenderableWidget(Button.builder(Component.literal(""), b -> {
            legs = !legs;
            refresh();
        }).bounds(x0 + 165, y, 155, 20).build());
        y += 34;
        header = addRenderableWidget(new EditBox(font, x0, y, 320, 18, Component.literal("Header")));
        header.setMaxLength(60);
        header.setValue(sign.header);
        y += 32;
        ahead = addRenderableWidget(new MultiLineEditBox(font, x0, y, 320, 44, Component.literal("Ahead / main destinations"),
                Component.literal("Ahead")));
        ahead.setValue(sign.ahead);
        y += 58;
        left = addRenderableWidget(new MultiLineEditBox(font, x0, y, 155, 44, Component.literal("Left destinations"),
                Component.literal("Left")));
        left.setValue(sign.left);
        right = addRenderableWidget(new MultiLineEditBox(font, x0 + 165, y, 155, 44, Component.literal("Right destinations"),
                Component.literal("Right")));
        right.setValue(sign.right);
        y += 62;
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> save()).bounds(cx - 100, y, 96, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(cx + 4, y, 96, 20).build());
        refresh();
    }

    private void resize(int dw, int dh) {
        signWidth = Math.max(1, Math.min(DirectionSignBlockEntity.MAX_WIDTH, signWidth + dw));
        signHeight = Math.max(1, Math.min(DirectionSignBlockEntity.MAX_HEIGHT, signHeight + dh));
        refresh();
    }

    private void refresh() {
        schemeButton.setMessage(Component.literal("Scheme: " + scheme.label));
        diagramButton.setMessage(Component.literal("Diagram: " + diagram.label));
        legsButton.setMessage(Component.literal("Size " + signWidth + " x " + signHeight + "   Legs: " + (legs ? "on" : "off")));
    }

    private void save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("scheme", scheme.name());
        tag.putString("diagram", diagram.name());
        tag.putInt("width", signWidth);
        tag.putInt("height", signHeight);
        tag.putBoolean("legs", legs);
        tag.putString("header", header.getValue());
        tag.putString("ahead", ahead.getValue());
        tag.putString("left", left.getValue());
        tag.putString("right", right.getValue());
        sign.read(tag);   // show it straight away; the server confirms
        PtmUkNetwork.CHANNEL.sendToServer(new EditBlockDataMessage(sign.getBlockPos(), tag));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int cx = this.width / 2;
        int x0 = cx - 160;
        g.drawCenteredString(font, title, cx, 8, 0xFFFFFF);
        g.drawString(font, "Header (junction name, optional)", x0, 70, 0xA0A0A0);
        g.drawString(font, "Ahead / main destinations (one per line)", x0, 102, 0xA0A0A0);
        g.drawString(font, "Left", x0, 160, 0xA0A0A0);
        g.drawString(font, "Right", x0 + 165, 160, 0xA0A0A0);
        g.drawString(font, "[A34] green route patch   {M1} blue motorway patch   <B1043> boxed", x0, 222, 0x80C0FF);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
