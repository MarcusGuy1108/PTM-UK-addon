package com.ptmuk.client.map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.ptmuk.client.FlatQuads;
import com.ptmuk.map.NameplateBlock;
import com.ptmuk.map.NameplateBlockEntity;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

/** The plate: white with a black edge, the street name in black capitals, the district and postcode under it. */
public class NameplateRenderer implements BlockEntityRenderer<NameplateBlockEntity> {
    private static final float Y0 = 0.28f, Y1 = 0.78f, THICK = 0.05f;
    private static final int WHITE = 0xFFF4F4F2, BLACK = 0xFF141414, RED = 0xFFC8102E;

    private final Font font = Minecraft.getInstance().font;

    public NameplateRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(NameplateBlockEntity plate, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!(plate.getBlockState().getBlock() instanceof NameplateBlock)) {
            return;
        }
        Direction facing = plate.getBlockState().getValue(NameplateBlock.FACING);
        boolean posts = plate.getBlockState().getValue(NameplateBlock.POSTS);
        String name = plate.name.isBlank() ? "STREET NAME" : plate.name.toUpperCase(Locale.ROOT);
        String area = plate.area.toUpperCase(Locale.ROOT);
        float nameH = area.isBlank() ? 0.2f : 0.16f;
        // as wide as the words need, from one block to three
        float half = Math.max(0.45f, Math.min(1.5f, (font.width(name) * nameH / 7f + 0.24f) / 2));
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        float back = posts ? -THICK / 2 : -0.5f, front = back + THICK;
        VertexConsumer vc = FlatQuads.of(buffers, pose);
        Matrix4f m = pose.last().pose();
        box(vc, m, -half, Y0, back, half, Y1, front, BLACK, light);
        float e = 0.035f;
        quad(vc, m, -half + e, Y0 + e, half - e, Y1 - e, front + 0.002f, WHITE, light);
        if (posts) {
            for (float x : new float[]{-half + 0.12f, half - 0.18f}) {
                box(vc, m, x, 0, -0.03f, x + 0.06f, Y0, 0.03f, BLACK, light);
            }
            quad(vc, m, half, Y0, -half, Y1, back - 0.002f, WHITE, light);   // the back is plain white
        }
        float z = front + 0.004f;
        if (area.isBlank()) {
            text(pose, buffers, name, (Y0 + Y1) / 2 - nameH / 2, nameH, half * 2 - 0.16f, BLACK, light, z);
        } else {
            text(pose, buffers, name, Y0 + 0.2f, nameH, half * 2 - 0.16f, BLACK, light, z);
            text(pose, buffers, area, Y0 + 0.07f, 0.085f, half * 2 - 0.2f, plate.red ? RED : BLACK, light, z);
        }
        pose.popPose();
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float z, int argb, int light) {
        int r = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255;
        vc.vertex(m, x0, y0, z).color(r, g, b, 255).uv2(light).endVertex();
        vc.vertex(m, x1, y0, z).color(r, g, b, 255).uv2(light).endVertex();
        vc.vertex(m, x1, y1, z).color(r, g, b, 255).uv2(light).endVertex();
        vc.vertex(m, x0, y1, z).color(r, g, b, 255).uv2(light).endVertex();
    }

    private static void box(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, int argb, int light) {
        float[][][] faces = {
                {{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}}, {{x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}},
                {{x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}, {x0, y1, z0}}, {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}},
                {{x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}}, {{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}}};
        int r = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255;
        for (float[][] f : faces) {
            for (float[] v : f) {
                vc.vertex(m, v[0], v[1], v[2]).color(r, g, b, 255).uv2(light).endVertex();
            }
        }
    }

    /** One line, centred, its letters h high from y up, shrunk to fit maxW. */
    private void text(PoseStack pose, MultiBufferSource buffers, String s, float y, float h, float maxW, int colour, int light, float z) {
        Component c = Component.literal(s);
        float scale = h / 7f, w = font.width(c) * scale;
        if (w > maxW) {
            scale *= maxW / w;
            w = maxW;
        }
        pose.pushPose();
        pose.translate(-w / 2, y + 7 * scale, z);
        pose.scale(scale, -scale, scale);
        font.drawInBatch(c, 0, 0, colour, false, pose.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, light);
        pose.popPose();
    }
}
