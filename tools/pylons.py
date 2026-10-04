"""Transmission towers built from real blocks (so Distant Horizons can show them in its LODs).

Each tower is described as members (rods) in block units, origin at the tower's base centre,
the line running north-south (z) and the cross-arms along x. Every rod becomes model elements:
axis-aligned, or rotated 22.5/45 degrees in a principal plane (the only rotations block models
allow), or failing that a chain of small boxes. Elements are grouped into the block cell their
centre falls in; each occupied cell becomes one state ("cell") of the tower's block.

Outputs per tower: the cell models and blockstate, data/ptmuk/pylons/<type>.json (cell offsets
and conductor attachment points, read by the Java side), and an item icon for the builder.
"""
import json
import math

from PIL import Image, ImageDraw
import numpy as np

G = None
PX = 16.0
ALLOWED = (-45.0, -22.5, 0.0, 22.5, 45.0)


# ----------------------------------------------------------------- rods -> elements

class Tower:
    def __init__(self, name):
        self.name = name
        self.elements = []        # (element, centre in px)
        self.attach = []          # conductor attachment points: [x, y, z, bundle, kind]

    def rod(self, p0, p1, t, tex="#steel"):
        p0 = np.array(p0, dtype=float) * PX
        p1 = np.array(p1, dtype=float) * PX
        tp = t * PX
        d = p1 - p0
        length = float(np.linalg.norm(d))
        if length < 0.05:
            return
        zeros = [abs(c) < 1e-6 for c in d]
        if sum(zeros) >= 2:
            lo, hi = np.minimum(p0, p1), np.maximum(p0, p1)
            for i in range(3):
                if zeros[i]:
                    lo[i] -= tp / 2
                    hi[i] += tp / 2
            self._split_axis(lo, hi, tex)
            return
        plan = self._plane_rotation(d) if sum(zeros) == 1 else None
        if plan is None:
            self._steps(p0, p1, tp, tex)
            return
        long_axis, rot_axis, angle = plan
        pieces = max(1, math.ceil(length / 28.0))
        for k in range(pieces):
            a = p0 + d * (k / pieces)
            b = p0 + d * ((k + 1) / pieces)
            c = (a + b) / 2
            seg = length / pieces + (0.4 if pieces > 1 else 0)
            half = np.full(3, tp / 2)
            half[long_axis] = seg / 2
            el = G.box(tuple(c - half), tuple(c + half), tex)
            el["rotation"] = {"angle": angle, "axis": "xyz"[rot_axis], "origin": [round(v, 3) for v in c], "rescale": False}
            self.elements.append((el, c))

    @staticmethod
    def _plane_rotation(d):
        """Find (long axis, rotation axis, angle) so a box along the long axis, rotated, points along d."""
        dx, dy, dz = d
        options = []
        if abs(dz) < 1e-6:      # x-y plane, rotate about z: (1,0,0)->(cos,sin), (0,1,0)->(-sin,cos)
            options += [(0, 2, math.degrees(math.atan2(dy, dx))), (1, 2, math.degrees(math.atan2(-dx, dy)))]
        if abs(dx) < 1e-6:      # y-z plane, rotate about x: (0,0,1)->(-sin,cos) in (y,z); (0,1,0)->(cos,sin)
            options += [(2, 0, math.degrees(math.atan2(-dy, dz))), (1, 0, math.degrees(math.atan2(dz, dy)))]
        if abs(dy) < 1e-6:      # x-z plane, rotate about y: (1,0,0)->(cos,-sin) in (x,z); (0,0,1)->(sin,cos)
            options += [(0, 1, math.degrees(math.atan2(-dz, dx))), (2, 1, math.degrees(math.atan2(dx, dz)))]
        for long_axis, rot_axis, ang in options:
            # a rod has no direction: angles 180 apart are the same rod
            for a in (ang, ang - 180, ang + 180):
                for allowed in ALLOWED:
                    if abs(a - allowed) < 1.0:
                        return long_axis, rot_axis, allowed
        return None

    def _split_axis(self, lo, hi, tex):
        """Axis-aligned box, split so each piece fits in one cell's -16..32 range."""
        ranges = []
        for i in range(3):
            n = max(1, math.ceil((hi[i] - lo[i]) / 28.0))
            edges = [lo[i] + (hi[i] - lo[i]) * k / n for k in range(n + 1)]
            ranges.append(list(zip(edges[:-1], edges[1:])))
        for xr in ranges[0]:
            for yr in ranges[1]:
                for zr in ranges[2]:
                    a = np.array([xr[0], yr[0], zr[0]])
                    b = np.array([xr[1], yr[1], zr[1]])
                    self.elements.append((G.box(tuple(a), tuple(b), tex), (a + b) / 2))

    def _steps(self, p0, p1, tp, tex):
        d = p1 - p0
        n = max(1, math.ceil(float(np.linalg.norm(d)) / max(tp, 1.5)))
        for k in range(n):
            c = p0 + d * ((k + 0.5) / n)
            half = np.full(3, tp / 2) + np.abs(d) / n / 2
            self._split_axis(c - half, c + half, tex)

    def block(self, lo, hi, tex):
        self._split_axis(np.array(lo, float) * PX, np.array(hi, float) * PX, tex)

    def column(self, x, z, r, y0, y1, tex):
        """Round column (16-sided) from y0 to y1, in block units."""
        for yy in range(math.floor(y0), math.ceil(y1)):
            a, b = max(y0, yy), min(y1, yy + 1)
            if b - a < 1e-3:
                continue
            for el in G.round_column(r * PX, (a - yy) * PX, (b - yy) * PX, tex, top=(b == y1), bottom=(a == y0)):
                el = json.loads(json.dumps(el))
                off = np.array([x * PX - 8, yy * PX, z * PX - 8])
                for k in ("from", "to"):
                    el[k] = [round(el[k][i] + off[i], 3) for i in range(3)]
                if "rotation" in el:
                    el["rotation"]["origin"] = [round(el["rotation"]["origin"][i] + off[i], 3) for i in range(3)]
                c = (np.array(el["from"]) + np.array(el["to"])) / 2
                self.elements.append((el, c))

    # ------------------------------------------------------------- parts

    def insulator_string(self, top, length, axis=1, sign=-1, glass=True):
        """String of discs from top along an axis; returns the far end."""
        top = np.array(top, float)
        end = top.copy()
        end[axis] += sign * length
        self.rod(top, end, 0.06, "#steel")
        n = max(3, int(length / 0.22))
        for k in range(1, n):
            c = top.copy()
            c[axis] += sign * length * k / n
            r = 0.13
            lo, hi = c - r, c + r
            lo[axis], hi[axis] = c[axis] - 0.035, c[axis] + 0.035
            self.block(lo, hi, "#glass" if glass else "#porcelain")
        return end

    def attach_point(self, p, bundle, kind="phase"):
        self.attach.append([round(float(v), 3) for v in p] + [bundle, kind])


