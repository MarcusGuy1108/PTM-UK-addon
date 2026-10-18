package com.ptmuk.client.home;

import com.ptmuk.home.RoomDesigner;
import com.ptmuk.network.DesignRoomMessage;
import com.ptmuk.network.PtmUkNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** What's this room? One button per kind of room, and Undo. */
public class InteriorDesignerScreen extends Screen {
    private final BlockPos floor;

    private InteriorDesignerScreen(BlockPos floor) {
        super(Component.literal("Interior Designer"));
        this.floor = floor;
    }

    /** floor null: right-clicked the air, so only Undo. */
    public static void open(BlockPos floor) {
        Minecraft.getInstance().setScreen(new InteriorDesignerScreen(floor));
    }

    @Override
    protected void init() {
        RoomDesigner.Room[] rooms = RoomDesigner.Room.values();
        int cols = 3, w = 120, x0 = width / 2 - (cols * (w + 4) - 4) / 2, y0 = 44;
        for (int i = 0; i < rooms.length; i++) {
            int room = i;
            Button b = addRenderableWidget(Button.builder(Component.literal(rooms[i].title), x -> send(room))
                    .bounds(x0 + i % cols * (w + 4), y0 + i / cols * 22, w, 20).build());
            b.active = floor != null;
        }
        int y = y0 + (rooms.length + cols - 1) / cols * 22 + 8;
        addRenderableWidget(Button.builder(Component.literal("Undo last room"), x -> send(-1)).bounds(width / 2 - 122, y, 120, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), x -> onClose()).bounds(width / 2 + 2, y, 120, 20).build());
    }

    private void send(int room) {
        PtmUkNetwork.CHANNEL.sendToServer(new DesignRoomMessage(floor == null ? BlockPos.ZERO : floor, room));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        g.drawCenteredString(font, floor == null ? "Right-click the floor of a room to furnish it"
                : "What's this room? Doorways stay clear; furniture already in it is replaced", width / 2, 26, 0xA0A0A0);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
