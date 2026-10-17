"""Out-of-town retail, as British retail parks really look: a long terrace of big shop units with
glazed fronts under a canopy on tall steel masts, a white cladding band with big fascias, and a
landscaped car park (tree islands, hedges, a walkway with zebra crossings and bollards, trolley
bays and lamp posts); and a stand-alone fashion store with dark ribbed cladding, a tall glass
entrance block with its name lit up, display windows and a lit roof edge. Every name is made up.
Coordinates as in prefab_lib (u across the front, y up with the ground layer at 0, v from the
front boundary back).
"""
from prefab_lib import B, Build, slab
import prefab_parts as P
from prefab_commercial import big_sign, fit, tall, wide
from prefab_civic import checkouts, rails
from prefab_leisure import bar_counter

GLAZING = "ptmuk:office_glazing"
WHITE, YELLOW = 0xFFFFFFFF, 0xFFF2C21B


def pole(b, u, v, y0, y1):
    for y in range(y0, y1 + 1):
        b.set(u, y, v, B("ptmuk:sign_pole_galvanised", base=(y == y0), cap=(y == y1)))


def bollard(b, u, v):
    b.set(u, 1, v, B("ptmuk:bollard_steel", facing="south"))


def zebra(b, u0, u1, v0, v1, along_v=True):
    """Black and white stripes across an aisle (the walkway runs along v)."""
    for u in range(u0, u1 + 1):
        for v in range(v0, v1 + 1):
            stripe = (v if along_v else u) % 2 == 0
            b.set(u, 0, v, "white_concrete" if stripe else "black_concrete")


def island(b, u0, u1, v, trees_every=8):
    """A planted island between rows of bays: kerbed grass, a low hedge, trees and lamp posts."""
    for u in range(u0, u1 + 1):
        b.set(u, 0, v, "grass_block")
        b.set(u, 0, v + 1, "grass_block")
        b.set(u, 1, v, B("oak_leaves", persistent=True, distance=1, waterlogged=False))
    for u in range(u0 + 3, u1 - 1, trees_every):
        b.set(u, 1, v, "air")
        P.tree(b, u, v + 1, 4, 1, leaves="birch_leaves", log="birch_log")
    for u in range(u0 + 7, u1 - 1, trees_every * 2):
        P.street_light(b, u, v, "front", 7)


def trolley_bay(b, u, v):
    """A trolley shelter: a glass-sided bay under a slab roof, trolleys nested inside."""
    for dv in range(3):
        b.set(u, 1, v + dv, B("glass_pane", north=dv > 0, south=dv < 2, east=False, west=False, waterlogged=False))
        b.set(u + 3, 1, v + dv, B("glass_pane", north=dv > 0, south=dv < 2, east=False, west=False, waterlogged=False))
        for du in (1, 2):
            fit(b, "shopping_trolley", u + du, 1, v + dv, "front")
    for du in range(4):
        for dv in range(3):
            b.set(u + du, 3, v + dv, slab("smooth_stone", "bottom"))
    for du in (0, 3):
        b.set(u + du, 2, v, B("ptmuk:sign_pole_galvanised", base=False, cap=False))
        b.set(u + du, 2, v + 2, B("ptmuk:sign_pole_galvanised", base=False, cap=False))


def car_park_rows(b, u0, u1, v0, walkway):
    """Two double rows of bays with aisles, a planted island between them and between the pairs,
    a walkway (u walkway..walkway+3) straight through with zebra crossings over the aisles.
    Returns the v just past the car park."""
    P.paving(b, u0, v0, u1, v0 + 33, "ptmuk:tarmac")
    rows = ((v0 + 1, v0 + 5, "back"), (v0 + 12, v0 + 16, "front"), (v0 + 19, v0 + 23, "back"), (v0 + 30, v0 + 34, "front"))
    for a, c, side in rows:
        P.parking_bays(b, u0 + 1, a, u1 - 1, min(c, v0 + 33), "u", 3, side)
    island(b, u0, u1, v0 + 17)
    w0, w1 = walkway, walkway + 3
    P.paving(b, w0, v0, w1, v0 + 33, "ptmuk:paving_slabs")
    zebra(b, w0, w1, v0 + 6, v0 + 11)
    zebra(b, w0, w1, v0 + 24, v0 + 29)
    for v in (v0 + 5, v0 + 12, v0 + 23, v0 + 30):
        bollard(b, w0 - 1, v)
        bollard(b, w1 + 1, v)
    for v in range(v0 + 17, v0 + 19):
        for u in range(w0, w1 + 1):
            b.set(u, 0, v, "ptmuk:paving_slabs")
            b.set(u, 1, v, "air")
    return v0 + 34


# ================================================================== retail park terrace

