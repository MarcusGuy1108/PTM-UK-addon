package com.ptmuk.client.power;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ptmuk.PtmUk;
import com.ptmuk.power.PowerLines;
import com.ptmuk.power.PylonData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Draws the conductors between connected towers as sagging catenaries. Each span pairs every
 * conductor on one tower with the matching one on the other; bundled conductors (twin on 400 kV)
 * are drawn with their spacing.
 */
@Mod.EventBusSubscriber(modid = PtmUk.MOD_ID, value = Dist.CLIENT)
public final class ClientPowerLines {
    private record Wire(Vec3 a, Vec3 b, double sag, double thickness) {
    }

    private static final int SEGMENTS = 24;
    private static List<PowerLines.Link> links = List.of();
    private static List<Wire> wires = List.of();

    private ClientPowerLines() {
    }

    public static void set(List<PowerLines.Link> newLinks) {
        links = List.copyOf(newLinks);
        List<Wire> out = new ArrayList<>();
        for (PowerLines.Link l : links) {
            build(l, out);
        }
        wires = out;
    }

    @SubscribeEvent
    public static void onUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            links = List.of();
            wires = List.of();
        }
    }

    /** One tower's conductor points as seen from the other end of the span. */
    private static List<PylonData.Attach> facing(PylonData data, BlockPos origin, net.minecraft.core.Direction dir,
                                                 Vec3 towards, boolean earth) {
        // tension towers have a string on each side of the arm: use the one on this span's side
        List<PylonData.Attach> out = new ArrayList<>();
        for (PylonData.Attach at : data.attach) {
            if (at.earth() != earth) {
                continue;
            }
            Vec3 w = PylonData.world(origin, dir, at.pos());
            PylonData.Attach twin = null;
            for (PylonData.Attach o : out) {
                Vec3 ow = PylonData.world(origin, dir, o.pos());
                if (Math.abs(o.pos().x - at.pos().x) < 0.3 && Math.abs(o.pos().y - at.pos().y) < 0.3) {
                    twin = o;
                    if (w.distanceToSqr(towards) < ow.distanceToSqr(towards)) {
                        out.remove(o);
                        out.add(new PylonData.Attach(at.pos(), at.bundle(), at.earth()));
                    }
                    break;
                }
            }
            if (twin == null) {
                out.add(at);
            }
        }
        return out;
    }

    private static void build(PowerLines.Link l, List<Wire> out) {
        PylonData da = PylonData.get(l.typeA()), db = PylonData.get(l.typeB());
        Vec3 ca = Vec3.atBottomCenterOf(l.a()), cb = Vec3.atBottomCenterOf(l.b());
        Vec3 along = cb.subtract(ca).multiply(1, 0, 1).normalize();
        Vec3 lateral = new Vec3(-along.z, 0, along.x);
        double span = cb.distanceTo(ca);
        for (boolean earth : new boolean[]{false, true}) {
            List<PylonData.Attach> pa = facing(da, l.a(), l.facingA(), cb, earth);
            List<PylonData.Attach> pb = new ArrayList<>(facing(db, l.b(), l.facingB(), ca, earth));
            for (PylonData.Attach at : pa) {
                Vec3 wa = PylonData.world(l.a(), l.facingA(), at.pos());
                // match by side of the line and height: the conductor that lines up best
                double sa = wa.subtract(ca).dot(lateral);
                PylonData.Attach best = null;
                double bestScore = Double.MAX_VALUE;
                for (PylonData.Attach bt : pb) {
                    Vec3 wb = PylonData.world(l.b(), l.facingB(), bt.pos());
                    double sb = wb.subtract(cb).dot(lateral);
                    double score = Math.abs(sa - sb) + 0.5 * Math.abs((wa.y - ca.y) - (wb.y - cb.y));
                    if (score < bestScore) {
                        bestScore = score;
                        best = bt;
                    }
                }
                if (best == null) {
                    continue;
                }
                pb.remove(best);
                Vec3 wb = PylonData.world(l.b(), l.facingB(), best.pos());
                double sag = span * (earth ? 0.025 : 0.035);
                double thick = earth ? 0.035 : 0.05;
                int bundle = Math.max(at.bundle(), best.bundle());
                if (bundle <= 1) {
                    out.add(new Wire(wa, wb, sag, thick));
                } else {
                    double spacing = 0.22;
                    for (int k = 0; k < bundle; k++) {
                        double off = (k - (bundle - 1) / 2.0) * spacing;
                        Vec3 o = lateral.scale(off);
                        out.add(new Wire(wa.add(o), wb.add(o), sag, thick));
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS || wires.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        double maxDist = mc.options.getEffectiveRenderDistance() * 16.0 + 64;
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.leash());
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();
        for (Wire w : wires) {
            Vec3 mid = w.a.add(w.b).scale(0.5);
            if (mid.distanceTo(cam) > maxDist + w.a.distanceTo(w.b) / 2) {
                continue;
            }
            int light = LevelRenderer.getLightColor(level, BlockPos.containing(mid.x, mid.y - w.sag, mid.z));
            drawWire(vc, m, w, light, cam);
        }
        pose.popPose();
        buffers.endBatch(RenderType.leash());
    }

    private static Vec3 at(Wire w, double t) {
        Vec3 p = w.a.add(w.b.subtract(w.a).scale(t));
        return p.add(0, -4 * w.sag * t * (1 - t), 0);
    }

    /** A ribbon that always faces the camera, as a triangle strip (the leash render type). */
    private static void drawWire(VertexConsumer vc, Matrix4f m, Wire w, int light, Vec3 cam) {
        int segs = Math.max(6, Math.min(48, (int) (w.a.distanceTo(w.b) / 3)));
        Vec3 prev = at(w, 0);
        for (int i = 0; i <= segs; i++) {
            double t = i / (double) segs;
            Vec3 p = at(w, t);
            Vec3 next = at(w, Math.min(1, t + 1.0 / segs));
            Vec3 dir = (i == segs ? p.subtract(prev) : next.subtract(p)).normalize();
            Vec3 toCam = cam.subtract(p).normalize();
            Vec3 side = dir.cross(toCam);
            if (side.lengthSqr() < 1e-6) {
                side = new Vec3(0, 1, 0);
            }
            side = side.normalize().scale(w.thickness / 2 * Math.max(1, cam.distanceTo(p) / 40));
            Vec3 l = p.subtract(side), r = p.add(side);
            int c = 38;
            if (i == 0) {
                // degenerate start so consecutive wires don't join up in the strip
                vc.vertex(m, (float) l.x, (float) l.y, (float) l.z).color(c, c, c + 2, 255).uv2(light).endVertex();
            }
            vc.vertex(m, (float) l.x, (float) l.y, (float) l.z).color(c, c, c + 2, 255).uv2(light).endVertex();
            vc.vertex(m, (float) r.x, (float) r.y, (float) r.z).color(c, c, c + 2, 255).uv2(light).endVertex();
            if (i == segs) {
                vc.vertex(m, (float) r.x, (float) r.y, (float) r.z).color(c, c, c + 2, 255).uv2(light).endVertex();
            }
            prev = p;
        }
    }
}
