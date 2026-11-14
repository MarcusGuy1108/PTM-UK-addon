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
    /** A building: its corners (the first again at the end), blocks tall, and what OSM says it is (its building, roof:shape, building:material and roof:material tags, and floors). */
    public record Outline(double[] x, double[] z, int height, String kind, String roof, int floors, String material, String roofMaterial,
                          Surface surface, List<Shop> shops) {
    }

    /** A shop (or café, pub, bank...) in a building: the kind of sign it gets (see ShopNames), its name, and where it's mapped (blocks). */
    public record Shop(String kind, String name, double x, double z) {
    }

    /**
     * A pitched roof's real shape (from LIDAR): its height every 2 blocks from block x0, z0, w
     * points across, in half blocks above the ground; 0 where there's no roof (the ground, or no survey).
     */
    public record Surface(int x0, int z0, int w, byte[] v) {
    }

    private record Way(long id, long[] nodes, Map<String, String> tags) {
        String tag(String key) {
            return tags.getOrDefault(key, "");
        }

        boolean closed() {
            return nodes.length > 3 && nodes[0] == nodes[nodes.length - 1];
        }
    }

    private record Relation(long id, Map<String, String> tags, List<Long> outers) {
    }

    private static final Set<String> ROADS = Set.of("motorway", "trunk", "primary", "secondary", "tertiary", "unclassified",
            "residential", "living_street", "service", "road", "motorway_link", "trunk_link", "primary_link", "secondary_link",
            "tertiary_link");
    private static final Set<String> MAJOR = Set.of("motorway", "trunk", "primary", "secondary", "tertiary");
    /** A real storey, floor to floor. */
    private static final double STOREY_M = 2.8;
    /** Longest stretch of road between two points (blocks). */
    private static final double MAX_STRETCH = 96;

    private final Map<Long, double[]> nodes = new HashMap<>();          // lat, lon
    private final Map<Long, String> pointHighway = new HashMap<>();     // traffic_signals, mini_roundabout
    /** Pedestrian crossings (highway=crossing, or traffic lights that are a crossing's): what kind each is. */
    private final Map<Long, RoadPlan.CrossingType> crossings = new HashMap<>();
    /** Bus stops (highway=bus_stop, or a bus platform): their names. */
    private final Map<Long, String> busStops = new HashMap<>();
    /** Shops, cafés, pubs and the like mapped as a point (in their building): the kind of sign each gets, and its name. */
    private final Map<Long, String[]> pois = new HashMap<>();
    /** Things mapped as a point (trees, benches, bins, pillar boxes, phone boxes, lamps, bollards, bike stands): what each is. */
    private final Map<Long, String> things = new java.util.LinkedHashMap<>();
    private final List<Way> ways = new ArrayList<>();
    private final List<Relation> relations = new ArrayList<>();
    public double minLat = 90, maxLat = -90, minLon = 180, maxLon = -180;
    /** Real heights for the map (England's LIDAR survey), if they've been fetched: each building's height and roof come from them. */
    public Lidar lidar;

    private OsmMap() {
    }

    // ------------------------------------------------------------------ reading

    /**
     * The Overpass API query for what the importer uses in a box (south, west, north, east in
     * degrees): roads, buildings (outlines and multipolygons), bus stops and the junction points,
     * with every node of each, as OSM XML.
     */
    public static String overpassQuery(double s, double w, double n, double e) {
        String box = String.format(java.util.Locale.ROOT, "(%.6f,%.6f,%.6f,%.6f)", s, w, n, e);
        return "[out:xml][timeout:180][maxsize:268435456];("
                + "way[\"highway\"]" + box + ";"
                + "way[\"building\"]" + box + ";"
                + "relation[\"building\"][\"type\"=\"multipolygon\"]" + box + ";"
                + "way[\"amenity\"~\"^(parking|fuel|grave_yard)$\"]" + box + ";"
                + "relation[\"amenity\"=\"parking\"][\"type\"=\"multipolygon\"]" + box + ";"
                // the rest of what's there: water, green, woods, paths (highway, above), hedges, fences, walls, trees, street furniture
                + "way[\"natural\"~\"^(water|wood|tree_row)$\"]" + box + ";"
                + "relation[\"natural\"~\"^(water|wood)$\"][\"type\"=\"multipolygon\"]" + box + ";"
                + "way[\"waterway\"]" + box + ";"
                + "way[\"landuse\"~\"^(grass|forest|recreation_ground|village_green|cemetery|reservoir|basin|meadow|retail|commercial|industrial)$\"]" + box + ";"
                + "relation[\"landuse\"~\"^(grass|forest|recreation_ground|cemetery|reservoir)$\"][\"type\"=\"multipolygon\"]" + box + ";"
                + "way[\"leisure\"~\"^(park|garden|pitch|playground|common|recreation_ground|village_green|dog_park)$\"]" + box + ";"
                + "relation[\"leisure\"~\"^(park|garden|common)$\"][\"type\"=\"multipolygon\"]" + box + ";"
                + "way[\"barrier\"~\"^(hedge|fence|wall|retaining_wall|city_wall)$\"]" + box + ";"
                + "way[\"place\"=\"square\"]" + box + ";"
                + "node[\"natural\"=\"tree\"]" + box + ";"
                + "node[\"amenity\"~\"^(bench|waste_basket|post_box|telephone|bicycle_parking)$\"]" + box + ";"
                + "node[\"highway\"=\"street_lamp\"]" + box + ";"
                + "node[\"barrier\"=\"bollard\"]" + box + ";"
                + "node[\"highway\"~\"^(bus_stop|traffic_signals|mini_roundabout|crossing)$\"]" + box + ";"
                + "node[\"public_transport\"=\"platform\"][\"bus\"=\"yes\"]" + box + ";"
                + ");(._;>;);out body;";
    }

    /** A box `half` metres each way round lat, lon: south, west, north, east in degrees. */
    public static double[] box(double lat, double lon, double half) {
        double dLat = half / 111250.0, dLon = half / (111320.0 * Math.cos(Math.toRadians(lat)));
        return new double[]{lat - dLat, lon - dLon, lat + dLat, lon + dLon};
    }

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
                            RoadPlan.CrossingType crossing = crossingType(tags);
                            if (crossing != null) {
                                m.crossings.put(id, crossing);
                            }
                        }
                        if (tags != null && ("bus_stop".equals(tags.get("highway"))
                                || "platform".equals(tags.get("public_transport")) && "yes".equals(tags.get("bus")))) {
                            m.busStops.put(id, tags.getOrDefault("name", ""));
                        }
                        String thing = tags == null ? null : pointKind(tags);
                        if (thing != null) {
                            m.things.put(id, thing);
                        }
                        String sign = tags == null ? null : signKind(tags);
                        if (sign != null) {
                            m.pois.put(id, new String[]{sign, tags.getOrDefault("name", tags.getOrDefault("brand", ""))});
                        }
                        tags = null;
                    }
                    case "way" -> {
                        m.ways.add(new Way(id, refs.stream().mapToLong(Long::longValue).toArray(), tags));
                        refs = null;
                        tags = null;
                    }
                    case "relation" -> {
                        if ("multipolygon".equals(tags.get("type")) && areaKind(tags) != null) {
                            m.relations.add(new Relation(id, tags, refs));
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

    /** Maps of the sections next to each other as one (a way or relation in two of them once), in a new map. */
    public static OsmMap merged(List<OsmMap> maps) {
        OsmMap m = new OsmMap();
        Set<Long> ways = new HashSet<>(), relations = new HashSet<>();
        for (OsmMap o : maps) {
            m.nodes.putAll(o.nodes);
            m.pointHighway.putAll(o.pointHighway);
            m.crossings.putAll(o.crossings);
            m.busStops.putAll(o.busStops);
            m.pois.putAll(o.pois);
            m.things.putAll(o.things);
            o.ways.stream().filter(w -> ways.add(w.id())).forEach(m.ways::add);
            o.relations.stream().filter(r -> relations.add(r.id())).forEach(m.relations::add);
            m.minLat = Math.min(m.minLat, o.minLat);
            m.maxLat = Math.max(m.maxLat, o.maxLat);
            m.minLon = Math.min(m.minLon, o.minLon);
            m.maxLon = Math.max(m.maxLon, o.maxLon);
            if (o.lidar != null) {
                if (m.lidar == null) {
                    m.lidar = new Lidar();
                }
                m.lidar.tiles.addAll(o.lidar.tiles);
            }
        }
        return m;
    }

    public double midLat() {
        return (minLat + maxLat) / 2;
    }

    public double midLon() {
        return (minLon + maxLon) / 2;
    }

    /**
     * The map's bus stops as the plan's (Road Planner bus stops, with their real names): each by the
     * map road nearest it, on the side it's on, if one's within 25 blocks; what was added.
     */
    public int busStops(RoadPlan plan, MapImport mi) {
        return busStops(plan, mi, new double[]{-Double.MAX_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE});
    }

    /** The bus stops in an area (x0, z0, x1, z1: a section of the map). */
    public int busStops(RoadPlan plan, MapImport mi, double[] area) {
        int added = 0;
        java.util.Set<String> placed = new java.util.HashSet<>();
        for (Map.Entry<Long, String> e : busStops.entrySet()) {
            double[] ll = nodes.get(e.getKey());
            if (ll == null || !inside(area, mi.x(ll[1]), mi.z(ll[0]))) {
                continue;
            }
            double px = mi.x(ll[1]), pz = mi.z(ll[0]), best = 25, at = 0;
            RoadPlan.Segment on = null;
            int side = 1;
            for (RoadPlan.Segment sg : plan.segments) {
                RoadPlan.Node a = plan.node(sg.a), b = plan.node(sg.b);
                if (!mi.owns(sg.id) || a == null || b == null || sg.style.dual) {
                    continue;
                }
                double ex = b.x - a.x, ez = b.z - a.z, l2 = ex * ex + ez * ez;
                if (l2 < 1) {
                    continue;
                }
                double t = Math.max(0, Math.min(1, ((px - a.x) * ex + (pz - a.z) * ez) / l2));
                double d = Math.hypot(a.x + ex * t - px, a.z + ez * t - pz);
                if (d < best) {
                    best = d;
                    on = sg;
                    at = t;
                    side = (px - a.x) * ez - (pz - a.z) * ex >= 0 ? 1 : -1;     // left of a to b, as the plan has it
                }
            }
            String name = e.getValue().isBlank() ? (on == null ? "" : on.name) : e.getValue();
            // (a road's stops either side share a name; one stop a side a name)
            if (on != null && placed.add(on.id + "/" + side + "/" + Math.round(at * 10)) ) {
                plan.addBusStop(on.id, at, side, name);
                added++;
            }
        }
        return added;
    }

    // ------------------------------------------------------------------ buildings

    /** What's in a shop, for a building that only says it's a building. */
    private static final Set<String> SHOP_AMENITIES = Set.of("restaurant", "cafe", "fast_food", "pub", "bar", "bank", "pharmacy", "post_office");
    /** Car parks that are a building or under or on one: the building's built, not the car park. */
    private static final Set<String> NOT_ON_THE_GROUND = Set.of("multi-storey", "underground", "rooftop");

    /**
     * What an area is built as: its building tag (a shop's plain "yes" as retail); a car park
     * ("parking") or a petrol station's forecourt ("fuel") as a surface (see MapBuildings); null if
     * it's none of those.
     */
    static String kind(Map<String, String> tags) {
        String b = tags.get("building");
        if (b != null && !b.equals("no")) {
            return b.equals("yes") && (tags.containsKey("shop") || SHOP_AMENITIES.contains(tags.getOrDefault("amenity", ""))) ? "retail" : b;
        }
        String a = tags.getOrDefault("amenity", "");
        if (a.equals("parking") && !NOT_ON_THE_GROUND.contains(tags.getOrDefault("parking", ""))) {
            return "parking";
        }
        return a.equals("fuel") ? "fuel" : null;
    }

    /** A car park or a forecourt: laid on the ground, not built up. */
    public static boolean surface(String kind) {
        return kind.equals("parking") || kind.equals("fuel");
    }

    private static final Set<String> GREEN_LEISURE = Set.of("park", "garden", "common", "playground", "recreation_ground", "village_green", "dog_park");
    private static final Set<String> GREEN_LANDUSE = Set.of("grass", "recreation_ground", "village_green", "cemetery", "meadow");
    private static final Set<String> PATHS = Set.of("footway", "path", "cycleway", "bridleway", "steps", "pedestrian", "track");
    private static final Set<String> SOFT = Set.of("dirt", "grass", "ground", "earth", "mud", "gravel", "fine_gravel", "compacted", "unpaved",
            "woodchips", "sand", "pebblestone");
    private static final Set<String> METAL_FENCES = Set.of("palisade", "metal", "railing", "chain_link", "metal_bars", "mesh");
    /** Not buildings: what's laid on the ground, runs along it, or stands on it (see MapBuildings). */
    private static final Set<String> FEATURES = Set.of("parking", "fuel", "water", "wood", "green", "pitch", "pedestrian", "yard:retail",
            "yard:industrial", "aisle", "hedge", "fence", "fence:metal", "wall", "tree_row", "tree", "bench", "bin", "post_box", "phone", "bike",
            "lamp", "bollard");

    /** Not a building: an area laid on the ground, a line along it (a path, a river, a hedge...) or a thing stood on it. */
    public static boolean feature(String kind) {
        return FEATURES.contains(kind) || kind.startsWith("path:") || kind.startsWith("river:");
    }

    /**
     * What a closed way (or a multipolygon) is as an area: a building (see kind), a car park or
     * forecourt, or water, a wood, a park or grass ("green"), a sports pitch, a pedestrian square;
     * null if none of those.
     */
    static String areaKind(Map<String, String> tags) {
        String k = kind(tags);
        if (k != null) {
            return k;
        }
        String natural = tags.getOrDefault("natural", ""), landuse = tags.getOrDefault("landuse", ""), leisure = tags.getOrDefault("leisure", "");
        String highway = tags.getOrDefault("highway", "");
        if (natural.equals("water") || "riverbank".equals(tags.get("waterway")) || landuse.equals("reservoir") || landuse.equals("basin")) {
            return "water";
        }
        if (natural.equals("wood") || landuse.equals("forest")) {
            return "wood";
        }
        if (leisure.equals("pitch")) {
            return "pitch";
        }
        if (GREEN_LEISURE.contains(leisure) || GREEN_LANDUSE.contains(landuse) || "grave_yard".equals(tags.get("amenity"))) {
            return "green";
        }
        if ((highway.equals("pedestrian") || highway.equals("footway")) && "yes".equals(tags.get("area")) || "square".equals(tags.get("place"))) {
            return "pedestrian";
        }
        // a retail park's, or an estate's, ground between its buildings, car parks and grass: tarmac, or a concrete yard
        if (landuse.equals("retail") || landuse.equals("commercial")) {
            return "yard:retail";
        }
        return landuse.equals("industrial") ? "yard:industrial" : null;
    }

    /**
     * What a way is as a line, with its width in blocks where it has one: a path ("path:paved",
     * "path:cycle" or "path:track", then its width), a river, stream or canal ("river:" and its
     * width), a hedge, fence or wall, a row of trees; null if none (or it's on a bridge or in a
     * tunnel, or a pavement drawn beside a road, which the road has already).
     */
    static String lineKind(Map<String, String> tags, double scale) {
        if (!tags.getOrDefault("bridge", "no").equals("no") || !tags.getOrDefault("tunnel", "no").equals("no")) {
            return null;
        }
        String highway = tags.getOrDefault("highway", ""), waterway = tags.getOrDefault("waterway", ""), barrier = tags.getOrDefault("barrier", "");
        if (highway.equals("service") && "parking_aisle".equals(tags.get("service"))) {
            return "aisle";                                 // a car park's aisle: bays either side (see MapBuildings)
        }
        if (PATHS.contains(highway) && !"sidewalk".equals(tags.get("footway")) && !"crossing".equals(tags.get("footway"))
                && !"yes".equals(tags.get("area"))) {
            String surface = tags.getOrDefault("surface", "");
            boolean soft = SOFT.contains(surface) || surface.isEmpty() && (highway.equals("path") || highway.equals("bridleway") || highway.equals("track"));
            double metres = highway.equals("track") ? 3 : highway.equals("cycleway") || highway.equals("bridleway") ? 2.5 : 1.5;
            return "path:" + (highway.equals("cycleway") ? "cycle" : soft ? "track" : "paved") + ":" + Math.max(2, (int) Math.round(metres * scale));
        }
        double width = metres(tags.get("width"));
        double metres = switch (waterway) {
            case "river" -> 10;
            case "canal" -> 12;
            case "stream" -> 2.5;
            case "drain", "ditch" -> 1.5;
            default -> Double.NaN;
        };
        if (!Double.isNaN(metres)) {
            return "river:" + Math.max(1, (int) Math.round((Double.isNaN(width) ? metres : Math.min(60, width)) * scale));
        }
        return switch (barrier) {
            case "hedge" -> "hedge";
            case "fence" -> METAL_FENCES.contains(tags.getOrDefault("fence_type", "")) ? "fence:metal" : "fence";
            case "wall", "retaining_wall", "city_wall" -> "wall";
            default -> "tree_row".equals(tags.get("natural")) ? "tree_row" : null;
        };
    }

    /**
     * What kind of crossing a point is: lights (crossing=traffic_signals, or traffic lights tagged as
     * a crossing's), a zebra (its markings or reference say so), or plain; null if it's not one.
     */
    static RoadPlan.CrossingType crossingType(Map<String, String> tags) {
        String highway = tags.getOrDefault("highway", ""), crossing = tags.getOrDefault("crossing", "");
        if (highway.equals("traffic_signals")) {
            String ts = tags.getOrDefault("traffic_signals", "");
            return !crossing.isEmpty() && !crossing.equals("no") || ts.equals("crossing") || ts.equals("pedestrian_crossing")
                    ? RoadPlan.CrossingType.SIGNALS : null;
        }
        if (!highway.equals("crossing") || crossing.equals("no")) {
            return null;
        }
        if (crossing.equals("traffic_signals")) {
            return RoadPlan.CrossingType.SIGNALS;
        }
        return crossing.equals("zebra") || "zebra".equals(tags.get("crossing_ref")) || "zebra".equals(tags.get("crossing:markings"))
                ? RoadPlan.CrossingType.ZEBRA : RoadPlan.CrossingType.PLAIN;
    }

    /**
     * The kind of shop sign (one of ShopNames') for a shop, café, pub, bank and the like, from its
     * tags: a nail bar a beauty salon's, a corner shop a newsagent's, a tyre place a trade counter's...;
     * null if it isn't one.
     */
    static String signKind(Map<String, String> tags) {
        String shop = tags.getOrDefault("shop", ""), amenity = tags.getOrDefault("amenity", ""), cuisine = tags.getOrDefault("cuisine", "");
        if (shop.equals("vacant") || shop.equals("no")) {
            return null;
        }
        switch (amenity) {
            case "cafe", "ice_cream" -> {
                return "coffee_shop";
            }
            case "restaurant" -> {
                return cuisine.contains("indian") || cuisine.contains("curry") ? "indian_restaurant"
                        : cuisine.contains("chicken") || cuisine.contains("portuguese") ? "peri_peri_restaurant" : "pizza_restaurant";
            }
            case "fast_food" -> {
                return cuisine.contains("fish") ? "fish_and_chips" : cuisine.contains("chinese") ? "chinese_takeaway"
                        : cuisine.contains("kebab") || cuisine.contains("turkish") ? "kebab_shop" : cuisine.contains("pizza") ? "pizza_takeaway"
                        : cuisine.contains("chicken") ? "drive_chicken" : cuisine.contains("coffee") ? "coffee_shop" : "drive_burgers";
            }
            case "pub" -> {
                return "corner_pub";
            }
            case "bar" -> {
                return "cocktail_bar";
            }
            case "nightclub" -> {
                return "nightclub";
            }
            case "bank", "bureau_de_change", "money_transfer" -> {
                return "bank";
            }
            case "pharmacy" -> {
                return "pharmacy";
            }
            case "post_office" -> {
                return "newsagent";
            }
            default -> {
            }
        }
        if (shop.isEmpty()) {
            return null;
        }
        return switch (shop) {
            case "convenience", "newsagent", "kiosk", "tobacco", "e-cigarette", "off_licence" -> "newsagent";
            case "supermarket", "butcher", "greengrocer", "deli", "frozen_food", "seafood" -> "retail_food";
            case "bakery", "pastry", "confectionery" -> "bakery";
            case "hairdresser", "barber", "tattoo" -> "barbers";
            case "beauty", "nails", "cosmetics", "perfumery", "massage", "optician", "hearing_aids" -> "retail_beauty";
            case "chemist", "medical_supply" -> "pharmacy";
            case "mobile_phone", "telecommunication", "computer" -> "phone_shop";
            case "bookmaker", "lottery" -> "bookmakers";
            case "estate_agent", "travel_agency", "insurance", "money_lender", "pawnbroker" -> "estate_agent";
            case "laundry", "dry_cleaning" -> "launderette";
            case "variety_store", "discount", "general" -> "pound_shop";
            case "clothes", "shoes", "boutique", "fashion", "jewelry", "charity", "second_hand", "bag", "fabric" -> "retail_fashion";
            case "furniture", "bed", "interior_decoration", "carpet", "kitchen", "bathroom_furnishing" -> "retail_furniture";
            case "doityourself", "hardware", "trade", "garden_centre", "car_repair", "tyres", "car_parts", "car", "paint", "building_materials" ->
                    "retail_diy";
            case "electronics", "electrical", "appliance", "hifi" -> "retail_electricals";
            case "pet", "pet_grooming" -> "retail_pets";
            case "sports", "outdoor", "bicycle", "fishing" -> "retail_sports";
            case "video_games", "games", "toys", "hobby", "model" -> "games_shop";
            case "alcohol", "wine", "beverages" -> "wine_bar";
            default -> "retail_home";
        };
    }

    /** What a point is, of the things built: a tree, a bench, a bin, a pillar box, a phone box, a bike stand, a street lamp, a bollard; or null. */
    static String pointKind(Map<String, String> tags) {
        if ("tree".equals(tags.get("natural"))) {
            return "tree";
        }
        if ("street_lamp".equals(tags.get("highway"))) {
            return "lamp";
        }
        if ("bollard".equals(tags.get("barrier"))) {
            return "bollard";
        }
        return switch (tags.getOrDefault("amenity", "")) {
            case "bench" -> "bench";
            case "waste_basket" -> "bin";
            case "post_box" -> "post_box";
            case "telephone" -> "phone";
            case "bicycle_parking" -> "bike";
            default -> null;
        };
    }

    public List<Outline> buildings(MapImport mi) {
        return buildings(mi, true);
    }

    /**
     * Everything built from the map: buildings, then the areas, lines and things (see feature)
     * after them, all as outlines. measured: real heights and roofs from LIDAR, where there are
     * any (without, it's quick: enough to fit the roads' pavements to the building fronts).
     */
    public List<Outline> buildings(MapImport mi, boolean measured) {
        List<Outline> out = new ArrayList<>();
        Map<Long, Way> byId = new HashMap<>();
        Lidar heights = measured ? lidar : null;
        for (Way w : ways) {
            byId.put(w.id(), w);
            String area = w.closed() ? areaKind(w.tags()) : null;
            if (area != null) {
                outline(out, w.nodes(), w.tags(), mi, heights);
            } else {
                String line = lineKind(w.tags(), mi.scale);
                if (line != null) {
                    feature(out, w.nodes(), line, mi);
                }
            }
        }
        for (Map.Entry<Long, String> t : things.entrySet()) {
            feature(out, new long[]{t.getKey()}, t.getValue(), mi);
        }
        shopsInto(out, mi);
        for (Relation rel : relations) {
            for (long id : rel.outers()) {
                Way w = byId.get(id);
                // shortcut: only outer rings drawn as one closed way; ones split over several ways are skipped
                if (w != null && w.closed()) {
                    outline(out, w.nodes(), rel.tags(), mi, heights);
                }
            }
        }
        return out;
    }

    /** The shops mapped as points, each into the building it's in (they're its shop fronts). */
    private void shopsInto(List<Outline> out, MapImport mi) {
        if (pois.isEmpty()) {
            return;
        }
        Map<Long, List<Integer>> grid = new HashMap<>();
        for (int i = 0; i < out.size(); i++) {
            Outline o = out.get(i);
            if (feature(o.kind()) || o.x().length < 3) {
                continue;
            }
            double x0 = Double.MAX_VALUE, z0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
            for (int k = 0; k < o.x().length; k++) {
                x0 = Math.min(x0, o.x()[k]);
                z0 = Math.min(z0, o.z()[k]);
                x1 = Math.max(x1, o.x()[k]);
                z1 = Math.max(z1, o.z()[k]);
            }
            for (int cx = (int) Math.floor(x0 / 32); cx <= (int) Math.floor(x1 / 32); cx++) {
                for (int cz = (int) Math.floor(z0 / 32); cz <= (int) Math.floor(z1 / 32); cz++) {
                    grid.computeIfAbsent(((long) cx << 32) ^ (cz & 0xffffffffL), k -> new ArrayList<>()).add(i);
                }
            }
        }
        Map<Integer, List<Shop>> found = new HashMap<>();
        for (Map.Entry<Long, String[]> p : pois.entrySet()) {
            double[] ll = nodes.get(p.getKey());
            if (ll == null) {
                continue;
            }
            double x = mi.x(ll[1]), z = mi.z(ll[0]);
            for (int i : grid.getOrDefault(((long) (int) Math.floor(x / 32) << 32) ^ ((int) Math.floor(z / 32) & 0xffffffffL), List.of())) {
                if (inside(out.get(i).x(), out.get(i).z(), x, z)) {
                    found.computeIfAbsent(i, k -> new ArrayList<>()).add(new Shop(p.getValue()[0], p.getValue()[1], x, z));
                    break;
                }
            }
        }
        found.forEach((i, shops) -> {
            Outline o = out.get(i);
            List<Shop> all = new ArrayList<>(shops);
            all.addAll(o.shops().stream().filter(sh -> !sh.name().isEmpty() && shops.stream().noneMatch(p -> p.name().equals(sh.name()))).toList());
            out.set(i, new Outline(o.x(), o.z(), o.height(), o.kind(), o.roof(), o.floors(), o.material(), o.roofMaterial(), o.surface(), all));
        });
    }

    /** Whether a point's inside an outline (its corners, the first again at the end). */
    static boolean inside(double[] xs, double[] zs, double px, double pz) {
        boolean in = false;
        for (int i = 0, j = xs.length - 1; i < xs.length; j = i++) {
            if ((zs[i] > pz) != (zs[j] > pz) && px < (xs[j] - xs[i]) * (pz - zs[i]) / (zs[j] - zs[i]) + xs[i]) {
                in = !in;
            }
        }
        return in;
    }

    /** A line or a thing (one point): where it is, in blocks. */
    private void feature(List<Outline> out, long[] ids, String kind, MapImport mi) {
        double[] x = new double[ids.length], z = new double[ids.length];
        for (int i = 0; i < ids.length; i++) {
            double[] ll = nodes.get(ids[i]);
            if (ll == null) {
                return;                                     // part of it is outside the export
            }
            x[i] = mi.x(ll[1]);
            z[i] = mi.z(ll[0]);
        }
        out.add(new Outline(x, z, 1, kind, "flat", 1, "", "", null, List.of()));
    }

    private void outline(List<Outline> out, long[] ids, Map<String, String> tags, MapImport mi, Lidar lidar) {
        double[] x = new double[ids.length], z = new double[ids.length], lat = new double[ids.length], lon = new double[ids.length];
        for (int i = 0; i < ids.length; i++) {
            double[] ll = nodes.get(ids[i]);
            if (ll == null) {
                return;                                     // part of it is outside the export
            }
            lat[i] = ll[0];
            lon[i] = ll[1];
            x[i] = mi.x(ll[1]);
            z[i] = mi.z(ll[0]);
        }
        String kind = areaKind(tags);
        if (feature(kind)) {
            out.add(new Outline(x, z, 1, kind, "flat", 1, "", "", null, List.of()));
            return;
        }
        String roof = tags.getOrDefault("roof:shape", "");
        Lidar.Shape real = lidar == null ? null : lidar.shape(lat, lon);
        int height;
        if (real != null) {                                 // measured: walls up to the eaves, its roof flat or pitched
            height = Math.max(2, (int) Math.round(real.eaves() * mi.scale));
            roof = roof.isEmpty() ? real.roof() : roof;
        } else {
            height = height(tags, mi.scale);
        }
        double levels = metres(tags.get("building:levels"));
        int floors = Double.isNaN(levels) ? (int) Math.max(1, Math.round(height / (STOREY_M * mi.scale))) : (int) Math.max(1, levels);
        // the building's own shop (or business: a named unit on a retail park or an estate gets its name up)
        List<Shop> shops = new ArrayList<>();
        String sign = signKind(tags), name = tags.getOrDefault("name", tags.getOrDefault("brand", ""));
        if (sign == null && !name.isEmpty() && Set.of("retail", "commercial", "industrial", "warehouse", "office", "supermarket").contains(kind)) {
            sign = "retail_home";
        }
        if (sign != null) {
            double mx = 0, mz = 0;
            for (int i = 0; i < x.length; i++) {
                mx += x[i] / x.length;
                mz += z[i] / x.length;
            }
            shops.add(new Shop(sign, name, mx, mz));
        }
        out.add(new Outline(x, z, height, kind, roof, floors,
                tags.getOrDefault("building:material", "").toLowerCase(java.util.Locale.ROOT), tags.getOrDefault("roof:material", "").toLowerCase(java.util.Locale.ROOT),
                real != null && !roof.equals("flat") ? surface(x, z, mi) : null, shops));
    }

    /** The real roof over an outline (corners in blocks), sampled from the LIDAR heights; null if it's too big to send. */
    private Surface surface(double[] x, double[] z, MapImport mi) {
        double x1 = -Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
        int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE;
        for (int i = 0; i < x.length; i++) {
            x0 = Math.min(x0, (int) Math.floor(x[i]));
            z0 = Math.min(z0, (int) Math.floor(z[i]));
            x1 = Math.max(x1, x[i]);
            z1 = Math.max(z1, z[i]);
        }
        int w = (int) Math.ceil((x1 - x0) / 2) + 1, h = (int) Math.ceil((z1 - z0) / 2) + 1;
        if ((long) w * h > 20_000) {
            return null;
        }
        byte[] v = new byte[w * h];
        for (int r = 0; r < h; r++) {
            for (int c = 0; c < w; c++) {
                float m = lidar.at(mi.lat(z0 + 2 * r), mi.lon(x0 + 2 * c));
                // the ground round it (or a gap in the survey) isn't roof
                v[r * w + c] = Float.isNaN(m) || m < 2 ? 0 : (byte) Math.min(255, Math.round(m * mi.scale * 2));
            }
        }
        return new Surface(x0, z0, w, v);
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
        return roads(plan, mi, buildings, x0, z0, x1, z1, false);
    }

    /**
     * As above; with a finite area, strictly that area (a section of the map): each road cut where it
     * leaves it, at a point that's the same block from either side, joined there to what the section
     * next door brought in (or left open for the next one). extending: another section of a map
     * already brought in (its ids go on from the first's).
     */
    public int roads(RoadPlan plan, MapImport mi, List<Outline> buildings, double x0, double z0, double x1, double z1, boolean extending) {
        if (!extending) {
            mi.firstId = plan.nextId;
        }
        int fresh = plan.nextId;
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
        boolean section = Double.isFinite(x0) && Double.isFinite(x1) && Math.abs(x0) < 1e8 && Math.abs(x1) < 1e8;
        Map<Long, Node> existing = new HashMap<>();
        for (Node n : plan.nodes) {
            existing.putIfAbsent(((long) (int) Math.floor(n.x) << 32) ^ ((int) Math.floor(n.z) & 0xffffffffL), n);
        }
        Set<Long> cuts = new HashSet<>();
        List<Way> whole = roads;
        if (section) {
            roads = clipped(roads, clip, mi, cuts);
        }
        try {
            return roads(plan, mi, clip, edges, roads, whole, rings, existing, cuts, fresh);
        } finally {
            cuts.forEach(nodes::remove);                    // (the cut points were only for this)
        }
    }

    /**
     * Roads cut to an area: each part inside it as a road of its own (the same tags), with a new
     * point where it crosses the area's edge (in nodes, its id in cuts).
     */
    private List<Way> clipped(List<Way> roads, double[] box, MapImport mi, Set<Long> cuts) {
        List<Way> out = new ArrayList<>();
        long next = Long.MIN_VALUE / 2;
        for (Way w : roads) {
            double[][] p = project(w.nodes(), mi);
            if (p == null) {
                continue;
            }
            long[] ids = w.nodes();
            List<Long> part = null;
            for (int i = 0; i + 1 < ids.length; i++) {
                double[] t = clipSegment(p[0][i], p[1][i], p[0][i + 1], p[1][i + 1], box);
                if (t == null) {
                    part = null;                            // (it can't have been in: it's outside from here)
                    continue;
                }
                if (part == null) {
                    part = new ArrayList<>();
                    if (t[0] <= 0) {
                        part.add(ids[i]);
                    } else {
                        part.add(cut(p, i, t[0], mi, next++, cuts));
                    }
                }
                if (t[1] < 1) {
                    part.add(cut(p, i, t[1], mi, next++, cuts));
                    if (part.size() >= 2) {
                        out.add(new Way(w.id(), part.stream().mapToLong(Long::longValue).toArray(), w.tags()));
                    }
                    part = null;
                } else {
                    part.add(ids[i + 1]);
                }
            }
            if (part != null && part.size() >= 2) {
                out.add(new Way(w.id(), part.stream().mapToLong(Long::longValue).toArray(), w.tags()));
            }
        }
        return out;
    }

    /** A point t of the way along a road's piece i to i+1 (where it crosses an area's edge), as a new point. */
    private long cut(double[][] p, int i, double t, MapImport mi, long id, Set<Long> cuts) {
        double x = p[0][i] + (p[0][i + 1] - p[0][i]) * t, z = p[1][i] + (p[1][i + 1] - p[1][i]) * t;
        nodes.put(id, new double[]{mi.lat(z), mi.lon(x)});
        cuts.add(id);
        return id;
    }

    /** Where a line a to b is inside a box (x0, z0, x1, z1): from t0 to t1 of the way along it, or null if it isn't. */
    static double[] clipSegment(double ax, double az, double bx, double bz, double[] box) {
        double t0 = 0, t1 = 1, dx = bx - ax, dz = bz - az;
        double[] p = {-dx, dx, -dz, dz}, q = {ax - box[0], box[2] - ax, az - box[1], box[3] - az};
        for (int k = 0; k < 4; k++) {
            if (p[k] == 0) {
                if (q[k] < 0) {
                    return null;
                }
            } else {
                double r = q[k] / p[k];
                if (p[k] < 0) {
                    if (r > t1) {
                        return null;
                    }
                    t0 = Math.max(t0, r);
                } else {
                    if (r < t0) {
                        return null;
                    }
                    t1 = Math.min(t1, r);
                }
            }
        }
        return t0 < t1 ? new double[]{t0, t1} : null;
    }

    private int roads(RoadPlan plan, MapImport mi, double[] clip, Edges edges, List<Way> roads, List<Way> whole, List<Way> rings,
                      Map<Long, Node> existing, Set<Long> cuts, int fresh) {
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
            if (n < 3) {
                continue;
            }
            if (!inside(clip, sx / n, sz / n)) {
                // its middle's in another section: the roads here join the one that section made, if it's been brought in
                Node there = null;
                for (int dx = -2; dx <= 2 && there == null; dx++) {
                    for (int dz = -2; dz <= 2 && there == null; dz++) {
                        Node o = existing.get(((long) ((int) Math.floor(sx / n) + dx) << 32) ^ (((int) Math.floor(sz / n) + dz) & 0xffffffffL));
                        there = o != null && (o.junction == Junction.ROUNDABOUT || o.junction == Junction.MINI_ROUNDABOUT) ? o : null;
                    }
                }
                if (there != null) {
                    ringRadius.putIfAbsent(there, 6.0);
                    for (long id : ring) {
                        ringNode.put(id, there);
                    }
                }
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
        Map<Way, RoadStyle> styles = streetStyles(roads, mi, edges);
        int count = 0;
        for (Way w : roads) {
            double[][] p = project(w.nodes(), mi);
            RoadStyle st = styles.get(w);
            if (p == null || st == null) {
                continue;
            }
            int start = 0;
            long[] ids = w.nodes();
            for (int i = 1; i < ids.length; i++) {
                if (i == ids.length - 1 || uses.getOrDefault(ids[i], 0) >= 2 || ringNode.containsKey(ids[i])) {
                    count += piece(plan, w, p, start, i, st, joins, ringNode, ringRadius, clip, done, existing, cuts);
                    start = i;
                }
            }
        }
        Map<Node, Node> moved = pairCarriageways(plan, fresh, existing);
        joins.replaceAll((id, n) -> moved.getOrDefault(n, n));
        // junctions: traffic lights at the junction nearest each signal, mini roundabouts tagged on a point
        Map<Integer, Integer> arms = new HashMap<>();
        for (RoadPlan.Segment s : plan.segments) {
            arms.merge(s.a, 1, Integer::sum);
            arms.merge(s.b, 1, Integer::sum);
        }
        // traffic lights: on a junction, or on the way into one (the first along the road, as the UK maps them), else they're a crossing's
        Map<Long, List<Way>> wayAt = new HashMap<>();
        for (Way w : whole) {                               // (the whole roads, past the section's edge: the junction may be over it)
            for (long id : w.nodes()) {
                wayAt.computeIfAbsent(id, k -> new ArrayList<>()).add(w);
            }
        }
        List<long[]> crossingsAt = new ArrayList<>();          // OSM point, kind (ordinal)
        for (Map.Entry<Long, String> e : pointHighway.entrySet()) {
            long id = e.getKey();
            if (e.getValue().equals("mini_roundabout") && joins.containsKey(id)) {
                joins.get(id).junction = Junction.MINI_ROUNDABOUT;
            } else if (e.getValue().equals("traffic_signals") && !crossings.containsKey(id)) {
                Node j = junctionAlong(id, wayAt, joins, existing, arms, 45 * mi.scale, mi);
                if (j != null) {
                    if (j.junction == Junction.AUTO) {
                        j.junction = Junction.SIGNALS;
                    }
                } else {
                    crossingsAt.add(new long[]{id, RoadPlan.CrossingType.SIGNALS.ordinal()});
                }
            }
        }
        crossings.forEach((id, type) -> crossingsAt.add(new long[]{id, type.ordinal()}));
        int crossed = 0;
        for (long[] c : crossingsAt) {
            double[] ll = nodes.get(c[0]);
            if (ll == null || !inside(clip, mi.x(ll[1]), mi.z(ll[0]))) {
                continue;
            }
            RoadPlan.CrossingType type = RoadPlan.CrossingType.values()[(int) c[1]];
            // one at a junction (or right by one) is the junction's: lights there are its, a plain one its dropped kerbs
            Node j = junctionAlong(c[0], wayAt, joins, existing, arms, 12 * mi.scale, mi);
            if (j != null && type != RoadPlan.CrossingType.ZEBRA) {
                continue;
            }
            crossed += crossingOn(plan, fresh, mi.x(ll[1]), mi.z(ll[0]), type) ? 1 : 0;
        }
        // a roundabout no road reached is left out
        plan.nodes.removeIf(n -> n.id >= fresh && ringRadius.containsKey(n) && !arms.containsKey(n.id));
        mi.lastId = plan.nextId - 1;
        return count;
    }

    /**
     * Each road's style, the same all along a street: OSM splits a street into many ways (at every
     * change of speed limit, bus lane or bridge) and each piece's own lanes and pavements would make
     * it narrow and widen again and again. The ways with the same name (or number), main or not,
     * take the style of its longest piece (its longest one-way piece, for those), with the street's
     * pavement (the middle of its pieces' by length); a one-way piece of a street that's two-way
     * elsewhere keeps the street's width, as one wide lane.
     */
    private Map<Way, RoadStyle> streetStyles(List<Way> roads, MapImport mi, Edges edges) {
        Map<Way, RoadStyle> styles = new HashMap<>();
        Map<Way, Double> length = new HashMap<>();
        Map<String, List<Way>> streets = new HashMap<>();
        for (Way w : roads) {
            double[][] p = project(w.nodes(), mi);
            if (p == null) {
                continue;
            }
            styles.put(w, style(w, p, edges));
            double len = 0;
            for (int i = 0; i + 1 < p[0].length; i++) {
                len += Math.hypot(p[0][i + 1] - p[0][i], p[1][i + 1] - p[1][i]);
            }
            length.put(w, len);
            String name = !w.tag("name").isEmpty() ? w.tag("name") : w.tag("ref");
            if (!name.isEmpty()) {
                streets.computeIfAbsent(name + "|" + MAJOR.contains(w.tag("highway")), k -> new ArrayList<>()).add(w);
            }
        }
        for (List<Way> street : streets.values()) {
            Way two = null, one = null;
            List<double[]> pavements = new ArrayList<>();         // pavement, length
            for (Way w : street) {
                RoadStyle st = styles.get(w);
                if (st.oneWay ? one == null || length.get(w) > length.get(one) : two == null || length.get(w) > length.get(two)) {
                    if (st.oneWay) {
                        one = w;
                    } else {
                        two = w;
                    }
                }
                if (st.pavement > 0) {
                    pavements.add(new double[]{st.pavement, length.get(w)});
                }
            }
            pavements.sort((a, b) -> Double.compare(a[0], b[0]));
            double total = pavements.stream().mapToDouble(v -> v[1]).sum(), run = 0;
            int pavement = 0;
            for (double[] v : pavements) {
                run += v[1];
                if (run >= total / 2) {
                    pavement = (int) v[0];
                    break;
                }
            }
            boolean minor = !MAJOR.contains(street.get(0).tag("highway"));
            for (Way w : street) {
                RoadStyle st = styles.get(w.equals(one) || styles.get(w).oneWay ? one : two).copy();
                if (st.pavement > 0 && pavement > 0) {
                    st.pavement = pavement;
                }
                if (st.oneWay && minor && two != null && !w.tag("lanes").equals("2")) {
                    st.lanes = 1;
                    st.laneWidth = (int) Math.round(styles.get(two).halfCarriageway() * 2);
                }
                styles.put(w, st);
            }
        }
        return styles;
    }

    /**
     * The junction (3 or more roads) nearest an OSM point along the roads through it, no further
     * than reach (blocks) along them; null if there's none that close.
     */
    private Node junctionAlong(long from, Map<Long, List<Way>> wayAt, Map<Long, Node> joins, Map<Long, Node> existing, Map<Integer, Integer> arms,
                               double reach, MapImport mi) {
        Node best = null;
        double bestD = reach;
        for (Way w : wayAt.getOrDefault(from, List.of())) {
            long[] ids = w.nodes();
            for (int i = 0; i < ids.length; i++) {
                if (ids[i] != from) {
                    continue;
                }
                for (int dir = -1; dir <= 1; dir += 2) {
                    double d = 0;
                    for (int k = i; k + dir >= 0 && k + dir < ids.length && d < bestD; k += dir) {
                        double[] a = nodes.get(ids[k]), b = nodes.get(ids[k + dir]);
                        if (a == null || b == null) {
                            break;
                        }
                        d += Math.hypot(mi.x(b[1]) - mi.x(a[1]), mi.z(b[0]) - mi.z(a[0]));
                        Node n = joins.get(ids[k + dir]);
                        if (n == null) {                    // one a section brought in before
                            n = existing.get(((long) (int) Math.floor(mi.x(b[1])) << 32) ^ ((int) Math.floor(mi.z(b[0])) & 0xffffffffL));
                        }
                        if (n != null && arms.getOrDefault(n.id, 0) >= 3 && d < bestD) {
                            best = n;
                            bestD = d;
                        }
                    }
                }
                Node here = joins.get(from);
                if (here != null && arms.getOrDefault(here.id, 0) >= 3) {
                    return here;                            // lights on the junction itself
                }
            }
        }
        return best;
    }

    /** A crossing on the map's road nearest x, z (within 4 blocks of its line, more on a wide one), not too near another; whether it went in. */
    private static boolean crossingOn(RoadPlan plan, int fresh, double x, double z, RoadPlan.CrossingType type) {
        Map<Integer, Node> byId = new HashMap<>();
        plan.nodes.forEach(n -> byId.put(n.id, n));
        RoadPlan.Segment best = null;
        double bestD = 4, bestAt = 0.5;
        for (RoadPlan.Segment sg : plan.segments) {
            Node a = byId.get(sg.a), b = byId.get(sg.b);
            if (sg.id < fresh || a == null || b == null) {
                continue;
            }
            double ax = a.x + 0.5, az = a.z + 0.5, dx = b.x - a.x, dz = b.z - a.z, l2 = dx * dx + dz * dz;
            if (l2 < 1) {
                continue;
            }
            double t = Math.max(0, Math.min(1, ((x - ax) * dx + (z - az) * dz) / l2));
            // (on a wide road, one mapped on one side, as on the two sides it was mapped as, is still on it)
            double d = Math.hypot(ax + dx * t - x, az + dz * t - z) - Math.abs(sg.bend) - Math.max(0, sg.style.halfCarriageway() - 3);
            if (d < bestD) {
                best = sg;
                bestD = d;
                bestAt = t;
            }
        }
        if (best == null) {
            return false;
        }
        double len = Math.hypot(byId.get(best.b).x - byId.get(best.a).x, byId.get(best.b).z - byId.get(best.a).z);
        for (RoadPlan.Crossing o : plan.crossings) {
            if (o.segment == best.id && Math.abs(o.at - bestAt) * len < 10) {
                return false;                               // one's enough (a road and the path beside it both mapped, say)
            }
        }
        plan.addCrossing(best.id, bestAt, type);
        return true;
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
                      Map<Long, Node> ringNode, Map<Node, Double> ringRadius, double[] clip, Set<String> done, Map<Long, Node> existing,
                      Set<Long> cuts) {
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
            if (node == null && cuts.contains(id)) {
                // where it crosses into the next section: that section's road's end, if it's in, else an end left open for it
                node = joins.computeIfAbsent(id, i -> {
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            Node o = existing.get(((long) ((int) Math.floor(x) + dx) << 32) ^ (((int) Math.floor(z) + dz) & 0xffffffffL));
                            if (o != null && o.open) {
                                o.open = false;
                                return o;
                            }
                        }
                    }
                    Node end = plan.addNode(Math.floor(x), Math.floor(z));
                    end.open = true;
                    return end;
                });
            } else if (node == null && (k == a || k == b)) {
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
                    // a long straight stretch gets points along it, so a part of a big map doesn't reach far past its edge
                    int pieces = (int) Math.ceil(Math.hypot(node.x - prev.x, node.z - prev.z) / MAX_STRETCH);
                    Node from = prev;
                    for (int i = 1; i < pieces; i++) {
                        Node mid = plan.addNode(Math.floor(prev.x + (node.x - prev.x) * i / pieces), Math.floor(prev.z + (node.z - prev.z) * i / pieces));
                        plan.addSegment(from.id, mid.id, st).name = w.tag("name");
                        from = mid;
                    }
                    plan.addSegment(from.id, node.id, st).name = w.tag("name");
                    count++;
                }
            }
            prev = node;
        }
        return count;
    }

    /**
     * The two sides of a road mapped as two one-way roads close together (a dual carriageway, or a
     * road split round an island coming up to a roundabout) made one two-way road down the middle:
     * left as two they'd lie over each other, lines and kerbs across each other's lanes. It takes
     * the street's own two-way style where it has one (else a dual carriageway's), and the roads
     * that joined either side join it. Returns the points that went, and what took their place.
     */
    private static Map<Node, Node> pairCarriageways(RoadPlan plan, int fresh, Map<Long, Node> existing) {
        Map<Integer, Node> byId = new HashMap<>();
        plan.nodes.forEach(n -> byId.put(n.id, n));
        Map<String, List<RoadPlan.Segment>> from = new HashMap<>(), to = new HashMap<>();
        List<RoadPlan.Segment> ones = new ArrayList<>();
        for (RoadPlan.Segment s : plan.segments) {
            if (s.id >= fresh && s.style.oneWay && !s.name.isEmpty() && s.a != s.b) {
                ones.add(s);
                from.computeIfAbsent(s.name + "|" + s.a, k -> new ArrayList<>()).add(s);
                to.computeIfAbsent(s.name + "|" + s.b, k -> new ArrayList<>()).add(s);
            }
        }
        // each side as a chain of its pieces, running on through plain points (not round a sharp turn)
        Map<RoadPlan.Segment, RoadPlan.Segment> next = new HashMap<>(), prev = new HashMap<>();
        for (RoadPlan.Segment s : ones) {
            Node at = byId.get(s.b);
            if (at.junction != Junction.AUTO || at.id < fresh) {
                continue;
            }
            RoadPlan.Segment o = straightest(byId, s, from.getOrDefault(s.name + "|" + s.b, List.of()), true);
            if (o != null && straightest(byId, o, to.get(s.name + "|" + s.b), false) == s) {
                next.put(s, o);
                prev.put(o, s);
            }
        }
        List<List<RoadPlan.Segment>> chains = new ArrayList<>();
        for (RoadPlan.Segment s : ones) {
            if (!prev.containsKey(s)) {
                List<RoadPlan.Segment> c = new ArrayList<>();
                for (RoadPlan.Segment t = s; t != null && c.size() < 2000; t = next.get(t)) {
                    c.add(t);
                }
                chains.add(c);
            }
        }
        Map<Node, Node> moved = new HashMap<>();
        Set<List<RoadPlan.Segment>> used = new HashSet<>();
        for (int i = 0; i < chains.size(); i++) {
            for (int j = i + 1; j < chains.size() && !used.contains(chains.get(i)); j++) {
                List<RoadPlan.Segment> p = chains.get(i), q = chains.get(j);
                if (used.contains(q) || !p.get(0).name.equals(q.get(0).name)) {
                    continue;
                }
                // only sides that would lie over each other, or all but: further apart, each is laid out fine as it is
                double sep = Math.min(32, p.get(0).style.halfCarriageway() + q.get(0).style.halfCarriageway() + 5);
                double[][] pl = chainLine(p, byId), ql = chainLine(q, byId);
                double gp = alongside(pl, ql, sep), gq = alongside(ql, pl, sep);
                if (!Double.isNaN(gp) && !Double.isNaN(gq) && mergeSides(plan, byId, p, q, pl, ql, (gp + gq) / 2, sep, fresh, existing, moved)) {
                    used.add(p);
                    used.add(q);
                }
            }
        }
        moved.replaceAll((o, n) -> {
            for (int i = 0; i < 100 && moved.containsKey(n); i++) {      // (moved again by a later pair)
                n = moved.get(n);
            }
            return n;
        });
        return moved;
    }

    /** Of the one-way pieces cand, the one running on most nearly straight from s (after it if after, else before it); null if none does. */
    private static RoadPlan.Segment straightest(Map<Integer, Node> byId, RoadPlan.Segment s, List<RoadPlan.Segment> cand, boolean after) {
        RoadPlan.Segment best = null;
        double bestDot = 0.25;                              // (no sharper than about 75 degrees)
        for (RoadPlan.Segment o : cand == null ? List.<RoadPlan.Segment>of() : cand) {
            RoadPlan.Segment first = after ? s : o, second = after ? o : s;
            Node a = byId.get(first.a), b = byId.get(first.b), c = byId.get(second.b);
            double ux = b.x - a.x, uz = b.z - a.z, vx = c.x - b.x, vz = c.z - b.z, l = Math.hypot(ux, uz) * Math.hypot(vx, vz);
            double dot = l < 1e-9 ? -1 : (ux * vx + uz * vz) / l;
            if (o != s && dot > bestDot) {
                best = o;
                bestDot = dot;
            }
        }
        return best;
    }

    private static double[][] chainLine(List<RoadPlan.Segment> c, Map<Integer, Node> byId) {
        double[][] l = new double[2][c.size() + 1];
        Node first = byId.get(c.get(0).a);
        l[0][0] = first.x;
        l[1][0] = first.z;
        for (int i = 0; i < c.size(); i++) {
            Node n = byId.get(c.get(i).b);
            l[0][i + 1] = n.x;
            l[1][i + 1] = n.z;
        }
        return l;
    }

    /** The point on line l nearest (x, z): {x, z, how far along l, its direction x, z, distance}. */
    private static double[] nearestOn(double[][] l, double x, double z) {
        double[] best = null;
        double run = 0;
        for (int i = 0; i + 1 < l[0].length; i++) {
            double ax = l[0][i], az = l[1][i], dx = l[0][i + 1] - ax, dz = l[1][i + 1] - az, len = Math.hypot(dx, dz);
            if (len < 1e-9) {
                continue;
            }
            double t = Math.max(0, Math.min(len, ((x - ax) * dx + (z - az) * dz) / len));
            double px = ax + dx / len * t, pz = az + dz / len * t, d = Math.hypot(px - x, pz - z);
            if (best == null || d < best[5]) {
                best = new double[]{px, pz, run + t, dx / len, dz / len, d};
            }
            run += len;
        }
        return best;
    }

    /** Points every step along l, its ends included: {x, z, direction x, z}. */
    private static List<double[]> samples(double[][] l, double step) {
        List<double[]> out = new ArrayList<>();
        double carry = 0, lx = 1, lz = 0;
        for (int i = 0; i + 1 < l[0].length; i++) {
            double ax = l[0][i], az = l[1][i], dx = l[0][i + 1] - ax, dz = l[1][i + 1] - az, len = Math.hypot(dx, dz);
            if (len < 1e-9) {
                continue;
            }
            lx = dx / len;
            lz = dz / len;
            double t = carry;
            for (; t < len; t += step) {
                out.add(new double[]{ax + lx * t, az + lz * t, lx, lz});
            }
            carry = t - len;
        }
        int n = l[0].length - 1;
        out.add(new double[]{l[0][n], l[1][n], lx, lz});
        return out;
    }

    /**
     * How far a runs from b (the middle of it), if nearly all of a runs alongside b the other way
     * within sep (further apart, up to half as far again, near its ends: they spread out to a
     * junction each); else NaN.
     */
    private static double alongside(double[][] a, double[][] b, double sep) {
        List<double[]> s = samples(a, 2);
        List<Double> gaps = new ArrayList<>();
        for (int i = 0; i < s.size(); i++) {
            double[] p = s.get(i), n = nearestOn(b, p[0], p[1]);
            double within = Math.min(i, s.size() - 1 - i) * 2 < 40 ? sep * 1.6 : sep;
            if (n != null && (n[5] < 3 || n[5] <= within && p[2] * n[3] + p[3] * n[4] < -0.3)) {
                gaps.add(n[5]);
            }
        }
        if (s.size() < 4 || gaps.size() < 0.85 * s.size()) {
            return Double.NaN;
        }
        Collections.sort(gaps);
        return gaps.get(gaps.size() / 2);
    }

    /** One side p and the other q (the other way) made one two-way road; false (and nothing changed) if they can't be. */
    private static boolean mergeSides(RoadPlan plan, Map<Integer, Node> byId, List<RoadPlan.Segment> p, List<RoadPlan.Segment> q,
                                      double[][] pl, double[][] ql, double gap, double sep, int fresh, Map<Long, Node> existing, Map<Node, Node> moved) {
        Node pa = byId.get(p.get(0).a), pb = byId.get(p.get(p.size() - 1).b), qa = byId.get(q.get(0).a), qb = byId.get(q.get(q.size() - 1).b);
        double ends = sep * 1.6;                            // (a dual carriageway's sides meet a road across at two junctions, apart)
        if (Math.hypot(pa.x - qb.x, pa.z - qb.z) > ends || Math.hypot(pb.x - qa.x, pb.z - qa.z) > ends
                || pa != qb && fixed(pa, fresh) && fixed(qb, fresh) || pb != qa && fixed(pb, fresh) && fixed(qa, fresh)) {
            return false;                                   // (two junctions of their own at an end: leave them be)
        }
        Set<Integer> sides = new HashSet<>();
        p.forEach(s -> sides.add(s.id));
        q.forEach(s -> sides.add(s.id));
        List<Node> inner = new ArrayList<>();
        for (List<RoadPlan.Segment> c : List.of(p, q)) {
            for (int i = 0; i + 1 < c.size(); i++) {
                Node n = byId.get(c.get(i).b);
                if (n.id < fresh) {
                    return false;
                }
                inner.add(n);
            }
        }
        // the line down the middle: each point of p halfway to q
        List<double[]> mid = new ArrayList<>();
        for (double[] s : samples(pl, 2)) {
            double[] n = nearestOn(ql, s[0], s[1]);
            mid.add(new double[]{(s[0] + n[0]) / 2, (s[1] + n[1]) / 2});
        }
        Node endA = end(plan, pa, qb, fresh, existing, moved, sides), endB = end(plan, pb, qa, fresh, existing, moved, sides);
        int last = mid.size() - 1;
        mid.set(0, new double[]{endA.x, endA.z});
        mid.set(last, new double[]{endB.x, endB.z});
        // where other roads joined a side: a point on the middle line there (those close along it one point)
        Map<Integer, List<Node>> joinAt = new java.util.TreeMap<>();
        for (Node n : inner) {
            boolean joined = false;
            for (RoadPlan.Segment s : plan.segments) {
                joined |= !sides.contains(s.id) && (s.a == n.id || s.b == n.id);
            }
            if (joined) {
                int k = 0;
                for (int i = 1; i <= last; i++) {
                    if (Math.hypot(mid.get(i)[0] - n.x, mid.get(i)[1] - n.z) < Math.hypot(mid.get(k)[0] - n.x, mid.get(k)[1] - n.z)) {
                        k = i;
                    }
                }
                joinAt.computeIfAbsent(k <= 2 ? 0 : k >= last - 2 ? last : k, x -> new ArrayList<>()).add(n);
            }
        }
        double[] mx = new double[last + 1], mz = new double[last + 1];
        for (int i = 0; i <= last; i++) {
            mx[i] = mid.get(i)[0];
            mz[i] = mid.get(i)[1];
        }
        boolean[] keep = simplify(mx, mz, 0, last, 1.0);
        int prevK = -10;
        List<Node> group = null;
        Map<Integer, List<Node>> groups = new java.util.TreeMap<>();
        for (Map.Entry<Integer, List<Node>> e : joinAt.entrySet()) {
            if (e.getKey() - prevK > 3 || e.getKey() == last) {
                group = new ArrayList<>();
                groups.put(e.getKey(), group);
            }
            group.addAll(e.getValue());
            prevK = e.getKey();
        }
        Map<Integer, Node> made = new HashMap<>();
        made.put(0, endA);
        made.put(last, endB);
        for (Map.Entry<Integer, List<Node>> e : groups.entrySet()) {
            int k = e.getKey();
            keep[k] = true;
            Node n = made.computeIfAbsent(k, x -> plan.addNode(Math.floor(mx[k]), Math.floor(mz[k])));
            for (Node o : e.getValue()) {
                if (n.junction == Junction.AUTO) {
                    n.junction = o.junction;
                }
                moved.put(o, n);
            }
        }
        RoadStyle st = twoWay(plan, p.get(0).name, endA, endB);
        if (st == null) {
            st = RoadStyle.dualCarriageway();
            st.lanes = Math.max(p.get(0).style.lanes, q.get(0).style.lanes);
            st.laneWidth = Math.max(p.get(0).style.laneWidth, q.get(0).style.laneWidth);
            st.reservation = (int) Math.max(1, Math.min(9, Math.round(gap - 2 * st.directionWidth())));
        }
        String name = p.get(0).name;
        Node from = endA;
        Set<Integer> line = new HashSet<>(List.of(endA.id, endB.id));
        List<RoadPlan.Segment> added = new ArrayList<>();
        for (int k = 1; k <= last; k++) {
            if (!keep[k]) {
                continue;
            }
            int kk = k;
            Node to = made.computeIfAbsent(k, x -> plan.addNode(Math.floor(mx[kk]), Math.floor(mz[kk])));
            int pieces = (int) Math.ceil(Math.hypot(to.x - from.x, to.z - from.z) / MAX_STRETCH);
            for (int i = 1; i < pieces; i++) {
                Node m = plan.addNode(Math.floor(from.x + (to.x - from.x) * i / pieces), Math.floor(from.z + (to.z - from.z) * i / pieces));
                added.add(plan.addSegment(from.id, m.id, st));
                line.add(m.id);
                from = m;
            }
            if (to != from) {
                added.add(plan.addSegment(from.id, to.id, st));
            }
            line.add(to.id);
            from = to;
        }
        added.forEach(s -> s.name = name);
        Set<Integer> addedIds = new HashSet<>();
        added.forEach(s -> addedIds.add(s.id));
        // the sides go; what joined them joins the middle (a bit of road across between them goes too)
        Map<Integer, Node> to = new HashMap<>();
        moved.forEach((o, n) -> to.put(o.id, n));
        plan.segments.removeIf(s -> sides.contains(s.id));
        for (RoadPlan.Segment s : plan.segments) {
            s.a = to.containsKey(s.a) ? to.get(s.a).id : s.a;
            s.b = to.containsKey(s.b) ? to.get(s.b).id : s.b;
        }
        plan.segments.removeIf(s -> s.a == s.b || !addedIds.contains(s.id) && line.contains(s.a) && line.contains(s.b));
        Set<Integer> still = new HashSet<>();
        plan.segments.forEach(s -> {
            still.add(s.a);
            still.add(s.b);
        });
        Set<Node> gone = new HashSet<>(inner);
        gone.addAll(List.of(pa, pb, qa, qb));
        plan.nodes.removeIf(n -> gone.contains(n) && !still.contains(n.id));
        plan.nodes.forEach(n -> byId.put(n.id, n));
        return true;
    }

    /** A point that has to stay where it is: one brought in before, or a junction of its own (a roundabout, say). */
    private static boolean fixed(Node n, int fresh) {
        return n.id < fresh || n.junction != Junction.AUTO;
    }

    /** The one point the two sides' ends a and b become: the one that has to stay, else halfway between (a section's end left open there, if there's one). */
    private static Node end(RoadPlan plan, Node a, Node b, int fresh, Map<Long, Node> existing, Map<Node, Node> moved, Set<Integer> sides) {
        if (a == b) {
            return a;
        }
        List<Node> between = between(plan, a, b, sides);
        Node keep;
        if (fixed(a, fresh) || fixed(b, fresh)) {
            keep = fixed(a, fresh) ? a : b;
        } else {
            double x = Math.floor((a.x + b.x) / 2), z = Math.floor((a.z + b.z) / 2);
            keep = null;
            for (int dx = -2; dx <= 2 && keep == null; dx++) {
                for (int dz = -2; dz <= 2 && keep == null; dz++) {
                    Node o = existing.get(((long) ((int) x + dx) << 32) ^ (((int) z + dz) & 0xffffffffL));
                    keep = o != null && o.open && plan.nodes.contains(o) ? o : null;
                }
            }
            if (keep != null) {
                keep.open = false;                          // the next section's: the two sides' ends there join it
            } else {
                keep = a;
                a.x = x;
                a.z = z;
                a.open |= b.open;
            }
        }
        if (a != keep) {
            moved.put(a, keep);
        }
        if (b != keep) {
            moved.put(b, keep);
        }
        for (Node n : between) {
            if (n != keep && !fixed(n, fresh) && !n.open) {
                moved.put(n, keep);                         // the bit of the road across between them goes into the one junction
            }
        }
        return keep;
    }

    /** The points along the shortest way by road (not the sides) from a to b, if it's not much further than straight: neither end, in order. */
    private static List<Node> between(RoadPlan plan, Node a, Node b, Set<Integer> sides) {
        Map<Integer, Node> byId = new HashMap<>();
        plan.nodes.forEach(n -> byId.put(n.id, n));
        Map<Integer, List<Integer>> next = new HashMap<>();
        for (RoadPlan.Segment s : plan.segments) {
            if (!sides.contains(s.id)) {
                next.computeIfAbsent(s.a, k -> new ArrayList<>()).add(s.b);
                next.computeIfAbsent(s.b, k -> new ArrayList<>()).add(s.a);
            }
        }
        double limit = Math.hypot(a.x - b.x, a.z - b.z) * 1.5 + 2;
        Map<Integer, Double> dist = new HashMap<>(Map.of(a.id, 0.0));
        Map<Integer, Integer> from = new HashMap<>();
        java.util.PriorityQueue<double[]> todo = new java.util.PriorityQueue<>((u, v) -> Double.compare(u[0], v[0]));
        todo.add(new double[]{0, a.id});
        while (!todo.isEmpty()) {
            double[] t = todo.poll();
            int id = (int) t[1];
            if (id == b.id) {
                List<Node> path = new ArrayList<>();
                for (int k = from.get(id); k != a.id; k = from.get(k)) {
                    path.add(0, byId.get(k));
                }
                return path;
            }
            if (t[0] > dist.get(id)) {
                continue;
            }
            Node n = byId.get(id);
            for (int o : next.getOrDefault(id, List.of())) {
                Node m = byId.get(o);
                double d = t[0] + Math.hypot(m.x - n.x, m.z - n.z);
                if (d <= limit && d < dist.getOrDefault(o, Double.MAX_VALUE)) {
                    dist.put(o, d);
                    from.put(o, id);
                    todo.add(new double[]{d, o});
                }
            }
        }
        return List.of();
    }

    /** The street's own two-way style (its widest piece at one of these ends, else anywhere), or null if it has none. */
    private static RoadStyle twoWay(RoadPlan plan, String name, Node endA, Node endB) {
        RoadStyle atEnd = null, any = null;
        for (RoadPlan.Segment s : plan.segments) {
            if (!s.style.oneWay && s.name.equals(name)) {
                if ((s.a == endA.id || s.b == endA.id || s.a == endB.id || s.b == endB.id)
                        && (atEnd == null || s.style.halfCarriageway() > atEnd.halfCarriageway())) {
                    atEnd = s.style;
                }
                any = any == null || s.style.halfCarriageway() > any.halfCarriageway() ? s.style : any;
            }
        }
        return atEnd != null ? atEnd : any;
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
    public static boolean[] simplify(double[] x, double[] z, int a, int b, double tol) {
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
            st.laneWidth = st.lanes == 1 ? 7 : st.laneWidth;          // (a town's one-way street: one lane, as wide as a car and a bit)
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
                for (int i = 0; i + 1 < o.x().length && !feature(o.kind()); i++) {     // pavements go to building fronts, not car parks and parks
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
