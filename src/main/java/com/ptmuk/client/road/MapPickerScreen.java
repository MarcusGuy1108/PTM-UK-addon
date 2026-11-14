package com.ptmuk.client.road;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/**
 * Picking the real area to download on a map: OpenStreetMap's own map, dragged about and zoomed
 * with the scroll wheel, with the area to fetch as a box in the middle; a place typed in (or a map
 * link pasted) jumps there. Download this area fetches it and brings it into the Road Planner it
 * was opened from. The map's pictures come from OpenStreetMap's tile server two at a time, under
 * the mod's name, and are kept on disk for a week, as its usage policy asks.
 */
final class MapPickerScreen extends Screen {
    /** How big a 256-pixel tile is drawn, in screen units. */
    private static final int TILE = 128;
    private static final int MIN_ZOOM = 3, MAX_ZOOM = 18, BAR = 28;
    /** Where it was left (Great Britain to start with). */
    private static double lat = 54.0, lon = -2.5;
    private static int zoom = 6;
    private static String placeName;

    private static final Map<String, ResourceLocation> TILES = new LinkedHashMap<>(64, 0.75f, true);
    private static final Set<String> LOADING = new HashSet<>(), FAILED = new HashSet<>();
    /** The tiles on screen now: the only ones worth fetching. */
    private static final Set<String> WANTED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final ExecutorService FETCH = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "PTM UK map tiles");
        t.setDaemon(true);
        return t;
    });

    private final RoadPlannerScreen planner;
    private EditBox search;
    private String status = "Drag the map so the box covers the area you want. Scroll to zoom.";

    MapPickerScreen(RoadPlannerScreen planner) {
        super(Component.literal("Pick an area"));
        this.planner = planner;
    }

    @Override
    protected void init() {
        int y = height - BAR + 5;
        search = new EditBox(font, 4, y, 150, 18, Component.literal("Place"));
        search.setMaxLength(200);
        search.setHint(Component.literal("place, postcode or map link"));
        addRenderableWidget(search);
        addRenderableWidget(Button.builder(Component.literal("Go"), b -> go()).bounds(158, y, 30, 18).build());
        addRenderableWidget(Button.builder(Component.literal(size()), b -> {
            RoadPlannerScreen.downloadSize = (RoadPlannerScreen.downloadSize + 1) % RoadPlannerScreen.DOWNLOAD_METRES.length;
            b.setMessage(Component.literal(size()));
        }).bounds(192, y, 70, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Download this area"), b -> download()).bounds(266, y, 110, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(380, y, 50, 18).build());
    }

    private static String size() {
        int m = RoadPlannerScreen.DOWNLOAD_METRES[RoadPlannerScreen.downloadSize];
        return "Area: " + (m >= 1000 ? m / 1000.0 + " km" : m + " m");
    }

    private void go() {
        String text = search.getValue().trim();
        if (text.isEmpty()) {
            return;
        }
        status = "Finding " + text + "...";
        Minecraft mc = Minecraft.getInstance();
        MapDownload.find(text).whenComplete((r, err) -> mc.execute(() -> {
            if (err != null) {
                status = (err.getCause() != null ? err.getCause() : err).getMessage();
                return;
            }
            lat = (Double) r[0];
            lon = (Double) r[1];
            placeName = (String) r[2];
            zoom = Math.max(zoom, 15);
            status = "Found " + placeName + ". Drag to fit the box, then Download this area.";
        }));
    }

    private void download() {
        String name = placeName != null ? placeName : String.format(Locale.ROOT, "%.4f_%.4f", lat, lon);
        Minecraft.getInstance().setScreen(planner);
        planner.downloadAt(name, lat, lon);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(planner);
    }

    // ------------------------------------------------------------------ the map (Web Mercator, in tiles at this zoom)

    private static double tileX(double lon, int z) {
        return (lon + 180) / 360 * (1 << z);
    }

    private static double tileY(double lat, int z) {
        double r = Math.toRadians(Math.max(-85, Math.min(85, lat)));
        return (1 - Math.log(Math.tan(r) + 1 / Math.cos(r)) / Math.PI) / 2 * (1 << z);
    }

    private static double lonOf(double tx, int z) {
        return tx / (1 << z) * 360 - 180;
    }

    private static double latOf(double ty, int z) {
        return Math.toDegrees(Math.atan(Math.sinh(Math.PI * (1 - 2 * ty / (1 << z)))));
    }

    private int mapH() {
        return height - BAR;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xFF202020);
        double cx = tileX(lon, zoom), cy = tileY(lat, zoom), sx0 = width / 2.0, sy0 = mapH() / 2.0;
        int n = 1 << zoom;
        WANTED.clear();
        g.enableScissor(0, 0, width, mapH());
        for (int ty = (int) Math.floor(cy - sy0 / TILE); ty <= (int) Math.floor(cy + sy0 / TILE); ty++) {
            for (int tx = (int) Math.floor(cx - sx0 / TILE); tx <= (int) Math.floor(cx + sx0 / TILE); tx++) {
                if (ty < 0 || ty >= n) {
                    continue;
                }
                ResourceLocation t = tile(zoom, Math.floorMod(tx, n), ty);
                int x = (int) Math.round(sx0 + (tx - cx) * TILE), y = (int) Math.round(sy0 + (ty - cy) * TILE);
                if (t != null) {
                    g.blit(t, x, y, TILE, TILE, 0, 0, 256, 256, 256, 256);
                } else {
                    g.fill(x + 1, y + 1, x + TILE - 1, y + TILE - 1, 0xFF2C2C2C);
                }
            }
        }
        // the area to download, its real size at this latitude
        double metresPerUnit = 40075016.686 * Math.cos(Math.toRadians(lat)) / n / TILE;
        int half = (int) Math.max(3, RoadPlannerScreen.DOWNLOAD_METRES[RoadPlannerScreen.downloadSize] / 2.0 / metresPerUnit);
        int bx0 = (int) sx0 - half, by0 = (int) sy0 - half, bx1 = (int) sx0 + half, by1 = (int) sy0 + half;
        g.fill(bx0, by0, bx1, by1, 0x30FFD24A);
        g.renderOutline(bx0, by0, bx1 - bx0, by1 - by0, 0xFFFFD24A);
        g.renderOutline(bx0 - 1, by0 - 1, bx1 - bx0 + 2, by1 - by0 + 2, 0xFF000000);
        g.disableScissor();
        String credit = "© OpenStreetMap contributors";
        g.fill(width - font.width(credit) - 6, mapH() - 12, width, mapH(), 0xC0FFFFFF);
        g.drawString(font, credit, width - font.width(credit) - 3, mapH() - 10, 0xFF303030, false);
        g.fill(0, 0, width, 14, 0xA0000000);
        g.drawString(font, status, 4, 3, 0xFFFFFF);
        String where = String.format(Locale.ROOT, "%.5f, %.5f", lat, lon);
        g.drawString(font, where, width - font.width(where) - 4, 3, 0xA0A0A0);
        g.fill(0, mapH(), width, height, 0xFF101010);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (my < mapH()) {
            lon = lonOf(tileX(lon, zoom) - dx / TILE, zoom);
            lat = latOf(Math.max(0, Math.min(1 << zoom, tileY(lat, zoom) - dy / TILE)), zoom);
            lon = ((lon + 540) % 360) - 180;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom + (delta > 0 ? 1 : -1)));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (search.isFocused() && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            go();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ tiles

    /** A tile's picture, or null while it's fetched (it's asked for now, if it hasn't been). */
    private static ResourceLocation tile(int z, int x, int y) {
        String key = z + "/" + x + "/" + y;
        WANTED.add(key);
        ResourceLocation t = TILES.get(key);
        if (t != null || FAILED.contains(key) || !LOADING.add(key)) {
            return t;
        }
        Minecraft mc = Minecraft.getInstance();
        FETCH.execute(() -> {
            byte[] png = null;
            try {
                if (WANTED.contains(key)) {                     // panned past already: not worth fetching
                    png = MapDownload.tile(z, x, y);
                }
            } catch (Exception e) {
                mc.execute(() -> FAILED.add(key));            // not again this time (no hammering the server)
            }
            byte[] got = png;
            mc.execute(() -> {
                LOADING.remove(key);
                if (got == null) {
                    return;
                }
                try {
                    DynamicTexture tex = new DynamicTexture(NativeImage.read(got));
                    TILES.put(key, mc.getTextureManager().register("ptmuk_osm_tile", tex));
                } catch (java.io.IOException e) {
                    FAILED.add(key);
                }
                while (TILES.size() > 300) {                    // the least lately seen go
                    var eldest = TILES.entrySet().iterator().next();
                    mc.getTextureManager().release(eldest.getValue());
                    TILES.remove(eldest.getKey());
                }
            });
        });
        return null;
    }
}
