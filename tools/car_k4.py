"""2026 Kia K4 hatchback in GT-Line S trim (red) for PTM2, as a GeckoLib model.

Built in PTM2's car frame: x across the car (+x is the driver's side in the model, which
PTM2 mirrors to the right in left-hand traffic worlds, so UK worlds get a right-hand drive
car), y up, front of the car towards -z. Sizes are the real car's scaled by S, to match
PTM2's own (slightly oversized) cars.

The body is made from projections: flat side panels whose texture carries the silhouette,
windows and wheel arches (transparent where there is no body), tilted panels for the
glasshouse, a raked windscreen and hatch glass, thin slices for the bonnet, roof and boot
lid, and vertical rounded corners front and back. No maker's badges are drawn.

Writes: geo model, animations, texture, item icon and model.
"""
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/ptmuk"
PTM_ASSETS = ROOT / "src/main/resources/assets/ptm2"
NAME = "k4"
PX = 16.0
ATLAS = 2048
PPM = 128                      # texels per metre on the projections
rng = np.random.default_rng(404)

# ------------------------------------------------------------------ dimensions (metres)
S = 1.18                       # PTM2 cars are built about this much over real size
L, W2, H = 4.44 * S, 0.925 * S, 1.435 * S       # length, half width, height
FA, RA = 0.9 * S, (0.9 + 2.72) * S              # axle positions from the front bumper
WR, TW = 0.318 * S, 0.225 * S                   # wheel radius, tyre width
TRACK = W2 - TW / 2 - 0.02                      # wheel centre plane
ARCH = WR + 0.06
RC_F, RC_R = 0.30, 0.28                         # plan radius of the front and rear corners
TILT = 16.0                                     # glasshouse tumblehome, degrees
BELT0 = 1.08                                    # hinge line of the glasshouse panels
T = 0.03


def z_of(f):
    """Distance from the front bumper -> model z (front at -z)."""
    return f - L / 2


def interp(pts, f):
    xs, ys = zip(*pts)
    return float(np.interp(f, xs, ys))


# side profile (f, y): bonnet, windscreen, roof, spoiler, hatch glass, boot lid
SCREEN = ((1.62, 1.08), (2.62, 1.62))
REARGLASS = ((4.48, 1.575), (5.06, 1.10))
TOP = [(0.0, 0.80), (0.10, 0.88), (0.40, 0.95), (1.00, 1.01), (1.62, 1.08), (2.62, 1.62), (3.0, 1.69), (3.6, 1.685),
       (4.2, 1.62), (4.48, 1.585), (5.06, 1.10), (5.16, 1.07), (L, 1.0)]
BOTTOM = [(0.0, 0.26), (0.30, 0.22), (L - 0.30, 0.22), (L, 0.26)]
BELT = [(1.62, 1.08), (2.9, 1.12), (3.9, 1.18), (4.30, 1.30), (5.06, 1.10)]     # window line (rises to the C pillar)
DLO_TOP = [(2.5, 1.56), (3.0, 1.62), (3.6, 1.615), (4.2, 1.55), (4.30, 1.53)]      # top of the side windows
DOOR1 = (1.60, 2.93)
DOOR2 = (2.96, 3.86)


def top_y(f):
    return interp(TOP, f)


def half_width(f):
    """Plan half width of the body at f (rounded front and rear corners)."""
    if f < RC_F:
        return W2 - RC_F + math.sqrt(max(0.0, RC_F ** 2 - (RC_F - f) ** 2))
    if f > L - RC_R:
        g = L - f
        return W2 - RC_R + math.sqrt(max(0.0, RC_R ** 2 - (RC_R - g) ** 2))
    return W2


def glass_x(y):
    """Half width of the glasshouse at height y (tumblehome)."""
    return W2 - max(0.0, y - BELT0) * math.tan(math.radians(TILT))


# ------------------------------------------------------------------ colours
RED = (178, 14, 26)            # deep metallic red
RED_HI = (214, 40, 46)
GLOSS = (16, 16, 18)
TRIM = (34, 34, 36)
CHROME = (186, 190, 194)
GREY = (78, 80, 84)


# ------------------------------------------------------------------ atlas and cubes

class Atlas:
    def __init__(self, size):
        self.size, self.pending, self.regions = size, [], {}

    def add(self, name, img):
        self.pending.append((name, img))

    def pack(self):
        self.img = Image.new("RGBA", (self.size, self.size), (0, 0, 0, 0))
        x = y = row = 0
        for name, img in sorted(self.pending, key=lambda p: -p[1].size[1]):
            w, h = img.size
            if x + w > self.size:
                x, y, row = 0, y + row + 2, 0
            if y + h > self.size:
                raise SystemExit(f"atlas full at {name}")
            self.img.paste(img, (x, y))
            self.regions[name] = (x, y, w, h)
            x += w + 2
            row = max(row, h)


ATL = Atlas(ATLAS)
BONES, ORDER = {}, []


def bone(name, parent="Car", pivot=(0, 0, 0)):
    if name not in BONES:
        BONES[name] = {"name": name, "parent": parent, "pivot": [round(p * PX, 4) for p in pivot], "cubes": []}
        ORDER.append(BONES[name])
    return BONES[name]


def uv(region, sub=None, flip_u=False, flip_v=False):
    x, y, w, h = ATL.regions[region]
    if sub:
        sx, sy, sw, sh = sub
        x, y, w, h = x + sx, y + sy, max(1, sw), max(1, sh)
    u, v, uw, vh = x, y, w, h
    if flip_u:
        u, uw = x + w, -w
    if flip_v:
        v, vh = y + h, -h
    return {"uv": [u, v], "uv_size": [uw, vh]}


