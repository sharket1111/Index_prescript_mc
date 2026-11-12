"""3D models and animations for the Mirror Dungeon's enemies (our own block-style
models, shaped after each enemy's silhouette and colours; not the game's art).

A model is a tree of bones; each bone is one item display showing an item model
made of boxes (measured from the bone's joint), textured from one atlas per
model that is painted here (shading, outlines, marks such as eyes and ties).
The plugin (MdModels.java) poses the bones every tick from keyframe clips:
idle, walk, attack_slash / attack_pierce / attack_blunt, guard, stagger, roar,
death. Frame: x to the model's right, y up, z forward, in pixels (16 = a block).

Writes (from the repository root: python3 tools/md_models.py [preview.png]):
* paper/pack/assets/index_prescript/textures/item/md/<id>.png
* paper/pack/assets/index_prescript/models/item/md/<id>/<bone>.json
* paper/pack/assets/index_prescript/items/md/<id>.json  (item model index_prescript:md/<id>, custom model data [bone])
* paper/src/main/resources/md/models.json: bones, clips, parts, and each enemy's model
"""
import json
import math
import os
import random
import shutil
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

NS = "paper/pack/assets/index_prescript"
OUT = "paper/src/main/resources/md/models.json"
ENEMIES = "paper/src/main/resources/md/enemies.json"
RES = 2  # texels per pixel
FACES = ("north", "south", "east", "west", "up", "down")
SHADE = {"up": 1.1, "north": 1.0, "east": 0.9, "west": 0.9, "south": 0.82, "down": 0.68}


# --- maths (the same as the plugin's: JOML translate / rotateXYZ / scale) -------------------------

def rot_xyz(r):
    x, y, z = (math.radians(a) for a in r)
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    mx = np.array([[1, 0, 0, 0], [0, cx, -sx, 0], [0, sx, cx, 0], [0, 0, 0, 1.0]])
    my = np.array([[cy, 0, sy, 0], [0, 1, 0, 0], [-sy, 0, cy, 0], [0, 0, 0, 1.0]])
    mz = np.array([[cz, -sz, 0, 0], [sz, cz, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1.0]])
    return mx @ my @ mz


def trans(v):
    m = np.eye(4)
    m[:3, 3] = v
    return m


def scale(s):
    m = np.eye(4)
    m[0, 0] = m[1, 1] = m[2, 2] = s
    return m


def shade(c, f):
    return tuple(max(0, min(255, int(round(x * f)))) for x in c[:3])


# --- model ------------------------------------------------------------------------------------------

class Bone:
    def __init__(self, name, parent, pivot, rest):
        self.name, self.parent, self.pivot, self.rest = name, parent, np.array(pivot, float), tuple(rest)
        self.cubes = []
        self.scale = 1.0  # the bone's own size (a smaller head on a tall figure)


class Model:
    def __init__(self, mid, scale=1.0, height=32, anims="humanoid", bright=False):
        self.id, self.scale, self.height, self.anims, self.bright = mid, scale, height, anims, bright
        self.bones = {}
        self.parts = {}
        self.clips = {}
        self.rng = random.Random(mid)

    def bone(self, name, parent, pivot, rest=(0, 0, 0)):
        self.bones[name] = Bone(name, parent, pivot, rest)
        return name

    def box(self, bone, x, y, z, w, h, d, color, marks=None, grow=0.0, rel=False, plain=False):
        """A box with its minimum corner at (x, y, z) in the model (or from the bone's joint if rel)."""
        b = self.bones[bone]
        lo = np.array([x, y, z], float) - (0 if rel else b.pivot) - grow
        size = np.array([w, h, d], float) + 2 * grow
        assert (lo > -24.01).all() and (lo + size < 24.01).all(), (self.id, bone, lo, size)
        b.cubes.append({"lo": lo, "size": size, "color": tuple(color[:3]), "marks": marks or {}, "plain": plain})

    def mirror_box(self, bone_r, bone_l, x, y, z, w, h, d, color, **kw):
        self.box(bone_r, x, y, z, w, h, d, color, **kw)
        self.box(bone_l, -x - w, y, z, w, h, d, color, **kw)

    # --- matrices ---

    def order(self):
        out, seen = [], set()

        def visit(n):
            if n in seen:
                return
            b = self.bones[n]
            if b.parent:
                visit(b.parent)
            seen.add(n)
            out.append(n)
        for n in self.bones:
            visit(n)
        return out

    def world(self, pose=None):
        """Bone name -> matrix taking the bone's local pixels to model pixels, in a pose {bone: (rot, pos, scale)}."""
        out = {}
        for n in self.order():
            b = self.bones[n]
            r, p, s = (pose or {}).get(n, ((0, 0, 0), (0, 0, 0), 1.0))
            dp = b.pivot - (self.bones[b.parent].pivot if b.parent else 0)
            local = trans(dp) @ trans(p) @ rot_xyz(tuple(a + c for a, c in zip(b.rest, r))) @ scale(s * b.scale)
            out[n] = (out[b.parent] @ local) if b.parent else local
        return out


def face_dims(size):
    w, h, d = size
    return {"north": (w, h), "south": (w, h), "east": (d, h), "west": (d, h), "up": (w, d), "down": (w, d)}


def build_atlas(model):
    """Packs every face of every box into a square texture and paints it. Sets cube['uv'][face] = (x, y, w, h) texels."""
    rects = []
    for b in model.bones.values():
        for c in b.cubes:
            c["uv"] = {}
            for f, (w, h) in face_dims(c["size"]).items():
                rects.append((max(1, int(round(w * RES))), max(1, int(round(h * RES))), c, f))
    rects.sort(key=lambda r: (-r[1], -r[0]))
    for size in (32, 64, 128, 256, 512):
        x = y = row = 0
        ok = True
        for w, h, c, f in rects:
            if x + w > size:
                x, y, row = 0, y + row, 0
            if y + h > size or w > size:
                ok = False
                break
            c["uv"][f] = (x, y, w, h)
            x += w
            row = max(row, h)
        if ok:
            break
    assert ok, model.id
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    rng = model.rng
    for b in model.bones.values():
        for c in b.cubes:
            for f in FACES:
                x0, y0, w, h = c["uv"][f]
                base = shade(c["color"], SHADE[f] if not c["plain"] else 1.0)
                edge = shade(base, 0.72)
                for j in range(h):
                    for i in range(w):
                        col = shade(base, 1 + (rng.random() - 0.5) * 0.06 - 0.10 * j / max(1, h))
                        if not c["plain"] and w >= 5 and h >= 5 and (i == 0 or j == 0 or i == w - 1 or j == h - 1):
                            col = edge
                        px[x0 + i, y0 + j] = col + (255,)
                fw, fh = face_dims(c["size"])[f]
                for (mx, my, mw, mh, mc) in c["marks"].get(f, []):
                    ax, ay = int(round(mx / fw * w)), int(round(my / fh * h))
                    bx, by = int(round((mx + mw) / fw * w)), int(round((my + mh) / fh * h))
                    for j in range(max(0, ay), min(h, max(by, ay + 1))):
                        for i in range(max(0, ax), min(w, max(bx, ax + 1))):
                            px[x0 + i, y0 + j] = tuple(mc[:3]) + (255,)
    model.atlas = img
    return img