def lattice_body(t, profile, leg0=0.32, leg1=0.18, brace=0.1, top=None):
    """Square lattice tower. profile: [(y, half_width)] breakpoints, linear between them.
    Panels are sized so the X-bracing is at exactly 45 degrees."""
    ys = [p[0] for p in profile]
    top = top if top is not None else ys[-1]

    def w(y):
        return float(np.interp(y, ys, [p[1] for p in profile]))

    y = 0.0
    panels = []
    while y < top - 0.2:
        wk = w(y)
        h = 2 * wk
        for _ in range(8):            # solve h = 2 * w(y + h/2)
            h = 2 * w(y + h / 2)
        h = min(h, top - y)
        panels.append((y, h, w(y + h / 2)))
        y += h
    for (y0, h, wm) in panels:
        frac = y0 / top
        leg = leg0 + (leg1 - leg0) * frac
        for sx in (-1, 1):
            for sz in (-1, 1):
                t.rod((sx * wm, y0, sz * wm), (sx * wm, y0 + h, sz * wm), leg)
        # struts at the panel bottom and X braces on each face
        for s in (-1, 1):
            t.rod((-wm, y0, s * wm), (wm, y0, s * wm), brace * 1.2)
            t.rod((s * wm, y0, -wm), (s * wm, y0, wm), brace * 1.2)
            if wm > 0.3 and abs(h - 2 * wm) < 0.05:
                t.rod((-wm, y0, s * wm), (wm, y0 + h, s * wm), brace)
                t.rod((wm, y0, s * wm), (-wm, y0 + h, s * wm), brace)
                t.rod((s * wm, y0, -wm), (s * wm, y0 + h, wm), brace)
                t.rod((s * wm, y0, wm), (s * wm, y0 + h, -wm), brace)
        # plan bracing every few panels keeps it looking solid from above
    last_y, last_h, last_w = panels[-1]
    for s in (-1, 1):
        t.rod((-last_w, last_y + last_h, s * last_w), (last_w, last_y + last_h, s * last_w), brace * 1.2)
        t.rod((s * last_w, last_y + last_h, -last_w), (s * last_w, last_y + last_h, last_w), brace * 1.2)
    # foundations: concrete chimneys at the four legs
    w0 = panels[0][2]
    for sx in (-1, 1):
        for sz in (-1, 1):
            t.block((sx * w0 - 0.45, -0.2, sz * w0 - 0.45), (sx * w0 + 0.45, 0.35, sz * w0 + 0.45), "#concrete")
    return w