def cube(bone_name, lo, hi, faces, rotation=None, pivot=None):
    lo, hi = np.minimum(lo, hi), np.maximum(lo, hi)
    swap = {"east": "west", "west": "east"}          # GeckoLib mirrors x
    c = {"origin": [round(v * PX, 4) for v in lo], "size": [round(v * PX, 4) for v in hi - lo],
         "uv": {swap.get(f, f): (s if isinstance(s, dict) else uv(s)) for f, s in faces.items()}}
    if rotation:
        c["rotation"] = rotation
        c["pivot"] = [round(v * PX, 4) for v in (pivot if pivot is not None else (lo + hi) / 2)]
    BONES[bone_name]["cubes"].append(c)


ALL = ("north", "south", "east", "west", "up", "down")


def solid(b, lo, hi, region, **kw):
    cube(b, lo, hi, {f: region for f in ALL}, **kw)


# ------------------------------------------------------------------ painted projections

def noise(img, amount=3):
    a = np.asarray(img).astype(np.float32)
    a[..., :3] += rng.normal(0, amount, a.shape[:2] + (1,))
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGBA")


def paint_metal(img, mask_colour=RED):
    """Metallic flake and a soft horizontal reflection band on the body colour."""
    a = np.asarray(img).astype(np.float32)
    body = (np.abs(a[..., 0] - mask_colour[0]) < 2) & (np.abs(a[..., 1] - mask_colour[1]) < 2) & (a[..., 3] > 0)
    h = a.shape[0]
    yy = np.mgrid[0:h, 0:a.shape[1]][0] / h
    shade = 1.0 + 0.16 * np.exp(-((yy - 0.38) / 0.08) ** 2) - 0.18 * yy
    flake = rng.normal(0, 5, a.shape[:2])
    for c in range(3):
        a[..., c] = np.where(body, a[..., c] * shade + flake, a[..., c])
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGBA")


def side_view(kind):
    """Side of the car, front at the left (u = f). kind: body / door / body_in / door_in."""
    ss = 2
    w, h = int(L * PPM), int(H * PPM)
    img = Image.new("RGBA", (w * ss, h * ss), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    def P(f, y):
        return (f * PPM * ss, (H - y) * PPM * ss)

    inner = kind.endswith("_in")
    body_col = (54, 54, 58) if inner else RED
    outline = [P(f, y) for f, y in TOP] + [P(f, y) for f, y in reversed(BOTTOM)]
    d.polygon(outline, fill=body_col + (255,))
    if not inner:
        # lower cladding along the sills and round the arches (GT-Line gloss black / grey)
        d.polygon([P(0.95, 0.22), P(L - 0.85, 0.22), P(L - 0.85, 0.33), P(0.95, 0.33)], fill=TRIM + (255,))
        d.rectangle([*P(1.2, 0.335), *P(L - 1.2, 0.325)], fill=(120, 124, 130, 255))      # satin sill blade
        for ax in (FA, RA):
            r = (ARCH + 0.07) * PPM * ss
            cx, cy = P(ax, WR)
            d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=TRIM + (255,))
        # front and rear bumper lower trim
        d.polygon([P(0, 0.26), P(0.6, 0.22), P(0.6, 0.36), P(0, 0.40)], fill=TRIM + (255,))
        d.polygon([P(L, 0.26), P(L - 0.55, 0.22), P(L - 0.55, 0.40), P(L, 0.46)], fill=TRIM + (255,))
    # wheel arch openings
    for ax in (FA, RA):
        r = ARCH * PPM * ss
        cx, cy = P(ax, WR)
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(0, 0, 0, 0))
        d.rectangle([cx - r, cy, cx + r, h * ss], fill=(0, 0, 0, 0))
    # windows: black surround, glass cut out; gloss black C pillar insert
    a_front = [(SCREEN[0][0] + 0.12, BELT0 + 0.02), (SCREEN[1][0] + 0.02, SCREEN[1][1] - 0.06)]
    dlo = [P(*a_front[0])] + [P(*a_front[1])] + [P(f, y) for f, y in DLO_TOP] + [P(4.30, 1.31), P(3.9, 1.18), P(2.9, 1.12)]
    if not inner:
        d.polygon([P(3.95, 1.19), P(4.30, 1.53), P(4.42, 1.56), P(4.48, 1.36), P(4.30, 1.31)], fill=GLOSS + (255,))
        big = [(x, y) for x, y in dlo]
        d.line(big + [big[0]], fill=GLOSS + (255,), width=int(0.035 * PPM * ss))
    d.polygon(dlo, fill=(0, 0, 0, 0))
    # B pillar (gloss black) and the rear door's window frame
    d.polygon([P(2.90, 1.12), P(2.98, 1.12), P(3.0, 1.62), P(2.92, 1.62)], fill=(GLOSS if not inner else (40, 40, 44)) + (255,))
    d.polygon([P(3.86, 1.17), P(3.93, 1.18), P(4.0, 1.585), P(3.93, 1.59)], fill=(GLOSS if not inner else (40, 40, 44)) + (255,))
    if not inner:
        # door shut lines and flush handles
        for f0, f1 in (DOOR1, DOOR2):
            for f in (f0, f1):
                d.line([P(f, 0.33), P(f, 1.12)], fill=(90, 8, 14, 255), width=2)
            d.rounded_rectangle([*P(f1 - 0.36, 1.02), *P(f1 - 0.16, 0.985)], radius=3, fill=(120, 10, 18, 255))
        d.line([P(0.3, 0.95), P(4.9, 1.0)], fill=(150, 10, 18, 255), width=2)              # shoulder crease
        d.rectangle([*P(L - 0.75, 0.93), *P(L - 0.58, 0.82)], outline=(130, 10, 16, 255), width=2)   # charge/fuel flap
    img = img.resize((w, h), Image.LANCZOS)
    a = np.asarray(img).copy()
    # split body / doors
    door = np.zeros(a.shape[:2], bool)
    for f0, f1 in (DOOR1, DOOR2):
        c0, c1 = int(f0 * PPM), int(f1 * PPM)
        door[:, c0:c1] = True
    rows = np.arange(a.shape[0])[:, None]
    door &= rows > int((H - 1.64) * PPM)          # below the roof rail
    door &= rows < int((H - 0.30) * PPM)          # above the sill
    if kind.startswith("door"):
        a[~door] = 0
    else:
        a[door] = 0
    img = Image.fromarray(a, "RGBA")
    if not inner:
        img = paint_metal(img)
    return noise(img, 2)


