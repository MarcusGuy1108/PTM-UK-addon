package com.ptmuk.client.sign;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ptmuk.PtmUk;
import com.ptmuk.sign.DirectionSignBlockEntity;
import com.ptmuk.sign.SignDiagram;
import com.ptmuk.sign.SignScheme;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Draws a UK direction sign in block units: the panel spans x -w/2..w/2, y 0..h, front facing
 * +z. Layout follows the usual UK conventions: retroreflective border, optional header bar,
 * a junction diagram in the legend colour, destinations above / beside its arms, route
 * numbers in coloured patches.
 */
public final class SignPainter {
    static final ResourceLocation HEAVY = PtmUk.id("transport_heavy");
    static final ResourceLocation MEDIUM = PtmUk.id("transport_medium");
    private static final Pattern PATCH = Pattern.compile("\\[([^\\]]+)]|\\{([^}]+)}|<([^>]+)>");
    private static final Pattern ROUTE = Pattern.compile("\\(?[AB]\\d+(\\(M\\))?\\)?");
    private static final int GREEN = 0xFF00703C, BLUE = 0xFF0057A0, YELLOW = 0xFFFFCC00, WHITE = 0xFFF8F8F6, BLACK = 0xFF111111;

    private final PoseStack pose;
    private final MultiBufferSource buffers;
    private final int light;
    private final Font font = Minecraft.getInstance().font;
    private final SignScheme scheme;
    private float z;

    private SignPainter(PoseStack pose, MultiBufferSource buffers, int light, SignScheme scheme) {
        this.pose = pose;
        this.buffers = buffers;
        this.light = light;
        this.scheme = scheme;
    }

    /** legLength: distance from the panel bottom down to the ground (0 = no legs). */
    public static void paint(PoseStack pose, MultiBufferSource buffers, DirectionSignBlockEntity s, int light, float legLength,
                             float hangLength) {
        new SignPainter(pose, buffers, light, s.scheme).draw(s, legLength, hangLength);
    }

    private void draw(DirectionSignBlockEntity s, float legLength, float hangLength) {
        float w = s.width - 0.06f, h = s.height - 0.06f;
        float x0 = -w / 2, x1 = w / 2, y0 = 0.03f, y1 = y0 + h;
        float m = Math.min(w, h);
        float border = 0.025f + 0.02f * m;
        float radius = 0.04f + 0.05f * m;

        // back, edges and legs
        z = -0.02f;
        rectBack(x0, y0, x1, y1, 0xFF808488);
        edges(x0, y0, x1, y1, 0.04f, 0xFF6E7276);
        if (legLength > 0) {
            float[] xs = s.width >= 2 ? new float[]{x0 + w * 0.22f, x1 - w * 0.22f} : new float[]{0};
            for (float lx : xs) {
                post(lx, -0.07f, -legLength, y0 + h * 0.8f, 0.045f);
            }
        }
        if (hangLength > 0) {
            float[] xs = s.width >= 2 ? new float[]{x0 + w * 0.2f, x1 - w * 0.2f} : new float[]{0};
            for (float lx : xs) {
                post(lx, -0.07f, y1 - 0.3f, y1 + hangLength, 0.035f);
            }
        }

        // face: border, background
        z = 0.02f;
        roundRect(x0, y0, x1, y1, radius, scheme.border);
        z += 0.001f;
        roundRect(x0 + border, y0 + border, x1 - border, y1 - border, Math.max(0.01f, radius - border), scheme.background);
        z += 0.001f;

        float bx0 = x0 + border * 2, bx1 = x1 - border * 2, by0 = y0 + border * 2, by1 = y1 - border * 2;
        if (!s.header.isBlank()) {
            float band = Math.min(0.3f * (by1 - by0), 0.5f);
            rect(x0 + border, by1 - band - border * 0.5f, x1 - border, by1 - band + border * 0.5f, scheme.border);
            textBlock(List.of(s.header), bx0, by1 - band + border, bx1, by1, Align.CENTRE, band * 0.62f);
            by1 = by1 - band - border;
        }
        diagramAndText(s, bx0, by0, bx1, by1);
    }

    // ------------------------------------------------------------ layout

