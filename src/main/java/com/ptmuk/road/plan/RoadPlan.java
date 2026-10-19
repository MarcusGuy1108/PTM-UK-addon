package com.ptmuk.road.plan;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * A road network drawn in the Road Planner: points (nodes) joined by roads (segments). A node
 * where three or more roads meet is a junction; a node can also be made a roundabout. Sites
 * (car parks, retail parks, drive-thrus) are rectangles laid out round the roads that reach
 * them. Kept as plain data so the planner screen, the item that remembers it and the server
 * builder all share it (sent between them as JSON).
 */
public final class RoadPlan {
    public enum Junction {
        /** Give way where 3+ roads meet, nothing special otherwise. */
        AUTO("Automatic"),
        GIVE_WAY("Give way"),
        STOP("Stop"),
        MINI_ROUNDABOUT("Mini roundabout"),
        ROUNDABOUT("Roundabout"),
        SIGNALS("Traffic lights"),
        /** A slip road joining or leaving the side of another road without a junction across it. */
        MERGE("Slip road merge");

        public final String title;

        Junction(String title) {
            this.title = title;
        }
    }

    public static final class Node {
        public int id;
        /** Position in blocks; the middle of a block is x + 0.5. */
        public double x, z;
        /** Height of the road surface here, or NaN to take it from the ground. */
        public double y = Double.NaN;
        public Junction junction = Junction.AUTO;
        /** Roundabout: outer radius of the circulating carriageway (0 = sized to the roads). */
        public int radius;
        /** Give way / stop junctions: the two roads that carry straight on (segment ids), or -1 to choose. */
        public int majorA = -1, majorB = -1;
        public boolean built;
    }

    public static final class Segment {
        public int id;
        public int a, b;
        /** How far the middle of the road is pushed sideways from a straight line (+ = to the left going a to b). */
        public double bend;
        public RoadStyle style = RoadStyle.mainRoad();
        public boolean built;
        /** The road's real name, from a map import (it goes on the world map when built). */
        public String name = "";
    }

    public enum SiteType {
        CAR_PARK("Car park"),
        RETAIL_PARK("Retail park"),
        DRIVE_THRU("Drive-thru"),
        PARADE("Shop parade"),
        TERRACES("Terraced houses"),
        SEMIS("Semi-detached houses"),
        DETACHED("Detached houses"),
        CIVIC("Church, pub and surgery"),
        /** Not built itself: laid out into roads and the sites above (TownLayout). */
        TOWN("Town"),
        /** Grass level with the roads and buildings round it, filling whatever they leave. */
        GREEN("Green space"),
        /** Left as it is: a town lays nothing out here and the builder doesn't touch the ground. */
        KEEP_CLEAR("Keep clear");

        public final String title;

        SiteType(String title) {
            this.title = title;
        }

        /** Laid out on the ground itself (a road ending at it meets it level); not a town, green or area kept clear. */
        public boolean ground() {
            return this != TOWN && this != GREEN && this != KEEP_CLEAR;
        }
    }

    /** A rectangle of ground laid out as a car park, a retail park or a drive-thru. */
    public static final class Site {
        public int id;
        public SiteType type = SiteType.CAR_PARK;
        /** Corners in blocks, both inclusive. */
        public int x0, z0, x1, z1;
        /** Height of the surface, or NaN to take it from the ground. */
        public double y = Double.NaN;
        /** The side facing the road (0 south, 1 west, 2 north, 3 east), or -1 for the side a road reaches. */
        public int front = -1;
        /** Bays in rows towards the front (aisles run front to back), across it, or -1 to choose. */
        public int rows = -1;
        public int disabled = 2, parentChild = 2, ev = 2;
        /** 0 no trees, 1 at the row ends, 2 also every few bays along the rows. */
        public int trees = 1;
        public boolean lights = true;
        /** Retail park: how many shop units (0 = as many as fit) and how deep they are. */
        public int units, unitDepth = 28;
        public boolean canopy = true;
        /** Which made-up brand names to use (cycled). */
        public int brand;
        /** Town: a main road through it, shops along its middle, a church, pub and surgery, and which houses (0 terraces, 1 semis, 2 detached, 3 a mix). */
        public boolean mainRoad = true, highStreet = true, civic = true;
        public int houses = 3;
        /** Town: kerbs on its roads, and winding streets (else a grid). */
        public boolean kerbs = true, winding = true;
        public boolean built;

        public int width() {
            return x1 - x0 + 1;
        }

        public int depth() {
            return z1 - z0 + 1;
        }

        public boolean contains(double x, double z) {
            return x >= x0 && x < x1 + 1 && z >= z0 && z < z1 + 1;
        }
    }

    /**
     * A real map (an OpenStreetMap export) laid over the plan: which file, the point of it that
     * sits at block x, z, the scale, how far its buildings are nudged to line up with the roads,
     * and which ids its roads were given (so they can be taken out again).
     */
    public static final class MapImport {
        /** Our main road's 6-block lanes against a real 3.3 m lane: 1.8 blocks a metre. */
        public static final double SCALE = 6 / 3.3;

        public String file;
        public double lat0, lon0;
        public int x, z;
        public double scale = SCALE;
        public int nudgeX, nudgeZ;
        public int firstId, lastId;
        public boolean guides = true;

        private double perLat() {
            double p = Math.toRadians(lat0);
            return 111132.92 - 559.82 * Math.cos(2 * p) + 1.175 * Math.cos(4 * p);
        }

