"""Textures for the shop and cinema fittings: goods on supermarket shelves, chilled and frozen food,
fruit and veg, checkout parts, the trolley mesh, sweets and posters for made-up films. All the
packaging is plain colour and shapes: no real products, brands or logos. Drawn procedurally with
their own random generator, so adding them doesn't change any other texture."""
import math
import random

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

R = random.Random(2024)
NP = np.random.default_rng(2024)

BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
SERIF = "/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf"
CONDENSED = "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf"

GAP = (34, 34, 38)          # the shadow at the back of a shelf between packs
PACK = [(222, 48, 40), (246, 196, 40), (40, 96, 196), (52, 150, 72), (240, 132, 30), (150, 60, 150),
        (238, 238, 232), (30, 160, 170), (200, 30, 90), (120, 70, 40)]


def _shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c)


def _img(w, h, colour=GAP):
    return Image.new("RGBA", (w, h), colour + (255,))


def _speckle(img, amount=6):
    a = np.asarray(img).astype(float)
    a[..., :3] += NP.normal(0, amount, a.shape[:2])[..., None]
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGBA")


# ----------------------------------------------------------------- goods (one shelf row: 64 x 24)

def _box(d, x, y0, w, h, colour, label=True, band=None):
    """A packet face: side shading, a highlight, a pale label panel and a coloured band."""
    d.rectangle([x, y0, x + w - 1, 23], fill=colour)
    d.line([(x, y0), (x, 23)], fill=_shade(colour, 0.7))
    d.line([(x + w - 1, y0), (x + w - 1, 23)], fill=_shade(colour, 0.78))
    d.line([(x + 1, y0), (x + w - 2, y0)], fill=_shade(colour, 1.25))
    if band:
        d.rectangle([x + 1, y0 + h // 3, x + w - 2, y0 + h // 3 + 2], fill=band)
    if label and w >= 6:
        lx = x + 2
        d.ellipse([lx, y0 + h // 2, x + w - 3, y0 + h // 2 + max(3, w // 2 - 1)], fill=(250, 248, 236))


def goods_row(kind):
    """One row of goods as seen on the shelf: 64 x 24 px for a 16 x 6 px face."""
    img = _img(64, 24)
    d = ImageDraw.Draw(img)
    x = 0
    while x < 64:
        if kind == "cereal":
            w, h = R.choice((10, 11, 12)), R.choice((19, 21, 22))
            c = R.choice(PACK[:6] + [PACK[8]])
            for _ in range(R.choice((1, 2))):
                if x >= 64:
                    break
                _box(d, x, 24 - h, w, h, c, band=_shade(c, 0.6))
                d.ellipse([x + 2, 24 - h + 3, x + 5, 24 - h + 6], fill=_shade(c, 1.4))
                x += w
        elif kind == "tins":
            w = 7
            c = R.choice(PACK)
            for _ in range(R.choice((2, 3))):
                for y0 in (14, 4):
                    d.rectangle([x, y0, x + w - 2, y0 + 9], fill=(170, 172, 176))
                    d.rectangle([x, y0 + 2, x + w - 2, y0 + 8], fill=c)
                    d.rectangle([x + 1, y0 + 4, x + w - 3, y0 + 6], fill=(246, 244, 232))
                    d.line([(x + w - 1, y0), (x + w - 1, y0 + 9)], fill=GAP)
                x += w
        elif kind == "jars":
            w = 7
            c = R.choice(((150, 40, 30), (200, 120, 30), (90, 50, 30), (180, 60, 90), (110, 130, 40)))
            lid = R.choice(((220, 190, 70), (240, 240, 240), (200, 40, 40)))
            for _ in range(R.choice((2, 3))):
                d.rectangle([x + 1, 11, x + w - 2, 23], fill=c)
                d.rectangle([x + 1, 9, x + w - 2, 11], fill=lid)
                d.rectangle([x + 2, 15, x + w - 3, 19], fill=(246, 240, 220))
                d.line([(x + 2, 12), (x + 2, 22)], fill=_shade(c, 1.5))
                x += w
        elif kind == "pasta":
            w = 9
            c = R.choice(((40, 80, 170), (200, 40, 40), (30, 120, 60)))
            for _ in range(2):
                d.rectangle([x, 7, x + w - 2, 23], fill=(236, 210, 120))
                for k in range(6):
                    d.line([(x + 1 + k, 9), (x + 1 + k, 22)], fill=(220, 180, 90) if k % 2 else (245, 222, 140))
                d.rectangle([x, 7, x + w - 2, 10], fill=c)
                d.rectangle([x, 19, x + w - 2, 23], fill=c)
                x += w
        elif kind == "bottles":
            w = 6
            c = R.choice(((60, 30, 20), (240, 130, 20), (200, 210, 60), (150, 200, 230), (200, 40, 40), (60, 140, 60)))
            cap = R.choice(((220, 40, 40), (40, 80, 200), (240, 240, 240), (40, 160, 60)))
            for _ in range(R.choice((3, 4))):
                d.rectangle([x, 9, x + w - 2, 23], fill=c)
                d.rectangle([x + 1, 5, x + w - 3, 9], fill=c)
                d.rectangle([x + 1, 3, x + w - 3, 5], fill=cap)
                d.rectangle([x, 13, x + w - 2, 17], fill=R.choice(PACK))
                d.line([(x + 1, 10), (x + 1, 22)], fill=_shade(c, 1.6))
                x += w
        elif kind == "cans":
            w = 5
            c = R.choice(PACK)
            for _ in range(R.choice((4, 6))):
                for y0 in (14, 5):
                    d.rectangle([x, y0, x + w - 2, y0 + 8], fill=c)
                    d.line([(x, y0), (x + w - 2, y0)], fill=(200, 200, 204))
                    d.line([(x + 1, y0 + 2), (x + 1, y0 + 7)], fill=_shade(c, 1.45))
                x += w
        elif kind == "cartons":
            w = 8
            c = R.choice(((240, 150, 30), (240, 220, 60), (220, 60, 60), (100, 180, 60)))
            for _ in range(R.choice((2, 3))):
                d.rectangle([x, 9, x + w - 2, 23], fill=(246, 244, 238))
                d.polygon([(x, 9), (x + (w - 2) // 2, 6), (x + w - 2, 9)], fill=(230, 228, 222))
                d.ellipse([x + 1, 13, x + w - 3, 19], fill=c)
                x += w
        elif kind == "detergent":
            w = 11
            c = R.choice(((40, 100, 200), (60, 170, 80), (130, 60, 170), (240, 240, 240), (240, 120, 30)))
            for _ in range(2):
                d.rounded_rectangle([x, 6, x + w - 2, 23], 2, fill=c)
                d.rectangle([x + 6, 7, x + 8, 11], fill=GAP)
                d.rectangle([x + 2, 3, x + 4, 6], fill=_shade(c, 0.7))
                d.ellipse([x + 1, 13, x + 7, 20], fill=(250, 250, 250))
                d.ellipse([x + 3, 15, x + 5, 18], fill=R.choice(PACK[:4]))
                x += w
        elif kind == "rolls":
            w = 16
            band = R.choice(((60, 120, 200), (220, 80, 120), (80, 170, 90)))
            d.rectangle([x, 4, x + w - 2, 23], fill=(244, 244, 246))
            for cx in range(x + 2, x + w - 2, 5):
                for cy in (7, 13, 19):
                    d.ellipse([cx - 2, cy - 2, cx + 2, cy + 2], outline=(214, 214, 218), fill=(250, 250, 252))
                    d.point((cx, cy), fill=(180, 160, 130))
            d.rectangle([x, 10, x + w - 2, 11], fill=band)
            d.line([(x + w - 1, 4), (x + w - 1, 23)], fill=GAP)
            x += w
        elif kind == "sprays":
            w = 5
            c = R.choice(((220, 220, 60), (60, 160, 220), (220, 60, 140), (240, 240, 240)))
            for _ in range(R.choice((3, 4))):
                d.rectangle([x, 8, x + w - 2, 23], fill=c)
                d.rectangle([x + 1, 4, x + w - 3, 8], fill=(240, 240, 240))
                d.rectangle([x, 4, x + 1, 5], fill=(240, 240, 240))
                d.rectangle([x, 13, x + w - 2, 16], fill=_shade(c, 0.6))
                x += w
        elif kind == "crisps":
            w = 9
            c = R.choice(((220, 40, 40), (40, 80, 200), (50, 160, 70), (240, 200, 30), (140, 60, 160), (240, 120, 30)))
            for _ in range(R.choice((2, 3))):
                d.rounded_rectangle([x, 9, x + w - 2, 23], 3, fill=c)
                for k in range(0, w - 1, 2):
                    d.point((x + k, 9), fill=_shade(c, 0.6))
                    d.point((x + k, 23), fill=_shade(c, 0.6))
                d.ellipse([x + 2, 14, x + w - 4, 19], fill=(240, 210, 120))
                d.line([(x + 1, 11), (x + 1, 21)], fill=_shade(c, 1.4))
                x += w
        elif kind == "biscuits":
            w = 16
            for y0 in (18, 12, 6):
                c = R.choice(PACK)
                d.rounded_rectangle([x, y0, x + w - 2, y0 + 5], 2, fill=c)
                d.rectangle([x + 4, y0 + 1, x + 10, y0 + 4], fill=(200, 150, 90))
            x += w
        elif kind == "chocolate":
            w = 4
            c = R.choice(((110, 40, 120), (200, 40, 40), (40, 60, 160), (90, 50, 30), (230, 160, 30)))
            for _ in range(R.choice((3, 4, 5))):
                for y0 in (17, 11):
                    d.rectangle([x, y0, x + w - 2, y0 + 5], fill=c)
                    d.line([(x, y0 + 2), (x + w - 2, y0 + 2)], fill=(240, 236, 220))
                x += w
        elif kind == "milk":
            w = 7
            cap = R.choice(((50, 150, 60), (40, 90, 200), (220, 40, 40)))
            for _ in range(R.choice((3, 4))):
                d.rectangle([x, 9, x + w - 2, 23], fill=(248, 248, 246))
                d.rectangle([x + 1, 6, x + w - 3, 9], fill=(240, 240, 238))
                d.rectangle([x + 1, 4, x + w - 3, 6], fill=cap)
                d.rectangle([x, 14, x + w - 2, 17], fill=cap)
                d.line([(x + w - 1, 4), (x + w - 1, 23)], fill=GAP)
                x += w
        elif kind == "yoghurt":
            w = 5
            c = R.choice(((240, 160, 180), (250, 230, 120), (200, 120, 200), (240, 240, 240)))
            for _ in range(R.choice((3, 4))):
                for y0 in (18, 12):
                    d.polygon([(x, y0), (x + w - 2, y0), (x + w - 3, y0 + 5), (x + 1, y0 + 5)], fill=(246, 246, 244))
                    d.rectangle([x, y0, x + w - 2, y0 + 1], fill=c)
                x += w
        elif kind == "meat":
            w = 13
            for y0 in (19, 14):
                c = R.choice(((200, 70, 70), (220, 120, 110), (180, 50, 50), (230, 170, 140)))
                d.rectangle([x, y0, x + w - 2, y0 + 4], fill=(30, 30, 32))
                d.rectangle([x + 1, y0 + 1, x + w - 3, y0 + 3], fill=c)
                d.rectangle([x + 2, y0, x + 6, y0 + 1], fill=(246, 246, 240))
            x += w
        elif kind == "meals":
            w = 12
            for y0 in (17, 11):
                c = R.choice(((30, 30, 30), (240, 240, 240), (120, 40, 60), (40, 90, 60)))
                d.rectangle([x, y0, x + w - 2, y0 + 5], fill=c)
                d.ellipse([x + 2, y0 + 1, x + 8, y0 + 4], fill=R.choice(((230, 170, 60), (200, 90, 40), (240, 220, 160))))
            x += w
        elif kind == "frozen":
            w = 12
            c = R.choice(((40, 90, 200), (200, 50, 50), (240, 240, 240), (60, 160, 200), (230, 150, 40)))
            for y0 in (16, 8):
                d.rectangle([x, y0, x + w - 2, y0 + 7], fill=c)
                d.ellipse([x + 2, y0 + 2, x + 8, y0 + 6], fill=R.choice(((240, 200, 120), (220, 120, 60), (120, 180, 80))))
                d.line([(x, y0), (x + w - 2, y0)], fill=(236, 244, 250))
            x += w
        elif kind == "paint":
            w = 8
            c = R.choice(((240, 240, 236), (200, 60, 50), (60, 110, 180), (90, 150, 90), (230, 200, 120), (120, 90, 140)))
            for _ in range(R.choice((2, 3))):
                for y0 in (13, 3):
                    d.rectangle([x, y0, x + w - 2, y0 + 10], fill=(214, 216, 220))
                    d.rectangle([x, y0 + 3, x + w - 2, y0 + 8], fill=c)
                    d.line([(x, y0 + 1), (x + w - 2, y0 + 1)], fill=(170, 170, 176))
                x += w
        elif kind == "tools":
            w = 10
            c = R.choice(((240, 200, 30), (220, 40, 40), (40, 90, 190), (40, 140, 70), (240, 120, 20)))
            for _ in range(2):
                d.rectangle([x, 3, x + w - 2, 23], fill=(236, 236, 232))
                d.rectangle([x, 3, x + w - 2, 7], fill=c)
                d.rectangle([x + 2, 9, x + w - 4, 20], fill=(200, 210, 220))
                d.rectangle([x + 3, 10, x + 5, 19], fill=_shade(c, 0.8))
                d.ellipse([x + 3, 4, x + 5, 6], fill=GAP)
                x += w
        else:
            raise ValueError(kind)
    return _speckle(img, 3)


def goods(top, bottom):
    """Two shelf rows on one 64 x 64 texture: rows at uv v 0-6 and 6-12, the shelf-edge price strip
    in the last quarter (uv v 12-16)."""
    img = _img(64, 64, (236, 236, 232))
    img.paste(goods_row(top), (0, 0))
    img.paste(goods_row(bottom), (0, 24))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 48, 63, 63], fill=(244, 244, 240))
    for x in range(2, 64, 13):
        colour = (250, 214, 40) if R.random() < 0.3 else (255, 255, 255)
        d.rectangle([x, 50, x + 8, 61], fill=colour, outline=(190, 190, 190))
        d.line([(x + 2, 54), (x + 6, 54)], fill=(40, 40, 40))
        d.line([(x + 2, 57), (x + 5, 57)], fill=(200, 30, 30))
    return img


# ----------------------------------------------------------------- other surfaces

def perforated(colour=(214, 216, 218), size=32):
    """The pegboard spine of a gondola shelf."""
    img = _img(size, size, colour)
    d = ImageDraw.Draw(img)
    for y in range(2, size, 4):
        for x in range(2, size, 4):
            d.point((x, y), fill=_shade(colour, 0.7))
    return img


def belt(size=32):
    img = _img(size, size, (26, 26, 28))
    d = ImageDraw.Draw(img)
    for x in range(0, size, 3):
        d.line([(x, 0), (x, size)], fill=(40, 40, 44))
    return _speckle(img, 3)


def scanner(size=32):
    img = _img(size, size, (60, 62, 66))
    d = ImageDraw.Draw(img)
    d.rectangle([4, 6, 27, 25], fill=(20, 22, 26), outline=(140, 140, 140))
    d.line([(6, 16), (25, 16)], fill=(255, 40, 40))
    d.line([(16, 8), (16, 23)], fill=(200, 30, 30))
    return img


def till_screen(size=32):
    img = _img(size, size, (230, 236, 240))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, size - 1, 5], fill=(40, 120, 70))
    for y in range(8, size - 6, 4):
        d.line([(2, y), (18, y)], fill=(90, 90, 100))
        d.line([(24, y), (29, y)], fill=(90, 90, 100))
    d.rectangle([2, size - 6, size - 3, size - 2], fill=(40, 160, 80))
    return img


def kiosk_screen(size=32):
    """Self-checkout screen: a big green 'start' button, nothing written."""
    img = _img(size, size, (240, 244, 246))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, size - 1, 5], fill=(40, 120, 70))
    d.rounded_rectangle([5, 10, 26, 22], 3, fill=(40, 170, 80))
    d.polygon([(13, 13), (13, 19), (19, 16)], fill=(255, 255, 255))
    d.rectangle([3, 25, 28, 28], fill=(200, 210, 216))
    return img


def card_reader(size=16):
    img = _img(size, size, (34, 34, 38))
    d = ImageDraw.Draw(img)
    d.rectangle([3, 2, 12, 6], fill=(120, 190, 140))
    for y in range(8, 15, 2):
        for x in range(4, 12, 3):
            d.point((x, y), fill=(200, 200, 200))
    return img


def lane_light(size=16):
    img = _img(size, size, (250, 250, 250))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, size - 1, size - 1], outline=(40, 140, 70))
    d.rectangle([5, 4, 10, 11], fill=(40, 160, 80))
    return img


