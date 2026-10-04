package com.ptmuk.client.bus;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ptmuk.bus.ALX400;
import com.ptmuk.bus.ALX400Layout;
import com.rinventor.ptm2.dimension.digital.OpSystemUtil;
import com.rinventor.ptm2.engine.animation.cache.GeoBone;
import com.rinventor.ptm2.engine.animation.model.GeoModel;
import com.rinventor.ptm2.engine.animation.renderer.GeoEntityRenderer;
import com.rinventor.ptm2.engine.bezier.utility.Pair;
import com.rinventor.ptm2.engine.graphics.EntityTextRenderer;
import com.rinventor.ptm2.engine.graphics.ScaledWorldLabel;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * Renders the ALX400 and the text on it: the LED destination displays (front, nearside, rear
 * route number), the next-stop screens on each deck and the number plates. Each display bone's
 * pivot is the top-left corner of its screen as seen from the front of it.
 */
public class ALX400Renderer extends GeoEntityRenderer<ALX400> {
    /** LED displays: yellow-green, like the dot matrix blinds on London buses. */
    private static final int LED = 0xFFD8F040;
    private static final int SCREEN_BG = 0xFF0A2A6E;
    private static final int SCREEN_ROW = 0xFFF2F2F2;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int BLACK = 0xFF101010;

    private final GeoModel<ALX400> model = getGeoModel();
    private static final boolean DEBUG = true;
    private static long lastLog;

    public ALX400Renderer(EntityRendererProvider.Context context) {
        super(context, new ALX400Model());
        this.shadowRadius = 1.2f;
    }

    @Override
    public void render(ALX400 bus, float entityYaw, float partialTick, PoseStack ps, MultiBufferSource buffers, int light) {
        float spin = (float) Math.toRadians(bus.wheelRotation % 360.0f);
        float steer = (float) -Math.toRadians(bus.strafe * 8.0f);
        for (String name : new String[]{"FrontLeftWheel", "FrontRightWheel", "BackLeftWheel", "BackRightWheel"}) {
            boolean front = name.startsWith("Front");
            model.getBone(name).ifPresent(b -> {
                b.setRotZ(-spin);
                b.setRotY(front ? steer : 0);
            });
        }
        model.getBone("SteeringWheel").ifPresent(b -> b.setRotZ((float) Math.toRadians(bus.strafe * 360.0f)));
        super.render(bus, entityYaw, partialTick, ps, buffers, light);
    }

