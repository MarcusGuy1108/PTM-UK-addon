package com.ptmuk.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ptmuk.block.ClassicLampBlockEntity;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Draws the lit lenses of classic (incandescent) heads with a brightness that follows the
 * signal like a filament: it takes a moment to warm up and glows on briefly after switching
 * off, so changes cross-fade instead of snapping like LEDs. The block model shows these
 * lenses unlit; this renderer adds the light on top.
 */
public class ClassicLampRenderer implements BlockEntityRenderer<ClassicLampBlockEntity> {
    /** Filament time constants in seconds. */
    private static final double WARM_UP = 0.07;
    private static final double COOL_DOWN = 0.16;
    /** Flashing amber / green man period: on for 10 ticks, off for 10. */
    private static final int FLASH_TICKS = 10;

    private final RandomSource random = RandomSource.create();

    public ClassicLampRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ClassicLampBlockEntity lamp, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = lamp.getBlockState();
        String block = ForgeRegistries.BLOCKS.getKey(state.getBlock()).getPath();
        SignalModels.BlockParts parts = SignalModels.index(block);
        if (parts == null || parts.aspects().isEmpty() || lamp.getLevel() == null) {
            return;
        }
        Map<String, String> looks = SignalModels.looks(parts, state);
        boolean flashOn = (lamp.getLevel().getGameTime() / FLASH_TICKS) % 2 == 0;

        long now = System.nanoTime();
        boolean first = lamp.brightness == null || lamp.brightness.length != parts.aspects().size();
        if (first) {
            lamp.brightness = new float[parts.aspects().size()];
        }
        double dt = first ? 0 : Math.min(0.25, (now - lamp.lastFrameNanos) / 1e9);
        lamp.lastFrameNanos = now;

        String geometry = SignalModels.geometry(state);
        int y = SignalModels.yRotation(state);
        // additive and unshaded: the lamp's light is added over the unlit coloured glass
        VertexConsumer consumer = buffers.getBuffer(RenderType.eyes(InventoryMenu.BLOCK_ATLAS));
        for (int i = 0; i < lamp.brightness.length; i++) {
            String aspect = parts.aspects().get(i);
            String look = looks.get(aspect);
            float target = "on".equals(look) || ("flash".equals(look) && flashOn) ? 1f : 0f;
            float b = lamp.brightness[i];
            if (first) {
                b = target;
            } else {
                double tau = target > b ? WARM_UP : COOL_DOWN;
                b += (float) ((target - b) * (1 - Math.exp(-dt / tau)));
            }
            lamp.brightness[i] = b;
            if (b < 0.01f) {
                continue;
            }
            BakedModel lit = Minecraft.getInstance().getModelManager().getModel(SignalModels.part(block, aspect + "_on", geometry, y));
            // filament colour: a dimmer bulb glows warmer and redder
            float warm = 0.55f + 0.45f * b;
            for (BakedQuad quad : lit.getQuads(state, null, random, ModelData.EMPTY, null)) {
                consumer.putBulkData(pose.last(), quad, b, b * warm, b * warm * warm, 1f, LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, false);
            }
        }
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public boolean shouldRenderOffScreen(ClassicLampBlockEntity lamp) {
        return true;
    }
}
