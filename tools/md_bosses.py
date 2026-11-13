"""Bosses modelled one by one, in finer boxes (see md_models.py): sculpted hair,
drawn faces, layered clothes and their own weapons, on the humanoid skeleton so
the humanoid clips move them. Shapes and colours follow each boss's look; the
models are our own."""
import md_models as M

sh = M.shade
GOLD, WHITE, BLACK, STEEL = (214, 180, 80), (244, 244, 244), (20, 18, 22), (196, 200, 210)
SIDES = ("north", "south", "east", "west")


def eye_face(iris, lash=(30, 24, 28), white=WHITE, mouth=(150, 90, 90), narrow=False, brow=None):
    """A drawn face on the 8x8 front of the head: lashes, whites, irises with a glint, a mouth."""
    out = []
    h = 1.0 if narrow else 1.7
    for x in (0.9, 4.9):
        out += [(x, 3.2, 2.2, 0.45, lash), (x, 3.65, 2.2, h, white), (x + 0.6, 3.65, 1.1, h, iris), (x + 0.95, 3.9, 0.45, h - 0.5, sh(iris, 0.35)),
                (x + 0.6, 3.65, 0.4, 0.4, WHITE)]
        if brow:
            out.append((x, 2.5, 2.2, 0.4, brow))
    out.append((3.4, 6.3, 1.2, 0.4, mouth))
    return out


def hair(m, col, back=26.0, side=27.0, vol=0.6, front=1.5, fringe=()):
    """Hair in chunks: a cap, a back down to y=back, sides down to y=side, and fringe pieces (x, width, length)."""
    m.box("head", -4 - vol, 31.5, -4 - vol, 8 + 2 * vol, 0.9 + vol, 8 + 2 * vol, col)
    m.box("head", -4 - vol, back, -4.2 - vol, 8 + 2 * vol, 32.2 - back, 0.9 + vol, sh(col, 0.92))
    m.mirror_box("head", "head", 3.8, side, -4 - vol, 0.4 + vol, 32 - side, 4 + vol + front, sh(col, 0.96))
    for i, (x, w, ln) in enumerate(fringe):
        m.box("head", x, 32.2 - ln, 3.8, w, ln, 0.8, sh(col, 1.0 if i % 2 else 0.94))


def base(mid, spec, scale, head=1.0):
    s = {"hair_style": "bald", "weapon": None}
    s.update(spec)
    m = M.humanoid(mid, s, scale=scale)
    m.bones["head"].scale = head
    return m


def wb(m):
    return lambda *a, **k: m.box("weapon", *a, rel=True, **k)


# --- La Manchaland ----------------------------------------------------------------------------------------

def don_quixote():
    coat, red, hairc = (62, 22, 28), (134, 30, 36), (208, 200, 172)
    m = base("b_don_quixote", {"skin": (242, 228, 222), "top": (38, 24, 28), "pants": (32, 22, 26), "shoes": (26, 22, 26), "coat": coat, "coat_len": 8.5,
                               "face": eye_face((190, 40, 50), mouth=(170, 110, 110)), "weapon": "lance", "weapon_tint": (176, 28, 38)}, 1.25)
    hair(m, hairc, back=18.5, side=20.5, vol=1.1, front=2.2, fringe=((-4.6, 2.8, 3.4), (-1.6, 2.6, 2.4), (1.2, 3.4, 3.0)))
    m.mirror_box("head", "head", 4.4, 18, -2.5, 1.8, 4.5, 4.5, sh(hairc, 0.9))
    m.mirror_box("head", "head", 3.2, 16.5, -5.6, 2.6, 4, 1.6, sh(hairc, 0.86))
    # A black and gold vest, a white cravat, red lapels.
    m.box("torso", -2.7, 13.6, 2.0, 5.4, 8.6, 0.45, (30, 22, 26),
          marks={"north": [(0.4, 1, 0.5, 6.6, GOLD), (4.5, 1, 0.5, 6.6, GOLD), (1.3, 6.8, 0.8, 0.8, GOLD), (2.3, 5.8, 0.8, 0.8, GOLD), (3.3, 6.8, 0.8, 0.8, GOLD), (2.3, 3, 0.8, 0.8, GOLD)]})
    m.box("torso", -1.5, 21.4, 2.1, 3, 2.7, 0.8, WHITE)
    m.mirror_box("torso", "torso", 2.7, 15.5, 2.0, 1.5, 8.6, 0.55, red)
    # Wide sleeves with red cuffs; a gold epaulette on the left shoulder.
    m.mirror_box("arm_r", "arm_l", 3.7, 18, -1.9, 3.6, 5.6, 3.8, coat)
    m.mirror_box("arm_r_low", "arm_l_low", 3.2, 13, -2.4, 4.6, 3.6, 4.8, red, marks={f: [(0.6, 0.6, 0.8, 0.8, GOLD), (3, 0.6, 0.8, 0.8, GOLD)] for f in SIDES})
    m.box("arm_l", -8.4, 23.3, -2.5, 4.8, 1.2, 5, GOLD)
    m.box("arm_l", -8.6, 21.2, -2.6, 0.7, 2.2, 5.2, GOLD, marks={"west": [(i * 0.9, 0, 0.4, 99, sh(GOLD, 0.6)) for i in range(6)]})
    m.box("arm_l", -8.4, 21.2, 2.2, 4.6, 2.2, 0.5, GOLD, marks={"north": [(i * 0.9, 0, 0.4, 99, sh(GOLD, 0.6)) for i in range(5)]})
    # The coat's front flaps and red hem; boots with red tops.
    m.mirror_box("coat", "coat", 1.8, 4.5, 1.7, 2.7, 8.5, 0.7, coat, marks={"north": [(0.5, 1 + i * 2, 0.7, 0.7, GOLD) for i in range(4)]})
    for f, (x, z, w, d) in (("b", (-4.6, -2.9, 9.2, 0.9)), ("r", (3.8, -2.6, 0.9, 5.2)), ("l", (-4.7, -2.6, 0.9, 5.2))):
        m.box("coat", x, 4.3, z, w, 1.5, d, red)
    m.mirror_box("leg_r_low", "leg_l_low", 0.0, 4.4, -2.0, 4.0, 1.3, 4.1, red)
    return m


def sancho():
    blond, red, coat = (246, 208, 84), (196, 30, 36), (30, 26, 30)
    skin = (246, 232, 226)
    m = base("b_sancho", {"skin": skin, "top": (46, 38, 42), "inner": (124, 58, 48), "coat": coat, "coat_len": 10.5, "pants": (26, 24, 28), "shoes": skin,
                          "cape": red, "cape_wide": 3, "cape_fur": (214, 38, 42), "face": eye_face((200, 30, 40), mouth=(160, 100, 100)),
                          "weapon": "lance", "weapon_tint": (190, 30, 40)}, 1.2)
    hair(m, blond, back=22.5, side=25, vol=0.8, front=2.0, fringe=((-4.4, 2.2, 3.0), (-2.0, 2.6, 2.2), (0.8, 2.2, 3.2), (3.0, 1.6, 2.4)))
    # Wild locks, swept up and to one side.
    for i, (x, y, z, w, h, d) in enumerate(((-7.4, 30, -2.5, 3, 1.8, 3.4), (-9, 31.4, -1.5, 2.2, 1.4, 2.2), (-4.2, 32.4, -3.6, 3.4, 1.4, 3), (0.4, 32.6, -1.5, 3.6, 1.2, 3.6),
                                            (4.4, 31.2, -3.4, 2.6, 1.8, 3), (6.4, 32.4, -2.4, 1.8, 1.4, 1.8), (5, 27.5, -2.5, 1.8, 3, 3.4), (-6.4, 26.5, -1.5, 1.8, 3.4, 3),
                                            (6.4, 25.4, -3.2, 1.4, 2.2, 2), (-2, 33.6, -2.8, 2.2, 1, 2))):
        m.box("head", x, y, z, w, h, d, sh(blond, 1.04 if i % 2 else 0.9))
    # A corset of black belts; the coat's red lining.
    m.box("torso", -4.15, 13.4, -2.15, 8.3, 3.8, 4.3, (22, 20, 24), marks={f: [(0, 1, 99, 0.5, (70, 66, 70)), (0, 2.4, 99, 0.5, (70, 66, 70))] for f in SIDES})
    m.box("torso", -0.5, 17.4, 2.05, 1, 4.6, 0.5, (140, 60, 50))
    m.mirror_box("coat", "coat", 2.4, 2.5, 1.7, 2.0, 10.5, 0.7, coat, marks={"north": [(0, 0, 0.7, 99, (170, 28, 34))]})
    # The cloak of red feathers: drapes over the arms, ragged tips below.
    m.mirror_box("arm_r_low", "arm_l_low", 6.6, 6.5, -3, 1.2, 10, 6, red)
    for i in range(7):
        m.box("cape", -7 + i * 2.1, 3.2 - (i % 2) * 2.2 - (i % 3) * 0.7, -4.6, 2.0, 5, 1.2, sh(red, 1.08 if i % 2 else 0.86))
    for i in range(5):
        m.box("cape", -6 + i * 2.6, 9 + (i % 2) * 2, -4.9, 2.2, 3.4, 0.8, sh(red, 0.8))
    return m


def dulcinea():
    cream, dark, purple, lace = (240, 230, 198), (34, 26, 46), (62, 36, 100), (164, 94, 234)
    m = base("b_dulcinea", {"skin": (246, 236, 236), "top": dark, "sleeve": dark, "skirt": purple, "coat_len": 12, "skirt_wide": 5, "frill": lace, "pants": dark,
                            "shoes": (30, 22, 40), "parasol": (92, 52, 154), "face": eye_face((150, 80, 220), lash=(60, 40, 70), mouth=(170, 110, 130))}, 1.3, head=0.95)
    hair(m, cream, back=6, side=22, vol=0.6, front=1.4, fringe=((-4.4, 2.6, 2.6), (-1.8, 3.6, 1.8), (1.8, 2.8, 2.8)))
    m.mirror_box("head", "head", 4.2, 7, -1, 1.2, 16, 2.4, sh(cream, 0.92))
    m.mirror_box("head", "head", 4.8, 4, -4, 1.4, 12, 1.6, sh(cream, 0.86))
    m.box("head", -2.4, 31.6, 3.6, 4.8, 1.6, 1.2, (40, 28, 60))
    m.mirror_box("head", "head", 1.6, 32.4, 3.6, 2.4, 1.4, 1, lace)
    # A laced bodice, puffed sleeves, a white underskirt showing at the front.
    m.box("torso", -3, 13.4, 2.0, 6, 9, 0.45, (240, 234, 244), marks={"north": [(1.2, i * 1.5 + 0.6, 3.6, 0.4, lace) for i in range(5)] + [(2.7, 0, 0.6, 99, dark)]})
    m.box("torso", -4.2, 21.6, -2.2, 8.4, 2.6, 4.4, dark, marks={f: [(0, 2, 99, 0.6, lace)] for f in SIDES})
    m.mirror_box("arm_r", "arm_l", 3.6, 19.5, -2.1, 3.8, 4.3, 4.2, purple, marks={f: [(0, 3.6, 99, 0.7, lace)] for f in SIDES})
    m.mirror_box("arm_r_low", "arm_l_low", 3.5, 12.8, -2, 4, 2.4, 4, (240, 234, 244))
    m.box("coat", -3.2, 1, 6.6, 6.4, 8, 0.7, (240, 234, 244), marks={"north": [(0, i * 2.2 + 1.6, 99, 0.5, (200, 190, 220)) for i in range(3)]})
    # The parasol's frilled rim and hanging charms.
    for x, y in ((-12.5, 22), (11, 22), (-8.5, 36.2), (7, 36.2), (-1, 37.6), (-12.5, 29), (11, 29)):
        m.box("parasol", x, y, -6.8, 1.6, 1.6, 0.9, lace)
    for x, ln in ((-11.6, 9), (11.2, 11), (-6.5, 6)):
        m.box("parasol", x, 22 - ln, -6.5, 0.3, ln, 0.3, (30, 22, 40))
        m.box("parasol", x - 0.6, 20.6 - ln, -6.9, 1.5, 1.5, 1.1, lace)
    return m


