"""Procedural HD textures for UK street furniture, fences and road signs."""
import math

import numpy as np
from PIL import Image, ImageDraw, ImageFont

from signal_textures import FONT, blur, grid, noise, rng, specular, to_image

BOLD = FONT
REGULAR = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"


# ----------------------------------------------------------------- materials

def paint(colour, size=64, wear=0.0, gloss=0.0):
    """Painted metal: fine noise, a soft vertical sheen and optional paint wear."""
    yy, xx = np.mgrid[0:size, 0:size].astype(float)
    rgb = np.array(colour, float) + noise((size, size, 1), 2.0)
    rgb += (gloss * 18 * np.exp(-((xx - size * 0.35) / (size * 0.18)) ** 2))[..., None]
    rgb += (5 * (0.5 - yy / size))[..., None]
    if wear:
        chips = blur(rng.random((size, size)), 1.2) > 1 - wear * 0.25
        rgb[chips] = rgb[chips] * 0.6 + np.array([70, 64, 58]) * 0.4
    return to_image(rgb)


def plastic(colour, size=64):
    """Moulded HDPE (wheelie bins): matte, faint vertical moulding ribs and texture."""
    yy, xx = np.mgrid[0:size, 0:size].astype(float)
    rgb = np.array(colour, float) + noise((size, size, 1), 2.5)
    rgb += (4 * (np.sin(xx / size * math.pi * 6) > 0.85))[..., None]
    rgb -= (6 * (yy / size))[..., None]
    return to_image(rgb)


def stainless(size=64):
    yy, xx = np.mgrid[0:size, 0:size].astype(float)
    streak = blur(rng.normal(0, 1, (size, size)) * np.array([[1.0]]), 0.6)
    rgb = np.array([178, 182, 186.0]) + (streak * 8)[..., None] + (16 * np.sin(xx / size * math.pi))[..., None]
    return to_image(rgb)


def wood(size=64, base=(132, 92, 55)):
    yy, xx = np.mgrid[0:size, 0:size].astype(float)
    grain = np.sin(xx * 0.9 + 3 * np.sin(yy * 0.07) + rng.normal(0, 0.15, (size, size)))
    rgb = np.array(base, float) + (grain * 9)[..., None] + noise((size, size, 1), 4)
    rgb[(yy % 16) < 0.8] -= 30          # plank joints
    return to_image(rgb)


def concrete(size=64, base=(165, 163, 157)):
    rgb = np.array(base, float) + noise((size, size, 1), 6) + (blur(rng.normal(0, 1, (size, size)), 3) * 10)[..., None]
    pits = rng.random((size, size)) > 0.985
    rgb[pits] -= 35
    return to_image(rgb)


def glass_window(size=128, cols=3, rows=8, frame=(176, 20, 22)):
    """K6-style glazing: red glazing bars, clear panes (transparent for cutout)."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    bar = max(2, size // 48)
    for c in range(cols + 1):
        x = round(c * (size - bar) / cols)
        d.rectangle([x, 0, x + bar, size], fill=frame + (255,))
    for r in range(rows + 1):
        y = round(r * (size - bar) / rows)
        d.rectangle([0, y, size, y + bar], fill=frame + (255,))
    return img


def stripes(size=64, bands=4):
    """Belisha beacon pole: black and white bands."""
    yy = np.mgrid[0:size, 0:size][0].astype(float)
    rgb = np.where(((yy // (size / bands)) % 2 == 0)[..., None], np.array([238, 238, 234.0]), np.array([22, 22, 22.0]))
    rgb = rgb + noise((size, size, 1), 2)
    return to_image(rgb)


def amber_globe(lit, size=64):
    x, y = grid(size)
    d = np.hypot(x, y * 0.6) / (size / 2)
    if lit:
        rgb = np.array([255, 150, 20.0]) + (np.array([0, 90, 120.0]) * np.clip(1 - d, 0, 1)[..., None] ** 2)
    else:
        rgb = np.array([150, 82, 10.0]) * (0.75 + 0.25 * np.clip(1 - d, 0, 1))[..., None]
    rgb += specular(x, y, size / 2, 60 if not lit else 25)[..., None]
    return to_image(rgb)


def mesh(size=64, colour=(150, 154, 156)):
    """Heras weld mesh: thin wires, transparent gaps (cutout)."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for i in range(0, size, 8):
        d.line([i, 0, i, size], fill=colour + (255,), width=1)
    for j in range(0, size, 16):
        d.line([0, j, size, j], fill=colour + (255,), width=1)
    return img


