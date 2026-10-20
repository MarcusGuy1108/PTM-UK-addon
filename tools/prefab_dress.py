"""Dresses the rooms of the homes in every prefab (houses, and flats over shops and pubs) once
prefab_furnish has done its work. What a room has says what it is: a bed, a bedroom; a bath, toilet or
basin, a bathroom; kitchen units, a kitchen; a sofa, fire or TV, a living room; a dining table, a
dining room; a small room with nothing in it, a hall or landing. It gets what such a room is missing
along its free walls: bedside tables and a lamp, a chest of drawers, a basin, mirror cabinet and towel
rail, a washing machine, kettle, toaster and microwave, a coffee table, a lamp and a plant, a radiator
under the window, pictures, a clock, coat hooks by the front door, curtains at the windows and a light
in the ceiling if it has none. Nothing goes in front of a door, a piece of furniture or the stairs, or
anywhere that would cut a room in two. Shops, bars and public rooms keep what they have.

A big floor left empty (the storeys of a block of flats, rooms over a pub) is fitted out first: cut
into flat-sized rooms by partition walls, each wall with a doorway, and each room given a kitchen, a
sofa (with a TV across from it), a bed, and a toilet, basin and bath."""
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


def dress(b):
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
        if not r.tags and len(r.cells) >= EMPTY and (b.category in ("Flats", "Estates") or r.y > 2):
            for j, part in enumerate(Dresser(b, g, out, r, pick + i).split(FLAT)):
                Dresser(b, g, out, part, pick + i + j).fit_out()
            fitted = True
    if fitted:
        PF.joins(b)                                          # the new sofas' ends and middles
        PF.tvs(b)                                            # and a TV across from each
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
        fittings = ("ptmuk:bath", "ptmuk:toilet", "ptmuk:shower")
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

    def fit_out(self):
        """An empty room made a home: a kitchen along a wall, a sofa away from it, a bed away from both, and a
        toilet, basin and bath, each where it leaves the way through clear."""
        placed = []

        def far(options, runs=True):
            """The options furthest from what's been placed first (a run by its first cell)."""
            def gap(o):
                c = o[0][0] if runs else o[0]
                return -min((abs(c[0] - p[0]) + abs(c[2] - p[2]) for p in placed), default=0)
            return sorted(options, key=gap)

        run = self._run(4) or self._run(3)
        if run:
            cells, f = run
            PF.kitchen(self.b, [(c[0], c[2]) for c in cells], self.y, f, "white" if self.seed % 2 else "oak")
            self._took(cells, placed)
        run = next(iter(far(self._runs(2))), None)
        if run:
            cells, f = run
            colour = ("grey", "navy", "green", "brown", "cream")[self.seed % 5]
            for c in cells:
                self.b.set(*c, home(f"sofa_{colour}", facing=f, lit=False, shape="single"))
            self._took(cells, placed)
        for c, f in far(self._spots(), runs=False):
            foot = (c[0] + VEC[f][0], c[1], c[2] + VEC[f][1])
            if foot in self.free and foot not in self.keep and self._can_take_all([c, foot]):
                bed = BEDS[self.seed % len(BEDS)]                  # its head to the wall
                self.b.set(*c, B(bed, facing=OPP[f], part="head", occupied=False))
                self.b.set(*foot, B(bed, facing=OPP[f], part="foot", occupied=False))
                self._took([c, foot], placed)
                break
        run = next(iter(far(self._runs(3))), None) or next(iter(far(self._runs(2))), None)
        if run:
            cells, f = run
            self.b.set(*cells[0], home("toilet", facing=f, lit=False))
            self.b.set(*cells[1], home("basin", facing=f, lit=False))
            if len(cells) > 2:
                self.b.set(*cells[2], home("towel_radiator", facing=f, lit=False))
            self._took(cells, placed)
            bath = next(iter(sorted(self._runs(2), key=lambda r: abs(r[0][0][0] - cells[0][0]) + abs(r[0][0][2] - cells[0][2]))),
                        None)
            if bath and abs(bath[0][0][0] - cells[0][0]) + abs(bath[0][0][2] - cells[0][2]) <= 4:
                PF.bath(self.b, [(c[0], c[2]) for c in bath[0]], self.y)
                self._took(bath[0], placed)

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