def element(c, size):
    lo, s = c["lo"], c["size"]
    frm = [8 + lo[0], 8 + lo[1], 8 - (lo[2] + s[2])]
    to = [8 + lo[0] + s[0], 8 + lo[1] + s[1], 8 - lo[2]]
    faces = {}
    for f in FACES:
        x, y, w, h = c["uv"][f]
        e = 0.02
        faces[f] = {"uv": [round((x + e) * 16 / size, 3), round((y + e) * 16 / size, 3), round((x + w - e) * 16 / size, 3),
                           round((y + h - e) * 16 / size, 3)], "texture": "#t"}
    return {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to], "faces": faces}


def export(model):
    size = model.atlas.size[0]
    tex = "index_prescript:item/md/" + model.id
    os.makedirs(os.path.join(NS, "textures/item/md"), exist_ok=True)
    model.atlas.save(os.path.join(NS, "textures/item/md", model.id + ".png"))
    mdir = os.path.join(NS, "models/item/md", model.id)
    os.makedirs(mdir, exist_ok=True)
    cases = []
    for n, b in model.bones.items():
        if not b.cubes:
            continue
        with open(os.path.join(mdir, n + ".json"), "w") as f:
            json.dump({"textures": {"t": tex, "particle": "#t"}, "elements": [element(c, size) for c in b.cubes], "display": {}}, f, separators=(",", ":"))
        cases.append({"when": n, "model": {"type": "minecraft:model", "model": "index_prescript:item/md/%s/%s" % (model.id, n)}})
    os.makedirs(os.path.join(NS, "items/md"), exist_ok=True)
    with open(os.path.join(NS, "items/md", model.id + ".json"), "w") as f:
        json.dump({"model": {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0, "cases": cases,
                             "fallback": cases[0]["model"]}}, f, separators=(",", ":"))
    order = model.order()
    bones = []
    for n in order:
        b = model.bones[n]
        dp = b.pivot - (model.bones[b.parent].pivot if b.parent else 0)
        bones.append({"name": n, "parent": order.index(b.parent) if b.parent else -1, "at": [round(float(v), 3) for v in dp],
                      "rest": list(b.rest), "draw": bool(b.cubes), **({"s": b.scale} if b.scale != 1.0 else {})})
    out = {"scale": model.scale, "height": model.height, "anims": model.anims, "bones": bones, "parts": {k: [b for b in v if b in model.bones] for k, v in model.parts.items()}}
    if model.bright:
        out["bright"] = True
    ps = getattr(model, "pscale", 1.0)
    if ps != 1.0 and not model.clips and model.anims in ANIMSETS:
        # A shorter or taller skeleton: the shared clips with their movements scaled.
        model.clips = {n: {"len": c["len"], "loop": c["loop"], "tracks": {b: [[k[0], k[1], k[2], k[3], k[4] * ps, k[5] * ps, k[6] * ps, k[7]] for k in t]
                                                                             for b, t in c["tracks"].items()}} for n, c in ANIMSETS[model.anims].items()}
    if model.clips:
        out["clips"] = model.clips
    return out


# --- clips ------------------------------------------------------------------------------------------

def K(t, r=(0, 0, 0), p=(0, 0, 0), s=1.0):
    return [t, r[0], r[1], r[2], p[0], p[1], p[2], s]


def clip(length, loop, **tracks):
    return {"len": length, "loop": loop, "tracks": {k: v for k, v in tracks.items()}}


def sample(track, t):
    a = b = track[0]
    for k in track:
        if k[0] <= t:
            a = k
        if k[0] >= t:
            b = k
            break
        b = k
    f = 0 if b[0] == a[0] else (t - a[0]) / (b[0] - a[0])
    f = f * f * (3 - 2 * f)
    v = [a[i] + (b[i] - a[i]) * f for i in range(1, 8)]
    return (tuple(v[0:3]), tuple(v[3:6]), v[6])


def pose_of(c, t):
    return {bone: sample(track, t) for bone, track in c["tracks"].items()}