# --- The Middle, the Hongyuan, the Pequod -------------------------------------------------------------------

def ricardo():
    skin, pink, fur, purple = (208, 162, 126), (226, 52, 122), (22, 20, 26), (118, 52, 160)
    m = base("b_ricardo", {"skin": skin, "bulk": 2.0, "top": skin, "sleeve": skin, "pants": (238, 238, 238), "shoes": (110, 50, 150), "gauntlets": GOLD,
                           "cape": fur, "cape_wide": 3.2, "cape_fur": fur,
                           "face": [(0.9, 3.0, 2.4, 0.45, (60, 30, 40)), (4.7, 3.0, 2.4, 0.45, (60, 30, 40)), (3.0, 6.2, 2.0, 0.5, (150, 90, 80)), (3.2, 6.7, 1.6, 0.3, WHITE)]}, 1.45, head=0.92)
    # Pink hair standing in spikes; round glasses that shine.
    m.box("head", -4.4, 31.4, -4.4, 8.8, 1.2, 8.8, pink)
    m.box("head", -4.4, 27, -4.6, 8.8, 5, 1, sh(pink, 0.85))
    m.mirror_box("head", "head", 3.9, 28.5, -4.4, 0.7, 3.5, 5, sh(pink, 0.9))
    for i, (x, z, h) in enumerate(((-3.8, -3.6, 3.6), (-1.2, -4, 4.6), (1.6, -3.6, 4.0), (-3.2, -0.8, 3.0), (-0.4, -1.2, 4.2), (2.4, -0.6, 3.2), (-2.2, 1.8, 2.2), (0.8, 1.8, 2.6), (3.0, 2.0, 1.8))):
        m.box("head", x, 32.4, z, 1.8, h, 1.8, sh(pink, 1.06 if i % 2 else 0.9))
    for x in (-3.5, 0.7):
        m.box("head", x, 26.5, 3.9, 2.8, 2.4, 0.6, (60, 50, 60))
        m.box("head", x + 0.4, 26.9, 4.1, 2.0, 1.6, 0.5, (250, 250, 255), plain=True)
    m.box("head", -0.8, 27.5, 3.9, 1.6, 0.5, 0.5, (60, 50, 60))
    tw = 12.0
    # A tattooed chest, a gold belt, a purple book at the hip.
    m.box("torso", -4.6, 15, 3.02, 9.2, 8.4, 0.3, skin, marks={"north": [(1, 1, 3, 0.6, (70, 60, 76)), (5.2, 1, 3, 0.6, (70, 60, 76)), (2, 2.4, 5.2, 0.6, (70, 60, 76)), (3.2, 3.6, 2.8, 2.4, (70, 60, 76)),
                                                                        (1.2, 4.4, 1.4, 2, (70, 60, 76)), (6.6, 4.4, 1.4, 2, (70, 60, 76)), (4.2, 0, 0.8, 8.4, sh(skin, 0.8))]})
    m.box("root", -tw / 2 - 0.3, 11.6, -3.3, tw + 0.6, 2, 6.6, GOLD, marks={"north": [(i * 1.6 + 0.4, 0.5, 0.8, 1, sh(GOLD, 0.55)) for i in range(7)]})
    m.box("root", -7.6, 6.4, 1.6, 4, 5.2, 1.6, purple, marks={"north": [(1.2, 1.6, 1.6, 1.6, GOLD), (0.3, 0.3, 3.4, 0.4, GOLD), (0.3, 4.5, 3.4, 0.4, GOLD)]})
    m.box("root", -6, 10.6, 2, 0.6, 1.6, 0.6, GOLD)
    # The fur coat: a mane of spikes round the shoulders and down the edges.
    for i, (x, y, z, w, h, d) in enumerate(((6.5, 23, -4, 4, 3, 2), (-10.5, 23, -4, 4, 3, 2), (4, 25, -5, 3, 3, 2), (-7, 25, -5, 3, 3, 2), (-2, 25.5, -5.4, 4, 3.2, 2),
                                            (9.5, 18, -4.6, 2.4, 4, 1.6), (-11.9, 18, -4.6, 2.4, 4, 1.6), (9.8, 11, -4.8, 2.2, 4, 1.4), (-12, 11, -4.8, 2.2, 4, 1.4),
                                            (8.6, 5, -5, 2.4, 4, 1.4), (-11, 5, -5, 2.4, 4, 1.4))):
        m.box("cape" if y < 22 else "torso", x, y, z, w, h, d, sh(fur, 1.3 if i % 2 else 1.0))
    m.mirror_box("cape", "cape", 6.2, 6, -3.9, 2.6, 15, 0.5, purple)
    # Chains in links round the forearms and shins.
    for bone, x, y, z in (("arm_r_low", 6.2, 14, 2.6), ("arm_l_low", -8.2, 14, 2.6), ("arm_r_low", 9.6, 15.4, -0.8), ("arm_l_low", -10.9, 15.4, -0.8),
                          ("leg_r_low", 1, 3, 2.6), ("leg_l_low", -3, 3, 2.6)):
        m.box(bone, x, y, z, 2, 1.3, 1.1 if z > 0 else 2, sh(GOLD, 1.1))
    return m


def lei_heng():
    red, dark, coat = (130, 26, 40), (40, 30, 34), (104, 20, 34)
    skin = (216, 172, 142)
    m = base("b_lei_heng", {"skin": skin, "bulk": 0.7, "top": red, "inner": dark, "lapels": 1, "pants": (144, 28, 42), "shoes": (30, 24, 26), "accent": GOLD,
                            "cape": coat, "cape_wide": 2.6, "cape_fur": coat,
                            "face": eye_face((60, 40, 30), brow=(26, 24, 28), mouth=(120, 60, 50), narrow=True) + [(1.4, 6.9, 5.2, 1.1, sh(skin, 0.72)), (2.8, 5.6, 2.4, 0.4, (60, 44, 40))]}, 1.45, head=0.84)
    black = (28, 26, 30)
    m.box("head", -4.3, 31.2, -4.3, 8.6, 1.5, 8.6, black)
    m.box("head", -4.3, 26, -4.5, 8.6, 6, 0.9, sh(black, 0.9))
    m.mirror_box("head", "head", 3.8, 28.6, -4.3, 0.7, 3.4, 5.5, black)
    m.box("head", -3.6, 32.6, -3.5, 7.2, 0.8, 6.4, sh(black, 1.3))
    m.box("head", 0.8, 32.8, -3, 1, 0.7, 7.2, (214, 184, 90))
    m.box("head", 1.6, 30, 3.8, 0.9, 2.4, 0.7, (214, 184, 90))
    m.box("head", -0.8, 20.5, -5.8, 1.6, 6, 1.4, black)
    # A cigar, glowing.
    m.box("head", 1.4, 25.3, 4, 0.7, 0.7, 2.6, (120, 80, 50))
    m.box("head", 1.4, 25.3, 6.6, 0.7, 0.7, 0.6, (255, 140, 50), plain=True)
    # The jacket's gold: buttons, a breast emblem, piping; the draped coat's gold edge.
    tw = 9.4
    m.box("torso", -tw / 2, 13.2, 2.36, tw, 6, 0.3, red, marks={"north": [(4.2, 0.6, 0.9, 0.9, GOLD), (4.2, 2.6, 0.9, 0.9, GOLD), (6.6, 4.2, 1.6, 1.4, GOLD), (0, 0, 0.5, 99, GOLD), (tw - 0.5, 0, 0.5, 99, GOLD)]})
    m.box("torso", -1.6, 19.6, 2.4, 3.2, 3.6, 0.3, dark, marks={"north": [(0.6, 0.5 + i, 2, 0.3, GOLD) for i in range(3)]})
    m.mirror_box("cape", "cape", 6.6, 5.5, -3.9, 1.2, 17, 0.6, GOLD)
    m.mirror_box("arm_r", "arm_l", 3.9, 21.6, -2.6, 5.2, 2.6, 5.2, coat, marks={f: [(0, 2, 99, 0.5, GOLD)] for f in SIDES})
    m.mirror_box("arm_r_low", "arm_l_low", 4.5, 13.2, -2, 3.8, 1.2, 4, sh(red, 0.8), marks={f: [(0, 0, 99, 0.4, GOLD)] for f in SIDES})
    m.mirror_box("leg_r_low", "leg_l_low", 0, 0, -2.2, 4.2, 0.6, 5.4, GOLD)
    # A great blade in its scabbard.
    w = wb(m)
    w(-0.6, -5, -0.6, 1.2, 5.5, 1.2, (30, 24, 26), marks={f: [(0, 1 + i * 1.2, 99, 0.4, GOLD) for i in range(4)] for f in SIDES})
    w(-1.8, 0.5, -0.9, 3.6, 1.2, 1.8, GOLD)
    w(-1.2, 1.7, -0.55, 2.4, 19, 1.1, (74, 22, 30), marks={f: [(0, 1, 99, 0.7, GOLD), (0, 9, 99, 0.7, GOLD), (0, 17.6, 99, 1.2, GOLD)] for f in SIDES})
    w(-0.8, 20.7, -0.45, 1.6, 1.6, 0.9, GOLD)
    return m


