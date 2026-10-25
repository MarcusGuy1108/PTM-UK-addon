"""Street fronts: what makes a British shop, cafe, takeaway, pub or hotel look lived in from the
pavement, added once a prefab is built (and enlarged): stone sills under bare upper windows, a
downpipe down one end, a burglar alarm box at first floor, now and then a satellite dish where
people live upstairs, roller shutter boxes over takeaway and small-shop windows, coach lanterns by
a pub's or hotel's door and hanging baskets along a pub's front, an A-board on the pavement
outside cafes, restaurants and pubs, and air conditioning units on flat roofs (big sheds too).
Things only go on bare wall with nothing already in front of it, never in front of a door or a
window."""
import zlib

import prefab_check as C
import prefab_parts as P
from prefab_furnish import home
from prefab_lib import DIRS, slab

FRONTS = ("Shops", "Food & Drink", "Pubs & Bars", "Hotels")
ROOF_PLANT = FRONTS + ("Commercial",)
HAS_OWN = {"car_dealership"}                                   # dressed in its own design
SHUTTERED = {"fish_and_chips", "chinese_takeaway", "kebab_shop", "pizza_takeaway", "games_shop", "pharmacy",
             "newsagent", "bookmakers", "launderette", "phone_shop", "estate_agent", "pound_shop", "convenience_store",
             "corner_shop", "barbers"}
A_BOARDS = {"coffee_shop", "bakery", "cafe_takeaway", "peri_peri_restaurant", "pizza_restaurant", "indian_restaurant",
            "wine_bar", "cocktail_bar", "fish_and_chips"}
LANTERNS = ("Pubs & Bars", "Hotels")
FACING = DIRS["front"]


def _is_window(blk):
    return blk is not None and blk.name.endswith(("_window", "_pane", "office_glazing"))


def _is_wall(blk):
    """Plain wall: a full block that isn't glass, a door or a sign."""
    return blk is not None and C.full_cube(blk) and not _is_window(blk) and not blk.name.endswith(("_door", "shop_sign"))


def _free(b, u, y, v):
    return b.inside(u, y, v) and b.get(u, y, v).name == "minecraft:air"


def _front_line(b):
    """The v of the front wall: where most columns first meet wall, glass or a door 2 or 3 blocks up."""
    count = {}
    for u in range(b.W):
        for v in range(b.D):
            if any(_is_wall(b.get(u, y, v)) or _is_window(b.get(u, y, v)) or b.get(u, y, v).name.endswith("_door")
                   for y in (2, 3)):
                count[v] = count.get(v, 0) + 1
                break
    return max(count, key=lambda v: (count[v], -v)) if count else None


class Front:
    def __init__(self, b, v):
        self.b, self.v, self.out = b, v, v - 1
        self.cols = [u for u in range(b.W) if any(_is_wall(b.get(u, y, v)) or _is_window(b.get(u, y, v)) for y in (2, 3))]
        self.top = {u: max((y for y in range(b.H) if not b.get(u, y, v).name == "minecraft:air"), default=0) for u in self.cols}
        signs = [y for (u, y, vv), blk in b.cells.items() if vv in (v, v - 1) and blk.name == "ptmuk:shop_sign" and y <= 6]
        self.sign_y = min(signs) if signs else None
        self.doors = sorted(u for u in self.cols if b.get(u, 1, v).name.endswith("_door"))

    def bare(self, u, y):
        """Plain wall at (u, y) with room in front of it, and not over a door."""
        return _is_wall(self.b.get(u, y, self.v)) and _free(self.b, u, y, self.out) and u not in self.doors

    def put(self, u, y, name, **props):
        self.b.set(u, y, self.out, home(name, facing=FACING, lit=False, **props))


def dress_front(b):
    if b.ident in HAS_OWN:
        return
    if b.category in ROOF_PLANT:
        roof_plant(b)
    if b.category not in FRONTS:
        return
    v = _front_line(b)
    if v is None or v < 1:
        return
    f = Front(b, v)
    if len(f.cols) < 3:
        return
    pick = zlib.crc32(b.ident.encode())
    sills(f)
    downpipe(f)
    upper = (f.sign_y or 4) + 2
    alarm = alarm_box(f, upper)
    if pick % 2 == 0 and max(f.top.values()) >= upper + 5:
        dish(f, upper + 3, avoid=alarm)
    if b.ident in SHUTTERED and f.sign_y:
        shutters(f)
    if b.category in LANTERNS:
        lanterns(f)
    if b.category == "Pubs & Bars":
        baskets(f, upper - 1)
    if b.ident in A_BOARDS or b.category == "Pubs & Bars":
        a_board(f)


