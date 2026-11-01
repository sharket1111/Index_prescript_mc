"""Generates Laevatein's four release stages: 16x16 sprites, in-hand 3D models,
the palette texture for the models, and the item model definition.

Each release unwraps another casing: sealed (black blade bound in brass
chains), release 1 (glowing serrated edge, angled tip), release 2 (wide blade,
glowing panel beside black armour) and release 3 (the bare, slender glowing
red blade).
All share the same black, ridged grip.

Usage: python3 tools/laevatein.py src/main/resources/assets/index_prescript [preview.png]
"""
import json
import math
import sys

from PIL import Image

import forms
import gen3d
from gen3d import Model

NS = "index_prescript"
STAGES = ["sealed", "release_1", "release_2", "release_3"]

# --- sprites ---------------------------------------------------------------

K = (14, 12, 12, 255)
GLOW_LIGHT = (255, 196, 92, 255)
GLOW = (236, 104, 30, 255)
GLOW_DARK = (164, 36, 18, 255)
EMBER = (88, 36, 18, 255)
METAL_DARK = (30, 30, 34, 255)
METAL = (64, 64, 72, 255)
METAL_LIGHT = (128, 128, 140, 255)
CHAIN = (206, 184, 112, 255)


def grip(u, v):
    if -0.6 <= u < 0.2 and abs(v) <= 0.5:
        return METAL
    if 0.2 <= u <= 4.4 and abs(v) <= (0.95 if int(u * 2) % 2 == 0 else 0.6):
        return METAL_LIGHT if v > 0.45 else METAL_DARK
    return None


def blade(u, v, u0, u1, lo, hi):
    return u0 <= u <= u1 and lo <= v <= hi


def bare(u, v, x, y):
    if (c := grip(u, v)):
        return c
    if blade(u, v, 4.4, 5.0, -1.2, 1.2):
        return METAL
    top = forms.L - 0.4
    if 5.0 <= u <= top:
        half = 1.0 if u < top - 2.5 else 1.0 * (top - u) / 2.5
        if -half <= v <= half + 0.15:
            if u < 8.5 and abs(v) < 0.45:
                return EMBER
            if v > half - 0.5:
                return GLOW_LIGHT
            return GLOW_DARK if v < -half + 0.5 else GLOW
    return None


def paneled(u, v, x, y):
    if (c := grip(u, v)):
        return c
    if blade(u, v, 4.4, 5.0, -1.6, 1.6):
        return METAL
    if blade(u, v, 5.0, forms.L - 0.5, -1.7, 1.7):
        if v > 0.0:
            return GLOW_LIGHT if v > 1.2 else GLOW
        return METAL_LIGHT if abs(v + 0.9) < 0.25 and int(u) % 3 == 0 else METAL_DARK
    if blade(u, v, 5.4, 7.6, 1.7, 2.6):
        return GLOW_DARK
    return None


def serrated(u, v, x, y):
    if (c := grip(u, v)):
        return c
    if blade(u, v, 4.4, 5.0, -1.5, 1.5):
        return METAL
    top = forms.L - 0.4
    if 5.0 <= u <= top:
        lo, hi = -1.5, 1.6
        if u > top - 3.0:
            hi = 1.6 - (u - (top - 3.0)) * 0.9  # angled tip
        if lo <= v <= hi:
            if v > 0.3:
                serrated = int(u * 1.5) % 2 == 0
                return GLOW_LIGHT if v > hi - 0.45 else (GLOW if serrated else GLOW_DARK)
            return METAL_LIGHT if abs(v + 0.7) < 0.2 and int(u) % 2 == 0 else METAL_DARK
    return None


def chained(u, v, x, y):
    if (c := grip(u, v)):
        return c
    if blade(u, v, 4.4, forms.L - 0.5, -1.8, 1.8):
        # brass chain wound diagonally around the black blade
        if (u + v * 1.4) % 3.0 < 0.85 or ((u - v * 1.4) % 4.4 < 0.7 and abs(v) > 0.5):
            return CHAIN
        return METAL if v > 0.8 else METAL_DARK
    return None


# Each release unwraps another casing: chained when sealed, the bare glowing blade at release 3.
SPRITES = {"sealed": chained, "release_1": serrated, "release_2": paneled, "release_3": bare}


