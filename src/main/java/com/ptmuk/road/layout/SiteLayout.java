package com.ptmuk.road.layout;

import static com.ptmuk.road.layout.SiteFrame.BACK;
import static com.ptmuk.road.layout.SiteFrame.FRONT;
import static com.ptmuk.road.layout.SiteFrame.LEFT;
import static com.ptmuk.road.layout.SiteFrame.RIGHT;

import com.ptmuk.building.PrefabCatalog;
import com.ptmuk.road.plan.RoadPlan.Site;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Lays out one site on its own grid, in its own frame (see {@link SiteFrame}: u across the front
 * from the left as seen from the road, v back from the front edge).
 *
 * <p>Car parks are rows of bays the way UK car parks are: three blocks to a bay, five deep, with
 * six-block aisles between the rows and cross aisles joining them at the ends. Rows end in kerbed
 * islands with trees and lamp posts; wider bays for disabled drivers, parents with children and
 * electric cars go nearest the way in (or the shops). A retail park puts a terrace of shop units
 * across the back behind a paved walkway, with zebra crossings to each door. A drive-thru puts the
 * restaurant inside a one-way lane that runs round it clockwise, so the order point and the
 * windows are on the driver's side.
 */
public final class SiteLayout {
    static final int BAY = 3, WIDE_BAY = 4, BAY_DEPTH = 5, AISLE = 6, EDGE = 3, LANE = 4, WALK = 6;
    private static final byte T_TARMAC = 1, T_GREEN = 2, T_FOOT = 3, T_PLOT = 4, T_TACTILE = 5;
    private static final int AISLE_BAND = 0, ROW_DOWN = 1, ROW_UP = 2;

    public enum ItemType {
        TREE, LAMP, EV_CHARGER, PAY_MACHINE, PARKING_SIGN, TROLLEYS, BIN, BOLLARD, BENCH,
        DISABLED_BAY, PARENT_BAY, EV_BAY, ORDER_POINT, MENU_BOARD, PICNIC_TABLE
    }

    /** Something stood on the site: where, and the local direction it faces. */
    public record Item(ItemType type, int u, int v, int dir) {
    }

    /** A building (or the canopy along a retail park's shops): its footprint, the way its front faces and its brand. */
    public record Building(String kind, int u0, int v0, int u1, int v1, int dir, int brand) {
    }

    /** A way in: the local side, the middle of it along that side, and how wide. */
    public record Entrance(int side, int at, int width) {
    }

    /** A made-up name for a shop or restaurant: its sign colours and what it sells. */
    public record Brand(String name, String sub, int board, int ink, String kind) {
    }

    public static final List<Brand> RETAIL_BRANDS = List.of(
            new Brand("TRAILHEAD", "OUTDOOR & CAMPING", 0xFF2E5E3A, 0xFFFFFFFF, "outdoor"),
            new Brand("GLOW", "BEAUTY & HEALTH", 0xFFE04E8C, 0xFFFFFFFF, "beauty"),
            new Brand("FASHION FIX", "CLOTHING & SHOES", 0xFF1A1A1A, 0xFFFFFFFF, "fashion"),
            new Brand("NEST HOME", "HOMEWARE", 0xFFF2EEE6, 0xFF3A3A3A, "home"),
            new Brand("SPRINT", "SPORTS", 0xFF1F4FB8, 0xFFFFFFFF, "sports"),
            new Brand("PET PLANET", "PET SUPPLIES", 0xFF2A9D8F, 0xFFFFFFFF, "pets"),
            new Brand("SPARKS", "ELECTRICALS", 0xFF222B5C, 0xFFFFD43B, "electricals"),
            new Brand("FIX & BUILD", "DIY & GARDEN", 0xFFF07F13, 0xFFFFFFFF, "diy"),
            new Brand("FRESHWAYS", "FOOD", 0xFF1E7A3C, 0xFFFFFFFF, "food"),
            new Brand("COMFY SOFAS", "FURNITURE", 0xFF6B2D5C, 0xFFFFFFFF, "furniture"));

    public static final List<Brand> DRIVE_THRU_BRANDS = List.of(
            new Brand("BUN & GONE", "BURGERS • DRIVE-THRU", 0xFFC8202A, 0xFFF2C21B, "burgers"),
            new Brand("BEAN THERE", "COFFEE • DRIVE-THRU", 0xFF1E5A3C, 0xFFF4EBD0, "coffee"),
            new Brand("CLUCK SHACK", "CHICKEN • DRIVE-THRU", 0xFFE0661C, 0xFFFFFFFF, "chicken"),
            new Brand("DOUGH TO GO", "BAKERY • DRIVE-THRU", 0xFF1F4E8C, 0xFFFFFFFF, "bakery"));