    enum Align { LEFT, CENTRE, RIGHT }

    private void diagramAndText(DirectionSignBlockEntity s, float bx0, float by0, float bx1, float by1) {
        float bw = bx1 - bx0, bh = by1 - by0;
        List<String> ahead = lines(s.ahead), left = lines(s.left), right = lines(s.right);
        SignDiagram d = s.diagram;
        if (d == SignDiagram.NONE) {
            List<String> all = new ArrayList<>(ahead);
            all.addAll(left);
            all.addAll(right);
            textBlock(all, bx0, by0, bx1, by1, Align.CENTRE, 0.5f);
            return;
        }
        int fg = scheme.legend;
        // one x-height for the whole sign, shrunk until every block of text fits
        float lh = Math.min(0.45f, Math.max(0.1f, Math.min(bw, bh) * 0.2f));
        if (d.isLanes()) {
            int n = d == SignDiagram.LANES_2 ? 2 : d == SignDiagram.LANES_3 ? 3 : 4;
            float arrowH = Math.min(bh * 0.4f, 0.9f);
            float t = Math.min(bw / n * 0.09f, arrowH * 0.12f);
            for (int i = 0; i < n; i++) {
                float cx = bx0 + (i + 0.5f) * bw / n;
                arrow(cx, by0 + arrowH, cx, by0 + bh * 0.03f, t, fg);
            }
            textBlock(ahead, bx0, by0 + arrowH + t * 2, bx1, by1, Align.CENTRE, 0.48f);
            return;
        }
        boolean ring = d == SignDiagram.ROUNDABOUT || d == SignDiagram.ROUNDABOUT_3;
        boolean up = d == SignDiagram.AHEAD || d == SignDiagram.CROSSROADS || d == SignDiagram.AHEAD_LEFT
                || d == SignDiagram.AHEAD_RIGHT || d == SignDiagram.ROUNDABOUT;
        boolean toLeft = d != SignDiagram.AHEAD && d != SignDiagram.RIGHT && d != SignDiagram.AHEAD_RIGHT;
        boolean toRight = d != SignDiagram.AHEAD && d != SignDiagram.LEFT && d != SignDiagram.AHEAD_LEFT;
        float cx = (bx0 + bx1) / 2;
        float aheadH = 0, cy = 0, a = 0, t = 0, sideTop = 0;
        for (int pass = 0; pass < 4; pass++) {
            aheadH = ahead.isEmpty() ? 0 : ahead.size() * lh * 1.12f + lh * 0.25f;
            float area = bh - aheadH;
            float size = Math.min(area, bw * 0.5f);
            a = size * 0.36f;
            t = size * 0.08f;
            cy = up ? by0 + area - a - t * 0.6f : by0 + area * 0.62f;
            sideTop = cy - (ring ? a * 0.42f : 0) - t * 1.3f;
            float fit = lh;
            int sideLines = Math.max(left.size(), right.size());
            if (sideLines > 0) {
                fit = Math.min(fit, (sideTop - by0) / (sideLines * 1.12f));
            }
            float sideW = bw / 2 - t * 1.6f;
            for (String line : left) {
                fit = Math.min(fit, lh * sideW / Math.max(1e-3f, lineWidth(line, lh)));
            }
            for (String line : right) {
                fit = Math.min(fit, lh * sideW / Math.max(1e-3f, lineWidth(line, lh)));
            }
            for (String line : ahead) {
                fit = Math.min(fit, lh * bw / Math.max(1e-3f, lineWidth(line, lh)));
            }
            if (fit >= lh * 0.99f) {
                break;
            }
            lh = fit;
        }
        float bottom = by0;
        float rr = a * 0.42f;
        if (ring) {
            ring(cx, cy, rr, t, fg);
            line(cx, bottom, cx, cy - rr, t, fg);
            if (up) {
                arrow(cx, cy + rr, cx, cy + a + t, t, fg);
            }
            arrow(cx - rr, cy, cx - a - t, cy, t, fg);
            arrow(cx + rr, cy, cx + a + t, cy, t, fg);
        } else {
            line(cx, bottom, cx, cy + t / 2, t, fg);
            if (up) {
                arrow(cx, cy, cx, cy + a + t, t, fg);
            }
            if (toLeft) {
                arrow(cx + t / 2, cy, cx - a - t, cy, t, fg);
            }
            if (toRight) {
                arrow(cx - t / 2, cy, cx + a + t, cy, t, fg);
            }
        }
        float gap = t * 1.6f;
        fixedBlock(ahead, bx0, by1 - aheadH + lh * 0.15f, bx1, by1, Align.CENTRE, lh);
        fixedTop(left, bx0, sideTop, cx - gap, Align.RIGHT, lh);
        fixedTop(right, cx + gap, sideTop, bx1, Align.LEFT, lh);
    }

