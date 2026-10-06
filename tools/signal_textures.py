"""High-resolution texture painting for the UK signals (used by generate_assets.py).

Everything is painted procedurally with numpy so it stays reproducible and tweakable:
lenses at 128 px (LED dot matrices with bloom, incandescent fresnel lenses with a glowing
bulb, glass highlights), faceplates with lens recesses sized to each module, 64 px
housings and 256 px backing boards.
"""
import math

import numpy as np
from PIL import Image, ImageDraw, ImageFont

FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
LENS = 128
rng = np.random.default_rng(1108)

# (hot centre, lit edge, unlit glass) - UK green is distinctly blue-green
UK = {
    "red": (np.array([255, 190, 160.0]), np.array([205, 18, 10.0]), np.array([62, 9, 6.0])),
    "amber": (np.array([255, 236, 170.0]), np.array([230, 118, 0.0]), np.array([72, 38, 3.0])),
    "green": (np.array([200, 255, 238.0]), np.array([0, 170, 122.0]), np.array([4, 44, 34.0])),
}
# saturated colour of a lit LED die
LED = {"red": np.array([255, 52, 34.0]), "amber": np.array([255, 178, 28.0]), "green": np.array([30, 255, 186.0])}


# ----------------------------------------------------------------- helpers

def grid(size):
    y, x = np.mgrid[0:size, 0:size].astype(float)
    c = (size - 1) / 2
    return x - c, y - c


def to_image(rgb, alpha=None):
    rgb = np.clip(rgb, 0, 255).astype(np.uint8)
    if alpha is None:
        alpha = np.full(rgb.shape[:2], 255, np.uint8)
    else:
        alpha = np.clip(alpha, 0, 255).astype(np.uint8)
    return Image.fromarray(np.dstack([rgb, alpha]), "RGBA")


def blur(arr, radius):
    """Separable Gaussian blur of a 2D float array (sigma = radius px), edges clamped."""
    n = int(radius * 3) + 1
    k = np.exp(-(np.arange(-n, n + 1) ** 2) / (2 * radius ** 2))
    k /= k.sum()
    padded = np.pad(arr, n, mode="edge")
    rows = np.apply_along_axis(lambda v: np.convolve(v, k, mode="valid"), 1, padded)
    return np.apply_along_axis(lambda v: np.convolve(v, k, mode="valid"), 0, rows)


def noise(shape, amount):
    return rng.normal(0, amount, shape)


def mask_from_draw(fn, size=LENS, ss=4):
    """Anti-aliased 0..1 mask drawn with PIL at ss x supersampling. fn(draw, k) where k
    converts the 0..32 design grid to pixels."""
    big = Image.new("L", (size * ss, size * ss), 0)
    fn(ImageDraw.Draw(big), size * ss / 32.0)
    return np.asarray(big.resize((size, size), Image.LANCZOS)) / 255.0


def hex_dots(size, pitch, sigma, within):
    """0..1 field of soft LED dots on a hex grid, only where `within` (bool array) is set."""
    field = np.zeros((size, size))
    yy, xx = np.mgrid[0:size, 0:size].astype(float)
    row = 0
    y = pitch / 2
    while y < size:
        x = pitch / 2 + (pitch / 2 if row % 2 else 0)
        while x < size:
            ix, iy = int(round(x)), int(round(y))
            if 0 <= ix < size and 0 <= iy < size and within[iy, ix]:
                x0, x1 = max(0, ix - 4), min(size, ix + 5)
                y0, y1 = max(0, iy - 4), min(size, iy + 5)
                d2 = (xx[y0:y1, x0:x1] - x) ** 2 + (yy[y0:y1, x0:x1] - y) ** 2
                field[y0:y1, x0:x1] = np.maximum(field[y0:y1, x0:x1], np.exp(-d2 / (2 * sigma ** 2)))
            x += pitch
        y += pitch * 0.866
        row += 1
    return field


def specular(x, y, radius, strength, start=200, end=265, width=0.06, at=0.8):
    """Curved glass highlight (top-left arc) as an additive 0..strength field."""
    d = np.hypot(x, y) / radius
    ang = (np.degrees(np.arctan2(y, x)) + 360) % 360
    band = np.exp(-((d - at) / width) ** 2)
    span = np.clip(1 - np.abs((ang - (start + end) / 2) / ((end - start) / 2)), 0, 1) ** 0.6
    return band * span * strength


