package com.ptmuk.building;

import java.util.List;
import java.util.Map;

/**
 * Made-up British shop names for the buildings the planner, the Town Builder and the Prefab Tool
 * put up, so no two high streets look the same: for each kind of shop a handful of patterns
 * (a family name, a first name, a local place, a pun), the line under it and a sign colour that
 * suits it. Everything comes from a seed (where the building goes), so the planner shows the
 * same names the builder puts up. Plain Java.
 */
public final class ShopNames {
    /** A sign: its name, the line under it and its board and lettering colours. */
    public record Sign(String text, String sub, int board, int ink) {
    }

    private record Kind(List<String> names, List<String> subs, int[][] colours) {
    }

    static final List<String> SURNAMES = List.of("SMITH", "JONES", "TAYLOR", "BROWN", "WILLIAMS", "WILSON", "JOHNSON", "DAVIES",
            "ROBINSON", "WRIGHT", "THOMPSON", "EVANS", "WALKER", "ROBERTS", "GREEN", "HALL", "WOOD", "JACKSON", "CLARKE",
            "PATEL", "KHAN", "SINGH", "AHMED", "MURPHY", "KELLY", "CAMPBELL", "STEWART", "HUGHES", "EDWARDS", "MORGAN",
            "BAKER", "TURNER", "PARKER", "COOPER", "HILL", "WARD", "MORRIS", "KING", "HARRIS", "LEWIS", "BENNETT",
            "GRIFFITHS", "PRICE", "MASON", "HOLMES", "FOSTER", "ELLIS", "MARSH", "DIXON", "FLETCHER", "SHAH", "LEE");
    static final List<String> FIRST = List.of("DAVE", "SUE", "MO", "RAJ", "TONY", "JACKIE", "BEV", "KEV", "GARY", "SHARON",
            "STAN", "ALI", "NICK", "JO", "SAM", "MANDY", "PETE", "DEBBIE", "TERRY", "LINDA", "VIC", "ANGIE", "YUSUF",
            "PRIYA", "MARCO", "GINO", "ROSA", "ELAINE", "COLIN", "MAUREEN");
    static final List<String> PLACES = List.of("VILLAGE", "PARKSIDE", "STATION", "MARKET", "CASTLE", "ABBEY", "BRIDGE",
            "WESTGATE", "EASTGATE", "NORTHGATE", "VICTORIA", "ALBERT", "KINGSWAY", "RIVERSIDE", "HILLTOP", "MILLFIELD",
            "BROADWAY", "CROSSWAYS", "CHURCH STREET", "MARKET SQUARE", "OLD TOWN", "PRIORY", "MEADOW", "GREEN LANE");

    private static final int NAVY = 0xFF1C2E4A, BLACK = 0xFF141414, GREEN = 0xFF1E4D2B, BURGUNDY = 0xFF6A1B2A, RED = 0xFFB3202A,
            CREAM = 0xFFF2E8CF, WHITE = 0xFFF4F4F4, TEAL = 0xFF0E6E6E, PLUM = 0xFF4B2A55, BLUE = 0xFF1F5FB0, ORANGE = 0xFFE0661C,
            YELLOW_BOARD = 0xFFF2C21B, GREY = 0xFF3A3F45, PINK = 0xFFE04E8C;
    private static final int W = 0xFFFFFFFF, K = 0xFF141414, GOLD = 0xFFE8C34A, YEL = 0xFFF2D04A;
    private static final int[][] SMART = {{NAVY, W}, {BLACK, GOLD}, {GREEN, CREAM}, {BURGUNDY, GOLD}, {CREAM, NAVY}, {TEAL, W},
            {PLUM, W}, {GREY, W}, {WHITE, K}};

