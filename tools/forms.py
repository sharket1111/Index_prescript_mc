"""Procedural 16x16 textures for Caduceus' liquid-metal forms.

Every form shares the brass rod grip at the bottom-left; the black metal part
is described in axis space: u runs along the diagonal from the grip end
(bottom-left) to the tip (top-right), v is the perpendicular offset
(positive = upper-left side).

Usage: python3 tools/forms.py src/main/resources/assets/index_prescript/textures/item [preview.png]
"""
import math
import sys
from PIL import Image

OUT = sys.argv[1]

K = (16, 16, 20, 255)
M = (52, 52, 60, 255)
H = (150, 152, 166, 255)
Y = (226, 214, 150, 255)
O = (170, 150, 90, 255)
D = (120, 104, 62, 255)

A0 = (1.5, 14.5)
A1 = (14.5, 1.5)
L = math.dist(A0, A1)
UX, UY = (A1[0] - A0[0]) / L, (A1[1] - A0[1]) / L
VX, VY = UY, -UX  # perpendicular, pointing up-left
VX, VY = -VX, -VY


def uv(x, y):
    dx, dy = x + 0.5 - A0[0], y + 0.5 - A0[1]
    return dx * UX + dy * UY, dx * VX + dy * VY


def grip(u, v, length=4.6):
    if 0 <= u <= length and abs(v) <= 0.75:
        return Y if v > 0 else O
    return None


def render(shape, extra=None):
    px = {}
    for y in range(16):
        for x in range(16):
            u, v = uv(x, y)
            c = shape(u, v, x, y)
            if c:
                px[(x, y)] = c
    if extra:
        for (x, y), c in extra.items():
            px[(x, y)] = c
    img = Image.new("RGBA", (16, 16))
    for (x, y), c in px.items():
        img.putpixel((x, y), c)
    # outline: transparent pixels 4-adjacent to metal get K
    for y in range(16):
        for x in range(16):
            if (x, y) in px:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if px.get((x + dx, y + dy)) in (M, H):
                    img.putpixel((x, y), K)
                    break
    return img


def metal(v, w):
    return H if v > w - 0.8 and w > 0.9 else M


def blade(u0, u1, w0, w1, u, v, side=0):
    """Tapering blade between u0..u1 with half-width w0 -> w1."""
    if not (u0 <= u <= u1):
        return None
    w = w0 + (w1 - w0) * (u - u0) / (u1 - u0)
    lo, hi = (-w, w) if side == 0 else ((0, w * 2) if side > 0 else (-w * 2, 0))
    if lo <= v <= hi:
        return metal(v, hi)
    return None


def first(*cs):
    for c in cs:
        if c:
            return c
    return None


def hatchet(u, v, x, y):
    return first(grip(u, v),
                 blade(4.6, 14.5, 0.6, 0.6, u, v),
                 blade(10.5, 15.5, 1.0, 2.6, u, v, side=1))


def stiletto(u, v, x, y):
    return first(grip(u, v),
                 blade(4.8, 5.8, 2.2, 2.2, u, v),
                 blade(5.8, L, 0.9, 0.0, u, v))


def bastard_sword(u, v, x, y):
    return first(grip(u, v, 4.0),
                 blade(4.0, 5.2, 2.8, 2.8, u, v),
                 blade(5.2, L - 2.5, 1.3, 1.1, u, v),
                 blade(L - 2.5, L, 1.1, 0.0, u, v))


def rapier(u, v, x, y):
    ring = 4.4 <= u <= 6.4 and 1.1 <= v <= 2.4 and not (5.0 < u < 5.8 and v < 1.9)
    return first(grip(u, v),
                 M if ring else None,
                 blade(4.8, 5.6, 1.6, 1.6, u, v, side=-1),
                 blade(5.6, L, 0.55, 0.3, u, v))


def hammer(u, v, x, y):
    if 11.6 <= u <= 14.6 and abs(v) <= 4.4:
        # framed head with lighter plates
        edge = u < 12.3 or u > 13.9 or abs(v) > 3.6 or abs(v) < 0.5
        return M if edge else H
    return first(grip(u, v),
                 blade(4.6, 14.0, 0.6, 0.6, u, v))


def greatsword(u, v, x, y):
    jag = 3.6 <= u <= 5.2 and abs(v) <= (3.2 if int(u * 2) % 2 else 2.2)
    return first(grip(u, v, 3.6),
                 M if jag else None,
                 blade(5.2, L - 3.5, 2.1, 1.9, u, v),
                 blade(L - 3.5, L, 1.9, 0.0, u, v))


def lance(u, v, x, y):
    return first(grip(u, v, 4.2),
                 blade(4.2, 5.4, 3.4, 3.4, u, v),
                 blade(5.4, L, 2.0, 0.0, u, v))


def scythe(u, v, x, y):
    return first(grip(u, v), M if 4.6 <= u <= L - 0.5 and abs(v) <= 0.6 else None)


# crescent blade sweeping left from the top of the shaft
SCYTHE_BLADE = {
    1: (range(7, 14), H),
    2: (range(4, 13), M),
    3: (range(2, 8), M),
    4: (range(1, 3), M),
}


def scythe_extra():
    return {(x, y): c for y, (xs, c) in SCYTHE_BLADE.items() for x in xs}


def whip(u, v, x, y):
    return grip(u, v)


# whip: chain of diamond links curving from the grip up and to the right
WHIP_NODES = [(5, 9), (6, 6), (8, 4), (11, 3), (13, 5), (14, 8)]


def whip_extra():
    px = {}
    prev = (4, 10)
    for nx, ny in WHIP_NODES:
        steps = max(abs(nx - prev[0]), abs(ny - prev[1]))
        for i in range(1, steps + 1):
            t = i / steps
            px[(round(prev[0] + (nx - prev[0]) * t), round(prev[1] + (ny - prev[1]) * t))] = M
        prev = (nx, ny)
    for nx, ny in WHIP_NODES:
        px[(nx, ny)] = H
    tx, ty = WHIP_NODES[-1]
    px[(tx, ty + 1)] = M
    px[(tx, ty + 2)] = H
    return px


FORMS = {
    "hatchet": hatchet,
    "stiletto": stiletto,
    "bastard_sword": bastard_sword,
    "rapier": rapier,
    "hammer": hammer,
    "greatsword": greatsword,
    "lance": lance,
    "whip": whip,
    "scythe": scythe,
}

if __name__ == "__main__":
    imgs = []
    for name, fn in FORMS.items():
        extra = {"whip": whip_extra, "scythe": scythe_extra}.get(name)
        img = render(fn, extra() if extra else None)
        img.save(f"{OUT}/caduceus_{name}.png")
        imgs.append(img)
    sheet = Image.new("RGBA", (9 * 176, 176), (205, 205, 205, 255))
    for i, img in enumerate(imgs):
        big = img.resize((160, 160), Image.NEAREST)
        sheet.paste(big, (i * 176 + 8, 8), big)
    if len(sys.argv) > 2:
        sheet.save(sys.argv[2])
