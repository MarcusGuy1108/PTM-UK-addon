"""Alexander ALX400 (dual door, London style) for PTM2, as a GeckoLib model.

Everything is laid out in metres in the model frame PTM2 uses: x forward, y up, z with the
nearside (door side) at -z and the driver at +z. PTM2 mirrors vehicles in left-hand traffic
worlds, which turns this into a proper UK bus with the doors on the left.

Writes: geo model, texture atlas, animations, item icon and ALX400Layout.java (seats, doors,
floors, display positions) so the Java side always matches the model.
"""
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/ptmuk"
# PTM2 only loads GeckoLib models and animations from its own namespace
PTM_ASSETS = ROOT / "src/main/resources/assets/ptm2"
# PTM2 only loads GeckoLib models and animations from its own namespace
PTM_ASSETS = ROOT / "src/main/resources/assets/ptm2"
JAVA = ROOT / "src/main/java/com/ptmuk/bus/ALX400Layout.java"
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
PX = 16.0          # model pixels per metre
TPM = 64           # texels per metre on the big painted panels
ATLAS = 2048
rng = np.random.default_rng(400)

# ------------------------------------------------------------------ dimensions (metres)
L, W, H = 10.2, 2.55, 4.38
X0, X1 = -L / 2, L / 2
ZN, ZO = -W / 2, W / 2                 # nearside (doors), offside (driver)
SKIRT = 0.28
LOWER_FLOOR = 0.38
LOWER_CEIL = 2.15
UPPER_FLOOR = 2.30
UPPER_CEIL = 4.12
LOW_WIN = (1.08, 2.0)
UP_WIN = (2.62, 3.72)
FRONT_AXLE, REAR_AXLE, WHEEL_R = 2.85, -2.85, 0.5
DOOR1 = (3.75, 4.95)                   # front entrance, nearside
DOOR2 = (0.35, 1.55)                   # centre exit, nearside
DOOR_TOP = 2.02
STAIRS = (1.2, 3.3)                    # offside, rising towards the rear
STAIR_Z = (0.25, 1.22)
CAB = (3.65, X1)
DISPLAY_FRONT = (0.96, 2.42, 1.92, 0.34)   # half-width z, top y, width, height
DISPLAY_SIDE = (4.75, 2.38, 1.5, 0.26)     # front x, top y, width, height (nearside)
DISPLAY_REAR = (0.35, 4.12, 0.7, 0.28)     # half-width, top y, width, height

# ------------------------------------------------------------------ colours
RED = (196, 18, 26)
RED_DARK = (150, 12, 18)
BLACK = (18, 18, 20)
GLASS_FRAME = (30, 30, 32)
WALL = (172, 202, 228)            # Stagecoach light blue panels
WALL_DARK = (140, 172, 204)
CEILING = (214, 224, 234)
ORANGE = (244, 142, 22)
SHELL = (28, 70, 176)
CARPET = (150, 34, 40)
GREY = (120, 124, 130)
CHROME = (190, 194, 198)
YELLOW = (250, 204, 30)


# ------------------------------------------------------------------ texture atlas

class Atlas:
    def __init__(self, size):
        self.img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        self.size = size
        self.x = self.y = self.row = 0
        self.regions = {}

    def add(self, name, img):
        w, h = img.size
        if self.x + w > self.size:
            self.x, self.y, self.row = 0, self.y + self.row + 2, 0
        if self.y + h > self.size:
            raise SystemExit("atlas full")
        self.img.paste(img, (self.x, self.y))
        self.regions[name] = (self.x, self.y, w, h)
        self.x += w + 2
        self.row = max(self.row, h)
        return name


ATL = Atlas(ATLAS)


def noise(img, amount=4):
    a = np.asarray(img).astype(np.float32)
    a[..., :3] += rng.normal(0, amount, a.shape[:2] + (1,))
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGBA")


def swatch(name, colour, size=16, amount=3):
    return ATL.add(name, noise(Image.new("RGBA", (size, size), colour + (255,)), amount))


def moquette(size=64):
    """Blue seat fabric with orange / red flecks."""
    img = Image.new("RGBA", (size, size), (34, 72, 186, 255))
    d = ImageDraw.Draw(img)
    for _ in range(140):
        x, y = rng.integers(0, size, 2)
        r = rng.integers(1, 3)
        col = [(30, 50, 140), (60, 110, 220), (240, 140, 30), (210, 50, 40)][rng.integers(4)]
        d.ellipse((x - r, y - r, x + r, y + r), fill=col + (255,))
    return noise(img, 3)


def carpet(size=64):
    img = Image.new("RGBA", (size, size), CARPET + (255,))
    d = ImageDraw.Draw(img)
    for _ in range(220):
        x, y = rng.integers(0, size, 2)
        d.point((x, y), fill=(190, 70, 60, 255) if rng.random() < 0.5 else (110, 24, 30, 255))
    return img


# ------------------------------------------------------------------ painted body panels

def tx(m):
    return int(round(m * TPM))


