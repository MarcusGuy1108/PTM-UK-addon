package com.ptmuk.client.map;

import com.ptmuk.network.PtmUkNetwork;
import com.ptmuk.network.RouteMessage;
import com.ptmuk.network.RouteRequestMessage;
import java.util.function.DoubleUnaryOperator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/**
 * Directions (the map's GPS): the route along the roads, drawn on the map and minimap, the next
 * turn across the top of the screen, a new route if you go off it, and "You have arrived".
 */
public final class Navigation {
    /** Further than this from the route and it works out a new one. */
    private static final double OFF_ROUTE = 14;
    private static final double ARRIVED = 8;

    private static boolean active;
    private static double toX, toZ;
    private static String name = "";
    private static RouteMessage route;
    private static String note = "";
    private static long asked, arrivedAt = -1;

    private Navigation() {
    }

    public static boolean active() {
        return active;
    }

    /** Start directions to (x, z). */
    public static void to(double x, double z, String destination) {
        active = true;
        toX = x;
        toZ = z;
        name = destination;
        route = null;
        arrivedAt = -1;
        note = "Working out the route...";
        ask();
    }

    public static void stop() {
        active = false;
        route = null;
    }

    private static void ask() {
        Minecraft mc = Minecraft.getInstance();
        asked = mc.level == null ? 0 : mc.level.getGameTime();
        PtmUkNetwork.CHANNEL.sendToServer(new RouteRequestMessage(toX, toZ, name));
    }

    public static void received(RouteMessage msg) {
        if (!active) {
            return;
        }
        route = msg.error().isEmpty() ? msg : null;
        note = msg.error();
    }

    /** Each tick: arrived? gone off the route? */
    static void tick(Minecraft mc) {
        if (!active || mc.player == null) {
            return;
        }
        long now = mc.level.getGameTime();
        double px = mc.player.getX(), pz = mc.player.getZ();
        if (arrivedAt >= 0) {
            if (now - arrivedAt > 100) {
                stop();
            }
            return;
        }
        if (Math.hypot(px - toX, pz - toZ) < ARRIVED) {
            arrivedAt = now;
            route = null;
            note = "You have arrived at " + name;
            return;
        }
        if (route != null && progress(px, pz)[1] > OFF_ROUTE && now - asked > 60) {
            note = "Rerouting...";
            ask();
        }
    }

    /** Where you are along the route: {the stretch you're on, how far off it you are, how far along it from the start}. */
    private static double[] progress(double px, double pz) {
        float[] x = route.x(), z = route.z();
        double best = Double.MAX_VALUE, along = 0, bestAlong = 0;
        int seg = 0;
        for (int i = 0; i + 1 < x.length; i++) {
            double dx = x[i + 1] - x[i], dz = z[i + 1] - z[i], l2 = dx * dx + dz * dz, len = Math.sqrt(l2);
            double t = l2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - x[i]) * dx + (pz - z[i]) * dz) / l2));
            double d = Math.hypot(px - (x[i] + dx * t), pz - (z[i] + dz * t));
            if (d < best) {
                best = d;
                seg = i;
                bestAlong = along + len * t;
            }
            along += len;
        }
        return new double[]{seg, best, bestAlong};
    }

    /** How far along the route point i is. */
    private static double alongTo(int index) {
        double along = 0;
        for (int i = 0; i < index && i + 1 < route.x().length; i++) {
            along += Math.hypot(route.x()[i + 1] - route.x()[i], route.z()[i + 1] - route.z()[i]);
        }
        return along;
    }

    /** The route as a blue line, through sx/sz (world to screen); width in screen pixels. */
    static void drawRoute(GuiGraphics g, DoubleUnaryOperator sx, DoubleUnaryOperator sz, float width) {
        if (!active || route == null) {
            return;
        }
        int n = route.x().length;
        float[] x = new float[n], y = new float[n];
        for (int i = 0; i < n; i++) {
            x[i] = (float) sx.applyAsDouble(route.x()[i]);
            y[i] = (float) sz.applyAsDouble(route.z()[i]);
        }
        MapDraw.line(g, x, y, width + 2, 0xFF0B3D91);
        MapDraw.line(g, x, y, width, 0xFF4A90F2);
    }

    /** The banner across the top: the next turn and how far to it, then how far to go. */
    static void hud(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (!active || mc.options.hideGui || mc.player == null || mc.screen instanceof WorldMapScreen) {
            return;
        }
        String top, under = "";
        if (route == null) {
            top = note;
            if (arrivedAt < 0 && !note.startsWith("Working") && !note.startsWith("Rerouting")) {
                // no road route: point the way as the crow flies
                double dx = toX - mc.player.getX(), dz = toZ - mc.player.getZ();
                under = name + ": " + Math.round(Math.hypot(dx, dz)) + " blocks " + com.ptmuk.map.Directions.compass(dx, dz);
            }
        } else {
            double[] at = progress(mc.player.getX(), mc.player.getZ());
            RouteMessage.Step next = null;
            for (RouteMessage.Step s : route.steps()) {
                if (s.index() > at[0]) {
                    next = s;
                    break;
                }
            }
            if (next == null) {
                next = route.steps().get(route.steps().size() - 1);
            }
            int to = (int) Math.max(0, alongTo(next.index()) - at[2]);
            top = (to > 5 ? "In " + roundTo(to) + " blocks, " + lower(next.text()) : next.text());
            under = Math.round(Math.max(0, alongTo(route.x().length - 1) - at[2])) + " blocks to " + name;
        }
        int w = Math.max(mc.font.width(top), mc.font.width(under)) + 16, x0 = width / 2 - w / 2;
        g.fill(x0, 4, x0 + w, under.isEmpty() ? 18 : 30, 0xC0123A6E);
        g.drawCenteredString(mc.font, top, width / 2, 8, 0xFFFFFF);
        if (!under.isEmpty()) {
            g.drawCenteredString(mc.font, under, width / 2, 19, 0xBFD4F0);
        }
    }

    private static int roundTo(int blocks) {
        return blocks < 50 ? Math.round(blocks / 5f) * 5 : Math.round(blocks / 10f) * 10;
    }

    private static String lower(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}
