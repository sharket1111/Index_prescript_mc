"""Builds the Extraction scene's models into paper/pack (our own art, not the original's):
a spiky shard per rarity (one, two, three, ego) and the ball of chain (plain and gold).

    python3 tools/extraction_models.py
"""
import json
import math
import os

from PIL import Image, ImageDraw

PACK = os.path.join("paper", "pack", "assets", "index_prescript")
TEX = os.path.join(PACK, "textures", "item", "extraction")
MOD = os.path.join(PACK, "models", "item", "extraction")
ITEMS = os.path.join(PACK, "items", "extraction")

RARITY = {
    "one": ((70, 64, 60), (210, 210, 215)),
    "two": ((150, 26, 18), (235, 90, 60)),
    "three": ((200, 120, 20), (255, 220, 120)),
    "ego": ((170, 40, 20), (255, 140, 70)),
}


def shard_texture(core, glow):
    """Left half: the burning core; right half: the pale spikes, coloured at their roots."""
    im = Image.new("RGBA", (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(8):
            d = math.hypot(x - 3.5, y % 8 - 3.5) / 5
            t = max(0.0, 1 - d)
            px[x, y] = tuple(int(core[i] * (1 - t) + glow[i] * t) for i in range(3)) + (255,)
        for x in range(8, 16):
            t = y / 15
            base = (240, 240, 245)
            px[x, y] = tuple(int(base[i] * (1 - t * 0.6) + glow[i] * t * 0.6) for i in range(3)) + (255,)
    return im


def chain_texture(gold):
    """Links of chain over embers."""
    im = Image.new("RGBA", (16, 16), (60, 30, 12, 255) if not gold else (120, 80, 20, 255))
    d = ImageDraw.Draw(im)
    for y in range(-2, 16, 4):
        for x in range(-2, 16, 6):
            off = 3 if (y // 4) % 2 else 0
            d.ellipse((x + off, y, x + off + 5, y + 3), outline=(150, 140, 130, 255) if not gold else (230, 200, 110, 255))
    for (x, y) in ((3, 5), (11, 9), (6, 13), (13, 2)):
        d.point((x, y), fill=(255, 170, 60, 255) if not gold else (255, 240, 170, 255))
    return im


def face_all(uv):
    return {f: {"uv": uv, "texture": "#t"} for f in ("north", "south", "east", "west", "up", "down")}


def shard_model(tex):
    els = [{"from": [5, 5, 5], "to": [11, 11, 11], "faces": face_all([0, 0, 8, 8])}]
    # Spikes: thin bars through the core, turned on each axis by 0 and +-45 degrees.
    for axis in ("x", "y", "z"):
        for angle in (-45, 0, 45):
            if angle == 0 and axis != "y":
                continue
            els.append({"from": [7.25, -1, 7.25], "to": [8.75, 17, 8.75], "rotation": {"angle": angle, "axis": axis, "origin": [8, 8, 8]},
                        "faces": face_all([8, 0, 10, 16])})
    for axis in ("x", "z"):
        for angle in (-22.5, 22.5):
            els.append({"from": [7.5, 0, 7.5], "to": [8.5, 16, 8.5], "rotation": {"angle": angle, "axis": axis, "origin": [8, 8, 8]},
                        "faces": face_all([10, 0, 12, 16])})
    # Across, so it bristles on every side.
    els.append({"from": [-1, 7.25, 7.25], "to": [17, 8.75, 8.75], "faces": face_all([8, 0, 10, 16])})
    els.append({"from": [7.25, 7.25, -1], "to": [8.75, 8.75, 17], "faces": face_all([8, 0, 10, 16])})
    return {"textures": {"t": tex, "particle": tex}, "elements": els,
            "display": {"fixed": {"scale": [1, 1, 1]}, "gui": {"rotation": [30, 45, 0], "scale": [0.7, 0.7, 0.7]}}}


def ball_model(tex):
    els = []
    # A rounded ball: three overlapping cubes turned on each axis.
    els.append({"from": [3, 3, 3], "to": [13, 13, 13], "faces": face_all([0, 0, 16, 16])})
    for axis in ("x", "y", "z"):
        els.append({"from": [3.5, 3.5, 3.5], "to": [12.5, 12.5, 12.5], "rotation": {"angle": 45, "axis": axis, "origin": [8, 8, 8]},
                    "faces": face_all([0, 0, 16, 16])})
    return {"textures": {"t": tex, "particle": tex}, "elements": els,
            "display": {"fixed": {"scale": [1, 1, 1]}, "gui": {"rotation": [30, 45, 0], "scale": [0.8, 0.8, 0.8]}}}


def item(model):
    return {"model": {"type": "minecraft:model", "model": model}}


def main():
    for d in (TEX, MOD, ITEMS):
        os.makedirs(d, exist_ok=True)
    for name, (core, glow) in RARITY.items():
        shard_texture(core, glow).save(os.path.join(TEX, "shard_%s.png" % name))
        json.dump(shard_model("index_prescript:item/extraction/shard_%s" % name), open(os.path.join(MOD, "shard_%s.json" % name), "w"), indent=1)
        json.dump(item("index_prescript:item/extraction/shard_%s" % name), open(os.path.join(ITEMS, "shard_%s.json" % name), "w"), indent=1)
    for name, gold in (("chain_ball", False), ("chain_ball_gold", True)):
        chain_texture(gold).save(os.path.join(TEX, name + ".png"))
        json.dump(ball_model("index_prescript:item/extraction/" + name), open(os.path.join(MOD, name + ".json"), "w"), indent=1)
        json.dump(item("index_prescript:item/extraction/" + name), open(os.path.join(ITEMS, name + ".json"), "w"), indent=1)
    print("extraction models written")


if __name__ == "__main__":
    main()
