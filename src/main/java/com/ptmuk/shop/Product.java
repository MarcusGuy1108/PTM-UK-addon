package com.ptmuk.shop;

import java.util.Locale;

/**
 * What the shops sell: a name, a usual UK price (pence; each shop's own prices are within a few
 * pence of it, a corner shop's dearer), and if it's food or drink, how filling it is. The shelf
 * it goes on (see Shelf), and services (a haircut) that aren't things you carry.
 */
public enum Product {
    // the fridge and the bread aisle
    MILK("Semi-skimmed milk, 2 pints", 145, 2, 0.2f, Shelf.CHILLED),
    BUTTER("Butter", 210, 2, 0.3f, Shelf.CHILLED),
    CHEESE("Mature cheddar", 280, 4, 0.6f, Shelf.CHILLED),
    MEAL_DEAL("Sandwich meal deal", 350, 7, 0.8f, Shelf.CHILLED),
    BREAD("Sliced white bread", 110, 5, 0.6f, Shelf.GROCERIES),
    EGGS("Free range eggs (6)", 220, 4, 0.5f, Shelf.GROCERIES),
    BEANS("Baked beans", 85, 4, 0.5f, Shelf.GROCERIES),
    SOUP("Tomato soup", 120, 4, 0.4f, Shelf.GROCERIES),
    CEREAL("Corn flakes", 250, 4, 0.4f, Shelf.GROCERIES),
    TEA_BAGS("Tea bags (80)", 320, 0, 0, Shelf.GROCERIES),
    PASTA("Pasta", 100, 3, 0.3f, Shelf.GROCERIES),
    APPLES("Apples (6)", 150, 4, 0.3f, Shelf.FRESH),
    BANANAS("Bananas (5)", 110, 4, 0.3f, Shelf.FRESH),
    FROZEN_PIZZA("Frozen pizza", 300, 6, 0.6f, Shelf.FROZEN),
    ICE_CREAM("Ice cream tub", 350, 3, 0.2f, Shelf.FROZEN),
    // sweets, crisps and drinks
    CRISPS("Crisps", 125, 2, 0.2f, Shelf.SNACKS),
    CHOCOLATE("Chocolate bar", 110, 3, 0.3f, Shelf.SNACKS),
    SWEETS("Bag of sweets", 100, 1, 0.1f, Shelf.SNACKS),
    COLA("Can of cola", 120, 1, 0.1f, Shelf.DRINKS),
    WATER("Bottle of water", 90, 1, 0.1f, Shelf.DRINKS),
    ENERGY_DRINK("Energy drink", 180, 1, 0.1f, Shelf.DRINKS),
    // the house
    TOILET_ROLL("Toilet roll (9)", 450, 0, 0, Shelf.HOUSEHOLD),
    WASHING_UP("Washing-up liquid", 130, 0, 0, Shelf.HOUSEHOLD),
    BATTERIES("Batteries (4)", 399, 0, 0, Shelf.HOUSEHOLD),
    LIGHT_BULB("Light bulb", 250, 0, 0, Shelf.HOUSEHOLD),
    // the newsagent's counter
    NEWSPAPER("Newspaper", 180, 0, 0, Shelf.NEWS),
    MAGAZINE("Magazine", 350, 0, 0, Shelf.NEWS),
    SCRATCHCARD("Scratchcard", 200, 0, 0, Shelf.NEWS),
    // the chemist
    PARACETAMOL("Paracetamol (16)", 75, 0, 0, Shelf.PHARMACY),
    PLASTERS("Plasters", 220, 0, 0, Shelf.PHARMACY),
    TOOTHPASTE("Toothpaste", 180, 0, 0, Shelf.PHARMACY),
    // made while you wait
    FISH_AND_CHIPS("Cod and chips", 950, 10, 1.0f, Shelf.COUNTER),
    CHIPS("Chips", 350, 6, 0.6f, Shelf.COUNTER),
    SAUSAGE("Battered sausage", 300, 5, 0.6f, Shelf.COUNTER),
    CURRY_SAUCE("Curry sauce", 180, 2, 0.2f, Shelf.COUNTER),
    MUSHY_PEAS("Mushy peas", 180, 2, 0.3f, Shelf.COUNTER),
    TEA("Cup of tea", 220, 1, 0.1f, Shelf.COUNTER),
    COFFEE("Flat white", 310, 1, 0.1f, Shelf.COUNTER),
    BACON_ROLL("Bacon roll", 380, 6, 0.7f, Shelf.COUNTER),
    CAKE("Slice of cake", 320, 4, 0.4f, Shelf.COUNTER),
    TOASTIE("Cheese toastie", 450, 6, 0.6f, Shelf.COUNTER),
    PINT("Pint of bitter", 520, 1, 0.1f, Shelf.COUNTER),
    PIE("Steak pie and mash", 1100, 10, 1.0f, Shelf.COUNTER),
    KEBAB("Doner kebab", 750, 8, 0.8f, Shelf.COUNTER),
    PIZZA("12\" pizza", 1000, 10, 0.9f, Shelf.COUNTER),
    // services: paid for, not carried
    HAIRCUT("Haircut", 1500, 0, 0, Shelf.SERVICE),
    BEARD_TRIM("Beard trim", 800, 0, 0, Shelf.SERVICE),
    BLOW_DRY("Wash and blow-dry", 2200, 0, 0, Shelf.SERVICE),
    MANICURE("Manicure", 2000, 0, 0, Shelf.SERVICE),
    GEL_NAILS("Gel nails", 2500, 0, 0, Shelf.SERVICE);

    /** Where it's kept: which shelves are stocked with it, or made at the counter, or a service. */
    public enum Shelf { CHILLED, GROCERIES, FRESH, FROZEN, SNACKS, DRINKS, HOUSEHOLD, NEWS, PHARMACY, COUNTER, SERVICE }

    public final String title;
    public final int pence, food;
    public final float saturation;
    public final Shelf shelf;

    Product(String title, int pence, int food, float saturation, Shelf shelf) {
        this.title = title;
        this.pence = pence;
        this.food = food;
        this.saturation = saturation;
        this.shelf = shelf;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean service() {
        return shelf == Shelf.SERVICE;
    }
}