def humanoid_clips():
    c = {}
    c["idle"] = clip(60, True,
                     torso=[K(0), K(30, (1.5, 0, 0)), K(60)],
                     head=[K(0), K(30, (-1.5, 0, 0)), K(60)],
                     arm_r=[K(0, (0, 0, 5)), K(30, (0, 0, 7)), K(60, (0, 0, 5))],
                     arm_l=[K(0, (0, 0, -5)), K(30, (0, 0, -7)), K(60, (0, 0, -5))],
                     arm_r_low=[K(0, (-14, 0, 0)), K(60, (-14, 0, 0))],
                     arm_l_low=[K(0, (-10, 0, 0)), K(60, (-10, 0, 0))])
    c["walk"] = clip(20, True,
                     root=[K(0, p=(0, -0.5, 0)), K(5, p=(0, 0.4, 0)), K(10, p=(0, -0.5, 0)), K(15, p=(0, 0.4, 0)), K(20, p=(0, -0.5, 0))],
                     torso=[K(0, (4, 6, 0)), K(10, (4, -6, 0)), K(20, (4, 6, 0))],
                     leg_r=[K(0, (-32, 0, 0)), K(10, (30, 0, 0)), K(20, (-32, 0, 0))],
                     leg_r_low=[K(0, (6, 0, 0)), K(10, (12, 0, 0)), K(15, (58, 0, 0)), K(20, (6, 0, 0))],
                     leg_l=[K(0, (30, 0, 0)), K(10, (-32, 0, 0)), K(20, (30, 0, 0))],
                     leg_l_low=[K(0, (12, 0, 0)), K(5, (58, 0, 0)), K(10, (6, 0, 0)), K(20, (12, 0, 0))],
                     arm_r=[K(0, (26, 0, 5)), K(10, (-26, 0, 5)), K(20, (26, 0, 5))],
                     arm_l=[K(0, (-26, 0, -5)), K(10, (26, 0, -5)), K(20, (-26, 0, -5))],
                     arm_r_low=[K(0, (-18, 0, 0)), K(20, (-18, 0, 0))],
                     arm_l_low=[K(0, (-18, 0, 0)), K(20, (-18, 0, 0))])
    c["attack_slash"] = clip(14, False,
                             root=[K(0), K(4, p=(0, 0, -1.5)), K(7, p=(0, -0.5, 3.5)), K(14)],
                             torso=[K(0), K(4, (-8, 28, 0)), K(7, (18, -26, 0)), K(10, (14, -20, 0)), K(14)],
                             arm_r=[K(0, (0, 0, 5)), K(4, (-165, 0, 22)), K(7, (-28, 0, -24)), K(10, (-15, 0, -20)), K(14, (0, 0, 5))],
                             arm_r_low=[K(0, (-14, 0, 0)), K(4, (-45, 0, 0)), K(7, (-4, 0, 0)), K(14, (-14, 0, 0))],
                             weapon=[K(0), K(4, (-35, 0, 0)), K(7, (35, 0, 0)), K(14)],
                             leg_r=[K(0), K(7, (-26, 0, 0)), K(14)], leg_l=[K(0), K(7, (22, 0, 0)), K(14)],
                             arm_l=[K(0, (0, 0, -5)), K(4, (20, 0, -18)), K(7, (-20, 0, -10)), K(14, (0, 0, -5))])
    c["attack_pierce"] = clip(14, False,
                              root=[K(0), K(4, p=(0, 0, -2)), K(7, p=(0, -1, 5)), K(10, p=(0, -1, 4)), K(14)],
                              torso=[K(0), K(4, (0, 32, 0)), K(7, (16, -22, 0)), K(10, (14, -18, 0)), K(14)],
                              arm_r=[K(0, (0, 0, 5)), K(4, (-55, 0, 10)), K(7, (-92, 0, 0)), K(10, (-88, 0, 0)), K(14, (0, 0, 5))],
                              arm_r_low=[K(0, (-14, 0, 0)), K(4, (-110, 0, 0)), K(7, (-2, 0, 0)), K(10, (-6, 0, 0)), K(14, (-14, 0, 0))],
                              leg_r=[K(0), K(7, (-38, 0, 0)), K(14)], leg_r_low=[K(0), K(7, (30, 0, 0)), K(14)],
                              leg_l=[K(0), K(7, (28, 0, 0)), K(14)],
                              arm_l=[K(0, (0, 0, -5)), K(7, (30, 0, -20)), K(14, (0, 0, -5))])
    c["attack_blunt"] = clip(16, False,
                             root=[K(0), K(5, p=(0, 0.8, -1)), K(8, p=(0, -2, 2.5)), K(11, p=(0, -2, 2.5)), K(16)],
                             torso=[K(0), K(5, (-16, 0, 0)), K(8, (32, 0, 0)), K(11, (30, 0, 0)), K(16)],
                             head=[K(0), K(5, (-10, 0, 0)), K(8, (-14, 0, 0)), K(16)],
                             arm_r=[K(0, (0, 0, 5)), K(5, (-172, 0, 8)), K(8, (-58, 0, -8)), K(11, (-52, 0, -8)), K(16, (0, 0, 5))],
                             arm_l=[K(0, (0, 0, -5)), K(5, (-172, 0, -8)), K(8, (-58, 0, 8)), K(11, (-52, 0, 8)), K(16, (0, 0, -5))],
                             arm_r_low=[K(0, (-14, 0, 0)), K(5, (-25, 0, 0)), K(8, (-6, 0, 0)), K(16, (-14, 0, 0))],
                             arm_l_low=[K(0, (-10, 0, 0)), K(5, (-25, 0, 0)), K(8, (-6, 0, 0)), K(16, (-10, 0, 0))],
                             leg_r=[K(0), K(8, (-30, 0, 0)), K(16)], leg_r_low=[K(0), K(8, (40, 0, 0)), K(16)],
                             leg_l=[K(0), K(8, (-30, 0, 0)), K(16)], leg_l_low=[K(0), K(8, (40, 0, 0)), K(16)])
    c["guard"] = clip(20, True,
                      root=[K(0, p=(0, -1.2, 0)), K(20, p=(0, -1.2, 0))],
                      torso=[K(0, (10, 0, 0)), K(20, (10, 0, 0))],
                      arm_r=[K(0, (-62, -22, 0)), K(20, (-62, -22, 0))], arm_r_low=[K(0, (-88, 0, 0)), K(20, (-88, 0, 0))],
                      arm_l=[K(0, (-66, 22, 0)), K(20, (-66, 22, 0))], arm_l_low=[K(0, (-96, 0, 0)), K(20, (-96, 0, 0))],
                      leg_r=[K(0, (-18, 0, 0)), K(20, (-18, 0, 0))], leg_r_low=[K(0, (30, 0, 0)), K(20, (30, 0, 0))],
                      leg_l=[K(0, (-14, 0, 0)), K(20, (-14, 0, 0))], leg_l_low=[K(0, (30, 0, 0)), K(20, (30, 0, 0))],
                      weapon=[K(0, (-70, 0, 0)), K(20, (-70, 0, 0))])
    c["stagger"] = clip(40, True,
                        root=[K(0, p=(0, -2.6, 0)), K(20, p=(0, -3.0, 0)), K(40, p=(0, -2.6, 0))],
                        torso=[K(0, (36, 0, 0)), K(20, (41, 0, 3)), K(40, (36, 0, 0))],
                        head=[K(0, (28, 0, 0)), K(20, (32, 0, -4)), K(40, (28, 0, 0))],
                        arm_r=[K(0, (-30, 0, 10)), K(20, (-36, 0, 12)), K(40, (-30, 0, 10))],
                        arm_l=[K(0, (-30, 0, -10)), K(20, (-36, 0, -12)), K(40, (-30, 0, -10))],
                        arm_r_low=[K(0, (-6, 0, 0)), K(40, (-6, 0, 0))], arm_l_low=[K(0, (-6, 0, 0)), K(40, (-6, 0, 0))],
                        leg_r=[K(0, (-30, 0, 4)), K(40, (-30, 0, 4))], leg_r_low=[K(0, (58, 0, 0)), K(40, (58, 0, 0))],
                        leg_l=[K(0, (-22, 0, -4)), K(40, (-22, 0, -4))], leg_l_low=[K(0, (50, 0, 0)), K(40, (50, 0, 0))])
    c["roar"] = clip(30, False,
                     root=[K(0), K(8, p=(0, 0.6, -1)), K(22, p=(0, 0.6, -1)), K(30)],
                     torso=[K(0), K(8, (-20, 0, 0)), K(15, (-23, 0, 0)), K(22, (-20, 0, 0)), K(30)],
                     head=[K(0), K(8, (-26, 0, 0)), K(22, (-26, 0, 0)), K(30)],
                     arm_r=[K(0, (0, 0, 5)), K(8, (-25, 0, 62)), K(15, (-25, 0, 70)), K(22, (-25, 0, 62)), K(30, (0, 0, 5))],
                     arm_l=[K(0, (0, 0, -5)), K(8, (-25, 0, -62)), K(15, (-25, 0, -70)), K(22, (-25, 0, -62)), K(30, (0, 0, -5))],
                     arm_r_low=[K(0, (-14, 0, 0)), K(8, (-50, 0, 0)), K(22, (-50, 0, 0)), K(30, (-14, 0, 0))],
                     arm_l_low=[K(0, (-10, 0, 0)), K(8, (-50, 0, 0)), K(22, (-50, 0, 0)), K(30, (-10, 0, 0))])
    c["death"] = clip(24, False,
                      root=[K(0), K(8, (-20, 0, 0), (0, -2, -2)), K(18, (-86, 0, 0), (0, -10.5, -6)), K(24, (-90, 0, 0), (0, -11, -6))],
                      head=[K(0), K(8, (-20, 0, 0)), K(24, (-8, 20, 0))],
                      arm_r=[K(0, (0, 0, 5)), K(10, (-60, 0, 40)), K(24, (-20, 0, 75))],
                      arm_l=[K(0, (0, 0, -5)), K(10, (-60, 0, -40)), K(24, (-10, 0, -60))],
                      leg_r=[K(0), K(10, (-30, 0, 0)), K(24, (-6, 0, 8))], leg_r_low=[K(0), K(10, (40, 0, 0)), K(24, (10, 0, 0))],
                      leg_l=[K(0), K(10, (-10, 0, 0)), K(24, (-30, 0, -8))], leg_l_low=[K(0), K(10, (20, 0, 0)), K(24, (50, 0, 0))])
    return c


