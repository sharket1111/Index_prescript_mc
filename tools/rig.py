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


def main():
    outfits = {}
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
    for atlas in ("blocks", "items"):
        write(os.path.join(PACK, "minecraft/atlases", atlas + ".json"), src)
    print(len(outfits), "outfits:", ", ".join(outfits))


if __name__ == "__main__":
    main()
