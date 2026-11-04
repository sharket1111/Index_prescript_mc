"""Generates the Brand Manager's hammer (fan-made, after Limbus Company): a 16x16
inventory sprite, the in-hand 3D model, its palette texture and the item
model definition.

A long black pole with brass collars and a spiked brass-tipped butt; at the
top, set across the pole, a great two-block head: two boxy black blocks
either side of a narrower collar, each framed in gold with three gold bars
on its faces, capped at both ends.

Like the other weapons it is designed upright (grip at the bottom, axis
x = z = 8) and tilted -45 degrees about Z by gen3d.to_json.

Usage: python3 tools/renoir_hammer.py src/main/resources/assets/index_prescript [preview.png]
"""
import json
import sys

from PIL import Image

import forms
import gen3d
from gen3d import Model

NS = "index_prescript"
NAME = "renoir_hammer"

# --- 3D model --------------------------------------------------------------

SWATCH = {
    "black": (0, 0, (18, 18, 22)),
    "dark": (4, 0, (36, 36, 42)),
    "steel": (8, 0, (66, 66, 74)),
    "steel_light": (12, 0, (112, 112, 122)),
    "gold_light": (0, 4, (236, 214, 150)),
    "gold": (4, 4, (190, 162, 98)),
    "gold_dark": (8, 4, (122, 102, 60)),
    "bronze": (12, 4, (92, 76, 46)),
}

# The head, across the pole: blocks run along x, the pole along y.
HEAD_LOW, HEAD_HIGH = 20.6, 26.4      # head width (along the pole)
HEAD_BACK, HEAD_FRONT = 5.3, 10.7     # head depth (z)
NECK = 1.9                            # half the collar's length, either side of the pole
BLOCK = 4.4                           # each block's length
CAP = 0.6                             # end caps
LINE = 0.28                           # gold line width
RAISE = 0.06                          # how far the gold stands proud of the black


def pole(m):
    # Butt: a brass-ringed spike below the grip.
    m.rod(-7.4, -6.6, 0.35, "gold_light")
    m.rod(-6.6, -5.6, 0.75, "steel")
    m.rod(-5.6, -4.6, 1.15, "dark")
    m.rod(-4.6, -4.2, 1.4, "gold")
    m.rod(-4.2, -3.2, 0.9, "black")
    m.rod(-3.2, -2.8, 1.3, "gold_dark")
    # The pole, with collars where it is held and below the head.
    m.rod(-2.8, HEAD_LOW, 0.9, "black")
    for y in (5.4, 15.2):
        m.rod(y, y + 0.35, 1.2, "gold")
    m.rod(HEAD_LOW - 1.4, HEAD_LOW, 1.3, "dark")
    m.rod(HEAD_LOW - 1.8, HEAD_LOW - 1.4, 1.45, "gold")
    return m


def framed_face(m, x0, x1, y0, y1, z, outward):
    """Gold frame and three bars on a block face at depth z, standing out towards {outward}."""
    za, zb = (z, z + outward * RAISE) if outward > 0 else (z + outward * RAISE, z)
    inset = 0.45
    fx0, fx1, fy0, fy1 = x0 + inset, x1 - inset, y0 + inset, y1 - inset
    m.box(fx0, fy0, za, fx1, fy0 + LINE, zb, "gold")
    m.box(fx0, fy1 - LINE, za, fx1, fy1, zb, "gold")
    m.box(fx0, fy0, za, fx0 + LINE, fy1, zb, "gold")
    m.box(fx1 - LINE, fy0, za, fx1, fy1, zb, "gold")
    width = fx1 - fx0
    for i in (1, 2, 3):
        x = fx0 + width * i / 4 - LINE / 2
        m.box(x, fy0 + 0.6, za, x + LINE, fy1 - 0.6, zb, "gold_light" if i == 2 else "gold")


def framed_top(m, x0, x1, y, outward):
    """Gold frame on a block's top or bottom face at height y."""
    ya, yb = (y, y + outward * RAISE) if outward > 0 else (y + outward * RAISE, y)
    inset = 0.45
    zf0, zf1 = HEAD_BACK + inset, HEAD_FRONT - inset
    fx0, fx1 = x0 + inset, x1 - inset
    m.box(fx0, ya, zf0, fx1, yb, zf0 + LINE, "gold_dark")
    m.box(fx0, ya, zf1 - LINE, fx1, yb, zf1, "gold_dark")
    m.box(fx0, ya, zf0, fx0 + LINE, yb, zf1, "gold_dark")
    m.box(fx1 - LINE, ya, zf0, fx1, yb, zf1, "gold_dark")


