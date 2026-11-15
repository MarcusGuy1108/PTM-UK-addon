package com.ptmuk.client.shop;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ptmuk.PtmUk;
import com.ptmuk.darts.DartEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** A dart: drawn the way an arrow is, smaller, with flights, barrel and point. */
public class DartRenderer extends ArrowRenderer<DartEntity> {
    private static final ResourceLocation TEXTURE = PtmUk.id("textures/entity/dart.png");

    public DartRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(DartEntity dart, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.scale(0.55F, 0.55F, 0.55F);
        super.render(dart, yaw, partialTick, pose, buffers, light);
        pose.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(DartEntity dart) {
        return TEXTURE;
    }
}
