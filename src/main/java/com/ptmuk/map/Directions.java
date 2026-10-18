package com.ptmuk.map;

import com.ptmuk.network.RouteMessage;
import java.util.ArrayList;
import java.util.List;

/** A route's turns, in words: "Turn left onto High Street", named after the roads people have named on the map. */
public final class Directions {
    /** Turns gentler than this are just the road bending. */
    private static final double BEND = 30;

    private Directions() {
    }

    public static RouteMessage of(List<float[]> pts, List<MapData.Road> roads, String destination) {
        int n = pts.size();
        float[] x = new float[n], z = new float[n];
        for (int i = 0; i < n; i++) {
            x[i] = pts.get(i)[0];
            z[i] = pts.get(i)[1];
        }
        List<RouteMessage.Step> steps = new ArrayList<>();
        String road = roadName(roads, x, z, 0);
        steps.add(new RouteMessage.Step(0, "Head " + compass(x[1] - x[0], z[1] - z[0]) + (road == null ? "" : " on " + road)));
        for (int i = 1; i < n - 1; i++) {
            double turn = Math.toDegrees(Math.atan2(z[i + 1] - z[i], x[i + 1] - x[i]) - Math.atan2(z[i] - z[i - 1], x[i] - x[i - 1]));
            turn = ((turn + 540) % 360) - 180;                 // right is positive (x east, z south)
            String next = roadName(roads, x, z, i);
            boolean renamed = next != null && !next.equals(road);
            if (Math.abs(turn) < BEND && !renamed) {
                continue;
            }
            String side = turn > 0 ? "right" : "left";
            String what = Math.abs(turn) < BEND ? "Continue" : Math.abs(turn) < 60 ? "Bear " + side
                    : Math.abs(turn) < 135 ? "Turn " + side : "Turn sharp " + side;
            steps.add(new RouteMessage.Step(i, what + (next == null ? "" : " onto " + next)));
            road = next;
        }
        steps.add(new RouteMessage.Step(n - 1, "Arrive at " + destination));
        return new RouteMessage(x, z, steps, "", destination);
    }

    /** The named road the stretch from point i to i + 1 runs along, if any. */
    private static String roadName(List<MapData.Road> roads, float[] x, float[] z, int i) {
        double mx = (x[i] + x[i + 1]) / 2.0, mz = (z[i] + z[i + 1]) / 2.0;
        MapData.Road best = null;
        double bestD = 8;
        for (MapData.Road r : roads) {
            double d = Math.max(MapPlaces.distance(r, mx, mz), MapPlaces.distance(r, x[i + 1], z[i + 1]) - 4);
            if (d < bestD) {
                bestD = d;
                best = r;
            }
        }
        return best == null ? null : best.name();
    }

    public static String compass(double dx, double dz) {
        String[] names = {"east", "south-east", "south", "south-west", "west", "north-west", "north", "north-east"};
        int i = (int) Math.round(Math.toDegrees(Math.atan2(dz, dx)) / 45.0);
        return names[Math.floorMod(i, 8)];
    }
}
