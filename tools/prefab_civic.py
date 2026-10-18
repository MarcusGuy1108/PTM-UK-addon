"""Bigger shops and the town's public buildings: a department store, a value fashion store, a DIY
warehouse with a garden centre, a retail park, a gym, a library, police and fire stations, a GP
surgery, a primary school, a leisure centre with a pool, a bus garage and a village hall. Every
name on them is made up. Coordinates as in prefab_lib (u across the front, y up with the ground
layer at 0, v from the front boundary back).
"""
from prefab_lib import AIR, B, Build, light, slab, stairs
import prefab_parts as P
from prefab_commercial import big_sign, car_park, fit, tall, wide
from prefab_town import ceiling_lights, dining, RED_BRICK, STOCK
from prefab_leisure import bar_counter, floors, toilets, windows, SASH, GEORGIAN, GREY_WIN

STOREY = 4
WHITE, GOLD, CREAM, YELLOW = 0xFFFFFFFF, 0xFFD9B44A, 0xFFF0E6C8, 0xFFF2C21B
GLAZING, SHOP_WIN = "ptmuk:office_glazing", "ptmuk:shop_window"


def rails(b, u0, u1, v0, v1, y=1, step_u=3, step_v=3):
    """Clothes rails in a grid with aisles between them."""
    for u in range(u0, u1 + 1, step_u):
        for v in range(v0, v1 + 1, step_v):
            fit(b, "clothes_rail", u, y, v, "front")


def island(b, u0, u1, v0, v1, y=1, till=True):
    """A square counter island (staff get in through a gap at the back right corner)."""
    for u in range(u0, u1 + 1):
        fit(b, "kitchen_unit_white", u, y, v0, "front")
        if u != u1 - 1:
            fit(b, "kitchen_unit_white", u, y, v1, "back")
    for v in range(v0 + 1, v1):
        fit(b, "kitchen_unit_white", u0, y, v, "left")
        fit(b, "kitchen_unit_white", u1, y, v, "right")
    if till:
        fit(b, "till", u0 + 1, y + 1, v0, "front", level="counter")


def checkouts(b, u0, u1, v, y=1, step=3):
    for u in range(u0, u1 + 1, step):
        wide(b, "checkout", u + 1, y, v, "front")


# ================================================================== department store

