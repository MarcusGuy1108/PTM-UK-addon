package com.ptmuk.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A block model made of already-baked parts, keeping each part's own render type. */
final class CombinedModel implements BakedModel {
    private final List<BakedModel> parts;

    CombinedModel(List<BakedModel> parts) {
        this.parts = List.copyOf(parts);
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand) {
        return getQuads(state, side, rand, ModelData.EMPTY, null);
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand,
                                             @NotNull ModelData data, @Nullable RenderType renderType) {
        List<BakedQuad> quads = new ArrayList<>();
        for (BakedModel part : parts) {
            if (renderType == null || part.getRenderTypes(state, rand, data).contains(renderType)) {
                quads.addAll(part.getQuads(state, side, rand, data, renderType));
            }
        }
        return quads;
    }

    @Override
    public @NotNull ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand, @NotNull ModelData data) {
        ChunkRenderTypeSet[] sets = new ChunkRenderTypeSet[parts.size()];
        for (int i = 0; i < sets.length; i++) {
            sets[i] = parts.get(i).getRenderTypes(state, rand, data);
        }
        return ChunkRenderTypeSet.union(sets);
    }

    @Override
    public boolean useAmbientOcclusion() {
        return true;
    }

    @Override
    public boolean isGui3d() {
        return true;
    }

    @Override
    public boolean usesBlockLight() {
        return true;
    }

    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    @Override
    public @NotNull TextureAtlasSprite getParticleIcon() {
        return parts.get(0).getParticleIcon();
    }

    @Override
    public @NotNull TextureAtlasSprite getParticleIcon(@NotNull ModelData data) {
        return parts.get(0).getParticleIcon(data);
    }

    @Override
    public @NotNull ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }

    @Override
    public @NotNull ItemTransforms getTransforms() {
        return ItemTransforms.NO_TRANSFORMS;
    }
}
