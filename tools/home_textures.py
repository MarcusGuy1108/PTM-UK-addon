"""Textures for the home furniture: fabrics, leather, woods, gloss and ceramic, appliance fronts,
screens with made-up programmes, flames, books and plants. Drawn procedurally, nothing copied."""
import math

import numpy as np
from PIL import Image, ImageDraw

import furniture_hd as H
from signal_textures import blur, noise, rng, to_image

S = 64


def _base(colour, size, n=2.0):
    return np.array(colour, float) + noise((size, size, 1), n)


# ----------------------------------------------------------------- materials

def fabric(colour, size=S):
    """Woven upholstery: a fine basket weave, slubs in the yarn and soft light falloff."""
    yy, xx = np.mgrid[0:size, 0:size].astype(float)
    weave = (np.sin(xx * math.pi) * np.sin(yy * math.pi * 0.5)) * 4 + np.where((xx + yy) % 4 < 2, 3, -3)
    slub = H.blur_x(rng.normal(0, 1, (size, size)), 3) * 5
    rgb = _base(colour, size, 2.5) + (weave + slub)[..., None]
    rgb *= (1 - 0.05 * blur(rng.random((size, size)), 6))[..., None]
    return to_image(rgb)


def cushion(colour, size=S):
    """A seat cushion seen from above: fabric with a piped seam round the edge, a soft dip in the
    middle where people sit."""
    yy, xx = np.mgrid[0:size, 0:size].astype(float)
    rgb = np.asarray(fabric(colour, size))[..., :3].astype(float)
    d = np.hypot((xx - size / 2) / (size / 2), (yy - size / 2) / (size / 2))
    rgb *= (1 - 0.1 * np.clip(1 - d, 0, 1) ** 2)[..., None]
    edge = np.minimum(np.minimum(xx, size - 1 - xx), np.minimum(yy, size - 1 - yy))
    rgb[edge < 1.5] *= 0.72
    rgb[(edge >= 1.5) & (edge < 3)] *= 1.08
    return to_image(rgb)


def leather(colour, size=S):
    """Leather: fine pebbled grain, creases and a soft sheen."""
    grain = blur(rng.normal(0, 1, (size, size)), 0.8) * 6
    crease = (blur(rng.normal(0, 1, (size, size)), 3) > 0.9) * -14
    rgb = _base(colour, size, 1.5) + (grain + crease)[..., None]
    rgb += (blur(rng.random((size, size)), 8) * 16 - 8)[..., None]
    return to_image(rgb)


def timber(base, size=S, plank=None):
    """Furniture timber: long straight grain with darker figure, no weathering."""
    yy, xx = np.mgrid[0:size, 0:size].astype(float)
    wobble = blur(rng.normal(0, 1, (size, size)), 3) * 4
    figure = np.sin((yy + wobble) * 0.9 + 3 * np.sin(xx * 0.05)) * 7
    streak = H.blur_x(rng.normal(0, 1, (size, size)), 10) * 6
    rgb = _base(base, size, 1.8) + (figure + streak)[..., None]
    if plank:
        rgb[(yy % plank) < 0.8] *= 0.78
    return to_image(rgb)


def gloss(colour, size=S):
    """Gloss painted or lacquered board: flat colour with a broad soft reflection."""
    yy, xx = np.mgrid[0:size, 0:size].astype(float)
    refl = np.exp(-((xx - yy * 0.6 - size * 0.25) / (size * 0.22)) ** 2) * 10
    return to_image(_base(colour, size, 0.8) + refl[..., None])


def ceramic(size=S):
    """Vitreous china: white with a cool glaze and bright highlights."""
    yy, xx = np.mgrid[0:size, 0:size].astype(float)
    refl = np.exp(-((xx + yy - size) / (size * 0.18)) ** 2) * 12
    rgb = _base((236, 238, 240), size, 0.8) + refl[..., None]
    rgb[..., 2] += 3
    return to_image(rgb)


def chrome(size=S):
    yy, xx = np.mgrid[0:size, 0:size].astype(float)
    band = np.cos(yy / size * math.pi * 3) * 40 + np.sin(xx / size * math.pi) * 10
    return to_image(_base((176, 182, 190), size, 1.5) + band[..., None])