def front_view(inside=False):
    """Front of the car seen from ahead (u = x from the -x side): grille, slim headlamps,
    vertical light strips at the corners, gloss black lower intake."""
    ss = 2
    w, h = int(2 * W2 * PPM), int(H * PPM)
    img = Image.new("RGBA", (w * ss, h * ss), RED + (255,))
    d = ImageDraw.Draw(img)

    def P(x, y):
        return ((x + W2) * PPM * ss, (H - y) * PPM * ss)
    if inside:
        return Image.new("RGBA", (w, h), (40, 40, 44, 255))
    d.rectangle([*P(-W2, H), *P(W2, 0.86)], fill=(0, 0, 0, 0))
    # slim upper lamp line with the lamps at each end
    d.rounded_rectangle([*P(-0.86, 0.80), *P(0.86, 0.76)], radius=4, fill=GLOSS + (255,))
    for s in (-1, 1):
        x0, x1 = sorted((s * 0.52, s * 0.86))
        d.rounded_rectangle([*P(x0, 0.82), *P(x1, 0.70)], radius=6, fill=(26, 26, 30, 255))
        for k in range(3):                                            # projector lenses
            cx = s * (0.60 + k * 0.08)
            d.ellipse([*P(cx - 0.025, 0.785), *P(cx + 0.025, 0.735)], fill=(200, 210, 220, 255))
    # gloss black grille band and the big lower intake with a mesh
    d.rounded_rectangle([*P(-0.48, 0.74), *P(0.48, 0.66)], radius=8, fill=GLOSS + (255,))
    d.rounded_rectangle([*P(-0.80, 0.52), *P(0.80, 0.30)], radius=12, fill=(14, 14, 16, 255))
    for i in range(18):
        x = -0.76 + i * 0.09
        d.line([P(x, 0.50), P(x + 0.04, 0.32)], fill=(42, 42, 46, 255), width=2)
    d.rectangle([*P(-0.26, 0.47), *P(0.26, 0.37)], fill=(244, 244, 240, 255))     # plate (text drawn live)
    d.polygon([P(-W2, 0.30), P(W2, 0.30), P(W2, 0.0), P(-W2, 0.0)], fill=TRIM + (255,))
    img = img.resize((w, h), Image.LANCZOS)
    return noise(paint_metal(img), 2)


def rear_view(inside=False):
    """Rear seen from behind: full width light bar under the spoiler line, black lower bumper
    with a satin skid plate, yellow plate, reflectors."""
    ss = 2
    w, h = int(2 * W2 * PPM), int(H * PPM)
    if inside:
        return Image.new("RGBA", (w, h), (40, 40, 44, 255))
    img = Image.new("RGBA", (w * ss, h * ss), RED + (255,))
    d = ImageDraw.Draw(img)

    def P(x, y):
        return ((x + W2) * PPM * ss, (H - y) * PPM * ss)
    d.rectangle([*P(-W2, H), *P(W2, 1.07)], fill=(0, 0, 0, 0))
    d.rectangle([*P(-0.92, 0.99), *P(0.92, 0.965)], fill=(90, 8, 12, 255))            # light bar (unlit)
    d.rectangle([*P(-0.92, 0.975), *P(0.92, 0.97)], fill=(150, 20, 26, 255))
    for sx_ in (-1, 1):                                                                 # tall L lamps at the edges
        x0, x1 = sorted((sx_ * 0.80, sx_ * 0.92))
        d.rectangle([*P(x0, 0.99), *P(x1, 0.66)], fill=(110, 8, 14, 255))
    d.rounded_rectangle([*P(-0.85, 0.52), *P(0.85, 0.16)], radius=10, fill=(30, 30, 32, 255))   # lower bumper
    d.rectangle([*P(-0.85, 0.52), *P(0.85, 0.49)], fill=(60, 60, 64, 255))
    d.rectangle([*P(-0.55, 0.30), *P(0.55, 0.24)], fill=(150, 154, 160, 255))                  # skid plate
    d.rectangle([*P(-0.27, 0.48), *P(0.27, 0.37)], fill=(246, 202, 30, 255))                    # plate
    for s in (-1, 1):
        x0, x1 = sorted((s * 0.62, s * 0.80))
        d.rectangle([*P(x0, 0.44), *P(x1, 0.41)], fill=(150, 14, 20, 255))                    # reflectors
    img = img.resize((w, h), Image.LANCZOS)
    return noise(paint_metal(img), 2)


def corner_strip(kind):
    """Rounded corners, column 0 at the side edge: front ones carry the vertical light strips."""
    w, h = 48, int(H * PPM)
    img = Image.new("RGBA", (w, h), RED + (255,))
    d = ImageDraw.Draw(img)

    def Y(y):
        return int((H - y) * PPM)
    d.rectangle([0, 0, w, Y(top_y(0.15 if kind == "front" else L - 0.15))], fill=(0, 0, 0, 0))
    d.rectangle([0, Y(0.36), w, h], fill=TRIM + (255,))
    if kind == "front":
        d.rectangle([int(w * 0.62), Y(0.84), int(w * 0.74), Y(0.42)], fill=(214, 220, 226, 255))   # star map strip
        d.rectangle([int(w * 0.30), Y(0.62), int(w * 0.46), Y(0.56)], fill=(236, 150, 30, 255))    # indicator
    else:
        d.rectangle([int(w * 0.70), Y(1.10), w, Y(0.78)], fill=(110, 8, 14, 255))                  # tail light L
    return noise(paint_metal(img), 2)