    @Override
    protected boolean renderBoneText(PoseStack ps, ALX400 bus, GeoBone bone, MultiBufferSource buffers, int light) {
        String name = bone.getName();
        if (DEBUG && name.startsWith("Display") && System.currentTimeMillis() - lastLog > 3000) {
            lastLog = System.currentTimeMillis();
            org.joml.Vector3f t = ps.last().pose().getTranslation(new org.joml.Vector3f());
            com.ptmuk.PtmUk.LOGGER.info("ALX400 text bone {} at {} ignition={} outside={} dest='{}' num='{}'", name, t, bus.ignition,
                    bus.outsideDisplays, bus.getDestination(), bus.getLineNumber());
        }
        if (DEBUG && ("Display6".equals(name) || "Display1".equals(name))) {
            int[] cols = {0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFF00};
            for (int k = 0; k < 4; k++) {
                new ScaledWorldLabel(0.6, 0.15, "Y" + (k * 90), cols[k], true).render(ps, buffers, 0xF000F0, 0, -0.16 * k, 0, k * 90f, 0f);
            }
            return true;
        }
        String number = bus.getLineNumber();
        number = "D".equals(number) ? "" : number;
        boolean lit = bus.ignition || DEBUG;
        if (DEBUG && number.isBlank()) {
            number = "96";
        }
        switch (name) {
            case "Display1" -> {      // front: route number and destination
                if (!lit || !bus.outsideDisplays) return false;
                float[] d = ALX400Layout.DISPLAY_FRONT;
                String dest = DEBUG ? "Woolwich" : bus.getDestination();
                if (number.isBlank() && (dest == null || dest.isBlank())) {
                    label(ps, buffers, "NOT IN SERVICE", d[0], d[1], 0, 0, LED, true, d[2]);
                } else {
                    label(ps, buffers, number, d[0] * 0.24, d[1], 0, 0, LED, true, d[2]);
                    label(ps, buffers, dest, d[0] * 0.76, d[1], d[0] * 0.24, 0, LED, true, d[2]);
                }
                return true;
            }
            case "Display2" -> {      // nearside: number and via points
                if (!lit || !bus.outsideDisplays) return false;
                float[] d = ALX400Layout.DISPLAY_SIDE;
                label(ps, buffers, (number + " " + (DEBUG ? "Woolwich" : bus.getSideDestination())).trim(), d[0], d[1], 0, 0, LED, true, d[2]);
                return true;
            }
            case "Display3" -> {      // rear route number
                if (!lit || !bus.outsideDisplays) return false;
                float[] d = ALX400Layout.DISPLAY_REAR;
                label(ps, buffers, number, d[0], d[1], 0, 0, LED, true, d[2]);
                return true;
            }
            case "Display5", "Display6" -> {   // next stop screen, lower and upper deck
                if (!lit || !bus.insideDisplays) return false;
                float[] d = ALX400Layout.DISPLAY_INSIDE;
                double w = d[0], rh = d[1] / 4.0;
                screenRow(ps, buffers, (number + " " + (DEBUG ? "Woolwich" : bus.getDestination())).trim(), w * 0.75, rh, 0, 0, WHITE, SCREEN_BG, d[2]);
                screenRow(ps, buffers, OpSystemUtil.currentClock(bus.level()), w * 0.25, rh, w * 0.75, 0, WHITE, SCREEN_BG, d[2]);
                List<Pair<String, String>> stops = bus.nextStopsWithTimes();
                for (int i = 0; i < 3; i++) {
                    Pair<String, String> p = stops.size() > i ? stops.get(i) : DEBUG ? Pair.of(new String[]{"Bexleyheath", "Dartford", "Woolwich"}[i], (i + 2) + " min") : null;
                    screenRow(ps, buffers, p != null ? p.getFirst() : "", w * 0.75, rh, 0, rh * (i + 1), BLACK, SCREEN_ROW, d[2]);
                    screenRow(ps, buffers, p != null ? p.getSecond() : "", w * 0.25, rh, w * 0.75, rh * (i + 1), BLACK, SCREEN_ROW, d[2]);
                }
                return true;
            }
            case "PlateFront" -> {
                EntityTextRenderer.drawStringC(bus.registrationPlate, false, 9, 0.0, -0.01, 0.02, ALX400Layout.DISPLAY_FRONT[2], 0.0f,
                        ps, buffers, BLACK, light);
                return true;
            }
            case "PlateBack" -> {
                EntityTextRenderer.drawStringC(bus.registrationPlate, false, 9, 0.0, -0.01, 0.02, ALX400Layout.DISPLAY_REAR[2], 0.0f,
                        ps, buffers, BLACK, light);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** A single LED line: right/down are offsets from the display's top-left corner, in blocks. */
    private static void label(PoseStack ps, MultiBufferSource buffers, String text, double w, double h, double right, double down,
                              int colour, boolean centre, float yaw) {
        new ScaledWorldLabel(w, h, text, colour, centre).render(ps, buffers, 0xF000F0, right, -down, 0.0, yaw, 0.0f);
    }

    private static void screenRow(PoseStack ps, MultiBufferSource buffers, String text, double w, double h, double right, double down,
                                  int colour, int bg, float yaw) {
        new ScaledWorldLabel(w, h, text == null ? "" : text, colour, bg, false).render(ps, buffers, 0xF000F0, right, -down, 0.0, yaw, 0.0f);
    }
}
