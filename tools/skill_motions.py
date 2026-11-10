"""Body motions for the weapon skills, as studio animations (paper/src/main/resources/animations).

Each skill gets a rig that stands in for its actor (the player, or the
cinematic stand-in) and moves its arms, legs and body on the same ticks the
skill code strikes, which were measured from the in-game scenes. Moves are
built from a few blocks (slash, thrust, overhead, kick, stomp, dash, leap,
two-handed hammer and scythe swings). The plugin copies these files to
plugins/IndexPrescript/animations/ and keeps them updated while they still
say "auto": true; a file saved from the studio drops that flag and is left
alone. Run from the repository root: python3 tools/skill_motions.py
"""
import json
import math
import os

OUT = "paper/src/main/resources/animations"
VERSION = 1
BONES = ["head", "body", "right_arm", "left_arm", "right_leg", "left_leg"]


def norm(v):
    length = math.sqrt(sum(c * c for c in v)) or 1.0
    return [c / length for c in v]


def aim(d, twist=0.0):
    """Euler XYZ (degrees) that turns a limb hanging straight down to point along d (right, up, forward)."""
    dx, dy, dz = norm(d)
    z = math.asin(max(-1.0, min(1.0, dx)))
    cz = math.cos(z) or 1e-6
    x = math.atan2(-dz / cz, -dy / cz)
    return [round(math.degrees(x), 1), round(twist, 1), round(math.degrees(z), 1)]


def mirror(d):
    return [-d[0], d[1], d[2]]


# Ready stance: the weapon held forward and low.
STANCE = {
    "head": [0, 0, 0], "body": [0, 0, 0],
    "right_arm": aim([0.15, -0.8, 0.55]), "left_arm": aim([-0.15, -0.95, 0.2]),
    "right_leg": aim([0, -1, -0.12]), "left_leg": aim([0, -1, 0.15]),
}