# ----------------------------------------------------------------- lenses

def _round_alpha(x, y, radius):
    d = np.hypot(x, y)
    return np.clip((radius + 0.5 - d) * 255, 0, 255)


def lens_led(colour, lit, symbol=None):
    """Round LED aspect, 128 px, transparent outside the lens (render as cutout)."""
    core, edge, glass = UK[colour]
    x, y = grid(LENS)
    R = LENS / 2 - 1.5
    d = np.hypot(x, y)
    r = np.clip(d / R, 0, 1)
    inside = d < R * 0.93
    dots = hex_dots(LENS, 6.6, 1.25, inside)
    fresnel = 1 - 0.05 * (np.cos(d * 2 * math.pi / 8.5) > 0.7)
    shape = symbol if symbol is not None else (inside * 1.0)

    # unlit: smoked diffuser with faint dots and the small coloured centre marker
    off = np.zeros((LENS, LENS, 3)) + np.array([13, 14, 16.0])
    off += glass * 0.45 * (1 - r)[..., None] ** 1.3
    off += (glass * 0.55 + 10)[None, None, :] * (dots * (0.5 if symbol is None else 0.25 + 0.6 * shape))[..., None]
    if symbol is None:
        marker = np.exp(-(d / 3.2) ** 2)
        off += (np.minimum(glass * 3.0 + 25, 255) - off) * marker[..., None]
    off += 24 * np.clip(-y / R, 0, 1)[..., None] ** 2 * (r < 0.95)[..., None]  # daylight on the glass
    rgb = off

    if lit:
        diffuse = edge * (0.38 + 0.32 * (1 - r))[..., None]
        led = diffuse + (LED[colour] - diffuse) * np.clip(dots * 1.2, 0, 1)[..., None]
        hot = np.exp(-(d / 13) ** 2)
        led = led + (core - led) * (0.6 * hot)[..., None]
        bloom = blur(shape * 1.0, 3.5)
        m = np.clip(shape, 0, 1)[..., None]
        rgb = rgb * (1 - m) + led * m
        rgb += (edge * 0.55)[None, None, :] * np.clip(bloom - shape, 0, 1)[..., None]
    rgb *= fresnel[..., None]
    rgb += specular(x, y, R, 70 if not lit else 35)[..., None]
    rim = np.clip((d - R * 0.93) / (R * 0.07), 0, 1)
    rgb = rgb * (1 - 0.8 * rim)[..., None] + np.array([8, 8, 9.0]) * (0.8 * rim)[..., None]
    rgb += noise((LENS, LENS, 1), 1.5)
    return to_image(rgb, _round_alpha(x, y, R))


def lens_classic(colour, lit, symbol=None):
    """Round incandescent aspect: fresnel rings, bulb hot-spot, coloured glass when unlit."""
    core, edge, glass = UK[colour]
    x, y = grid(LENS)
    R = LENS / 2 - 1.5
    d = np.hypot(x, y)
    r = np.clip(d / R, 0, 1)
    theta = np.arctan2(y, x)
    rings = np.cos(d * 2 * math.pi / 5.6)
    prisms = 1 + 0.035 * np.cos(theta * 40)

    if lit:
        bright = LED[colour] * 0.85 + edge * 0.15
        rgb = edge * (0.62 + 0.38 * (1 - r))[..., None] + (bright - edge) * ((1 - r) ** 1.4)[..., None]
        rgb = rgb * (0.88 + 0.12 * rings)[..., None] * prisms[..., None]
        bulb = np.exp(-(np.hypot(x, y + 3) / 10) ** 2)
        rgb += (core - rgb) * (0.85 * bulb)[..., None]
    else:
        rgb = glass * (0.85 + 0.6 * (1 - r))[..., None]
        rgb = rgb * (0.85 + 0.3 * (rings > 0.6))[..., None] * prisms[..., None]
        reflector = np.exp(-(np.hypot(x, y + 3) / 20) ** 2)
        rgb += np.array([46, 44, 40.0]) * (0.55 * reflector)[..., None] + glass * (0.6 * reflector)[..., None]
        rgb += 38 * np.clip(-y / R, 0, 1)[..., None] ** 2        # sky reflection on the top half

    if symbol is not None:
        # stencilled aspect: black mask with the symbol cut through
        stencil = np.array([17, 17, 18.0]) * (0.9 + 0.1 * rings)[..., None]
        m = symbol[..., None]
        rgb = stencil * (1 - m) + rgb * m
        if lit:
            rgb += (edge * 0.6)[None, None, :] * np.clip(blur(symbol, 3) - symbol, 0, 1)[..., None]
    rgb += specular(x, y, R, 95 if not lit else 40)[..., None]
    rgb += specular(x, y, R, 40 if not lit else 15, start=20, end=60, width=0.05, at=0.86)[..., None]
    rim = np.clip((d - R * 0.92) / (R * 0.08), 0, 1)
    rgb = rgb * (1 - 0.75 * rim)[..., None] + np.array([22, 22, 21.0]) * (0.75 * rim)[..., None]
    rgb += noise((LENS, LENS, 1), 1.5)
    return to_image(rgb, _round_alpha(x, y, R))


