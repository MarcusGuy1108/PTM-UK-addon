"""Higher detail textures for the street furniture: materials at 128 px with wear, grime and
moulding, and face panels drawn at the proportions of the faces they sit on (so lettering is no
longer squashed). Used by furniture.make_textures()."""
import math

import numpy as np
from PIL import Image, ImageDraw, ImageFont

from signal_textures import blur, noise, rng, to_image

BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
REGULAR = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
CONDENSED = "/usr/share/fonts/truetype/dejavu/DejaVuSansCondensed-Bold.ttf"
SERIF = "/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf"
MONO = "/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf"
S = 128


def font(path, px):
    try:
        return ImageFont.truetype(path, max(6, int(px)))
    except OSError:
        return ImageFont.truetype(BOLD, max(6, int(px)))


def blur_y(arr, radius):
    """Blur down the columns only (for streaks and drips)."""
    n = int(radius * 3) + 1
    k = np.exp(-(np.arange(-n, n + 1) ** 2) / (2 * radius ** 2))
    k /= k.sum()
    p = np.pad(arr, ((n, n), (0, 0)), mode="wrap")
    return np.apply_along_axis(lambda v: np.convolve(v, k, mode="valid"), 0, p)


def blur_x(arr, radius):
    return blur_y(arr.T, radius).T