    private static final Map<String, Kind> KINDS = Map.ofEntries(
            Map.entry("fish_and_chips", new Kind(List.of("THE GOLDEN FRYER", "{S}'S FISH BAR", "THE {P} CHIPPY", "THE CODFATHER",
                    "BATTER DAYS", "THE FRYING SCOTSMAN", "NEPTUNE FISH BAR", "SEA BREEZE", "OCEAN BLUE", "THE PLAICE TO BE",
                    "{F}'S CHIPPY", "THE SALTY SEA DOG", "OH MY COD", "THE CHIPPY"),
                    List.of("Fish & Chips • Est. 19{Y}", "Traditional Fish & Chips", "Fish • Chips • Pies • Sausages", "Eat In or Take Away"),
                    new int[][]{{NAVY, YEL}, {BLUE, W}, {TEAL, W}, {WHITE, BLUE}, {YELLOW_BOARD, NAVY}})),
            Map.entry("chinese_takeaway", new Kind(List.of("GOLDEN DRAGON", "JADE GARDEN", "LUCKY STAR", "HAPPY VALLEY", "NEW CHINA",
                    "PEKING HOUSE", "WOK THIS WAY", "PANDA HOUSE", "BAMBOO GARDEN", "GREAT WALL", "RED LANTERN", "THE {P} CANTONESE",
                    "ORIENTAL PEARL", "GOLDEN BOWL"),
                    List.of("Chinese Takeaway • Open 5pm - 11pm", "Cantonese Cuisine • Free Delivery", "Chinese & Thai Takeaway",
                            "Chinese Takeaway • Phone Orders Welcome"),
                    new int[][]{{RED, GOLD}, {BLACK, GOLD}, {RED, W}, {BURGUNDY, GOLD}})),
            Map.entry("kebab_shop", new Kind(List.of("ISTANBUL GRILL", "ANKARA KEBAB HOUSE", "BOSPHORUS GRILL", "SULTAN'S", "MEGA BITE",
                    "CHARCOAL GRILL", "{F}'S KEBABS", "MEZE GRILL", "TASTY BITES", "THE KEBAB HOUSE", "ANATOLIA", "EFES GRILL"),
                    List.of("Kebabs • Pizza • Burgers • Late", "Kebabs • Burgers • Fried Chicken", "Open Till 3am • Free Delivery",
                            "Charcoal Grill • Kebabs • Wraps"),
                    new int[][]{{BLACK, 0xFFF04030}, {RED, W}, {BLACK, YEL}, {ORANGE, K}})),
            Map.entry("pizza_takeaway", new Kind(List.of("SPEEDY SLICE", "PIZZA EXPRESSO", "PIZZA PALACE", "PIZZA PLANET", "NAPOLI",
                    "MAMMA MIA", "PIZZA ROMA", "{F}'S PIZZA", "LA DOLCE VITA", "DOUGH BROS", "SLICE OF LIFE", "THE PIZZA OVEN"),
                    List.of("Pizza Delivered Hot • Order Online", "Stone Baked Pizza • Collection & Delivery", "Pizza • Pasta • Wings",
                            "Free Delivery Over £15"),
                    new int[][]{{GREEN, W}, {RED, W}, {WHITE, RED}, {BLACK, 0xFFF04030}})),
            Map.entry("pharmacy", new Kind(List.of("{S} PHARMACY", "{P} CHEMIST", "{S} & {S2} CHEMISTS", "HEALTHWISE PHARMACY",
                    "THE {P} PHARMACY", "{S}'S PHARMACY", "WELLBEING PHARMACY", "CARE CHEMISTS"),
                    List.of("Pharmacy • Prescriptions • Health Advice", "NHS Prescriptions • Flu Jabs", "Dispensing Chemist",
                            "Pharmacy • Photo • Toiletries"),
                    new int[][]{{GREEN, W}, {TEAL, W}, {WHITE, GREEN}, {NAVY, W}})),
            Map.entry("coffee_shop", new Kind(List.of("THE DAILY GRIND", "{F}'S CAFE", "BEAN AROUND", "THE COFFEE POT", "BREW HA HA",
                    "MUG SHOT", "THE TEA ROOMS", "CAFE NERONI", "THE COSY CUP", "FROTH & CO", "THE GREASY SPOON", "{P} CAFE",
                    "ESPRESSO YOURSELF"),
                    List.of("Coffee • Cakes • Breakfast", "All Day Breakfast • Teas & Coffees", "Freshly Roasted Coffee",
                            "Sandwiches • Paninis • Cakes"),
                    SMART)),
            Map.entry("newsagent", new Kind(List.of("{S} NEWS", "{F}'S NEWSAGENTS", "{P} NEWS & BOOZE", "THE PAPER SHOP", "CORNER STORE",
                    "NEWS & BOOZE", "{F}'S LOCAL", "{P} STORES", "THE PAPER CHAIN", "OPEN ALL HOURS"),
                    List.of("Newsagent • Off Licence • Lottery", "Papers • Sweets • Tobacco • Lottery", "Groceries • Off Licence",
                            "Open 7 Days • 6am - 10pm"),
                    new int[][]{{BLUE, W}, {RED, W}, {NAVY, YEL}, {GREEN, W}, {YELLOW_BOARD, K}})),
            Map.entry("bakery", new Kind(List.of("{S}'S BAKERY", "THE GOLDEN LOAF", "CRUST & CRUMB", "THE BREAD BASKET", "{S} & SON BAKERS",
                    "BATCH", "THE {P} BAKEHOUSE", "RISE & SHINE", "THE OVEN DOOR", "KNEAD TO KNOW", "THE COTTAGE LOAF"),
                    List.of("Bakers • Est. 19{Y}", "Fresh Bread • Cakes • Pasties", "Craft Bakery • Sandwiches to Order",
                            "Baked Fresh Every Morning"),
                    new int[][]{{CREAM, BURGUNDY}, {BURGUNDY, CREAM}, {GREEN, CREAM}, {NAVY, CREAM}, {BLACK, GOLD}})),
            Map.entry("bookmakers", new Kind(List.of("{S} BETS", "LUCKY HORSESHOE", "{S} & {S2} BOOKMAKERS", "WINNERS' ENCLOSURE",
                    "{S} TURF ACCOUNTANTS", "FAST FURLONG", "THE BETTING SHOP", "PHOTO FINISH", "ODDS ON"),
                    List.of("Bookmakers • Horses • Football", "Live Sport • In-Play Betting", "Racing • Football • Virtuals",
                            "Licensed Betting Office"),
                    new int[][]{{BLUE, W}, {GREEN, YEL}, {RED, W}, {NAVY, YEL}, {BLACK, W}})),
            Map.entry("barbers", new Kind(List.of("{F}'S BARBERS", "SHARP CUTS", "THE BARBER SHOP", "BLADES", "FADE AWAY", "TOP CUTS",
                    "HAIR BY {F}", "CUT ABOVE", "CURL UP & DYE", "THE GENTS", "{P} BARBERS", "SNIP SNIP", "CLIP JOINT"),
                    List.of("Barbers • Walk-ins Welcome", "Hair & Beauty • Appointments", "Gents Hair • Hot Towel Shaves",
                            "Unisex Hair Salon"),
                    new int[][]{{BLACK, W}, {RED, W}, {NAVY, W}, {PINK, W}, {WHITE, K}, {GREY, W}})),
            Map.entry("games_shop", new Kind(List.of("GAME ON", "LEVEL UP", "PLAYER ONE", "{P} GAMES", "PIXEL PALACE", "RETRO REPLAY",
                    "CONSOLE CORNER", "THE GAME VAULT", "HIGH SCORE"),
                    List.of("Games • Consoles • Trade-ins", "Buy • Sell • Exchange", "Retro & New Games", "Video Games & Accessories"),
                    new int[][]{{BLACK, 0xFF40E0FF}, {PLUM, W}, {BLUE, W}, {RED, W}})),
            Map.entry("estate_agent", new Kind(List.of("{S} & {S2}", "{S} {S2} PROPERTY", "{P} HOMES", "KEY MOVES", "{S} ESTATES",
                    "MOVE ON UP", "HOME TRUTHS", "{S} & CO"),
                    List.of("Estate Agents • Sales & Lettings", "Residential Sales & Lettings", "Independent Estate Agents",
                            "Lettings • Property Management"),
                    new int[][]{{NAVY, W}, {PLUM, W}, {GREEN, W}, {WHITE, NAVY}, {GREY, 0xFFF2C21B}})),
            Map.entry("launderette", new Kind(List.of("{P} LAUNDERETTE", "SUDS", "BUBBLES", "SPIN CITY", "THE WASH HOUSE", "CLEAN MACHINE",
                    "SOAP ON THE ROPES", "THE LAUNDRY BASKET", "FRESH & FOLD"),
                    List.of("Launderette • Service Washes", "Self Service • Dry Cleaning", "Washes • Duvets • Ironing",
                            "Open 7 Days 8am - 8pm"),
                    new int[][]{{BLUE, W}, {TEAL, W}, {WHITE, BLUE}, {0xFF2AA0D0, W}})),
            Map.entry("phone_shop", new Kind(List.of("{P} MOBILES", "PHONE FIX", "SCREEN SAVERS", "CELL MATES", "UNLOCKED", "TECH REPAIRS",
                    "{F}'S MOBILES", "FIX IT FAST"),
                    List.of("Phones • Repairs • Unlocking", "Screen Repairs While You Wait", "Phones • Tablets • Accessories",
                            "We Buy Old Phones"),
                    new int[][]{{BLACK, W}, {BLUE, W}, {ORANGE, K}, {GREY, 0xFF40E0FF}})),
            Map.entry("wine_bar", new Kind(List.of("THE CORK & BOTTLE", "VINO", "THE WINE LODGE", "{S}'S WINE BAR", "THE GRAPEVINE",
                    "UNCORKED", "THE VINEYARD", "BOTTLE & GLASS"),
                    List.of("Wine Bar • Tapas", "Wine • Cocktails • Small Plates", "Fine Wines & Craft Beers", "Live Music Fridays"),
                    new int[][]{{BURGUNDY, GOLD}, {BLACK, GOLD}, {PLUM, CREAM}, {GREEN, GOLD}})),
            Map.entry("pound_shop", new Kind(List.of("POUND PLUS", "POUND PALACE", "EVERYTHING £1", "BARGAIN BUYS", "{P} DISCOUNT",
                    "PENNY WISE", "VALUE CENTRE", "BARGAIN BASEMENT", "THE POUND STOP"),
                    List.of("Everything £1 And More", "Household • Toys • Snacks", "Bargains Every Day", "Big Brands • Low Prices"),
                    new int[][]{{YELLOW_BOARD, RED}, {RED, YEL}, {BLUE, YEL}, {0xFF7A2A9A, YEL}})),
            Map.entry("cocktail_bar", new Kind(List.of("THE BLUE LAGOON", "SHAKERS", "THE LOUNGE", "NEON", "THE GIN JOINT", "MOJITO",
                    "VELVET", "THE COPPER STILL", "TWISTED", "THE SPEAKEASY"),
                    List.of("Cocktail Bar • Happy Hour 5-7pm", "Cocktails • DJs Fri & Sat", "Gin • Cocktails • Prosecco",
                            "Cocktail Lounge"),
                    new int[][]{{PLUM, 0xFFFF7AD0}, {BLACK, 0xFF40E0FF}, {BLACK, GOLD}, {TEAL, W}})),
            Map.entry("corner_pub", new Kind(List.of("THE {C} {N}", "THE {N} & {N2}", "THE RED LION", "THE CROWN", "THE ROYAL OAK",
                    "THE KING'S HEAD", "THE PLOUGH", "THE GEORGE", "THE BELL", "THE WHEATSHEAF", "THE NAG'S HEAD", "THE GREEN MAN",
                    "THE CROSS KEYS", "THE ANCHOR", "THE RISING SUN", "THE {P} ARMS", "THE {S} ARMS"),
                    List.of("Free House • Real Ales • Food Served Daily", "Real Ales • Sky Sports • Beer Garden",
                            "Home Cooked Food • Quiz Night Thursdays", "Traditional Pub • Est. 18{Y}"),
                    new int[][]{{BLACK, GOLD}, {GREEN, GOLD}, {BURGUNDY, GOLD}, {NAVY, GOLD}, {CREAM, BLACK}})),
            Map.entry("bank", new Kind(List.of("{R} BANK", "{R} BUILDING SOCIETY", "{R} & COUNTIES BANK", "THE {R} SAVINGS BANK"),
                    List.of("", "Mortgages • Savings • Current Accounts"),
                    new int[][]{{0xFFE6DCC2, 0xFF3A3A3A}, {NAVY, W}, {BURGUNDY, W}, {GREEN, W}})),
            Map.entry("church", new Kind(List.of("ST MARY'S", "ST JOHN'S", "ST PETER'S", "ST MICHAEL'S", "ALL SAINTS", "HOLY TRINITY",
                    "ST GEORGE'S", "ST ANDREW'S", "CHRIST CHURCH", "ST PAUL'S", "ST LUKE'S", "ST JAMES'"),
                    List.of("Sunday Service 10.30am • All Welcome", "Sunday Eucharist 9.30am • Evensong 6pm", "Parish Church • All Welcome"),
                    new int[][]{{NAVY, GOLD}, {BURGUNDY, GOLD}, {BLACK, GOLD}})));