ANIMSETS = {"humanoid": humanoid_clips()}

# --- weapons (in the weapon bone's own frame: the grip at the joint, the blade along +y) --------------

STEEL, DARK, WOOD, GOLD = (196, 200, 210), (60, 62, 70), (110, 78, 48), (206, 170, 80)


def weapon(m, kind, tint=None):
    b = "weapon"
    box = lambda *a, **k: m.box(b, *a, rel=True, **k)
    if kind == "sword":
        box(-0.5, -2.5, -0.5, 1, 3.5, 1, WOOD)
        box(-2, 1, -0.6, 4, 0.9, 1.2, GOLD)
        box(-0.9, 1.9, -0.3, 1.8, 12, 0.6, STEEL)
        box(-0.45, 13.9, -0.3, 0.9, 1.2, 0.6, STEEL)
    elif kind == "katana":
        box(-0.45, -3, -0.45, 0.9, 4.5, 0.9, tint or (120, 30, 36))
        box(-1.1, 1.5, -1.1, 2.2, 0.5, 2.2, DARK)
        box(-0.4, 2, -0.2, 1.0, 14.5, 0.4, STEEL)
    elif kind == "mace":
        box(-0.5, -3, -0.5, 1, 11, 1, DARK)
        box(-1.7, 8, -1.7, 3.4, 3.4, 3.4, STEEL)
        box(-2.3, 9.2, -0.5, 4.6, 1, 1, STEEL)
        box(-0.5, 9.2, -2.3, 1, 1, 4.6, STEEL)
    elif kind == "club":
        box(-0.5, -3, -0.5, 1, 6, 1, WOOD)
        box(-0.9, 3, -0.9, 1.8, 8, 1.8, tint or (140, 100, 62))
    elif kind == "axe":
        box(-0.5, -4, -0.5, 1, 15, 1, WOOD)
        box(-0.3, 6.5, -4.5, 0.6, 5, 4.2, STEEL)
        box(-0.4, 8, -0.6, 0.8, 2.4, 1.6, DARK)
    elif kind == "pickaxe":
        box(-0.5, -4, -0.5, 1, 15, 1, WOOD)
        box(-0.4, 9.5, -4.5, 0.8, 1.3, 9, STEEL)
    elif kind == "spear":
        box(-0.4, -9, -0.4, 0.8, 22, 0.8, WOOD)
        box(-0.9, 13, -0.3, 1.8, 4, 0.6, STEEL)
        box(-0.4, 17, -0.3, 0.8, 1.6, 0.6, STEEL)
    elif kind == "trident":
        box(-0.4, -9, -0.4, 0.8, 21, 0.8, DARK)
        box(-2.4, 12, -0.3, 4.8, 0.8, 0.6, STEEL)
        for x in (-2.4, -0.4, 1.6):
            box(x, 12.8, -0.3, 0.8, 4.5, 0.6, STEEL)
    elif kind == "scythe":
        box(-0.4, -8, -0.4, 0.8, 22, 0.8, WOOD)
        box(-0.3, 12.6, -6.5, 0.6, 1.6, 6.5, STEEL)
        box(-0.3, 11.4, -7.5, 0.6, 1.4, 2.5, STEEL)
    elif kind == "hammer":
        box(-0.5, -4, -0.5, 1, 16, 1, DARK)
        box(-2.2, 9.5, -3.5, 4.4, 4.4, 7, tint or (90, 92, 100))
        box(-1.6, 10.1, -4.1, 3.2, 3.2, 8.2, GOLD)
    elif kind == "gun":
        box(-0.6, -2.5, -0.6, 1.2, 3.5, 1.2, WOOD)
        box(-0.7, 0.5, -1.6, 1.4, 2, 2.4, DARK)
        box(-0.45, 2.5, -0.45, 0.9, 9, 0.9, DARK)
    elif kind == "baton":
        box(-0.45, -2.5, -0.45, 0.9, 12, 0.9, tint or DARK)
        box(-0.6, 8.5, -0.6, 1.2, 1.2, 1.2, tint or (240, 210, 70))
    elif kind == "rapier":
        box(-0.4, -2.5, -0.4, 0.8, 3.5, 0.8, DARK)
        box(-1.4, 0.6, -1.4, 2.8, 0.8, 2.8, GOLD)
        box(-0.25, 1.4, -0.25, 0.5, 16, 0.5, STEEL)
    elif kind == "lance":
        box(-0.5, -6, -0.5, 1, 8, 1, DARK)
        box(-1.8, 2, -1.8, 3.6, 1.4, 3.6, GOLD)
        box(-1.2, 3.4, -1.2, 2.4, 7, 2.4, tint or (220, 216, 200))
        box(-0.8, 10.4, -0.8, 1.6, 6, 1.6, tint or (220, 216, 200))
        box(-0.4, 16.4, -0.4, 0.8, 4, 0.8, tint or (220, 216, 200))
    elif kind == "greatsword":
        box(-0.6, -4, -0.6, 1.2, 5, 1.2, DARK)
        box(-3, 1, -0.7, 6, 1.2, 1.4, GOLD)
        box(-1.7, 2.2, -0.4, 3.4, 16, 0.8, tint or STEEL)
        box(-0.9, 18.2, -0.4, 1.8, 1.8, 0.8, tint or STEEL)
    elif kind == "scissors":
        box(-0.5, -3, -0.5, 1, 4, 1, DARK)
        box(-1.4, 1, -0.3, 1.2, 15, 0.6, tint or (170, 30, 40))
        box(0.2, 1, -0.3, 1.2, 15, 0.6, tint or (170, 30, 40))
        box(-1.8, -1, -0.4, 3.6, 2, 0.8, GOLD)
    elif kind == "hook":
        box(-0.4, -3, -0.4, 0.8, 9, 0.8, WOOD)
        box(-0.35, 6, -0.35, 0.7, 4.5, 0.7, STEEL)
        box(-0.35, 9.8, -0.35, 0.7, 0.7, 3.2, STEEL)
        box(-0.35, 7.6, 2.4, 0.7, 2.6, 0.7, STEEL)
    elif kind == "lantern":
        box(-0.3, -1, -0.3, 0.6, 3, 0.6, DARK)
        box(-1.6, -5.5, -1.6, 3.2, 4.2, 3.2, tint or (250, 220, 120), plain=True)
        box(-1.9, -1.4, -1.9, 3.8, 0.6, 3.8, DARK)
        box(-1.9, -6, -1.9, 3.8, 0.6, 3.8, DARK)
    elif kind == "chainsaw":
        box(-1.4, -3.5, -1.2, 2.8, 5, 2.4, tint or (200, 120, 40))
        box(-0.9, 1.5, -0.35, 1.8, 13, 0.7, STEEL)
        box(-1.1, 1.5, -0.2, 2.2, 13.5, 0.4, DARK)
    elif kind == "shield":
        box(-0.5, -2.5, -0.5, 1, 3.5, 1, DARK)
        box(-3.5, -4, 0.6, 7, 10, 0.9, tint or (90, 100, 120))
        box(-1, -0.5, 1.5, 2, 3, 0.4, GOLD)


