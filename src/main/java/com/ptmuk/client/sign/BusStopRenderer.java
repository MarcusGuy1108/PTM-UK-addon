package com.ptmuk.client.sign;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.ptmuk.sign.BusStopBlock;
import com.ptmuk.sign.BusStopBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.joml.Matrix4f;

/**
 * London-style bus stop: silver pole with a red cap, stop letter disc on top, and a double-sided
 * flag: red header with a flat white block emblem and BUS STOP / REQUEST STOP, the stop name on
 * a dark blue band, "towards ..." and white route number tiles.
 */
public class BusStopRenderer implements BlockEntityRenderer<BusStopBlockEntity> {
    private static final int RED = 0xFFD8202A, NAVY = 0xFF27355A, PALE = 0xFFE6E8EA, WHITE = 0xFFF8F8F6, BLACK = 0xFF151515,
            SILVER = 0xFFB4B8BC, SILVER_DARK = 0xFF8C9094, FRAME = 0xFF5A5E62;
    private static final float POLE_TOP = 3.45f, PX0 = 0.07f, PX1 = 0.79f, PY0 = 1.95f, PY1 = 3.33f;

    private final Font font = Minecraft.getInstance().font;
    private MultiBufferSource buffers;
    private PoseStack pose;
    private int light;
    private float z;

