package com.ptmuk.client.building;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ptmuk.PtmUk;
import com.ptmuk.building.SectionToolItem;
import com.ptmuk.building.Sections;
import com.ptmuk.network.PtmUkNetwork;
import com.ptmuk.network.SectionMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client side of the Section Mover: aiming (as far as the Prefab Tool), outlines of the area or the paste, a line saying what it'll do. */
@Mod.EventBusSubscriber(modid = PtmUk.MOD_ID, value = Dist.CLIENT)
public final class SectionToolClient {
    private SectionToolClient() {
    }

    public static InteractionHand toolHand(Player player) {
        if (player.getMainHandItem().getItem() instanceof SectionToolItem) {
            return InteractionHand.MAIN_HAND;
        }
        if (player.getOffhandItem().getItem() instanceof SectionToolItem) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    /** Where the tool points: the block aimed at, or with nothing in reach the block under your feet. */
    static BlockPos aim(Player player, float partialTick) {
        BlockPos t = PrefabToolClient.target(player, partialTick);
        return t != null ? t : player.blockPosition().below();
    }

    public static void send(SectionMessage.Action action, String name, BlockPos pos, InteractionHand hand) {
        PtmUkNetwork.CHANNEL.sendToServer(new SectionMessage(action, name, pos, hand == InteractionHand.OFF_HAND));
    }

    public static void use(InteractionHand hand, boolean sneaking) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            return;
        }
        if (sneaking) {
            mc.setScreen(new SectionScreen(hand));
            return;
        }
        ItemStack stack = player.getItemInHand(hand);
        send(SectionToolItem.size(stack) != null ? SectionMessage.Action.PASTE : SectionMessage.Action.CORNER, "", aim(player, 1.0f), hand);
    }

    /** The R key with the tool in hand (the Prefab Tool's key). */
    static void rotate(InteractionHand hand) {
        send(SectionMessage.Action.ROTATE, "", BlockPos.ZERO, hand);
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        InteractionHand hand = player == null ? null : toolHand(player);
        if (hand == null) {
            return;
        }
        ItemStack stack = player.getItemInHand(hand);
        BlockPos at = aim(player, event.getPartialTick());
        int[] size = SectionToolItem.size(stack);
        AABB box;
        float r = 1f, g = 1f, b = 1f;
        if (size != null) {
            BoundingBox bb = Sections.box(size[0], size[1], size[2], new BlockPos(size[3], size[4], size[5]), at,
                    SectionToolItem.rotation(stack), SectionToolItem.mirror(stack));
            box = new AABB(bb.minX(), bb.minY(), bb.minZ(), bb.maxX() + 1, bb.maxY() + 1, bb.maxZ() + 1);
            g = 0.85f;
            b = 0.3f;
        } else {
            BlockPos c1 = SectionToolItem.corner(stack, SectionToolItem.C1), c2 = SectionToolItem.corner(stack, SectionToolItem.C2);
            boolean second = stack.getOrCreateTag().getInt(SectionToolItem.NEXT) == 1;
            if (c1 == null) {
                c1 = at;
                c2 = at;
            } else if (second || c2 == null) {
                c2 = at;
            }
            box = new AABB(Math.min(c1.getX(), c2.getX()), Math.min(c1.getY(), c2.getY()) - Sections.BELOW, Math.min(c1.getZ(), c2.getZ()),
                    Math.max(c1.getX(), c2.getX()) + 1, Math.max(c1.getY(), c2.getY()) + 1, Math.max(c1.getZ(), c2.getZ()) + 1);
            r = 0.3f;
            g = 0.8f;
        }
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        LevelRenderer.renderLineBox(pose, vc, box, r, g, b, 0.9f);
        LevelRenderer.renderLineBox(pose, vc, new AABB(at), 1f, 1f, 1f, 1f);
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    @SubscribeEvent
    public static void onHud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        InteractionHand hand = player == null || mc.options.hideGui || mc.screen != null ? null : toolHand(player);
        if (hand == null) {
            return;
        }
        ItemStack stack = player.getItemInHand(hand);
        String picked = stack.getOrCreateTag().getString(SectionToolItem.PICKED);
        String line = SectionToolItem.size(stack) != null
                ? "Right-click to paste " + picked + " (turned " + SectionToolItem.rotation(stack).ordinal() * 90 + ")  -  R to turn"
                : SectionToolItem.corner(stack, SectionToolItem.C1) == null || stack.getOrCreateTag().getInt(SectionToolItem.NEXT) == 0
                ? "Section Mover: right-click the first corner" : "Section Mover: right-click the other corner";
        event.getGuiGraphics().drawCenteredString(mc.font, line, event.getWindow().getGuiScaledWidth() / 2, 6, 0xFFFFE08A);
    }
}
