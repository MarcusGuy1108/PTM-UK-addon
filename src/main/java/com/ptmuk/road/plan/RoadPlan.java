package com.ptmuk.road.plan;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * A road network drawn in the Road Planner: points (nodes) joined by roads (segments). A node
 * where three or more roads meet is a junction; a node can also be made a roundabout. Kept as
 * plain data so the planner screen, the item that remembers it and the server builder all share
 * it (sent between them as JSON).
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
    }

    public List<Node> nodes = new ArrayList<>();
    public List<Segment> segments = new ArrayList<>();
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
        nodes.removeIf(n -> n == null);
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
