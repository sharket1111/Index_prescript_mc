"""Generates the in-hand 3D models for Caduceus and a preview sheet.

Each weapon is designed upright (grip at the bottom, axis x=8, z=8) and every
element is then tilted -45 degrees about Z around (8, 8, 8), so the model
lies along the same diagonal as a vanilla handheld sprite and can reuse the
vanilla handheld display transforms.

Usage: python3 tools/gen3d.py src/main/resources/assets/index_prescript preview.png
"""
import json
import math
import random
import sys

from PIL import Image, ImageDraw

NS = "index_prescript"

# 16x16 palette texture: 4x4 swatch per material
SWATCH = {
    "brass_light": (0, 0, (232, 220, 158)),
    "brass": (4, 0, (186, 164, 100)),
    "brass_dark": (8, 0, (128, 110, 64)),
    "chain": (12, 0, (204, 190, 130)),
    "metal_dark": (0, 4, (26, 26, 31)),
    "metal": (4, 4, (48, 48, 56)),
    "metal_light": (8, 4, (96, 98, 110)),
    "edge": (12, 4, (170, 172, 186)),
}


def make_texture(path, swatch=None):
    swatch = swatch or SWATCH
    rnd = random.Random(7)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for x0, y0, (r, g, b) in swatch.values():
        for y in range(y0, y0 + 4):
            for x in range(x0, x0 + 4):
                d = rnd.randint(-6, 6) - (y - y0) * 2
                img.putpixel((x, y), (max(0, min(255, r + d)), max(0, min(255, g + d)), max(0, min(255, b + d)), 255))
    img.save(path)


class Model:
    def __init__(self):
        self.boxes = []

    def box(self, x0, y0, z0, x1, y1, z1, mat):
        self.boxes.append(((x0, y0, z0), (x1, y1, z1), mat))
        return self

    def rod(self, y0, y1, w, mat):
        h = w / 2
        return self.box(8 - h, y0, 8 - h, 8 + h, y1, 8 + h, mat)

    def flat(self, x0, y0, x1, y1, t, mat):
        h = t / 2
        return self.box(x0, y0, 8 - h, x1, y1, 8 + h, mat)


def grip(m, top=6.0):
    """Brass rod grip with dark caps, a pommel spike and a hanging chain."""
    m.rod(-1.0, top, 1.3, "brass")
    m.rod(-1.6, -1.0, 1.8, "metal_dark")
    m.rod(top, top + 0.6, 1.8, "metal_dark")
    m.rod(-3.2, -1.6, 0.6, "metal")
    # chain loop on the side of the grip
    for y in (0.0, 1.0, 2.0, 3.0, 4.0):
        m.box(9.3, y, 7.8, 9.8, y + 0.6, 8.2, "chain")
    m.box(8.65, 4.4, 7.8, 9.8, 4.8, 8.2, "chain")
    m.box(8.65, -0.2, 7.8, 9.8, 0.2, 8.2, "chain")
    return m


def taper(m, y0, y1, w0, w1, t0, t1, mat, steps):
    """Stack of boxes approximating a tapered blade."""
    for i in range(steps):
        a, b = i / steps, (i + 1) / steps
        w = w0 + (w1 - w0) * a
        t = t0 + (t1 - t0) * a
        m.box(8 - w / 2, y0 + (y1 - y0) * a, 8 - t / 2, 8 + w / 2, y0 + (y1 - y0) * b, 8 + t / 2, mat)


def base():
    m = Model()
    m.rod(-1.0, 16.0, 1.3, "brass")
    m.rod(-1.8, -1.0, 1.8, "metal_dark")
    m.rod(16.0, 16.8, 1.8, "metal_dark")
    m.rod(16.8, 18.4, 0.6, "metal")
    m.rod(-3.4, -1.8, 0.6, "metal")
    for y in (1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0):
        m.box(9.3, y, 7.8, 9.8, y + 0.6, 8.2, "chain")
    m.box(8.65, 7.4, 7.8, 9.8, 7.8, 8.2, "chain")
    m.box(8.65, 0.8, 7.8, 9.8, 1.2, 8.2, "chain")
    return m


def hatchet():
    m = grip(Model())
    m.rod(6.6, 16.5, 1.0, "metal")
    m.flat(5.0, 12.5, 7.5, 16.5, 0.7, "metal")
    m.flat(3.4, 11.6, 5.0, 17.4, 0.5, "edge")
    m.flat(8.5, 14.2, 10.2, 15.0, 0.6, "metal_dark")
    return m


def stiletto():
    m = grip(Model())
    m.flat(5.6, 6.6, 10.4, 7.3, 0.9, "metal_dark")
    taper(m, 7.3, 19.5, 1.3, 0.3, 1.3, 0.3, "metal", 6)
    m.rod(9.0, 19.0, 0.35, "edge")
    return m