def tileable(arr):
    """Fade the edges so a tile repeats without a hard seam."""
    return (arr + np.roll(arr, arr.shape[0] // 2, 0) + np.roll(arr, arr.shape[1] // 2, 1)) / 3


def grime(rgb, size, amount=1.0):
    """Darker, browner towards the bottom of the tile (rain splash and road dirt)."""
    yy = np.mgrid[0:size, 0:size][0] / size
    g = (np.clip((yy - 0.55) / 0.45, 0, 1) ** 1.6 * amount)[..., None]
    dirt = blur(rng.random((size, size)), 2.0)[..., None]
    return rgb * (1 - 0.18 * g) + np.array([70, 60, 48.0]) * 0.12 * g * (0.5 + dirt)


# ----------------------------------------------------------------- materials

def paint(colour, size=S, wear=0.0, gloss=0.0, streaks=1.0):
    """Painted steel: orange peel, faint run streaks, sheen, wear chips with bare metal and
    a dark rim, a few scratches, grime towards the bottom."""
    yy, xx = np.mgrid[0:size, 0:size].astype(float)
    base = np.array(colour, float)
    lum = base.mean()
    rgb = base + noise((size, size, 1), 1.5)
    rgb += (blur(rng.normal(0, 1, (size, size)), 0.8) * 4)[..., None]                      # orange peel
    st = blur_y(rng.normal(0, 1, (size, size)) * (rng.random(size) < 0.25), 10) * 10 * streaks
    rgb += st[..., None] * (0.4 if lum > 140 else 0.7)
    rgb += (gloss * 22 * np.exp(-((xx - size * 0.3) / (size * 0.14)) ** 2))[..., None]
    rgb += (6 * (0.5 - yy / size))[..., None]
    if wear:
        chips = blur(rng.random((size, size)), 1.6)
        hole = chips > 1 - wear * 0.22
        rim = (chips > 1 - wear * 0.30) & ~hole
        rgb[rim] = rgb[rim] * 0.72
        rgb[hole] = np.array([128, 124, 118.0]) + noise((int(hole.sum()), 3), 6)
    for _ in range(int(3 + wear * 20)):                                                    # scratches
        x0, y0 = rng.integers(0, size, 2)
        ang = rng.uniform(-0.6, 0.6)
        for t in range(int(rng.integers(6, size // 4))):
            x, y = int(x0 + t * math.cos(ang)) % size, int(y0 + t * math.sin(ang)) % size
            rgb[y, x] = rgb[y, x] * 0.7 + np.array([190, 190, 184]) * 0.3
    return to_image(grime(rgb, size, 0.8))


def plastic(colour, size=S):
    """Moulded HDPE (wheelie bins): raised vertical ribs with a lit edge and a shaded edge,
    stippled grain, pale scuffs and road dirt at the bottom."""
    yy, xx = np.mgrid[0:size, 0:size].astype(float)
    base = np.array(colour, float)
    rgb = base + noise((size, size, 1), 1.8) + (blur(rng.normal(0, 1, (size, size)), 0.6) * 3)[..., None]
    period = size / 6
    ph = (xx % period) / period
    rgb += (np.where(ph < 0.08, 14, 0) - np.where((ph > 0.42) & (ph < 0.5), 10, 0))[..., None]
    rgb += (8 * (0.5 - yy / size))[..., None]
    scuff = blur(rng.random((size, size)), 1.0) > 0.86
    rgb[scuff] = rgb[scuff] * 0.8 + np.array([200, 200, 196]) * 0.2
    return to_image(grime(rgb, size, 1.0))


def cast_iron(size=S):
    """Cast iron, painted black: lumpy casting, satin highlights, flecks of rust."""
    lump = blur(rng.normal(0, 1, (size, size)), 2.2)
    rgb = np.array([28, 28, 30.0]) + (lump * 9)[..., None] + noise((size, size, 1), 2)
    rgb += (np.clip(lump, 0, None) ** 2 * 10)[..., None]
    rust = blur(rng.random((size, size)), 1.2) > 0.9
    rgb[rust] = np.array([96, 54, 32.0]) + noise((int(rust.sum()), 3), 8)
    return to_image(grime(rgb, size, 0.5))


def stainless(size=S):
    """Brushed stainless: fine horizontal grain, sky above and street below in the reflection."""
    yy, xx = np.mgrid[0:size, 0:size].astype(float)
    grain = blur_x(rng.normal(0, 1, (size, size)), 6) * 9
    refl = 22 * np.cos(yy / size * math.pi) + 10 * np.sin(xx / size * math.pi * 2)
    rgb = np.array([172, 176, 180.0]) + (grain + refl)[..., None] + noise((size, size, 1), 1.5)
    rgb[..., 2] += 4
    return to_image(rgb)


def wood(size=S, base=(128, 88, 52)):
    """Bench slats running across the tile: grain, knots, weathering, dark gaps and screws."""
    yy, xx = np.mgrid[0:size, 0:size].astype(float)
    plank = size / 4
    row = (yy // plank)
    off = rng.uniform(0, 100, 4)[row.astype(int) % 4]
    grain = np.sin((yy % plank) * 1.4 + 4 * np.sin(xx * 0.035 + off) + blur(rng.normal(0, 1, (size, size)), 1.5) * 1.6)
    rgb = np.array(base, float) + (grain * 10)[..., None] + noise((size, size, 1), 3)
    rgb += (blur_x(rng.normal(0, 1, (size, size)), 8) * 8)[..., None]
    weather = blur(rng.random((size, size)), 4)
    rgb = rgb * (1 - 0.25 * weather[..., None]) + np.array([150, 146, 138.0]) * 0.25 * weather[..., None]
    for k in range(4):                                                                     # knots
        cx, cy = rng.uniform(0, size), (k + rng.uniform(0.3, 0.7)) * plank
        d = np.hypot((xx - cx) / 3.2, yy - cy)
        rgb -= (np.exp(-(d / 2.2) ** 2) * 45)[..., None]
    edge = (yy % plank < 1.2) | (yy % plank > plank - 1.2)
    rgb[edge] = rgb[edge] * 0.45
    for r in range(4):
        for x in (size * 0.12, size * 0.88):
            y = (r + 0.5) * plank
            rgb[int(y) - 1:int(y) + 2, int(x) - 1:int(x) + 2] = [70, 70, 72]
    return to_image(grime(rgb, size, 0.4))


def wood_vertical(size=S):
    return wood(size).transpose(Image.Transpose.ROTATE_90)


def concrete(size=S, base=(166, 164, 158)):
    """Precast concrete: aggregate, pores, soft blotches and staining low down."""
    rgb = np.array(base, float) + noise((size, size, 1), 4)
    rgb += (blur(rng.normal(0, 1, (size, size)), 5) * 9)[..., None]
    agg = rng.random((size, size))
    rgb[agg > 0.97] += 22
    rgb[agg < 0.03] -= 26
    pores = blur(rng.random((size, size)), 0.7) > 0.8
    rgb[pores] -= 30
    return to_image(grime(rgb, size, 1.2))


def rubber(size=S):
    rgb = np.array([22, 22, 23.0]) + noise((size, size, 1), 2.5) + (blur(rng.normal(0, 1, (size, size)), 1.5) * 3)[..., None]
    return to_image(grime(rgb, size, 0.6))


def dark(size=S):
    rgb = np.array([16, 16, 17.0]) + noise((size, size, 1), 1.5)
    return to_image(rgb)


# ----------------------------------------------------------------- face panels

class Panel:
    """A face texture drawn 3x oversize and scaled down for smooth edges and lettering."""

    def __init__(self, w, h, bg, ss=3):
        self.w, self.h, self.ss = w, h, ss
        self.img = Image.new("RGBA", (w * ss, h * ss), tuple(bg) + (255,) if len(bg) == 3 else tuple(bg))
        self.d = ImageDraw.Draw(self.img)

    def k(self, *v):
        return [int(round(a * self.ss)) for a in v]

    def rect(self, x0, y0, x1, y1, fill, outline=None, width=0, r=0):
        box = self.k(x0, y0, x1, y1)
        if r:
            self.d.rounded_rectangle(box, radius=int(r * self.ss), fill=fill, outline=outline, width=int(width * self.ss))
        else:
            self.d.rectangle(box, fill=fill, outline=outline, width=int(width * self.ss))

    def ellipse(self, x0, y0, x1, y1, fill, outline=None, width=0):
        self.d.ellipse(self.k(x0, y0, x1, y1), fill=fill, outline=outline, width=int(width * self.ss))

    def text(self, cx, cy, s, size, fill, path=BOLD, anchor="mm", max_w=None):
        f = font(path, size * self.ss)
        if max_w:
            while self.d.textlength(s, font=f) > max_w * self.ss and f.size > 8:
                f = font(path, f.size - 2)
        self.d.text((cx * self.ss, cy * self.ss), s, fill=fill, font=f, anchor=anchor)

    def line(self, pts, fill, width=1):
        self.d.line([tuple(self.k(*p)) for p in pts], fill=fill, width=int(width * self.ss))

    def done(self, wear=0.0):
        img = self.img.resize((self.w, self.h), Image.LANCZOS)
        if wear:
            a = np.asarray(img).astype(float)
            a[..., :3] += noise(a.shape[:2] + (1,), 2.5)
            a[..., :3] = grime(a[..., :3], max(a.shape[:2]), wear)[:a.shape[0], :a.shape[1]] if a.shape[0] == a.shape[1] else a[..., :3]
            img = to_image(a[..., :3], a[..., 3])
        return img


def litter_band():
    """Gold LITTER on the black band of a cast iron bin, with gold pinstripes (face 5.4 x 2)."""
    p = Panel(216, 80, (26, 26, 28))
    gold = (204, 168, 72)
    for y in (6, 74):
        p.rect(4, y - 2, 212, y + 1, gold)
    p.text(108, 41, "LITTER", 50, gold, SERIF, max_w=190)
    return p.done()


def telephone_sign():
    """Back-lit TELEPHONE fascia: white letters on black (face 10 x 1.8)."""
    p = Panel(400, 72, (14, 14, 15))
    p.rect(3, 3, 397, 69, None, outline=(60, 60, 62), width=2)
    p.text(200, 37, "TELEPHONE", 50, (250, 250, 238), CONDENSED, max_w=370)
    return p.done()


def post_plate():
    """Enamelled collection plate."""
    p = Panel(160, 120, (240, 238, 230))
    p.rect(3, 3, 157, 117, None, outline=(30, 30, 30), width=3, r=6)
    p.text(80, 20, "POST", 22, (20, 20, 20), BOLD)
    p.rect(14, 33, 146, 35, (30, 30, 30))
    p.text(80, 46, "COLLECTIONS", 13, (20, 20, 20), CONDENSED)
    for i, (day, t) in enumerate((("Mon - Fri", "9.00 am   5.30 pm"), ("Saturday", "12.00 noon"), ("Sunday", "No collection"))):
        p.text(18, 66 + i * 16, day, 10, (30, 30, 30), REGULAR, anchor="lm")
        p.text(142, 66 + i * 16, t, 10, (30, 30, 30), REGULAR, anchor="rm")
    return p.done()


def bin_front(colour, label):
    """Front of a wheelie bin (upper body, face 9 x 6.8): recessed panel, house number and a
    hot-stamp label; recycling arrows on the recycling bins."""
    w, h = 128, 96
    img = plastic(colour, 128).crop((0, 16, 128, 112))
    p = Panel(w, h, (0, 0, 0, 0))
    dark = tuple(max(0, c - 24) for c in colour) + (255,)
    light = tuple(min(255, c + 22) for c in colour) + (255,)
    p.rect(10, 8, 118, 84, None, outline=dark, width=3, r=10)
    p.line([(13, 86), (115, 86)], light, 2)
    p.text(64, 28, "24", 26, (245, 245, 240), BOLD)
    p.rect(26, 46, 102, 66, (245, 245, 240), r=3)
    p.text(64, 56, label, 10, (30, 30, 30), CONDENSED, max_w=70)
    if label in ("RECYCLING", "GARDEN WASTE", "FOOD WASTE"):
        cx, cy, r = 64, 76, 6
        for k in range(3):
            a0, a1 = math.radians(90 + k * 120), math.radians(90 + k * 120 + 95)
            p.line([(cx + r * math.cos(a0), cy - r * math.sin(a0)), (cx + r * math.cos(a1), cy - r * math.sin(a1))],
                   (245, 245, 240), 2)
    img.alpha_composite(p.done())
    return img


def cabinet_door(colour, sticker=False, size=256):
    """Roadside cabinet front: two doors with seams and a drip strip, louvres top and bottom,
    lock barrels and T-handles, hinges, an ID plate and (if asked) a warning sticker."""
    img = paint(colour, size=size, wear=0.12, streaks=1.4)
    p = Panel(size, size, (0, 0, 0, 0))
    s = size / 128
    dark = tuple(max(0, c - 40) for c in colour) + (255,)
    light = tuple(min(255, c + 26) for c in colour) + (255,)
    p.rect(2 * s, 2 * s, 126 * s, 126 * s, None, outline=dark, width=2 * s)
    p.rect(63 * s, 3 * s, 65 * s, 125 * s, (20, 20, 22, 255))
    for x0, x1 in ((7, 59), (69, 121)):
        p.rect(x0 * s, 6 * s, x1 * s, 122 * s, None, outline=dark, width=1.2 * s)
        p.line([(x0 * s, 6.5 * s), (x1 * s, 6.5 * s)], light, 1 * s)
        for y in list(range(12, 30, 4)) + list(range(100, 116, 4)):       # louvres
            p.rect((x0 + 6) * s, y * s, (x1 - 6) * s, (y + 2) * s, (20, 20, 22, 230))
            p.line([((x0 + 6) * s, (y + 2.3) * s), ((x1 - 6) * s, (y + 2.3) * s)], light, 0.7 * s)
    for x in (55, 73):                                                       # handles and locks
        p.rect((x - 2) * s, 56 * s, (x + 2) * s, 74 * s, (36, 36, 38, 255), r=1 * s)
        p.rect((x - 1) * s, 57 * s, (x + 0.2) * s, 73 * s, (90, 90, 94, 255))
        p.ellipse((x - 2.5) * s, 48 * s, (x + 2.5) * s, 53 * s, (170, 170, 168, 255), outline=(50, 50, 50, 255), width=0.6 * s)
    for x in (8, 120):                                                       # hinges
        for y in (16, 104):
            p.rect((x - 1.5) * s, y * s, (x + 1.5) * s, (y + 8) * s, (60, 60, 62, 255))
    p.rect(20 * s, 40 * s, 46 * s, 48 * s, (230, 230, 226, 255), outline=(40, 40, 40, 255), width=0.6 * s)
    p.text(33 * s, 44 * s, "CAB 1108", 4.6 * s, (30, 30, 30, 255), MONO)
    if sticker:
        p.d.polygon([tuple(p.k(96 * s, 82 * s)), tuple(p.k(108 * s, 96 * s)), tuple(p.k(84 * s, 96 * s))],
                    fill=(250, 210, 20, 255), outline=(20, 20, 20, 255))
        p.line([(96 * s, 86 * s), (96 * s, 92 * s)], (20, 20, 20, 255), 1.6 * s)
        p.ellipse(95.2 * s, 93.2 * s, 96.8 * s, 94.8 * s, (20, 20, 20, 255))
    img.alpha_composite(p.done())
    return img


def pay_display_face():
    """Pay and display machine front (face 9.6 x 15.5): P sign, screen, keypad, coin and card
    slots, ticket window and instructions."""
    p = Panel(160, 256, (46, 64, 104))
    p.rect(0, 0, 160, 38, (250, 250, 250))
    p.rect(8, 6, 34, 32, (0, 82, 160), r=3)
    p.text(21, 19, "P", 22, (255, 255, 255))
    p.text(97, 19, "PAY & DISPLAY", 14, (20, 40, 110), CONDENSED, max_w=112)
    p.rect(16, 50, 144, 96, (36, 40, 44), r=4)
    p.rect(22, 55, 138, 91, (24, 46, 32))
    for i, s in enumerate(("TARIFF  1 hr  1.20", "MAX STAY 2 hrs", "PRESS GREEN")):
        p.text(28, 63 + i * 11, s, 8, (130, 255, 170), MONO, anchor="lm")
    keys = "123456789*0#"
    for i, ch in enumerate(keys):
        r, c = divmod(i, 3)
        x, y = 22 + c * 24, 108 + r * 22
        p.rect(x, y, x + 20, y + 17, (208, 208, 204), outline=(120, 120, 120), width=1, r=3)
        p.text(x + 10, y + 9, ch, 10, (30, 30, 30))
    p.rect(104, 108, 140, 124, (60, 160, 70), r=3)
    p.text(122, 116, "OK", 9, (255, 255, 255))
    p.rect(104, 130, 140, 146, (190, 40, 40), r=3)
    p.text(122, 138, "X", 9, (255, 255, 255))
    p.rect(110, 156, 134, 196, (24, 24, 26), r=3)                     # card reader
    p.rect(118, 160, 126, 192, (8, 8, 8))
    p.ellipse(119, 197, 125, 203, (80, 230, 110))
    p.rect(26, 206, 74, 214, (14, 14, 14))                             # coin slot
    p.text(50, 222, "COINS", 7, (220, 220, 220))
    p.rect(30, 232, 130, 248, (18, 18, 20), r=3)                       # ticket window
    p.rect(40, 238, 120, 242, (60, 60, 60))
    return p.done()


def meter_face():
    p = Panel(128, 160, (58, 60, 64))
    p.ellipse(16, 12, 112, 108, (226, 228, 222), outline=(24, 24, 26), width=5)
    p.d.pieslice(p.k(26, 22, 102, 98), 200, 300, fill=(200, 40, 40))
    for k in range(9):
        a = math.radians(200 + k * 17.5)
        p.line([(64 + 34 * math.cos(a), 60 + 34 * math.sin(a)), (64 + 40 * math.cos(a), 60 + 40 * math.sin(a))], (30, 30, 30), 1.5)
    p.line([(64, 60), (88, 36)], (20, 20, 20), 3)
    p.ellipse(60, 56, 68, 64, (20, 20, 20))
    p.text(64, 88, "MINUTES", 8, (40, 40, 40))
    p.rect(50, 118, 78, 126, (16, 16, 16))
    p.rect(30, 136, 98, 152, (200, 200, 196), r=3)
    p.text(64, 144, "20p  50p  1", 8, (40, 40, 40))
    return p.done()


def ev_face():
    """EV charging point (face 6.6 x 9.5): status strip, screen, contactless pad, socket."""
    p = Panel(132, 190, (236, 238, 236))
    p.rect(0, 0, 132, 12, (20, 150, 90))
    p.rect(16, 22, 116, 74, (24, 26, 30), r=6)
    p.rect(22, 28, 110, 68, (14, 34, 40))
    p.text(66, 40, "READY", 13, (110, 240, 170))
    p.text(66, 56, "22 kW  Type 2", 8, (170, 210, 210))
    p.rect(30, 84, 102, 116, (210, 214, 214), outline=(150, 154, 154), width=1.5, r=5)
    for k in range(3):
        p.d.arc(p.k(56 - k * 6, 90 - k * 3, 76 + k * 6, 110 + k * 3), -40, 40, fill=(60, 120, 160), width=int(2 * p.ss))
    p.ellipse(36, 124, 96, 184, (40, 42, 46), outline=(20, 150, 90), width=4)
    p.ellipse(44, 132, 88, 176, (24, 24, 26))
    for a in range(5):
        ang = math.radians(90 + a * 72)
        p.ellipse(66 + 13 * math.cos(ang) - 3, 154 - 13 * math.sin(ang) - 3, 66 + 13 * math.cos(ang) + 3, 154 - 13 * math.sin(ang) + 3, (8, 8, 8))
    p.ellipse(63, 151, 69, 157, (8, 8, 8))
    return p.done()


def dog_bin_face():
    p = Panel(128, 112, (186, 26, 30))
    p.rect(14, 10, 114, 34, (24, 24, 24), r=4)
    p.rect(20, 18, 108, 26, (6, 6, 6))
    p.text(64, 52, "DOG", 18, (250, 250, 250))
    p.text(64, 72, "WASTE", 18, (250, 250, 250))
    p.text(64, 92, "ONLY", 14, (250, 250, 250))
    p.text(64, 106, "Max penalty 100", 6, (250, 220, 220), REGULAR)
    return p.done()


def grit_face():
    p = Panel(128, 64, (232, 186, 28))
    p.rect(4, 4, 124, 60, None, outline=(180, 140, 10), width=2, r=4)
    p.text(64, 30, "GRIT", 34, (24, 24, 24), CONDENSED)
    p.text(64, 54, "FOR USE ON THE HIGHWAY", 6.5, (40, 40, 40), REGULAR)
    return p.done()


def poster():
    """Back-lit shelter advert (face 9.8 x 24): a made-up holiday advert."""
    w, h = 104, 256
    yy, xx = np.mgrid[0:h, 0:w].astype(float)
    sky = np.dstack([90 + 120 * yy / h, 160 + 50 * yy / h, 230 - 30 * yy / h])
    sea = (yy > h * 0.56)
    sky[sea] = np.dstack([20 + 30 * yy / h, 110 + 40 * np.sin(xx / 7) * 0.2 + 10, 170 + 0 * xx])[sea]
    sand = yy > h * 0.7 + 6 * np.sin(xx / 16)
    sky[sand] = [236, 210, 150]
    sun = np.hypot(xx - 72, yy - 70) < 16
    sky[sun] = [255, 236, 140]
    img = to_image(sky + noise((h, w, 1), 2))
    p = Panel(w, h, (0, 0, 0, 0))
    p.text(52, 30, "ESCAPE", 20, (255, 255, 255))
    p.text(52, 50, "to the coast", 11, (255, 255, 255), REGULAR)
    p.rect(14, 214, 90, 240, (250, 200, 40), r=4)
    p.text(52, 227, "trains from 9.50", 8, (30, 30, 30))
    img.alpha_composite(p.done())
    return img


def timetable():
    p = Panel(128, 160, (246, 246, 242))
    p.rect(0, 0, 128, 22, (170, 30, 35))
    p.text(64, 11, "BUS TIMES", 11, (255, 255, 255))
    p.text(8, 32, "Route 96  towards Town Centre", 6.5, (30, 30, 30), REGULAR, anchor="lm")
    p.rect(6, 40, 122, 50, (220, 222, 226))
    for c, s in enumerate(("Mon-Fri", "Sat", "Sun")):
        p.text(26 + c * 38, 45, s, 6.5, (30, 30, 30), BOLD)
    for i in range(12):
        y = 56 + i * 8
        p.line([(6, y + 7), (122, y + 7)], (210, 210, 210), 0.6)
        for c in range(3):
            p.text(26 + c * 38, y + 3.5, f"{6 + i:02d} {['05', '25', '45'][c]}", 6, (40, 40, 40), MONO)
    p.text(64, 154, "Times are approximate", 5.5, (90, 90, 90), REGULAR)
    return p.done()


def sos_panel():
    p = Panel(96, 96, (236, 110, 20))
    p.rect(4, 4, 92, 92, None, outline=(255, 255, 255), width=2.5, r=4)
    p.text(48, 38, "SOS", 30, (255, 255, 255))
    p.rect(30, 58, 66, 84, (30, 30, 30), r=4)
    p.text(48, 71, "LIFT", 9, (255, 255, 255))
    return p.done()
