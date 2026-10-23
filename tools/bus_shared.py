"""Fixes shared by the bus generators (ALX400, Enviro400, OmniCity, UK Double Decker).

bleed(): each painted region in an atlas sits in a transparent 2 px gutter. The pixels at a
face's edge can sample just past its region, and with cutout rendering a transparent texel there
is a hole: the edges of panels and door frames showed flickering see-through hairlines. Copying
each region's edge pixels one texel out into the gutter makes such samples land on the same colour.

wheel_wells(): the wheel arches were open to the inside of the bus, so the floor, raised floors,
seat legs and the stairs showed round the tyres, and some of them ran through the tyres. Each
wheel now sits in a closed well: a black box from the inside of the side panel to beyond the
tyre, a little longer and taller than the arch. Everything static inside the box is cut away
first (cubes are split, keeping their textures), then the box's walls and roof are added.
"""
import math

from PIL import Image

WELL_LENGTH = 0.12        # well half-length beyond the tyre radius (the arch is radius + 0.1)
WELL_HEADROOM = 0.12      # roof above the top of the tyre
WELL_BOTTOM = 0.15
WALL = 0.03               # plate thickness
SLIVER = 0.01             # px: pieces thinner than this are rounding, not geometry
REACH = {"front": 0.345 + 0.08, "rear": 0.61 + 0.03}   # well depth from the side: tyre's inner face plus room to steer
# bones that move or are drawn separately: never cut
KEEP = ("Wheel", "Door", "Right", "Left", "Blinkers", "Lights", "TurnSignal", "BusStopping", "Mirror", "Display",
        "wiper", "Plate", "FrontID", "SteeringWheel")


def bleed(img: Image.Image, regions):
    """Copy every region's edge rows and columns one texel out into its gutter."""
    px = img.load()
    W, H = img.size
    for x, y, w, h in regions.values():
        for i in range(w):
            if y > 0:
                px[x + i, y - 1] = px[x + i, y]
            if y + h < H:
                px[x + i, y + h] = px[x + i, y + h - 1]
        for j in range(-1, h + 1):
            yy = min(max(y + j, y), y + h - 1)
            if 0 <= y + j < H:
                if x > 0:
                    px[x - 1, y + j] = px[x, yy]
                if x + w < W:
                    px[x + w, y + j] = px[x + w - 1, yy]


def _keep(bone_name, parents):
    n = bone_name
    while n:
        if any(k in n for k in KEEP):
            return True
        n = parents.get(n)
    return False


# which way each face's texture u and v run, as (axis, +1 grows with it / -1 against it); x, y, z
# are model axes. Taken from PTM2's GeckoLib (GeoQuad.build and VertexSet): faces are named the
# JSON way, where "west" is the face at max x
UV_AXES = {"north": ((0, 1), (1, -1)), "south": ((0, -1), (1, -1)), "west": ((2, 1), (1, -1)),
           "east": ((2, -1), (1, -1)), "up": ((0, 1), (2, -1)), "down": ((0, 1), (2, 1))}
FACE_AT = {"north": (2, 0), "south": (2, 1), "east": (0, 0), "west": (0, 1), "down": (1, 0), "up": (1, 1)}


def _piece(c, lo, hi, keep_all=False):
    """Cube c cut down to lo..hi (px): faces on the old boundary (or all, with keep_all) keep their
    texture, cropped to the piece."""
    olo = c["origin"]
    ohi = [olo[i] + c["size"][i] for i in range(3)]
    if any(hi[i] - lo[i] < SLIVER for i in range(3)):
        return None                               # rounding left a skin on the cut: it would fight its neighbour
    uv = {}
    for f, spec in c["uv"].items():
        ax, side = FACE_AT[f]
        if not keep_all and abs((hi[ax] if side else lo[ax]) - (ohi[ax] if side else olo[ax])) > SLIVER:
            continue                              # a new cut face: it faces into the well
        u, v = spec["uv"]
        su, sv = spec["uv_size"]
        out = []
        for (axis, sense), start, size in zip(UV_AXES[f], (u, v), (su, sv)):
            ext = ohi[axis] - olo[axis]
            a0, a1 = (lo[axis] - olo[axis]) / ext, (hi[axis] - olo[axis]) / ext
            if sense < 0:
                a0, a1 = 1 - a1, 1 - a0
            out.append((start + a0 * size, (a1 - a0) * size))
        uv[f] = {"uv": [round(out[0][0], 4), round(out[1][0], 4)], "uv_size": [round(out[0][1], 4), round(out[1][1], 4)]}
    if not uv:
        return None
    return {"origin": [round(v, 4) for v in lo], "size": [round(hi[i] - lo[i], 4) for i in range(3)], "uv": uv}


