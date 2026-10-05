package com.ptmuk.client.bus;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ptmuk.bus.BusLayout;
import com.ptmuk.bus.DoubleDeckerBus;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rinventor.ptm2.client.view.MirrorRenderer;
import com.rinventor.ptm2.dimension.digital.OpSystemUtil;
import com.rinventor.ptm2.engine.animation.util.RenderUtils;
import com.rinventor.ptm2.engine.animation.cache.GeoBone;
import com.rinventor.ptm2.engine.animation.model.GeoModel;
import com.rinventor.ptm2.engine.animation.renderer.GeoEntityRenderer;
import com.rinventor.ptm2.engine.bezier.utility.Pair;
import com.rinventor.ptm2.engine.graphics.EntityTextRenderer;
import com.rinventor.ptm2.engine.graphics.ScaledWorldLabel;
import java.util.List;
import java.util.Set;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * Renders a UK double decker and the text on it: the LED destination displays (front, nearside, rear
 * route number), the next-stop screens on each deck and the number plates. Each display bone's
 * pivot is the top-left corner of its screen as seen from the front of it.
 */
public class DoubleDeckerRenderer<T extends DoubleDeckerBus> extends GeoEntityRenderer<T> {
    /** LED displays: yellow-green, like the dot matrix blinds on London buses. */
    private static final int LED = 0xFFD8F040;
    private static final int SCREEN_BG = 0xFF0A2A6E;
    private static final int SCREEN_ROW = 0xFFF2F2F2;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int BLACK = 0xFF101010;

    /** Dev runs only (-Dptmuk.busDebug=true): fills the displays with sample text on buses without a route. */
    private static final boolean DEBUG = Boolean.getBoolean("ptmuk.busDebug");

    private final GeoModel<T> model = getGeoModel();

    public DoubleDeckerRenderer(EntityRendererProvider.Context context, String name) {
        this(context, name, name);
    }

    public DoubleDeckerRenderer(EntityRendererProvider.Context context, String name, String texture) {
        super(context, new DoubleDeckerModel<>(name, texture));
        this.shadowRadius = 1.2f;
    }

    @Override
    public void render(T bus, float entityYaw, float partialTick, PoseStack ps, MultiBufferSource buffers, int light) {
        float spin = (float) Math.toRadians(bus.wheelRotation % 360.0f);
        float steer = (float) -Math.toRadians(bus.strafe * 8.0f);
        for (String name : new String[]{"FrontLeftWheel", "FrontRightWheel", "BackLeftWheel", "BackRightWheel"}) {
            boolean front = name.startsWith("Front");
            model.getBone(name).ifPresent(b -> {
                b.setRotZ(-spin);
                b.setRotY(front ? steer : 0);
            });
        }
        // the wheel is modelled flat on its tilted column, so it turns about its own y axis
        model.getBone("SteeringWheel").ifPresent(b -> b.setRotY((float) Math.toRadians(-bus.strafe * 360.0f)));
        super.render(bus, entityYaw, partialTick, ps, buffers, light);
    }

    /** Bones holding the lit lamps: drawn full bright so lights glow at night. */
    static final Set<String> LIGHT_BONES = Set.of("FrontLights", "StopLights", "BackLights", "FrontLeftTurnSignal",
            "FrontRightTurnSignal", "BackLeftTurnSignal", "BackRightTurnSignal", "LeftTurnSignal", "RightTurnSignal", "BusStopping");
    static final int FULL_BRIGHT = 0xF000F0;