def ahab():
    green, fur, brown = (58, 94, 72), (228, 226, 216), (112, 94, 86)
    m = base("b_ahab", {"skin": (228, 202, 182), "top": (232, 230, 222), "coat": green, "coat_len": 8, "sleeve": green, "pants": (62, 98, 78), "shoes": (30, 26, 24),
                        "accent": (30, 26, 24), "cape": fur, "cape_wide": 3, "cape_fur": fur, "lapels": 1,
                        "face": eye_face((90, 110, 120), brow=(90, 76, 70), mouth=(150, 90, 80), narrow=True)}, 1.4, head=0.86)
    hair(m, brown, back=14.5, side=17.5, vol=0.9, front=1.8, fringe=((-4.6, 2.0, 3.6), (2.8, 1.8, 3.2)))
    m.mirror_box("head", "head", 4.6, 14, -3, 1.6, 6, 4, sh(brown, 0.86))
    m.mirror_box("head", "head", 3.4, 12.5, -5.8, 2.4, 5, 1.4, sh(brown, 0.8))
    # A military cap with a gold badge; a pipe.
    cap = (66, 98, 62)
    m.box("head", -4.5, 31, -4.5, 9, 1.7, 9, cap, marks={f: [(0, 1.1, 99, 0.5, (30, 40, 30))] for f in SIDES})
    m.box("head", -5.1, 32.7, -5.1, 10.2, 1.5, 10.6, sh(cap, 1.15))
    m.box("head", -3.7, 30.5, 4.3, 7.4, 0.6, 2.6, (30, 30, 28))
    m.box("head", -1, 31.2, 4.5, 2, 1.4, 0.4, GOLD)
    m.box("head", -2.6, 25.3, 4, 0.6, 0.6, 2.2, (90, 60, 40))
    m.box("head", -2.9, 25.3, 6, 1.2, 1.6, 1.2, (70, 46, 30))
    # Fur down the coat's edges; a jacket with pockets and a belt.
    for i in range(6):
        m.box("cape", 6.4 - (i % 2) * 0.6, 4 + i * 3, -4.6, 2.2, 3.2, 1.6, sh(fur, 0.9 if i % 2 else 1.0))
        m.box("cape", -8.6 + (i % 2) * 0.6, 4 + i * 3, -4.6, 2.2, 3.2, 1.6, sh(fur, 1.0 if i % 2 else 0.9))
    m.box("torso", -4.05, 13.2, 2.0, 2.6, 5, 0.4, green, marks={"north": [(0.4, 1, 1.8, 1.6, sh(green, 0.7))]})
    m.box("torso", 1.45, 13.2, 2.0, 2.6, 5, 0.4, green, marks={"north": [(0.4, 1, 1.8, 1.6, sh(green, 0.7))]})
    m.mirror_box("arm_r_low", "arm_l_low", 3.6, 13, -2.1, 3.8, 1.6, 4.2, fur)
    # Her left leg: a harpoon for a peg.
    m.bones["leg_l_low"].cubes = []
    m.box("leg_l_low", -3.7, 5.6, -1.8, 3.5, 1.2, 3.6, (40, 36, 34))
    m.box("leg_l_low", -2.5, 2.6, -0.55, 1.1, 3.2, 1.1, STEEL)
    m.box("leg_l_low", -3.0, 1.2, -1.05, 2.1, 1.6, 2.1, GOLD)
    m.box("leg_l_low", -2.4, 0, -0.45, 0.9, 1.3, 0.9, GOLD)
    # The gas harpoon, its tip alight.
    m.bones["weapon"].rest = (24, 0, 0)
    w = wb(m)
    w(-0.45, -11, -0.45, 0.9, 25, 0.9, (60, 56, 54), marks={f: [(0, 8, 99, 3, (90, 60, 40))] for f in SIDES})
    w(-1.5, 13.5, -0.7, 3, 5.5, 1.4, (70, 70, 76))
    w(-0.7, 14.2, -0.9, 1.4, 4, 1.8, (255, 176, 60), plain=True)
    w(-0.9, 19, -0.5, 1.8, 2, 1.0, (70, 70, 76))
    w(-0.45, 21, -0.4, 0.9, 2.4, 0.8, STEEL)
    return m


# --- N Corp., the Wild Hunt ----------------------------------------------------------------------------------

def kromer():
    robe, blond, grey, red = (242, 238, 226), (234, 224, 182), (228, 228, 230), (170, 30, 36)
    m = base("b_kromer", {"skin": (246, 232, 224), "top": robe, "sleeve": robe, "skirt": robe, "coat_len": 12.4, "pants": (60, 20, 24), "shoes": (120, 26, 30),
                          "accent": GOLD, "face": eye_face((120, 150, 170), lash=(90, 80, 70), mouth=(180, 120, 120))}, 1.05)
    # A bob in two tones, one eye under the fringe.
    m.box("head", 0, 31.4, -4.7, 4.7, 1.5, 9.4, grey)
    m.box("head", -4.7, 31.4, -4.7, 4.7, 1.5, 9.4, blond)
    m.box("head", 0, 24.5, -4.9, 4.7, 7.5, 1.2, sh(grey, 0.92))
    m.box("head", -4.7, 24.5, -4.9, 4.7, 7.5, 1.2, sh(blond, 0.9))
    m.box("head", 3.8, 24.5, -4.7, 1.1, 7.4, 7.6, sh(grey, 0.96))
    m.box("head", -4.9, 25, -4.7, 1.1, 7, 7.2, sh(blond, 0.95))
    m.box("head", 0.2, 27.2, 3.8, 4.4, 5, 0.9, grey)
    m.box("head", -4.5, 29.8, 3.8, 4.7, 2.4, 0.8, blond)
    m.box("head", 3.4, 23.6, 1.5, 1.4, 2, 2.4, sh(grey, 0.9))
    # The robe: gold trim and a cross, a rope belt, blood, a dark hem.
    m.box("torso", -4.05, 13.2, 2.0, 8.1, 10.6, 0.4, robe, marks={"north": [(3.6, 1, 0.9, 6, GOLD), (2.2, 2.6, 3.7, 0.9, GOLD), (0, 0, 0.6, 99, GOLD), (7.5, 0, 0.6, 99, GOLD),
                                                                        (0.8, 5, 1.8, 1.6, red), (5.6, 7.4, 1.6, 2, red), (1.6, 8.6, 1.2, 1.2, red)]})
    m.box("torso", -4.3, 14.6, -2.3, 8.6, 1.2, 4.6, GOLD, marks={f: [(i * 1.2, 0, 0.4, 99, sh(GOLD, 0.6)) for i in range(8)] for f in SIDES})
    m.box("root", 2.2, 8, 2.2, 0.8, 6, 0.8, GOLD)
    for x, z, w2, d in ((-4.7, -3, 9.4, 1.0), (-4.7, 2.4, 9.4, 1.0), (3.8, -2.6, 1.0, 5.2), (-4.8, -2.6, 1.0, 5.2)):
        m.box("coat", x, 0.5, z, w2, 3.4, d, (70, 22, 26))
        m.box("coat", x, 3.9, z, w2, 0.6, d, GOLD)
    for x, y in ((-3.6, 6), (1.4, 8), (-1, 4.4), (2.6, 5)):
        m.box("coat", x, y, 2.75, 1.6, 1.8, 0.4, red, plain=True)
    # Her left arm in steel.
    steel = (84, 84, 96)
    m.box("arm_l", -8.3, 21.4, -2.3, 4.6, 3.2, 4.6, steel, marks={f: [(0, 2.4, 99, 0.5, sh(steel, 0.6))] for f in SIDES})
    m.box("arm_l_low", -7.5, 12.4, -1.9, 3.8, 5.8, 3.8, sh(steel, 0.9), marks={f: [(0, 1.6, 99, 0.5, sh(steel, 0.6)), (0, 3.6, 99, 0.5, sh(steel, 0.6))] for f in SIDES})
    # The great nail.
    w = wb(m)
    dark = (50, 46, 50)
    w(-0.5, -3, -0.5, 1, 4.2, 1, (30, 28, 30))
    w(-2.2, 1, -0.7, 4.4, 1, 1.4, (70, 64, 60))
    w(-0.85, 2, -0.4, 1.7, 15, 0.8, dark, marks={f: [(0.3, 2, 0.5, 4, red), (0.9, 9, 0.5, 3, red)] for f in ("north", "south")})
    w(-0.55, 17, -0.35, 1.1, 2.6, 0.7, dark)
    w(-0.3, 19.6, -0.3, 0.6, 2, 0.6, dark)
    return m


def erlking_heathcliff():
    coat, hairc, grey = (74, 54, 48), (58, 38, 34), (122, 118, 114)
    skin = (216, 182, 162)
    m = base("b_erlking_heathcliff", {"skin": skin, "bulk": 0.5, "top": (60, 50, 50), "coat": coat, "coat_len": 11, "sleeve": coat, "pants": (40, 34, 36), "shoes": (26, 22, 22),
                                      "accent": (30, 26, 26), "pack": (22, 20, 26), "pack_big": True, "cape": (20, 18, 22), "cape_wide": 1.2,
                                      "face": eye_face((130, 100, 190), brow=(40, 26, 24), mouth=(120, 70, 60), narrow=True) + [(1.6, 7, 4.8, 0.9, sh(skin, 0.78))]}, 1.4, head=0.86)
    hair(m, hairc, back=17, side=19.5, vol=0.9, front=2.0, fringe=((-4.6, 2.0, 5.4), (-1.6, 2.2, 3.0), (1.8, 2.6, 5.8)))
    for i, (x, y, z, w2, h, d) in enumerate(((4.6, 16, -3, 1.6, 6, 4), (-6.2, 15, -3, 1.6, 7, 4), (-3, 14.5, -6, 3, 4, 1.4), (1, 13.5, -6, 3, 5, 1.4), (-1.4, 33, -2, 3, 1, 4))):
        m.box("head", x, y, z, w2, h, d, sh(hairc, 0.86 if i % 2 else 1.1))
    # A grey scarf, belts and straps, a ragged coat.
    m.box("torso", -4.9, 21.2, -2.9, 9.8, 3.2, 5.8, grey)
    m.box("torso", 0.8, 15.5, 2.4, 2.2, 6, 0.7, sh(grey, 0.9))
    m.box("torso", -4.6, 13.3, 2.3, 9.2, 8, 0.3, (60, 50, 50), marks={"north": [(0, 5.6, 99, 0.8, (30, 26, 26)), (0, 3.2, 99, 0.6, (30, 26, 26)), (4, 5.4, 1.2, 1.2, (150, 140, 120)), (2, 0, 0.6, 3.2, (30, 26, 26))]})
    for i, (x, ln) in enumerate(((-4.6, 2.5), (-2.4, 1.2), (0, 2.8), (2.6, 1.6))):
        m.box("coat", x, 2 - ln, -3.3, 2.2, ln + 0.2, 0.8, sh(coat, 0.85))
    m.mirror_box("coat", "coat", 1.6, 1, 2.1, 3.1, 12, 0.7, coat, marks={"north": [(0, 0, 0.5, 99, sh(coat, 0.6)), (1.4, 3, 0.8, 0.8, (150, 140, 120))]})
    m.mirror_box("leg_r_low", "leg_l_low", -0.1, 2, -2.1, 4.2, 4.6, 4.2, (30, 26, 26), marks={f: [(0, 1, 99, 0.5, (90, 80, 76))] for f in SIDES})
    for x, y, h in ((5.6, 12, 8), (-8, 9, 10), (7.2, 18, 5), (-9.4, 16, 4)):
        m.box("cape", x, y, -4.8, 2.2, h, 0.8, (16, 14, 18))
    m.box("pack", -5, 16, -8.4, 10, 1, 6, (120, 116, 122))
    m.box("pack", -5, 24, -8.4, 10, 1, 6, (120, 116, 122))
    # The great sword of red thorn.
    w = wb(m)
    blade, bone = (126, 38, 38), (216, 192, 172)
    w(-0.6, -4.5, -0.6, 1.2, 5.5, 1.2, (30, 26, 26))
    w(-2.4, 1, -0.8, 4.8, 1.2, 1.6, (60, 30, 30))
    w(-1.6, 2.2, -0.5, 3.2, 17.5, 1.0, blade, marks={f: [(1.2, 0, 0.8, 99, sh(blade, 0.6))] for f in ("north", "south")})
    w(-1.0, 19.7, -0.45, 2, 2.4, 0.9, blade)
    w(-0.5, 22, -0.4, 1, 1.8, 0.8, sh(blade, 1.2))
    for i in range(7):
        sg = 1 if i % 2 else -1
        w(sg * 1.6 - (0 if sg > 0 else 1.4), 3.5 + i * 2.3, -0.3, 1.4, 0.9, 0.6, bone)
        w(sg * 2.6 - (0 if sg > 0 else 0.7), 4.2 + i * 2.3, -0.3, 0.7, 1.2, 0.6, bone)
    return m


# --- The Abnormalities' champions and the house of Jia -----------------------------------------------------------