UNITS = (
    (22, "TRAILHEAD", "Outdoor Clothing • Camping • Walking", 0xFF1F4E3D, WHITE, "outdoor"),
    (16, "GLOW", "Health & Beauty • Pharmacy", 0xFF0D3B66, WHITE, "beauty"),
    (20, "FASHION FIX", "Fashion • Kids • Accessories", 0xFF111111, 0xFFFF4D6D, "fashion"),
    (16, "NEST HOME", "Furniture • Homeware • Living", 0xFF2E7D32, WHITE, "home"),
    (22, "SPRINT", "Sportswear • Trainers • Fitness", 0xFFC62828, WHITE, "sport"),
)


def unit_fitout(b, kind, u0, u1, v0, v1):
    checkouts(b, u0 + 2, min(u0 + 8, u1 - 4), v0 + 4)
    if kind == "outdoor":
        rails(b, u0 + 3, u1 - 3, v0 + 9, v1 - 9)
        for v in range(v1 - 6, v1 - 1):
            tall(b, "shelves_household", u0 + 1, 1, v, "right")
            tall(b, "shelves_snacks", u1 - 1, 1, v, "left")
    elif kind == "beauty":
        for u in range(u0 + 3, u1 - 1, 4):
            for v in range(v0 + 9, v1 - 3):
                tall(b, "shelves_pharmacy", u, 1, v, "right")
    elif kind == "fashion":
        rails(b, u0 + 2, u1 - 2, v0 + 8, v1 - 3)
    elif kind == "home":
        for v in range(v0 + 9, v1 - 4, 4):
            P.sofa(b, u0 + 2, u0 + 4, 1, v, "back", ("spruce", "mangrove", "jungle")[v % 3])
            fit(b, "coffee_table", u0 + 3, 1, v + 2, "front")
            P.bed(b, u1 - 3, 1, v + 1, ("white", "light_blue", "lime")[v % 3], "front")
        for v in range(v0 + 9, v1 - 2):
            tall(b, "shelves_household", u0 + 8, 1, v, "right")
    else:
        rails(b, u0 + 3, u1 - 6, v0 + 9, v1 - 6)
        for v in range(v1 - 5, v1 - 1, 2):
            wide(b, "treadmill", u1 - 2, 1, v, "front")
            fit(b, "weight_bench", u0 + 3, 1, v, "front")
    for u in range(u0 + 2, u1, 4):
        for v in range(v0 + 2, v1, 4):
            b.set(u, 10, v, "sea_lantern")


def retail_park_terrace(ident="retail_park_terrace", name="Retail Park Terrace (5 Units)"):
    W, D = 100, 74
    b = Build(ident, name, "Commercial", W, 16, D,
              "A modern retail park: five big units in a terrace with glazed fronts under a canopy on tall steel masts, "
              "a white cladding band with big signs, and a landscaped car park with tree islands, a walkway with zebra "
              "crossings, bollards and trolley bays. TRAILHEAD, GLOW, FASHION FIX, NEST HOME and SPRINT are made-up names.")
    walkway = 48
    end = car_park_rows(b, 0, W - 1, 0, walkway)
    v0 = end + 10                                  # the shop fronts
    P.paving(b, 0, end, W - 1, v0 - 1, "ptmuk:paving_slabs")
    for u in range(0, W):
        b.set(u, 0, end, "ptmuk:concrete_kerb")
    for u in range(2, W - 2, 4):
        if not walkway - 1 <= u <= walkway + 4:
            bollard(b, u, end + 1)
    trolley_bay(b, 12, end + 2)
    trolley_bay(b, W - 16, end + 2)
    b.fill(0, 0, v0, W - 1, 0, D - 1, "smooth_stone")
    u = 2
    for width, text, sub, board, ink, kind in UNITS:
        u0, u1 = u, u + width - 1
        b.walls(u0, 1, v0, u1, 9, D - 1, "light_gray_concrete")
        b.window(u0 + 1, 1, v0, width - 2, 4, GLAZING)
        mid = (u0 + u1) // 2
        b.door(mid, 1, v0, "birch", facing="back", hinge="left")
        b.door(mid + 1, 1, v0, "birch", facing="back", hinge="right")
        b.fill(u0, 5, v0, u1, 9, v0, "white_concrete")
        big_sign(b, mid - 4, mid + 5, 7, 8, v0 - 1, text, sub, board, ink)
        unit_fitout(b, kind, u0, u1, v0, D - 1)
        u = u1 + 1
    P.flat_roof(b, 0, W - 1, v0, D - 1, 10, deck="gray_concrete", parapet="white_concrete", coping="smooth_stone")
    b.fill(0, 1, v0, 1, 9, D - 1, "light_gray_concrete")
    b.fill(W - 2, 1, v0, W - 1, 9, D - 1, "light_gray_concrete")
    # the canopy along the front on tall masts
    for uu in range(0, W):
        for v in range(v0 - 5, v0):
            b.set(uu, 5, v, slab("smooth_stone", "top"))
        b.set(uu, 5, v0 - 6, "light_gray_concrete")
    for uu in range(4, W - 2, 10):
        pole(b, uu, v0 - 6, 1, 4)
        pole(b, uu, v0 - 6, 6, 14)
    return b


