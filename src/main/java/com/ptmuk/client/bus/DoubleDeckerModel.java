package com.ptmuk.client.bus;

import com.ptmuk.PtmUk;
import com.ptmuk.bus.DoubleDeckerBus;
import com.rinventor.ptm2.engine.animation.model.GeoModel;
import net.minecraft.resources.ResourceLocation;

/**
 * A generated double decker model. PTM2's model loader only reads its own namespace, so the
 * geometry and animations live under ptm2 (prefixed ptmuk_); the texture stays in ours.
 */
public class DoubleDeckerModel<T extends DoubleDeckerBus> extends GeoModel<T> {
    private final ResourceLocation geo;
    private final ResourceLocation texture;
    private final ResourceLocation animations;

    public DoubleDeckerModel(String name) {
        this.geo = new ResourceLocation("ptm2", "geo/bus/ptmuk_" + name + ".geo.json");
        this.texture = PtmUk.id("textures/entity/bus/" + name + ".png");
        this.animations = new ResourceLocation("ptm2", "animations/bus/ptmuk_" + name + ".animation.json");
    }

    @Override
    public ResourceLocation getModelResource(T bus) {
        return geo;
    }

    @Override
    public ResourceLocation getTextureResource(T bus) {
        return texture;
    }

    @Override
    public ResourceLocation getAnimationResource(T bus) {
        return animations;
    }
}