    public final Site site;
    public final SiteFrame frame;
    public final int w, d;
    /** Layout kinds, how far above the site's level each surface is, markings and the local direction of each. */
    public final byte[] kind;
    public final float[] rise;
    public final byte[] mark;
    public final byte[] markDir;
    public final List<Item> items = new ArrayList<>();
    public final List<Building> buildings = new ArrayList<>();
    public final List<Entrance> entrances;
    /** Set when the site was too small for what was asked and a car park was laid out instead. */
    public String warning;

    private final byte[] type;
    private final boolean[] aisle;
    /** The planted band round the edge: level with the ground outside, where islands are raised. */
    private final boolean[] edgeBand;
    private final List<int[]> payPlaces = new ArrayList<>();
    /** The wide bays still to place: they go in the first rows laid out near the focus. */
    private List<ItemType> wideBays;

    public SiteLayout(Site site, SiteFrame frame, List<Entrance> entrances) {
        this.site = site;
        this.frame = frame;
        this.w = frame.width();
        this.d = frame.depth();
        this.entrances = entrances.isEmpty() ? List.of(new Entrance(FRONT, w / 2, 8)) : entrances;
        int n = w * d;
        type = new byte[n];
        aisle = new boolean[n];
        edgeBand = new boolean[n];
        kind = new byte[n];
        rise = new float[n];
        mark = new byte[n];
        markDir = new byte[n];
        switch (site.type) {
            case RETAIL_PARK -> retailPark();
            case DRIVE_THRU -> driveThru();
            case PARADE -> parade();
            default -> carPark();
        }
        throats();
        lawns();
        edgePlanting();
        finish();
    }

    public int at(int u, int v) {
        return u < 0 || v < 0 || u >= w || v >= d ? -1 : v * w + u;
    }

    // ------------------------------------------------------------------ the three kinds of site

    private void carPark() {
        perimeter(EDGE, EDGE, EDGE, EDGE);
        int u0 = EDGE, v0 = EDGE, u1 = w - 1 - EDGE, v1 = d - 1 - EDGE;
        if (u1 - u0 < 5 || v1 - v0 < 5) {
            open(u0, v0, u1, v1);
            return;
        }
        double[] focus = point(entrances.get(0));
        parking(u0, v0, u1, v1, rowsAlongV(u1 - u0 + 1, v1 - v0 + 1), false, false, focus[0], focus[1], true);
        // a parking sign by each way in, and pay machines on the islands nearest the first
        for (Entrance e : entrances) {
            int off = e.width / 2 + 2;
            switch (e.side) {
                case FRONT -> item(ItemType.PARKING_SIGN, e.at - off, 1, FRONT);
                case BACK -> item(ItemType.PARKING_SIGN, e.at + off, d - 2, BACK);
                case LEFT -> item(ItemType.PARKING_SIGN, 1, e.at + off, LEFT);
                default -> item(ItemType.PARKING_SIGN, w - 2, e.at - off, RIGHT);
            }
        }
        payPlaces.sort(Comparator.comparingDouble(p -> Math.hypot(p[0] - focus[0], p[1] - focus[1])));
        int machines = Math.min(payPlaces.size(), 1 + bays / 80);
        for (int k = 0; k < machines; k++) {
            int[] p = payPlaces.get(k);
            item(ItemType.PAY_MACHINE, p[0], p[1], p[2]);
        }
    }

    /** The terraced high street units a shop parade is made of (shops with flats or offices over them). */
    public static final List<String> PARADE_UNITS = List.of("fish_and_chips", "pharmacy", "coffee_shop", "newsagent", "bakery",
            "bookmakers", "chinese_takeaway", "barbers", "games_shop", "estate_agent", "kebab_shop", "launderette", "phone_shop",
            "pizza_takeaway", "wine_bar", "pound_shop", "cocktail_bar");

    /**
     * A row of high street shops along the front, side by side in a mix that changes with the
     * site's brand number, centred in the frontage with paving at the ends and behind.
     */
    private void parade() {
        fill(0, 0, w - 1, d - 1, T_FOOT);
        int n = PARADE_UNITS.size(), used = 0;
        List<int[]> row = new ArrayList<>();                    // {unit, width, depth}
        for (int next = Math.floorMod(site.brand, n), tries = 0; tries < n; ) {
            PrefabCatalog.Entry e = PrefabCatalog.get(PARADE_UNITS.get(next));
            next = (next + 1) % n;
            if (e != null && e.width() <= w - used && e.depth() <= d) {
                row.add(new int[]{PARADE_UNITS.indexOf(e.id()), e.width(), e.depth()});
                used += e.width();
                tries = 0;
            } else {
                tries++;
            }
        }
        if (row.isEmpty()) {
            warning = "Too small for a shop parade (it needs about 10 wide and 22 deep)";
            return;
        }
        int u = (w - used) / 2;
        for (int[] r : row) {
            fill(u, 0, u + r[1] - 1, r[2] - 1, T_PLOT);
            buildings.add(new Building("prefab", u, 0, u + r[1] - 1, r[2] - 1, FRONT, r[0]));
            u += r[1];
        }
    }

