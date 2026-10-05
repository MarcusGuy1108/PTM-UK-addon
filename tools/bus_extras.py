"""Bus extras: travel cards, card readers, the ticket and top-up machine, the countdown sign,
bus road signs and road markings. Textures and lang here; the furniture models are built in
furniture.py and the road sign plates by furniture.roadsign_parts(). Called from generate_assets.

No real-world operator branding: the card and the machines carry the world's own flat cube
emblem instead of a transport authority's logo, and the card is the "Cube Card".
"""
import math

import numpy as np
from PIL import Image, ImageDraw, ImageFont

import furniture_textures as F
from furniture_hd import BOLD, REGULAR, Panel

NARROW = "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf"

G = None

CUBE_BLUE = (22, 72, 166)
CUBE_BLUE_DARK = (12, 44, 112)
PASS_GREEN = (30, 140, 110)
READER_YELLOW = (255, 204, 0)
WHITE = (250, 250, 248)
AMBER = (255, 178, 30)
MARK_YELLOW = (238, 192, 32)
MARK_WHITE = (236, 236, 230)


def emblem(d, cx, cy, s, fg, bg):
    """The world's transport emblem: a flat isometric block (a hexagon with its three inner
    edges cut out), as on the buses and the bus stop flags."""
    c = 0.866 * s
    d.polygon([(cx, cy - s), (cx + c, cy - s / 2), (cx + c, cy + s / 2), (cx, cy + s), (cx - c, cy + s / 2),
               (cx - c, cy - s / 2)], fill=fg)
    t = max(1, int(s * 0.2))
    for ex, ey in ((cx, cy + s), (cx - c, cy - s / 2), (cx + c, cy - s / 2)):
        d.line([(cx, cy), (ex, ey)], fill=bg, width=t)


def contactless(d, cx, cy, s, colour):
    """The contactless symbol: four arcs of growing size."""
    w = max(1, int(s * 0.12))
    for i in range(4):
        r = s * (0.25 + 0.22 * i)
        d.arc([cx - r - s * 0.5, cy - r, cx + r - s * 0.5, cy + r], -50, 50, fill=colour, width=w)


def bus_symbol(d, x0, y0, w, fg, bg):
    """Side view of a bus facing left, for the road signs."""
    h = w * 0.52
    d.rounded_rectangle([x0, y0, x0 + w, y0 + h], radius=w * 0.07, fill=fg)
    wy0, wy1 = y0 + h * 0.13, y0 + h * 0.46
    d.rounded_rectangle([x0 + w * 0.04, wy0, x0 + w * 0.14, y0 + h * 0.62], radius=w * 0.02, fill=bg)  # windscreen
    for i in range(4):
        wx0 = x0 + w * 0.2 + i * w * 0.195
        d.rectangle([wx0, wy0, wx0 + w * 0.15, wy1], fill=bg)
    r = h * 0.2
    for cx in (x0 + w * 0.22, x0 + w * 0.8):
        d.ellipse([cx - r * 1.25, y0 + h - r * 1.25, cx + r * 1.25, y0 + h + r * 1.25], fill=bg)
        d.ellipse([cx - r, y0 + h - r, cx + r, y0 + h + r], fill=fg)


# ----------------------------------------------------------------- items

