"""What the shops sell, in one list: each product's name (with its made-up brand), usual UK price,
how filling it is, which shelf it goes on, and its icon. Writes the icons (64 px, drawn at four
times the size and shrunk, shaded, in their real packaging: bottles, cans, tins, boxes, packets,
trays, loose fruit and veg, the chippy's paper...), the item models, the names, and
src/main/java/com/ptmuk/shop/Product.java (do not edit that by hand).

Every brand here is made up (Daleside, Hearthstone, Crunch Co, Fizz Cola...), nothing real.
"""
import math
from pathlib import Path
from PIL import Image, ImageDraw

G = None
SS, N = 4, 64

# ------------------------------------------------------------------ a small pixel font (3 x 5) for what's printed on packs

GLYPHS = {
    "A": "010101111101101", "B": "110101110101110", "C": "011100100100011", "D": "110101101101110", "E": "111100110100111",
    "F": "111100110100100", "G": "011100101101011", "H": "101101111101101", "I": "111010010010111", "J": "001001001101010",
    "K": "101101110101101", "L": "100100100100111", "M": "101111111101101", "N": "110101101101101", "O": "010101101101010",
    "P": "110101110100100", "Q": "010101101110011", "R": "110101110101101", "S": "011100010001110", "T": "111010010010010",
    "U": "101101101101111", "V": "101101101101010", "W": "101101111111101", "X": "101101010101101", "Y": "101101010010010",
    "Z": "111001010100111", "0": "111101101101111", "1": "010110010010111", "2": "110001010100111", "3": "110001010001110",
    "4": "101101111001001", "5": "111100110001110", "6": "011100111101111", "7": "111001010010010", "8": "111101111101111",
    "9": "111101111001110", "&": "010101010101011", "'": "010010000000000", ".": "000000000000010", "%": "101001010100101",
    "-": "000000111000000", "!": "010010010000010", "?": "110001010000010", " ": "000000000000000", "+": "000010111010000",
}


def _c(rgb, a=255):
    return tuple(int(max(0, min(255, v))) for v in rgb[:3]) + (a,)


def shade(rgb, f):
    return tuple(max(0, min(255, int(v * f))) for v in rgb[:3])


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


class Pen:
    """Draws in 64 x 64 units on a picture four times the size."""

    def __init__(self):
        self.img = Image.new("RGBA", (N * SS, N * SS), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.img)

    def _b(self, x0, y0, x1, y1):
        return [x0 * SS, y0 * SS, x1 * SS - 1, y1 * SS - 1]

    def rect(self, x0, y0, x1, y1, fill, outline=None, r=0, w=1):
        if r:
            self.d.rounded_rectangle(self._b(x0, y0, x1, y1), radius=r * SS, fill=_c(fill),
                                     outline=_c(outline) if outline else None, width=int(w * SS))
        else:
            self.d.rectangle(self._b(x0, y0, x1, y1), fill=_c(fill), outline=_c(outline) if outline else None, width=int(w * SS))

    def ell(self, x0, y0, x1, y1, fill, outline=None, w=1):
        self.d.ellipse(self._b(x0, y0, x1, y1), fill=_c(fill), outline=_c(outline) if outline else None, width=int(w * SS))

    def poly(self, pts, fill, outline=None, w=1):
        p = [(x * SS, y * SS) for x, y in pts]
        self.d.polygon(p, fill=_c(fill))
        if outline:
            self.d.line(p + [p[0]], fill=_c(outline), width=int(w * SS), joint="curve")

    def line(self, pts, col, w=1):
        self.d.line([(x * SS, y * SS) for x, y in pts], fill=_c(col), width=max(1, int(w * SS)), joint="curve")

    def cyl(self, x0, y0, x1, y1, col, r=0, light=0.33):
        """A cylinder's side, lit from the left: lightest a third of the way across, darker to the edges."""
        mask = Image.new("L", self.img.size, 0)
        ImageDraw.Draw(mask).rounded_rectangle(self._b(x0, y0, x1, y1), radius=r * SS, fill=255)
        band = Image.new("RGBA", self.img.size, (0, 0, 0, 0))
        bd = ImageDraw.Draw(band)
        for px in range(int(x0 * SS), int(x1 * SS)):
            t = (px - x0 * SS) / max(1, (x1 - x0) * SS)
            f = 0.72 + 0.42 * math.cos((t - light) * math.pi * 0.95)
            bd.line([(px, y0 * SS), (px, y1 * SS)], fill=_c(shade(col, f)))
        self.img.paste(band, (0, 0), mask)

    def vgrad(self, x0, y0, x1, y1, top, bottom, r=0):
        mask = Image.new("L", self.img.size, 0)
        ImageDraw.Draw(mask).rounded_rectangle(self._b(x0, y0, x1, y1), radius=r * SS, fill=255)
        band = Image.new("RGBA", self.img.size, (0, 0, 0, 0))
        bd = ImageDraw.Draw(band)
        for py in range(int(y0 * SS), int(y1 * SS)):
            t = (py - y0 * SS) / max(1, (y1 - y0) * SS)
            bd.line([(x0 * SS, py), (x1 * SS, py)], fill=_c(mix(top, bottom, t)))
        self.img.paste(band, (0, 0), mask)

    def shine(self, x0, y0, x1, y1, a=90):
        over = Image.new("RGBA", self.img.size, (0, 0, 0, 0))
        ImageDraw.Draw(over).rounded_rectangle(self._b(x0, y0, x1, y1), radius=min(x1 - x0, y1 - y0) / 2 * SS, fill=(255, 255, 255, a))
        self.img.alpha_composite(over)

    def text(self, s, x, y, col, size=1.0, centre=True, bold=False):
        """Pixel letters (3 x 5), size 1 = one 64th of the icon a pixel."""
        s = s.upper()
        w = (len(s) * 4 - 1) * size
        x0 = x - w / 2 if centre else x
        for k, ch in enumerate(s):
            g = GLYPHS.get(ch, GLYPHS[" "])
            for r in range(5):
                for c in range(3):
                    if g[r * 3 + c] == "1":
                        px, py = x0 + (k * 4 + c) * size, y + r * size
                        self.d.rectangle([px * SS, py * SS, (px + size + (0.35 if bold else 0)) * SS - 1, (py + size) * SS - 1], fill=_c(col))

    def done(self):
        return self.img.resize((N, N), Image.LANCZOS)


INK, WHITE, SILVER, BLACK = (30, 30, 34), (250, 250, 248), (196, 200, 206), (24, 24, 26)

# ------------------------------------------------------------------ packaging

def milk(cap, band, brand, words, liquid=(250, 250, 244)):
    """A plastic milk bottle with its handle, coloured cap, label band."""
    p = Pen()
    p.cyl(14, 16, 50, 61, liquid, r=6)
    p.line([(46, 18), (53, 20), (54, 32), (47, 34)], shade(liquid, 0.85), 3)
    p.cyl(23, 8, 39, 18, liquid, r=3)
    p.cyl(23, 2, 39, 9, cap, r=2)
    p.rect(23, 7, 39, 9, shade(cap, 0.7))
    p.rect(14, 33, 50, 51, WHITE)
    p.rect(14, 33, 50, 37, band)
    p.rect(14, 47, 50, 51, band)
    p.text(brand, 32, 39, shade(band, 0.75), 1.0, bold=True)
    p.text(words, 32, 44.5, INK, 0.6)
    p.shine(18, 18, 21, 46, 70)
    return p.done()


def pet(liquid, cap, label, brand, words="", clear=True):
    """A plastic drinks bottle: the drink inside, its label, cap."""
    p = Pen()
    body = mix(liquid, WHITE, 0.25) if clear else liquid
    p.cyl(20, 18, 44, 62, body, r=6)
    p.poly([(20, 22), (27, 10), (37, 10), (44, 22)], body)
    p.cyl(27, 6, 37, 12, body, r=1)
    p.cyl(26, 1, 38, 7, cap, r=2)
    if clear:
        p.rect(21, 20, 43, 23, mix(liquid, WHITE, 0.5))              # the top of the drink
    p.rect(20, 32, 44, 47, label)
    p.text(brand, 32, 36, WHITE if sum(label) < 400 else INK, 0.9, bold=True)
    if words:
        p.text(words, 32, 42, WHITE if sum(label) < 400 else INK, 0.6)
    p.shine(23, 22, 26, 58, 80)
    return p.done()


