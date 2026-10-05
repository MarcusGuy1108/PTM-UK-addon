package com.ptmuk.client.bus;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ptmuk.PtmUk;
import com.ptmuk.bus.KiaK4;
import com.rinventor.ptm2.client.AnimationUtils;
import com.rinventor.ptm2.client.view.MirrorRenderer;
import com.rinventor.ptm2.engine.animation.cache.GeoBone;
import com.rinventor.ptm2.engine.animation.model.GeoModel;
import com.rinventor.ptm2.engine.animation.renderer.GeoEntityRenderer;
import com.rinventor.ptm2.engine.animation.util.RenderUtils;
import com.rinventor.ptm2.engine.graphics.EntityTextRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** Renders the K4 the way PTM2 renders its own cars: turning wheels and steering wheel, working
 * door mirrors and the registration on both plates. */
public class KiaK4Renderer extends GeoEntityRenderer<KiaK4> {
    private static final int BLACK = 0xFF101010;
    private final GeoModel<KiaK4> model = getGeoModel();

    public KiaK4Renderer(EntityRendererProvider.Context context) {
        super(context, new GeoModel<>() {
            private final ResourceLocation geo = new ResourceLocation("ptm2", "geo/car/ptmuk_k4.geo.json");
            private final ResourceLocation texture = PtmUk.id("textures/entity/car/k4.png");
            private final ResourceLocation animations = new ResourceLocation("ptm2", "animations/car/ptmuk_k4.animation.json");

            @Override
            public ResourceLocation getModelResource(KiaK4 car) {
                return geo;
            }

            @Override
            public ResourceLocation getTextureResource(KiaK4 car) {
                return texture;
            }

            @Override
            public ResourceLocation getAnimationResource(KiaK4 car) {
                return animations;
            }
        });
        this.shadowRadius = 0.9f;
    }

    @Override
    public void render(KiaK4 car, float entityYaw, float partialTick, PoseStack ps, MultiBufferSource buffers, int light) {
        AnimationUtils.wheels(model, car);
        AnimationUtils.steeringWheel(model, car, false);
        super.render(car, entityYaw, partialTick, ps, buffers, light);
    }

    @Override
    public void renderRecursively(PoseStack ps, KiaK4 car, GeoBone bone, RenderType renderType, MultiBufferSource buffers,
                                  VertexConsumer buffer, boolean isReRender, float partialTick, int light, int overlay,
                                  float red, float green, float blue, float alpha) {
        String name = bone.getName();
        if (DoubleDeckerRenderer.LIGHT_BONES.contains(name)) {
            light = DoubleDeckerRenderer.FULL_BRIGHT;
        }
        boolean mirrorBone = "Mirrors".equals(name) || "LeftMirror".equals(name) || "RightMirror".equals(name);
        boolean hide = MirrorRenderer.renderingMirror && mirrorBone && car.equals(MirrorRenderer.renderingBus);
        boolean hidden = bone.isHidden();
        boolean childrenHidden = bone.isHidingChildren();
        if (hide) {
            bone.setHidden(true);
        }
        try {
            super.renderRecursively(ps, car, bone, renderType, buffers, buffer, isReRender, partialTick, light, overlay, red, green, blue, alpha);
        } finally {
            if (hide) {
                bone.setHidden(hidden);
                bone.setChildrenHidden(childrenHidden);
            }
        }
        if (!isReRender && ("LeftMirror".equals(name) || "RightMirror".equals(name))) {
            ps.pushPose();
            RenderUtils.prepMatrixForBone(ps, bone);
            MirrorRenderer.draw(car, ps, bone, buffers, ps.last().pose().determinant3x3() < 0.0f);
            ps.popPose();
        }
    }

    @Override
    protected boolean renderBoneText(PoseStack ps, KiaK4 car, GeoBone bone, MultiBufferSource buffers, int light) {
        if ("PlateFront".equals(bone.getName())) {
            EntityTextRenderer.drawStringC(car.registrationPlate, false, 9, 0.0, -0.01, 0.0, 0.0f, 0.0f, ps, buffers, BLACK, light);
            return true;
        }
        if ("PlateBack".equals(bone.getName())) {
            EntityTextRenderer.drawStringC(car.registrationPlate, false, 9, 0.0, -0.01, 0.0, 180.0f, 0.0f, ps, buffers, BLACK, light);
            return true;
        }
        return false;
    }
}
