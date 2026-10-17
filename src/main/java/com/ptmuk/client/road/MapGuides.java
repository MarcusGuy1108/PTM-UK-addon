package com.ptmuk.client.road;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ptmuk.PtmUk;
import com.ptmuk.road.OsmMap;
import com.ptmuk.road.RoadPlannerItem;
import com.ptmuk.road.RoadPlans;
import com.ptmuk.road.plan.RoadPlan;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * While you hold a Road Planner with a real map on it, the map's buildings stand in the world
 * as orange outlines at their real size and height, to build over.
 */
@Mod.EventBusSubscriber(modid = PtmUk.MOD_ID, value = Dist.CLIENT)
public final class MapGuides {
    private static byte[] lastData;
    private static RoadPlan.MapImport map;
    private static String key;
    private static List<OsmMap.Outline> outlines;

    private MapGuides() {
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        ItemStack stack = player == null ? null : planner(player);
        CompoundTag tag = stack == null ? null : stack.getTag();
        if (tag == null || mc.level == null) {
            return;
        }
        byte[] data = tag.getByteArray(RoadPlans.TAG);
        if (data != lastData) {                             // the same array until the plan is saved again
            lastData = data;
            map = RoadPlans.unpack(data).map;
        }
        RoadPlan.MapImport mi = map;
        if (mi == null || !mi.guides) {
            return;
        }
        String k = mi.file + "|" + mi.lat0 + "|" + mi.lon0 + "|" + mi.x + "|" + mi.z + "|" + mi.scale;
        if (!k.equals(key)) {
            CompletableFuture<OsmMap> f = MapFiles.get(mi.file);
            if (!f.isDone() || f.isCompletedExceptionally()) {
                return;
            }
            outlines = f.join().buildings(mi);
            key = k;
        }
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        int min = mc.level.getMinBuildHeight();
        int reach = mc.options.getEffectiveRenderDistance() * 16;
        for (OsmMap.Outline o : outlines) {
            double[] xs = o.x(), zs = o.z();
            if (Math.abs(xs[0] + mi.nudgeX - player.getX()) > reach || Math.abs(zs[0] + mi.nudgeZ - player.getZ()) > reach) {
                continue;
            }
            // stood on the lowest ground under its corners, so the rings stay flat
            int base = Integer.MAX_VALUE;
            for (int i = 0; i < xs.length; i++) {
                int y = mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, (int) Math.floor(xs[i] + mi.nudgeX), (int) Math.floor(zs[i] + mi.nudgeZ));
                if (y > min) {
                    base = Math.min(base, y);
                }
            }
            if (base == Integer.MAX_VALUE) {
                continue;                                   // not loaded yet
            }
            double y0 = base + 0.05, y1 = base + o.height();
            for (int i = 0; i + 1 < xs.length; i++) {
                double ax = xs[i] + mi.nudgeX, az = zs[i] + mi.nudgeZ, bx = xs[i + 1] + mi.nudgeX, bz = zs[i + 1] + mi.nudgeZ;
                line(vc, m, n, ax, y0, az, bx, y0, bz, 1f, 0.55f, 0.1f);
                line(vc, m, n, ax, y1, az, bx, y1, bz, 1f, 0.8f, 0.45f);
                line(vc, m, n, ax, y0, az, ax, y1, az, 1f, 0.8f, 0.45f);
            }
        }
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static ItemStack planner(Player p) {
        for (InteractionHand h : InteractionHand.values()) {
            if (p.getItemInHand(h).getItem() instanceof RoadPlannerItem) {
                return p.getItemInHand(h);
            }
        }
        return null;
    }

    private static void line(VertexConsumer vc, Matrix4f m, Matrix3f n, double x0, double y0, double z0, double x1, double y1, double z1,
                             float r, float g, float b) {
        float dx = (float) (x1 - x0), dy = (float) (y1 - y0), dz = (float) (z1 - z0);
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-4f) {
            return;
        }
        vc.vertex(m, (float) x0, (float) y0, (float) z0).color(r, g, b, 1f).normal(n, dx / len, dy / len, dz / len).endVertex();
        vc.vertex(m, (float) x1, (float) y1, (float) z1).color(r, g, b, 1f).normal(n, dx / len, dy / len, dz / len).endVertex();
    }
}
