"""Scania OmniCity double decker (N230UD), London dual door spec, for PTM2, as a GeckoLib model.

Everything is laid out in metres in the model frame PTM2 uses: x forward, y up, z with the
nearside (door side) at -z and the driver at +z. PTM2 mirrors vehicles in left-hand traffic
worlds, which turns this into a proper UK bus with the doors on the left.

Writes: geo model, texture atlas, animations, item icon and OmnicityLayout.java (seats, doors,
floors, display positions) so the Java side always matches the model.
"""
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

import bus_extras

import livery

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/ptmuk"
# PTM2 only loads GeckoLib models and animations from its own namespace
PTM_ASSETS = ROOT / "src/main/resources/assets/ptm2"
# PTM2 only loads GeckoLib models and animations from its own namespace
PTM_ASSETS = ROOT / "src/main/resources/assets/ptm2"
JAVA = ROOT / "src/main/java/com/ptmuk/bus/OmnicityLayout.java"
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
# PTM2's own buses are built about 1.18 times real size (3 m wide), so ours are too, or they
# look small next to them and the roads; everything below is in real metres
SCALE = 1.18
PX = 16.0 * SCALE  # model pixels per (real) metre
STEER_TILT = -30   # steering wheel tilt, degrees
TPM = 80           # texels per metre on the big painted panels
ATLAS = 2048
rng = np.random.default_rng(230)

# ------------------------------------------------------------------ dimensions (metres)
# Proportions from photos of London OmniCitys (Stagecoach 15127 and 15xxx on routes 215 and
# 298, First Hyper 207, Brighton & Hove for the rear): 10.8 m long, a flat upright front with
# well rounded corners, one big upper deck window over a shallow full width black destination
# box, a red band, the tall windscreen in a black surround with the fleet number on a black
# strip below it, a red lower panel with a slatted grille, small headlamp clusters low in the
# corners; bonded glazing in black bands down the sides, an engine air louvre at the back of
# the upper deck, the front entrance ahead of the front axle and the exit just ahead of the
# rear axle. The interior follows the Enviro400's, stretched 0.35 m at each end.
L, W, H = 10.8, 2.55, 4.38
X0, X1 = -L / 2, L / 2
ZN, ZO = -W / 2, W / 2                 # nearside (doors), offside (driver)
SKIRT = 0.26
LOWER_FLOOR = 0.36
LOWER_CEIL = 2.3
UPPER_FLOOR = 2.43
UPPER_CEIL = 4.18
LOW_WIN = (1.22, 2.2)
UP_WIN = (3.14, 4.06)
FRONT_AXLE, REAR_AXLE, WHEEL_R = 2.8, -2.95, 0.5
DOOR1 = (3.7, 4.97)                    # front entrance, nearside, ahead of the front axle
DOOR2 = (-1.85, -0.67)                 # exit, nearside, just ahead of the rear axle
DOOR_TOP = 2.2
STAIRS = (1.55, 3.65)                  # offside behind the cab, rising towards the front
STAIR_Z = (0.25, 1.22)
STAIR_COVER = 0.35                     # the ceiling carries on over the bottom of the stairs
STAIR_RAIL_SENSE = -1                  # flip if the stair handrail slopes the wrong way
READER = (4.34, 1.17)                  # card reader centre: x along the bus, height
CAB = (3.97, X1)
FS, RS = 0.35, -0.35                   # Enviro400 interior positions moved forward / back by these
# lower deck behind the centre door, as on London OmniCitys: a raised box each side over the
# rear axle (two forward-facing pairs on a plinth), then the gangway steps up to a back row of
# three, with a tall cupboard over the engine in the offside rear corner
POD = (-3.62, -2.0, 0.42)              # x from, x to, plinth height
POD_ROWS = (-2.45, -3.25)
POD_Z = (0.96, 0.54)                  # |z| of the window and gangway seats in the boxes
POD_PANEL = 0.29                       # |z| of the gangway edge of the raised floors
REAR_STEPS = ((-4.0, POD[0], 0.21), (X0 + 0.12, -4.0, 0.42))   # gangway steps up to the back row
BACK_ROW = -4.6
UP_REAR_STEP = (X0 + 0.12, X0 + 0.85, 0.15)   # the upper deck back row sits up a step
STAIR_HOOD = 3.93                      # underside of the light blue box over the stairwell, upstairs
# window groups: (x from, x to, panes); the offside has a blank panel by the stairs
LOW_WINDOWS_NEAR = ((-0.55, 3.57, 3), (-4.1, -1.97, 2))
LOW_WINDOWS_OFF = ((-4.1, 1.42, 4),)
CAB_WINDOW = (3.65, 5.12)
UP_SPAN = (X0 + 0.78, X1 - 0.36)       # seven upper deck windows in one black band, louvre behind
UP_PANES = 7
# front, bottom to top
BUMPER_TOP = 0.5
GRILLE = (0.66, 0.9)                   # slatted grille across the lower red panel
SCREEN = (1.12, 2.3)                   # windscreen glass
DEST = (2.6, 2.98)                     # full width black destination box
FRONT_WIN = (3.06, 4.16)               # upper deck front window (one big pane)
DISPLAY_FRONT = (0.98, 2.94, 1.96, 0.32)   # half-width z, top y, width, height (one line: destination, route)
DISPLAY_SIDE = (3.55, 2.56, 1.45, 0.24)    # front x, top y, width, height (box on the panel behind the front door)
DISPLAY_REAR = (0.36, 3.12, 0.72, 0.3)     # offside edge z, top y, width, height (route box under the rear window)

# rounder than the Enviro400: well rounded lower corners and roof edges, a near vertical front
R_ROOF = 0.3           # radius of the roof edges and the front / rear domes
RC_LOW = 0.3           # plan radius of the front corners
RC_TOP = R_ROOF
RC_REAR = 0.3
D_TOP = 0.04           # the upper front leans back only a little
RAKE_FROM = 3.3
SIDE_FRONT = RC_LOW    # the flat side panels stop this far behind the front


def front_rc(y):
    if y <= 2.3:
        return RC_LOW
    if y >= RAKE_FROM:
        return RC_TOP
    return RC_LOW + (RC_TOP - RC_LOW) * (y - 2.3) / (RAKE_FROM - 2.3)


def front_d(y):
    if y <= RAKE_FROM:
        return 0.0
    return D_TOP * min(1.0, (y - RAKE_FROM) / (H - R_ROOF - RAKE_FROM))


def front_bands():
    """(y0, y1) bands of the front: short ones where the radius or the rake change."""
    edges = [SKIRT, BUMPER_TOP, 1.08, 2.3]
    edges += [2.3 + (RAKE_FROM - 2.3) * k / 6 for k in range(1, 7)]
    edges += [RAKE_FROM + (H - R_ROOF - RAKE_FROM) * k / 6 for k in range(1, 7)]
    return list(zip(edges[:-1], edges[1:]))


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
    """Collects the painted images, then packs them in shelves, tallest first."""

    def __init__(self, size):
        self.size = size
        self.pending = []
        self.regions = {}
        self.img = None

    def add(self, name, img):
        self.pending.append((name, img))
        return name

    def pack(self):
        self.img = Image.new("RGBA", (self.size, self.size), (0, 0, 0, 0))
        x = y = row = 0
        for name, img in sorted(self.pending, key=lambda p: (-p[1].size[1], -p[1].size[0])):
            w, h = img.size
            if x + w > self.size:
                x, y, row = 0, y + row + 2, 0
            if y + h > self.size:
                raise SystemExit(f"atlas full at {name}")
            self.img.paste(img, (x, y))
            self.regions[name] = (x, y, w, h)
            x += w + 2
            row = max(row, h)
        print(f"  atlas used to y={y + row} of {self.size}")


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

GRAD_N = 12


def grad_name(x):
    """Body colour swatch for pieces at x along the bus (they follow the livery's fade)."""
    return f"grad{min(GRAD_N - 1, max(0, int((x - X0) / L * GRAD_N)))}"


def tx(m):
    return int(round(m * TPM))


ADVERTS = {"near": (-4.45, 1.2), "off": (-4.5, 2.4)}     # x span of the side adverts
ADVERT_Y = (2.42, 2.98)
# regions holding text: mirrored in place in the _left texture, because PTM2 mirrors the whole
# bus in left-hand traffic worlds and loads <texture>_left.png if there is one
TEXT_REGIONS = ["advert_near", "advert_off", "stopping_off", "stopping_on"]


def advert_image(which):
    xa, xb = ADVERTS[which]
    img = Image.new("RGBA", (tx(xb - xa) * 2, tx(ADVERT_Y[1] - ADVERT_Y[0]) * 2), (0, 0, 0, 0))
    if livery.active():
        return img
    advert(img, 3, 3, img.width - 4, img.height - 4, which)
    return img


def advert(img, x0, y0, x1, y1, which):
    """A made-up bus-side advert in a grey frame."""
    d = ImageDraw.Draw(img)
    x0, x1 = min(x0, x1), max(x0, x1)
    d.rectangle((x0 - 3, y0 - 3, x1 + 3, y1 + 3), fill=(170, 172, 176, 255))
    hgt = y1 - y0
    if which == "near":
        bg, fg, text, sub = (36, 60, 140), (250, 250, 250), "CUBE FM 101.4", "the sound of the city"
        d.rectangle((x0, y0, x1, y1), fill=bg + (255,))
        d.rectangle((x1 - hgt * 1.6, y0, x1, y1), fill=(250, 200, 40, 255))
        d.ellipse((x1 - hgt * 1.35, y0 + hgt * 0.15, x1 - hgt * 0.25, y1 - hgt * 0.15), fill=(36, 60, 140, 255))
    else:
        bg, fg, text, sub = (250, 200, 40), (30, 30, 30), "VISIT BRICKFORD ZOO", "open every day"
        d.rectangle((x0, y0, x1, y1), fill=bg + (255,))
        d.rectangle((x0, y1 - hgt * 0.22, x1, y1), fill=(40, 120, 60, 255))
    f = ImageFont.truetype(FONT, max(8, int(hgt * 0.42)))
    d.text((x0 + hgt * 0.25, y0 + hgt * 0.14), text, fill=fg + (255,), font=f)
    f2 = ImageFont.truetype(FONT, max(6, int(hgt * 0.16)))
    d.text((x0 + hgt * 0.27, y0 + hgt * 0.62), sub, fill=fg + (255,), font=f2)


def emblem(d, cx, cy, s, fg=(255, 255, 255, 255), bg=RED + (255,)):
    """The world's transport emblem (used instead of the TfL roundel): a flat isometric block,
    a hexagon in fg with its three inner edges cut in bg, as on the bus stop flags."""
    c = 0.866 * s
    d.polygon([(cx, cy - s), (cx + c, cy - s / 2), (cx + c, cy + s / 2), (cx, cy + s), (cx - c, cy + s / 2), (cx - c, cy - s / 2)],
              fill=fg)
    t = max(1, int(s * 0.2))
    for ex, ey in ((cx, cy + s), (cx - c, cy - s / 2), (cx + c, cy - s / 2)):
        d.line([(cx, cy), (ex, ey)], fill=bg, width=t)


def rrect(d, box, r_top, r_bot, fill):
    """Rounded rectangle with different radii at the top and bottom corners."""
    x0, y0, x1, y1 = box
    mid = (y0 + y1) // 2
    d.rounded_rectangle((x0, y0, x1, mid + r_top + 1), radius=r_top, fill=fill)
    d.rounded_rectangle((x0, mid - r_bot - 1, x1, y1), radius=r_bot, fill=fill)


def window_rows(nearside):
    """Window groups on one side: (x0, x1, y0, y1, panes, kind)."""
    a, b = UP_SPAN
    out = [(a, b, UP_WIN[0], UP_WIN[1], UP_PANES, "")]
    for xa, xb, n in (LOW_WINDOWS_NEAR if nearside else LOW_WINDOWS_OFF):
        out.append((xa, xb, LOW_WIN[0], LOW_WIN[1], n, ""))
    if not nearside:
        out.append((CAB_WINDOW[0], CAB_WINDOW[1], LOW_WIN[0] - 0.12, LOW_WIN[1], 2, "cab"))
    return out