# ----------------------------------------------------------------- text panels

def text_panel(lines, size=(128, 128), bg=(20, 20, 20), fg=(240, 240, 240), font=BOLD, heights=None, pad=0.08,
               lit=False):
    """Centred lines of text on a panel. heights: per-line height as a fraction of the panel."""
    w, h = size
    ss = 4
    img = Image.new("RGBA", (w * ss, h * ss), bg + (255,))
    d = ImageDraw.Draw(img)
    heights = heights or [0.8 / max(1, len(lines))] * len(lines)
    total = sum(heights) * h * ss * 1.15
    y = (h * ss - total) / 2
    for line, frac in zip(lines, heights):
        px = int(frac * h * ss)
        f = ImageFont.truetype(font, px)
        while d.textlength(line, font=f) > w * ss * (1 - 2 * pad) and px > 6:
            px -= 2
            f = ImageFont.truetype(font, px)
        tw = d.textlength(line, font=f)
        d.text(((w * ss - tw) / 2, y), line, fill=fg, font=f)
        y += frac * h * ss * 1.15
    img = img.resize((w, h), Image.LANCZOS)
    if lit:
        arr = np.asarray(img).astype(float)
        arr[..., :3] = arr[..., :3] * 1.0 + 6
        return to_image(arr[..., :3])
    return img


def telephone_sign():
    return text_panel(["TELEPHONE"], (128, 32), bg=(18, 18, 18), fg=(250, 250, 240), heights=[0.6])


def litter_band():
    return text_panel(["LITTER"], (128, 32), bg=(196, 160, 60), fg=(18, 18, 18), heights=[0.62])


def post_plate():
    img = text_panel(["POST", "COLLECTIONS", "9.00 am  5.30 pm"], (128, 96), bg=(238, 236, 228), fg=(25, 25, 25),
                     heights=[0.3, 0.14, 0.14])
    return img


def dog_bin_face():
    img = Image.new("RGBA", (128, 128), (190, 28, 30, 255))
    d = ImageDraw.Draw(img)
    d.rectangle([20, 18, 108, 40], fill=(30, 30, 30))                       # posting slot
    f = ImageFont.truetype(BOLD, 18)
    for i, line in enumerate(["DOG", "WASTE", "ONLY"]):
        tw = d.textlength(line, font=f)
        d.text(((128 - tw) / 2, 52 + i * 22), line, fill=(250, 250, 250), font=f)
    return img


def grit_face():
    img = text_panel(["GRIT"], (128, 64), bg=(232, 186, 28), fg=(20, 20, 20), heights=[0.55])
    return img


def hydrant_plate():
    img = Image.new("RGBA", (128, 128), (238, 196, 22, 255))
    d = ImageDraw.Draw(img)
    f = ImageFont.truetype(BOLD, 96)
    tw = d.textlength("H", font=f)
    d.text(((128 - tw) / 2, -4), "H", fill=(15, 15, 15), font=f)
    f2 = ImageFont.truetype(BOLD, 20)
    d.text((14, 96), "100", fill=(15, 15, 15), font=f2)
    d.text((84, 96), "4.5", fill=(15, 15, 15), font=f2)
    return img


def timetable():
    img = Image.new("RGBA", (128, 128), (245, 245, 240, 255))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 128, 18], fill=(170, 30, 35))
    f = ImageFont.truetype(BOLD, 12)
    d.text((8, 2), "BUS TIMES", fill=(255, 255, 255), font=f)
    for i in range(12):
        y = 26 + i * 8
        d.line([8, y, 120, y], fill=(120, 120, 120))
        for c in range(5):
            d.rectangle([12 + c * 22, y + 2, 26 + c * 22, y + 5], fill=(60, 60, 60))
    return img