def time_ripper():
    black, gold = (24, 24, 28), (200, 168, 84)
    cage = [(0.6, 2.0, 6.8, 5.6, (16, 14, 16))] + [(1.1 + i * 1.4, 2.0, 0.45, 5.6, gold) for i in range(5)] + [(0.6, 5.0, 6.8, 0.4, gold), (0.6, 2.0, 6.8, 0.4, gold),
            (1.7, 3.3, 1.3, 1.1, (255, 226, 130)), (5.0, 3.3, 1.3, 1.1, (255, 226, 130))]
    m = base("b_time_ripper", {"skin": (36, 34, 36), "slim": True, "top": black, "coat": black, "coat_len": 11.5, "sleeve": black, "pants": (20, 20, 24), "shoes": (20, 20, 24),
                               "hands": gold, "claws": gold, "inner": (232, 232, 232), "tie": (18, 18, 20), "lapels": 1, "face": cage}, 1.5, head=0.86)
    # A tall hat with goggles, a gear and an aerial.
    m.box("head", -5.6, 31.6, -5.6, 11.2, 0.8, 11.2, black, marks={f: [(0, 0, 99, 0.3, gold)] for f in SIDES})
    m.box("head", -3.9, 32.4, -3.9, 7.8, 6.6, 7.8, black, marks={f: [(0, 5.2, 99, 1.2, gold)] for f in SIDES})
    m.box("head", -4.1, 39, -4.1, 8.2, 0.6, 8.2, sh(black, 1.3))
    for x in (-3.2, 0.6):
        m.box("head", x, 33.2, 3.8, 2.6, 2.6, 1.3, gold, marks={"north": [(0.5, 0.5, 1.6, 1.6, (40, 60, 70))]})
    m.box("head", 2.6, 36.4, 3.8, 2.8, 2.8, 0.6, gold, marks={"north": [(0.9, 0.9, 1.0, 1.0, black)]})
    m.box("head", -3.2, 39.6, -1, 0.5, 4.2, 0.5, gold)
    m.box("head", -3.5, 43.4, -1.3, 1.1, 0.8, 1.1, gold)
    m.box("head", -4.1, 24, -4.1, 8.2, 2.0, 8.2, black)
    # The long coat: gold piping, two rows of buttons, banded sleeves.
    tw = 7.0
    m.box("torso", -tw / 2 - 0.05, 13.2, 1.82, tw + 0.1, 7, 0.3, black, marks={"north": [(1.4, 0.8 + i * 1.6, 0.7, 0.7, gold) for i in range(4)] + [(4.9, 0.8 + i * 1.6, 0.7, 0.7, gold) for i in range(4)]
                                                                                 + [(0, 0, 0.35, 99, gold), (tw - 0.3, 0, 0.35, 99, gold)]})
    m.mirror_box("coat", "coat", 0.3, 1.5, 1.6, 3.4, 11.5, 0.6, black, marks={"north": [(0, 0, 0.4, 99, gold), (3.0, 0, 0.4, 99, gold), (1.4, 2, 0.7, 0.7, gold), (1.4, 5, 0.7, 0.7, gold), (0, 11, 99, 0.5, gold)]})
    for bone, ys in (("arm_r", (22.6, 20.6, 18.8)), ("arm_r_low", (16.6, 15, 13.4))):
        for y in ys:
            m.mirror_box(bone, bone.replace("_r", "_l"), 3.35, y, -1.4, 2.8, 0.45, 2.8, gold)
    m.mirror_box("arm_r", "arm_l", 3.1, 23, -1.7, 3.3, 1.1, 3.4, gold)
    m.mirror_box("leg_r_low", "leg_l_low", 0, 0, -2.1, 4, 0.5, 5.2, gold)
    return m


def knight_of_despair():
    blue, deep, armor, star = (30, 64, 160), (22, 44, 120), (22, 22, 32), (236, 244, 255)
    skull = [(1.0, 3.0, 2.3, 2.6, BLACK), (4.7, 3.0, 2.3, 2.6, BLACK), (1.7, 5.6, 0.6, 2.4, BLACK), (5.7, 5.6, 0.6, 2.4, BLACK), (3.6, 5.2, 0.8, 0.9, (70, 70, 84)),
             (2.6, 6.8, 2.8, 0.35, (70, 70, 84))]
    m = base("b_knight_of_despair", {"skin": (244, 244, 248), "top": armor, "sleeve": armor, "skirt": blue, "coat_len": 12, "skirt_wide": 5, "frill": (70, 120, 236),
                                     "pants": deep, "shoes": (16, 16, 24), "gauntlets": (16, 16, 26), "cape": blue, "cape_wide": 4, "cape_fur": deep, "face": skull}, 1.5, head=0.9)
    # Hair like a night sky, pouring down and out behind her; a crown of black thorns.
    m.box("head", -4.7, 30.8, -4.7, 9.4, 2.2, 9.4, blue)
    m.box("head", -4.7, 20, -5.2, 9.4, 12, 1.4, deep)
    m.mirror_box("head", "head", 4.0, 21, -4.7, 0.9, 10.6, 4.2, blue)
    m.box("head", -4.7, 30, 3.6, 2.4, 2, 1, blue)
    m.box("head", 2.3, 30, 3.6, 2.4, 2, 1, blue)
    m.box("head", -1.1, 31, 4.2, 2.2, 1.5, 0.6, star, plain=True)
    for x, y, z, w2, h, d in ((-0.5, 33, -3, 1, 5.5, 1), (-3.4, 32.8, -2, 0.9, 3.8, 0.9), (2.5, 32.8, -2, 0.9, 3.8, 0.9), (4.6, 30.4, -1, 3, 0.9, 0.9), (-7.6, 30.4, -1, 3, 0.9, 0.9),
                              (7, 30.4, -1, 0.9, 2.6, 0.9), (-7.6, 30.4, -1, 0.9, 2.6, 0.9)):
        m.box("head", x, y, z, w2, h, d, BLACK)
    for i, (x, y, w2, h) in enumerate(((7.6, 9, 7, 13), (-14.6, 6, 7, 12), (12.5, 13, 5, 8), (-18, 9, 4.5, 7))):
        m.box("cape", x, y, -5.4 - (i % 2) * 0.5, w2, h, 1.0, deep if i % 2 else blue)
    for i, (x, y) in enumerate(((-6, 20), (3, 16), (9, 18), (-11, 12), (0, 9), (6, 7), (-4, 5), (14, 15), (-15, 10), (-9, 17), (10, 11))):
        m.box("cape", x, y, -6.6 if abs(x) > 8 else -5.7, 0.6 + (i % 3 == 0) * 0.5, 0.6 + (i % 3 == 0) * 0.5, 0.5, star, plain=True)
    # Black armour with white edges; stars on the dress.
    m.box("torso", -4.1, 13.2, 2.0, 8.2, 10.6, 0.4, armor, marks={"north": [(3.7, 0, 0.8, 99, star), (0.6, 1, 2.6, 0.5, (150, 160, 190)), (5, 1, 2.6, 0.5, (150, 160, 190)), (1.4, 8.6, 5.4, 0.5, (150, 160, 190))]})
    m.mirror_box("arm_r", "arm_l", 3.8, 22.2, -2.2, 3.8, 2.4, 4.4, armor)
    m.mirror_box("arm_r", "arm_l", 6.6, 23.6, -1, 2.4, 0.9, 0.9, BLACK)
    for i, (x, y, z) in enumerate(((-6, 9, 5.6), (4.6, 6, 6.7), (-2, 3, 7.1), (7.4, 2.4, 2), (-8.8, 3, -1), (1, 8.4, 5.6), (-5.4, 1.6, 7.1), (5.8, 1.2, -7.1))):
        m.box("coat", x, y, z, 0.7, 0.7, 0.4, star, plain=True)
    m.box("coat", -3.4, 1.4, 6.9, 6.8, 0.3, 0.4, star, plain=True)
    # Black blades fanned out behind her.
    for i, rz in enumerate((38, -38, 78, -78, 118, -118)):
        n = "blade%d" % i
        m.bone(n, "torso", (0, 17, -4.6 - (i % 2) * 0.3), rest=(0, 0, rz))
        m.box(n, -0.45, 4, -0.2, 0.9, 19, 0.4, BLACK, rel=True)
        m.box(n, -0.2, 22.6, -0.15, 0.4, 1.4, 0.3, BLACK, rel=True)
    m.parts["Back"] = ["blade%d" % i for i in range(6)]
    w = wb(m)
    w(-0.4, -2.5, -0.4, 0.8, 3.5, 0.8, (30, 30, 44))
    w(-1.6, 1, -0.5, 3.2, 0.7, 1.0, star)
    w(-0.4, 1.7, -0.25, 0.8, 19, 0.5, BLACK)
    w(-0.2, 20.7, -0.2, 0.4, 2, 0.4, BLACK)
    return m


def jia_mu():
    robe, gold, white = (246, 242, 230), (212, 178, 72), (240, 240, 240)
    skin = (240, 232, 224)
    face = [(1.0, 3.9, 2.2, 0.5, (150, 130, 80)), (4.8, 3.9, 2.2, 0.5, (150, 130, 80)), (1.0, 3.0, 2.2, 0.35, (200, 200, 200)), (4.8, 3.0, 2.2, 0.35, (200, 200, 200)),
            (3.4, 6.3, 1.2, 0.35, (170, 120, 120)), (3.7, 2.2, 0.6, 0.6, gold)]
    m = base("b_jia_mu", {"skin": skin, "slim": True, "top": robe, "sleeve": robe, "skirt": gold, "coat_len": 12.6, "pants": (200, 170, 70), "shoes": (130, 108, 44),
                          "accent": gold, "face": face}, 1.45, head=0.84)
    # White hair: a high bun with gold pins, long down the back, locks at the temples.
    m.box("head", -4.4, 31.3, -4.4, 8.8, 1.2, 8.8, white)
    m.box("head", -3.7, 5, -5.0, 7.4, 27.2, 0.9, sh(white, 0.93))
    m.mirror_box("head", "head", 3.9, 26, -4.4, 0.6, 6, 6, sh(white, 0.96))
    m.mirror_box("head", "head", 3.9, 13, 1.6, 0.7, 16, 1.2, white)
    m.box("head", -2.1, 32.5, -2.8, 4.2, 3.2, 4.2, white)
    m.box("head", -1.5, 35.7, -2.2, 3, 1.6, 3, sh(white, 0.94))
    m.box("head", -2.4, 32.4, -3.1, 4.8, 0.8, 4.8, gold)
    m.mirror_box("head", "head", 2.1, 33.6, -1.2, 3.8, 0.5, 0.5, gold)
    m.mirror_box("head", "head", 5.4, 33.6, -1.2, 0.5, 2.2, 0.5, gold)
    m.mirror_box("head", "head", 4.4, 35.4, -1.2, 1.5, 0.5, 0.5, gold)
    # The robe: a gold collar, a jewelled belt, sleeves like bells.
    tw = 7.0
    m.box("torso", -tw / 2 - 0.05, 13.2, 1.82, tw + 0.1, 10.6, 0.35, robe, marks={"north": [(3.0, 0, 1.1, 7, gold), (2.2, 0, 0.5, 4.5, gold), (4.4, 0, 0.5, 4.5, gold), (2.6, 3, 1.9, 0.5, white),
                                                                                      (0, 8.6, 99, 1.6, gold), (2.2, 8.9, 0.9, 0.9, white), (3.2, 8.9, 0.9, 0.9, white), (4.2, 8.9, 0.9, 0.9, white)]})
    m.box("torso", -tw / 2 - 0.3, 14, -2.1, tw + 0.6, 1.7, 4.2, gold)
    m.mirror_box("arm_r", "arm_l", 3.2, 18, -1.9, 3.6, 5.6, 3.8, robe, marks={f: [(0, 0, 99, 0.5, gold)] for f in SIDES})
    m.mirror_box("arm_r_low", "arm_l_low", 2.9, 10.6, -2.7, 4.4, 7.4, 5.4, robe, marks={f: [(0, 6.6, 99, 0.8, gold), (0, 5.4, 99, 0.3, gold)] for f in SIDES})
    # The skirt: gold with white stripes, white panels at the sides, a train spread on the floor.
    for i in range(5):
        m.box("coat", -3.1 + i * 1.4, 0.4, 1.95, 0.6, 12.4, 0.4, white, plain=True)
    m.mirror_box("coat", "coat", 2.2, 0.4, -2.9, 2.4, 12.6, 5.6, robe, marks={f: [(0.6, 4 + i * 2.4, 1.2, 1.2, gold) for i in range(3)] + [(0, 11.6, 99, 0.6, gold)] for f in ("east", "west", "north")})
    m.box("coat", -4.2, 0.4, -3.3, 8.4, 12.6, 0.8, robe)
    for i, (w2, d, y, zb) in enumerate(((13, 7, 1.6, -7.5), (18, 9, 0.8, -10.5), (22, 9, 0, -13))):
        m.box("coat", -w2 / 2, y, zb, w2, 0.8, d + 3.5, sh(robe, 1.0 - 0.04 * i), marks={"up": [(1 + j * 2.6, 1, 1.2, 1.2, gold) for j in range(int(w2 / 2.6))], "south": [(0, 0, 99, 0.3, gold)]})
    m.mirror_box("coat", "coat", 0.6, 0, 2.2, 2.6, 1.6, 1.6, (130, 108, 44))
    return m


