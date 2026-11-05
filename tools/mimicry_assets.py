#!/usr/bin/env python3
"""Builds Mimicry (Gebura's E.G.O, Library of Ruina) from the art.

Source: tools/art/mimicry_blade.png, the upright blade cut out of the
reference picture (pommel at the top, tip at the bottom).

Writes into the plugin pack (paper/pack/assets/index_prescript):
- textures/item/mimicry_3d.png, mimicry_ego_3d.png: the blade, downscaled to
  a pixel grid, on a 128x128 sheet (the E.G.O one re-coloured: deeper,
  glowing reds, a crimson steel edge, burning eyes);
- models/item/mimicry_3d.json, mimicry_ego_3d.json: the held model, built
  from the pixel grid like an extruded sprite. Every run of pixels on a row
  becomes a box, thicker for the flesh than for the steel edge, and boxes of
  the same span on consecutive rows are merged. As with the other weapons
  (tools/gen3d.py) it is designed upright, grip at the bottom on x=8, z=8,
  and tilted -45 degrees about Z so the vanilla handheld transforms fit;
- textures/item/mimicry.png, mimicry_ego.png and models/item/mimicry.json,
  mimicry_ego.json: the 32x32 inventory icons;
- items/mimicry.json: icon in the GUI, on the ground and in frames, the 3D
  model in hand; the first custom model data string "ego" picks the E.G.O look.
Also writes build/mimicry_preview.png.

Run from the repository root: python3 tools/mimicry_assets.py
"""
import colorsys
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "tools/art/mimicry_blade.png"
PACK = ROOT / "paper/pack/assets/index_prescript"
NS = "index_prescript"

GRID_W, GRID_H = 30, 112
SHEET = 128
UV = 16 / SHEET
# Upright: the pommel's end at BOTTOM, the tip at TOP (model units).
BOTTOM, TOP = -3.4, 28.0
PX = (TOP - BOTTOM) / GRID_H
# Rows (from the pommel) that are grip: thinner than the blade.
GRIP_ROWS = 22
THICK = {"grip": 0.9, "steel": 0.55, "flesh": 1.25, "bone": 0.85}
TILT = -45.0
ORIGIN = [8.0, 8.0, 8.0]
DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
}


def grid():
    """The blade on its pixel grid, tip up (row 0 = the tip), hard alpha."""
    art = Image.open(SOURCE).convert("RGBA").transpose(Image.Transpose.ROTATE_180)
    small = art.resize((GRID_W, GRID_H), Image.Resampling.LANCZOS)
    px = small.load()
    for y in range(GRID_H):
        for x in range(GRID_W):
            r, g, b, a = px[x, y]
            px[x, y] = (r, g, b, 255) if a >= 110 else (0, 0, 0, 0)
    return small


def kind(rgb, row):
    """What a pixel is made of, for its thickness: grip, steel, flesh or bone."""
    if row >= GRID_H - GRIP_ROWS:
        return "grip"
    r, g, b = (c / 255 for c in rgb)
    h, s, v = colorsys.rgb_to_hsv(r, g, b)
    if s < 0.28 and v > 0.62:
        return "bone"
    if s < 0.35:
        return "steel"
    return "flesh"


def ego(img):
    """The manifested look: reds deepen and glow, the steel takes a crimson sheen, the eyes burn."""
    out = img.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if s >= 0.35 and (h < 0.08 or h > 0.9):
                # Flesh: hotter, brighter red with an orange edge to the highlights.
                v = min(1.0, v * 1.25 + 0.05)
                s = min(1.0, s * 1.1)
                h = 0.995 if v < 0.75 else 0.02
            elif s >= 0.25 and 0.25 < h < 0.75:
                # The eyes' irises: burning gold.
                h, s, v = 0.11, 0.95, min(1.0, v * 1.3 + 0.2)
            elif s < 0.35 and v <= 0.62:
                # Steel: crimson sheen.
                h, s, v = 0.99, min(1.0, s + 0.45), min(1.0, v * 1.05)
            elif s < 0.28:
                # Bone and the whites of the eyes: a faint red cast.
                h, s = 0.0, min(1.0, s + 0.18)
            r2, g2, b2 = colorsys.hsv_to_rgb(h, s, v)
            px[x, y] = (round(r2 * 255), round(g2 * 255), round(b2 * 255), a)
    return out


def sheet(img):
    canvas = Image.new("RGBA", (SHEET, SHEET), (0, 0, 0, 0))
    canvas.paste(img, (0, 0))
    return canvas


def r(v):
    return round(v, 4)