def side_panel(nearside, inside):
    """One side of the bus (outside or inside face), drawn as seen looking at that face, front
    of the bus to the viewer's right for the nearside outside. Windows and door openings are
    transparent."""
    w, h = tx(L), tx(H)
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    def X(x):          # bus x (m) -> texel column, as seen from outside the nearside
        return tx(x - X0)

    def Y(y):
        return h - tx(y)

    body = (WALL if inside else RED) + (255,)
    d.rectangle((0, Y(H - 0.02), w, Y(SKIRT)), fill=body)
    if not inside:
        d.rectangle((0, Y(SKIRT + 0.22), w, Y(SKIRT)), fill=(40, 40, 44, 255))       # black skirt / rubbing strip
        d.rectangle((0, Y(H), w, Y(H - 0.12)), fill=RED_DARK + (255,))               # roof edge
    else:
        d.rectangle((0, Y(LOWER_CEIL + 0.02), w, Y(LOWER_CEIL - 0.12)), fill=CEILING + (255,))
        d.rectangle((0, Y(UPPER_FLOOR + 0.25), w, Y(UPPER_FLOOR)), fill=WALL_DARK + (255,))
        d.rectangle((0, Y(LOWER_FLOOR + 0.25), w, Y(LOWER_FLOOR)), fill=WALL_DARK + (255,))
        d.rectangle((0, Y(H), w, Y(UPPER_CEIL - 0.1)), fill=CEILING + (255,))
    frame = (GLASS_FRAME if not inside else (88, 92, 98)) + (255,)

    def window_row(xa, xb, y0, y1, bays):
        d.rectangle((X(xa), Y(y1 + 0.05), X(xb), Y(y0 - 0.05)), fill=frame)
        step = (xb - xa) / bays
        for i in range(bays):
            a, b = xa + i * step + 0.05, xa + (i + 1) * step - 0.05
            d.rounded_rectangle((X(a), Y(y1), X(b), Y(y0)), radius=6, fill=(0, 0, 0, 0))
            if not inside:      # hopper vent on alternate upper windows
                if y0 > 2.5 and i % 2 == 1:
                    d.rectangle((X(a), Y(y1), X(b), Y(y1 - 0.2)), fill=(20, 22, 26, 140))

    window_row(X0 + 0.25, X1 - 0.18, *UP_WIN, 7)
    doors = [DOOR1, DOOR2] if nearside else []
    # lower deck windows between the doors / ends
    if nearside:
        window_row(X0 + 0.9, DOOR2[0] - 0.12, *LOW_WIN, 3)
        window_row(DOOR2[1] + 0.12, DOOR1[0] - 0.12, *LOW_WIN, 2)
    else:
        window_row(X0 + 0.9, CAB[0] - 0.2, *LOW_WIN, 6)
        window_row(CAB[0] + 0.05, X1 - 0.25, LOW_WIN[0] - 0.1, LOW_WIN[1], 1)      # cab side window
    for a, b in doors:
        d.rectangle((X(a), Y(DOOR_TOP), X(b), Y(SKIRT)), fill=(0, 0, 0, 0))
        if not inside:
            d.rectangle((X(a) - 4, Y(DOOR_TOP + 0.06), X(b) + 4, Y(DOOR_TOP)), fill=frame)
    # wheel arches
    for ax in (FRONT_AXLE, REAR_AXLE):
        r = tx(WHEEL_R + 0.12)
        cx, cy = X(ax), Y(0.5)
        d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(0, 0, 0, 0) if not inside else WALL_DARK + (255,))
        if not inside:
            d.arc((cx - r - 3, cy - r - 3, cx + r + 3, cy + r + 3), 180, 360, fill=(30, 30, 30, 255), width=4)
    if not inside:
        # fuel flap, tail lights edge, hazard strip
        if not nearside:
            d.rectangle((X(-2.0), Y(0.9), X(-1.7), Y(0.65)), outline=(120, 10, 16, 255), width=2)
        d.rectangle((X(X0), Y(1.6), X(X0) + 6, Y(0.8)), fill=(180, 20, 20, 255))
    img = noise(img, 2)
    # the inside faces are seen from inside, i.e. mirrored
    flip_x = (not nearside) ^ inside
    return img.transpose(Image.Transpose.FLIP_LEFT_RIGHT) if flip_x else img


def front_panel(inside):
    w, h = tx(W), tx(H)
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    def Z(z):          # seen from the front: offside (+z, driver) on the viewer's left
        return tx(ZO - z)

    def Y(y):
        return h - tx(y)

    if inside:
        d.rectangle((0, 0, w, h), fill=WALL + (255,))
        d.rectangle((Z(ZO - 0.05), Y(UP_WIN[1] + 0.08), Z(ZN + 0.05), Y(UP_WIN[0] - 0.12)), fill=(0, 0, 0, 0))
        d.rectangle((Z(ZO - 0.08), Y(2.12), Z(ZN + 0.08), Y(0.95)), fill=(0, 0, 0, 0))
        return noise(img, 2)
    d.rectangle((0, Y(H - 0.02), w, Y(SKIRT)), fill=RED + (255,))
    # upper deck front windows (two panes) with black surround
    d.rounded_rectangle((Z(ZO - 0.04), Y(UP_WIN[1] + 0.12), Z(ZN + 0.04), Y(UP_WIN[0] - 0.14)), radius=10, fill=BLACK + (255,))
    mid = 0
    for a, b in ((ZO - 0.1, mid + 0.03), (mid - 0.03, ZN + 0.1)):
        d.rounded_rectangle((Z(a), Y(UP_WIN[1] + 0.06), Z(b), Y(UP_WIN[0] - 0.08)), radius=8, fill=(0, 0, 0, 0))
    # between-deck black band holding the destination display
    d.rectangle((Z(ZO - 0.04), Y(2.5), Z(ZN + 0.04), Y(2.06)), fill=BLACK + (255,))
    # lower windscreen (split) with black surround
    d.rounded_rectangle((Z(ZO - 0.04), Y(2.12), Z(ZN + 0.04), Y(0.9)), radius=12, fill=BLACK + (255,))
    for a, b in ((ZO - 0.09, 0.02), (-0.02, ZN + 0.09)):
        d.rounded_rectangle((Z(a), Y(2.04), Z(b), Y(0.97)), radius=10, fill=(0, 0, 0, 0))
    # bumper, grille, headlights, indicators
    d.rectangle((0, Y(0.62), w, Y(SKIRT)), fill=(34, 34, 36, 255))
    d.rectangle((Z(0.45), Y(0.86), Z(-0.45), Y(0.66)), fill=(26, 26, 28, 255))
    for i in range(5):
        y = Y(0.84) + i * 3
        d.line((Z(0.42), y, Z(-0.42), y), fill=(60, 60, 64, 255), width=1)
    for side in (1, -1):
        cz = side * (W / 2 - 0.3)
        for k, (zz, col) in enumerate(((cz + side * 0.08, (235, 235, 225)), (cz - side * 0.08, (235, 235, 225)))):
            r = tx(0.075)
            d.ellipse((Z(zz) - r, Y(0.5) - r, Z(zz) + r, Y(0.5) + r), fill=(60, 60, 60, 255))
            d.ellipse((Z(zz) - r + 2, Y(0.5) - r + 2, Z(zz) + r - 2, Y(0.5) + r - 2), fill=col + (255,))
        d.rectangle((Z(cz + side * 0.2) - 4, Y(0.56), Z(cz + side * 0.2) + 4, Y(0.44)), fill=(240, 150, 30, 255))
    # number plate area (text drawn live)
    d.rectangle((Z(0.26), Y(0.47), Z(-0.26), Y(0.36)), fill=(240, 240, 236, 255))
    return noise(img, 2)


