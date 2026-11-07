"""Dresses the rooms of the homes in every prefab (houses, and flats over shops and pubs) once
prefab_furnish has done its work. What a room has says what it is: a bed, a bedroom; a bath, toilet or
basin, a bathroom; kitchen units, a kitchen; a sofa, fire or TV, a living room; a dining table, a
dining room; a small room with nothing in it, a hall or landing. It gets what such a room is missing
along its free walls: bedside tables and a lamp, a chest of drawers, a basin, mirror cabinet and towel
rail, a washing machine, kettle, toaster and microwave, a coffee table, a lamp and a plant, a radiator
under the window, pictures, a clock, coat hooks by the front door, curtains at the windows and a light
in the ceiling if it has none. Nothing goes in front of a door, a piece of furniture or the stairs, or
anywhere that would cut a room in two. Shops, bars and public rooms keep what they have.

A big floor left empty (the storeys of a block of flats, rooms over a pub) is fitted out first as
flats: a corridor from the stairs with a flat off it every few blocks, each behind its own front door
(or the whole floor one flat if it's small). Each flat gets a bathroom walled off with its own door
(bath, toilet and basin), a bedroom walled off with a door if there's room for one (else the bed goes
in the living room), and a kitchen and a sofa in what's left; nothing cuts off the way through."""
import zlib

import prefab_check as pc
import prefab_furnish as PF
from prefab_furnish import CW, CCW, OPP, VEC, home
from prefab_lib import B

HOME_TAGS = {"bed", "bath", "kitchen", "living", "dining"}
TAGS = (("bed", ("_bed",)),
        ("bath", ("ptmuk:bath", "ptmuk:toilet", "ptmuk:basin", "ptmuk:shower")),
        ("kitchen", ("ptmuk:kitchen_", "ptmuk:fridge_freezer")),
        ("living", ("ptmuk:sofa_", "ptmuk:fireplace", "ptmuk:tv")),
        ("dining", ("ptmuk:dining_table",)),
        ("shop", ("till", "shelves_", "menu_board", "fryer", "checkout", "back_bar", "beer_pumps", "barber_chair",
                  "chiller", "clothes_rail", "pizza_oven", "doner_spit", "coffee_machine", "cake_display", "pool_table",
                  "cinema_seat", "bar_stool", "freezer_island", "cash_machine", "bottle_fridge", "upright_freezer",
                  "dj_decks", "pallet_racking", "produce_stand", "lectern", "pew", "office_chair")),
        ("garage", ("tool_chest", "garage_door", "car_lift", "tyre_stack", "oil_drum")),
        ("loft", ("roof_",)))
# a light you can see (the invisible light blocks some rooms have don't count: a room still wants a fitting)
LIGHT_WORDS = ("lantern", "glowstone", "shroomlight", "torch", "lamp", "froglight", "end_rod", "candle", "pendant",
               "ceiling_light")
PICTURES = ("picture_landscape", "picture_seaside", "picture_flowers", "picture_abstract")
CURTAINS = ("curtains_cream", "curtains_red", "curtains_blue", "curtains_green")


# the kinds of building people live in (or over)
CATEGORIES = ("Houses", "Flats", "Estates", "Shops", "Food & Drink", "Pubs & Bars", "Hotels")
# an empty room this big is fitted out as a home (if it's a block of flats, or upstairs), in rooms about FLAT big
EMPTY, FLAT = 40, 110
BEDS = ("white_bed", "light_blue_bed", "blue_bed", "red_bed", "green_bed", "gray_bed")


def _narrowest(r):
    """The room's narrowest span across (a strip or a passage is one or two)."""
    us, vs = [c[0] for c in r.cells], [c[2] for c in r.cells]
    return min(max(us) - min(us), max(vs) - min(vs)) + 1


def _doors(g, r):
    """How many doors open off a room (a flat has its one front door; a hall or a landing has more)."""
    return len({n for (u, y, v) in r.cells for du, dv in VEC.values()
                if (blk := g.get(*(n := (u + du, y, v + dv)))) is not None and blk.name.endswith("_door")})


def dress(b):
    _dress(b)
    _unblock(b)


def _unblock(b):
    """Furniture that shuts part of a room off (a bed or a telly placed across the way): take it out, a piece
    at a time, until you can walk everywhere again (prefab_check would refuse the building otherwise)."""
    for _ in range(8):
        g = pc.Grid(b)
        out = pc.outside(g)
        if not pc.walk_in_problems(g, out):
            return
        stand, seen = pc.walk_in(g, out)
        lost = {p for p in stand - seen if p not in out}
        piece = next((n for (u, y, v) in sorted(lost) for du, dv in VEC.values()
                      if _movable(g.get(*(n := (u + du, y, v + dv))))
                      and any((n[0] + a, y, n[2] + c) in seen for a, c in VEC.values())), None)
        if piece is None:
            return
        u, y, v = piece
        blk = g.get(u, y, v)
        if blk.name.endswith("_bed"):                        # both halves
            for du, dv in VEC.values():
                if (other := g.get(u + du, y, v + dv)) is not None and other.name == blk.name:
                    b.set(u + du, y, v + dv, "air")
        if _movable(g.get(u, y + 1, v)):                     # a telly on its stand, a lamp on a table
            b.set(u, y + 1, v, "air")
        b.set(u, y, v, "air")


def _movable(blk):
    return blk is not None and (blk.name.endswith("_bed") or blk.name.startswith("ptmuk:") and not pc._ptmuk_full(blk.name)
                                and not blk.name.endswith(("_door", "_window", "_pane", "_stairs", "_slab")))


