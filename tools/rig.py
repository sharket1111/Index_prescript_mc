"""Player rig models for studio animations (paper/pack).

A rig is six item displays (head, body, arms, legs) that the plugin moves like
a player model. Each bone is an item model whose origin (8, 8, 8) is the
bone's joint, cut from a 64x64 skin in the standard layout (inner layer plus
the outer layer, slightly larger), so any skin PNG works as an outfit:

* the game's default skins (minecraft:entity/player/{wide,slim}/<name>), and
* custom outfits: assets/index_prescript/textures/item/rig/<name>.png
  (a name ending in _slim gets 3-pixel arms).

Item definition index_prescript:rig: custom_model_data strings [outfit, bone].
Run from the repository root: python3 tools/rig.py
"""
import json
import os

from PIL import Image, ImageDraw

PACK = "paper/pack/assets"
NS = os.path.join(PACK, "index_prescript")
DEFAULTS = ["steve", "alex", "ari", "efe", "kai", "makena", "noor", "sunny", "zuri"]
BONES = ["head", "body", "right_arm", "left_arm", "right_leg", "left_leg"]

# Bone boxes in pixels relative to the joint, in the player's frame:
# x to the player's right, y up, z forward. (offset, size w h d, inner uv, outer uv, inflate)
def boxes(slim):
    aw = 3 if slim else 4
    return {
        "head": ((-4, 0, -4), (8, 8, 8), (0, 0), (32, 0), 0.5),
        "body": ((-4, -12, -2), (8, 12, 4), (16, 16), (16, 32), 0.25),
        "right_arm": ((-1, -10, -2), (aw, 12, 4), (40, 16), (40, 32), 0.25),
        "left_arm": ((1 - aw, -10, -2), (aw, 12, 4), (32, 48), (48, 48), 0.25),
        "right_leg": ((-2, -12, -2), (4, 12, 4), (0, 16), (0, 32), 0.25),
        "left_leg": ((-2, -12, -2), (4, 12, 4), (16, 48), (0, 48), 0.25),
    }


def element(off, size, uv, grow):
    """One box. Model space: x = player's right, y up, -z = forward (item displays turn models 180 degrees)."""
    (x, y, z), (w, h, d), (u, v) = off, size, uv
    frm = [8 + x - grow, 8 + y - grow, 8 - (z + d) - grow]
    to = [8 + x + w + grow, 8 + y + h + grow, 8 - z + grow]
    q = lambda *a: [round(c / 4, 4) for c in a]  # 64 px skin -> 0..16 uv
    faces = {
        # Character-relative regions of the skin layout.
        "north": q(u + d, v + d, u + d + w, v + d + h),            # front
        "south": q(u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),  # back
        "east": q(u, v + d, u + d, v + d + h),                   # the player's right side
        "west": q(u + d + w, v + d, u + 2 * d + w, v + d + h),   # left side
        "up": q(u + d + w, v + d, u + d, v),                     # top, front edge toward north
        "down": q(u + 2 * w + d, v, u + d + w, v + d),           # bottom
    }
    return {"from": [round(c, 4) for c in frm], "to": [round(c, 4) for c in to],
            "faces": {k: {"uv": uv_, "texture": "#skin"} for k, uv_ in faces.items()}}


def bone_model(bone, slim):
    off, size, inner, outer, grow = boxes(slim)[bone]
    return {
        "textures": {"skin": "minecraft:entity/player/wide/steve", "particle": "#skin"},
        "elements": [element(off, size, inner, 0), element(off, size, outer, grow)],
    }


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=1)
        f.write("\n")