def can(body, accent, brand, words="", top=SILVER):
    """A drinks can: ring pull, rims, its colours and name."""
    p = Pen()
    p.cyl(17, 10, 47, 60, body, r=3)
    p.ell(17, 6, 47, 14, top, outline=shade(top, 0.7))
    p.ell(22, 8, 42, 12, shade(top, 0.85))
    p.ell(28, 7, 36, 11, shade(top, 1.1), outline=shade(top, 0.6))   # the ring pull
    p.rect(17, 56, 47, 60, shade(top, 0.8), r=1)
    p.rect(17, 30, 47, 34, accent)
    p.text(brand, 32, 20, WHITE if sum(body) < 450 else INK, 1.0, bold=True)
    if words:
        p.text(words, 32, 41, WHITE if sum(body) < 450 else INK, 0.6)
    p.shine(21, 14, 24, 54, 70)
    return p.done()


def tin(label, accent, brand, picture, words=""):
    """A food tin: ridged rims, label, a picture of what's in it."""
    p = Pen()
    p.cyl(12, 14, 52, 58, SILVER, r=2)
    p.ell(12, 10, 52, 18, shade(SILVER, 1.05), outline=shade(SILVER, 0.7))
    p.cyl(12, 19, 52, 53, label)
    p.rect(12, 19, 52, 23, accent)
    p.text(brand, 32, 25, WHITE if sum(label) < 450 else INK, 0.8, bold=True)
    picture(p, 32, 40)
    if words:
        p.text(words, 32, 48, WHITE if sum(label) < 450 else INK, 0.55)
    for y in (55, 57):
        p.line([(12, y), (52, y)], shade(SILVER, 0.75), 0.5)
    return p.done()


def box(front, side, brand, art=None, words="", tall=True, band=None):
    """A box seen from a little to its right: its front (brand, a picture), its side and top."""
    p = Pen()
    x0, x1, y0, y1, dx, dy = (12, 42, 6, 60, 10, 5) if tall else (8, 44, 18, 56, 12, 6)
    p.poly([(x0, y0), (x0 + dx, y0 - dy), (x1 + dx, y0 - dy), (x1, y0)], shade(front, 1.12))
    p.poly([(x1, y0), (x1 + dx, y0 - dy), (x1 + dx, y1 - dy), (x1, y1)], side)
    p.vgrad(x0, y0, x1, y1, shade(front, 1.06), shade(front, 0.88))
    if band:
        p.rect(x0, y0 + 2, x1, y0 + 10, band)
    p.text(brand, (x0 + x1) / 2, y0 + 3.5, WHITE if sum(band or front) < 450 else INK, 0.9 if len(brand) <= 7 else 0.65, bold=True)
    if art:
        art(p, (x0 + x1) / 2, (y0 + y1) / 2 + 4)
    if words:
        p.text(words, (x0 + x1) / 2, y1 - 7, WHITE if sum(front) < 450 else INK, 0.55)
    p.line([(x0, y0), (x0, y1), (x1, y1)], shade(front, 0.6), 0.6)
    return p.done()


def packet(col, accent, brand, words="", window=None, sweets=False):
    """A packet with crimped ends (crisps, sweets, frozen chips): shaded, a window or picture, its name."""
    p = Pen()
    top, bot = 6, 60
    pts = [(10, top + 4)]
    for k in range(9):
        pts.append((10 + k * 5 + 2.5, top + (0 if k % 2 else 2)))
    pts += [(54, top + 4), (52, bot - 4)]
    for k in range(9):
        pts.append((52 - k * 5 - 2.5, bot - (0 if k % 2 else 2)))
    pts.append((12, bot - 4))
    p.poly(pts, col)
    p.cyl(10, top + 4, 54, bot - 4, col)
    p.rect(10, top + 2, 54, top + 7, shade(col, 0.82))
    p.rect(10, bot - 7, 54, bot - 2, shade(col, 0.82))
    p.rect(14, 15, 50, 23, accent, r=2)
    p.text(brand, 32, 17, WHITE if sum(accent) < 450 else INK, 0.85 if len(brand) <= 8 else 0.7, bold=True)
    if window:
        window(p, 32, 37)
    if words:
        p.text(words, 32, 48, WHITE if sum(col) < 450 else INK, 0.6)
    return p.done()


def crisps_in(p, cx, cy):
    p.ell(cx - 13, cy - 7, cx + 13, cy + 7, (250, 236, 180))
    for dx, dy in ((-6, -2), (2, -3), (-1, 2), (6, 1), (-8, 3)):
        p.ell(cx + dx - 4, cy + dy - 2.5, cx + dx + 4, cy + dy + 2.5, (232, 190, 80), outline=(196, 150, 50), w=0.4)


def bar(wrapper, accent, brand, words=""):
    """A chocolate bar in its wrapper, foil ends."""
    p = Pen()
    p.poly([(4, 30), (10, 22), (60, 22), (60, 42), (10, 42), (4, 34)], SILVER)
    p.vgrad(10, 22, 56, 42, shade(wrapper, 1.15), shade(wrapper, 0.8))
    p.rect(52, 22, 60, 42, SILVER)
    for y in range(23, 42, 3):
        p.line([(56, y), (60, y + 1.5)], shade(SILVER, 0.7), 0.5)
    p.rect(14, 26, 50, 38, accent, r=3)
    p.text(brand, 32, 28.5, WHITE if sum(accent) < 450 else INK, 1.0, bold=True)
    if words:
        p.text(words, 32, 34.5, WHITE if sum(accent) < 450 else INK, 0.55)
    return p.done()


def loaf(bag_band, brand, words, bread=(214, 170, 110)):
    """A sliced loaf in its bag: the bread showing, the band, the tie."""
    p = Pen()
    p.rect(6, 16, 56, 54, (246, 244, 236), r=8)
    p.rect(9, 18, 53, 30, bread, r=7)
    p.rect(9, 26, 53, 52, mix(bread, (250, 236, 200), 0.6), r=4)
    p.rect(6, 31, 56, 45, bag_band)
    p.text(brand, 31, 34, WHITE, 0.9, bold=True)
    p.text(words, 31, 40, WHITE, 0.6)
    p.poly([(56, 28), (62, 24), (62, 44), (56, 40)], (240, 238, 230))
    p.rect(55, 30, 58, 38, (220, 40, 40))
    p.shine(10, 20, 30, 23, 80)
    return p.done()


def tub(body, lid, brand, words="", fruit=None):
    p = Pen()
    p.poly([(12, 22), (52, 22), (48, 58), (16, 58)], body)
    p.cyl(13, 22, 51, 58, body)
    p.ell(10, 15, 54, 27, lid, outline=shade(lid, 0.7))
    p.text(brand, 32, 34, WHITE if sum(body) < 450 else INK, 0.9, bold=True)
    if words:
        p.text(words, 32, 41, WHITE if sum(body) < 450 else INK, 0.55)
    if fruit:
        p.ell(27, 46, 37, 54, fruit)
    return p.done()


def jar(contents, lid, label, brand, words=""):
    p = Pen()
    p.cyl(14, 18, 50, 60, contents, r=6)
    p.cyl(16, 8, 48, 18, lid, r=2)
    p.rect(14, 28, 50, 48, label)
    p.text(brand, 32, 32, WHITE if sum(label) < 450 else INK, 0.85, bold=True)
    if words:
        p.text(words, 32, 39, WHITE if sum(label) < 450 else INK, 0.55)
    p.shine(18, 20, 21, 56, 100)
    return p.done()


def carton(col, top, brand, words="", fruit=None):
    """A drinks carton with a gable top."""
    p = Pen()
    p.poly([(16, 14), (28, 4), (40, 14)], shade(col, 1.1))
    p.poly([(40, 14), (28, 4), (34, 2), (48, 10)], shade(col, 0.85))
    p.vgrad(16, 14, 40, 60, shade(col, 1.08), shade(col, 0.9))
    p.poly([(40, 14), (48, 10), (48, 56), (40, 60)], shade(col, 0.7))
    p.cyl(30, 5, 36, 9, top)
    p.rect(16, 22, 40, 30, WHITE)
    p.text(brand, 28, 24, INK, 0.7, bold=True)
    if fruit:
        fruit(p, 28, 42)
    if words:
        p.text(words, 28, 52, WHITE, 0.55)
    return p.done()


def tray(contents, label, brand, words):
    """Meat in a black tray under film, with its label."""
    p = Pen()
    p.rect(4, 22, 60, 56, (40, 40, 44), r=4)
    p.rect(7, 25, 57, 53, (60, 60, 64), r=3)
    contents(p)
    p.shine(8, 26, 56, 30, 60)
    p.rect(30, 40, 58, 54, WHITE, outline=(200, 200, 200), w=0.5)
    p.text(brand, 44, 42, label, 0.65, bold=True)
    p.text(words, 44, 48, INK, 0.5)
    return p.done()


