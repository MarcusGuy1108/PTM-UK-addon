"""A display car for showrooms, forecourts and car parks: a made-up five-door hatchback about the
size of PTM2's cars, with no maker's name or badge on it. A block model can't reach past three
blocks, so the car is drawn at half size and its block entity renderer draws it twice as big. The
body is tinted, so one model does every paint. Writes the model, its textures, the blockstates,
item models, loot and names.

Car coordinates are in blocks at full size: x across (0 in the middle), y up from the ground, z
along the car with the nose at -z (the way the block faces)."""
import math

import numpy as np
from PIL import Image, ImageDraw

PAINTS = {"red": (176, 24, 30), "white": (238, 238, 234), "black": (30, 30, 33), "silver": (170, 174, 180),
          "blue": (28, 58, 138), "grey": (90, 94, 100)}
G = None
K = 8                       # model pixels per block: the renderer doubles the model
rng = np.random.default_rng(4808)

L, W = 4.8, 2.08            # length and body width
NOSE, TAIL = -L / 2, L / 2
WHEEL_R, WHEEL_W = 0.36, 0.26
AXLES = (NOSE + 0.82, TAIL - 0.86)
SILL, BELT, ROOF = 0.24, 0.98, 1.64
ARCH = 0.42                 # half the length of a wheel arch
ARCH_TOP = 0.76


def ids():
    return [f"display_car_{p}" for p in PAINTS]


# ----------------------------------------------------------------- textures

def _img(size, rgb):
    return Image.new("RGBA", (size, size), tuple(rgb) + (255,))


def _noise(img, amount):
    a = np.asarray(img).astype(float)
    a[..., :3] += rng.normal(0, amount, a.shape[:2])[..., None]
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGBA")