def _dress(b):
    if b.category == "Offices":
        offices(b)
        return
    if b.category not in CATEGORIES:
        return
    g = pc.Grid(b)
    out = pc.outside(g)
    rooms = list(_rooms(b, g, out))
    pick = zlib.crc32(b.ident.encode())
    fitted = False
    for i, r in enumerate(rooms):
        if not r.tags and len(r.cells) >= EMPTY and (b.category in ("Flats", "Estates") or r.y > 2) and _doors(g, r) < 2 \
                and _narrowest(r) >= 3:
            for j, part in enumerate(Dresser(b, g, out, r, pick + i).flats()):
                Dresser(b, g, out, part, pick + i + j).fit_flat()
            fitted = True
    if fitted:
        PF.joins(b)                                          # the new sofas' ends and middles
        PF.tvs(b)                                            # and a TV across from each
        rooms = list(_rooms(b, g, out))
    if b.category == "Hotels":
        # every bedroom its own shower room or bathroom, walled off in a corner
        for i, r in enumerate(rooms):
            if "bed" in r.tags and "bath" not in r.tags:
                d = Dresser(b, g, out, r, pick + i)
                d.keep = d._inside_door()                     # a small room: only the way in has to stay clear
                en_suite = d._enclose(3, 2, want_window=False, min_rest=8) or d._enclose(2, 2, want_window=False, min_rest=6)
                if en_suite is not None:
                    Dresser(b, g, out, en_suite, pick + i).fit_bathroom()
        rooms = list(_rooms(b, g, out))
    if not any(r.tags & HOME_TAGS for r in rooms):
        return                                               # not a home, or no home in it
    curtains = CURTAINS[pick % len(CURTAINS)]
    for i, r in enumerate(rooms):
        if "garage" in r.tags and "shop" not in r.tags:
            Dresser(b, g, out, r, pick + i).garage()
            continue
        if r.tags & {"shop", "garage"} or not (r.tags & HOME_TAGS or len(r.cells) <= 30 and "loft" not in r.tags):
            continue
        d = Dresser(b, g, out, r, pick + i)
        d.light()
        d.curtains(curtains)
        if "bed" in r.tags:
            d.bedroom()
        if "bath" in r.tags:
            d.bathroom()
        if "kitchen" in r.tags:
            d.kitchen()
        if "living" in r.tags:
            d.living()
        if "dining" in r.tags:
            d.dining()
        if not r.tags & HOME_TAGS:
            d.hall()


def offices(b):
    """An office building's empty floors get desks with computers along their walls, plants and lights."""
    g = pc.Grid(b)
    out = pc.outside(g)
    seed = zlib.crc32(b.ident.encode())
    for i, r in enumerate(_rooms(b, g, out)):
        if not r.tags and len(r.cells) >= EMPTY:
            d = Dresser(b, g, out, r, seed + i)
            d.office()
            d.light()


class Room:
    def __init__(self, cells, y):
        self.cells, self.y = cells, y
        self.tags = set()
        self.names = []


def _rooms(b, g, out):
    stand = set()
    for (u, y, v), blk in list(b.cells.items()):
        p = (u, y + 1, v)
        if p not in out and b.inside(*p) and pc._floor(blk) and g.clear(u, y + 1, v) and g.clear(u, y + 2, v) \
                and not _stairs(blk):
            stand.add(p)
    done = set()
    for p in sorted(stand):
        if p in done:
            continue
        cells, todo = [], [p]
        done.add(p)
        while todo:
            u, y, v = todo.pop()
            cells.append((u, y, v))
            for du, dv in VEC.values():
                n = (u + du, y, v + dv)
                if n in stand and n not in done:
                    done.add(n)
                    todo.append(n)
        if len(cells) < 3:
            continue
        r = Room(set(cells), p[1])
        for (u, y, v) in cells:
            for du, dv in list(VEC.values()) + [(0, 0)]:
                for dy in (0, 1, 2):
                    blk = g.get(u + du, y + dy, v + dv)
                    if blk is not None and blk.name != "minecraft:air":
                        r.names.append(blk.name)
        for tag, words in TAGS:
            if any(w in n for n in r.names for w in words):
                r.tags.add(tag)
        yield r


def _stairs(blk):
    return blk.name.endswith("_stairs")


def _wall(blk):
    return blk is not None and pc.full_cube(blk) and not pc.is_pane(blk) and not blk.name.endswith("_door")