    private static final int[][] SMART_BRIGHT = {{GREEN, W}, {PINK, W}, {BLACK, W}, {0xFFF2EEE6, 0xFF3A3A3A}, {BLUE, W},
            {TEAL, W}, {NAVY, YEL}, {ORANGE, W}, {0xFF1E7A3C, W}, {PLUM, W}};

    /** Buildings named like another kind. */
    private static final Map<String, String> ALIAS = Map.of("town_pub", "corner_pub", "country_pub", "corner_pub", "estate_pub",
            "corner_pub", "gastropub", "corner_pub", "coaching_inn", "corner_pub", "corner_shop", "newsagent", "convenience_store",
            "newsagent");

    private static final Map<String, Kind> MORE = Map.ofEntries(
            Map.entry("indian_restaurant", new Kind(List.of("THE TAJ", "SPICE OF {P}", "{P} TANDOORI", "BOMBAY NIGHTS", "THE JAIPUR",
                    "MOTI MAHAL", "THE BENGAL LANCER", "SAFFRON", "THE SPICE ROOM", "MUMBAI MASALA", "THE KASHMIR"),
                    List.of("Indian Restaurant & Takeaway", "Balti • Tandoori • Fully Licensed", "Fine Indian Cuisine"),
                    new int[][]{{BURGUNDY, GOLD}, {BLACK, GOLD}, {ORANGE, W}, {PLUM, GOLD}})),
            Map.entry("peri_peri_restaurant", new Kind(List.of("FLAME GRILL", "PIRI PIRI PALACE", "HOT COALS", "CHICKEN CABANA",
                    "THE PERI SHACK", "FIRE & SPICE", "LISBON GRILL"),
                    List.of("Flame Grilled Chicken • Eat In or Take Away", "Peri-Peri Chicken • Wraps • Burgers", "Grilled to Order"),
                    new int[][]{{BLACK, 0xFFF04030}, {RED, W}, {ORANGE, K}})),
            Map.entry("pizza_restaurant", new Kind(List.of("{F}'S TRATTORIA", "LA PIAZZA", "IL FORNO", "PIZZERIA {F}", "CASA MIA",
                    "LUIGI'S", "VESUVIO", "LA TAVOLA", "TERRA ROSSA"),
                    List.of("Italian Restaurant • Wood Fired Pizza", "Pizza • Pasta • Wine", "Authentic Italian Kitchen"),
                    new int[][]{{GREEN, W}, {RED, CREAM}, {CREAM, GREEN}, {BLACK, GOLD}})),
            Map.entry("nightclub", new Kind(List.of("PULSE", "ECLIPSE", "OASIS", "CLUB TROPICANA", "NEON NIGHTS", "THE VAULT", "ELECTRIC",
                    "SPACE", "HYPE"),
                    List.of("Open Thu - Sat Till Late", "Nightclub • Over 18s", "DJs Every Weekend"),
                    new int[][]{{BLACK, 0xFFFF40C0}, {BLACK, 0xFF40E0FF}, {PLUM, W}})),
            Map.entry("guest_house", new Kind(List.of("SEA VIEW", "THE {P} GUEST HOUSE", "{S}'S B&B", "THE ANCHORAGE", "BAY VIEW",
                    "THE LAURELS", "ROSEDENE", "THE MOORINGS"),
                    List.of("Bed & Breakfast • Vacancies", "Guest House • En Suite Rooms", "B&B • Sea Views"),
                    new int[][]{{WHITE, NAVY}, {NAVY, W}, {CREAM, GREEN}})),
            retail("outdoor", "OUTDOOR & CAMPING", "TRAILHEAD", "PEAK & PINE", "BASECAMP", "{P} OUTDOORS", "SUMMIT", "WILDTRACK"),
            retail("beauty", "BEAUTY & HEALTH", "GLOW", "RADIANCE", "PURE BEAUTY", "BLUSH", "BLOOM"),
            retail("fashion", "CLOTHING & SHOES", "FASHION FIX", "THREADS", "STYLE CITY", "URBAN EDGE", "WARDROBE", "LOOK SHARP"),
            retail("home", "HOMEWARE", "NEST HOME", "HEARTH & HOME", "LIVING SPACE", "HOMEMAKERS", "HOUSE PROUD"),
            retail("sports", "SPORTS", "SPRINT", "SPORTS ZONE", "PRO SPORT", "GOAL", "ATHLETICA"),
            retail("pets", "PET SUPPLIES", "PET PLANET", "PAWS & CLAWS", "WAGGY TAILS", "PETS CORNER", "FUR & FEATHER"),
            retail("electricals", "ELECTRICALS", "SPARKS", "CURRENT", "PLUG IN", "TECHWORLD", "WIRED"),
            retail("diy", "DIY & GARDEN", "FIX & BUILD", "TOOL TIME", "BUILD IT", "HAMMER & NAIL", "HOMEFIX"),
            retail("food", "FOOD", "FRESHWAYS", "VALUE FOODS", "HARVEST", "FOOD HALL", "BASKET"),
            retail("furniture", "FURNITURE", "COMFY SOFAS", "SOFA CITY", "LOUNGE LIFE", "SIT TIGHT", "OAK & IRON"),
            Map.entry("drive_burgers", new Kind(List.of("BUN & GONE", "BURGER BARN", "PATTY WAGON", "STACKED", "GRILL & GO"),
                    List.of("BURGERS • DRIVE-THRU"), new int[][]{{0xFFC8202A, 0xFFF2C21B}, {BLACK, YEL}, {BLUE, W}})),
            Map.entry("drive_coffee", new Kind(List.of("BEAN THERE", "DRIVE BREW", "JAVA JUNCTION", "CUPPA ON THE GO"),
                    List.of("COFFEE • DRIVE-THRU"), new int[][]{{0xFF1E5A3C, 0xFFF4EBD0}, {0xFF5A3A28, CREAM}, {BLACK, CREAM}})),
            Map.entry("drive_chicken", new Kind(List.of("CLUCK SHACK", "WING IT", "CHICKEN RUN", "CRISPY CO", "THE COOP"),
                    List.of("CHICKEN • DRIVE-THRU"), new int[][]{{0xFFE0661C, W}, {RED, W}, {YELLOW_BOARD, RED}})),
            Map.entry("drive_bakery", new Kind(List.of("DOUGH TO GO", "ROLL UP", "THE BAKE STOP", "CRUMBS"),
                    List.of("BAKERY • DRIVE-THRU"), new int[][]{{0xFF1F4E8C, W}, {CREAM, BURGUNDY}, {BURGUNDY, CREAM}})));