# --- The Nursefathers: the EXTREME floors' hidden bosses ----------------------------------------------------------

def chain(m, bone, x0, y0, x1, y1, z, n, col=GOLD, size=1.3, depth=0.9):
    """A chain in links from (x0, y0) to (x1, y1) on the plane z."""
    for i in range(n):
        f = i / max(1, n - 1)
        m.box(bone, x0 + (x1 - x0) * f - size / 2, y0 + (y1 - y0) * f - size / 2, z, size, size, depth, sh(col, 1.1 if i % 2 else 0.85))


def valencina():
    red, hairc, tan = (142, 26, 36), (178, 162, 98), (204, 174, 114)
    m = base("b_valencina", {"skin": (238, 216, 198), "top": tan, "coat": red, "coat_len": 12.2, "sleeve": red, "pants": (132, 24, 34), "shoes": (92, 52, 38), "hands": (26, 24, 26),
                             "accent": (92, 52, 38), "lapels": 1, "face": eye_face((100, 140, 92), lash=(70, 56, 40), mouth=(170, 80, 80))}, 1.45, head=0.84)
    hair(m, hairc, back=7, side=13, vol=0.6, front=1.6, fringe=((-4.4, 2.4, 3.4), (2.2, 2.2, 4.2)))
    m.mirror_box("head", "head", 4.3, 8, -2.5, 1.2, 12, 3.4, sh(hairc, 0.9))
    m.mirror_box("head", "head", 2.9, 10, 2.2, 1.6, 17, 1.2, sh(hairc, 1.05))
    # A wide red hat, tilted; a cigar.
    hat = (126, 22, 32)
    m.box("head", -7.6, 31.3, -7.6, 15.2, 0.8, 15.2, hat)
    m.box("head", -6.4, 32.0, -6.4, 3.4, 0.7, 12.8, sh(hat, 1.12))
    m.box("head", -3.9, 32.1, -3.9, 7.8, 3.6, 7.8, hat, marks={f: [(0, 2.6, 99, 1.0, (70, 12, 18)), (0, 2.5, 99, 0.25, GOLD)] for f in SIDES})
    m.box("head", -3.2, 35.7, -3.2, 6.4, 0.7, 6.4, sh(hat, 1.1))
    m.box("head", 1.2, 25.4, 4, 0.6, 0.6, 2.4, (120, 80, 50))
    m.box("head", 1.2, 25.4, 6.4, 0.6, 0.6, 0.5, (255, 140, 50), plain=True)
    # The long coat: gold edges and buttons, an emblem; a belt with a gold buckle.
    m.box("torso", 1.2, 19, 2.05, 2.4, 2.4, 0.4, GOLD, marks={"north": [(0.6, 0.6, 1.2, 1.2, red)]})
    m.mirror_box("torso", "torso", 1.6, 13.2, 2.02, 0.5, 10.6, 0.4, GOLD)
    m.box("root", -4.3, 11.8, -2.3, 8.6, 1.6, 4.6, (92, 52, 38), marks={"north": [(3.4, 0.2, 1.8, 1.2, GOLD)]})
    m.mirror_box("coat", "coat", 0.9, 0.8, 1.7, 3.2, 12.2, 0.7, red, marks={"north": [(0, 0, 0.5, 99, GOLD)] + [(1.6, 1.2 + i * 2.2, 0.7, 0.7, GOLD) for i in range(5)]})
    for x, z, w2, d in ((-4.6, -2.9, 9.2, 0.8), (3.8, -2.6, 0.9, 5.2), (-4.7, -2.6, 0.9, 5.2)):
        m.box("coat", x, 0.8, z, w2, 0.6, d, GOLD)
    m.mirror_box("arm_r_low", "arm_l_low", 3.9, 13.6, -1.6, 3.2, 0.6, 3.2, GOLD)
    # At her hip: a long gun, slung low.
    m.bone("rifle", "root", (-4.4, 12, 0.5), rest=(0, 0, 38))
    m.box("rifle", -0.6, -15, -0.5, 1.2, 8, 1.0, (200, 110, 40), rel=True)
    m.box("rifle", -0.45, -7, -0.4, 0.9, 13, 0.8, (120, 30, 34), rel=True, marks={f: [(0, 3, 99, 0.6, GOLD), (0, 9, 99, 0.6, GOLD)] for f in SIDES})
    m.box("rifle", -0.9, 4.5, -0.6, 1.8, 1.4, 1.2, GOLD, rel=True)
    # A sabre with a gold guard.
    w = wb(m)
    w(-0.5, -3, -0.5, 1, 4, 1, (120, 26, 34))
    w(-1.8, 1, -1.2, 3.6, 0.8, 2.4, GOLD)
    w(-0.5, 0.2, 0.9, 1, 2.2, 0.5, GOLD)
    w(-0.4, 1.8, -0.2, 0.8, 17, 0.4, STEEL)
    w(-0.25, 18.8, -0.2, 0.5, 1.6, 0.4, STEEL)
    return m


def rien():
    black = (26, 26, 30)
    skin = (242, 230, 224)
    face = [(0.9, 3.2, 2.2, 0.45, (20, 18, 20)), (0.9, 3.65, 2.2, 1.1, WHITE), (1.5, 3.65, 1.1, 1.1, (150, 150, 62)), (1.85, 3.9, 0.4, 0.6, (40, 40, 20)),
            (0.9, 2.5, 2.2, 0.4, (20, 18, 20)), (2.6, 6.3, 1.6, 0.35, (150, 100, 100))]
    m = base("b_rien", {"skin": skin, "slim": True, "top": black, "lapels": 1, "inner": WHITE, "tie": (16, 16, 18), "pants": (22, 22, 26), "shoes": (16, 16, 18), "hands": WHITE,
                        "face": face}, 1.45, head=0.84)
    hc = (20, 20, 24)
    m.box("head", -4.3, 31.3, -4.3, 8.6, 1.3, 8.6, hc)
    m.box("head", -4.3, 26.4, -4.5, 8.6, 5.8, 0.9, sh(hc, 0.9))
    m.mirror_box("head", "head", 3.8, 28.4, -4.3, 0.7, 3.8, 5.6, hc)
    m.box("head", -3.4, 32.5, -3.6, 6.8, 0.7, 6.8, sh(hc, 1.5))
    for x, w2, ln in ((-4.3, 1.6, 3.6), (-1.4, 1.2, 2.2), (1.2, 1.0, 4.4), (3.0, 1.3, 2.6)):
        m.box("head", x, 32.2 - ln, 3.8, w2, ln, 0.7, hc)
    # Half his face under a cracked porcelain mask, rimmed in gold.
    porcelain, crack = (242, 242, 246), (120, 124, 136)
    m.box("head", -4.15, 25.6, 3.95, 4.3, 5.4, 0.5, porcelain, plain=True,
          marks={"north": [(0, 0, 0.3, 5.4, GOLD), (0, 5.1, 1.4, 0.3, GOLD), (1.2, 0, 0.2, 2.2, crack), (1.2, 2.0, 1.6, 0.2, crack), (2.6, 2.0, 0.2, 2.2, crack), (0.4, 3.2, 1.0, 0.2, crack),
                           (2.6, 1.0, 1.4, 0.2, crack), (3.4, 2.8, 0.2, 2.0, crack), (1.6, 4.0, 1.2, 0.2, crack), (0.5, 0.6, 0.6, 1.2, (214, 226, 246)), (3.0, 0.3, 0.8, 0.6, (250, 232, 214)),
                           (1.8, 2.6, 0.7, 0.9, (214, 226, 246))]})
    m.box("head", -4.25, 25.6, 1.6, 0.4, 5.4, 2.8, porcelain, plain=True)
    m.box("head", -3.2, 25.0, 3.95, 3.3, 0.7, 0.5, porcelain, plain=True)
    # A glossy suit: gold buttons, a gold armband, gold under the soles.
    tw = 7.0
    m.box("torso", -tw / 2 - 0.05, 13.2, 1.82, tw + 0.1, 6.6, 0.3, black, marks={"north": [(3.1, 0.8, 0.8, 0.8, GOLD), (3.1, 2.8, 0.8, 0.8, GOLD), (1.0, 0, 0.5, 6.6, (60, 60, 68)), (5.6, 0, 0.5, 6.6, (60, 60, 68))]})
    m.box("torso", -2.9, 19, 1.9, 1.5, 0.8, 0.3, WHITE)
    m.box("arm_l", -6.75, 19.6, -1.55, 3.1, 2.4, 3.1, GOLD, marks={f: [(0, 0.5, 99, 0.4, black), (0, 1.5, 99, 0.4, black)] for f in SIDES})
    m.mirror_box("arm_r_low", "arm_l_low", 3.4, 13.4, -1.4, 2.8, 0.5, 2.8, GOLD)
    m.mirror_box("leg_r_low", "leg_l_low", 0, 0, -2.1, 4, 0.5, 5.2, GOLD)
    m.mirror_box("leg_r", "leg_l", 0.1, 6.6, 1.5, 3.8, 6.4, 0.4, sh(black, 1.5))
    # The terminal in his hand.
    w = wb(m)
    w(-1.0, 0.2, -1.6, 2.0, 0.5, 3.2, (30, 30, 36), marks={"up": [(0.3, 0.4, 1.4, 2.4, (120, 220, 200))]})
    w(-1.1, 0.0, -1.7, 2.2, 0.3, 3.4, GOLD)
    return m