    private void retailPark() {
        int back = 2;
        int depth = Math.max(16, Math.min(48, site.unitDepth));
        int minCar = AISLE + BAY_DEPTH + AISLE;
        if (d - EDGE - WALK - depth - back < minCar) {
            depth = d - EDGE - WALK - back - minCar;
        }
        if (w < 2 * EDGE + 12 || depth < 12) {
            warning = "Too small for a retail park (it needs about 20 x 50) - laid out as a car park";
            carPark();
            return;
        }
        perimeter(EDGE, EDGE, back, EDGE);
        int ua = EDGE, ub = w - 1 - EDGE;
        int vb0 = d - back - depth, vb1 = d - back - 1, vf0 = vb0 - WALK;
        fill(ua, vb0, ub, vb1, T_PLOT);
        fill(ua, vf0, ub, vb0 - 1, T_FOOT);
        int span = ub - ua + 1;
        int n = site.units > 0 ? site.units : Math.max(1, Math.round(span / 24f));
        n = Math.max(1, Math.min(n, span / 10));
        List<Integer> doors = new ArrayList<>();
        List<Integer> walls = new ArrayList<>();
        int u = ua;
        for (int i = 0; i < n; i++) {
            int uw = span / n + (i < span % n ? 1 : 0);
            buildings.add(new Building("unit", u, vb0, u + uw - 1, vb1, FRONT, Math.floorMod(site.brand + i, RETAIL_BRANDS.size())));
            doors.add(u + uw / 2);
            walls.add(u);
            u += uw;
        }
        walls.add(ub);
        if (site.canopy) {
            buildings.add(new Building("canopy", ua, vf0, ub, vb0 - 1, FRONT, n));
        }
        parking(ua, EDGE, ub, vf0 - 1, site.rows != 1, false, false, (ua + ub) / 2.0, vf0, true);
        for (int door : doors) {
            zebra(door, vf0 - 1);
            item(ItemType.TROLLEYS, door - 3, vb0 - 2, RIGHT);
            item(ItemType.BIN, door + 3, vb0 - 1, FRONT);
            if (site.canopy) {
                item(ItemType.BENCH, door + 5, vb0 - 1, FRONT);
            }
        }
        // bollards along the edge of the walkway, clear of the crossings and the canopy masts
        for (int k = ua + 2; k <= ub - 2; k += 3) {
            boolean clear = true;
            for (int door : doors) {
                clear &= Math.abs(k - door) > 2;
            }
            for (int wall : walls) {
                clear &= Math.abs(k - wall) > 1;
            }
            if (clear) {
                item(ItemType.BOLLARD, k, vf0, FRONT);
            }
        }
        if (!site.canopy && site.lights) {
            for (int k = ua + 6; k <= ub - 6; k += 16) {
                item(ItemType.LAMP, k, vf0 + 1, FRONT);
            }
        }
    }

    /** Where the i-th of n shop units starts across a terrace span wide (the canopy's masts line up with them). */
    public static int unitStart(int span, int n, int i) {
        return i * (span / n) + Math.min(i, span % n);
    }