class Motion:
    def __init__(self, name, length, weapon_preview, title):
        self.name, self.length, self.preview, self.title = name, length, weapon_preview, title
        self.tracks = {b: [] for b in BONES}
        self.root = [{"t": 0, "pos": [0, 0, 0], "rot": [0, 0, 0], "scale": [1, 1, 1], "ease": "linear"}]
        self.events = []
        for b in BONES:
            self.key(b, 0, STANCE[b])

    # -- keys ---------------------------------------------------------------------------
    def key(self, bone, t, rot, ease="linear", pos=None):
        t = max(0, min(self.length, int(round(t))))
        keys = [k for k in self.tracks[bone] if k["t"] != t]
        keys.append({"t": t, "pos": pos or [0, 0, 0], "rot": [round(r, 1) for r in rot], "scale": [1, 1, 1], "ease": ease})
        self.tracks[bone] = sorted(keys, key=lambda k: k["t"])

    def sample(self, bone, t):
        keys = self.tracks[bone]
        a = keys[0]
        b = None
        for k in keys:
            if k["t"] <= t:
                a = k
            if k["t"] >= t and b is None:
                b = k
        if b is None or b["t"] == a["t"]:
            return list(a["rot"])
        f = (t - a["t"]) / (b["t"] - a["t"])
        return [a["rot"][i] + (b["rot"][i] - a["rot"][i]) * f for i in range(3)]

    def begin(self, t, bones):
        """A new move takes these bones from tick t: later keys go, and the pose at t is held until then."""
        t = max(0, int(round(t)))
        for b in bones:
            here = self.sample(b, t)
            self.tracks[b] = [k for k in self.tracks[b] if k["t"] < t]
            self.key(b, t, here)

    def pose(self, t, ease="linear", **bones):
        for b, rot in bones.items():
            self.key(b, t, rot, ease)

    def recover(self, t, bones=None, ease="inout"):
        for b in bones or BONES:
            self.key(b, t, STANCE[b], ease)

    def root_key(self, t, pos=(0, 0, 0), rot=(0, 0, 0), ease="linear"):
        t = max(0, min(self.length, int(round(t))))
        self.root = sorted([k for k in self.root if k["t"] != t] + [{"t": t, "pos": list(pos), "rot": list(rot), "scale": [1, 1, 1], "ease": ease}],
                           key=lambda k: k["t"])

    # -- moves (t is the tick the blow lands) ---------------------------------------------
    def slash(self, t, back=False, rising=False, big=False, lead=6):
        """A horizontal cut: forehand from the right across to the left, or backhand from the left."""
        s = -1 if back else 1
        self.begin(t - lead, BONES)
        lift = -0.6 if rising else 0.45
        self.pose(t - lead + 1, "out",
                  right_arm=aim([0.9 * s if not back else -0.75, lift, -0.15 if not back else 0.35]),
                  left_arm=aim([-0.5, -0.8, 0.2]), body=[0, 30 * s, 0], head=[0, -10 * s, 0],
                  right_leg=aim([0, -1, -0.2]), left_leg=aim([0, -1, 0.2]))
        end_y = 0.75 if rising else 0.0
        self.pose(t, "in",
                  right_arm=aim([-0.75 * s if not back else 0.9, end_y, 0.65 if not back else 0.4]),
                  body=[8 if big else 4, -30 * s, 0], head=[0, 10 * s, 0],
                  left_arm=aim([-0.6, -0.6, -0.3]), left_leg=aim([0, -0.95, 0.35]), right_leg=aim([0, -0.95, -0.3]))
        self.pose(t + 3, "out", right_arm=aim([-0.9 * s if not back else 0.95, end_y - 0.3, 0.25]), body=[10 if big else 6, -38 * s, 0])
        self.recover(t + (14 if big else 10))

    def thrust(self, t, lead=5):
        self.begin(t - lead, BONES)
        self.pose(t - lead + 1, "out", right_arm=aim([0.3, -0.45, -0.8]), body=[0, 25, 0], left_arm=aim([-0.35, -0.4, 0.8]),
                  right_leg=aim([0, -1, -0.25]), left_leg=aim([0, -0.95, 0.3]))
        self.pose(t, "in", right_arm=aim([0.02, 0.05, 1]), body=[12, -18, 0], left_arm=aim([-0.3, -0.7, -0.6]), head=[-8, 0, 0],
                  right_leg=aim([0, -0.9, -0.45]), left_leg=aim([0, -0.85, 0.5]))
        self.pose(t + 4, "out", right_arm=aim([0.05, 0.0, 1]))
        self.recover(t + 11)

    def overhead(self, t, two_hands=False, lead=8, big=False):
        self.begin(t - lead, BONES)
        up = [0.1, 1, -0.35]
        self.pose(t - lead + 2, "out", right_arm=aim(up), body=[-10, 0, 0], head=[-15, 0, 0],
                  left_arm=aim([0.35, 1, -0.35] if two_hands else [-0.5, 0.2, 0.4]))
        self.pose(t - 2, "linear", right_arm=aim([0.1, 1, -0.5]), left_arm=aim([0.35, 1, -0.5] if two_hands else [-0.5, 0.2, 0.4]))
        down = [0.05, -0.55, 0.85]
        self.pose(t, "in", right_arm=aim(down), body=[22 if big else 16, 0, 0], head=[5, 0, 0],
                  left_arm=aim([0.4, -0.55, 0.85] if two_hands else [-0.5, -0.8, -0.3]),
                  right_leg=aim([0, -0.95, -0.35]), left_leg=aim([0, -0.85, 0.5]))
        self.recover(t + (16 if big else 11))

    def kick(self, t, chamber=6):
        self.begin(t - chamber, ["right_leg", "left_leg", "body", "right_arm", "left_arm"])
        self.pose(t - chamber + 2, "out", right_leg=aim([0, -0.2, 1]), body=[-10, 0, 0], right_arm=aim([0.6, -0.5, -0.3]), left_arm=aim([-0.6, -0.3, 0.3]))
        self.pose(t, "in", right_leg=aim([0, 0.25, 1]), body=[-22, 0, 0], left_leg=aim([0, -1, -0.1]))
        self.recover(t + 10)

    def stomp(self, t, big=False):
        self.begin(t - 7, BONES)
        self.pose(t - 5, "out", right_leg=aim([0, -0.3, 0.9]), body=[-6, 0, 0], right_arm=aim([0.5, 0.1, 0.3]), left_arm=aim([-0.7, 0.1, 0.2]))
        self.pose(t, "in", right_leg=aim([0, -1, 0.15]), body=[18 if big else 12, 0, 0], head=[10, 0, 0],
                  right_arm=aim([0.5, -0.6, 0.3]), left_arm=aim([-0.7, -0.4, 0.1]))
        self.recover(t + (14 if big else 9))

    def dash(self, t0, t1):
        """Leans into a dash between t0 and t1 (the actor itself is moved by the skill)."""
        self.begin(t0, BONES)
        self.pose(t0 + 1, "out", body=[30, 0, 0], head=[-25, 0, 0], right_arm=aim([0.2, -0.55, -0.8]), left_arm=aim([-0.2, -0.55, -0.8]),
                  right_leg=aim([0, -0.75, -0.65]), left_leg=aim([0, -0.8, 0.6]))
        self.pose(t1, "linear", body=[25, 0, 0])

    def crouch(self, t0, t1):
        self.begin(t0, BONES)
        self.pose(t0 + 2, "out", body=[28, 0, 0], head=[-20, 0, 0], right_arm=aim([0.3, -0.6, -0.6]), left_arm=aim([-0.3, -0.6, -0.6]),
                  right_leg=aim([0, -0.9, 0.45]), left_leg=aim([0, -0.9, 0.45]))
        self.root_key(t0, (0, 0, 0))
        self.root_key(t0 + 2, (0, -0.25, 0), ease="out")
        self.root_key(t1, (0, -0.25, 0))
        self.root_key(t1 + 2, (0, 0, 0), ease="out")

    def air(self, t0, t1):
        """In the air: knees tucked, arms up."""
        self.begin(t0, BONES)
        self.pose(t0 + 3, "out", right_leg=aim([0, -0.35, 0.9]), left_leg=aim([0, -0.7, -0.7]), body=[-8, 0, 0],
                  left_arm=aim([-0.7, 0.6, 0.1]), head=[-15, 0, 0])
        self.pose(t1, "linear", right_leg=aim([0, -0.5, 0.8]))

    def raise_arm(self, t0, t1, hold=True):
        """Raises the weapon skyward (a command, lightning called down)."""
        self.begin(t0, BONES)
        self.pose(t0 + 4, "out", right_arm=aim([0.15, 1, 0.1]), body=[-6, 0, 0], head=[-25, 0, 0], left_arm=aim([-0.4, -0.9, 0.1]))
        if hold:
            self.pose(t1, "linear", right_arm=aim([0.15, 1, 0.15]))
        self.recover(t1 + 6)

    def power(self, t0, t1):
        """Gathering power: wide stance, weapon low and back, arms tense."""
        self.begin(t0, BONES)
        self.pose(t0 + 5, "out", body=[12, 15, 0], right_arm=aim([0.55, -0.7, -0.45]), left_arm=aim([-0.6, -0.5, 0.4]), head=[-10, -12, 0],
                  right_leg=aim([0.2, -0.95, -0.2]), left_leg=aim([-0.2, -0.95, 0.25]))
        self.pose(t1, "linear", body=[14, 18, 0])

    def roar(self, t0, t1):
        self.begin(t0, BONES)
        self.pose(t0 + 3, "out", body=[-14, 0, 0], head=[-35, 0, 0], right_arm=aim([0.85, -0.25, 0.45]), left_arm=aim([-0.85, -0.25, 0.45]),
                  right_leg=aim([0.2, -1, 0]), left_leg=aim([-0.2, -1, 0]))
        self.pose(t1, "linear", head=[-30, 0, 0])
        self.recover(t1 + 5)

    def reach(self, t0, t1):
        """Reaching out with the empty hand (pulling a chain, catching)."""
        self.begin(t0, ["right_arm", "left_arm", "body"])
        self.pose(t0 + 3, "out", right_arm=aim([0.1, 0.1, 1]), left_arm=aim([-0.2, -0.3, 0.6]), body=[6, 0, 0])
        self.pose(t1, "in", right_arm=aim([0.4, -0.3, 0.5]))

    def arm_angle(self, t, angle, two_hands=True, ease="linear"):
        """Both arms along a weapon held at angle (degrees from upright, positive forward), pivoting at the shoulder."""
        x = -180 + angle
        rot = [x, 0, 4]
        self.key("right_arm", t, rot, ease)
        if two_hands:
            self.key("left_arm", t, [x, 0, 22], ease)
        lean = max(-12, min(22, (angle - 60) * 0.25))
        self.key("body", t, [lean, 0, 0], ease)

    def hammer(self, raise_from, slam_at):
        """Renoir's great hammer (RenoirSkill.swingHammer): up behind the head, then over the shoulder onto the ground."""
        self.begin(raise_from, BONES)
        # Hammer angles there are positive backward; here positive is forward.
        for i in range(0, 9, 2):
            u = min(1.0, i / 8.0)
            eased = 1 - (1 - u) * (1 - u)
            self.arm_angle(raise_from + i, -(-100 + 160 * eased))
        self.pose(raise_from + 8, "linear", head=[-12, 0, 0], left_leg=aim([0, -0.95, 0.3]), right_leg=aim([0, -1, -0.2]))
        down = slam_at - 3
        self.arm_angle(down, -60)
        for i, a in enumerate([20, -40, -95, -130]):
            self.arm_angle(down + i, -a, ease="in")
        self.pose(slam_at, "in", head=[12, 0, 0], left_leg=aim([0, -0.85, 0.5]), right_leg=aim([0, -0.95, -0.35]))
        self.arm_angle(slam_at + 6, 130)
        self.recover(slam_at + 16)

    def scythe_chop(self, raise_at, slam_at, hit_at, end_at):
        """Furioso's last coin: the scythe raised overhead and back, slammed down in front (Furioso.chop)."""
        self.begin(raise_at, BONES)
        steps = slam_at - raise_at
        for i in range(0, steps + 1, 3):
            u = i / steps
            self.arm_angle(raise_at + i, 20 + (-150 - 20) * math.sin(u * math.pi / 2), ease="out")
        self.arm_angle(slam_at, -150)
        self.arm_angle(hit_at, 105, ease="in")
        self.pose(hit_at, "in", left_leg=aim([0, -0.8, 0.55]), right_leg=aim([0, -0.95, -0.4]), head=[10, 0, 0])
        self.arm_angle(end_at, 105)
        self.recover(end_at + 10)

    def sound(self, t, sound, pitch=1.0, volume=1.0):
        self.events.append({"t": t, "type": "sound", "sound": sound, "volume": volume, "pitch": pitch})

    # -- output ---------------------------------------------------------------------------
    def json(self):
        bones = {b: ks for b, ks in self.tracks.items()}
        return {
            "name": self.name, "title": self.title, "auto": True, "version": VERSION, "length": self.length, "follow": False,
            "parts": [
                {"id": "actor", "kind": "rig", "skin": "player", "hide_player": True, "drive_player": False, "keys": self.root, "bones": bones},
                {"id": "weapon", "kind": "item", "item": "@mainhand", "transform": "thirdperson_righthand", "parent": "actor.right_arm",
                 "preview": self.preview, "keys": [{"t": 0, "pos": [0.0625, -0.625, 0.125], "rot": [90, 0, 0], "scale": [1, 1, 1], "ease": "linear"}]},
            ],
            "events": self.events,
        }