def fit_mask(mask, scale, dx=0.0):
    """Shrink a symbol mask about the centre (and shift it by dx * size) to fit a round lens."""
    size = mask.shape[0]
    small = Image.fromarray((mask * 255).astype(np.uint8), "L").resize((int(size * scale),) * 2, Image.LANCZOS)
    out = Image.new("L", (size, size), 0)
    off = (size - small.width) // 2
    out.paste(small, (off + int(dx * size), off))
    return np.asarray(out) / 255.0


def square_lens(colour, lit, symbol, style):
    """Pedestrian / toucan aspect: square black fascia with a lit figure."""
    core, edge, glass = UK[colour]
    x, y = grid(LENS)
    rgb = np.zeros((LENS, LENS, 3)) + np.array([12, 12, 13.0])
    rgb += noise((LENS, LENS, 1), 1.2)
    m = symbol[..., None]
    if style == "led":
        within = symbol > 0.3
        dots = hex_dots(LENS, 5.2, 1.05, within)
        if lit:
            fill = edge * 0.55 + (LED[colour] - edge * 0.55) * np.clip(dots * 1.15, 0, 1)[..., None]
            rgb = rgb * (1 - m) + fill * m
            rgb += (edge * 0.5)[None, None, :] * np.clip(blur(symbol, 3.5) - symbol, 0, 1)[..., None]
        else:
            rgb += (glass * 0.7 + 8)[None, None, :] * (dots * 0.8 + 0.25 * symbol)[..., None]
    else:
        if lit:
            fill = LED[colour] * 0.8 + edge * 0.2
            rgb = rgb * (1 - m) + fill[None, None, :] * m
            rgb += (edge * 0.55)[None, None, :] * np.clip(blur(symbol, 3) - symbol, 0, 1)[..., None]
        else:
            rgb = rgb * (1 - m) + (glass * 1.3 + 6)[None, None, :] * m
    # glossy cover glass
    rgb += 22 * np.clip(-(y + x * 0.4) / LENS * 2 - 0.25, 0, 1)[..., None]
    edge_px = np.maximum(np.abs(x), np.abs(y)) > LENS / 2 - 3
    rgb[edge_px] = [26, 27, 28]
    return to_image(rgb)


def flash(on, off):
    strip = Image.new("RGBA", (on.width, on.height * 2))
    strip.paste(on, (0, 0))
    strip.paste(off, (0, on.height))
    return strip


# ----------------------------------------------------------------- symbols (0..1 masks)

def arrow_mask(direction):
    def draw(d, k):
        pts = [(16, 3.5), (27, 15), (20.5, 15), (20.5, 28.5), (11.5, 28.5), (11.5, 15), (5, 15)]
        a = math.radians({"ahead": 0, "left": 90, "right": -90}[direction])
        d.polygon([(((px - 16) * math.cos(a) + (py - 16) * math.sin(a) + 16) * k,
                    (-(px - 16) * math.sin(a) + (py - 16) * math.cos(a) + 16) * k) for px, py in pts], fill=255)
    return mask_from_draw(draw)


def bicycle_mask():
    def draw(d, k):
        w = int(2.3 * k)
        d.ellipse([3 * k, 15 * k, 13 * k, 25 * k], outline=255, width=w)
        d.ellipse([19 * k, 15 * k, 29 * k, 25 * k], outline=255, width=w)
        for a, b in (((8, 20), (14, 12)), ((14, 12), (23, 12)), ((23, 12), (24, 20)), ((8, 20), (16, 20)),
                     ((16, 20), (23, 12)), ((14, 12), (16, 20)), ((23, 12), (22, 8)), ((20, 8), (25.5, 8)),
                     ((11.5, 10.5), (16.5, 10.5))):
            d.line([(a[0] * k, a[1] * k), (b[0] * k, b[1] * k)], fill=255, width=w)
    return mask_from_draw(draw)


