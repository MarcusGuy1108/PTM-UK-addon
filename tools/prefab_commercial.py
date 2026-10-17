"""Commercial prefabs: a multiplex cinema with stadium-seated screens, a supermarket superstore,
a car dealership with a service workshop and a drive-thru restaurant. Every name on them is made
up. Fitted out with the shop and cinema fittings from home_furniture. Coordinates as in
prefab_lib (u across the front, y up with the ground layer at 0, v from the front boundary back).
"""
from prefab_lib import AIR, B, Block, Build, DIRS, light, shop_sign_nbt, slab, stairs
import prefab_parts as P
import prefab_furnish as PF

WHITE = 0xFFFFFFFF
CLAD = "light_gray_concrete"
DARK = "gray_concrete"
GLAZING = "ptmuk:office_glazing"


def fit(b, name, u, y, v, facing, **props):
    """A shop / cinema fitting facing a local direction."""
    b.set(u, y, v, PF.home(name, facing=DIRS[facing], lit=False, **props))


def tall(b, name, u, y, v, facing):
    fit(b, name, u, y, v, facing, half="lower")
    fit(b, name, u, y + 1, v, facing, half="upper")


def wide(b, name, u, y, v, facing):
    """A two-block fitting: the right-hand part goes clockwise of its facing."""
    step = {"front": -1, "back": 1}[facing]          # facing south: right part to the west, north: to the east
    fit(b, name, u, y, v, facing, part="left")
    fit(b, name, u + step, y, v, facing, part="right")


def big_sign(b, u0, u1, y0, y1, v, text, sub, board, ink=WHITE, facing="front"):
    """A sign several boards high: the text sits on the bottom left-hand board."""
    left = u0 if facing == "front" else u1
    for u in range(u0, u1 + 1):
        for y in range(y0, y1 + 1):
            nbt = shop_sign_nbt(text, sub, board, ink) if (u == left and y == y0) else shop_sign_nbt("", "", board, ink)
            b.set(u, y, v, Block("ptmuk:shop_sign", {"facing": DIRS[facing]}, nbt))


def lights_in(b, u0, v0, u1, v1, y, step=4, level=12):
    for u in range(u0, u1 + 1, step):
        for v in range(v0, v1 + 1, step):
            if b.get(u, y, v).name == "minecraft:air":
                b.set(u, y, v, light(level))


def car_park(b, u0, u1, v0, v1):
    """Two facing rows of bays with an aisle between them."""
    P.paving(b, u0, v0, u1, v1, "ptmuk:tarmac")
    depth = (v1 - v0 + 1 - 5) // 2
    P.parking_bays(b, u0, v0, u1, v0 + depth - 1, "u", 3, "back")
    P.parking_bays(b, u0, v1 - depth + 1, u1, v1, "u", 3, "front")


# ================================================================== multiplex cinema