def _card(bg, text, ss=8, photo=False):
    """A travel card lying flat, as an item icon (32 px): the emblem and a short name, with the
    contactless arcs (or a photo, for the pass)."""
    S = 32 * ss
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    x0, y0, x1, y1 = 1 * ss, 6 * ss, 31 * ss, 26 * ss
    d.rounded_rectangle([x0 + ss, y0 + ss, x1 + ss, y1 + ss], radius=3 * ss, fill=(0, 0, 0, 80))     # shadow
    d.rounded_rectangle([x0, y0, x1, y1], radius=3 * ss, fill=bg + (255,))
    dark = tuple(int(c * 0.6) for c in bg)
    d.polygon([(x0 + 12 * ss, y1), (x1, y0 + 9 * ss), (x1, y1)], fill=dark + (255,))       # sweep across the corner
    d.rounded_rectangle([x0, y0, x1, y1], radius=3 * ss, outline=(255, 255, 255, 120), width=ss // 2)
    emblem(d, x0 + 6 * ss, y0 + 6 * ss, 4 * ss, WHITE + (255,), bg + (255,))
    f = ImageFont.truetype(NARROW, int(8 * ss))
    while d.textlength(text, font=f) > 17 * ss:
        f = ImageFont.truetype(NARROW, f.size - ss // 2)
    d.text((x0 + 11.5 * ss, y0 + 6 * ss), text, font=f, fill=WHITE + (255,), anchor="lm")
    if photo:
        d.rectangle([x1 - 9 * ss, y0 + 11 * ss, x1 - 3 * ss, y1 - 2 * ss], fill=(232, 232, 226, 255))
        d.ellipse([x1 - 7.6 * ss, y0 + 12 * ss, x1 - 4.4 * ss, y0 + 15.2 * ss], fill=(120, 96, 80, 255))
        d.rectangle([x1 - 8.2 * ss, y0 + 15.6 * ss, x1 - 3.8 * ss, y1 - 2 * ss], fill=(60, 80, 130, 255))
        d.rectangle([x0 + 3 * ss, y0 + 13 * ss, x0 + 15 * ss, y0 + 14.5 * ss], fill=(255, 255, 255, 200))
        d.rectangle([x0 + 3 * ss, y0 + 16 * ss, x0 + 11 * ss, y0 + 17.5 * ss], fill=(255, 255, 255, 200))
    else:
        d.rectangle([x0 + 3 * ss, y0 + 13 * ss, x0 + 9 * ss, y0 + 17 * ss], fill=(214, 186, 90, 255))   # chip
        contactless(d, x1 - 5 * ss, y1 - 6 * ss, 4 * ss, (255, 255, 255, 230))
    return img.resize((32, 32), Image.LANCZOS)


def cube_card_icon():
    return _card(CUBE_BLUE, "CUBE")


def bus_pass_icon():
    return _card(PASS_GREEN, "PASS", photo=True)


def countdown_icon(ss=8):
    S = 32 * ss
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([15 * ss, 18 * ss, 17 * ss, 31 * ss], fill=(140, 144, 148, 255))
    d.rectangle([1 * ss, 5 * ss, 31 * ss, 20 * ss], fill=(46, 48, 52, 255))
    d.rectangle([2.5 * ss, 6.5 * ss, 29.5 * ss, 18.5 * ss], fill=(8, 8, 9, 255))
    for i, (a, b) in enumerate(((0.0, 0.62), (0.0, 0.5), (0.0, 0.7))):
        y = (8.2 + i * 3.4) * ss
        d.rectangle([4 * ss, y, (4 + 18 * b) * ss, y + 1.6 * ss], fill=AMBER + (255,))
        d.rectangle([24 * ss, y, 28 * ss, y + 1.6 * ss], fill=AMBER + (255,))
    return img.resize((32, 32), Image.LANCZOS)


# ----------------------------------------------------------------- furniture faces

def reader_face(size=64, ss=6):
    """Card reader pad: yellow ring round a dark target with a card and the contactless arcs."""
    S = size * ss
    img = Image.new("RGBA", (S, S), (34, 36, 40, 255))
    d = ImageDraw.Draw(img)
    m = S * 0.06
    d.ellipse([m, m, S - m, S - m], fill=READER_YELLOW + (255,))
    r = S * 0.2
    d.ellipse([m + r, m + r, S - m - r, S - m - r], fill=(22, 22, 24, 255))
    c = S / 2
    contactless(d, c + S * 0.07, c, S * 0.22, (255, 255, 255, 255))
    d.rounded_rectangle([c - S * 0.2, c - S * 0.13, c - S * 0.02, c + S * 0.13], radius=S * 0.02,
                        outline=(255, 255, 255, 255), width=int(S * 0.025))
    # status lamps above the ring
    for i, col in enumerate(((60, 220, 90), (60, 220, 90), (60, 220, 90))):
        x = c + (i - 1) * S * 0.08
        d.ellipse([x - S * 0.018, S * 0.012, x + S * 0.018, S * 0.048], fill=col + (255,))
    return img.resize((size, size), Image.LANCZOS)


def reader_screen(w=96, h=30):
    """The reader's little screen: TOUCH CARD in white on blue."""
    p = Panel(w, h, (18, 46, 110))
    p.text(w / 2, h * 0.5, "TOUCH CARD", 15, (255, 255, 255), NARROW, max_w=w * 0.9)
    return p.done()


def top_up_face():
    """Ticket and top-up machine front (face 10.8 x 19.4 px)."""
    p = Panel(144, 256, (52, 58, 66))
    p.rect(0, 0, 144, 40, CUBE_BLUE)
    img_d = p.d
    emblem(img_d, 22 * p.ss, 20 * p.ss, 11 * p.ss, WHITE + (255,), CUBE_BLUE + (255,))
    p.text(88, 13, "TICKETS &", 12, WHITE, NARROW, max_w=100)
    p.text(88, 29, "TOP-UP", 14, WHITE, NARROW, max_w=100)
    p.rect(12, 50, 132, 124, (28, 30, 34), r=4)                    # touch screen
    p.rect(17, 55, 127, 119, (232, 238, 246))
    p.rect(17, 55, 127, 69, CUBE_BLUE)
    p.text(72, 62, "Cube Card", 8.5, WHITE)
    for i, (label, col) in enumerate((("Top up 10 fares", (40, 140, 70)), ("Top up 1 fare", (40, 110, 170)),
                                       ("Check balance", (90, 96, 104)))):
        y = 74 + i * 15
        p.rect(23, y, 121, y + 12, col, r=2)
        p.text(72, y + 6, label, 7.5, WHITE, NARROW)
    # card reader pad
    p.rect(14, 134, 70, 190, (34, 36, 40), r=4)
    p.ellipse(19, 139, 65, 185, READER_YELLOW)
    p.ellipse(28, 148, 56, 176, (22, 22, 24))
    contactless(p.d, 44 * p.ss, 162 * p.ss, 8 * p.ss, (255, 255, 255, 255))
    p.text(42, 197, "TOUCH CARD", 7, (230, 230, 230), NARROW)
    # bank card slot and coin slot
    p.rect(84, 136, 130, 160, (24, 24, 26), r=3)
    p.rect(92, 146, 122, 150, (6, 6, 6))
    p.text(107, 168, "CARDS", 7, (230, 230, 230), NARROW)
    p.rect(84, 178, 130, 196, (24, 24, 26), r=3)
    p.rect(104, 181, 110, 193, (6, 6, 6))
    p.text(107, 204, "COINS", 7, (230, 230, 230), NARROW)
    # ticket and change tray
    p.rect(20, 214, 124, 246, (22, 22, 24), r=4)
    p.rect(30, 222, 114, 240, (40, 42, 46), r=3)
    p.text(72, 251, "TICKETS AND CHANGE", 6, (200, 200, 200), NARROW)
    return p.done()


def marking_icon(words, colour, ss=8):
    """Inventory icon for a road marking: the words on a patch of tarmac."""
    S = 32 * ss
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([1 * ss, 1 * ss, 31 * ss, 31 * ss], radius=3 * ss, fill=(52, 54, 58, 255))
    for i, word in enumerate(words):
        f = ImageFont.truetype(BOLD, int(11 * ss))
        while d.textlength(word, font=f) > 27 * ss:
            f = ImageFont.truetype(BOLD, f.size - ss)
        d.text((16 * ss, (10 + i * 12) * ss), word, font=f, fill=colour + (255,), anchor="mm")
    return img.resize((32, 32), Image.LANCZOS)


def marking(words, colour, w=192, h=384, stretch=2.4):
    """Elongated road lettering (as UK road markings are), drawn the way a driver approaching
    from the bottom of the image reads it: the first word nearest, at the bottom."""
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    half = h // len(words)
    for i, word in enumerate(reversed(words)):          # far word at the top
        tile_h = int(half / stretch)
        tile = Image.new("L", (w * 2, tile_h * 2), 0)
        d = ImageDraw.Draw(tile)
        px = int(tile_h * 1.7)
        f = ImageFont.truetype(BOLD, px)
        while d.textlength(word, font=f) > w * 2 * 0.86 and px > 10:
            px -= 2
            f = ImageFont.truetype(BOLD, px)
        d.text((w, tile_h), word, font=f, fill=255, anchor="mm")
        tile = tile.resize((w, half), Image.LANCZOS)
        a = np.asarray(tile).astype(float)
        # worn paint: a few speckles and slightly broken edges
        rng = np.random.default_rng(len(word) * 7 + i)
        a *= np.clip(1.0 - (rng.random(a.shape) < 0.05) * rng.random(a.shape) * 0.8, 0, 1)
        alpha = Image.fromarray(np.where(a > 110, 255, 0).astype(np.uint8), "L")
        layer = Image.new("RGBA", (w, half), colour + (255,))
        out.paste(layer, (0, i * half), alpha)
    return out


# ----------------------------------------------------------------- road sign plates

def bus_lane_sign():
    """Bus lane: blue plate, white bus, the hours the lane is in force."""
    img, d, S = F.rect_sign(F.BLUE, F.WHITE, 0.74, 1.0)
    bus_symbol(d, S * 0.24, S * 0.12, S * 0.52, F.WHITE, F.BLUE)
    d.rectangle([S * 0.47, S * 0.48, S * 0.53, S * 0.56], fill=F.WHITE)       # lane arrow
    d.polygon([(S * 0.5, S * 0.42), (S * 0.43, S * 0.49), (S * 0.57, S * 0.49)], fill=F.WHITE)
    F.centred_text(d, S, "Mon - Sat", S * 0.66, S * 0.1, F.WHITE, max_w=0.6)
    F.centred_text(d, S, "7 am - 7 pm", S * 0.8, S * 0.1, F.WHITE, max_w=0.62)
    return F._finish(img)


def buses_only_sign():
    """Route for buses only: white bus on a blue disc."""
    img, d, S = F.sign_disc(F.BLUE)
    d.ellipse([S * 0.04, S * 0.04, S * 0.96, S * 0.96], outline=F.WHITE, width=int(S * 0.02))
    bus_symbol(d, S * 0.2, S * 0.28, S * 0.6, F.WHITE, F.BLUE)
    F.centred_text(d, S, "ONLY", S * 0.75, S * 0.12, F.WHITE, max_w=0.4)
    return F._finish(img)


def bus_stop_clearway_sign():
    """Bus stop clearway: no stopping roundel (blue, red ring, red cross) and the hours."""
    img, d, S = F.rect_sign(F.WHITE, F.BLACK, 1.0, 0.6)
    cx, cy, r = S * 0.24, S * 0.5, S * 0.17
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=F.RED)
    ri = r * 0.8
    d.ellipse([cx - ri, cy - ri, cx + ri, cy + ri], fill=F.BLUE)
    w = int(S * 0.045)
    k = ri * 0.72
    d.line([cx - k, cy - k, cx + k, cy + k], fill=F.RED, width=w)
    d.line([cx - k, cy + k, cx + k, cy - k], fill=F.RED, width=w)
    f = ImageFont.truetype(BOLD, int(S * 0.072))
    f2 = ImageFont.truetype(REGULAR, int(S * 0.066))
    for i, (text, fnt) in enumerate((("Bus stop", f), ("No stopping", f2), ("Mon - Sat", f2), ("7 am - 7 pm", f2))):
        d.text((S * 0.46, S * (0.3 + i * 0.105)), text, font=fnt, fill=F.BLACK, anchor="lm")
    return F._finish(img)


ROAD_SIGNS = {
    "bus_lane_sign": (bus_lane_sign, 12, "Bus Lane Sign"),
    "buses_only_sign": (buses_only_sign, 10.5, "Buses Only Sign"),
    "bus_stop_clearway_sign": (bus_stop_clearway_sign, 12, "Bus Stop Clearway Sign"),
}


# ----------------------------------------------------------------- write

def generate(gmod):
    global G
    G = gmod
    item_tex = G.ASSETS / "textures/item"
    item_tex.mkdir(parents=True, exist_ok=True)
    for name, painter in (("cube_card", cube_card_icon), ("bus_pass", bus_pass_icon),
                          ("bus_countdown_sign", countdown_icon)):
        painter().save(item_tex / f"{name}.png")
        G.write_json(G.ASSETS / f"models/item/{name}.json",
                     {"parent": "minecraft:item/generated", "textures": {"layer0": f"{G.MOD_ID}:item/{name}"}})
    # flat icons for the road markings (their block models are flat decals 3 blocks long)
    for name, words, colour in (("bus_stop_marking", ["BUS", "STOP"], MARK_YELLOW),
                                ("bus_lane_marking", ["BUS", "LANE"], MARK_WHITE)):
        marking_icon(words, colour).save(item_tex / f"{name}.png")
        G.write_json(G.ASSETS / f"models/item/{name}.json",
                     {"parent": "minecraft:item/generated", "textures": {"layer0": f"{G.MOD_ID}:item/{name}"}})
    # the countdown sign is drawn by its renderer; the block model only gives the particle
    G.write_json(G.ASSETS / "models/block/bus_countdown_sign.json", {"textures": {"particle": f"{G.MOD_ID}:block/pole/galvanised"}})
    G.write_json(G.ASSETS / "blockstates/bus_countdown_sign.json",
                 {"variants": {"": {"model": f"{G.MOD_ID}:block/bus_countdown_sign"}}})


def lang():
    m = G.MOD_ID
    return {
        f"item.{m}.cube_card": "Cube Card (Pay As You Go)",
        f"item.{m}.bus_pass": "Bus Pass (Free Travel)",
        f"block.{m}.bus_countdown_sign": "Bus Countdown Sign (Live Arrivals)",
        f"block.{m}.card_reader": "Card Reader",
        f"block.{m}.top_up_machine": "Ticket & Top-up Machine",
        f"block.{m}.bus_stop_marking": "BUS STOP Road Marking",
        f"block.{m}.bus_lane_marking": "BUS LANE Road Marking",
        f"message.{m}.fare.pass_valid": "Bus pass valid. Have a good journey!",
        f"message.{m}.fare.hopper": "Hopper fare: no charge",
        f"message.{m}.fare.hopper_balance": "Hopper fare: no charge. Balance %s",
        f"message.{m}.fare.no_credit": "Not enough credit (balance %s). Top up at a ticket machine",
        f"message.{m}.fare.paid_card": "Fare %s paid. Balance %s",
        f"message.{m}.fare.declined": "Card declined: not enough money in your bank account",
        f"message.{m}.fare.bank_description": "Bus fare (contactless)",
        f"message.{m}.fare.paid_contactless": "Contactless: fare %s paid",
        f"message.{m}.card.balance": "Cube Card balance: %s",
        f"message.{m}.top_up.new_card": "Here's your new Cube Card. Use it on the machine to top up",
        f"message.{m}.top_up.free": "Bus travel is free in this world: no top-up needed",
        f"message.{m}.top_up.no_money": "Top-up of %s refused: your bank balance is %s",
        f"message.{m}.top_up.description": "Cube Card top-up",
        f"message.{m}.top_up.done": "Topped up %s. New balance %s",
        f"tooltip.{m}.cube_card.balance": "Balance: %s",
        f"tooltip.{m}.cube_card.fare": "Bus fare: %s (free changes within an hour)",
        f"tooltip.{m}.cube_card.use": "Right-click by a bus's yellow card reader to tap",
        f"tooltip.{m}.bus_pass.free": "Free travel on all buses",
    }
