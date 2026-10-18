package com.ptmuk.client.map;

import com.ptmuk.map.MapData;
import com.ptmuk.network.NameRoadMessage;
import com.ptmuk.network.PtmUkNetwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The full-screen map (M), a bit like a phone's: drag to move, scroll to zoom, search for a shop,
 * stop, road or pin, click a place for directions, right-click to drop a pin or name a road.
 * Shops and petrol stations come from their signs; stops, stations, districts, car parks, traffic
 * and the buses on the move come from PTM2.
 */
public class WorldMapScreen extends Screen {
    private static final float MIN_SCALE = 0.125f, MAX_SCALE = 8f;
    /** Where the map was looking last time, so it opens there again (until you move a long way). */
    private static double lastX = Double.NaN, lastZ;
    private static float lastScale = 1f;

    /** Something picked on the map: a place, a pin, a road or just a spot. */
    private record Sel(String name, String what, double x, double z, MapPrefs.Pin pin) {
    }

    private enum Naming { PIN, ROAD }

    private double vx, vz;
    private float scale;
    private boolean dragging;
    /** The right-click menu: where it is on screen and in the world, or menuX < 0 for none. */
    private int menuX = -1, menuY;
    private int menuWX, menuWZ;
    private Naming naming;
    private EditBox nameBox, search;
    private String nameText = "", searchText = "";
    private Sel selected;

    public WorldMapScreen() {
        super(Component.literal("Map"));
        Minecraft mc = Minecraft.getInstance();
        double px = mc.player.getX(), pz = mc.player.getZ();
        boolean near = !Double.isNaN(lastX) && Math.abs(lastX - px) < 1500 && Math.abs(lastZ - pz) < 1500;
        vx = near ? lastX : px;
        vz = near ? lastZ : pz;
        scale = lastScale;
    }