    @Override
    public void renderRecursively(PoseStack ps, T bus, GeoBone bone, RenderType renderType, MultiBufferSource buffers,
                                  VertexConsumer buffer, boolean isReRender, float partialTick, int light, int overlay,
                                  float red, float green, float blue, float alpha) {
        String name = bone.getName();
        if (LIGHT_BONES.contains(name)) {
            light = FULL_BRIGHT;
        }
        // working mirrors, as on PTM2's own buses: hide ours while the mirror view is drawn
        boolean mirrorBone = "Mirrors".equals(name) || "LeftMirror".equals(name) || "RightMirror".equals(name);
        boolean hide = MirrorRenderer.renderingMirror && mirrorBone && bus.equals(MirrorRenderer.renderingBus);
        boolean hidden = bone.isHidden();
        boolean childrenHidden = bone.isHidingChildren();
        if (hide) {
            bone.setHidden(true);
        }
        try {
            super.renderRecursively(ps, bus, bone, renderType, buffers, buffer, isReRender, partialTick, light, overlay, red, green, blue, alpha);
        } finally {
            if (hide) {
                bone.setHidden(hidden);
                bone.setChildrenHidden(childrenHidden);
            }
        }
        if (!isReRender && ("LeftMirror".equals(name) || "RightMirror".equals(name))) {
            ps.pushPose();
            RenderUtils.prepMatrixForBone(ps, bone);
            MirrorRenderer.draw(bus, ps, bone, buffers, ps.last().pose().determinant3x3() < 0.0f);
            ps.popPose();
        }
    }