MOTIONS = []


def motion(name, length, preview, title):
    m = Motion(name, length, preview, title)
    MOTIONS.append(m)
    return m


# --- Laevatein (LaevateinSkill, Laevatein.Release) -------------------------------------
LAEV = "laevatein_release_3_3d"

m = motion("skill_laevatein_stomp", 48, LAEV, "레바테인: 짓밟기")
m.dash(2, 6)
m.stomp(9)
m.stomp(31, big=True)

m = motion("skill_laevatein_heated", 110, LAEV, "레바테인: 후끈해지지 않나?!")
m.raise_arm(0, 6)
m.slash(16)
m.thrust(41)
m.slash(60, back=True)
m.overhead(88, big=True)

m = motion("skill_laevatein_melt", 115, LAEV, "레바테인: 슬슬 녹아 죽을 차례다")
m.power(0, 12)
m.slash(16)
m.slash(42, back=True)
m.thrust(48, lead=3)
m.slash(92, big=True)

m = motion("skill_laevatein_stab", 160, LAEV, "레바테인: 찌르기")
m.slash(9)
m.slash(21, back=True)
m.thrust(47)
m.overhead(68)
m.power(90, 118)
m.slash(126, big=True)
m.slash(134, back=True, big=True, lead=4)

m = motion("skill_laevatein_rule_violation", 104, LAEV, "레바테인: 규칙 위반")
for base in (0, 52):
    m.slash(base + 15)
    m.slash(base + 36, back=True)