    private void driveThru() {
        int wb = Math.min(18, w - 2 * EDGE - 2 * LANE - 4);
        int room = d - 2 * EDGE - LANE - 1;               // front edge to the back of the restaurant
        if (wb < 12 || room < AISLE + 3 + 10) {
            warning = "Too small for a drive-thru (it needs about 30 x 30) - laid out as a car park";
            carPark();
            return;
        }
        perimeter(EDGE, EDGE, EDGE, EDGE);
        int lu0 = EDGE, lu1 = EDGE + LANE - 1;            // the lane: up the left, across the back, down the right
        int ru1 = w - 1 - EDGE, ru0 = ru1 - LANE + 1;
        int bl1 = d - 1 - EDGE, bl0 = bl1 - LANE + 1;
        int iu0 = lu1 + 1, iu1 = ru0 - 1, iv = bl0 - 1;   // kerbed islands inside the lane
        int bu1 = iu1 - 1, bu0 = bu1 - wb + 1;            // the restaurant, beside the right-hand lane (the windows)
        int bv1 = iv - 1;
        boolean bayRow = room - AISLE - 3 - BAY_DEPTH >= 10;
        int db = Math.max(10, Math.min(14, room - AISLE - 3 - (bayRow ? BAY_DEPTH : 0)));
        int bv0 = bv1 - db + 1;
        int fv0 = bv0 - 3;                                 // walkway along the front of it
        int rv0 = bayRow ? fv0 - BAY_DEPTH : fv0;          // bays facing the main aisle
        int av1 = rv0 - 1, av0 = av1 - AISLE + 1;          // the main aisle, right across the site
        fill(EDGE, EDGE, w - 1 - EDGE, d - 1 - EDGE, T_GREEN);
        fill(lu0, av1 + 1, lu1, bl1, T_TARMAC);
        fill(ru0, av1 + 1, ru1, bl1, T_TARMAC);
        fill(lu0, bl0, ru1, bl1, T_TARMAC);
        fill(bu0, bv0, bu1, bv1, T_PLOT);
        fill(iu0 + 1, bv0, bu0 - 1, bv1, T_FOOT);          // seating beside the restaurant
        fill(iu0 + 1, fv0, iu1 - 1, bv0 - 1, T_FOOT);
        if (bayRow) {
            fill(iu0 + 1, rv0, iu1 - 1, rv0 + BAY_DEPTH - 1, T_TARMAC);
            Grid g = new Grid(iu0 + 1, rv0, false);
            layRow(g, 0, false, 0, iu1 - 1 - (iu0 + 1), false, false, specials(), 0);
        }
        open(EDGE, av0, w - 1 - EDGE, av1);
        if (av0 - EDGE >= BAY_DEPTH) {
            parking(EDGE, EDGE, w - 1 - EDGE, av0 - 1, false, false, true, (bu0 + bu1) / 2.0, fv0,
                    av0 - EDGE > BAY_DEPTH + AISLE);
        } else if (av0 > EDGE) {
            fill(EDGE, EDGE, w - 1 - EDGE, av0 - 1, T_GREEN);
            for (int v = EDGE; v < av0; v++) {
                for (int u = EDGE; u < w - EDGE; u++) {
                    edgeBand[v * w + u] = true;             // part of the verge along the front
                }
            }
        }
        // arrows round the lane
        for (int v = av1 + 3; v <= bl0 - 2; v += 8) {
            line(lu0 + 1, v, Layout.M_ARROW, BACK);
        }
        for (int v = bl0 - 3; v >= av1 + 2; v -= 8) {
            line(ru0 + 2, v, Layout.M_ARROW, FRONT);
        }
        for (int u = lu1 + 4; u <= ru0 - 3; u += 8) {
            line(u, bl0 + 1, Layout.M_ARROW, RIGHT);
        }
        // the menu board, then the order point, on the island beside the way in
        int order = Math.min(bv0 + 2, bl0 - 3);
        item(ItemType.ORDER_POINT, iu0, order, LEFT);
        item(ItemType.MENU_BOARD, iu0, order - 4, LEFT);
        // picnic tables beside the restaurant
        for (int u = iu0 + 2; u + 1 < bu0; u += 4) {
            for (int v = bv0 + 1; v + 1 <= bv1; v += 4) {
                item(ItemType.PICNIC_TABLE, u, v, RIGHT);
            }
        }
        item(ItemType.BIN, bu0 - 1, fv0, FRONT);
        int brand = Math.floorMod(site.brand, DRIVE_THRU_BRANDS.size());
        buildings.add(new Building("drive_thru", bu0, bv0, bu1, bv1, FRONT, brand));
        if (site.lights) {
            item(ItemType.LAMP, iu0, av1 + 3, LEFT);
            item(ItemType.LAMP, iu1, av1 + 3, RIGHT);
            item(ItemType.LAMP, (iu0 + iu1) / 2, iv, BACK);
        }
    }

    // ------------------------------------------------------------------ parking

    /** p runs along the rows, q across them; a grid's origin is its corner at the site's front left. */
    private record Grid(int u0, int v0, boolean alongV) {
        int u(int p, int q) {
            return alongV ? u0 + q : u0 + p;
        }

        int v(int p, int q) {
            return alongV ? v0 + p : v0 + q;
        }

        int plusP() {
            return alongV ? BACK : RIGHT;
        }

        int plusQ() {
            return alongV ? RIGHT : BACK;
        }
    }

    private int bays;

    /** Bays in rows running front to back (true) or across the front. */
    private boolean rowsAlongV(int lu, int lv) {
        if (site.rows == 0) {
            return true;
        }
        if (site.rows == 1) {
            return false;
        }
        return estimate(lv, lu) * 13 >= estimate(lu, lv) * 10;     // facing the road unless across fits clearly more
    }

    private static int estimate(int lp, int lq) {
        int rows = 0;
        for (int[] b : stack(lq, false, false)) {
            if (b[0] != AISLE_BAND) {
                rows++;
            }
        }
        boolean crossEnd = lp >= 2 * AISLE + 12;
        return rows * Math.max(0, (lp - AISLE - (crossEnd ? AISLE : 0) - 6) / BAY);
    }

