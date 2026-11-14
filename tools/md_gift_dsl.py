"""A short language for what each E.G.O Gift does here (md_gift_specs.py), turned
into the effects the plugin plays (MdGifts.java) and into Korean text that says
exactly that.

A spec is clauses separated by ';'. A clause is

    trigger: act args [@condition ...] [!limit]

Numbers may differ by enhancement level: "3/4/5" is 3, then 4 at +, 5 at ++.

Triggers: combat_start turn_start turn_end hit crit clash_win clash_lose kill
  stagger hurt ally_down shop floor floor_clear reward always
Acts:
  inflict KW POT CNT [to]    a status on enemies (to: target, all, random, spread, attacker)
  gain KW POT CNT [to]       Poise/Charge (or another keyword) on allies (to: self, all)
  buff NAME N [to]           haste power dmgup protect offense defense (until next turn)
  debuff NAME N [to]         offense_down defense_down fragile bind paralysis on enemies
  dmg PCT                    damage dealt +PCT% (always, when its conditions hold)
  taken PCT                  damage taken +PCT% (negative: less)
  clash N                    clash power +N (skills)
  heal PCT% | heal N [to]    heal HP (percent of max, or the original's HP: a tenth here)
  sp N [to]                  Sanity
  cost N | costpct N         Cost now / more Cost from battles (%)
  crit X                     critical hit multiplier x X
  burst [to]                 Tremor Burst
  maxcharge N                max Charge +N
  fixed PCT [to]             fixed damage, PCT% of the target's max HP
  lifesteal PCT              heal PCT% of the damage dealt
  shopdiscount PCT           shop prices -PCT%
Conditions: has:KW notstaggered staggered kind:K sin:S hp<N hp>N thp<N thp>N kw>=KW:N chance:N
Limits: !turn N  !combat N
"""
import re

KW_KO = {"burn": "화상", "bleed": "출혈", "tremor": "진동", "rupture": "파열", "sinking": "침잠", "poise": "호흡", "charge": "충전"}
SIN_KO = {"wrath": "분노", "lust": "색욕", "sloth": "나태", "gluttony": "탐식", "gloom": "우울", "pride": "오만", "envy": "질투"}
KIND_KO = {"slash": "참격", "pierce": "관통", "blunt": "타격"}
TRIG_KO = {"combat_start": "전투 시작 시", "turn_start": "턴 시작 시", "turn_end": "턴 종료 시", "hit": "적중 시", "crit": "치명타 시",
           "clash_win": "합 승리 시", "clash_lose": "합 패배 시", "kill": "적 처치 시", "stagger": "적을 흐트러뜨렸을 때", "hurt": "피격 시",
           "ally_down": "아군이 쓰러지면", "shop": "상점 입장 시", "floor": "새 층에 들어설 때", "floor_clear": "층을 클리어하면",
           "reward": "전투 승리 시", "always": ""}
BUFF_KO = {"haste": "신속", "power": "공격 위력 증가", "dmgup": "피해량 증가", "protect": "보호", "offense": "공격 레벨 증가", "defense": "방어 레벨 증가"}
BUFF_ID = {"haste": "haste", "power": "power", "dmgup": "damage", "protect": "protection", "offense": "offense_level", "defense": "defense_level"}
DEBUFF_KO = {"offense_down": "공격 레벨 감소", "defense_down": "방어 레벨 감소", "fragile": "취약", "bind": "속박", "paralysis": "마비"}
DEBUFF_ID = {"offense_down": "offense_level_down", "defense_down": "defense_level_down", "fragile": "fragile", "bind": "bind", "paralysis": "paralysis"}
TO_ENEMY_KO = {"target": "대상에게", "all": "적 전원에게", "random": "무작위 적 1명에게", "spread": "적 전체에 무작위로 나누어 총", "attacker": "공격한 적에게"}
TO_ENEMY_ID = {"target": "target", "all": "all_enemies", "random": "random_enemy", "spread": "random_enemies", "attacker": "attacker"}
TO_ALLY_KO = {"self": "", "all": "아군 전원이 "}
TO_ALLY_ID = {"self": "self", "all": "all_allies"}


def values(tok, levels):
    """'3/4/5' -> [3, 4, 5] (padded to the number of levels with the last one)."""
    parts = [float(x) for x in tok.rstrip("%").split("/")]
    while len(parts) < levels:
        parts.append(parts[-1])
    return parts


def fmt(x):
    return ("%d" % x) if float(x).is_integer() else ("%g" % x)