def rear_panel(inside):
    w, h = tx(W), tx(H)
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    def Z(z):          # seen from behind: nearside (-z) on the viewer's left
        return tx(z - ZN)

    def Y(y):
        return h - tx(y)

    if inside:
        d.rectangle((0, 0, w, h), fill=WALL + (255,))
        d.rectangle((Z(ZN + 0.25), Y(UP_WIN[1]), Z(ZO - 0.25), Y(UP_WIN[0] + 0.05)), fill=(0, 0, 0, 0))
        d.rectangle((Z(ZN + 0.5), Y(1.75), Z(ZO - 0.5), Y(1.45)), fill=(0, 0, 0, 0))
        return noise(img, 2)
    d.rectangle((0, Y(H - 0.02), w, Y(SKIRT)), fill=RED + (255,))
    d.rounded_rectangle((Z(ZN + 0.22), Y(UP_WIN[1] + 0.05), Z(ZO - 0.22), Y(UP_WIN[0])), radius=10, fill=BLACK + (255,))
    d.rounded_rectangle((Z(ZN + 0.27), Y(UP_WIN[1]), Z(ZO - 0.27), Y(UP_WIN[0] + 0.05)), radius=8, fill=(0, 0, 0, 0))
    # small lower rear window
    d.rounded_rectangle((Z(ZN + 0.45), Y(1.8), Z(ZO - 0.45), Y(1.4)), radius=6, fill=BLACK + (255,))
    d.rounded_rectangle((Z(ZN + 0.5), Y(1.75), Z(ZO - 0.5), Y(1.45)), radius=5, fill=(0, 0, 0, 0))
    # route number box above the rear window
    d.rectangle((Z(-DISPLAY_REAR[0]) - 3, Y(DISPLAY_REAR[1]) - 3, Z(DISPLAY_REAR[0]) + 3, Y(DISPLAY_REAR[1] - DISPLAY_REAR[3]) + 3),
                fill=BLACK + (255,))
    # engine bay grilles and lights
    for gy in (0.75, 1.1):
        d.rectangle((Z(ZN + 0.3), Y(gy + 0.2), Z(ZO - 0.3), Y(gy)), fill=(60, 14, 18, 255))
        for i in range(8):
            y = Y(gy + 0.2) + 2 + i * 3
            d.line((Z(ZN + 0.32), y, Z(ZO - 0.32), y), fill=(30, 8, 10, 255), width=1)
    for side in (-1, 1):
        cz = side * (W / 2 - 0.14)
        d.rectangle((Z(cz) - 6, Y(1.55), Z(cz) + 6, Y(0.7)), fill=(140, 10, 16, 255))
        d.rectangle((Z(cz) - 5, Y(1.5), Z(cz) + 5, Y(1.35)), fill=(240, 150, 30, 255))
        d.rectangle((Z(cz) - 5, Y(0.95), Z(cz) + 5, Y(0.82)), fill=(230, 230, 230, 255))
    d.rectangle((0, Y(0.5), w, Y(SKIRT)), fill=(34, 34, 36, 255))
    d.rectangle((Z(-0.26), Y(0.62), Z(0.26), Y(0.51)), fill=YELLOW + (255,))
    return noise(img, 2)


def roof_panel(inside):
    w, h = tx(L), tx(W)
    if inside:
        img = Image.new("RGBA", (w, h), CEILING + (255,))
        d = ImageDraw.Draw(img)
        for zc in (0.45, W - 0.45):
            d.rectangle((0, tx(zc) - 5, w, tx(zc) + 5), fill=(250, 250, 240, 255))      # light strips
        return noise(img, 2)
    img = Image.new("RGBA", (w, h), RED + (255,))
    d = ImageDraw.Draw(img)
    d.rectangle((tx(L * 0.62), tx(0.3), tx(L * 0.62) + 40, h - tx(0.3)), fill=(170, 16, 22, 255))   # roof hatch
    d.rectangle((tx(L * 0.25), tx(0.3), tx(L * 0.25) + 40, h - tx(0.3)), fill=(170, 16, 22, 255))
    return noise(img, 3)


def lit_panel(text, size, fg, bg):
    img = Image.new("RGBA", size, bg + (255,))
    d = ImageDraw.Draw(img)
    f = ImageFont.truetype(FONT, int(size[1] * 0.6))
    tw = d.textlength(text, font=f)
    d.text(((size[0] - tw) / 2, size[1] * 0.12), text, fill=fg + (255,), font=f)
    return img


def build_textures():
    a = ATL
    a.add("side_near_out", side_panel(True, False))
    a.add("side_near_in", side_panel(True, True))
    a.add("side_off_out", side_panel(False, False))
    a.add("side_off_in", side_panel(False, True))
    a.add("front_out", front_panel(False))
    a.add("front_in", front_panel(True))
    a.add("rear_out", rear_panel(False))
    a.add("rear_in", rear_panel(True))
    a.add("roof_out", roof_panel(False))
    a.add("roof_in", roof_panel(True))
    a.add("moquette", moquette())
    a.add("carpet", carpet())
    a.add("stopping_off", lit_panel("BUS STOPPING", (160, 24), (70, 20, 20), (20, 20, 22)))
    a.add("stopping_on", lit_panel("BUS STOPPING", (160, 24), (255, 60, 40), (30, 10, 10)))
    for name, col in (("red", RED), ("red_dark", RED_DARK), ("black", BLACK), ("wall", WALL), ("wall_dark", WALL_DARK),
                      ("ceiling", CEILING), ("orange", ORANGE), ("shell", SHELL), ("grey", GREY), ("chrome", CHROME),
                      ("yellow", YELLOW), ("tyre", (28, 28, 30)), ("hub", (170, 172, 176)), ("screen", (16, 36, 90)),
                      ("glass_door", (60, 70, 76)), ("lamp_on", (255, 252, 220)), ("lamp_off", (120, 120, 112)),
                      ("brake_on", (255, 40, 30)), ("brake_off", (110, 14, 16)), ("amber_on", (255, 170, 30)),
                      ("amber_off", (120, 70, 20)), ("white", (240, 240, 236)), ("bell", (210, 30, 30)),
                      ("cab", (60, 90, 150)), ("dash", (40, 42, 46))):
        swatch(name, col)
    # door leaves: glass with a frame, transparent glazing
    leaf = Image.new("RGBA", (40, 110), BLACK + (255,))
    ImageDraw.Draw(leaf).rectangle((5, 6, 34, 100), fill=(0, 0, 0, 0))
    ImageDraw.Draw(leaf).rectangle((5, 52, 34, 56), fill=BLACK + (255,))
    a.add("door_leaf", leaf)