def sausages_in(p):
    for k in range(4):
        y = 28 + k * 6
        p.rect(10, y, 50, y + 5, (196, 130, 110), r=2.5)
        p.line([(14, y + 1.5), (44, y + 1.5)], (220, 170, 150), 0.6)


def bacon_in(p):
    for k in range(3):
        y = 27 + k * 8
        p.rect(9, y, 52, y + 7, (210, 110, 110), r=2)
        p.rect(9, y + 4, 52, y + 6, (246, 220, 210), r=1)


def chicken_in(p):
    for x in (9, 30):
        p.ell(x, 28, x + 22, 50, (240, 200, 180), outline=(220, 170, 150), w=0.6)


def mince_in(p):
    p.rect(10, 28, 52, 50, (170, 50, 50), r=4)
    for k in range(40):
        x, y = 11 + (k * 37) % 40, 29 + (k * 17) % 20
        p.rect(x, y, x + 1.5, y + 1.5, (210, 110, 110))


def ham_in(p):
    for x in (10, 22, 34):
        p.ell(x, 28, x + 16, 46, (236, 160, 170), outline=(250, 220, 220), w=1)

# ------------------------------------------------------------------ pictures on packs

def beans_pic(p, cx, cy):
    p.ell(cx - 9, cy - 6, cx + 9, cy + 6, WHITE)
    for dx, dy in ((-4, -2), (0, -3), (4, -1), (-2, 2), (3, 2), (-5, 1)):
        p.ell(cx + dx - 2, cy + dy - 1.5, cx + dx + 2, cy + dy + 1.5, (226, 110, 40))


def soup_pic(p, cx, cy):
    p.ell(cx - 9, cy - 6, cx + 9, cy + 6, WHITE)
    p.ell(cx - 7, cy - 4, cx + 7, cy + 4, (210, 60, 40))


def tomato_pic(p, cx, cy):
    p.ell(cx - 7, cy - 6, cx + 7, cy + 6, (220, 40, 40))
    p.rect(cx - 2, cy - 7, cx + 2, cy - 5, (60, 140, 40))


def bowl_pic(flakes):
    def art(p, cx, cy):
        p.ell(cx - 11, cy - 2, cx + 11, cy + 4, flakes)
        for dx in (-7, -3, 1, 5):
            p.ell(cx + dx - 2, cy - 4, cx + dx + 3, cy, shade(flakes, 1.1))
        p.poly([(cx - 12, cy + 1), (cx + 12, cy + 1), (cx + 8, cy + 10), (cx - 8, cy + 10)], WHITE)
    return art


def circle_pic(col, ring=None):
    def art(p, cx, cy):
        if ring:
            p.ell(cx - 10, cy - 10, cx + 10, cy + 10, ring)
        p.ell(cx - 8, cy - 8, cx + 8, cy + 8, col)
    return art


def pizza_pic(p, cx, cy):
    p.ell(cx - 12, cy - 12, cx + 12, cy + 12, (226, 170, 90))
    p.ell(cx - 10, cy - 10, cx + 10, cy + 10, (220, 60, 40))
    for dx, dy in ((-5, -4), (4, -5), (-2, 4), (6, 3), (0, 0)):
        p.ell(cx + dx - 2, cy + dy - 2, cx + dx + 2, cy + dy + 2, (250, 220, 120))


def fish_fingers_pic(p, cx, cy):
    for k in range(3):
        p.rect(cx - 12, cy - 6 + k * 5, cx + 12, cy - 2 + k * 5, (226, 150, 60), r=1.5)