def standing_man_mask():
    def draw(d, k):
        d.ellipse([13 * k, 2.5 * k, 19 * k, 8.5 * k], fill=255)
        d.rounded_rectangle([11 * k, 9.5 * k, 21 * k, 20 * k], radius=2.2 * k, fill=255)
        d.rounded_rectangle([8.6 * k, 10 * k, 11.6 * k, 19.5 * k], radius=1.4 * k, fill=255)
        d.rounded_rectangle([20.4 * k, 10 * k, 23.4 * k, 19.5 * k], radius=1.4 * k, fill=255)
        d.rounded_rectangle([12 * k, 18.5 * k, 15.6 * k, 29.5 * k], radius=1.2 * k, fill=255)
        d.rounded_rectangle([16.4 * k, 18.5 * k, 20 * k, 29.5 * k], radius=1.2 * k, fill=255)
    return mask_from_draw(draw)


def walking_man_mask():
    def draw(d, k):
        w = int(3.4 * k)
        d.ellipse([14 * k, 2.5 * k, 20 * k, 8.5 * k], fill=255)
        d.line([(16.3 * k, 10 * k), (14.2 * k, 19 * k)], fill=255, width=int(4.6 * k))
        d.line([(15.6 * k, 12 * k), (9.5 * k, 17.5 * k)], fill=255, width=w)
        d.line([(15.6 * k, 12 * k), (22 * k, 16.5 * k)], fill=255, width=w)
        d.line([(14.2 * k, 19 * k), (9 * k, 28.5 * k)], fill=255, width=w)
        d.line([(14.2 * k, 19 * k), (20 * k, 23 * k), (21.5 * k, 29 * k)], fill=255, width=w)
    return mask_from_draw(draw)


# ----------------------------------------------------------------- housings, faceplates, boards

BASES = {"led": (23, 24, 26), "led_tunnel": (27, 28, 31), "classic": (31, 32, 31), "classic_large_green": (33, 34, 34)}


def housing(style, size=64):
    base = np.array(BASES[style], float)
    yy = np.mgrid[0:size, 0:size][0].astype(float)
    rgb = base + noise((size, size, 1), 2.2) + (6 * (0.5 - yy / size))[..., None]
    if style.startswith("classic"):
        # cast aluminium ribs and paint wear
        rib = (yy % 8 < 1.2)
        rgb[rib] -= 8
        rgb[(yy % 8 > 1.2) & (yy % 8 < 2.2)] += 7
        chips = rng.random((size, size)) > 0.996
        rgb[chips] = [70, 70, 66]
    else:
        # fine moulded grain
        rgb += noise((size, size, 1), 1.0) * (rng.random((size, size, 1)) > 0.5)
    return to_image(rgb)


def hood_inside(size=32):
    yy = np.mgrid[0:size, 0:size][0].astype(float)
    rgb = np.zeros((size, size, 3)) + 7 + (6 * yy / size)[..., None] + noise((size, size, 1), 1.0)
    return to_image(rgb)


def grey_metal(size=64):
    yy, xx = np.mgrid[0:size, 0:size].astype(float)
    rgb = np.array([118, 121, 124.0]) + noise((size, size, 1), 3.5) + (10 * np.sin(xx / 3.0))[..., None] * 0.2
    rgb += (14 * (0.5 - yy / size))[..., None]
    return to_image(rgb)


def detector_lens(size=64):
    x, y = grid(size)
    d = np.hypot(x, y) / (size / 2)
    rgb = np.array([16, 20, 30.0]) + (np.array([40, 60, 90.0]) * np.clip(1 - d, 0, 1)[..., None] ** 3)
    rgb += specular(x, y, size / 2, 90)[..., None]
    return to_image(rgb)