WEAPON_OF_ITEM = {"netherite_sword": "katana", "iron_sword": "sword", "mace": "mace", "iron_axe": "axe", "trident": "spear",
                  "iron_shovel": "club", "iron_pickaxe": "pickaxe", "crossbow": "gun", "iron_hoe": "scythe", "": None}

# --- humanoids -----------------------------------------------------------------------------------------

def humanoid(mid, s, scale=1.0):
    """A person with elbows and knees, clothes, headwear and a weapon, from a spec (colours and features)."""
    m = Model(mid, scale=scale, height=33 + (s.get("crown_h", 3) if s.get("hat") else 0))
    skin = s.get("skin", (206, 160, 124))
    top = s["top"]
    coat = s.get("coat")
    body_col = coat or top
    sleeve = s.get("sleeve", body_col)
    pants = s.get("pants", shade(top, 0.8))
    shoes = s.get("shoes", (24, 22, 26))
    hair = s.get("hair", (40, 32, 28))
    hands = s.get("hands", skin)
    accent = s.get("accent")
    bulk = s.get("bulk", 0.0)  # wider shoulders and limbs
    slim = 1 if s.get("slim") else 0
    tw, td = 8 + 2 * bulk - slim, 4 + bulk - 0.4 * slim
    ax = tw / 2  # where the arms hang
    aw = 3 + bulk * 0.6 - 0.5 * slim
    m.bone("root", None, (0, 13, 0))
    m.bone("torso", "root", (0, 13, 0), rest=(s.get("hunch", 0), 0, 0))
    m.bone("head", "torso", (0, 24, 0), rest=(-s.get("hunch", 0) * 0.7, 0, 0))
    for side, sg in (("r", 1), ("l", -1)):
        m.bone("arm_" + side, "torso", (sg * (ax + aw / 2), 23, 0))
        m.bone("arm_%s_low" % side, "arm_" + side, (sg * (ax + aw / 2), 18.2, 0))
        m.bone("leg_" + side, "root", (sg * 2, 13, 0))
        m.bone("leg_%s_low" % side, "leg_" + side, (sg * 2, 6.6, 0))
    m.bone("weapon", "arm_r_low", (ax + aw / 2, 12.6, 0.4), rest=(90, 0, 0))

    # Torso: jacket or coat, a shirt and tie down the front, a belt.
    front, around = [], []
    if s.get("inner"):
        front.append((tw / 2 - 1.5, 0, 3, 5.5, s["inner"]))
    if s.get("lapels"):
        front += [(tw / 2 - 2.2, 0, 0.7, 6.5, shade(body_col, 0.6)), (tw / 2 + 1.5, 0, 0.7, 6.5, shade(body_col, 0.6))]
    if s.get("tie"):
        front.append((tw / 2 - 0.5, 0, 1, 7.5, s["tie"]))
    if s.get("emblem"):
        front.append((tw / 2 + 1.6, 2, 1.5, 1.5, s["emblem"]))
    for i, c in enumerate(s.get("splotches") or []):
        front.append((m.rng.random() * (tw - 2), m.rng.random() * 8, 2, 2, c))
    for i in range(3 if s.get("veins") else 0):
        front.append((1 + i * 1.7, 2 + i * 2.6, 3, 0.5, s["veins"]))
    if accent:
        around = [(0, 9.3, 99, 1.1, accent)]
    m.box("torso", -tw / 2, 13, -td / 2, tw, 11, td, body_col,
          marks={"north": front + around, "south": around, "east": around, "west": around})
    m.box("root", -tw / 2 + 0.3, 12, -td / 2 + 0.2, tw - 0.6, 1.6, td - 0.4, pants)
    if s.get("belly"):
        m.box("torso", -tw / 2 - 1.2, 12.5, -td / 2 - 0.8, tw + 2.4, 7.5, td + 4.2, s["belly"])
        m.box("torso", -tw / 2 - 0.4, 14, td / 2 + 3, tw + 0.8, 4.5, 1.4, s["belly"])
    if s.get("carapace"):
        m.box("torso", -tw / 2 - 0.6, 14, -td / 2 - 1.4, tw + 1.2, 10, 1.4, s["carapace"])
    if s.get("pack"):
        m.bone("pack", "torso", (0, 18, -td / 2))
        if s.get("pack_big"):  # a coffin carried on the back
            m.box("pack", -4.5, 9, -td / 2 - 5.2, 9, 22, 5, s["pack"], marks={"south": [(1, 1, 7, 0.7, GOLD), (1, 20, 7, 0.7, GOLD), (4.1, 3, 0.8, 9, GOLD), (2.4, 5.5, 4.2, 0.8, GOLD)]})
        else:
            m.box("pack", -3, 14, -td / 2 - 3.6, 6, 8, 3.6, s["pack"])
    if s.get("pads"):
        m.mirror_box("arm_r", "arm_l", ax - 0.4, 22.2, -2.1, aw + 1.2, 2.4, 4.2, s["pads"])
    # Head: a face, hair, and what is worn on it.
    eye = s.get("glow_eyes") or s.get("eyes", (40, 46, 70))
    white = s.get("glow_eyes") or s.get("eye_white", (240, 240, 240))
    face = [(1.2, 3.6, 1.8, 1.4, white), (5.0, 3.6, 1.8, 1.4, white), (1.9, 3.6, 1.0, 1.4, eye), (5.0, 3.6, 1.0, 1.4, eye),
            (1.2, 3.0, 1.8, 0.5, shade(hair, 0.8)), (5.0, 3.0, 1.8, 0.5, shade(hair, 0.8)), (3.3, 6.2, 1.4, 0.5, shade(skin, 0.62))]
    if s.get("face") is not None:
        face = list(s["face"])
    if s.get("glasses"):
        face += [(0.7, 3.3, 2.8, 2.0, s["glasses"]), (4.5, 3.3, 2.8, 2.0, s["glasses"]), (3.5, 4.0, 1.0, 0.5, (30, 30, 34))]
    if s.get("grin"):
        face += [(0.8, 5.6, 6.4, 1.3, s["grin"])] + [(1.4 + i * 1.3, 5.4, 0.4, 1.7, shade(skin, 0.5)) for i in range(5)]
    if not s.get("no_head"):
        m.box("head", -4, 24, -4, 8, 8, 8, skin, marks={"north": [] if s.get("full_mask") else face})
    else:
        m.box("head", -2, 24, -2, 4, 1.2, 4, s.get("neck", (120, 40, 40)))
    style = s.get("hair_style", "short") if not s.get("no_head") else "bald"
    if s.get("tiny_hat"):
        m.box("head", -1.6, 32, -1.6, 3.2, 0.6, 3.2, s["tiny_hat"])
        m.box("head", -1, 32.6, -1, 2, 2, 2, s["tiny_hat"])
    if s.get("ears_long"):
        m.mirror_box("head", "head", 1.8, 31.5, -1, 1.8, 5.5, 1.4, s["ears_long"])
    if s.get("gauntlets"):
        g = s["gauntlets"]
        for sg, side in ((1, "r"), (-1, "l")):
            x0 = ax if sg > 0 else -ax - aw
            m.box("arm_%s_low" % side, x0, 13.2, -aw / 2, aw, 4.6, aw, g, grow=0.55,
                  marks={f: [(0.5, 1, 99, 0.6, shade(g, 0.6)), (0.5, 3, 99, 0.6, shade(g, 0.6))] for f in ("north", "south", "east", "west")})
            m.box("leg_%s_low" % side, (0.15 if sg > 0 else -3.85), 2, -1.85, 3.7, 3.6, 3.7, g, grow=0.5,
                  marks={f: [(0.5, 1, 99, 0.6, shade(g, 0.6)), (0.5, 2.6, 99, 0.6, shade(g, 0.6))] for f in ("north", "south", "east", "west")})
    if s.get("parasol"):
        pc = s["parasol"]
        m.bone("parasol", "torso", (0, 26, -4), rest=(-12, 0, 0))
        ribs = {"north": [(i * 3.2 + 1, 0, 0.5, 99, shade(pc, 0.6)) for i in range(7)]}
        m.box("parasol", -11, 19, -6.6, 22, 14, 0.8, pc, marks=ribs)
        m.box("parasol", -7, 15, -6.9, 14, 22, 0.8, pc, marks=ribs)
        m.box("parasol", -9.5, 16.5, -6.3, 19, 19, 0.8, shade(pc, 0.8))
        m.box("parasol", -0.4, 12, -6, 0.8, 14, 0.8, (30, 26, 34))
    if s.get("ears"):
        m.mirror_box("head", "head", 2.6, 31, -1, 2.6, 2.6, 1.4, s["ears"])
    if s.get("snout"):
        m.box("head", -1.8, 24.6, 4, 3.6, 2.6, 1.6, s["snout"], marks={"north": [(1.2, 0.2, 1.2, 0.8, (20, 20, 24))]})
    if s.get("wings"):
        m.bone("wings", "torso", (0, 21, -td / 2))
        for sg in (1, -1):
            m.box("wings", 1 if sg > 0 else -1 - s.get("wing_span", 9), 14, -td / 2 - 1.2, s.get("wing_span", 9), 11, 0.6, s["wings"],
                  marks={f: [(1, 1, s.get("wing_span", 9) - 2, 0.8, shade(s["wings"], 0.6)), (1, 5, s.get("wing_span", 9) - 3, 0.8, shade(s["wings"], 0.6))] for f in ("north", "south")})
    if s.get("big_arm"):
        m.box("arm_r_low", ax - 1, 11, -aw / 2 - 1.2, aw + 2.4, 7.6, aw + 2.4, s["big_arm"])
    if s.get("claws"):
        for sg, bn in ((1, "arm_r_low"), (-1, "arm_l_low")):
            for dz in (-1.2, 0, 1.2):
                m.box(bn, sg * (ax + aw / 2) - 0.35, 9.4, dz - 0.35, 0.7, 2.8, 0.7, s["claws"])
    if s.get("chains"):
        m.box("torso", -tw / 2 - 0.3, 17, -td / 2 - 0.3, tw + 0.6, 1, td + 0.6, s["chains"])
        m.box("torso", -tw / 2 - 0.3, 20, -td / 2 - 0.3, tw + 0.6, 1, td + 0.6, s["chains"])
    if s.get("crown"):
        m.box("head", -3.6, 32.4, -3.6, 7.2, 1.2, 7.2, s["crown"])
        for x, z in ((-3.6, -3.6), (2.4, -3.6), (-3.6, 2.4), (2.4, 2.4), (-0.6, 2.4), (-0.6, -3.6)):
            m.box("head", x, 33.6, z, 1.2, 1.6, 1.2, s["crown"])
    if style != "bald":
        m.box("head", -4.3, 31.4, -4.3, 8.6, 1.1, 8.6, hair)
        m.box("head", -4.3, 26.5 if style == "short" else 17.5 if style == "long" else 24, -4.5, 8.6, 32.3 - (26.5 if style == "short" else 17.5 if style == "long" else 24), 0.8, hair)
        m.mirror_box("head", "head", 3.7, 28, -4.3, 0.7, 4, 5.5, hair)
        m.box("head", -4.2, 30.4, 3.7, 8.4, 1.8, 0.6, hair)
        if style == "tail":
            m.box("head", -1, 20, -6, 2, 9, 1.5, hair)
        if style == "spiky":
            for x, z in ((-3, -2), (1.5, -3), (-1, 1), (2, 2), (-3, 2.5)):
                m.box("head", x, 32, z, 1.6, 1.6 + m.rng.random() * 1.2, 1.6, hair)
    if s.get("hood"):
        h = s["hood"]
        m.box("head", -4.8, 31.6, -4.8, 9.6, 1.2, 9.4, h)
        m.box("head", -4.8, 23, -4.9, 9.6, 9.8, 0.9, h)
        m.mirror_box("head", "head", 4.0, 23, -4.8, 0.8, 9.8, 8.2, h)
    if s.get("helmet"):
        m.box("head", -4.5, 28.4, -4.5, 9, 4.2, 9, s["helmet"])
    if s.get("bandana"):
        m.box("head", -4.4, 29, -4.4, 8.8, 1.7, 8.8, s["bandana"])
        m.box("head", -0.8, 27, -5.6, 1.6, 2.8, 1.2, s["bandana"])
    if s.get("hat"):
        brim = s.get("brim", 2.2)
        m.box("head", -4 - brim, 31.2, -4 - brim, 8 + 2 * brim, 0.8, 8 + 2 * brim, s["hat"])
        ch = s.get("crown_h", 3)
        m.box("head", -3.7, 32, -3.7, 7.4, ch, 7.4, s["hat"],
              marks={f: [(0, ch - 1, 99, 1, s.get("band", shade(s["hat"], 0.6)))] for f in ("north", "south", "east", "west")})
    if s.get("mask"):
        m.box("head", -4.2, 24, 3.6, 8.4, 3.4, 0.7, s["mask"])
    if s.get("visor"):
        m.box("head", -4.3, 26.8, 3.4, 8.6, 2.2, 1.0, s["visor"])
        m.mirror_box("head", "head", 3.6, 26.8, 0, 0.8, 2.2, 4, s["visor"])
    if s.get("full_mask"):
        g = s.get("glow_eyes", (20, 20, 20))
        m.box("head", -4.2, 24, 3.6, 8.4, 8.2, 0.7, s["full_mask"], marks={"north": [(1.2, 3.4, 2, 1.4, g), (5.2, 3.4, 2, 1.4, g)]})
    if s.get("horns"):
        m.mirror_box("head", "head", 2.2, 32, -1, 1.4, 3.5, 1.4, s["horns"])
        m.mirror_box("head", "head", 3.0, 34.5, -1, 1.2, 2.4, 1.2, s["horns"])
    if s.get("halo"):
        m.box("head", -3.5, 34.2, -3.5, 7, 0.6, 7, s["halo"], plain=True)
    if s.get("beard"):
        m.box("head", -3, 21.8, 3.4, 6, 3.6, 0.9, s["beard"])
    # Arms and legs.
    cuff = [(0, 4.3, 99, 0.9, accent)] if accent else []
    for side, sg in (("r", 1), ("l", -1)):
        x0 = ax if sg > 0 else -ax - aw
        m.box("arm_" + side, x0, 18.2, -aw / 2, aw, 5.8, aw, sleeve)
        m.box("arm_%s_low" % side, x0 + 0.15, 13.4, -aw / 2 + 0.15, aw - 0.3, 4.9, aw - 0.3, sleeve,
              marks={f: cuff for f in ("north", "south", "east", "west")})
        m.box("arm_%s_low" % side, x0 + 0.3, 12, -aw / 2 + 0.3, aw - 0.6, 1.5, aw - 0.6, hands)
        lw = 3.7 + bulk * 0.5
        lx = (0.15 if sg > 0 else -0.15 - lw)
        m.box("leg_" + side, lx, 6.6, -lw / 2, lw, 6.4, lw, pants)
        m.box("leg_%s_low" % side, lx + 0.15, 1.6, -lw / 2 + 0.15, lw - 0.3, 5.1, lw - 0.3, pants)
        m.box("leg_%s_low" % side, lx - 0.1, 0, -lw / 2 - 0.1, lw + 0.2, 1.9, lw + 1.0, shoes)
    if coat or s.get("skirt"):
        c = s.get("skirt") or coat
        m.bone("coat", "root", (0, 13, 0))
        ln = s.get("coat_len", 7.5)
        m.box("coat", -tw / 2 - 0.3, 13 - ln, -td / 2 - 0.5, tw + 0.6, ln, 0.7, c)
        m.mirror_box("coat", "coat", tw / 2 - 0.3, 13 - ln, -td / 2 - 0.3, 0.7, ln, td - 0.2, c)
        if s.get("skirt"):
            m.box("coat", -tw / 2 - 0.3, 13 - ln, td / 2 - 0.2, tw + 0.6, ln, 0.7, c)
        sw = s.get("skirt_wide", 0)
        if sw:  # a wide dress in tiers
            for i, (yy, grow_) in enumerate(((13 - ln * 0.45, sw * 0.5), (13 - ln * 0.75, sw * 0.8), (13 - ln, sw))):
                m.box("coat", -tw / 2 - grow_, yy, -td / 2 - grow_, tw + 2 * grow_, ln * 0.3, td + 2 * grow_, shade(c, 1.0 - 0.12 * (i % 2)),
                      marks={f: [(0, ln * 0.3 - 0.7, 99, 0.7, s.get("frill", shade(c, 1.4)))] for f in ("north", "south", "east", "west")})
    if s.get("cape"):
        cw = s.get("cape_wide", 0)
        m.bone("cape", "torso", (0, 23.5, -td / 2 - 0.4), rest=(-8, 0, 0))
        m.box("cape", -tw / 2 - 0.5 - cw, 8 - cw, -td / 2 - 1.0, tw + 1 + 2 * cw, 15.5 + cw, 0.7 + cw * 0.3, s["cape"],
              marks={"south": [(i * 2.2 + 0.5, 12, 1, 99, shade(s["cape"], 0.7)) for i in range(int((tw + 2 * cw) / 2.2))] if cw else []})
        if cw:
            m.box("torso", -tw / 2 - cw, 21.5, -td / 2 - 1.6, tw + 2 * cw, 3.6, td / 2 + 1.9, s.get("cape_fur", s["cape"]))
            m.mirror_box("torso", "torso", tw / 2 - 0.4, 21.5, 0.3, cw + 0.4, 3.6, td / 2 + 0.5, s.get("cape_fur", s["cape"]))
            m.mirror_box("cape", "cape", tw / 2 + cw - 0.5, 9 - cw, -td / 2 - 1.0, 0.8, 13, 3.2, s["cape"])
    if s.get("tail"):
        m.bone("tail", "root", (0, 12.5, -td / 2), rest=(-50, 0, 0))
        m.box("tail", -0.8, 3, -td / 2 - 0.8, 1.6, 9.5, 1.6, s["tail"])
    kind = s.get("weapon")
    if kind:
        weapon(m, kind, s.get("weapon_tint"))
    if s.get("shield"):
        m.bone("offhand", "arm_l_low", (-(ax + aw / 2), 13.5, 0))
        m.box("offhand", -(ax + aw / 2) - 0.6 - 0.8, 9, -3.5, 0.9, 10, 7, s["shield"])
    m.parts = {"Head": ["head"], "Right Arm": ["arm_r", "arm_r_low", "weapon"], "Left Arm": ["arm_l", "arm_l_low"] + (["offhand"] if s.get("shield") else []),
               "Leg": ["leg_l", "leg_l_low"]}
    if s.get("cape"):
        m.parts["Back"] = ["cape"]
    if s.get("tail"):
        m.parts["Tail"] = ["tail"]
    if s.get("wings"):
        m.parts["Back"] = ["wings"]
    if s.get("pack"):
        m.parts["Back"] = m.parts["Gift Sack"] = ["pack"]
    return m