def bus_stop_flag():
    img = Image.new("RGBA", (128, 128), (250, 250, 248, 255))
    d = ImageDraw.Draw(img)
    d.rectangle([2, 2, 125, 125], outline=(30, 30, 30), width=3)
    d.ellipse([24, 10, 104, 90], fill=(206, 18, 30))
    d.ellipse([34, 20, 94, 80], fill=(250, 250, 248))
    # bus pictogram
    d.rounded_rectangle([42, 34, 86, 66], radius=5, fill=(20, 20, 20))
    d.rectangle([46, 38, 82, 50], fill=(250, 250, 248))
    d.ellipse([46, 62, 54, 70], fill=(20, 20, 20))
    d.ellipse([74, 62, 82, 70], fill=(20, 20, 20))
    f = ImageFont.truetype(BOLD, 22)
    tw = d.textlength("bus stop", font=f)
    d.text(((128 - tw) / 2, 94), "bus stop", fill=(20, 20, 20), font=f)
    return img


def poster():
    """Back-lit advert for the bus shelter."""
    w, h = 64, 128
    yy, xx = np.mgrid[0:h, 0:w].astype(float)
    rgb = np.dstack([40 + 120 * yy / h, 90 + 60 * np.sin(xx / 9), 170 - 80 * yy / h])
    img = to_image(rgb).resize((128, 128))
    d = ImageDraw.Draw(img)
    f = ImageFont.truetype(BOLD, 16)
    for i, line in enumerate(["VISIT", "THE", "CITY"]):
        tw = d.textlength(line, font=f)
        d.text(((128 - tw) / 2, 20 + i * 20), line, fill=(255, 255, 255), font=f)
    d.rectangle([20, 90, 108, 110], fill=(255, 210, 40))
    return img


def pay_display_face():
    img = Image.new("RGBA", (128, 128), (52, 70, 110, 255))
    d = ImageDraw.Draw(img)
    f = ImageFont.truetype(BOLD, 13)
    tw = d.textlength("PAY & DISPLAY", font=f)
    d.rectangle([0, 0, 128, 20], fill=(250, 250, 250))
    d.text(((128 - tw) / 2, 3), "PAY & DISPLAY", fill=(20, 40, 110), font=f)
    d.rectangle([24, 28, 104, 56], fill=(20, 40, 30))                       # screen
    d.text((32, 34), "TARIFF  £1.20", fill=(120, 255, 160), font=ImageFont.truetype(REGULAR, 10))
    for r in range(4):
        for c in range(3):
            d.rounded_rectangle([34 + c * 22, 62 + r * 12, 50 + c * 22, 71 + r * 12], radius=2, fill=(200, 200, 200))
    d.rectangle([96, 64, 112, 100], fill=(30, 30, 30))                      # card slot
    d.rectangle([40, 112, 88, 120], fill=(20, 20, 20))                      # ticket slot
    return img


def meter_face():
    img = Image.new("RGBA", (128, 128), (60, 62, 66, 255))
    d = ImageDraw.Draw(img)
    d.ellipse([22, 10, 106, 94], fill=(232, 232, 226), outline=(30, 30, 30), width=4)
    d.pieslice([30, 18, 98, 86], 200, 300, fill=(200, 40, 40))
    d.line([64, 52, 90, 30], fill=(20, 20, 20), width=4)
    d.rectangle([52, 104, 76, 112], fill=(20, 20, 20))
    return img


def ev_face():
    img = Image.new("RGBA", (128, 128), (238, 240, 238, 255))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 128, 14], fill=(20, 150, 90))
    d.rounded_rectangle([30, 22, 98, 58], radius=4, fill=(18, 24, 30))
    d.text((40, 30), "CHARGE", fill=(110, 230, 160), font=ImageFont.truetype(BOLD, 12))
    d.ellipse([46, 68, 82, 104], fill=(30, 30, 30), outline=(20, 150, 90), width=4)
    d.ellipse([56, 78, 72, 94], fill=(80, 80, 80))
    return img