def peas_pic(p, cx, cy):
    for k in range(10):
        x, y = cx - 10 + (k % 5) * 5, cy - 3 + (k // 5) * 5
        p.ell(x, y, x + 4.5, y + 4.5, (90, 170, 50), outline=(60, 120, 30), w=0.4)


def fusilli_pic(p, cx, cy):
    for k in range(6):
        x, y = cx - 12 + (k % 3) * 9, cy - 6 + (k // 3) * 7
        p.line([(x, y), (x + 2, y + 3), (x + 4, y), (x + 6, y + 3), (x + 8, y)], (236, 196, 100), 2)


def rice_pic(p, cx, cy):
    p.ell(cx - 12, cy - 6, cx + 12, cy + 8, WHITE)
    for k in range(18):
        x, y = cx - 9 + (k * 7) % 18, cy - 3 + (k * 5) % 9
        p.ell(x, y, x + 3, y + 1.5, (236, 230, 210), outline=(200, 196, 180), w=0.3)


def chips_pic(p, cx, cy):
    for k in range(7):
        x = cx - 12 + k * 4
        p.rect(x, cy - 7 + (k % 3) * 2, x + 3, cy + 7, (240, 200, 90), outline=(200, 150, 50), w=0.4)


def orange_pic(p, cx, cy):
    p.ell(cx - 7, cy - 7, cx + 7, cy + 7, (250, 150, 30))
    p.rect(cx, cy - 9, cx + 4, cy - 6, (60, 140, 40))


def cookie_pic(p, cx, cy):
    p.ell(cx - 10, cy - 8, cx + 10, cy + 8, (200, 150, 90))
    for dx, dy in ((-4, -3), (3, -2), (-1, 3), (5, 3)):
        p.ell(cx + dx - 1.5, cy + dy - 1.5, cx + dx + 1.5, cy + dy + 1.5, (80, 50, 30))


def jelly_pic(p, cx, cy):
    for k, col in enumerate(((230, 40, 60), (250, 200, 40), (60, 180, 70), (250, 120, 30), (150, 60, 200))):
        x = cx - 12 + k * 5
        p.ell(x, cy - 4 + (k % 2) * 3, x + 5, cy + 2 + (k % 2) * 3, col)


def popcorn_pic(p, cx, cy):
    for k in range(8):
        x, y = cx - 10 + (k % 4) * 6, cy - 5 + (k // 4) * 6
        p.ell(x, y, x + 6, y + 5, (252, 246, 220), outline=(220, 200, 150), w=0.4)

# ------------------------------------------------------------------ fresh food, bakery, and the like

def apples():
    p = Pen()
    for x, y, col in ((8, 22, (200, 30, 30)), (30, 20, (120, 190, 50)), (18, 34, (210, 40, 40))):
        p.ell(x, y, x + 24, y + 24, col, outline=shade(col, 0.6))
        p.shine(x + 5, y + 5, x + 11, y + 11, 110)
        p.line([(x + 12, y + 2), (x + 13, y - 3)], (90, 60, 30), 1.2)
        p.ell(x + 13, y - 4, x + 20, y + 1, (70, 150, 40))
    return p.done()


def bananas():
    p = Pen()
    for k, (cy, col) in enumerate(((10, (236, 196, 40)), (17, (248, 212, 50)), (24, (252, 224, 80)))):
        outer, inner = [], []
        for i in range(29):
            t = math.radians(15 + i * 5)
            thick = 2 + 8 * math.sin((i / 28) * math.pi)
            outer.append((32 + 27 * math.cos(t), cy + 24 * math.sin(t)))
            inner.append((32 + (27 - thick) * math.cos(t), cy + (24 - thick) * math.sin(t)))
        p.poly(outer + inner[::-1], col, outline=shade(col, 0.7), w=0.7)
        p.line(inner[6:22], shade(col, 1.1), 0.8)
        p.rect(4, cy + 4, 9, cy + 9, (90, 70, 30), r=1)
    p.rect(53, 14, 61, 30, (110, 120, 40), r=2)
    return p.done()


def oranges():
    p = Pen()
    for x, y in ((8, 26), (28, 24), (18, 38), (36, 38)):
        p.ell(x, y, x + 20, y + 20, (250, 150, 30), outline=(210, 110, 20))
        p.shine(x + 4, y + 4, x + 9, y + 9, 100)
    return p.done()


def grapes(col=(90, 40, 110)):
    p = Pen()
    p.line([(32, 4), (32, 14)], (100, 70, 30), 1.5)
    for row, n in enumerate((6, 5, 4, 3, 2)):
        for k in range(n):
            x = 32 - n * 4.5 + k * 9
            y = 12 + row * 8.5
            p.ell(x, y, x + 9.5, y + 9.5, col, outline=shade(col, 0.6), w=0.5)
            p.shine(x + 2, y + 2, x + 4, y + 4, 90)
    return p.done()


def carrots():
    p = Pen()
    for k in range(4):
        x = 10 + k * 11
        p.poly([(x, 20), (x + 9, 20), (x + 5, 60)], (240, 120, 30))
        for y in (28, 36, 44):
            p.line([(x + 1, y), (x + 6, y)], (200, 90, 20), 0.5)
        p.poly([(x + 2, 20), (x - 1, 6), (x + 4, 18), (x + 6, 4), (x + 7, 20)], (60, 150, 50))
    return p.done()


def potatoes():
    p = Pen()
    p.rect(8, 10, 56, 60, (200, 180, 140), r=5)
    p.rect(14, 26, 50, 50, (250, 246, 236), r=3)
    for x, y in ((18, 30), (32, 32), (24, 40)):
        p.ell(x, y, x + 14, y + 10, (210, 170, 110), outline=(170, 130, 80))
    p.text("POTATOES", 32, 14, (80, 60, 30), 0.7, bold=True)
    p.text("2.5KG", 32, 53, (80, 60, 30), 0.6)
    return p.done()


def broccoli():
    p = Pen()
    p.rect(28, 36, 36, 60, (130, 180, 90), r=2)
    for x, y, r in ((14, 14, 14), (30, 8, 14), (36, 20, 13), (20, 24, 12)):
        p.ell(x, y, x + r * 1.4, y + r * 1.3, (50, 120, 50), outline=(30, 90, 30))
    return p.done()


def tomatoes():
    p = Pen()
    p.line([(8, 16), (56, 16)], (60, 120, 40), 1.6)
    for x, y in ((6, 22), (22, 26), (38, 22), (14, 38), (32, 40)):
        p.ell(x, y, x + 18, y + 18, (220, 40, 40), outline=(160, 20, 20))
        p.shine(x + 4, y + 4, x + 8, y + 8, 110)
        p.line([(x + 9, y), (x + 9, 16)], (60, 120, 40), 1)
    return p.done()


def onions():
    p = Pen()
    for x, y in ((8, 26), (28, 22), (18, 38)):
        p.poly([(x + 10, y - 4), (x + 12, y)], (150, 100, 40))
        p.ell(x, y, x + 22, y + 22, (200, 140, 70), outline=(150, 100, 40))
        p.line([(x + 11, y + 1), (x + 11, y + 21)], (170, 115, 55), 0.6)
    return p.done()


def lettuce():
    p = Pen()
    for x, y in ((6, 20), (30, 20), (8, 34), (28, 34)):
        p.ell(x, y, x + 28, y + 26, (80, 160, 60), outline=(50, 120, 40))
    p.ell(14, 14, 50, 50, (200, 236, 160), outline=(130, 190, 90))
    p.d.arc([18 * SS, 18 * SS, 46 * SS, 46 * SS], 200, 340, fill=_c((150, 200, 110)), width=SS)
    p.d.arc([22 * SS, 24 * SS, 42 * SS, 44 * SS], 200, 340, fill=_c((150, 200, 110)), width=SS)
    p.shine(20, 20, 30, 26, 90)
    return p.done()


def eggs():
    p = Pen()
    p.rect(4, 30, 60, 54, (170, 160, 140), r=3)
    for x in (6, 24, 42):
        p.ell(x, 16, x + 16, 38, (236, 206, 160), outline=(200, 170, 120))
        p.shine(x + 3, 19, x + 7, 25, 100)
    p.rect(4, 34, 60, 44, (90, 140, 70))
    p.text("HILLFARM", 32, 36.5, WHITE, 0.75, bold=True)
    p.text("FREE RANGE 6", 32, 47, (60, 60, 50), 0.55)
    return p.done()


def cheese():
    p = Pen()
    p.poly([(6, 44), (40, 56), (58, 40), (24, 28)], (250, 200, 60))
    p.poly([(6, 44), (6, 34), (24, 18), (24, 28)], (240, 180, 40))
    p.poly([(24, 18), (58, 30), (58, 40), (24, 28)], (252, 214, 90))
    p.rect(22, 36, 52, 46, (40, 60, 120))
    p.text("MOORCROFT", 37, 38, WHITE, 0.6, bold=True)
    p.text("MATURE", 37, 43, (250, 210, 80), 0.45)
    return p.done()


def butter():
    p = Pen()
    p.poly([(6, 30), (20, 20), (60, 20), (46, 30)], (250, 236, 150))
    p.poly([(46, 30), (60, 20), (60, 40), (46, 50)], (220, 196, 90))
    p.vgrad(6, 30, 46, 50, (250, 226, 120), (220, 190, 80))
    p.rect(6, 34, 46, 44, (30, 110, 60))
    p.text("DALESIDE", 26, 36.5, WHITE, 0.7, bold=True)
    p.text("BUTTER", 26, 46.2, (60, 50, 20), 0.55)
    return p.done()


def meal_deal():
    p = Pen()
    p.poly([(4, 56), (40, 56), (4, 20)], (230, 236, 240))
    p.poly([(7, 53), (34, 53), (7, 26)], (226, 196, 140))
    p.line([(8, 46), (30, 46)], (100, 170, 60), 2)
    p.line([(8, 41), (25, 41)], (240, 200, 160), 2)
    p.cyl(42, 18, 56, 58, (200, 30, 40), r=3)
    p.ell(42, 15, 56, 21, SILVER)
    p.rect(8, 8, 40, 16, (11, 122, 69), r=2)
    p.text("MEAL DEAL", 24, 10.5, WHITE, 0.6, bold=True)
    return p.done()


def croissants():
    p = Pen()
    for y in (6, 30):
        for k, (dx, r) in enumerate(((-20, 5), (-12, 7), (0, 9), (12, 7), (20, 5))):
            cy = y + 14 + abs(dx) * 0.35
            p.ell(32 + dx - r, cy - r, 32 + dx + r, cy + r, shade((226, 160, 80), 0.9 + 0.05 * (k % 2)), outline=(170, 110, 50), w=0.6)
        p.shine(24, y + 8, 36, y + 11, 80)
    return p.done()


def doughnuts():
    p = Pen()
    for x, y in ((6, 10), (32, 10), (18, 32)):
        p.ell(x, y, x + 26, y + 22, (226, 170, 100), outline=(190, 130, 60))
        p.ell(x + 3, y + 3, x + 23, y + 17, (240, 140, 170))
        p.ell(x + 10, y + 8, x + 16, y + 13, (226, 170, 100))
    return p.done()


def crumpets():
    p = Pen()
    p.rect(6, 26, 58, 56, (246, 244, 236), r=4)
    for x in (10, 26, 42):
        p.ell(x, 30, x + 14, 44, (226, 196, 140), outline=(190, 150, 100))
        for k in range(5):
            p.ell(x + 2 + (k * 3) % 10, 33 + (k * 5) % 8, x + 3.5 + (k * 3) % 10, 34.5 + (k * 5) % 8, (170, 130, 80))
    p.rect(6, 46, 58, 54, (180, 40, 40))
    p.text("CRUMPETS 6", 32, 48, WHITE, 0.6, bold=True)
    return p.done()


def toilet_roll():
    p = Pen()
    p.rect(4, 10, 60, 60, (236, 240, 250), r=4)
    for x in (6, 24, 42):
        for y in (12, 34):
            p.cyl(x, y, x + 16, y + 22, (250, 250, 250))
            p.ell(x + 5, y + 1, x + 11, y + 5, (220, 220, 220))
    p.rect(4, 26, 60, 36, (60, 120, 210))
    p.text("COSY 9 ROLLS", 32, 28.5, WHITE, 0.6, bold=True)
    return p.done()


def kitchen_roll():
    p = Pen()
    for x in (8, 32):
        p.cyl(x, 8, x + 22, 60, (250, 250, 250))
        p.ell(x, 4, x + 22, 12, (240, 240, 240), outline=(200, 200, 200))
        p.ell(x + 8, 6, x + 14, 10, (200, 170, 130))
    p.rect(6, 28, 56, 38, (60, 120, 210))
    p.text("COSY KITCHEN", 31, 30.5, WHITE, 0.6, bold=True)
    return p.done()


def batteries():
    p = Pen()
    p.rect(8, 4, 56, 60, (240, 196, 40), r=3)
    p.rect(8, 4, 56, 14, (30, 30, 34), r=3)
    p.text("POWERCELL", 32, 7, (240, 196, 40), 0.7, bold=True)
    for k in range(4):
        x = 12 + k * 11
        p.cyl(x, 18, x + 9, 56, (40, 40, 44))
        p.cyl(x, 18, x + 9, 30, (210, 140, 40))
        p.rect(x + 3, 16, x + 6, 18, SILVER)
    return p.done()


def bulb():
    p = Pen()
    p.ell(14, 4, 50, 40, (252, 248, 210), outline=(210, 200, 150))
    p.shine(20, 10, 28, 20, 140)
    p.line([(26, 26), (30, 18), (34, 26), (38, 18)], (230, 160, 40), 1)
    p.cyl(22, 38, 42, 54, SILVER)
    for y in (42, 46, 50):
        p.line([(22, y), (42, y + 1)], shade(SILVER, 0.7), 0.7)
    p.rect(26, 54, 38, 60, (60, 60, 64), r=2)
    return p.done()


def bin_bags():
    p = Pen()
    p.cyl(10, 14, 54, 52, (40, 40, 46), r=4)
    p.ell(10, 10, 54, 20, (60, 60, 66), outline=(30, 30, 34))
    p.ell(26, 12, 38, 18, (20, 20, 22))
    p.rect(10, 30, 54, 40, (240, 196, 40))
    p.text("TIDY BIN BAGS", 32, 32.5, INK, 0.55, bold=True)
    return p.done()


def newspaper(masthead, col, photo=(120, 150, 180)):
    p = Pen()
    p.rect(6, 4, 56, 60, (242, 240, 232), outline=(200, 196, 186), w=0.5)
    p.rect(8, 6, 54, 14, col)
    p.text(masthead, 31, 7.5, WHITE, 0.75 if len(masthead) < 12 else 0.6, bold=True)
    p.rect(9, 17, 53, 21, INK)
    p.rect(9, 24, 31, 42, photo)
    p.ell(14, 27, 22, 35, (230, 200, 170))
    for y in range(24, 56, 3):
        p.rect(34, y, 53, y + 1, (150, 150, 150))
    for y in range(45, 56, 3):
        p.rect(9, y, 31, y + 1, (150, 150, 150))
    return p.done()


def magazine(masthead, col):
    p = Pen()
    p.vgrad(10, 2, 54, 62, mix(col, WHITE, 0.3), shade(col, 0.8))
    p.ell(18, 18, 46, 52, (238, 200, 170))
    p.ell(16, 12, 48, 34, (110, 70, 40))
    p.ell(22, 22, 42, 46, (240, 205, 175))
    p.rect(10, 2, 54, 12, WHITE)
    p.text(masthead, 32, 4.5, col, 0.85, bold=True)
    p.rect(10, 52, 30, 58, (250, 220, 40))
    p.text("NEW!", 20, 53.5, INK, 0.55, bold=True)
    return p.done()


def scratchcard():
    p = Pen()
    p.vgrad(6, 8, 58, 56, (252, 220, 70), (230, 170, 30), r=3)
    p.text("LUCKY STARS", 32, 11, (150, 40, 40), 0.65, bold=True)
    for k in range(3):
        x = 10 + k * 16
        p.rect(x, 22, x + 13, 34, (180, 184, 190), r=2)
        p.rect(x, 38, x + 13, 50, (180, 184, 190), r=2)
    p.poly([(48, 46), (50, 41), (52, 46), (57, 46), (53, 49), (55, 54), (50, 51), (45, 54), (47, 49), (43, 46)], (220, 40, 40))
    p.text("WIN 100", 18, 52, (150, 40, 40), 0.5, bold=True)
    return p.done()


def tube(col, cap, brand, words):
    p = Pen()
    p.poly([(6, 22), (46, 18), (46, 46), (6, 42)], col)
    p.vgrad(6, 22, 46, 42, shade(col, 1.08), shade(col, 0.85))
    p.cyl(46, 24, 58, 40, cap, r=2)
    p.rect(4, 20, 8, 44, shade(col, 0.8))
    p.text(brand, 27, 26, WHITE if sum(col) < 450 else (30, 80, 170), 0.75, bold=True)
    p.text(words, 27, 33, WHITE if sum(col) < 450 else INK, 0.5)
    return p.done()


def toothbrush():
    p = Pen()
    p.line([(10, 54), (50, 14)], (60, 140, 220), 5)
    p.line([(46, 18), (54, 10)], WHITE, 6)
    for k in range(4):
        p.line([(44 + k * 2.5, 8 + k * 2.5), (48 + k * 2.5, 4 + k * 2.5)], (250, 250, 250), 1.2)
    p.line([(16, 48), (30, 34)], (250, 250, 250), 1.5)
    return p.done()


def plain_bottle(col, cap, brand, words, tall=True):
    p = Pen()
    y0 = 14 if tall else 24
    p.cyl(16, y0, 48, 60, col, r=8)
    p.cyl(24, y0 - 10, 40, y0 + 2, cap, r=2)
    p.rect(16, y0 + 12, 48, y0 + 26, WHITE)
    p.text(brand, 32, y0 + 14.5, shade(col, 0.6), 0.75, bold=True)
    p.text(words, 32, y0 + 20.5, INK, 0.5)
    p.shine(20, y0 + 4, 23, 54, 90)
    return p.done()


def wine():
    p = Pen()
    p.cyl(20, 22, 44, 62, (70, 20, 40), r=5)
    p.cyl(27, 2, 37, 24, (70, 20, 40), r=2)
    p.cyl(27, 2, 37, 10, (150, 30, 40), r=1)
    p.rect(20, 36, 44, 54, (244, 236, 214))
    p.text("VALLEY", 32, 39, (120, 30, 40), 0.7, bold=True)
    p.text("RIDGE RED", 32, 46, INK, 0.5)
    p.shine(23, 24, 26, 58, 70)
    return p.done()


def lager():
    p = Pen()
    for x in (8, 30):
        p.cyl(x, 8, x + 22, 56, (200, 170, 60), r=2)
        p.ell(x, 4, x + 22, 12, SILVER)
    p.rect(4, 28, 60, 60, (30, 70, 140), r=2)
    p.text("HARBOUR", 32, 34, (240, 210, 90), 0.9, bold=True)
    p.text("LAGER 4 X 440ML", 32, 44, WHITE, 0.5)
    p.text("4.0%", 32, 52, WHITE, 0.5)
    return p.done()

# ------------------------------------------------------------------ made while you wait

def fish_and_chips():
    p = Pen()
    p.poly([(2, 30), (32, 14), (62, 30), (32, 60)], (250, 248, 240))
    p.poly([(2, 30), (32, 14), (32, 24), (12, 34)], (230, 228, 220))
    p.ell(10, 24, 46, 40, (220, 150, 50), outline=(180, 110, 30))
    for k in range(5):
        p.ell(14 + k * 6, 27, 18 + k * 6, 31, (240, 180, 80))
    for k in range(7):
        x = 22 + k * 4
        p.rect(x, 36 + (k % 3) * 2, x + 3, 52 - (k % 2) * 3, (244, 210, 100), outline=(200, 150, 50), w=0.4)
    p.ell(46, 38, 52, 44, (250, 240, 120))                            # a lemon wedge
    p.line([(48, 20), (56, 50)], (190, 150, 90), 1.5)                   # the wooden fork
    return p.done()


def chips():
    p = Pen()
    for k in range(9):
        x = 14 + k * 4
        p.rect(x, 6 + (k * 7) % 10, x + 3.5, 34, (244, 208, 100), outline=(200, 150, 50), w=0.4)
    p.poly([(10, 28), (54, 28), (48, 60), (16, 60)], (250, 250, 246))
    p.rect(10, 28, 54, 32, (220, 40, 40))
    p.text("CHIPS", 32, 42, (220, 40, 40), 1.0, bold=True)
    return p.done()


def battered_sausage():
    p = Pen()
    p.poly([(4, 40), (32, 24), (60, 40), (32, 58)], (250, 248, 240))
    p.rect(8, 30, 56, 42, (220, 150, 50), r=6)
    for k in range(6):
        p.ell(12 + k * 7, 32, 16 + k * 7, 36, (240, 180, 80))
    return p.done()


def pot(col, lid=None):
    p = Pen()
    p.poly([(12, 24), (52, 24), (48, 58), (16, 58)], (248, 248, 244))
    p.cyl(13, 24, 51, 58, (248, 248, 244))
    p.ell(12, 18, 52, 30, col, outline=shade(col, 0.7))
    p.shine(18, 20, 30, 24, 80)
    return p.done()


def mug():
    p = Pen()
    p.d.arc([44 * SS, 26 * SS, 60 * SS, 46 * SS], -90, 90, fill=_c((220, 220, 216)), width=4 * SS)
    p.cyl(10, 18, 48, 58, (240, 240, 236), r=4)
    p.ell(10, 14, 48, 24, (150, 100, 60), outline=(200, 200, 196))
    p.rect(18, 34, 40, 44, (11, 122, 69), r=2)
    for x in (20, 28, 36):
        p.line([(x, 12), (x - 2, 6), (x + 1, 1)], (230, 230, 230), 1.2)
    return p.done()


def coffee_cup():
    p = Pen()
    p.poly([(16, 14), (48, 14), (44, 60), (20, 60)], WHITE)
    p.cyl(17, 14, 47, 60, (250, 250, 248))
    p.rect(17, 28, 47, 44, (150, 100, 60))
    p.ell(26, 30, 38, 42, (240, 230, 210))
    p.text("BREW", 32, 34, (120, 80, 40), 0.55, bold=True)
    p.rect(14, 8, 50, 15, (40, 40, 44), r=2)
    p.rect(20, 4, 44, 9, (40, 40, 44), r=2)
    return p.done()


def bacon_roll():
    p = Pen()
    p.ell(6, 30, 58, 58, (220, 170, 100), outline=(170, 120, 60))
    p.rect(6, 34, 58, 42, (200, 90, 80), r=3)
    p.rect(8, 38, 56, 40, (246, 220, 210))
    p.line([(10, 36), (50, 36)], (200, 40, 30), 1.2)
    p.ell(6, 14, 58, 40, (226, 176, 104), outline=(170, 120, 60))
    p.shine(18, 18, 36, 24, 90)
    for x in (20, 28, 36):
        p.ell(x, 20, x + 2, 22, (250, 240, 210))
    return p.done()


def cake_slice():
    p = Pen()
    p.poly([(4, 50), (44, 60), (60, 34), (24, 24)], (236, 200, 150))
    p.poly([(4, 50), (4, 40), (24, 14), (24, 24)], (240, 210, 160))
    for y, col in ((30, (250, 248, 240)), (40, (220, 60, 80))):
        p.line([(6, y + 10), (40, y + 18)], col, 2)
    p.poly([(4, 40), (24, 14), (60, 24), (44, 50)], (250, 248, 240))
    p.ell(30, 18, 40, 28, (220, 30, 50))
    return p.done()


def toastie():
    p = Pen()
    for dx in (0, 10):
        p.poly([(6 + dx, 56), (46 + dx, 56), (6 + dx, 16)], (200, 140, 70), outline=(150, 100, 40))
        p.line([(10 + dx, 50), (32 + dx, 50)], (250, 210, 60), 2)
        for k in range(3):
            p.line([(12 + dx + k * 6, 54), (12 + dx + k * 6 + 4, 50)], (160, 100, 40), 1)
    return p.done()


def pint():
    p = Pen()
    p.poly([(14, 10), (50, 10), (46, 62), (18, 62)], (190, 120, 40))
    p.vgrad(15, 16, 49, 62, (220, 150, 60), (160, 90, 30))
    p.rect(13, 6, 51, 16, (250, 246, 230), r=3)
    p.shine(18, 18, 22, 58, 80)
    p.line([(14, 10), (18, 62), (46, 62), (50, 10)], (220, 220, 220), 0.6)
    return p.done()


def pie_and_mash():
    p = Pen()
    p.ell(2, 14, 62, 60, (250, 250, 250), outline=(200, 200, 200))
    p.ell(8, 22, 34, 48, (200, 140, 70), outline=(160, 100, 40))
    for x in (14, 20, 26):
        p.line([(x, 28), (x + 2, 42)], (230, 180, 110), 1)
    p.ell(30, 24, 54, 46, (250, 246, 230))
    p.ell(16, 38, 50, 54, (120, 70, 40))
    p.ell(40, 34, 56, 46, (90, 170, 50))
    return p.done()


def kebab():
    p = Pen()
    p.poly([(14, 4), (50, 4), (40, 60), (24, 60)], (236, 214, 170))
    p.poly([(16, 6), (48, 6), (44, 22), (20, 22)], (150, 90, 60))
    for k in range(5):
        p.ell(20 + k * 5, 8, 25 + k * 5, 13, (120, 180, 70))
        p.ell(22 + k * 5, 14, 26 + k * 5, 18, (220, 50, 50))
    p.poly([(14, 24), (50, 24), (40, 60), (24, 60)], (250, 250, 246))
    p.text("KEBAB", 32, 36, (200, 40, 40), 0.8, bold=True)
    return p.done()


def pizza():
    p = Pen()
    p.ell(4, 4, 60, 60, (226, 170, 90), outline=(190, 130, 60))
    p.ell(9, 9, 55, 55, (215, 60, 40))
    for dx, dy in ((-10, -10), (9, -12), (-4, 9), (12, 6), (0, -2), (-14, 4), (6, 16)):
        p.ell(32 + dx - 4, 32 + dy - 4, 32 + dx + 4, 32 + dy + 4, (250, 220, 120))
        p.ell(32 + dx - 1.5, 32 + dy + 4, 32 + dx + 1.5, 32 + dy + 7, (120, 30, 30))
    for a in (0, 60, 120):
        r = math.radians(a)
        p.line([(32 - math.cos(r) * 26, 32 - math.sin(r) * 26), (32 + math.cos(r) * 26, 32 + math.sin(r) * 26)], (180, 120, 60), 0.6)
    return p.done()

# ------------------------------------------------------------------ the list

# shelf, then (id, title, pence, food, saturation, flags, icon). flags: d drink, a age check, l loose (looked up at a self-checkout)
C, GR, F, FZ, S, D, H, NW, PH, CO, SV = "CHILLED", "GROCERIES", "FRESH", "FROZEN", "SNACKS", "DRINKS", "HOUSEHOLD", "NEWS", "PHARMACY", "COUNTER", "SERVICE"
DALE, HEARTH, BRIGHT, FRESH = (30, 110, 60), (170, 50, 40), (30, 150, 160), (11, 122, 69)

PRODUCTS = [
    # the fridge
    (C, "milk", "Daleside Semi-Skimmed Milk, 2 pints", 145, 2, 0.2, "d", lambda: milk((40, 160, 70), DALE, "DALESIDE", "SEMI SKIMMED")),
    (C, "whole_milk", "Daleside Whole Milk, 1 pint", 95, 2, 0.3, "d", lambda: milk((40, 90, 200), (40, 90, 200), "DALESIDE", "WHOLE MILK")),
    (C, "butter", "Daleside Butter", 210, 2, 0.3, "", butter),
    (C, "cheese", "Moorcroft Mature Cheddar", 280, 4, 0.6, "", cheese),
    (C, "yoghurt", "Daleside Strawberry Yoghurt (4)", 160, 3, 0.3, "", lambda: tub((250, 236, 240), (230, 60, 90), "DALESIDE", "STRAWBERRY", (220, 40, 60))),
    (C, "orange_juice", "Sunvale Orange Juice, 1 litre", 180, 2, 0.2, "d", lambda: carton((250, 150, 30), (250, 200, 40), "SUNVALE", "100% ORANGE", orange_pic)),
    (C, "meal_deal", "Freshways Meal Deal", 350, 7, 0.8, "", meal_deal),
    (C, "sausages", "Butcher's Pride Pork Sausages (8)", 299, 6, 0.6, "", lambda: tray(sausages_in, (150, 30, 40), "BUTCHER'S", "PORK SAUSAGES")),
    (C, "bacon", "Butcher's Pride Back Bacon", 275, 5, 0.6, "", lambda: tray(bacon_in, (150, 30, 40), "BUTCHER'S", "BACK BACON")),
    (C, "chicken", "Hillfarm Chicken Breasts", 425, 7, 0.8, "", lambda: tray(chicken_in, (60, 120, 50), "HILLFARM", "CHICKEN")),
    (C, "mince", "Hillfarm Beef Mince", 350, 6, 0.7, "", lambda: tray(mince_in, (60, 120, 50), "HILLFARM", "BEEF MINCE")),
    (C, "ham", "Butcher's Pride Cooked Ham", 200, 3, 0.4, "", lambda: tray(ham_in, (150, 30, 40), "BUTCHER'S", "COOKED HAM")),
    # bread, tins, the cupboard
    (GR, "bread", "Hearthstone Sliced White Bread", 110, 5, 0.6, "", lambda: loaf(HEARTH, "HEARTHSTONE", "SLICED WHITE")),
    (GR, "wholemeal", "Hearthstone Wholemeal Bread", 125, 5, 0.7, "", lambda: loaf((120, 80, 40), "HEARTHSTONE", "WHOLEMEAL", (170, 120, 70))),
    (GR, "eggs", "Hillfarm Free Range Eggs (6)", 220, 4, 0.5, "", eggs),
    (GR, "beans", "Brightway Baked Beans", 85, 4, 0.5, "", lambda: tin(BRIGHT, (30, 100, 110), "BRIGHTWAY", beans_pic, "BAKED BEANS")),
    (GR, "soup", "Brightway Tomato Soup", 120, 4, 0.4, "", lambda: tin((200, 40, 40), (240, 220, 200), "BRIGHTWAY", soup_pic, "TOMATO SOUP")),
    (GR, "chopped_tomatoes", "Freshways Chopped Tomatoes", 55, 2, 0.2, "", lambda: tin((240, 240, 236), FRESH, "FRESHWAYS", tomato_pic, "CHOPPED")),
    (GR, "cereal", "Sunrise Corn Flakes", 250, 4, 0.4, "", lambda: box((250, 200, 40), (210, 160, 30), "SUNRISE", bowl_pic((240, 190, 80)), "CORN FLAKES", band=(220, 40, 40))),
    (GR, "porridge", "Highland Porridge Oats", 160, 4, 0.6, "", lambda: box((70, 110, 170), (50, 80, 130), "HIGHLAND", bowl_pic((236, 220, 180)), "PORRIDGE OATS")),
    (GR, "tea_bags", "Brew Up Tea Bags (80)", 320, 0, 0.0, "", lambda: box((150, 30, 40), (110, 20, 30), "BREW UP", circle_pic((150, 100, 60), WHITE), "80 TEA BAGS", tall=False)),
    (GR, "coffee_jar", "Roastery Instant Coffee", 450, 0, 0.0, "", lambda: jar((70, 40, 20), (180, 40, 40), (30, 30, 34), "ROASTERY", "INSTANT")),
    (GR, "pasta", "Freshways Fusilli", 100, 3, 0.3, "", lambda: packet((236, 236, 230), FRESH, "FRESHWAYS", "FUSILLI 500G", fusilli_pic)),
    (GR, "rice", "Freshways Basmati Rice", 150, 3, 0.3, "", lambda: packet((236, 236, 230), (210, 160, 40), "FRESHWAYS", "BASMATI 1KG", rice_pic)),
    (GR, "jam", "Orchard Strawberry Jam", 175, 2, 0.2, "", lambda: jar((180, 20, 40), (240, 240, 240), WHITE, "ORCHARD", "STRAWBERRY")),
    (GR, "peanut_butter", "Nutty's Smooth Peanut Butter", 225, 3, 0.5, "", lambda: jar((190, 130, 60), (220, 40, 40), (250, 220, 60), "NUTTY'S", "SMOOTH")),
    (GR, "ketchup", "Brightway Tomato Ketchup", 199, 1, 0.1, "", lambda: pet((200, 20, 20), (240, 240, 240), WHITE, "BRIGHTWAY", "KETCHUP", clear=False)),
    (GR, "sugar", "Sweetfield Granulated Sugar, 1kg", 110, 1, 0.0, "", lambda: box((250, 250, 250), (220, 220, 220), "SWEETFIELD", circle_pic((250, 250, 250), (60, 120, 210)), "SUGAR 1KG", tall=False, band=(60, 120, 210))),
    (GR, "flour", "Millstone Plain Flour, 1.5kg", 95, 1, 0.0, "", lambda: box((240, 236, 220), (210, 200, 180), "MILLSTONE", circle_pic((250, 246, 230), (60, 140, 70)), "PLAIN FLOUR", band=(60, 140, 70))),
    (GR, "crumpets", "Hearthstone Crumpets (6)", 99, 3, 0.3, "", crumpets),
    (GR, "croissants", "Hearthstone Croissants (4)", 175, 4, 0.4, "l", croissants),
    (GR, "doughnuts", "Hearthstone Jam Doughnuts (5)", 120, 4, 0.3, "l", doughnuts),
    # fruit and veg, loose
    (F, "apples", "Apples (6)", 150, 4, 0.3, "l", apples),
    (F, "bananas", "Bananas (5)", 110, 4, 0.3, "l", bananas),
    (F, "oranges", "Easy Peeler Oranges (6)", 175, 4, 0.3, "l", oranges),
    (F, "grapes", "Red Seedless Grapes", 199, 3, 0.3, "l", grapes),
    (F, "carrots", "Carrots, 1kg", 95, 3, 0.3, "l", carrots),
    (F, "potatoes", "Potatoes, 2.5kg", 175, 4, 0.4, "l", potatoes),
    (F, "broccoli", "Broccoli", 75, 2, 0.3, "l", broccoli),
    (F, "tomatoes", "Vine Tomatoes", 150, 2, 0.2, "l", tomatoes),
    (F, "onions", "Brown Onions (3)", 99, 1, 0.1, "l", onions),
    (F, "lettuce", "Iceberg Lettuce", 70, 1, 0.1, "l", lettuce),
    # the freezer
    (FZ, "frozen_pizza", "Bellissimo Margherita Pizza", 300, 6, 0.6, "", lambda: box((30, 90, 170), (20, 60, 120), "BELLISSIMO", pizza_pic, "MARGHERITA", tall=False)),
    (FZ, "ice_cream", "Daleside Vanilla Ice Cream", 350, 3, 0.2, "", lambda: tub((250, 246, 230), (60, 120, 210), "DALESIDE", "VANILLA")),
    (FZ, "fish_fingers", "Captain's Catch Fish Fingers (10)", 250, 6, 0.6, "", lambda: box((30, 90, 170), (20, 60, 120), "CAPTAIN'S", fish_fingers_pic, "FISH FINGERS", tall=False)),
    (FZ, "frozen_peas", "Garden Gold Peas", 125, 2, 0.3, "", lambda: packet((60, 140, 60), (250, 230, 120), "GARDEN GOLD", "GARDEN PEAS", peas_pic)),
    (FZ, "oven_chips", "Golden Crisp Oven Chips", 199, 5, 0.5, "", lambda: packet((220, 40, 40), (250, 210, 60), "GOLDEN", "OVEN CHIPS", chips_pic)),
    # sweets and crisps
    (S, "crisps", "Crunch Co Ready Salted", 125, 2, 0.2, "", lambda: packet((210, 30, 40), (250, 214, 60), "CRUNCH CO", "READY SALTED", crisps_in)),
    (S, "crisps_cheese", "Crunch Co Cheese & Onion", 125, 2, 0.2, "", lambda: packet((40, 90, 200), (250, 214, 60), "CRUNCH CO", "CHEESE & ONION", crisps_in)),
    (S, "crisps_vinegar", "Crunch Co Salt & Vinegar", 125, 2, 0.2, "", lambda: packet((40, 150, 70), (250, 214, 60), "CRUNCH CO", "SALT & VINEGAR", crisps_in)),
    (S, "chocolate", "Velvet Milk Chocolate", 110, 3, 0.3, "", lambda: bar((80, 30, 110), (250, 214, 60), "VELVET", "MILK CHOCOLATE")),
    (S, "chocolate_caramel", "Velvet Caramel", 120, 3, 0.3, "", lambda: bar((160, 80, 30), (250, 230, 180), "VELVET", "CARAMEL")),
    (S, "sweets", "Wiggles Fruit Sweets", 100, 1, 0.1, "", lambda: packet((240, 120, 200), (250, 240, 120), "WIGGLES", "FRUIT SWEETS", jelly_pic)),
    (S, "biscuits", "Teatime Digestives", 129, 3, 0.3, "", lambda: packet((40, 60, 140), (240, 200, 120), "TEATIME", "DIGESTIVES", cookie_pic)),
    (S, "cookies", "Teatime Chocolate Chip Cookies", 150, 3, 0.3, "", lambda: box((120, 70, 40), (90, 50, 30), "TEATIME", cookie_pic, "CHOC CHIP", tall=False)),
    (S, "popcorn", "Poppers Sweet Popcorn", 150, 2, 0.2, "", lambda: packet((250, 200, 40), (220, 40, 40), "POPPERS", "SWEET POPCORN", popcorn_pic)),
    # drinks
    (D, "cola", "Fizz Cola, can", 120, 1, 0.1, "d", lambda: can((200, 20, 30), (250, 250, 250), "FIZZ", "COLA")),
    (D, "cola_bottle", "Fizz Cola, 2 litres", 220, 1, 0.1, "d", lambda: pet((60, 30, 20), (200, 20, 30), (200, 20, 30), "FIZZ", "COLA 2L")),
    (D, "lemonade", "Sparkle Lemonade, 2 litres", 120, 1, 0.1, "d", lambda: pet((230, 240, 220), (40, 160, 70), (40, 160, 70), "SPARKLE", "LEMONADE")),
    (D, "orange_can", "Zesto Orange, can", 99, 1, 0.1, "d", lambda: can((250, 140, 20), (250, 220, 60), "ZESTO", "ORANGE")),
    (D, "water", "Glenbrook Still Water", 90, 1, 0.1, "d", lambda: pet((190, 220, 250), (40, 90, 200), (40, 90, 200), "GLENBROOK", "STILL WATER")),
    (D, "energy_drink", "Volt Energy Drink", 180, 1, 0.1, "da", lambda: can((24, 24, 26), (120, 220, 60), "VOLT", "ENERGY")),
    (D, "lager", "Harbour Lager (4 x 440ml)", 450, 1, 0.1, "da", lager),
    (D, "wine", "Valley Ridge Red Wine", 650, 1, 0.1, "da", wine),
    # the house
    (H, "toilet_roll", "Cosy Toilet Roll (9)", 450, 0, 0.0, "", toilet_roll),
    (H, "kitchen_roll", "Cosy Kitchen Roll (2)", 250, 0, 0.0, "", kitchen_roll),
    (H, "washing_up", "Sparkle Washing-Up Liquid", 130, 0, 0.0, "", lambda: plain_bottle((60, 180, 70), (240, 240, 240), "SPARKLE", "WASHING UP")),
    (H, "laundry", "Fresh Linen Laundry Pods", 600, 0, 0.0, "", lambda: tub((90, 150, 230), (250, 250, 250), "FRESH LINEN", "LAUNDRY PODS")),
    (H, "bin_bags", "Tidy Bin Bags (20)", 175, 0, 0.0, "", bin_bags),
    (H, "batteries", "PowerCell AA Batteries (4)", 399, 0, 0.0, "", batteries),
    (H, "light_bulb", "Bright LED Light Bulb", 250, 0, 0.0, "", bulb),
    # the newsagent's
    (NW, "newspaper", "The Daily Chronicle", 180, 0, 0.0, "", lambda: newspaper("CHRONICLE", (30, 30, 34))),
    (NW, "local_paper", "The Evening Post", 120, 0, 0.0, "", lambda: newspaper("EVENING POST", (40, 80, 160))),
    (NW, "magazine", "Weekend Magazine", 350, 0, 0.0, "", lambda: magazine("WEEKEND", (220, 40, 90))),
    (NW, "puzzle_book", "Puzzler Crosswords", 299, 0, 0.0, "", lambda: magazine("PUZZLER", (30, 130, 200))),
    (NW, "scratchcard", "Lucky Stars Scratchcard", 200, 0, 0.0, "", scratchcard),
    # the chemist
    (PH, "paracetamol", "Paracetamol (16)", 75, 0, 0.0, "a", lambda: box((250, 250, 250), (220, 220, 220), "PARACETAMOL", circle_pic((220, 40, 40)), "500MG 16 TABS", tall=False, band=(220, 40, 40))),
    (PH, "ibuprofen", "Ibuprofen (16)", 99, 0, 0.0, "a", lambda: box((250, 250, 250), (220, 220, 220), "IBUPROFEN", circle_pic((40, 90, 200)), "200MG 16 TABS", tall=False, band=(240, 140, 30))),
    (PH, "plasters", "Patch-It Plasters", 220, 0, 0.0, "", lambda: box((240, 214, 180), (210, 180, 150), "PATCH-IT", circle_pic((236, 200, 160), (220, 40, 40)), "PLASTERS", tall=False, band=(220, 40, 40))),
    (PH, "toothpaste", "Brightsmile Toothpaste", 180, 0, 0.0, "", lambda: tube((250, 250, 250), (40, 90, 200), "BRIGHTSMILE", "FRESH MINT")),
    (PH, "toothbrush", "Brightsmile Toothbrush", 150, 0, 0.0, "", toothbrush),
    (PH, "shampoo", "Silken Shampoo", 300, 0, 0.0, "", lambda: plain_bottle((150, 90, 200), (250, 250, 250), "SILKEN", "SHAMPOO")),
    (PH, "sun_cream", "SunGuard Sun Cream SPF30", 450, 0, 0.0, "", lambda: plain_bottle((250, 160, 30), (250, 220, 60), "SUNGUARD", "SPF 30", tall=False)),
    # made while you wait
    (CO, "fish_and_chips", "Cod and Chips", 950, 10, 1.0, "", fish_and_chips),
    (CO, "chips", "Chips", 350, 6, 0.6, "", chips),
    (CO, "sausage", "Battered Sausage", 300, 5, 0.6, "", battered_sausage),
    (CO, "curry_sauce", "Curry Sauce", 180, 2, 0.2, "", lambda: pot((170, 110, 40))),
    (CO, "mushy_peas", "Mushy Peas", 180, 2, 0.3, "", lambda: pot((110, 170, 50))),
    (CO, "tea", "Cup of Tea", 220, 1, 0.1, "d", mug),
    (CO, "coffee", "Flat White", 310, 1, 0.1, "d", coffee_cup),
    (CO, "bacon_roll", "Bacon Roll", 380, 6, 0.7, "", bacon_roll),
    (CO, "cake", "Slice of Victoria Sponge", 320, 4, 0.4, "", cake_slice),
    (CO, "toastie", "Cheese Toastie", 450, 6, 0.6, "", toastie),
    (CO, "pint", "Pint of Bitter", 520, 1, 0.1, "da", pint),
    (CO, "pie", "Steak Pie and Mash", 1100, 10, 1.0, "", pie_and_mash),
    (CO, "kebab", "Doner Kebab", 750, 8, 0.8, "", kebab),
    (CO, "pizza", "12\" Pizza", 1000, 10, 0.9, "", pizza),
    # services: paid for, not carried
    (SV, "haircut", "Haircut", 1500, 0, 0.0, "", None),
    (SV, "beard_trim", "Beard trim", 800, 0, 0.0, "", None),
    (SV, "blow_dry", "Wash and blow-dry", 2200, 0, 0.0, "", None),
    (SV, "manicure", "Manicure", 2000, 0, 0.0, "", None),
    (SV, "gel_nails", "Gel nails", 2500, 0, 0.0, "", None),
]

JAVA = Path(__file__).resolve().parent.parent / "src/main/java/com/ptmuk/shop/Product.java"


def write_java():
    rows = []
    for shelf, pid, title, pence, food, sat, flags, _ in PRODUCTS:
        t = title.replace('"', '\\"')
        rows.append(f'    {pid.upper()}("{t}", {pence}, {food}, {sat}f, Shelf.{shelf}, {str("d" in flags).lower()}, '
                    f'{str("a" in flags).lower()}, {str("l" in flags).lower()})')
    JAVA.write_text('''package com.ptmuk.shop;

import java.util.Locale;

/**
 * What the shops sell (made-up brands): its name, the usual UK price (pence; each shop's are near
 * it, a corner shop's dearer), how filling it is if it's food or drink, the shelf it goes on (or
 * made at the counter, or a service), and whether it's a drink, needs an age check at a
 * self-checkout, or is loose (looked up there). Generated by tools/shop_goods.py with the icons:
 * do not edit by hand.
 */
public enum Product {
''' + ",\n".join(rows) + ''';

    /** Where it's kept: which shelves are stocked with it, or made at the counter, or a service. */
    public enum Shelf { CHILLED, GROCERIES, FRESH, FROZEN, SNACKS, DRINKS, HOUSEHOLD, NEWS, PHARMACY, COUNTER, SERVICE }

    public final String title;
    public final int pence, food;
    public final float saturation;
    public final Shelf shelf;
    public final boolean drink, ageCheck, loose;

    Product(String title, int pence, int food, float saturation, Shelf shelf, boolean drink, boolean ageCheck, boolean loose) {
        this.title = title;
        this.pence = pence;
        this.food = food;
        this.saturation = saturation;
        this.shelf = shelf;
        this.drink = drink;
        this.ageCheck = ageCheck;
        this.loose = loose;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean service() {
        return shelf == Shelf.SERVICE;
    }

    /** Its code to key in at a self-checkout (loose bananas and apples have their real ones). */
    public int code() {
        return this == BANANAS ? 4011 : this == APPLES ? 4131 : 3000 + ordinal() * 11;
    }

    /** Sold off a shelf (so a self-checkout can look it up), not made at a counter or a service. */
    public boolean onShelf() {
        return shelf != Shelf.COUNTER && shelf != Shelf.SERVICE;
    }
}
''')


def generate(gmod):
    global G
    G = gmod
    tex = G.ASSETS / "textures/item"
    tex.mkdir(parents=True, exist_ok=True)
    for shelf, pid, title, pence, food, sat, flags, icon in PRODUCTS:
        if icon is None:
            continue
        icon().save(tex / f"{pid}.png")
        G.write_json(G.ASSETS / f"models/item/{pid}.json",
                     {"parent": "minecraft:item/generated", "textures": {"layer0": f"{G.MOD_ID}:item/{pid}"}})
    write_java()


def lang(mid):
    return {f"item.{mid}.{pid}": title for shelf, pid, title, *_ in PRODUCTS if shelf != SV}