def side_panel(nearside, inside):
    """One side of the bus (outside or inside face), drawn as seen looking at that face, front
    of the bus to the viewer's right for the nearside outside. Windows and door openings are
    transparent; each window sits in its own black rubber gasket with rounded corners and red
    pillars between them, as on the ALX400."""
    ss = 2
    w, h = tx(L) * ss, tx(H) * ss
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    def X(x):          # bus x (m) -> texel column, as seen from outside the nearside
        return int(round((x - X0) * TPM * ss))

    def Y(y):
        return h - int(round(y * TPM * ss))

    def R(m):
        return int(round(m * TPM * ss))
    clear = (0, 0, 0, 0)
    body = (WALL if inside else RED) + (255,)
    d.rectangle((0, Y(H), w, Y(SKIRT)), fill=body)
    if not inside:
        d.rectangle((0, Y(SKIRT + 0.06), w, Y(SKIRT)), fill=(36, 36, 40, 255))      # rubbing strip
        d.rectangle((0, Y(LOW_WIN[0] - 0.24), w, Y(LOW_WIN[0] - 0.25)), fill=RED_DARK + (255,))   # panel seam
    else:
        d.rectangle((0, Y(LOWER_CEIL + 0.02), w, Y(LOW_WIN[1] + 0.02)), fill=CEILING + (255,))
        d.rectangle((0, Y(UPPER_FLOOR + 0.3), w, Y(UPPER_FLOOR)), fill=WALL_DARK + (255,))
        d.rectangle((0, Y(LOWER_FLOOR + 0.3), w, Y(LOWER_FLOOR)), fill=WALL_DARK + (255,))
        d.rectangle((0, Y(H), w, Y(UP_WIN[1] + 0.02)), fill=CEILING + (255,))
    if not inside:
        livery.side_design(img, X, Y, R, X0, X1, (LOW_WIN[1] + 0.06, UP_WIN[0] - 0.06))
    rubber = (GLASS_FRAME if not inside else (34, 56, 150)) + (255,)       # dark blue frames inside
    for xa, xb, ya, yb, n, kind in window_rows(nearside):
        # bonded glazing: one black band, the panes cut out with thin black pillars between
        g = 0.05
        d.rounded_rectangle((X(xa - g), Y(yb + g), X(xb + g), Y(ya - g)), radius=R(0.1), fill=rubber)
        step = (xb - xa) / n
        if kind == "cab":           # driver's window with the small signalling pane at the back
            cuts = [(xa, xa + (xb - xa) * 0.3), (xa + (xb - xa) * 0.3 + 0.07, xb)]
        else:
            cuts = [(xa + i * step + (0.035 if i else 0), xa + (i + 1) * step - (0.035 if i < n - 1 else 0)) for i in range(n)]
        for ca, cb in cuts:
            d.rounded_rectangle((X(ca), Y(yb), X(cb), Y(ya)), radius=R(0.05), fill=clear)
            if ya > 2.5:                  # small top hopper on the upper deck
                d.rectangle((X(ca), Y(yb - 0.16), X(cb), Y(yb - 0.175)), fill=rubber)
                if inside:                # its black frame, tipped in at the top
                    d.rectangle((X(ca), Y(yb), X(ca) + R(0.02), Y(yb - 0.16)), fill=(20, 20, 22, 255))
                    d.rectangle((X(cb) - R(0.02), Y(yb), X(cb), Y(yb - 0.16)), fill=(20, 20, 22, 255))
    if nearside:
        for a, b in (DOOR1, DOOR2):
            d.rounded_rectangle((X(a) - R(0.04), Y(DOOR_TOP + 0.05), X(b) + R(0.04), Y(SKIRT)), radius=R(0.05), fill=rubber)
            d.rectangle((X(a), Y(DOOR_TOP), X(b), Y(SKIRT)), fill=clear)
    # wheel arches: open, with a black trim round them
    for ax in (FRONT_AXLE, REAR_AXLE):
        r = R(WHEEL_R + 0.1)
        cx, cy = X(ax), Y(WHEEL_R)
        if not inside:
            t = R(0.045)
            d.ellipse((cx - r - t, cy - r - t, cx + r + t, cy + r + t), fill=(26, 26, 28, 255))
            d.rectangle((cx - r - t, cy, cx + r + t, Y(SKIRT)), fill=(26, 26, 28, 255))
        # open on both faces: a painted inside face showed through the arch and flickered on the tyre
        d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=clear)
        d.rectangle((cx - r, cy, cx + r, Y(0)), fill=clear)
    if not inside:
        def louvre(xa, xb, ya, yb, step=0.045):
            d.rectangle((X(xa), Y(yb), X(xb), Y(ya)), fill=(150, 12, 18, 255))
            x = xa + 0.02
            while x < xb - 0.02:
                d.rectangle((X(x), Y(yb - 0.02), X(x + 0.018), Y(ya + 0.02)), fill=(40, 6, 10, 255))
                x += step
        # black slatted engine vent low down at the back corner, both sides
        d.rectangle((X(-5.37), Y(1.28), X(-4.85), Y(0.62)), fill=(24, 24, 26, 255))
        x = -5.34
        while x < -4.88:
            d.rectangle((X(x), Y(1.24), X(x + 0.02), Y(0.66)), fill=(60, 60, 64, 255))
            x += 0.05
        # the OmniCity's engine air louvre at the back of the upper deck, behind the windows:
        # a black grille with vertical slats, both sides
        d.rounded_rectangle((X(X0 + 0.16), Y(UP_WIN[1] - 0.04), X(UP_SPAN[0] - 0.12), Y(UP_WIN[0] + 0.1)),
                            radius=R(0.04), fill=(26, 26, 28, 255))
        x = X0 + 0.2
        while x < UP_SPAN[0] - 0.17:
            d.rectangle((X(x), Y(UP_WIN[1] - 0.08), X(x + 0.025), Y(UP_WIN[0] + 0.14)), fill=(70, 70, 74, 255))
            x += 0.06
        if nearside:            # side destination box on the panel behind the front door
            sx, sy, sw, sh = DISPLAY_SIDE
            d.rounded_rectangle((X(sx - sw - 0.04), Y(sy + 0.04), X(sx + 0.04), Y(sy - sh - 0.04)), radius=R(0.04),
                                fill=(18, 18, 20, 255))
        else:
            d.rectangle((X(-5.35), Y(1.32), X(-4.1), Y(0.42)), outline=(140, 10, 16, 255), width=R(0.012))   # engine bay door
            d.rectangle((X(2.1), Y(0.88), X(2.27), Y(0.7)), outline=(140, 10, 16, 255), width=R(0.01))      # fuel flap
            d.rectangle((X(-1.95), Y(2.15), X(-1.25), Y(1.3)), outline=(140, 10, 16, 255), width=R(0.01))   # emergency door
        # transport emblem low on the panel just behind the exit door, both sides
        ex = DOOR2[1] + 0.5
        ered = livery.active() and livery.fade(ex, X0, X1) > 0.5
        emblem(d, X(ex), Y(0.92), R(0.27), fg=(livery.EMBLEM_RED if ered else (255, 255, 255)) + (255,))
        # small amber side repeater just ahead of the front wheel
        d.rectangle((X(3.52), Y(0.53), X(3.59), Y(0.47)), fill=(200, 110, 20, 255))
    if not inside:
        img = livery.recolour(img, livery.fade(X0 + (np.arange(w) + 0.5) / (TPM * ss), X0, X1))
    img = noise(img.resize((w // ss, h // ss), Image.LANCZOS), 2)
    # the inside faces are seen from inside, i.e. mirrored
    flip_x = (not nearside) ^ inside
    return img.transpose(Image.Transpose.FLIP_LEFT_RIGHT) if flip_x else img


FPM = 128          # texels per metre on the front and rear (finer detail)


def fx(m):
    return int(round(m * FPM))


def front_panel(inside):
    """OmniCity front, seen from in front (offside on the viewer's left): one big upper window
    and the shallow destination box in one black surround, a red band, the windscreen in its
    own black surround, a black strip with the fleet number, the red lower panel with a slatted
    grille, small headlamp clusters in the lower corners and the red bumper with the plate."""
    ss = 2
    w, h = fx(W) * ss, fx(H) * ss
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    def Z(z):
        return int((ZO - z) * FPM * ss)

    def Y(y):
        return h - int(y * FPM * ss)

    def R(m):
        return int(m * FPM * ss)
    clear = (0, 0, 0, 0)
    zw = W / 2 - 0.05
    if inside:
        d.rectangle((0, 0, w, h), fill=WALL + (255,))
        d.rectangle((0, Y(FRONT_WIN[1]), w, Y(FRONT_WIN[0])), fill=clear)
        d.rectangle((0, Y(SCREEN[1]), w, Y(SCREEN[0])), fill=clear)
        d.rectangle((0, Y(DEST[1]), w, Y(DEST[0])), fill=(40, 40, 44, 255))
        return noise(img.resize((w // ss, h // ss), Image.LANCZOS), 2)
    d.rectangle((0, Y(H), w, Y(SKIRT)), fill=RED + (255,))
    black = (16, 16, 18, 255)
    # upper window and destination box in one black surround, the window's top corners well rounded
    d.rounded_rectangle((Z(zw + 0.03), Y(FRONT_WIN[1] + 0.04), Z(-zw - 0.03), Y(DEST[0] - 0.03)), radius=R(0.16), fill=black)
    d.rounded_rectangle((Z(zw - 0.02), Y(FRONT_WIN[1]), Z(-zw + 0.02), Y(FRONT_WIN[0])), radius=R(0.14), fill=clear)
    d.rectangle((Z(zw - 0.02), Y(FRONT_WIN[0] + 0.1), Z(-zw + 0.02), Y(FRONT_WIN[0])), fill=clear)
    d.rectangle((0, Y(DEST[1] + 0.03), w, Y(DEST[0] - 0.03)), fill=(26, 26, 28, 255))
    # windscreen in its own black surround, with a black strip under it for the fleet number
    d.rounded_rectangle((Z(zw + 0.03), Y(SCREEN[1] + 0.05), Z(-zw - 0.03), Y(0.98)), radius=R(0.07), fill=black)
    d.rounded_rectangle((Z(zw - 0.02), Y(SCREEN[1]), Z(-zw + 0.02), Y(SCREEN[0])), radius=R(0.06), fill=clear)
    # silver grab rails up the upper deck corners
    for z in (zw + 0.02, -zw - 0.02):
        d.rectangle((Z(z) - R(0.015), Y(H - 0.06), Z(z) + R(0.015), Y(DEST[0])), fill=(196, 198, 202, 255))
    # slatted grille across the lower red panel, the emblem in the middle of it
    g0, g1 = GRILLE
    d.rounded_rectangle((Z(0.62), Y(g1), Z(-0.62), Y(g0)), radius=R(0.03), fill=RED_DARK + (255,))
    y = g1 - 0.03
    while y > g0 + 0.02:
        d.rectangle((Z(0.6), Y(y), Z(-0.6), Y(y - 0.022)), fill=(70, 10, 14, 255))
        y -= 0.045
    emblem(d, Z(0.0), Y((g0 + g1) / 2), R(0.07), bg=RED_DARK + (255,))
    d.rectangle((0, Y(BUMPER_TOP + 0.13), w, Y(BUMPER_TOP + 0.12)), fill=RED_DARK + (255,))   # crease
    # small headlamp clusters low in the corners: a grey pod with two round lamps, the amber
    # indicator above it on the corner
    for sz in (1, -1):
        za, zb = sorted((Z(sz * 0.72), Z(sz * 1.14)))
        d.rounded_rectangle((za, Y(0.73), zb, Y(0.55)), radius=R(0.05), fill=(130, 134, 140, 255))
        d.rounded_rectangle((za + R(0.012), Y(0.718), zb - R(0.012), Y(0.562)), radius=R(0.04), fill=(196, 200, 206, 255))
        for (lz, ly, lr) in LAMPS:
            cx, cy = Z(sz * lz), Y(ly)
            d.ellipse((cx - R(lr + 0.012), cy - R(lr + 0.012), cx + R(lr + 0.012), cy + R(lr + 0.012)), fill=(110, 112, 116, 255))
            col = (236, 140, 30) if ly > 0.75 else (236, 238, 234)
            d.ellipse((cx - R(lr), cy - R(lr), cx + R(lr), cy + R(lr)), fill=col + (255,))
            d.ellipse((cx - R(lr * 0.5), cy - R(lr * 0.6), cx, cy - R(lr * 0.1)), fill=(252, 252, 250, 255))
    d.rectangle((Z(0.26), Y(0.44), Z(-0.26), Y(0.32)), fill=(242, 242, 238, 255))       # plate (text drawn live)
    return noise(img.resize((w // ss, h // ss), Image.LANCZOS), 2)


# headlamp cluster lamps (|z|, height, radius): indicator above, main headlamp, sidelight
LAMPS = ((1.08, 0.8, 0.045), (1.0, 0.64, 0.058), (0.82, 0.64, 0.05))


REAR_WIN = (3.4, 4.06)
REAR_BOX = (-0.36, 0.36, 2.82, 3.12)       # route number box: z from, z to, y from, y to
REAR_CLUSTER = (0.86, 1.1, 0.62, 1.44)     # |z| from, |z| to, y from, y to: a column of round lamps
# round rear lamps up the cluster, top to bottom: brake, indicator, reverse, tail / brake
REAR_LAMPS = ((1.32, "brake"), (1.12, "indicator"), (0.92, "reverse"), (0.72, "tail"))
REAR_LAMP_R = 0.085
REAR_LAMP_Z = 0.98


def rear_panel(inside):
    """OmniCity rear, as seen from behind (nearside on the viewer's left): a wide upper window,
    the route number box under it, a tall panel over the engine bay (with a made-up advert),
    columns of round lamps in black surrounds at the corners, the plate, a silver handle on the
    engine cover and a slatted grille underneath."""
    ss = 2
    w, h = fx(W) * ss, fx(H) * ss
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    def Z(z):        # drawn nearside-left, then mirrored to match the face's UV (checked in game)
        return int((z - ZN) * FPM * ss)

    def Y(y):
        return h - int(y * FPM * ss)

    def R(m):
        return int(m * FPM * ss)
    clear = (0, 0, 0, 0)
    if inside:
        d.rectangle((0, 0, w, h), fill=WALL + (255,))
        d.rounded_rectangle((Z(-0.95), Y(REAR_WIN[1]), Z(0.95), Y(REAR_WIN[0])), radius=R(0.1), fill=clear)
        return noise(img.resize((w // ss, h // ss), Image.LANCZOS), 2).transpose(Image.Transpose.FLIP_LEFT_RIGHT)
    d.rectangle((0, Y(H), w, Y(SKIRT)), fill=RED + (255,))
    livery.rear_design(img, Z, Y, R)
    d.rounded_rectangle((Z(-1.0), Y(REAR_WIN[1] + 0.05), Z(1.0), Y(REAR_WIN[0] - 0.05)), radius=R(0.14), fill=BLACK + (255,))
    d.rounded_rectangle((Z(-0.95), Y(REAR_WIN[1]), Z(0.95), Y(REAR_WIN[0])), radius=R(0.1), fill=clear)
    d.rectangle((Z(-0.25), Y(4.18), Z(0.25), Y(4.13)), fill=(150, 14, 18, 255))       # high level brake light
    za, zb, ya, yb = REAR_BOX
    d.rounded_rectangle((Z(za) - R(0.04), Y(yb) - R(0.04), Z(zb) + R(0.04), Y(ya) + R(0.04)), radius=R(0.04), fill=BLACK + (255,))
    # engine bay: a recessed panel outline, the plate, the engine cover and the grille
    d.rounded_rectangle((Z(-0.78), Y(2.35), Z(0.78), Y(0.62)), radius=R(0.05), outline=(140, 10, 16, 255), width=R(0.014))
    d.rectangle((Z(-0.6), Y(2.1), Z(0.6), Y(1.35)), fill=(236, 236, 230, 255))        # advert frame (made-up advert)
    d.rectangle((Z(-0.58), Y(2.08), Z(0.58), Y(1.37)), fill=(40, 120, 190, 255))
    d.rectangle((Z(-0.58), Y(1.62), Z(0.58), Y(1.37)), fill=(250, 214, 60, 255))
    d.ellipse((Z(0.12), Y(2.0), Z(0.5), Y(1.68)), fill=(240, 240, 236, 255))
    d.rectangle((Z(-0.5), Y(1.95), Z(-0.05), Y(1.86)), fill=(250, 250, 250, 255))
    d.rectangle((Z(-0.26), Y(1.0), Z(0.26), Y(0.87)), fill=YELLOW + (255,))            # plate (text drawn live)
    d.rounded_rectangle((Z(-0.3), Y(1.24), Z(0.3), Y(1.19)), radius=R(0.02), fill=(196, 198, 202, 255))   # engine cover handle
    d.rectangle((0, Y(0.6), w, Y(SKIRT)), fill=(24, 24, 26, 255))                     # bumper
    for i in range(4):                                                                  # grille slats
        y = 0.8 - i * 0.045
        d.rectangle((Z(-0.6), Y(y), Z(0.6), Y(y - 0.018)), fill=(70, 12, 16, 255))
    # columns of round lamps in black surrounds at the corners: brake, indicator, reverse, tail
    a0, a1, y0, y1 = REAR_CLUSTER
    unlit = {"brake": (170, 16, 20), "indicator": (240, 150, 30), "reverse": (236, 236, 236), "tail": (150, 14, 18)}
    for side in (-1, 1):
        x0, x1 = sorted((Z(side * a0), Z(side * a1)))
        d.rounded_rectangle((x0, Y(y1), x1, Y(y0)), radius=R(0.1), fill=(30, 30, 32, 255))
        for ly, kind in REAR_LAMPS:
            cx, cy, r = Z(side * REAR_LAMP_Z), Y(ly), R(REAR_LAMP_R)
            d.ellipse((cx - r - R(0.01), cy - r - R(0.01), cx + r + R(0.01), cy + r + R(0.01)), fill=(150, 152, 156, 255))
            d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=unlit[kind] + (255,))
    img = livery.recolour(img, 1.0)
    return noise(img.resize((w // ss, h // ss), Image.LANCZOS), 2).transpose(Image.Transpose.FLIP_LEFT_RIGHT)


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
    for xf in (0.25, 0.62):        # roof hatches
        d.rectangle((tx(L * xf), tx(0.6), tx(L * xf) + tx(0.7), h - tx(0.6)), fill=(170, 16, 22, 255))
    xs = X0 + (np.arange(w) + 0.5) / TPM
    img = livery.recolour(img, livery.fade(xs[::-1] if ROOF_U_FRONT else xs, X0, X1))
    return noise(img, 3)


ROOF_U_FRONT = False   # True if column 0 of the roof texture is at the front of the bus (checked in game)


CPM = 128          # texels per metre up the corner strips


def corner_strip(kind, inside=False):
    """Texture for the rounded vertical corners, column 0 at the side edge and the last column
    at the front / rear edge. At the front the black surround and the windscreen and upper
    window glass carry round the corner, with the silver rail up the upper deck."""
    w, h = 64, int(round(H * CPM))
    img = Image.new("RGBA", (w, h), (WALL if inside else RED) + (255,))
    d = ImageDraw.Draw(img)

    def Y(y):
        return h - int(round(y * CPM))
    clear = (0, 0, 0, 0)
    if kind == "front":
        if inside:
            d.rectangle((int(w * 0.35), Y(SCREEN[1]), w, Y(SCREEN[0])), fill=clear)
            d.rectangle((int(w * 0.35), Y(FRONT_WIN[1]), w, Y(FRONT_WIN[0])), fill=clear)
            return noise(img, 2)
        d.rectangle((0, Y(BUMPER_TOP), w, Y(SKIRT)), fill=(22, 22, 24, 255))
        d.rectangle((int(w * 0.3), Y(FRONT_WIN[1] + 0.03), w, Y(DEST[0] - 0.03)), fill=(16, 16, 18, 255))
        d.rectangle((int(w * 0.3), Y(SCREEN[1] + 0.05), w, Y(0.98)), fill=(16, 16, 18, 255))
        d.rectangle((int(w * 0.4), Y(FRONT_WIN[1] - 0.1), w, Y(FRONT_WIN[0])), fill=clear)
        d.rectangle((int(w * 0.4), Y(SCREEN[1]), w, Y(SCREEN[0])), fill=clear)
        d.rectangle((int(w * 0.62), Y(H - 0.06), int(w * 0.72), Y(DEST[0])), fill=(196, 198, 202, 255))
        d.rectangle((int(w * 0.55), Y(0.73), w, Y(0.55)), fill=(130, 134, 140, 255))     # end of the headlamp pod
        d.rectangle((int(w * 0.6), Y(0.84), w, Y(0.76)), fill=(236, 140, 30, 255))       # indicator round the corner
    elif not inside:
        d.rectangle((0, Y(0.6), w, Y(SKIRT)), fill=(24, 24, 26, 255))
        d.rectangle((int(w * 0.6), Y(REAR_CLUSTER[3]), w, Y(REAR_CLUSTER[2])), fill=(30, 30, 32, 255))
    return noise(livery.recolour(img, 0.0 if kind == "front" else 1.0), 2)


def lit_panel(text, size, fg, bg):
    img = Image.new("RGBA", size, bg + (255,))
    d = ImageDraw.Draw(img)
    f = ImageFont.truetype(FONT, int(size[1] * 0.6))
    tw = d.textlength(text, font=f)
    d.text(((size[0] - tw) / 2, size[1] * 0.12), text, fill=fg + (255,), font=f)
    return img


def cove_texture():
    """Ceiling cove: light blue panels with advert frames along the bus."""
    w, h = tx(L), 24
    img = Image.new("RGBA", (w, h), WALL + (255,))
    d = ImageDraw.Draw(img)
    cols = [(230, 230, 220), (240, 200, 60), (90, 160, 210), (220, 90, 80), (120, 190, 120)]
    for i, x in enumerate(range(20, w - 60, 70)):
        d.rectangle((x, 3, x + 56, h - 4), fill=(200, 205, 210, 255))
        d.rectangle((x + 2, 5, x + 54, h - 6), fill=cols[i % len(cols)] + (255,))
        d.rectangle((x + 6, 8, x + 30, 11), fill=(40, 40, 40, 255))
    return noise(img, 2)


def lamp(on):
    """Round headlamp in a chrome ring, transparent outside the ring."""
    s = 64
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = s / 2
    for r, col in ((31, (196, 198, 202)), (27, (70, 72, 76)), (24, (255, 250, 220) if on else (214, 218, 216))):
        d.ellipse((c - r, c - r, c + r, c + r), fill=col + (255,))
    if not on:
        d.ellipse((c - 18, c - 18, c + 4, c + 4), fill=(240, 242, 240, 255))     # reflector highlight
        d.ellipse((c - 6, c - 6, c + 6, c + 6), fill=(180, 184, 186, 255))
    else:
        d.ellipse((c - 16, c - 16, c + 16, c + 16), fill=(255, 255, 248, 255))
    return img


def round_lamp(col):
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse((0, 0, 31, 31), fill=col + (255,))
    d.ellipse((6, 6, 25, 25), fill=tuple(min(255, c + 60) for c in col) + (255,))
    return img


def indicator(on):
    img = Image.new("RGBA", (32, 24), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((0, 0, 31, 23), radius=9, fill=(60, 60, 64, 255))
    d.rounded_rectangle((2, 2, 29, 21), radius=8, fill=((255, 180, 40) if on else (236, 140, 30)) + (255,))
    return img


def bumper_front():
    """Flat front of the bumper: dark, with fog lamps and the white plate (text drawn live)."""
    zf = W / 2 - RC_LOW
    s = FPM * 2
    w, h = int(2 * zf * s), int((BUMPER_TOP - SKIRT) * s)
    img = Image.new("RGBA", (w, h), RED + (255,))
    d = ImageDraw.Draw(img)

    def Z(z):
        return int((zf - z) * s)
    d.rectangle((0, int(h * 0.72), w, h), fill=(24, 24, 26, 255))         # black lip along the bottom

    def Y(y):
        return h - int((y - SKIRT) * s)
    # accessibility sticker on the nearside: the transport emblem and the wheelchair sign
    # (this texture runs the other way to the front panel: +z here is the nearside, checked in game)
    d.rectangle((Z(0.86), Y(0.5), Z(0.62), Y(0.38)), fill=(240, 240, 240, 255))
    d.rectangle((Z(0.84), Y(0.48), Z(0.74), Y(0.4)), fill=(30, 80, 170, 255))
    d.rectangle((Z(0.72), Y(0.48), Z(0.64), Y(0.4)), fill=RED + (255,))
    emblem(d, (Z(0.72) + Z(0.64)) / 2, Y(0.44), int(0.04 * s))
    d.rectangle((Z(0.26), Y(0.49), Z(-0.26), Y(0.38)), fill=(242, 242, 238, 255))
    return noise(img.resize((w // 2, h // 2), Image.LANCZOS), 2)


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
    a.add("corner_front", corner_strip("front"))
    a.add("corner_front_in", corner_strip("front", True))
    a.add("corner_rear", corner_strip("rear"))
    a.add("corner_rear_in", corner_strip("rear", True))
    a.add("bumper_front", bumper_front())
    a.add("cove", cove_texture())
    a.add("advert_near", advert_image("near"))
    a.add("advert_off", advert_image("off"))
    a.add("moquette", moquette())
    a.add("reader", bus_extras.reader_face(48))
    a.add("instruments", bus_extras.instrument_panel())
    a.add("cab_glass", bus_extras.cab_glass())
    a.add("carpet", carpet())
    a.add("headlamp", lamp(False))
    a.add("headlamp_on", lamp(True))
    a.add("indicator", indicator(False))
    a.add("indicator_on", indicator(True))
    a.add("brake_round_on", round_lamp((255, 30, 24)))
    a.add("amber_round_on", round_lamp((255, 170, 30)))
    a.add("white_round_on", round_lamp((255, 252, 230)))
    a.add("stopping_off", lit_panel("BUS STOPPING", (160, 24), (70, 20, 20), (20, 20, 22)))
    a.add("stopping_on", lit_panel("BUS STOPPING", (160, 24), (255, 60, 40), (30, 10, 10)))
    for name, col in (("red", RED), ("red_dark", RED_DARK), ("black", BLACK), ("wall", WALL), ("wall_dark", WALL_DARK),
                      ("ceiling", CEILING), ("orange", ORANGE), ("shell", SHELL), ("grey", GREY), ("chrome", CHROME),
                      ("yellow", YELLOW), ("tyre", (28, 28, 30)), ("hub", (170, 172, 176)), ("screen", (16, 36, 90)),
                      ("glass_door", (60, 70, 76)), ("lamp_on", (255, 252, 220)), ("lamp_off", (120, 120, 112)),
                      ("brake_on", (255, 40, 30)), ("brake_off", (110, 14, 16)), ("amber_on", (255, 170, 30)),
                      ("amber_off", (120, 70, 20)), ("white", (240, 240, 236)), ("bell", (210, 30, 30)),
                      ("cab", (60, 90, 150)), ("dash", (40, 42, 46)), ("rim", (150, 152, 156)), ("bumper", (26, 30, 40))):
        swatch(name, col)
    for i in range(GRAD_N):
        t = livery.fade(X0 + (i + 0.5) / GRAD_N * L, X0, X1) if livery.active() else 0.0
        swatch(f"grad{i}", livery.mix(RED, t))
    # door leaves: glazed almost top to bottom in black frames, with a rail across
    leaf = Image.new("RGBA", (40, 120), BLACK + (255,))
    ImageDraw.Draw(leaf).rounded_rectangle((5, 6, 34, 112), radius=3, fill=(0, 0, 0, 0))
    ImageDraw.Draw(leaf).rectangle((5, 66, 34, 70), fill=BLACK + (255,))
    a.add("door_leaf", leaf)
    a.pack()


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

# GeckoLib rotation senses (flip if a test render shows arcs bending the wrong way)
RX, RY = 1, 1
ADVERT_OFF_FLIP = False     # flip if the offside advert reads backwards in game
T = 0.035                   # panel thickness

# side textures are drawn front-right for the nearside outside; these ones were mirrored
SIDE_FLIP = {"side_near_out": False, "side_near_in": True, "side_off_out": True, "side_off_in": False}


def side_uv(region, xa, xb, ya, yb):
    """UV for the part of a side texture between x = xa..xb and y = ya..yb (metres)."""
    x, y, w, h = ATL.regions[region]
    c0, c1 = tx(xa - X0), tx(xb - X0)
    if SIDE_FLIP[region]:
        c0, c1 = w - c1, w - c0
    r0, r1 = h - tx(yb), h - tx(ya)
    return face_uv(region, sub=(c0, r0, max(1, c1 - c0), max(1, r1 - r0)))


def end_uv(region, zhalf, ya, yb):
    """UV for the middle of a front / rear texture: z = -zhalf..zhalf, y = ya..yb."""
    x, y, w, h = ATL.regions[region]
    s = w / W
    c0, c1 = int(round((W / 2 - zhalf) * s)), int(round((W / 2 + zhalf) * s))
    r0, r1 = h - int(round(yb * h / H)), h - int(round(ya * h / H))
    return face_uv(region, sub=(c0, r0, max(1, c1 - c0), max(1, r1 - r0)))


def strip_uv(region, i, n, ya, yb, flip):
    """Slice i of n of a corner strip texture (column 0 at the side edge), rows for y = ya..yb."""
    x, y, w, h = ATL.regions[region]
    c0, c1 = int(round(w * i / n)), int(round(w * (i + 1) / n))
    r0, r1 = h - int(round(yb * CPM)), h - int(round(ya * CPM))
    return face_uv(region, sub=(c0, r0, max(1, c1 - c0), max(1, r1 - r0)), flip_u=flip)


def corner(bone_name, cx, cz, sx, sz, y0, y1, r, region, region_in=None, n=5, t=T, top=None):
    """Quarter-round vertical corner centred on (cx, cz), radius r; sx/sz give the outward
    directions. Each segment shows its own slice of the strip texture so details can wrap
    round. t > T makes the segments reach inwards (for the stepped roof corners); top gives
    their upper faces a texture."""
    # which way u runs along the arc on the outward face (worked out from the side panels:
    # u grows with x on north faces and against x on south faces)
    rev = sx * sz > 0
    out_face, in_face = ("north", "south") if sz < 0 else ("south", "north")
    ro = r + T / 2                      # outer surface
    rm = ro - t / 2                     # middle of the segment
    for i in range(n):
        a = math.radians(90 * (i + 0.5) / n)
        chord = 2 * ro * math.sin(math.radians(90 / n) / 2) + 0.012
        px, pz = cx + sx * rm * math.sin(a), cz + sz * rm * math.cos(a)
        ang = math.degrees(a) * sx * sz
        out_uv = strip_uv(region, i, n, y0, y1, rev) if region.startswith("corner") else region
        faces = {out_face: out_uv, "east": out_uv, "west": out_uv}
        if region_in:
            faces[in_face] = strip_uv(region_in, i, n, y0, y1, not rev) if region_in.startswith("corner") else region_in
        if top:
            faces["up"] = top
            faces["down"] = region_in or top
        cube(bone_name, (px - chord / 2, y0, pz - t / 2), (px + chord / 2, y1, pz + t / 2), faces,
             rotation=[0, RY * ang, 0], pivot=(px, 0, pz))


def arc_x(bone_name, x0, x1, cy, cz, r, phi0, phi1, n, region, t=T):
    """Curved panel running along x: quarter arc in the y-z plane around (cy, cz).
    phi measured from the -z (nearside) direction towards +y."""
    for i in range(n):
        a0 = phi0 + (phi1 - phi0) * i / n
        a1 = phi0 + (phi1 - phi0) * (i + 1) / n
        am = math.radians((a0 + a1) / 2)
        chord = 2 * r * math.sin(math.radians(abs(a1 - a0)) / 2) + 0.01
        py, pz = cy + r * math.sin(am), cz - r * math.cos(am)
        cube(bone_name, (x0, py - chord / 2, pz - t / 2), (x1, py + chord / 2, pz + t / 2),
             {"north": region, "south": "ceiling", "up": region, "down": region}, rotation=[RX * math.degrees(am), 0, 0], pivot=(0, py, pz))


def roof_corner(cx, cz, sx, sz, rc, region="red"):
    """Rounded roof corner: stacked rings that shrink as the roof edge curves in, each reaching
    in to the next so there are no gaps from above."""
    n = 4
    rr = R_ROOF
    for k in range(n):
        p0, p1 = 90 * k / n, 90 * (k + 1) / n
        y0 = H - rr + rr * math.sin(math.radians(p0))
        y1 = H - rr + rr * math.sin(math.radians(p1)) + 0.004
        r_out = rc - rr * (1 - math.cos(math.radians((p0 + p1) / 2)))
        r_next = rc - rr * (1 - math.cos(math.radians(min(90, p1 + 90 / n / 2)))) if k < n - 1 else 0.0
        t = max(T, r_out - r_next + T)
        corner("Roof", cx, cz, sx, sz, y0, y1, max(r_out, 0.01), region, "ceiling", n=4, t=min(t, r_out + T / 2), top=region)


def bands():
    """The front from the bumper up: (y0, y1, d, rc) with d the lean back and rc the corner radius."""
    out = []
    for y0, y1 in front_bands():
        ym = (y0 + y1) / 2
        out.append((y0, y1, front_d(ym), front_rc(ym)))
    return out


def shell():
    # flat sides between the corners
    side = (X0 + RC_REAR, X1 - SIDE_FRONT, SKIRT, H - R_ROOF)
    cube("Body", (side[0], SKIRT, ZN), (side[1], H - R_ROOF, ZN + T),
         {"north": side_uv("side_near_out", *side), "south": side_uv("side_near_in", *side)})
    cube("Body", (side[0], SKIRT, ZO - T), (side[1], H - R_ROOF, ZO),
         {"south": side_uv("side_off_out", *side), "north": side_uv("side_off_in", *side)})
    # the front, in bands: flat middle, two rounded corners and short side fillers
    for y0, y1, d, rc in bands():
        xf = X1 - d
        zh = W / 2 - rc
        cube("Body", (xf - T, y0, -zh), (xf, y1, zh), {"east": end_uv("front_out", zh, y0, y1), "west": end_uv("front_in", zh, y0, y1)})
        for zs, sz in ((ZN, -1), (ZO, 1)):
            corner("Body", xf - rc, zs - sz * rc, 1, sz, y0, y1, rc, "corner_front", "corner_front_in")
            xa, xb = X1 - SIDE_FRONT, xf - rc
            if xb - xa > 0.002:
                near = sz < 0
                zz = (ZN, ZN + T) if near else (ZO - T, ZO)
                o, i = ("side_near_out", "side_near_in") if near else ("side_off_out", "side_off_in")
                cube("Body", (xa, y0, zz[0]), (xb, y1, zz[1]),
                     {("north" if near else "south"): side_uv(o, xa, xb, y0, y1), ("south" if near else "north"): side_uv(i, xa, xb, y0, y1)})
    # the rear: flat middle and two rounded corners, full height
    zh = W / 2 - RC_REAR
    cube("Body", (X0, SKIRT, -zh), (X0 + T, H - R_ROOF, zh), {"west": end_uv("rear_out", zh, SKIRT, H - R_ROOF),
                                                              "east": end_uv("rear_in", zh, SKIRT, H - R_ROOF)})
    for zs, sz in ((ZN, -1), (ZO, 1)):
        corner("Body", X0 + RC_REAR, zs - sz * RC_REAR, -1, sz, SKIRT, H - R_ROOF, RC_REAR, "corner_rear", "corner_rear_in")
    # bumpers: a dark rubber moulding standing proud of the body, wrapping round the corners
    pb = 0.04
    for x, sx, rc, ytop in ((X1, 1, RC_LOW, BUMPER_TOP), (X0, -1, RC_REAR, 0.62)):
        zh = W / 2 - rc
        if sx > 0:
            cube("Body", (x - 0.02, SKIRT, -zh), (x + pb, ytop, zh), {"east": face_uv("bumper_front"), "up": "bumper", "down": "bumper"})
        else:
            solid("Body", (x - pb, SKIRT, -zh), (x + 0.02, ytop, zh), "bumper")
        for zs, sz in ((ZN, -1), (ZO, 1)):
            corner("Body", x - sx * rc, zs - sz * rc, sx, sz, SKIRT, ytop, rc + pb - T / 2, "bumper", "bumper", t=0.06, top="bumper")
            zz = (zs - pb, zs + 0.02) if sz < 0 else (zs - 0.02, zs + pb)
            xa, xb = sorted((x - sx * rc, x - sx * (rc + 0.5)))
            solid("Body", (xa, SKIRT, zz[0]), (xb, ytop, zz[1]), "bumper")
    # roof: flat top, curved side edges, front and rear domes, rounded corners
    xr0, xr1 = X0 + RC_REAR, X1 - D_TOP - RC_TOP
    cube("Roof", (xr0, H - T, ZN + R_ROOF), (xr1, H, ZO - R_ROOF), {"up": face_uv("roof_out"), "down": face_uv("roof_in")})
    cube("Roof", (X0 + R_ROOF, H - T, ZN + RC_REAR), (xr0, H, ZO - RC_REAR), {"up": grad_name(X0), "down": "ceiling"})
    # roof side edges in lengths, so each piece can take its part of the livery's fade
    nseg = GRAD_N
    cuts = [xr0 + (xr1 - xr0) * k / nseg for k in range(nseg + 1)]
    for xa, xb in zip(cuts, cuts[1:]):
        g = grad_name((xa + xb) / 2)
        arc_x("Roof", xa, xb + 0.002, H - R_ROOF, ZN + R_ROOF, R_ROOF, 0, 90, 4, g)
        for i in range(4):          # offside edge: same arc mirrored in z
            a0, a1 = 90 * i / 4, 90 * (i + 1) / 4
            am = math.radians((a0 + a1) / 2)
            chord = 2 * R_ROOF * math.sin(math.radians(11.25)) + 0.01
            py, pz = H - R_ROOF + R_ROOF * math.sin(am), ZO - R_ROOF + R_ROOF * math.cos(am)
            cube("Roof", (xa, py - chord / 2, pz - T / 2), (xb + 0.002, py + chord / 2, pz + T / 2),
                 {"south": g, "north": "ceiling", "up": g, "down": g}, rotation=[-RX * math.degrees(am), 0, 0], pivot=(0, py, pz))
    for x, sx, rc in ((X1 - D_TOP, 1, RC_TOP), (X0, -1, RC_REAR)):
        for i in range(4):
            a0, a1 = 90 * i / 4, 90 * (i + 1) / 4
            am = math.radians((a0 + a1) / 2)
            chord = 2 * R_ROOF * math.sin(math.radians(11.25)) + 0.01
            px, py = x - sx * R_ROOF + sx * R_ROOF * math.cos(am), H - R_ROOF + R_ROOF * math.sin(am)
            outer, inner = ("east", "west") if sx > 0 else ("west", "east")
            cube("Roof", (px - T / 2, py - chord / 2, ZN + rc), (px + T / 2, py + chord / 2, ZO - rc),
                 {outer: grad_name(x), inner: "ceiling", "up": grad_name(x), "down": grad_name(x)},
                 rotation=[0, 0, -sx * math.degrees(am)], pivot=(px, py, 0))
        for zs, sz in ((ZN, -1), (ZO, 1)):
            roof_corner(x - sx * rc, zs - sz * rc, sx, sz, rc, grad_name(x))
    # floors and decks, kept inside the rounded corners
    def slab(name, xa, xb, za, zb, y0, y1, faces, rf=RC_LOW, rr=RC_REAR):
        """A floor slab clipped to the rounded plan: where it reaches the front or back it is
        narrowed by the corner radius, with the full width only between the corners."""
        pieces = [(max(xa, X0 + rr), min(xb, X1 - rf), za, zb)]
        for end, r, cx in ((xa < X0 + rr, rr, X0 + rr), (xb > X1 - rf, rf, X1 - rf)):
            if not end:
                continue
            sx = 1 if cx > 0 else -1
            edge = xb if sx > 0 else xa
            pieces.append((min(cx, edge), max(cx, edge), max(za, ZN + r), min(zb, ZO - r)))
            # the corners themselves: a square reaching most of the way into the rounding
            q = 0.68 * r
            for zc, sz in ((ZN + r, -1), (ZO - r, 1)):
                if (sz < 0 and za < zc) or (sz > 0 and zb > zc):
                    pieces.append((min(cx, cx + sx * q), max(cx, cx + sx * q), min(zc, zc + sz * q), max(zc, zc + sz * q)))
        for a, b, c, e in pieces:
            if b - a > 0.001 and e - c > 0.001:
                cube(name, (a, y0, c), (b, y1, e), dict(faces))
    slab("Floor", X0 + T, X1 - T, ZN + T, ZO - T, 0.30, LOWER_FLOOR, {"up": "carpet", "down": "black"})
    walls = {"north": "wall_dark", "south": "wall_dark", "east": "wall_dark", "west": "wall_dark"}
    for (xa, xb, za, zb) in ((X0 + T, STAIRS[0], ZN + T, ZO - T), (STAIRS[1], CAB[0], ZN + T, ZO - T),
                             (STAIRS[0], STAIRS[1], ZN + T, STAIR_Z[0]), (CAB[0], X1 - T, ZN + T, ZO - T),
                             (STAIRS[0], STAIRS[0] + STAIR_COVER, STAIR_Z[0], ZO - T)):
        slab("Floor", xa, xb, za, zb, LOWER_CEIL, UPPER_FLOOR, {"up": "carpet", "down": "ceiling", **walls})
    slab("Floor", X0 + 0.12, X1 - 0.12, ZN + 0.12, ZO - 0.12, 0.18, 0.30, {f: "black" for f in ALL})
    # wheel arch housings inside
    # wheel arch housings inside: a hump over each wheel and an inner wall beyond the tyres,
    # kept clear of the wheels (boxes through the tyres flickered on them)
    for ax, depth in ((FRONT_AXLE, 0.42), (REAR_AXLE, 0.6)):
        for side in (-1, 1):
            if ax == FRONT_AXLE and side > 0:
                continue       # the offside front arch is under the stairs (it poked up through a step)
            edge = ZN + T if side < 0 else ZO - T
            inner = edge - side * depth
            za, zb = sorted((edge, inner))
            cube("Interior", (ax - 0.64, 1.04, za), (ax + 0.64, 1.14, zb),
                 {"up": "wall_dark", "down": "black", "east": "black", "west": "black", "north": "black", "south": "black"})
            wa, wb = sorted((inner, inner - side * 0.03))
            cube("Interior", (ax - 0.64, LOWER_FLOOR, wa), (ax + 0.64, 1.14, wb),
                 {f: ("black" if f in ("east", "west", "down", "north" if side < 0 else "south") else "wall_dark") for f in ALL})


def wheels():
    """16-sided tyres, steel rims with an 8-stud hub; twin wheels at the rear."""
    for name, ax, z in (("FrontLeftWheel", FRONT_AXLE, ZO - 0.2), ("FrontRightWheel", FRONT_AXLE, ZN + 0.2),
                        ("BackLeftWheel", REAR_AXLE, ZO - 0.33), ("BackRightWheel", REAR_AXLE, ZN + 0.33)):
        bone(name, "Wheels", (ax, WHEEL_R, z))
        twin = name.startswith("Back")
        zw = 0.56 if twin else 0.29
        za, zb = z - zw / 2, z + zw / 2
        r = WHEEL_R
        side = -1 if z < 0 else 1
        # round tyre: eight planks through the hub, 22.5 degrees apart, make a filled 16-gon
        # (turned squares would poke their corners out into a star)
        plank = r * math.tan(math.radians(11.25)) * 1.02
        for rot in (0, 22.5, 45, 67.5, 90, 112.5, 135, 157.5):
            cube(name, (ax - r, r - plank, za), (ax + r, r + plank, zb), {f: "tyre" for f in ALL},
                 rotation=[0, 0, rot], pivot=(ax, r, z), parent="Wheels")
        if twin:   # groove between the two tyres
            solid(name, (ax - r - 0.002, r - 0.06, z - 0.01), (ax + r + 0.002, r + 0.06, z + 0.01), "black", parent="Wheels")
        face_z = z + side * zw / 2
        # rim (octagonal steel disc), recessed hub, 8 studs
        rr = 0.27
        rp = rr * math.tan(math.radians(22.5)) * 1.02
        for rot in (0, 45, 90, 135):
            cube(name, (ax - rr, r - rp, min(face_z, face_z + side * 0.012)), (ax + rr, r + rp, max(face_z, face_z + side * 0.012)),
                 {f: "rim" for f in ALL}, rotation=[0, 0, rot], pivot=(ax, r, z), parent="Wheels")
        hz = face_z + side * 0.012
        hp = 0.12 * math.tan(math.radians(22.5)) * 1.02
        for rot in (0, 45, 90, 135):
            cube(name, (ax - 0.12, r - hp, min(hz, hz + side * 0.03)), (ax + 0.12, r + hp, max(hz, hz + side * 0.03)),
                 {f: "hub" for f in ALL}, rotation=[0, 0, rot], pivot=(ax, r, z), parent="Wheels")
        for k in range(8):
            a = math.radians(k * 45)
            sxp, syp = ax + 0.16 * math.cos(a), r + 0.16 * math.sin(a)
            cube(name, (sxp - 0.018, syp - 0.018, min(hz, hz + side * 0.045)), (sxp + 0.018, syp + 0.018, max(hz, hz + side * 0.045)),
                 {f: "chrome" for f in ALL}, parent="Wheels")
        # wheel-nut cover ring on the rear twins
        if twin:
            cube(name, (ax - 0.05, r - 0.05, min(hz, hz + side * 0.07)), (ax + 0.05, r + 0.05, max(hz, hz + side * 0.07)),
                 {f: "chrome" for f in ALL}, parent="Wheels")
    bone("Wheels")


def seat(name, x, z, floor, facing=1, width=0.44):
    """High-back bus seat: moquette cushion and back, blue plastic shell behind, grey grab
    loop on top, pedestal leg. facing 1 = towards the front."""
    f = facing
    w2 = width / 2 - 0.01
    cush = (floor + 0.42, floor + 0.52)
    back_x = x - f * 0.22
    front, rear = ("east", "west") if f > 0 else ("west", "east")
    # cushion: moquette top and front edge, plastic underneath
    cube(name, (x - 0.21, cush[0], z - w2), (x + 0.23, cush[1], z + w2),
         {"up": "moquette", front: "moquette", rear: "shell", "north": "shell", "south": "shell", "down": "shell"})
    # back: moquette facing the passenger, blue shell behind, slightly taller in the middle
    cube(name, (back_x - 0.035, cush[1], z - w2), (back_x + 0.035, floor + 1.08, z + w2),
         {front: "moquette", rear: "shell", "up": "shell", "north": "shell", "south": "shell"})
    cube(name, (back_x - 0.03, floor + 1.08, z - w2 + 0.05), (back_x + 0.03, floor + 1.13, z + w2 - 0.05),
         {f2: "shell" for f2 in ALL})
    # shell lip round the back edge
    cube(name, (back_x - f * 0.05, cush[1] + 0.05, z - w2 - 0.005), (back_x - f * 0.035, floor + 1.1, z + w2 + 0.005),
         {f2: "shell" for f2 in ALL})
    # grey grab loop: two uprights and a top rail
    for zz in (z - w2 + 0.07, z + w2 - 0.07):
        solid(name, (back_x - 0.016, floor + 1.13, zz - 0.016), (back_x + 0.016, floor + 1.2, zz + 0.016), "grey")
    solid(name, (back_x - 0.018, floor + 1.2, z - w2 + 0.054), (back_x + 0.018, floor + 1.235, z + w2 - 0.054), "grey")
    # pedestal leg and foot
    solid(name, (x - 0.03, floor, z - 0.03), (x + 0.03, cush[0], z + 0.03), "grey")
    solid(name, (x - 0.12, floor, z - 0.03), (x + 0.12, floor + 0.03, z + 0.03), "grey")


def pole(name, x, z, y0, y1, thick=0.04):
    solid(name, (x - thick / 2, y0, z - thick / 2), (x + thick / 2, y1, z + thick / 2), "orange")


def bell(name, x, z, y):
    solid(name, (x - 0.035, y, z - 0.035), (x + 0.035, y + 0.07, z + 0.035), "bell")


SEATS = []        # (right, height, forward, yaw, drop_right, drop_height, drop_forward)


def add_seat(x, z, floor, facing=1, yaw=None):
    seat("Seats", x, z, floor, facing, width=0.42)
    # a seated Minecraft body is 1 m wide across the arms and its legs reach 0.75 m forward, so
    # sit passengers back in the seat and window passengers a little inboard of the cushion
    pz = z - math.copysign(0.07, z) if abs(z) > 0.7 else z
    SEATS.append((-pz, floor + 0.52, x - facing * 0.08, (0.0 if facing > 0 else 180.0) if yaw is None else yaw,
                  0.0, floor, x))


def interior():
    zp = (-0.9, -0.47, 0.47, 0.9)         # seat centres across the bus, aisle in the middle
    # driver first: PTM2 treats seat 0 as the driver's seat
    SEATS.append((-0.72, LOWER_FLOOR + 0.57, 4.27 + FS, 0.0, -0.72, LOWER_FLOOR, 4.35 + FS))
    seat("Cab", 4.35 + FS, 0.72, LOWER_FLOOR + 0.05, 1, width=0.5)
    # cab: partition, dashboard, steering wheel, ticket machine
    # the cab is boxed in: a back wall up to the ceiling, and on the gangway side a solid panel
    # with glass above it up to the ceiling, framed
    solid("Cab", (CAB[0], LOWER_FLOOR, 0.2), (CAB[0] + 0.05, LOWER_CEIL, ZO - 0.04), "cab")
    solid("Cab", (CAB[0], LOWER_FLOOR, 0.17), (X1 - 0.34, 1.25, 0.22), "cab")
    cube("Cab", (CAB[0] + 0.05, 1.25, 0.185), (X1 - 0.36, LOWER_CEIL, 0.205), {"north": "cab_glass", "south": "cab_glass"})
    solid("Cab", (CAB[0], LOWER_CEIL - 0.05, 0.17), (X1 - 0.34, LOWER_CEIL, 0.22), "cab")
    solid("Cab", (X1 - 0.38, 1.25, 0.17), (X1 - 0.34, LOWER_CEIL, 0.22), "cab")
    solid("Cab", (CAB[0] + 0.6, 1.25, 0.175), (CAB[0] + 0.63, LOWER_CEIL, 0.215), "cab")
    # dashboard: a shelf right across under the windscreen, lower in front of the driver so
    # the driver's legs (straight out, about 0.86-1.08 m up) pass over it; the binnacle with the
    # instruments sits above the legs behind the wheel, and a switch panel by the offside window
    solid("Cab", (X1 - 0.34, 0.82, ZN + 0.18), (X1 - 0.06, 1.04, ZO - 0.9), "dash")
    solid("Cab", (X1 - 0.36, 1.04, ZN + 0.2), (X1 - 0.08, 1.07, ZO - 0.92), "grey")
    solid("Cab", (X1 - 0.2, 0.6, ZO - 0.9), (X1 - 0.06, 0.84, ZO - 0.06), "dash")
    solid("Cab", (X1 - 0.24, 1.12, ZO - 0.9), (X1 - 0.06, 1.18, ZO - 0.06), "grey")
    solid("Cab", (X1 - 0.27, 1.12, ZO - 0.82), (X1 - 0.12, 1.36, ZO - 0.12), "dash")
    cube("Cab", (X1 - 0.29, 1.15, ZO - 0.78), (X1 - 0.27, 1.34, ZO - 0.16), {"west": "instruments"})
    solid("Cab", (X1 - 0.31, 1.36, ZO - 0.84), (X1 - 0.12, 1.4, ZO - 0.1), "black")
    solid("Cab", (4.0 + FS, LOWER_FLOOR + 0.35, ZO - 0.16), (X1 - 0.5, 0.84, ZO - 0.05), "dash")
    solid("Cab", (4.05 + FS, 0.84, ZO - 0.15), (X1 - 0.55, 0.86, ZO - 0.06), "black")
    # steering wheel: a round rim with three spokes and a hub on its column, tilted back like a
    # bus wheel; the renderer turns the SteeringWheel bone about its own axis
    hub = (X1 - 0.4, 1.3, 0.72)
    bone("SteeringTilt", "Vehicle", hub, rotation=[0, 0, STEER_TILT])
    bone("SteeringWheel", "SteeringTilt", hub)
    solid("SteeringTilt", (hub[0] - 0.035, hub[1] - 0.16, hub[2] - 0.035), (hub[0] + 0.035, hub[1] - 0.02, hub[2] + 0.035), "black")
    r, seg = 0.23, 2 * math.pi * 0.23 / 16 * 1.12
    for i in range(16):
        cube("SteeringWheel", (hub[0] + r - 0.017, hub[1] - 0.017, hub[2] - seg / 2),
             (hub[0] + r + 0.017, hub[1] + 0.017, hub[2] + seg / 2), {f: "black" for f in ALL},
             rotation=[0, i * 22.5, 0], pivot=hub)
    for a in (90, 210, 330):
        cube("SteeringWheel", (hub[0], hub[1] - 0.01, hub[2] - 0.022), (hub[0] + r, hub[1] + 0.01, hub[2] + 0.022),
             {f: "grey" for f in ALL}, rotation=[0, a, 0], pivot=hub)
    solid("SteeringWheel", (hub[0] - 0.05, hub[1] - 0.02, hub[2] - 0.05), (hub[0] + 0.05, hub[1] + 0.025, hub[2] + 0.05), "black")
    solid("Cab", (4.25 + FS, 1.15, 0.05), (4.45 + FS, 1.45, 0.2), "dash")          # ticket machine
    # card reader pod on the cab screen behind it, its yellow ring facing the entrance (the
    # layout's validator is here, so card taps on the bus are made at this spot)
    solid("Cab", (READER[0] - 0.13, READER[1] - 0.13, 0.05), (READER[0] + 0.13, READER[1] + 0.13, 0.17), "dash")
    cube("Cab", (READER[0] - 0.11, READER[1] - 0.11, 0.035), (READER[0] + 0.11, READER[1] + 0.11, 0.05), {"north": "reader"})
    # lower deck: perch seats over the front wheel arch (nearside), facing across the bus
    for x in (2.45 + FS, 2.95 + FS):
        seat("Seats", x, ZN + 0.68, LOWER_FLOOR + 0.25, 1)
        SEATS.append((-(ZN + 0.68), LOWER_FLOOR + 0.77, x, -90.0, 0.0, LOWER_FLOOR, x))
    # forward-facing pairs between the doors (the stairs take the offside front)
    for x in (1.6 + FS, 0.8 + FS, 0.0 + FS, -0.45):        # one more row: the OmniCity is longer
        for z in zp[:2]:
            add_seat(x, z, LOWER_FLOOR)
    for x in (0.8 + FS, 0.0 + FS):
        for z in zp[2:]:
            add_seat(x, z, LOWER_FLOOR)
    # wheelchair bay opposite the centre door: blue backboard and a rail
    solid("Interior", (DOOR2[0], LOWER_FLOOR + 0.3, ZO - 0.12), (DOOR2[1], 1.6, ZO - 0.05), "shell")
    solid("Interior", (DOOR2[0], 1.0, ZO - 0.2), (DOOR2[1], 1.04, ZO - 0.12), "orange")
    # rear saloon behind the centre door, as on London OmniCitys: a raised box each side over
    # the rear axle with two forward-facing pairs, open to the gangway; then the gangway steps
    # up twice to the back row
    x0, x1, ph = POD
    top = LOWER_FLOOR + ph
    for side in (-1, 1):
        wall = ZN + T if side < 0 else ZO - T
        za, zb = sorted((wall, side * POD_PANEL))
        cube("Interior", (x0, LOWER_FLOOR, za), (x1, top, zb),
             {"up": "carpet", "east": "wall_dark", "north": "wall_dark", "south": "wall_dark", "west": "wall_dark"})
        solid("Interior", (x1 - 0.04, top - 0.03, za), (x1 + 0.004, top + 0.003, zb), "yellow")     # nosing
        # yellow nosing along the gangway edge too: the pairs are open to the gangway
        na, nb = sorted((side * POD_PANEL, side * (POD_PANEL - 0.004)))
        solid("Interior", (x0, top - 0.03, na), (x1, top + 0.003, nb), "yellow")
        for x in POD_ROWS:
            for z in POD_Z:
                add_seat(x, side * z, top)
    for xa, xb, h in REAR_STEPS:
        cube("Interior", (xa, LOWER_FLOOR, ZN + 0.12), (xb, LOWER_FLOOR + h, ZO - 0.12),
             {"up": "carpet", "east": "wall_dark", "north": "wall_dark", "south": "wall_dark", "west": "wall_dark"})
        solid("Interior", (xb - 0.04, LOWER_FLOOR + h - 0.03, ZN + 0.12), (xb + 0.004, LOWER_FLOOR + h + 0.003, ZO - 0.12),
              "yellow")
    # back row: one seat at the end of the gangway and two to its nearside; the offside rear
    # corner is a tall panelled cupboard over the engine, floor to ceiling
    for z in (-0.84, -0.42, 0.0):
        add_seat(BACK_ROW, z, LOWER_FLOOR + REAR_STEPS[-1][2])
    cx0, cx1, cz0 = X0 + 0.14, POD[0] - 0.02, 0.25
    solid("Interior", (cx0, LOWER_FLOOR, cz0), (cx1, LOWER_CEIL, ZO - 0.12), "wall_dark")
    cube("Interior", (cx1 + 0.002, 0.9, cz0 + 0.12), (cx1 + 0.006, 1.9, ZO - 0.25), {"east": "wall"})        # access panel
    for xa, xb in ((cx0 + 0.15, (cx0 + cx1) / 2 - 0.05), ((cx0 + cx1) / 2 + 0.05, cx1 - 0.15)):
        cube("Interior", (xa, 0.7, cz0 - 0.006), (xb, 1.9, cz0 - 0.002), {"north": "wall"})
    # lower deck poles and bells
    for x, z in ((DOOR1[0] - 0.05, ZN + 0.35), (DOOR1[1] - 0.15, ZN + 0.3), (DOOR2[0] - 0.05, ZN + 0.32), (DOOR2[1] + 0.05, ZN + 0.32),
                 (DOOR2[1] + 0.05, -0.3), (0.0 + FS, 0.3), (1.6 + FS, -0.3)):
        pole("Interior", x, z, LOWER_FLOOR, LOWER_CEIL)
        bell("Interior", x, z, 1.45)
    # poles at the front and back corners of the raised boxes, on the gangway side
    for x, z in ((POD[1] + 0.03, -POD_PANEL + 0.02), (POD[1] + 0.03, POD_PANEL - 0.02),
                 (POD[0] - 0.03, -POD_PANEL + 0.02), (POD[0] - 0.03, POD_PANEL - 0.02)):
        y0 = LOWER_FLOOR + (REAR_STEPS[0][2] if x < POD[0] else 0.0)
        pole("Interior", x, z, y0, LOWER_CEIL)
        bell("Interior", x, z, 1.45)
    for z in (-0.45, 0.45):
        solid("Interior", (X0 + 0.4, LOWER_CEIL - 0.08, z - 0.02), (CAB[0], LOWER_CEIL - 0.04, z + 0.02), "orange")
    # staircase on the offside behind the cab, rising towards the front (as on London buses):
    # you step on at the back end or from the gangway beside the lower steps, and come out at
    # the top just behind the cab
    n = 9
    run = (STAIRS[1] - STAIRS[0]) / n
    rise = (UPPER_FLOOR - LOWER_FLOOR) / n
    for i in range(n):
        xa = STAIRS[0] + i * run
        top = LOWER_FLOOR + (i + 1) * rise
        cube("Stairs", (xa, LOWER_FLOOR, STAIR_Z[0]), (xa + run, top, STAIR_Z[1]),
             {"up": "carpet", "west": "yellow", "north": "wall_dark", "south": "wall_dark", "east": "wall_dark"})
    # lower deck: a solid wall right across the back end of the stairs (the seats behind see a
    # panel, not the steps); on the gangway side the bottom three steps are open to step on,
    # then a kick panel with glass above up to the ceiling runs on to the cab, as on the photos,
    # with a grab pole where it starts and a handrail following the stairs
    solid("Stairs", (STAIRS[0] - 0.04, LOWER_FLOOR, STAIR_Z[0] - 0.04), (STAIRS[0], LOWER_CEIL, ZO - 0.04), "wall")
    solid("Stairs", (STAIRS[0] - 0.07, 0.95, STAIR_Z[0] + 0.05), (STAIRS[0] - 0.04, 0.99, ZO - 0.1), "orange")
    open_x = STAIRS[0] + 3 * run
    solid("Stairs", (open_x, LOWER_FLOOR, STAIR_Z[0] - 0.04), (CAB[0], LOWER_FLOOR + 0.3, STAIR_Z[0]), "wall")
    cube("Stairs", (open_x, LOWER_FLOOR + 0.3, STAIR_Z[0] - 0.03), (CAB[0], LOWER_CEIL, STAIR_Z[0] - 0.01),
         {"north": "cab_glass", "south": "cab_glass"})
    solid("Stairs", (open_x - 0.02, LOWER_FLOOR, STAIR_Z[0] - 0.05), (open_x + 0.02, LOWER_CEIL, STAIR_Z[0] - 0.01), "orange")
    slope = math.atan2(UPPER_FLOOR - LOWER_FLOOR, STAIRS[1] - STAIRS[0])
    span = (STAIRS[1] - STAIRS[0]) / math.cos(slope)
    mx, my = (STAIRS[0] + STAIRS[1]) / 2, (LOWER_FLOOR + UPPER_FLOOR) / 2 + 0.92
    # handrail on the side wall beside the stairs, on brackets (it used to float over the open steps)
    rz = ZO - 0.04 - 0.07
    solid("Stairs", (mx - span / 2, my - 0.02, rz - 0.02), (mx + span / 2, my + 0.02, rz + 0.02), "orange",
          rotation=[0, 0, STAIR_RAIL_SENSE * math.degrees(slope)], pivot=(mx, my, rz))
    for t in (0.15, 0.5, 0.85):
        bx = STAIRS[0] + t * (STAIRS[1] - STAIRS[0])
        by = my + (bx - mx) * math.tan(slope)
        solid("Stairs", (bx - 0.02, by - 0.02, rz), (bx + 0.02, by + 0.02, ZO - 0.04), "grey")
    # upper deck: a guard round the stairwell, open at the top (front) end on the gangway side:
    # a light blue panel with an orange rail standing on posts above it, as on the photos
    gx = STAIRS[0] + STAIR_COVER
    gend = STAIRS[1] - 0.75
    ph, rh = 0.78, 1.04                    # panel top, rail top
    solid("Stairs", (gx, UPPER_FLOOR, STAIR_Z[0] - 0.04), (gend, UPPER_FLOOR + ph, STAIR_Z[0]), "wall")
    solid("Stairs", (gx - 0.04, UPPER_FLOOR, STAIR_Z[0] - 0.04), (gx, UPPER_FLOOR + ph, ZO - 0.04), "wall")
    solid("Stairs", (gx, UPPER_FLOOR + rh - 0.04, STAIR_Z[0] - 0.04), (gend, UPPER_FLOOR + rh, STAIR_Z[0]), "orange")
    solid("Stairs", (gx - 0.04, UPPER_FLOOR + rh - 0.04, STAIR_Z[0] - 0.04), (gx, UPPER_FLOOR + rh, ZO - 0.04), "orange")
    for x in (gx + 0.02, (gx + gend) / 2, gend - 0.02):
        solid("Stairs", (x - 0.016, UPPER_FLOOR + ph, STAIR_Z[0] - 0.036), (x + 0.016, UPPER_FLOOR + rh, STAIR_Z[0] - 0.004), "orange")
    for z in ((STAIR_Z[0] + ZO) / 2, ZO - 0.08):
        solid("Stairs", (gx - 0.036, UPPER_FLOOR + ph, z - 0.016), (gx - 0.004, UPPER_FLOOR + rh, z + 0.016), "orange")
    pole("Stairs", gend + 0.02, STAIR_Z[0] - 0.02, UPPER_FLOOR, UPPER_CEIL)
    solid("Stairs", (gend - 0.02, UPPER_CEIL - 0.06, STAIR_Z[0] - 0.06), (gend + 0.06, UPPER_CEIL, STAIR_Z[0] + 0.02), "dash")
    # the light blue box hung from the ceiling over the stairwell, two downlights underneath and
    # a dark grille on its gangway face
    hx0, hx1 = gx, STAIRS[1] + 0.2
    solid("Stairs", (hx0, STAIR_HOOD, STAIR_Z[0] - 0.08), (hx1, UPPER_CEIL, ZO - T), "wall")
    for f in (0.3, 0.7):
        lx = hx0 + (hx1 - hx0) * f
        cube("Stairs", (lx - 0.06, STAIR_HOOD - 0.006, STAIR_Z[0] + 0.25), (lx + 0.06, STAIR_HOOD, STAIR_Z[0] + 0.37),
             {"down": "lamp_on", "north": "grey", "south": "grey", "east": "grey", "west": "grey"})
    solid("Stairs", (hx0 + 0.3, STAIR_HOOD + 0.08, STAIR_Z[0] - 0.086), (hx1 - 0.3, STAIR_HOOD + 0.17, STAIR_Z[0] - 0.08), "dash")
    # upper deck: forward-facing pairs, front row at the big front windows, rear bench of five
    xs = [4.5 + FS - 0.8 * k for k in range(12)]
    for x in xs:
        for z in zp:
            if z > 0 and STAIRS[0] - 0.2 < x < STAIRS[1] + 0.25:
                continue           # stairwell
            add_seat(x, z, UPPER_FLOOR)
    xa, xb, sh = UP_REAR_STEP
    cube("Interior", (xa, UPPER_FLOOR, ZN + 0.12), (xb, UPPER_FLOOR + sh, ZO - 0.12),
         {"up": "carpet", "east": "wall_dark", "north": "wall_dark", "south": "wall_dark", "west": "wall_dark"})
    solid("Interior", (xb - 0.03, UPPER_FLOOR + sh - 0.02, ZN + 0.12), (xb + 0.004, UPPER_FLOOR + sh + 0.003, ZO - 0.12), "grey")
    for z in (-0.84, -0.42, 0.0, 0.42, 0.84):
        add_seat(X0 + 0.46, z, UPPER_FLOOR + sh)
    # upper deck curved poles from seat backs to the ceiling and the front rail
    for x in xs[1::2]:
        for z in (-0.3, 0.3):
            if z > 0 and STAIRS[0] - 0.2 < x < STAIRS[1] + 0.25:
                continue
            top = UPPER_CEIL - 0.32
            pole("Interior", x - 0.22, z, UPPER_FLOOR + 1.2, top)
            sz = 1 if z > 0 else -1
            cube("Interior", (x - 0.24, top - 0.02, z - 0.02), (x - 0.2, top + 0.38, z + 0.02), {f2: "orange" for f2 in ALL},
                 rotation=[-sz * RX * 35, 0, 0], pivot=(x - 0.22, top, z))
            bell("Interior", x - 0.22, z, UPPER_FLOOR + 1.45)
            # dark grey clamps: on the seat back, at the bend and where it meets the ceiling
            for cy in (UPPER_FLOOR + 1.2, top - 0.03):
                solid("Interior", (x - 0.25, cy, z - 0.03), (x - 0.19, cy + 0.06, z + 0.03), "dash")
            ez = z + sz * 0.38 * math.sin(math.radians(35))     # the top leans out towards the windows
            solid("Interior", (x - 0.26, UPPER_CEIL - 0.05, ez - 0.05), (x - 0.18, UPPER_CEIL, ez + 0.05), "dash")
    # front handrail, seen across the upper front window as on the photos
    solid("Interior", (X1 - 0.33, UPPER_FLOOR + 1.08, ZN + 0.25), (X1 - 0.28, UPPER_FLOOR + 1.13, ZO - 0.25), "orange")
    for z in (ZN + 0.27, 0.0, ZO - 0.27):          # posts at the ends and in the middle, up to the ceiling
        solid("Interior", (X1 - 0.33, UPPER_FLOOR, z - 0.025), (X1 - 0.28, UPPER_CEIL, z + 0.025), "orange")
        solid("Interior", (X1 - 0.34, UPPER_FLOOR + 1.06, z - 0.035), (X1 - 0.27, UPPER_FLOOR + 1.15, z + 0.035), "dash")
    # header across the top of the front window, a small light in the middle
    solid("Interior", (X1 - 0.24, 4.03, ZN + 0.2), (X1 - 0.1, UPPER_CEIL, ZO - 0.2), "wall")
    cube("Interior", (X1 - 0.246, 4.07, -0.12), (X1 - 0.24, 4.12, 0.12), {"west": "lamp_on"})
    # CCTV domes on the upper deck ceiling, front and back
    for x, z in ((X1 - 1.2, -0.55), (X0 + 1.4, 0.55)):
        solid("Interior", (x - 0.06, UPPER_CEIL - 0.03, z - 0.06), (x + 0.06, UPPER_CEIL, z + 0.06), "white")
        solid("Interior", (x - 0.04, UPPER_CEIL - 0.07, z - 0.04), (x + 0.04, UPPER_CEIL - 0.03, z + 0.04), "black")
    # ceiling coves both sides, both decks: angled panels with advert frames and a light strip
    for y_top, y_low, xa, xb in ((LOWER_CEIL, LOW_WIN[1] + 0.03, X0 + 0.4, CAB[0]), (UPPER_CEIL, UP_WIN[1] + 0.03, X0 + 0.4, X1 - 0.4)):
        gap = y_top - y_low
        for z, sign in ((ZN, 1), (ZO, -1)):
            cy = (y_top + y_low) / 2
            cz = z + sign * (T + gap / 2)
            cube("Interior", (xa, cy - gap * 0.75, cz - 0.012), (xb, cy + gap * 0.75, cz + 0.012),
                 {"north": "cove", "south": "cove", "up": "wall", "down": "wall"}, rotation=[sign * RX * 45, 0, 0],
                 pivot=(0, cy, cz))
            ly = y_top - 0.02
            lz = z + sign * (T + gap + 0.06)
            cube("Interior", (xa, ly - 0.025, lz - 0.05), (xb, ly, lz + 0.05), {"down": "lamp_on", "north": "wall", "south": "wall"})
    # inside displays: next stop screens facing the rear, lower and upper deck
    for name, x, y in (("Display5", STAIRS[0] - 0.09, LOWER_CEIL - 0.04), ("Display6", X1 - 0.42, UPPER_CEIL - 0.06)):
        w, h = 0.62, 0.3
        # the lower deck screen sits on top of the wall across the back of the stairs, facing
        # the seats behind (as on the photos); the upper deck one hangs at the front
        zc = (STAIR_Z[0] + ZO) / 2 if name == "Display5" else 0.0
        bone(name, "Interior", (x - 0.012, y, zc + w / 2))
        solid(name, (x, y - h - 0.02, zc - w / 2 - 0.02), (x + 0.04, y + 0.02, zc + w / 2 + 0.02), "black", parent="Interior")
    # BUS STOPPING signs (unlit face; the lit one slides forward when the bell has been rung)
    for x, y, z in ((0.0 + FS, LOWER_CEIL - 0.12, 0.0), (X1 - 0.43, UPPER_CEIL - 0.48, 0.0)):
        cube("Interior", (x, y, z - 0.3), (x + 0.03, y + 0.07, z + 0.3), {"west": "stopping_off", "east": "black", "up": "black",
                                                                           "down": "black", "north": "black", "south": "black"})
        cube("BusStopping", (x + 0.005, y + 0.002, z - 0.298), (x + 0.025, y + 0.068, z + 0.298), {"west": "stopping_on"},
             parent="Blinkers")


def doors():
    """Two-leaf glazed doors; each leaf folds inwards and slides towards its frame in the animation."""
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
    """Lit parts sit just behind their unlit lenses (painted on the front and rear); the
    animations push them out."""
    bone("Blinkers")
    for name in ("FrontLights", "StopLights", "BackLights"):
        bone(name, "Blinkers")
    xl = X1 - 0.012
    (iz, iy, ir), (hz, hy, hr), (sz_, sy, sr) = LAMPS
    for sz in (1, -1):
        for (lz, ly, lr) in ((hz, hy, hr), (sz_, sy, sr)):
            cube("FrontLights", (xl, ly - lr, sz * lz - lr), (xl + 0.002, ly + lr, sz * lz + lr), {"east": "headlamp_on"},
                 parent="Blinkers")
        rl = REAR_LAMP_R
        for ly, kind in REAR_LAMPS:
            bone_name, tex = {"brake": ("StopLights", "brake_round_on"), "tail": ("StopLights", "brake_round_on"),
                              "reverse": ("BackLights", "white_round_on")}.get(kind, (None, None))
            if bone_name:
                cube(bone_name, (X0 + 0.008, ly - rl, sz * REAR_LAMP_Z - rl), (X0 + 0.02, ly + rl, sz * REAR_LAMP_Z + rl),
                     {"west": tex}, parent="Blinkers")
    cube("StopLights", (X0 + 0.008, 4.13, -0.25), (X0 + 0.02, 4.18, 0.25), {"west": "brake_on"}, parent="Blinkers")
    for name, sz in (("FrontLeftTurnSignal", 1), ("FrontRightTurnSignal", -1)):
        bone(name, "Blinkers")
        cube(name, (xl, iy - ir, sz * iz - ir), (xl + 0.002, iy + ir, sz * iz + ir), {"east": "amber_on"}, parent="Blinkers")
    for name, sz in (("BackLeftTurnSignal", 1), ("BackRightTurnSignal", -1)):
        bone(name, "Blinkers")
        ly = next(y for y, k in REAR_LAMPS if k == "indicator")
        rl = REAR_LAMP_R
        cube(name, (X0 + 0.008, ly - rl, sz * REAR_LAMP_Z - rl), (X0 + 0.02, ly + rl, sz * REAR_LAMP_Z + rl),
             {"west": "amber_round_on"}, parent="Blinkers")
    # side repeaters just ahead of the front wheels, hidden in the panel until they flash
    for name, z, face in (("LeftTurnSignal", ZO, "south"), ("RightTurnSignal", ZN, "north")):
        bone(name, "Blinkers", (3.555, 0.5, z))
        za, zb = (z - 0.02, z - 0.008) if z > 0 else (z + 0.008, z + 0.02)
        cube(name, (3.52, 0.47, za), (3.59, 0.53, zb), {face: "amber_on"}, parent="Blinkers")


def details():
    # destination displays (text drawn by the renderer at these bones' pivots)
    # labels run from the bone pivot towards +z (front), +x (side) and -z (rear and inside),
    # so each pivot sits at the end of its screen where the text starts
    zf, top, w, h = DISPLAY_FRONT
    bone("Display1", "Vehicle", (X1 + 0.014, top, -zf))
    solid("Display1", (X1 - 0.01, top - h, -zf), (X1 + 0.012, top, zf), "black")
    sx, sy, sw, sh = DISPLAY_SIDE           # box on the panel behind the front door (painted on the side)
    bone("Display2", "Vehicle", (sx - sw, sy, ZN - 0.014))
    rz, rt, rw, rh = DISPLAY_REAR
    bone("Display3", "Vehicle", (X0 - 0.014, rt, rz))
    solid("Display3", (X0 - 0.012, rt - rh, rz - rw), (X0 + 0.01, rt, rz), "black")
    bone("PlateFront", "Vehicle", (X1 + 0.054, 0.435, 0))
    bone("FrontID", "Vehicle", (X1 + 0.014, 1.05, -0.95))   # fleet number on the black strip, nearside
    bone("PlateBack", "Vehicle", (X0 - 0.014, 0.935, 0))
    # side adverts as thin decals just proud of the panels
    xa, xb = ADVERTS["near"]
    cube("Body", (xa, ADVERT_Y[0], ZN - 0.006), (xb, ADVERT_Y[1], ZN - 0.002), {"north": face_uv("advert_near")})
    xa, xb = ADVERTS["off"]
    cube("Body", (xa, ADVERT_Y[0], ZO + 0.002), (xb, ADVERT_Y[1], ZO + 0.006), {"south": face_uv("advert_off", flip_u=ADVERT_OFF_FLIP)})
    # "bunny ear" mirrors: an arm forward from the top of each windscreen corner, then down
    bone("Mirrors")
    for name, z, s in (("LeftMirror", ZO, 1), ("RightMirror", ZN, -1)):
        bone(name, "Mirrors", (X1 + 0.2, 1.9, z + s * 0.1))
        solid(name, (X1 - 0.32, 2.3, z + s * 0.01), (X1 + 0.21, 2.34, z + s * 0.04), "black", parent="Mirrors")
        solid(name, (X1 + 0.17, 2.0, z + s * 0.01), (X1 + 0.21, 2.34, z + s * 0.04), "black", parent="Mirrors")
        solid(name, (X1 + 0.14, 1.64, z - s * 0.02), (X1 + 0.22, 2.02, z + s * 0.15), "black", parent="Mirrors")
        cube(name, (X1 + 0.135, 1.67, min(z - s * 0.01, z + s * 0.14)), (X1 + 0.14, 1.99, max(z - s * 0.01, z + s * 0.14)),
             {"west": "glass_door"}, parent="Mirrors")
    # wipers hung from the top of the windscreen, as on the Enviro400
    bone("windscreenwipers")
    for name, z in (("1", 0.55), ("2", -0.45)):
        top = SCREEN[1] - 0.02
        bone(name, "windscreenwipers", (X1 + 0.02, top, z))
        solid(name, (X1 + 0.012, top - 0.85, z - 0.01), (X1 + 0.03, top, z + 0.01), "black", parent="windscreenwipers")


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


def plug_anim(front, rear, open_, out=0.1, slide=0.5):
    """Plug door, as on most modern buses: each leaf pushes out from the body, then slides along
    the outside, the front leaf forwards and the rear one back. A negative out moves the leaves
    into the bus instead and slides them along the inside (the front entrance)."""
    def track(sx):
        pts = [[0, 0, 0], [0, 0, -out * PX], [sx * slide * PX, 0, -out * PX]]
        times = ("0.0", "0.3", "1.0")
        seq = pts if open_ else pts[::-1]
        return {"position": {t: {"vector": v} for t, v in zip(times, seq)}}
    return anim(1.0, "hold_on_last_frame", {front: track(1), rear: track(-1)})


def write_animations():
    out = 0.025 * PX
    anims = {
        "omnicity.door1_open": plug_anim("Right", "Left", True, out=-0.12, slide=0.35),   # glide inwards
        "omnicity.door1_close": plug_anim("Right", "Left", False, out=-0.12, slide=0.35),   # glide inwards
        "omnicity.door2_open": plug_anim("Right2", "Left2", True),
        "omnicity.door2_close": plug_anim("Right2", "Left2", False),
        "omnicity.front_lights": anim(loop=True, bones={"FrontLights": {"position": [out, 0, 0]}}),
        "omnicity.stop_lights": anim(loop=True, bones={"StopLights": {"position": [-out, 0, 0]}}),
        "omnicity.reverse_lights": anim(loop=True, bones={"BackLights": {"position": [-out, 0, 0]}}),
        "omnicity.bus_stopping": anim(loop=True, bones={"BusStopping": {"position": [-0.4, 0, 0]}}),
        "omnicity.null": anim(loop=True),
    }
    for side, bones in (("left", ("FrontLeftTurnSignal", "BackLeftTurnSignal", "LeftTurnSignal")),
                        ("right", ("FrontRightTurnSignal", "BackRightTurnSignal", "RightTurnSignal"))):
        b = {}
        for name in bones:
            vec = [out, 0, 0] if name.startswith("Front") else [-out, 0, 0] if name.startswith("Back") else [0, 0, out if side == "left" else -out]
            b[name] = {"position": {"0.0": {"vector": vec}, "0.5": {"vector": [0, 0, 0]}}}
        anims[f"omnicity.{side}_signal"] = anim(1.0, True, b)
    anims["omnicity.wipers"] = anim(2.0, True, {
        "1": {"rotation": {"0.0": {"vector": [0, 0, 0]}, "1.0": {"vector": [-80, 0, 0]}, "2.0": {"vector": [0, 0, 0]}}},
        "2": {"rotation": {"0.0": {"vector": [0, 0, 0]}, "1.0": {"vector": [-80, 0, 0]}, "2.0": {"vector": [0, 0, 0]}}}})
    path = PTM_ASSETS / "animations/bus/ptmuk_omnicity.animation.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps({"format_version": "1.8.0", "animations": anims}, indent=1))


# ------------------------------------------------------------------ output

def write_geo():
    # PTM2 builds its buses facing +x and turns everything 90 degrees about y (each of its cubes
    # carries rotation [0, 90, 0]); doing the same on the root bone puts our bus in PTM2's frame,
    # so it faces the way PTM2 drives it and left-hand traffic mirrors it side to side
    bones = [{"name": "Vehicle", "pivot": [0, 0, 0], "rotation": [0, 90, 0]}]
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
        "description": {"identifier": "geometry.omnicity", "texture_width": ATLAS, "texture_height": ATLAS,
                        "visible_bounds_width": 17, "visible_bounds_height": 7, "visible_bounds_offset": [0, 3, 0]},
        "bones": sort_bones(bones)}]}
    path = PTM_ASSETS / "geo/bus/ptmuk_omnicity.geo.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(geo, separators=(",", ":")))
    print(f"  omnicity: {sum(len(b.get('cubes', [])) for b in bones)} cubes, {len(bones)} bones, {len(SEATS)} seats")


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


def write_texture(suffix=""):
    path = ASSETS / f"textures/entity/bus/omnicity{suffix}.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    ATL.img.save(path)
    left = ATL.img.copy()
    for name in TEXT_REGIONS:
        x, y, w, h = ATL.regions[name]
        left.paste(left.crop((x, y, x + w, y + h)).transpose(Image.Transpose.FLIP_LEFT_RIGHT), (x, y))
    left.save(ASSETS / f"textures/entity/bus/omnicity{suffix}_left.png")


def write_livery(livery_name):
    """Paint the same atlas again in another livery (same layout, so the same model uses it)."""
    global ATL
    regions = dict(ATL.regions)
    livery.STATE["name"] = livery_name
    try:
        ATL = Atlas(ATLAS)
        build_textures()
        if ATL.regions != regions:
            raise SystemExit("livery atlas layout differs")
        write_texture("_" + livery_name)
    finally:
        livery.STATE["name"] = None
    write_livery_icon(livery_name)


def write_livery_icon(livery_name):
    s = 8
    img = Image.new("RGBA", (16 * s, 16 * s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for x in range(6, 123):                        # red front (right) fading to white
        t = float(livery.fade(X0 + (x - 6) / 116 * L, X0, X1))
        d.line((x, 18, x, 108), fill=livery.mix(RED, t) + (255,))
    for y0, y1 in ((26, 52), (64, 88)):
        d.rectangle((14, y0, 116, y1), fill=(30, 30, 34, 255))
    d.polygon([(8, 106), (8, 92), (60, 54), (66, 60)], fill=livery.YELLOW + (255,))
    d.polygon([(8, 88), (8, 84), (56, 50), (58, 53)], fill=livery.BLACK + (255,))
    livery.note(d, 30, 60, 16, livery.BLACK + (255,), beamed=True)
    for cx in (30, 96):
        d.ellipse((cx - 11, 96, cx + 11, 118), fill=(20, 20, 20, 255))
        d.ellipse((cx - 5, 102, cx + 5, 112), fill=(170, 170, 170, 255))
    item = "enviro400_" + livery_name
    path = ASSETS / f"textures/item/{item}.png"
    img.resize((16, 16), Image.LANCZOS).save(path)
    (ASSETS / f"models/item/{item}.json").write_text(json.dumps(
        {"parent": "minecraft:item/generated", "textures": {"layer0": f"ptmuk:item/{item}"}}, indent=1))


def write_icon():
    s = 8
    img = Image.new("RGBA", (16 * s, 16 * s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((4, 16, 124, 108), radius=10, fill=RED + (255,))
    for y0, y1 in ((26, 52), (64, 88)):
        d.rectangle((14, y0, 116, y1), fill=(30, 30, 34, 255))
        for x in range(30, 116, 22):
            d.line((x, y0, x, y1), fill=RED + (255,), width=3)
    d.rectangle((86, 64, 100, 106), fill=(40, 40, 44, 255))
    for cx in (30, 96):
        d.ellipse((cx - 11, 96, cx + 11, 118), fill=(20, 20, 20, 255))
        d.ellipse((cx - 5, 102, cx + 5, 112), fill=(170, 170, 170, 255))
    path = ASSETS / "textures/item/omnicity.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    img.resize((16, 16), Image.LANCZOS).save(path)
    (ASSETS / "models/item").mkdir(parents=True, exist_ok=True)
    (ASSETS / "models/item/omnicity.json").write_text(json.dumps(
        {"parent": "minecraft:item/generated", "textures": {"layer0": "ptmuk:item/omnicity"}}, indent=1))


def floors(upper):
    out = []
    n = 9
    run = (STAIRS[1] - STAIRS[0]) / n
    rise = (UPPER_FLOOR - LOWER_FLOOR) / n
    for i in range(n):
        xa = STAIRS[0] + i * run
        out.append((xa, xa + run, STAIR_Z[0], STAIR_Z[1], LOWER_FLOOR + (i + 1) * rise))
    if upper:
        out.append((X0, UP_REAR_STEP[1], -W, W, UPPER_FLOOR + UP_REAR_STEP[2]))
        out.append((X0, X1, -W, W, UPPER_FLOOR))
    else:
        # the gangway steps up to the back row, and the raised boxes either side of it
        for xa, xb, h in REAR_STEPS:
            out.append((xa, xb, -W, W, LOWER_FLOOR + h))
        out.append((POD[0], POD[1], -W, -POD_PANEL, LOWER_FLOOR + POD[2]))
        out.append((POD[0], POD[1], POD_PANEL, W, LOWER_FLOOR + POD[2]))
    return out


def write_java():
    def f(v):
        return f"{v * SCALE:.3f}"
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

/** Generated by tools/bus_omnicity.py together with the model. Do not edit by hand. */
public final class OmnicityLayout implements BusLayout {{
    public static final OmnicityLayout INSTANCE = new OmnicityLayout();

    public static final float LOWER_FLOOR = {f(LOWER_FLOOR)}f;
    public static final float UPPER_FLOOR = {f(UPPER_FLOOR)}f;
    /** Above this height (relative to the bus) the local player counts as being upstairs. */
    public static final double DECK_SWITCH_HEIGHT = {f((LOWER_FLOOR + UPPER_FLOOR) / 2)};
    public static final double DOOR1_FORWARD = {f(sum(DOOR1) / 2)};
    public static final double DOOR2_FORWARD = {f(sum(DOOR2) / 2)};
    public static final double[] TICKET_MACHINE = {{{f(-0.04)}, {f(READER[1])}, {f(READER[0])}}};
    /** Display sizes for the renderer: width, height (blocks) and label yaw. */
    // label yaws measured in game: 90 faces the front, 180 the nearside, 270 the rear
    public static final float[] DISPLAY_FRONT = {{{f(DISPLAY_FRONT[2])}f, {f(DISPLAY_FRONT[3])}f, 90.0f}};
    public static final float[] DISPLAY_SIDE = {{{f(DISPLAY_SIDE[2])}f, {f(DISPLAY_SIDE[3])}f, 180.0f}};
    public static final float[] DISPLAY_REAR = {{{f(DISPLAY_REAR[2])}f, {f(DISPLAY_REAR[3])}f, 270.0f}};
    public static final float[] DISPLAY_INSIDE = {{{f(0.62)}f, {f(0.3)}f, 270.0f}};
    public static final int SEAT_COUNT = {len(SEATS)};

    private OmnicityLayout() {{
    }}

    @Override public float lowerFloor() {{ return LOWER_FLOOR; }}
    @Override public double deckSwitchHeight() {{ return DECK_SWITCH_HEIGHT; }}
    @Override public double door1Forward() {{ return DOOR1_FORWARD; }}
    @Override public double door2Forward() {{ return DOOR2_FORWARD; }}
    @Override public double[] ticketMachine() {{ return TICKET_MACHINE; }}
    @Override public float[] displayFront() {{ return DISPLAY_FRONT; }}
    @Override public float[] displaySide() {{ return DISPLAY_SIDE; }}
    @Override public float[] displayRear() {{ return DISPLAY_REAR; }}
    @Override public float[] displayInside() {{ return DISPLAY_INSIDE; }}
    @Override public List<SeatLocation> seatList() {{ return seats(); }}
    @Override public List<WheelLocation> wheelList() {{ return wheels(); }}
    @Override public List<FloorObject> floorList(boolean upper) {{ return floors(upper); }}
    /** The fleet number sits on the black band under the windscreen. */
    @Override public int frontIdColour() {{ return 0xFFF0F0F0; }}
    /** OmniCity blinds: destination and route number on one line. */
    @Override public boolean singleLineFront() {{ return true; }}

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