def sills(f):
    """A stone sill under every upper window that hasn't one (the shop front's glass is left alone)."""
    b = f.b
    for u in f.cols:
        for y in range(5, b.H):
            if _is_window(b.get(u, y, f.v)) and not _is_window(b.get(u, y - 1, f.v)) and _is_wall(b.get(u, y - 1, f.v)) \
                    and _free(b, u, y - 1, f.out):
                b.set(u, y - 1, f.out, slab(P.STONE, "top"))


def downpipe(f):
    """Down one end of the front from under the eaves, as far as it can go before something's in the way."""
    b = f.b
    for u in (max(f.cols), min(f.cols)):
        y0 = f.top[u] - 1
        ys = []
        for y in range(y0, 0, -1):
            if not (_free(b, u, y, f.out) and not _is_window(b.get(u, y, f.v))):
                break
            ys.append(y)
        if len(ys) >= 4:
            for y in ys:
                f.put(u, y, "downpipe")
            return


def alarm_box(f, y):
    """A burglar alarm box high on the first floor, near one end."""
    order = sorted(f.cols, key=lambda u: min(u - min(f.cols), max(f.cols) - u))
    for u in order[2:] + order[:2]:
        if f.bare(u, y) and _free(f.b, u, y + 1, f.out):
            f.put(u, y, "alarm_box")
            return u
    return None


def dish(f, y, avoid):
    for u in sorted(f.cols, key=lambda u: -abs(u - (avoid if avoid is not None else 0))):
        if f.bare(u, y):
            f.put(u, y, "satellite_dish")
            return


def shutters(f):
    """Roller shutter boxes across the top of the shop window, under the fascia."""
    b = f.b
    y = f.sign_y - 1
    for u in f.cols:
        if b.get(u, f.sign_y, f.out).name == "ptmuk:shop_sign" and _free(b, u, y, f.out) and _is_window(b.get(u, y, f.v)):
            f.put(u, y, "shutter_box")


def lanterns(f):
    """A coach lantern each side of the front door (lit)."""
    if not f.doors:
        return
    d = f.doors[len(f.doors) // 2]
    for u in (d - 1, d + 1, d - 2, d + 2):
        for y in (3, 4):
            if f.bare(u, y) and abs(u - d) <= 2:
                f.b.set(u, y, f.out, home("coach_lantern", facing=FACING, lit=True))
                break


def baskets(f, y):
    """Hanging baskets along the front between the windows."""
    last = -9
    for u in f.cols:
        if u - last >= 4 and f.bare(u, y) and _free(f.b, u, y - 1, f.out):
            f.put(u, y, "hanging_basket")
            last = u


def a_board(f):
    """An A-board on the pavement beside the door, out of the way of it."""
    if not f.doors:
        return
    d = f.doors[0]
    for u in (d - 2, d + 2, d - 3, d + 3):
        if _free(f.b, u, 1, f.out) and _free(f.b, u, 2, f.out) and f.b.inside(u, 0, f.out) and \
                f.b.get(u, 0, f.out).name != "minecraft:air" and not f.b.get(u, 1, f.v).name.endswith("_door"):
            f.put(u, 1, "a_board")
            return


def roof_plant(b):
    """Air conditioning units on a flat roof, set back from the front: one on a small roof, a few on a big one."""
    tops = {}
    for (u, y, v), blk in b.cells.items():
        if y > tops.get((u, v), (-1, None))[0]:
            tops[(u, v)] = (y, blk)
    deck = [(u, y, v) for (u, v), (y, blk) in tops.items()
            if y >= 5 and blk.name in ("minecraft:gray_concrete", "minecraft:light_gray_concrete")
            and all(tops.get((u + du, v + dv), (-1, None))[0] == y for du in (-1, 0, 1) for dv in (-1, 0, 1))]
    if not deck:
        return
    want = min(4, max(1, len(deck) // 80))
    deck.sort(key=lambda p: (-p[2], p[0]))
    placed = []
    for u, y, v in deck:
        if len(placed) == want:
            break
        if all(abs(u - pu) + abs(v - pv) >= 4 for pu, pv in placed) and _free(b, u, y + 1, v):
            b.set(u, y + 1, v, home("air_con", facing=DIRS["front"], lit=False))
            placed.append((u, v))