def cabinet_door(colour, sticker=False):
    """Roadside cabinet: two doors, handles, vents."""
    img = paint(colour, size=128, wear=0.15).convert("RGBA")
    d = ImageDraw.Draw(img)
    dark = tuple(max(0, c - 30) for c in colour)
    d.rectangle([3, 3, 124, 124], outline=dark, width=2)
    d.line([64, 4, 64, 124], fill=dark, width=2)
    for x in (54, 74):
        d.rectangle([x, 56, x + 4, 72], fill=(40, 40, 40))
    for y in range(10, 26, 4):
        d.line([12, y, 50, y], fill=dark, width=1)
        d.line([78, y, 116, y], fill=dark, width=1)
    if sticker:
        d.polygon([(96, 92), (110, 116), (82, 116)], fill=(250, 210, 20), outline=(20, 20, 20))
        d.line([96, 100, 96, 108], fill=(20, 20, 20), width=2)
    return img


def manhole(size=128):
    x, y = grid(size)
    rgb = np.array([58, 56, 52.0]) + noise((size, size, 1), 4)
    pattern = ((np.abs(x) % 12 < 3) | (np.abs(y) % 12 < 3)).astype(float)
    rgb += (pattern * 16)[..., None]
    edge = (np.maximum(np.abs(x), np.abs(y)) > size / 2 - 6)
    rgb[edge] = [40, 40, 38]
    img = to_image(rgb)
    d = ImageDraw.Draw(img)
    d.text((40, 54), "WATER", fill=(84, 82, 78), font=ImageFont.truetype(BOLD, 14))
    return img


def drain(size=128):
    rgb = np.zeros((size, size, 3)) + 12
    yy, xx = np.mgrid[0:size, 0:size].astype(float)
    bars = (xx % 14 < 7) | (yy < 10) | (yy > size - 10) | (xx < 8) | (xx > size - 8)
    rgb[bars] = [70, 68, 64]
    rgb += noise((size, size, 1), 3)
    return to_image(rgb)


def led_panel(size=64):
    x, y = grid(size)
    rgb = np.array([235, 240, 255.0]) - (np.hypot(x, y) / size * 60)[..., None]
    dots = ((np.abs(x) % 8 < 3) & (np.abs(y) % 8 < 3))
    rgb[dots] = [255, 255, 255]
    return to_image(rgb)


def sodium_bowl(size=64):
    x, y = grid(size)
    rgb = np.array([255, 150, 40.0]) + (np.array([0, 60, 60.0]) * np.clip(1 - np.hypot(x, y) / (size / 2), 0, 1)[..., None])
    return to_image(rgb)


# ----------------------------------------------------------------- road signs (256 px)

SIGN_SS = 4


def _canvas(size=256):
    S = size * SIGN_SS
    return Image.new("RGBA", (S, S), (0, 0, 0, 0)), S


def _finish(img, size=256):
    return img.resize((size, size), Image.LANCZOS)


RED, WHITE, BLACK, BLUE, YELLOW = (200, 20, 32), (250, 250, 248), (16, 16, 16), (0, 82, 160), (255, 205, 0)


def sign_disc(fill, ring=None, ring_frac=0.16):
    img, S = _canvas()
    d = ImageDraw.Draw(img)
    m = S * 0.02
    if ring:
        d.ellipse([m, m, S - m, S - m], fill=ring)
        r = S * ring_frac
        d.ellipse([m + r, m + r, S - m - r, S - m - r], fill=fill)
    else:
        d.ellipse([m, m, S - m, S - m], fill=fill)
    return img, d, S


