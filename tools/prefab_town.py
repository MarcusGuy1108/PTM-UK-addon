"""High street prefabs: chip shops, takeaways, cafes and restaurants, the shops every British
high street has, and bars. Each is a terraced unit (party walls either side so they line up in
a row) with a shop front and fascia, a fitted-out shop floor, stairs at the back up to flats or
more floor space, a roof and a back yard. Every name on them is made up. Coordinates as in
prefab_lib (u across the front, y up with the ground layer at 0, v from the front boundary back).
"""
from prefab_lib import B, Build, slab, stairs
import prefab_parts as P
from prefab_commercial import fit, tall, wide

STOREY = 4
WHITE, GOLD, YELLOW, BLACK, CREAM = 0xFFFFFFFF, 0xFFD9B44A, 0xFFF2C21B, 0xFF121214, 0xFFF0E6C8
STOCK, RED_BRICK = "ptmuk:london_stock_brick", "ptmuk:red_brick"


class Unit:
    """Where things are in a high street unit: party walls at u = 0 and W - 1, the shop front in
    the wall at v = 1 (the pavement in front), the back wall at v = back with a yard behind it.
    Inside: u x0..x1, v 2..back-1. Stairs go up in a 2 x 4 core against the right-hand wall at the
    back (u core_u..core_u+1, v core_v..core_v+3); its landing at v = core_v is kept clear on every
    floor, and so is a way to it and to the back door at (1, back)."""

    def __init__(self, b, W, back, storeys, door_u):
        self.b, self.W, self.back, self.storeys, self.door_u = b, W, back, storeys, door_u
        self.x0, self.x1 = 1, W - 2
        self.core_u = W - 3
        self.core_v = back - 5
        self.top = STOREY * storeys

    def counter(self, u0, u1, v, facing="front", finish="white", items=None, y=1):
        """A serving counter of fitted units, with things standing on it ({u: name}); a few
        fittings (fridges, displays) stand in the run in place of a unit."""
        for u in range(u0, u1 + 1):
            name = (items or {}).get(u)
            if name in ("bottle_fridge", "cake_display", "freezer_island"):
                fit(self.b, name, u, y, v, facing)
                continue
            fit(self.b, f"kitchen_unit_{finish}", u, y, v, facing)
            if name:
                fit(self.b, name, u, y + 1, v, facing, level="counter")