def stretch(m, legs=1.0, torso=1.0, thin=1.0):
    """Taller, slimmer proportions for a humanoid: longer legs and torso (and arms with it), narrower body; the head keeps its size."""
    hip, neck = 13 * legs, 13 * legs + 11 * torso

    def fy(y, arm=False):
        if y >= 24:
            return neck + (y - 24)
        if y >= 13 or arm:
            return hip + (y - 13) * torso
        return y * legs
    for b in m.bones.values():
        arm = b.name.startswith(("arm", "weapon", "offhand"))
        head = b.name == "head"
        fixed = b.name.startswith(("weapon", "blade", "parasol", "offhand"))
        k = 1.0 if head or b.name.startswith(("parasol", "blade")) else thin
        new_pivot = np.array([b.pivot[0] * (thin if not head else 1.0), fy(b.pivot[1], arm), b.pivot[2] * (thin if not head else 1.0)])
        if not fixed:
            for c in b.cubes:
                lo, hi = b.pivot + c["lo"], b.pivot + c["lo"] + c["size"]
                lo2 = np.array([lo[0] * k, fy(lo[1], arm), lo[2] * k])
                hi2 = np.array([hi[0] * k, fy(hi[1], arm), hi[2] * k])
                c["lo"], c["size"] = lo2 - new_pivot, np.maximum(hi2 - lo2, 0.3)
        b.pivot = new_pivot
    m.pscale = legs
    m.height += 13 * (legs - 1) + 11 * (torso - 1)
    return m