def matthias():
    skin, white, purple, fur = (98, 64, 52), (242, 242, 242), (98, 52, 152), (24, 20, 28)
    face = [(0.4, 3.0, 7.2, 1.9, (18, 16, 20)), (2.4, 5.9, 3.2, 1.0, (244, 244, 244)), (2.4, 6.3, 3.2, 0.25, (60, 40, 34)), (3.2, 5.9, 0.25, 1.0, (60, 40, 34)), (4.6, 5.9, 0.25, 1.0, (60, 40, 34))]
    m = base("b_matthias", {"skin": skin, "bulk": 1.4, "top": white, "inner": skin, "lapels": 1, "pants": white, "shoes": (92, 52, 142), "accent": (24, 24, 28),
                            "cape": purple, "cape_wide": 3.4, "cape_fur": fur, "face": face}, 1.55, head=0.86)
    m.box("head", -4.4, 31.3, -4.4, 8.8, 1.4, 8.8, white)
    m.box("head", -4.4, 27, -4.6, 8.8, 5.2, 0.9, sh(white, 0.92))
    m.mirror_box("head", "head", 3.9, 29, -4.4, 0.7, 3.2, 5, sh(white, 0.95))
    for i, (x, z, h) in enumerate(((-3.6, -3, 1.6), (-0.8, -3.4, 2.2), (2, -2.6, 1.6), (-2.6, 0.4, 1.4), (0.6, 0.6, 1.8), (2.6, 1.6, 1.2))):
        m.box("head", x, 32.6, z, 1.9, h, 1.9, sh(white, 1.0 if i % 2 else 0.9))
    m.box("head", -4.2, 30.2, 3.8, 8.4, 1.6, 0.7, white)
    m.box("head", -4.5, 27.4, -4.5, 9, 1.9, 9, (18, 16, 20))
    tw = 10.8
    # The chest, scarred; great gold chains across it and round the waist.
    m.box("torso", -1.9, 17, 2.72, 3.8, 6.4, 0.3, skin, marks={"north": [(0.6, 1 + i * 1.3, 2.6, 0.35, sh(skin, 0.6)) for i in range(4)]})
    chain(m, "torso", -5.4, 13.6, 5.2, 23.2, 2.9, 9, size=1.7, depth=1.2)
    chain(m, "torso", -5.4, 13.6, 5.2, 23.2, -3.9, 9, size=1.7, depth=1.0)
    m.box("root", -tw / 2 - 0.3, 11.6, -3.1, tw + 0.6, 1.8, 6.2, (24, 24, 28), marks={"north": [(4.6, 0.2, 2, 1.4, GOLD)]})
    chain(m, "root", -4.6, 11.4, -1, 8.2, 2.9, 5, size=1.6, depth=1.1)
    # A purple book with a gold clasp at his hip; chains wound down one leg.
    m.box("root", 2.4, 5.4, 2.6, 4.4, 5.6, 1.8, (128, 40, 130), marks={"north": [(1.4, 1.8, 1.6, 1.8, GOLD), (0.3, 0.3, 3.8, 0.45, GOLD), (0.3, 4.9, 3.8, 0.45, GOLD), (0.3, 0.3, 0.45, 5, GOLD), (3.7, 0.3, 0.45, 5, GOLD)]})
    for y in (10.6, 8.4):
        m.box("leg_l", -5.1, y, -2.7, 4.9, 1.5, 5.4, sh(GOLD, 1.05), marks={f: [(i * 1.3, 0, 0.4, 99, sh(GOLD, 0.55)) for i in range(4)] for f in SIDES})
    for y in (4.8, 2.6):
        m.box("leg_l_low", -5.1, y, -2.7, 4.9, 1.5, 5.4, sh(GOLD, 0.95), marks={f: [(i * 1.3, 0, 0.4, 99, sh(GOLD, 0.55)) for i in range(4)] for f in SIDES})
    m.box("leg_l_low", -5.0, 0, -2.6, 4.6, 1.9, 6.4, (60, 36, 34))
    for dz in (-1.6, 0, 1.6):
        m.box("leg_l_low", -3 + dz * 0.9, 0, 3.6, 0.9, 0.9, 1.4, (20, 18, 20))
    # The coat: black fur at the collar and down the edges, a pattern in its purple.
    for i, (x, y, z, w2, h, d) in enumerate(((6, 23, -4, 4.4, 3, 2.2), (-10.4, 23, -4, 4.4, 3, 2.2), (3.4, 25, -5, 3.2, 3, 2), (-6.6, 25, -5, 3.2, 3, 2), (-2, 25.4, -5.4, 4, 3, 2))):
        m.box("torso", x, y, z, w2, h, d, sh(fur, 1.3 if i % 2 else 1.0))
    for i in range(6):
        m.box("cape", 8.2 - (i % 2) * 0.5, 3.5 + i * 3.2, -5, 2.4, 3.4, 1.8, sh(fur, 1.3 if i % 2 else 1.0))
        m.box("cape", -10.6 + (i % 2) * 0.5, 3.5 + i * 3.2, -5, 2.4, 3.4, 1.8, sh(fur, 1.0 if i % 2 else 1.3))
    for i in range(8):
        m.box("cape", -7 + i * 1.9, 3.2, -5.2, 1.7, 2.2, 1.6, sh(fur, 1.3 if i % 2 else 1.0))
    for i, (x, y) in enumerate(((-6, 18), (-2, 14), (3, 19), (5.5, 12), (-4.5, 9), (0.5, 8), (-7, 13), (2, 23))):
        m.box("cape", x, y, -4.6, 1.6, 1.6, 0.3, sh(purple, 0.6), plain=True)
    m.mirror_box("arm_r", "arm_l", 4.6, 17, -2.3, 4.2, 6.8, 4.6, purple)
    # Laevateinn: a great black blade, stepped like a key.
    w = wb(m)
    dark = (30, 30, 36)
    w(-0.7, -5, -0.7, 1.4, 6, 1.4, (60, 60, 68), marks={f: [(0, 1 + i * 1.3, 99, 0.4, dark) for i in range(4)] for f in SIDES})
    w(-2.6, 1, -1, 5.2, 1.6, 2, dark)
    w(-1.9, 2.6, -0.6, 3.8, 15, 1.2, dark, marks={f: [(1.6, 0, 0.6, 99, (170, 40, 40))] for f in ("north", "south")})
    for i in range(4):
        w(1.9, 4 + i * 3.4, -0.5, 1.3, 1.8, 1.0, (60, 60, 68))
    w(-1.3, 17.6, -0.55, 2.6, 2.6, 1.1, dark)
    w(-0.7, 20.2, -0.5, 1.4, 2.2, 1.0, (60, 60, 68))
    return m


def callisto():
    robe, cream, red = (246, 244, 238), (238, 232, 212), (152, 26, 32)
    skin = (228, 228, 234)
    m = base("b_callisto", {"skin": skin, "slim": True, "top": robe, "sleeve": robe, "skirt": robe, "coat_len": 12.6, "pants": robe, "shoes": robe, "hands": red, "claws": (110, 14, 20),
                            "face": [(3.6, 1.6, 0.8, 5.6, (168, 168, 178)), (1.2, 3.8, 1.8, 0.4, (168, 168, 178)), (5.0, 3.8, 1.8, 0.4, (168, 168, 178))]}, 1.6, head=0.8)
    # The great hat: a white crown on a wide, lobed brim spotted black.
    spots = [(1 + (i * 37 % 17), 1 + (i * 53 % 13), 1.6 + (i % 3) * 0.6, 1.4 + (i % 2) * 0.8, (26, 24, 26)) for i in range(16)]
    m.box("head", -10.5, 31.6, -8, 21, 0.8, 16, cream, marks={"up": spots, "down": spots})
    m.box("head", 8.5, 31.8, -5, 5, 0.7, 9, cream, marks={"up": [(1, 1, 2, 1.6, (26, 24, 26)), (2.4, 4.6, 1.6, 2, (26, 24, 26)), (0.6, 6.6, 2, 1.4, (26, 24, 26))]})
    m.box("head", -14, 31.8, -4, 4.5, 0.7, 8, cream, marks={"up": [(1, 1, 2, 1.6, (26, 24, 26)), (1.6, 4.4, 2, 2, (26, 24, 26))]})
    m.box("head", -3.2, 32.4, -3.2, 6.4, 5.6, 6.4, robe, marks={f: [(0, 4.6, 99, 0.5, (200, 196, 180))] for f in SIDES})
    m.box("head", -3.6, 38, -3.6, 7.2, 0.6, 7.2, robe)
    # The robe: gold edges, panels down the front, a chest laid open; a train on the floor.
    tw = 7.0
    m.box("torso", -2.2, 18.4, 1.84, 4.4, 4.6, 0.4, red, marks={"north": [(0.4, 0.6 + i * 1.0, 3.6, 0.35, (240, 220, 220)) for i in range(4)] + [(2.0, 0, 0.4, 4.6, (90, 10, 16))]})
    m.mirror_box("torso", "torso", 2.3, 13.2, 1.84, 1.2, 10.6, 0.4, robe, marks={"north": [(0, 0, 0.3, 99, GOLD), (0.9, 0, 0.3, 99, GOLD)]})
    m.box("torso", -tw / 2 - 0.3, 22.4, -2.1, tw + 0.6, 1.8, 4.2, robe, marks={f: [(0, 1.3, 99, 0.4, GOLD)] for f in SIDES})
    m.mirror_box("arm_r", "arm_l", 3.1, 17.6, -2, 3.8, 6, 4, robe, marks={f: [(0, 0, 99, 0.4, GOLD), (1.2, 1, 0.3, 5, (255, 214, 226)), (2.4, 1, 0.3, 5, (206, 226, 255))] for f in SIDES})
    m.mirror_box("arm_r_low", "arm_l_low", 3.5, 12.4, -1.1, 2.2, 5.6, 2.2, (120, 20, 26))
    m.mirror_box("coat", "coat", 2.2, 0.4, -2.9, 2.4, 12.6, 5.6, robe, marks={f: [(0.5, 0, 0.3, 99, (255, 214, 226)), (1.4, 0, 0.3, 99, (206, 226, 255)), (0, 12, 99, 0.6, GOLD)] for f in ("east", "west", "north")})
    m.box("coat", -1.6, 0.4, 1.9, 3.2, 12.4, 0.5, robe, marks={"north": [(0, 0, 0.35, 99, GOLD), (2.85, 0, 0.35, 99, GOLD)]})
    for i, (w2, d, y, zb) in enumerate(((14, 8, 1.4, -8), (20, 9, 0.7, -11), (25, 8, 0, -13))):
        m.box("coat", -w2 / 2, y, zb, w2, 0.7, d + 4, sh(robe, 1.0 - 0.03 * i), marks={"up": [(1.5 + j * 3.2, 1, 0.3, d, (255, 214, 226) if j % 2 else (206, 226, 255)) for j in range(int(w2 / 3.2))], "south": [(0, 0, 99, 0.3, GOLD)]})
    # A sword of bone and red flesh, taller than he is.
    m.bones["weapon"].rest = (18, 0, 0)
    w = wb(m)
    w(-0.6, -9, -0.6, 1.2, 8, 1.2, (110, 16, 22))
    w(-1.6, -1.4, -0.9, 3.2, 2.6, 1.8, red, marks={f: [(0.5, 0.4 + i * 0.7, 2.2, 0.3, (240, 220, 220)) for i in range(3)] for f in ("north", "south")})
    w(-0.9, 1.2, -0.5, 1.8, 18, 1.0, red, marks={f: [(0.7, 0, 0.4, 99, (90, 10, 16))] for f in ("north", "south")})
    for i in range(6):
        sg = 1 if i % 2 else -1
        w(sg * 0.9 - (0 if sg > 0 else 2.2), 2.5 + i * 2.7, -0.35, 2.2, 1.0, 0.7, (120, 18, 24))
        w(sg * 2.6 - (0 if sg > 0 else 0.9), 3.3 + i * 2.7, -0.3, 0.9, 1.5, 0.6, (120, 18, 24))
    w(-0.6, 19.2, -0.45, 1.2, 2.6, 0.9, (226, 214, 200))
    w(-1.1, 21.6, -0.5, 2.2, 1.2, 1.0, (226, 214, 200))
    return m