m = motion("skill_laevatein_execution", 250, LAEV, "레바테인: 즉결처형")
m.dash(1, 5)
m.slash(11, rising=True, lead=5)
m.slash(26, back=True, big=True)
m.kick(44, chamber=6)
m.overhead(68, lead=12)             # raised at 56, hurled at 68
m.reach(70, 80)                     # the hand empty after the throw
m.recover(84)
m.air(90, 96)                       # backstep hop
m.recover(100)
m.crouch(128, 139)
# The drop kick, feet first: the stand-in faces away from the target, so the body tips forward.
m.begin(139, BONES)
m.pose(140, "out", right_leg=aim([0, -0.2, -1]), left_leg=aim([0, -0.5, -0.85]), right_arm=aim([0.6, 0.6, 0.2]), left_arm=aim([-0.6, 0.6, 0.2]))
m.root_key(139, (0, 0, 0), (0, 0, 0))
m.root_key(141, (0, 0.6, 0), (75, 0, 0), "out")
m.root_key(147, (0, 0.4, 0), (85, 0, 0))
m.root_key(151, (0, 0, 0), (0, 0, 0), "out")
m.recover(152)
m.reach(186, 196)                   # the chains pull the blade back
m.slash(199, big=True, lead=3)
m.power(214, 230)
m.recover(240)

