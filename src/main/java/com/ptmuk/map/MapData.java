package com.ptmuk.map;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;

/** What the map shows besides the picture: places, areas, named roads and the buses and cars on the move. */
public final class MapData {
    public enum Kind { SHOP, FUEL, STOP, STATION, PARKING }

    public enum AreaKind { DISTRICT, TRAFFIC }

    public record Place(Kind kind, String name, int x, int z) {
    }

    /** A rectangle: a PTM2 district (named) or a congestion zone (level is how busy). */
    public record Area(AreaKind kind, String name, int x0, int z0, int x1, int z1, int level) {
    }

    /** A named road, down its middle. */
    public record Road(String name, float[] x, float[] z) {
    }

    /** A PTM2 vehicle: type 0 car, 1 bus, 2 anything else; label is its route number, if it has one. */
    public record Vehicle(int type, String label, float x, float z) {
    }

    public final List<Place> places = new ArrayList<>();
    public final List<Area> areas = new ArrayList<>();
    public final List<Road> roads = new ArrayList<>();
    public final List<Vehicle> vehicles = new ArrayList<>();

    public void write(FriendlyByteBuf buf) {
        buf.writeCollection(places, (b, p) -> {
            b.writeEnum(p.kind());
            b.writeUtf(p.name(), 64);
            b.writeInt(p.x());
            b.writeInt(p.z());
        });
        buf.writeCollection(areas, (b, a) -> {
            b.writeEnum(a.kind());
            b.writeUtf(a.name(), 64);
            b.writeInt(a.x0());
            b.writeInt(a.z0());
            b.writeInt(a.x1());
            b.writeInt(a.z1());
            b.writeVarInt(a.level());
        });
        buf.writeCollection(roads, (b, r) -> {
            b.writeUtf(r.name(), 64);
            b.writeVarInt(r.x().length);
            for (int i = 0; i < r.x().length; i++) {
                b.writeFloat(r.x()[i]);
                b.writeFloat(r.z()[i]);
            }
        });
        buf.writeCollection(vehicles, (b, v) -> {
            b.writeVarInt(v.type());
            b.writeUtf(v.label(), 16);
            b.writeFloat(v.x());
            b.writeFloat(v.z());
        });
    }

    public static MapData read(FriendlyByteBuf buf) {
        MapData d = new MapData();
        d.places.addAll(buf.readList(b -> new Place(b.readEnum(Kind.class), b.readUtf(64), b.readInt(), b.readInt())));
        d.areas.addAll(buf.readList(b -> new Area(b.readEnum(AreaKind.class), b.readUtf(64), b.readInt(), b.readInt(), b.readInt(), b.readInt(),
                b.readVarInt())));
        d.roads.addAll(buf.readList(b -> {
            String name = b.readUtf(64);
            int n = Math.min(b.readVarInt(), 4096);
            float[] x = new float[n], z = new float[n];
            for (int i = 0; i < n; i++) {
                x[i] = b.readFloat();
                z[i] = b.readFloat();
            }
            return new Road(name, x, z);
        }));
        d.vehicles.addAll(buf.readList(b -> new Vehicle(b.readVarInt(), b.readUtf(16), b.readFloat(), b.readFloat())));
        return d;
    }
}