    /**
     * Rows and aisles across a strip lq deep: each row backs onto another row or the edge and
     * faces an aisle. Returns {kind, start, length}, shifted so any row needing the aisle beyond
     * the far edge meets it.
     */
    private static List<int[]> stack(int lq, boolean aisleBefore, boolean aisleAfter) {
        List<int[]> out = new ArrayList<>();
        int q = 0;
        boolean prevAisle = aisleBefore;
        while (true) {
            int rem = lq - q;
            if (prevAisle) {
                if (rem < BAY_DEPTH) {
                    break;
                }
                out.add(new int[]{ROW_DOWN, q, BAY_DEPTH});
                q += BAY_DEPTH;
                prevAisle = false;
            } else if (rem >= BAY_DEPTH + AISLE) {
                out.add(new int[]{ROW_UP, q, BAY_DEPTH});
                out.add(new int[]{AISLE_BAND, q + BAY_DEPTH, AISLE});
                q += BAY_DEPTH + AISLE;
                prevAisle = true;
            } else if (aisleAfter && rem >= BAY_DEPTH) {
                out.add(new int[]{ROW_UP, q, BAY_DEPTH});
                q += BAY_DEPTH;
                break;
            } else {
                break;
            }
        }
        int shift = aisleAfter && !aisleBefore ? lq - q : 0;
        for (int[] b : out) {
            b[1] += shift;
        }
        return out;
    }

    /**
     * Rows of bays filling a rectangle, with cross aisles joining the rows' aisles at the ends
     * (cross: false when the rows face an aisle outside the rectangle and need none).
     */
    private void parking(int u0, int v0, int u1, int v1, boolean alongV, boolean aisleBefore, boolean aisleAfter,
                         double fu, double fv, boolean cross) {
        fill(u0, v0, u1, v1, T_TARMAC);
        Grid g = new Grid(u0, v0, alongV);
        int lp = alongV ? v1 - v0 + 1 : u1 - u0 + 1;
        int lq = alongV ? u1 - u0 + 1 : v1 - v0 + 1;
        boolean crossEnd = cross && lp >= 2 * AISLE + 12;
        int pa = cross ? AISLE : 0, pb = lp - 1 - (crossEnd ? AISLE : 0);
        for (int q = 0; q < lq; q++) {
            for (int p = 0; p < lp; p++) {
                if (p < pa || p > pb) {
                    setAisle(g.u(p, q), g.v(p, q));
                }
            }
        }
        if (pb - pa + 1 < 2 * BAY + 1) {
            open(u0, v0, u1, v1);
            return;
        }
        List<int[]> bands = stack(lq, aisleBefore, aisleAfter);
        boolean[] used = new boolean[lq];
        List<int[]> rows = new ArrayList<>();
        for (int[] b : bands) {
            for (int q = b[1]; q < b[1] + b[2]; q++) {
                used[q] = true;
                if (b[0] == AISLE_BAND) {
                    for (int p = pa; p <= pb; p++) {
                        setAisle(g.u(p, q), g.v(p, q));
                    }
                }
            }
            if (b[0] != AISLE_BAND) {
                rows.add(b);
            }
        }
        for (int q = 0; q < lq; q++) {
            if (!used[q]) {
                for (int p = pa; p <= pb; p++) {
                    set(g.u(p, q), g.v(p, q), T_GREEN);
                }
            }
        }
        // focus: the special bays go in the rows nearest it, at its end of them
        double fp = alongV ? fv - v0 : fu - u0, fq = alongV ? fu - u0 : fv - v0;
        boolean fromEnd = fp > (pa + pb) / 2.0;
        // a way in that meets a row rather than an aisle cuts through it (see throats): no wide bays there
        java.util.Set<int[]> cut = new java.util.HashSet<>();
        for (Entrance e : entrances) {
            int edge = alongV ? (e.side == LEFT ? -1 : e.side == RIGHT ? 1 : 0) : (e.side == FRONT ? -1 : e.side == BACK ? 1 : 0);
            for (int k = 0; edge != 0 && k < bands.size(); k++) {
                int[] b = bands.get(edge < 0 ? k : bands.size() - 1 - k);
                if (b[0] == AISLE_BAND) {
                    break;
                }
                cut.add(b);
            }
        }
        List<int[]> order = new ArrayList<>(rows);
        order.removeIf(cut::contains);
        order.sort(Comparator.comparingDouble(r -> Math.abs(r[1] + 2 - fq)));
        List<ItemType> wanted = specials();
        java.util.Map<int[], List<ItemType>> perRow = new java.util.HashMap<>();
        int k = 0;
        for (int[] r : order) {
            List<ItemType> mine = new ArrayList<>();
            while (k < wanted.size() && mine.size() < 4) {
                mine.add(wanted.get(k++));
            }
            perRow.put(r, mine);
        }
        int index = 0;
        for (int[] r : rows) {
            layRow(g, r[1], r[0] == ROW_UP, pa, pb, cross, fromEnd, perRow.get(r), index++);
        }
        // a line along the back where two rows meet back to back
        for (int[] r : rows) {
            if (r[0] != ROW_DOWN) {
                continue;
            }
            for (int[] o : rows) {
                if (o[0] == ROW_UP && o[1] == r[1] + BAY_DEPTH) {
                    int q = r[1] + BAY_DEPTH - 1;
                    for (int p = pa; p <= pb; p++) {
                        line(g.u(p, q), g.v(p, q), Layout.M_LINE, g.plusP());
                    }
                }
            }
        }
    }