    private static Map.Entry<String, Kind> retail(String kind, String sub, String... names) {
        return Map.entry("retail_" + kind, new Kind(List.of(names), List.of(sub), SMART_BRIGHT));
    }

    private static final List<String> COLOURS = List.of("RED", "WHITE", "BLACK", "GOLDEN", "GREEN", "BLUE", "GREY");
    private static final List<String> ANIMALS = List.of("LION", "HART", "HORSE", "SWAN", "BULL", "BOAR", "DRAGON", "COCKEREL", "STAG", "FOX");
    private static final List<String> NOUNS = List.of("ROSE", "CROWN", "FOX", "HOUNDS", "COACH", "HORSES", "HARE", "PLOUGH", "HARROW",
            "ANCHOR", "HOPE", "BELL", "BOOT", "SHOVEL", "FEATHERS", "FIDDLE");
    private static final List<String> REGIONS = List.of("NORTHERN", "WESSEX", "MERCIA", "PENNINE", "CHILTERN", "SEVERN", "THAMES VALLEY",
            "WEALDEN", "MENDIP", "COTSWOLD", "FENLAND", "CUMBRIAN", "HUMBER", "SOLENT");

    private ShopNames() {
    }

    /** Whether there are names for this kind of building (a prefab's id, or a parade unit's). */
    public static boolean has(String kind) {
        return kind(kind) != null;
    }