# ------------------------------------------------------------------ geometry

class Bone:
    def __init__(self, name, parent=None, pivot=(0, 0, 0), rotation=None):
        self.name, self.parent = name, parent
        self.pivot = [round(v, 4) for v in pivot]
        self.rotation = rotation
        self.cubes = []


BONES = {}
ORDER = []


def bone(name, parent="Vehicle", pivot_m=(0, 0, 0), rotation=None):
    if name not in BONES:
        b = Bone(name, parent, [p * PX for p in pivot_m], rotation)
        BONES[name] = b
        ORDER.append(b)
    return BONES[name]


def m2px(v):
    return [round(c * PX, 4) for c in v]


def face_uv(region, sub=None, flip_u=False, flip_v=False):
    x, y, w, h = ATL.regions[region]
    if sub:                          # sub-rectangle (texels, relative to the region)
        sx, sy, sw, sh = sub
        x, y, w, h = x + sx, y + sy, sw, sh
    u, v, uw, vh = x, y, w, h
    if flip_u:
        u, uw = u + w, -w
    if flip_v:
        v, vh = v + h, -h
    return {"uv": [u, v], "uv_size": [uw, vh]}


def cube(bone_name, lo, hi, faces, rotation=None, pivot=None, parent="Vehicle"):
    """lo/hi in metres. faces: {face: region name | uv dict}; missing faces are left out."""
    b = bone(bone_name, parent)
    lo, hi = np.minimum(lo, hi), np.maximum(lo, hi)
    c = {"origin": m2px(lo), "size": m2px(hi - lo)}
    uv = {}
    # GeckoLib mirrors the model's x axis: our "east" (front, +x) is its "west" face
    swap = {"east": "west", "west": "east"}
    faces = {swap.get(f, f): v for f, v in faces.items()}
    for f, spec in faces.items():
        uv[f] = spec if isinstance(spec, dict) else face_uv(spec)
    c["uv"] = uv
    if rotation:
        c["rotation"] = rotation
        c["pivot"] = m2px(pivot if pivot is not None else (lo + hi) / 2)
    b.cubes.append(c)
    return c


ALL = ("north", "south", "east", "west", "up", "down")


def solid(bone_name, lo, hi, region, faces=ALL, **kw):
    return cube(bone_name, lo, hi, {f: region for f in faces}, **kw)


def sub_rect(region, frac):
    """Fractional sub-rectangle of a painted panel: frac = (u0, v0, u1, v1) in 0..1."""
    x, y, w, h = ATL.regions[region]
    u0, v0, u1, v1 = frac
    return (int(u0 * w), int(v0 * h), max(1, int((u1 - u0) * w)), max(1, int((v1 - v0) * h)))


# ------------------------------------------------------------------ the bus

def shell():
    t = 0.035
    # sides: outer and inner faces painted separately (north = nearside outer face)
    cube("Body", (X0, SKIRT, ZN), (X1, H, ZN + t), {"north": face_uv("side_near_out"), "south": face_uv("side_near_in")})
    cube("Body", (X0, SKIRT, ZO - t), (X1, H, ZO), {"south": face_uv("side_off_out"), "north": face_uv("side_off_in")})
    cube("Body", (X1 - t, SKIRT, ZN), (X1, H, ZO), {"east": face_uv("front_out"), "west": face_uv("front_in")})
    cube("Body", (X0, SKIRT, ZN), (X0 + t, H, ZO), {"west": face_uv("rear_out"), "east": face_uv("rear_in")})
    cube("Roof", (X0, H - t, ZN), (X1, H, ZO), {"up": face_uv("roof_out"), "down": face_uv("roof_in")})
    # rounded roof edges and front / rear corners
    for z, sign in ((ZN, -1), (ZO, 1)):
        solid("Roof", (X0 + 0.02, H - 0.16, z - 0.03 * sign), (X1 - 0.02, H - 0.02, z + 0.11 * sign), "red", rotation=[45 * sign, 0, 0])
    for x in (X0, X1):
        for z in (ZN, ZO):
            solid("Body", (x - 0.06, SKIRT + 0.3, z - 0.06), (x + 0.06, H - 0.1, z + 0.06), "red", rotation=[0, 45, 0])
    # floors and decks
    cube("Floor", (X0 + t, 0.30, ZN + t), (X1 - t, LOWER_FLOOR, ZO - t), {"up": "carpet", "down": "black"})
    # upper floor / lower ceiling, with the stairwell opening on the offside
    for (xa, xb, za, zb) in ((X0 + t, STAIRS[0], ZN + t, ZO - t), (STAIRS[1], CAB[0], ZN + t, ZO - t),
                             (STAIRS[0], STAIRS[1], ZN + t, STAIR_Z[0]), (CAB[0], X1 - t, ZN + t, ZO - t)):
        cube("Floor", (xa, LOWER_CEIL, za), (xb, UPPER_FLOOR, zb), {"up": "carpet", "down": "ceiling", "north": "wall_dark",
                                                                      "south": "wall_dark", "east": "wall_dark", "west": "wall_dark"})
    # skirt underneath, wheel arch housings inside
    solid("Floor", (X0 + 0.1, 0.18, ZN + 0.1), (X1 - 0.1, 0.30, ZO - 0.1), "black")
    for ax in (FRONT_AXLE, REAR_AXLE):
        for z0, z1 in ((ZN + t, ZN + 0.32), (ZO - 0.32, ZO - t)):
            solid("Interior", (ax - 0.62, LOWER_FLOOR, z0), (ax + 0.62, 1.0, z1), "wall_dark")


