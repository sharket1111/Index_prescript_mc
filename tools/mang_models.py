#!/usr/bin/env python3
"""Bakes [Mang]'s glowing golden rings into the held weapon models.

The client draws the held item in first person in its own pass, after the
world, so rings floating in the world can never wrap the blade there. Instead
every 3D weapon model (Caduceus and its nine forms, Laevatein and its three
release stages) gets six variants with 1-6 rings wound round the blade, and
the item definitions pick one by the second custom_model_data string
("mang1".."mang6"); the first string still picks the form or stage.

All weapon elements are drawn upright along Y and turned by -45 about Z (the
diagonal of a held item), so the rings are built the same way: an octagon of
thin bars round the blade's axis (x = z = 8), with the slanted sides made of
small steps since an element can only turn about one axis. The rings glow
(light_emission) and shimmer with an animated texture.

Writes into paper/pack/assets/index_prescript (the plugin's resource pack):
models/item/<model>_mang<n>.json, items/caduceus.json, items/laevatein.json
(which replace the shared ones in the plugin pack) and textures/item/mang_glow.png.
Also writes a preview image to build/mang_preview.png.

Run from the repository root: python3 tools/mang_models.py
"""
import copy
import json
import math
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / "src/main/resources/assets/index_prescript"
PACK = ROOT / "paper/pack/assets/index_prescript"
NS = "index_prescript"
ITEMS = ["caduceus", "laevatein", "renoir_hammer"]
MAX_RINGS = 6
AXIS = 8.0
# Where the rings may sit: above the guard, below the tip, and where the blade is slim enough.
LOWEST = 7.6
LOWEST_FALLBACK = 3.0
MIN_SPAN = 3.0
ROOMY_SPAN = 6.0
TIP_MARGIN = 1.6
MAX_EXTENT = 3.2
SPACING = 2.3
MIN_SPACING = 0.9
GAP = 0.55
MIN_RADIUS = 1.35
TUBE = 0.42
HEIGHT = 0.55
STEPS = 3
ROTATION = {"angle": -45.0, "axis": "z", "origin": [8.0, 8.0, 8.0]}


def extent(model, y0, y1):
    """How far the weapon reaches from its axis between heights y0 and y1."""
    reach = 0.0
    for e in model["elements"]:
        a, b = e["from"], e["to"]
        if b[1] < y0 or a[1] > y1:
            continue
        reach = max(reach, abs(a[0] - AXIS), abs(b[0] - AXIS), abs(a[2] - AXIS), abs(b[2] - AXIS))
    return reach


def blade_radius(model, lowest):
    """A ring radius that clears most of the blade: the reach most of its length stays within."""
    top = max(e["to"][1] for e in model["elements"]) - TIP_MARGIN
    reaches, y = [], lowest
    while y <= top:
        reaches.append(extent(model, y - HEIGHT, y + HEIGHT))
        y += 0.2
    reaches.sort()
    reach = reaches[int(len(reaches) * 0.35)] if reaches else 0.0
    return max(MIN_RADIUS, min(reach, MAX_EXTENT) + GAP)


def ring_span(model, radius, lowest):
    """The longest stretch of blade that fits inside the rings (clear of guards and heads)."""
    top = max(e["to"][1] for e in model["elements"]) - TIP_MARGIN
    best, start, y = (lowest, lowest), None, lowest
    while y <= top + 1e-6:
        fits = extent(model, y - HEIGHT, y + HEIGHT) <= radius - TUBE
        if fits and start is None:
            start = y
        if (not fits or y + 0.2 > top) and start is not None:
            end = y if fits else y - 0.2
            if end - start > best[1] - best[0]:
                best = (start, end)
            start = None
        y += 0.2
    return best


def ring_heights(span, count):
    lo, hi = span
    if count == 1:
        return [(lo + hi) / 2]
    spacing = max(MIN_SPACING, min(SPACING, (hi - lo) / (count - 1)))
    middle = (lo + hi) / 2
    return [middle + (i - (count - 1) / 2) * spacing for i in range(count)]