        private double perLon() {
            double p = Math.toRadians(lat0);
            return 111412.84 * Math.cos(p) - 93.5 * Math.cos(3 * p);
        }

        /** Where a real place lands, in blocks (north stays north). */
        public double x(double lon) {
            return x + (lon - lon0) * perLon() * scale;
        }

        public double z(double lat) {
            return z - (lat - lat0) * perLat() * scale;
        }

        public boolean owns(int id) {
            return id >= firstId && id <= lastId;
        }
    }

    public List<Node> nodes = new ArrayList<>();
    public List<Segment> segments = new ArrayList<>();
    public List<Site> sites = new ArrayList<>();
    public MapImport map;
    public int nextId = 1;

    private static final Gson GSON = new GsonBuilder().serializeSpecialFloatingPointValues().create();

    public String toJson() {
        return GSON.toJson(this);
    }

    public static RoadPlan fromJson(String json) {
        if (json == null || json.isEmpty()) {
            return new RoadPlan();
        }
        try {
            RoadPlan p = GSON.fromJson(json, RoadPlan.class);
            return p == null ? new RoadPlan() : p.tidy();
        } catch (RuntimeException e) {
            return new RoadPlan();
        }
    }

    public RoadPlan copy() {
        return fromJson(toJson());
    }

    /** Drop roads whose ends no longer exist and make sure nothing is null. */
    public RoadPlan tidy() {
        if (nodes == null) {
            nodes = new ArrayList<>();
        }
        if (segments == null) {
            segments = new ArrayList<>();
        }
        if (sites == null) {
            sites = new ArrayList<>();
        }
        nodes.removeIf(n -> n == null);
        sites.removeIf(st -> st == null || st.type == null);
        for (Site st : sites) {
            int ax = Math.min(st.x0, st.x1), bx = Math.max(st.x0, st.x1), az = Math.min(st.z0, st.z1), bz = Math.max(st.z0, st.z1);
            st.x0 = ax;
            st.x1 = bx;
            st.z0 = az;
            st.z1 = bz;
            nextId = Math.max(nextId, st.id + 1);
        }
        segments.removeIf(s -> s == null || node(s.a) == null || node(s.b) == null || s.a == s.b);
        for (Node n : nodes) {
            if (n.junction == null) {
                n.junction = Junction.AUTO;
            }
            nextId = Math.max(nextId, n.id + 1);
        }
        for (Segment s : segments) {
            if (s.style == null) {
                s.style = RoadStyle.mainRoad();
            }
            if (s.style.centre == null) {
                s.style.centre = RoadStyle.Centre.DASHED;
            }
            if (s.style.yellow == null) {
                s.style.yellow = s.style.doubleYellow ? RoadStyle.Yellow.DOUBLE : RoadStyle.Yellow.NONE;
            }
            if (s.style.footSurface == null || s.style.footSurface.isEmpty()) {
                s.style.footSurface = RoadStyle.FOOTWAYS.get(0);
            }
            nextId = Math.max(nextId, s.id + 1);
        }
        return this;
    }

    public Node node(int id) {
        for (Node n : nodes) {
            if (n.id == id) {
                return n;
            }
        }
        return null;
    }

    public Segment segment(int id) {
        for (Segment s : segments) {
            if (s.id == id) {
                return s;
            }
        }
        return null;
    }

    public Site site(int id) {
        for (Site s : sites) {
            if (s.id == id) {
                return s;
            }
        }
        return null;
    }

    public Site addSite(SiteType type, int x0, int z0, int x1, int z1) {
        Site s = new Site();
        s.id = nextId++;
        s.type = type;
        s.x0 = Math.min(x0, x1);
        s.x1 = Math.max(x0, x1);
        s.z0 = Math.min(z0, z1);
        s.z1 = Math.max(z0, z1);
        sites.add(s);
        return s;
    }

    public List<Segment> segmentsAt(int nodeId) {
        List<Segment> out = new ArrayList<>();
        for (Segment s : segments) {
            if (s.a == nodeId || s.b == nodeId) {
                out.add(s);
            }
        }
        return out;
    }

    public Node addNode(double x, double z) {
        Node n = new Node();
        n.id = nextId++;
        n.x = x;
        n.z = z;
        nodes.add(n);
        return n;
    }

    public Segment addSegment(int a, int b, RoadStyle style) {
        Segment s = new Segment();
        s.id = nextId++;
        s.a = a;
        s.b = b;
        s.style = style.copy();
        segments.add(s);
        return s;
    }

    /** Remove a node and every road joined to it. */
    public void removeNode(int id) {
        segments.removeIf(s -> s.a == id || s.b == id);
        nodes.removeIf(n -> n.id == id);
    }

    /** Remove a road, and any end left with no roads. */
    public void removeSegment(int id) {
        Segment s = segment(id);
        if (s == null) {
            return;
        }
        segments.remove(s);
        for (int end : new int[]{s.a, s.b}) {
            if (segmentsAt(end).isEmpty()) {
                nodes.removeIf(n -> n.id == end);
            }
        }
    }

    /** Mark everything touching this node as needing building again. */
    public void touch(int nodeId) {
        Node n = node(nodeId);
        if (n != null) {
            n.built = false;
        }
        for (Segment s : segmentsAt(nodeId)) {
            s.built = false;
            for (int end : new int[]{s.a, s.b}) {
                Node e = node(end);
                if (e != null) {
                    e.built = false;
                }
            }
        }
    }
}
