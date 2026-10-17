package com.ptmuk.road;

import com.ptmuk.road.plan.RoadPlan;
import com.ptmuk.road.plan.RoadPlan.Junction;
import com.ptmuk.road.plan.RoadPlan.MapImport;
import com.ptmuk.road.plan.RoadPlan.Node;
import com.ptmuk.road.plan.RoadStyle;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * A real map, exported from OpenStreetMap (.osm), read for its roads and buildings and laid out
 * at a scale. Roads go into a road plan in the matching UK style, with roundabouts, mini
 * roundabouts and traffic lights as junctions and the pavements widened or narrowed to meet the
 * building fronts (the buildings are the most reliable part of the map). Buildings come out as
 * outlines with a height from their floors. Plain Java, shared by the planner and the game tests.
 */
public final class OsmMap {
    /** One building: its outline in blocks (before any nudge) and how tall it is. */
    public record Outline(double[] x, double[] z, int height) {
    }

    private record Way(long id, long[] nodes, Map<String, String> tags) {
        String tag(String key) {
            return tags.getOrDefault(key, "");
        }

        boolean closed() {
            return nodes.length > 3 && nodes[0] == nodes[nodes.length - 1];
        }
    }

    private record Relation(Map<String, String> tags, List<Long> outers) {
    }

    private static final Set<String> ROADS = Set.of("motorway", "trunk", "primary", "secondary", "tertiary", "unclassified",
            "residential", "living_street", "service", "road", "motorway_link", "trunk_link", "primary_link", "secondary_link",
            "tertiary_link");
    private static final Set<String> MAJOR = Set.of("motorway", "trunk", "primary", "secondary", "tertiary");
    /** A real storey, floor to floor. */
    private static final double STOREY_M = 2.8;

    private final Map<Long, double[]> nodes = new HashMap<>();          // lat, lon
    private final Map<Long, String> pointHighway = new HashMap<>();     // traffic_signals, mini_roundabout
    private final List<Way> ways = new ArrayList<>();
    private final List<Relation> relations = new ArrayList<>();
    public double minLat = 90, maxLat = -90, minLon = 180, maxLon = -180;

    private OsmMap() {
    }

    // ------------------------------------------------------------------ reading

    public static OsmMap read(InputStream in) throws XMLStreamException {
        XMLInputFactory f = XMLInputFactory.newFactory();
        f.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        f.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        XMLStreamReader r = f.createXMLStreamReader(in);
        OsmMap m = new OsmMap();
        boolean bounds = false;
        long id = 0;
        List<Long> refs = null;
        Map<String, String> tags = null;
        while (r.hasNext()) {
            int e = r.next();
            if (e == XMLStreamConstants.START_ELEMENT) {
                switch (r.getLocalName()) {
                    case "bounds" -> {
                        bounds = true;
                        m.minLat = num(r, "minlat");
                        m.maxLat = num(r, "maxlat");
                        m.minLon = num(r, "minlon");
                        m.maxLon = num(r, "maxlon");
                    }
                    case "node" -> {
                        id = Long.parseLong(r.getAttributeValue(null, "id"));
                        double lat = num(r, "lat"), lon = num(r, "lon");
                        m.nodes.put(id, new double[]{lat, lon});
                        if (!bounds) {
                            m.minLat = Math.min(m.minLat, lat);
                            m.maxLat = Math.max(m.maxLat, lat);
                            m.minLon = Math.min(m.minLon, lon);
                            m.maxLon = Math.max(m.maxLon, lon);
                        }
                        tags = new HashMap<>();
                    }
                    case "way", "relation" -> {
                        id = Long.parseLong(r.getAttributeValue(null, "id"));
                        refs = new ArrayList<>();
                        tags = new HashMap<>();
                    }
                    case "nd" -> {
                        if (refs != null) {
                            refs.add(Long.parseLong(r.getAttributeValue(null, "ref")));
                        }
                    }
                    case "member" -> {
                        if (refs != null && "way".equals(r.getAttributeValue(null, "type")) && "outer".equals(r.getAttributeValue(null, "role"))) {
                            refs.add(Long.parseLong(r.getAttributeValue(null, "ref")));
                        }
                    }
                    case "tag" -> {
                        if (tags != null) {
                            tags.put(r.getAttributeValue(null, "k"), r.getAttributeValue(null, "v"));
                        }
                    }
                    default -> {
                    }
                }
            } else if (e == XMLStreamConstants.END_ELEMENT) {
                switch (r.getLocalName()) {
                    case "node" -> {
                        if (tags != null && tags.containsKey("highway")) {
                            m.pointHighway.put(id, tags.get("highway"));
                        }
                        tags = null;
                    }
                    case "way" -> {
                        m.ways.add(new Way(id, refs.stream().mapToLong(Long::longValue).toArray(), tags));
                        refs = null;
                        tags = null;
                    }
                    case "relation" -> {
                        if ("multipolygon".equals(tags.get("type")) && building(tags)) {
                            m.relations.add(new Relation(tags, refs));
                        }
                        refs = null;
                        tags = null;
                    }
                    default -> {
                    }
                }
            }
        }
        return m;
    }