def window_cols(W):
    """Upper-floor windows across a front W wide."""
    inner = list(range(1, W - 1))
    if len(inner) <= 6:
        return [inner[1], inner[-2]] if len(inner) >= 4 else [inner[len(inner) // 2]]
    return list(range(2, W - 2, 3))


def ceiling_lights(b, u0, v0, u1, v1, y, step=3):
    """Flush lights in the ceiling, but never where the stairs cut through it."""
    for u in range(u0 + 1, u1, step):
        for v in range(v0 + 1, v1, step):
            if b.get(u, y, v).name == "minecraft:oak_planks":
                b.set(u, y, v, "sea_lantern")


def flat(b, u, y):
    """A one-bedroom flat over the shop: sofa and TV at the front, kitchen against the back wall,
    a bed beside the stairs, a table, lights and a back window."""
    x0, back = u.x0, u.back
    P.sofa(b, x0, x0 + 1, y, 2, "front", "spruce")
    kitchen_to = min(x0 + 2, u.core_u - 2)
    if kitchen_to - x0 >= 2:
        P.kitchen_run(b, x0, kitchen_to, y, back - 1, "front")
    if u.core_u - 1 > kitchen_to:
        P.bed(b, u.core_u - 1, y, back - 1, "white", "back")
    mid = (2 + u.core_v) // 2
    if x0 + 2 < u.core_u:
        P.table(b, x0 + 2, y, mid)
        b.set(x0 + 1, y, mid, stairs("spruce", "left"))
    b.light_grid(x0, 2, u.x1, back - 1, y + 2, step=3, level=12)
    b.window(x0 + 1, y + 1, back, 1, 2, "ptmuk:upvc_window")


def high_street_unit(ident, name, category, W, D, storeys, sign, desc, interior, wall=STOCK, style="victorian",
                     roof="slate", stall="black_terracotta", glass="ptmuk:shop_window", door_u=None, upper=flat,
                     floor="polished_andesite", door="jungle", yard=3):
    """Builds the unit round `interior(b, unit)` (the shop floor's fit-out) and `upper(b, unit, y)`
    for each floor above (None leaves them empty, as stock rooms or offices)."""
    back = D - 1 - yard
    top = STOREY * storeys
    half = (back - 2 + 1) // 2
    H = top + (half + 4 if roof == "slate" else 3)
    b = Build(ident, name, category, W, H, D, desc)
    door_u = W - 2 if door_u is None else door_u
    u = Unit(b, W, back, storeys, door_u)
    P.paving(b, 0, 0, W - 1, D - 1)
    b.fill(1, 0, 2, W - 2, 0, back - 1, floor)
    b.walls(0, 1, 1, W - 1, top - 1, back, wall)
    for k in range(1, storeys):
        b.fill(1, STOREY * k, 2, W - 2, STOREY * k, back - 1, "oak_planks")
    text, sub, board, ink = sign
    P.shopfront(b, 1, W - 2, 1, 1, text, sub, board, ink, door_u=door_u, stall=stall, glass=glass, door=door)
    # pilasters each side of the shop front, with brackets on a Victorian one
    trim = P.STONE if style == "victorian" else wall
    for x in (0, W - 1):
        b.fill(x, 1, 1, x, 4, 1, trim)
        if style == "victorian":
            b.set(x, 4, 0, stairs(P.STONE, "back", "top"))
    # the floors over the shop: windows, string courses, a cornice
    for k in range(1, storeys):
        y = STOREY * k + 1
        for x in window_cols(W):
            if style == "victorian":
                P.sash(b, x, y, 1)
            else:
                b.window(x, y, 1, 1, 2, "ptmuk:upvc_window")
        if style == "victorian" and k > 1:
            b.fill(0, STOREY * k, 1, W - 1, STOREY * k, 1, P.STONE)
    if storeys > 1 and style == "victorian":
        b.fill(0, top - 1, 0, W - 1, top - 1, 0, slab(P.STONE, "top"))
    # the stairs, the back door and window
    if storeys > 1:
        P.dogleg_stairs(b, u.core_u, u.core_v, [STOREY * k for k in range(storeys)], "oak")
    b.door(1, 1, back, "spruce", facing="front", hinge="left")
    if u.core_u > 3:
        b.window(3, 2, back, 1, 2, "ptmuk:upvc_window")
    # roof: slate with a parapet and chimney, or flat
    if roof == "slate":
        b.fill(0, top, 1, W - 1, top, 1, wall)
        b.fill(0, top + 1, 1, W - 1, top + 1, 1, slab(P.STONE))
        b.gable_roof(0, W - 1, 2, back, top, "ptmuk:roof_slate")
        P.close_gable(b, 0, 2, back - 1, top, wall)
        P.close_gable(b, W - 1, 2, back - 1, top, wall)
        b.chimney(0, (2 + back) // 2 - 1, top, top + half + 1, wall, w=1, d=2, pots=2)
    else:
        P.flat_roof(b, 0, W - 1, 1, back, top, deck="gray_concrete", parapet=wall, coping="smooth_stone")
    interior(b, u)
    if storeys > 1:
        ceiling_lights(b, 1, 2, W - 2, back - 1, STOREY)
    else:
        b.light_grid(1, 2, W - 2, back - 1, 3, step=3, level=14)
    for k in range(1, storeys):
        if upper:
            upper(b, u, STOREY * k + 1)
        else:
            b.light_grid(1, 2, W - 2, back - 1, STOREY * k + 3, step=3, level=11)
    # the yard
    b.set(W - 2, 1, D - 1, B("ptmuk:wheelie_bin_black", facing="north"))
    if W > 6:
        b.set(W - 3, 1, D - 1, B("ptmuk:wheelie_bin_green", facing="north"))
    return b


def dining(b, u0, u1, v0, v1, every=3, chair="dark_oak", y=1):
    """Tables for two with a chair either side, in rows across a dining room (a clear row
    between them)."""
    for v in range(v0, v1 + 1, 2):
        for x in range(u0 + 1, u1, every):
            P.table(b, x, y, v)
            b.set(x - 1, y, v, stairs(chair, "left"))
            b.set(x + 1, y, v, stairs(chair, "right"))


def dining_upstairs(chair="dark_oak"):
    def upper(b, u, y):
        dining(b, u.x0, u.core_u - 1, 3, u.core_v - 2, chair=chair, y=y)
        b.light_grid(1, 2, u.W - 2, u.back - 1, y + 2, step=3, level=12)
    return upper


# ================================================================== takeaways

def takeaway_fit(b, u, counter, cooking, seats="stools", back_room=("freezer_island",)):
    """A small takeaway: a counter across the shop (things on it from `counter`, {u: item}), a
    gap for staff, the cooking line against a partition with menu boards over it, a back room
    beyond (reached past the stairs' landing), and seats by the window for people waiting."""
    x0, x1 = u.x0, u.core_u - 1
    u.counter(x0, x1, u.core_v - 3, items=counter)
    b.fill(x0, 1, u.core_v, x1 - 1, 3, u.core_v, "calcite")          # the partition, open at x1
    for x, item in cooking.items():
        if item.startswith("kitchen_oven") or item in ("fryer", "pizza_oven", "bottle_fridge"):
            fit(b, item, x, 1, u.core_v - 1, "front")
        else:
            fit(b, "kitchen_unit_white", x, 1, u.core_v - 1, "front")
            fit(b, item, x, 2, u.core_v - 1, "front", level="counter")
    for left in range(x0 + 1, x1 + 1, 2):
        wide(b, "menu_board", left, 3, u.core_v - 1, "front")
    if seats == "stools":
        for x in range(x0, min(x1, u.door_u - 1) + 1):
            b.set(x, 1, 2, slab("smooth_quartz", "top"))
            fit(b, "bar_stool", x, 1, 3, "front")
    elif seats == "bench":
        P.sofa(b, x0, x0 + 1, 1, 2, "front", "dark_oak")
    for i, item in enumerate(back_room):
        fit(b, item, x0 + 2 + i, 1, u.back - 3, "front")
    fit(b, "kitchen_sink_white", x0 + 1, 1, u.back - 1, "front")


def fish_and_chips():
    return high_street_unit(
        "fish_and_chips", "Fish and Chip Shop", "Food & Drink", 8, 17, 3,
        ("THE GOLDEN FRYER", "Fish & Chips • Est. 1965", 0xFF1C3F94, YELLOW),
        "A traditional chippy: a range of fryers behind the counter, menu boards, stools by the window, flats over the shop.",
        lambda b, u: takeaway_fit(b, u, {2: "till", 4: "bottle_fridge"}, {1: "fryer", 2: "fryer", 3: "fryer", 4: "fryer"}),
        stall="light_blue_glazed_terracotta")


def chinese_takeaway():
    return high_street_unit(
        "chinese_takeaway", "Chinese Takeaway", "Food & Drink", 8, 17, 3,
        ("GOLDEN DRAGON", "Chinese Takeaway • Open 5pm - 11pm", 0xFFB3202A, GOLD),
        "A Chinese takeaway: red and gold fascia, a counter with menu boards behind, the kitchen with ovens and a fryer, "
        "flats over the shop.",
        lambda b, u: takeaway_fit(b, u, {3: "till"}, {1: "kitchen_oven_white", 2: "kitchen_oven_white", 3: "microwave",
                                                      4: "fryer"}, seats="bench"),
        wall=RED_BRICK, stall="red_terracotta")


def kebab_shop():
    return high_street_unit(
        "kebab_shop", "Kebab and Pizza Shop", "Food & Drink", 8, 17, 3,
        ("ISTANBUL GRILL", "Kebabs • Pizza • Burgers • Late", 0xFF101010, 0xFFF04030),
        "A late-night kebab shop: doner grills on the counter, a pizza oven, fryers, menu boards and stools, flats over it.",
        lambda b, u: takeaway_fit(b, u, {1: "doner_spit", 2: "doner_spit", 3: "till", 4: "bottle_fridge"},
                                  {1: "pizza_oven", 2: "fryer", 3: "fryer", 4: "kitchen_oven_white"}),
        style="modern", roof="flat", stall="black_terracotta")


def pizza_takeaway():
    return high_street_unit(
        "pizza_takeaway", "Pizza Delivery Takeaway", "Food & Drink", 8, 17, 2,
        ("SPEEDY SLICE", "Pizza Delivered Hot • Order Online", 0xFF0E7A55, WHITE),
        "A pizza delivery shop: pizza ovens behind the serving counter, menu boards and a bench for collections, a flat "
        "over the shop.",
        lambda b, u: takeaway_fit(b, u, {2: "till", 3: "till"}, {1: "pizza_oven", 2: "pizza_oven", 3: "pizza_oven",
                                                                 4: "bottle_fridge"}, seats="bench"),
        style="modern", roof="flat", wall="white_concrete", stall="green_terracotta")


def bakery():
    def fitout(b, u):
        cv = u.core_v - 3
        u.counter(u.x0, u.core_u - 1, cv, items={1: "cake_display", 2: "cake_display", 3: "till", 4: "coffee_machine"})
        for v in range(3, cv - 1):
            tall(b, "shelves_snacks", u.x1, 1, v, "left")
        tall(b, "chiller_cabinet", u.x0, 1, cv - 2, "right")
        fit(b, "bottle_fridge", u.x0, 1, cv - 3, "right")
        b.fill(u.x0, 1, u.core_v, u.core_u - 2, 3, u.core_v, "calcite")
        for x in range(u.x0, u.core_u - 1):
            fit(b, "pizza_oven" if x == u.x0 else "kitchen_unit_white", x, 1, u.core_v - 1, "front")
        fit(b, "kitchen_sink_white", u.x0 + 1, 1, u.back - 1, "front")
    return high_street_unit(
        "bakery", "High Street Bakery", "Food & Drink", 8, 17, 2,
        ("CRUMBS", "Bakery • Sandwiches • Coffee", 0xFF1B3557, 0xFFF4C430),
        "A bakery chain shop: cake and pastry displays on the counter, a coffee machine, a chiller of sandwiches and "
        "drinks, an oven out the back and a flat over the shop.", fitout, style="modern", roof="flat", wall=RED_BRICK,
        stall="blue_terracotta")


def coffee_shop():
    def fitout(b, u):
        cv = u.core_v - 2
        u.counter(u.x0, u.x0 + 3, cv, items={u.x0: "cake_display", u.x0 + 1: "coffee_machine", u.x0 + 2: "till"})
        P.sofa(b, u.x0, u.x0 + 1, 1, 2, "front", "dark_oak")
        P.armchair(b, u.x0, 1, 4, "left", "spruce")
        P.rug(b, u.x0, 3, u.x0 + 2, 5, 1, "brown")
        for v in (3, 5):                                   # tables along the right, a walkway beside them
            P.table(b, u.x1 - 1, 1, v)
            b.set(u.x1, 1, v, stairs("dark_oak", "right"))
        fit(b, "kitchen_sink_white", u.x0 + 1, 1, u.back - 1, "front")
        tall(b, "fridge_freezer", u.x0 + 2, 1, u.back - 1, "front")
    return high_street_unit(
        "coffee_shop", "Coffee Shop", "Food & Drink", 9, 17, 2,
        ("BEAN & BREW", "Coffee House • Cakes • Wi-Fi", 0xFF2E2420, 0xFFE8D2A6),
        "A coffee shop: a counter with the coffee machine and cakes, a sofa, an armchair and tables, a flat over the top.",
        fitout, stall="brown_terracotta", door_u=4)


# ================================================================== shops

def shelf_shop(b, u, left, right, middle=None, counter_items=None):
    """A small shop: tall shelves down both walls (left / right stock kinds), maybe a gondola down
    the middle, the counter at the back."""
    cv = u.core_v - 1
    for v in range(3, cv):
        tall(b, f"shelves_{left}", u.x0, 1, v, "right")
    for v in range(3, cv - 1):
        tall(b, f"shelves_{right}", u.x1, 1, v, "left")
    if middle and u.x1 - u.x0 >= 5:
        mid = (u.x0 + u.x1) // 2
        for v in range(4, cv - 1):
            tall(b, f"shelves_{middle}", mid, 1, v, "right")
    u.counter(u.x0 + 1, u.core_u - 2, cv, items=counter_items or {u.x0 + 2: "till"})


def games_shop():
    def fitout(b, u):
        shelf_shop(b, u, "games", "games", counter_items={u.x0 + 1: "till", u.x0 + 2: "computer"})
        for v in (3, 5):
            b.set(3, 1, v, B("smooth_quartz"))
            fit(b, "tv", 3, 2, v, "front", level="floor")
    return high_street_unit(
        "games_shop", "Video Game Shop", "Shops", 8, 17, 2,
        ("LEVEL UP", "Games • Consoles • Trade-ins", 0xFF101010, 0xFF46D25A),
        "A video game shop: walls of games and consoles, demo TVs, a counter for trade-ins and a stock room, a flat over it.",
        fitout, style="modern", roof="flat", wall="gray_concrete", stall="black_concrete")


def pharmacy():
    def fitout(b, u):
        shelf_shop(b, u, "pharmacy", "pharmacy", counter_items={u.x0 + 1: "till", u.x0 + 2: "computer"})
        b.fill(u.x0, 1, u.core_v + 1, u.x0 + 2, 3, u.core_v + 1, "calcite")      # the dispensary behind the counter
        fit(b, "desk", u.x0 + 1, 1, u.back - 1, "front")
        fit(b, "office_chair", u.x0 + 1, 1, u.back - 2, "front")
    return high_street_unit(
        "pharmacy", "Pharmacy", "Shops", 8, 17, 2,
        ("HEALTH FIRST", "Pharmacy • Prescriptions • Advice", 0xFF0E8A4A, WHITE),
        "A chemist's: shelves of medicines and toiletries, a prescription counter and dispensary, a flat over the top.",
        fitout, style="modern", roof="flat", wall="white_concrete", stall="lime_terracotta")


def newsagent():
    def fitout(b, u):
        shelf_shop(b, u, "newsagent", "snacks", counter_items={u.x0 + 1: "till", u.x0 + 2: "till"})
        fit(b, "freezer_island", 3, 1, 4, "front")
    return high_street_unit(
        "newsagent", "Newsagent and Parcels Counter", "Shops", 8, 17, 3,
        ("POST & NEWS", "Newsagent • Parcels • Lottery • Off Licence", 0xFFB3202A, YELLOW),
        "A newsagent with a parcels counter: magazines, sweets and crisps, an ice cream freezer and flats over the shop.",
        fitout, stall="red_terracotta")


def bookmakers():
    def fitout(b, u):
        cv = u.core_v - 1
        u.counter(u.x0 + 1, u.core_u - 2, cv, items={u.x0 + 1: "till", u.x0 + 2: "computer"})
        for v in range(3, cv):
            b.set(u.x0, 1, v, B("black_concrete"))
            b.set(u.x0, 2, v, B("black_concrete"))
            fit(b, "tv", u.x0, 3, v, "right", level="floor")
            fit(b, "bar_stool", u.x0 + 1, 1, v, "left")
        for v in (3, 4):
            tall(b, "fruit_machine", u.x1, 1, v, "left")
        P.rug(b, u.x0 + 2, 2, u.x1 - 1, cv - 1, 1, "green")
    return high_street_unit(
        "bookmakers", "Bookmakers", "Shops", 8, 17, 2,
        ("LUCKY STAR", "Bookmakers • Live Sport", 0xFF0B2A5B, 0xFFF2C21B),
        "A betting shop: a wall of screens with stools, fruit machines, a counter, and a flat over it.", fitout,
        style="modern", roof="flat", wall=RED_BRICK, stall="blue_terracotta")


def launderette():
    def fitout(b, u):
        for v in range(3, u.core_v):
            fit(b, "washing_machine", u.x0, 1, v, "right")
        for v in range(3, u.core_v - 1):
            fit(b, "washing_machine", u.x1, 1, v, "left")
        P.sofa(b, u.x0 + 1, u.x0 + 2, 1, 2, "front", "birch")
        u.counter(u.x0 + 2, u.x0 + 3, u.core_v - 1, items={u.x0 + 3: "till"})
    return high_street_unit(
        "launderette", "Launderette", "Shops", 8, 17, 2,
        ("BUBBLES", "Launderette • Service Washes • Dry Cleaning", 0xFF2A8BD8, WHITE),
        "A launderette: rows of washing machines down both walls, a bench and a service counter, a flat over it.",
        fitout, style="modern", roof="flat", wall="white_concrete", stall="light_blue_terracotta", door_u=4)


def barbers():
    def fitout(b, u):
        for v in (3, 5, 7):
            fit(b, "barber_chair", u.x0 + 1, 1, v, "left")
            fit(b, "bathroom_cabinet", u.x0, 2, v, "right")
        fit(b, "basin", u.x0, 1, 4, "right")
        P.sofa(b, u.x1 - 1, u.x1, 1, 2, "front", "dark_oak")
        u.counter(u.x1 - 1, u.x1 - 1, u.core_v - 2, items={u.x1 - 1: "till"})
        P.rug(b, u.x0 + 1, 2, u.x0 + 2, u.core_v, 1, "black")
    return high_street_unit(
        "barbers", "Barber's Shop", "Shops", 8, 17, 2,
        ("SMOOTH CUTS", "Turkish Barbers • No Appointment Needed", 0xFF121214, 0xFFD9B44A),
        "A barber's: red barber's chairs facing mirrors, a basin, a waiting sofa and the till, a flat over it.", fitout,
        stall="black_terracotta", door_u=4)


def phone_shop():
    def fitout(b, u):
        for v in (3, 5):
            for x in (u.x0 + 1, u.x0 + 3):
                fit(b, "coffee_table", x, 1, v, "front")
                fit(b, "computer", x, 2, v, "front", level="low")
        u.counter(u.x0, u.x0 + 2, u.core_v - 1, items={u.x0: "till", u.x0 + 1: "computer"})
        for v in range(3, u.core_v - 2):
            fit(b, "back_bar", u.x1, 1, v, "left")
    return high_street_unit(
        "phone_shop", "Phone Shop", "Shops", 8, 17, 2,
        ("PHONEZONE", "Phones • Repairs • Accessories", 0xFF6A1B9A, WHITE),
        "A mobile phone shop: display tables of handsets, shelves of accessories, a repairs counter, a flat over it.",
        fitout, style="modern", roof="flat", wall="light_gray_concrete", stall="purple_terracotta", door_u=4)


def estate_agent():
    def fitout(b, u):
        for v in (4, 7):
            fit(b, "desk", u.x0 + 1, 1, v, "front")
            fit(b, "computer", u.x0 + 1, 2, v, "front", level="table")
            fit(b, "office_chair", u.x0 + 1, 1, v + 1, "front")
            fit(b, "dining_chair", u.x0 + 1, 1, v - 1, "back")
        P.sofa(b, u.x1 - 1, u.x1, 1, 2, "front", "birch")
        fit(b, "houseplant", u.x1, 1, 4, "front", level="floor")
    return high_street_unit(
        "estate_agent", "Estate Agent", "Shops", 8, 17, 2,
        ("HOBBS & WREN", "Estate Agents • Lettings • Valuations", 0xFF143D2B, CREAM),
        "An estate agent's office: desks with computers, a waiting sofa, a flat over it.", fitout,
        stall="green_terracotta", door_u=4)


def pound_shop():
    def fitout(b, u):
        for v in range(3, u.core_v - 1):
            tall(b, "shelves_household", u.x0, 1, v, "right")
            tall(b, "shelves_snacks", u.x1, 1, v, "left")
        kinds = ("groceries", "household", "drinks", "snacks")
        for i, x in enumerate(range(u.x0 + 3, u.x1 - 2, 3)):
            for v in range(4, u.core_v - 2):
                tall(b, f"shelves_{kinds[i % 4]}", x, 1, v, "right")
        for x in (u.x0 + 2, u.x0 + 5):
            wide(b, "checkout", x + 1, 1, u.core_v - 1, "front")
    return high_street_unit(
        "pound_shop", "Discount Variety Store", "Shops", 14, 19, 2,
        ("POUND PALACE", "Everything Cheaper • Groceries • Home • Toys", 0xFFE21F26, YELLOW),
        "A big discount variety store in a double shop unit: aisles of groceries, cleaning things, sweets and drinks, "
        "two checkouts and a stock room, with offices over it.", fitout, style="modern", roof="flat",
        wall="light_gray_concrete", stall="red_terracotta", door_u=7, upper=None)


# ================================================================== restaurants

def peri_peri():
    def fitout(b, u):
        cv = u.core_v - 2
        u.counter(u.x0, u.x0 + 4, cv, items={u.x0: "till", u.x0 + 1: "till", u.x0 + 3: "drinks_dispenser"})
        b.fill(u.x0, 1, u.core_v + 1, u.core_u - 2, 3, u.core_v + 1, "calcite")
        for x in range(u.x0, u.x0 + 5):
            fit(b, "pizza_oven" if x % 2 else "fryer", x, 1, u.core_v, "front")
        wide(b, "menu_board", u.x0 + 1, 3, u.core_v, "front")
        wide(b, "menu_board", u.x0 + 3, 3, u.core_v, "front")
        for v in (3, 6):                                   # booths down the left, tables on the right
            P.sofa(b, u.x0, u.x0 + 1, 1, v - 1, "front", "jungle")
            P.table(b, u.x0, 1, v)
            P.table(b, u.x0 + 1, 1, v)
            P.sofa(b, u.x0, u.x0 + 1, 1, v + 1, "back", "jungle")
        dining(b, u.x0 + 4, u.x1, 3, cv - 2)
        P.rug(b, u.x0 + 2, 2, u.x0 + 3, cv - 1, 1, "orange")
    return high_street_unit(
        "peri_peri_restaurant", "Peri-Peri Chicken Restaurant", "Food & Drink", 14, 20, 2,
        ("CASA PERI", "Flame-Grilled Peri-Peri Chicken", 0xFF111111, 0xFFE8432D),
        "A flame-grilled chicken restaurant in a double unit: order at the counter, grills and fryers behind, booths and "
        "tables, a drinks machine, more tables upstairs.", fitout, style="modern", roof="flat", wall="white_concrete",
        stall="black_terracotta", door_u=9, upper=dining_upstairs("jungle"))


def pizza_restaurant():
    def fitout(b, u):
        fit(b, "pizza_oven", u.x0, 1, u.core_v, "front")
        fit(b, "pizza_oven", u.x0 + 1, 1, u.core_v, "front")
        b.fill(u.x0, 1, u.core_v - 2, u.x0 + 1, 1, u.core_v - 2, RED_BRICK)
        u.counter(u.x0 + 2, u.core_u - 2, u.core_v - 2, items={u.x0 + 3: "till", u.x0 + 4: "coffee_machine"}, finish="oak")
        for v in range(u.core_v + 1, u.back - 1):
            fit(b, "kitchen_unit_white", u.x0, 1, v, "right")
        dining(b, u.x0, u.x1, 3, u.core_v - 4, chair="spruce")
        P.rug(b, u.x0, 2, u.x1, 2, 1, "red")
    return high_street_unit(
        "pizza_restaurant", "Italian Pizza Restaurant", "Food & Drink", 12, 19, 2,
        ("FORNO ROSSO", "Pizzeria • Pasta • Wine", 0xFF1E4D2B, CREAM),
        "A family pizza restaurant: wood-fired ovens behind a brick counter, tables for two in rows, and more tables "
        "upstairs.", fitout, wall=RED_BRICK, stall="green_terracotta", door_u=6, upper=dining_upstairs("spruce"))


def indian_restaurant():
    def fitout(b, u):
        u.counter(u.x0, u.x0 + 2, u.core_v - 2, finish="oak", items={u.x0: "till", u.x0 + 2: "beer_pumps"})
        for x in range(u.x0, u.x0 + 3):
            fit(b, "back_bar", x, 1, u.core_v, "front")
        dining(b, u.x0, u.x1, 3, u.core_v - 4)
        for v in range(u.core_v + 1, u.back - 1):
            fit(b, "kitchen_oven_white", u.x0, 1, v, "right")
        P.rug(b, u.x0, 2, u.x1, u.core_v - 3, 1, "red")
    return high_street_unit(
        "indian_restaurant", "Indian Restaurant", "Food & Drink", 11, 19, 3,
        ("SPICE OF INDIA", "Indian Restaurant & Takeaway", 0xFF5B1A2A, GOLD),
        "A curry house: red carpet and tables in rows, a little bar, the kitchen at the back and flats over it.", fitout,
        wall=RED_BRICK, stall="red_terracotta", door_u=5)


# ================================================================== bars

def bar_fit(b, u, finish="oak", lounge="dark_oak"):
    """A narrow bar: a back bar against the left wall, a gap for the staff, the bar counter with
    pumps and stools along it, a walkway, and armchairs with coffee tables along the right."""
    for v in range(3, 7):
        fit(b, "back_bar", u.x0, 1, v, "right")
        fit(b, f"kitchen_unit_{finish}", u.x0 + 2, 1, v, "right")
        fit(b, "bar_stool", u.x0 + 3, 1, v, "left")
    fit(b, "beer_pumps", u.x0 + 2, 2, 4, "right", level="counter")
    fit(b, "till", u.x0 + 2, 2, 6, "right", level="counter")
    fit(b, "bottle_fridge", u.x0, 1, 7, "right")
    for v in (3, 5):
        P.armchair(b, u.x1, 1, v, "right", lounge)
    fit(b, "coffee_table", u.x1, 1, 4, "front")


def cocktail_bar():
    def fitout(b, u):
        bar_fit(b, u, finish="white", lounge="crimson")
        fit(b, "kitchen_unit_white", u.x1, 1, u.core_v - 1, "left")
        fit(b, "dj_decks", u.x1, 2, u.core_v - 1, "left", level="counter")
        for x in range(u.x0 + 1, u.core_u - 1):          # a light-up dance floor at the back
            for v in range(u.core_v + 1, u.back - 1):
                b.set(x, 0, v, B("ptmuk:dance_floor", facing="south", lit=True))
    return high_street_unit(
        "cocktail_bar", "Cocktail Bar", "Pubs & Bars", 8, 17, 2,
        ("NEON LOUNGE", "Cocktails • DJs Friday & Saturday", 0xFF120A24, 0xFFFF3EA5),
        "A cocktail bar: a long bar with stools and a back bar of bottles, velvet armchairs, DJ decks and a light-up "
        "dance floor at the back, a flat over it.", fitout, style="modern", roof="flat", wall="black_concrete",
        stall="black_concrete", door_u=5)


def wine_bar():
    return high_street_unit(
        "wine_bar", "Wine Bar", "Pubs & Bars", 8, 17, 2,
        ("THE CELLAR", "Wine Bar • Small Plates", 0xFF3B0D18, CREAM),
        "A wine bar in a Victorian shop: an oak bar with stools and shelves of bottles, armchairs, a flat over it.",
        lambda b, u: bar_fit(b, u, finish="oak", lounge="dark_oak"), wall=RED_BRICK, stall="brown_terracotta", door_u=5)


def all_town():
    return [fish_and_chips(), chinese_takeaway(), kebab_shop(), pizza_takeaway(), bakery(), coffee_shop(),
            peri_peri(), pizza_restaurant(), indian_restaurant(), games_shop(), pharmacy(), newsagent(), bookmakers(),
            launderette(), barbers(), phone_shop(), estate_agent(), pound_shop(), cocktail_bar(), wine_bar()]