    @Override
    protected void init() {
        int y = height - 24;
        addRenderableWidget(Button.builder(Component.literal("Centre on me"), b -> {
            vx = minecraft.player.getX();
            vz = minecraft.player.getZ();
        }).bounds(6, y, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Minimap: " + (MapPrefs.settings().minimap ? "On" : "Off")), b -> {
            MapPrefs.settings().minimap = !MapPrefs.settings().minimap;
            MapPrefs.saveSettings();
            rebuildWidgets();
        }).bounds(90, y, 80, 20).build());
        if (Navigation.active()) {
            addRenderableWidget(Button.builder(Component.literal("End directions"), b -> {
                Navigation.stop();
                rebuildWidgets();
            }).bounds(174, y, 84, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(width - 56, y, 50, 20).build());
        // search, top middle, with what it finds underneath
        search = addRenderableWidget(new EditBox(font, width / 2 - 80, 18, 160, 16, Component.literal("Search")));
        search.setMaxLength(40);
        search.setValue(searchText);
        search.setHint(Component.literal("Search shops, stops, roads..."));
        search.setResponder(t -> {
            if (!t.equals(searchText)) {
                searchText = t;
                rebuildWidgets();
            }
        });
        if (!searchText.isEmpty() && naming == null) {
            setInitialFocus(search);                         // still typing: each letter rebuilds the screen
        }
        List<Sel> found = find(searchText);
        for (int i = 0; i < found.size(); i++) {
            Sel s = found.get(i);
            addRenderableWidget(Button.builder(Component.literal(s.name() + "  · " + s.what()), b -> {
                select(s);
                vx = s.x();
                vz = s.z();
                scale = Math.max(scale, 2);
                searchText = "";
                rebuildWidgets();
            }).bounds(width / 2 - 100, 36 + i * 21, 200, 20).build());
        }
        if (selected != null) {
            Sel sel = selected;
            int bx = width - 60;
            if (sel.pin() != null) {
                bx -= 80;
                addRenderableWidget(Button.builder(Component.literal("Remove pin"), b -> {
                    MapPrefs.pins().remove(sel.pin());
                    MapPrefs.savePins();
                    selected = null;
                    rebuildWidgets();
                }).bounds(bx, y, 76, 20).build());
            }
            bx -= 80;
            addRenderableWidget(Button.builder(Component.literal("Directions"), b -> {
                Navigation.to(sel.x(), sel.z(), sel.name());
                onClose();
            }).bounds(bx, y, 76, 20).build());
        }
        if (naming != null) {
            nameBox = addRenderableWidget(new EditBox(font, menuX, menuY, 120, 18, Component.literal("Name")));
            nameBox.setMaxLength(40);
            nameBox.setValue(nameText);
            nameBox.setHighlightPos(0);                     // typing replaces the suggested name
            nameBox.setResponder(t -> nameText = t);
            setInitialFocus(nameBox);
            addRenderableWidget(Button.builder(Component.literal("Save"), b -> saveName()).bounds(menuX + 124, menuY - 1, 40, 20).build());
        } else if (menuX >= 0) {
            int my = menuY;
            addRenderableWidget(Button.builder(Component.literal("Directions to here"), b -> {
                Navigation.to(menuWX + 0.5, menuWZ + 0.5, "X " + menuWX + " Z " + menuWZ);
                onClose();
            }).bounds(menuX, my, 120, 20).build());
            my += 21;
            addRenderableWidget(Button.builder(Component.literal("Drop a pin here"), b -> {
                naming = Naming.PIN;
                nameText = "Pin " + (MapPrefs.pins().size() + 1);
                rebuildWidgets();
            }).bounds(menuX, my, 120, 20).build());
            if (minecraft.player.isCreative()) {
                my += 21;
                MapData.Road road = MapClient.roadAt(menuWX + 0.5, menuWZ + 0.5);
                addRenderableWidget(Button.builder(Component.literal(road == null ? "Name this road" : "Rename this road"), b -> {
                    naming = Naming.ROAD;
                    nameText = road == null ? "" : road.name();
                    rebuildWidgets();
                }).bounds(menuX, my, 120, 20).build());
            }
        }
    }

    private void saveName() {
        String name = nameText.trim();
        if (naming == Naming.PIN) {
            List<MapPrefs.Pin> pins = MapPrefs.pins();
            MapPrefs.Pin pin = new MapPrefs.Pin(name.isEmpty() ? "Pin" : name, menuWX, menuWZ, MapPrefs.PIN_COLOURS[pins.size() % MapPrefs.PIN_COLOURS.length]);
            pins.add(pin);
            MapPrefs.savePins();
            select(new Sel(pin.name(), "Pin", pin.x() + 0.5, pin.z() + 0.5, pin));
        } else {
            PtmUkNetwork.CHANNEL.sendToServer(new NameRoadMessage(menuWX, menuWZ, name));
        }
        naming = null;
        menuX = -1;
        rebuildWidgets();
    }

    private void select(Sel s) {
        selected = s;
        menuX = -1;
        naming = null;
    }

    /** Places, roads, areas and pins whose names have the text in them, nearest first. */
    private List<Sel> find(String text) {
        List<Sel> out = new ArrayList<>();
        String q = text.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return out;
        }
        MapData d = MapClient.data;
        for (MapData.Place p : d.places) {
            if (p.name().toLowerCase(Locale.ROOT).contains(q)) {
                out.add(new Sel(p.name(), what(p.kind()), p.x() + 0.5, p.z() + 0.5, null));
            }
        }
        for (MapData.Road r : d.roads) {
            if (r.name().toLowerCase(Locale.ROOT).contains(q)) {
                int m = r.x().length / 2;
                out.add(new Sel(r.name(), "Road", r.x()[m], r.z()[m], null));
            }
        }
        for (MapData.Area a : d.areas) {
            if (a.kind() == MapData.AreaKind.DISTRICT && a.name().toLowerCase(Locale.ROOT).contains(q)) {
                out.add(new Sel(a.name(), "Area", (a.x0() + a.x1()) / 2.0, (a.z0() + a.z1()) / 2.0, null));
            }
        }
        for (MapPrefs.Pin p : MapPrefs.pins()) {
            if (p.name().toLowerCase(Locale.ROOT).contains(q)) {
                out.add(new Sel(p.name(), "Pin", p.x() + 0.5, p.z() + 0.5, p));
            }
        }
        double px = minecraft.player.getX(), pz = minecraft.player.getZ();
        out.sort((a, b) -> Double.compare(Math.hypot(a.x() - px, a.z() - pz), Math.hypot(b.x() - px, b.z() - pz)));
        return out.subList(0, Math.min(6, out.size()));
    }

