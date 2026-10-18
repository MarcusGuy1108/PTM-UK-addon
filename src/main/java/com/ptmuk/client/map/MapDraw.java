package com.ptmuk.client.map;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.ptmuk.map.MapData;
import java.util.function.DoubleUnaryOperator;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/** The map picture, the "you are here" arrow and markers, for both the full map and the minimap. */
final class MapDraw {
    private MapDraw() {
    }

    /**
     * The map round (vx, vz) at `scale` GUI pixels a block, reaching `reach` blocks each way; the pose
     * must already be at the middle of the view (turned, for the minimap).
     */
    static void regions(GuiGraphics g, MapStore store, double vx, double vz, float scale, double reach) {
        int r0x = Math.floorDiv((int) Math.floor(vx - reach), MapStore.REGION), r1x = Math.floorDiv((int) Math.ceil(vx + reach), MapStore.REGION);
        int r0z = Math.floorDiv((int) Math.floor(vz - reach), MapStore.REGION), r1z = Math.floorDiv((int) Math.ceil(vz + reach), MapStore.REGION);
        for (int rx = r0x; rx <= r1x; rx++) {
            for (int rz = r0z; rz <= r1z; rz++) {
                MapStore.Region r = store.region(rx, rz, false);
                if (r == null) {
                    continue;
                }
                g.pose().pushPose();
                g.pose().scale(scale, scale, 1);
                g.pose().translate((float) (rx * (double) MapStore.REGION - vx), (float) (rz * (double) MapStore.REGION - vz), 0);
                g.blit(r.id, 0, 0, 0, 0, MapStore.REGION, MapStore.REGION, MapStore.REGION, MapStore.REGION);
                g.pose().popPose();
            }
        }
        store.flush();
    }

    /** An arrowhead at (x, y) pointing `degrees` clockwise from up, with a dark edge. */
    static void arrow(GuiGraphics g, float x, float y, float degrees, float size, int fill) {
        g.flush();
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(x, y, 0);
        ps.mulPose(Axis.ZP.rotationDegrees(degrees));
        Matrix4f m = ps.last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        head(b, m, size * 1.4f, 0xFF101010);
        head(b, m, size, fill);
        BufferUploader.drawWithShader(b.end());
        RenderSystem.enableCull();
        ps.popPose();
    }

    private static void head(BufferBuilder b, Matrix4f m, float s, int argb) {
        float[][] pts = {{0, -s}, {s * 0.75f, s * 0.85f}, {0, s * 0.35f}, {0, -s}, {0, s * 0.35f}, {-s * 0.75f, s * 0.85f}};
        int a = argb >>> 24, r = argb >> 16 & 255, gr = argb >> 8 & 255, bl = argb & 255;
        for (float[] p : pts) {
            b.vertex(m, p[0], p[1], 0).color(r, gr, bl, a).endVertex();
        }
    }

    /** A round-ish marker. */
    static void dot(GuiGraphics g, float x, float y, int size, int argb) {
        int ix = Math.round(x), iy = Math.round(y);
        g.fill(ix - size - 1, iy - size, ix + size + 1, iy + size, 0xFF101010);
        g.fill(ix - size, iy - size - 1, ix + size, iy + size + 1, 0xFF101010);
        g.fill(ix - size, iy - size + 1, ix + size, iy + size - 1, argb);
        g.fill(ix - size + 1, iy - size, ix + size - 1, iy + size, argb);
    }

    /** The colour and letter for each kind of place. */
    static int colour(MapData.Kind kind) {
        return switch (kind) {
            case SHOP -> 0xFF8E44AD;
            case FUEL -> 0xFF1E8449;
            case STOP -> 0xFFD32F2F;
            case STATION -> 0xFF1F3A93;
            case PARKING -> 0xFF2E86DE;
        };
    }

    static String letter(MapData.Kind kind) {
        return switch (kind) {
            case SHOP -> "\u00A3";
            case FUEL -> "F";
            case STOP -> "B";
            case STATION -> "S";
            case PARKING -> "P";
        };
    }

