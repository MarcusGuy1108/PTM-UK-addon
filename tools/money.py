"""Notes, coins and receipts: item icons (32 px, drawn big and shrunk), models and names.

Notes are made-up designs in the UK's colours (£5 teal, £10 orange, £20 purple, £50 red), not
copies of real banknotes; coins are their real shapes and metals (the 20p and 50p seven-sided,
the £1 twelve-sided, the £2 two-coloured).
"""
from PIL import Image, ImageDraw

G = None
SS = 8
NOTES = {5: (32, 150, 150), 10: (214, 120, 40), 20: (140, 80, 180), 50: (200, 40, 50)}
COPPER, SILVER, GOLD = (196, 110, 70), (200, 204, 210), (222, 186, 82)
# pence: (diameter of 32, sides (0 round), outer metal, inner metal or None)
COINS = {1: (15, 0, COPPER, None), 2: (21, 0, COPPER, None), 5: (13, 0, SILVER, None), 10: (19, 0, SILVER, None),
         20: (17, 7, SILVER, None), 50: (22, 7, SILVER, None), 100: (19, 12, GOLD, SILVER), 200: (23, 0, GOLD, SILVER)}


def _shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c) + (255,)


def note(pounds):
    S = 32 * SS
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    base = NOTES[pounds]
    # two notes fanned, the top one full
    for k, (dx, dy) in enumerate(((2, 3), (0, 0))):
        x0, y0, x1, y1 = (2 + dx) * SS, (8 + dy) * SS, (30 + dx) * SS, (23 + dy) * SS
        d.rounded_rectangle([x0, y0, x1, y1], radius=SS, fill=_shade(base, 0.75 if k == 0 else 1.0), outline=_shade(base, 0.5), width=SS // 2)
    x0, y0, x1, y1 = 2 * SS, 8 * SS, 30 * SS, 23 * SS
    d.rectangle([x0 + 2 * SS, y0 + 2 * SS, x1 - 2 * SS, y1 - 2 * SS], outline=_shade(base, 1.25), width=SS // 2)
    d.ellipse([x1 - 10 * SS, y0 + 3 * SS, x1 - 3 * SS, y1 - 3 * SS], fill=_shade(base, 1.45))      # the window
    for i in range(3):
        d.line([x0 + 3 * SS, y0 + (8 + i * 2) * SS, x0 + 15 * SS, y0 + (8 + i * 2) * SS], fill=_shade(base, 1.3), width=SS // 2)
    _digits(d, str(pounds), x0 + 3 * SS, y0 + 2.6 * SS, (255, 255, 255, 255))
    return img.resize((32, 32), Image.LANCZOS)


FONT = {"0": ["111", "101", "101", "101", "111"], "1": ["010", "110", "010", "010", "111"], "2": ["111", "001", "111", "100", "111"],
        "5": ["111", "100", "111", "001", "111"], "p": ["110", "101", "110", "100", "100"]}


def _digits(d, s, x, y, colour, px=None):
    px = px or SS
    for ch in s:
        for r, row in enumerate(FONT[ch]):
            for c, on in enumerate(row):
                if on == "1":
                    d.rectangle([x + c * px, y + r * px, x + (c + 1) * px - 1, y + (r + 1) * px - 1], fill=colour)
        x += 4 * px


def _shape(cx, cy, r, sides):
    import math
    if sides == 0:
        return None
    return [(cx + r * math.cos(2 * math.pi * i / sides - math.pi / 2), cy + r * math.sin(2 * math.pi * i / sides - math.pi / 2))
            for i in range(sides)]


def coin(pence):
    size, sides, outer, inner = COINS[pence]
    S = 32 * SS
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c, r = S / 2, size * SS / 2

    def disc(rad, metal, f=1.0):
        poly = _shape(c, c, rad, sides)
        col, rim = _shade(metal, f), _shade(metal, f * 0.6)
        if poly:
            d.polygon(poly, fill=col, outline=rim)
        else:
            d.ellipse([c - rad, c - rad, c + rad, c + rad], fill=col, outline=rim, width=SS // 2)

    disc(r, outer)
    if inner:
        disc(r * 0.62, inner)
    shine = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ImageDraw.Draw(shine).ellipse([c - r * 0.55, c - r * 0.65, c - r * 0.05, c - r * 0.25], fill=(255, 255, 255, 80))
    img.alpha_composite(shine)
    d = ImageDraw.Draw(img)
    label = str(pence) if pence < 100 else str(pence // 100)
    w = (len(label) * 4 - 1) * SS * 0.75
    _digits(d, label, c - w / 2, c - 2.5 * SS * 0.75, _shade(outer if not inner else inner, 0.45), px=SS * 0.75)
    return img.resize((32, 32), Image.LANCZOS)


def receipt():
    S = 32 * SS
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    x0, x1 = 9 * SS, 23 * SS
    d.rectangle([x0, 2 * SS, x1, 27 * SS], fill=(248, 246, 238, 255), outline=(190, 186, 176, 255), width=SS // 2)
    for i, w in enumerate((10, 7, 11, 6, 9, 11, 8)):
        y = (5 + i * 3) * SS
        d.rectangle([x0 + 2 * SS, y, x0 + (2 + w) * SS, y + SS], fill=(150, 146, 140, 255))
    teeth = [(x0 + i * SS, 27 * SS + (SS if i % 2 else 0) * 2) for i in range(0, 15)]
    d.polygon([(x0, 27 * SS)] + teeth + [(x1, 27 * SS)], fill=(248, 246, 238, 255))
    return img.resize((32, 32), Image.LANCZOS)


def item_id(pence):
    return f"note_{pence // 100}" if pence >= 500 else f"coin_{pence // 100}_pound" if pence >= 100 else f"coin_{pence}p"


def all_cash():
    return [5000, 2000, 1000, 500, 200, 100, 50, 20, 10, 5, 2, 1]


def generate(gmod):
    global G
    G = gmod
    tex = G.ASSETS / "textures/item"
    tex.mkdir(parents=True, exist_ok=True)
    icons = {item_id(p): (note(p // 100) if p >= 500 else coin(p)) for p in all_cash()}
    icons["receipt"] = receipt()
    for name, img in icons.items():
        img.save(tex / f"{name}.png")
        G.write_json(G.ASSETS / f"models/item/{name}.json",
                     {"parent": "minecraft:item/generated", "textures": {"layer0": f"{G.MOD_ID}:item/{name}"}})


def lang(mid):
    out = {}
    for p in all_cash():
        out[f"item.{mid}.{item_id(p)}"] = f"£{p // 100} Note" if p >= 500 else f"£{p // 100} Coin" if p >= 100 else f"{p}p Coin"
    out[f"item.{mid}.receipt"] = "Receipt"
    return out