def bastard_sword():
    m = grip(Model())
    m.flat(4.0, 6.6, 12.0, 7.6, 1.5, "metal_dark")
    m.flat(6.8, 7.6, 9.2, 17.2, 0.6, "metal")
    m.flat(7.7, 7.6, 8.3, 16.8, 0.8, "metal_light")
    taper(m, 17.2, 19.4, 2.0, 0.4, 0.6, 0.4, "metal", 3)
    m.flat(6.6, 8.0, 6.8, 17.0, 0.4, "edge")
    m.flat(9.2, 8.0, 9.4, 17.0, 0.4, "edge")
    return m


def rapier():
    m = grip(Model())
    m.box(6.6, 6.6, 6.6, 9.4, 7.2, 9.4, "metal_dark")
    m.flat(5.0, 7.2, 11.0, 7.6, 0.5, "metal")
    m.box(9.9, 0.4, 7.8, 10.3, 6.6, 8.2, "metal")
    m.box(8.6, 0.0, 7.8, 10.3, 0.4, 8.2, "metal")
    m.rod(7.6, 20.5, 0.55, "metal_light")
    return m


def hammer():
    m = grip(Model())
    m.rod(6.6, 14.5, 1.0, "metal")
    m.box(3.5, 14.5, 6.0, 12.5, 19.5, 10.0, "metal_dark")
    m.box(4.0, 15.0, 5.8, 7.5, 19.0, 10.2, "metal_light")
    m.box(8.5, 15.0, 5.8, 12.0, 19.0, 10.2, "metal_light")
    m.box(3.3, 15.0, 6.5, 12.7, 19.0, 9.5, "metal")
    m.rod(19.5, 21.0, 0.6, "metal")
    return m


def greatsword():
    m = grip(Model(), top=5.0)
    m.flat(4.4, 5.6, 11.6, 7.0, 1.6, "metal_dark")
    m.flat(5.4, 7.0, 10.6, 7.8, 1.4, "metal")
    taper(m, 7.8, 21.5, 4.2, 0.4, 1.8, 0.5, "metal", 7)
    m.flat(7.7, 8.0, 8.3, 19.0, 2.0, "metal_light")
    return m


def lance():
    m = grip(Model())
    m.box(5.0, 6.6, 5.0, 11.0, 7.6, 11.0, "metal_dark")
    m.box(5.6, 7.6, 5.6, 10.4, 8.0, 10.4, "metal")
    taper(m, 8.0, 21.5, 3.2, 0.3, 3.2, 0.3, "metal", 8)
    m.rod(8.0, 18.0, 0.5, "metal_light")
    return m


def whip():
    m = grip(Model())
    nodes = [(8.0, 6.6), (8.0, 10.0), (8.8, 13.0), (10.6, 15.6), (13.2, 16.8), (15.6, 15.6), (16.8, 13.0), (16.8, 10.6)]
    for (xa, ya), (xb, yb) in zip(nodes, nodes[1:]):
        steps = 4
        for i in range(steps):
            t = (i + 0.5) / steps
            x, y = xa + (xb - xa) * t, ya + (yb - ya) * t
            m.box(x - 0.3, y - 0.3, 7.7, x + 0.3, y + 0.3, 8.3, "metal")
    for x, y in nodes[1:]:
        m.box(x - 0.7, y - 0.7, 7.4, x + 0.7, y + 0.7, 8.6, "metal_dark")
        m.box(x - 0.3, y - 0.3, 7.3, x + 0.3, y + 0.3, 8.7, "edge")
    x, y = nodes[-1]
    m.box(x - 0.3, y - 2.2, 7.7, x + 0.3, y - 0.7, 8.3, "metal")
    return m


def scythe():
    m = grip(Model())
    m.rod(6.6, 19.5, 1.0, "metal")
    m.rod(19.5, 21.0, 0.5, "metal")
    m.box(8.5, 9.0, 7.7, 10.2, 9.6, 8.3, "metal")
    m.flat(1.0, 18.0, 8.5, 19.6, 0.7, "metal")
    m.flat(-2.0, 16.8, 2.0, 18.6, 0.6, "metal")
    m.flat(-4.0, 15.2, -1.0, 17.0, 0.5, "metal")
    m.flat(-5.0, 13.8, -3.2, 15.4, 0.4, "metal")
    m.flat(1.0, 17.6, 8.0, 18.0, 0.5, "edge")
    m.flat(-2.0, 16.4, 1.0, 16.8, 0.4, "edge")
    m.flat(-4.0, 14.8, -2.0, 15.2, 0.4, "edge")
    return m


