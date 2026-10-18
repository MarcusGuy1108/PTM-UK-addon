package com.ptmuk.client.map;

import com.mojang.math.Axis;
import com.ptmuk.map.MapData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/** The minimap in the top right corner: the way you're facing is up, with your pins on it. */
final class MinimapHud {
    private static final int SIZE = 96;
    /** Below the corner, where PTM2 puts its phone icon. */
    private static final int TOP = 22;

    private MinimapHud() {
    }

    static void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        MapPrefs.Settings settings = MapPrefs.settings();
        if (!settings.minimap || mc.options.hideGui || mc.options.renderDebug || mc.player == null || mc.screen instanceof WorldMapScreen) {
            return;
        }
        MapStore store = MapStore.get();
        if (store == null) {
            return;
        }
        int x0 = width - SIZE - 6, y0 = TOP, cx = x0 + SIZE / 2, cy = y0 + SIZE / 2;
        float zoom = settings.minimapZoom;
        double px = mc.player.getX(), pz = mc.player.getZ();
        float turn = -(mc.player.getViewYRot(partialTick) + 180);
        g.fill(x0 - 2, y0 - 2, x0 + SIZE + 2, y0 + SIZE + 2, 0xFF101010);
        g.fill(x0, y0, x0 + SIZE, y0 + SIZE, 0xFF14181C);
        g.enableScissor(x0, y0, x0 + SIZE, y0 + SIZE);
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(turn));
        MapDraw.regions(g, store, px, pz, zoom, SIZE * 0.75 / zoom);
        Navigation.drawRoute(g, x -> (x - px) * zoom, z -> (z - pz) * zoom, 2);
        MapData d = MapClient.data;
        for (MapData.Vehicle v : d.vehicles) {
            if (v.type() == 1) {
                MapDraw.dot(g, (float) ((v.x() - px) * zoom), (float) ((v.z() - pz) * zoom), 1, 0xFFE53935);
            }
        }
        for (MapData.Place pl : d.places) {
            float x = (float) ((pl.x() + 0.5 - px) * zoom), y = (float) ((pl.z() + 0.5 - pz) * zoom);
            g.fill((int) x - 2, (int) y - 2, (int) x + 2, (int) y + 2, MapDraw.colour(pl.kind()));
        }
        for (MapPrefs.Pin pin : MapPrefs.pins()) {
            MapDraw.dot(g, (float) ((pin.x() + 0.5 - px) * zoom), (float) ((pin.z() + 0.5 - pz) * zoom), 2, pin.colour());
        }
        g.pose().popPose();
        g.disableScissor();
        MapDraw.arrow(g, cx, cy, 0, 4, 0xFFFFFFFF);
        // the road you're on, underneath
        MapData.Road road = MapClient.roadAt(px, pz);
        if (road != null) {
            int w = mc.font.width(road.name()) + 6;
            g.fill(cx - w / 2, y0 + SIZE + 3, cx + w / 2, y0 + SIZE + 14, 0xC0FFFFFF);
            g.drawString(mc.font, road.name(), cx - w / 2 + 3, y0 + SIZE + 5, 0x202020, false);
        }
        // north, round the edge
        double a = Math.toRadians(turn);
        float r = SIZE / 2f - 6;
        g.drawCenteredString(mc.font, "N", (int) (cx + Math.sin(a) * r), (int) (cy - Math.cos(a) * r) - 4, 0xFFFF5555);
    }
}