def granite(size=S, base=(46, 46, 50)):
    """Speckled stone worktop."""
    rgb = _base(base, size, 4)
    speck = rng.random((size, size))
    rgb[speck > 0.93] += 50
    rgb[speck < 0.05] -= 18
    rgb += (blur(rng.normal(0, 1, (size, size)), 4) * 6)[..., None]
    return to_image(rgb)


def solid(colour, size=16, n=1.0):
    return to_image(_base(colour, size, n))


def glass_tex(tint=(200, 225, 235), alpha=70, size=32):
    """See-through glass for the shower: faint tint, a streak of reflection, a frame line."""
    img = Image.new("RGBA", (size, size), tint + (alpha,))
    d = ImageDraw.Draw(img)
    for i in range(3):
        d.line([(size * 0.2 + i, size), (size * 0.55 + i, 0)], fill=(255, 255, 255, 60))
    d.rectangle([0, 0, size - 1, size - 1], outline=(190, 196, 202, 255))
    return img


def mirror(size=S):
    yy, xx = np.mgrid[0:size, 0:size].astype(float)
    rgb = np.array([186, 200, 210.0]) + (yy / size * -30)[..., None] + (np.exp(-((xx - yy - 8) / 6) ** 2) * 40)[..., None]
    rgb[(xx < 2) | (xx > size - 3) | (yy < 2) | (yy > size - 3)] = [230, 232, 234]
    return to_image(rgb)


# ----------------------------------------------------------------- fronts