def carve(bones, box):
    """Remove the parts of every static cube inside box ((lo, hi) in px); returns rotated cubes it had to leave."""
    blo, bhi = box
    parents = {b.name: b.parent for b in bones.values()}
    left = []
    for b in bones.values():
        if _keep(b.name, parents):
            continue
        out = []
        for c in b.cubes:
            lo = list(c["origin"])
            hi = [lo[i] + c["size"][i] for i in range(3)]
            if any(hi[i] <= blo[i] + SLIVER or lo[i] >= bhi[i] - SLIVER for i in range(3)):
                out.append(c)
                continue
            if c.get("rotation"):
                left.append((b.name, lo, hi))
                out.append(c)
                continue
            cur_lo, cur_hi = lo[:], hi[:]
            for i in range(3):
                if cur_lo[i] < blo[i] - SLIVER:
                    p_hi = cur_hi[:]
                    p_hi[i] = blo[i]
                    out.append(_piece(c, cur_lo[:], p_hi))
                    cur_lo[i] = blo[i]
                if cur_hi[i] > bhi[i] + SLIVER:
                    p_lo = cur_lo[:]
                    p_lo[i] = bhi[i]
                    out.append(_piece(c, p_lo, cur_hi[:]))
                    cur_hi[i] = bhi[i]
        b.cubes = [c for c in out if c]
    return left


def _model_corners(c):
    """A cube's 8 corners in model px, cube rotation applied. PTM2's GeckoLib mirrors x and turns
    cubes by (-rx, -ry, rz) in its own frame, which in model space is (-rx, ry, -rz)."""
    o, s = c["origin"], c["size"]
    pts = [[o[0] + dx * s[0], o[1] + dy * s[1], o[2] + dz * s[2]] for dx in (0, 1) for dy in (0, 1) for dz in (0, 1)]
    r = c.get("rotation")
    if not r or not any(r):
        return pts
    pv = c.get("pivot", [0, 0, 0])
    ax, ay, az = math.radians(-r[0]), math.radians(r[1]), math.radians(-r[2])
    out = []
    for p in pts:
        x, y, z = p[0] - pv[0], p[1] - pv[1], p[2] - pv[2]
        y, z = y * math.cos(ax) - z * math.sin(ax), y * math.sin(ax) + z * math.cos(ax)
        x, z = x * math.cos(ay) + z * math.sin(ay), -x * math.sin(ay) + z * math.cos(ay)
        x, y = x * math.cos(az) - y * math.sin(az), x * math.sin(az) + y * math.cos(az)
        out.append([x + pv[0], y + pv[1], z + pv[2]])
    return out


def _hull(points):
    pts = sorted(set(points))
    if len(pts) < 3:
        return pts

    def cross(o, a, b):
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])
    lower, upper = [], []
    for q in pts:
        while len(lower) >= 2 and cross(lower[-2], lower[-1], q) <= 0:
            lower.pop()
        lower.append(q)
    for q in reversed(pts):
        while len(upper) >= 2 and cross(upper[-2], upper[-1], q) <= 0:
            upper.pop()
        upper.append(q)
    return lower[:-1] + upper[:-1]


def _beyond(h, q):
    """How far plan point q = (x, z) lies outside convex hull h (negative: inside)."""
    d = -1e9
    for i in range(len(h)):
        a, b = h[i], h[(i + 1) % len(h)]
        ex, ez = b[0] - a[0], b[1] - a[1]
        d = max(d, ((q[0] - a[0]) * ez - (q[1] - a[1]) * ex) / math.hypot(ex, ez))
    return d