    /** Lines at a fixed height, centred vertically in the box. */
    private void fixedBlock(List<String> lines, float x0, float y0, float x1, float y1, Align align, float lh) {
        float total = lines.size() * lh * 1.12f;
        fixedTop(lines, x0, (y0 + y1) / 2 + total / 2, x1, align, lh);
    }

    /** Lines at a fixed height, hanging down from top. */
    private void fixedTop(List<String> lines, float x0, float top, float x1, Align align, float lh) {
        float y = top - lh;
        for (String line : lines) {
            float lw = lineWidth(line, lh);
            float x = switch (align) {
                case LEFT -> x0;
                case RIGHT -> x1 - lw;
                default -> (x0 + x1) / 2 - lw / 2;
            };
            drawLine(line, x, y, lh);
            y -= lh * 1.12f;
        }
    }

    private static List<String> lines(String s) {
        List<String> out = new ArrayList<>();
        for (String line : s.split("[\n|]")) {
            if (!line.isBlank()) {
                out.add(line.strip());
            }
        }
        return out;
    }

    /** Lines fitted into a box: as large as fits up to maxLine block units per line. */
    private void textBlock(List<String> lines, float x0, float y0, float x1, float y1, Align align, float maxLine) {
        if (lines.isEmpty() || x1 - x0 < 0.05f || y1 - y0 < 0.05f) {
            return;
        }
        float lh = Math.min(maxLine, (y1 - y0) / (lines.size() * 1.12f));
        for (String line : lines) {
            float wNeeded = lineWidth(line, lh);
            if (wNeeded > x1 - x0) {
                lh *= (x1 - x0) / wNeeded;
            }
        }
        float total = lines.size() * lh * 1.12f;
        float y = (y0 + y1) / 2 + total / 2 - lh;
        for (String line : lines) {
            float lw = lineWidth(line, lh);
            float x = switch (align) {
                case LEFT -> x0;
                case RIGHT -> x1 - lw;
                default -> (x0 + x1) / 2 - lw / 2;
            };
            drawLine(line, x, y, lh);
            y -= lh * 1.12f;
        }
    }

    private record Segment(String text, int fg, int bg, int border) {
    }

