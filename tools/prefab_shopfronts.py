"""Shopfronts to fit into an existing building's front wall (to refit an old build's shops): the
wall line gets pilasters, a stall riser, shop windows, a glazed door with a transom light and a
fascia board; the row in front gets the shop sign (editable in game) and, on the Victorian ones,
a stone cornice. The pavement in front and the wall above are left as they are. Stand on the
pavement and aim at the block in front of the wall where the middle of the shop goes.
Coordinates as in prefab_lib (u across the front, y up with the ground layer at 0, v back)."""
from prefab_lib import Block, Build, shop_sign_nbt, stairs
from prefab_parts import pane

STYLES = {
    "victorian": dict(title="Victorian", pilaster="ptmuk:portland_stone", riser="ptmuk:green_faience", door="dark_oak",
                      back="black_concrete", board=0xFF1F3B2A, ink=0xFFD9B44A, cornice=True,
                      what="stone pilasters, a green tiled stall riser, a glazed timber door with a transom light and a "
                           "stone cornice over a gold-on-green fascia"),
    "modern": dict(title="Modern", pilaster="light_gray_concrete", riser="gray_concrete", door="birch",
                   back="gray_concrete", board=0xFF1C2E4A, ink=0xFFFFFFFF, cornice=False,
                   what="grey pilasters, tall glazing on a low grey riser, a glazed door and a lit fascia"),
}
WIDTHS = (5, 7, 9, 11)


def shopfront(style, w):
    s = STYLES[style]
    b = Build(f"shopfront_{style}_{w}", f"Shopfront ({s['title']}, {w} wide)", "Shopfronts", w, 6, 2,
              f"A {w}-block {s['title'].lower()} shopfront to replace part of an old building's front wall: {s['what']}. "
              "Aim at the pavement block in front of the wall where the middle of the shop goes; the pavement and the "
              "wall above are left alone. Right-click the sign to name the shop.")
    for u in range(w):
        b.set(u, 0, 0, "ptmuk:paving_slabs")                    # stands for the pavement that's there: kept
        for y in range(6):
            b.keep.add((u, y, 0))
        b.set(u, 0, 1, "polished_andesite")                     # the threshold under the front
    for u in (0, w - 1):
        b.fill(u, 1, 1, u, 4, 1, s["pilaster"])
    door_u = 1 if w < 7 else w // 2
    for u in range(1, w - 1):
        if u != door_u:
            b.set(u, 1, 1, s["riser"])
            b.fill(u, 2, 1, u, 3, 1, pane("ptmuk:shop_window"))
    b.door(door_u, 1, 1, s["door"], facing="back", hinge="left")
    b.set(door_u, 3, 1, pane("ptmuk:shop_window"))             # the transom light over the door
    for u in range(1, w - 1):
        nbt = shop_sign_nbt("SHOP NAME" if u == 1 else "", "", s["board"], s["ink"])
        b.set(u, 4, 0, Block("ptmuk:shop_sign", {"facing": "south"}, nbt))
        b.keep.discard((u, 4, 0))
        b.set(u, 4, 1, s["back"])
    if s["cornice"]:
        for u in range(w):
            b.set(u, 5, 0, stairs("ptmuk:portland_stone", "back", "top"))
            b.keep.discard((u, 5, 0))
            b.set(u, 5, 1, "ptmuk:portland_stone")
    else:
        for u in range(w):
            b.keep.add((u, 5, 1))                                # the wall above carries on as it was
    return b


def all_shopfronts():
    return [shopfront(style, w) for style in STYLES for w in WIDTHS]