def top_view():
    """Bonnet, roof and boot lid from above (u = x, v = f): body red, panoramic glass roof."""
    ss = 1
    w, h = int(2 * W2 * PPM), int(L * PPM)
    img = Image.new("RGBA", (w, h), RED + (255,))
    d = ImageDraw.Draw(img)

    def P(x, f):
        return ((x + W2) * PPM, f * PPM)
    d.rounded_rectangle([*P(-0.62, 2.85), *P(0.62, 4.05)], radius=12, fill=(14, 16, 20, 255))   # glass roof
    d.line([P(-0.62, 3.45), P(0.62, 3.45)], fill=(40, 40, 44, 255), width=2)
    for x in (-0.36, 0.36):                                                                       # bonnet creases
        d.line([P(x, 0.25), P(x * 1.15, 1.5)], fill=(150, 10, 18, 255), width=2)
    d.polygon([P(-0.05, 3.12), P(0.05, 3.12), P(0.03, 3.3), P(-0.03, 3.3)], fill=RED_HI + (255,))  # shark fin
    return noise(paint_metal(img), 2)


def glass_panel(w_bottom, w_top, length, frame=0.05):
    """Trapezoid glass (transparent) in a black frame; u across, v from the top edge."""
    ss = 2
    w, h = int(2 * w_bottom * PPM), int(length * PPM)
    img = Image.new("RGBA", (w * ss, h * ss), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    inset = (w_bottom - w_top) * PPM * ss
    poly = [(inset, 0), (w * ss - inset, 0), (w * ss, h * ss), (0, h * ss)]
    d.polygon(poly, fill=GLOSS + (255,))
    f = frame * PPM * ss
    d.polygon([(inset + f, f), (w * ss - inset - f, f), (w * ss - f, h * ss - f), (f, h * ss - f)], fill=(0, 0, 0, 0))
    return img.resize((w, h), Image.LANCZOS)


def rim_texture(size=96):
    """18 inch two-tone wheel face: machined five double spokes on dark grey, black tyre wall
    outside the rim (transparent corners)."""
    ss = 3
    s = size * ss
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = s / 2
    d.ellipse([0, 0, s, s], fill=(20, 20, 22, 255))                       # tyre wall
    rr = s * 0.37
    d.ellipse([c - rr, c - rr, c + rr, c + rr], fill=(46, 48, 52, 255))   # rim barrel (dark)
    for k in range(5):
        for off in (-7, 7):
            a = math.radians(k * 72 + off - 90)
            pts = []
            for r_, da in ((0.07, -5), (rr / s * 0.98, -4), (rr / s * 0.98, 4), (0.07, 5)):
                aa = a + math.radians(da)
                pts.append((c + r_ * s * math.cos(aa), c + r_ * s * math.sin(aa)))
            d.polygon(pts, fill=(196, 200, 204, 255))
    d.ellipse([c - s * 0.075, c - s * 0.075, c + s * 0.075, c + s * 0.075], fill=(30, 30, 32, 255))
    d.ellipse([c - s * 0.04, c - s * 0.04, c + s * 0.04, c + s * 0.04], fill=(160, 164, 168, 255))
    return img.resize((size, size), Image.LANCZOS)


def screen_texture():
    """Panoramic dual screen: instruments on the driver's side, navigation in the middle."""
    w, h = 256, 40
    img = Image.new("RGBA", (w, h), (8, 10, 14, 255))
    d = ImageDraw.Draw(img)
    d.rectangle([4, 4, 122, 36], fill=(16, 22, 34, 255))
    d.ellipse([20, 8, 46, 34], outline=(80, 170, 220, 255), width=2)
    d.ellipse([80, 8, 106, 34], outline=(80, 170, 220, 255), width=2)
    d.text((54, 14), "37", fill=(230, 230, 230, 255))
    d.rectangle([130, 4, 252, 36], fill=(26, 40, 34, 255))
    for i in range(6):
        d.line([(136 + i * 18, 8), (150 + i * 14, 32)], fill=(70, 120, 90, 255), width=2)
    d.line([(140, 30), (200, 16), (246, 22)], fill=(80, 160, 240, 255), width=3)
    return img


def build_textures():
    for k in ("body", "door", "body_in", "door_in"):
        ATL.add("side_" + k, side_view(k))
    ATL.add("front", front_view())
    ATL.add("rear", rear_view())
    ATL.add("inner", Image.new("RGBA", (16, 16), (40, 40, 44, 255)))
    ATL.add("corner_front", corner_strip("front"))
    ATL.add("corner_rear", corner_strip("rear"))
    ATL.add("top", top_view())
    ATL.add("windscreen", glass_panel(1.0, 0.86, math.hypot(SCREEN[1][0] - SCREEN[0][0], SCREEN[1][1] - SCREEN[0][1])))
    ATL.add("rearglass", glass_panel(0.98, 0.86, math.hypot(REARGLASS[1][0] - REARGLASS[0][0], REARGLASS[1][1] - REARGLASS[0][1]), 0.07))
    ATL.add("rim", rim_texture())
    ATL.add("screen", screen_texture())
    sw = {"red": RED, "gloss": GLOSS, "trim": TRIM, "chrome": CHROME, "grey": GREY, "tyre": (24, 24, 26),
          "seat": (30, 30, 33), "seat_trim": (196, 194, 188), "dash": (40, 41, 44), "silver": (150, 154, 158),
          "headliner": (64, 64, 68), "carpet": (24, 24, 26), "wheel_hi": (210, 206, 196), "lamp_on": (250, 252, 255),
          "brake_on": (255, 30, 34), "amber_on": (255, 168, 30), "reverse_on": (240, 240, 240), "mirror": (150, 170, 190),
          "black": (10, 10, 11)}
    for k, c in sw.items():
        ATL.add(k, noise(Image.new("RGBA", (16, 16), c + (255,)), 2))
    ATL.pack()


# ------------------------------------------------------------------ geometry

FLIP_EAST, FLIP_WEST = False, True      # u along the side textures runs front to rear on east faces


def side_uv(region, f0, f1, y0, y1, face):
    x, y, w, h = ATL.regions[region]
    c0, c1 = int(round(f0 * PPM)), int(round(f1 * PPM))
    r0, r1 = int(round((H - y1) * PPM)), int(round((H - y0) * PPM))
    return uv(region, (c0, r0, c1 - c0, r1 - r0), flip_u=FLIP_EAST if face == "east" else FLIP_WEST)


def end_uv(region, x0, x1, y0, y1, flip=False):
    rx, ry, w, h = ATL.regions[region]
    c0, c1 = int(round((x0 + W2) * PPM)), int(round((x1 + W2) * PPM))
    r0, r1 = int(round((H - y1) * PPM)), int(round((H - y0) * PPM))
    return uv(region, (c0, r0, c1 - c0, r1 - r0), flip_u=flip)


def top_uv(x0, x1, f0, f1):
    c0, c1 = int(round((x0 + W2) * PPM)), int(round((x1 + W2) * PPM))
    r0, r1 = int(round(f0 * PPM)), int(round(f1 * PPM))
    return uv("top", (c0, r0, max(1, c1 - c0), max(1, r1 - r0)))


def sides(b, region, f0, f1, y0, y1, inner_region):
    """Lower body side panels (vertical) both sides."""
    for sx, face, inner in ((1, "east", "west"), (-1, "west", "east")):
        x = sx * W2
        cube(b, (x - T if sx > 0 else x, y0, z_of(f0)), (x if sx > 0 else x + T, y1, z_of(f1)),
             {face: side_uv(region, f0, f1, y0, y1, face), inner: side_uv(inner_region, f0, f1, y0, y1, inner)})


def glasshouse(b, region, f0, f1, inner_region, pivot_f=None):
    """Tilted upper side panels from the hinge line up to the roof."""
    y0, y1 = BELT0, H
    ln = (y1 - y0) / math.cos(math.radians(TILT))
    for sx, face, inner in ((1, "east", "west"), (-1, "west", "east")):
        x = sx * W2
        cube(b, (x - T if sx > 0 else x, y0, z_of(f0)), (x if sx > 0 else x + T, y0 + ln, z_of(f1)),
             {face: side_uv(region, f0, f1, y0, y1, face), inner: side_uv(inner_region, f0, f1, y0, y1, inner)},
             rotation=[0, 0, -sx * TILT], pivot=(x, y0, 0))


def corner(b, cx, cz, sz, sx, y0, y1, r, region, n=6):
    """Vertical quarter-round corner; sz: -1 front / +1 rear, sx: side. Column 0 = side edge."""
    rx, ry, w, h = ATL.regions[region]
    for i in range(n):
        a = math.radians(90 * (i + 0.5) / n)               # 0 at the side, 90 at the end face
        chord = 2 * r * math.sin(math.radians(90 / n) / 2) + 0.01
        px, pz = cx + sx * r * math.cos(a), cz + sz * r * math.sin(a)
        c0, c1 = int(w * i / n), int(w * (i + 1) / n)
        r0, r1 = int(round((H - y1) * PPM)), int(round((H - y0) * PPM))
        face_uv = uv(region, (c0, r0, c1 - c0, r1 - r0))
        # plank lying along z at the side, turned towards the end face
        ang = math.degrees(a) * (-sx * sz)
        out = "east" if sx > 0 else "west"
        cube(b, (px - T / 2, y0, pz - chord / 2), (px + T / 2, y1, pz + chord / 2),
             {out: face_uv, ("west" if sx > 0 else "east"): "inner"}, rotation=[0, ang, 0], pivot=(px, 0, pz))


def body():
    bone("Body")
    fs, fe = RC_F, L - RC_R
    # lower sides: front wing, sills/doors area (doors are their own bones), rear quarter
    sides("Body", "side_body", fs, fe, 0.2, 1.14, "side_body_in")
    glasshouse("Body", "side_body", SCREEN[0][0], REARGLASS[1][0] + 0.05, "side_body_in")
    # front and rear faces and their rounded corners
    xf = W2 - RC_F
    cube("Body", (-xf, 0.22, z_of(0)), (xf, top_y(0.02), z_of(0) + T),
         {"north": end_uv("front", -xf, xf, 0.22, top_y(0.02)), "south": "inner"})
    xr = W2 - RC_R
    cube("Body", (-xr, 0.2, z_of(L) - T), (xr, top_y(L - 0.02), z_of(L)),
         {"south": end_uv("rear", -xr, xr, 0.2, top_y(L - 0.02), flip=True), "north": "inner"})
    for sx in (1, -1):
        corner("Body", sx * xf, z_of(RC_F), -1, sx, 0.22, top_y(0.12), RC_F, "corner_front")
        corner("Body", sx * xr, z_of(L - RC_R), 1, sx, 0.2, top_y(L - 0.12), RC_R, "corner_rear")
    # bonnet, roof and boot lid: angled plates following the side profile (smooth, no steps)
    def plates(b_name, f_from, f_to, width, under):
        pts = [(f, y) for f, y in TOP if f_from - 1e-6 <= f <= f_to + 1e-6]
        for (fa, ya), (fb, yb) in zip(pts, pts[1:]):
            ln = math.hypot(fb - fa, yb - ya) + 0.012
            hw = width((fa + fb) / 2)
            ang = math.degrees(math.atan2(yb - ya, fb - fa))
            cube(b_name, (-hw, ya - 0.035, z_of(fa) - 0.006), (hw, ya, z_of(fa) - 0.006 + ln),
                 {"up": top_uv(-hw, hw, fa, fb), "down": under, "north": "red", "south": "red", "east": "red", "west": "red"},
                 rotation=[ang, 0, 0], pivot=(0, ya, z_of(fa)))
    plates("Body", 0.0, SCREEN[0][0], lambda f: half_width(f) - 0.005, "inner")
    plates("Body", SCREEN[1][0], REARGLASS[0][0], lambda f: glass_x(top_y(f)) - 0.01, "headliner")
    plates("Trunk", REARGLASS[1][0], L, lambda f: half_width(f) - 0.005, "inner")
    # windscreen and hatch glass (tilted panels; the frame is drawn, the glass is clear)
    for region, (pa, pb), b in (("windscreen", SCREEN, "Body"), ("rearglass", REARGLASS, "Trunk")):
        (fa, ya), (fb, yb) = pa, pb
        ln = math.hypot(fb - fa, yb - ya)
        hw = glass_x(min(ya, yb)) - 0.01
        ang = math.degrees(math.atan2(yb - ya, fb - fa))
        cube(b, (-hw, ya - 0.01, z_of(fa)), (hw, ya + 0.01, z_of(fa) + ln),
             {"up": uv(region, flip_v=region == "windscreen"), "down": uv(region, flip_v=region == "windscreen")},
             rotation=[ang, 0, 0], pivot=(0, ya, z_of(fa)))
    # spoiler over the hatch glass with the high level brake light
    cube("Trunk", (-0.86, 1.555, z_of(4.32)), (0.86, 1.6, z_of(4.6)), {"up": "red", "down": "gloss", "south": "gloss",
                                                                        "east": "red", "west": "red"})
    # underfloor and wheel wells
    solid("Body", (-W2 + 0.03, 0.18, z_of(0.2)), (W2 - 0.03, 0.24, z_of(L - 0.2)), "black")
    for ax in (FA, RA):
        for sx in (1, -1):
            x0, x1 = sorted((sx * (W2 - 0.04), sx * (W2 - 0.04 - TW - 0.12)))
            cube("Body", (x0, WR - 0.05, z_of(ax) - ARCH), (x1, WR + ARCH, z_of(ax) + ARCH),
                 {f: "black" for f in ("north", "south", "down", "east", "west")})


def doors():
    for name, (f0, f1), sx in (("Door1", DOOR1, 1), ("Door2", DOOR1, -1), ("Door3", DOOR2, 1), ("Door4", DOOR2, -1)):
        x = sx * W2
        bone(name, "Car", (x, 0.6, z_of(f0)))
        face, inner = ("east", "west") if sx > 0 else ("west", "east")
        cube(name, (x - T if sx > 0 else x, 0.3, z_of(f0)), (x if sx > 0 else x + T, 1.14, z_of(f1)),
             {face: side_uv("side_door", f0, f1, 0.3, 1.14, face), inner: side_uv("side_door_in", f0, f1, 0.3, 1.14, inner)})
        y0, y1 = BELT0, H
        ln = (y1 - y0) / math.cos(math.radians(TILT))
        cube(name, (x - T if sx > 0 else x, y0, z_of(f0)), (x if sx > 0 else x + T, y0 + ln, z_of(f1)),
             {face: side_uv("side_door", f0, f1, y0, y1, face), inner: side_uv("side_door_in", f0, f1, y0, y1, inner)},
             rotation=[0, 0, -sx * TILT], pivot=(x, y0, 0))
        # door card: armrest and pull
        xi = x - sx * 0.06
        cube(name, (min(x - sx * T, xi), 0.78, z_of(f0 + 0.2)), (max(x - sx * T, xi), 0.84, z_of(f1 - 0.15)),
             {f: "dash" for f in ALL})


def wheels():
    bone("Wheels")
    for name, ax, sx in (("FrontLeftWheel", FA, 1), ("FrontRightWheel", FA, -1), ("BackLeftWheel", RA, 1), ("BackRightWheel", RA, -1)):
        cx, cz = sx * TRACK, z_of(ax)
        bone(name, "Wheels", (cx, WR, cz))
        x0, x1 = cx - TW / 2, cx + TW / 2
        w = WR * math.tan(math.radians(11.25)) * 1.04
        for ang in (0, 22.5, 45, 67.5):
            cube(name, (x0, WR - w, cz - WR), (x1, WR + w, cz + WR), {f: "tyre" for f in ALL},
                 rotation=[ang, 0, 0], pivot=(cx, WR, cz))
            cube(name, (x0, WR - WR, cz - w), (x1, WR + WR, cz + w), {f: "tyre" for f in ALL},
                 rotation=[ang, 0, 0], pivot=(cx, WR, cz))
        out = "east" if sx > 0 else "west"
        xo = cx + sx * (TW / 2 + 0.004)
        cube(name, (min(xo, xo - sx * 0.002), 0, cz - WR), (max(xo, xo - sx * 0.002), 2 * WR, cz + WR), {out: "rim"})


def interior():
    bone("Interior")
    # dashboard with a silver line, panoramic screens on the driver's side and centre
    solid("Interior", (-W2 + 0.08, 0.72, z_of(1.62)), (W2 - 0.08, 1.04, z_of(2.08)), "dash")
    solid("Interior", (-W2 + 0.1, 0.92, z_of(2.08)), (W2 - 0.1, 0.94, z_of(2.1)), "silver")
    cube("Interior", (-0.12, 1.02, z_of(2.0)), (0.98, 1.17, z_of(2.03)), {"south": "screen", "north": "black", "up": "black",
                                                                          "east": "black", "west": "black"})
    # centre console with the drive selector
    solid("Interior", (-0.16, 0.3, z_of(2.05)), (0.16, 0.66, z_of(3.0)), "dash")
    solid("Interior", (-0.03, 0.66, z_of(2.35)), (0.03, 0.78, z_of(2.41)), "silver")
    solid("Interior", (-W2 + 0.04, 0.24, z_of(1.62)), (W2 - 0.04, 0.3, z_of(4.6)), "carpet")
    # seats: two buckets with light piping, a rear bench
    for x in (0.47, -0.47):
        solid("Interior", (x - 0.26, 0.3, z_of(2.48)), (x + 0.26, 0.56, z_of(2.98)), "seat")
        solid("Interior", (x - 0.26, 0.56, z_of(2.92)), (x + 0.26, 1.28, z_of(3.04)), "seat",
              rotation=[-12, 0, 0], pivot=(x, 0.56, z_of(2.98)))
        solid("Interior", (x - 0.27, 0.55, z_of(2.46)), (x - 0.24, 0.575, z_of(2.96)), "seat_trim")
        solid("Interior", (x + 0.24, 0.55, z_of(2.46)), (x + 0.27, 0.575, z_of(2.96)), "seat_trim")
        solid("Interior", (x - 0.13, 1.22, z_of(3.0)), (x + 0.13, 1.42, z_of(3.08)), "seat",
              rotation=[-12, 0, 0], pivot=(x, 0.56, z_of(2.98)))
    solid("Interior", (-0.95, 0.3, z_of(3.55)), (0.95, 0.56, z_of(4.0)), "seat")
    solid("Interior", (-0.95, 0.56, z_of(3.98)), (0.95, 1.22, z_of(4.1)), "seat", rotation=[-14, 0, 0], pivot=(0, 0.56, z_of(4.04)))
    solid("Interior", (-0.95, 0.56, z_of(4.4)), (0.95, 1.0, z_of(4.45)), "carpet")       # parcel shelf back
    # two-tone steering wheel facing the driver (+x side in the model)
    bone("SteeringWheel", "Car", (0.47, 1.0, z_of(2.22)))
    r = 0.19
    for ang in range(0, 360, 30):
        a = math.radians(ang)
        cx, cy = 0.47 + r * math.cos(a), 1.0 + r * math.sin(a)
        reg = "wheel_hi" if 30 <= ang <= 150 else "black"
        solid("SteeringWheel", (cx - 0.035, cy - 0.035, z_of(2.22) - 0.02), (cx + 0.035, cy + 0.035, z_of(2.22) + 0.02), reg)
    solid("SteeringWheel", (0.47 - 0.08, 1.0 - 0.07, z_of(2.22) - 0.03), (0.47 + 0.08, 1.0 + 0.06, z_of(2.22) + 0.03), "wheel_hi")
    solid("SteeringWheel", (0.47 - 0.17, 0.98, z_of(2.22) - 0.015), (0.47 + 0.17, 1.02, z_of(2.22) + 0.015), "black")


def lights():
    bone("Blinkers")
    for n in ("FrontLights", "StopLights", "BackLights", "FrontLeftTurnSignal", "FrontRightTurnSignal",
              "BackLeftTurnSignal", "BackRightTurnSignal", "LeftTurnSignal", "RightTurnSignal"):
        bone(n, "Blinkers")
    zf, zr = z_of(0), z_of(L)
    for s in (1, -1):
        x0, x1 = sorted((s * 0.52, s * 0.86))
        cube("FrontLights", (x0, 0.70, zf + 0.012), (x1, 0.82, zf + 0.022), {"north": "lamp_on"})
        # vertical strip lights at the corners
        cx = s * (W2 - RC_F + RC_F * math.sin(math.radians(55)))
        cz = z_of(RC_F) - RC_F * math.cos(math.radians(55))
        cube("FrontLights", (cx - 0.02, 0.42, cz + 0.012), (cx + 0.02, 0.84, cz + 0.022), {"north": "lamp_on"},
             rotation=[0, -s * 40, 0], pivot=(cx, 0, cz))
        cube("FrontLeftTurnSignal" if s > 0 else "FrontRightTurnSignal", (cx - 0.04, 0.56, cz + 0.012),
             (cx + 0.04, 0.62, cz + 0.022), {"north": "amber_on"}, rotation=[0, -s * 40, 0], pivot=(cx, 0, cz))
        # rear: vertical part of the L at the corner
        rx = s * (W2 - 0.04)
        cube("StopLights", (rx - 0.03, 0.78, zr - 0.15), (rx + 0.03, 1.10, zr - 0.05), {"east" if s > 0 else "west": "brake_on",
                                                                                        "south": "brake_on"})
        cube("BackLeftTurnSignal" if s > 0 else "BackRightTurnSignal", (s * 0.62 - 0.08, 0.40, zr - 0.022),
             (s * 0.62 + 0.08, 0.45, zr - 0.012), {"south": "amber_on"})
        cube("BackLights", (s * 0.45 - 0.05, 0.40, zr - 0.022), (s * 0.45 + 0.05, 0.45, zr - 0.012), {"south": "reverse_on"})
        n = "LeftTurnSignal" if s > 0 else "RightTurnSignal"
        cube(n, (s * 1.12 - 0.01, 1.05, z_of(1.82)), (s * 1.12 + 0.01, 1.08, z_of(1.70)), {"east" if s > 0 else "west": "amber_on"})
    cube("StopLights", (-0.92, 0.965, zr - 0.022), (0.92, 0.99, zr - 0.012), {"south": "brake_on"})
    cube("StopLights", (-0.3, 1.565, z_of(4.6) - 0.012), (0.3, 1.585, z_of(4.6) - 0.002), {"south": "brake_on"})


def details():
    # plates (text drawn by the renderer)
    bone("PlateFront", "Body", (0, 0.42, z_of(0) - 0.004))
    bone("PlateBack", "Trunk", (0, 0.425, z_of(L) + 0.004))
    # mirrors on the doors: gloss black caps, glass towards the back
    bone("Mirrors")
    for name, sx in (("LeftMirror", 1), ("RightMirror", -1)):
        x = sx * W2
        bone(name, "Mirrors", (sx * 1.2, 1.1, z_of(1.78)))
        x0, x1 = sorted((x, x + sx * 0.22))
        solid("Mirrors", (x0, 1.02, z_of(1.86)), (x1, 1.17, z_of(1.70)), "gloss")
        cube(name, (x0 + 0.01, 1.035, z_of(1.70)), (x1 - 0.01, 1.155, z_of(1.70) + 0.004), {"south": "mirror"})
    # wipers parked at the bottom of the windscreen, rear wiper on the hatch glass
    bone("Wipers")
    for name, x in (("1", 0.42), ("2", -0.30)):
        bone(name, "Wipers", (x, 1.10, z_of(1.66)))
        solid(name, (x - 0.38, 1.09, z_of(1.66) - 0.01), (x + 0.02, 1.11, z_of(1.66) + 0.01), "black")
    bone("BackWiper", "Trunk", (0, 1.2, z_of(4.96)))
    solid("BackWiper", (-0.32, 1.19, z_of(4.96) - 0.01), (0.0, 1.21, z_of(4.96) + 0.01), "black")


# ------------------------------------------------------------------ animations

def anim(length=None, loop=None, bones=None):
    a = {"bones": bones or {}}
    if length is not None:
        a["animation_length"] = length
    if loop is not None:
        a["loop"] = loop
    return a


def door_anim(b, rot, open_):
    k = {"rotation": {"0.0": {"vector": [0, 0, 0] if open_ else rot}, "0.6": {"vector": rot if open_ else [0, 0, 0]}}}
    return anim(0.6, "hold_on_last_frame", {b: k})


def write_animations():
    out = 0.02 * PX
    a = {}
    for i, (b, s) in enumerate((("Door1", 1), ("Door2", -1), ("Door3", 1), ("Door4", -1)), 1):
        a[f"{NAME}.door{i}_open"] = door_anim(b, [0, -s * 65, 0], True)
        a[f"{NAME}.door{i}_close"] = door_anim(b, [0, -s * 65, 0], False)
    a[f"{NAME}.trunk_open"] = door_anim("Trunk", [70, 0, 0], True)
    a[f"{NAME}.trunk_close"] = door_anim("Trunk", [70, 0, 0], False)
    a[f"{NAME}.front_lights"] = anim(loop=True, bones={"FrontLights": {"position": [0, 0, -out]}})
    a[f"{NAME}.stop_lights"] = anim(loop=True, bones={"StopLights": {"position": [0, 0, out]}})
    a[f"{NAME}.reverse_lights"] = anim(loop=True, bones={"BackLights": {"position": [0, 0, out]}})
    for side, names in (("left", ("FrontLeftTurnSignal", "BackLeftTurnSignal", "LeftTurnSignal")),
                        ("right", ("FrontRightTurnSignal", "BackRightTurnSignal", "RightTurnSignal"))):
        bs = {}
        for n in names:
            vec = [0, 0, -out] if n.startswith("Front") else [0, 0, out] if n.startswith("Back") else [out if side == "left" else -out, 0, 0]
            bs[n] = {"position": {"0.0": {"vector": vec}, "0.4": {"vector": [0, 0, 0]}}}
        a[f"{NAME}.{side}_signal"] = anim(0.8, True, bs)
    sweep = {"0.0": {"vector": [0, 0, 0]}, "0.6": {"vector": [0, 0, -75]}, "1.2": {"vector": [0, 0, 0]}}
    a[f"{NAME}.wipers"] = anim(1.2, True, {"1": {"rotation": sweep}, "2": {"rotation": sweep}})
    a[f"{NAME}.wipers_off"] = anim(loop=True)
    a[f"{NAME}.back_wiper"] = anim(1.6, True, {"BackWiper": {"rotation": {"0.0": {"vector": [0, 0, 0]}, "0.8": {"vector": [0, 0, -100]},
                                                                           "1.6": {"vector": [0, 0, 0]}}}})
    a[f"{NAME}.null"] = anim(loop=True)
    p = PTM_ASSETS / f"animations/car/ptmuk_{NAME}.animation.json"
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps({"format_version": "1.8.0", "animations": a}, indent=1))