for stage, length, title, moves in (
        (1, 110, "포장 하나 풀기", [("slash", 20, {}), ("slash", 36, {"back": True}), ("raise", 52, 64), ("slash", 90, {"big": True})]),
        (2, 172, "두 번째 포장", [("slash", 18, {}), ("slash", 45, {"back": True}), ("slash", 60, {}), ("slash", 88, {"rising": True}),
                                  ("power", 110, 128), ("overhead", 132, {"big": True})]),
        (3, 175, "여기까지 꺼내게 하다니", [("raise", 2, 10), ("slash", 29, {}), ("raise", 64, 72), ("slash", 80, {"back": True}),
                                         ("power", 112, 130), ("overhead", 135, {"big": True})])):
    m = motion("skill_laevatein_release_%d" % stage, length, LAEV, "레바테인 해방 %d단계: %s" % (stage, title))
    for move in moves:
        if move[0] == "slash":
            m.slash(move[1], **move[2])
        elif move[0] == "overhead":
            m.overhead(move[1], **move[2])
        elif move[0] == "raise":
            m.raise_arm(move[1], move[2])
        elif move[0] == "power":
            m.power(move[1], move[2])

# --- Rien (RienSkill): the wielder flashes beside the foe 3 ticks before each coin ---------
RIEN = {
    "gluttony_blunt": ([18, 52], 80, "blunt"), "gluttony_pierce": ([13, 38], 60, "pierce"), "gluttony_slash": ([28, 54], 80, "slash"),
    "pride_blunt": ([25, 45, 70], 95, "blunt"), "pride_pierce": ([19, 40, 64], 85, "pierce"), "pride_slash": ([17, 38, 62], 85, "slash"),
    "gloom_blunt": ([23, 49, 72, 95], 120, "blunt"), "gloom_pierce": ([46, 74, 95, 115], 135, "pierce"),
    "gloom_slash": ([42, 65, 82, 106], 135, "slash"),
}
for key, (coins, length, kind) in RIEN.items():
    m = motion("skill_rien_" + key, length, "caduceus_greatsword_3d", "뤼엔: " + key)
    if coins[0] > 12:
        m.power(0, coins[0] - 8)
    for i, t in enumerate(coins):
        last = i == len(coins) - 1
        m.dash(t - 4, t - 3)
        if kind == "blunt":
            m.overhead(t, two_hands=last, lead=3, big=last)
        elif kind == "pierce":
            m.thrust(t, lead=3)
        else:
            m.slash(t, back=i % 2 == 1, lead=3, big=last)

# --- Furioso (Furioso): eight strikes, the gathering, the scythe chop ---------------------
m = motion("skill_furioso", 250, "caduceus_scythe_3d", "Furioso-Replica")
cycle = ["slash", "thrust", "slash_back", "overhead"]
for i, t in enumerate([12, 27, 45, 59, 79, 96, 112, 128]):
    m.dash(t - 4, t - 3)
    move = cycle[i % 4]
    if move == "slash":
        m.slash(t, lead=3)
    elif move == "slash_back":
        m.slash(t, back=True, lead=3)
    elif move == "thrust":
        m.thrust(t, lead=3)
    else:
        m.overhead(t, lead=3)
m.power(136, 200)
m.scythe_chop(204, 213, 216, 232)

# --- Renoir (RenoirSkill) ---------------------------------------------------------------
HAM = "renoir_hammer_3d"
m = motion("skill_renoir_nail", 56, HAM, "르누아르: 못박히라")
m.slash(14, big=True)
m.hammer(24, 34)

