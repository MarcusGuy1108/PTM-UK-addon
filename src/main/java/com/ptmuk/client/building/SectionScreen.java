package com.ptmuk.client.building;

import com.ptmuk.building.SectionToolItem;
import com.ptmuk.building.Sections;
import com.ptmuk.network.SectionMessage;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** The Section Mover's screen: copy the area picked out, the saved sections to paste, turn, mirror, undo, delete. */
public class SectionScreen extends Screen {
    private static final int ROWS = 8;
    private static List<Sections.Info> sections = List.of();
    private final InteractionHand hand;
    private EditBox name;
    private int scroll;

    public SectionScreen(InteractionHand hand) {
        super(Component.literal("Section Mover"));
        this.hand = hand;
        SectionToolClient.send(SectionMessage.Action.LIST, "", BlockPos.ZERO, hand);
    }

    /** The server's list arrived. */
    public static void list(List<Sections.Info> list) {
        sections = list;
        if (Minecraft.getInstance().screen instanceof SectionScreen s) {
            s.rebuildWidgets();
        }
    }

    private ItemStack stack() {
        return Minecraft.getInstance().player == null ? ItemStack.EMPTY : Minecraft.getInstance().player.getItemInHand(hand);
    }

    private void send(SectionMessage.Action action, String name) {
        SectionToolClient.send(action, name, BlockPos.ZERO, hand);
    }

    @Override
    protected void init() {
        int x = width / 2 - 150, y = 40;
        String typed = name == null ? "" : name.getValue();
        name = new EditBox(font, x, y, 196, 18, Component.literal("Name"));
        name.setMaxLength(40);
        name.setValue(typed);
        name.setHint(Component.literal("name for the copy"));
        addRenderableWidget(name);
        addRenderableWidget(Button.builder(Component.literal("Copy the area"), b -> {
            send(SectionMessage.Action.COPY, name.getValue());
            onClose();
        }).bounds(x + 200, y, 100, 18).build());
        y += 30;
        String picked = stack().getOrCreateTag().getString(SectionToolItem.PICKED);
        for (int i = scroll; i < Math.min(sections.size(), scroll + ROWS); i++) {
            Sections.Info s = sections.get(i);
            String label = (s.name().equals(picked) ? "> " : "") + s.name() + "   " + s.sx() + " x " + s.sz() + ", " + s.sy() + " high";
            addRenderableWidget(Button.builder(Component.literal(label), b -> {
                send(SectionMessage.Action.PICK, s.name());
                onClose();
            }).bounds(x, y, 240, 18).build());
            addRenderableWidget(Button.builder(Component.literal("Delete"), b -> send(SectionMessage.Action.DELETE, s.name()))
                    .bounds(x + 244, y, 56, 18).build());
            y += 20;
        }
        y = Math.max(y, 70 + ROWS * 20) + 6;
        ItemStack stack = stack();
        addRenderableWidget(Button.builder(Component.literal("Turn: " + SectionToolItem.rotation(stack).ordinal() * 90), b -> {
            send(SectionMessage.Action.ROTATE, "");
            b.setMessage(Component.literal("Turn: " + (SectionToolItem.rotation(stack()).ordinal() + 1) % 4 * 90));
        }).bounds(x, y, 98, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Mirror: " + (stack.getOrCreateTag().getBoolean(SectionToolItem.MIRROR) ? "yes" : "no")), b -> {
            send(SectionMessage.Action.MIRROR, "");
            b.setMessage(Component.literal("Mirror: " + (stack().getOrCreateTag().getBoolean(SectionToolItem.MIRROR) ? "no" : "yes")));
        }).bounds(x + 101, y, 98, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Air: " + (stack.getOrCreateTag().getBoolean(SectionToolItem.KEEP_AIR) ? "keeps what's there" : "clears")), b -> {
            send(SectionMessage.Action.KEEP_AIR, "");
            b.setMessage(Component.literal("Air: " + (stack().getOrCreateTag().getBoolean(SectionToolItem.KEEP_AIR) ? "clears" : "keeps what's there")));
        }).bounds(x + 202, y, 98, 18).build());
        y += 22;
        addRenderableWidget(Button.builder(Component.literal("Pick a new area"), b -> {
            send(SectionMessage.Action.CLEAR, "");
            onClose();
        }).bounds(x, y, 148, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Undo the last paste"), b -> {
            send(SectionMessage.Action.UNDO, "");
            onClose();
        }).bounds(x + 152, y, 148, 18).build());
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int max = Math.max(0, sections.size() - ROWS);
        int s = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
        if (s != scroll) {
            scroll = s;
            rebuildWidgets();
        }
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        super.render(g, mx, my, pt);
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
        ItemStack stack = stack();
        BlockPos c1 = SectionToolItem.corner(stack, SectionToolItem.C1), c2 = SectionToolItem.corner(stack, SectionToolItem.C2);
        String area = c1 == null || c2 == null ? "Right-click two corners of an area to copy it"
                : "Area: " + (Math.abs(c1.getX() - c2.getX()) + 1) + " x " + (Math.abs(c1.getZ() - c2.getZ()) + 1)
                + ", from " + Sections.BELOW + " below the lower corner to the top of what's there";
        g.drawCenteredString(font, area, width / 2, 26, 0xAAAAAA);
        if (sections.isEmpty()) {
            g.drawCenteredString(font, "No sections saved yet", width / 2, 80, 0x888888);
        } else {
            g.drawString(font, "Click one to paste it (then right-click where its first corner goes):", width / 2 - 150, 62, 0xAAAAAA);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