# ================================================================== out-of-town fashion store

def fashion_superstore(ident="fashion_superstore", name="Out-of-Town Fashion Store"):
    W, D = 64, 70
    b = Build(ident, name, "Shops", W, 18, D,
              "A big out-of-town fashion store: wide wings in dark ribbed cladding with lit display windows, a tall glass "
              "entrance block with the name lit up, a glowing roof edge, and a car park with a walkway, zebra crossing "
              "and bollards to the doors. Inside, one huge floor of rails and tills. THREADS is a made-up name.")
    end = car_park_rows(b, 0, W - 1, 0, 30)
    v0 = end + 4
    P.paving(b, 0, end, W - 1, v0 - 1, "ptmuk:paving_slabs")
    for u in range(2, W - 2, 3):
        if not 29 <= u <= 34:
            bollard(b, u, end + 1)
    b.fill(0, 0, v0, W - 1, 0, D - 1, "polished_andesite")
    # wings, low, in dark ribs with display windows; the central block taller and glazed
    b.walls(1, 1, v0, W - 2, 9, D - 1, "black_concrete")
    for uu in range(1, W - 1):
        rib = "gray_concrete" if uu % 2 else "black_concrete"
        for y in range(5, 10):
            b.set(uu, y, v0, rib)
    for u0 in (3, 9, 45, 51):
        b.window(u0, 1, v0, 4, 3, "ptmuk:shop_window")
    P.flat_roof(b, 1, W - 2, v0, D - 1, 10, deck="gray_concrete", parapet="black_concrete", coping="smooth_stone")
    for uu in range(1, W - 1):
        b.set(uu, 11, v0, "sea_lantern")                         # the lit roof edge
    # the tall glass entrance block in the middle: a double-height hall behind it
    c0, c1 = 20, 43
    b.fill(c0, 1, v0, c1, 14, v0, "black_concrete")
    b.window(c0 + 1, 5, v0, c1 - c0 - 1, 9, GLAZING)
    b.window(c0 + 1, 1, v0, 8, 3, "ptmuk:shop_window")
    b.window(c1 - 8, 1, v0, 8, 3, "ptmuk:shop_window")
    for uu in (30, 31, 32, 33):
        b.door(uu, 1, v0, "birch", facing="back", hinge="left" if uu % 2 == 0 else "right")
    for uu in (c0, c1):
        b.fill(uu, 10, v0, uu, 14, v0 + 10, "black_concrete")
    b.fill(c0, 10, v0 + 10, c1, 14, v0 + 10, "black_concrete")
    b.fill(c0 + 1, 10, v0 + 1, c1 - 1, 14, v0 + 9, "air")
    P.flat_roof(b, c0, c1, v0, v0 + 10, 15, deck="gray_concrete", parapet="black_concrete", coping="smooth_stone")
    for uu in range(c0, c1 + 1):
        b.set(uu, 16, v0, "sea_lantern")
    big_sign(b, 25, 38, 10, 12, v0 - 1, "THREADS", "", 0xFF06121C, 0xFF3FF0E8)
    big_sign(b, 28, 35, 4, 4, v0 - 1, "THREADS", "", 0xFF06121C, 0xFF3FF0E8)
    # one big floor: rails everywhere, tills along the back, fitting rooms down one side
    rails(b, 4, W - 6, v0 + 12, D - 8)
    for uu in (4, 8, 12, 16, 47, 51, 55, 59):
        for v in (v0 + 3, v0 + 6):
            fit(b, "clothes_rail", uu, 1, v, "front")
    bar_counter(b, 18, 45, D - 4, "front", tills=tuple(range(19, 45, 3)), finish="white")
    for v in range(v0 + 14, D - 6, 2):
        b.set(W - 3, 1, v, "light_gray_concrete")
        b.set(W - 3, 2, v, "light_gray_concrete")
        b.set(W - 3, 3, v, "light_gray_concrete")
    for uu in range(3, W - 2, 4):
        for v in range(v0 + 2, D - 1, 4):
            if b.get(uu, 10, v).name == "minecraft:gray_concrete":
                b.set(uu, 10, v, "sea_lantern")
    for uu in range(c0 + 2, c1 - 1, 4):
        for v in range(v0 + 2, v0 + 9, 4):
            b.set(uu, 15, v, "sea_lantern")
    return b


def all_retail():
    return [retail_park_terrace(), fashion_superstore()]