    /** A place's marker: a little coloured square with its letter. */
    static void place(GuiGraphics g, Font font, float x, float y, MapData.Kind kind) {
        int ix = Math.round(x), iy = Math.round(y);
        g.fill(ix - 5, iy - 5, ix + 5, iy + 5, 0xFFFFFFFF);
        g.fill(ix - 4, iy - 4, ix + 4, iy + 4, colour(kind));
        g.drawCenteredString(font, letter(kind), ix, iy - 3, 0xFFFFFF);
    }

    /** A road's name along it every so often, kept the right way up; sx/sz turn world into screen. */
    static void roadName(GuiGraphics g, Font font, MapData.Road r, DoubleUnaryOperator sx, DoubleUnaryOperator sz) {
        int n = r.x().length;
        float[] px = new float[n], pz = new float[n];
        float total = 0;
        for (int i = 0; i < n; i++) {
            px[i] = (float) sx.applyAsDouble(r.x()[i]);
            pz[i] = (float) sz.applyAsDouble(r.z()[i]);
            if (i > 0) {
                total += (float) Math.hypot(px[i] - px[i - 1], pz[i] - pz[i - 1]);
            }
        }
        int w = font.width(r.name());
        if (total < 24) {
            return;                                         // too small to see at this zoom
        }
        float step = 320;
        for (float at = total < step ? total / 2 : step / 2; at < total; at += step) {
            float d = at;
            for (int i = 1; i < n; i++) {
                float len = (float) Math.hypot(px[i] - px[i - 1], pz[i] - pz[i - 1]);
                if (d <= len || i == n - 1) {
                    float t = len == 0 ? 0 : Math.min(1, d / len);
                    float x = px[i - 1] + (px[i] - px[i - 1]) * t, y = pz[i - 1] + (pz[i] - pz[i - 1]) * t;
                    float angle = (float) Math.toDegrees(Math.atan2(pz[i] - pz[i - 1], px[i] - px[i - 1]));
                    if (angle > 90) {
                        angle -= 180;
                    } else if (angle < -90) {
                        angle += 180;
                    }
                    g.pose().pushPose();
                    g.pose().translate(x, y, 0);
                    g.pose().mulPose(Axis.ZP.rotationDegrees(angle));
                    g.fill(-w / 2 - 3, -6, w / 2 + 3, 5, 0xB0FFFFFF);
                    g.drawString(font, r.name(), -w / 2, -4, 0x202020, false);
                    g.pose().popPose();
                    break;
                }
                d -= len;
            }
        }
    }

    /** A thick line through the points (already on screen, or in the pose's units), as quads. */
    static void line(GuiGraphics g, float[] x, float[] y, float width, int argb) {
        if (x.length < 2) {
            return;
        }
        g.flush();
        Matrix4f m = g.pose().last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int a = argb >>> 24, r = argb >> 16 & 255, gr = argb >> 8 & 255, bl = argb & 255;
        float h = width / 2;
        for (int i = 1; i < x.length; i++) {
            float dx = x[i] - x[i - 1], dy = y[i] - y[i - 1], len = (float) Math.hypot(dx, dy);
            if (len < 1e-3f) {
                continue;
            }
            // a little past each end, so the joins don't show gaps
            float nx = -dy / len * h, ny = dx / len * h, ex = dx / len * h, ey = dy / len * h;
            b.vertex(m, x[i - 1] - ex + nx, y[i - 1] - ey + ny, 0).color(r, gr, bl, a).endVertex();
            b.vertex(m, x[i] + ex + nx, y[i] + ey + ny, 0).color(r, gr, bl, a).endVertex();
            b.vertex(m, x[i] + ex - nx, y[i] + ey - ny, 0).color(r, gr, bl, a).endVertex();
            b.vertex(m, x[i - 1] - ex - nx, y[i - 1] - ey - ny, 0).color(r, gr, bl, a).endVertex();
        }
        BufferUploader.drawWithShader(b.end());
        RenderSystem.enableCull();
    }
}