    /** The wide bays not placed yet (each is placed once, in the first rows that ask). */
    private List<ItemType> specials() {
        if (wideBays != null) {
            List<ItemType> rest = wideBays;
            wideBays = new ArrayList<>();
            return rest;
        }
        List<ItemType> out = new ArrayList<>();
        for (int i = 0; i < site.disabled; i++) {
            out.add(ItemType.DISABLED_BAY);
        }
        for (int i = 0; i < site.parentChild; i++) {
            out.add(ItemType.PARENT_BAY);
        }
        for (int i = 0; i < site.ev; i++) {
            out.add(ItemType.EV_BAY);
        }
        wideBays = new ArrayList<>();
        return out;
    }

    /**
     * One row of bays five deep from q0, along p from pa to pb, facing its aisle (+q when faceUp).
     * Bays start from one end (fromEnd: the pb end), the wide ones first.
     */
    private void layRow(Grid g, int q0, boolean faceUp, int pa, int pb, boolean islands, boolean fromEnd,
                        List<ItemType> specials, int index) {
        int qMid = q0 + 2, qBack = faceUp ? q0 : q0 + BAY_DEPTH - 1;
        int toAisle = faceUp ? g.plusQ() : (g.plusQ() + 2) & 3;
        boolean ends = islands && pb - pa + 1 >= 6 + 2 * BAY;
        if (ends) {
            green(g, pa, q0, pa + 2, q0 + BAY_DEPTH - 1);
            green(g, pb - 2, q0, pb, q0 + BAY_DEPTH - 1);
            island(g, pa + 1, qMid, index, (g.plusP() + 2) & 3);
            island(g, pb - 1, qMid, index + 1, g.plusP());
            payPlaces.add(new int[]{g.u(pa + 1, faceUp ? q0 + 3 : q0 + 1), g.v(pa + 1, faceUp ? q0 + 3 : q0 + 1), (g.plusP() + 2) & 3});
        }
        int step = fromEnd ? -1 : 1;
        int first = fromEnd ? (ends ? pb - 2 : pb) : (ends ? pa + 2 : pa);
        int last = fromEnd ? (ends ? pa + 2 : pa) : (ends ? pb - 2 : pb);
        int pos = first;
        if (!ends) {
            side(g, pos, q0);
        }
        int k = 0, since = 0;
        while (true) {
            ItemType sp = specials != null && k < specials.size() ? specials.get(k) : null;
            if (sp == null && site.trees >= 2 && since >= 8 && step * (last - (pos + 3 * step)) >= BAY) {
                // a planted island in place of a bay
                green(g, Math.min(pos, pos + 2 * step), q0, Math.max(pos, pos + 2 * step), q0 + BAY_DEPTH - 1);
                island(g, pos + step, qMid, index + k + since, toAisle);
                pos += 2 * step;
                since = 0;
                continue;
            }
            int next = pos + step * (sp != null ? WIDE_BAY : BAY);
            if (step * (last - next) < 0) {
                break;
            }
            if (!(ends && next == last)) {
                side(g, next, q0);
            }
            if (sp != null) {
                int p = pos + 2 * step;
                item(sp, g.u(p, qMid), g.v(p, qMid), toAisle);
                if (sp == ItemType.EV_BAY) {
                    item(ItemType.EV_CHARGER, g.u(p, qBack), g.v(p, qBack), toAisle);
                }
                k++;
            } else {
                since++;
            }
            bays++;
            pos = next;
        }
        if (pos != last) {
            green(g, Math.min(pos + step, last), q0, Math.max(pos + step, last), q0 + BAY_DEPTH - 1);
        }
    }

    /** The white line between two bays, the depth of the row. */
    private void side(Grid g, int p, int q0) {
        for (int q = q0; q < q0 + BAY_DEPTH; q++) {
            line(g.u(p, q), g.v(p, q), Layout.M_LINE, g.plusQ());
        }
    }

    private void green(Grid g, int p0, int q0, int p1, int q1) {
        for (int p = p0; p <= p1; p++) {
            for (int q = q0; q <= q1; q++) {
                set(g.u(p, q), g.v(p, q), T_GREEN);
            }
        }
    }

    /** A tree or a lamp post in an island, taking turns. */
    private void island(Grid g, int p, int q, int n, int dir) {
        int u = g.u(p, q), v = g.v(p, q);
        if (site.lights && (n % 2 == 0 || site.trees == 0)) {
            item(ItemType.LAMP, u, v, dir);
        } else if (site.trees > 0) {
            item(ItemType.TREE, u, v, dir);
        }
    }

    // ------------------------------------------------------------------ edges, ways in and planting