def _screen(b, v0, v1, mirror, W):
    """One stadium-seated screen between the outside wall (u = 2) and the corridor wall (u = 25),
    v0..v1 inside. The screen is on the corridor wall, you come in beside it and climb the aisle
    up to the rows, the projector looks over the back row."""
    def U(u):
        return W - 1 - u if mirror else u
    toward_screen = "left" if mirror else "right"
    up_aisle = "right" if mirror else "left"
    # the screen and its black masking on the corridor wall
    for v in range(v0 + 2, v1):
        for y in range(2, 12):
            edge = v in (v0 + 2, v1 - 1) or y in (2, 11)
            b.set(U(25), y, v, "black_concrete" if edge else "white_concrete")
    b.door(U(25), 1, v0 + 1, "dark_oak", facing="left" if mirror else "right", hinge="left")
    # stepped rows, two blocks deep and one higher each row
    for k in range(8):
        for u in (19 - 2 * k, 18 - 2 * k):
            for v in range(v0, v1 + 1):
                for y in range(1, k + 1):
                    b.set(U(u), y, v, "black_concrete")
                if k and u == 19 - 2 * k and v in (v0, v0 + 1):
                    b.set(U(u), k, v, stairs("blackstone", up_aisle))
                if v >= v0 + 2 and b.get(U(u), k + 1, v).name == "minecraft:air":
                    b.set(U(u), k + 1, v, "red_carpet")
        for v in range(v0 + 2, v1):
            fit(b, "cinema_seat_red", U(18 - 2 * k), k + 1, v, toward_screen)
    # behind the back row: solid, with the projector on a ledge
    for v in range(v0, v1 + 1):
        for y in range(1, 9):
            b.set(U(3), y, v, "black_concrete")
    fit(b, "projector", U(3), 9, (v0 + v1) // 2, toward_screen, level="floor")
    for u in range(4, 24, 5):
        for v in range(v0 + 1, v1 + 1, 5):
            if b.get(U(u), 15, v).name == "minecraft:air":
                b.set(U(u), 15, v, light(7))
    for k in range(8):                                   # dim step lights along the aisle
        u = U(18 - 2 * k)
        if b.get(u, k + 2, v0).name == "minecraft:air":
            b.set(u, k + 2, v0, light(5))


def multiplex(ident="multiplex_cinema", name="Multiplex Cinema (4 Screens)"):
    W, D = 56, 64
    b = Build(ident, name, "Commercial", W, 20, D,
              "An out-of-town multiplex: car park, glass foyer with box office, concessions and pick and mix, a "
              "corridor to four stadium-seated screens with a projector over each. The name, CUBEPLEX, is made up.")
    car_park(b, 0, W - 1, 0, 15)
    P.paving(b, 0, 16, W - 1, 19)
    for u in (0, 18, 37, W - 1):
        P.street_light(b, u, 16, "front", 6)
    # shell
    b.fill(2, 0, 20, W - 3, 0, D - 1, "polished_andesite")
    b.walls(2, 1, 20, W - 3, 16, D - 1, CLAD)
    b.fill(2, 14, 20, W - 3, 14, 20, "red_concrete")
    P.flat_roof(b, 2, W - 3, 20, D - 1, 17, deck=DARK, parapet=CLAD, coping="smooth_stone")
    # glass foyer front and entrance canopy
    b.window(12, 1, 20, 32, 9, GLAZING)
    for u in range(25, 31):
        b.door(u, 1, 20, "birch", facing="back", hinge="left" if u % 2 else "right")
    for u in range(22, 34):
        for v in range(16, 20):
            b.set(u, 10, v, slab("smooth_stone", "top"))
    for u in (22, 33):
        for y in range(1, 10):
            b.set(u, y, 16, "light_gray_concrete")
    big_sign(b, 16, 39, 11, 13, 19, "CUBEPLEX", "CINEMA", 0xFF1A1A2E, 0xFFF2C21B)
    for i, film in enumerate(("night_bus", "cube_quest", "orbit_nine", "last_junction")):
        tall(b, f"film_poster_{film}", (4, 7, 48, 51)[i], 1, 19, "front")
    # foyer: ceiling, box office, concessions, ticket machines, posters, seats
    b.fill(3, 10, 21, W - 4, 10, 31, "smooth_stone")
    b.fill(3, 11, 21, W - 4, 16, 31, CLAD)                     # solid above the foyer ceiling
    b.fill(3, 1, 32, W - 4, 16, 32, CLAD)
    b.fill(26, 1, 32, 29, 4, 32, AIR)
    P.ceiling_lights(b, 3, 21, W - 4, 31, 10, step=4, block="sea_lantern")
    for u in range(4, 14):
        fit(b, "kitchen_unit_white", u, 1, 28, "front")
        if u % 3 == 1:
            fit(b, "till", u, 2, 28, "front", level="counter")
    for u in range(41, 52):
        if u in (44, 48):
            fit(b, "popcorn_machine", u, 2, 28, "front", level="counter")
        elif u in (42, 50):
            fit(b, "drinks_dispenser", u, 2, 28, "front", level="counter")
        elif u == 46:
            fit(b, "till", u, 2, 28, "front", level="counter")
        fit(b, "kitchen_unit_white", u, 1, 28, "front")
    for u in (41, 51):
        tall(b, "film_poster_rain_moors", u, 1, 31, "front")
    for u in (43, 47):
        wide(b, "menu_board", u + 1, 4, 31, "front")
    for u in range(42, 50, 2):
        fit(b, "pick_and_mix", u, 1, 24, "front")
    for u in (8, 10, 12):
        tall(b, "ticket_machine", u, 1, 22, "front")
    for u, film in ((18, "orbit_nine"), (21, "cube_quest"), (34, "night_bus"), (37, "last_junction")):
        tall(b, f"film_poster_{film}", u, 1, 31, "front")
    for u in (16, 39):
        for v in (24, 25, 26):
            fit(b, "sofa_navy", u, 1, v, "right" if u == 16 else "left", shape="single")
    # corridor to the screens
    b.fill(25, 1, 33, 25, 16, D - 2, CLAD)
    b.fill(30, 1, 33, 30, 16, D - 2, CLAD)
    b.fill(26, 6, 33, 29, 6, D - 2, "smooth_stone")
    b.fill(26, 7, 33, 29, 16, D - 2, CLAD)
    b.fill(26, 0, 33, 29, 0, D - 2, "red_wool")
    for v in range(35, D - 1, 4):
        b.set(27, 5, v, light(13))
    b.fill(3, 1, 48, 24, 16, 48, CLAD)
    b.fill(31, 1, 48, W - 4, 16, 48, CLAD)
    for v0, v1 in ((33, 47), (49, D - 2)):
        _screen(b, v0, v1, False, W)
        _screen(b, v0, v1, True, W)
    for v in (40, 56):
        b.set(25, 3, v, B("ptmuk:shop_sign", facing="east"))
        b.set(30, 3, v, B("ptmuk:shop_sign", facing="west"))
    b.door(27, 1, D - 1, "iron", facing="front", hinge="left")
    return b


# ================================================================== superstore

def superstore(ident="superstore", name="Supermarket Superstore", brand="FRESHWAYS", board=0xFF1E7A3C):
    W, D = 48, 58
    b = Build(ident, name, "Commercial", W, 12, D,
              f"A big out-of-town supermarket: car park with a trolley bay, glass entrance, checkouts and "
              f"self-checkouts, aisles of shelves, chillers, freezers and fruit and veg. The brand, {brand}, is made up.")
    car_park(b, 0, W - 1, 0, 17)
    P.paving(b, 0, 18, W - 1, 21)
    for u in range(36, 42):
        fit(b, "shopping_trolley", u, 1, 19, "front")
    for u in (0, 24, W - 1):
        P.street_light(b, u, 18, "front", 6)
    b.fill(2, 0, 22, W - 3, 0, D - 1, "smooth_quartz")
    b.walls(2, 1, 22, W - 3, 8, D - 1, CLAD)
    b.fill(2, 7, 22, W - 3, 8, 22, DARK)
    P.flat_roof(b, 2, W - 3, 22, D - 1, 9, deck=DARK, parapet=CLAD, coping="smooth_stone")
    b.window(4, 1, 22, 20, 5, GLAZING)
    for u in (12, 13):
        b.door(u, 1, 22, "birch", facing="back", hinge="left" if u == 12 else "right")
    big_sign(b, 18, 37, 7, 9, 21, brand, "SUPERSTORE", board)
    fit(b, "cash_machine", 30, 2, 22, "front")
    fit(b, "cash_machine", 32, 2, 22, "front")
    # checkouts and self-checkouts behind the front
    for u in range(20, 42, 4):
        wide(b, "checkout", u + 1, 1, 27, "front")
    for u in range(5, 14, 2):
        tall(b, "self_checkout", u, 1, 27, "front")
    # aisles: double-sided shelving in runs from front to back
    kinds = ("groceries", "groceries", "drinks", "snacks", "household", "household", "drinks", "groceries", "snacks")
    for i, u in enumerate(range(8, 44, 4)):
        for v in range(32, 47):
            tall(b, f"shelves_{kinds[i]}", u, 1, v, "right")
    # chillers along the back, freezers down the left, fruit and veg on the right
    for u in range(4, W - 4):
        tall(b, "chiller_cabinet", u, 1, D - 2, "front")
    for v in range(31, D - 4):
        tall(b, "upright_freezer", 3, 1, v, "right")
    for v in range(31, 50, 2):
        fit(b, "produce_stand", W - 4, 1, v, "left")
    for u in range(10, 40, 2):
        fit(b, "freezer_island", u, 1, 51, "front")
    P.ceiling_lights(b, 2, 22, W - 3, D - 1, 8, step=4, block="sea_lantern")
    return b


def discount_store():
    b = superstore("discount_supermarket", "Discount Supermarket", "SAVEMORE", 0xFF1C3F94)
    b.description = ("A no-frills discount supermarket with a big car park, checkouts, self-checkouts and long aisles. "
                     "The brand, SAVEMORE, is made up.")
    return b


# ================================================================== car dealership

def car_dealership(ident="car_dealership", name="Car Dealership with Workshop"):
    W, D = 42, 40
    b = Build(ident, name, "Commercial", W, 13, D,
              "A main dealer: a forecourt of display bays, a double-height glass showroom with sales desks, and a "
              "service workshop with lifts, tool chests and up-and-over doors. The name, ROADSTAR MOTORS, is made up.")
    P.paving(b, 0, 0, W - 1, 13, "ptmuk:tarmac")
    P.parking_bays(b, 2, 2, 25, 8, "u", 4, "back")
    P.paving(b, 0, 11, W - 1, 13, "ptmuk:block_paving_grey")
    for y in range(1, 4):
        b.set(1, y, 1, "gray_concrete")
        b.set(4, y, 1, "gray_concrete")
    big_sign(b, 1, 4, 4, 6, 1, "ROADSTAR", "MOTORS", 0xFF0B2A5B)
    for u in (0, 20, W - 1):
        P.street_light(b, u, 12, "front", 6)
    # showroom
    b.fill(2, 0, 14, 25, 0, D - 2, "polished_diorite")
    b.walls(2, 1, 14, 25, 9, D - 2, "white_concrete")
    b.window(3, 1, 14, 22, 7, GLAZING)
    for u in (12, 13):
        b.door(u, 1, 14, "birch", facing="back", hinge="left" if u == 12 else "right")
    big_sign(b, 4, 23, 8, 9, 13, "ROADSTAR MOTORS", "SALES • SERVICE • PARTS", 0xFF0B2A5B)
    for u in (5, 10, 17, 22):
        fit(b, "desk", u, 1, 33, "front")
        fit(b, "computer", u, 2, 33, "front", level="table")
        fit(b, "office_chair", u, 1, 34, "front")
        fit(b, "dining_chair", u, 1, 32, "back")
    for u in (3, 24):
        fit(b, "houseplant", u, 1, 15, "front", level="floor")
    P.ceiling_lights(b, 2, 14, 25, D - 2, 9, step=4, block="sea_lantern")
    P.flat_roof(b, 2, 25, 14, D - 2, 10, deck=DARK, parapet="white_concrete", coping="smooth_stone")
    # workshop, through a doorway from the showroom
    b.fill(26, 0, 14, W - 2, 0, D - 2, "smooth_stone")
    b.walls(26, 1, 14, W - 2, 8, D - 2, DARK)
    b.fill(25, 1, 20, 26, 2, 20, AIR)
    P.garage_door(b, 28, 31, 14, 1, 4, "front", "white_garage_door")
    P.garage_door(b, 34, 37, 14, 1, 4, "front", "white_garage_door")
    for u0 in (28, 34):
        for v in (22, 30):
            tall(b, "car_lift", u0 - 1, 1, v, "right")
            tall(b, "car_lift", u0 + 4, 1, v, "left")
    for u in range(28, 38, 2):
        fit(b, "tool_chest", u, 1, D - 3, "front")
    fit(b, "tyre_stack", W - 3, 1, 17, "left")
    fit(b, "tyre_stack", W - 3, 1, 18, "left")
    fit(b, "oil_drum", W - 3, 1, 20, "left")
    P.ceiling_lights(b, 26, 14, W - 2, D - 2, 8, step=4, block="sea_lantern")
    P.flat_roof(b, 26, W - 2, 14, D - 2, 9, deck=DARK, parapet=DARK, coping="smooth_stone")
    return b


# ================================================================== drive-thru

def drive_thru(ident="drive_thru", name="Drive-Thru Restaurant"):
    W, D = 32, 34
    b = Build(ident, name, "Food & Drink", W, 9, D,
              "A drive-thru burger restaurant: a lane round the building with an order point and menu board, a "
              "pay window, a dining room and a kitchen with fryers. The name, BUN & GONE, is made up.")
    P.paving(b, 0, 0, W - 1, D - 1, "ptmuk:tarmac")
    P.parking_bays(b, 1, 1, 11, 6, "u", 3, "back")
    b.fill(8, 0, 12, 23, 0, 25, "polished_andesite")
    b.walls(8, 1, 12, 23, 5, 25, "ptmuk:red_brick")
    b.fill(8, 5, 12, 23, 5, 12, "black_concrete")
    b.window(10, 1, 12, 5, 3, GLAZING)
    b.door(16, 1, 12, "birch", facing="back", hinge="left")
    big_sign(b, 10, 21, 6, 6, 11, "BUN & GONE", "BURGERS • DRIVE-THRU", 0xFFC8202A, 0xFFF2C21B)
    b.side_window(23, 2, 17, 2, 2, GLAZING)              # the pay and collect windows on the lane side
    b.side_window(23, 2, 21, 2, 2, GLAZING)
    # the lane runs up the right-hand side: order point and menu board facing it
    tall(b, "drive_thru_speaker", 29, 1, 10, "left")
    for v in (6, 7):
        b.set(30, 1, v, "gray_concrete")
        b.set(29, 0, v, "ptmuk:paving_slabs")
    fit(b, "menu_board", 30, 2, 6, "left", part="left")
    fit(b, "menu_board", 30, 2, 7, "left", part="right")
    # dining room at the front, kitchen at the back
    for u in (10, 13):
        for v in (15, 17):
            b.set(u, 1, v, B("spruce_fence"))
            b.set(u, 2, v, B("spruce_pressure_plate", powered=False))
            b.set(u - 1, 1, v, stairs("spruce", "left"))
            b.set(u + 1, 1, v, stairs("spruce", "right"))
    for u in range(9, 23):
        if u not in (16, 17):
            fit(b, "kitchen_unit_white", u, 1, 20, "front")
    for u in (11, 14, 19):
        fit(b, "till", u, 2, 20, "front", level="counter")
    wide(b, "menu_board", 13, 4, 24, "front")
    wide(b, "menu_board", 20, 4, 24, "front")
    for u in (10, 12):
        fit(b, "fryer", u, 1, 24, "front")
    fit(b, "drinks_dispenser", 18, 2, 20, "front", level="counter")
    tall(b, "fridge_freezer", 22, 1, 24, "front")
    P.ceiling_lights(b, 8, 12, 23, 25, 5, step=3, block="sea_lantern")
    P.flat_roof(b, 8, 23, 12, 25, 6, deck=DARK, parapet="ptmuk:red_brick", coping="smooth_stone")
    for v in (2, 30):
        P.street_light(b, 30, v, "left", 5)
    P.tree(b, 3, 30, 4, 2)
    return b


def all_commercial():
    return [multiplex(), superstore(), discount_store(), car_dealership(), drive_thru()]