def box(x0, y0, z0, x1, y1, z1):
    faces = {side: {"uv": [0, 0, 16, 16], "texture": "#mang"}
             for side in ("north", "east", "south", "west", "up", "down")}
    return {
        "from": [round(x0, 4), round(y0, 4), round(z0, 4)],
        "to": [round(x1, 4), round(y1, 4), round(z1, 4)],
        "rotation": dict(ROTATION),
        "shade": False,
        "light_emission": 15,
        "faces": faces,
    }


def ring(y, radius):
    """An octagon of bars round the blade at height y: four straight sides and stepped corners."""
    h0, h1 = y - HEIGHT / 2, y + HEIGHT / 2
    side = radius * math.tan(math.pi / 8)
    inner = radius - TUBE
    c = AXIS
    parts = [
        box(c - side, h0, c + inner, c + side, h1, c + radius),
        box(c - side, h0, c - radius, c + side, h1, c - inner),
        box(c + inner, h0, c - side, c + radius, h1, c + side),
        box(c - radius, h0, c - side, c - inner, h1, c + side),
    ]
    # Each slanted side, from (side, r) to (r, side), as small squares stepping across.
    step = (radius - side) / STEPS
    half = (step + TUBE) / 2
    for sx in (-1, 1):
        for sz in (-1, 1):
            for k in range(STEPS):
                t = (k + 0.5) / STEPS
                px = side + (radius - side) * t - TUBE * 0.5
                pz = radius - (radius - side) * t - TUBE * 0.5
                x, z = c + sx * px, c + sz * pz
                parts.append(box(x - half, h0, z - half, x + half, h1, z + half))
    return parts


def fit(model):
    """The ring radius and the stretch of blade they go on: the slimmest rings with room for
    them all, starting lower (down the grip) on weapons whose blade has no room, like the whip."""
    for lowest, room in ((LOWEST, ROOMY_SPAN), (LOWEST, MIN_SPAN), (LOWEST_FALLBACK, MIN_SPAN)):
        radius = blade_radius(model, lowest) if lowest == LOWEST else MIN_RADIUS
        while radius <= MAX_EXTENT + GAP:
            span = ring_span(model, radius, lowest)
            if span[1] - span[0] >= room:
                return radius, span
            radius += 0.1
    radius = MAX_EXTENT + GAP
    return radius, ring_span(model, radius, LOWEST_FALLBACK)


def rings_for(model, count):
    radius, span = fit(model)
    elements = []
    for y in ring_heights(span, count):
        elements += ring(y, radius)
    return elements, span, radius


def held_models(node, found):
    """Every model the 3D (non-GUI) branch of an item definition can show."""
    if node.get("type") == "minecraft:model":
        found.append(node["model"].split("/", 1)[1])
    for case in node.get("cases", []):
        held_models(case["model"], found)
    if "fallback" in node:
        held_models(node["fallback"], found)
    return found


def renamed(node, count):
    node = copy.deepcopy(node)

    def walk(n):
        if n.get("type") == "minecraft:model":
            n["model"] += f"_mang{count}"
        for case in n.get("cases", []):
            walk(case["model"])
        if "fallback" in n:
            walk(n["fallback"])
    walk(node)
    return node


def glow_texture():
    """A golden shimmer: a bright band running across a warm gold, 8 frames."""
    frames = 8
    image = Image.new("RGBA", (16, 16 * frames))
    for f in range(frames):
        for u in range(16):
            band = math.cos((u / 16 - f / frames) * 2 * math.pi)
            light = 0.5 + 0.5 * band
            r = 255
            g = int(205 + 45 * light)
            b = int(70 + 150 * light ** 3)
            for v in range(16):
                image.putpixel((u, f * 16 + v), (r, g, b, 255))
    target = PACK / "textures/item/mang_glow.png"
    target.parent.mkdir(parents=True, exist_ok=True)
    image.save(target)
    (target.parent / "mang_glow.png.mcmeta").write_text(
        json.dumps({"animation": {"frametime": 2, "interpolate": True}}, indent="\t") + "\n")


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent="\t") + "\n")