def kira():
    white, purple = (244, 244, 246), (112, 62, 172)
    m = base("b_kira", {"skin": (246, 234, 234), "bulk": 0.6, "top": white, "sleeve": white, "coat": white, "coat_len": 3.6, "pants": purple, "shoes": (240, 240, 244),
                        "hood": white, "mask": white, "face": eye_face((150, 90, 220), lash=(90, 80, 110), mouth=(190, 140, 150))}, 1.1)
    m.box("head", -4.2, 30.4, 3.7, 8.4, 1.9, 0.7, (238, 238, 244))
    m.box("head", -4.1, 27.6, 3.7, 1.4, 3, 0.6, (238, 238, 244))
    m.box("head", 2.7, 27.6, 3.7, 1.4, 3, 0.6, (238, 238, 244))
    m.box("head", -1.6, 32.8, 2.4, 3.2, 1.4, 1, purple)
    m.box("head", -0.5, 32.9, 2.3, 1, 1.2, 1.2, sh(purple, 0.7))
    m.box("torso", -4.9, 21.6, -2.6, 9.8, 3.4, 5.6, white)
    # The parka: a zip, pockets, a hem; a gold chain across it and gold at the wrists; a book at her hip.
    tw = 9.2
    m.box("torso", -tw / 2, 13.2, 2.3, tw, 8.4, 0.3, white, marks={"north": [(tw / 2 - 0.2, 0, 0.4, 99, (170, 170, 178)), (0.8, 4.6, 2.8, 2.6, (224, 224, 230)), (5.6, 4.6, 2.8, 2.6, (224, 224, 230)), (0, 0, 99, 0.4, (200, 200, 208))]})
    chain(m, "torso", -4.4, 14.4, 4.4, 21.6, 2.6, 7, size=1.2, depth=0.8)
    m.mirror_box("arm_r_low", "arm_l_low", 4.3, 12.8, -2.1, 4.2, 2.2, 4.2, GOLD, marks={f: [(i * 1.1, 0, 0.35, 99, sh(GOLD, 0.55)) for i in range(4)] for f in SIDES})
    m.box("root", -6.6, 8.2, 1.8, 3.2, 4.2, 1.4, (128, 40, 130), marks={"north": [(1, 1.3, 1.2, 1.4, GOLD), (0.25, 0.25, 2.7, 0.35, GOLD), (0.25, 3.6, 2.7, 0.35, GOLD)]})
    # Trousers fading to black, a white stripe down the side; heavy white shoes.
    m.mirror_box("leg_r_low", "leg_l_low", 0.1, 1.8, -1.95, 3.9, 4.9, 3.9, (44, 32, 64))
    m.mirror_box("leg_r", "leg_l", 3.75, 6.6, -0.4, 0.3, 6.4, 0.8, WHITE)
    m.mirror_box("leg_r_low", "leg_l_low", -0.3, 0, -2.3, 4.6, 2.2, 6, (240, 240, 244), marks={f: [(0, 1.5, 99, 0.4, (190, 190, 198))] for f in SIDES})
    # A great white blade.
    w = wb(m)
    w(-0.6, -4, -0.6, 1.2, 5, 1.2, (150, 150, 160))
    w(-2.2, 1, -0.8, 4.4, 1.4, 1.6, (200, 200, 208))
    w(-1.8, 2.4, -0.5, 3.6, 15, 1.0, white, marks={f: [(1.5, 0, 0.6, 99, (190, 190, 200))] for f in ("north", "south")})
    w(-1.2, 17.4, -0.45, 2.4, 2.2, 0.9, white)
    w(-0.6, 19.6, -0.4, 1.2, 1.8, 0.8, (200, 200, 208))
    return m


def lucio():
    red, grey, cream = (128, 26, 34), (176, 180, 188), (240, 234, 216)
    skin = (244, 232, 224)
    # Eyes closed: two calm lines.
    face = [(0.9, 4.0, 2.2, 0.4, (90, 90, 100)), (4.9, 4.0, 2.2, 0.4, (90, 90, 100)), (0.9, 3.0, 2.2, 0.35, (150, 154, 162)), (4.9, 3.0, 2.2, 0.35, (150, 154, 162)), (3.4, 6.3, 1.2, 0.35, (170, 120, 120))]
    m = base("b_lucio", {"skin": skin, "slim": True, "top": red, "sleeve": cream, "inner": WHITE, "tie": (150, 30, 30), "pants": (112, 22, 30), "shoes": (70, 44, 34), "accent": (60, 40, 34),
                         "face": face}, 1.3, head=0.86)
    # Silver hair: a fringe swept aside, a ponytail down past the knees.
    hair(m, grey, back=25, side=26.5, vol=0.5, front=1.6, fringe=((-4.4, 3.4, 3.2), (-0.8, 2.4, 2.0), (1.8, 2.6, 2.8)))
    m.box("head", -1.2, 29, -5.8, 2.4, 2.4, 1.6, sh(grey, 0.85))
    m.bone("ponytail", "head", (0, 29.5, -5.4), rest=(-10, 0, 0))
    m.box("ponytail", -1.3, 14, -6.6, 2.6, 15.5, 1.6, grey)
    m.box("ponytail", -1.0, 6, -6.4, 2.0, 8.4, 1.3, sh(grey, 0.92))
    m.parts["Back"] = ["ponytail"]
    # A red waistcoat with gold buttons and a badge; full white sleeves; a belt.
    tw = 7.0
    m.box("torso", -tw / 2 - 0.05, 13.2, 1.82, tw + 0.1, 8.2, 0.35, red, marks={"north": [(2.2, 1 + i * 1.6, 0.7, 0.7, GOLD) for i in range(4)] + [(4.2, 1 + i * 1.6, 0.7, 0.7, GOLD) for i in range(4)]
                                                                                 + [(5.0, 6.2, 1.5, 1.5, GOLD), (0, 0, 0.3, 99, sh(red, 0.6)), (tw - 0.2, 0, 0.3, 99, sh(red, 0.6))]})
    m.mirror_box("torso", "torso", 2.0, 16, 1.8, 0.5, 8, 0.45, (60, 40, 34))
    m.box("root", -tw / 2 - 0.3, 11.8, -2.1, tw + 0.6, 1.5, 4.2, (60, 40, 34), marks={"north": [(3, 0.2, 1.6, 1.1, GOLD)]})
    m.mirror_box("arm_r", "arm_l", 3.2, 18.4, -1.9, 3.6, 5.4, 3.8, cream)
    m.mirror_box("arm_r_low", "arm_l_low", 3.1, 14.6, -2, 3.8, 3.6, 4, cream)
    m.mirror_box("arm_r_low", "arm_l_low", 3.4, 13.6, -1.4, 2.8, 1, 2.8, sh(cream, 0.85))
    # A red sash hanging at his side; gold on his shoes; a second sword across his back.
    m.bone("coat", "root", (0, 13, 0))
    m.box("coat", -4.6, 2.5, -2.6, 1, 10.5, 4.4, red, marks={"west": [(0, 9.6, 99, 0.6, GOLD)]})
    m.box("coat", -4.4, 2.5, -3.2, 4, 10.5, 0.7, sh(red, 0.9))
    m.mirror_box("leg_r_low", "leg_l_low", 0, 0, -2.1, 4, 0.5, 5.2, GOLD)
    m.bone("sword_back", "torso", (0, 20, -2.6), rest=(0, 0, -24))
    m.box("sword_back", -0.5, -9, -1.2, 1, 16, 0.8, (112, 22, 30), rel=True, marks={f: [(0, 2, 99, 0.5, GOLD), (0, 12, 99, 0.5, GOLD)] for f in SIDES})
    m.box("sword_back", -1.6, 7, -1.4, 3.2, 0.8, 1.2, GOLD, rel=True)
    m.box("sword_back", -0.4, 7.8, -1.2, 0.8, 3.6, 0.8, (60, 40, 34), rel=True)
    # The sword he fights with: a straight blade, a gold hilt.
    w = wb(m)
    w(-0.45, -3, -0.45, 0.9, 4, 0.9, (60, 40, 34))
    w(-1.9, 1, -0.6, 3.8, 0.8, 1.2, GOLD)
    w(-0.6, 0.3, -0.9, 1.2, 0.8, 1.8, GOLD)
    w(-0.6, 1.8, -0.25, 1.2, 16, 0.5, STEEL, marks={f: [(0.45, 0, 0.3, 99, (150, 154, 166))] for f in ("north", "south")})
    w(-0.35, 17.8, -0.22, 0.7, 1.6, 0.45, STEEL)
    return m