def cross_arm(t, y, root_w, length, depth, side, brace=0.09, chord=0.14):
    """Lattice cross-arm from the body face (x = side*root_w) out to the tip. The top chord
    slopes down at 22.5 degrees, so depth sets where it meets the bottom chords."""
    x0 = side * root_w
    tip = side * (root_w + length)
    zw = 0.7
    slope = math.tan(math.radians(22.5))
    run = min(length, depth / slope)
    top_root = y + run * slope
    for z in (-zw, zw):
        t.rod((x0, y, z), (tip, y, z), chord)
    t.rod((x0, top_root, 0), (x0 + side * run, y, 0), chord)
    # verticals and bottom lacing
    n = max(2, int(length / 1.4))
    for k in range(n + 1):
        x = x0 + side * length * k / n
        t.rod((x, y, -zw), (x, y, zw), brace)
        d = abs(x - x0)
        hgt = max(0.0, (run - d) * slope)
        if hgt > 0.15:
            t.rod((x, y, 0), (x, y + hgt, 0), brace)
    step = 2 * zw
    x = x0
    while abs(x - x0) + step <= length + 1e-6:
        t.rod((x, y, -zw), (x + side * step, y, zw), brace)
        t.rod((x, y, zw), (x + side * step, y, -zw), brace)
        x += side * step
    for z in (-zw, zw):
        t.rod((x0, y, z), (x0, top_root, z), chord)
    return tip


def earth_peak(t, y0, w0, height):
    """Earth-wire peak on top of the body."""
    for sx in (-1, 1):
        for sz in (-1, 1):
            t.rod((sx * w0, y0, sz * w0), (sx * 0.18, y0 + height, sz * 0.18), 0.2)
    t.block((-0.25, y0 + height, -0.25), (0.25, y0 + height + 0.25, 0.25), "#steel")
    t.attach_point((0, y0 + height + 0.1, 0), 1, "earth")


# ----------------------------------------------------------------- towers

def l6_suspension():
    t = Tower("pylon_400kv")
    w = lattice_body(t, [(0, 5.2), (30, 1.8), (52, 1.55)], leg0=0.52, leg1=0.3, brace=0.16)
    for y, length in ((33, 8.6), (41, 9.6), (49, 7.9)):
        for side in (-1, 1):
            tip = cross_arm(t, y, w(y), length, 3.2, side, brace=0.14, chord=0.22)
            end = t.insulator_string((tip, y, 0), 4.6)
            t.attach_point(end, 2)
    earth_peak(t, 52, w(52), 5.5)
    return t


