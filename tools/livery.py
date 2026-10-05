"""Alternative bus liveries, painted into a second texture atlas with the same layout.

"len": red at the front fading to white towards the back, with a yellow and black
sailor-collar swoosh and music notes on the white part (colours after Kagamine Len; no
character art, so a resource pack can paint its own artwork over the white areas).

The painters draw the normal red livery; with a livery active they call design hooks here and
finally recolour the body red pixels (exact colours, before resizing) by how far back they are.
"""
import numpy as np
from PIL import ImageDraw

STATE = {"name": None}

WHITE = (246, 246, 242)
YELLOW = (248, 208, 40)
BLACK = (22, 22, 24)
EMBLEM_RED = (190, 22, 32)          # the emblem keeps its red on the white part

# body-family colours -> their counterparts in white (the lamps use different exact colours)
BODY_MAP = {
    (196, 18, 26): WHITE,           # RED
    (150, 12, 18): (214, 214, 210),  # RED_DARK, louvres, intakes
    (170, 14, 20): (230, 230, 226),  # engine cover
    (140, 10, 16): (204, 204, 200),  # panel outlines
    (170, 16, 22): (226, 226, 222),  # roof hatches
    (110, 10, 16): (62, 62, 66),    # louvre slats
    (40, 6, 10): (40, 40, 44),
    (70, 12, 16): (60, 60, 64),
}


def active():
    return STATE["name"] is not None


def fade(x, x0, x1):
    """0 = red, 1 = white, for a point x along the bus (x0 rear, x1 front): red over the front
    half, blending to white over the next third, white at the back."""
    length = x1 - x0
    a = x0 + 0.55 * length         # red ahead of this
    b = x0 + 0.14 * length         # white behind this
    t = np.clip((a - np.asarray(x, float)) / (a - b), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def mix(c, t):
    return tuple(int(round(c[i] + (WHITE[i] - c[i]) * t)) for i in range(3))


def recolour(img, t_cols):
    """Recolour the body-family pixels of an RGBA image; t_cols: per-column fade (or a number)."""
    if not active():
        return img
    a = np.asarray(img).copy()
    h, w = a.shape[:2]
    t = np.broadcast_to(np.asarray(t_cols, float).reshape(1, -1) if np.ndim(t_cols) else np.full((1, w), float(t_cols)), (h, w))
    rgb = a[..., :3].astype(np.int32)
    out = a[..., :3].astype(np.float64)
    for src, dst in BODY_MAP.items():
        m = (rgb[..., 0] == src[0]) & (rgb[..., 1] == src[1]) & (rgb[..., 2] == src[2])
        if m.any():
            for i in range(3):
                out[..., i] = np.where(m, src[i] + (dst[i] - src[i]) * t, out[..., i])
    a[..., :3] = np.clip(out, 0, 255).astype(np.uint8)
    from PIL import Image
    return Image.fromarray(a, "RGBA")


def note(d, cx, cy, size, colour, beamed=False):
    """A music note (or a beamed pair) with its head centred on (cx, cy); size = stem height px."""
    hw, hh = size * 0.22, size * 0.16
    heads = [(cx, cy)] + ([(cx + size * 0.55, cy - size * 0.12)] if beamed else [])
    for hx, hy in heads:
        d.ellipse((hx - hw, hy - hh, hx + hw, hy + hh), fill=colour)
        d.rectangle((hx + hw * 0.75, hy - size, hx + hw, hy), fill=colour)
    tx = heads[0][0] + hw
    if beamed:
        x2 = heads[1][0] + hw
        d.polygon([(tx - hw * 0.25, cy - size), (x2, heads[1][1] - size), (x2, heads[1][1] - size * 0.82),
                   (tx - hw * 0.25, cy - size * 0.82)], fill=colour)
    else:
        d.polygon([(tx, cy - size), (tx + size * 0.32, cy - size * 0.62), (tx + size * 0.28, cy - size * 0.5),
                   (tx, cy - size * 0.78)], fill=colour)


def band(d, P, p0, p1, offset, width, colour):
    """A straight stripe parallel to p0-p1, offset (metres, upwards) from the line, width metres.
    P maps (x, y) metres to pixels."""
    (x0, y0), (x1, y1) = p0, p1
    dx, dy = x1 - x0, y1 - y0
    n = (dx * dx + dy * dy) ** 0.5
    nx, ny = -dy / n, dx / n
    if ny < 0:
        nx, ny = -nx, -ny
    a, b = offset, offset + width
    pts = [(x0 + nx * a, y0 + ny * a), (x1 + nx * a, y1 + ny * a), (x1 + nx * b, y1 + ny * b), (x0 + nx * b, y0 + ny * b)]
    d.polygon([P(*p) for p in pts], fill=colour)


def side_design(img, X, Y, R, x0, x1, belt):
    """Draw the Len design on a side panel (nearside outside coordinates, front to the right):
    a yellow / black / yellow sailor-collar swoosh rising to the back, and music notes along
    the belt between the decks. belt = (y from, y to) between the window rows."""
    if STATE["name"] != "len":
        return
    d = ImageDraw.Draw(img)

    def P(x, y):
        return (X(x), Y(y))
    p0, p1 = (x0 + 0.42 * (x1 - x0), 0.3), (x0 - 0.2, 2.5)
    y_, k_, w_ = YELLOW + (255,), BLACK + (255,), WHITE + (255,)
    band(d, P, p0, p1, 0.0, 0.34, y_)
    band(d, P, p0, p1, 0.40, 0.07, k_)
    band(d, P, p0, p1, 0.52, 0.05, y_)
    # a short echo stripe further back
    q0, q1 = (x0 + 0.24 * (x1 - x0), 0.3), (x0 - 0.2, 1.3)
    band(d, P, q0, q1, 0.0, 0.08, k_)
    ya, yb = belt
    mid = (ya + yb) / 2
    span = yb - ya
    for i, (fx, dy, s, beam, col) in enumerate(((0.10, 0.10, 0.62, True, k_), (0.18, -0.05, 0.5, False, y_),
                                                 (0.26, 0.12, 0.58, False, k_), (0.34, -0.08, 0.66, True, k_),
                                                 (0.42, 0.06, 0.5, False, y_))):
        x = x0 + fx * (x1 - x0)
        note(d, X(x), Y(mid - span * 0.28 + dy * span), R(span * s), col, beamed=beam)


def rear_design(img, Z, Y, R):
    """Yellow and black stripes across the back and two notes beside the route number box."""
    if STATE["name"] != "len":
        return
    d = ImageDraw.Draw(img)
    w = img.size[0]
    d.rectangle((0, Y(2.47), w, Y(2.38)), fill=YELLOW + (255,))
    d.rectangle((0, Y(2.5), w, Y(2.48)), fill=BLACK + (255,))
    for z, beam in ((-0.66, True), (0.5, False)):
        note(d, Z(z), Y(2.95), R(0.2), BLACK + (255,), beamed=beam)
