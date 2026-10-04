package com.ptmuk.client.sign;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.ptmuk.motorway.GantryBeamBlock;
import com.ptmuk.sign.DirectionSignBlock;
import com.ptmuk.sign.DirectionSignBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class DirectionSignRenderer implements BlockEntityRenderer<DirectionSignBlockEntity> {
    public DirectionSignRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(DirectionSignBlockEntity sign, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = sign.getBlockState();
        if (!(state.getBlock() instanceof DirectionSignBlock)) {
            return;
        }
        Direction facing = state.getValue(DirectionSignBlock.FACING);
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        // retroreflective signs stay readable: keep a little light even in the dark
        int block = Math.max(LightTexture.block(light), 4);
        int packed = LightTexture.pack(block, LightTexture.sky(light));
        SignPainter.paint(pose, buffers, sign, packed, sign.legs ? groundDistance(sign) : 0, hangLength(sign));
        pose.popPose();
    }

    /** How far the legs need to reach: down to the first block with a collision shape. */
    private static float groundDistance(DirectionSignBlockEntity sign) {
        Level level = sign.getLevel();
        if (level == null) {
            return 0;
        }
        BlockPos.MutableBlockPos p = sign.getBlockPos().mutable();
        for (int i = 1; i <= 12; i++) {
            p.move(Direction.DOWN);
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
                return i - 1;
            }
        }
        return 0;
    }

    /** Overhead signs: brackets up to a gantry beam just above the panel. */
    private static float hangLength(DirectionSignBlockEntity sign) {
        Level level = sign.getLevel();
        if (level == null || sign.legs) {
            return 0;
        }
        BlockPos above = sign.getBlockPos().above(sign.height);
        return level.getBlockState(above).getBlock() instanceof GantryBeamBlock ? 0.15f : 0;
    }

    @Override
    public boolean shouldRenderOffScreen(DirectionSignBlockEntity sign) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 192;
    }
}