def corners(element):
    """The element's eight corners, turned -45 about z round (8, 8, 8) like the weapon."""
    (x0, y0, z0), (x1, y1, z1) = element["from"], element["to"]
    c, s = math.cos(math.radians(-45)), math.sin(math.radians(-45))
    points = []
    for x in (x0, x1):
        for y in (y0, y1):
            for z in (z0, z1):
                dx, dy = x - 8, y - 8
                points.append((8 + dx * c - dy * s, 8 + dx * s + dy * c, z))
    return points


FACES = [(0, 1, 3, 2), (4, 5, 7, 6), (0, 1, 5, 4), (2, 3, 7, 6), (0, 2, 6, 4), (1, 3, 7, 5)]


def preview(models):
    """Each weapon with six rings, seen from a little above and to the side."""
    cell, scale = 190, 6.5
    cols = 5
    rows = (len(models) + cols - 1) // cols
    image = Image.new("RGB", (cols * cell, rows * cell), (30, 32, 40))
    draw = ImageDraw.Draw(image)
    yaw, pitch = math.radians(-28), math.radians(18)
    light = (0.4, 0.8, 0.45)
    for index, (name, model) in enumerate(models):
        ox, oy = (index % cols) * cell, (index // cols) * cell
        draw.text((ox + 6, oy + 4), name.replace("_3d", ""), fill=(200, 200, 210))

        def view(p):
            x, y, z = p[0] - 8, p[1] - 8, p[2] - 8
            x, z = x * math.cos(yaw) + z * math.sin(yaw), -x * math.sin(yaw) + z * math.cos(yaw)
            y, z = y * math.cos(pitch) - z * math.sin(pitch), y * math.sin(pitch) + z * math.cos(pitch)
            return x, y, z

        polygons = []
        for e in model["elements"]:
            points = [view(p) for p in corners(e)]
            gold = bool(e.get("light_emission"))
            for face in FACES:
                q = [points[i] for i in face]
                ax, ay, az = (q[1][k] - q[0][k] for k in range(3))
                bx, by, bz = (q[3][k] - q[0][k] for k in range(3))
                n = (ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx)
                length = math.sqrt(sum(v * v for v in n)) or 1
                shade = abs(sum(n[k] * light[k] for k in range(3))) / length
                depth = sum(v[2] for v in q) / 4
                if gold:
                    colour = (255, int(215 + 30 * shade), int(90 + 110 * shade))
                else:
                    g = int(95 + 90 * shade)
                    colour = (g, g, g + 12)
                polygons.append((depth, [(ox + cell / 2 + v[0] * scale, oy + cell / 2 + 8 - v[1] * scale) for v in q], colour))
        for _, poly, colour in sorted(polygons, key=lambda t: t[0]):
            draw.polygon(poly, fill=colour)
    target = ROOT / "build/mang_preview.png"
    target.parent.mkdir(parents=True, exist_ok=True)
    image.save(target)
    return target


def main():
    glow_texture()
    shown = []
    for item in ITEMS:
        definition = json.loads((JAVA / "items" / f"{item}.json").read_text())
        root = definition["model"]
        held = root["fallback"]
        for name in dict.fromkeys(held_models(held, [])):
            model = json.loads((JAVA / "models/item" / f"{name}.json").read_text())
            for count in range(1, MAX_RINGS + 1):
                ringed = copy.deepcopy(model)
                ringed["textures"]["mang"] = f"{NS}:item/mang_glow"
                elements, span, radius = rings_for(model, count)
                ringed["elements"] = model["elements"] + elements
                write(PACK / "models/item" / f"{name}_mang{count}.json", ringed)
                if count == MAX_RINGS:
                    shown.append((name, ringed))
            print(f"{name}: rings between y {span[0]:.1f} and {span[1]:.1f}, radius {radius:.2f}")
        root["fallback"] = {
            "type": "minecraft:select",
            "property": "minecraft:custom_model_data",
            "index": 1,
            "cases": [{"when": f"mang{count}", "model": renamed(held, count)}
                      for count in range(1, MAX_RINGS + 1)],
            "fallback": held,
        }
        write(PACK / "items" / f"{item}.json", definition)
    print("preview:", preview(shown))


if __name__ == "__main__":
    main()