def tension_tower(name, terminal=False):
    t = Tower(name)
    w = lattice_body(t, [(0, 6.8), (28, 2.5), (50, 2.2)], leg0=0.62, leg1=0.36, brace=0.18)
    for k, (y, length) in enumerate(((31, 7.4), (39.5, 8.4), (47.5, 6.9))):
        for side in (-1, 1):
            tip = cross_arm(t, y, w(y), length, 3.8, side, brace=0.16, chord=0.26)
            far = t.insulator_string((tip, y - 0.2, 0.4), 4.6, axis=2, sign=1)
            t.attach_point(far, 2)
            if terminal:
                # downlead from the arm tip to the cable sealing ends on the platform
                plat_y = 9.0
                t.rod((tip, y - 0.2, -0.3), (side * (1.6 + k * 1.3), plat_y + 2.8, -3.2), 0.05, "#wire")
            else:
                back = t.insulator_string((tip, y - 0.2, -0.4), 4.6, axis=2, sign=-1)
                t.attach_point(back, 2)
                # jumper loop hanging under the arm between the two strings
                t.rod((tip, y - 0.25, 0.4), (tip, y - 1.4, 0.4 - 1.15), 0.05, "#wire")
                t.rod((tip, y - 0.25, -0.4), (tip, y - 1.4, -0.4 + 1.15), 0.05, "#wire")
    if terminal:
        # sealing-end platform on the -z side with six post insulators
        plat_y = 9.0
        t.block((-5.2, plat_y - 0.15, -4.2), (5.2, plat_y + 0.1, -2.2), "#grating")
        for x in (-4.6, 4.6):
            for z in (-4.0, -2.4):
                t.rod((x, 0.3, z), (x, plat_y, z), 0.2)
        for side in (-1, 1):
            for k, length in enumerate((6.0, 6.8, 5.6)):
                x = side * (1.6 + k * 1.3)
                t.column(x, -3.2, 0.2, plat_y + 0.1, plat_y + 2.6, "#porcelain")
                t.block((x - 0.22, plat_y + 2.6, -3.42), (x + 0.22, plat_y + 2.8, -2.98), "#steel")
                t.rod((x, plat_y + 2.8, -3.2), (x, plat_y + 2.8, -3.2 + 0.01), 0.05, "#wire")
                # cable down the leg to the ground
                t.rod((x, 0.0, -3.0), (x, plat_y, -3.0), 0.12, "#cable")
    earth_peak(t, 50, w(50), 5.0)
    return t


def pl16_132kv():
    t = Tower("pylon_132kv")
    w = lattice_body(t, [(0, 3.3), (19, 1.25), (34, 1.1)], leg0=0.36, leg1=0.22, brace=0.12)
    for y, length in ((21.5, 4.5), (26.5, 5.2), (31.5, 4.5)):
        for side in (-1, 1):
            tip = cross_arm(t, y, w(y), length, 2.0, side, brace=0.1, chord=0.17)
            end = t.insulator_string((tip, y, 0), 2.3)
            t.attach_point(end, 1)
    earth_peak(t, 34, w(34), 3.2)
    return t


def t_pylon():
    t = Tower("t_pylon")
    tex = "#white"
    t.column(0, 0, 0.75, 0, 30, tex)
    t.block((-1.3, -0.2, -1.3), (1.3, 0.3, 1.3), "#concrete")
    # two arms rising at 22.5 degrees from the top of the mast: the "T"
    slope = math.tan(math.radians(22.5))
    for side in (-1, 1):
        tip = (side * 7.2, 30 + 7.2 * slope, 0)
        t.rod((0, 30, 0), tip, 0.7, tex)
        # diamond "earring" of insulators under each arm, conductors at three corners
        top = np.array([side * 6.2, 30 + 6.2 * slope - 0.4, 0])
        left = top + np.array([0, -2.6, -2.6])
        right = top + np.array([0, -2.6, 2.6])
        bottom = top + np.array([0, -5.2, 0])
        for a, b in ((top, left), (top, right), (left, bottom), (right, bottom)):
            t.rod(a, b, 0.14, "#composite")
        for p in (left, bottom, right):
            t.block(p - 0.18, p + 0.18, "#steel")
            t.attach_point(p, 2)
    t.attach_point((0, 30 + 0.6, 0), 1, "earth")
    return t


def wood_pole():
    t = Tower("wood_pole_11kv")
    t.column(0, 0, 0.17, 0, 9.4, "#wood")
    t.block((-1.05, 8.55, -0.07), (1.05, 8.75, 0.07), "#steel")
    for x in (-0.9, 0.9):
        t.rod((x, 8.75, 0), (x, 8.95, 0), 0.03, "#steel")
        t.column(x, 0, 0.07, 8.95, 9.15, "#porcelain")
        t.attach_point((x, 9.17, 0), 1)
    t.column(0, 0, 0.07, 9.4, 9.6, "#porcelain")
    t.attach_point((0, 9.62, 0), 1)
    # stay wire to the ground
    return t