    private static Kind kind(String kind) {
        String k = ALIAS.getOrDefault(kind, kind);
        return KINDS.containsKey(k) ? KINDS.get(k) : MORE.get(k);
    }

    /** A sign for this kind of building, picked by the seed; null if there aren't names for it. */
    public static Sign pick(String kind, long seed) {
        Kind k = kind(kind);
        if (k == null) {
            return null;
        }
        java.util.Random r = new java.util.Random(seed * 0x9E3779B97F4A7C15L + kind.hashCode());
        String name = fill(k.names.get(r.nextInt(k.names.size())), r);
        String sub = fill(k.subs.get(r.nextInt(k.subs.size())), r);
        int[] c = k.colours[r.nextInt(k.colours.length)];
        return new Sign(name.length() > 30 ? name.substring(0, 30) : name, sub, c[0], c[1]);
    }

    /** A seed from where a building goes. */
    public static long seed(int x, int z) {
        return x * 341873128712L + z * 132897987541L;
    }

    private static String fill(String pattern, java.util.Random r) {
        String s1 = SURNAMES.get(r.nextInt(SURNAMES.size())), s2 = SURNAMES.get(r.nextInt(SURNAMES.size()));
        if (s2.equals(s1)) {
            s2 = SURNAMES.get((SURNAMES.indexOf(s1) + 7) % SURNAMES.size());
        }
        String n1 = NOUNS.get(r.nextInt(NOUNS.size())), n2 = NOUNS.get(r.nextInt(NOUNS.size()));
        if (n2.equals(n1)) {
            n2 = NOUNS.get((NOUNS.indexOf(n1) + 3) % NOUNS.size());
        }
        return pattern.replace("{S2}", s2).replace("{S}", s1).replace("{F}", FIRST.get(r.nextInt(FIRST.size())))
                .replace("{P}", PLACES.get(r.nextInt(PLACES.size()))).replace("{N2}", n2)
                .replace("{N}", r.nextBoolean() ? n1 : ANIMALS.get(r.nextInt(ANIMALS.size())))
                .replace("{C}", COLOURS.get(r.nextInt(COLOURS.size()))).replace("{R}", REGIONS.get(r.nextInt(REGIONS.size())))
                .replace("{Y}", String.valueOf(10 + r.nextInt(89)));
    }
}
