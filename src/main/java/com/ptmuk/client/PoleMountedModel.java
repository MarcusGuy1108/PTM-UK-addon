package com.ptmuk.client;

import com.ptmuk.block.PoleType;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A head or sign that switches to the UK pole-top mounting when it sits on a UK pole: the
 * pole continues up behind it and it is clamped to the pole's front. The block below is read
 * while the chunk is built (model data), so this costs no extra block states.
 */
final class PoleMountedModel implements BakedModel {
    private static final ModelProperty<PoleType> POLE = new ModelProperty<>();

    private final CombinedModel normal;
    private final Map<PoleType, CombinedModel> onPole;

    PoleMountedModel(CombinedModel normal, Map<PoleType, CombinedModel> onPole) {
        this.normal = normal;
        this.onPole = onPole;
    }

    private BakedModel pick(ModelData data) {
        PoleType pole = data.get(POLE);
        return pole == null ? normal : onPole.get(pole);
    }

    @Override
    public @NotNull ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos, @NotNull BlockState state,
                                           @NotNull ModelData data) {
        PoleType pole = SignalModels.poleBelow(level, pos, state);
        return pole == null ? data : data.derive().with(POLE, pole).build();
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand) {
        return normal.getQuads(state, side, rand);
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand,
                                             @NotNull ModelData data, @Nullable RenderType renderType) {
        return pick(data).getQuads(state, side, rand, data, renderType);
    }

    @Override
    public @NotNull ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand, @NotNull ModelData data) {
        return pick(data).getRenderTypes(state, rand, data);
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
        return normal.getParticleIcon();
    }

    @Override
    public @NotNull TextureAtlasSprite getParticleIcon(@NotNull ModelData data) {
        return normal.getParticleIcon(data);
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