def head(m):
    mid = (HEAD_LOW + HEAD_HIGH) / 2
    # The collar round the pole.
    m.box(8 - NECK, HEAD_LOW + 0.5, HEAD_BACK + 0.6, 8 + NECK, HEAD_HIGH - 0.5, HEAD_FRONT - 0.6, "dark")
    for x in (8 - NECK, 8 + NECK - 0.35):
        m.box(x, HEAD_LOW + 0.3, HEAD_BACK + 0.4, x + 0.35, HEAD_HIGH - 0.3, HEAD_FRONT - 0.4, "gold")
    m.box(8 - 0.5, mid - 0.9, HEAD_FRONT - 0.6, 8 + 0.5, mid + 0.9, HEAD_FRONT - 0.6 + RAISE + 0.1, "gold_light")
    m.box(8 - 0.5, mid - 0.9, HEAD_BACK + 0.6 - RAISE - 0.1, 8 + 0.5, mid + 0.9, HEAD_BACK + 0.6, "gold_light")
    # The two blocks and their end caps.
    for side in (-1, 1):
        inner = 8 + side * NECK
        outer = inner + side * BLOCK
        x0, x1 = min(inner, outer), max(inner, outer)
        m.box(x0, HEAD_LOW, HEAD_BACK, x1, HEAD_HIGH, HEAD_FRONT, "black")
        framed_face(m, x0, x1, HEAD_LOW, HEAD_HIGH, HEAD_FRONT, 1)
        framed_face(m, x0, x1, HEAD_LOW, HEAD_HIGH, HEAD_BACK, -1)
        framed_top(m, x0, x1, HEAD_HIGH, 1)
        framed_top(m, x0, x1, HEAD_LOW, -1)
        c0, c1 = (outer, outer + side * CAP)
        cx0, cx1 = min(c0, c1), max(c0, c1)
        m.box(cx0, HEAD_LOW + 0.4, HEAD_BACK + 0.4, cx1, HEAD_HIGH - 0.4, HEAD_FRONT - 0.4, "dark")
        e0, e1 = (outer + side * CAP, outer + side * (CAP + 0.25))
        m.box(min(e0, e1), HEAD_LOW + 0.9, HEAD_BACK + 0.9, max(e0, e1), HEAD_HIGH - 0.9, HEAD_FRONT - 0.9, "gold_dark")
        # A gold band where the block meets its cap.
        b0, b1 = outer - side * 0.3, outer
        m.box(min(b0, b1), HEAD_LOW - 0.05, HEAD_BACK - 0.05, max(b0, b1), HEAD_HIGH + 0.05, HEAD_FRONT + 0.05, "gold")
    return m


def model():
    return head(pole(Model()))


# --- sprite ----------------------------------------------------------------

K = (12, 12, 14, 255)
BLACK = (24, 24, 28, 255)
DARK = (46, 46, 54, 255)
GOLD = (196, 168, 100, 255)
GOLD_LIGHT = (238, 218, 156, 255)
GOLD_DARK = (126, 106, 62, 255)


def sprite():
    """Pixel art: the pole along the diagonal from the bottom-left, and across
    its top the head, seen corner-on: two black blocks rimmed in gold (drawn
    as diamonds) either side of a dark collar."""
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    blocks = [(7.5, 4.0), (12.0, 8.5)]

    def in_block(x, y, center):
        return abs(x + 0.5 - center[0]) + abs(y + 0.5 - center[1]) <= 3.6

    # The pole: brass butt, black shaft with two gold collars, up to the collar of the head.
    for i in range(10):
        x, y = 1 + i, 14 - i
        colour = GOLD_LIGHT if i == 0 else GOLD if i in (3, 6) else BLACK
        px[x, y] = colour
        if 0 < i < 9 and y + 1 < 16:
            px[x, y + 1] = DARK if colour == BLACK else GOLD_DARK
    for y in range(16):
        for x in range(16):
            for center in blocks:
                if not in_block(x, y, center):
                    continue
                edge = any(not in_block(x + dx, y + dy, center) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                if edge:
                    px[x, y] = GOLD
                elif abs(x + 0.5 - center[0]) < 1 and abs(y + 0.5 - center[1]) < 1:
                    px[x, y] = GOLD_LIGHT
                else:
                    px[x, y] = BLACK
    # The collar joining the blocks, over the pole's end.
    for x, y in ((9, 6), (10, 6), (9, 7), (10, 7)):
        px[x, y] = DARK
    filled = {(x, y) for y in range(16) for x in range(16) if px[x, y][3] > 0}
    for y in range(16):
        for x in range(16):
            if (x, y) not in filled and any((x + dx, y + dy) in filled for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[x, y] = K
    return img


def item_definition():
    def ref(name):
        return {"type": "minecraft:model", "model": f"{NS}:item/{name}"}

    return {"model": {"type": "minecraft:select", "property": "minecraft:display_context",
                      "cases": [{"when": ["gui", "ground", "fixed"], "model": ref(NAME)}],
                      "fallback": ref(f"{NAME}_3d")}}


def dump(path, obj):
    with open(path, "w") as f:
        json.dump(obj, f, indent="\t")
        f.write("\n")


if __name__ == "__main__":
    assets = sys.argv[1]
    preview = sys.argv[2] if len(sys.argv) > 2 else None
    gen3d.make_texture(f"{assets}/textures/item/{NAME}_3d.png", SWATCH)
    img = sprite()
    img.save(f"{assets}/textures/item/{NAME}.png")
    dump(f"{assets}/models/item/{NAME}.json",
         {"parent": "minecraft:item/handheld", "textures": {"layer0": f"{NS}:item/{NAME}"}})
    m = model()
    dump(f"{assets}/models/item/{NAME}_3d.json", gen3d.to_json(m, f"{NAME}_3d", SWATCH))
    dump(f"{assets}/items/{NAME}.json", item_definition())
    if preview:
        sheet = Image.new("RGBA", (660, 440), (210, 210, 214, 255))
        big = img.resize((200, 200), Image.NEAREST)
        sheet.paste(big, (10, 10), big)
        sheet.paste(gen3d.render(m, size=420, scale=6.5, swatch=SWATCH), (230, 10))
        sheet.save(preview)