def centred_text(d, S, text, cy, height, fill, font=BOLD, max_w=0.8):
    px = int(height)
    f = ImageFont.truetype(font, px)
    while d.textlength(text, font=f) > S * max_w and px > 8:
        px -= 4
        f = ImageFont.truetype(font, px)
    tw = d.textlength(text, font=f)
    bbox = d.textbbox((0, 0), text, font=f)
    d.text(((S - tw) / 2, cy - (bbox[3] + bbox[1]) / 2), text, fill=fill, font=f)


def speed_sign(n):
    img, d, S = sign_disc(WHITE, RED)
    centred_text(d, S, str(n), S / 2, S * 0.42, BLACK, max_w=0.56)
    return _finish(img)


def national_speed():
    img, d, S = sign_disc(WHITE, BLACK, ring_frac=0.035)
    d.ellipse([S * 0.07, S * 0.07, S * 0.93, S * 0.93], fill=WHITE)
    d.line([S * 0.78, S * 0.18, S * 0.22, S * 0.82], fill=BLACK, width=int(S * 0.15))
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).ellipse([S * 0.02, S * 0.02, S * 0.98, S * 0.98], fill=255)
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return _finish(out)


def no_entry():
    img, d, S = sign_disc(RED)
    d.rectangle([S * 0.18, S * 0.42, S * 0.82, S * 0.58], fill=WHITE)
    return _finish(img)


def keep_left():
    img, d, S = sign_disc(BLUE)
    d.ellipse([S * 0.04, S * 0.04, S * 0.96, S * 0.96], outline=WHITE, width=int(S * 0.02))
    c = S / 2
    a = math.radians(225)
    pts = [(0, -0.3), (0.17, -0.06), (0.06, -0.06), (0.06, 0.32), (-0.06, 0.32), (-0.06, -0.06), (-0.17, -0.06)]
    rot = math.radians(-135)
    d.polygon([(c + (x * math.cos(rot) - y * math.sin(rot)) * S, c + (x * math.sin(rot) + y * math.cos(rot)) * S)
               for x, y in pts], fill=WHITE)
    del a
    return _finish(img)


def triangle(up=True):
    img, S = _canvas()
    d = ImageDraw.Draw(img)
    m = S * 0.03
    pts = [(S / 2, m), (S - m, S * 0.86), (m, S * 0.86)] if up else [(m, S * 0.14), (S - m, S * 0.14), (S / 2, S - m)]
    d.polygon(pts, fill=RED)
    cx = sum(p[0] for p in pts) / 3
    cy = sum(p[1] for p in pts) / 3
    inner = [(cx + (px - cx) * 0.7, cy + (py - cy) * 0.7) for px, py in pts]
    d.polygon(inner, fill=WHITE)
    return img, d, S, cy


def give_way():
    img, d, S, cy = triangle(up=False)
    centred_text(d, S, "GIVE", S * 0.33, S * 0.11, BLACK, max_w=0.42)
    centred_text(d, S, "WAY", S * 0.46, S * 0.11, BLACK, max_w=0.32)
    return _finish(img)


def stop_sign():
    img, S = _canvas()
    d = ImageDraw.Draw(img)
    c = S / 2

    def octagon(r):
        return [(c + r * math.cos(math.radians(22.5 + 45 * i)), c + r * math.sin(math.radians(22.5 + 45 * i))) for i in range(8)]
    d.polygon(octagon(S * 0.48), fill=WHITE)
    d.polygon(octagon(S * 0.44), fill=RED)
    centred_text(d, S, "STOP", c, S * 0.28, WHITE, max_w=0.7)
    return _finish(img)


def rect_sign(fill, border=None, w_frac=1.0, h_frac=1.0, radius=0.06):
    img, S = _canvas()
    d = ImageDraw.Draw(img)
    x1, y1 = S * (1 - w_frac) / 2, S * (1 - h_frac) / 2
    x2, y2 = S - x1, S - y1
    if border:
        d.rounded_rectangle([x1, y1, x2, y2], radius=S * radius, fill=border)
        b = S * 0.03
        d.rounded_rectangle([x1 + b, y1 + b, x2 - b, y2 - b], radius=S * radius * 0.6, fill=fill)
    else:
        d.rounded_rectangle([x1, y1, x2, y2], radius=S * radius, fill=fill)
    return img, d, S


