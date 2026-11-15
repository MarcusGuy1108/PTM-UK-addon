package com.ptmuk.shop;

import static com.ptmuk.shop.Product.*;

import com.ptmuk.home.HomeFurnitureType;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The kinds of shop a till can be: what its staff wear, whether you take things off the shelves
 * and hand them over (a supermarket, a corner shop) or order at the counter (a chippy, a café, a
 * pub, a hairdresser), what's on the menu, how dear it is, and how it's made while you wait.
 * A till works out which it is from the fittings round it and the shop's sign.
 */
public enum ShopKind {
    SUPERMARKET("Supermarket", 0, false, 1.0, List.of()),
    CORNER_SHOP("Corner shop", 1, false, 1.25, List.of()),
    PHARMACY("Pharmacy", 7, false, 1.1, List.of()),
    SHOP("Shop", 1, false, 1.15, List.of()),
    CHIPPY("Fish and chip shop", 2, true, 1.0, List.of(FISH_AND_CHIPS, CHIPS, SAUSAGE, CURRY_SAUCE, MUSHY_PEAS, COLA, ORANGE_CAN, WATER)),
    KEBAB_SHOP("Kebab shop", 2, true, 1.0, List.of(KEBAB, CHIPS, CURRY_SAUCE, COLA, ORANGE_CAN)),
    PIZZA_TAKEAWAY("Pizza takeaway", 2, true, 1.0, List.of(PIZZA, CHIPS, COLA, COLA_BOTTLE, ICE_CREAM)),
    CAFE("Café", 3, true, 1.0, List.of(TEA, COFFEE, BACON_ROLL, TOASTIE, CAKE, CROISSANTS, ORANGE_JUICE, WATER)),
    PUB("Pub", 4, true, 1.0, List.of(PINT, WINE, PIE, CRISPS, CRISPS_CHEESE, CRISPS_VINEGAR, COLA)),
    CHARITY_SHOP("Charity shop", 5, true, 1.0, List.of()),
    CLOTHES_SHOP("Clothes shop", 1, true, 1.0, List.of()),
    HAIRDRESSER("Hairdresser", 6, true, 1.0, List.of(HAIRCUT, BEARD_TRIM, BLOW_DRY)),
    NAIL_BAR("Nail bar", 6, true, 1.0, List.of(MANICURE, GEL_NAILS));

    public final String title;
    /** Which staff outfit (see ShopKeeperEntity's skins). */
    public final int outfit;
    /** Ordered at the counter (a menu), not taken off the shelves. */
    public final boolean counter;
    public final double dearness;
    public final List<Product> menu;

    ShopKind(String title, int outfit, boolean counter, double dearness, List<Product> menu) {
        this.title = title;
        this.outfit = outfit;
        this.counter = counter;
        this.dearness = dearness;
        this.menu = menu;
    }

    /** Second-hand (or new) things off the rails: the menu changes each day. */
    public boolean rails() {
        return this == CHARITY_SHOP || this == CLOTHES_SHOP;
    }

    /**
     * Which kind a till is, from the fittings near it and what the shop's sign says (the name of a
     * chippy, a salon, a charity...); a supermarket checkout is a supermarket's.
     */
    public static ShopKind detect(Set<HomeFurnitureType> near, String signs, boolean checkout) {
        String s = " " + signs.toUpperCase(Locale.ROOT) + " ";
        if (has(s, "NAIL")) {
            return NAIL_BAR;
        }
        if (near.contains(HomeFurnitureType.BARBER_CHAIR) || has(s, "HAIR", "BARBER", "SALON", "CUTS", "STYLE", "TRIM")) {
            return HAIRDRESSER;
        }
        if (near.contains(HomeFurnitureType.DONER_SPIT) || has(s, "KEBAB", "GRILL", "DONER")) {
            return KEBAB_SHOP;
        }
        if (near.contains(HomeFurnitureType.PIZZA_OVEN) || has(s, "PIZZA")) {
            return PIZZA_TAKEAWAY;
        }
        if (near.contains(HomeFurnitureType.FRYER) || has(s, "CHIP", "FISH", "FRIER", "FRYER", "PLAICE", "COD ", "BATTER")) {
            return CHIPPY;
        }
        if (near.contains(HomeFurnitureType.BEER_PUMPS) || near.contains(HomeFurnitureType.BACK_BAR)
                || has(s, " ARMS", " INN", "TAVERN", " PUB", "HEAD ", "CROWN", "LION", "HORSE", "ANCHOR", "BELL ", "PLOUGH", "OAK ")) {
            return PUB;
        }
        if (near.contains(HomeFurnitureType.COFFEE_MACHINE) || near.contains(HomeFurnitureType.CAKE_DISPLAY)
                || has(s, "CAFE", "CAFÉ", "COFFEE", "TEA ROOM", "BAKER", "BEAN", "BREW", "KITCHEN")) {
            return CAFE;
        }
        if (has(s, "CHARITY", "HOSPICE", "RESCUE", "TRUST", " AID", "HEART", "CANCER", "DONATE", "SECOND")) {
            return CHARITY_SHOP;
        }
        if (near.contains(HomeFurnitureType.CLOTHES_RAIL)) {
            return CLOTHES_SHOP;
        }
        if (near.contains(HomeFurnitureType.SHELVES_PHARMACY) || has(s, "PHARMACY", "CHEMIST")) {
            return PHARMACY;
        }
        if (checkout || has(s, "SUPERMARKET", "SUPERSTORE", "FRESHWAYS", "SAVEMORE")) {
            return SUPERMARKET;
        }
        for (HomeFurnitureType t : near) {
            if (Shelves.of(t) != null) {
                return CORNER_SHOP;
            }
        }
        return SHOP;
    }

    private static boolean has(String s, String... words) {
        for (String w : words) {
            if (s.contains(w)) {
                return true;
            }
        }
        return false;
    }

    /** Which shelves (the shop fittings) hold which products. */
    public static final class Shelves {
        private Shelves() {
        }

        public static Product.Shelf of(HomeFurnitureType t) {
            return switch (t) {
                case SHELVES_GROCERIES -> Product.Shelf.GROCERIES;
                case SHELVES_DRINKS, BOTTLE_FRIDGE -> Product.Shelf.DRINKS;
                case SHELVES_SNACKS -> Product.Shelf.SNACKS;
                case SHELVES_HOUSEHOLD, SHELVES_DIY, SHELVES_GAMES -> Product.Shelf.HOUSEHOLD;
                case SHELVES_NEWSAGENT -> Product.Shelf.NEWS;
                case SHELVES_PHARMACY -> Product.Shelf.PHARMACY;
                case CHILLER_CABINET -> Product.Shelf.CHILLED;
                case UPRIGHT_FREEZER, FREEZER_ISLAND -> Product.Shelf.FROZEN;
                case PRODUCE_STAND -> Product.Shelf.FRESH;
                default -> null;
            };
        }
    }
}