def parse(spec, levels):
    """Returns, for each level, (effects, Korean lines)."""
    out = [([], []) for _ in range(levels)]
    for clause in [c.strip() for c in spec.split(";") if c.strip()]:
        trig, _, rest = clause.partition(":")
        trig = trig.strip()
        assert trig in TRIG_KO, ("trigger", trig, spec)
        words = rest.split()
        conds = [w[1:] for w in words if w.startswith("@")]
        limit = None
        if "!" in rest:
            lw = rest[rest.index("!") + 1:].split()
            limit = (lw[0], int(lw[1]))
        args = [w for w in words if not w.startswith("@") and not w.startswith("!")]
        if limit:
            args = args[:len(args) - 1] if args and args[-1] == str(limit[1]) else args
        act = args[0]
        for lv in range(levels):
            e, ko = effect(trig, act, args[1:], conds, limit, lv, levels)
            if e is None:
                continue
            out[lv][0].append(e)
            out[lv][1].append(ko)
    return out


def cond_ko(conds, enemy_to=None):
    """Korean for the conditions: (clause before the effect, chance prefix, enemy phrase).
    Conditions on enemies become part of who gets the effect when it goes to enemies."""
    parts, pre, enemy = [], "", []
    for c in conds:
        if c.startswith("has:"):
            enemy.append(("%s이 있는" % KW_KO[c[4:]], "대상에게 %s이 있으면" % KW_KO[c[4:]]))
        elif c == "staggered":
            enemy.append(("흐트러진", "대상이 흐트러져 있으면"))
        elif c == "notstaggered":
            enemy.append(("흐트러지지 않은", "대상이 흐트러지지 않았으면"))
        elif c.startswith("thp<"):
            enemy.append(("체력 %s%% 이하인" % c[4:], "대상의 체력이 %s%% 이하면" % c[4:]))
        elif c.startswith("thp>"):
            enemy.append(("체력 %s%% 이상인" % c[4:], "대상의 체력이 %s%% 이상이면" % c[4:]))
        elif c.startswith("kw>="):
            kw, n = c[4:].split(":")
            if kw in ("poise", "charge"):
                parts.append("자신의 %s이 %s 이상이면" % (KW_KO[kw] if kw == "poise" else "충전 횟수", n) if n != "1" else "자신에게 %s이 있으면" % KW_KO[kw])
            else:
                enemy.append(("%s 위력 %s 이상인" % (KW_KO[kw], n), "대상의 %s 위력이 %s 이상이면" % (KW_KO[kw], n)) if n != "1"
                             else ("%s이 있는" % KW_KO[kw], "대상에게 %s이 있으면" % KW_KO[kw]))
        elif c.startswith("kind:"):
            parts.append("%s 공격이면" % KIND_KO[c[5:]])
        elif c.startswith("sin:"):
            parts.append("%s 속성 무기면" % SIN_KO[c[4:]])
        elif c.startswith("hp<"):
            parts.append("자신의 체력이 %s%% 이하면" % c[3:])
        elif c.startswith("hp>"):
            parts.append("자신의 체력이 %s%% 이상이면" % c[3:])
        elif c.startswith("chance:"):
            pre = "%s%% 확률로 " % c[7:]
        else:
            raise ValueError(c)
    return parts, pre, enemy


def cond_id(conds):
    out = {}
    for c in conds:
        if c.startswith("chance:"):
            out["chance"] = int(c[7:])
    rest = [c for c in conds if not c.startswith("chance:")]
    if rest:
        out["if"] = rest
    return out


