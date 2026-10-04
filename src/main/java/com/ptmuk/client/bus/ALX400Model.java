package com.ptmuk.client.bus;

import com.ptmuk.PtmUk;
import com.ptmuk.bus.ALX400;
import com.rinventor.ptm2.engine.animation.model.GeoModel;
import net.minecraft.resources.ResourceLocation;

/** PTM2's model loader only reads its own namespace, so the geometry and animations live under ptm2. */
public class ALX400Model extends GeoModel<ALX400> {
    @Override
    public ResourceLocation getModelResource(ALX400 bus) {
        return new ResourceLocation("ptm2", "geo/bus/ptmuk_alx400.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(ALX400 bus) {
        return PtmUk.id("textures/entity/bus/alx400.png");
    }

    @Override
    public ResourceLocation getAnimationResource(ALX400 bus) {
        return new ResourceLocation("ptm2", "animations/bus/ptmuk_alx400.animation.json");
    }
}