def one_way():
    img, d, S = rect_sign(BLUE, WHITE, 1.0, 0.5)
    c = S / 2
    d.rectangle([S * 0.12, c - S * 0.05, S * 0.72, c + S * 0.05], fill=WHITE)
    d.polygon([(S * 0.88, c), (S * 0.68, c - S * 0.15), (S * 0.68, c + S * 0.15)], fill=WHITE)
    return _finish(img)


def parking():
    img, d, S = rect_sign(BLUE, WHITE)
    centred_text(d, S, "P", S / 2, S * 0.75, WHITE)
    return _finish(img)


def pay_at_machine():
    img, d, S = rect_sign(WHITE, BLACK, 1.0, 0.56)
    centred_text(d, S, "Pay at", S * 0.4, S * 0.15, BLACK, font=BOLD)
    centred_text(d, S, "machine", S * 0.6, S * 0.15, BLACK, font=BOLD)
    return _finish(img)


def disabled_parking():
    img, d, S = rect_sign(WHITE, BLACK, 1.0, 0.66)
    d.rounded_rectangle([S * 0.08, S * 0.24, S * 0.36, S * 0.52], radius=S * 0.02, fill=BLUE)
    # wheelchair symbol
    d.ellipse([S * 0.19, S * 0.27, S * 0.24, S * 0.32], fill=WHITE)
    d.line([S * 0.21, S * 0.33, S * 0.21, S * 0.42, S * 0.29, S * 0.42, S * 0.31, S * 0.49], fill=WHITE, width=int(S * 0.02))
    d.arc([S * 0.13, S * 0.37, S * 0.27, S * 0.51], 30, 270, fill=WHITE, width=int(S * 0.02))
    f = ImageFont.truetype(BOLD, int(S * 0.075))
    for i, line in enumerate(["Disabled", "badge", "holders only"]):
        d.text((S * 0.4, S * (0.24 + i * 0.1)), line, fill=BLACK, font=f)
    return _finish(img)


def warning(symbol):
    img, d, S, cy = triangle(up=True)
    k = S / 100
    if symbol == "traffic_signals":
        d.rounded_rectangle([43 * k, 38 * k, 57 * k, 76 * k], radius=2 * k, fill=BLACK)
        for i, col in enumerate([RED, (255, 170, 0), (0, 160, 90)]):
            d.ellipse([46 * k, (41 + i * 12) * k, 54 * k, (49 + i * 12) * k], fill=col)
    elif symbol == "pedestrian_crossing":
        d.ellipse([46 * k, 34 * k, 54 * k, 42 * k], fill=BLACK)
        d.line([50 * k, 43 * k, 47 * k, 58 * k], fill=BLACK, width=int(5 * k))
        d.line([47 * k, 58 * k, 41 * k, 70 * k], fill=BLACK, width=int(4 * k))
        d.line([47 * k, 58 * k, 55 * k, 69 * k], fill=BLACK, width=int(4 * k))
        d.line([49 * k, 47 * k, 42 * k, 54 * k], fill=BLACK, width=int(3 * k))
        d.line([49 * k, 47 * k, 57 * k, 52 * k], fill=BLACK, width=int(3 * k))
        for i in range(4):
            d.rectangle([(28 + i * 12) * k, 74 * k, (34 + i * 12) * k, 78 * k], fill=BLACK)
    elif symbol == "children":
        for ox, s in ((-7, 1.0), (7, 0.85)):
            cx = 50 + ox
            d.ellipse([(cx - 3.5 * s) * k, (38 + (1 - s) * 10) * k, (cx + 3.5 * s) * k, (45 + (1 - s) * 10) * k], fill=BLACK)
            d.line([cx * k, (46 + (1 - s) * 10) * k, (cx - 2) * k, 62 * k], fill=BLACK, width=int(4 * k))
            d.line([(cx - 2) * k, 62 * k, (cx - 7) * k, 74 * k], fill=BLACK, width=int(3.5 * k))
            d.line([(cx - 2) * k, 62 * k, (cx + 5) * k, 73 * k], fill=BLACK, width=int(3.5 * k))
    elif symbol == "roadworks":
        d.ellipse([40 * k, 38 * k, 47 * k, 45 * k], fill=BLACK)
        d.line([44 * k, 46 * k, 50 * k, 60 * k], fill=BLACK, width=int(5 * k))
        d.line([50 * k, 60 * k, 44 * k, 74 * k], fill=BLACK, width=int(4 * k))
        d.line([50 * k, 60 * k, 56 * k, 74 * k], fill=BLACK, width=int(4 * k))
        d.line([46 * k, 50 * k, 62 * k, 58 * k], fill=BLACK, width=int(3 * k))   # spade handle
        d.line([62 * k, 58 * k, 64 * k, 74 * k], fill=BLACK, width=int(3 * k))
        d.polygon([(58 * k, 74 * k), (72 * k, 74 * k), (66 * k, 66 * k)], fill=BLACK)  # spoil heap
    return _finish(img)