def faceplate(style, w, h, lenses, size=128):
    """Front of a housing module (w x h block px) with a recess for each lens.
    lenses: (cx, cy, diameter, round) in module px, origin bottom-left. Drawn pre-squashed so
    circles stay round once the square texture is stretched over the module face."""
    sx, sy = size / w, size / h
    yy, xx = np.mgrid[0:size, 0:size].astype(float)
    mx, my = xx / sx, (size - 1 - yy) / sy          # module coordinates, y up
    base = np.array(BASES[style], float) - 4
    rgb = base + noise((size, size, 1), 1.8)

    # raised rim around the module edge: light top/left, dark bottom/right
    rim = 0.32
    near = np.minimum.reduce([mx, my, w - mx, h - my])
    band = near < rim
    rgb[band & ((my > h - rim) | (mx < rim))] += 14
    rgb[band & ((my < rim) | (mx > w - rim))] -= 8

    for cx, cy, dia, rnd in lenses:
        if rnd:
            dist = np.hypot(mx - cx, my - cy)
        else:
            dist = np.maximum(np.abs(mx - cx), np.abs(my - cy))
        rr = dia / 2
        recess = dist < rr + 0.32
        # inner shadow of the recess, darker towards the top (hood shade)
        depth = np.clip((rr + 0.32 - dist) / 0.32, 0, 1)
        rgb[recess] = (np.array([6, 6, 7.0]) + 6 * depth[recess, None] + 4 * ((my[recess] - cy) < 0)[:, None])
        # glossy gasket ring
        ring = (dist > rr + 0.32) & (dist < rr + 0.48)
        light = (my - cy) / (rr + 0.4)
        rgb[ring] = np.array([34, 35, 37.0]) + 22 * np.clip(light[ring], 0, 1)[:, None]

    # screws
    screws = [(0.55, h - 0.55), (w - 0.55, h - 0.55)]
    if style == "classic":
        screws += [(0.55, 0.55), (w - 0.55, 0.55)]
    for scx, scy in screws:
        sd = np.hypot(mx - scx, my - scy)
        rgb[sd < 0.2] = [58, 58, 56]
        rgb[(sd < 0.2) & (np.abs((mx - scx) - (my - scy)) < 0.05)] = [25, 25, 25]
    return to_image(rgb)


def board(w, h, size=256, fill=(44, 46, 49)):
    """Backing board w x h block px: dark grey with a white retroreflective border and rounded
    corners (transparent outside; render as cutout). Drawn pre-squashed for non-square boards."""
    sx, sy = size / w, size / h
    yy, xx = np.mgrid[0:size, 0:size].astype(float)
    mx, my = (xx + 0.5) / sx, (yy + 0.5) / sy

    def rounded(inset, radius):
        cx = np.clip(mx, inset + radius, w - inset - radius)
        cy = np.clip(my, inset + radius, h - inset - radius)
        return np.hypot(mx - cx, my - cy) <= radius

    outer, inner = rounded(0, 1.1), rounded(0.5, 0.65)      # narrow white retroreflective strip
    rgb = np.zeros((size, size, 3)) + np.array(fill, float) + noise((size, size, 1), 1.6)
    rgb += (8 * (0.5 - my / h))[..., None]
    # retroreflective border with a faint micro-prism pattern
    prism = 0.5 + 0.5 * np.sin(xx * 1.9) * np.sin(yy * 1.9)
    border = outer & ~inner
    rgb[border] = (np.array([232, 233, 229.0]) + (6 * prism[border])[:, None] + noise((border.sum(), 1), 2))
    # thin shadow line where the border meets the board
    edge = inner & ~rounded(0.62, 0.55)
    rgb[edge] -= 10
    # fixing clips top and bottom
    for cy in (0.25, h - 0.25):
        clip = (np.abs(mx - w / 2) < 0.5) & (np.abs(my - cy) < 0.15)
        rgb[clip] = [150, 152, 155]
    return to_image(rgb, outer * 255.0)


