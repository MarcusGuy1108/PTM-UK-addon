package com.ptmuk.map;

import com.ptmuk.PtmUk;
import com.rinventor.ptm2.core.properties.VDMemoryIDs;
import com.rinventor.ptm2.core.properties.VehicleTypes;
import com.rinventor.ptm2.dimension.virtual.storage.VDStorageHandler;
import com.rinventor.ptm2.dimension.virtual.types.VDCongestionZone;
import com.rinventor.ptm2.dimension.virtual.types.VDDistrict;
import com.rinventor.ptm2.dimension.virtual.types.VDParkingZone;
import com.rinventor.ptm2.dimension.virtual.types.VDPlatform;
import com.rinventor.ptm2.dimension.virtual.types.VDStation;
import com.rinventor.ptm2.dimension.virtual.types.VDTrackableVehicle;
import com.rinventor.ptm2.dimension.virtual.types.VDTransportLine;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.server.level.ServerLevel;

/**
 * PTM2's side of the map, read straight from PTM2's own records: its stations and stops, its
 * districts (as area names), car parks, congestion zones and the buses and cars on the move.
 * Anything PTM2 can't give (an older or newer PTM2) is just left off.
 */
final class Ptm2Map {
    private static boolean warned;

    private Ptm2Map() {
    }

    static void add(ServerLevel level, double px, double pz, int reach, MapData d) {
        try {
            Map<Integer, String> stationNames = new HashMap<>();
            each(level, VDMemoryIDs.STATION, VDStation::new, (id, s) -> {
                stationNames.put(id, s.name);
                if (close(s.getCenterX(), s.getCenterZ(), px, pz, reach) && s.name != null && !s.name.isBlank()) {
                    d.places.add(new MapData.Place(MapData.Kind.STATION, s.name, s.getCenterX(), s.getCenterZ()));
                }
            });
            each(level, VDMemoryIDs.PLATFORM, VDPlatform::new, (id, p) -> {
                String name = p.name != null && !p.name.isBlank() ? p.name : stationNames.getOrDefault(p.stationID, "Bus stop");
                if (close(p.getCenterX(), p.getCenterZ(), px, pz, reach)) {
                    d.places.add(new MapData.Place(MapData.Kind.STOP, name, p.getCenterX(), p.getCenterZ()));
                }
            });
            each(level, VDMemoryIDs.DISTRICT, VDDistrict::new, (id, a) -> {
                if (a.name != null && !a.name.isBlank() && close(a.getCenterX(), a.getCenterZ(), px, pz, reach)) {
                    d.areas.add(new MapData.Area(MapData.AreaKind.DISTRICT, a.name, Math.min(a.sbX, a.fbX), Math.min(a.sbZ, a.fbZ),
                            Math.max(a.sbX, a.fbX), Math.max(a.sbZ, a.fbZ), 0));
                }
            });
            each(level, VDMemoryIDs.PARKING_ZONE, VDParkingZone::new, (id, a) -> {
                int cx = (a.sbX + a.fbX) / 2, cz = (a.sbZ + a.fbZ) / 2;
                if (close(cx, cz, px, pz, reach)) {
                    d.places.add(new MapData.Place(MapData.Kind.PARKING, "Car park (" + a.currentCapacity + "/" + a.maxCapacity + ")", cx, cz));
                }
            });
            each(level, VDMemoryIDs.TRAFFIC_CONGESTION, VDCongestionZone::new, (id, a) -> {
                if (close(a.getCenterX(), a.getCenterZ(), px, pz, reach)) {
                    d.areas.add(new MapData.Area(MapData.AreaKind.TRAFFIC, "Traffic", Math.min(a.sbX, a.fbX), Math.min(a.sbZ, a.fbZ),
                            Math.max(a.sbX, a.fbX), Math.max(a.sbZ, a.fbZ), a.congestion));
                }
            });
            Map<Integer, String> lines = new HashMap<>();
            each(level, VDMemoryIDs.TRANSPORT_LINE, VDTransportLine::new, (id, l) -> lines.put(id, l.number == null ? "" : l.number));
            for (String data : VDStorageHandler.getStrs(level, VDMemoryIDs.TRACKABLE_VEHICLE).values()) {
                VDTrackableVehicle v = parse(data, VDTrackableVehicle::new);
                if (v == null || v.position == null || !close(v.position.x, v.position.z, px, pz, reach)) {
                    continue;
                }
                int type = v.type == VehicleTypes.BUS || v.type == VehicleTypes.TROLLEYBUS ? 1
                        : v.type == VehicleTypes.CAR || v.type == VehicleTypes.TAXI ? 0 : 2;
                d.vehicles.add(new MapData.Vehicle(type, lines.getOrDefault(v.lineID, ""), (float) v.position.x, (float) v.position.z));
            }
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                PtmUk.LOGGER.warn("Couldn't read PTM2's stops and zones for the map", t);
            }
        }
    }

    private static boolean close(double x, double z, double px, double pz, int reach) {
        return Math.abs(x - px) < reach && Math.abs(z - pz) < reach;
    }

    /** Each of PTM2's records of a kind, with its id. */
    private static <T> void each(ServerLevel level, VDMemoryIDs kind, Function<String, T> make, BiConsumer<Integer, T> use) {
        for (Map.Entry<Integer, String> e : VDStorageHandler.getAll(level, kind).entrySet()) {
            T t = parse(e.getValue(), make);
            if (t != null) {
                use.accept(e.getKey(), t);
            }
        }
    }

    private static <T> T parse(String data, Function<String, T> make) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        try {
            return make.apply(data);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