def sign_back(shape):
    """Grey back of a sign, same outline as the face (cutout)."""
    img, S = _canvas()
    d = ImageDraw.Draw(img)
    grey = (128, 131, 134)
    if shape == "disc":
        d.ellipse([S * 0.02, S * 0.02, S * 0.98, S * 0.98], fill=grey)
    elif shape == "tri_up":
        m = S * 0.03
        d.polygon([(S / 2, m), (S - m, S * 0.86), (m, S * 0.86)], fill=grey)
    elif shape == "tri_down":
        m = S * 0.03
        d.polygon([(m, S * 0.14), (S - m, S * 0.14), (S / 2, S - m)], fill=grey)
    elif shape == "octagon":
        c = S / 2
        d.polygon([(c + S * 0.48 * math.cos(math.radians(22.5 + 45 * i)), c + S * 0.48 * math.sin(math.radians(22.5 + 45 * i)))
                   for i in range(8)], fill=grey)
    else:
        d.rounded_rectangle([0, 0, S, S], radius=S * 0.06, fill=grey)
    return _finish(img)


# ----------------------------------------------------------------- cameras, roadworks, motorway

ORANGE = (236, 106, 22)


def camera_front(kind, size=64):
    """Front of a speed camera: dark recessed panel with lens and flash windows."""
    ss = 4
    S = size * ss
    img = Image.new("RGBA", (S, S), (232, 186, 28, 255))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([S * 0.08, S * 0.08, S * 0.92, S * 0.92], radius=S * 0.05, fill=(30, 31, 33, 255))
    if kind == "gatso":
        d.rounded_rectangle([S * 0.16, S * 0.16, S * 0.84, S * 0.46], radius=S * 0.03, fill=(55, 60, 66, 255))
        d.ellipse([S * 0.36, S * 0.2, S * 0.64, S * 0.44], fill=(12, 12, 14, 255))
        d.ellipse([S * 0.44, S * 0.27, S * 0.52, S * 0.33], fill=(120, 140, 170, 255))
        d.rounded_rectangle([S * 0.16, S * 0.54, S * 0.84, S * 0.84], radius=S * 0.03, fill=(200, 200, 196, 255))
        for i in range(6):
            x = S * (0.2 + i * 0.105)
            d.line([x, S * 0.56, x, S * 0.82], fill=(170, 170, 166, 255), width=ss)
    else:
        for cy in (0.3, 0.7):
            d.ellipse([S * 0.28, S * (cy - 0.17), S * 0.72, S * (cy + 0.17)], fill=(14, 14, 16, 255))
            d.ellipse([S * 0.42, S * (cy - 0.06), S * 0.54, S * (cy + 0.04)], fill=(110, 125, 160, 255))
    return img.resize((size, size), Image.LANCZOS)


