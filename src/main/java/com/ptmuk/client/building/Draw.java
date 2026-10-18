package com.ptmuk.client.building;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/** Flat colour boxes, rectangles, pictures, text and LED digits for block entity renderers. */
final class Draw {
    /** Seven-segment patterns for 0-9, bits a..g. */
    private static final int[] SEGMENTS = {0x3F, 0x06, 0x5B, 0x4F, 0x66, 0x6D, 0x7D, 0x07, 0x7F, 0x6F};
    /** Width of the decimal point's cell, as a share of a digit's. */
    private static final float DOT_CELL = 0.4f;

    private Draw() {
    }

    static int shade(int argb, float f) {
        int r = Math.min(255, (int) (((argb >> 16) & 255) * f));
        int g = Math.min(255, (int) (((argb >> 8) & 255) * f));
        int b = Math.min(255, (int) ((argb & 255) * f));
        return (argb & 0xFF000000) | r << 16 | g << 8 | b;
    }

    static boolean dark(int argb) {
        int r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        return r * 0.299 + g * 0.587 + b * 0.114 < 140;
    }

    /** A rectangle facing +z at depth z. */
    static void rect(PoseStack pose, MultiBufferSource buffers, float x0, float y0, float x1, float y1, float z, int argb, int light) {
        Matrix4f m = pose.last().pose();
        VertexConsumer vc = com.ptmuk.client.FlatQuads.of(buffers, pose);
        int a = argb >>> 24, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        vc.vertex(m, x0, y0, z).color(r, g, b, a).uv2(light).endVertex();
        vc.vertex(m, x1, y0, z).color(r, g, b, a).uv2(light).endVertex();
        vc.vertex(m, x1, y1, z).color(r, g, b, a).uv2(light).endVertex();
        vc.vertex(m, x0, y1, z).color(r, g, b, a).uv2(light).endVertex();
    }

    /** A closed box with outward faces, lightly shaded per side. */
    static void box(PoseStack pose, MultiBufferSource buffers, float x0, float y0, float z0, float x1, float y1, float z1,
                    int argb, int light) {
        Matrix4f m = pose.last().pose();
        VertexConsumer vc = com.ptmuk.client.FlatQuads.of(buffers, pose);
        float[][][] faces = {
                {{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}}, {{x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}},
                {{x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}, {x0, y1, z0}}, {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}},
                {{x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}}, {{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}}};
        float[] shades = {1f, 0.8f, 1.08f, 0.6f, 0.9f, 0.85f};
        int a = argb >>> 24;
        for (int i = 0; i < 6; i++) {
            int c = shade(argb, shades[i]);
            for (float[] v : faces[i]) {
                vc.vertex(m, v[0], v[1], v[2]).color((c >> 16) & 255, (c >> 8) & 255, c & 255, a).uv2(light).endVertex();
            }
        }
    }

    /** A picture fitted inside the box x0..x1, y0..y1 (keeping its shape), facing +z. */
    static void image(PoseStack pose, MultiBufferSource buffers, CustomImages.Image img, float x0, float y0, float x1, float y1,
                      float z, int light) {
        float bw = x1 - x0, bh = y1 - y0;
        float aspect = img.width() / (float) Math.max(1, img.height());
        float w = bw, h = bw / aspect;
        if (h > bh) {
            h = bh;
            w = bh * aspect;
        }
        float cx = (x0 + x1) / 2, cy = (y0 + y1) / 2;
        float ax = cx - w / 2, bx = cx + w / 2, ay = cy - h / 2, by = cy + h / 2;
        ResourceLocation tex = img.texture();
        Matrix4f m = pose.last().pose();
        VertexConsumer vc = buffers.getBuffer(RenderType.text(tex));
        vc.vertex(m, ax, ay, z).color(255, 255, 255, 255).uv(0, 1).uv2(light).endVertex();
        vc.vertex(m, bx, ay, z).color(255, 255, 255, 255).uv(1, 1).uv2(light).endVertex();
        vc.vertex(m, bx, by, z).color(255, 255, 255, 255).uv(1, 0).uv2(light).endVertex();
        vc.vertex(m, ax, by, z).color(255, 255, 255, 255).uv(0, 0).uv2(light).endVertex();
    }

    /** One line of text h high with its bottom at y, centred on cx (or starting at cx), shrunk to fit maxW. */
    static void text(PoseStack pose, MultiBufferSource buffers, Font font, String s, float cx, float y, float h, float maxW,
                     int colour, int light, float z, boolean centre) {
        if (s == null || s.isBlank()) {
            return;
        }
        Component c = Component.literal(s);
        float scale = h / 7f;
        float w = font.width(c) * scale;
        if (maxW > 0 && w > maxW) {
            scale *= maxW / w;
            w = maxW;
        }
        pose.pushPose();
        pose.translate(centre ? cx - w / 2 : cx, y + (h + 7 * scale) / 2, z);
        pose.scale(scale, -scale, scale);
        font.drawInBatch(c, 0, 0, colour, false, pose.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, light);
        pose.popPose();
    }

    /** A price on seven-segment LED digits filling x0..x1, y0..y1: digits, '.', '-' and spaces. */
    static void digits(PoseStack pose, MultiBufferSource buffers, String s, float x0, float y0, float x1, float y1, float z,
                       int on, int light) {
        int off = 0xFF000000 | shade(on, 0.13f) & 0xFFFFFF;
        float cells = 0;
        for (char ch : s.toCharArray()) {
            cells += ch == '.' ? DOT_CELL : 1;
        }
        cells = Math.max(cells, 1);
        float pitch = (x1 - x0) / cells;
        float w = pitch * 0.8f, h = y1 - y0, t = Math.min(w, h) * 0.16f;
        float x = x0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '.') {
                // the decimal point gets its own narrow cell, so it sits clear of the digits either side
                float cell = pitch * DOT_CELL, d = Math.min(cell * 0.75f, t * 1.8f), cx = x + cell / 2;
                rect(pose, buffers, cx - d / 2, y0, cx + d / 2, y0 + d, z, on, light);
                x += cell;
                continue;
            }
            int bits = ch >= '0' && ch <= '9' ? SEGMENTS[ch - '0'] : ch == '-' ? 0x40 : 0;
            float hm = y0 + h / 2, l = x + (pitch - w) / 2;
            float[][] seg = {
                    {l + t, y1 - t, l + w - t, y1}, {l + w - t, hm, l + w, y1 - t}, {l + w - t, y0 + t, l + w, hm},
                    {l + t, y0, l + w - t, y0 + t}, {l, y0 + t, l + t, hm}, {l, hm, l + t, y1 - t},
                    {l + t, hm - t / 2, l + w - t, hm + t / 2}};
            for (int k = 0; k < 7; k++) {
                boolean lit = (bits & (1 << k)) != 0;
                rect(pose, buffers, seg[k][0], seg[k][1], seg[k][2], seg[k][3], z, lit ? on : off, light);
            }
            x += pitch;
        }
    }
}