def wheels():
    for name, ax, z in (("FrontLeftWheel", FRONT_AXLE, ZO - 0.2), ("FrontRightWheel", FRONT_AXLE, ZN + 0.2),
                        ("BackLeftWheel", REAR_AXLE, ZO - 0.2), ("BackRightWheel", REAR_AXLE, ZN + 0.2)):
        b = bone(name, "Wheels", (ax, WHEEL_R, z))
        twin = name.startswith("Back")
        zw = 0.55 if twin else 0.3
        za, zb = (z - zw / 2, z + zw / 2)
        r = WHEEL_R
        # octagonal tyre: two boxes rotated 45 degrees about the axle (z)
        for rot in (0, 45):
            cube(name, (ax - r * 0.92, r - r * 0.38, za), (ax + r * 0.92, r + r * 0.38, zb), {f: "tyre" for f in ALL},
                 rotation=[0, 0, rot], pivot=(ax, r, z), parent="Wheels")
            cube(name, (ax - r * 0.38, r - r * 0.92, za), (ax + r * 0.38, r + r * 0.92, zb), {f: "tyre" for f in ALL},
                 rotation=[0, 0, rot], pivot=(ax, r, z), parent="Wheels")
        side = -1 if z < 0 else 1
        hub_z = (z + side * zw / 2, z + side * (zw / 2 + 0.02))
        cube(name, (ax - 0.22, r - 0.22, min(hub_z)), (ax + 0.22, r + 0.22, max(hub_z)), {f: "hub" for f in ALL}, parent="Wheels")
    bone("Wheels")


def seat(name, x, z, floor, facing=1, width=0.44):
    """Forward (facing=1), rearward (-1) or sideways ('in' handled by caller) bus seat."""
    f = facing
    cush = (floor + 0.40, floor + 0.50)
    back_x = x - f * 0.22
    cube(name, (x - 0.2, cush[0], z - width / 2), (x + 0.22, cush[1], z + width / 2),
         {"up": "moquette", "north": "shell", "south": "shell", "east": "moquette", "west": "moquette", "down": "shell"})
    cube(name, (back_x - 0.045, cush[1], z - width / 2), (back_x + 0.045, floor + 1.05, z + width / 2),
         {"east" if f > 0 else "west": "moquette", "west" if f > 0 else "east": "shell", "up": "shell", "north": "shell",
          "south": "shell"})
    solid(name, (back_x - 0.03, floor + 1.05, z - width / 2 + 0.05), (back_x + 0.03, floor + 1.11, z + width / 2 - 0.05), "orange")
    solid(name, (x - 0.03, floor, z - 0.03), (x + 0.03, cush[0], z + 0.03), "grey")


def pole(name, x, z, y0, y1, thick=0.04):
    solid(name, (x - thick / 2, y0, z - thick / 2), (x + thick / 2, y1, z + thick / 2), "orange")


def bell(name, x, z, y):
    solid(name, (x - 0.035, y, z - 0.035), (x + 0.035, y + 0.07, z + 0.035), "bell")


SEATS = []        # (right, height, forward, yaw, drop_right, drop_height, drop_forward)


def add_seat(x, z, floor, facing=1, yaw=None):
    seat("Seats", x, z, floor, facing)
    SEATS.append((-z, floor + 1.0, x, (0.0 if facing > 0 else 180.0) if yaw is None else yaw, 0.0, floor, x))