    static String what(MapData.Kind kind) {
        return switch (kind) {
            case SHOP -> "Shop";
            case FUEL -> "Petrol station";
            case STOP -> "Bus stop";
            case STATION -> "Station";
            case PARKING -> "Parking";
        };
    }

    /** Screen to world and back. */
    private double wx(double mx) {
        return vx + (mx - width / 2.0) / scale;
    }

    private double wz(double my) {
        return vz + (my - height / 2.0) / scale;
    }

    private float sx(double x) {
        return (float) (width / 2.0 + (x - vx) * scale);
    }

    private float sy(double z) {
        return (float) (height / 2.0 + (z - vz) * scale);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xFF14181C);
        MapStore store = MapStore.get();
        if (store != null) {
            g.pose().pushPose();
            g.pose().translate(width / 2f, height / 2f, 0);
            MapDraw.regions(g, store, vx, vz, scale, Math.max(width, height) / scale);
            g.pose().popPose();
        }
        MapData d = MapClient.data;
        for (MapData.Area a : d.areas) {
            float x0 = sx(a.x0()), y0 = sy(a.z0()), x1 = sx(a.x1() + 1), y1 = sy(a.z1() + 1);
            if (a.kind() == MapData.AreaKind.TRAFFIC) {
                g.fill((int) x0, (int) y0, (int) x1, (int) y1, (Math.min(0x70, 0x28 + a.level() * 0x10) << 24) | 0xE53935);
            }
        }
        Navigation.drawRoute(g, this::sx, this::sy, 3);
        for (MapData.Road r : d.roads) {
            MapDraw.roadName(g, font, r, this::sx, this::sy);
        }
        for (MapData.Area a : d.areas) {
            if (a.kind() == MapData.AreaKind.DISTRICT) {
                String name = a.name().toUpperCase(Locale.ROOT);
                g.drawCenteredString(font, name, (int) sx((a.x0() + a.x1()) / 2.0), (int) sy((a.z0() + a.z1()) / 2.0), 0xD0E8E8E8);
            }
        }
        for (MapData.Vehicle v : d.vehicles) {
            MapDraw.dot(g, sx(v.x()), sy(v.z()), v.type() == 1 ? 2 : 1, v.type() == 1 ? 0xFFE53935 : v.type() == 0 ? 0xFFB0B0B0 : 0xFFF2C21B);
            if (v.type() == 1 && scale >= 2 && !v.label().isEmpty()) {
                g.drawString(font, v.label(), (int) sx(v.x()) + 4, (int) sy(v.z()) - 4, 0xFFFFFF, true);
            }
        }
        for (MapData.Place p : d.places) {
            float x = sx(p.x() + 0.5), y = sy(p.z() + 0.5);
            MapDraw.place(g, font, x, y, p.kind());
            boolean hover = Math.abs(mouseX - x) <= 5 && Math.abs(mouseY - y) <= 5;
            if (scale >= 2 || hover) {
                g.drawString(font, p.name(), (int) x + 7, (int) y - 4, 0xFFFFFF, true);
            }
        }
        for (MapPrefs.Pin pin : MapPrefs.pins()) {
            float x = sx(pin.x() + 0.5), y = sy(pin.z() + 0.5);
            MapDraw.dot(g, x, y, selected != null && selected.pin() == pin ? 4 : 3, pin.colour());
            g.drawString(font, pin.name(), (int) x + 6, (int) y - 4, 0xFFFFFF, true);
        }
        if (selected != null) {
            MapDraw.dot(g, sx(selected.x()), sy(selected.z()), 2, 0xFFFFFFFF);
        }
        var p = minecraft.player;
        MapDraw.arrow(g, sx(p.getX()), sy(p.getZ()), p.getYRot() + 180, 5, 0xFFFFFFFF);
        g.drawString(font, "X " + Mth.floor(wx(mouseX)) + "  Z " + Mth.floor(wz(mouseY)), 6, 6, 0xFFFFFF, true);
        MapData.Road under = MapClient.roadAt(wx(mouseX), wz(mouseY));
        if (under != null) {
            g.drawString(font, under.name(), 6, 18, 0xFFFFFF, true);
        }
        if (selected != null) {
            g.drawString(font, selected.name() + "  · " + selected.what(), 6, height - 38, 0xFFFFFF, true);
        } else if (searchText.isEmpty() && menuX < 0) {
            g.drawCenteredString(font, "Drag to move, scroll to zoom, click a place, right-click for more", width / 2, height - 38, 0xA0A0A0);
        }
        if (menuX >= 0 && naming == null) {
            g.drawString(font, "X " + menuWX + "  Z " + menuWZ, menuX, menuY - 10, 0xFFFFFF, true);
        } else if (naming != null) {
            g.drawString(font, naming == Naming.PIN ? "Pin name" : "Road name (blank to take it off)", menuX, menuY - 10, 0xFFFFFF, true);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) {
            if (naming != null && nameBox != null) {
                setFocused(nameBox);                        // the click left focus on a button that's gone
            }
            return true;
        }
        setFocused(null);
        if (button == 1) {
            menuX = (int) Math.min(mx, width - 130);
            menuY = (int) Math.min(my, height - 96);
            menuWX = Mth.floor(wx(mx));
            menuWZ = Mth.floor(wz(my));
            naming = null;
            rebuildWidgets();
            return true;
        }
        if (button == 0) {
            Sel hit = null;
            for (MapData.Place p : MapClient.data.places) {
                if (Math.abs(sx(p.x() + 0.5) - mx) <= 5 && Math.abs(sy(p.z() + 0.5) - my) <= 5) {
                    hit = new Sel(p.name(), what(p.kind()), p.x() + 0.5, p.z() + 0.5, null);
                }
            }
            for (MapPrefs.Pin pin : MapPrefs.pins()) {
                if (Math.abs(sx(pin.x() + 0.5) - mx) <= 5 && Math.abs(sy(pin.z() + 0.5) - my) <= 5) {
                    hit = new Sel(pin.name(), "Pin", pin.x() + 0.5, pin.z() + 0.5, pin);
                }
            }
            boolean changed = !java.util.Objects.equals(hit, selected) || menuX >= 0 || naming != null;
            select(hit);
            if (changed) {
                rebuildWidgets();
            }
            dragging = hit == null;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging && button == 0) {
            vx -= dx / scale;
            vz -= dy / scale;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        double wx = wx(mx), wz = wz(my);
        scale = Mth.clamp(delta > 0 ? scale * 2 : scale / 2, MIN_SCALE, MAX_SCALE);
        vx = wx - (mx - width / 2.0) / scale;
        vz = wz - (my - height / 2.0) / scale;
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (naming != null && (key == 257 || key == 335)) {
            saveName();
            return true;
        }
        boolean typing = getFocused() instanceof EditBox;
        if (!typing && MapClient.OPEN.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void removed() {
        lastX = vx;
        lastZ = vz;
        lastScale = scale;
        MapStore store = MapStore.get();
        if (store != null) {
            store.saveAll();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