def mesh(size=32, colour=(196, 200, 206)):
    """Trolley wire mesh: see-through between the wires (cutout)."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for k in range(0, size, 4):
        d.line([(k, 0), (k, size)], fill=colour + (255,))
        d.line([(0, k), (size, k)], fill=colour + (255,))
    return img


def plastic(colour, size=16):
    return _speckle(_img(size, size, colour), 4)


def produce(size=64):
    """Fruit and veg crates seen from above: two rows (uv v 0-8 and 8-16) of four crates each."""
    img = _img(size, size, (150, 110, 70))
    d = ImageDraw.Draw(img)
    kinds = [((200, 30, 30), 3), ((250, 150, 20), 3), ((240, 210, 50), 0), ((70, 150, 50), 4),
             ((150, 110, 60), 3), ((120, 40, 120), 2), ((80, 170, 60), 3), ((230, 60, 40), 2)]
    for i, (colour, r) in enumerate(kinds):
        x0, y0 = (i % 4) * 16, (i // 4) * 32
        d.rectangle([x0, y0, x0 + 15, y0 + 31], fill=(120, 84, 50))
        d.rectangle([x0 + 1, y0 + 1, x0 + 14, y0 + 30], fill=_shade(colour, 0.55))
        if r == 0:                                             # bananas: curved bunches
            for k in range(5):
                d.arc([x0 - 2, y0 + 2 + k * 6, x0 + 16, y0 + 12 + k * 6], 200, 340, fill=colour, width=2)
        else:
            for k in range(40):
                cx, cy = x0 + 2 + R.random() * 11, y0 + 2 + R.random() * 27
                rr = r + R.random() * 0.8
                c = _shade(colour, 0.85 + R.random() * 0.3)
                d.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=c)
                d.point((cx - rr / 2, cy - rr / 2), fill=_shade(c, 1.4))
    return img


def crate_side(size=32):
    img = _img(size, size, (160, 116, 70))
    d = ImageDraw.Draw(img)
    for y in range(0, size, 8):
        d.rectangle([0, y + 6, size, y + 7], fill=(90, 60, 36))
    return _speckle(img, 5)


def frost_doors(size=64):
    """Upright freezer: two glass doors (1 x 2) with frosty goods behind and lit shelf edges."""
    img = Image.new("RGBA", (size, size * 2), (210, 222, 230, 255))
    d = ImageDraw.Draw(img)
    for k in range(2):
        x0 = k * size // 2
        d.rectangle([x0 + 2, 4, x0 + size // 2 - 3, size * 2 - 6], fill=(150, 176, 196))
        for row in range(5):
            y = 10 + row * 22
            rowimg = goods_row(R.choice(("frozen", "frozen", "meals"))).resize((size // 2 - 6, 16))
            img.paste(rowimg, (x0 + 3, y))
            d.rectangle([x0 + 3, y + 16, x0 + size // 2 - 4, y + 17], fill=(240, 250, 255))
        d.rectangle([x0 + size // 2 - 6 if k == 0 else x0 + 4, 50, x0 + size // 2 - 5 if k == 0 else x0 + 5, 78],
                    fill=(200, 200, 204))
    a = np.asarray(img).astype(float)
    a[..., :3] = a[..., :3] * 0.8 + np.array([220, 236, 248]) * 0.2          # frost on the glass
    d2 = ImageDraw.Draw(img := Image.fromarray(a.astype(np.uint8), "RGBA"))
    d2.rectangle([0, 0, size - 1, size * 2 - 1], outline=(70, 74, 80), width=2)
    d2.line([(size // 2, 0), (size // 2, size * 2)], fill=(70, 74, 80), width=2)
    d2.rectangle([0, size * 2 - 5, size - 1, size * 2 - 1], fill=(50, 52, 56))
    return img


def frozen_lid(size=64):
    """A chest freezer seen from above through its sliding glass lids."""
    img = _img(size, size, (200, 214, 224))
    for y in range(4, size - 8, 14):
        row = goods_row("frozen").resize((size - 8, 12))
        img.paste(row, (4, y))
    a = np.asarray(img).astype(float)
    a[..., :3] = a[..., :3] * 0.75 + np.array([226, 240, 250]) * 0.25
    img = Image.fromarray(a.astype(np.uint8), "RGBA")
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, size - 1, size - 1], outline=(240, 240, 240), width=3)
    d.line([(size // 2, 0), (size // 2, size)], fill=(240, 240, 240), width=2)
    return img


def promo_band(colour=(40, 90, 190), size=32):
    img = _img(size, size, (244, 244, 242))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 10, size, 20], fill=colour)
    d.rectangle([0, 22, size, 23], fill=_shade(colour, 1.3))
    return img


def popcorn(size=32):
    img = _img(size, size, (240, 214, 130))
    d = ImageDraw.Draw(img)
    for _ in range(140):
        x, y, r = R.random() * size, R.random() * size, 1 + R.random() * 1.6
        d.ellipse([x - r, y - r, x + r, y + r], fill=R.choice(((252, 244, 214), (246, 228, 170), (236, 200, 110))))
    return img


def sweets(size=64):
    """Pick and mix: eight bins of brightly coloured sweets."""
    img = _img(size, size, (240, 240, 240))
    d = ImageDraw.Draw(img)
    palettes = [((240, 60, 60), (250, 200, 40)), ((40, 170, 80), (240, 240, 240)), ((250, 120, 180), (250, 250, 250)),
                ((60, 60, 60), (240, 240, 240)), ((250, 160, 30), (220, 40, 40)), ((150, 60, 200), (60, 200, 220)),
                ((250, 220, 60), (240, 80, 120)), ((80, 180, 240), (240, 240, 240))]
    for i, pal in enumerate(palettes):
        x0, y0 = (i % 4) * 16, (i // 4) * 32
        d.rectangle([x0 + 1, y0 + 1, x0 + 14, y0 + 30], fill=(250, 250, 250))
        for _ in range(26):
            cx, cy = x0 + 3 + R.random() * 9, y0 + 3 + R.random() * 25
            c = R.choice(pal)
            if R.random() < 0.4:
                d.rectangle([cx - 1.5, cy - 1, cx + 1.5, cy + 1], fill=c)
            else:
                d.ellipse([cx - 1.6, cy - 1.6, cx + 1.6, cy + 1.6], fill=c)
        d.rectangle([x0, y0, x0 + 15, y0 + 31], outline=(200, 200, 204))
    return img


def drinks_panel(size=32):
    """The lit front of a cinema drinks dispenser: four flavour panels."""
    img = _img(size, size, (30, 30, 34))
    d = ImageDraw.Draw(img)
    for k, c in enumerate(((40, 40, 40), (240, 130, 30), (60, 170, 60), (210, 40, 50))):
        x = 1 + k * 8
        d.rectangle([x, 4, x + 6, 20], fill=c, outline=(230, 230, 230))
        d.ellipse([x + 1, 8, x + 5, 14], fill=_shade(c, 1.6) if c[0] > 50 else (90, 90, 90))
    return img


def atm(size=32):
    """A cash machine fascia: screen, side keys, keypad, card slot and cash slot. No bank name."""
    img = _img(size, size, (176, 180, 186))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, size - 1, 5], fill=(40, 46, 60))
    d.rectangle([7, 7, 24, 17], fill=(30, 60, 110))
    d.rectangle([9, 9, 22, 11], fill=(200, 220, 240))
    d.rectangle([9, 13, 18, 14], fill=(130, 170, 220))
    for y in (8, 11, 14):
        d.rectangle([3, y, 5, y + 1], fill=(90, 90, 96))
        d.rectangle([26, y, 28, y + 1], fill=(90, 90, 96))
    for r in range(4):
        for c in range(3):
            d.rectangle([8 + c * 3, 19 + r * 2, 9 + c * 3, 19 + r * 2], fill=(60, 60, 66))
    d.rectangle([19, 19, 25, 20], fill=(30, 30, 30))
    d.point((26, 19), fill=(60, 220, 60))
    d.rectangle([9, 28, 22, 29], fill=(20, 20, 20))
    return img


def boxes(size=32):
    """Cardboard boxes stacked on a pallet (warehouse racking)."""
    img = _img(size, size, (190, 150, 100))
    d = ImageDraw.Draw(img)
    for y0 in (0, 16):
        for x0 in ((0, 16) if y0 == 0 else (-8, 8, 24)):
            d.rectangle([x0, y0, x0 + 15, y0 + 15], fill=(196 + R.randint(-14, 14), 152, 100), outline=(150, 112, 70))
            d.line([(x0 + 8, y0), (x0 + 8, y0 + 5)], fill=(220, 200, 150))
            d.rectangle([x0 + 2, y0 + 9, x0 + 6, y0 + 12], fill=(240, 240, 236))
    return _speckle(img, 4)


def pallet(size=16):
    img = _img(size, size, (180, 140, 90))
    d = ImageDraw.Draw(img)
    for x in range(0, size, 6):
        d.rectangle([x + 3, 0, x + 5, size], fill=(30, 26, 22))
    return img


# ----------------------------------------------------------------- film posters (made-up films)

def _font(path, size):
    return ImageFont.truetype(path, size)


def _centre(d, y, text, font, fill, w=128, stroke=0, stroke_fill=None):
    """Centred text, made smaller until it fits the poster with a margin."""
    while d.textlength(text, font=font) + 2 * stroke > w - 10 and font.size > 6:
        font = ImageFont.truetype(font.path, font.size - 1)
    tw = d.textlength(text, font=font)
    d.text(((w - tw) / 2, y), text, font=font, fill=fill, stroke_width=stroke, stroke_fill=stroke_fill)


def _credits(d, y, colour):
    for k in range(3):
        w = 96 - k * 14
        d.rectangle([(128 - w) / 2, y + k * 4, (128 + w) / 2, y + k * 4 + 1], fill=colour)


def _gradient(top, bottom, w=128, h=256):
    t = np.linspace(0, 1, h)[:, None, None]
    rgb = np.array(top, float) * (1 - t) + np.array(bottom, float) * t
    rgb = np.repeat(rgb, w, axis=1)
    return Image.fromarray(np.dstack([rgb.astype(np.uint8), np.full((h, w), 255, np.uint8)]), "RGBA")


def poster(film):
    """A 1 x 2 cinema poster (128 x 256). Every film here is made up."""
    if film == "night_bus":
        img = _gradient((8, 12, 40), (30, 20, 60))
        d = ImageDraw.Draw(img)
        for _ in range(60):
            x, y = R.random() * 128, R.random() * 120
            d.point((x, y), fill=(220, 220, 255))
        d.ellipse([78, 20, 108, 50], fill=(240, 236, 210))
        d.rectangle([0, 188, 128, 256], fill=(14, 14, 20))
        for x in range(0, 128, 18):                                  # street lights
            d.line([(x, 150), (x, 190)], fill=(60, 60, 70), width=2)
            d.ellipse([x - 3, 146, x + 5, 152], fill=(255, 200, 100))
        d.rectangle([22, 140, 106, 186], fill=(170, 20, 24))          # a double-decker in the dark
        d.rectangle([22, 140, 106, 144], fill=(120, 14, 16))
        for x in range(26, 104, 13):
            d.rectangle([x, 147, x + 9, 157], fill=(250, 220, 120))
            d.rectangle([x, 166, x + 9, 176], fill=(250, 220, 120))
        d.rectangle([92, 166, 104, 186], fill=(40, 40, 50))
        for x in (36, 90):
            d.ellipse([x - 7, 180, x + 7, 194], fill=(20, 20, 20))
        d.ellipse([60, 150, 68, 158], fill=(20, 10, 10))              # someone at the top deck window
        _centre(d, 200, "NIGHT BUS", _font(BOLD, 22), (230, 30, 40))
        _centre(d, 226, "THE LAST STOP IS ONLY THE BEGINNING", _font(CONDENSED, 7), (200, 200, 210))
        _centre(d, 6, "IN CINEMAS FRIDAY", _font(CONDENSED, 9), (200, 200, 220))
        _credits(d, 240, (90, 90, 110))
    elif film == "cube_quest":
        img = _gradient((90, 180, 250), (200, 240, 255))
        d = ImageDraw.Draw(img)
        for cx, cy in ((24, 40), (100, 60), (60, 24)):
            d.ellipse([cx - 16, cy - 8, cx + 16, cy + 8], fill=(255, 255, 255))
        d.rectangle([0, 190, 128, 256], fill=(90, 190, 70))
        d.polygon([(0, 196), (40, 176), (90, 192), (128, 178), (128, 256), (0, 256)], fill=(70, 170, 60))
        # the hero: a flat cube with a friendly face
        d.polygon([(64, 92), (102, 110), (64, 128), (26, 110)], fill=(250, 210, 60))
        d.polygon([(26, 110), (64, 128), (64, 172), (26, 154)], fill=(230, 150, 40))
        d.polygon([(64, 128), (102, 110), (102, 154), (64, 172)], fill=(200, 110, 30))
        d.ellipse([36, 130, 44, 140], fill=(255, 255, 255))
        d.ellipse([50, 136, 58, 146], fill=(255, 255, 255))
        d.ellipse([39, 133, 43, 138], fill=(20, 20, 20))
        d.ellipse([53, 139, 57, 144], fill=(20, 20, 20))
        d.arc([38, 140, 58, 158], 20, 150, fill=(60, 20, 10), width=2)
        _centre(d, 194, "CUBE QUEST", _font(BOLD, 21), (255, 255, 255), stroke=2, stroke_fill=(40, 60, 160))
        _centre(d, 222, "ONE SMALL CUBE. ONE BIG WORLD.", _font(CONDENSED, 7), (20, 50, 20))
        _centre(d, 6, "THIS HALF TERM", _font(CONDENSED, 9), (20, 40, 110))
        _credits(d, 238, (40, 100, 40))
    elif film == "orbit_nine":
        img = _gradient((4, 4, 12), (20, 10, 40))
        d = ImageDraw.Draw(img)
        for _ in range(140):
            x, y = R.random() * 128, R.random() * 256
            d.point((x, y), fill=(R.randint(160, 255),) * 3)
        d.ellipse([10, 60, 118, 168], fill=(60, 110, 170))
        d.ellipse([20, 66, 112, 150], fill=(90, 150, 200))
        d.arc([-20, 98, 148, 132], 190, 350, fill=(220, 200, 150), width=3)
        d.arc([-20, 98, 148, 132], 10, 170, fill=(200, 180, 130), width=3)
        d.polygon([(84, 40), (100, 30), (96, 46)], fill=(230, 230, 240))
        d.line([(84, 40), (60, 58)], fill=(255, 160, 80), width=2)
        _centre(d, 184, "ORBIT NINE", _font(BOLD, 21), (240, 240, 255))
        _centre(d, 210, "NO SIGNAL. NO RESCUE. NO WAY BACK.", _font(CONDENSED, 7), (160, 170, 210))
        _centre(d, 6, "EXPERIENCE IT IN 3D", _font(CONDENSED, 9), (180, 190, 230))
        _credits(d, 232, (70, 70, 110))
    elif film == "last_junction":
        img = _gradient((250, 170, 40), (120, 30, 10))
        d = ImageDraw.Draw(img)
        d.ellipse([24, 60, 104, 140], fill=(255, 220, 120))
        d.polygon([(0, 256), (52, 150), (76, 150), (128, 256)], fill=(40, 40, 44))
        for y in range(160, 256, 18):
            d.rectangle([62, y, 66, y + 8], fill=(240, 240, 240))
        d.polygon([(54, 150), (60, 128), (66, 128), (72, 150)], fill=(20, 16, 14))     # a figure walking away
        d.ellipse([58, 118, 68, 128], fill=(20, 16, 14))
        _centre(d, 12, "THE LAST", _font(BOLD, 16), (40, 14, 6))
        _centre(d, 30, "JUNCTION", _font(BOLD, 22), (40, 14, 6))
        _centre(d, 214, "EVERY ROAD LEADS SOMEWHERE", _font(CONDENSED, 8), (255, 230, 200))
        _credits(d, 234, (240, 180, 140))
    elif film == "rain_moors":
        img = _gradient((150, 160, 170), (210, 214, 210))
        d = ImageDraw.Draw(img)
        d.polygon([(0, 150), (40, 120), (80, 140), (128, 110), (128, 256), (0, 256)], fill=(90, 110, 80))
        d.polygon([(0, 180), (60, 160), (128, 176), (128, 256), (0, 256)], fill=(70, 90, 60))
        for _ in range(90):
            x, y = R.random() * 128, R.random() * 256
            d.line([(x, y), (x - 2, y + 8)], fill=(200, 206, 214))
        d.polygon([(70, 176), (74, 146), (80, 146), (86, 176)], fill=(40, 30, 40))
        d.ellipse([72, 136, 82, 146], fill=(40, 30, 40))
        d.polygon([(70, 140), (84, 136), (94, 168), (82, 168)], fill=(110, 30, 40))      # a cloak in the wind
        _centre(d, 14, "RAIN ON", _font(SERIF, 15), (40, 44, 50))
        _centre(d, 32, "THE MOORS", _font(SERIF, 18), (40, 44, 50))
        _centre(d, 214, "SOME LOVES ARE WORTH THE STORM", _font(CONDENSED, 7), (230, 230, 220))
        _credits(d, 232, (200, 210, 200))
    else:
        raise ValueError(film)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 127, 255], outline=(10, 10, 10), width=2)
    return img


POSTERS = {"night_bus": "Night Bus", "cube_quest": "Cube Quest", "orbit_nine": "Orbit Nine",
           "last_junction": "The Last Junction", "rain_moors": "Rain on the Moors"}


def velvet(colour, size=32):
    a = NP.normal(0, 1, (size, size))
    rgb = np.array(colour, float) + (a * 7)[..., None]
    rgb *= (0.92 + 0.08 * np.sin(np.arange(size) / 2.0))[None, :, None]
    img = Image.fromarray(np.dstack([np.clip(rgb, 0, 255).astype(np.uint8), np.full((size, size), 255, np.uint8)]), "RGBA")
    return img.filter(ImageFilter.SMOOTH)


# ----------------------------------------------------------------- workshop, fast food, garden

def toolchest_front(size=32):
    img = _img(size, size, (196, 28, 32))
    d = ImageDraw.Draw(img)
    rows = (0, 4, 8, 12, 17, 23, 32)
    for y0, y1 in zip(rows, rows[1:]):
        d.rectangle([1, y0 + 1, size - 2, y1 - 1], fill=(206, 34, 38), outline=(150, 18, 22))
        d.rectangle([6, y0 + 2, size - 7, y0 + 2], fill=(214, 216, 220))
    return _speckle(img, 3)


def tyre_tread(size=32):
    img = _img(size, size, (30, 30, 32))
    d = ImageDraw.Draw(img)
    for x in range(0, size, 4):
        d.line([(x, 2), (x + 2, size - 3)], fill=(18, 18, 20), width=1)
    d.rectangle([0, 0, size, 1], fill=(44, 44, 46))
    d.rectangle([0, size - 2, size, size], fill=(44, 44, 46))
    return _speckle(img, 3)


def tyre_side(size=32):
    img = _img(size, size, (36, 36, 38))
    d = ImageDraw.Draw(img)
    d.ellipse([1, 1, size - 2, size - 2], fill=(34, 34, 36), outline=(24, 24, 26))
    d.ellipse([8, 8, size - 9, size - 9], fill=(12, 12, 14))
    d.ellipse([10, 10, size - 11, size - 11], fill=(150, 152, 156))
    return img


def drum_side(colour=(30, 80, 170), size=32):
    img = _img(size, size, colour)
    d = ImageDraw.Draw(img)
    for y in (6, 25):
        d.rectangle([0, y, size, y + 1], fill=_shade(colour, 0.65))
        d.rectangle([0, y + 2, size, y + 2], fill=_shade(colour, 1.3))
    d.rectangle([9, 11, 22, 19], fill=(240, 240, 236))
    d.rectangle([11, 13, 20, 14], fill=(60, 60, 60))
    d.rectangle([11, 16, 17, 17], fill=(60, 60, 60))
    return _speckle(img, 4)


def drum_top(colour=(30, 80, 170), size=32):
    img = _img(size, size, _shade(colour, 0.9))
    d = ImageDraw.Draw(img)
    d.ellipse([3, 3, size - 4, size - 4], outline=_shade(colour, 0.6))
    d.ellipse([20, 7, 25, 12], fill=(160, 160, 166))
    d.ellipse([8, 20, 11, 23], fill=(160, 160, 166))
    return img


def oil(size=32):
    """Hot fryer oil, golden with a few bubbles."""
    img = _img(size, size, (214, 160, 50))
    d = ImageDraw.Draw(img)
    for _ in range(30):
        x, y, r = R.random() * size, R.random() * size, 0.6 + R.random()
        d.ellipse([x - r, y - r, x + r, y + r], outline=(244, 210, 120))
    return img


def _food(d, kind, x, y):
    """A little picture of the food on a menu board, drawn in a 26 x 18 box."""
    if kind == "burger":
        d.chord([x + 3, y + 1, x + 23, y + 13], 180, 360, fill=(214, 150, 70))
        d.rectangle([x + 3, y + 8, x + 23, y + 9], fill=(90, 170, 60))
        d.rectangle([x + 3, y + 10, x + 23, y + 12], fill=(110, 60, 30))
        d.rectangle([x + 4, y + 13, x + 22, y + 15], fill=(214, 150, 70))
    elif kind == "fries":
        for k in range(6):
            d.rectangle([x + 8 + k * 2, y + 1 + (k % 3), x + 9 + k * 2, y + 10], fill=(250, 210, 80))
        d.polygon([(x + 6, y + 7), (x + 20, y + 7), (x + 18, y + 17), (x + 8, y + 17)], fill=(210, 40, 40))
    elif kind == "drink":
        d.polygon([(x + 8, y + 3), (x + 18, y + 3), (x + 17, y + 17), (x + 9, y + 17)], fill=(240, 240, 240))
        d.rectangle([x + 9, y + 7, x + 17, y + 11], fill=(210, 40, 40))
        d.line([(x + 14, y + 3), (x + 16, y - 1)], fill=(240, 240, 240), width=1)
    elif kind == "wrap":
        d.polygon([(x + 4, y + 14), (x + 20, y + 2), (x + 23, y + 6), (x + 8, y + 17)], fill=(230, 200, 140))
        d.ellipse([x + 17, y + 1, x + 24, y + 7], fill=(90, 170, 60))
    elif kind == "nuggets":
        for cx, cy in ((8, 10), (14, 7), (19, 11), (12, 13)):
            d.ellipse([x + cx - 4, y + cy - 3, x + cx + 4, y + cy + 3], fill=(210, 150, 60), outline=(170, 110, 40))
    elif kind == "ice_cream":
        d.polygon([(x + 9, y + 8), (x + 17, y + 8), (x + 13, y + 17)], fill=(210, 160, 90))
        d.ellipse([x + 8, y + 1, x + 18, y + 10], fill=(250, 246, 236))
        d.ellipse([x + 10, y - 1, x + 16, y + 5], fill=(250, 246, 236))


def menu_board():
    """A drive-thru / counter menu board, two blocks wide (256 x 128): six made-up dishes with
    pictures and prices, and a meal deal band along the bottom."""
    img = _img(256, 128, (24, 26, 30))
    d = ImageDraw.Draw(img)
    name_f, price_f, band_f = _font(CONDENSED, 11), _font(BOLD, 11), _font(BOLD, 12)
    items = (("burger", "Classic Burger", "£4.29"), ("fries", "Fries", "£1.89"), ("drink", "Cold Drink", "£1.59"),
             ("wrap", "Chicken Wrap", "£3.99"), ("nuggets", "Bites x6", "£3.19"), ("ice_cream", "Whippy Cone", "£1.29"))
    for i, (kind, name, price) in enumerate(items):
        x, y = 6 + (i % 3) * 83, 6 + (i // 3) * 51
        d.rectangle([x, y, x + 78, y + 46], fill=(36, 40, 46))
        pic = _img(26, 18, (36, 40, 46))
        _food(ImageDraw.Draw(pic), kind, 0, 0)
        img.paste(pic.resize((52, 36), Image.NEAREST), (x + 2, y + 2))
        d.text((x + 4, y + 32), name, font=name_f, fill=(240, 240, 240))
        d.text((x + 50, y + 6), price, font=price_f, fill=(250, 200, 50))
    d.rectangle([0, 109, 255, 127], fill=(200, 30, 36))
    text = "MEAL DEAL   BURGER + FRIES + DRINK   £6.49"
    while d.textlength(text, font=band_f) > 244:
        band_f = _font(BOLD, band_f.size - 1)
    tw = d.textlength(text, font=band_f)
    d.text(((256 - tw) / 2, 111), text, font=band_f, fill=(255, 255, 255))
    return img


def order_screen(size=32):
    img = _img(size, size, (16, 18, 22))
    d = ImageDraw.Draw(img)
    d.rectangle([2, 2, size - 3, 6], fill=(40, 140, 70))
    for y in (9, 13, 17):
        d.line([(4, y), (18, y)], fill=(220, 220, 220))
        d.line([(23, y), (27, y)], fill=(250, 200, 50))
    d.line([(4, 24), (27, 24)], fill=(120, 120, 120))
    d.line([(16, 27), (27, 27)], fill=(250, 200, 50))
    return img


def grille(size=16):
    img = _img(size, size, (50, 52, 56))
    d = ImageDraw.Draw(img)
    for y in range(2, size, 3):
        for x in range(2, size, 3):
            d.point((x, y), fill=(14, 14, 16))
    return img


def ticket_screen(size=32):
    """Self-service ticket machine: a grid of made-up film tiles."""
    img = _img(size, size, (20, 20, 30))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, size - 1, 4], fill=(150, 20, 40))
    cols = ((30, 60, 120), (250, 190, 60), (40, 120, 70), (200, 80, 40), (120, 60, 140), (60, 160, 200))
    for i, c in enumerate(cols):
        x, y = 2 + (i % 3) * 10, 7 + (i // 3) * 12
        d.rectangle([x, y, x + 7, y + 9], fill=c)
        d.rectangle([x, y + 7, x + 7, y + 9], fill=(240, 240, 240))
    return img


def lens(size=16):
    img = _img(size, size, (20, 20, 22))
    d = ImageDraw.Draw(img)
    d.ellipse([2, 2, 13, 13], fill=(40, 60, 90), outline=(120, 120, 126))
    d.ellipse([5, 4, 8, 7], fill=(180, 200, 230))
    return img


def plants(size=64):
    """A garden centre bench from above: rows of potted plants in flower."""
    img = _img(size, size, (60, 46, 34))
    d = ImageDraw.Draw(img)
    flowers = ((230, 60, 80), (250, 220, 60), (180, 90, 220), (250, 250, 250), (250, 140, 40), (90, 140, 230))
    for gy in range(4):
        for gx in range(4):
            cx, cy = 8 + gx * 16, 8 + gy * 16
            d.ellipse([cx - 7, cy - 7, cx + 7, cy + 7], fill=(40 + R.randint(0, 30), 110 + R.randint(0, 40), 40))
            c = flowers[(gx + gy * 2) % len(flowers)]
            for _ in range(7):
                fx, fy = cx + R.uniform(-5, 5), cy + R.uniform(-5, 5)
                d.ellipse([fx - 1.5, fy - 1.5, fx + 1.5, fy + 1.5], fill=c)
    return img


def slats(colour=(150, 110, 70), size=32):
    img = _img(size, size, colour)
    d = ImageDraw.Draw(img)
    for x in range(0, size, 6):
        d.line([(x, 0), (x, size)], fill=_shade(colour, 0.6))
    return _speckle(img, 5)


def till_face(size=16):
    img = _img(size, size, (36, 36, 40))
    d = ImageDraw.Draw(img)
    d.rectangle([2, 2, 13, 7], fill=(40, 140, 90))
    d.rectangle([3, 3, 9, 4], fill=(200, 240, 210))
    for y in (10, 12):
        for x in range(3, 13, 3):
            d.rectangle([x, y, x + 1, y], fill=(170, 170, 176))
    return img


def garment(colour, size=16):
    img = _img(size, size, colour)
    d = ImageDraw.Draw(img)
    for y in range(0, size, 3):
        d.line([(0, y), (size, y)], fill=_shade(colour, 0.92))
    return img