def h_pole():
    t = Tower("h_pole_33kv")
    for x in (-1.1, 1.1):
        t.column(x, 0, 0.17, 0, 10.2, "#wood")
    t.block((-2.3, 9.4, -0.09), (2.3, 9.62, 0.09), "#steel")
    t.rod((-1.1, 8.0, 0.12), (1.1, 8.0 + 2.2 * math.tan(math.radians(22.5)) * 0.62, 0.12), 0.06, "#steel")
    for x in (-2.0, 0.0, 2.0):
        t.column(x, 0, 0.09, 9.62, 9.95, "#porcelain")
        t.attach_point((x, 9.98, 0), 1)
    return t


def l2_275kv():
    """Narrow 275 kV tower: short flat arms, top arm shortest, single vertical strings."""
    t = Tower("pylon_275kv")
    w = lattice_body(t, [(0, 4.0), (24, 1.5), (44, 1.3)], leg0=0.44, leg1=0.26, brace=0.14)
    for y, length in ((28, 6.2), (35, 6.8), (42, 5.2)):
        for side in (-1, 1):
            tip = cross_arm(t, y, w(y), length, 1.4, side, brace=0.12, chord=0.2)
            t.attach_point(t.insulator_string((tip, y, 0), 3.4), 2)
    earth_peak(t, 44, w(44), 3.5)
    return t


def square_400kv():
    """400 kV tower with deep square-ended arms, the middle arm the widest."""
    t = Tower("pylon_400kv_square")
    w = lattice_body(t, [(0, 5.6), (30, 1.9), (54, 1.6)], leg0=0.55, leg1=0.32, brace=0.17)
    for y, length in ((34, 7.4), (43, 10.2), (51, 7.4)):
        for side in (-1, 1):
            tip = cross_arm(t, y, w(y), length, 2.2, side, brace=0.14, chord=0.24)
            t.rod((tip, y, -0.7), (tip, y + 1.0, -0.7), 0.16)
            t.rod((tip, y, 0.7), (tip, y + 1.0, 0.7), 0.16)
            t.attach_point(t.insulator_string((tip, y, 0), 4.8), 2)
    earth_peak(t, 54, w(54), 4.0)
    return t


def angle_400kv():
    """Wide-based angle tower with heavy drooping arms and horizontal tension strings."""
    t = Tower("pylon_400kv_angle")
    w = lattice_body(t, [(0, 7.4), (26, 2.6), (48, 2.2)], leg0=0.7, leg1=0.4, brace=0.2)
    for y, length in ((30, 7.0), (38.5, 7.8), (46.5, 6.4)):
        for side in (-1, 1):
            tip = cross_arm(t, y, w(y), length, 4.2, side, brace=0.18, chord=0.3)
            for zs in (1, -1):
                t.attach_point(t.insulator_string((tip, y - 0.2, zs * 0.4), 4.2, axis=2, sign=zs), 2)
            t.rod((tip, y - 0.25, 0.4), (tip, y - 1.6, 0.4 - 1.15), 0.06, "#wire")
            t.rod((tip, y - 0.25, -0.4), (tip, y - 1.6, -0.4 + 1.15), 0.06, "#wire")
    earth_peak(t, 48, w(48), 4.5)
    return t


def bt_pole():
    """BT telephone pole: wooden, step bolts, distribution point at the top with drop wires."""
    t = Tower("bt_telephone_pole")
    t.column(0, 0, 0.15, 0, 8.6, "#wood")
    for i in range(10):
        y = 2.2 + i * 0.6
        a = 1 if i % 2 else -1
        t.rod((0.15 * a, y, 0), (0.32 * a, y, 0), 0.03, "#steel")
    t.block((-0.12, 7.6, 0.14), (0.12, 8.0, 0.26), "#black_box")
    t.block((-0.35, 8.25, -0.05), (0.35, 8.35, 0.05), "#steel")
    for x in (-0.3, 0.0, 0.3):
        t.attach_point((x, 8.37, 0), 1, "earth")
    return t