def interior():
    zp = (-0.98, -0.53, 0.53, 0.98)       # seat centres across the bus, aisle in the middle
    # driver first: PTM2 treats seat 0 as the driver's seat
    SEATS.append((-0.72, 1.05, 4.35, 0.0, -0.72, LOWER_FLOOR, 4.35))
    seat("Cab", 4.35, 0.72, LOWER_FLOOR + 0.05, 1, width=0.5)
    # cab: partition, dashboard, steering wheel, ticket machine
    solid("Cab", (CAB[0], LOWER_FLOOR, 0.2), (CAB[0] + 0.05, 1.7, ZO - 0.04), "cab")
    solid("Cab", (CAB[0], LOWER_FLOOR, 0.17), (X1 - 0.3, 1.25, 0.22), "cab")
    solid("Cab", (X1 - 0.35, 0.8, 0.2), (X1 - 0.04, 1.2, ZO - 0.05), "dash")
    solid("Cab", (X1 - 0.6, 1.22, -0.2), (X1 - 0.04, 1.3, 0.2), "dash")
    sw = bone("SteeringWheel", "Vehicle", (4.72, 1.32, 0.72))
    for rot in (0, 45, 90, 135):
        cube("SteeringWheel", (4.71, 1.32 - 0.2, 0.71), (4.73, 1.32 + 0.2, 0.73), {f: "black" for f in ALL},
             rotation=[rot, 0, 0], pivot=(4.72, 1.32, 0.72))
    solid("Cab", (4.25, 1.15, 0.05), (4.45, 1.45, 0.2), "dash")          # ticket machine
    # lower deck: perch seats over the front wheel arch (nearside), facing across the bus
    for x in (2.35, 2.85):
        seat("Seats", x, ZN + 0.45, LOWER_FLOOR + 0.25, 1)
        SEATS.append((-(ZN + 0.45), LOWER_FLOOR + 1.25, x, -90.0, 0.0, LOWER_FLOOR, x))
    # wheelchair bay opposite the centre door: blue backboard
    solid("Interior", (0.3, LOWER_FLOOR + 0.3, ZO - 0.12), (1.15, 1.6, ZO - 0.05), "shell")
    solid("Interior", (0.3, 1.0, ZO - 0.2), (1.15, 1.04, ZO - 0.12), "orange")
    # rear saloon: forward-facing pairs, rear bench
    for x in (-0.25, -1.05, -1.85, -2.65, -3.45, -4.25):
        for z in zp:
            add_seat(x, z, LOWER_FLOOR + (0.25 if -3.5 < x < -2.2 else 0.0))
    # lower deck poles and bells
    for x, z in ((DOOR1[0] - 0.05, ZN + 0.35), (DOOR1[1] - 0.15, ZN + 0.3), (DOOR2[0] - 0.05, ZN + 0.32), (DOOR2[1] + 0.05, ZN + 0.32),
                 (DOOR2[1] + 0.05, -0.3), (-0.25, 0.3), (-1.85, -0.3), (-1.85, 0.3), (-3.45, -0.3), (-3.45, 0.3), (1.5, 0.3)):
        pole("Interior", x, z, LOWER_FLOOR, LOWER_CEIL)
        bell("Interior", x, z, 1.45)
    for z in (-0.45, 0.45):
        solid("Interior", (X0 + 0.3, LOWER_CEIL - 0.08, z - 0.02), (CAB[0], LOWER_CEIL - 0.04, z + 0.02), "orange")
    # staircase on the offside, rising towards the rear
    n = 8
    run = (STAIRS[1] - STAIRS[0]) / n
    rise = (UPPER_FLOOR - LOWER_FLOOR) / n
    for i in range(n):
        xb = STAIRS[1] - i * run
        top = LOWER_FLOOR + (i + 1) * rise
        cube("Stairs", (xb - run, LOWER_FLOOR, STAIR_Z[0]), (xb, top, STAIR_Z[1]),
             {"up": "carpet", "east": "yellow", "north": "wall_dark", "south": "wall_dark", "west": "wall_dark"})
    solid("Stairs", (STAIRS[0], LOWER_FLOOR, STAIR_Z[0] - 0.04), (STAIRS[1] + 0.1, UPPER_FLOOR + 0.9, STAIR_Z[0]), "wall")
    solid("Stairs", (STAIRS[0], UPPER_FLOOR + 0.9, STAIR_Z[0] - 0.05), (STAIRS[1] + 0.1, UPPER_FLOOR + 0.95, STAIR_Z[0] + 0.01), "orange")
    # upper deck: forward-facing pairs, front row at the big front windows, rear bench of five
    xs = [4.5 - 0.78 * k for k in range(12)]
    for x in xs:
        for z in zp:
            if z > 0 and STAIRS[0] - 0.2 < x < STAIRS[1] + 0.25:
                continue           # stairwell
            add_seat(x, z, UPPER_FLOOR)
    for z in (-0.96, -0.48, 0.0, 0.48, 0.96):
        add_seat(X0 + 0.42, z, UPPER_FLOOR)
    # upper deck curved poles from seat backs to the ceiling (straight here) and the front rail
    for x in xs[1::2]:
        for z in (-0.3, 0.3):
            if z > 0 and STAIRS[0] - 0.2 < x < STAIRS[1] + 0.25:
                continue
            pole("Interior", x - 0.22, z, UPPER_FLOOR + 1.05, UPPER_CEIL)
            bell("Interior", x - 0.22, z, UPPER_FLOOR + 1.35)
    solid("Interior", (X1 - 0.35, UPPER_FLOOR + 0.85, ZN + 0.1), (X1 - 0.3, UPPER_FLOOR + 0.9, ZO - 0.1), "orange")
    # inside displays: next stop screens facing the rear, lower and upper deck
    for name, x, y in (("Display5", CAB[0] - 0.02, 2.05), ("Display6", X1 - 0.12, UPPER_CEIL - 0.04)):
        w, h = 0.62, 0.3
        bone(name, "Interior", (x - 0.01, y, -w / 2))
        solid(name, (x, y - h - 0.02, -w / 2 - 0.02), (x + 0.04, y + 0.02, w / 2 + 0.02), "black", parent="Interior")
    # BUS STOPPING signs (unlit face; the lit one slides forward when the bell has been rung)
    for x, y, z in ((CAB[0] - 0.015, 1.72, -0.55), (X1 - 0.13, UPPER_CEIL - 0.42, 0.0)):
        cube("Interior", (x, y, z - 0.3), (x + 0.03, y + 0.07, z + 0.3), {"west": "stopping_off", "east": "black", "up": "black",
                                                                           "down": "black", "north": "black", "south": "black"})
        cube("BusStopping", (x + 0.005, y + 0.002, z - 0.298), (x + 0.025, y + 0.068, z + 0.298), {"west": "stopping_on"},
             parent="Blinkers")


def doors():
    """Two-leaf doors; each leaf folds inwards and slides towards its frame in the animation."""
    for bone_pair, (a, b) in ((("Right", "Left"), DOOR1), (("Right2", "Left2"), DOOR2)):
        mid = (a + b) / 2
        group = "FrontDoors" if bone_pair[0] == "Right" else "MiddleDoors"
        bone(group)
        for name, (xa, xb) in zip(bone_pair, ((mid, b), (a, mid))):
            bone(name, group, (xb if name.startswith("Right") else xa, LOWER_FLOOR, ZN + 0.02))
            cube(name, (xa + 0.01, LOWER_FLOOR + 0.02, ZN + 0.005), (xb - 0.01, DOOR_TOP - 0.02, ZN + 0.035),
                 {"north": "door_leaf", "south": "door_leaf", "east": "black", "west": "black", "up": "black", "down": "black"},
                 parent=group)


def lights():
    """Lit parts sit just behind their unlit lenses; the animations push them out."""
    bone("Blinkers")
    for name, x, faces in (("FrontLights", X1 + 0.005, "east"), ("StopLights", X0 - 0.005, "west"), ("BackLights", X0 - 0.005, "west")):
        bone(name, "Blinkers")
    for side in (1, -1):
        cz = side * (W / 2 - 0.3)
        for zz in (cz + side * 0.08, cz - side * 0.08):
            cube("FrontLights", (X1 - 0.02, 0.43, zz - 0.07), (X1 - 0.008, 0.57, zz + 0.07), {"east": "lamp_on"}, parent="Blinkers")
        rz = side * (W / 2 - 0.14)
        cube("StopLights", (X0 + 0.008, 1.0, rz - 0.09), (X0 + 0.02, 1.3, rz + 0.09), {"west": "brake_on"}, parent="Blinkers")
        cube("BackLights", (X0 + 0.008, 0.82, rz - 0.08), (X0 + 0.02, 0.95, rz + 0.08), {"west": "lamp_on"}, parent="Blinkers")
    for name, x, z, face in (("FrontLeftTurnSignal", X1 - 0.02, W / 2 - 0.1, "east"), ("FrontRightTurnSignal", X1 - 0.02, -W / 2 + 0.1, "east"),
                             ("BackLeftTurnSignal", X0 + 0.008, W / 2 - 0.14, "west"), ("BackRightTurnSignal", X0 + 0.008, -W / 2 + 0.14, "west")):
        bone(name, "Blinkers")
        y = 0.5 if name.startswith("Front") else 1.42
        xa, xb = (x, x + 0.012) if face == "east" else (x, x + 0.012)
        cube(name, (xa, y - 0.06, z - 0.07), (xb, y + 0.06, z + 0.07), {face: "amber_on"}, parent="Blinkers")
    # side repeaters
    for name, z, face in (("LeftTurnSignal", ZO + 0.008, "south"), ("RightTurnSignal", ZN - 0.008, "north")):
        bone(name, "Blinkers", (3.4, 0.75, z))
        cube(name, (3.35, 0.72, min(z, z + 0.01)), (3.45, 0.78, max(z, z + 0.01)), {face: "amber_on"}, parent="Blinkers")


