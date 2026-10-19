package com.ptmuk.map;

import java.util.Set;
import net.minecraft.util.RandomSource;

/** Made-up but ordinary British street names: Station Road, Victoria Avenue, Orchard Close... */
public final class StreetNames {
    private static final String[] FIRST = {"Station", "Church", "Victoria", "Albert", "Park", "Mill", "King's", "Queen's", "Manor",
            "Orchard", "Meadow", "Windsor", "York", "Clarence", "Alexandra", "Cedar", "Willow", "Rectory", "Vicarage", "School",
            "Highfield", "Springfield", "Lime", "Marlborough", "Wellington", "Nelson", "Bridge", "Hill", "Broad", "North", "South",
            "Mount", "Priory", "Abbey", "Garden", "Cromwell", "Stanley", "Cambridge", "Oxford", "Belmont", "Fairfield", "Ashley",
            "Hawthorn", "Holly", "Laurel", "Maple", "Rose", "Woodland", "Heath", "Green", "Oak", "Elm", "Beech", "Chestnut", "Grange",
            "Brook", "Kingsley", "Avondale", "Beaconsfield", "Gladstone", "Salisbury", "Warwick", "Lancaster", "Gordon", "Russell"};
    private static final String[] LAST = {"Road", "Road", "Road", "Street", "Street", "Avenue", "Lane", "Close", "Gardens", "Crescent",
            "Way", "Drive", "Terrace", "Grove", "Place"};

    private StreetNames() {
    }

    /** A name not already in `used` (which it's added to). */
    public static String pick(RandomSource rand, Set<String> used) {
        for (int i = 0; i < 200; i++) {
            String name = FIRST[rand.nextInt(FIRST.length)] + " " + LAST[rand.nextInt(LAST.length)];
            if (used.add(name)) {
                return name;
            }
        }
        String name = FIRST[rand.nextInt(FIRST.length)] + " Road " + (used.size() + 1);
        used.add(name);
        return name;
    }
}
