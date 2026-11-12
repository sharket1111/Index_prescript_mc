"""Creature bodies for the Mirror Dungeon's Abnormalities and monsters (see
md_models.py): four-legged beasts, winged things, serpents and centipedes,
clams, plants, floating objects with hands, and crabs. Each builder returns a
Model with its own clips. Parts (for part breaking) are named by the
original's part icons (Head, Back, Left Arm...) and by part names."""
import math

from md_models import K, Model, shade

WHITE, BLACK, RED, GLOW = (240, 240, 240), (20, 20, 24), (200, 40, 40), (255, 240, 170)


def C(n, loop, **tracks):
    return {"len": n, "loop": loop, "tracks": {b: [K(*k) for k in ks] for b, ks in tracks.items()}}


def W(n, period, base=(0, 0, 0), amp=(0, 0, 0), phase=0.0, pbase=(0, 0, 0), pamp=(0, 0, 0)):
    """Keys of a sine wave over n ticks: rotation base + amp * sin, position pbase + pamp * sin."""
    out, step = [], max(1, period // 4)
    for t in list(range(0, n, step)) + [n]:
        s = math.sin(2 * math.pi * (t / period + phase))
        out.append((t, tuple(b + a * s for b, a in zip(base, amp)), tuple(b + a * s for b, a in zip(pbase, pamp))))
    return out


def hold(n, r=(0, 0, 0), p=(0, 0, 0), s=1.0):
    return [(0, r, p, s), (n, r, p, s)]


def eyes(col, y, x1, x2, w=1.4, h=1.4):
    return [(x1, y, w, h, col), (x2, y, w, h, col)]


# --- four legs ----------------------------------------------------------------------------------------

def quadruped(mid, c, scale=1.0, H=9, length=16, width=8, bh=7, head=(6, 6, 6), snout=3, horns=None, wool=None, ears=None, tail=9,
              mane=None, squat=False, plates=None, extra=None, no_head=False, spikes=None):
    """A beast: body, head with a jaw, four two-part legs, a tail. c: body, belly, head, eye, leg."""
    m = Model(mid, scale=scale, height=H + bh + 4, anims="none")
    body, headc, eye = c["body"], c.get("head", c["body"]), c.get("eye", GLOW)
    legc = c.get("leg", shade(body, 0.8))
    L, Wd = length, width
    m.bone("root", None, (0, H, 0))
    m.bone("body", "root", (0, H + bh / 2, -L / 2 + 2))
    stripes = [(i * 3 + 1, 0, 1.2, 99, c["stripe"]) for i in range(int(L // 3))] if c.get("stripe") else []
    m.box("body", -Wd / 2, H, -L / 2, Wd, bh, L, body, marks={"east": stripes, "west": stripes, "up": stripes})
    if c.get("belly"):
        m.box("body", -Wd / 2 + 0.6, H - 0.6, -L / 2 + 1, Wd - 1.2, 1, L - 2, c["belly"])
    if wool:
        for i in range(4):
            m.box("body", -Wd / 2 - 1.2 + (i % 2) * 0.6, H + 1 + (i % 2), -L / 2 + i * L / 4, Wd + 2.4 - (i % 2) * 1.2, bh + 1.2, L / 4 + 0.6, wool)
    if plates:
        for i in range(4):
            m.box("body", -1, H + bh, -L / 2 + 1 + i * L / 4, 2, 2.2, L / 4 - 1.2, plates)
    if mane:
        m.box("body", -Wd / 2 - 0.8, H + bh - 4, L / 2 - 4.5, Wd + 1.6, 5.2, 4.5, mane)
    if spikes:
        for i in range(7):
            m.box("body", -Wd / 2 + (i * 37 % int(Wd)), H + bh, -L / 2 + 1 + i * (L - 2) / 7, 1.4, 2 + (i * 53 % 3), 1.6, spikes)
    hw, hh, hd = head
    hy = H + bh - 2 if not squat else H + bh - 4
    m.bone("head", "body", (0, hy, L / 2 - 0.5))
    if no_head:
        snout = 0
    else:
        m.box("head", -hw / 2, hy - hh / 2 + 1, L / 2 - 0.5, hw, hh, hd, headc,
              marks={"north": eyes(eye, 1.2, 0.8, hw - 2.2), "east": [(hd - 2.2, 1.2, 1.4, 1.4, eye)], "west": [(0.8, 1.2, 1.4, 1.4, eye)]})
    if snout:
        m.box("head", -hw / 2 + 1, hy - hh / 2 + 2.2, L / 2 - 0.5 + hd, hw - 2, hh / 2 - 0.6, snout, shade(headc, 0.9),
              marks={"north": [(hw / 2 - 1.6, 0.4, 1.2, 0.8, BLACK)]})
        m.bone("jaw", "head", (0, hy - hh / 2 + 2, L / 2 + 1))
        m.box("jaw", -hw / 2 + 1.2, hy - hh / 2 + 0.8, L / 2 + 1, hw - 2.4, 1.3, hd + snout - 2, c.get("jaw", shade(headc, 0.7)),
              marks={"up": [(i, 0, 0.6, 1, WHITE) for i in range(0, int(hw - 2))]})
    if horns:
        m.mirror_box("head", "head", hw / 2 - 0.4, hy + hh / 2 - 0.6, L / 2 + 1, 3.2, 1.3, 1.3, horns)
        m.mirror_box("head", "head", hw / 2 + 1.8, hy + hh / 2 - 0.2, L / 2 + 1, 1.2, 3.2, 1.2, horns)
    if ears:
        m.mirror_box("head", "head", hw / 2 - 1.8, hy + hh / 2 + 0.6, L / 2 + 0.5, 1.8, 2.6, 1, ears)
    lw = max(2.0, Wd / 3.4)
    for name, sx, z, parent in (("leg_fr", 1, L / 2 - 2.5, "body"), ("leg_fl", -1, L / 2 - 2.5, "body"), ("leg_br", 1, -L / 2 + 2.5, "root"), ("leg_bl", -1, -L / 2 + 2.5, "root")):
        x = sx * (Wd / 2 - lw / 2 + (1.2 if squat else 0))
        m.bone(name, parent, (x, H + 1, z), rest=(0, 0, sx * 28) if squat else (0, 0, 0))
        m.box(name, x - lw / 2, H / 2, z - lw / 2, lw, H / 2 + 1.6, lw, legc)
        m.bone(name + "_low", name, (x, H / 2, z), rest=(0, 0, -sx * 28) if squat else (0, 0, 0))
        m.box(name + "_low", x - lw / 2 + 0.2, 0, z - lw / 2 + 0.2, lw - 0.4, H / 2, lw - 0.4, legc)
        m.box(name + "_low", x - lw / 2, 0, z - lw / 2, lw, 1.2, lw + 1.2, c.get("paw", shade(legc, 0.7)))
    if tail:
        m.bone("tail", "root", (0, H + bh - 1.5, -L / 2), rest=(35, 0, 0))
        m.box("tail", -0.9, -0.9, -tail, 1.8, 1.8, tail, c.get("tail", body), rel=True)
        if c.get("tuft"):
            m.box("tail", -1.5, -1.5, -tail - 2.5, 3, 3, 3, c["tuft"], rel=True)
    m.parts = {"Head": ["head"], "Tail": ["tail"], "Leg": ["leg_fl", "leg_fr"], "Left Arm": ["leg_fl"], "Right Arm": ["leg_fr"]}
    if extra:
        extra(m, H, L, Wd, bh, hy)
    legs = ("leg_fr", "leg_fl", "leg_br", "leg_bl")
    walk = {}
    for i, n in enumerate(legs):
        ph = 0.0 if i in (0, 3) else 0.5
        walk[n] = W(20, 20, amp=(30, 0, 0), phase=ph)
        walk[n + "_low"] = W(20, 20, base=(14, 0, 0), amp=(14, 0, 0), phase=ph + 0.25)
    drop = -(H * 0.55)
    jaw = "jaw" if snout else "head"
    m.clips = {
        "idle": C(60, True, body=W(60, 60, pamp=(0, 0.3, 0)), head=W(60, 60, amp=(3, 4, 0), phase=0.2), tail=W(60, 20, amp=(0, 22, 0)),
                  **({"jaw": W(60, 30, base=(5, 0, 0), amp=(5, 0, 0))} if snout else {})),
        "walk": C(20, True, root=W(20, 10, pamp=(0, 0.5, 0)), head=W(20, 20, amp=(4, 0, 0)), tail=W(20, 10, amp=(0, 25, 0)), **walk),
        "attack_slash": C(14, False, root=[(0,), (4, (0, 0, 0), (0, 0, -2.5)), (7, (0, 0, 0), (0, 0, 6)), (14,)],
                          body=[(0,), (4, (-8, 0, 0)), (7, (6, 0, 0)), (14,)],
                          head=[(0,), (4, (-28, 0, 0)), (7, (16, 0, 0)), (14,)], **{jaw: [(0,), (4, (42, 0, 0)), (7, (0, 0, 0)), (14,)]} if snout else {}),
        "attack_blunt": C(16, False, root=[(0,), (5, (0, 0, 0), (0, -1, -3)), (8, (0, 0, 0), (0, 0, 8)), (11, (0, 0, 0), (0, 0, 7)), (16,)],
                          head=[(0,), (5, (40, 0, 0)), (8, (20, 0, 0)), (16,)], body=[(0,), (5, (6, 0, 0)), (8, (-4, 0, 0)), (16,)]),
        "attack_pierce": C(14, False, root=[(0,), (4, (0, 0, 0), (0, -1.5, -2)), (7, (0, 0, 0), (0, 2, 7)), (14,)],
                           body=[(0,), (4, (10, 0, 0)), (7, (-22, 0, 0)), (14,)],
                           leg_fr=[(0,), (4, (20, 0, 0)), (7, (-70, 0, 0)), (14,)], leg_fl=[(0,), (4, (20, 0, 0)), (7, (-60, 0, 0)), (14,)],
                           head=[(0,), (7, (20, 0, 0)), (14,)]),
        "guard": C(20, True, root=hold(20, p=(0, drop * 0.4, 0)), head=hold(20, (26, 0, 0)), body=hold(20, (6, 0, 0)),
                   **{n: hold(20, (-20 if "f" in n[4:] else 20, 0, 0)) for n in legs}, **{n + "_low": hold(20, (40, 0, 0)) for n in legs}),
        "stagger": C(40, True, root=W(40, 40, pbase=(0, drop, 0), pamp=(0, 0.3, 0)), head=W(40, 40, base=(34, 0, 12), amp=(3, 0, 0)),
                     tail=hold(40, (-30, 0, 0)), **{n: hold(40, (0, 0, (48 if n.endswith("r") else -48))) for n in legs},
                     **{n + "_low": hold(40, (0, 0, (-70 if n.endswith("r") else 70))) for n in legs}),
        "roar": C(30, False, body=[(0,), (8, (-26, 0, 0)), (22, (-26, 0, 0)), (30,)], head=[(0,), (8, (-30, 0, 0)), (15, (-34, 6, 0)), (22, (-30, -6, 0)), (30,)],
                  leg_fr=[(0,), (8, (-40, 0, 0)), (22, (-30, 0, 0)), (30,)], leg_fl=[(0,), (8, (-30, 0, 0)), (22, (-40, 0, 0)), (30,)],
                  **({"jaw": [(0,), (8, (46, 0, 0)), (22, (46, 0, 0)), (30,)]} if snout else {})),
        "death": C(24, False, root=[(0,), (8, (0, 0, 20), (0, -1, 0)), (18, (0, 0, 86), (0, drop * 1.2, 0)), (24, (0, 0, 90), (0, drop * 1.25, 0))],
                   head=[(0,), (18, (20, 0, 0)), (24, (30, 0, 0))], **{n: [(0,), (24, (25 if "f" in n[4:] else -25, 0, 0))] for n in legs}),
    }
    return m


# --- wings ---------------------------------------------------------------------------------------------

def winged(mid, c, scale=1.0, span=14, hover=14, antennae=True, crown=None, long_legs=False, flame=False):
    """A moth or fairy: a hovering body with four wings. c: body, wing, wing2, spot, eye."""
    m = Model(mid, scale=scale, height=hover + 14, anims="none", bright=c.get("bright", False))
    body, wing, eye = c["body"], c["wing"], c.get("eye", GLOW)
    y = hover
    m.bone("root", None, (0, y, 0))
    m.bone("body", "root", (0, y, 0), rest=(-18, 0, 0))
    m.box("body", -3, y - 3, -3, 6, 7, 6, body, marks={"north": [(1, 4, 4, 0.6, shade(body, 0.6))]})
    m.bone("abdomen", "body", (0, y - 2, -2), rest=(38, 0, 0))
    m.box("abdomen", -2.4, -9, -2.4, 4.8, 9, 4.8, c.get("abdomen", shade(body, 0.85)), rel=True,
          marks={f: [(0, 2, 99, 0.8, c.get("band", shade(body, 0.55))), (0, 5, 99, 0.8, c.get("band", shade(body, 0.55)))] for f in ("north", "south", "east", "west")})
    m.bone("head", "body", (0, y + 4, 0.5))
    m.box("head", -2.6, y + 4, -2, 5.2, 4.6, 5, c.get("head", body), marks={"north": eyes(eye, 1.2, 0.5, 3.1, 1.6, 1.8)})
    if antennae:
        m.mirror_box("head", "head", 0.8, y + 8.4, 1, 0.6, 4, 0.6, c.get("antenna", shade(body, 0.6)))
        m.mirror_box("head", "head", 0.6, y + 12, 1, 1.6, 0.8, 0.8, c.get("antenna", shade(body, 0.6)))
    if crown:
        m.box("head", -2, y + 8.6, -1.4, 4, 1.6, 3.8, crown)
    spot = c.get("spot", shade(wing, 1.3))
    for side, sg in (("r", 1), ("l", -1)):
        m.bone("wing_%su" % side, "body", (sg * 2.6, y + 3, -2), rest=(0, 0, sg * 12))
        x0 = 0 if sg > 0 else -span
        m.box("wing_%su" % side, x0, -1, -0.3, span, span * 0.75, 0.6, wing, rel=True,
              marks={f: [(span * 0.45, span * 0.2, span * 0.3, span * 0.3, spot), (span * 0.55, span * 0.28, span * 0.12, span * 0.12, BLACK),
                         (0, 0, span, 0.8, shade(wing, 0.6))] for f in ("north", "south")})
        if flame:  # wings of fire: a bright heart, tongues of flame at the edge
            n, hot, edge = "wing_%su" % side, c.get("spot", (255, 226, 100)), c.get("wing2", wing)
            m.box(n, sg * 1 - (0 if sg > 0 else span * 0.5), 0.5, -0.5, span * 0.5, span * 0.45, 1.0, hot, rel=True, plain=True)
            for i, (fx, fh) in enumerate(((0.15, 0.5), (0.4, 0.75), (0.65, 0.55), (0.85, 0.3))):
                w2 = span * 0.16
                m.box(n, sg * span * fx - (0 if sg > 0 else w2), span * 0.75 - 1.2, -0.3, w2, span * fh, 0.6, edge if i % 2 else wing, rel=True)
            m.box(n, sg * span - (0 if sg > 0 else span * 0.3), span * 0.2, -0.3, span * 0.3, span * 0.2, 0.6, edge, rel=True)
        m.bone("wing_%sl" % side, "body", (sg * 2.4, y, -2), rest=(0, 0, -sg * 16))
        x1 = 0 if sg > 0 else -span * 0.7
        m.box("wing_%sl" % side, x1, -span * 0.6, -0.3, span * 0.7, span * 0.62, 0.6, c.get("wing2", shade(wing, 0.82)), rel=True,
              marks={f: [(span * 0.25, span * 0.2, span * 0.2, span * 0.2, spot)] for f in ("north", "south")})
        lx = sg * 1.6 - 0.4
        m.box("body", lx, y - (12 if long_legs else 6), 1.5, 0.8, 9 if long_legs else 3.5, 0.8, c.get("leg", BLACK))
    m.parts = {"Back": ["wing_ru", "wing_rl", "wing_lu", "wing_ll"], "Wings": ["wing_ru", "wing_rl", "wing_lu", "wing_ll"], "Head": ["head"]}

    def flap(n, period, amp=38, base=10, droop=0):
        return {"wing_ru": W(n, period, base=(0, -base, droop), amp=(0, -amp, 0)), "wing_lu": W(n, period, base=(0, base, -droop), amp=(0, amp, 0)),
                "wing_rl": W(n, period, base=(0, -base, droop), amp=(0, -amp * 0.8, 0), phase=-0.08),
                "wing_ll": W(n, period, base=(0, base, -droop), amp=(0, amp * 0.8, 0), phase=-0.08)}
    fold = lambda n, a: {"wing_ru": hold(n, (0, -a, 0)), "wing_lu": hold(n, (0, a, 0)), "wing_rl": hold(n, (0, -a, 0)), "wing_ll": hold(n, (0, a, 0))}
    m.clips = {
        "idle": C(40, True, root=W(40, 40, pamp=(0, 1.4, 0)), abdomen=W(40, 20, amp=(6, 0, 0)), head=W(40, 40, amp=(0, 6, 0)), **flap(40, 10)),
        "walk": C(20, True, root=W(20, 20, base=(14, 0, 0), pamp=(0, 1, 0)), **flap(20, 5, 42)),
        "attack_slash": C(14, False, root=[(0,), (4, (-20, 0, 0), (0, 3, -3)), (7, (38, 0, 0), (0, -3, 8)), (14,)], **flap(14, 7, 50)),
        "attack_blunt": C(16, False, root=[(0,), (5, (-10, 0, 0), (0, 2, -2)), (8, (10, 0, 0), (0, 0, 3)), (16,)],
                          wing_ru=[(0,), (5, (0, 55, 0)), (8, (0, -85, 0)), (16,)], wing_lu=[(0,), (5, (0, -55, 0)), (8, (0, 85, 0)), (16,)],
                          wing_rl=[(0,), (5, (0, 45, 0)), (8, (0, -75, 0)), (16,)], wing_ll=[(0,), (5, (0, -45, 0)), (8, (0, 75, 0)), (16,)]),
        "attack_pierce": C(14, False, root=[(0,), (4, (0, 0, 0), (0, 4, -2)), (7, (50, 0, 0), (0, -5, 9)), (14,)],
                           abdomen=[(0,), (7, (-60, 0, 0)), (14,)], **flap(14, 7, 30)),
        "guard": C(20, True, root=W(20, 20, pamp=(0, 0.6, 0)), **fold(20, 78)),
        "stagger": C(40, True, root=W(40, 40, base=(30, 0, 8), pbase=(0, -hover + 5, 0), pamp=(0, 0.3, 0)), head=hold(40, (25, 0, 0)), **flap(40, 40, 6, -20, -35)),
        "roar": C(30, False, root=[(0,), (8, (-20, 0, 0), (0, 3, 0)), (22, (-20, 0, 0), (0, 3, 0)), (30,)],
                  wing_ru=[(0,), (8, (0, 20, 15), (0, 0, 0), 1.25), (22, (0, 20, 15), (0, 0, 0), 1.25), (30,)],
                  wing_lu=[(0,), (8, (0, -20, -15), (0, 0, 0), 1.25), (22, (0, -20, -15), (0, 0, 0), 1.25), (30,)],
                  wing_rl=[(0,), (8, (0, 20, -10), (0, 0, 0), 1.2), (22, (0, 20, -10), (0, 0, 0), 1.2), (30,)],
                  wing_ll=[(0,), (8, (0, -20, 10), (0, 0, 0), 1.2), (22, (0, -20, 10), (0, 0, 0), 1.2), (30,)]),
        "death": C(24, False, root=[(0,), (10, (30, 0, 30), (0, -hover * 0.5, 0)), (20, (70, 0, 80), (0, -hover + 3, 0)), (24, (70, 0, 84), (0, -hover + 2.5, 0))],
                   **{k: [(0,), (20, v[0][1])] for k, v in fold(1, 60).items()}),
    }
    return m


# --- a chain of segments -------------------------------------------------------------------------------------

def serpent(mid, c, scale=1.0, front=3, back=6, w=5, h=4, seg=6, legs=True, fins=False, hover=0, head=(7, 5, 7), mandibles=True, lure=None, rise=24):
    """A centipede, serpent or eel: a chain of segments, the front ones raised. c: body, plate, leg, head, eye."""
    m = Model(mid, scale=scale, height=max(10, hover + seg * front * 0.7 + 6), anims="none", bright=c.get("bright", False))
    body, plate = c["body"], c.get("plate", shade(c["body"], 0.7))
    y = hover + h / 2 + (1.5 if legs else 0)
    m.bone("root", None, (0, y, 0))

    def seg_box(b, z0, i, ww, hh):
        m.box(b, -ww / 2, -hh / 2, z0, ww, hh, seg, body if i % 2 else shade(body, 0.9), rel=True)
        m.box(b, -ww / 2 - 0.3, hh / 2 - 0.6, z0 + 0.6, ww + 0.6, 1.1, seg - 1.2, plate, rel=True)
        if legs:
            for sg in (1, -1):
                m.box(b, sg * ww / 2 - (0 if sg > 0 else 3), -hh / 2 - 1.4, z0 + seg / 2 - 0.5, 3, 1, 1, c.get("leg", plate), rel=True)
                m.box(b, sg * (ww / 2 + 2.2) - 0.5, -hh / 2 - 2.6 - (0.0 if hover else 0), z0 + seg / 2 - 0.5, 1, 2.2, 1, c.get("leg", plate), rel=True)
        if fins:
            m.box(b, -0.3, hh / 2, z0 + 1, 0.6, 2.6, seg - 2, c.get("fin", plate), rel=True)
    m.bone("s0", "root", (0, y, 0))
    seg_box("s0", -seg / 2, 0, w, h)
    prev, z = "s0", seg / 2
    names_f = []
    for i in range(front):
        n = "f%d" % (i + 1)
        m.bone(n, prev, (0, y, z), rest=(-rise if i < 2 else rise * 0.6, 0, 0))
        seg_box(n, 0, i + 1, w, h)
        names_f.append(n)
        prev, z = n, z + seg
    hw, hh, hd = head
    m.bone("head", prev, (0, y, z), rest=(rise * 0.9, 0, 0))
    m.box("head", -hw / 2, -hh / 2, 0, hw, hh, hd, c.get("head", shade(body, 0.8)), rel=True,
          marks={"north": eyes(c.get("eye", GLOW), 1, 0.8, hw - 2.4, 1.6, 1.4), "up": eyes(c.get("eye", GLOW), hd - 2.4, 0.6, hw - 2, 1.4, 1.4)})
    if mandibles:
        for side, sg in (("r", 1), ("l", -1)):
            m.bone("mandible_" + side, "head", (sg * (hw / 2 - 0.8), y - 1, z + hd - 0.5))
            m.box("mandible_" + side, -0.7, -0.7, 0, 1.4, 1.4, 4.5, c.get("fang", (230, 220, 190)), rel=True)
            m.box("mandible_" + side, -0.7 - sg * 1.2, -0.6, 3.6, 1.4, 1.2, 1.6, c.get("fang", (230, 220, 190)), rel=True)
    if lure:
        m.box("head", -0.4, hh / 2, hd - 2, 0.8, 5, 0.8, plate, rel=True)
        m.box("head", -1.3, hh / 2 + 4.5, hd - 1, 2.6, 2.6, 2.6, lure, rel=True, plain=True)
    prev, z = "s0", -seg / 2
    names_b = []
    for i in range(back):
        n = "b%d" % (i + 1)
        m.bone(n, prev, (0, y, z))
        t = 1 - 0.55 * (i / max(1, back - 1)) ** 2
        m.box(n, -w * t / 2, -h * t / 2, -seg, w * t, h * t, seg, body if i % 2 else shade(body, 0.9), rel=True)
        m.box(n, -w * t / 2 - 0.3, h * t / 2 - 0.6, -seg + 0.6, w * t + 0.6, 1.1, seg - 1.2, plate, rel=True)
        if legs:
            for sg in (1, -1):
                m.box(n, sg * w * t / 2 - (0 if sg > 0 else 3), -h * t / 2 - 1.4, -seg / 2 - 0.5, 3, 1, 1, c.get("leg", plate), rel=True)
        if fins:
            m.box(n, -0.3, h * t / 2, -seg + 1, 0.6, 2.6 * t, seg - 2, c.get("fin", plate), rel=True)
        names_b.append(n)
        prev, z = n, z - seg
    if fins:
        m.box(prev, -0.3, -4, -seg - 4, 0.6, 8, 5, c.get("fin", plate), rel=True)
    m.parts = {"Head": ["head"], "Tail": names_b[len(names_b) // 2:], "Body": [], "Fluorescence": []}
    und = lambda n, period, amp: {**{b: W(n, period, amp=(0, amp, 0), phase=-0.14 * (i + 1)) for i, b in enumerate(names_b)},
                                  **{f: W(n, period, amp=(0, amp * 0.6, 0), phase=0.14 * (i + 1)) for i, f in enumerate(names_f)}}
    mand = lambda keys: {"mandible_r": [(t, (0, -a, 0)) for t, a in keys], "mandible_l": [(t, (0, a, 0)) for t, a in keys]} if mandibles else {}
    f1 = names_f[0]
    m.clips = {
        "idle": C(40, True, root=W(40, 40, pamp=(0, 0.5 if hover else 0.15, 0)), head=W(40, 40, amp=(4, 8, 0)), **und(40, 40, 7), **mand([(0, 10), (20, 22), (40, 10)])),
        "walk": C(20, True, **und(20, 20, 16), **mand([(0, 10), (20, 10)])),
        "attack_slash": C(14, False, **{f1: [(0,), (4, (-22, 0, 0)), (7, (46, 0, 0)), (10, (40, 0, 0)), (14,)]},
                          root=[(0,), (4, (0, 0, 0), (0, 0, -2)), (7, (0, 0, 0), (0, 0, 5)), (14,)],
                          head=[(0,), (4, (-20, 0, 0)), (7, (10, 0, 0)), (14,)], **mand([(0, 10), (4, 50), (7, -5), (14, 10)])),
        "attack_blunt": C(16, False, root=[(0,), (5, (0, 50, 0)), (8, (0, -200, 0), (0, 0, 3)), (12, (0, -360, 0)), (16, (0, -360, 0))],
                          **{b: [(0,), (5, (0, 14, 0)), (8, (0, -8, 0)), (16,)] for b in names_b}),
        "attack_pierce": C(14, False, **{f: [(0,), (4, (-12, 0, 0)), (7, (rise * 0.8, 0, 0)), (14,)] for f in names_f[:2]},
                           root=[(0,), (4, (0, 0, 0), (0, 0, -3)), (7, (0, 0, 0), (0, 0, 7)), (14,)], **mand([(0, 10), (4, 40), (7, 0), (14, 10)])),
        "guard": C(20, True, **{b: hold(20, (0, 34, 0)) for b in names_b}, **{f: hold(20, (0, -16, 0)) for f in names_f}, head=hold(20, (20, -30, 0))),
        "stagger": C(40, True, **{f: W(40, 40, base=(rise * 0.9 if i < 2 else -rise * 0.5, 0, 0), amp=(2, 0, 0)) for i, f in enumerate(names_f)},
                     head=hold(40, (-rise * 0.6, 0, 20)), **{b: hold(40, (0, 10 * (-1) ** i, 0)) for i, b in enumerate(names_b)}),
        "roar": C(30, False, **{f: [(0,), (8, (-14, 0, 0)), (22, (-14, 0, 0)), (30,)] for f in names_f[:2]},
                  head=[(0,), (8, (-30, 0, 0)), (15, (-30, 12, 0)), (22, (-30, -12, 0)), (30,)], **mand([(0, 10), (8, 60), (22, 60), (30, 10)])),
        "death": C(24, False, root=[(0,), (20, (0, 0, 170), (0, -hover, 0)), (24, (0, 0, 180), (0, -hover, 0))],
                   **{f: [(0,), (16, (rise * 0.9 if i < 2 else -rise * 0.6, 0, 0)), (24, (rise if i < 2 else -rise * 0.6, 0, 0))] for i, f in enumerate(names_f)},
                   **{b: [(0,), (24, (0, 18 * (-1) ** i, 0))] for i, b in enumerate(names_b)}),
    }
    return m


# --- a clam -----------------------------------------------------------------------------------------------

def clam(mid, c, scale=1.0, stilts=0):
    """A shell with a pearl; with stilts, carried high on six long thin legs."""
    m = Model(mid, scale=scale, height=18 + stilts, anims="none")
    shell, inner, pearl = c["shell"], c.get("inner", (226, 160, 170)), c.get("pearl", (250, 250, 255))
    m.bone("root", None, (0, 3 + stilts, 0))
    m.bone("lift", "root", (0, 3 + stilts, 0))
    _bone, _box = m.bone, m.box
    if stilts:
        legc = c.get("leg", BLACK)
        for side, sg in (("r", 1), ("l", -1)):
            for i, z in enumerate((6, 0, -6)):
                n = "leg_%s%d" % (side, i + 1)
                _bone(n, "root", (sg * 8, 5 + stilts, z), rest=(0, -sg * (i - 1) * 25, sg * 35))
                _box(n, 0 if sg > 0 else -10, -0.5, -0.5, 10, 1, 1, legc, rel=True)
                _bone(n + "_low", n, (sg * 18, 5 + stilts, z), rest=(0, 0, -sg * 105))
                _box(n + "_low", 0 if sg > 0 else -(stilts + 9), -0.4, -0.4, stilts + 9, 0.8, 0.8, legc, rel=True)
        # Everything below is drawn lifted by the stilts.
        m.bone = lambda name, parent, pivot, rest=(0, 0, 0): _bone(name, "lift" if parent == "root" else parent, (pivot[0], pivot[1] + stilts, pivot[2]), rest)
        m.box = lambda bone, x, y, z, w, h, d, color, **kw: _box(bone, x, y if kw.get("rel") else y + stilts, z, w, h, d, color, **kw)
    m.bone("lower", "root", (0, 3, 0))
    ridge = lambda: {"north": [(i * 3 + 1, 0, 0.8, 99, shade(shell, 0.7)) for i in range(6)]}
    m.box("lower", -9, 3, -8, 18, 4.5, 16, shell, marks=ridge())
    m.box("lower", -7.5, 7, -6.5, 15, 1, 13, inner)
    for sx in (1, -1):
        for z in (-5, 4):
            m.box("lower", sx * 6 - 1, 0, z, 2, 3.2, 2, c.get("leg", shade(shell, 0.6)))
    m.bone("upper", "root", (0, 7.6, -8), rest=(-14, 0, 0))
    m.box("upper", -9, 0, 0, 18, 5, 16, shell, rel=True, marks={"up": [(i * 3 + 1, 0, 0.8, 99, shade(shell, 0.7)) for i in range(6)], **ridge()})
    m.box("upper", -7.5, -0.6, 1.5, 15, 0.8, 13, inner, rel=True)
    m.box("upper", -5, 5, 4, 10, 1.6, 8, shade(shell, 0.85), rel=True)
    m.bone("pearl", "root", (0, 10.5, 0))
    m.box("pearl", -3, 8, -3, 6, 6, 6, pearl, plain=True, marks={"north": eyes(c.get("eye", (60, 60, 90)), 2, 1, 3.6)})
    m.parts = {"Shell": ["upper"], "Pearl": ["pearl"], "Head": ["pearl"], "Body": []}
    if stilts:
        del m.__dict__["bone"], m.__dict__["box"]
    m.clips = {
        "idle": C(40, True, upper=W(40, 40, base=(-6, 0, 0), amp=(5, 0, 0)), pearl=W(40, 20, pamp=(0, 0.3, 0))),
        "walk": C(20, True, root=W(20, 20, amp=(0, 0, 6), pamp=(0, 0.6, 0)), upper=W(20, 10, base=(-4, 0, 0), amp=(4, 0, 0))),
        "attack_blunt": C(16, False, root=[(0,), (5, (-14, 0, 0), (0, 3, -2)), (8, (10, 0, 0), (0, 0, 6)), (16,)], upper=[(0,), (5, (-62, 0, 0)), (8, (14, 0, 0)), (16,)]),
        "attack_slash": C(14, False, root=[(0,), (4, (0, 40, 0)), (7, (0, -60, 0), (0, 0, 5)), (14,)], upper=[(0,), (4, (-40, 0, 0)), (7, (10, 0, 0)), (14,)]),
        "attack_pierce": C(14, False, upper=[(0,), (4, (-55, 0, 0)), (10, (-55, 0, 0)), (14,)], pearl=[(0,), (4, (0, 0, 0), (0, 2, 0), 1.2), (7, (0, 0, 0), (0, 3, 12), 1.3), (14,)]),
        "guard": C(20, True, upper=hold(20, (14, 0, 0)), root=hold(20, p=(0, -1, 0))),
        "stagger": C(40, True, upper=W(40, 40, base=(-80, 0, 0), amp=(4, 0, 0)), pearl=W(40, 20, amp=(0, 0, 10)), root=hold(40, (0, 0, 8))),
        "roar": C(30, False, upper=[(0,), (8, (-70, 0, 0)), (22, (-70, 0, 0)), (30,)], pearl=[(0,), (8, (0, 0, 0), (0, 3, 0), 1.45), (22, (0, 180, 0), (0, 3, 0), 1.45), (30,)]),
        "death": C(24, False, upper=[(0,), (12, (-100, 0, 0)), (24, (-110, 0, 0))], pearl=[(0,), (12, (0, 0, 0), (0, 2, 0)), (24, (0, 0, 60), (0, -5, 7), 0.6)],
                   root=[(0,), (24, (0, 0, 10), (0, -1.5, 0))]),
    }
    if stilts:
        legs = ["leg_%s%d" % (s2, i) for s2 in "rl" for i in (1, 2, 3)]
        sgn = lambda n: 1 if "_r" in n else -1
        m.clips["walk"] = C(16, True, lift=W(16, 8, pamp=(0, 0.5, 0)), **{n: W(16, 16, amp=(0, 16, sgn(n) * 8), phase=(int(n[-1]) % 2) * 0.5) for n in legs})
        m.clips["idle"]["tracks"].update(C(40, True, **{n: W(40, 40, amp=(0, 0, sgn(n) * 3), phase=int(n[-1]) * 0.2) for n in legs})["tracks"])
        for name, drop in (("stagger", stilts * 0.75), ("death", stilts * 0.95)):
            m.clips[name]["tracks"]["lift"] = [K(0), K(12, p=(0, -drop, 0)), K(m.clips[name]["len"], p=(0, -drop, 0))]
            for n in legs:
                m.clips[name]["tracks"][n] = [K(0), K(12, (0, 0, sgn(n) * 40)), K(m.clips[name]["len"], (0, 0, sgn(n) * 40))]
    return m


# --- plants ----------------------------------------------------------------------------------------------------

def plant(mid, c, scale=1.0, bloom="apple", trunk=14, arm=8, thorns=False):
    """A plant with two vine arms and a bloom: apple, rose, lantern, cotton or bud. c: trunk, vine, bloom, accent, eye."""
    m = Model(mid, scale=scale, height=trunk + 14, anims="none", bright=c.get("bright", False))
    tk, vine, bl, eye = c["trunk"], c.get("vine", c["trunk"]), c["bloom"], c.get("eye", GLOW)
    accent = c.get("accent", shade(bl, 0.6))
    m.bone("root", None, (0, 0, 0))
    m.bone("roots", "root", (0, 0, 0))
    for i, (x, z) in enumerate(((3, 3), (-4, 2), (2, -4), (-3, -3), (5, -1), (-5.5, -0.5))):
        m.box("roots", x - 1.2, 0, z - 1.2, 2.6 + (i % 2), 1.6, 2.6 + ((i + 1) % 2), shade(tk, 0.75))
    m.bone("trunk", "root", (0, 1, 0))
    m.box("trunk", -2.6, 1, -2.6, 5.2, trunk - 1, 5.2, tk, marks={f: [(1, 3, 0.8, 4, shade(tk, 0.6)), (3.4, 7, 0.8, 4, shade(tk, 0.6))] for f in ("north", "south", "east", "west")})
    if thorns:
        for i in range(4):
            m.box("trunk", (-3.8 if i % 2 else 2.6), 3 + i * 2.6, -0.5, 1.2, 1, 1, accent)
    m.bone("crown", "trunk", (0, trunk, 0))
    y = trunk
    if bloom == "apple":
        m.box("crown", -6, y, -6, 12, 11, 12, bl, marks={"north": eyes(eye, 4, 2.4, 7.6, 2, 2.4) + ([(3, 7.6, 6, 1.4, c["mouth"])] if c.get("mouth") else [])})
        m.box("crown", -4.5, y + 11, -4.5, 9, 1, 9, shade(bl, 1.1))
        m.box("crown", -0.6, y + 12, -0.6, 1.2, 3, 1.2, tk)
        m.box("crown", 0.6, y + 13.5, -1.2, 4, 0.6, 2.4, c.get("leaf", (70, 140, 60)))
        if c.get("rot"):
            m.box("crown", 2, y + 2, 5.6, 3.4, 4, 0.8, c["rot"])
            m.box("crown", -6.4, y + 5, -2, 0.8, 3.4, 4, c["rot"])
    elif bloom == "rose":
        m.box("crown", -4, y, -4, 8, 6, 8, bl, marks={"north": eyes(eye, 2.4, 1.6, 5, 1.4, 1.6)})
        m.box("crown", -6, y + 1.5, -3.5, 2, 6, 7, shade(bl, 0.84))
        m.box("crown", 4, y + 1.5, -3.5, 2, 6, 7, shade(bl, 0.84))
        m.box("crown", -3.5, y + 1.5, -6, 7, 6, 2, shade(bl, 0.9))
        m.box("crown", -3.5, y + 3, 4, 7, 5, 2, shade(bl, 0.9))
        m.box("crown", -2.5, y + 6, -2.5, 5, 2.4, 5, shade(bl, 1.15))
        m.box("crown", -5, y - 1, -5, 10, 1, 10, c.get("leaf", (60, 120, 60)))
    elif bloom == "lantern":
        m.box("crown", -5, y, -4, 10, 6, 9, bl, marks={"north": [(1, 3.4, 8, 1.6, BLACK)] + [(1.4 + i * 1.6, 3.4, 0.8, 1, WHITE) for i in range(5)]})
        m.box("crown", -0.5, y + 6, 2, 1, 6, 1, vine)
        m.box("crown", -0.5, y + 11.5, 2, 1, 1, 5, vine)
        m.box("crown", -1.8, y + 7, 6, 3.6, 4.2, 3.6, c.get("light", GLOW), plain=True)
    elif bloom == "cotton":
        m.box("crown", -3, y, -3, 6, 5, 6, tk, marks={"north": eyes(eye, 1.6, 1, 3.6)})
        for dx, dy, dz, s2 in ((-6, 3, -2, 6), (1, 4, -5, 6), (-2, 6, 1, 6.5), (-5, 1, 2, 5), (2, 2, 2, 5)):
            m.box("crown", dx, y + dy, dz, s2, s2 * 0.8, s2, bl)
    else:  # bud
        m.box("crown", -3.5, y, -3.5, 7, 8, 7, bl, marks={"north": eyes(eye, 3, 1.2, 4.4, 1.4, 1.8)})
        m.box("crown", -2, y + 8, -2, 4, 3, 4, accent)
    for side, sg in (("r", 1), ("l", -1)):
        m.bone("vine_%s1" % side, "trunk", (sg * 2.6, trunk - 3, 0), rest=(0, 0, -sg * 28))
        x0 = 0 if sg > 0 else -arm
        m.box("vine_%s1" % side, x0, -1, -1, arm, 2, 2, vine, rel=True)
        m.bone("vine_%s2" % side, "vine_%s1" % side, (sg * (2.6 + arm), trunk - 3, 0), rest=(0, 0, -sg * 34))
        x1 = 0 if sg > 0 else -arm * 0.9
        m.box("vine_%s2" % side, x1, -0.8, -0.8, arm * 0.9, 1.6, 1.6, shade(vine, 0.9), rel=True)
        m.box("vine_%s2" % side, sg * arm * 0.9 - (0 if sg > 0 else 2.4), -1.4, -1.4, 2.4, 2.8, 2.8, accent, rel=True)
        if thorns:
            m.box("vine_%s1" % side, x0 + arm / 2, 1, -0.4, 0.8, 1.2, 0.8, accent, rel=True)
    R, Lf = ["vine_r1", "vine_r2"], ["vine_l1", "vine_l2"]
    m.parts = {"Right Arm": R, "Left Arm": Lf, "Right Stem": R, "Left Stem": Lf, "Head": ["crown"], "Fruit": ["crown"], "Roots": ["roots"], "Leg": ["roots"]}
    whip = lambda a, sg: {a + "1": [(0, (0, 0, sg * 5)), (4, (0, sg * 50, sg * 40)), (7, (0, -sg * 95, sg * 20)), (10, (0, -sg * 85, sg * 16)), (14, (0, 0, sg * 5))],
                          a + "2": [(0,), (4, (0, sg * 30, 0)), (7, (0, -sg * 20, sg * 30)), (14,)]}
    m.clips = {
        "idle": C(60, True, trunk=W(60, 60, amp=(0, 0, 2.5)), crown=W(60, 60, amp=(3, 0, 3), phase=0.2),
                  vine_r1=W(60, 30, base=(0, 0, 5), amp=(0, 6, 6)), vine_l1=W(60, 30, base=(0, 0, -5), amp=(0, 6, -6), phase=0.3),
                  vine_r2=W(60, 30, amp=(0, 0, 10), phase=0.2), vine_l2=W(60, 30, amp=(0, 0, -10), phase=0.5)),
        "walk": C(20, True, root=W(20, 20, amp=(0, 0, 5), pamp=(0, 0, 0)), roots=W(20, 10, amp=(0, 14, 0)), trunk=W(20, 20, base=(6, 0, 0), amp=(0, 0, -3)),
                  vine_r1=W(20, 20, amp=(0, 20, 0)), vine_l1=W(20, 20, amp=(0, 20, 0))),
        "attack_slash": C(14, False, trunk=[(0,), (4, (0, 20, 0)), (7, (8, -22, 0)), (14,)], **whip("vine_r", 1)),
        "attack_right_arm": C(14, False, trunk=[(0,), (4, (0, 20, 0)), (7, (8, -22, 0)), (14,)], **whip("vine_r", 1)),
        "attack_left_arm": C(14, False, trunk=[(0,), (4, (0, -20, 0)), (7, (8, 22, 0)), (14,)], **whip("vine_l", -1)),
        "attack_blunt": C(16, False, trunk=[(0,), (5, (-12, 0, 0)), (8, (24, 0, 0)), (11, (22, 0, 0)), (16,)],
                          vine_r1=[(0,), (5, (0, 0, 95)), (8, (0, -40, -25)), (11, (0, -40, -25)), (16,)], vine_l1=[(0,), (5, (0, 0, -95)), (8, (0, 40, 25)), (11, (0, 40, 25)), (16,)],
                          vine_r2=[(0,), (5, (0, 0, 30)), (8, (0, 0, 20)), (16,)], vine_l2=[(0,), (5, (0, 0, -30)), (8, (0, 0, -20)), (16,)]),
        "attack_pierce": C(14, False, trunk=[(0,), (4, (-20, 0, 0)), (7, (46, 0, 0)), (10, (40, 0, 0)), (14,)], crown=[(0,), (4, (-10, 0, 0)), (7, (20, 0, 0), (0, 0, 0), 1.15), (14,)]),
        "attack_head": C(14, False, trunk=[(0,), (4, (-20, 0, 0)), (7, (46, 0, 0)), (10, (40, 0, 0)), (14,)], crown=[(0,), (4, (-10, 0, 0)), (7, (20, 0, 0), (0, 0, 0), 1.15), (14,)]),
        "guard": C(20, True, vine_r1=hold(20, (0, -78, 24)), vine_l1=hold(20, (0, 78, -24)), vine_r2=hold(20, (0, -30, 30)), vine_l2=hold(20, (0, 30, -30)), trunk=hold(20, (8, 0, 0))),
        "stagger": C(40, True, trunk=W(40, 40, base=(30, 0, 6), amp=(3, 0, 0)), crown=hold(40, (32, 0, 10)),
                     vine_r1=hold(40, (0, 0, -50)), vine_l1=hold(40, (0, 0, 50)), vine_r2=hold(40, (0, 0, -20)), vine_l2=hold(40, (0, 0, 20))),
        "roar": C(30, False, trunk=[(0,), (8, (-12, 0, 0)), (22, (-12, 0, 0)), (30,)], crown=[(0,), (8, (-10, 0, 0), (0, 0, 0), 1.2), (15, (-10, 0, 8), (0, 0, 0), 1.25), (22, (-10, 0, -8), (0, 0, 0), 1.2), (30,)],
                  vine_r1=[(0,), (8, (0, 0, 85)), (22, (0, 0, 85)), (30,)], vine_l1=[(0,), (8, (0, 0, -85)), (22, (0, 0, -85)), (30,)]),
        "death": C(24, False, trunk=[(0,), (10, (-30, 0, 10)), (20, (-84, 0, 14)), (24, (-88, 0, 14))], crown=[(0,), (24, (-20, 0, 30), (0, 0, 0), 0.8)],
                   vine_r1=[(0,), (24, (0, 0, -60))], vine_l1=[(0,), (24, (0, 0, 60))], roots=[(0,), (24, (0, 0, 0), (0, 0, 0), 0.8)]),
    }
    return m


# --- floating things with hands -----------------------------------------------------------------------------------

def totem(mid, c, scale=1.0, core="slab", hover=10, hands=True, face=True, rotors=False):
    """A floating object with a face and two hands: slab (a doll or calendar), frame, spiral, heart, statue, orb, drone. c: body, face, hand, eye, accent."""
    m = Model(mid, scale=scale, height=hover + 20, anims="none", bright=c.get("bright", False))
    body, eye, accent = c["body"], c.get("eye", GLOW), c.get("accent", shade(c["body"], 0.6))
    y = hover
    m.bone("root", None, (0, y + 8, 0))
    m.bone("core", "root", (0, y + 8, 0))
    fz = 4
    if core == "slab":
        m.box("core", -6, y, -4, 12, 18, 8, body, marks={"north": [(1, 1, 10, 1, accent), (1, 16, 10, 1, accent)]})
        m.box("core", -7, y + 17, -5, 14, 2, 10, accent)
    elif core == "disc":  # a great stone wheel with a face, on a mound of cloth
        st = body
        m.box("core", -9, y + 8, -1.5, 18, 12, 3, st)
        m.box("core", -6, y + 5, -1.6, 12, 18, 3.2, st)
        m.box("core", -8, y + 6.5, -1.4, 16, 15, 2.8, shade(st, 0.9), marks={"north": [(3, 2.5, 10, 10, accent), (4.5, 4, 7, 7, shade(st, 0.6))]})
        skirt = c.get("skirt", (150, 24, 24))
        for i, (w2, h2) in enumerate(((8, 5), (12, 4), (16, 4), (20, 3))):
            m.box("root", -w2 / 2, y + 8 - sum(hh for _, hh in ((8, 5), (12, 4), (16, 4), (20, 3))[:i + 1]), -w2 / 2.4, w2, h2, w2 / 1.2, shade(skirt, 1.0 - 0.1 * i))
        fz = 1.7
    elif core == "frame":
        m.box("core", -8, y, -1, 16, 20, 2, c.get("picture", (60, 70, 100)), marks={"north": [(3, 4, 10, 8, c.get("sky", (120, 140, 180))), (5, 12, 6, 6, c.get("figure", BLACK))]})
        for (x, yy, w2, h2) in ((-9, y - 1, 18, 2), (-9, y + 19, 18, 2), (-9, y - 1, 2, 22), (7, y - 1, 2, 22)):
            m.box("core", x, yy, -1.6, w2, h2, 3.2, body)
        fz = 1.6
    elif core == "spiral":
        for i in range(6):
            s2 = 14 - i * 2
            m.box("core", -s2 / 2, y + i * 3, -s2 / 2, s2, 3, s2, body if i % 2 else accent)
        fz = 7
    elif core == "heart":
        m.box("core", -6, y + 3, -3, 12, 9, 6, body)
        m.box("core", -7, y + 9, -3, 6, 6, 6, body)
        m.box("core", 1, y + 9, -3, 6, 6, 6, body)
        m.box("core", -3, y, -3, 6, 3.5, 6, body)
        fz = 3
    elif core == "statue":
        m.box("core", -8, y, -6, 16, 5, 12, accent)
        m.box("core", -5.5, y + 5, -4, 11, 8, 8, body)
        m.box("core", -3.5, y + 13, -3.5, 7, 7, 7, c.get("head", body))
        m.box("core", -1.2, y + 20, -1.2, 2.4, 2, 2.4, accent)
        fz = 3.5
    elif core == "orb":
        m.box("core", -6, y + 2, -6, 12, 12, 12, body)
        m.box("core", -4.5, y + 1, -4.5, 9, 14, 9, body)
        fz = 6
    else:  # drone
        m.box("core", -5, y + 4, -5, 10, 6, 10, body, marks={"north": [(1, 4.4, 8, 0.8, accent)]})
        m.box("core", -2, y + 2, -2, 4, 2, 4, accent)
        m.box("core", -0.8, y + 0.4, 3, 1.6, 2, 4, BLACK)
        fz = 5
    if face:
        m.bone("face", "core", (0, y + 12, fz))
        fy = y + 14 if core == "statue" else y + 6
        fc = c.get("face", WHITE)
        if core == "drone":
            m.box("face", -2, y + 6, fz, 4, 2.6, 0.6, eye, plain=True)
        else:
            m.box("face", -3.5, fy, fz, 7, 6.5 if core != "statue" else 5, 0.7, fc,
                  marks={"north": eyes(eye if eye != GLOW else BLACK, 1.8, 1, 4.4, 1.6, 1.6) + [(2.2, 4.4 if core != "statue" else 3.6, 2.6, 0.8, c.get("mouth", BLACK))]})
    if rotors:
        for i, (x, z) in enumerate(((6, 6), (-6, 6), (6, -6), (-6, -6))):
            n = "rotor%d" % i
            m.box("core", x - 0.5, y + 8, z - 0.5, 1, 3, 1, accent)
            m.bone(n, "core", (x, y + 11.2, z))
            m.box(n, -4, 0, -0.6, 8, 0.4, 1.2, c.get("blade", (200, 200, 210)), rel=True)
    if hands:
        hc = c.get("hand", body)
        for side, sg in (("r", 1), ("l", -1)):
            n = "hand_" + side
            m.bone(n, "root", (sg * 12, y + 6, 2))
            m.box(n, -2.2, -3, -2.2, 4.4, 6, 4.4, hc, rel=True)
            m.box(n, -2.2, -5, -2.2, 4.4, 2, 1.2, shade(hc, 0.85), rel=True)
            m.box(n, -2.2, -5, 1, 4.4, 2, 1.2, shade(hc, 0.85), rel=True)
            m.box(n, -1.4, 3, -1.4, 2.8, 2.4, 2.8, accent, rel=True)
    m.parts = {"Left Arm": ["hand_l"], "Right Arm": ["hand_r"], "Face": ["face"], "Head": ["face"], "Body": []}
    spin = {("rotor%d" % i): [(0,), (10, (0, 180, 0)), (20, (0, 360, 0))] for i in range(4)} if rotors else {}

    def rot(n):
        return {k: [(0,), (n / 2, (0, 180 * (n // 10), 0)), (n, (0, 360 * (n // 10), 0))] for k in spin}
    hr = lambda keys: {"hand_r": keys} if hands else {}
    hl = lambda keys: {"hand_l": keys} if hands else {}
    m.clips = {
        "idle": C(40, True, root=W(40, 40, pamp=(0, 1.2, 0)), core=W(40, 40, amp=(0, 5, 2), phase=0.2), **hr(W(40, 40, pamp=(0, 1.2, 0), phase=0.3)),
                  **hl(W(40, 40, pamp=(0, 1.2, 0), phase=0.6)), **rot(40)),
        "walk": C(20, True, root=W(20, 20, base=(10, 0, 0), pamp=(0, 0.8, 0)), **hr(W(20, 20, pamp=(0, 0, 2))), **hl(W(20, 20, pamp=(0, 0, -2))), **rot(20)),
        "attack_blunt": C(16, False, core=[(0,), (5, (-10, 0, 0)), (8, (16, 0, 0)), (16,)],
                          **hr([(0,), (5, (-30, 0, 0), (-3, 12, 2)), (8, (20, 0, 0), (-5, -9, 8)), (11, (20, 0, 0), (-5, -9, 8)), (16,)]),
                          **hl([(0,), (5, (-30, 0, 0), (3, 12, 2)), (8, (20, 0, 0), (5, -9, 8)), (11, (20, 0, 0), (5, -9, 8)), (16,)]), **rot(16)),
        "attack_slash": C(14, False, core=[(0,), (4, (0, 24, 0)), (7, (0, -24, 0)), (14,)],
                          **hr([(0,), (4, (0, 0, 40), (4, 5, -2)), (7, (0, 0, -60), (-18, -2, 9)), (10, (0, 0, -60), (-20, -3, 7)), (14,)]), **rot(14)),
        "attack_right_arm": C(14, False, core=[(0,), (4, (0, 24, 0)), (7, (0, -24, 0)), (14,)],
                              **hr([(0,), (4, (-90, 0, 0), (-2, 2, -5)), (7, (-90, 0, 0), (-8, 0, 15)), (10, (-90, 0, 0), (-8, 0, 13)), (14,)]), **rot(14)),
        "attack_left_arm": C(14, False, core=[(0,), (4, (0, -24, 0)), (7, (0, 24, 0)), (14,)],
                             **hl([(0,), (4, (-90, 0, 0), (2, 2, -5)), (7, (-90, 0, 0), (8, 0, 15)), (10, (-90, 0, 0), (8, 0, 13)), (14,)]), **rot(14)),
        "attack_pierce": C(14, False, root=[(0,), (4, (-14, 0, 0), (0, 2, -3)), (7, (26, 0, 0), (0, -2, 9)), (14,)], **rot(14)),
        "guard": C(20, True, **hr(hold(20, (-80, 0, 0), (-7, 2, 6))), **hl(hold(20, (-80, 0, 0), (7, 2, 6))), root=hold(20, p=(0, -1, 0)), **rot(20)),
        "stagger": C(40, True, root=W(40, 40, base=(0, 0, 0), pbase=(0, -hover * 0.8, 0), pamp=(0, 0.3, 0)), core=W(40, 40, base=(14, 0, 22), amp=(2, 0, 0)),
                     **hr(hold(40, (0, 0, 40), (2, -hover * 0.3 - 4, 0))), **hl(hold(40, (0, 0, -40), (-2, -hover * 0.3 - 4, 0)))),
        "roar": C(30, False, core=[(0,), (8, (-10, 0, 0), (0, 2, 0), 1.2), (15, (-10, 180, 0), (0, 3, 0), 1.25), (22, (-10, 360, 0), (0, 2, 0), 1.2), (30, (0, 360, 0))],
                  **hr([(0,), (8, (0, 0, 30), (6, 6, 0)), (22, (0, 0, 30), (6, 6, 0)), (30,)]), **hl([(0,), (8, (0, 0, -30), (-6, 6, 0)), (22, (0, 0, -30), (-6, 6, 0)), (30,)]), **rot(30)),
        "death": C(24, False, root=[(0,), (10, (20, 0, 20), (0, -hover * 0.5, 0)), (20, (80, 0, 30), (0, -hover - 3, 0)), (24, (84, 0, 30), (0, -hover - 3.5, 0))],
                   **hr([(0,), (20, (0, 0, 90), (4, -4, 0), 0.5), (24, (0, 0, 90), (4, -4, 0), 0.01)]), **hl([(0,), (20, (0, 0, -90), (-4, -4, 0), 0.5), (24, (0, 0, -90), (-4, -4, 0), 0.01)])),
    }
    return m


# --- crabs -------------------------------------------------------------------------------------------------------

def crab(mid, c, scale=1.0, cannon=None, bayonet=None, spikes=None, crown=None, big_claw=1.0):
    m = Model(mid, scale=scale, height=14, anims="none")
    shell, limb = c["shell"], c.get("limb", shade(c["shell"], 0.8))
    m.bone("root", None, (0, 5, 0))
    m.bone("shell", "root", (0, 5, 0))
    m.box("shell", -8, 4, -6, 16, 6, 12, shell, marks={"north": [(3, 3.6, 10, 0.8, shade(shell, 0.55))]})
    m.box("shell", -6, 10, -4.5, 12, 1.6, 9, shade(shell, 1.1))
    m.box("shell", -7, 3, -5, 14, 1, 10, c.get("belly", (230, 210, 180)))
    for sx in (2.5, -2.5):
        m.box("shell", sx - 0.5, 10, 5, 1, 3, 1, limb)
        m.box("shell", sx - 1, 12.6, 4.6, 2, 2, 2, c.get("eye", BLACK))
    if spikes:
        for x, z in ((-5, -3), (0, -4), (5, -3), (-3, 1), (3, 1), (0, 3)):
            m.box("shell", x - 0.7, 11.6, z - 0.7, 1.4, 2.6, 1.4, spikes)
    if crown:
        m.box("shell", -3, 11.6, -2, 6, 1.2, 4, crown)
        for x in (-3, -0.6, 1.8):
            m.box("shell", x, 12.8, -2, 1.2, 1.6, 1.2, crown)
    if cannon:
        m.bone("cannon", "shell", (0, 11.5, -1), rest=(-25, 0, 0))
        m.box("cannon", -2, -1, -3, 4, 4, 5, shade(cannon, 0.8), rel=True)
        m.box("cannon", -1.4, -0.2, 2, 2.8, 2.8, 9, cannon, rel=True)
        m.box("cannon", -1.8, -0.6, 10, 3.6, 3.6, 1.4, shade(cannon, 0.7), rel=True)
    for side, sg in (("r", 1), ("l", -1)):
        k = big_claw if sg > 0 else 1.0
        n = "claw_" + side
        m.bone(n, "shell", (sg * 8, 7, 4), rest=(0, -sg * 40, 0))
        x0 = 0 if sg > 0 else -5
        m.box(n, x0, -1.4, -1.4, 5, 2.8, 2.8, limb, rel=True)
        m.bone(n + "_low", n, (sg * 13, 7, 4), rest=(0, -sg * 50, 0))
        x1 = 0 if sg > 0 else -6 * k
        m.box(n + "_low", x1, -2.4 * k, -1.6 * k, 6 * k, 3 * k, 3.2 * k, c.get("claw", shell), rel=True)
        m.bone(n + "_tip", n + "_low", (sg * (13 + 1.5 * k), 7 + 0.8 * k, 4))
        x2 = 0 if sg > 0 else -5 * k
        m.box(n + "_tip", x2, 0, -1.2 * k, 5 * k, 1.6 * k, 2.4 * k, c.get("claw", shell), rel=True)
        if bayonet and sg > 0:
            m.box(n + "_low", 5 * k, -0.4, -0.3, 8, 0.8, 0.6, bayonet, rel=True)
        for i, z in enumerate((2, -1.5, -5)):
            ln = "leg_%s%d" % (side, i + 1)
            m.bone(ln, "shell", (sg * 7.5, 5.5, z), rest=(0, 0, -sg * 10))
            x3 = 0 if sg > 0 else -5
            m.box(ln, x3, -0.8, -0.8, 5, 1.6, 1.6, limb, rel=True)
            m.box(ln, sg * 5 - 0.7 - (0 if sg > 0 else 0), -5.5, -0.7, 1.4, 5.4, 1.4, shade(limb, 0.85), rel=True)
    legs = ["leg_%s%d" % (s, i) for s in "rl" for i in (1, 2, 3)]
    sgn = lambda n: 1 if "_r" in n else -1
    m.parts = {"Right Arm": ["claw_r", "claw_r_low", "claw_r_tip"], "Left Arm": ["claw_l", "claw_l_low", "claw_l_tip"], "Back": ["cannon"] if cannon else [], "Leg": legs[:3]}
    snap = lambda s, sg: {"claw_%s" % s: [(0,), (4, (0, sg * 50, sg * 20)), (7, (0, -sg * 50, 0)), (10, (0, -sg * 44, 0)), (14,)],
                          "claw_%s_low" % s: [(0,), (4, (0, sg * 30, 0)), (7, (0, -sg * 10, 0)), (14,)],
                          "claw_%s_tip" % s: [(0,), (4, (0, 0, sg * 50)), (7, (0, 0, -sg * 5)), (14,)]}
    m.clips = {
        "idle": C(40, True, shell=W(40, 40, pamp=(0, 0.4, 0)), claw_r_tip=W(40, 20, base=(0, 0, 14), amp=(0, 0, 12)), claw_l_tip=W(40, 20, base=(0, 0, -14), amp=(0, 0, -12), phase=0.4),
                  claw_r=W(40, 40, amp=(0, 5, 4)), claw_l=W(40, 40, amp=(0, 5, -4), phase=0.5)),
        "walk": C(12, True, root=W(12, 12, amp=(0, 0, 3)), **{n: W(12, 12, amp=(0, 14, sgn(n) * 16), phase=(int(n[-1]) % 2) * 0.5) for n in legs}),
        "attack_slash": C(14, False, root=[(0,), (4, (0, 14, 0)), (7, (0, -18, 0), (0, 0, 4)), (14,)], **snap("r", 1)),
        "attack_right_arm": C(14, False, root=[(0,), (4, (0, 14, 0)), (7, (0, -18, 0), (0, 0, 4)), (14,)], **snap("r", 1)),
        "attack_left_arm": C(14, False, root=[(0,), (4, (0, -14, 0)), (7, (0, 18, 0), (0, 0, 4)), (14,)], **snap("l", -1)),
        "attack_blunt": C(16, False, root=[(0,), (5, (-16, 0, 0), (0, 3, -1)), (8, (12, 0, 0), (0, -1, 4)), (16,)],
                          claw_r=[(0,), (5, (0, 0, 70)), (8, (0, -20, -30)), (16,)], claw_l=[(0,), (5, (0, 0, -70)), (8, (0, 20, 30)), (16,)]),
        "attack_pierce": C(14, False, root=[(0,), (4, (-6, 0, 0), (0, 0, -2)), (6, (8, 0, 0), (0, 0, -5)), (14,)],
                           **({"cannon": [(0,), (4, (12, 0, 0)), (6, (-14, 0, 0), (0, 0, -3)), (14,)]} if cannon else snap("r", 1))),
        "guard": C(20, True, claw_r=hold(20, (0, -30, 30)), claw_l=hold(20, (0, 30, -30)), claw_r_low=hold(20, (0, -40, 0)), claw_l_low=hold(20, (0, 40, 0)), root=hold(20, p=(0, -1.5, 0))),
        "stagger": C(40, True, root=W(40, 40, base=(0, 0, 180), amp=(0, 0, 5), pbase=(0, 6, 0)), **{n: W(40, 10, base=(0, 0, sgn(n) * 30), amp=(0, 0, 20), phase=int(n[-1]) * 0.3) for n in legs},
                     claw_r=W(40, 20, amp=(0, 0, 20)), claw_l=W(40, 20, amp=(0, 0, -20))),
        "roar": C(30, False, root=[(0,), (8, (-18, 0, 0), (0, 2, 0)), (22, (-18, 0, 0), (0, 2, 0)), (30,)],
                  claw_r=[(0,), (8, (0, 30, 70)), (22, (0, 30, 70)), (30,)], claw_l=[(0,), (8, (0, -30, -70)), (22, (0, -30, -70)), (30,)],
                  claw_r_tip=[(0,), (8, (0, 0, 50)), (12, (0, 0, 0)), (16, (0, 0, 50)), (30,)], claw_l_tip=[(0,), (8, (0, 0, -50)), (12, (0, 0, 0)), (16, (0, 0, -50)), (30,)]),
        "death": C(24, False, root=[(0,), (12, (0, 0, 0), (0, 2, 0)), (20, (0, 0, 0), (0, -3.4, 0)), (24, (0, 0, 0), (0, -3.6, 0))],
                   **{n: [(0,), (20, (0, 0, sgn(n) * 50))] for n in legs}, claw_r=[(0,), (20, (0, 30, -30))], claw_l=[(0,), (20, (0, -30, 30))]),
    }
    return m


# --- bodies on the humanoid skeleton (they use the humanoid clips) ---------------------------------------------------

def skeleton(m, hip=13.0, neck=24.0, shx=5.5, sh=23.0, elbow=18.2, hand=12.6, legx=2.0, knee=6.6):
    m.anims = "humanoid"
    m.pscale = hip / 13.0
    m.bone("root", None, (0, hip, 0))
    m.bone("torso", "root", (0, hip, 0))
    m.bone("head", "torso", (0, neck, 0))
    for side, sg in (("r", 1), ("l", -1)):
        m.bone("arm_" + side, "torso", (sg * shx, sh, 0))
        m.bone("arm_%s_low" % side, "arm_" + side, (sg * shx, elbow, 0))
        m.bone("leg_" + side, "root", (sg * legx, hip, 0))
        m.bone("leg_%s_low" % side, "leg_" + side, (sg * legx, knee, 0))
    m.bone("weapon", "arm_r_low", (shx, hand, 0.4), rest=(90, 0, 0))
    m.parts = {"Head": ["head"], "Right Arm": ["arm_r", "arm_r_low", "weapon"], "Left Arm": ["arm_l", "arm_l_low"], "Leg": ["leg_l", "leg_l_low"]}


def bot(mid, c, scale=1.0, tool="saw", head="visor", pipes=True):
    """A machine on two legs with a tool for a right arm: saw, ball, claw, gun, drill. c: body, trim, light, tool."""
    m = Model(mid, scale=scale, height=30 if head != "screen" else 34)
    body, trim, light = c["body"], c.get("trim", shade(c["body"], 0.6)), c.get("light", (240, 70, 50))
    skeleton(m, hip=12, neck=22.5, shx=8, sh=21, elbow=16.5, hand=11, legx=3, knee=6)
    m.box("torso", -6, 12, -4.5, 12, 10.5, 9, body, marks={"north": [(1.5, 1.5, 4, 3, trim), (7, 1.5, 3.5, 1.2, light), (7, 3.4, 3.5, 1.2, c.get("light2", (240, 200, 60))), (1.5, 6.5, 9, 0.8, trim)]})
    m.box("root", -4.5, 10.5, -3.5, 9, 2.5, 7, trim)
    if pipes:
        m.mirror_box("torso", "torso", 2.5, 19, -6.4, 1.6, 6.5, 1.6, trim)
    if head == "screen":
        m.box("head", -6, 23.5, -1.5, 12, 9, 3, trim, marks={"north": [(1, 1, 10, 7, c.get("screen", (40, 60, 80))), (3, 3, 1.6, 1.6, light), (7.4, 3, 1.6, 1.6, light), (4, 6, 4, 0.8, light)]})
        m.box("head", -1, 22.5, -1, 2, 1.2, 2, trim)
    elif head == "dome":
        m.box("head", -3.5, 22.5, -3.5, 7, 4, 7, trim, marks={"north": [(1, 1.2, 5, 1.4, light)]})
        m.box("head", -2.5, 26.5, -2.5, 5, 1.6, 5, body)
    else:
        m.box("head", -3.5, 22.5, -3, 7, 5, 6.5, body, marks={"north": [(0.8, 1.6, 5.4, 1.6, light)]})
        m.box("head", 2, 27.5, -1, 0.6, 3.5, 0.6, trim)
    for side, sg in (("r", 1), ("l", -1)):
        x0 = sg * 8 - 1.75
        m.box("arm_" + side, x0 - 0.4, 19.5, -2.2, 4.3, 3, 4.4, trim)
        m.box("arm_" + side, x0, 16.5, -1.75, 3.5, 4.5, 3.5, body)
        m.box("arm_%s_low" % side, x0 + 0.2, 11.5, -1.55, 3.1, 5, 3.1, shade(body, 0.9))
        lx = sg * 3 - 2
        m.box("leg_" + side, lx, 6, -2, 4, 6, 4, body)
        m.box("leg_%s_low" % side, lx + 0.3, 1.5, -1.7, 3.4, 4.5, 3.4, trim)
        m.box("leg_%s_low" % side, lx - 0.3, 0, -2.3, 4.6, 1.8, 5.8, shade(body, 0.8))
    m.box("arm_l_low", -8 - 1.9, 9.6, -1.2, 1.2, 2.4, 2.4, trim)
    m.box("arm_l_low", -8 + 0.7, 9.6, -1.2, 1.2, 2.4, 2.4, trim)
    tc = c.get("tool", (200, 204, 212))
    wb = lambda *a, **k: m.box("weapon", *a, rel=True, **k)
    if tool == "saw":
        wb(-0.5, -1, -4.5, 1, 9, 9, tc, marks={f: [(3.4, 3.4, 2.2, 2.2, trim)] for f in ("east", "west")})
        for i in range(4):
            wb(-0.4, -2 + i * 2.6, -5.4, 0.8, 1.2, 1.2, tc)
            wb(-0.4, -2 + i * 2.6, 4.2, 0.8, 1.2, 1.2, tc)
    elif tool == "ball":
        wb(-0.4, -1, -0.4, 0.8, 7, 0.8, trim)
        wb(-3.2, 6, -3.2, 6.4, 6.4, 6.4, tc)
        wb(-3.8, 8.4, -0.6, 7.6, 1.2, 1.2, trim)
    elif tool == "gun":
        wb(-1.5, -2, -1.5, 3, 4, 3, trim)
        wb(-0.8, 2, -0.8, 1.6, 9, 1.6, tc)
    elif tool == "drill":
        wb(-2, -1, -2, 4, 3, 4, trim)
        wb(-1.4, 2, -1.4, 2.8, 4, 2.8, tc)
        wb(-0.8, 6, -0.8, 1.6, 4, 1.6, tc)
    else:
        wb(-2, 0, -1, 1.2, 5, 2, tc)
        wb(0.8, 0, -1, 1.2, 5, 2, tc)
    return m


def chicken(mid, c, scale=1.0, comb=True, crown=None, apron=None, bucket=None):
    """A chicken on two legs; its wings are its arms. c: body, wing, comb, beak, leg."""
    m = Model(mid, scale=scale, height=27 if crown else 25)
    body, wing = c["body"], c.get("wing", shade(c["body"], 0.88))
    skeleton(m, hip=8, neck=17, shx=5.3, sh=15.5, elbow=11.5, hand=8.5, legx=2.4, knee=4)
    m.box("torso", -4.6, 8, -5, 9.2, 9, 10, body, marks={"north": [(1.6, 3, 6, 5, c.get("chest", shade(body, 1.1)))]})
    m.box("torso", -3, 12, -8, 6, 7, 3, c.get("tail", wing))
    m.box("torso", -2, 16, -9.5, 4, 5, 2, c.get("tail", wing))
    if apron:
        m.box("torso", -4.9, 8, 4.6, 9.8, 6, 0.6, apron)
    if bucket:
        m.bone("bucket", "torso", (0, 13, -5))
        m.box("bucket", -3, 10, -10, 6, 7, 5, bucket, marks={"south": [(0, 1.5, 6, 1.5, RED), (0, 4.5, 6, 1.5, RED)]})
        m.parts["Back"] = m.parts["Chicken Bucket"] = ["bucket"]
    m.box("head", -3, 17, -2.5, 6, 6, 6, c.get("head", body), marks={"north": eyes(c.get("eye", BLACK), 1.6, 0.6, 3.8, 1.4, 1.6)})
    m.box("head", -1, 18.6, 3.5, 2, 1.6, 2.6, c.get("beak", (240, 190, 60)))
    m.box("head", -0.8, 16.6, 3.3, 1.6, 2, 1, c.get("comb", RED))
    if comb:
        m.box("head", -0.7, 23, -1.5, 1.4, 2, 4.5, c.get("comb", RED))
        m.box("head", -0.7, 25, -0.5, 1.4, 1, 2, c.get("comb", RED))
    if crown:
        m.box("head", -2.6, 23, -2.4, 5.2, 1.2, 5.2, crown)
        for x in (-2.6, -0.6, 1.4):
            m.box("head", x, 24.2, 1.6, 1.2, 1.8, 1.2, crown)
    for side, sg in (("r", 1), ("l", -1)):
        x0 = 4.6 if sg > 0 else -6
        m.box("arm_" + side, x0, 11.5, -3.5, 1.4, 5, 7, wing)
        m.box("arm_%s_low" % side, x0 + 0.1, 8.2, -3, 1.2, 3.4, 5.5, shade(wing, 0.9))
        lx = sg * 2.4 - 0.7
        m.box("leg_" + side, lx, 4, -0.7, 1.4, 4.4, 1.4, c.get("leg", (230, 170, 50)))
        m.box("leg_%s_low" % side, lx, 0.6, -0.7, 1.4, 3.6, 1.4, c.get("leg", (230, 170, 50)))
        m.box("leg_%s_low" % side, lx - 0.8, 0, -1, 3, 0.8, 4, c.get("leg", (230, 170, 50)))
    return m


def roundling(mid, c, scale=1.0, shape="jar", weapon_kind=None):
    """A round body on short limbs with its face on the body: a jar, a fruit, a blob. c: body, limb, eye, lid, mark."""
    import md_models
    m = Model(mid, scale=scale, height=21)
    body, limb, eye = c["body"], c.get("limb", shade(c["body"], 0.7)), c.get("eye", BLACK)
    skeleton(m, hip=5, neck=17, shx=7.2, sh=13, elbow=10, hand=6.6, legx=3, knee=2.6)
    face = eyes(eye, 4, 2.6, 7.4, 2, 2.2) + [(4, 8, 4, 1, c.get("mouth", BLACK))] + [(x, y, 1.6, 3.4, c["mark"]) for x, y in ((1, 1), (9.4, 6)) if c.get("mark")]
    m.box("torso", -6, 5, -6, 12, 12, 12, body, marks={"north": face})
    m.box("torso", -5, 4, -5, 10, 1, 10, shade(body, 0.8))
    if shape == "jar":
        m.box("head", -4, 17, -4, 8, 1.6, 8, shade(body, 0.8))
        m.box("head", -5, 18.6, -5, 10, 1.4, 10, c.get("lid", shade(body, 1.15)))
        m.box("head", -1, 20, -1, 2, 1.2, 2, c.get("lid", shade(body, 1.15)))
    elif shape == "fruit":
        m.box("head", -4.5, 17, -4.5, 9, 1, 9, shade(body, 1.08))
        m.box("head", -2.5, 18, -2.5, 5, 0.8, 5, c.get("leaf", (90, 130, 60)))
        m.box("head", -0.5, 18.8, -0.5, 1, 2, 1, (90, 70, 40))
    else:
        m.box("head", -4, 17, -4, 8, 2, 8, shade(body, 0.92))
    if c.get("nails"):
        for x, z in ((-3, 2), (2, -2), (0, 3)):
            m.box("head", x, 19, z, 0.8, 3.5, 0.8, c["nails"])
    for side, sg in (("r", 1), ("l", -1)):
        x0 = sg * 7.2 - 0.9
        m.box("arm_" + side, x0, 10, -0.9, 1.8, 3.4, 1.8, limb)
        m.box("arm_%s_low" % side, x0 + 0.1, 6.4, -0.8, 1.6, 3.6, 1.6, limb)
        lx = sg * 3 - 1.1
        m.box("leg_" + side, lx, 2.6, -1.1, 2.2, 2.8, 2.2, limb)
        m.box("leg_%s_low" % side, lx, 0, -1.1, 2.2, 2.6, 3.4, shade(limb, 0.8))
    if weapon_kind:
        md_models.weapon(m, weapon_kind)
    return m


def mermaid(mid, spec, scale=1.0):
    """A person above, a fish below: the humanoid with its legs made one tail."""
    import md_models
    m = md_models.humanoid(mid, dict(spec, pants=spec.get("fish", (120, 170, 190))), scale=scale)
    fish, fin = spec.get("fish", (120, 170, 190)), spec.get("fin", (200, 230, 240))
    for b in ("leg_r", "leg_r_low", "leg_l", "leg_l_low"):
        m.bones[b].cubes = []
    band = {f: [(0, 2, 99, 0.7, shade(fish, 0.7)), (0, 4.4, 99, 0.7, shade(fish, 0.7))] for f in ("north", "south", "east", "west")}
    m.box("leg_r", -3.4, 6.6, -2.4, 6.8, 6.4, 4.8, fish, marks=band)
    m.box("leg_r_low", -2.6, 1.6, -2, 5.2, 5.2, 4, shade(fish, 0.9), marks=band)
    m.box("leg_r_low", -5.5, -0.6, -1, 11, 2.4, 5.5, fin)
    m.parts["Tail"] = ["leg_r", "leg_r_low"]
    return m
