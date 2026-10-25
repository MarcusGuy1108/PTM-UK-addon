package com.ptmuk.client.building;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.ptmuk.PtmUk;
import com.ptmuk.building.DisplayCarBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

/** A display car: its half-size model drawn twice as big round the block, nose the way it faces, tinted its paint. */
public class DisplayCarRenderer implements BlockEntityRenderer<DisplayCarBlock.DisplayCarBlockEntity> {
    public static final ResourceLocation MODEL = new ResourceLocation(PtmUk.MOD_ID, "block/display_car");

    public DisplayCarRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(DisplayCarBlock.DisplayCarBlockEntity car, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = car.getBlockState();
        if (!(state.getBlock() instanceof DisplayCarBlock block)) {
            return;
        }
        int c = block.colour;
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-(state.getValue(DisplayCarBlock.FACING).toYRot() + 180)));   // the model's nose is north
        pose.scale(2, 2, 2);
        pose.translate(-0.5, 0, -0.5);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(Sheets.cutoutBlockSheet()),
                state, Minecraft.getInstance().getModelManager().getModel(MODEL), (c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f,
                (c & 255) / 255f, light, overlay, ModelData.EMPTY, RenderType.cutout());
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(DisplayCarBlock.DisplayCarBlockEntity car) {
        return true;                                       // the car reaches well past its block
    }
}
