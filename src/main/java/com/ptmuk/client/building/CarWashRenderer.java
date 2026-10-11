package com.ptmuk.client.building;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.ptmuk.building.CarWashBlock;
import com.ptmuk.building.CarWashBlockEntity;
import com.ptmuk.building.CarWashBlockEntity.Phase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

/** The car wash gantry on its rails, with spinning brushes and a status board facing the entrance. */
public class CarWashRenderer implements BlockEntityRenderer<CarWashBlockEntity> {
    private static final int WHITE = 0xFFF0F0EC, BLUE = 0xFF1F5FB0, RAIL = 0xFF8C9094, DARK = 0xFF2A2C30,
            BRISTLE_A = 0xFF2F6BE0, BRISTLE_B = 0xFFE03A3A, SHAFT = 0xFFB8BCC0;
    private static final float LEG_X = 2.7f, LEG_W = 0.25f, BEAM_Y = 3.7f, BRUSH_R = 0.42f;
    private static final int FINS = 12;

    private final Font font = Minecraft.getInstance().font;

    public CarWashRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CarWashBlockEntity wash, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!(wash.getBlockState().getBlock() instanceof CarWashBlock) || wash.getLevel() == null) {
            return;
        }
        Direction facing = wash.facing();
        float p = wash.progress(partialTick);
        Phase phase = CarWashBlockEntity.phase(p);
        float z = CarWashBlockEntity.gantry(p);
        float time = (wash.getLevel().getGameTime() % 100000) + partialTick;
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        // rails
        for (int s = -1; s <= 1; s += 2) {
            Draw.box(pose, buffers, s * LEG_X - 0.12f, 0, -CarWashBlockEntity.BAY_HALF_L, s * LEG_X + 0.12f, 0.08f,
                    CarWashBlockEntity.BAY_HALF_L, RAIL, light);
        }
        // gantry: legs, beam, blue side panels
        for (int s = -1; s <= 1; s += 2) {
            float x0 = s * LEG_X - LEG_W, x1 = s * LEG_X + LEG_W;
            Draw.box(pose, buffers, Math.min(x0, x1), 0.08f, z - 0.32f, Math.max(x0, x1), BEAM_Y, z + 0.32f, WHITE, light);
            float px = s * (LEG_X + LEG_W) + s * 0.01f;
            Draw.box(pose, buffers, Math.min(px, px + s * 0.02f), 0.6f, z - 0.28f, Math.max(px, px + s * 0.02f), BEAM_Y - 0.3f,
                    z + 0.28f, BLUE, light);
        }
        Draw.box(pose, buffers, -LEG_X - LEG_W, BEAM_Y, z - 0.36f, LEG_X + LEG_W, BEAM_Y + 0.55f, z + 0.36f, WHITE, light);
        Draw.box(pose, buffers, -LEG_X - LEG_W, BEAM_Y + 0.55f, z - 0.36f, LEG_X + LEG_W, BEAM_Y + 0.65f, z + 0.36f, BLUE, light);
        // dryer nozzles along the beam
        for (int k = -3; k <= 3; k++) {
            Draw.box(pose, buffers, k * 0.55f - 0.12f, BEAM_Y - 0.15f, z - 0.15f, k * 0.55f + 0.12f, BEAM_Y, z + 0.15f, DARK, light);
        }
        // brushes: two vertical ones at the sides, one horizontal across the top
        boolean spinning = phase == Phase.WASH || phase == Phase.STOP;
        float spin = spinning ? time * 0.6f : 0;
        for (int s = -1; s <= 1; s += 2) {
            Draw.box(pose, buffers, s * 1.75f - 0.05f, 0.1f, z - 0.05f, s * 1.75f + 0.05f, BEAM_Y, z + 0.05f, SHAFT, light);
            for (int k = 0; k < FINS; k++) {
                pose.pushPose();
                pose.translate(s * 1.75f, 0, z);
                pose.mulPose(Axis.YP.rotation(s * spin + k * (float) (2 * Math.PI / FINS)));
                Draw.box(pose, buffers, 0.05f, 0.15f, -0.03f, BRUSH_R, 2.85f, 0.03f, k % 2 == 0 ? BRISTLE_A : BRISTLE_B, light);
                pose.popPose();
            }
        }
        float topY = 2.25f;
        Draw.box(pose, buffers, -1.4f, topY - 0.05f, z - 0.05f, 1.4f, topY + 0.05f, z + 0.05f, SHAFT, light);
        for (int k = 0; k < FINS; k++) {
            pose.pushPose();
            pose.translate(0, topY, z);
            pose.mulPose(Axis.XP.rotation(spin + k * (float) (2 * Math.PI / FINS)));
            Draw.box(pose, buffers, -1.3f, 0.05f, -0.03f, 1.3f, BRUSH_R, 0.03f, k % 2 == 0 ? BRISTLE_A : BRISTLE_B, light);
            pose.popPose();
        }
        // status board on the beam, facing the entrance, and the name on the far side
        float fz = z + 0.37f;
        Draw.rect(pose, buffers, -1.3f, BEAM_Y + 0.05f, 1.3f, BEAM_Y + 0.5f, fz, 0xFF050506, LightTexture.FULL_BRIGHT);
        String text;
        int colour;
        switch (phase) {
            case STOP -> { text = "STOP - ENGINE OFF"; colour = 0xFFFF3A2E; }
            case WASH -> { text = "WASHING"; colour = 0xFFFFB21E; }
            case TURN -> { text = "RINSING"; colour = 0xFFFFB21E; }
            case DRY -> { text = "DRYING"; colour = 0xFFFFB21E; }
            case EXIT -> { text = "PLEASE EXIT"; colour = 0xFF42F26A; }
            default -> {
                boolean justDone = wash.getLevel().getGameTime() - wash.lastEnd < 100;
                text = justDone ? "THANK YOU" : "DRIVE IN";
                colour = 0xFF42F26A;
            }
        }
        Draw.text(pose, buffers, font, text, 0, BEAM_Y + 0.12f, 0.3f, 2.4f, colour, LightTexture.FULL_BRIGHT, fz + 0.002f, true);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180));
        Draw.text(pose, buffers, font, "CAR WASH", 0, BEAM_Y + 0.12f, 0.32f, 2.4f, BLUE, light, -z + 0.372f, true);
        pose.popPose();
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(CarWashBlockEntity wash) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