    private void perimeter(int f, int l, int b, int r) {
        for (int v = 0; v < d; v++) {
            for (int u = 0; u < w; u++) {
                if (u < l || u >= w - r || v < f || v >= d - b) {
                    set(u, v, T_GREEN);
                    edgeBand[v * w + u] = true;
                }
            }
        }
        for (Entrance e : entrances) {
            int lo = e.at - e.width / 2, hi = lo + e.width - 1;
            for (int k = lo; k <= hi; k++) {
                switch (e.side) {
                    case FRONT -> {
                        if (k >= l && k < w - r) {
                            open(k, 0, k, f - 1);
                        }
                    }
                    case BACK -> {
                        if (k >= l && k < w - r) {
                            open(k, d - b, k, d - 1);
                        }
                    }
                    case LEFT -> {
                        if (k >= f && k < d - b) {
                            open(0, k, l - 1, k);
                        }
                    }
                    default -> {
                        if (k >= f && k < d - b) {
                            open(w - r, k, w - 1, k);
                        }
                    }
                }
            }
        }
    }

    private double[] point(Entrance e) {
        return switch (e.side) {
            case FRONT -> new double[]{e.at, 0};
            case BACK -> new double[]{e.at, d - 1};
            case LEFT -> new double[]{0, e.at};
            default -> new double[]{w - 1, e.at};
        };
    }

    /**
     * Where a way in meets a row of bays rather than an aisle, the bays in front of it give way to
     * a short drive through to the nearest aisle.
     */
    private void throats() {
        for (Entrance e : entrances) {
            int du = e.side == LEFT ? 1 : e.side == RIGHT ? -1 : 0;
            int dv = e.side == FRONT ? 1 : e.side == BACK ? -1 : 0;
            int lo = e.at - e.width / 2, hi = lo + e.width - 1;
            int u = e.side == LEFT ? EDGE : e.side == RIGHT ? w - 1 - EDGE : 0;
            int v = e.side == FRONT ? EDGE : e.side == BACK ? d - 1 - EDGE : 0;
            for (int depth = 0; depth < 14; depth++) {
                int cu = u + du * depth, cv = v + dv * depth;
                boolean reached = true;
                for (int k = lo; k <= hi; k++) {
                    int i = du != 0 ? at(cu, k) : at(k, cv);
                    reached &= i < 0 || aisle[i];
                }
                if (reached) {
                    break;
                }
                boolean blocked = false;
                for (int k = lo; k <= hi; k++) {
                    int i = du != 0 ? at(cu, k) : at(k, cv);
                    blocked |= i >= 0 && (type[i] == T_PLOT || type[i] == T_FOOT);
                }
                if (blocked) {
                    break;
                }
                for (int k = lo - 1; k <= hi + 1; k++) {
                    int i = du != 0 ? at(cu, k) : at(k, cv);
                    if (i < 0) {
                        continue;
                    }
                    if (k < lo || k > hi) {
                        if (!aisle[i] && type[i] == T_TARMAC) {
                            type[i] = T_GREEN;
                            mark[i] = 0;
                        }
                    } else {
                        type[i] = T_TARMAC;
                        aisle[i] = true;
                        mark[i] = 0;
                    }
                }
                final int fu = cu, fv = cv;
                items.removeIf(it -> du != 0 ? it.u() == fu && it.v() >= lo - 1 && it.v() <= hi + 1
                        : it.v() == fv && it.u() >= lo - 1 && it.u() <= hi + 1);
            }
        }
    }

    /** Trees (and lamp posts) round the edge, clear of the ways in. */
    private void edgePlanting() {
        int n = 0;
        int[][] lines = {{1, 1, w - 2, 1}, {w - 2, 1, w - 2, d - 2}, {w - 2, d - 2, 1, d - 2}, {1, d - 2, 1, 1}};
        for (int[] ln : lines) {
            int du = Integer.signum(ln[2] - ln[0]), dv = Integer.signum(ln[3] - ln[1]);
            int len = Math.max(Math.abs(ln[2] - ln[0]), Math.abs(ln[3] - ln[1]));
            for (int s = 4; s <= len - 4; s += 8) {
                int u = ln[0] + du * s, v = ln[1] + dv * s;
                int i = at(u, v);
                if (i < 0 || type[i] != T_GREEN || near(u, v, 3) || openNear(u, v, 3)) {
                    continue;
                }
                n++;
                if (site.lights && n % 3 == 0) {
                    int inward = du != 0 ? (v < d / 2 ? BACK : FRONT) : (u < w / 2 ? RIGHT : LEFT);
                    item(ItemType.LAMP, u, v, inward);
                } else if (site.trees > 0) {
                    item(ItemType.TREE, u, v, FRONT);
                }
            }
        }
    }

