package com.ptmuk.client.building;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ptmuk.PtmUk;
import com.ptmuk.building.PrefabCatalog;
import com.ptmuk.building.PrefabPlacement;
import com.ptmuk.building.PrefabToolItem;
import com.ptmuk.network.PrefabToolMessage;
import com.ptmuk.network.PtmUkNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Client side of the Prefab Tool: aiming, the outline of where the building will go, the R key. */
@Mod.EventBusSubscriber(modid = PtmUk.MOD_ID, value = Dist.CLIENT)
public final class PrefabToolClient {
    /** How far away you can aim the tool. */
    public static final double REACH = 128;
    public static final KeyMapping ROTATE = new KeyMapping("key.ptmuk.prefab_rotate", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R,
            "key.categories.ptmuk");

    private PrefabToolClient() {
    }

    @Mod.EventBusSubscriber(modid = PtmUk.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Keys {
        private Keys() {
        }

        @SubscribeEvent
        public static void register(RegisterKeyMappingsEvent event) {
            event.register(ROTATE);
        }
    }

    /** The hand holding the tool, or null. */
    public static InteractionHand toolHand(Player player) {
        if (player.getMainHandItem().getItem() instanceof PrefabToolItem) {
            return InteractionHand.MAIN_HAND;
        }
        if (player.getOffhandItem().getItem() instanceof PrefabToolItem) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    /** The block the tool is aimed at, up to REACH away, or null. */
    public static BlockPos target(Player player, float partialTick) {
        HitResult hit = player.pick(REACH, partialTick, false);
        return hit.getType() == HitResult.Type.BLOCK ? ((BlockHitResult) hit).getBlockPos() : null;
    }

    public static PrefabPlacement placement(Player player, ItemStack stack, float partialTick) {
        PrefabCatalog.Entry entry = PrefabPlacement.selected(stack);
        BlockPos target = entry == null ? null : target(player, partialTick);
        if (target == null) {
            return null;
        }
        return PrefabPlacement.of(entry, target, player.getDirection(), PrefabPlacement.turns(stack), PrefabPlacement.mirrored(stack));
    }

    /** Right-click with the tool: place where aimed, or open the catalogue. */
    public static void use(InteractionHand hand, boolean sneaking) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            return;
        }
        ItemStack stack = player.getItemInHand(hand);
        PrefabPlacement p = sneaking ? null : placement(player, stack, 1.0f);
        if (p == null) {
            mc.setScreen(new PrefabScreen(hand));
            return;
        }
        PtmUkNetwork.CHANNEL.sendToServer(new PrefabToolMessage(PrefabToolMessage.Action.PLACE, p.entry().id(),
                target(player, 1.0f), player.getDirection(), hand == InteractionHand.OFF_HAND));
    }

    public static void send(PrefabToolMessage.Action action, String id, InteractionHand hand) {
        PtmUkNetwork.CHANNEL.sendToServer(new PrefabToolMessage(action, id, BlockPos.ZERO, Direction.NORTH, hand == InteractionHand.OFF_HAND));
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        while (ROTATE.consumeClick()) {
            InteractionHand hand = mc.player == null || mc.screen != null ? null : toolHand(mc.player);
            if (hand != null) {
                send(PrefabToolMessage.Action.ROTATE, "", hand);
            }
        }
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
        PrefabPlacement p = placement(player, player.getItemInHand(hand), event.getPartialTick());
        if (p == null) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        BoundingBox b = p.box();
        AABB box = new AABB(b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1);
        LevelRenderer.renderLineBox(pose, vc, box, 1f, 1f, 1f, 0.9f);
        // the ground layer, and the front edge in green so you can see which way it faces
        LevelRenderer.renderLineBox(pose, vc, new AABB(box.minX, box.minY, box.minZ, box.maxX, box.minY + 1, box.maxZ), 1f, 0.85f, 0.3f, 0.7f);
        LevelRenderer.renderLineBox(pose, vc, frontEdge(box, p.front()), 0.2f, 1f, 0.3f, 1f);
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static AABB frontEdge(AABB box, Direction front) {
        double t = 0.12, y0 = box.minY, y1 = box.minY + 1.2;
        return switch (front) {
            case NORTH -> new AABB(box.minX, y0, box.minZ - t, box.maxX, y1, box.minZ + t);
            case SOUTH -> new AABB(box.minX, y0, box.maxZ - t, box.maxX, y1, box.maxZ + t);
            case WEST -> new AABB(box.minX - t, y0, box.minZ, box.minX + t, y1, box.maxZ);
            default -> new AABB(box.maxX - t, y0, box.minZ, box.maxX + t, y1, box.maxZ);
        };
    }

    /** A line at the top of the screen saying what the tool will place. */
    @SubscribeEvent
    public static void onHud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        InteractionHand hand = player == null || mc.options.hideGui || mc.screen != null ? null : toolHand(player);
        if (hand == null) {
            return;
        }
        ItemStack stack = player.getItemInHand(hand);
        PrefabCatalog.Entry e = PrefabPlacement.selected(stack);
        String line = e == null ? "Prefab Tool: sneak + right-click to choose a building"
                : e.name() + "  (" + e.width() + " x " + e.depth() + ", " + e.height() + " high)  -  R to turn"
                + (PrefabPlacement.mirrored(stack) ? ", mirrored" : "");
        int w = event.getWindow().getGuiScaledWidth();
        event.getGuiGraphics().drawCenteredString(mc.font, line, w / 2, 6, e == null ? 0xFFBBBBBB : 0xFFFFE08A);
    }
}