def paint(size=32):
    """Near-white paint (the colour is the tint) with a soft gloss band."""
    yy = np.mgrid[0:size, 0:size][0].astype(float) / size
    v = 214 + 30 * np.exp(-((yy - 0.3) / 0.16) ** 2)
    a = np.dstack([v, v, v, np.full_like(v, 255)])
    return _noise(Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGBA"), 2)


def glass(size=32):
    img = _img(size, (34, 42, 52))
    d = ImageDraw.Draw(img)
    d.polygon([(size * 0.15, 0), (size * 0.4, 0), (size * 0.1, size), (0, size), (0, size * 0.6)], fill=(70, 84, 100))
    d.polygon([(size * 0.55, 0), (size * 0.62, 0), (size * 0.35, size), (size * 0.28, size)], fill=(58, 70, 84))
    return _noise(img, 2)


def wheel(size=32):
    """Tyre and alloy wheel side on, the corners clear."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = (size - 1) / 2
    d.ellipse([0, 0, size - 1, size - 1], fill=(22, 22, 24, 255))
    r = size * 0.33
    d.ellipse([c - r, c - r, c + r, c + r], fill=(176, 180, 186, 255))
    for k in range(5):
        a = k * 2 * math.pi / 5 - math.pi / 2
        for s in (-0.2, 0.2):
            d.polygon([(c, c), (c + r * math.cos(a + s), c + r * math.sin(a + s)),
                       (c + r * 0.85 * math.cos(a + 2 * math.pi / 10), c + r * 0.85 * math.sin(a + 2 * math.pi / 10))],
                      fill=(60, 62, 66, 255))
    d.ellipse([c - r * 0.22, c - r * 0.22, c + r * 0.22, c + r * 0.22], fill=(120, 124, 130, 255))
    return img


def tread(size=16):
    img = _img(size, (26, 26, 28))
    d = ImageDraw.Draw(img)
    for y in range(0, size, 3):
        d.line([(0, y), (size, y)], fill=(40, 40, 42))
    return img


def trim(size=16):
    return _noise(_img(size, (28, 29, 31)), 3)


def chrome(size=16):
    yy = np.mgrid[0:size, 0:size][0].astype(float) / size
    v = 150 + 90 * np.cos(yy * math.pi * 2) ** 2
    return Image.fromarray(np.dstack([v, v, v + 6, np.full_like(v, 255)]).clip(0, 255).astype(np.uint8), "RGBA")


def headlight(size=16):
    img = _img(size, (200, 206, 214))
    d = ImageDraw.Draw(img)
    d.rectangle([1, 4, size - 2, size - 5], fill=(236, 240, 246))
    d.line([(1, size - 4), (size - 2, size - 4)], fill=(250, 250, 255))
    d.ellipse([size * 0.55, 5, size * 0.85, size - 6], fill=(170, 176, 186))
    return img


def taillight(size=16):
    img = _img(size, (150, 16, 20))
    d = ImageDraw.Draw(img)
    d.rectangle([1, 2, size - 2, size * 0.45], fill=(210, 30, 34))
    d.rectangle([1, size * 0.62, size * 0.4, size - 3], fill=(236, 140, 40))
    d.rectangle([size * 0.45, size * 0.62, size - 2, size - 3], fill=(220, 220, 220))
    return img


def grille(size=16):
    img = _img(size, (18, 18, 20))
    d = ImageDraw.Draw(img)
    for y in range(1, size, 3):
        for x in range((y // 3) % 2, size, 3):
            d.point((x, y), fill=(56, 58, 62))
    return img


def plate(yellow, size=32):
    """A number plate: made-up letters as plain blocks of black (no real registration)."""
    img = Image.new("RGBA", (size, size // 4), (238, 196, 30, 255) if yellow else (240, 240, 236, 255))
    d = ImageDraw.Draw(img)
    x = 3
    for k in range(7):
        if k == 4:
            x += 2
        d.rectangle([x, 2, x + 2, size // 4 - 3], fill=(20, 20, 20))
        x += 4
    big = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    big.paste(img, (0, 0))
    return big


# ----------------------------------------------------------------- the model

def _p(x, y, z):
    """Car coordinates (blocks) to model pixels: the middle of the car is the middle of the block."""
    return [round(8 + x * K, 3), round(y * K, 3), round(8 + z * K, 3)]


def _box(x0, y0, z0, x1, y1, z1, tex, faces=None, rot=None, uv=None):
    frm, to = _p(min(x0, x1), min(y0, y1), min(z0, z1)), _p(max(x0, x1), max(y0, y1), max(z0, z1))
    el = G.box(frm, to, tex, faces=faces or ("north", "east", "south", "west", "up", "down"), rotation=rot)
    for f, face in el["faces"].items():
        if tex == "#paint":
            face["tintindex"] = 0
        if uv and f in uv:
            face["uv"] = uv[f]
    return el


def _sides(x, y0, z0, y1, z1, tex, t=0.02, **kw):
    """A thin panel on both sides of the car at |x|."""
    return [_box(s * x, y0, z0, s * (x + t), y1, z1, tex, **kw) for s in (-1, 1)]


def _chamfer(x, z, sx, sz, y0, y1, tex):
    """A 45 degree corner in plan at (x, z), bevelling CHAMFER off each way (sx, sz: which corner)."""
    c = CHAMFER
    mx, mz = x - sx * c / 2, z - sz * c / 2
    half = c * math.sqrt(2) / 2
    angle = 45 if sx * sz > 0 else -45
    return _box(mx - half, y0, mz - 0.06, mx + half, y1, mz + 0.06, tex, rot=("y", angle, _p(mx, y0, mz)))


CHAMFER = 0.26


def elements():
    hw = W / 2
    c = CHAMFER
    els = []
    # the body below the windows, cut away over the wheels, its corners bevelled
    cuts = [(a - ARCH, a + ARCH) for a in AXLES]
    edges = [NOSE + c, cuts[0][0], cuts[0][1], cuts[1][0], cuts[1][1], TAIL - c]
    for k in range(5):
        low = ARCH_TOP if k in (1, 3) else SILL
        top = 0.92 if k == 0 else BELT
        els.append(_box(-hw, low, edges[k], hw, top, edges[k + 1], "#paint"))
    for a0, a1 in cuts:                                              # the dark insides of the arches
        els.append(_box(-hw + 0.06, SILL, a0, hw - 0.06, ARCH_TOP, a1, "#trim", faces=("east", "west", "down")))
    els.append(_box(-hw + c, SILL, NOSE, hw - c, 0.86, NOSE + c, "#paint"))
    els.append(_box(-hw + c, SILL, TAIL - c, hw - c, 1.08, TAIL, "#paint"))
    for sx in (-1, 1):
        els.append(_chamfer(sx * hw, NOSE, sx, -1, SILL, 0.86, "#paint"))
        els.append(_chamfer(sx * hw, TAIL, sx, 1, SILL, 1.08, "#paint"))
    # nose and tail: bumpers, grille, lights, plates
    els.append(_box(-hw + c, SILL - 0.02, NOSE - 0.01, hw - c, 0.36, NOSE + 0.2, "#trim"))
    els.append(_box(-0.5, 0.42, NOSE - 0.012, 0.5, 0.62, NOSE + 0.05, "#grille", faces=("north",), uv={"north": [0, 0, 16, 16]}))
    for s in (-1, 1):
        els.append(_box(s * 0.44, 0.66, NOSE - 0.012, s * (hw - c - 0.02), 0.8, NOSE + 0.3, "#headlight",
                        faces=("north", "up"), uv={"north": [0, 0, 16, 16]}))
        els.append(_box(s * 0.5, 0.82, TAIL + 0.012, s * (hw - c - 0.02), 1.06, TAIL - 0.22, "#taillight",
                        faces=("south",), uv={"south": [0, 0, 16, 16]}))
    els.append(_box(-0.3, 0.27, NOSE - 0.03, 0.3, 0.39, NOSE - 0.01, "#plate_front", faces=("north",),
                    uv={"north": [0, 0, 16, 4]}))
    els.append(_box(-hw + c, SILL - 0.02, TAIL - 0.2, hw - c, 0.4, TAIL + 0.01, "#trim"))
    els.append(_box(-0.3, 0.6, TAIL + 0.012, 0.3, 0.72, TAIL + 0.03, "#plate_rear", faces=("south",),
                    uv={"south": [0, 0, 16, 4]}))
    # the glasshouse: windscreen leaning back, side windows, roof, a raked hatch
    cw = hw - 0.18
    rise = ROOF - BELT
    screen_base, hatch_base = -1.25, TAIL - 0.14
    ws_len, hatch_len = rise / math.cos(math.radians(45)), (rise - 0.1) / math.cos(math.radians(45))
    roof_front, roof_back = screen_base + rise, hatch_base - (rise - 0.1)
    els.append(_box(-cw, BELT, screen_base, cw, BELT + ws_len, screen_base + 0.04, "#glass", faces=("north", "south"),
                    rot=("x", 45, _p(0, BELT, screen_base)), uv={"north": [0, 0, 16, 16], "south": [0, 0, 16, 16]}))
    for s in (-1, 1):                                                 # A pillars along its edges
        els.append(_box(s * cw, BELT, screen_base - 0.02, s * (cw + 0.08), BELT + ws_len, screen_base + 0.05, "#paint",
                        rot=("x", 45, _p(0, BELT, screen_base))))
    els.append(_box(-cw, ROOF - 0.06, roof_front - 0.02, cw, ROOF, roof_back + 0.02, "#paint"))
    hatch_y = BELT + 0.1
    els.append(_box(-cw, hatch_y, hatch_base - 0.04, cw, hatch_y + hatch_len, hatch_base, "#glass", faces=("north", "south"),
                    rot=("x", -45, _p(0, hatch_y, hatch_base)), uv={"north": [0, 0, 16, 16], "south": [0, 0, 16, 16]}))
    els.append(_box(-hw + c, BELT, hatch_base - 0.12, hw - c, hatch_y, TAIL - 0.02, "#paint"))
    # side windows stepped to follow the screen and the hatch
    steps = 3
    for k in range(steps):
        y_top = BELT + rise * (1 - k / steps) - 0.06
        front = screen_base + (y_top - BELT) * 1.0
        back = hatch_base - max(0.0, y_top - hatch_y)
        y_bot = BELT if k == steps - 1 else BELT + rise * (1 - (k + 1) / steps) - 0.06
        els += _sides(cw, y_bot, front, y_top, back, "#glass", faces=("east", "west"))
    els += _sides(cw - 0.01, BELT, 0.1, ROOF - 0.06, 0.22, "#trim", t=0.04)                    # B pillar
    els += _sides(cw - 0.01, BELT, roof_back - 0.36, ROOF - 0.06, roof_back + 0.02, "#paint", t=0.04)   # C pillar
    els.append(_box(-cw, BELT, screen_base + 0.1, cw, BELT + 0.06, hatch_base - 0.12, "#trim", faces=("up",)))
    # door shut lines, handles and mirrors
    for z in (-1.12, 0.1, 1.0):
        els += _sides(hw, SILL + 0.06, z, BELT - 0.04, z + 0.025, "#trim", t=0.006, faces=("east", "west"))
    for z in (-0.4, 0.76):
        els += _sides(hw, 0.84, z, 0.88, z + 0.22, "#chrome", t=0.03)
    for s in (-1, 1):
        els.append(_box(s * cw, 1.02, screen_base + 0.08, s * (hw + 0.16), 1.2, screen_base + 0.24, "#paint"))
        els.append(_box(s * (hw + 0.02), 1.04, screen_base + 0.069, s * (hw + 0.15), 1.18, screen_base + 0.08, "#glass", faces=("south",)))
    # wheels: a round face each side, the tread an octagon
    for a in AXLES:
        for s in (-1, 1):
            x0, x1 = s * (hw - 0.02), s * (hw - 0.02 - WHEEL_W)
            els.append(_box(x0, 0, a - WHEEL_R, x1, 2 * WHEEL_R, a + WHEEL_R, "#wheel", faces=("east", "west"),
                            uv={"east": [0, 0, 16, 16], "west": [0, 0, 16, 16]}))
            els.append(_box(x0 - s * 0.01, 0.1, a - WHEEL_R, x1 + s * 0.01, 2 * WHEEL_R - 0.1, a + WHEEL_R, "#tread",
                            faces=("north", "south", "up", "down")))
            els.append(_box(x0 - s * 0.01, 0, a - WHEEL_R + 0.1, x1 + s * 0.01, 2 * WHEEL_R, a + WHEEL_R - 0.1, "#tread",
                            faces=("north", "south", "up", "down")))
    return els


def generate(gmod):
    global G
    G = gmod
    q = "car/"
    tex = {"paint": G.save(paint(), q + "display_paint"), "glass": G.save(glass(), q + "display_glass"),
           "wheel": G.save(wheel(), q + "display_wheel"), "tread": G.save(tread(), q + "display_tread"),
           "trim": G.save(trim(), q + "display_trim"), "chrome": G.save(chrome(), q + "display_chrome"),
           "headlight": G.save(headlight(), q + "display_headlight"), "taillight": G.save(taillight(), q + "display_taillight"),
           "grille": G.save(grille(), q + "display_grille"), "plate_front": G.save(plate(False), q + "display_plate_front"),
           "plate_rear": G.save(plate(True), q + "display_plate_rear")}
    model = {"render_type": "minecraft:cutout", "textures": dict(tex, particle=tex["paint"]), "elements": elements()}
    G.write_json(G.ASSETS / "models/block/display_car.json", model)
    G.write_json(G.ASSETS / "models/block/display_car_particle.json", {"textures": {"particle": tex["paint"]}})
    for name in ids():
        G.write_json(G.ASSETS / f"blockstates/{name}.json", {"variants": {"": {"model": f"{G.MOD_ID}:block/display_car_particle"}}})
        G.write_json(G.ASSETS / f"models/item/{name}.json", {
            "parent": f"{G.MOD_ID}:block/display_car",
            "display": {"gui": {"rotation": [25, 210, 0], "translation": [0, 2, 0], "scale": [0.42, 0.42, 0.42]},
                        "ground": {"translation": [0, 2, 0], "scale": [0.2, 0.2, 0.2]},
                        "fixed": {"rotation": [0, 90, 0], "scale": [0.4, 0.4, 0.4]},
                        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.25] * 3},
                        "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.3] * 3},
                        "firstperson_lefthand": {"rotation": [0, 225, 0], "scale": [0.3] * 3}}})


def lang(mid):
    return {f"block.{mid}.display_car_{p}": f"Display Car ({p.title()})" for p in PAINTS}