m = motion("skill_renoir_born", 72, HAM, "르누아르: 못생기라")
m.overhead(18)
m.crouch(26, 30)
m.air(30, 34)
m.hammer(34, 46)

m = motion("skill_renoir_sever", 137, HAM, "르누아르: 탄생을 끊으리라")
m.raise_arm(4, 12)
m.slash(27)
m.dash(44, 47)
m.slash(53, back=True)
m.crouch(74, 78)
m.air(78, 82)
m.hammer(82, 102)

m = motion("skill_renoir_again", 180, HAM, "르누아르: 거듭 내리쳐")
m.raise_arm(14, 26)
m.slash(44, big=True)
m.raise_arm(56, 62, hold=False)
m.dash(68, 71)
m.slash(75, back=True, big=True)
m.crouch(88, 99)
m.air(99, 118)
m.hammer(118, 140)

# --- Mimicry (MimicrySkill) -------------------------------------------------------------
MIM = "mimicry_3d"
m = motion("skill_mimicry_vertical", 46, MIM, "미미크리: 종베기")
for t in (10, 22):
    m.dash(t - 4, t - 3)
    m.overhead(t, two_hands=True, lead=3)

m = motion("skill_mimicry_stab", 46, MIM, "미미크리: 찌르기")
for t in (8, 16, 24):
    m.dash(t - 4, t - 3)
    m.thrust(t, lead=3)

m = motion("skill_mimicry_horizontal", 50, MIM, "미미크리: 횡베기")
for i, t in enumerate((12, 26)):
    m.dash(t - 4, t - 3)
    m.slash(t, back=i == 1, lead=4)

m = motion("skill_mimicry_roar", 44, MIM, "미미크리: 기합")
m.roar(0, 6)
m.dash(16, 17)
m.slash(20, big=True, lead=3)

m = motion("skill_mimicry_charge", 40, MIM, "미미크리: 돌진")
m.crouch(0, 3)
m.dash(4, 13)
m.slash(14, big=True, lead=2)
m.dash(15, 17)
m.recover(24)

m = motion("skill_mimicry_great_vertical", 56, "mimicry_ego_3d", "미미크리: 대절단-세로")
m.begin(2, BONES)
for t in range(2, 17, 2):
    m.arm_angle(t, 20 - (t - 2) * 13.5, ease="out")   # raised high as the mist climbs
m.arm_angle(18, -170)
m.arm_angle(24, 115, ease="in")
m.pose(24, "in", left_leg=aim([0, -0.8, 0.55]), right_leg=aim([0, -0.95, -0.4]), head=[12, 0, 0])
m.arm_angle(32, 110)
m.recover(44)

m = motion("skill_mimicry_great_horizontal", 60, "mimicry_ego_3d", "미미크리: 대절단-가로")
m.begin(0, BONES)
m.pose(6, "out", body=[10, 70, 0], right_arm=aim([0.95, -0.1, -0.35]), left_arm=aim([0.7, -0.1, -0.1]), head=[0, -40, 0],
       right_leg=aim([0.25, -0.95, -0.2]), left_leg=aim([-0.25, -0.95, 0.3]))
m.pose(24, "linear", body=[14, 80, 0])
m.pose(28, "in", body=[10, -70, 0], right_arm=aim([-0.95, 0.05, 0.3]), left_arm=aim([-0.7, 0.0, 0.6]), head=[0, 30, 0])
m.root_key(24, (0, 0, 0), (0, 0, 0))
m.root_key(30, (0, 0, 0), (0, -360, 0), "out")
m.root_key(31, (0, 0, 0), (0, 0, 0), "step")
m.recover(42)


def main():
    os.makedirs(OUT, exist_ok=True)
    for f in os.listdir(OUT):
        if f.startswith("skill_") and f.endswith(".json"):
            os.remove(os.path.join(OUT, f))
    names = []
    for mo in MOTIONS:
        with open(os.path.join(OUT, mo.name + ".json"), "w", encoding="utf-8") as fh:
            json.dump(mo.json(), fh, ensure_ascii=False, separators=(",", ":"))
        names.append(mo.name)
    with open(os.path.join(OUT, "index.txt"), "w") as fh:
        fh.write("\n".join(names) + "\n")
    print(len(names), "motions")


if __name__ == "__main__":
    main()