def sign(kind, size=128):
    ss = 4
    S = size * ss
    img = Image.new("RGBA", (S, S), (14, 14, 15, 255))
    d = ImageDraw.Draw(img)
    c, r = S / 2, S / 2 - 6 * ss
    red, blue, black, white = (206, 18, 30), (0, 80, 158), (14, 14, 14), (250, 250, 248)
    k = S / 128

    if kind in ("ahead_only", "turn_left", "turn_right"):
        d.ellipse([c - r, c - r, c + r, c + r], fill=white)
        d.ellipse([c - r + 4 * k, c - r + 4 * k, c + r - 4 * k, c + r - 4 * k], fill=blue)
        pts = [(0, -40), (25, -11), (10, -11), (10, 38), (-10, 38), (-10, -11), (-25, -11)]
        a = math.radians({"ahead_only": 0, "turn_left": 90, "turn_right": -90}[kind])
        d.polygon([(c + (px * math.cos(a) + py * math.sin(a)) * k, c + (-px * math.sin(a) + py * math.cos(a)) * k)
                   for px, py in pts], fill=white)
    elif kind == "except_buses":
        d.ellipse([c - r, c - r, c + r, c + r], fill=white, outline=black, width=int(4 * k))
        font = ImageFont.truetype(FONT, int(18 * k))
        for i, line in enumerate(["Except", "buses,", "taxis &", "cycles"]):
            tw = d.textlength(line, font=font)
            d.text((c - tw / 2, (19 + i * 23) * k), line, fill=black, font=font)
    else:
        d.ellipse([c - r, c - r, c + r, c + r], fill=red)
        d.ellipse([c - r + 14 * k, c - r + 14 * k, c + r - 14 * k, c + r - 14 * k], fill=white)
        w = int(13 * k)
        if kind in ("no_left_turn", "no_right_turn"):
            s = 1 if kind == "no_right_turn" else -1
            d.line([(c - 15 * s * k, c + 38 * k), (c - 15 * s * k, c - 6 * k), (c + 3 * s * k, c - 6 * k)],
                   fill=black, width=w, joint="curve")
            d.polygon([(c + 2 * s * k, c - 24 * k), (c + 28 * s * k, c - 6 * k), (c + 2 * s * k, c + 12 * k)], fill=black)
            bar = [(c - 36 * k, c + 36 * k), (c + 36 * k, c - 36 * k)] if s == 1 else \
                [(c - 36 * k, c - 36 * k), (c + 36 * k, c + 36 * k)]
        else:  # no U-turn
            d.line([(c - 17 * k, c + 36 * k), (c - 17 * k, c - 8 * k)], fill=black, width=w)
            d.arc([c - 24 * k, c - 28 * k, c + 24 * k, c + 12 * k], 180, 360, fill=black, width=w)
            d.line([(c + 17 * k, c - 8 * k), (c + 17 * k, c + 6 * k)], fill=black, width=w)
            d.polygon([(c + 1 * k, c + 4 * k), (c + 33 * k, c + 4 * k), (c + 17 * k, c + 27 * k)], fill=black)
            bar = [(c - 36 * k, c - 36 * k), (c + 36 * k, c + 36 * k)]
        d.line(bar, fill=red, width=int(12 * k))
    img = img.resize((size, size), Image.LANCZOS)
    # gentle glow of the internally lit sign and a cover-glass sheen
    arr = np.asarray(img).astype(float)
    x, y = grid(size)
    arr[..., :3] += specular(x, y, size / 2 - 6, 30, start=195, end=270, width=0.05, at=0.88)[..., None]
    return to_image(arr[..., :3])