def draw_suit(path, slim):
    """Our own outfit (a dark suit with a crimson tie), in the skin layout. It lives in the item atlas like every other mod texture."""
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    g = ImageDraw.Draw(img)

    def box(u, v, w, h, d, col):
        g.rectangle([u + d, v, u + d + 2 * w - 1, v + d - 1], fill=col)
        g.rectangle([u, v + d, u + 2 * (w + d) - 1, v + d + h - 1], fill=col)

    aw = 3 if slim else 4
    skin, hair, suit, shade, shirt, tie, pants, shoe = (201, 154, 116), (52, 38, 30), (34, 31, 38), (26, 24, 30), (226, 222, 214), (178, 44, 38), (40, 37, 46), (18, 16, 20)
    box(0, 0, 8, 8, 8, skin)
    g.rectangle([8, 0, 15, 7], fill=hair)
    g.rectangle([0, 8, 31, 9], fill=hair)
    g.rectangle([24, 8, 31, 15], fill=hair)
    for x, y, c in ((9, 12, (255, 255, 255)), (10, 12, (60, 70, 110)), (13, 12, (60, 70, 110)), (14, 12, (255, 255, 255))):
        img.putpixel((x, y), c + (255,))
    g.rectangle([11, 14, 12, 14], fill=(140, 92, 72))
    # Body: jacket, a shirt and tie down the front.
    box(16, 16, 8, 12, 4, suit)
    g.rectangle([22, 20, 25, 23], fill=shirt)
    g.rectangle([23, 20, 24, 27], fill=tie)
    g.rectangle([20, 20, 21, 31], fill=shade)
    g.rectangle([26, 20, 27, 31], fill=shade)
    # Arms: sleeves, hands at the bottom.
    for u, v in ((40, 16), (32, 48)):
        box(u, v, aw, 12, 4, suit)
        g.rectangle([u, v + 14, u + 2 * (aw + 4) - 1, v + 15], fill=skin)
        g.rectangle([u + 4 + aw, v, u + 4 + 2 * aw - 1, v + 3], fill=skin)
    # Legs: trousers and shoes.
    for u, v in ((0, 16), (16, 48)):
        box(u, v, 4, 12, 4, pants)
        g.rectangle([u, v + 13, u + 15, v + 15], fill=shoe)
        g.rectangle([u + 8, v, u + 11, v + 3], fill=shoe)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def main():
    custom_dir = os.path.join(NS, "textures/item/rig")
    draw_suit(os.path.join(custom_dir, "suit.png"), False)
    draw_suit(os.path.join(custom_dir, "suit_slim.png"), True)
    outfits = {"suit": ("index_prescript:item/rig/suit", False), "suit_slim": ("index_prescript:item/rig/suit_slim", True)}
    for name in DEFAULTS:
        outfits[name] = ("minecraft:entity/player/wide/" + name, False)
        outfits[name + "_slim"] = ("minecraft:entity/player/slim/" + name, True)
    custom = os.path.join(NS, "textures/item/rig")
    if os.path.isdir(custom):
        for f in sorted(os.listdir(custom)):
            if f.endswith(".png"):
                n = f[:-4]
                outfits[n] = ("index_prescript:item/rig/" + n, n.endswith("_slim"))

    models = os.path.join(NS, "models/item/rig")
    for slim in (False, True):
        for bone in BONES:
            write(os.path.join(models, "base", bone + ("_slim" if slim else "_wide") + ".json"), bone_model(bone, slim))
    cases = []
    for name, (tex, slim) in outfits.items():
        bone_cases = []
        for bone in BONES:
            parent = "index_prescript:item/rig/base/" + bone + ("_slim" if slim else "_wide")
            write(os.path.join(models, "outfit", name, bone + ".json"), {"parent": parent, "textures": {"skin": tex}})
            bone_cases.append({"when": bone, "model": {"type": "minecraft:model", "model": "index_prescript:item/rig/outfit/" + name + "/" + bone}})
        cases.append({"when": name, "model": {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 1,
                                             "cases": bone_cases, "fallback": bone_cases[1]["model"]}})
    write(os.path.join(NS, "items/rig.json"), {"model": {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
                                                         "cases": cases, "fallback": cases[0]["model"]}})
    # The default skins live outside the item atlas; add them (to both, item models may read either).
    src = {"sources": [{"type": "minecraft:directory", "source": "entity/player", "prefix": "entity/player/"}]}
    # One atlas only: a sprite in two atlases is a duplicate the game warns about.
    write(os.path.join(PACK, "minecraft/atlases/items.json"), src)
    stale = os.path.join(PACK, "minecraft/atlases/blocks.json")
    if os.path.exists(stale):
        os.remove(stale)
    print(len(outfits), "outfits:", ", ".join(outfits))


if __name__ == "__main__":
    main()