    @Override
    protected boolean renderBoneText(PoseStack ps, T bus, GeoBone bone, MultiBufferSource buffers, int light) {
        String name = bone.getName();
        BusLayout layout = bus.layout();
        String number = bus.getLineNumber();
        number = "D".equals(number) ? "" : number;
        boolean lit = bus.ignition || DEBUG;
        if (DEBUG && number.isBlank()) {
            number = "96";
        }
        String dest = sample(bus.getDestination(), "Woolwich");
        switch (name) {
            case "Display1" -> {      // front: route number and destination
                if (!lit || !bus.outsideDisplays) return false;
                float[] d = layout.displayFront();
                if (number.isBlank() && (dest == null || dest.isBlank())) {
                    label(ps, buffers, "NOT IN SERVICE", d[0], d[1], 0, 0, LED, true, d[2]);
                } else if (layout.singleLineFront()) {
                    // destination on the left, big route number on the right
                    // (the offset runs right to left as seen from the front, checked in game)
                    label(ps, buffers, dest, d[0] * 0.7, d[1] * 0.5, d[0] * 0.26, d[1] * 0.25, LED, false, d[2]);
                    label(ps, buffers, number, d[0] * 0.22, d[1] * 0.8, d[0] * 0.02, d[1] * 0.1, LED, true, d[2]);
                } else {
                    // as on London blinds: via points small top left, route number big top right,
                    // destination big underneath
                    label(ps, buffers, via(bus), d[0] * 0.66, d[1] * 0.3, d[0] * 0.31, d[1] * 0.06, LED, false, d[2]);
                    label(ps, buffers, number, d[0] * 0.26, d[1] * 0.46, d[0] * 0.02, d[1] * 0.02, LED, true, d[2]);
                    label(ps, buffers, dest, d[0] * 0.9, d[1] * 0.44, d[0] * 0.05, d[1] * 0.52, LED, true, d[2]);
                }
                return true;
            }
            case "Display2" -> {      // nearside: number and via points
                if (!lit || !bus.outsideDisplays) return false;
                float[] d = layout.displaySide();
                label(ps, buffers, (number + " " + sample(bus.getSideDestination(), "Woolwich")).trim(), d[0], d[1], 0, 0, LED, true, d[2]);
                return true;
            }
            case "Display3" -> {      // rear route number
                if (!lit || !bus.outsideDisplays) return false;
                float[] d = layout.displayRear();
                label(ps, buffers, number, d[0], d[1], 0, 0, LED, true, d[2]);
                return true;
            }
            case "Display5", "Display6" -> {   // next stop screen, lower and upper deck
                if (!lit || !bus.insideDisplays) return false;
                float[] d = layout.displayInside();
                double w = d[0], rh = d[1] / 4.0;
                // offsets run right to left as seen from the seats: destination and stops on the left, clock and times on the right
                screenRow(ps, buffers, (number + " " + dest).trim(), w * 0.75, rh, w * 0.25, 0, WHITE, SCREEN_BG, d[2]);
                screenRow(ps, buffers, OpSystemUtil.currentClock(bus.level()), w * 0.25, rh, 0, 0, WHITE, SCREEN_BG, d[2]);
                List<Pair<String, String>> stops = bus.nextStopsWithTimes();
                for (int i = 0; i < 3; i++) {
                    Pair<String, String> p = stops.size() > i ? stops.get(i) : DEBUG ? Pair.of(new String[]{"Bexleyheath", "Dartford", "Woolwich"}[i], (i + 2) + " min") : null;
                    screenRow(ps, buffers, p != null ? p.getFirst() : "", w * 0.75, rh, w * 0.25, rh * (i + 1), BLACK, SCREEN_ROW, d[2]);
                    screenRow(ps, buffers, p != null ? p.getSecond() : "", w * 0.25, rh, 0, rh * (i + 1), BLACK, SCREEN_ROW, d[2]);
                }
                return true;
            }
            case "FrontID" -> {
                return com.rinventor.ptm2.client.AnimationUtils.garageNumber(ps, bus, bone, buffers, light, 12, 0.0, 0.02,
                        layout.displayFront()[2], 0.0f, layout.frontIdColour());
            }
            case "PlateFront" -> {
                EntityTextRenderer.drawStringC(bus.registrationPlate, false, 9, 0.0, -0.01, 0.02, layout.displayFront()[2], 0.0f,
                        ps, buffers, BLACK, light);
                return true;
            }
            case "PlateBack" -> {
                EntityTextRenderer.drawStringC(bus.registrationPlate, false, 9, 0.0, -0.01, 0.02, layout.displayRear()[2], 0.0f,
                        ps, buffers, BLACK, light);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** Via points for the blind: the next two stops, as London blinds show them. */
    private static String via(DoubleDeckerBus bus) {
        List<Pair<String, String>> stops = bus.nextStopsWithTimes();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(2, stops.size() - 1); i++) {
            sb.append(i > 0 ? "  " : "").append(stops.get(i).getFirst());
        }
        return sample(sb.toString(), "Bexleyheath  Dartford");
    }

    private static String sample(String text, String debugText) {
        return text == null || text.isBlank() ? (DEBUG ? debugText : "") : text;
    }

    /** A single LED line: right/down are offsets from the display's top-left corner, in blocks. */
    private static void label(PoseStack ps, MultiBufferSource buffers, String text, double w, double h, double right, double down,
                              int colour, boolean centre, float yaw) {
        if (sideways(ps, yaw)) {
            new ScaledWorldLabel(w, h, text, colour, centre).render(ps, buffers, 0xF000F0, right, -down, 0.0, 0.0f, 0.0f);
            return;
        }
        new ScaledWorldLabel(w, h, text, colour, centre).render(ps, buffers, 0xF000F0, right, -down, 0.0, yaw, 0.0f);
    }

    /**
     * In left-hand traffic worlds PTM2 mirrors the bus. ScaledWorldLabel undoes the mirror for
     * text facing the front or back, but text facing the side then ends up facing into the bus;
     * turning it round puts it back on the outside (checked in game).
     */
    private static boolean sideways(PoseStack ps, float yaw) {
        return yaw == 180.0f && ps.last().pose().determinant3x3() < 0.0f;
    }

    private static void screenRow(PoseStack ps, MultiBufferSource buffers, String text, double w, double h, double right, double down,
                                  int colour, int bg, float yaw) {
        new ScaledWorldLabel(w, h, text == null ? "" : text, colour, bg, false).render(ps, buffers, 0xF000F0, right, -down, 0.0, yaw, 0.0f);
    }
}
