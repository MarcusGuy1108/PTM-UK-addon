package com.ptmuk.client.shop;

import com.ptmuk.PtmUk;
import com.ptmuk.shop.ShopKeeperEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** A shop's person: a player-shaped figure in their shop's uniform (one of 24 faces and outfits). */
public class ShopKeeperRenderer extends HumanoidMobRenderer<ShopKeeperEntity, PlayerModel<ShopKeeperEntity>> {
    public ShopKeeperRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(ShopKeeperEntity e) {
        return PtmUk.id("textures/entity/shop_keeper/skin_" + Math.floorMod(e.skin(), ShopKeeperEntity.OUTFITS * ShopKeeperEntity.PEOPLE) + ".png");
    }
}