def department_store(ident="department_store", name="Edwardian Department Store"):
    W, D = 30, 27
    b = Build(ident, name, "Shops", W, 19, D,
              "A four-storey Edwardian department store in Portland stone: beauty counters and accessories on the ground "
              "floor, womenswear and menswear above and a home department with a cafe at the top. HARTLEY'S is a "
              "made-up name.")
    stone = P.STONE
    P.paving(b, 0, 0, W - 1, 1)
    b.fill(0, 0, 2, W - 1, 0, D - 1, "polished_diorite")
    b.walls(0, 1, 2, W - 1, 15, D - 1, stone)
    floors(b, 1, W - 2, 3, D - 2, 4)
    b.window(2, 1, 2, 10, 3, SHOP_WIN)
    b.window(18, 1, 2, 10, 3, SHOP_WIN)
    b.door(14, 1, 2, "dark_oak", facing="back", hinge="left")
    b.door(15, 1, 2, "dark_oak", facing="back", hinge="right")
    b.window(14, 3, 2, 2, 1, SHOP_WIN)
    for y in (5, 9, 13):
        windows(b, range(2, W - 2, 2), y, 2, kind=SASH)
    for y in (4, 8, 12):
        b.fill(0, y, 1, W - 1, y, 1, slab(stone, "top"))
    P.flat_roof(b, 0, W - 1, 2, D - 1, 16, deck="gray_concrete", parapet=stone, coping="smooth_stone")
    big_sign(b, 8, 21, 16, 17, 1, "HARTLEY'S", "Department Store • Est. 1909", 0xFF1B2A41, GOLD)
    P.dogleg_stairs(b, 26, 20, [0, 4, 8, 12], "stone_brick")
    # ground floor: beauty counters against the walls, two islands, accessories
    for v in range(4, 18):
        fit(b, "back_bar", 1, 1, v, "right")
        fit(b, "back_bar", W - 2, 1, v, "left")
    island(b, 6, 9, 8, 11)
    island(b, 20, 23, 8, 11)
    rails(b, 5, 23, 15, 18, step_u=3, step_v=3)
    P.rug(b, 12, 3, 17, 20, 1, "white")
    # womenswear and menswear: rails in rows, tills at the back
    for y, colour in ((5, "pink"), (9, "light_blue")):
        rails(b, 3, 23, 5, 17, y=y)
        bar_counter(b, 10, 16, 22, "front", tills=(11, 15), finish="white", y=y)
        P.rug(b, 2, 3, W - 3, 4, y, colour)
    # home department and cafe on the top floor
    for v in (5, 9):
        P.sofa(b, 3, 5, 13, v, "back", "spruce")
        P.sofa(b, 8, 10, 13, v, "back", "mangrove")
    for u in (14, 17, 20):
        P.bed(b, u, 13, 5, ("white", "light_blue", "lime")[(u - 14) // 3], "front")
    bar_counter(b, 3, 8, 21, "front", tills=(4,), finish="oak", y=13)
    fit(b, "coffee_machine", 6, 14, 21, "front", level="counter")
    fit(b, "cake_display", 9, 13, 21, "front")
    dining(b, 2, 22, 13, 17, y=13)
    for y in (4, 8, 12):
        ceiling_lights(b, 1, 3, W - 2, D - 2, y, step=3)
    b.light_grid(1, 3, W - 2, D - 2, 15, step=3, level=14)
    return b


# ================================================================== value fashion store

def fashion_store(ident="fashion_store", name="High Street Fashion Store (3 Floors)"):
    W, D = 28, 28
    b = Build(ident, name, "Shops", W, 16, D,
              "A big three-storey value fashion store: a glass front, rails and rails of clothes on every floor, tills "
              "along the back and a beauty and homeware floor at the top. THREADS is a made-up name.")
    P.paving(b, 0, 0, W - 1, 1)
    b.fill(0, 0, 2, W - 1, 0, D - 1, "smooth_quartz")
    b.walls(0, 1, 2, W - 1, 11, D - 1, "light_gray_concrete")
    floors(b, 1, W - 2, 3, D - 2, 3)
    b.window(1, 1, 2, W - 2, 3, GLAZING)
    for u in (12, 13, 14, 15):
        b.door(u, 1, 2, "birch", facing="back", hinge="left" if u % 2 == 0 else "right")
    b.fill(0, 4, 2, W - 1, 4, 2, "black_concrete")
    for y in (5, 9):
        b.window(2, y, 2, W - 4, 2, GLAZING)
    P.flat_roof(b, 0, W - 1, 2, D - 1, 12, deck="gray_concrete", parapet="light_gray_concrete", coping="smooth_stone")
    big_sign(b, 7, 20, 12, 13, 1, "THREADS", "Fashion • Home • Beauty", 0xFF0A1F3A, WHITE)
    P.dogleg_stairs(b, 1, 20, [0, 4, 8], "stone_brick")
    for k, y in enumerate((1, 5, 9)):
        rails(b, 4, W - 4, 4, 17, y=y, step_u=3, step_v=3)
        bar_counter(b, 8, 20, D - 3, "front", tills=(9, 12, 15, 18), finish="white", y=y)
        for v in range(4, 18, 2):
            fit(b, "back_bar" if k == 2 else "clothes_rail", W - 2, y, v, "left")
    for y in (4, 8):
        ceiling_lights(b, 1, 3, W - 2, D - 2, y, step=3)
    b.light_grid(1, 3, W - 2, D - 2, 11, step=3, level=14)
    return b


# ================================================================== DIY warehouse and garden centre

def diy_store(ident="diy_store", name="DIY Warehouse and Garden Centre"):
    W, D = 46, 58
    b = Build(ident, name, "Commercial", W, 12, D,
              "An out-of-town DIY warehouse: car park, checkouts, aisles of paint and tools, pallet racking of timber "
              "and boxes, and a fenced garden centre of plant benches. FIX & BUILD is a made-up name.")
    car_park(b, 0, W - 1, 0, 17)
    P.paving(b, 0, 18, W - 1, 19)
    b.fill(0, 0, 20, 33, 0, D - 1, "smooth_stone")
    b.walls(0, 1, 20, 33, 8, D - 1, "light_gray_concrete")
    b.fill(0, 6, 20, 33, 8, 20, "orange_concrete")
    b.window(2, 1, 20, 10, 3, GLAZING)
    b.window(22, 1, 20, 10, 3, GLAZING)
    for u in (15, 16, 17, 18):
        b.door(u, 1, 20, "birch", facing="back", hinge="left" if u % 2 else "right")
    P.flat_roof(b, 0, 33, 20, D - 1, 9, deck="gray_concrete", parapet="light_gray_concrete", coping="smooth_stone")
    big_sign(b, 8, 25, 6, 8, 19, "FIX & BUILD", "DIY • Trade • Garden Centre", 0xFFE86A10, WHITE)
    checkouts(b, 3, 12, 24)
    checkouts(b, 21, 30, 24)
    for u in range(4, 31, 4):
        for v in range(29, D - 4):
            if u < 16:
                tall(b, "shelves_diy", u, 1, v, "right")
            else:
                tall(b, "pallet_racking", u, 1, v, "right")
    for u in range(2, 33, 4):
        for v in range(22, D - 1, 4):
            b.set(u, 9, v, "sea_lantern")
    # the garden centre, through a wide opening in the side wall
    b.fill(33, 1, 30, 33, 3, 33, AIR)
    b.fill(34, 0, 20, W - 1, 0, 49, "gravel")
    for u in range(34, W):
        b.set(u, 1, 49, B("oak_fence"))
        b.set(u, 1, 20, B("oak_fence"))
    for v in range(21, 49):
        b.set(W - 1, 1, v, B("oak_fence"))
    for u in range(36, W - 2, 3):
        for v in range(22, 48):
            if not 30 <= v <= 33 or u > 36:
                fit(b, "plant_bench", u, 1, v, "front")
    P.lawn(b, 34, 50, W - 1, D - 1)
    for u, v in ((38, 54), (43, 53)):
        P.tree(b, u, v, 4, 2)
    return b


# ================================================================== retail park

def retail_park(ident="retail_park", name="Small Retail Park (3 Warehouse Units)"):
    W, D = 66, 52
    b = Build(ident, name, "Commercial", W, 12, D,
              "An out-of-town retail park: a big car park in front of three warehouse units, a pet store, an "
              "electricals store and a sports shop, each with its own checkouts. PET PLANET, SPARKS and GOAL SPORTS "
              "are made-up names.")
    car_park(b, 0, W - 1, 0, 21)
    P.paving(b, 0, 22, W - 1, 23)
    v0 = 24
    b.fill(0, 0, v0, W - 1, 0, D - 1, "smooth_stone")
    units = (("PET PLANET", "Pet Food • Toys • Grooming", 0xFF2E7D32, WHITE),
             ("SPARKS", "TVs • Laptops • Appliances", 0xFFC62828, WHITE),
             ("GOAL SPORTS", "Sportswear • Fitness • Bikes", 0xFF0D47A1, YELLOW))
    for i, (text, sub, board, ink) in enumerate(units):
        u0, u1 = i * 22, i * 22 + 21
        b.walls(u0, 1, v0, u1, 7, D - 1, "light_gray_concrete")
        b.window(u0 + 2, 1, v0, 7, 3, GLAZING)
        b.window(u0 + 13, 1, v0, 7, 3, GLAZING)
        b.door(u0 + 10, 1, v0, "birch", facing="back", hinge="left")
        b.door(u0 + 11, 1, v0, "birch", facing="back", hinge="right")
        b.fill(u0, 4, v0, u1, 7, v0, "gray_concrete")
        big_sign(b, u0 + 4, u0 + 17, 5, 6, v0 - 1, text, sub, board, ink)
        checkouts(b, u0 + 2, u0 + 8, v0 + 4)
        if i == 0:                        # pets: aisles of food and supplies
            for u in range(u0 + 3, u1 - 2, 4):
                for v in range(v0 + 9, D - 4):
                    tall(b, ("shelves_groceries", "shelves_household", "shelves_snacks")[(u // 4) % 3], u, 1, v, "right")
        elif i == 1:                      # electricals: TVs, laptops, washing machines and fridges
            for v in range(v0 + 8, D - 3, 2):
                fit(b, "tv_unit", u0 + 1, 1, v, "right")
                fit(b, "tv", u0 + 1, 2, v, "right", level="low")
                fit(b, "washing_machine", u1 - 1, 1, v, "left")
                tall(b, "fridge_freezer", u1 - 1, 1, v + 1, "left")
            for u in range(u0 + 5, u1 - 4, 4):
                for v in range(v0 + 10, D - 5, 4):
                    fit(b, "desk", u, 1, v, "front")
                    fit(b, "computer", u, 2, v, "front", level="table")
        else:                             # sports: rails of kit, treadmills and benches
            rails(b, u0 + 3, u1 - 4, v0 + 9, D - 10)
            for v in range(D - 7, D - 2, 2):
                wide(b, "treadmill", u1 - 3, 1, v, "front")
                fit(b, "weight_bench", u0 + 3, 1, v, "front")
        for u in range(u0 + 2, u1, 4):
            for v in range(v0 + 2, D - 1, 4):
                b.set(u, 8, v, "sea_lantern")
    P.flat_roof(b, 0, W - 1, v0, D - 1, 8, deck="gray_concrete", parapet="light_gray_concrete", coping="smooth_stone")
    for u in (0, 32, W - 1):
        P.street_light(b, u, 22, "front", 6)
    return b


# ================================================================== gym

def gym(ident="gym", name="Gym in an Industrial Unit"):
    W, D = 22, 30
    b = Build(ident, name, "Commercial", W, 12, D,
              "A 24-hour gym in a converted industrial unit: reception, rows of treadmills along the window, weight "
              "benches, a mirror wall, changing rooms and parking. IRONWORKS GYM is a made-up name.")
    car_park(b, 0, W - 1, 0, 9)
    P.paving(b, 0, 10, W - 1, 11)
    b.fill(0, 0, 12, W - 1, 0, D - 1, "black_concrete")
    b.walls(0, 1, 12, W - 1, 7, D - 1, "light_gray_concrete")
    b.fill(0, 5, 12, W - 1, 7, 12, "gray_concrete")
    b.window(2, 1, 12, 8, 3, GLAZING)
    b.door(12, 1, 12, "birch", facing="back", hinge="left")
    b.door(13, 1, 12, "birch", facing="back", hinge="right")
    P.garage_door(b, 15, 19, 12, 1, 4, "front", "white_garage_door")
    P.flat_roof(b, 0, W - 1, 12, D - 1, 8, deck="gray_concrete", parapet="light_gray_concrete", coping="smooth_stone")
    big_sign(b, 3, 18, 5, 6, 11, "IRONWORKS GYM", "Open 24 Hours • No Contract", 0xFF111111, 0xFFFFC400)
    bar_counter(b, 11, 14, 15, "front", tills=(12,), finish="white")
    fit(b, "computer", 13, 2, 15, "front", level="counter")
    for u in range(2, 10, 2):
        wide(b, "treadmill", u + 1, 1, 14, "front")
    for u in range(3, 18, 3):
        for v in (19, 22):
            fit(b, "weight_bench", u, 1, v, "front")
    for u in range(1, W - 1):
        b.set(u, 2, D - 2, "glass")
        b.set(u, 3, D - 2, "glass")
    toilets(b, 2, 8, 25, 27, "light_gray_concrete", 24, h=7)
    for u in range(2, W - 1, 4):
        for v in range(14, D - 1, 4):
            b.set(u, 8, v, "sea_lantern")
    return b


# ================================================================== library

def library(ident="library", name="Carnegie Public Library"):
    W, D = 22, 24
    b = Build(ident, name, "Civic", W, 20, D,
              "An Edwardian public library in red brick and stone: a front desk, rows of bookcases, reading tables, "
              "public computers and a children's corner. RIVERSIDE is a made-up name.")
    stone = P.STONE
    P.paving(b, 0, 0, W - 1, 2)
    b.fill(0, 0, 3, W - 1, 0, D - 1, "oak_planks")
    b.walls(0, 1, 3, W - 1, 6, D - 1, RED_BRICK)
    for u in (0, W - 1, 9, 12):
        b.fill(u, 1, 3, u, 6, 3, stone)
    windows(b, (2, 3, 5, 6, 15, 16, 18, 19), 2, 3, h=3, kind=SASH, lintel=stone)
    windows(b, (4, 9, 14, 18), 2, D - 1, h=3, kind=SASH)
    b.door(10, 1, 3, "dark_oak", facing="back", hinge="left")
    b.door(11, 1, 3, "dark_oak", facing="back", hinge="right")
    b.window(10, 3, 3, 2, 1, SASH)
    b.fill(9, 7, 2, 12, 7, 2, slab(stone, "top"))
    big_sign(b, 7, 14, 6, 6, 2, "RIVERSIDE LIBRARY", "Public Library • Free Wi-Fi", 0xFF2A2A2A, GOLD)
    b.hipped_roof(0, W - 1, 3, D - 1, 7, "ptmuk:roof_slate")
    bar_counter(b, 8, 13, 7, "front", tills=(9,), finish="oak")
    fit(b, "computer", 12, 2, 7, "front", level="counter")
    for u in range(2, 20, 3):
        if u in (8, 11):
            continue
        for v in (10, 11, 12, 15, 16, 17):
            P.bookcase(b, u, 1, v)
    for v in (5, 7):
        P.table(b, 3, 1, v)
        b.set(2, 1, v, stairs("oak", "left"))
        b.set(4, 1, v, stairs("oak", "right"))
    for u in (16, 18):
        for v in (5, 7):
            fit(b, "desk", u, 1, v, "front")
            fit(b, "computer", u, 2, v, "front", level="table")
            fit(b, "office_chair", u, 1, v - 1, "back")
    P.rug(b, 8, 20, 13, 22, 1, "yellow")
    for u in (9, 12):
        P.armchair(b, u, 1, 22, "back", "jungle")
    b.light_grid(1, 4, W - 2, D - 2, 5, step=3, level=14)
    return b


# ================================================================== police station

def police_station(ident="police_station", name="Police Station"):
    W, D = 26, 30
    b = Build(ident, name, "Civic", W, 12, D,
              "A 1960s town police station: a front counter in the public lobby, offices with desks and computers, "
              "an interview room, a row of cells at the back and the blue lamp outside.")
    P.paving(b, 0, 0, W - 1, 2)
    b.fill(0, 0, 3, W - 1, 0, 24, "polished_andesite")
    b.walls(0, 1, 3, W - 1, 7, 24, RED_BRICK)
    floors(b, 1, W - 2, 4, 23, 2)
    windows(b, range(2, W - 2, 3), 2, 3, kind=GREY_WIN)
    windows(b, range(2, W - 2, 3), 5, 3, kind=GREY_WIN)
    b.door(12, 1, 3, "iron", facing="back", hinge="left")
    b.door(13, 1, 3, "birch", facing="back", hinge="right")
    P.flat_roof(b, 0, W - 1, 3, 24, 8, deck="gray_concrete", parapet=RED_BRICK, coping="smooth_stone")
    big_sign(b, 9, 16, 4, 4, 2, "POLICE", "Station", 0xFF0B2A6B, WHITE)
    b.set(10, 1, 1, "smooth_stone")
    for y in (2, 3):
        b.set(10, y, 1, B("stone_brick_wall", up=True, north="none", south="none", east="none", west="none", waterlogged=False))
    b.set(10, 4, 1, "blue_stained_glass")
    b.set(10, 5, 1, B("light", level=12, waterlogged=False))
    # lobby and front counter, offices either side
    b.fill(1, 1, 9, W - 2, 3, 9, "calcite")
    b.fill(10, 1, 9, 15, 1, 9, AIR)
    bar_counter(b, 10, 15, 9, "front", tills=(), finish="white")
    fit(b, "computer", 12, 2, 9, "front", level="counter")
    for u in (2, 4):
        P.armchair(b, u, 1, 4, "front", "birch")
    b.door(7, 1, 9, "birch", facing="back", hinge="left")
    b.door(18, 1, 9, "birch", facing="back", hinge="left")
    for u in (3, 7, 18, 22):
        for v in (12, 15):
            fit(b, "desk", u, 1, v, "front")
            fit(b, "computer", u, 2, v, "front", level="table")
            fit(b, "office_chair", u, 1, v + 1, "front")
    # cells along the back
    b.fill(1, 1, 18, W - 2, 3, 18, "stone_bricks")
    for u0 in range(1, W - 2, 4):
        if u0 + 3 > W - 2:
            break
        b.fill(u0 + 3, 1, 19, u0 + 3, 3, 23, "stone_bricks")
        b.door(u0 + 1, 1, 18, "iron", facing="front", hinge="left")
        P.bed(b, u0 + 2, 1, 23, "blue", "back")
    P.dogleg_stairs(b, W - 3, 12, [0, 4], "stone_brick")
    # upstairs: CID office and a canteen
    for u in (3, 7, 11, 15):
        for v in (6, 10, 14):
            fit(b, "desk", u, 5, v, "front")
            fit(b, "computer", u, 6, v, "front", level="table")
            fit(b, "office_chair", u, 5, v + 1, "front")
    P.kitchen_run(b, 2, 6, 5, 23, "front", finish="white")
    dining(b, 2, 18, 19, 21, y=5)
    ceiling_lights(b, 1, 4, W - 2, 23, 4, step=3)
    b.light_grid(1, 4, W - 2, 23, 7, step=3, level=13)
    # the yard for police cars
    P.paving(b, 0, 25, W - 1, D - 1, "ptmuk:tarmac")
    P.parking_bays(b, 1, 25, W - 2, D - 1, "u", 4, "front")
    return b


# ================================================================== fire station

def fire_station(ident="fire_station", name="Fire Station"):
    W, D = 30, 32
    b = Build(ident, name, "Civic", W, 22, D,
              "A fire station: three appliance bays behind red doors, a hatched apron out the front, the watch room, "
              "kitchen and mess, a dormitory upstairs and a drill tower in the yard.")
    P.paving(b, 0, 0, W - 1, 7, "ptmuk:tarmac")
    for u in range(1, 19):
        for v in range(1, 7):
            if (u + v) % 4 == 0:
                b.set(u, 0, v, "yellow_concrete")
    v0, v1 = 8, 24
    b.fill(0, 0, v0, W - 1, 0, v1, "smooth_stone")
    b.walls(0, 1, v0, W - 1, 7, v1, RED_BRICK)
    for i in range(3):
        u0 = 1 + i * 6
        P.garage_door(b, u0, u0 + 4, v0, 1, 4, "front", "red_garage_door")
        b.fill(u0 + 5, 1, v0, u0 + 5, 7, v0, "white_concrete")
    b.fill(1, 5, v0, 18, 7, v0, RED_BRICK)
    b.fill(19, 4, v0 + 1, W - 2, 4, v1 - 1, "oak_planks")
    windows(b, (21, 24, 27), 2, v0, kind=GREY_WIN)
    windows(b, (21, 24, 27), 5, v0, kind=GREY_WIN)
    b.door(20, 1, v0, "birch", facing="back", hinge="left")
    P.flat_roof(b, 0, W - 1, v0, v1, 8, deck="gray_concrete", parapet=RED_BRICK, coping="smooth_stone")
    big_sign(b, 3, 16, 6, 7, v0 - 1, "FIRE STATION", "Fire and Rescue Service", 0xFFB71C1C, WHITE)
    # the appliance bays: a big open hall, the wall between it and the station side
    b.fill(19, 1, v0 + 1, 19, 3, v1 - 1, RED_BRICK)
    b.door(19, 1, 14, "birch", facing="left", hinge="left")
    for i in range(3):
        for v in range(v0 + 1, v1, 3):
            b.set(1 + i * 6 + 2, 0, v, "yellow_concrete")
    for v in range(v0 + 2, v1 - 1, 2):
        fit(b, "tool_chest", 1, 1, v, "right")
    # watch room, kitchen and mess downstairs, the dormitory up the stairs
    bar_counter(b, 21, 24, 11, "front", tills=(), finish="white")
    fit(b, "computer", 22, 2, 11, "front", level="counter")
    P.kitchen_run(b, 21, 25, 1, v1 - 1, "front", finish="white")
    dining(b, 20, 27, 15, 18)
    P.dogleg_stairs(b, W - 3, 14, [0, 4], "stone_brick")
    for u in (21, 24):
        for v in (12, 18):
            P.bed(b, u, 5, v, "red", "front")
    P.sofa(b, 21, 23, 5, 22, "back", "dark_oak")
    ceiling_lights(b, 20, v0 + 1, W - 2, v1 - 1, 4, step=3)
    b.light_grid(20, v0 + 1, W - 2, v1 - 1, 7, step=3, level=13)
    for u in range(3, 18, 4):
        for v in range(v0 + 2, v1, 4):
            b.set(u, 8, v, "sea_lantern")
    # the yard and the drill tower: an open frame with floors to climb
    P.paving(b, 0, v1 + 1, W - 1, D - 1, "ptmuk:tarmac")
    t0, t1 = 22, 26
    for k in range(1, 5):
        b.fill(t0, 4 * k, v1 + 2, t1, 4 * k, v1 + 6, "smooth_stone")
    for u in (t0, t1):
        for v in (v1 + 2, v1 + 6):
            b.fill(u, 1, v, u, 19, v, RED_BRICK)
    b.fill(t0, 20, v1 + 2, t1, 20, v1 + 6, "smooth_stone")
    return b


# ================================================================== GP surgery

def gp_surgery(ident="gp_surgery", name="GP Surgery and Health Centre"):
    W, D = 24, 22
    b = Build(ident, name, "Civic", W, 9, D,
              "A modern health centre: reception, a waiting room of chairs, consulting rooms with desks and couches off "
              "a corridor, toilets and a car park. RIVERSIDE HEALTH CENTRE is a made-up name.")
    P.paving(b, 0, 0, W - 1, 3)
    b.fill(0, 0, 4, W - 1, 0, D - 1, "white_concrete")
    b.walls(0, 1, 4, W - 1, 4, D - 1, "ptmuk:white_render")
    windows(b, (2, 3, 5, 6, 17, 18, 20, 21), 2, 4, kind=GREY_WIN)
    b.window(9, 1, 4, 2, 3, GLAZING)
    b.window(13, 1, 4, 2, 3, GLAZING)
    b.door(11, 1, 4, "birch", facing="back", hinge="left")
    b.door(12, 1, 4, "birch", facing="back", hinge="right")
    P.flat_roof(b, 0, W - 1, 4, D - 1, 5, deck="gray_concrete", parapet="ptmuk:white_render", coping="smooth_stone")
    big_sign(b, 6, 17, 5, 5, 3, "RIVERSIDE HEALTH CENTRE", "GP Surgery • Pharmacy", 0xFF005EB8, WHITE)
    bar_counter(b, 15, 19, 7, "front", tills=(), finish="white")
    for u in (16, 18):
        fit(b, "computer", u, 2, 7, "front", level="counter")
    for u in range(2, 10, 2):
        for v in (7, 9):
            fit(b, "dining_chair", u, 1, v, "back")
    fit(b, "houseplant", 1, 1, 5, "front", level="floor")
    # a corridor behind the waiting room, consulting rooms off it
    b.fill(1, 1, 11, W - 2, 3, 11, "calcite")
    b.fill(11, 1, 11, 12, 3, 11, AIR)
    b.fill(1, 1, 14, W - 2, 3, 14, "calcite")
    for wall_u in (6, 12, 17):
        b.fill(wall_u, 1, 15, wall_u, 3, D - 2, "calcite")
    for u0, u1 in ((1, 5), (7, 11), (13, 16), (18, W - 2)):
        b.door(u0, 1, 14, "birch", facing="back", hinge="left")
        fit(b, "desk", u0 + 1, 1, D - 2, "front")
        fit(b, "computer", u0 + 1, 2, D - 2, "front", level="table")
        fit(b, "office_chair", u0 + 1, 1, D - 3, "front")
        P.bed(b, u1, 1, 15, "white", "front")
    b.light_grid(1, 5, W - 2, D - 2, 4, step=3, level=14)
    return b


# ================================================================== primary school

def primary_school(ident="primary_school", name="Victorian Primary School"):
    W, D = 36, 36
    b = Build(ident, name, "Civic", W, 20, D,
              "A Victorian board school in red brick: tall windows, a bell turret, four classrooms with rows of desks "
              "round a hall with a stage, and a playground with railings. ST. MARY'S is a made-up name.")
    P.paving(b, 0, 0, W - 1, 1)
    for u in range(W):
        b.set(u, 1, 0, B("iron_bars", north=False, south=False, east=u < W - 1, west=u > 0, waterlogged=False))
    b.fill(16, 1, 0, 19, 1, 0, AIR)
    b.fill(0, 0, 2, W - 1, 0, 5, "ptmuk:tarmac")
    v0, v1 = 6, 24
    b.fill(0, 0, v0, W - 1, 0, v1, "oak_planks")
    b.walls(0, 1, v0, W - 1, 5, v1, RED_BRICK)
    for u in list(range(2, 15, 3)) + list(range(22, 34, 3)):
        b.window(u, 2, v0, 1, 3, SASH)
        b.window(u, 2, v1, 1, 3, SASH)
    b.door(17, 1, v0, "dark_oak", facing="back", hinge="left")
    b.door(18, 1, v0, "dark_oak", facing="back", hinge="right")
    b.window(17, 3, v0, 2, 1, SASH)
    big_sign(b, 12, 23, 5, 5, v0 - 1, "ST. MARY'S PRIMARY SCHOOL", "", 0xFF1D3557, WHITE)
    top = b.gable_roof(0, W - 1, v0, v1, 6, "ptmuk:roof_slate")
    P.close_gable(b, 0, v0 + 1, v1 - 1, 6, RED_BRICK)
    P.close_gable(b, W - 1, v0 + 1, v1 - 1, 6, RED_BRICK)
    for y in range(top, top + 3):
        for u, v in ((17, 14), (18, 14), (17, 15), (18, 15)):
            b.set(u, y, v, "oak_fence" if y > top else "oak_planks")
    b.set(17, top + 1, 14, B("bell", facing="south", attachment="floor", powered=False))
    for u in (17, 18):
        for v in (14, 15):
            b.set(u, top + 3, v, stairs("ptmuk:roof_slate", "back" if v == 14 else "front"))
    # the hall in the middle with a stage, classrooms either side
    b.fill(12, 1, v0 + 1, 12, 4, v1 - 1, "calcite")
    b.fill(23, 1, v0 + 1, 23, 4, v1 - 1, "calcite")
    for u0, u1 in ((1, 11), (24, W - 2)):
        b.fill(u0, 1, 15, u1, 4, 15, "calcite")
    for v in (10, 19):
        b.door(12, 1, v, "birch", facing="left", hinge="left")
        b.door(23, 1, v, "birch", facing="right", hinge="left")
    b.fill(13, 1, v1 - 4, 22, 1, v1 - 1, slab("oak", "top"))
    for u in range(14, 22, 2):
        for v in range(10, 16, 2):
            fit(b, "dining_chair", u, 1, v, "front")
    for u0, u1 in ((1, 11), (24, W - 2)):
        for v0c, v1c in ((v0 + 1, 14), (16, v1 - 1)):
            for v in range(v0c + 1, v1c - 1, 2):
                for u in range(u0 + 1, u1 - 1, 3):
                    P.table(b, u, 1, v)
                    b.set(u + 1, 1, v, stairs("birch", "right"))
    b.light_grid(1, v0 + 1, W - 2, v1 - 1, 4, step=3, level=14)
    # the playground
    b.fill(0, 0, v1 + 1, W - 1, 0, D - 1, "ptmuk:tarmac")
    for u in range(4, 12):
        b.set(u, 0, 30, "white_concrete")
    for v in range(27, 34):
        if v != 30:
            b.set(8, 0, v, "white_concrete")
    for k in range(8):
        b.set(20 + k, 0, 29, ("red_concrete", "yellow_concrete", "blue_concrete", "lime_concrete")[k % 4])
    # the back door opens off the stage, a step up from the playground
    b.set(17, 1, v1, b.get(16, 1, v1))
    b.door(17, 2, v1, "dark_oak", facing="front", hinge="left")
    b.set(17, 1, v1 + 1, stairs("stone_brick", "back"))
    b.set(15, 2, v1 - 3, B("lectern", facing="south", has_book=False, powered=False))
    for u in (13, 22):
        b.set(u, 2, v1 - 1, B("potted_azalea_bush"))
    return b


# ================================================================== leisure centre with a pool

def leisure_centre(ident="leisure_centre", name="Leisure Centre with Swimming Pool"):
    W, D, SINK = 44, 40, 3
    b = Build(ident, name, "Civic", W, 13, D,
              "A council leisure centre: reception, a 20-metre swimming pool with changing rooms, a sports hall marked "
              "out for badminton and a gym. RIVERSIDE LEISURE is a made-up name.", sink=SINK)
    b.fill(0, -SINK, 0, W - 1, -1, D - 1, "dirt")
    car_park(b, 0, W - 1, 0, 9)
    P.paving(b, 0, 10, W - 1, 11)
    v0, v1 = 12, D - 1
    b.fill(0, 0, v0, W - 1, 0, v1, "smooth_quartz")
    b.walls(0, 1, v0, W - 1, 8, v1, "light_gray_concrete")
    b.fill(0, 7, v0, W - 1, 8, v0, "light_blue_concrete")
    b.window(2, 1, v0, 14, 4, GLAZING)
    b.window(26, 1, v0, 14, 4, GLAZING)
    for u in (20, 21, 22, 23):
        b.door(u, 1, v0, "birch", facing="back", hinge="left" if u % 2 == 0 else "right")
    P.flat_roof(b, 0, W - 1, v0, v1, 9, deck="gray_concrete", parapet="light_gray_concrete", coping="smooth_stone")
    big_sign(b, 14, 29, 7, 8, v0 - 1, "RIVERSIDE LEISURE", "Pool • Gym • Sports Hall", 0xFF006D77, WHITE)
    # reception between the pool hall (left) and sports hall (right)
    b.fill(18, 1, v0 + 1, 18, 7, v1 - 1, "light_gray_concrete")
    b.fill(25, 1, v0 + 1, 25, 7, v1 - 1, "light_gray_concrete")
    bar_counter(b, 19, 23, 17, "front", tills=(20,), finish="white")
    fit(b, "computer", 22, 2, 17, "front", level="counter")
    b.door(18, 1, 22, "birch", facing="left", hinge="left")
    b.door(25, 1, 22, "birch", facing="right", hinge="left")
    toilets(b, 19, 23, 26, 28, "calcite", 25, h=8)
    # the pool
    pu0, pu1, pv0, pv1 = 3, 15, 19, 34
    b.fill(pu0 - 1, -SINK, pv0 - 1, pu1 + 1, -1, pv1 + 1, "light_blue_concrete")
    b.fill(pu0, -2, pv0, pu1, 0, pv1, B("water", level=0))
    for v in range(pv0, pv1 + 1, 5):
        b.set(pu0, -SINK, v, "sea_lantern")
    for u in range(pu0 + 2, pu1, 3):
        for v in range(pv0, pv1 + 1):
            b.set(u, -SINK, v, "blue_concrete")
    for v in range(pv0, pv1 + 1, 2):
        b.set(pu1, -1, v, B("quartz_stairs", facing="west", half="bottom", shape="straight", waterlogged=True))
    for v in (pv0 + 2, pv0 + 8, pv1 - 2):                  # poolside benches and a lifeguard's chair
        b.set(pu1 + 2, 1, v, B("ptmuk:bench_wood", facing="west"))
    b.set(pu0 - 2, 1, (pv0 + pv1) // 2, stairs("spruce", "left"))
    # the sports hall: court lines on a wooden floor, a gym at the front
    b.fill(26, 0, 22, W - 2, 0, v1 - 1, "oak_planks")
    for u in range(27, W - 2):
        b.set(u, 0, 23, "yellow_concrete")
        b.set(u, 0, v1 - 2, "yellow_concrete")
    for v in range(23, v1 - 1):
        b.set(27, 0, v, "yellow_concrete")
        b.set(W - 3, 0, v, "yellow_concrete")
        b.set(34, 0, v, "white_concrete")
    for u in range(27, W - 2):
        b.set(u, 0, 30, "white_concrete")
    for u in range(27, 41, 3):
        wide(b, "treadmill", u + 1, 1, 14, "front")
        fit(b, "weight_bench", u, 1, 18, "front")
    for u in range(2, W - 1, 4):
        for v in range(v0 + 2, v1, 4):
            b.set(u, 9, v, "sea_lantern")
    return b


# ================================================================== bus garage

def bus_garage(ident="bus_garage", name="Bus Garage"):
    W, D = 44, 64
    b = Build(ident, name, "Other", W, 14, D,
              "A bus garage for PTM2's buses: a tall shed with six lanes of parking bays behind big doorways, a "
              "workshop, the fuel island and bus wash, and an office block with the canteen. RIVERSIDE BUS GARAGE is a "
              "made-up name.")
    P.paving(b, 0, 0, W - 1, D - 1, "ptmuk:tarmac")
    v0, v1 = 8, 52
    b.fill(0, 0, v0, 33, 0, v1, "smooth_stone")
    b.walls(0, 1, v0, 33, 9, v1, "light_gray_concrete")
    for i in range(5):
        u0 = 2 + i * 6
        b.fill(u0, 1, v0, u0 + 4, 7, v0, AIR)
        b.fill(u0, 1, v1, u0 + 4, 7, v1, AIR)
        for v in range(v0 + 1, v1):
            b.set(u0 - 1, 0, v, "white_concrete")
    b.fill(0, 8, v0, 33, 9, v0, "light_gray_concrete")
    P.flat_roof(b, 0, 33, v0, v1, 10, deck="gray_concrete", parapet="light_gray_concrete", coping="smooth_stone")
    big_sign(b, 6, 27, 8, 9, v0 - 1, "RIVERSIDE BUS GARAGE", "", 0xFF8E1B1B, WHITE)
    for u in range(3, 33, 4):
        for v in range(v0 + 2, v1, 4):
            b.set(u, 10, v, "sea_lantern")
    for v in range(v0 + 2, v1 - 2, 3):
        fit(b, "tool_chest", 32, 1, v, "left")
    for v in (v1 - 6, v1 - 3):
        fit(b, "tyre_stack", 1, 1, v, "right")
        fit(b, "oil_drum", 1, 1, v - 1, "right")
    for v in (v0 + 6, v0 + 12):
        tall(b, "car_lift", 32, 1, v + 1, "left")
    # office block and canteen on the right
    b.fill(35, 0, v0, W - 1, 0, 22, "polished_andesite")
    b.walls(35, 1, v0, W - 1, 7, 22, RED_BRICK)
    floors(b, 36, W - 2, v0 + 1, 21, 2)
    windows(b, (37, 39, 41), 2, v0, kind=GREY_WIN)
    windows(b, (37, 39, 41), 5, v0, kind=GREY_WIN)
    b.door(42, 1, v0, "birch", facing="back", hinge="left")
    P.flat_roof(b, 35, W - 1, v0, 22, 8, deck="gray_concrete", parapet=RED_BRICK, coping="smooth_stone")
    bar_counter(b, 36, 39, 12, "front", tills=(), finish="white")
    fit(b, "computer", 37, 2, 12, "front", level="counter")
    dining(b, 36, 40, 15, 19)
    P.kitchen_run(b, 36, 39, 1, 21, "front", finish="white")
    P.dogleg_stairs(b, 41, 15, [0, 4], "stone_brick")
    for u in (37, 40):
        for v in (11, 14):
            fit(b, "desk", u, 5, v, "front")
            fit(b, "computer", u, 6, v, "front", level="table")
            fit(b, "office_chair", u, 5, v + 1, "front")
    ceiling_lights(b, 36, v0 + 1, W - 2, 21, 4, step=3)
    b.light_grid(36, v0 + 1, W - 2, 21, 7, step=3, level=13)
    # fuel island and bus wash in the yard
    for v in (30, 36):
        b.set(38, 1, v, B("ptmuk:fuel_pump", facing="west"))
    b.fill(37, 0, 28, 41, 0, 38, "smooth_stone")
    return b


# ================================================================== village hall

def village_hall(ident="village_hall", name="Village Hall"):
    W, D = 16, 20
    b = Build(ident, name, "Civic", W, 15, D,
              "A brick village hall: a porch, a hall laid out with chairs facing a stage, a serving hatch from the "
              "kitchen and toilets.")
    P.paving(b, 0, 0, W - 1, 2)
    b.fill(0, 0, 3, W - 1, 0, D - 2, "oak_planks")
    b.walls(0, 1, 3, W - 1, 4, D - 2, RED_BRICK)
    windows(b, (2, 4, 11, 13), 2, 3, kind=SASH)
    for v in (7, 10, 13):
        b.window(0, 2, v, 1, 2, SASH)
        b.window(W - 1, 2, v, 1, 2, SASH)
    b.door(7, 1, 3, "dark_oak", facing="back", hinge="left")
    b.door(8, 1, 3, "dark_oak", facing="back", hinge="right")
    b.gable_roof_side(0, W - 1, 2, D - 1, 5, "ptmuk:roof_clay_tiles")       # the gable end faces the street
    P.close_gable_front(b, 3, 1, W - 2, 5, RED_BRICK)
    P.close_gable_front(b, D - 2, 1, W - 2, 5, RED_BRICK)
    big_sign(b, 5, 10, 5, 5, 2, "VILLAGE HALL", "", 0xFF234D20, CREAM)
    for u in range(2, 14, 2):
        for v in range(6, 12, 2):
            if u not in (7, 8):
                fit(b, "dining_chair", u, 1, v, "back")
    b.fill(1, 1, 13, W - 2, 1, 15, slab("oak", "top"))
    b.set(W // 2, 2, 14, B("lectern", facing="south", has_book=False, powered=False))
    for u in (1, W - 2):
        b.set(u, 2, 15, B("potted_azalea_bush"))
    P.kitchen_run(b, 2, 6, 1, D - 3, "front", finish="white")
    b.light_grid(1, 4, W - 2, D - 3, 4, step=3, level=13)
    return b


def all_civic():
    return [department_store(), fashion_store(), diy_store(), retail_park(), gym(), library(), police_station(),
            fire_station(), gp_surgery(), primary_school(), leisure_centre(), bus_garage(), village_hall()]
