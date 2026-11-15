"""3D models for the ten two-star weapons (our own low-poly designs, not the original's art), written into
paper/pack: models/item/weapon2/<id>.json, textures/item/weapon2/<id>.png (a 4x4 palette of 4-pixel colour
cells) and items/weapon2/<id>.json. The weapon stands along +Y with its grip at the bottom (y -8 to 0).

    python3 tools/weapon2_models.py
"""
import json
import os

from PIL import Image

PACK = os.path.join("paper", "pack", "assets", "index_prescript")

DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "gui": {"rotation": [0, 90, -45], "translation": [0, 0, 0], "scale": [0.55, 0.55, 0.55]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.45, 0.45, 0.45]},
    "fixed": {"rotation": [0, 90, -45], "translation": [0, 0, 0], "scale": [0.7, 0.7, 0.7]},
    "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]},
}


def palette(colours):
    """16 colours -> 16x16 texture, cell i at (i % 4, i // 4), each 4x4 with a light top edge."""
    im = Image.new("RGBA", (16, 16))
    px = im.load()
    for i, c in enumerate(colours):
        x0, y0 = (i % 4) * 4, (i // 4) * 4
        for y in range(4):
            for x in range(4):
                k = 1.12 if y == 0 else 0.9 if y == 3 else 1.0
                px[x0 + x, y0 + y] = tuple(max(0, min(255, int(v * k))) for v in c) + (255,)
    return im


def uv(i):
    x0, y0 = (i % 4) * 4, (i // 4) * 4
    return [x0 + 0.5, y0 + 0.5, x0 + 3.5, y0 + 3.5]


def box(frm, to, colour, rot=None):
    e = {"from": frm, "to": to, "faces": {f: {"uv": uv(colour), "texture": "#t"} for f in ("north", "south", "east", "west", "up", "down")}}
    if rot:
        e["rotation"] = rot
    return e


def r(angle, axis, origin):
    return {"angle": angle, "axis": axis, "origin": origin}


def grip(c_grip, c_pommel, top=0, bottom=-8, w=1.6):
    a, b = 8 - w / 2, 8 + w / 2
    return [box([a, bottom, a], [b, top, b], c_grip), box([a - 0.4, bottom - 1.2, a - 0.4], [b + 0.4, bottom, b + 0.4], c_pommel)]


# Colour indices: 0 steel, 1 steel dark, 2 edge light, 3 gold, 4 leather, 5 wrap, 6 accent A, 7 accent B,
# 8 accent C, 9 black, 10 white, 11 red, 12 cyan, 13 orange, 14 green, 15 brown
BASE = [(190, 195, 205), (110, 115, 125), (235, 240, 248), (215, 170, 60), (90, 60, 40), (40, 35, 35), (60, 120, 80), (200, 200, 120),
        (120, 90, 160), (25, 25, 30), (240, 240, 240), (190, 40, 40), (80, 220, 240), (240, 140, 40), (60, 150, 90), (120, 80, 50)]


def colours(**over):
    c = list(BASE)
    for k, v in over.items():
        c[int(k[1:])] = v
    return c


def seven_rapier():
    # Seven Association: a long thin blade, a swept cup hilt in dark green and brass.
    e = grip(5, 3)
    e += [box([7.6, 0, 7.4], [8.4, 23, 8.6], 0), box([7.75, 23, 7.55], [8.25, 25, 8.45], 2)]
    e += [box([6, -0.6, 6], [10, 0.4, 10], 6), box([5.5, -0.2, 7.6], [10.5, 0.4, 8.4], 3)]
    e += [box([9.6, -6, 7.7], [10.2, 0, 8.3], 3), box([8, -6.4, 7.7], [10.2, -5.8, 8.3], 3)]
    return e, colours(c6=(40, 90, 60))


def shi_blade():
    # Shi Association: a single-edged blade curving slightly near the tip, round guard, dark wrap.
    e = grip(9, 1, w=1.8)
    e += [box([7.5, 0.6, 7.3], [8.5, 14, 8.7], 0), box([7.5, 0.6, 8.7], [8.5, 14, 8.95], 2)]
    e += [box([7.5, 13, 7.3], [8.5, 22, 8.7], 0, r(-22.5, "x", [8, 13, 8])), box([7.5, 13, 8.7], [8.5, 22, 8.95], 2, r(-22.5, "x", [8, 13, 8]))]
    e += [box([6.2, -0.2, 6.2], [9.8, 0.6, 9.8], 9), box([6.8, -0.2, 5.8], [9.2, 0.6, 10.2], 9)]
    return e, colours()


def kleinhammer():
    # N Corp small hammer: a plain shaft and a blocky head with a nail-pulling claw.
    e = [box([7.3, -8, 7.3], [8.7, 12, 8.7], 15), box([7.1, -8.6, 7.1], [8.9, -6, 8.9], 4)]
    e += [box([5, 12, 6], [11, 16, 10], 1), box([10.6, 12.5, 6.5], [12.2, 15.5, 9.5], 0), box([3.2, 13, 7], [5, 15, 9], 1, r(-22.5, "z", [5, 14, 8]))]
    e += [box([7.6, 16, 7.6], [8.4, 19, 8.4], 3)]
    return e, colours()


def liu_gauntlet():
    # Liu Association: a heavy gauntlet, red and gold, knuckles forward.
    e = [box([5, -6, 5], [11, 4, 11], 11), box([4.6, 0, 4.6], [11.4, 6, 11.4], 11)]
    e += [box([4.2, 4, 5.4], [11.8, 6.6, 10.6], 3), box([4.4, -6.6, 4.4], [11.6, -5, 11.6], 3)]
    for i, z in enumerate((5.6, 7.3, 9.0)):
        e.append(box([10.8, 1.5, z], [12.2, 4, z + 1.4], 13))
    e += [box([5.8, 6.6, 6.6], [10.2, 7.6, 9.4], 9)]
    return e, colours(c11=(170, 30, 30))


def zwei_sword():
    # Zwei Association: a broad two-handed sword, long grip, wide crossguard.
    e = grip(4, 1, top=0, bottom=-8, w=2.0)
    e += [box([6.5, 0.8, 7.2], [9.5, 24, 8.8], 0), box([7.6, 0.8, 7.0], [8.4, 22, 9.0], 1), box([7, 24, 7.4], [9, 26, 8.6], 2)]
    e += [box([2.5, -0.4, 7], [13.5, 0.8, 9], 1), box([2, -0.6, 7.3], [3, 1, 8.7], 3), box([13, -0.6, 7.3], [14, 1, 8.7], 3)]
    return e, colours()


def kurokumo_katana():
    # Kurokumo Clan: a black-bladed katana with a red edge and a square guard.
    e = grip(11, 9, w=1.7)
    e += [box([7.5, 0.6, 7.4], [8.5, 15, 8.6], 9), box([7.5, 0.6, 8.6], [8.5, 15, 8.9], 11)]
    e += [box([7.5, 14, 7.4], [8.5, 23, 8.6], 9, r(-22.5, "x", [8, 14, 8])), box([7.5, 14, 8.6], [8.5, 23, 8.9], 11, r(-22.5, "x", [8, 14, 8]))]
    e += [box([6, -0.2, 6], [10, 0.6, 10], 3)]
    return e, colours()


def w_corp_blade():
    # W Corp cleanup baton: a dark rod banded with glowing cyan cells.
    e = [box([7.1, -8, 7.1], [8.9, 18, 8.9], 9)]
    for y in (-2, 3, 8, 13):
        e.append(box([6.8, y, 6.8], [9.2, y + 2.4, 9.2], 12))
    e += [box([6.6, 18, 6.6], [9.4, 20, 9.4], 1), box([7.4, 20, 7.4], [8.6, 22, 8.6], 12), box([6.8, -8.8, 6.8], [9.2, -7, 9.2], 1)]
    return e, colours()


def t_corp_mace():
    # T Corp collection staff: a brass mace whose head is a clock face ringed with spikes.
    e = [box([7.3, -8, 7.3], [8.7, 13, 8.7], 4), box([7, -8.8, 7], [9, -7, 9], 3)]
    e += [box([5, 13, 7], [11, 19, 9], 3), box([5.6, 12.4, 6.4], [10.4, 19.6, 9.6], 7)]
    for (x0, y0, x1, y1) in ((7.4, 19.6, 8.6, 21.4), (7.4, 10.6, 8.6, 12.4), (3.2, 15.4, 5, 16.6), (11, 15.4, 12.8, 16.6)):
        e.append(box([x0, y0, 7.5], [x1, y1, 8.5], 1))
    e += [box([7.8, 15.2, 6.2], [8.2, 18.4, 6.4], 9), box([7.8, 15.6, 6.2], [10, 16, 6.4], 9)]
    return e, colours(c7=(230, 220, 190))


def cinq_epee():
    # Cinq Association: a straight épée with a wide round bell guard in silver and blue.
    e = grip(8, 0)
    e += [box([7.7, 0.4, 7.6], [8.3, 24, 8.4], 2), box([7.6, 0.4, 7.5], [8.4, 4, 8.5], 0)]
    e += [box([5, -0.2, 5], [11, 0.4, 11], 0), box([5.6, -0.6, 5.6], [10.4, 0.6, 10.4], 8), box([4.6, 0, 6.2], [11.4, 0.3, 9.8], 0)]
    return e, colours(c8=(70, 90, 170))


def mariachi_maracas():
    # Los Mariachis: a maraca, a painted gourd on a short handle.
    e = [box([7.3, -8, 7.3], [8.7, 2, 8.7], 15), box([7, -8.6, 7], [9, -7.4, 9], 3)]
    e += [box([5, 2, 5], [11, 10, 11], 13), box([5.6, 1.4, 5.6], [10.4, 10.6, 10.4], 13), box([4.4, 3.5, 6], [11.6, 8.5, 10], 13)]
    e += [box([4.3, 5.5, 5.8], [11.7, 6.5, 10.2], 11), box([5.4, 4, 4.9], [10.6, 4.6, 11.1], 14), box([5.4, 7.4, 4.9], [10.6, 8, 11.1], 3)]
    return e, colours()


WEAPONS = [seven_rapier, shi_blade, kleinhammer, liu_gauntlet, zwei_sword, kurokumo_katana, w_corp_blade, t_corp_mace, cinq_epee,
           mariachi_maracas]


def main():
    for d in ("textures/item/weapon2", "models/item/weapon2", "items/weapon2"):
        os.makedirs(os.path.join(PACK, d), exist_ok=True)
    for fn in WEAPONS:
        wid = fn.__name__
        elements, cols = fn()
        palette(cols).save(os.path.join(PACK, "textures/item/weapon2", wid + ".png"))
        tex = "index_prescript:item/weapon2/" + wid
        model = {"textures": {"t": tex, "particle": tex}, "elements": elements, "display": DISPLAY}
        json.dump(model, open(os.path.join(PACK, "models/item/weapon2", wid + ".json"), "w"), indent=1)
        json.dump({"model": {"type": "minecraft:model", "model": tex}}, open(os.path.join(PACK, "items/weapon2", wid + ".json"), "w"), indent=1)
    print(len(WEAPONS), "weapon models")


if __name__ == "__main__":
    main()