def model(img, texture):
    px = img.load()
    # Runs per row: (x0, x1, kind).
    rows = []
    for y in range(GRID_H):
        runs = []
        x = 0
        while x < GRID_W:
            if px[x, y][3] == 0:
                x += 1
                continue
            k = kind(px[x, y][:3], y)
            x0 = x
            while x < GRID_W and px[x, y][3] and kind(px[x, y][:3], y) == k:
                x += 1
            runs.append((x0, x, k))
        rows.append(runs)
    # Merge identical runs on consecutive rows into one box: (x0, x1, kind, y0, y1).
    boxes = []
    open_boxes = {}
    for y, runs in enumerate(rows):
        current = {}
        for run in runs:
            if run in open_boxes:
                box = open_boxes.pop(run)
                box[4] = y + 1
            else:
                box = [run[0], run[1], run[2], y, y + 1]
                boxes.append(box)
            current[run] = box
        open_boxes = current
    tex = f"{NS}:item/{texture}"
    centre = GRID_W / 2
    elements = []
    for x0, x1, k, y0, y1 in boxes:
        t = THICK[k] / 2
        fx0, fx1 = 8 + (x0 - centre) * PX, 8 + (x1 - centre) * PX
        # Row 0 is the tip, at TOP.
        fy0, fy1 = TOP - y1 * PX, TOP - y0 * PX
        for c in (fx0, fx1, fy0, fy1):
            assert -16 <= c <= 32, c
        u0, u1, v0, v1 = x0 * UV, x1 * UV, y0 * UV, y1 * UV
        faces = {
            "south": {"uv": [r(u0), r(v0), r(u1), r(v1)], "texture": "#0"},
            "north": {"uv": [r(u1), r(v0), r(u0), r(v1)], "texture": "#0"},
            "east": {"uv": [r(u1 - UV), r(v0), r(u1), r(v1)], "texture": "#0"},
            "west": {"uv": [r(u0), r(v0), r(u0 + UV), r(v1)], "texture": "#0"},
            "up": {"uv": [r(u0), r(v0), r(u1), r(v0 + UV)], "texture": "#0"},
            "down": {"uv": [r(u0), r(v1 - UV), r(u1), r(v1)], "texture": "#0"},
        }
        elements.append({
            "from": [r(fx0), r(fy0), r(8 - t)],
            "to": [r(fx1), r(fy1), r(8 + t)],
            "rotation": {"angle": TILT, "axis": "z", "origin": ORIGIN},
            "faces": faces,
        })
    return {"textures": {"0": tex, "particle": tex}, "elements": elements, "display": DISPLAY}


def icon(art):
    """32x32 inventory icon: the full art laid along the diagonal, tip top right, outlined."""
    tilted = art.rotate(135, resample=Image.Resampling.BICUBIC, expand=True)
    tilted = tilted.crop(tilted.getbbox())
    side = max(tilted.size)
    square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    square.paste(tilted, ((side - tilted.width) // 2, (side - tilted.height) // 2))
    small = square.resize((30, 30), Image.Resampling.LANCZOS)
    out = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px = small.load()
    for y in range(30):
        for x in range(30):
            rr, gg, bb, a = px[x, y]
            if a >= 100:
                out.putpixel((x + 1, y + 1), (rr, gg, bb, 255))
    # One-pixel dark outline.
    edge = out.copy()
    for y in range(32):
        for x in range(32):
            if out.getpixel((x, y))[3]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 32 and 0 <= ny < 32 and out.getpixel((nx, ny))[3]:
                    edge.putpixel((x, y), (20, 6, 8, 255))
                    break
    return edge


def write(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent="\t") + "\n")


def item_definition():
    def m(name):
        return {"type": "minecraft:model", "model": f"{NS}:item/{name}"}

    def by_state(base, manifested):
        return {
            "type": "minecraft:select",
            "property": "minecraft:custom_model_data",
            "index": 0,
            "cases": [{"when": "ego", "model": m(manifested)}],
            "fallback": m(base),
        }

    return {"model": {
        "type": "minecraft:select",
        "property": "minecraft:display_context",
        "cases": [{"when": ["gui", "ground", "fixed"], "model": by_state("mimicry", "mimicry_ego")}],
        "fallback": by_state("mimicry_3d", "mimicry_ego_3d"),
    }}


def main():
    base = grid()
    manifested = ego(base)
    textures = PACK / "textures/item"
    textures.mkdir(parents=True, exist_ok=True)
    sheet(base).save(textures / "mimicry_3d.png")
    sheet(manifested).save(textures / "mimicry_ego_3d.png")
    built = model(base, "mimicry_3d")
    write(PACK / "models/item/mimicry_3d.json", built)
    ego_model = dict(built)
    tex = f"{NS}:item/mimicry_ego_3d"
    ego_model["textures"] = {"0": tex, "particle": tex}
    write(PACK / "models/item/mimicry_ego_3d.json", ego_model)

    art = Image.open(SOURCE).convert("RGBA")
    icon(art).save(textures / "mimicry.png")
    icon(ego(art)).save(textures / "mimicry_ego.png")
    for name in ("mimicry", "mimicry_ego"):
        write(PACK / f"models/item/{name}.json", {"parent": "minecraft:item/handheld", "textures": {"layer0": f"{NS}:item/{name}"}})
    write(PACK / "items/mimicry.json", item_definition())

    preview = Image.new("RGBA", (GRID_W * 8 + 32 * 8 + 24, GRID_H * 4), (54, 54, 62, 255))
    preview.alpha_composite(base.resize((GRID_W * 4, GRID_H * 4), Image.Resampling.NEAREST), (0, 0))
    preview.alpha_composite(manifested.resize((GRID_W * 4, GRID_H * 4), Image.Resampling.NEAREST), (GRID_W * 4 + 8, 0))
    preview.alpha_composite(icon(art).resize((256, 256), Image.Resampling.NEAREST), (GRID_W * 8 + 16, 0))
    preview.alpha_composite(icon(ego(art)).resize((128, 128), Image.Resampling.NEAREST), (GRID_W * 8 + 16, 260))
    (ROOT / "build").mkdir(exist_ok=True)
    preview.save(ROOT / "build/mimicry_preview.png")
    print(f"{len(built['elements'])} elements")


if __name__ == "__main__":
    main()