    private List<Segment> segments(String line) {
        List<Segment> out = new ArrayList<>();
        Matcher m = PATCH.matcher(line);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) {
                plain(line.substring(last, m.start()), out);
            }
            if (m.group(1) != null) {
                out.add(new Segment(m.group(1), YELLOW, GREEN, 0));
            } else if (m.group(2) != null) {
                out.add(new Segment(m.group(2), WHITE, BLUE, 0));
            } else {
                out.add(new Segment(m.group(3), BLACK, WHITE, BLACK));
            }
            last = m.end();
        }
        if (last < line.length()) {
            plain(line.substring(last), out);
        }
        return out;
    }

    /** Plain text; on green signs, A/B route numbers are yellow. */
    private void plain(String text, List<Segment> out) {
        if (scheme != SignScheme.PRIMARY) {
            out.add(new Segment(text, scheme.legend, 0, 0));
            return;
        }
        Matcher r = ROUTE.matcher(text);
        int last = 0;
        while (r.find()) {
            if (r.start() > last) {
                out.add(new Segment(text.substring(last, r.start()), scheme.legend, 0, 0));
            }
            out.add(new Segment(r.group(), YELLOW, 0, 0));
            last = r.end();
        }
        if (last < text.length()) {
            out.add(new Segment(text.substring(last), scheme.legend, 0, 0));
        }
    }

    private Component comp(String text, int fg) {
        boolean heavy = fg == BLACK || (scheme.heavy && fg == scheme.legend);
        return Component.literal(text).withStyle(Style.EMPTY.withFont(heavy ? HEAVY : MEDIUM));
    }

    private float scale(float lh) {
        return lh / font.lineHeight;
    }

    private float lineWidth(String line, float lh) {
        float w = 0;
        for (Segment seg : segments(line)) {
            w += font.width(comp(seg.text, seg.fg)) * scale(lh) + (seg.bg != 0 ? lh * 0.4f : 0);
        }
        return w;
    }

    private void drawLine(String line, float x, float y, float lh) {
        float s = scale(lh);
        for (Segment seg : segments(line)) {
            Component c = comp(seg.text, seg.fg);
            float tw = font.width(c) * s;
            float pad = seg.bg != 0 ? lh * 0.2f : 0;
            if (seg.bg != 0) {
                float zz = z;
                z += 0.001f;
                if (seg.border != 0) {
                    roundRect(x, y - lh * 0.12f, x + tw + pad * 2, y + lh * 0.98f, lh * 0.12f, seg.border);
                    z += 0.001f;
                    roundRect(x + lh * 0.05f, y - lh * 0.07f, x + tw + pad * 2 - lh * 0.05f, y + lh * 0.93f, lh * 0.09f, seg.bg);
                } else {
                    roundRect(x, y - lh * 0.12f, x + tw + pad * 2, y + lh * 0.98f, lh * 0.12f, seg.bg);
                }
                z = zz;
            }
            pose.pushPose();
            pose.translate(x + pad, y + lh * 0.95f, z + 0.004f);
            pose.scale(s, -s, s);
            font.drawInBatch(c, 0, 0, seg.fg, false, pose.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, light);
            pose.popPose();
            x += tw + pad * 2;
        }
    }

    // ------------------------------------------------------------ shapes

    /** Fetched per shape: drawing text in between switches buffers and ends the previous one. */
    private VertexConsumer fill() {
        return buffers.getBuffer(RenderType.textBackground());
    }

    private void quad(float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy, int argb) {
        Matrix4f mat = pose.last().pose();
        VertexConsumer fill = fill();
        int a = argb >>> 24, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        fill.vertex(mat, ax, ay, z).color(r, g, b, a).uv2(light).endVertex();
        fill.vertex(mat, bx, by, z).color(r, g, b, a).uv2(light).endVertex();
        fill.vertex(mat, cx, cy, z).color(r, g, b, a).uv2(light).endVertex();
        fill.vertex(mat, dx, dy, z).color(r, g, b, a).uv2(light).endVertex();
    }

    private void rect(float x0, float y0, float x1, float y1, int argb) {
        quad(x0, y0, x1, y0, x1, y1, x0, y1, argb);
    }

    private void rectBack(float x0, float y0, float x1, float y1, int argb) {
        quad(x0, y0, x0, y1, x1, y1, x1, y0, argb);
    }

    private void roundRect(float x0, float y0, float x1, float y1, float r, int argb) {
        r = Math.min(r, Math.min(x1 - x0, y1 - y0) / 2);
        rect(x0 + r, y0, x1 - r, y1, argb);
        rect(x0, y0 + r, x0 + r, y1 - r, argb);
        rect(x1 - r, y0 + r, x1, y1 - r, argb);
        corner(x0 + r, y0 + r, r, 180, argb);
        corner(x1 - r, y0 + r, r, 270, argb);
        corner(x1 - r, y1 - r, r, 0, argb);
        corner(x0 + r, y1 - r, r, 90, argb);
    }

    private void corner(float cx, float cy, float r, float startDeg, int argb) {
        int steps = 6;
        for (int i = 0; i < steps; i++) {
            double a0 = Math.toRadians(startDeg + 90.0 * i / steps), a1 = Math.toRadians(startDeg + 90.0 * (i + 1) / steps);
            float px0 = cx + (float) Math.cos(a0) * r, py0 = cy + (float) Math.sin(a0) * r;
            float px1 = cx + (float) Math.cos(a1) * r, py1 = cy + (float) Math.sin(a1) * r;
            quad(cx, cy, px0, py0, px1, py1, cx, cy, argb);
        }
    }

    /** Thick line from (x0,y0) to (x1,y1). */
    private void line(float x0, float y0, float x1, float y1, float t, int argb) {
        float dx = x1 - x0, dy = y1 - y0, len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-4f) {
            return;
        }
        float nx = -dy / len * t / 2, ny = dx / len * t / 2;
        quad(x0 - nx, y0 - ny, x1 - nx, y1 - ny, x1 + nx, y1 + ny, x0 + nx, y0 + ny, argb);
    }

    /** Line with a UK-style pointed arrowhead at (x1,y1). */
    private void arrow(float x0, float y0, float x1, float y1, float t, int argb) {
        float dx = x1 - x0, dy = y1 - y0, len = (float) Math.sqrt(dx * dx + dy * dy);
        float ux = dx / len, uy = dy / len;
        float head = t * 2.4f;
        line(x0, y0, x1 - ux * head * 0.9f, y1 - uy * head * 0.9f, t, argb);
        float bx = x1 - ux * head, by = y1 - uy * head;
        float nx = -uy * t * 1.5f, ny = ux * t * 1.5f;
        quad(bx - nx, by - ny, x1, y1, x1, y1, bx + nx, by + ny, argb);
        quad(bx - nx, by - ny, bx + nx, by + ny, x1, y1, x1, y1, argb);
    }

    private void ring(float cx, float cy, float r, float t, int argb) {
        int steps = 28;
        for (int i = 0; i < steps; i++) {
            double a0 = 2 * Math.PI * i / steps, a1 = 2 * Math.PI * (i + 1) / steps;
            float ri = r - t / 2, ro = r + t / 2;
            quad(cx + (float) Math.cos(a0) * ri, cy + (float) Math.sin(a0) * ri,
                    cx + (float) Math.cos(a0) * ro, cy + (float) Math.sin(a0) * ro,
                    cx + (float) Math.cos(a1) * ro, cy + (float) Math.sin(a1) * ro,
                    cx + (float) Math.cos(a1) * ri, cy + (float) Math.sin(a1) * ri, argb);
        }
    }

    private void edges(float x0, float y0, float x1, float y1, float depth, int argb) {
        float zz = z;
        // top and sides of the plate, drawn as thin quads facing out
        Matrix4f mat = pose.last().pose();
        VertexConsumer fill = fill();
        int a = argb >>> 24, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        float[][] sides = {{x0, y1, x1, y1}, {x1, y1, x1, y0}, {x1, y0, x0, y0}, {x0, y0, x0, y1}};
        for (float[] e : sides) {
            fill.vertex(mat, e[0], e[1], zz).color(r, g, b, a).uv2(light).endVertex();
            fill.vertex(mat, e[2], e[3], zz).color(r, g, b, a).uv2(light).endVertex();
            fill.vertex(mat, e[2], e[3], zz + depth).color(r, g, b, a).uv2(light).endVertex();
            fill.vertex(mat, e[0], e[1], zz + depth).color(r, g, b, a).uv2(light).endVertex();
        }
    }

    /** Square galvanised post from y0 to y1 at (x, z). */
    private void post(float x, float pz, float y0, float y1, float half) {
        Matrix4f mat = pose.last().pose();
        VertexConsumer fill = fill();
        int[] shades = {0xFFA9ADB1, 0xFF8E9296, 0xFF9DA1A5, 0xFF84888C};
        float[][] corners = {{x - half, pz - half}, {x + half, pz - half}, {x + half, pz + half}, {x - half, pz + half}};
        for (int i = 0; i < 4; i++) {
            float[] c0 = corners[i], c1 = corners[(i + 1) % 4];
            int argb = shades[i];
            int a = argb >>> 24, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
            fill.vertex(mat, c0[0], y0, c0[1]).color(r, g, b, a).uv2(light).endVertex();
            fill.vertex(mat, c0[0], y1, c0[1]).color(r, g, b, a).uv2(light).endVertex();
            fill.vertex(mat, c1[0], y1, c1[1]).color(r, g, b, a).uv2(light).endVertex();
            fill.vertex(mat, c1[0], y0, c1[1]).color(r, g, b, a).uv2(light).endVertex();
        }
    }
}