TOWERS = {
    "pylon_400kv": (l6_suspension, "400 kV Suspension Pylon"),
    "pylon_400kv_tension": (lambda: tension_tower("pylon_400kv_tension"), "400 kV Tension Pylon"),
    "pylon_400kv_terminal": (lambda: tension_tower("pylon_400kv_terminal", terminal=True), "400 kV Terminal Pylon"),
    "pylon_132kv": (pl16_132kv, "132 kV Pylon"),
    "t_pylon": (t_pylon, "T-Pylon"),
    "wood_pole_11kv": (wood_pole, "11 kV Wooden Pole"),
    "h_pole_33kv": (h_pole, "33 kV H Pole"),
    "pylon_275kv": (l2_275kv, "275 kV Pylon"),
    "pylon_400kv_square": (square_400kv, "400 kV Pylon (Square Arms)"),
    "pylon_400kv_angle": (angle_400kv, "400 kV Angle Pylon"),
    "bt_telephone_pole": (bt_pole, "BT Telephone Pole"),
}


# ----------------------------------------------------------------- textures

def tex_noise(base, amount, seed, size=32):
    rng = np.random.default_rng(seed)
    a = np.array(base, np.float32)[None, None, :] + rng.normal(0, amount, (size, size, 1))
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGB").convert("RGBA")


def wood():
    rng = np.random.default_rng(3)
    img = np.zeros((32, 32, 3), np.float32)
    for x in range(32):
        img[:, x] = (96, 74, 52)
        img[:, x] += rng.normal(0, 7)
    img += rng.normal(0, 4, (32, 32, 1))
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB").convert("RGBA")


def make_textures():
    t = {
        "steel": G.save(tex_noise((150, 154, 150), 7, 21), "power/steel"),
        "glass": G.save(tex_noise((96, 122, 112), 6, 22), "power/glass_insulator"),
        "porcelain": G.save(tex_noise((120, 72, 46), 5, 23), "power/porcelain"),
        "composite": G.save(tex_noise((70, 72, 76), 4, 24), "power/composite"),
        "white": G.save(tex_noise((226, 228, 226), 3, 25), "power/white_steel"),
        "concrete": G.save(tex_noise((168, 166, 158), 8, 26), "power/concrete"),
        "grating": G.save(tex_noise((118, 122, 126), 10, 27), "power/grating"),
        "wire": G.save(tex_noise((70, 72, 74), 3, 28), "power/wire"),
        "cable": G.save(tex_noise((30, 30, 32), 3, 29), "power/cable"),
        "black_box": G.save(tex_noise((40, 42, 40), 3, 30), "power/black_box"),
        "wood": G.save(wood(), "power/wood"),
    }
    return t


def icon(tower):
    """Side-on silhouette of the tower for the builder item."""
    s = 128
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    pts = []
    for el, c in tower.elements:
        pts.append(c)
    pts = np.array(pts)
    xs, ys = pts[:, 0], pts[:, 1]
    span = max(xs.max() - xs.min(), ys.max() - ys.min())
    scale = (s - 8) / span
    d = ImageDraw.Draw(img)
    col = (60, 62, 64, 255)
    for el, c in tower.elements:
        f, to = np.array(el["from"]), np.array(el["to"])
        x0 = (f[0] - xs.min()) * scale + (s - (xs.max() - xs.min()) * scale) / 2
        x1 = (to[0] - xs.min()) * scale + (s - (xs.max() - xs.min()) * scale) / 2
        y0 = s - 4 - (to[1] - ys.min()) * scale
        y1 = s - 4 - (f[1] - ys.min()) * scale
        if "rotation" in el and el["rotation"]["axis"] == "z":
            cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
            a = math.radians(el["rotation"]["angle"])
            hl = max(x1 - x0, y1 - y0) / 2
            horiz = (x1 - x0) >= (y1 - y0)
            dx, dy = (math.cos(a), -math.sin(a)) if horiz else (-math.sin(a), -math.cos(a))
            d.line((cx - dx * hl, cy - dy * hl, cx + dx * hl, cy + dy * hl), fill=col, width=2)
        else:
            d.rectangle((min(x0, x1), min(y0, y1), max(x0, x1, min(x0, x1) + 1), max(y0, y1, min(y0, y1) + 1)), fill=col)
    return img.resize((32, 32), Image.LANCZOS)


# ----------------------------------------------------------------- output