def details():
    # destination displays (text drawn by the renderer at these bones' pivots)
    zf, top, w, h = DISPLAY_FRONT
    bone("Display1", "Vehicle", (X1 + 0.012, top, zf))
    solid("Display1", (X1 - 0.01, top - h, -zf), (X1 + 0.01, top, zf), "black")
    sx, sy, sw, sh = DISPLAY_SIDE
    bone("Display2", "Vehicle", (sx, sy, ZN - 0.012))
    solid("Display2", (sx - sw, sy - sh, ZN - 0.01), (sx, sy, ZN + 0.01), "black")
    rz, rt, rw, rh = DISPLAY_REAR
    bone("Display3", "Vehicle", (X0 - 0.012, rt, -rz))
    solid("Display3", (X0 - 0.01, rt - rh, -rz), (X0 + 0.01, rt, rz), "black")
    bone("PlateFront", "Vehicle", (X1 + 0.012, 0.415, 0))
    bone("PlateBack", "Vehicle", (X0 - 0.012, 0.565, 0))
    # "bunny ear" mirrors hanging from the front upper corners
    bone("Mirrors")
    for name, z, s in (("LeftMirror", ZO, 1), ("RightMirror", ZN, -1)):
        bone(name, "Mirrors", (X1 + 0.25, 1.85, z + s * 0.1))
        solid(name, (X1 - 0.3, 2.45, z + s * 0.0), (X1 + 0.3, 2.5, z + s * 0.06), "black", parent="Mirrors")
        solid(name, (X1 + 0.25, 1.95, z + s * 0.02), (X1 + 0.3, 2.5, z + s * 0.07), "black", parent="Mirrors")
        solid(name, (X1 + 0.18, 1.55, z + s * 0.0), (X1 + 0.32, 2.0, z + s * 0.2), "black", parent="Mirrors")
    # wipers
    bone("windscreenwipers")
    for name, z in (("1", 0.55), ("2", -0.55)):
        bone(name, "windscreenwipers", (X1 + 0.02, 1.0, z))
        solid(name, (X1 + 0.012, 1.0, z - 0.01), (X1 + 0.03, 1.75, z + 0.01), "black", parent="windscreenwipers")


# ------------------------------------------------------------------ animations

def anim(length=None, loop=None, bones=None):
    a = {"bones": bones or {}}
    if length is not None:
        a["animation_length"] = length
    if loop is not None:
        a["loop"] = loop
    return a


def door_anim(right, left, open_):
    """Leaves swing in a little then fold towards their frame edges."""
    def key(rot, pos):
        return {"rotation": {"0.0": {"vector": [0, 0, 0] if open_ else rot}, "1.0": {"vector": rot if open_ else [0, 0, 0]}},
                "position": {"0.0": {"vector": [0, 0, 0] if open_ else pos}, "1.0": {"vector": pos if open_ else [0, 0, 0]}}}
    return anim(1.0, "hold_on_last_frame", {right: key([0, 80, 0], [0, 0, 0]), left: key([0, -80, 0], [0, 0, 0])})


def write_animations():
    out = 0.025 * PX
    anims = {
        "alx400.door1_open": door_anim("Right", "Left", True),
        "alx400.door1_close": door_anim("Right", "Left", False),
        "alx400.door2_open": door_anim("Right2", "Left2", True),
        "alx400.door2_close": door_anim("Right2", "Left2", False),
        "alx400.front_lights": anim(loop=True, bones={"FrontLights": {"position": [out, 0, 0]}}),
        "alx400.stop_lights": anim(loop=True, bones={"StopLights": {"position": [-out, 0, 0]}}),
        "alx400.reverse_lights": anim(loop=True, bones={"BackLights": {"position": [-out, 0, 0]}}),
        "alx400.bus_stopping": anim(loop=True, bones={"BusStopping": {"position": [-0.4, 0, 0]}}),
        "alx400.null": anim(loop=True),
    }
    for side, bones in (("left", ("FrontLeftTurnSignal", "BackLeftTurnSignal", "LeftTurnSignal")),
                        ("right", ("FrontRightTurnSignal", "BackRightTurnSignal", "RightTurnSignal"))):
        b = {}
        for name in bones:
            vec = [out, 0, 0] if name.startswith("Front") else [-out, 0, 0] if name.startswith("Back") else [0, 0, out if side == "left" else -out]
            b[name] = {"position": {"0.0": {"vector": vec}, "0.5": {"vector": [0, 0, 0]}}}
        anims[f"alx400.{side}_signal"] = anim(1.0, True, b)
    anims["alx400.wipers"] = anim(2.0, True, {
        "1": {"rotation": {"0.0": {"vector": [0, 0, 0]}, "1.0": {"vector": [-80, 0, 0]}, "2.0": {"vector": [0, 0, 0]}}},
        "2": {"rotation": {"0.0": {"vector": [0, 0, 0]}, "1.0": {"vector": [-80, 0, 0]}, "2.0": {"vector": [0, 0, 0]}}}})
    path = PTM_ASSETS / "animations/bus/ptmuk_alx400.animation.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps({"format_version": "1.8.0", "animations": anims}, indent=1))


# ------------------------------------------------------------------ output