def push_button_face(lit, w=5.0, h=8.0, size=128):
    """Front of a UK pedestrian push-button unit (w x h block px, drawn pre-squashed):
    instruction panel, WAIT indicator (lit or unlit), wait / cross-with-care diagram, button."""
    ss = 4
    S = size * ss
    img = Image.new("RGBA", (S, S), (24, 25, 27, 255))
    d = ImageDraw.Draw(img)
    kx, ky = S / w, S / h          # pixels per block px

    def R(x1, y1, x2, y2):        # rectangle in block px from the top-left
        return [x1 * kx, y1 * ky, x2 * kx, y2 * ky]

    def text(line, cx, top, height, fill):
        font = ImageFont.truetype(FONT, max(8, int(height * ky)))
        tw = d.textlength(line, font=font)
        # squash horizontally to undo the texture stretch
        layer = Image.new("RGBA", (int(tw) + 8, int(height * ky * 1.4)), (0, 0, 0, 0))
        ImageDraw.Draw(layer).text((4, 0), line, fill=fill, font=font)
        layer = layer.resize((max(1, int(layer.width * ky / kx)), layer.height), Image.LANCZOS)
        img.alpha_composite(layer, (int(cx * kx - layer.width / 2), int(top * ky)))

    d.rounded_rectangle(R(0.15, 0.15, w - 0.15, h - 0.15), radius=0.3 * kx, outline=(40, 41, 44), width=int(0.08 * kx))
    d.ellipse(R(w / 2 - 0.15, 0.3, w / 2 + 0.15, 0.6), fill=(150, 150, 146))                    # top screw
    d.rectangle(R(0.55, 0.85, w - 0.55, 6.25), fill=(8, 8, 9))                                    # window
    d.rectangle(R(0.65, 0.95, w - 0.65, 2.0), fill=(222, 224, 222))                               # instructions
    text("PEDESTRIANS", w / 2, 1.0, 0.36, (15, 15, 15))
    text("push button and wait", w / 2, 1.42, 0.24, (15, 15, 15))
    text("for signal opposite", w / 2, 1.70, 0.24, (15, 15, 15))
    wait_bg = (40, 26, 8) if lit else (196, 194, 186)
    d.rectangle(R(0.65, 2.12, w - 0.65, 3.22), fill=wait_bg)
    text("WAIT", w / 2, 2.17, 0.85, (255, 196, 70) if lit else (232, 221, 206))
    # wait / cross with care diagram
    text("wait", 1.55, 3.4, 0.2, (225, 225, 225))
    text("cross", 3.45, 3.32, 0.18, (225, 225, 225))
    text("with care", 3.45, 3.52, 0.18, (225, 225, 225))
    d.line([2.5 * kx, 3.35 * ky, 2.5 * kx, 6.05 * ky], fill=(210, 210, 210), width=int(0.04 * kx))
    for cx, man in ((1.55, "red"), (3.45, "green")):
        d.rectangle(R(cx - 0.45, 3.85, cx + 0.45, 5.75), outline=(220, 220, 220), width=int(0.04 * kx))
        d.line([(cx - 0.45) * kx, 4.8 * ky, (cx + 0.45) * kx, 4.8 * ky], fill=(220, 220, 220), width=int(0.04 * kx))
        y0 = 3.95 if man == "red" else 4.9
        col = (200, 40, 25) if man == "red" else (40, 175, 90)
        d.ellipse(R(cx - 0.08, y0, cx + 0.08, y0 + 0.16), fill=col)
        d.rectangle(R(cx - 0.1, y0 + 0.18, cx + 0.1, y0 + 0.5), fill=col)
        d.rectangle(R(cx - 0.1, y0 + 0.5, cx - 0.02, y0 + 0.75), fill=col)
        d.rectangle(R(cx + 0.02, y0 + 0.5, cx + 0.1, y0 + 0.75), fill=col)
    # push button with chrome ring
    d.ellipse(R(w / 2 - 0.42, 6.75, w / 2 + 0.42, 7.55), fill=(30, 30, 32), outline=(70, 70, 72), width=int(0.05 * kx))
    d.ellipse(R(w / 2 - 0.28, 6.88, w / 2 + 0.28, 7.42), fill=(212, 212, 208))
    d.ellipse(R(w / 2 - 0.17, 6.95, w / 2 + 0.05, 7.12), fill=(240, 240, 238))
    out = img.resize((size, size), Image.LANCZOS)
    arr = np.asarray(out).astype(float)
    arr[..., :3] += noise((size, size, 1), 1.0)
    return to_image(arr[..., :3])


def yellow_plastic(size=32):
    return to_image(np.array([238, 190, 20.0]) + noise((size, size, 1), 4))

def pole(kind, size=64):
    """Pole surface: satin black paint or spangled galvanised steel, with faint vertical
    drawing marks."""
    yy, xx = np.mgrid[0:size, 0:size].astype(float)
    if kind == "black":
        rgb = np.array([21, 22, 23.0]) + noise((size, size, 1), 1.6)
        rgb += (3 * np.sin(xx / 2.3 + rng.normal(0, 0.3, (size, size))))[..., None]
    elif kind == "galvanised":
        rgb = np.array([150, 154, 156.0]) + noise((size, size, 1), 4)
        # zinc spangle: blotches of slightly different brightness
        spangle = blur(rng.normal(0, 1, (size, size)), 2.2)
        rgb += (spangle / (np.abs(spangle).max() + 1e-6) * 14)[..., None]
        rgb += (4 * np.sin(xx / 1.7))[..., None]
    else:  # black plastic cap / collar
        rgb = np.array([14, 14, 15.0]) + noise((size, size, 1), 1.2) + (5 * (0.5 - yy / size))[..., None]
    return to_image(rgb)