    private static double num(XMLStreamReader r, String attr) {
        return Double.parseDouble(r.getAttributeValue(null, attr));
    }

    public double midLat() {
        return (minLat + maxLat) / 2;
    }

    public double midLon() {
        return (minLon + maxLon) / 2;
    }

    // ------------------------------------------------------------------ buildings

    private static boolean building(Map<String, String> tags) {
        String b = tags.get("building");
        return b != null && !b.equals("no");
    }

    public List<Outline> buildings(MapImport mi) {
        List<Outline> out = new ArrayList<>();
        Map<Long, Way> byId = new HashMap<>();
        for (Way w : ways) {
            byId.put(w.id(), w);
            if (building(w.tags()) && w.closed()) {
                outline(out, w.nodes(), w.tags(), mi);
            }
        }
        for (Relation rel : relations) {
            for (long id : rel.outers()) {
                Way w = byId.get(id);
                // shortcut: only outer rings drawn as one closed way; ones split over several ways are skipped
                if (w != null && w.closed()) {
                    outline(out, w.nodes(), rel.tags(), mi);
                }
            }
        }
        return out;
    }

    private void outline(List<Outline> out, long[] ids, Map<String, String> tags, MapImport mi) {
        double[] x = new double[ids.length], z = new double[ids.length];
        for (int i = 0; i < ids.length; i++) {
            double[] ll = nodes.get(ids[i]);
            if (ll == null) {
                return;                                     // part of it is outside the export
            }
            x[i] = mi.x(ll[1]);
            z[i] = mi.z(ll[0]);
        }
        out.add(new Outline(x, z, height(tags, mi.scale)));
    }

    /** Blocks tall: from its height, or its floors, or a guess from what kind of building it is. */
    static int height(Map<String, String> tags, double scale) {
        double metres = metres(tags.get("height"));
        if (Double.isNaN(metres)) {
            double floors = metres(tags.get("building:levels"));
            if (Double.isNaN(floors)) {
                floors = switch (tags.getOrDefault("building", "")) {
                    case "apartments", "flats" -> 4;
                    case "garage", "garages", "shed", "hut", "kiosk", "bungalow", "roof" -> 1;
                    default -> 2;
                };
            }
            metres = floors * STOREY_M;
        }
        return Math.max(2, (int) Math.round(metres * scale));
    }