def door_front(colour, size=S, handle="bar", shaker=True, split=None, wood=None, h=None):
    """A cupboard door front (full face, size wide and h high): shaker frame, a recessed panel and
    a handle on the opening side. split = 'v' for a pair of doors, an int for that many drawers."""
    base = np.asarray(wood if wood is not None else gloss(colour, size))[..., :3].astype(float)
    img = to_image(base)
    hh = h or size
    if hh != size:
        img = img.resize((size, hh))
    d = ImageDraw.Draw(img)
    dark = tuple(int(c * 0.72) for c in colour)
    light = tuple(min(255, int(c * 1.1)) for c in colour)
    met = (172, 176, 182)

    def panel(x0, y0, x1, y1, hx, hy, vertical):
        d.rectangle([x0, y0, x1, y1], outline=dark, width=1)
        if shaker:
            m = max(2, min(x1 - x0, y1 - y0) // 7)
            d.rectangle([x0 + m, y0 + m, x1 - m, y1 - m], outline=dark, width=1)
            d.line([(x0 + m + 1, y1 - m - 1), (x1 - m - 1, y1 - m - 1)], fill=light)
        if handle == "bar":
            if vertical:
                d.rectangle([hx - 1, hy - 5, hx + 1, hy + 5], fill=met, outline=(110, 112, 116))
            else:
                d.rectangle([hx - 6, hy - 1, hx + 6, hy + 1], fill=met, outline=(110, 112, 116))
        elif handle == "knob":
            d.ellipse([hx - 2, hy - 2, hx + 2, hy + 2], fill=(150, 120, 60), outline=(90, 70, 30))
    if split == "v":
        m = size // 2
        panel(0, 0, m - 1, hh - 1, m - 5, hh // 2, True)
        panel(m, 0, size - 1, hh - 1, m + 4, hh // 2, True)
    elif isinstance(split, int):
        rows = split
        for i in range(rows):
            y0, y1 = i * hh // rows, (i + 1) * hh // rows - 1
            panel(0, y0, size - 1, y1, size // 2, (y0 + y1) // 2, False)
    else:
        panel(0, 0, size - 1, hh - 1, size - 7, hh // 2, True)
    return img


def tall_door(colour, size=S, wood=None, mirror_panel=False):
    """A wardrobe or fridge door pair over two blocks: drawn 64 x 128 for a 16 x 32 face."""
    base = np.asarray(wood if wood is not None else gloss(colour, size))[..., :3].astype(float)
    top = to_image(np.vstack([base, base]))
    d = ImageDraw.Draw(top)
    dark = tuple(int(c * 0.7) for c in colour)
    h = size // 2
    for x0, x1 in ((0, h - 1), (h, size - 1)):
        d.rectangle([x0, 0, x1, 2 * size - 1], outline=dark)
        d.rectangle([x0 + 4, 4, x1 - 4, 2 * size - 5], outline=dark)
        if mirror_panel and x0 == 0:
            m = mirror(size)
            top.paste(m.resize((x1 - x0 - 10, 2 * size - 12)), (x0 + 5, 6))
    for x in (h - 4, h + 3):
        d.rectangle([x - 1, size - 10, x + 1, size + 10], fill=(172, 176, 182), outline=(110, 112, 116))
    return top


def books(size=S):
    """A shelf of book spines: random heights, colours and title bands."""
    img = Image.new("RGBA", (size, size), (52, 38, 26, 255))
    d = ImageDraw.Draw(img)
    pal = [(128, 32, 30), (30, 60, 110), (40, 90, 50), (200, 170, 90), (90, 40, 90), (220, 220, 210),
           (30, 30, 32), (170, 90, 40), (60, 110, 120)]
    for row in range(2):
        x = 1
        y1 = (row + 1) * size // 2 - 3
        while x < size - 2:
            w = int(rng.integers(3, 6))
            h = int(rng.integers(size // 2 - 12, size // 2 - 5))
            c = pal[int(rng.integers(len(pal)))]
            d.rectangle([x, y1 - h, x + w - 1, y1], fill=c)
            d.line([(x, y1 - h + 3), (x + w - 1, y1 - h + 3)], fill=(225, 200, 120))
            d.line([(x + w - 1, y1 - h), (x + w - 1, y1)], fill=tuple(int(v * 0.6) for v in c))
            x += w + (1 if rng.random() < 0.15 else 0)
        d.rectangle([0, y1 + 1, size - 1, y1 + 3], fill=(96, 70, 46))
    return img


def leaves(size=32):
    """Cut-out leaves for the house plant."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for _ in range(26):
        cx, cy = rng.uniform(2, size - 2), rng.uniform(2, size - 2)
        a = rng.uniform(0, math.pi)
        L, W = rng.uniform(5, 9), rng.uniform(2, 3.5)
        pts = []
        for t in np.linspace(0, 2 * math.pi, 12):
            x, y = L * math.cos(t), W * math.sin(t)
            pts.append((cx + x * math.cos(a) - y * math.sin(a), cy + x * math.sin(a) + y * math.cos(a)))
        g = int(rng.uniform(90, 150))
        d.polygon(pts, fill=(30, g, 40, 255))
        d.line([pts[0], pts[6]], fill=(70, g + 40, 70, 255))
    return img


def radiator_face(size=S):
    """A white panel radiator: vertical convector channels."""
    img = gloss((238, 238, 234), size)
    d = ImageDraw.Draw(img)
    for x in range(2, size, 4):
        d.line([(x, 3), (x, size - 4)], fill=(206, 206, 202))
        d.line([(x + 1, 3), (x + 1, size - 4)], fill=(250, 250, 248))
    d.rectangle([0, 0, size - 1, size - 1], outline=(214, 214, 210))
    return img


# ----------------------------------------------------------------- appliances

def hob(on=False, size=S):
    """Black glass ceramic hob with four rings (glowing red when on)."""
    img = to_image(_base((20, 20, 22), size, 1.2))
    d = ImageDraw.Draw(img)
    for (cx, cy, r) in ((18, 18, 11), (46, 18, 8), (18, 46, 8), (46, 46, 11)):
        if on:
            for k in range(r, 0, -1):
                t = k / r
                d.ellipse([cx - k, cy - k, cx + k, cy + k], fill=(int(250 - 80 * t), int(70 - 50 * t), 20))
        else:
            d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=(90, 90, 96))
            d.ellipse([cx - r + 3, cy - r + 3, cx + r - 3, cy + r - 3], outline=(55, 55, 60))
    d.rectangle([0, 0, size - 1, size - 1], outline=(70, 70, 76))
    return img


def oven_front(on=False, size=S):
    """Built-under oven: brushed steel fascia with knobs and a clock, black glass door, a bar
    handle; the light behind the glass comes on with it."""
    img = H.stainless(size)
    d = ImageDraw.Draw(img)
    d.rectangle([4, 16, size - 5, size - 4], fill=(24, 24, 26), outline=(60, 60, 64))
    if on:
        d.rectangle([10, 24, size - 11, size - 10], fill=(120, 70, 20))
        d.rectangle([12, 34, size - 13, 38], fill=(40, 36, 34))
    d.rectangle([12, 18, size - 13, 20], fill=(200, 204, 208))
    for x in (8, 16, 46, 54):
        d.ellipse([x - 2, 6, x + 2, 10], fill=(40, 40, 44))
    d.rectangle([26, 5, 37, 11], fill=(10, 10, 12))
    d.text((27, 4), "12:00", fill=(80, 220, 120) if on else (60, 60, 60))
    return img


def fridge_front(size=S):
    """Tall fridge freezer, white: fridge above, freezer below, recessed handles (64 x 128)."""
    base = np.asarray(gloss((240, 241, 240), size))[..., :3].astype(float)
    img = to_image(np.vstack([base, base]))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, size - 1, 2 * size - 1], outline=(200, 202, 204))
    d.line([(0, 72), (size, 72)], fill=(180, 182, 186), width=2)
    for y0, y1 in ((8, 40), (82, 108)):
        d.rectangle([size - 9, y0, size - 6, y1], fill=(196, 198, 202))
    d.rectangle([20, 12, 30, 18], fill=(70, 120, 180))
    return img


def washer_front(frames=8, size=S):
    """Front-loading washing machine: control panel, round door with a porthole. Animated: the
    drum turns with the laundry tumbling when it's on (frame 0 is used off)."""
    out = Image.new("RGBA", (size, size * frames))
    for f in range(frames):
        img = gloss((240, 241, 240), size)
        d = ImageDraw.Draw(img)
        d.rectangle([0, 0, size - 1, 12], fill=(226, 228, 230))
        d.ellipse([6, 3, 12, 9], fill=(160, 164, 170))
        d.rectangle([34, 4, 52, 8], fill=(20, 22, 24))
        d.text((35, 3), "40", fill=(110, 220, 255))
        cx, cy, r = 32, 38, 21
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(196, 198, 202))
        d.ellipse([cx - r + 4, cy - r + 4, cx + r - 4, cy + r - 4], fill=(48, 56, 66))
        a0 = f / frames * 2 * math.pi
        for k, col in enumerate(((180, 60, 60), (60, 90, 170), (230, 230, 220), (70, 140, 80))):
            a = a0 + k * math.pi / 2
            x, y = cx + 9 * math.cos(a), cy + 9 * math.sin(a) + 3
            d.ellipse([x - 5, y - 4, x + 5, y + 4], fill=col)
        d.ellipse([cx - r + 6, cy - r + 6, cx - 3, cy - 5], outline=(255, 255, 255))
        out.paste(img, (0, f * size))
    return out


def microwave_front(on=False, size=S):
    img = to_image(_base((30, 30, 32), size, 1))
    d = ImageDraw.Draw(img)
    d.rectangle([3, 6, 44, size - 7], fill=(150, 110, 50) if on else (14, 14, 16), outline=(70, 70, 74))
    if on:
        d.ellipse([10, 30, 38, 52], outline=(220, 200, 150))
        d.rectangle([16, 22, 30, 34], fill=(200, 60, 40))
    d.rectangle([48, 6, 60, 14], fill=(12, 14, 12))
    d.text((49, 5), "1:30" if on else "", fill=(90, 230, 120))
    for r in range(4):
        for c in range(3):
            d.rectangle([48 + c * 4, 20 + r * 5, 50 + c * 4, 22 + r * 5], fill=(110, 110, 114))
    return img


def screen(frames_out=None, size=64):
    """The TV picture: a sequence of made-up programmes (a channel ident, a news studio, a
    nature film and a football match), each held for a few seconds."""
    scenes = []

    def ident(t):
        img = Image.new("RGBA", (size, size // 2 + 4), (20, 40, 110, 255))
        d = ImageDraw.Draw(img)
        for i in range(8):
            d.line([(0, i * 5 + t % 5), (size, i * 5 + t % 5 - 10)], fill=(40, 80, 170))
        d.rectangle([size // 2 - 9, 7, size // 2 + 9, 25], fill=(255, 255, 255))
        d.rectangle([size // 2 - 6, 10, size // 2 + 6, 22], fill=(220, 40, 40))
        d.text((size // 2 - 3, 11), "4", fill=(255, 255, 255))
        return img

    def news(t):
        img = Image.new("RGBA", (size, size // 2 + 4), (30, 36, 70, 255))
        d = ImageDraw.Draw(img)
        d.rectangle([0, 0, size, 14], fill=(60, 90, 160))
        d.ellipse([24, 6, 38, 20], fill=(224, 182, 150))
        d.rectangle([20, 18, 42, 32], fill=(30, 30, 40))
        d.rectangle([0, 26, size, 35], fill=(190, 20, 30))
        d.text((2 + (-t * 2) % 20, 26), "NEWS  LIVE  ", fill=(255, 255, 255))
        return img

    def nature(t):
        img = Image.new("RGBA", (size, size // 2 + 4), (120, 180, 230, 255))
        d = ImageDraw.Draw(img)
        d.polygon([(0, 24), (20, 10), (40, 22), (64, 12), (64, 36), (0, 36)], fill=(70, 130, 70))
        x = 10 + t * 3
        d.ellipse([x, 16, x + 12, 24], fill=(150, 110, 70))
        d.ellipse([x + 10, 13, x + 15, 18], fill=(150, 110, 70))
        return img

    def football(t):
        img = Image.new("RGBA", (size, size // 2 + 4), (50, 140, 60, 255))
        d = ImageDraw.Draw(img)
        for i in range(0, size, 8):
            d.rectangle([i, 0, i + 3, 36], fill=(56, 150, 66))
        d.line([(32, 0), (32, 36)], fill=(240, 240, 240))
        d.ellipse([26, 12, 38, 24], outline=(240, 240, 240))
        for k in range(5):
            x = (k * 13 + t * (2 if k % 2 else -2)) % size
            d.rectangle([x, 8 + k * 4, x + 2, 12 + k * 4], fill=(200, 30, 30) if k % 2 else (240, 240, 240))
        d.ellipse([30 + t, 18, 33 + t, 21], fill=(255, 255, 255))
        d.rectangle([2, 1, 22, 6], fill=(20, 20, 30))
        d.text((3, 0), "1-0", fill=(255, 255, 255))
        return img
    for fn in (ident, news, nature, football):
        for t in range(4):
            scenes.append(fn(t))
    h = scenes[0].height
    strip = Image.new("RGBA", (size, size * len(scenes)), (0, 0, 0, 255))
    for i, sc in enumerate(scenes):
        frame = Image.new("RGBA", (size, size), (6, 6, 8, 255))
        frame.paste(sc, (0, (size - h) // 2))
        strip.paste(frame, (0, i * size))
    return strip


def screen_off(size=32):
    yy, xx = np.mgrid[0:size, 0:size].astype(float)
    rgb = np.array([12, 13, 16.0]) + (np.exp(-((xx - yy * 0.8 - 6) / 4) ** 2) * 14)[..., None]
    return to_image(rgb)


def monitor_screen(size=64):
    """A desktop on the computer: windows, a document and a taskbar."""
    img = Image.new("RGBA", (size, size), (40, 90, 150, 255))
    d = ImageDraw.Draw(img)
    for y in range(size):
        d.line([(0, y), (size, y)], fill=(30 + y // 3, 80 + y // 4, 150))
    d.rectangle([6, 8, 44, 40], fill=(250, 250, 250), outline=(90, 90, 100))
    d.rectangle([6, 8, 44, 12], fill=(40, 120, 200))
    for k in range(6):
        d.line([(9, 16 + k * 4), (9 + int(rng.integers(18, 32)), 16 + k * 4)], fill=(120, 120, 130))
    d.rectangle([30, 22, 58, 46], fill=(230, 236, 240), outline=(90, 90, 100))
    d.rectangle([33, 30, 55, 42], fill=(120, 170, 90))
    d.rectangle([0, size - 6, size, size], fill=(24, 26, 30))
    return img


def flames(frames=8, size=32):
    """Electric fire flames over glowing coals, animated."""
    out = Image.new("RGBA", (size, size * frames))
    for f in range(frames):
        img = Image.new("RGBA", (size, size), (14, 10, 8, 255))
        d = ImageDraw.Draw(img)
        for k in range(7):
            x = 3 + k * 4.2 + math.sin(f * 0.8 + k) * 1.2
            h = 12 + 6 * math.sin(f * 0.9 + k * 1.7)
            d.polygon([(x - 2.6, size - 7), (x + 2.6, size - 7), (x + math.sin(f + k) * 1.5, size - 7 - h)],
                      fill=(255, 140 + int(40 * math.sin(f + k)), 30))
            d.polygon([(x - 1.2, size - 7), (x + 1.2, size - 7), (x, size - 7 - h * 0.55)], fill=(255, 230, 120))
        for k in range(9):
            x = 2 + k * 3.4
            d.ellipse([x, size - 8, x + 4, size - 3], fill=(200 + int(40 * math.sin(f + k * 2)), 70, 20))
        out.paste(img, (0, f * size))
    return out


def coals_off(size=32):
    img = Image.new("RGBA", (size, size), (16, 14, 14, 255))
    d = ImageDraw.Draw(img)
    for k in range(9):
        x = 2 + k * 3.4
        d.ellipse([x, size - 8, x + 4, size - 3], fill=(60, 56, 52))
    return img


def glow(colour, size=16):
    """An evenly lit lamp shade or bulb."""
    yy, xx = np.mgrid[0:size, 0:size].astype(float)
    rgb = np.array(colour, float) + (np.cos((xx / size - 0.5) * math.pi) * 10)[..., None]
    return to_image(rgb)


def hob_knobs(size=32):
    img = H.stainless(size)
    d = ImageDraw.Draw(img)
    for x in (5, 13, 19, 27):
        d.ellipse([x - 2, 12, x + 2, 16], fill=(40, 40, 44))
    return img


def toaster_top(size=16):
    img = H.stainless(size)
    d = ImageDraw.Draw(img)
    d.rectangle([4, 3, 11, 5], fill=(20, 20, 20))
    d.rectangle([4, 10, 11, 12], fill=(20, 20, 20))
    return img


def sink_top(colour=(236, 238, 240), size=S, steel=True):
    """A worktop sink seen from above: a bowl and a ribbed drainer."""
    img = H.stainless(size) if steel else ceramic(size)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([6, 8, 36, 56], radius=6, fill=(126, 130, 136), outline=(96, 100, 106))
    d.ellipse([18, 40, 24, 46], fill=(60, 62, 66))
    for y in range(12, 54, 4):
        d.line([(42, y), (58, y)], fill=(150, 154, 160))
    return img


def basin_top(size=32):
    img = ceramic(size)
    d = ImageDraw.Draw(img)
    d.ellipse([5, 7, 27, 27], fill=(214, 220, 226), outline=(196, 200, 206))
    d.ellipse([14, 16, 18, 20], fill=(150, 154, 160))
    return img


def bath_top(size=32):
    img = ceramic(size)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([3, 3, size - 4, size - 4], radius=6, fill=(214, 222, 228), outline=(196, 200, 206))
    return img


def toilet_seat(size=16):
    img = ceramic(size)
    d = ImageDraw.Draw(img)
    d.ellipse([3, 3, 12, 13], fill=(188, 214, 224), outline=(220, 224, 228))
    return img


def halves(img):
    """Split a 1 x 2 tall face texture into its (lower, upper) square halves."""
    w, h = img.size
    return img.crop((0, h // 2, w, h)), img.crop((0, 0, w, h // 2))