def albina():
    white, dark, hairc = (244, 244, 246), (34, 34, 40), (190, 192, 200)
    face = eye_face((170, 170, 180), lash=(120, 122, 132), mouth=(190, 150, 150), narrow=True)
    m = base("b_albina", {"skin": (246, 240, 238), "slim": True, "top": white, "sleeve": white, "pants": white, "shoes": white, "hands": white, "face": face}, 1.3, head=0.86)
    # Silver hair: a straight fringe, long locks at the temples, a high ponytail.
    hair(m, hairc, back=24.5, side=25, vol=0.5, front=1.4, fringe=((-4.4, 8.8, 2.6),))
    m.mirror_box("head", "head", 3.6, 19, 2.2, 1, 9, 1.2, sh(hairc, 1.05))
    m.box("head", -1.3, 32.4, -3.6, 2.6, 2.6, 2.6, hairc)
    m.bone("ponytail", "head", (0, 33, -3.4), rest=(-16, 0, 0))
    m.box("ponytail", -1.2, 20, -5.8, 2.4, 13, 1.5, sh(hairc, 0.92))
    m.parts["Back"] = ["ponytail"]
    # A white suit of armour: black at the waist and thighs, fine gold lines.
    tw = 7.0
    m.box("torso", -tw / 2 - 0.05, 13.2, 1.82, tw + 0.1, 10.6, 0.35, white, marks={"north": [(1.6, 6.6, 3.9, 3.2, dark), (2.9, 3.4, 1.3, 3.2, dark), (2.2, 2.2, 2.7, 0.3, GOLD), (1.6, 6.5, 3.9, 0.25, GOLD),
                                                                                       (3.4, 0, 0.3, 3.4, (200, 200, 208)), (0.6, 1, 1.6, 1.4, (226, 226, 232)), (4.9, 1, 1.6, 1.4, (226, 226, 232))]})
    m.box("torso", -tw / 2 - 0.2, 13.2, -2.0, tw + 0.4, 3, 4, dark, marks={"south": [(0, 0, 99, 0.3, GOLD)]})
    m.mirror_box("arm_r", "arm_l", 3.0, 21.4, -1.8, 3.6, 2.4, 3.6, white, marks={f: [(0, 2, 99, 0.3, GOLD)] for f in SIDES})
    m.mirror_box("arm_r_low", "arm_l_low", 3.3, 13.6, -1.5, 3, 3.4, 3, (232, 232, 238), marks={f: [(0, 0, 99, 0.3, GOLD)] for f in SIDES})
    m.mirror_box("leg_r", "leg_l", 0.05, 8.6, -1.95, 3.9, 4.4, 3.9, dark, marks={"north": [(0, 0, 0.3, 99, GOLD), (3.6, 0, 0.3, 99, GOLD)]})
    m.mirror_box("leg_r", "leg_l", 0.0, 6.4, -2.0, 4, 2.4, 4.1, white)
    m.mirror_box("leg_r_low", "leg_l_low", 0.0, 1.6, -2.0, 4, 5.2, 4, white, marks={"north": [(1.8, 0, 0.3, 99, GOLD)]})
    m.mirror_box("leg_r_low", "leg_l_low", -0.1, 0, -2.1, 4.2, 1.8, 5.6, white, marks={f: [(0, 1.3, 99, 0.3, GOLD)] for f in SIDES})
    # A slab of a sword, dark red, banded in steel, taller than she is: stood on its point at her side, the hilt above her head.
    m.bones["weapon"].rest = (0, 0, 0)
    m.weapon_flip = True
    w = wb(m)
    blade = (112, 22, 28)
    ox = 3.4  # out from the hand, clear of her side
    w(ox - 3.6, -13, -0.6, 7.2, 22, 1.2, blade, marks={f: [(0, 2 + i * 3.6, 99, 0.6, (150, 150, 160)) for i in range(6)] + [(1 + (i * 37 % 5), 1 + i * 2.3, 1.2, 1.0, (160, 40, 46)) for i in range(9)] for f in ("north", "south")})
    w(ox - 2.6, -15, -0.55, 5.2, 2, 1.1, blade)
    w(ox - 1.4, -16.6, -0.5, 2.8, 1.6, 1.0, sh(blade, 0.8))
    w(ox - 0.5, -17.8, -0.4, 1.0, 1.2, 0.8, sh(blade, 0.7))
    w(ox - 2.4, 9, -0.9, 4.8, 1.6, 1.8, (90, 20, 24))
    w(ox - 0.6, 10.6, -0.6, 1.2, 9, 1.2, (60, 30, 30), marks={f: [(0, 1 + i * 1.5, 99, 0.4, (30, 16, 16)) for i in range(5)] for f in SIDES})
    w(ox - 1.3, 19.6, -1.0, 2.6, 2, 2, (90, 20, 24))
    w(ox - 0.5, 21.6, -0.4, 1.0, 1.8, 0.8, sh(blade, 0.8))
    # Her hand on the blade's shoulder.
    w(-0.4, -0.6, -0.7, ox - 3.2 + 0.6, 1.2, 1.4, (150, 150, 160))
    return m


def _tall(fn, legs, torso, thin):
    def build():
        return M.stretch(fn(), legs, torso, thin)
    build.__name__ = fn.__name__
    return build


BOSSES = {"Don Quixote": don_quixote, "Sancho": sancho, "Dulcinea": _tall(dulcinea, 1.1, 1.0, 1.0), "Ricardo": _tall(ricardo, 1.12, 1.05, 1.0),
          "Lei Heng": _tall(lei_heng, 1.3, 1.12, 0.92), "Ahab": _tall(ahab, 1.3, 1.12, 0.92), "Kromer": kromer,
          "Erlking Heathcliff": _tall(erlking_heathcliff, 1.25, 1.1, 0.95), "The Time Ripper": _tall(time_ripper, 1.38, 1.18, 0.86),
          "The Knight of Despair": _tall(knight_of_despair, 1.2, 1.05, 0.95), "Jia Mu": _tall(jia_mu, 1.35, 1.15, 0.9),
          "The Thumb Nursefather - Valencina": _tall(valencina, 1.3, 1.12, 0.9), "The Index Nursefather - Rien": _tall(rien, 1.38, 1.15, 0.85),
          "The Middle Nursefather - Matthias": _tall(matthias, 1.2, 1.08, 1.0), "The Ring Nursefather - Callisto": _tall(callisto, 1.4, 1.18, 0.84),
          "The Middle Apprentice - Kira": _tall(kira, 1.08, 1.0, 1.0), "The Thumb Apprentice - Lucio": _tall(lucio, 1.32, 1.12, 0.88),
          "The Ring Apprentice - Albina": _tall(albina, 1.3, 1.1, 0.86)}


# --- how each boss fights: a clip for every coin of every skill, and the colours of its effects -----------------------

SLASH = ["m_slash", "m_backslash", "m_sweep", "m_slash", "m_spin", "m_backslash"]
PIERCE = ["m_thrust", "m_lowthrust", "m_thrust", "m_lowthrust", "m_thrust"]
BLUNT = ["m_smash", "m_sweep", "m_stomp", "m_smash"]
FISTS = ["m_punch_r", "m_punch_l", "m_kick", "m_punch_r", "m_stomp"]
CAST = ["m_cast", "m_cast2", "m_cast", "m_cast2", "m_cast"]
LANCE = ["m_thrust", "m_sweep", "m_lowthrust", "m_thrust"]

# The model's function name -> (colours, particle, moves by attack kind, [(words in a skill's name, moves)], words that make a skill's last coin a great one).
STYLE = {
    "don_quixote": ((176, 28, 38), GOLD, "CRIMSON_SPORE", {"slash": LANCE, "pierce": LANCE, "blunt": LANCE}, [], ["Eternal Festival"]),
    "sancho": ((200, 30, 40), (246, 208, 84), "CRIMSON_SPORE", {"slash": SLASH, "pierce": LANCE, "blunt": ["m_kick", "m_sweep", "m_stomp"]}, [], ["La Aventura", "Hardblood Arts 11", "Hardblood Arts 2"]),
    "dulcinea": ((150, 80, 220), (240, 230, 198), "WITCH", {"slash": CAST, "pierce": CAST, "blunt": CAST}, [], ["Finale", "Eternity"]),
    "ricardo": (GOLD, (118, 52, 160), "CRIT", {"slash": FISTS, "pierce": FISTS, "blunt": FISTS}, [("Kick", ["m_kick", "m_stomp", "m_kick"]), ("Assemble", CAST), ("COUPO", ["m_smash"])], ["COUPO", "Payback"]),
    "lei_heng": ((190, 40, 50), GOLD, "FLAME", {"slash": SLASH, "pierce": PIERCE, "blunt": BLUNT}, [], ["Blast", "Tanglecleaver", "Tigerslayer"]),
    "ahab": ((255, 170, 60), (228, 226, 216), "FLAME", {"slash": LANCE, "pierce": PIERCE, "blunt": LANCE}, [("Exhale", CAST)], ["Burning Harpoon", "Tempering", "Be Fearless"]),
    "kromer": ((240, 236, 222), (214, 60, 60), "END_ROD", {"slash": SLASH, "pierce": PIERCE, "blunt": ["m_smash"]}, [("Gripping", CAST)], ["Purify", "Claim Your Life", "Sin-skewering"]),
    "erlking_heathcliff": ((150, 40, 44), (30, 26, 34), "LARGE_SMOKE", {"slash": BLUNT, "pierce": PIERCE, "blunt": ["m_smash", "m_sweep", "m_slash", "m_spin"]}, [("Glass Shard", CAST)], ["Wild Hunt", "Dullahan", "Nightmarish", "Fused Blade"]),
    "time_ripper": ((214, 180, 84), (40, 40, 46), "ENCHANTED_HIT", {"slash": ["m_punch_r", "m_punch_l", "m_slash", "m_backslash"], "pierce": ["m_punch_r", "m_punch_l", "m_thrust"], "blunt": FISTS}, [("Time to be Saved", CAST)], ["Running Out of Time", "Extraction"]),
    "knight_of_despair": ((70, 120, 236), (236, 244, 255), "END_ROD", {"slash": SLASH, "pierce": ["m_cast", "m_thrust", "m_cast2", "m_lowthrust", "m_cast"], "blunt": CAST}, [], ["Arcana", "River", "Violent Cascade"]),
    "jia_mu": ((226, 190, 80), (246, 242, 230), "WAX_ON", {"slash": CAST, "pierce": CAST, "blunt": CAST}, [], ["Hun", "You"]),
    "valencina": ((170, 30, 40), GOLD, "FLAME", {"slash": SLASH, "pierce": PIERCE, "blunt": BLUNT}, [("Blast a Hole", ["m_shoot"])], ["Disposal", "Blast a Hole", "Elefante"]),
    "rien": ((236, 236, 240), GOLD, "ENCHANTED_HIT", {"slash": ["m_slash", "m_backslash", "m_sweep", "m_spin"], "pierce": ["m_punch_r", "m_thrust", "m_lowthrust", "m_punch_l"], "blunt": ["m_stomp", "m_punch_r", "m_kick", "m_smash"]}, [], ["Furioso", "Crush Flat", "Penetrate the Memory", "Cut the Form"]),
    "matthias": ((170, 80, 230), GOLD, "FLAME", {"slash": SLASH, "pierce": PIERCE, "blunt": ["m_stomp", "m_kick", "m_punch_r", "m_stomp"]}, [("Punt", ["m_kick"]), ("Unbox", ["m_smash", "m_sweep", "m_slash", "m_spin"]), ("Heat", ["m_slash", "m_sweep", "m_smash"]),
                                                                                                                                             ("Break Ya", ["m_smash", "m_backslash", "m_sweep"])], ["Unbox", "Heat", "Stamp of Vengeance"]),
    "callisto": ((170, 30, 36), (246, 244, 238), "CRIMSON_SPORE", {"slash": SLASH, "pierce": PIERCE, "blunt": BLUNT}, [("Installation Art", CAST)], ["Closing Time", "Tibia"]),
    "kira": ((150, 90, 220), (244, 244, 246), "SNOWFLAKE", {"slash": SLASH, "pierce": PIERCE, "blunt": ["m_punch_r", "m_kick", "m_punch_l", "m_stomp"]}, [], ["Violet", "Crossing Dervish"]),
    "lucio": ((170, 30, 40), (196, 200, 210), "CRIT", {"slash": SLASH, "pierce": PIERCE, "blunt": BLUNT}, [], ["Cervo", "Cacciatore"]),
    "albina": ((150, 26, 32), (244, 244, 246), "CRIMSON_SPORE", {"slash": ["m_smash", "m_sweep", "m_slash", "m_spin"], "pierce": PIERCE, "blunt": BLUNT}, [], ["Repressed Flesh", "Fascia"]),
}


def style(model, fn_name, data):
    """Sets a boss model's effect colours and, for each of its skills, the clip of every coin."""
    if fn_name not in STYLE or data is None:
        return
    col, col2, particle, kinds, named, greats = STYLE[fn_name]
    model.fx = {"color": col[0] << 16 | col[1] << 8 | col[2], "color2": col2[0] << 16 | col2[1] << 8 | col2[2], "particle": particle}
    model.skills = {}
    for sk in data["skills"]:
        if sk["type"] not in ("slash", "pierce", "blunt"):
            continue
        moves = kinds[sk["type"]]
        for words, special in named:
            if words.lower() in sk["name"].lower():
                moves = special
        coins = max(1, sk["coins"])
        clips = [moves[i % len(moves)] for i in range(coins)]
        great = any(w.lower() in sk["name"].lower() for w in greats)
        if coins >= 3 or great:
            clips[-1] = "m_flourish" if clips[-1] not in ("m_cast", "m_cast2", "m_shoot") else "m_cast2" if clips[-1] != "m_shoot" else "m_shoot"
        model.skills[sk["name"]] = {"clips": clips, "great": great}