# --- preview renderer --------------------------------------------------------------------------------------

def render(model, pose=None, yaw=28, pitch=12, px=7, size=(300, 330), light=True):
    """An orthographic picture of the model in a pose, seen from the front left."""
    world = model.world(pose)
    atlas = model.atlas.load()
    quads = []
    cy, sy, cp, sp = math.cos(math.radians(yaw)), math.sin(math.radians(yaw)), math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

    def view(p):
        x, y, z = p[0], p[1], p[2]
        x2, z2 = x * cy + z * sy, -x * sy + z * cy
        y3, z3 = y * cp - z2 * sp, y * sp + z2 * cp
        return (size[0] / 2 - x2 * px, size[1] - 24 - y3 * px, z3)

    for n, b in model.bones.items():
        mtx = world[n]
        for c in b.cubes:
            lo, s = c["lo"], c["size"]
            x0, y0, z0 = lo[0], lo[1], -(lo[2] + s[2])  # model space (z flipped), around the joint
            x1, y1, z1 = lo[0] + s[0], lo[1] + s[1], -lo[2]
            dx, dy, dz = x1 - x0, y1 - y0, z1 - z0
            maps = {"north": lambda u, v: (x1 - u * dx, y1 - v * dy, z0), "south": lambda u, v: (x0 + u * dx, y1 - v * dy, z1),
                    "east": lambda u, v: (x1, y1 - v * dy, z1 - u * dz), "west": lambda u, v: (x0, y1 - v * dy, z0 + u * dz),
                    "up": lambda u, v: (x0 + u * dx, y1, z0 + v * dz), "down": lambda u, v: (x0 + u * dx, y0, z1 - v * dz)}
            for f in FACES:
                ux, uy, w, h = c["uv"][f]
                fn = maps[f]

                def at(u, v):
                    mx, my, mz = fn(u, v)
                    return view(mtx @ np.array([mx, my, -mz, 1.0]))
                a, bb, cc = at(0, 0), at(1, 0), at(0, 1)
                # Facing the viewer? (screen-space winding)
                cross = (bb[0] - a[0]) * (cc[1] - a[1]) - (bb[1] - a[1]) * (cc[0] - a[0])
                if cross <= 0:
                    continue
                for j in range(h):
                    for i in range(w):
                        p = [at(i / w, j / h), at((i + 1) / w, j / h), at((i + 1) / w, (j + 1) / h), at(i / w, (j + 1) / h)]
                        quads.append((sum(q[2] for q in p) / 4, [(q[0], q[1]) for q in p], atlas[ux + i, uy + j]))
    quads.sort(key=lambda q: q[0])
    img = Image.new("RGBA", size, (74, 76, 88, 255))
    g = ImageDraw.Draw(img)
    for _, pts, col in quads:
        g.polygon(pts, fill=col)
    return img