    /** Trees dotted over any bigger stretch of grass inside the site. */
    private void lawns() {
        if (site.trees == 0) {
            return;
        }
        for (int v = EDGE + 1; v < d - EDGE - 1; v += 3) {
            for (int u = EDGE + 1; u < w - EDGE - 1; u += 3) {
                boolean grass = true;
                for (int a = -1; a <= 1 && grass; a++) {
                    for (int b = -1; b <= 1 && grass; b++) {
                        int i = at(u + a, v + b);
                        grass = i >= 0 && type[i] == T_GREEN;
                    }
                }
                if (grass && !near(u, v, 4)) {
                    item(ItemType.TREE, u, v, FRONT);
                }
            }
        }
    }

    private boolean near(int u, int v, int r) {
        for (Item it : items) {
            if (Math.abs(it.u() - u) <= r && Math.abs(it.v() - v) <= r) {
                return true;
            }
        }
        return false;
    }

    private boolean openNear(int u, int v, int r) {
        for (int a = -r; a <= r; a++) {
            for (int b = -r; b <= r; b++) {
                int i = at(u + a, v + b);
                if (i >= 0 && type[i] == T_TARMAC && (u + a < EDGE || v + b < EDGE || u + a >= w - EDGE || v + b >= d - EDGE)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A zebra crossing three wide from the walkway out across the aisle in front of it. */
    private void zebra(int u, int v) {
        for (int k = 0; k < AISLE; k++) {
            int i = at(u, v - k);
            if (i < 0 || !aisle[i]) {
                break;
            }
            for (int du = -1; du <= 1; du++) {
                int j = at(u + du, v - k);
                if (j >= 0 && type[j] == T_TARMAC) {
                    mark[j] = Layout.M_ZEBRA;
                    markDir[j] = BACK;
                }
            }
        }
        for (int du = -1; du <= 1; du++) {
            int j = at(u + du, v + 1);
            if (j >= 0 && type[j] == T_FOOT) {
                type[j] = T_TACTILE;
            }
        }
    }

    // ------------------------------------------------------------------ cells

    private void set(int u, int v, byte t) {
        int i = at(u, v);
        if (i >= 0) {
            type[i] = t;
            aisle[i] = false;
            mark[i] = 0;
        }
    }

    private void fill(int u0, int v0, int u1, int v1, byte t) {
        for (int v = v0; v <= v1; v++) {
            for (int u = u0; u <= u1; u++) {
                set(u, v, t);
            }
        }
    }

    /** Open tarmac that cars drive across. */
    private void open(int u0, int v0, int u1, int v1) {
        for (int v = v0; v <= v1; v++) {
            for (int u = u0; u <= u1; u++) {
                setAisle(u, v);
            }
        }
    }

    private void setAisle(int u, int v) {
        int i = at(u, v);
        if (i >= 0) {
            type[i] = T_TARMAC;
            aisle[i] = true;
            mark[i] = 0;
        }
    }

    private void line(int u, int v, int m, int dir) {
        int i = at(u, v);
        if (i >= 0 && type[i] == T_TARMAC) {
            mark[i] = (byte) m;
            markDir[i] = (byte) dir;
        }
    }

    private void item(ItemType t, int u, int v, int dir) {
        int i = at(u, v);
        if (i < 0) {
            return;
        }
        for (Item it : items) {
            if (it.u() == u && it.v() == v) {
                return;
            }
        }
        items.add(new Item(t, u, v, dir));
        if (t != ItemType.DISABLED_BAY && t != ItemType.PARENT_BAY && t != ItemType.EV_BAY) {
            mark[i] = 0;
        }
    }

    /** Kerbs go round anything raised next to the tarmac; the rest is grass, paving or floor. */
    private void finish() {
        for (int v = 0; v < d; v++) {
            for (int u = 0; u < w; u++) {
                int i = v * w + u;
                boolean edge = tarmacAt(u - 1, v) || tarmacAt(u + 1, v) || tarmacAt(u, v - 1) || tarmacAt(u, v + 1);
                switch (type[i]) {
                    case T_TARMAC -> {
                        kind[i] = Layout.ROAD;
                        rise[i] = 0;
                    }
                    case T_FOOT -> {
                        kind[i] = edge ? Layout.KERB : Layout.FOOTWAY;
                        rise[i] = 0.5f;
                    }
                    case T_TACTILE -> {
                        kind[i] = Layout.TACTILE;
                        rise[i] = 0.5f;
                    }
                    case T_PLOT -> {
                        kind[i] = Layout.PLOT;
                        rise[i] = 1;
                    }
                    default -> {
                        kind[i] = edge ? Layout.ISLAND_KERB : Layout.ISLAND;
                        rise[i] = edge ? 0.5f : edgeBand[i] ? 0 : 1;
                    }
                }
                if (type[i] != T_TARMAC) {
                    mark[i] = 0;
                }
            }
        }
    }

    private boolean tarmacAt(int u, int v) {
        int i = at(u, v);
        return i >= 0 && type[i] == T_TARMAC;
    }

    /** How many bays were laid out (for the planner's status line). */
    public int bayCount() {
        return bays;
    }
}