def trim_to_body(m, inset):
    """Parts that poked out through the rounded corners of the body (the UK Double Decker's deck
    floors, the dashboards) are cut back inside it: in plan, each is cut into strips 5 cm long and
    each strip narrowed until it sits inset (m) inside the bodywork at that height."""
    px = m.PX
    body = [_model_corners(c) for c in m.BONES["Body"].cubes]
    parents = {b.name: b.parent for b in m.BONES.values()}
    hulls = {}

    def hull_for(y0, y1):
        key = (round(y0, 1), round(y1, 1))
        if key not in hulls:
            hulls[key] = _hull([(p[0], p[2]) for cs in body if max(q[1] for q in cs) > y0 + 0.1 and min(q[1] for q in cs) < y1 - 0.1
                                for p in cs])
        return hulls[key]

    def fits(h, x0, x1, z0, z1, margin):
        return all(_beyond(h, (x, z)) <= -margin for x in (x0, x1) for z in (z0, z1))
    for b in m.BONES.values():
        if b.name == "Body" or _keep(b.name, parents):
            continue
        out = []
        for c in b.cubes:
            lo = list(c["origin"])
            hi = [lo[i] + c["size"][i] for i in range(3)]
            h = hull_for(lo[1], hi[1])
            if c.get("rotation") or len(h) < 3 or fits(h, lo[0], hi[0], lo[2], hi[2], -0.2 * px / 100):
                out.append(c)
                continue
            n = max(1, int(math.ceil((hi[0] - lo[0]) / (0.05 * px))))
            strips = []
            for k in range(n):
                x0, x1 = lo[0] + (hi[0] - lo[0]) * k / n, lo[0] + (hi[0] - lo[0]) * (k + 1) / n
                z0, z1 = lo[2], hi[2]
                if not fits(h, x0, x1, z0, z1, -0.2 * px / 100):
                    for _ in range(80):                 # narrow from whichever side is outside
                        if fits(h, x0, x1, z0, z1, inset * px) or z1 - z0 < SLIVER:
                            break
                        if max(_beyond(h, (x, z1)) for x in (x0, x1)) > max(_beyond(h, (x, z0)) for x in (x0, x1)):
                            z1 -= 0.005 * px
                        else:
                            z0 += 0.005 * px
                    if z1 - z0 < SLIVER:
                        continue
                if strips and strips[-1][1] == x0 and strips[-1][2:] == (z0, z1):
                    strips[-1] = (strips[-1][0], x1, z0, z1)
                else:
                    strips.append((x0, x1, z0, z1))
            for x0, x1, z0, z1 in strips:
                out.append(_piece(c, [x0, lo[1], z0], [x1, hi[1], z1], keep_all=True))
        b.cubes = [c for c in out if c]


def wheel_wells(m, wall_t):
    """m: a bus generator module; wall_t: its side panel thickness (m)."""
    px = m.PX
    r = m.WHEEL_R
    L, top = r + WELL_LENGTH, 2 * r + WELL_HEADROOM
    for kind, ax in (("front", m.FRONT_AXLE), ("rear", m.REAR_AXLE)):
        for side in (-1, 1):
            edge = (m.ZN + wall_t) if side < 0 else (m.ZO - wall_t)
            inner = (m.ZN + REACH[kind]) if side < 0 else (m.ZO - REACH[kind])
            # everything inside the well, its walls included, goes first
            za, zb = sorted((edge, inner - side * WALL))
            lo = [(ax - L - WALL) * px, WELL_BOTTOM * px, za * px]
            hi = [(ax + L + WALL) * px, (top + WALL) * px, zb * px]
            for name, lo_, hi_ in carve(m.BONES, (lo, hi)):
                print(f"    wheel well: left rotated cube in {name} at {[round(v / px, 2) for v in lo_]}")
            # the walls: black inside the well, the bus's dark panelling on the side facing the saloon
            in_face = "south" if side < 0 else "north"
            dark = {f: "black" for f in m.ALL}
            za, zb = sorted((edge, inner))
            wa, wb = sorted((inner, inner - side * WALL))
            m.cube("WheelWells", (ax - L - WALL, WELL_BOTTOM, wa), (ax + L + WALL, top + WALL, wb), {**dark, in_face: "wall_dark"})
            m.cube("WheelWells", (ax + L, WELL_BOTTOM, za), (ax + L + WALL, top, zb), {**dark, "east": "wall_dark"})
            m.cube("WheelWells", (ax - L - WALL, WELL_BOTTOM, za), (ax - L, top, zb), {**dark, "west": "wall_dark"})
            m.cube("WheelWells", (ax - L, top, za), (ax + L, top + WALL, zb), {**dark, "up": "wall_dark"})