FORMS = {
    "": base,
    "_hatchet": hatchet,
    "_stiletto": stiletto,
    "_bastard_sword": bastard_sword,
    "_rapier": rapier,
    "_hammer": hammer,
    "_greatsword": greatsword,
    "_lance": lance,
    "_whip": whip,
    "_scythe": scythe,
}

DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
}

TILT = -45.0
ORIGIN = (8.0, 8.0, 8.0)


def r(v):
    return round(v, 3)


def to_json(m, texture="caduceus_3d", swatch=None):
    swatch = swatch or SWATCH
    tex = f"{NS}:item/{texture}"
    elements = []
    for (x0, y0, z0), (x1, y1, z1), mat in m.boxes:
        u, v, _ = swatch[mat]
        uv = [u, v, u + 4, v + 4]
        for c in (x0, y0, z0, x1, y1, z1):
            assert -16 <= c <= 32, (mat, c)
        elements.append({
            "from": [r(x0), r(y0), r(z0)],
            "to": [r(x1), r(y1), r(z1)],
            "rotation": {"angle": TILT, "axis": "z", "origin": list(ORIGIN)},
            "faces": {f: {"uv": uv, "texture": "#0"} for f in ("north", "east", "south", "west", "up", "down")},
        })
    return {"textures": {"0": tex, "particle": tex}, "elements": elements, "display": DISPLAY}


# --- preview renderer -------------------------------------------------------

def tilt(p):
    a = math.radians(TILT)
    x, y, z = p[0] - ORIGIN[0], p[1] - ORIGIN[1], p[2] - ORIGIN[2]
    return (x * math.cos(a) - y * math.sin(a) + ORIGIN[0], x * math.sin(a) + y * math.cos(a) + ORIGIN[1], z + ORIGIN[2])


def view(p, yaw, pitch):
    x, y, z = p[0] - 8, p[1] - 8, p[2] - 8
    cy, sy = math.cos(yaw), math.sin(yaw)
    x, z = x * cy + z * sy, -x * sy + z * cy
    cp, sp = math.cos(pitch), math.sin(pitch)
    y, z = y * cp - z * sp, y * sp + z * cp
    return x, y, z


FACES = [((0, 1, 3, 2), (-1, 0, 0)), ((4, 6, 7, 5), (1, 0, 0)), ((0, 4, 5, 1), (0, -1, 0)),
         ((2, 3, 7, 6), (0, 1, 0)), ((0, 2, 6, 4), (0, 0, -1)), ((1, 5, 7, 3), (0, 0, 1))]


def render(m, size=220, yaw=0.5, pitch=-0.35, scale=7.0, swatch=None):
    swatch = swatch or SWATCH
    img = Image.new("RGBA", (size, size), (210, 210, 214, 255))
    draw = ImageDraw.Draw(img)
    polys = []
    for (x0, y0, z0), (x1, y1, z1), mat in m.boxes:
        corners = [tilt((x, y, z)) for x in (x0, x1) for y in (y0, y1) for z in (z0, z1)]
        vc = [view(c, yaw, pitch) for c in corners]
        for idx, n in FACES:
            nv = view(tilt((8 + n[0], 8 + n[1], 8 + n[2])), yaw, pitch)
            nz = nv[2] - view(tilt(ORIGIN), yaw, pitch)[2]
            if nz < 0:
                continue
            light = 0.55 + 0.45 * max(0.0, (nv[1] - view(tilt(ORIGIN), yaw, pitch)[1]) * 0.6 + nz * 0.6)
            col = tuple(min(255, int(c * light)) for c in swatch[mat][2])
            pts = [(size / 2 + vc[i][0] * scale, size / 2 - vc[i][1] * scale) for i in idx]
            depth = sum(vc[i][2] for i in idx) / 4
            polys.append((depth, pts, col))
    for _, pts, col in sorted(polys, key=lambda p: p[0]):
        draw.polygon(pts, fill=col, outline=(10, 10, 12))
    return img


if __name__ == "__main__":
    ASSETS, PREVIEW = sys.argv[1], sys.argv[2]
    make_texture(f"{ASSETS}/textures/item/caduceus_3d.png")
    tiles = []
    for suffix, fn in FORMS.items():
        m = fn()
        with open(f"{ASSETS}/models/item/caduceus{suffix}_3d.json", "w") as f:
            json.dump(to_json(m), f, indent="\t")
            f.write("\n")
        tiles.append(render(m))
    sheet = Image.new("RGBA", (5 * 220, 2 * 220), (210, 210, 214, 255))
    for i, t in enumerate(tiles):
        sheet.paste(t, ((i % 5) * 220, (i // 5) * 220))
    sheet.save(PREVIEW)
