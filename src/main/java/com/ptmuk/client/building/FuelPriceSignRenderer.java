package com.ptmuk.client.building;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.ptmuk.building.FuelPriceSignBlock;
import com.ptmuk.building.FuelPriceSignBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

/** The fuel price totem, both sides: logo, brand, four prices on LED digits and a line of extras. */
public class FuelPriceSignRenderer implements BlockEntityRenderer<FuelPriceSignBlockEntity> {
    private static final float HALF_W = 0.7f, DEPTH = 0.14f, BASE = 0.25f, TOP = 4.25f;
    private static final int FRAME = 0xFF3A3E44, DISPLAY = 0xFF0A0A0B;

    private final Font font = Minecraft.getInstance().font;

    public FuelPriceSignRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(FuelPriceSignBlockEntity sign, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!(sign.getBlockState().getBlock() instanceof FuelPriceSignBlock)) {
            return;
        }
        Direction facing = sign.getBlockState().getValue(FuelPriceSignBlock.FACING);
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        Draw.box(pose, buffers, -HALF_W - 0.03f, BASE, -DEPTH - 0.01f, HALF_W + 0.03f, TOP + 0.04f, DEPTH + 0.01f, FRAME, light);
        int glow = sign.lit ? LightTexture.FULL_BRIGHT : light;
        for (int side = 0; side < 2; side++) {
            pose.pushPose();
            if (side == 1) {
                pose.mulPose(Axis.YP.rotationDegrees(180));
            }
            face(sign, pose, buffers, glow);
            pose.popPose();
        }
        pose.popPose();
    }

    private void face(FuelPriceSignBlockEntity s, PoseStack pose, MultiBufferSource buffers, int light) {
        float z = DEPTH + 0.012f;
        float x0 = -HALF_W, x1 = HALF_W;
        Draw.rect(pose, buffers, x0, BASE + 0.05f, x1, TOP, z, s.panel, light);
        z += 0.002f;
        // logo and brand name at the top
        CustomImages.Image img = CustomImages.get(s.logo);
        if (img != null) {
            Draw.image(pose, buffers, img, x0 + 0.1f, 3.42f, x1 - 0.1f, 4.15f, z, light);
            Draw.text(pose, buffers, font, s.brand, 0, 3.14f, 0.22f, 1.28f, s.ink, light, z, true);
        } else {
            Draw.text(pose, buffers, font, s.brand, 0, 3.3f, 0.5f, 1.28f, s.ink, light, z, true);
        }
        // four price rows: the fuel's coloured plate, then the price on LED digits
        for (int i = 0; i < FuelPriceSignBlockEntity.ROWS; i++) {
            float top = 3.05f - i * 0.5f, bottom = top - 0.44f;
            if (s.labels[i].isBlank() && s.prices[i].isBlank()) {
                continue;
            }
            Draw.rect(pose, buffers, -0.64f, bottom, -0.03f, top, z, s.plates[i], light);
            Draw.rect(pose, buffers, 0.0f, bottom, 0.64f, top, z, DISPLAY, light);
            int labelInk = Draw.dark(s.plates[i]) ? 0xFFFFFFFF : 0xFF121214;
            label(pose, buffers, s.labels[i].trim(), bottom, labelInk, light, z + 0.002f);
            Draw.digits(pose, buffers, s.prices[i], 0.05f, bottom + 0.07f, 0.6f, top - 0.07f, z + 0.002f, s.digits, light);
        }
        // the extras along the bottom: split at the "|" nearest the middle, two lines the same size
        Draw.rect(pose, buffers, -0.64f, 0.42f, 0.64f, 1.0f, z, s.ink, light);
        int infoInk = Draw.dark(s.ink) ? 0xFFFFFFFF : 0xFF121214;
        int split = -1;
        for (int i = s.info.indexOf('|'); i >= 0; i = s.info.indexOf('|', i + 1)) {
            if (split < 0 || Math.abs(2 * i - s.info.length()) < Math.abs(2 * split - s.info.length())) {
                split = i;
            }
        }
        if (split >= 0) {
            String a = tidy(s.info.substring(0, split)), b = tidy(s.info.substring(split + 1));
            float h = Math.min(0.19f, Math.min(fit(a, 1.2f), fit(b, 1.2f)));
            Draw.text(pose, buffers, font, a, 0, 0.73f, h, 1.2f, infoInk, light, z + 0.002f, true);
            Draw.text(pose, buffers, font, b, 0, 0.5f, h, 1.2f, infoInk, light, z + 0.002f, true);
        } else {
            Draw.text(pose, buffers, font, s.info, 0, 0.6f, 0.22f, 1.2f, infoInk, light, z + 0.002f, true);
        }
    }

    /** Any "|" left inside one line becomes a dot. */
    private static String tidy(String line) {
        return line.trim().replaceAll("\\s*\\|\\s*", " \u00B7 ");
    }

    /** The largest text height at which s still fits in maxW. */
    private float fit(String s, float maxW) {
        return maxW * 7f / Math.max(1, font.width(s));
    }

    /** The fuel's name on its plate: one line, or two when a long name like "Super Unleaded" would shrink too far. */
    private void label(PoseStack pose, MultiBufferSource buffers, String label, float bottom, int ink, int light, float z) {
        float maxW = 0.55f, oneLine = 0.17f;
        int split = label.lastIndexOf(' ');
        if (split > 0 && font.width(label) * oneLine / 7f > maxW) {
            Draw.text(pose, buffers, font, label.substring(0, split), -0.335f, bottom + 0.235f, 0.14f, maxW, ink, light, z, true);
            Draw.text(pose, buffers, font, label.substring(split + 1), -0.335f, bottom + 0.065f, 0.14f, maxW, ink, light, z, true);
        } else {
            Draw.text(pose, buffers, font, label, -0.335f, bottom + 0.13f, oneLine, maxW, ink, light, z, true);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(FuelPriceSignBlockEntity sign) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 160;
    }
}
