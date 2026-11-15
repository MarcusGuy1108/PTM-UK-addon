package com.ptmuk.client.shop;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.ptmuk.money.CashItem;
import com.ptmuk.shop.ProductItem;
import com.ptmuk.shop.SelfCheckoutBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import org.joml.Matrix4f;

/**
 * The self-checkout's own screen, in the world (on the tilted screen of its model): the shop's
 * name, what's been scanned with the prices and the total, what to do next, and a colleague's
 * approval when it's waiting for one. Lit, like a real screen.
 */
public class SelfCheckoutRenderer implements BlockEntityRenderer<SelfCheckoutBlockEntity> {
    private static final int PAPER = 0xFFF4F7F8, BRAND = 0xFF0B7A45, INK = 0xFF16222C, SOFT = 0xFF4A5864, AMBER = 0xFFFFC94D, WHITE = 0xFFFFFFFF;
    /** The screen on the model (north-facing): x 3.3-12.7, y 16.3-22.7 px, its face at z 10.98, tipped back 22.5 degrees about (8, 19.5, 11.5). */
    private static final float X = 0.29f, Y = 0.198f, FACE = (11.5f - 10.98f) / 16f + 0.002f;
    private final Font font = Minecraft.getInstance().font;
    private PoseStack pose;
    private MultiBufferSource buffers;
    private float z;

    public SelfCheckoutRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(SelfCheckoutBlockEntity till, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!till.getBlockState().hasProperty(HorizontalDirectionalBlock.FACING)) {
            return;
        }
        this.pose = pose;
        this.buffers = buffers;
        Direction facing = till.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.translate(0, 19.5 / 16, -(11.5 / 16 - 0.5));
        pose.mulPose(Axis.XP.rotationDegrees(-22.5f));
        z = FACE;
        rect(-X, -Y, X, Y, PAPER);
        rect(-X, Y - 0.05f, X, Y, BRAND);
        z = FACE + 0.001f;
        text(till.shopName(), -X + 0.015f, Y - 0.04f, 0.03f, WHITE, false, 0.36f);
        text("Self-checkout", X - 0.015f - width("Self-checkout", 0.024f), Y - 0.037f, 0.024f, WHITE, false, 0);
        if (!till.thanks().isEmpty()) {
            text("Thank you!", 0, 0.03f, 0.05f, BRAND, true, 0.55f);
            text("Please take your shopping", 0, -0.04f, 0.026f, INK, true, 0.55f);
            text(till.receipt() ? "and your receipt" : "", 0, -0.075f, 0.026f, INK, true, 0.55f);
        } else if (!till.busy()) {
            text("Scan an item to start", 0, 0.05f, 0.036f, INK, true, 0.55f);
            text("Hold it and click the checkout", 0, -0.01f, 0.024f, SOFT, true, 0.55f);
            text("Touch the screen for more options", 0, -0.045f, 0.024f, SOFT, true, 0.55f);
            text("Card, phone and cash accepted", 0, -0.12f, 0.022f, BRAND, true, 0.55f);
        } else {
            List<String[]> lines = new ArrayList<>();
            for (ItemStack s : till.scanned()) {
                lines.add(new String[]{(s.getCount() > 1 ? s.getCount() + " x " : "") + s.getHoverName().getString(),
                        CashItem.money((long) ProductItem.price(s) * s.getCount())});
            }
            for (SelfCheckoutBlockEntity.Extra e : till.extras()) {
                lines.add(new String[]{(e.count() > 1 ? e.count() + " x " : "") + e.product().title, CashItem.money((long) e.each() * e.count())});
            }
            if (till.bags() > 0) {
                lines.add(new String[]{(till.bags() > 1 ? till.bags() + " x " : "") + "Carrier bag", CashItem.money((long) till.bags() * SelfCheckoutBlockEntity.BAG)});
            }
            int first = Math.max(0, lines.size() - 6);
            for (int i = first; i < lines.size(); i++) {
                float y = Y - 0.085f - (i - first) * 0.036f;
                float pw = width(lines.get(i)[1], 0.024f);
                text(lines.get(i)[0], -X + 0.015f, y, 0.024f, INK, false, 2 * X - pw - 0.05f);
                text(lines.get(i)[1], X - 0.015f - pw, y, 0.024f, INK, false, 0);
            }
            z = FACE + 0.0005f;
            rect(-X, -Y, X, -Y + 0.07f, 0xFFE3E9EC);
            z = FACE + 0.001f;
            String total = "Total " + CashItem.money(till.total());
            text(total, X - 0.015f - width(total, 0.034f), -Y + 0.02f, 0.034f, INK, false, 0);
            text(till.count() + (till.count() == 1 ? " item" : " items"), -X + 0.015f, -Y + 0.04f, 0.022f, SOFT, false, 0);
            text("Tap card or pay cash", -X + 0.015f, -Y + 0.012f, 0.022f, BRAND, false, 0);
        }
        if (!till.assist().isEmpty()) {
            z = FACE + 0.002f;
            rect(-X + 0.03f, -0.07f, X - 0.03f, 0.07f, AMBER);
            z = FACE + 0.003f;
            text(till.assist(), 0, 0.015f, 0.024f, INK, true, 2 * X - 0.08f);
            text("Please wait", 0, -0.03f, 0.024f, INK, true, 0.4f);
        }
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(SelfCheckoutBlockEntity till) {
        return true;
    }

    private float width(String s, float h) {
        return font.width(s) * h / 7f;
    }

    /** Text at x (or centred on it), its baseline at y, h tall; squeezed to fit maxW. */
    private void text(String s, float x, float y, float h, int colour, boolean centre, float maxW) {
        if (s.isEmpty()) {
            return;
        }
        float scale = h / 7f, w = font.width(s) * scale;
        if (maxW > 0 && w > maxW) {
            scale *= maxW / w;
            w = maxW;
        }
        pose.pushPose();
        pose.translate(centre ? x - w / 2 : x, y + 7 * scale, z);
        pose.scale(scale, -scale, scale);
        font.drawInBatch(Component.literal(s), 0, 0, colour, false, pose.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0,
                LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    private void rect(float x0, float y0, float x1, float y1, int argb) {
        Matrix4f m = pose.last().pose();
        VertexConsumer vc = com.ptmuk.client.FlatQuads.of(buffers, pose);
        int a = argb >>> 24, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        int light = LightTexture.FULL_BRIGHT;
        vc.vertex(m, x0, y0, z).color(r, g, b, a).uv2(light).endVertex();
        vc.vertex(m, x1, y0, z).color(r, g, b, a).uv2(light).endVertex();
        vc.vertex(m, x1, y1, z).color(r, g, b, a).uv2(light).endVertex();
        vc.vertex(m, x0, y1, z).color(r, g, b, a).uv2(light).endVertex();
    }
}