# ------------------------------------------------------------------ output

def write_geo():
    bones = [{"name": "Car", "pivot": [0, 0, 0]}]
    for b in ORDER:
        e = {"name": b["name"], "parent": b["parent"], "pivot": b["pivot"]}
        if b["cubes"]:
            e["cubes"] = b["cubes"]
        bones.append(e)
    order, seen, by = [], set(), {b["name"]: b for b in bones}

    def visit(b):
        if b["name"] in seen:
            return
        if b.get("parent"):
            visit(by[b["parent"]])
        seen.add(b["name"])
        order.append(b)
    for b in bones:
        visit(b)
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.ptmuk_k4", "texture_width": ATLAS, "texture_height": ATLAS,
                        "visible_bounds_width": 8, "visible_bounds_height": 3, "visible_bounds_offset": [0, 1, 0]},
        "bones": order}]}
    p = PTM_ASSETS / f"geo/car/ptmuk_{NAME}.geo.json"
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(geo, separators=(",", ":")))
    print(f"  {NAME}: {sum(len(b.get('cubes', [])) for b in order)} cubes, {len(order)} bones")


def write_texture():
    p = ASSETS / f"textures/entity/car/{NAME}.png"
    p.parent.mkdir(parents=True, exist_ok=True)
    ATL.img.save(p)