def write_geo():
    bones = [{"name": "Vehicle", "pivot": [0, 0, 0]}]
    for b in ORDER:
        if b.name == "Vehicle":
            continue
        e = {"name": b.name, "parent": b.parent or "Vehicle", "pivot": b.pivot}
        if b.rotation:
            e["rotation"] = b.rotation
        if b.cubes:
            e["cubes"] = b.cubes
        bones.append(e)
    # parents must exist and come before their children
    names = {b["name"] for b in bones}
    for b in bones:
        if b.get("parent") and b["parent"] not in names:
            raise SystemExit(f"missing parent {b['parent']} for {b['name']}")
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.alx400", "texture_width": ATLAS, "texture_height": ATLAS,
                        "visible_bounds_width": 14, "visible_bounds_height": 6, "visible_bounds_offset": [0, 2.5, 0]},
        "bones": sort_bones(bones)}]}
    path = PTM_ASSETS / "geo/bus/ptmuk_alx400.geo.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(geo, separators=(",", ":")))
    print(f"  alx400: {sum(len(b.get('cubes', [])) for b in bones)} cubes, {len(bones)} bones, {len(SEATS)} seats")


def sort_bones(bones):
    by = {b["name"]: b for b in bones}
    out, seen = [], set()

    def visit(b):
        if b["name"] in seen:
            return
        p = b.get("parent")
        if p:
            visit(by[p])
        seen.add(b["name"])
        out.append(b)
    for b in bones:
        visit(b)
    return out


def write_texture():
    path = ASSETS / "textures/entity/bus/alx400.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    ATL.img.save(path)


def write_icon():
    s = 8
    img = Image.new("RGBA", (16 * s, 16 * s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((6, 18, 122, 108), radius=12, fill=RED + (255,))
    for y0, y1 in ((26, 52), (64, 88)):
        d.rectangle((14, y0, 116, y1), fill=(30, 30, 34, 255))
        for x in range(30, 116, 22):
            d.line((x, y0, x, y1), fill=RED + (255,), width=3)
    d.rectangle((86, 64, 100, 106), fill=(40, 40, 44, 255))
    for cx in (30, 96):
        d.ellipse((cx - 11, 96, cx + 11, 118), fill=(20, 20, 20, 255))
        d.ellipse((cx - 5, 102, cx + 5, 112), fill=(170, 170, 170, 255))
    path = ASSETS / "textures/item/alx400.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    img.resize((16, 16), Image.LANCZOS).save(path)
    (ASSETS / "models/item").mkdir(parents=True, exist_ok=True)
    (ASSETS / "models/item/alx400.json").write_text(json.dumps(
        {"parent": "minecraft:item/generated", "textures": {"layer0": "ptmuk:item/alx400"}}, indent=1))


def floors(upper):
    out = []
    n = 8
    run = (STAIRS[1] - STAIRS[0]) / n
    rise = (UPPER_FLOOR - LOWER_FLOOR) / n
    for i in range(n):
        xb = STAIRS[1] - i * run
        out.append((xb - run, xb, STAIR_Z[0], STAIR_Z[1], LOWER_FLOOR + (i + 1) * rise))
    if upper:
        out.append((X0, X1, -W, W, UPPER_FLOOR))
    return out


def write_java():
    def f(v):
        return f"{v:.3f}"
    seats = ",\n".join(f"                new SeatLocation({f(r)}, {f(h)}, {f(fw)}, {yaw:.1f}f, false, {f(dr)}, {f(dh)}, {f(df)})"
                       for r, h, fw, yaw, dr, dh, df in SEATS)

    def floor_list(upper):
        return ",\n".join(f"                    new FloorObject({f(a)}, {f(b)}, {f(lmin)}, {f(lmax)}, {f(hh)}f)" for a, b, lmin, lmax, hh in floors(upper))
    src = f'''package com.ptmuk.bus;

import com.rinventor.ptm2.engine.vehicle.FloorObject;
import com.rinventor.ptm2.engine.vehicle.SeatLocation;
import com.rinventor.ptm2.engine.vehicle.WheelLocation;
import java.util.ArrayList;
import java.util.List;

/** Generated by tools/bus_alx400.py together with the model. Do not edit by hand. */
public final class ALX400Layout {{
    public static final float LOWER_FLOOR = {f(LOWER_FLOOR)}f;
    public static final float UPPER_FLOOR = {f(UPPER_FLOOR)}f;
    /** Above this height (relative to the bus) the local player counts as being upstairs. */
    public static final double DECK_SWITCH_HEIGHT = {f((LOWER_FLOOR + UPPER_FLOOR) / 2)};
    public static final double DOOR1_FORWARD = {f(sum(DOOR1) / 2)};
    public static final double DOOR2_FORWARD = {f(sum(DOOR2) / 2)};
    public static final double[] TICKET_MACHINE = {{-0.12, 1.3, 4.35}};
    /** Display sizes for the renderer: width, height (blocks) and label yaw. */
    public static final float[] DISPLAY_FRONT = {{{f(DISPLAY_FRONT[2])}f, {f(DISPLAY_FRONT[3])}f, 90.0f}};
    public static final float[] DISPLAY_SIDE = {{{f(DISPLAY_SIDE[2])}f, {f(DISPLAY_SIDE[3])}f, 0.0f}};
    public static final float[] DISPLAY_REAR = {{{f(DISPLAY_REAR[2])}f, {f(DISPLAY_REAR[3])}f, 270.0f}};
    public static final float[] DISPLAY_INSIDE = {{0.62f, 0.3f, 270.0f}};
    public static final int SEAT_COUNT = {len(SEATS)};

    private ALX400Layout() {{
    }}

    public static List<SeatLocation> seats() {{
        return new ArrayList<>(List.of(
{seats}));
    }}

    public static List<WheelLocation> wheels() {{
        return new ArrayList<>(List.of(new WheelLocation({f(FRONT_AXLE)}), new WheelLocation({f(REAR_AXLE)})));
    }}

    /** The stairs first (so they win), then for the upper deck its floor everywhere else. */
    public static List<FloorObject> floors(boolean upper) {{
        if (upper) {{
            return new ArrayList<>(List.of(
{floor_list(True)}));
        }}
        return new ArrayList<>(List.of(
{floor_list(False)}));
    }}
}}
'''
    JAVA.write_text(src)


def main():
    build_textures()
    bone("Body")
    shell()
    wheels()
    interior()
    doors()
    lights()
    details()
    write_geo()
    write_texture()
    write_animations()
    write_icon()
    write_java()


if __name__ == "__main__":
    main()
