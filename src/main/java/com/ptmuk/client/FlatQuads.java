package com.ptmuk.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ptmuk.PtmUk;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

/**
 * Flat colour quads (sign boards, LED segments, wires) drawn the way chests and banners are, in an
 * entity render type on a plain white texture, so shader packs (Oculus) light and place them like
 * everything else. Callers write quads as they would for the text background: position, colour,
 * light; this fills in the rest, with each quad's normal tipped upwards so it isn't shaded dark.
 */
public final class FlatQuads implements VertexConsumer {
    public static final ResourceLocation WHITE = PtmUk.id("textures/misc/white.png");
    public static final RenderType TYPE = RenderType.entityCutoutNoCull(WHITE);

    private final VertexConsumer out;
    private final Vector3f up;
    private final double[] xyz = new double[12];
    private final int[] colour = new int[4];
    private final int[] light = new int[4];
    private int n;
    private double x, y, z;
    private int c = -1, l;

    private FlatQuads(VertexConsumer out, Vector3f up) {
        this.out = out;
        this.up = up;
    }

    /** A buffer for flat quads drawn under this pose (only its up direction matters). */
    public static VertexConsumer of(MultiBufferSource buffers, PoseStack pose) {
        return new FlatQuads(buffers.getBuffer(TYPE), pose.last().normal().transform(new Vector3f(0, 1, 0)).normalize());
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    @Override
    public VertexConsumer color(int r, int g, int b, int a) {
        c = (a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | (b & 255);
        return this;
    }

    @Override
    public VertexConsumer uv(float u, float v) {
        return this;
    }

    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
        l = (u & 0xFFFF) | (v & 0xFFFF) << 16;
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        return this;
    }

    @Override
    public void endVertex() {
        xyz[n * 3] = x;
        xyz[n * 3 + 1] = y;
        xyz[n * 3 + 2] = z;
        colour[n] = c;
        light[n] = l;
        if (++n == 4) {
            n = 0;
            quad();
        }
    }

    private void quad() {
        Vector3f a = new Vector3f((float) (xyz[3] - xyz[0]), (float) (xyz[4] - xyz[1]), (float) (xyz[5] - xyz[2]));
        Vector3f b = new Vector3f((float) (xyz[6] - xyz[0]), (float) (xyz[7] - xyz[1]), (float) (xyz[8] - xyz[2]));
        Vector3f nrm = a.cross(b);
        if (!(nrm.lengthSquared() > 1e-12f)) {
            nrm.set(up);
        }
        nrm.normalize().add(up.x * 1.5f, up.y * 1.5f, up.z * 1.5f).normalize();
        for (int i = 0; i < 4; i++) {
            int col = colour[i];
            out.vertex(xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2]).color(col >> 16 & 255, col >> 8 & 255, col & 255, col >>> 24)
                    .uv(0.5f, 0.5f).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light[i] & 0xFFFF, light[i] >>> 16)
                    .normal(nrm.x, nrm.y, nrm.z).endVertex();
        }
    }

    @Override
    public void defaultColor(int r, int g, int b, int a) {
    }

    @Override
    public void unsetDefaultColor() {
    }
}