def specs_front(size=64):
    ss = 4
    S = size * ss
    img = Image.new("RGBA", (S, S), (232, 186, 28, 255))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([S * 0.1, S * 0.12, S * 0.62, S * 0.88], radius=S * 0.04, fill=(28, 28, 30, 255))
    d.ellipse([S * 0.2, S * 0.3, S * 0.52, S * 0.7], fill=(10, 10, 12, 255))
    d.ellipse([S * 0.32, S * 0.44, S * 0.4, S * 0.52], fill=(110, 125, 160, 255))
    # infrared illuminator: grid of dark red LEDs
    d.rounded_rectangle([S * 0.66, S * 0.2, S * 0.92, S * 0.8], radius=S * 0.03, fill=(40, 16, 16, 255))
    for i in range(4):
        for j in range(8):
            x, y = S * (0.69 + i * 0.058), S * (0.24 + j * 0.068)
            d.ellipse([x, y, x + S * 0.035, y + S * 0.035], fill=(120, 30, 30, 255))
    return img.resize((size, size), Image.LANCZOS)


def speed_camera_sign():
    img, d, S = rect_sign(WHITE, BLACK, 0.7, 1.0)
    k = S / 100
    # camera body, lens and the flash on top, as on diagram 880
    d.rounded_rectangle([30 * k, 38 * k, 70 * k, 64 * k], radius=3 * k, fill=BLACK)
    d.rectangle([38 * k, 32 * k, 50 * k, 38 * k], fill=BLACK)
    d.ellipse([42 * k, 42 * k, 58 * k, 58 * k], fill=WHITE)
    d.ellipse([46 * k, 46 * k, 54 * k, 54 * k], fill=BLACK)
    return _finish(img)


def cone_bands(size=64):
    rgb = np.zeros((size, size, 3)) + np.array(ORANGE, float) + noise((size, size, 1), 2)
    yy = np.mgrid[0:size, 0:size][0]
    band = ((yy > size * 0.18) & (yy < size * 0.36)) | ((yy > size * 0.48) & (yy < size * 0.62))
    rgb[band] = np.array([238, 240, 240.0]) + noise((int(band.sum()), 1), 2)
    return to_image(rgb)


def chapter8(size=64):
    """Red and white diagonal barrier stripes."""
    yy, xx = np.mgrid[0:size, 0:size].astype(float)
    stripe = ((xx + yy) // (size / 4)) % 2 == 0
    rgb = np.where(stripe[..., None], np.array([200, 24, 30.0]), np.array([240, 240, 236.0]))
    rgb += noise((size, size, 1), 2)
    return to_image(rgb)


def road_closed(size=128):
    img, d, S = rect_sign(WHITE, RED, 1.0, 0.6, radius=0.03)
    centred_text(d, S, "ROAD", S * 0.42, S * 0.15, BLACK, max_w=0.7)
    centred_text(d, S, "CLOSED", S * 0.6, S * 0.15, BLACK, max_w=0.8)
    return _finish(img, 128)


def sos_panel(size=64):
    return text_panel(["SOS"], size=(size, size), bg=ORANGE, fg=(250, 250, 250), heights=[0.38])


def marker_plate(size=64):
    img = Image.new("RGBA", (size, size), (0, 82, 160, 255))
    d = ImageDraw.Draw(img)
    f = ImageFont.truetype(BOLD, int(size * 0.32))
    for i, line in enumerate(["A", "123.4"]):
        tw = d.textlength(line, font=f)
        d.text(((size - tw) / 2, size * (0.1 + i * 0.42)), line, fill=(250, 250, 250), font=f)
    return img


def w_beam(size=64):
    """Galvanised W-profile crash barrier rail: two ridges seen face on."""
    yy = np.mgrid[0:size, 0:size][0].astype(float)
    shade = 18 * np.cos(yy / size * 4 * math.pi)
    rgb = np.array([170, 174, 176.0]) + shade[..., None] + noise((size, size, 1), 3)
    return to_image(rgb)