    public BusStopRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(BusStopBlockEntity stop, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!(stop.getBlockState().getBlock() instanceof BusStopBlock)) {
            return;
        }
        this.buffers = buffers;
        this.pose = pose;
        this.light = LightTexture.pack(Math.max(LightTexture.block(light), 5), LightTexture.sky(light));
        Direction facing = stop.getBlockState().getValue(BusStopBlock.FACING);
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pole();
        if (!stop.letter.isBlank()) {
            for (int side = 0; side < 2; side++) {
                pose.pushPose();
                if (side == 1) {
                    pose.mulPose(Axis.YP.rotationDegrees(180));
                }
                letterDisc(stop.letter.strip());
                pose.popPose();
            }
        }
        // the flag is double sided: draw it again turned round its own centre line
        float cx = (PX0 + PX1) / 2;
        for (int side = 0; side < 2; side++) {
            pose.pushPose();
            if (side == 1) {
                pose.translate(cx, 0, 0);
                pose.mulPose(Axis.YP.rotationDegrees(180));
                pose.translate(-cx, 0, 0);
            }
            flag(stop);
            pose.popPose();
        }
        // brackets from the pole to the flag
        z = 0;
        for (float y : new float[]{PY0 + 0.12f, PY1 - 0.12f}) {
            box(0.04f, y - 0.02f, -0.02f, PX0, y + 0.02f, 0.02f, FRAME);
        }
        pose.popPose();
    }

    private void pole() {
        float h = 0.045f;
        box(-h, 0, -h, h, POLE_TOP, h, SILVER);
        box(-h - 0.005f, POLE_TOP, -h - 0.005f, h + 0.005f, POLE_TOP + 0.08f, h + 0.005f, RED);
        box(-h - 0.01f, 0, -h - 0.01f, h + 0.01f, 0.06f, h + 0.01f, SILVER_DARK);
    }

    private void letterDisc(String letter) {
        float cy = POLE_TOP + 0.26f, r = 0.17f;
        z = 0.052f;
        disc(0, cy, r, RED);
        z += 0.002f;
        text(letter, 0, cy - r * 0.6f, r * 1.2f, WHITE, true, 1f);
    }

    private void flag(BusStopBlockEntity stop) {
        float x0 = PX0, x1 = PX1, w = x1 - x0;
        z = 0.012f;
        rect(x0, PY0, x1, PY1, FRAME);
        z = 0.014f;
        float b = 0.015f;
        float iy1 = PY1 - b, ix0 = x0 + b, ix1 = x1 - b, iw = ix1 - ix0;
        // header: red with the block emblem and BUS STOP / REQUEST STOP
        float headH = 0.6f;
        rect(ix0, iy1 - headH, ix1, iy1, RED);
        z += 0.002f;
        emblem((ix0 + ix1) / 2, iy1 - 0.25f, 0.17f);
        text(stop.request ? "REQUEST STOP" : "BUS STOP", (ix0 + ix1) / 2, iy1 - headH + 0.04f, 0.085f, WHITE, true, iw * 0.9f);
        // thin white rule under the header, then the stop name on navy
        float y = iy1 - headH;
        rect(ix0, y - 0.01f, ix1, y, WHITE);
        y -= 0.01f;
        float nameH = 0.22f;
        rect(ix0, y - nameH, ix1, y, NAVY);
        text(stop.displayName(), (ix0 + ix1) / 2, y - nameH * 0.72f, 0.1f, WHITE, true, iw * 0.92f);
        y -= nameH;
        float towH = 0.2f;
        rect(ix0, y - towH, ix1, y, PALE);
        if (!stop.towards.isBlank()) {
            text("towards " + stop.towards, (ix0 + ix1) / 2, y - towH * 0.7f, 0.075f, 0xFF3A3E44, true, iw * 0.92f);
        }
        y -= towH;
        // route tiles
        rect(ix0, PY0 + b, ix1, y, 0xFFD5D8DA);
        String[] routes = stop.routes.trim().isEmpty() ? new String[0] : stop.routes.trim().split("[ ,]+");
        int n = Math.max(3, Math.min(6, routes.length));
        float gap = 0.012f, tw = (iw - gap * (n + 1)) / n, th = y - (PY0 + b) - gap * 2;
        for (int i = 0; i < n; i++) {
            float tx0 = ix0 + gap + i * (tw + gap);
            z += 0.001f;
            rect(tx0, PY0 + b + gap, tx0 + tw, y - gap, WHITE);
            if (i < routes.length) {
                text(routes[i], tx0 + tw / 2, PY0 + b + gap + th * 0.22f, th * 0.55f, BLACK, true, tw * 0.9f);
            }
        }
    }

    /** Flat isometric block: a white hexagon with the three inner edges cut in the background red. */
    private void emblem(float cx, float cy, float s) {
        float c = 0.866f * s;
        float[][] hex = {{cx, cy + s}, {cx + c, cy + s / 2}, {cx + c, cy - s / 2}, {cx, cy - s}, {cx - c, cy - s / 2}, {cx - c, cy + s / 2}};
        for (int i = 0; i < 6; i++) {
            float[] a = hex[i], bb = hex[(i + 1) % 6];
            quad(cx, cy, bb[0], bb[1], a[0], a[1], a[0], a[1], WHITE);
        }
        z += 0.002f;
        float t = s * 0.1f;
        line(cx, cy, cx, cy - s, t, RED);
        line(cx, cy, cx - c, cy + s / 2, t, RED);
        line(cx, cy, cx + c, cy + s / 2, t, RED);
    }

    // ------------------------------------------------------------------ drawing helpers

    private void text(String s, float cx, float y, float h, int colour, boolean centre, float maxW) {
        Component c = Component.literal(s).withStyle(Style.EMPTY.withFont(SignPainter.HEAVY));
        float scale = h / 7f;
        float w = font.width(c) * scale;
        if (w > maxW && maxW > 0) {
            scale *= maxW / w;
            w = maxW;
        }
        pose.pushPose();
        pose.translate(centre ? cx - w / 2 : cx, y + 7 * scale + (h - 7 * scale) / 2, z + 0.003f);
        pose.scale(scale, -scale, scale);
        font.drawInBatch(c, 0, 0, colour, false, pose.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, light);
        pose.popPose();
    }

    private VertexConsumer fill() {
        return buffers.getBuffer(RenderType.textBackground());
    }

    private void quad(float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy, int argb) {
        Matrix4f m = pose.last().pose();
        VertexConsumer vc = fill();
        int a = argb >>> 24, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        vc.vertex(m, ax, ay, z).color(r, g, b, a).uv2(light).endVertex();
        vc.vertex(m, bx, by, z).color(r, g, b, a).uv2(light).endVertex();
        vc.vertex(m, cx, cy, z).color(r, g, b, a).uv2(light).endVertex();
        vc.vertex(m, dx, dy, z).color(r, g, b, a).uv2(light).endVertex();
    }

    private void rect(float x0, float y0, float x1, float y1, int argb) {
        quad(x0, y0, x1, y0, x1, y1, x0, y1, argb);
    }

    private void line(float x0, float y0, float x1, float y1, float t, int argb) {
        float dx = x1 - x0, dy = y1 - y0, len = (float) Math.sqrt(dx * dx + dy * dy);
        float nx = -dy / len * t / 2, ny = dx / len * t / 2;
        quad(x0 - nx, y0 - ny, x1 - nx, y1 - ny, x1 + nx, y1 + ny, x0 + nx, y0 + ny, argb);
    }

    private void disc(float cx, float cy, float r, int argb) {
        int n = 20;
        for (int i = 0; i < n; i++) {
            double a0 = 2 * Math.PI * i / n, a1 = 2 * Math.PI * (i + 1) / n;
            quad(cx, cy, cx + (float) Math.cos(a0) * r, cy + (float) Math.sin(a0) * r,
                    cx + (float) Math.cos(a1) * r, cy + (float) Math.sin(a1) * r, cx, cy, argb);
        }
    }

    /** Closed box with outward faces, lightly shaded per side. */
    private void box(float x0, float y0, float z0, float x1, float y1, float z1, int argb) {
        Matrix4f m = pose.last().pose();
        VertexConsumer vc = fill();
        float[][][] faces = {
                {{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}}, {{x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}},
                {{x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}, {x0, y1, z0}}, {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}},
                {{x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}}, {{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}}};
        float[] shade = {1f, 0.8f, 1.08f, 0.6f, 0.9f, 0.85f};
        for (int i = 0; i < 6; i++) {
            int r = Math.min(255, (int) (((argb >> 16) & 255) * shade[i]));
            int g = Math.min(255, (int) (((argb >> 8) & 255) * shade[i]));
            int b = Math.min(255, (int) ((argb & 255) * shade[i]));
            for (float[] v : faces[i]) {
                vc.vertex(m, v[0], v[1], v[2]).color(r, g, b, 255).uv2(light).endVertex();
            }
        }
    }

    @Override
    public boolean shouldRenderOffScreen(BusStopBlockEntity stop) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
