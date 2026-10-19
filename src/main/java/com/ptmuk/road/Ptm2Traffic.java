package com.ptmuk.road;

import com.ptmuk.PtmUk;
import com.ptmuk.road.layout.Layout;
import com.rinventor.ptm2.PTM;
import com.rinventor.ptm2.core.init.ModBlocks;
import com.rinventor.ptm2.core.init.ModExecutors;
import com.rinventor.ptm2.core.properties.IntersectionOperationStyles;
import com.rinventor.ptm2.core.properties.TrafficPointPlacementInfo;
import com.rinventor.ptm2.core.properties.VDMemoryIDs;
import com.rinventor.ptm2.dimension.virtual.storage.VDStorageHandler;
import com.rinventor.ptm2.dimension.virtual.storage.requests.DataAddServerboundPacket;
import com.rinventor.ptm2.dimension.virtual.types.VDIntersection;
import com.rinventor.ptm2.dimension.virtual.types.VDTrafficPoint;
import com.rinventor.ptm2.objects.blockentities.traffic_point.TrafficPoint;
import com.rinventor.ptm2.objects.blocks.RoadMarking;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * PTM2's traffic points along the roads the Road Planner builds, so its AI cars drive them: a
 * point block in the middle of each lane (or just PTM2's record of one, where a road marking
 * already sits there), joined up the way the layout says. PTM2 would try to join new points up
 * itself, but it guesses from its own road blocks and markings (and checks every point in the
 * world for each one placed), so that's paused while ours go in.
 */
final class Ptm2Traffic {
    private Ptm2Traffic() {
    }

    /** The point block for a lane the layout has going the PTM2 way rot8 (turned round if PTM2 drives on the right), on a slab or not; null without PTM2. */
    static BlockState block(ServerLevel level, int rot8, boolean slab) {
        try {
            int facing = leftHanded(level) ? rot8 : (rot8 + 4) % 8;
            return ModBlocks.TRAFFIC_POINT.get().defaultBlockState().setValue(TrafficPoint.ROTATION, facing).setValue(TrafficPoint.SLAB, slab);
        } catch (RuntimeException | LinkageError e) {
            PtmUk.LOGGER.warn("Couldn't make PTM2 traffic points", e);
            return null;
        }
    }

    /** A PTM2 road marking, which keeps a traffic point's record as it is (so the marking stays). */
    static boolean onMarking(BlockState state) {
        return state.getBlock() instanceof RoadMarking;
    }

    static boolean isPoint(BlockState state) {
        return state.getBlock() instanceof TrafficPoint;
    }

    /** Stop PTM2 joining new points up itself; returns its setting, to put back. */
    // shortcut: if the server stops mid-build it stays off until switched back in PTM2's road settings; restore it on load if anyone hits that
    static int pause(ServerLevel level) {
        int was = PTM.DATA.get(level).TRAFFIC_POINTS_CONNECTING;
        PTM.DATA.get(level).TRAFFIC_POINTS_CONNECTING = 0;
        return was;
    }

    /** Whether PTM2 has its traffic driving on the left in this world. */
    static boolean leftHanded(ServerLevel level) {
        return PTM.DATA.get(level).LEFT_TRAFFIC;
    }

    /**
     * Give each point placed its connections (to points at at[n], placed now or by an earlier
     * build) and put PTM2's setting back, once PTM2 has dealt with the blocks just placed: its
     * traffic jobs run one at a time in order, so this queues behind them. The layout keeps left;
     * where PTM2 drives on the right every point is turned round and every connection reversed,
     * as PTM2 does itself when switched over, so switching it to the left later puts them back.
     */
    static void connect(ServerLevel level, List<Layout.TrafficPoint> points, BlockPos[] at, boolean[] placed, int connecting) {
        ModExecutors.runTrafficOp(() -> level.getServer().execute(() -> {
            PTM.DATA.get(level).TRAFFIC_POINTS_CONNECTING = connecting;
            PTM.DATA.setDirty(level);
            boolean left = leftHanded(level);
            List<List<Integer>> into = new ArrayList<>();
            for (int i = 0; i < points.size(); i++) {
                into.add(new ArrayList<>());
            }
            for (int i = 0; i < points.size(); i++) {
                for (int n : points.get(i).next()) {
                    into.get(n).add(i);
                }
            }
            for (int i = 0; i < at.length; i++) {
                if (!placed[i]) {
                    continue;
                }
                int rot8 = left ? points.get(i).rot8() : (points.get(i).rot8() + 4) % 8;
                VDTrafficPoint p = new VDTrafficPoint(rot8, TrafficPointPlacementInfo.ROAD.getID(), false);
                for (int n : left ? points.get(i).next() : into.get(i)) {
                    p.connections.add(new Vec3(at[n].getX(), at[n].getY(), at[n].getZ()));
                }
                VDStorageHandler.update(level, at[i], p.toString());
            }
        }));
    }

    /**
     * PTM2's junction area over a give way junction or roundabout: its cars don't start off in the
     * middle of one, know a roundabout when they reach it, and take turns through the rest (PTM2
     * sorts out who goes at a junction with no lights or signs, and does it better than with give
     * way signs, which only hold cars back for others already moving). With no lights it must
     * never count as "operating", so its hours never come.
     */
    static void junction(ServerLevel level, int x0, int z0, int x1, int z1, boolean roundabout) {
        try {
            VDIntersection zone = new VDIntersection(x0, z0, x1, z1, "", true, false);
            zone.roundabout = roundabout;
            zone.nightOperationStyle = IntersectionOperationStyles.OFF.getID();
            zone.timeFrom = zone.timeTo = 2400;                // PTM2's clock runs 0000 to 2359
            DataAddServerboundPacket.handle(level, VDStorageHandler.nextID(level, VDMemoryIDs.INTERSECTION), zone.toString());
        } catch (RuntimeException | LinkageError e) {
            PtmUk.LOGGER.warn("Couldn't set up a PTM2 junction for the new road", e);
        }
    }
}