def outline(img):
    px = img.load()
    filled = {(x, y) for y in range(16) for x in range(16) if px[x, y][3] > 0}
    for y in range(16):
        for x in range(16):
            if (x, y) in filled:
                continue
            if any((x + dx, y + dy) in filled for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[x, y] = K
    return img


def sprite(fn):
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            u, v = forms.uv(x, y)
            c = fn(u, v, x, y)
            if c:
                img.putpixel((x, y), c)
    return outline(img)


def icon_sprite():
    """[Seal Release] passive icon: a small glowing orange sword with a dark hilt."""
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            u, v = forms.uv(x, y)
            c = None
            if 1.0 <= u <= 4.2 and abs(v) <= 0.7:
                c = METAL if v > 0 else METAL_DARK
            elif 4.2 < u <= 5.0 and abs(v) <= 1.9:
                c = GLOW_DARK
            elif 5.0 < u <= forms.L - 0.8:
                half = 1.3 if u < forms.L - 3.2 else 1.3 * (forms.L - 0.8 - u) / 2.4
                if abs(v) <= half:
                    c = GLOW_LIGHT if v > half - 0.6 else GLOW
            if c:
                img.putpixel((x, y), c)
    return outline(img)


# --- 3D models -------------------------------------------------------------

SWATCH = {
    "glow_light": (0, 0, (255, 200, 96)),
    "glow": (4, 0, (240, 108, 32)),
    "glow_dark": (8, 0, (168, 38, 18)),
    "ember": (12, 0, (90, 38, 18)),
    "metal_dark": (0, 4, (28, 28, 32)),
    "metal": (4, 4, (58, 58, 66)),
    "metal_light": (8, 4, (118, 118, 130)),
    "chain": (12, 4, (206, 184, 112)),
}


def grip3d(m):
    m.rod(-3.4, -2.2, 0.8, "metal")
    for i in range(7):
        y = -2.2 + i
        m.rod(y, y + 0.55, 1.5, "metal_dark")
        m.rod(y + 0.55, y + 1.0, 1.1, "metal")
    m.rod(4.8, 5.6, 2.2, "metal_dark")
    return m


def bare3d():
    m = grip3d(Model())
    gen3d.taper(m, 5.6, 18.0, 2.0, 1.8, 0.5, 0.5, "glow", 3)
    m.flat(6.85, 5.6, 7.15, 18.0, 0.55, "glow_dark")
    m.flat(8.75, 5.6, 9.05, 18.0, 0.55, "glow_light")
    m.flat(7.6, 5.6, 8.4, 9.5, 0.6, "ember")
    gen3d.taper(m, 18.0, 21.0, 1.8, 0.2, 0.5, 0.3, "glow_light", 4)
    return m


def paneled3d():
    m = grip3d(Model())
    m.flat(6.2, 5.6, 8.0, 20.0, 0.9, "glow")
    m.flat(6.2, 5.6, 6.6, 20.0, 1.0, "glow_light")
    m.flat(8.0, 5.6, 9.8, 20.0, 1.1, "metal_dark")
    for y in (8.0, 11.0, 14.0, 17.0):
        m.flat(8.4, y, 9.4, y + 0.3, 1.2, "metal_light")
    m.flat(5.0, 6.0, 6.2, 9.0, 1.2, "metal_dark")
    m.flat(5.2, 6.4, 6.2, 8.6, 1.3, "glow_dark")
    return m


def serrated3d():
    m = grip3d(Model())
    m.flat(8.0, 5.6, 9.8, 19.0, 1.1, "metal_dark")
    m.flat(8.3, 9.0, 9.5, 9.3, 1.2, "metal_light")
    m.flat(8.3, 14.0, 9.5, 14.3, 1.2, "metal_light")
    for i in range(9):
        y = 5.6 + i * 1.5
        m.flat(6.4, y, 8.0, y + 1.0, 0.8, "glow")
        m.flat(6.7, y + 1.0, 8.0, y + 1.5, 0.8, "glow_dark")
    m.flat(6.2, 5.6, 6.5, 19.0, 0.9, "glow_light")
    gen3d.taper(m, 19.0, 21.0, 3.0, 1.0, 1.0, 0.8, "metal_dark", 3)
    return m


def chained3d():
    m = grip3d(Model())
    m.flat(6.2, 5.6, 9.8, 20.0, 1.2, "metal_dark")
    m.flat(6.4, 5.6, 7.0, 20.0, 1.3, "metal")
    # chain wound around the blade as a helix of small links
    for i in range(64):
        t = i / 63
        a = t * math.pi * 9
        y = 6.0 + t * 13.5
        x = 8.0 + math.cos(a) * 2.15
        z = 8.0 + math.sin(a) * 1.0
        m.box(x - 0.45, y - 0.3, z - 0.25, x + 0.45, y + 0.3, z + 0.25, "chain")
    return m


MODELS = {"sealed": chained3d, "release_1": serrated3d, "release_2": paneled3d, "release_3": bare3d}


def item_definition():
    def ref(name):
        return {"type": "minecraft:model", "model": f"{NS}:item/{name}"}

    def by_stage(suffix):
        return {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
                "cases": [{"when": s, "model": ref(f"laevatein_{s}{suffix}")} for s in STAGES[1:]],
                "fallback": ref(f"laevatein_sealed{suffix}")}

    return {"model": {"type": "minecraft:select", "property": "minecraft:display_context",
                      "cases": [{"when": ["gui", "ground", "fixed"], "model": by_stage("")}],
                      "fallback": by_stage("_3d")}}


def dump(path, obj):
    with open(path, "w") as f:
        json.dump(obj, f, indent="\t")
        f.write("\n")


if __name__ == "__main__":
    assets = sys.argv[1]
    preview = sys.argv[2] if len(sys.argv) > 2 else None
    gen3d.make_texture(f"{assets}/textures/item/laevatein_3d.png", SWATCH)
    sprites, renders = [], []
    for stage in STAGES:
        img = sprite(SPRITES[stage])
        img.save(f"{assets}/textures/item/laevatein_{stage}.png")
        dump(f"{assets}/models/item/laevatein_{stage}.json",
             {"parent": "minecraft:item/handheld", "textures": {"layer0": f"{NS}:item/laevatein_{stage}"}})
        m = MODELS[stage]()
        dump(f"{assets}/models/item/laevatein_{stage}_3d.json", gen3d.to_json(m, "laevatein_3d", SWATCH))
        sprites.append(img)
        renders.append(gen3d.render(m, swatch=SWATCH))
    dump(f"{assets}/items/laevatein.json", item_definition())
    icon_sprite().save(f"{assets}/textures/item/laevatein_icon.png")
    dump(f"{assets}/models/item/laevatein_icon.json",
         {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/laevatein_icon"}})
    dump(f"{assets}/items/laevatein_icon.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/laevatein_icon"}})
    if preview:
        sheet = Image.new("RGBA", (4 * 220, 400), (210, 210, 214, 255))
        for i, (img, r) in enumerate(zip(sprites, renders)):
            big = img.resize((160, 160), Image.NEAREST)
            sheet.paste(big, (i * 220 + 30, 10), big)
            sheet.paste(r, (i * 220, 180))
        sheet.save(preview)