    private static double metres(String v) {
        if (v == null) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(v.replace("m", "").trim());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    // ------------------------------------------------------------------ roads

    /**
     * Lays the roads into the plan, keeping to the area x0..x1, z0..z1 (the planner's map), with
     * the buildings (and their nudge) used to fit the pavements. Returns how many roads came in;
     * the plan's new ids run from mi.firstId to mi.lastId.
     */
    public int roads(RoadPlan plan, MapImport mi, List<Outline> buildings, double x0, double z0, double x1, double z1) {
        mi.firstId = plan.nextId;
        double[] clip = {x0, z0, x1, z1};
        Edges edges = new Edges(buildings, mi.nudgeX, mi.nudgeZ);
        List<Way> roads = new ArrayList<>(), rings = new ArrayList<>();
        for (Way w : ways) {
            String hw = w.tag("highway"), service = w.tag("service");
            if (!ROADS.contains(hw) || w.tag("area").equals("yes") || (hw.equals("service") && !service.isEmpty() && !service.equals("alley"))) {
                continue;
            }
            String j = w.tag("junction");
            (j.equals("roundabout") || j.equals("circular") ? rings : roads).add(w);
        }
        // each roundabout (its pieces joined up) becomes one junction in its middle
        Map<Long, Node> ringNode = new HashMap<>();
        Map<Node, Double> ringRadius = new HashMap<>();
        for (Set<Long> ring : rings(rings)) {
            double sx = 0, sz = 0;
            int n = 0;
            for (long id : ring) {
                double[] ll = nodes.get(id);
                if (ll != null) {
                    sx += mi.x(ll[1]);
                    sz += mi.z(ll[0]);
                    n++;
                }
            }
            if (n < 3 || !inside(clip, sx / n, sz / n)) {
                continue;
            }
            double cx = sx / n, cz = sz / n, r = 0;
            for (long id : ring) {
                double[] ll = nodes.get(id);
                if (ll != null) {
                    r += Math.hypot(mi.x(ll[1]) - cx, mi.z(ll[0]) - cz) / n;
                }
            }
            Node c = plan.addNode(Math.floor(cx), Math.floor(cz));
            c.junction = r < 6 ? Junction.MINI_ROUNDABOUT : Junction.ROUNDABOUT;
            c.radius = r < 6 ? 0 : (int) Math.max(10, Math.min(30, Math.round(r + 3)));
            ringRadius.put(c, r);
            for (long id : ring) {
                ringNode.put(id, c);
            }
        }
        // points shared by roads, and their ends, are junctions (or road ends)
        Map<Long, Integer> uses = new HashMap<>();
        for (Way w : roads) {
            long[] ids = w.nodes();
            for (int i = 0; i < ids.length; i++) {
                uses.merge(ids[i], i == 0 || i == ids.length - 1 ? 2 : 1, Integer::sum);
            }
        }
        Map<Long, Node> joins = new HashMap<>();
        Set<String> done = new HashSet<>();
        int count = 0;
        for (Way w : roads) {
            double[][] p = project(w.nodes(), mi);
            if (p == null) {
                continue;
            }
            RoadStyle st = style(w, p, edges);
            int start = 0;
            long[] ids = w.nodes();
            for (int i = 1; i < ids.length; i++) {
                if (i == ids.length - 1 || uses.getOrDefault(ids[i], 0) >= 2 || ringNode.containsKey(ids[i])) {
                    count += piece(plan, w, p, start, i, st, joins, ringNode, ringRadius, clip, done);
                    start = i;
                }
            }
        }
        // junctions: traffic lights at the junction nearest each signal, mini roundabouts tagged on a point
        Map<Integer, Integer> arms = new HashMap<>();
        for (RoadPlan.Segment s : plan.segments) {
            if (s.id >= mi.firstId) {
                arms.merge(s.a, 1, Integer::sum);
                arms.merge(s.b, 1, Integer::sum);
            }
        }
        for (Map.Entry<Long, String> e : pointHighway.entrySet()) {
            double[] ll = nodes.get(e.getKey());
            if (e.getValue().equals("mini_roundabout") && joins.containsKey(e.getKey())) {
                joins.get(e.getKey()).junction = Junction.MINI_ROUNDABOUT;
            } else if (e.getValue().equals("traffic_signals") && ll != null) {
                Node best = null;
                double bestD = 30;
                for (Node n : plan.nodes) {
                    double d = Math.hypot(n.x - mi.x(ll[1]), n.z - mi.z(ll[0]));
                    if (n.id >= mi.firstId && arms.getOrDefault(n.id, 0) >= 3 && d < bestD && n.junction == Junction.AUTO) {
                        best = n;
                        bestD = d;
                    }
                }
                if (best != null) {
                    best.junction = Junction.SIGNALS;
                }
            }
        }
        // a roundabout no road reached is left out
        plan.nodes.removeIf(n -> n.id >= mi.firstId && ringRadius.containsKey(n) && !arms.containsKey(n.id));
        mi.lastId = plan.nextId - 1;
        return count;
    }

    private double[][] project(long[] ids, MapImport mi) {
        double[][] p = new double[2][ids.length];
        for (int i = 0; i < ids.length; i++) {
            double[] ll = nodes.get(ids[i]);
            if (ll == null) {
                return null;
            }
            p[0][i] = mi.x(ll[1]);
            p[1][i] = mi.z(ll[0]);
        }
        return p;
    }

    private static boolean inside(double[] clip, double x, double z) {
        return x >= clip[0] && x <= clip[2] && z >= clip[1] && z <= clip[3];
    }

    /** One stretch of road between two junctions: its bends kept where it curves, joined at each end. */
    private int piece(RoadPlan plan, Way w, double[][] p, int a, int b, RoadStyle st, Map<Long, Node> joins,
                      Map<Long, Node> ringNode, Map<Node, Double> ringRadius, double[] clip, Set<String> done) {
        boolean in = false;
        for (int k = a; k <= b; k++) {
            in |= inside(clip, p[0][k], p[1][k]);
        }
        if (!in) {
            return 0;
        }
        boolean[] keep = simplify(p[0], p[1], a, b, 1.5);
        Node ringA = ringNode.get(w.nodes()[a]), ringB = ringNode.get(w.nodes()[b]);
        Node prev = null;
        int count = 0;
        for (int k = a; k <= b; k++) {
            if (!keep[k]) {
                continue;
            }
            long id = w.nodes()[k];
            double x = p[0][k], z = p[1][k];
            Node node = ringNode.get(id);
            if (node == null && (k == a || k == b)) {
                node = joins.computeIfAbsent(id, i -> plan.addNode(Math.floor(x), Math.floor(z)));
            } else if (node == null) {
                if (near(ringA, ringRadius, x, z) || near(ringB, ringRadius, x, z)) {
                    continue;                               // the flare where it meets a roundabout
                }
                node = plan.addNode(Math.floor(x), Math.floor(z));
            }
            if (prev != null && prev != node) {
                String key = Math.min(prev.id, node.id) + "-" + Math.max(prev.id, node.id);
                if (done.add(key)) {
                    plan.addSegment(prev.id, node.id, st);
                    count++;
                }
            }
            prev = node;
        }
        return count;
    }

    private static boolean near(Node ring, Map<Node, Double> radius, double x, double z) {
        return ring != null && Math.hypot(x - ring.x, z - ring.z) < radius.get(ring) + 4;
    }

    /** Roundabout pieces that share points, grouped into whole rings (their points). */
    private static List<Set<Long>> rings(List<Way> pieces) {
        Map<Long, List<Way>> at = new HashMap<>();
        for (Way w : pieces) {
            for (long id : w.nodes()) {
                at.computeIfAbsent(id, k -> new ArrayList<>()).add(w);
            }
        }
        List<Set<Long>> out = new ArrayList<>();
        Set<Way> seen = new HashSet<>();
        for (Way start : pieces) {
            if (!seen.add(start)) {
                continue;
            }
            Set<Long> ring = new HashSet<>();
            Deque<Way> todo = new ArrayDeque<>(List.of(start));
            while (!todo.isEmpty()) {
                for (long id : todo.poll().nodes()) {
                    if (ring.add(id)) {
                        for (Way o : at.get(id)) {
                            if (seen.add(o)) {
                                todo.add(o);
                            }
                        }
                    }
                }
            }
            out.add(ring);
        }
        return out;
    }

    /** Douglas-Peucker: which points of a..b to keep so the line stays within tol of the original. */
    static boolean[] simplify(double[] x, double[] z, int a, int b, double tol) {
        boolean[] keep = new boolean[x.length];
        keep[a] = keep[b] = true;
        Deque<int[]> todo = new ArrayDeque<>();
        todo.push(new int[]{a, b});
        while (!todo.isEmpty()) {
            int[] r = todo.pop();
            double dx = x[r[1]] - x[r[0]], dz = z[r[1]] - z[r[0]], len = Math.hypot(dx, dz);
            int best = -1;
            double bestD = tol;
            for (int k = r[0] + 1; k < r[1]; k++) {
                double d = len < 1e-9 ? Math.hypot(x[k] - x[r[0]], z[k] - z[r[0]])
                        : Math.abs((x[k] - x[r[0]]) * dz - (z[k] - z[r[0]]) * dx) / len;
                if (d > bestD) {
                    bestD = d;
                    best = k;
                }
            }
            if (best > 0) {
                keep[best] = true;
                todo.push(new int[]{r[0], best});
                todo.push(new int[]{best, r[1]});
            }
        }
        return keep;
    }

    /** The UK style nearest the real road, its pavements reaching the building fronts. */
    private static RoadStyle style(Way w, double[][] p, Edges edges) {
        String hw = w.tag("highway");
        boolean oneway = w.tag("oneway").equals("yes") || w.tag("oneway").equals("1") || hw.equals("motorway");
        int lanes = (int) Math.max(0, Math.min(6, nanToZero(metres(w.tags().get("lanes")))));
        boolean major = MAJOR.contains(hw);
        RoadStyle st;
        if (hw.endsWith("_link")) {
            st = RoadStyle.slipRoad();
        } else if (oneway && major) {
            st = RoadStyle.slipRoad();                      // one side of a dual carriageway
            st.name = "Dual carriageway (one side)";
            st.lanes = lanes > 0 ? Math.min(lanes, 3) : 2;
        } else if (oneway) {
            st = RoadStyle.oneWayStreet();
            st.lanes = lanes > 0 ? Math.min(lanes, 3) : 1;
        } else if (major) {
            st = lanes >= 4 ? RoadStyle.wideMainRoad() : RoadStyle.mainRoad();
        } else {
            st = RoadStyle.residential();
        }
        if (st.pavement > 0) {
            fitPavement(st, p, edges);
        }
        return st;
    }

    private static double nanToZero(double v) {
        return Double.isNaN(v) ? 0 : v;
    }

    /** Pavements out to the building fronts either side (the nearer side decides), when there are buildings close by. */
    private static void fitPavement(RoadStyle st, double[][] p, Edges edges) {
        double hc = st.halfCarriageway(), reach = hc + 12;
        List<Double> left = new ArrayList<>(), right = new ArrayList<>();
        for (int i = 0; i + 1 < p[0].length; i++) {
            double dx = p[0][i + 1] - p[0][i], dz = p[1][i + 1] - p[1][i], len = Math.hypot(dx, dz);
            if (len < 1e-6) {
                continue;
            }
            dx /= len;
            dz /= len;
            for (double s = 3; s < len - 3; s += 6) {
                double x = p[0][i] + dx * s, z = p[1][i] + dz * s;
                double l = edges.ray(x, z, dz, -dx, reach), r = edges.ray(x, z, -dz, dx, reach);
                if (!Double.isNaN(l)) {
                    left.add(l);
                }
                if (!Double.isNaN(r)) {
                    right.add(r);
                }
            }
        }
        double gap = Math.min(median(left), median(right));
        if (gap < Double.MAX_VALUE) {
            st.pavement = (int) Math.max(2, Math.min(8, Math.floor(gap - hc)));
        }
    }

    private static double median(List<Double> v) {
        if (v.size() < 2) {
            return Double.MAX_VALUE;
        }
        Collections.sort(v);
        return v.get(v.size() / 2);
    }

    /** Building walls, bucketed on a grid so rays only test the walls near them. */
    private static final class Edges {
        private static final int CELL = 16;
        private final Map<Long, List<double[]>> grid = new HashMap<>();

        Edges(List<Outline> outlines, int nx, int nz) {
            for (Outline o : outlines) {
                for (int i = 0; i + 1 < o.x().length; i++) {
                    double[] e = {o.x()[i] + nx, o.z()[i] + nz, o.x()[i + 1] + nx, o.z()[i + 1] + nz};
                    for (int cx = cell(Math.min(e[0], e[2])); cx <= cell(Math.max(e[0], e[2])); cx++) {
                        for (int cz = cell(Math.min(e[1], e[3])); cz <= cell(Math.max(e[1], e[3])); cz++) {
                            grid.computeIfAbsent(key(cx, cz), k -> new ArrayList<>()).add(e);
                        }
                    }
                }
            }
        }

        private static int cell(double v) {
            return (int) Math.floor(v / CELL);
        }

        private static long key(int cx, int cz) {
            return ((long) cx << 32) ^ (cz & 0xffffffffL);
        }

        /** How far along (dx, dz) from x, z the first wall is, up to max, or NaN. */
        double ray(double x, double z, double dx, double dz, double max) {
            double best = Double.NaN;
            for (int cx = cell(Math.min(x, x + dx * max)); cx <= cell(Math.max(x, x + dx * max)); cx++) {
                for (int cz = cell(Math.min(z, z + dz * max)); cz <= cell(Math.max(z, z + dz * max)); cz++) {
                    for (double[] e : grid.getOrDefault(key(cx, cz), List.of())) {
                        double ex = e[2] - e[0], ez = e[3] - e[1];
                        double den = dx * ez - dz * ex;
                        if (Math.abs(den) < 1e-9) {
                            continue;
                        }
                        double ax = e[0] - x, az = e[1] - z;
                        double t = (ax * ez - az * ex) / den, u = (ax * dz - az * dx) / den;
                        if (t >= 0 && t <= max && u >= 0 && u <= 1 && !(t >= best)) {
                            best = t;
                        }
                    }
                }
            }
            return best;
        }
    }
}
