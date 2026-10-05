package com.ptmuk.client.sign;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.ptmuk.sign.CountdownSignBlock;
import com.ptmuk.sign.CountdownSignBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;

/**
 * Countdown sign: a black dot-matrix panel in a grey case with amber text, as at London bus
 * stops. Three rows of "n  route  destination  x min" and the stop name and clock below. The
 * panel is double sided; it stands on a stem, or hangs from rods when there is a roof above it
 * and nothing below.
 */
public class CountdownSignRenderer implements BlockEntityRenderer<CountdownSignBlockEntity> {
    private static final int CASE = 0xFF2E3034, CASE_EDGE = 0xFF45484D, FACE = 0xFF080809, AMBER = 0xFFFFB21E,
            AMBER_DIM = 0xFFB87A10, STEM = 0xFF8C9094;
    private static final float X0 = -0.62f, X1 = 0.62f, Y0 = 0.32f, Y1 = 0.84f, D = 0.05f;

    private final Font font = Minecraft.getInstance().font;
    private MultiBufferSource buffers;
    private PoseStack pose;
    private int light;
    private float z;

    public CountdownSignRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CountdownSignBlockEntity sign, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!(sign.getBlockState().getBlock() instanceof CountdownSignBlock)) {
            return;
        }
        this.buffers = buffers;
        this.pose = pose;
        this.light = light;
        Direction facing = sign.getBlockState().getValue(CountdownSignBlock.FACING);
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        Level level = sign.getLevel();
        boolean hang = level != null && level.getBlockState(sign.getBlockPos().below()).isAir()
                && !level.getBlockState(sign.getBlockPos().above()).isAir();
        z = 0;
        if (hang) {
            box(-0.47f, Y1, -0.015f, -0.44f, 1.0f, 0.015f, STEM);
            box(0.44f, Y1, -0.015f, 0.47f, 1.0f, 0.015f, STEM);
        } else {
            box(-0.04f, 0, -0.04f, 0.04f, Y0, 0.04f, STEM);
        }
        box(X0, Y0, -D, X1, Y1, D, CASE);
        box(X0 - 0.01f, Y1, -D - 0.01f, X1 + 0.01f, Y1 + 0.025f, D + 0.01f, CASE_EDGE);
        this.light = LightTexture.FULL_BRIGHT;
        for (int side = 0; side < 2; side++) {
            pose.pushPose();
            if (side == 1) {
                pose.mulPose(Axis.YP.rotationDegrees(180));
            }
            face(sign, level);
            pose.popPose();
        }
        pose.popPose();
    }

    private void face(CountdownSignBlockEntity sign, Level level) {
        z = D + 0.001f;
        float fx0 = X0 + 0.035f, fx1 = X1 - 0.035f, fy0 = Y0 + 0.035f, fy1 = Y1 - 0.035f;
        rect(fx0, fy0, fx1, fy1, FACE);
        float pad = 0.025f;
        float tx0 = fx0 + pad, tx1 = fx1 - pad;
        float rowH = (fy1 - fy0 - 2 * pad) / 4f;
        float h = rowH * 0.72f;
        z = D + 0.002f;
        if (!sign.linked) {
            text("Place at a", (tx0 + tx1) / 2, row(fy1, pad, rowH, 0), h, AMBER, true, tx1 - tx0);
            text("PTM2 bus stop", (tx0 + tx1) / 2, row(fy1, pad, rowH, 1), h, AMBER, true, tx1 - tx0);
        } else if (sign.rows.isEmpty()) {
            text("No buses due", (tx0 + tx1) / 2, row(fy1, pad, rowH, 1), h, AMBER, true, tx1 - tx0);
        } else {
            for (int i = 0; i < sign.rows.size(); i++) {
                String[] r = sign.rows.get(i);
                float y = row(fy1, pad, rowH, i);
                int minutes = parse(r[2]);
                String due = minutes <= 0 ? "due" : minutes + "min";
                float dueW = textWidth(due, h);
                text(due, tx1 - dueW, y, h, AMBER, false, 0);
                text(Integer.toString(i + 1), tx0, y, h, AMBER, false, 0);
                float routeX = tx0 + (tx1 - tx0) * 0.105f;
                text(r[0], routeX, y, h, AMBER, false, (tx1 - tx0) * 0.16f);
                float destX = tx0 + (tx1 - tx0) * 0.3f;
                text(r[1], destX, y, h, AMBER, false, tx1 - dueW - destX - 0.03f);
            }
        }
        float y = row(fy1, pad, rowH, 3);
        String clock = clock(level);
        float clockW = textWidth(clock, h);
        text(clock, tx1 - clockW, y, h, AMBER, false, 0);
        if (!sign.stopName.isBlank()) {
            text(sign.stopName, tx0, y, h, AMBER_DIM, false, tx1 - clockW - tx0 - 0.04f);
        }
    }

    private static float row(float top, float pad, float rowH, int i) {
        return top - pad - rowH * (i + 1) + rowH * 0.14f;
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** The world's time of day as HH:mm (Minecraft day starts at 06:00). */
    private static String clock(Level level) {
        if (level == null) {
            return "";
        }
        long t = Math.floorMod(level.getDayTime(), 24000L);
        int hours = (int) ((t / 1000 + 6) % 24);
        int minutes = (int) (t % 1000 * 60 / 1000);
        return String.format("%02d:%02d", hours, minutes);
    }

    // ------------------------------------------------------------------ drawing helpers

    private float textWidth(String s, float h) {
        return font.width(s) * h / 7f;
    }

    private void text(String s, float x, float y, float h, int colour, boolean centre, float maxW) {
        Component c = Component.literal(s);
        float scale = h / 7f;
        float w = font.width(c) * scale;
        if (w > maxW && maxW > 0) {
            scale *= maxW / w;
            w = maxW;
        }
        pose.pushPose();
        pose.translate(centre ? x - w / 2 : x, y + 7 * scale + (h - 7 * scale) / 2, z);
        pose.scale(scale, -scale, scale);
        font.drawInBatch(c, 0, 0, colour, false, pose.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, light);
        pose.popPose();
    }

    private VertexConsumer fill() {
        return buffers.getBuffer(RenderType.textBackground());
    }

    private void rect(float x0, float y0, float x1, float y1, int argb) {
        Matrix4f m = pose.last().pose();
        VertexConsumer vc = fill();
        int a = argb >>> 24, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        vc.vertex(m, x0, y0, z).color(r, g, b, a).uv2(light).endVertex();
        vc.vertex(m, x1, y0, z).color(r, g, b, a).uv2(light).endVertex();
        vc.vertex(m, x1, y1, z).color(r, g, b, a).uv2(light).endVertex();
        vc.vertex(m, x0, y1, z).color(r, g, b, a).uv2(light).endVertex();
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
    public boolean shouldRenderOffScreen(CountdownSignBlockEntity sign) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