def cells_of(tower):
    cells = {}
    for el, c in tower.elements:
        # cell (0,0,0) spans -8..8 px around the tower axis in x and z, 0..16 in y
        key = (int(math.floor((c[0] + 8) / PX)), int(math.floor(c[1] / PX)), int(math.floor((c[2] + 8) / PX)))
        el = json.loads(json.dumps(el))
        off = np.array(key) * PX
        el["from"] = [round(el["from"][i] - off[i], 3) for i in range(3)]
        el["to"] = [round(el["to"][i] - off[i], 3) for i in range(3)]
        if "rotation" in el:
            el["rotation"]["origin"] = [round(el["rotation"]["origin"][i] - off[i], 3) for i in range(3)]
        # the block's 0..16 is centred on the tower axis: shift by half a block in x and z
        for k in ("from", "to"):
            el[k][0] += 8
            el[k][2] += 8
        if "rotation" in el:
            el["rotation"]["origin"][0] += 8
            el["rotation"]["origin"][2] += 8
        cells.setdefault(key, []).append(el)
    return cells


def generate(gmod):
    global G
    G = gmod
    tex = make_textures()
    counts = {}
    for name, (builder, _) in TOWERS.items():
        tower = builder()
        cells = cells_of(tower)
        keys = sorted(cells, key=lambda k: (k[1], k[0], k[2]))
        textures = dict(tex)
        textures["particle"] = tex["white"] if name == "t_pylon" else tex["wood"] if "pole" in name else tex["steel"]
        variants = {}
        for i, key in enumerate(keys):
            els = cells[key]
            for el in els:
                for k in ("from", "to"):
                    el[k] = [min(32.0, max(-16.0, v)) for v in el[k]]
            G.write_json(G.ASSETS / f"models/block/power/{name}/{i}.json", G.model(textures, els), compact=True)
            for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
                v = {"model": f"{G.MOD_ID}:block/power/{name}/{i}"}
                if y:
                    v["y"] = y
                variants[f"cell={i},facing={facing}"] = v
        G.write_json(G.ASSETS / f"blockstates/{name}.json", {"variants": variants}, compact=True)
        G.write_json(G.DATA / G.MOD_ID / f"pylons/{name}.json",
                     {"cells": [list(k) for k in keys], "attach": tower.attach}, compact=True)
        item_tex = G.ASSETS / "textures/item"
        item_tex.mkdir(parents=True, exist_ok=True)
        icon(tower).save(item_tex / f"{name}_builder.png")
        G.write_json(G.ASSETS / f"models/item/{name}_builder.json",
                     {"parent": "minecraft:item/generated", "textures": {"layer0": f"{G.MOD_ID}:item/{name}_builder"}})
        counts[name] = len(keys)
        print(f"  {name}: {len(keys)} cells, {len(tower.elements)} elements, {len(tower.attach)} attachment points")
    write_java(counts)
    write_cable_icon()


def write_cable_icon():
    s = 8
    img = Image.new("RGBA", (16 * s, 16 * s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.arc((-40, -60, 168, 100), 20, 160, fill=(40, 40, 42, 255), width=10)
    d.rectangle((10, 70, 40, 120), fill=(230, 180, 30, 255))
    d.rectangle((16, 30, 34, 70), fill=(60, 60, 64, 255))
    img.resize((16, 16), Image.LANCZOS).save(G.ASSETS / "textures/item/cable_tool.png")
    G.write_json(G.ASSETS / "models/item/cable_tool.json",
                 {"parent": "minecraft:item/handheld", "textures": {"layer0": f"{G.MOD_ID}:item/cable_tool"}})


def write_java(counts):
    """Cell counts must be known when the blocks are constructed; keep them in generated code."""
    body = "\n".join(f'        COUNTS.put("{n}", {c});' for n, c in counts.items())
    src = f'''package com.ptmuk.power;

import java.util.LinkedHashMap;
import java.util.Map;

/** Generated by tools/pylons.py: number of block cells in each tower. Do not edit. */
public final class PylonCells {{
    public static final Map<String, Integer> COUNTS = new LinkedHashMap<>();

    static {{
{body}
    }}

    private PylonCells() {{
    }}
}}
'''
    path = G.ROOT / "src/main/java/com/ptmuk/power/PylonCells.java"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(src)


def lang(gmod):
    out = {f"block.{gmod.MOD_ID}.{n}": title for n, (_, title) in TOWERS.items()}
    out.update({f"item.{gmod.MOD_ID}.{n}_builder": title for n, (_, title) in TOWERS.items()})
    out[f"item.{gmod.MOD_ID}.cable_tool"] = "Overhead Line Tool"
    out["itemGroup.ptmuk.power"] = "UK Power"
    return out