def effect(trig, act, args, conds, limit, lv, levels):
    on = "damage" if trig == "always" and act in ("dmg", "taken", "clash", "crit", "maxcharge", "lifesteal", "costpct", "shopdiscount") else trig
    e = {"on": on, "act": act}
    e.update(cond_id(conds))
    if limit:
        e["limit"] = limit[0]
        e["times"] = limit[1]
    c_parts, pre, enemy = cond_ko(conds)
    t_ko = TRIG_KO[trig]
    to_enemy = act in ("inflict", "debuff", "fixed", "burst")
    who = " ".join(a for a, _ in enemy)
    obj = ""
    if enemy and not to_enemy:
        if act in ("dmg", "clash", "lifesteal", "taken", "crit"):
            # The enemy dealt with: "Burn이 있는 적에게 주는 피해".
            obj = {"taken": "%s 적에게서 " % who, "clash": "%s 적과 합할 때 " % who}.get(act, "%s 적에게 " % who)
        elif trig in ("hit", "crit", "clash_win", "clash_lose", "kill", "stagger", "hurt"):
            c_parts = [" ".join(b for _, b in enemy)] + c_parts
        else:
            c_parts = ["%s 적이 있으면" % who] + c_parts
    head = " ".join(x for x in [t_ko] + c_parts if x)

    def enemy_ko(to):
        base = TO_ENEMY_KO[to]
        if not who:
            return base
        return {"target": " ".join(b for _, b in enemy), "all": "%s 적 전원에게" % who, "random": "%s 무작위 적 1명에게" % who,
                "spread": "%s 적들에게 무작위로 나누어 총" % who, "attacker": "공격한 적이 %s 적이면 그 적에게" % who}[to]
    tail = ""
    if limit:
        tail = " (%s %d회)" % ("턴마다" if limit[0] == "turn" else "전투마다", limit[1])

    def v(i):
        return values(args[i], levels)[lv]
    if act in ("inflict", "gain"):
        kw = args[0]
        pot, cnt = v(1), v(2)
        to = args[3] if len(args) > 3 else ("target" if act == "inflict" else "self")
        if not pot and not cnt:
            return None, None
        e.update({"act": act, "kw": kw, "potency": int(pot), "count": int(cnt)})
        parts = []
        if pot:
            parts.append("%s %s" % (KW_KO[kw], fmt(pot)) if to == "spread" else "%s 위력 %s" % (KW_KO[kw], fmt(pot)))
        if cnt:
            parts.append("%s 횟수 +%s" % (KW_KO[kw], fmt(cnt)))
        if act == "inflict":
            e["to"] = TO_ENEMY_ID[to]
            ko = "%s %s %s 부여" % (head, enemy_ko(to), ", ".join(parts))
        else:
            e["to"] = TO_ALLY_ID[to]
            ko = "%s %s%s 얻음" % (head, TO_ALLY_KO[to], ", ".join(parts))
    elif act == "buff":
        name = args[0]
        to = args[2] if len(args) > 2 else "self"
        if not v(1):
            return None, None
        e.update({"buff": BUFF_ID[name], "amount": int(v(1)), "to": TO_ALLY_ID[to]})
        ko = "%s %s%s %s 얻음" % (head, TO_ALLY_KO[to], BUFF_KO[name], fmt(v(1)))
    elif act == "debuff":
        name = args[0]
        to = args[2] if len(args) > 2 else "target"
        if not v(1):
            return None, None
        e.update({"debuff": DEBUFF_ID[name], "amount": int(v(1)), "to": TO_ENEMY_ID[to]})
        ko = "%s %s %s %s 부여" % (head, enemy_ko(to), DEBUFF_KO[name], fmt(v(1)))
    elif act == "dmg":
        e.update({"act": "damage_pct", "pct": v(0)})
        ko = "%s %s주는 피해 %+g%%" % (head, obj, v(0))
    elif act == "taken":
        e.update({"act": "taken_pct", "pct": v(0)})
        ko = "%s %s받는 피해 %+g%%" % (head, obj, v(0))
    elif act == "clash":
        e.update({"act": "clash_power", "amount": int(v(0))})
        ko = "%s %s합 위력 %+d" % (head, obj, int(v(0)))
    elif act == "heal":
        pct = args[0].endswith("%")
        to = args[1] if len(args) > 1 else "self"
        e.update({"act": "heal_hp", "amount": v(0), "pct": pct, "to": TO_ALLY_ID[to]})
        ko = "%s %s체력 %s%s 회복" % (head, TO_ALLY_KO[to], fmt(v(0)), "%" if pct else "")
    elif act == "sp":
        to = args[1] if len(args) > 1 else "self"
        e.update({"act": "heal_sp", "amount": v(0), "pct": False, "to": TO_ALLY_ID[to]})
        ko = "%s %s정신력 %s %s" % (head, TO_ALLY_KO[to], fmt(abs(v(0))), "회복" if v(0) >= 0 else "감소")
    elif act == "cost":
        e.update({"amount": v(0), "pct": False})
        ko = "%s 코스트 %+d" % (head, int(v(0)))
    elif act == "costpct":
        e.update({"act": "cost", "on": "reward", "amount": v(0), "pct": True})
        ko = "전투 보상 코스트 %+g%%" % v(0)
    elif act == "crit":
        e.update({"act": "crit_mult", "value": v(0)})
        ko = "%s 치명타 피해 배율 x%g" % (head, v(0))
    elif act == "burst":
        to = args[0] if args else "target"
        e.update({"act": "tremor_burst", "to": TO_ENEMY_ID[to]})
        ko = "%s %s 진동 폭발" % (head, enemy_ko(to).replace("에게", ""))
    elif act == "maxcharge":
        e.update({"act": "max_charge", "amount": int(v(0))})
        ko = "%s 최대 충전 횟수 %+d" % (head, int(v(0)))
    elif act == "fixed":
        to = args[1] if len(args) > 1 else "target"
        e.update({"act": "fixed_damage", "pct": v(0), "to": TO_ENEMY_ID[to]})
        ko = "%s %s 최대 체력의 %s%% 고정 피해" % (head, enemy_ko(to), fmt(v(0)))
    elif act == "lifesteal":
        e.update({"act": "lifesteal", "pct": v(0)})
        ko = "%s %s준 피해의 %s%%만큼 체력 회복" % (head, obj, fmt(v(0)))
    elif act == "shopdiscount":
        e.update({"act": "shop_discount", "pct": v(0)})
        ko = "상점 가격 -%s%% (최대 60%%)" % fmt(v(0))
    else:
        raise ValueError(act)
    ko = re.sub(r"\s+", " ", (pre + ko if not head else head + " " + pre + ko[len(head):].strip()).strip())
    return e, ko + tail