class Dresser:
    def __init__(self, b, g, out, room, seed):
        self.b, self.g, self.out, self.r, self.seed = b, g, out, room, seed
        self.y = room.y
        self.free = set(room.cells)
        self.keep = self._reserved()

    # ------------------------------------------------------------------ where things can go

    def _reserved(self):
        """Cells left clear: by doors and stairs, and in front of furniture you use from the front."""
        keep = set()
        for (u, y, v) in self.r.cells:
            # the way out to somewhere at another height (a step, a landing) or outside
            if any((u + du, y, v + dv) not in self.r.cells and self.g.clear(u + du, y, v + dv) for du, dv in VEC.values()):
                keep.add((u, y, v))
            # a way through no more than two wide (a doorway with no door, a narrow passage)
            for a, b_ in (("east", "west"), ("north", "south")):
                if all(any(_wall(self.g.get(u + VEC[d][0] * k, y, v + VEC[d][1] * k)) for k in (1, 2)) for d in (a, b_)):
                    keep.add((u, y, v))
            for du, dv in list(VEC.values()) + [(1, 1), (1, -1), (-1, 1), (-1, -1)]:
                for dy in (-1, 0, 1, 2):
                    blk = self.g.get(u + du, y + dy, v + dv)
                    if blk is None:
                        continue
                    if blk.name.endswith(("_door", "_fence_gate", "_trapdoor")) and abs(du) + abs(dv) == 1 and dy in (0, 1):
                        keep.add((u, y, v))
                    if _stairs(blk) and (u + du, y + dy, v + dv) not in self.b.roof_cells and dy in (-1, 0, 1):
                        keep.add((u, y, v))
            for d, (du, dv) in VEC.items():
                blk = self.g.get(u + du, y, v + dv)
                if blk is None or not (blk.name.startswith("ptmuk:") or blk.name.endswith("_bed")):
                    continue
                f = blk.props.get("facing")
                if blk.name.endswith("_bed"):
                    if blk.props.get("part") == "foot":
                        keep.add((u, y, v))                  # get into bed from the side or the end
                elif f in VEC and VEC[f] == (-du, -dv):
                    keep.add((u, y, v))                      # in front of it
        return keep

    def _connected(self, free):
        if not free:
            return True
        start = next(iter(free))
        seen, todo = {start}, [start]
        while todo:
            u, y, v = todo.pop()
            for du, dv in VEC.values():
                n = (u + du, y, v + dv)
                if n in free and n not in seen:
                    seen.add(n)
                    todo.append(n)
        return len(seen) == len(free)

    def _can_take(self, c):
        """Whether the floor cell c can be given up without cutting the room in two."""
        return c in self.free and c not in self.keep and self._connected(self.free - {c})

    def _walls(self, c, dy=0):
        """The ways from cell c to a wall at height y + dy."""
        u, y, v = c
        return [d for d, (du, dv) in VEC.items() if _wall(self.g.get(u + du, y + dy, v + dv))]

    def _window(self, c, d, dy):
        u, y, v = c
        blk = self.g.get(u + VEC[d][0], y + dy, v + VEC[d][1])
        return blk is not None and pc.is_pane(blk)

    def _empty(self, u, y, v):
        blk = self.g.get(u, y, v)
        return blk is not None and blk.name in ("minecraft:air", "minecraft:light")

    def _put(self, c, name, floor=True, **props):
        u, y, v = c
        self.b.set(u, y, v, home(name, lit=False, **props) if "lit" not in props else home(name, **props))
        if floor:
            self.free.discard((u, self.y, v))

    def _anchors(self, words):
        """Where the furniture whose names have one of words stands, by this room."""
        out = []
        for (u, y, v) in self.r.cells:
            for du, dv in VEC.values():
                blk = self.g.get(u + du, y, v + dv)
                if blk is not None and any(w in blk.name for w in words):
                    out.append((u + du, v + dv))
        return out

    def _spots(self, tall=False, under_window=True, corner=False, near=None, reach=4):
        """Free floor cells against a wall, with the way they'd face (back to the wall); only within
        reach of the furniture named by near (rooms opening into each other are one room here)."""
        out = []
        anchors = self._anchors(near) if near else None
        for c in sorted(self.free):
            if c in self.keep:
                continue
            if anchors is not None and not any(abs(c[0] - a[0]) + abs(c[2] - a[1]) <= reach for a in anchors):
                continue
            walls = self._walls(c)
            if corner and len(walls) < 2:
                continue
            for d in walls:
                if tall and (self._window(c, d, 1) or not self._empty(c[0], c[1] + 1, c[2])):
                    continue
                if not under_window and self._window(c, d, 1):
                    continue
                out.append((c, OPP[d]))
                break
        n = len(out)
        k = self.seed % n if n else 0
        return out[k:] + out[:k]                             # start somewhere different in each room

    def _place_floor(self, name, tall=False, under_window=True, corner=False, near=None, reach=4, **props):
        for c, f in self._spots(tall, under_window, corner, near, reach):
            if self._can_take(c):
                if tall:
                    self._put(c, name, facing=f, half="lower", **props)
                    self._put((c[0], c[1] + 1, c[2]), name, floor=False, facing=f, half="upper", **props)
                else:
                    self._put(c, name, facing=f, **props)
                return c, f
        return None

    def _has(self, *words):
        return any(w in n for n in self.r.names for w in words)

    # ------------------------------------------------------------------ every room

    def light(self):
        if self._has(*LIGHT_WORDS):
            return
        cells = sorted(self.r.cells)
        cu = sum(c[0] for c in cells) / len(cells)
        cv = sum(c[2] for c in cells) / len(cells)
        spots = []
        for (u, y, v) in cells:
            k = 2
            while k < 7 and self._empty(u, y + k, v):
                k += 1
            if k >= 3 and k < 7 and not self._empty(u, y + k, v) and pc.full_cube(self.g.get(u, y + k, v)):
                spots.append(((u - cu) ** 2 + (v - cv) ** 2, (u, y + k - 1, v)))
        spots.sort()
        placed = []
        for _, p in spots:
            if all(abs(p[0] - q[0]) + abs(p[2] - q[2]) >= 7 for q in placed):
                self._put(p, "pendant_light", floor=False, facing="north", lit=True)
                placed.append(p)
            if len(placed) > len(cells) // 40:
                break

    def curtains(self, name):
        """Curtains drawn back either side of each window, from the bottom of the glass up."""
        for c in sorted(self.r.cells):
            u, y, v = c
            for d, (du, dv) in VEC.items():
                dy = next((k for k in (1, 2) if self._window(c, d, k)), None)
                if dy is None:
                    continue
                for side in (CW[d], CCW[d]):
                    su, sv = u + VEC[side][0], v + VEC[side][1]
                    s = (su, y, sv)
                    if s not in self.r.cells or s in self.keep or self._window(s, d, dy):
                        continue
                    if not (_wall(self.g.get(su + du, y + dy, sv + dv)) and _wall(self.g.get(su + du, y + dy + 1, sv + dv))
                            and self._empty(su, y + dy, sv) and self._empty(su, y + dy + 1, sv)):
                        continue
                    if dy == 1 and not self._can_take(s):
                        continue                         # hanging that low it fills the space over the floor
                    self._put((su, y + dy, sv), name, floor=dy == 1, facing=OPP[d], half="lower")
                    self._put((su, y + dy + 1, sv), name, floor=False, facing=OPP[d], half="upper")

    def _wall_piece(self, name, dy=1, near=None, **props):
        """Something hung on a wall at height y + dy over a free floor cell (nearest `near` if given)."""
        spots = []
        for c in sorted(self.r.cells):
            if c in self.keep or (dy == 1 and c not in self.free):
                continue
            u, y, v = c
            if not self._empty(u, y + dy, v):
                continue
            for d in self._walls(c, dy):
                if dy == 1 and not _wall(self.g.get(u + VEC[d][0], y + 2, v + VEC[d][1])):
                    continue
                key = (abs(u - near[0]) + abs(v - near[2])) if near else 0
                spots.append((key, c, OPP[d]))
                break
        if not near:
            n = len(spots)
            k = self.seed % n if n else 0
            spots = spots[k:] + spots[:k]
        else:
            spots.sort()
        for _, c, f in spots:
            if dy >= 2 or self._can_take(c):
                self._put((c[0], c[1] + dy, c[2]), name, floor=dy == 1, facing=f, **props)
                return c
        return None

    def _picture(self, n=1):
        for i in range(n):
            self._wall_piece(PICTURES[(self.seed + i) % len(PICTURES)])

    def _above(self, words, name, **props):
        """Something standing on top of the first piece whose name has one of words (a lamp on a table)."""
        for c in sorted(self.r.cells):
            u, y, v = c
            for d, (du, dv) in VEC.items():
                blk = self.g.get(u + du, y, v + dv)
                if blk is None or not any(w in blk.name for w in words) or not self._empty(u + du, y + 1, v + dv):
                    continue
                self._put((u + du, y + 1, v + dv), name, floor=False, facing=blk.props.get("facing", OPP[d]), **props)
                return True
        return False

    # ------------------------------------------------------------------ by room

    def bedroom(self):
        fin = "white" if self._has("wardrobe_white") else "oak"
        lamp = False
        for c in sorted(self.r.cells):
            u, y, v = c
            for d, (du, dv) in VEC.items():
                bed = self.g.get(u + du, y, v + dv)
                if bed is None or not bed.name.endswith("_bed") or bed.props.get("part") != "head":
                    continue
                f = bed.props.get("facing")
                if f not in VEC or d not in (CW[f], CCW[f]):
                    continue
                if self._empty(u, y, v) and _wall(self.g.get(u + VEC[f][0], y, v + VEC[f][1])) and self._can_take(c):
                    self._put(c, f"bedside_table_{fin}", facing=OPP[f])
                    if not lamp and self._empty(u, y + 1, v):
                        self._put((u, y + 1, v), "table_lamp", floor=False, facing=OPP[f], level="low")
                        lamp = True
                    # a picture over the head of the bed
                    hu, hv = u + du, v + dv
                    if self._empty(hu, y + 1, hv) and _wall(self.g.get(hu + VEC[f][0], y + 1, hv + VEC[f][1])):
                        self._put((hu, y + 1, hv), PICTURES[self.seed % len(PICTURES)], floor=False, facing=OPP[f])
        if not self._has("chest_of_drawers"):
            self._place_floor(f"chest_of_drawers_{fin}", near=("_bed",))
        self._radiator()

    def bathroom(self):
        fittings = ("ptmuk:bath", "ptmuk:toilet", "ptmuk:shower", "ptmuk:basin")
        if not self._has("ptmuk:toilet"):
            self._place_floor("toilet", under_window=False, near=fittings, reach=3)
        basin = self._has("ptmuk:basin") or self._place_floor("basin", under_window=False, near=fittings, reach=3)
        if basin and not self._has("bathroom_cabinet", "mirror"):
            self._above(("ptmuk:basin",), "bathroom_cabinet")
        if not self._has("towel_radiator"):
            self._place_floor("towel_radiator", under_window=False, near=fittings, reach=3)

    def kitchen(self):
        if not self._has("washing_machine"):
            for c, f in self._spots():
                u, y, v = c
                nxt = [self.g.get(u + VEC[s][0], y, v + VEC[s][1]) for s in (CW[f], CCW[f])]
                if any(n is not None and n.name.startswith("ptmuk:kitchen_") and n.props.get("facing") == f for n in nxt) \
                        and self._can_take(c):
                    self._put(c, "washing_machine", facing=f)
                    break
        for name in ("kettle", "toaster", "microwave"):
            if not self._has(name):
                self._above(("kitchen_unit_", "kitchen_drawers_"), name, level="counter")
        if not self._has("wall_clock"):
            self._wall_piece("wall_clock", dy=2)

    def living(self):
        if not self._has("coffee_table"):
            self._coffee_table()
        sitting = ("ptmuk:sofa_", "ptmuk:fireplace", "ptmuk:tv")
        if not self._has("floor_lamp", "table_lamp"):
            self._place_floor("floor_lamp", tall=True, corner=True, near=sitting)
        if not self._has("houseplant"):
            self._place_floor("houseplant", corner=True, near=sitting)
        self._radiator()
        self._picture(2)

    def dining(self):
        if not self._has("chest_of_drawers"):
            self._place_floor("chest_of_drawers_oak", under_window=False, near=("dining_table",))
        if not self._has("houseplant"):
            self._place_floor("houseplant", corner=True, near=("dining_table",))
        self._picture()

    def hall(self):
        self._radiator(window=False)
        doors = [c for c in self.r.cells if self._front_door(c)]
        if doors and not self._has("coat_hooks"):
            self._wall_piece("coat_hooks", near=doors[0])
        self._picture()

    def garage(self):
        if not self._has("shelves_"):
            self._place_floor("shelves_diy", tall=True, under_window=False)
        if not self._has("tyre_stack"):
            self._place_floor("tyre_stack", corner=True)
        if not self._has("oil_drum"):
            self._place_floor("oil_drum")

    # ------------------------------------------------------------------ fitting out an empty floor

    def _ceiling(self, u, v):
        """How many blocks are clear over the floor at (u, v) (up to 8)."""
        k = 0
        while k < 8 and self._empty(u, self.y + k, v):
            k += 1
        return k

    def split(self, limit):
        """Cut the room into parts no bigger than about limit with partition walls up to the ceiling, the long
        way across first; each wall keeps a doorway, never runs through the way to a door or the stairs, and
        leaves both sides whole. Returns the parts, as rooms."""
        todo, out = [set(self.r.cells)], []
        while todo:
            cells = todo.pop()
            if len(cells) <= limit or not self._cut(cells, todo):
                room = Room(cells, self.y)
                out.append(room)
        return out

    def _cut(self, cells, todo):
        us, vs = [c[0] for c in cells], [c[2] for c in cells]
        across_u = max(us) - min(us) >= max(vs) - min(vs)          # a wall at one u, from front to back
        lo, hi = (min(us), max(us)) if across_u else (min(vs), max(vs))
        mid = (lo + hi) // 2
        for m in sorted(range(lo + 3, hi - 2), key=lambda m: abs(m - mid)):
            wall = sorted(c for c in cells if (c[0] if across_u else c[2]) == m)
            if not wall or any(c in self.keep for c in wall):
                continue
            a = {c for c in cells if (c[0] if across_u else c[2]) < m}
            z = {c for c in cells if (c[0] if across_u else c[2]) > m}
            if len(a) < 20 or len(z) < 20 or not self._connected(a) or not self._connected(z):
                continue
            # the doorway: the wall cell nearest its middle with floor both sides
            step = (1, 0, 0) if across_u else (0, 0, 1)
            doors = [c for c in wall if (c[0] - step[0], c[1], c[2] - step[2]) in a and (c[0] + step[0], c[1], c[2] + step[2]) in z]
            if not doors:
                continue
            door = doors[len(doors) // 2]
            for (u, y, v) in wall:
                for k in range(2 if (u, y, v) == door else 0, self._ceiling(u, v)):
                    self.b.set(u, y + k, v, "calcite")
            self.free -= set(wall) - {door}
            self.keep.add(door)
            todo += [a | {door}, z]
            return True
        return False

    # ------------------------------------------------------------------ flats

    def _entries(self, cells):
        """The cells of the room you come in by: next to a door, a stair or a landing outside it."""
        out = []
        for (u, y, v) in cells:
            for du, dv in VEC.values():
                n = (u + du, y, v + dv)
                if n in cells:
                    continue
                blk, below = self.g.get(*n), self.g.get(u + du, y - 1, v + dv)
                if blk is None:
                    continue
                if blk.name.endswith(("_door", "_fence_gate")) or _stairs(blk) or below is not None and _stairs(below) or \
                        self.g.clear(*n) and self.g.clear(u + du, y + 1, v + dv):
                    out.append((u, y, v))
                    break
        return out

    def _partition(self, c):
        """A partition wall up to the ceiling at floor cell c."""
        u, y, v = c
        for k in range(max(3, self._ceiling(u, v))):
            if self._empty(u, y + k, v):
                self.b.set(u, y + k, v, "calcite")

    def _door(self, c, into, wood="oak"):
        """A door in a partition at c, opening into the room the direction `into` lies."""
        u, y, v = c
        self.b.set(u, y, v, B(wood + "_door", facing=into, half="lower", hinge="left", open=False, powered=False))
        self.b.set(u, y + 1, v, B(wood + "_door", facing=into, half="upper", hinge="left", open=False, powered=False))
        for k in range(2, max(3, self._ceiling(u, v) + 2)):
            if self._empty(u, y + k, v):
                self.b.set(u, y + k, v, "calcite")

    def flats(self):
        """Cut a big empty floor into flats off a corridor from the stairs (see _corridor); a floor about
        one flat big is one flat. Returns the flats, as rooms."""
        cells = set(self.r.cells)
        if len(cells) <= FLAT + 20 or _doors(self.g, self.r) == 1 and len(cells) <= 2 * FLAT + 60:
            return [self.r]                                    # about a flat's worth, or behind its own front door
        done = self._corridor(cells)
        if done is None and len(cells) <= 3 * FLAT:
            return [self.r]                                    # one big flat beats flats you walk through
        return done if done is not None else self.split(FLAT)

    def _corridor(self, cells):
        """A corridor the long way down the floor (down the middle with flats both sides if it's deep, else
        along the side the stairs are nearer), a passage from each way in to it, and flats along it cut every
        few blocks, each with a front door onto it. None if the floor won't take it (the old way is used)."""
        us, vs = [c[0] for c in cells], [c[2] for c in cells]
        along_u = max(us) - min(us) >= max(vs) - min(vs)
        A = (lambda c: c[0]) if along_u else (lambda c: c[2])
        S = (lambda c: c[2]) if along_u else (lambda c: c[0])

        def at(a, s):
            return (a, self.y, s) if along_u else (s, self.y, a)
        entries = self._entries(cells)
        if not entries:
            return None
        s_lo, s_hi = min(S(c) for c in cells), max(S(c) for c in cells)
        depth = s_hi - s_lo + 1
        mean = sum(S(e) for e in entries) / len(entries)
        rows = [s_lo, s_hi] if mean - s_lo <= s_hi - mean else [s_hi, s_lo]    # along a side, near the way in
        if depth >= 13:                                                          # down the middle first if deep
            rows = sorted(range(s_lo + 6, s_hi - 5), key=lambda r: abs(r - (s_lo + s_hi) / 2)) + rows
        for r in rows:
            plan = self._try_corridor(cells, entries, r, A, S, at)
            if plan is not None:
                corridor, walls, flats = plan
                if len(flats["rooms"]) < 2:
                    return [self.r]                            # a corridor to just one flat: the floor is the flat
                for c in walls:
                    if c not in flats["doors"]:
                        self._partition(c)
                for c, into in flats["doors"].items():
                    self._door(c, into, "dark_oak")
                self.free -= corridor | walls
                return [Room(f, self.y) for f in flats["rooms"]]
        return None

    def _try_corridor(self, cells, entries, r, A, S, at):
        corridor = {c for c in cells if S(c) == r}
        if not corridor:
            return None
        # a passage from each way in that isn't on the corridor, the shortest way to it (round the stairs)
        passages = set()
        for e in entries:
            if S(e) == r:
                continue
            path = self._path(e, corridor, cells)
            if path is None:
                return None
            passages.update(path)
        hall = corridor | passages
        if not self._connected(hall):
            return None
        # walls between the hall and the flats either side
        walls = set()
        for c in hall:
            for du, dv in VEC.values():
                n = (c[0] + du, c[1], c[2] + dv)
                if n in cells and n not in hall:
                    walls.add(n)
        if walls & self.keep:
            return None                                        # it would block a doorway or the stairs
        rest = cells - hall - walls
        # the flats: each run of floor beside the corridor, cut across every few blocks
        rooms, doors, cut = [], {}, set()
        for comp in self._components(rest):
            span = sorted({A(c) for c in comp})
            d = max(S(c) for c in comp) - min(S(c) for c in comp) + 1
            length = max(5, round(FLAT / max(d, 1)))
            pieces, start = [], span[0]
            while start <= span[-1]:
                end = start + length - 1
                if span[-1] - end < 5:
                    end = span[-1]
                pieces.append((start, end))
                start = end + 2                                    # a block for the wall between
            # a scrap too small for a flat (beside the stairs, say) goes in with the one next to it
            size = lambda piece: sum(1 for c in comp if piece[0] <= A(c) <= piece[1])
            k = 0
            while len(pieces) > 1 and k < len(pieces):
                if size(pieces[k]) < 50:
                    j = k - 1 if k else 1
                    lo, hi = min(pieces[j][0], pieces[k][0]), max(pieces[j][1], pieces[k][1])
                    pieces[min(j, k)] = (lo, hi)
                    del pieces[max(j, k)]
                    k = 0
                    continue
                k += 1
            for k, (a0, a1) in enumerate(pieces):
                room = {c for c in comp if a0 <= A(c) <= a1}
                if k + 1 < len(pieces):
                    cut |= {c for c in comp if A(c) == a1 + 1}
                if len(room) < 40 or not self._connected(room):
                    return None
                # its front door: a wall cell between it and the hall, nearest its end by the stairs
                options = []
                for w in walls:
                    inside = [n for n in self._nbrs(w) if n in room]
                    outside_ = [n for n in self._nbrs(w) if n in hall]
                    if inside and outside_ and not any(n in doors for n in self._nbrs(w)):
                        di = (inside[0][0] - w[0], inside[0][2] - w[2])
                        into = next(k2 for k2, vv in VEC.items() if vv == di)
                        options.append((min(abs(A(w) - A(e)) + abs(S(w) - S(e)) for e in entries), w, into))
                if not options:
                    return None
                options.sort()
                _, w, into = options[0]
                doors[w] = into
                rooms.append(room)
        walls |= cut
        return hall, walls, {"rooms": rooms, "doors": doors}

    def _path(self, start, goal, cells):
        """The cells from start up to (not into) goal, the shortest way through cells; None if there's none."""
        back, todo = {start: None}, [start]
        for c in todo:
            if c in goal:
                path = []
                c = back[c]
                while c is not None:
                    path.append(c)
                    c = back[c]
                return path
            for n in self._nbrs(c):
                if n in cells and n not in back:
                    back[n] = c
                    todo.append(n)
        return None

    def _nbrs(self, c):
        return [(c[0] + du, c[1], c[2] + dv) for du, dv in VEC.values()]

    def _components(self, cells):
        out, done = [], set()
        for p in sorted(cells):
            if p in done:
                continue
            comp, todo = {p}, [p]
            done.add(p)
            while todo:
                c = todo.pop()
                for n in self._nbrs(c):
                    if n in cells and n not in done:
                        done.add(n)
                        comp.add(n)
                        todo.append(n)
            out.append(comp)
        return out

    def _enclose(self, w, d, want_window, min_rest=12):
        """Wall off a w x d room in a corner of this one (or along a wall), with a door into it; the corner
        where the rest stays in one piece, with a window if want_window (a bedroom) or without (a bathroom).
        Returns the new room, or None."""
        cells = set(self.r.cells)
        us, vs = [c[0] for c in cells], [c[2] for c in cells]
        u0, u1, v0, v1 = min(us), max(us), min(vs), max(vs)
        y = self.y
        best = None
        for (ou, ov, su, sv) in ((u0, v0, 1, 1), (u1, v0, -1, 1), (u0, v1, 1, -1), (u1, v1, -1, -1)):
            for iw, idp in ((w, d), (d, w)):
                rect = {(ou + su * i, y, ov + sv * j) for i in range(iw) for j in range(idp)}
                wall = {(ou + su * iw, y, ov + sv * j) for j in range(idp + 1)} | \
                       {(ou + su * i, y, ov + sv * idp) for i in range(iw + 1)}
                wall &= cells
                if not rect <= self.free or rect & self.keep or not wall <= self.free or wall & self.keep:
                    continue
                if not wall or any(not self._shut(n) for c in rect for n in self._nbrs(c) if n not in rect | wall):
                    continue                                   # the corner isn't walled on its other sides
                rest = self.free - rect - wall
                if len(rest) < min_rest:
                    continue
                windows = sum(1 for c in rect for dd in VEC if self._window(c, dd, 1))
                if want_window and not windows:
                    continue
                # a partition ending against glass would cut a window in two
                cuts = sum(1 for c in wall for dd in VEC
                           if (c[0] + VEC[dd][0], y, c[2] + VEC[dd][1]) not in cells and self._window(c, dd, 1))
                for dc in sorted(wall):
                    inside = [n for n in self._nbrs(dc) if n in rect]
                    outer = [n for n in self._nbrs(dc) if n in rest]
                    if not inside or not outer or not self._connected(rest | {dc}) or not self._connected(rect):
                        continue
                    # a door in the middle of a side leaves room for things round the walls either side of it
                    middle = sum(1 for n in self._nbrs(inside[0]) if n in rect)
                    score = cuts * 10 + (0 if want_window else windows) - 2 * middle
                    if best is None or score < best[0]:
                        di = (inside[0][0] - dc[0], inside[0][2] - dc[2])
                        best = (score, rect, wall, dc, next(k for k, vv in VEC.items() if vv == di), outer[0])
                    break
        if best is None:
            return None
        _, rect, wall, dc, into, outer = best
        for c in wall:
            if c != dc:
                self._partition(c)
        self._door(dc, into)
        self.free -= rect | wall
        self.keep.add(outer)                                   # the way to the door stays clear
        return Room(rect, y)

    def _shut(self, c):
        """Whether c is wall or glass (not floor, furniture or a doorway)."""
        blk = self.g.get(*c)
        return blk is not None and (pc.is_pane(blk) or pc.full_cube(blk))

    def fit_flat(self):
        """A flat: a bathroom and (room permitting) a bedroom walled off with doors, then a kitchen along a wall
        and a sofa in the rest (with the bed there too if there's no bedroom)."""
        placed = []

        def far(options, runs=True):
            def gap(o):
                c = o[0][0] if runs else o[0]
                return -min((abs(c[0] - p[0]) + abs(c[2] - p[2]) for p in placed), default=0)
            return sorted(options, key=gap)

        bath = (self._enclose(3, 2, want_window=False) or self._enclose(2, 2, want_window=False)) \
            if len(self.free) >= 30 else None
        if bath is not None:
            Dresser(self.b, self.g, self.out, bath, self.seed).fit_bathroom()
            placed += list(bath.cells)
        bed = None
        if len(self.free) >= 48:
            bed = self._enclose(4, 3, want_window=True) or self._enclose(3, 3, want_window=True)
        if bed is not None:
            Dresser(self.b, self.g, self.out, bed, self.seed).fit_bedroom()
            placed += list(bed.cells)
            for _ in range(2):                                 # a big flat: another bedroom or two
                more = self._enclose(4, 3, want_window=True) if len(self.free) >= 90 else None
                if more is None:
                    break
                Dresser(self.b, self.g, self.out, more, self.seed + 1).fit_bedroom()
                placed += list(more.cells)
        run = self._run(4) or self._run(3)
        if run:
            cells, f = run
            PF.kitchen(self.b, [(c[0], c[2]) for c in cells], self.y, f, "white" if self.seed % 2 else "oak")
            self._took(cells, placed)
        run = next(iter(far(self._runs(3) or self._runs(2))), None)
        if run:
            cells, f = run
            colour = ("grey", "navy", "green", "brown", "cream")[self.seed % 5]
            for c in cells:
                self.b.set(*c, home(f"sofa_{colour}", facing=f, lit=False, shape="single"))
            self._took(cells, placed)
        if bed is None:
            self._bed(far(self._spots(), runs=False), placed)
        if bath is None:                                       # too small to wall off: a shower room corner at least
            run = next(iter(far(self._runs(2))), None)
            if run:
                cells, f = run
                self.b.set(*cells[0], home("toilet", facing=f, lit=False))
                self.b.set(*cells[1], home("basin", facing=f, lit=False))
                self._took(cells, placed)

    def _bed(self, spots, placed=None, double=False):
        for c, f in spots:
            foot = (c[0] + VEC[f][0], c[1], c[2] + VEC[f][1])
            if foot not in self.free or foot in self.keep or not self._can_take_all([c, foot]) or \
                    any(n in self.keep for n in self._nbrs(c) + self._nbrs(foot)):
                continue                                       # (not hard by a doorway either)
            bed = BEDS[self.seed % len(BEDS)]                  # its head to the wall
            pair = [(c, foot)]
            if double:
                side = VEC[CW[f]]
                c2, f2 = (c[0] + side[0], c[1], c[2] + side[1]), (foot[0] + side[0], c[1], foot[2] + side[1])
                if self._can_take_all([c, foot, c2, f2]) and _wall(self.g.get(c2[0] - VEC[f][0], c[1], c2[2] - VEC[f][1])):
                    pair.append((c2, f2))
            for h, ft in pair:
                self.b.set(*h, B(bed, facing=OPP[f], part="head", occupied=False))
                self.b.set(*ft, B(bed, facing=OPP[f], part="foot", occupied=False))
                self.free -= {h, ft}
                if placed is not None:
                    placed += [h, ft]
            return True
        return False

    def _inside_door(self):
        """The cells just inside this room's doors."""
        return {c for c in self.r.cells if any((n := self.g.get(*x)) is not None and n.name.endswith("_door")
                                               for x in self._nbrs(c))}

    def fit_bathroom(self):
        """A bath along a wall, a toilet and a basin against the walls, each reachable from the floor left
        clear inside the door (tried every way; the bath goes furthest from the door)."""
        cells = set(self.r.cells)
        entry = self._inside_door()
        walls = {c: [d for d in VEC if (c[0] + VEC[d][0], c[1], c[2] + VEC[d][1]) not in cells
                     and _wall(self.g.get(c[0] + VEC[d][0], c[1], c[2] + VEC[d][1]))] for c in cells}
        on_wall = sorted((c for c in cells if walls[c] and c not in entry),
                         key=lambda c: -min((abs(c[0] - e[0]) + abs(c[2] - e[2]) for e in entry), default=0))
        pairs = [(a, b_) for a in on_wall for b_ in on_wall if a < b_ and abs(a[0] - b_[0]) + abs(a[2] - b_[2]) == 1
                 and set(walls[a]) & set(walls[b_])]

        def ok(bath, taken):
            free = cells - set(bath) - set(taken)
            return entry <= free and self._connected(free) and all(any(n in free for n in self._nbrs(x)) for x in taken) \
                and (not bath or any(n in free for x in bath for n in self._nbrs(x)))
        for bath in pairs + [None]:
            used = list(bath or ())
            for t in on_wall:
                if t in used:
                    continue
                for bs in on_wall:
                    if bs in used or bs == t or not ok(used, [t, bs]):
                        continue
                    if bath:
                        PF.bath(self.b, [(c[0], c[2]) for c in bath], self.y)
                    self.b.set(*t, home("toilet", facing=OPP[walls[t][0]], lit=False))
                    self.b.set(*bs, home("basin", facing=OPP[walls[bs][0]], lit=False))
                    self.free -= set(used + [t, bs])
                    if not bath:                                # a shower instead, if one fits
                        for sh in on_wall:
                            if sh not in (t, bs) and self._empty(sh[0], sh[1] + 1, sh[2]) and ok((), [t, bs, sh]):
                                f = OPP[walls[sh][0]]
                                self.b.set(*sh, home("shower", facing=f, lit=False, half="lower"))
                                self.b.set(sh[0], sh[1] + 1, sh[2], home("shower", facing=f, lit=False, half="upper"))
                                self.free.discard(sh)
                                break
                    return

    def fit_bedroom(self):
        """A bed (a double if it fits) with its head to the wall, and a wardrobe; a small room only keeps the
        way in clear (the rule against blocking narrow passages would leave it empty)."""
        self.keep = self._inside_door()
        self._bed(self._spots(under_window=False) + self._spots(), double=True)
        self._place_floor("wardrobe_oak", tall=True, under_window=False)

    def office(self):
        """Desks along the walls, two apart, each with a computer and a chair facing it, and plants in corners."""
        desks, spots = [], []
        for c in sorted(self.free - self.keep):                # against a wall or a window (glass offices)
            for d, (du, dv) in VEC.items():
                blk = self.g.get(c[0] + du, c[1], c[2] + dv)
                if _wall(blk) or blk is not None and pc.is_pane(blk):
                    spots.append((c, OPP[d]))
                    break
        for c, f in spots:
            chair = (c[0] + VEC[f][0], c[1], c[2] + VEC[f][1])
            if len(desks) >= len(self.r.cells) // 12 or any(abs(c[0] - p[0]) + abs(c[2] - p[2]) < 2 for p in desks):
                continue
            if chair in self.free and chair not in self.keep and self._can_take_all([c, chair]) \
                    and self._empty(c[0], c[1] + 1, c[2]):
                self.b.set(*c, home("desk", facing=f, lit=False))
                self.b.set(c[0], c[1] + 1, c[2], home("computer", facing=f, lit=False, level="table"))
                self.b.set(*chair, home("office_chair", facing=OPP[f], lit=False))
                self.free -= {c, chair}
                desks.append(c)
        for _ in range(2):
            self._place_floor("houseplant", corner=True)

    def _took(self, cells, placed):
        for c in cells:
            self.free.discard(c)
            placed.append(c)

    def _can_take_all(self, cells):
        return all(c in self.free and c not in self.keep for c in cells) and self._connected(self.free - set(cells))

    def _runs(self, n):
        """Every row of n free cells side by side along a wall, all backing onto it, that can be given up together."""
        spots = dict(self._spots())
        out = []
        for c, f in sorted(spots.items()):
            side = VEC[CW[f]]
            cells = [(c[0] + side[0] * k, c[1], c[2] + side[1] * k) for k in range(n)]
            if all(spots.get(x) == f for x in cells) and self._can_take_all(cells):
                out.append((cells, f))
        return out

    def _run(self, n):
        runs = self._runs(n)
        return runs[self.seed % len(runs)] if runs else None

    # ------------------------------------------------------------------ helpers

    def _front_door(self, c):
        u, y, v = c
        for du, dv in VEC.values():
            blk = self.g.get(u + du, y, v + dv)
            if blk is not None and blk.name.endswith("_door") and ((u + 2 * du, y, v + 2 * dv) in self.out):
                return True
        return False

    def _radiator(self, window=True):
        if self._has("radiator"):
            return
        if window:
            for c in sorted(self.free):
                if c in self.keep:
                    continue
                for d in self._walls(c):
                    if self._window(c, d, 1) and self._can_take(c):
                        self._put(c, "radiator", facing=OPP[d])
                        return
        self._place_floor("radiator")

    def _coffee_table(self):
        for (u, y, v) in sorted(self.r.cells):
            for d, (du, dv) in VEC.items():
                s = self.g.get(u + du, y, v + dv)
                if s is None or not s.name.startswith("ptmuk:sofa_") or s.props.get("shape") not in ("middle", "single", "east"):
                    continue
                f = s.props.get("facing")
                if f not in VEC or VEC[f] != (-du, -dv):
                    continue
                # the cell in front of the sofa is kept clear to sit down; the table goes one further out
                c = (u - du, y, v - dv)
                if c in self.free and (u - 2 * du, y, v - 2 * dv) in self.free and self._can_take(c):
                    self._put(c, "coffee_table", facing=f)
                    return
