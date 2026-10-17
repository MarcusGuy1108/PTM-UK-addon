package com.ptmuk.road;

import com.ptmuk.PtmUk;
import com.rinventor.ptm2.core.properties.VDMemoryIDs;
import com.rinventor.ptm2.dimension.virtual.storage.VDStorageHandler;
import com.rinventor.ptm2.dimension.virtual.storage.requests.DataAddServerboundPacket;
import com.rinventor.ptm2.dimension.virtual.types.VDIntersection;
import net.minecraft.server.level.ServerLevel;

/**
 * Sets up PTM2's intersection controller for a traffic-light junction built by the Road Planner:
 * an automatic intersection covering the junction, so the signal heads placed inside it join it
 * and PTM2 cycles them approach by approach (and its AI traffic obeys them).
 */
final class Ptm2Signals {
    private Ptm2Signals() {
    }

    static int intersection(ServerLevel level, int x0, int z0, int x1, int z1) {
        try {
            int id = VDStorageHandler.nextID(level, VDMemoryIDs.INTERSECTION);
            VDIntersection zone = new VDIntersection(x0, z0, x1, z1, "", true, true);
            DataAddServerboundPacket.handle(level, id, zone.toString());
            return id;
        } catch (RuntimeException | LinkageError e) {
            PtmUk.LOGGER.warn("Couldn't set up a PTM2 intersection for the new traffic lights", e);
            return -1;
        }
    }
}
