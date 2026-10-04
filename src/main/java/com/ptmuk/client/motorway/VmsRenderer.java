package com.ptmuk.client.motorway;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.ptmuk.motorway.DotFont;
import com.ptmuk.motorway.VmsBlock;
import com.ptmuk.motorway.VmsBlockEntity;
import com.ptmuk.motorway.VmsLayout;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/** Draws the VMS housing and its lit amber dots (unlit dots are skipped to keep it cheap). */
public class VmsRenderer implements BlockEntityRenderer<VmsBlockEntity> {
    private static final int AMBER = 0xFFFFA81E;
    private static final int AMBER_OFF = 0xFF3A2A12;
    private static final int HOUSING = 0xFF1C1E20;
    private static final int FACE = 0xFF0B0B0C;
    private static final int FRAME = 0xFF8E9296;

    public VmsRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(VmsBlockEntity vms, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = vms.getBlockState();
        if (!(state.getBlock() instanceof VmsBlock)) {
            return;
        }
        Direction facing = state.getValue(VmsBlock.FACING);
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        Matrix4f m = pose.last().pose();
        VertexConsumer vc = buffers.getBuffer(RenderType.textBackground());
        float w = vms.width - 0.1f, h = VmsLayout.HEIGHT;
        float x0 = -w / 2, x1 = w / 2;
        // housing box: front at z 0.2, back at z -0.25
        box(vc, m, x0, 0, -0.25f, x1, h, 0.2f, HOUSING, light);
        // grey frame and black face
        quad(vc, m, x0 + 0.02f, 0.02f, x1 - 0.02f, h - 0.02f, 0.201f, FRAME, light);
        quad(vc, m, x0 + 0.07f, 0.07f, x1 - 0.07f, h - 0.07f, 0.202f, FACE, light);

        int full = LightTexture.FULL_BRIGHT;
        float p = VmsLayout.PITCH, dot = p * 0.72f;
        int cols = vms.charsPerLine() * 6 - 1;
        float gx0 = -cols * p / 2;
        float lineH = 7 * p, gap = 3 * p;
        float top = h / 2 + (3 * lineH + 2 * gap) / 2;
        for (int li = 0; li < VmsBlockEntity.LINES; li++) {
            String text = vms.lines[li].strip();
            if (text.length() > vms.charsPerLine()) {
                text = text.substring(0, vms.charsPerLine());
            }
            int offset = (vms.charsPerLine() - text.length()) * 3;   // centred, in dots
            float ly = top - li * (lineH + gap);
            for (int ci = 0; ci < text.length(); ci++) {
                char c = text.charAt(ci);
                for (int row = 0; row < DotFont.HEIGHT; row++) {
                    for (int col = 0; col < DotFont.WIDTH; col++) {
                        if (DotFont.dot(c, col, row)) {
                            float x = gx0 + (offset + ci * 6 + col) * p;
                            float y = ly - (row + 1) * p;
                            quad(vc, m, x, y, x + dot, y + dot, 0.204f, AMBER, full);
                        }
                    }
                }
            }
        }
        // lanterns: four amber lamps at the corners flashing in diagonal pairs
        if (vms.lanterns && !vms.blank()) {
            long t = vms.getLevel() != null ? vms.getLevel().getGameTime() : 0;
            boolean phase = (t / 10) % 2 == 0;
            float r = 0.09f;
            float lx0 = x0 + 0.2f, lx1 = x1 - 0.2f, ly0 = 0.2f, ly1 = h - 0.2f;
            lamp(vc, m, lx0, ly1, r, phase, full, light);
            lamp(vc, m, lx1, ly0, r, phase, full, light);
            lamp(vc, m, lx1, ly1, r, !phase, full, light);
            lamp(vc, m, lx0, ly0, r, !phase, full, light);
        }
        pose.popPose();
    }

    private static void lamp(VertexConsumer vc, Matrix4f m, float cx, float cy, float r, boolean on, int full, int light) {
        quad(vc, m, cx - r, cy - r, cx + r, cy + r, 0.205f, on ? AMBER : AMBER_OFF, on ? full : light);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float z, int argb, int light) {
        int a = argb >>> 24, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        vc.vertex(m, x0, y0, z).color(r, g, b, a).uv2(light).endVertex();
        vc.vertex(m, x1, y0, z).color(r, g, b, a).uv2(light).endVertex();
        vc.vertex(m, x1, y1, z).color(r, g, b, a).uv2(light).endVertex();
        vc.vertex(m, x0, y1, z).color(r, g, b, a).uv2(light).endVertex();
    }

    /** Closed box with faces pointing outwards, slightly shaded per side. */
    static void box(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, int argb, int light) {
        float[][][] faces = {
                {{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}},   // front
                {{x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}},   // back
                {{x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}, {x0, y1, z0}},   // top
                {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}},   // bottom
                {{x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}},   // right
                {{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}},   // left
        };
        float[] shade = {1f, 0.8f, 1.1f, 0.6f, 0.85f, 0.85f};
        for (int i = 0; i < faces.length; i++) {
            int r = Math.min(255, (int) (((argb >> 16) & 255) * shade[i]));
            int g = Math.min(255, (int) (((argb >> 8) & 255) * shade[i]));
            int b = Math.min(255, (int) ((argb & 255) * shade[i]));
            for (float[] v : faces[i]) {
                vc.vertex(m, v[0], v[1], v[2]).color(r, g, b, 255).uv2(light).endVertex();
            }
        }
    }

    @Override
    public boolean shouldRenderOffScreen(VmsBlockEntity vms) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 192;
    }
}