def write_icon():
    s = 8
    img = Image.new("RGBA", (16 * s, 16 * s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.polygon([(6, 86), (10, 66), (40, 58), (62, 38), (100, 36), (120, 58), (122, 86)], fill=RED + (255,))
    d.polygon([(46, 58), (64, 42), (84, 41), (86, 58)], fill=(30, 34, 40, 255))
    d.polygon([(90, 58), (88, 41), (100, 41), (112, 58)], fill=(30, 34, 40, 255))
    d.rectangle((110, 60, 122, 64), fill=(255, 40, 40, 255))
    d.rectangle((6, 80, 122, 88), fill=TRIM + (255,))
    for cx in (30, 98):
        d.ellipse((cx - 13, 74, cx + 13, 100), fill=(20, 20, 20, 255))
        d.ellipse((cx - 7, 80, cx + 7, 94), fill=(190, 194, 198, 255))
    p = ASSETS / "textures/item/kia_k4_gt_line_s.png"
    p.parent.mkdir(parents=True, exist_ok=True)
    img.resize((16, 16), Image.LANCZOS).save(p)
    (ASSETS / "models/item/kia_k4_gt_line_s.json").write_text(json.dumps(
        {"parent": "minecraft:item/generated", "textures": {"layer0": "ptmuk:item/kia_k4_gt_line_s"}}, indent=1))


def main():
    build_textures()
    bone("Body")
    bone("Trunk", "Car", (0, 1.58, z_of(4.48)))
    body()
    doors()
    wheels()
    interior()
    lights()
    details()
    write_geo()
    write_texture()
    write_animations()
    write_icon()


if __name__ == "__main__":
    main()
