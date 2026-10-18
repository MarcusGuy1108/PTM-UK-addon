package com.ptmuk.client.building;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.ptmuk.building.ShopSignBlock;
import com.ptmuk.building.ShopSignBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

/** Paints each fascia board in its colour; the left-hand board writes the name across the row. */
public class ShopSignRenderer implements BlockEntityRenderer<ShopSignBlockEntity> {
    /** Front of the board, from the block centre (the board is 3 px thick against the wall). */
    private static final float FRONT = -0.5f + 3f / 16f + 0.002f;

    private final Font font = Minecraft.getInstance().font;

    public ShopSignRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ShopSignBlockEntity sign, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!(sign.getBlockState().getBlock() instanceof ShopSignBlock)) {
            return;
        }
        Direction facing = sign.getBlockState().getValue(ShopSignBlock.FACING);
        int glow = sign.lit ? LightTexture.FULL_BRIGHT : light;
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        if (sign.getBlockState().getValue(ShopSignBlock.BOARD)) {
            drawBoard(pose, buffers, sign.board, glow, -0.5f, 0.5f, !sign.joined(Direction.DOWN), !sign.joined(Direction.UP));
        }
        if (sign.isAnchor()) {
            int span = sign.span(), rows = sign.rows();
            float x0 = -0.5f, x1 = -0.5f + span;
            boolean words = !(sign.text.isBlank() && sign.sub.isBlank());
            CustomImages.Image img = CustomImages.get(sign.logo);
            if (img != null) {
                // the picture fills the sign, or sits at the left of the name
                float pad = 0.06f * rows;
                float right = words ? Math.min(x1 - pad, x0 + pad + (rows - 2 * pad) * img.width() / (float) Math.max(1, img.height())) : x1 - pad;
                Draw.image(pose, buffers, img, x0 + pad, pad, right, rows - pad, FRONT + 0.003f, glow);
                x0 = right;
            }
            if (words) {
                drawText(pose, buffers, sign.text, sign.sub, sign.ink, glow, x0, x1, rows);
            }
        }
        pose.popPose();
    }

    /** The painted face with a darker moulding along the top and bottom edges. */
    public static void drawBoard(PoseStack pose, MultiBufferSource buffers, int argb, int light, float x0, float x1) {
        drawBoard(pose, buffers, argb, light, x0, x1, true, true);
    }

    /** The painted face; the mouldings only on edges not joined to another board of the same sign. */
    public static void drawBoard(PoseStack pose, MultiBufferSource buffers, int argb, int light, float x0, float x1,
                                 boolean bottom, boolean top) {
        float y0 = bottom ? 0.06f : 0f, y1 = top ? 0.94f : 1f;
        rect(pose, buffers, x0, y0, x1, y1, FRONT, argb, light);
        int edge = darker(argb, 0.62f);
        if (bottom) {
            rect(pose, buffers, x0, 0.0f, x1, 0.06f, FRONT, edge, light);
        }
        if (top) {
            rect(pose, buffers, x0, 0.94f, x1, 1.0f, FRONT, edge, light);
        }
    }

    /** The name (and the line under it) across a sign {@code rows} boards high. */
    private void drawText(PoseStack pose, MultiBufferSource buffers, String text, String sub, int ink, int light, float x0, float x1,
                          int rows) {
        float maxW = (x1 - x0) * 0.94f - 0.08f;
        float cx = (x0 + x1) / 2;
        boolean two = !sub.isBlank();
        if (!text.isBlank()) {
            float h = two ? 0.44f : 0.56f;
            float y = two ? 0.42f : 0.22f;
            line(pose, buffers, text, cx, y * rows, h * rows, maxW, ink, light);
        }
        if (two) {
            line(pose, buffers, sub, cx, (text.isBlank() ? 0.36f : 0.12f) * rows, (text.isBlank() ? 0.3f : 0.2f) * rows, maxW,
                    ink, light);
        }
    }

    /** One line of text, centred on cx with its baseline region from y to y + h, shrunk to fit maxW. */
    private void line(PoseStack pose, MultiBufferSource buffers, String s, float cx, float y, float h, float maxW, int colour, int light) {
        Component c = Component.literal(s);
        float scale = h / 8f;
        float w = font.width(c) * scale;
        if (w > maxW) {
            scale *= maxW / w;
            w = maxW;
        }
        pose.pushPose();
        pose.translate(cx - w / 2, y + (h + 7 * scale) / 2, FRONT + 0.003f);
        pose.scale(scale, -scale, scale);
        font.drawInBatch(c, 0, 0, colour, false, pose.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, light);
        pose.popPose();
    }

    private static int darker(int argb, float f) {
        int r = (int) (((argb >> 16) & 255) * f), g = (int) (((argb >> 8) & 255) * f), b = (int) ((argb & 255) * f);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static void rect(PoseStack pose, MultiBufferSource buffers, float x0, float y0, float x1, float y1, float z, int argb, int light) {
        Matrix4f m = pose.last().pose();
        VertexConsumer vc = com.ptmuk.client.FlatQuads.of(buffers, pose);
        int a = argb >>> 24, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        vc.vertex(m, x0, y0, z).color(r, g, b, a).uv2(light).endVertex();
        vc.vertex(m, x1, y0, z).color(r, g, b, a).uv2(light).endVertex();
        vc.vertex(m, x1, y1, z).color(r, g, b, a).uv2(light).endVertex();
        vc.vertex(m, x0, y1, z).color(r, g, b, a).uv2(light).endVertex();
    }

    @Override
    public boolean shouldRenderOffScreen(ShopSignBlockEntity sign) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