def sheet(items, path, cols=6, size=(300, 330)):
    rows = (len(items) + cols - 1) // cols
    out = Image.new("RGBA", (cols * size[0], rows * size[1]), (74, 76, 88, 255))
    g = ImageDraw.Draw(out)
    for i, (label, img) in enumerate(items):
        out.paste(img, ((i % cols) * size[0], (i // cols) * size[1]))
        g.text(((i % cols) * size[0] + 6, (i // cols) * size[1] + 4), label, fill=(255, 255, 255, 255))
    out.save(path)


# --- the cast -------------------------------------------------------------------------------------------------

def build_all():
    import md_looks
    from md_cast import cast
    enemies = json.load(open(ENEMIES, encoding="utf-8"))["enemies"]
    return cast(enemies, md_looks)


def main():
    models, assign = build_all()
    for d in ("models/item/md", "textures/item/md", "items/md"):
        shutil.rmtree(os.path.join(NS, d), ignore_errors=True)
    out = {}
    for mid, m in models.items():
        build_atlas(m)
        out[mid] = export(m)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump({"about": "Enemy models (tools/md_models.py): bones [name, parent, at (pixels from the parent's joint), rest rotation], clips [t, rx, ry, rz, px, py, pz, scale].",
                   "animsets": ANIMSETS, "models": out, "enemies": assign}, f, ensure_ascii=False, separators=(",", ":"))
    print(len(models), "models for", len(assign), "enemies;", sum(len(m.bones) for m in models.values()), "bones")
    if len(sys.argv) > 1:
        want = sys.argv[2:] or list(models)[:24]
        items = []
        for spec in want:
            mid, _, anim = spec.partition(":")
            m = models[mid]
            clips = dict(ANIMSETS.get(m.anims, {}), **m.clips)
            if anim and anim in clips:
                c = clips[anim]
                for t in range(0, c["len"] + 1, max(1, c["len"] // 5)):
                    items.append(("%s %s t=%d" % (mid, anim, t), render(m, pose_of(c, t))))
            else:
                items.append((mid, render(m, pose_of(clips["idle"], 0) if "idle" in clips else None)))
        sheet(items, sys.argv[1])


if __name__ == "__main__":
    main()
