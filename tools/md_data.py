"""Mirror Dungeon data for the plugin (paper/src/main/resources/md/).

Reads the original's data, saved from the Limbus Company Wiki (limbuscompany.wiki.gg,
CC BY-SA 4.0), and writes what the plugin plays:

* gifts.json   E.G.O Gifts: name, tier, sin, keyword, price, enhance levels, the original
               text, and *effects*: the text turned into triggers and actions the plugin runs
               in real time (a turn is 7 seconds), with a Korean summary of each.
* themes.json  Theme Packs: floors on Normal/Hard, length, boss name, colour, keyword,
               unique gifts, and their own events.
* events.json  Abnormality events: story, choices, sin checks and results.
* trials.json  Mounting Trials for floors 1-4 (chosen after each floor's boss).
* recipes.json The fixed fusions: ingredients -> result (results are made only this way).

    python3 tools/md_data.py <raw dir>
<raw dir> holds the wiki text: Mirror_of_Sisyphe.txt, List_of_Floor_Themes.txt,
gift_data.lua (Module:EgoGift/data), fusion.txt (List of E.G.O Gifts/Fusion), enbox.lua (Module:EnBox/data), themes/*.txt and abno/*.txt (one page each,
title on the first line). tools/md_fetch.py downloads them.
"""
import json
import os
import re
import sys

OUT = "paper/src/main/resources/md"
KEYWORDS = ["Burn", "Bleed", "Tremor", "Rupture", "Sinking", "Poise", "Charge"]
KW_KO = {"burn": "화상", "bleed": "출혈", "tremor": "진동", "rupture": "파열", "sinking": "침잠", "poise": "호흡", "charge": "충전"}
SINS = ["wrath", "lust", "sloth", "gluttony", "gloom", "pride", "envy"]
SIN_KO = {"wrath": "분노", "lust": "색욕", "sloth": "나태", "gluttony": "탐식", "gloom": "우울", "pride": "오만", "envy": "질투"}


def clean(s):
    s = re.sub(r"<br\s*/?>", "\n", s)
    s = re.sub(r"\{\{StatusEffect\|([^|}]*)[^}]*\}\}", r"\1", s)
    s = re.sub(r"\{\{GiftTT\|([^|}]*)[^}]*\}\}", r"\1", s)
    s = re.sub(r"\{\{StatCon\|[a-z]+\|([^|}]*)\}\}", r"\1", s)
    s = re.sub(r"\{\{[^{}]*\}\}", "", s)
    s = re.sub(r"\[\[(?:[^|\]]*\|)?([^\]]*)\]\]", r"\1", s)
    s = re.sub(r"'''?", "", s)
    s = re.sub(r"<[^>]+>", "", s)
    return re.sub(r"\n\s*\n+", "\n", s).strip()


# --- gifts ---------------------------------------------------------------------------------

def read_gifts(path):
    src = open(path, encoding="utf-8").read()
    out, section = {}, ""
    for line in src.split("\n"):
        s = line.strip()
        if s.startswith("--"):
            section = s[2:].strip(" -")
            continue
        m = re.match(r'\["(.+?)"\]\s*=\s*\{(.*)\},?\s*$', s)
        if not m:
            continue
        key, body = m.group(1), m.group(2)

        def f(name):
            mm = re.search(name + r'\s*=\s*"((?:[^"\\]|\\.)*)"', body)
            return mm.group(1) if mm else None

        def n(name):
            mm = re.search(name + r'\s*=\s*(-?\d+)', body)
            return int(mm.group(1)) if mm else None
        if "Legacy" in section or "Story Dungeon" in section or "Legacy" in key:
            continue
        base = key.rstrip("+")
        level = len(key) - len(base)
        g = out.setdefault(base, {"levels": {}})
        g["levels"][level] = clean(f("desc") or "")
        if level == 0:
            g.update({"name": f("name") or base, "tier": f("tier"), "sin": f("sin"), "keyword": (f("category") or "None").lower(),
                      "cost": n("cost") or 0, "enhance": n("upgrade") or 0, "source": section})
    return {k: v for k, v in out.items() if v.get("tier")}


NUM = r"(\d+(?:\.\d+)?)"
KW_RE = r"(Burn|Bleed|Tremor|Rupture|Sinking|Poise|Charge)"


def trigger_of(line):
    l = line.lower()
    table = [
        ("first turn start", "combat_start"), ("encounter start", "combat_start"), ("combat start", "combat_start"), ("start of a wave", "combat_start"),
        ("start of each wave", "combat_start"), ("turn start", "turn_start"), ("turn end", "turn_end"), ("at the end of the turn", "turn_end"),
        ("critical hit", "crit"), ("clash win", "clash_win"), ("win a clash", "clash_win"), ("winning a clash", "clash_win"),
        ("defeating", "kill"), ("defeat ", "kill"), ("killing", "kill"), ("is killed", "kill"), ("kills", "kill"), ("kill ", "kill"),
        ("stagger", "stagger"), ("taking damage", "hurt"), ("take damage", "hurt"), ("takes damage", "hurt"), ("when hit", "hurt"), ("getting hit", "hurt"),
        ("takes a hit", "hurt"), ("take a hit", "hurt"), ("is hit", "hurt"), ("ally is killed", "ally_death"), ("ally dies", "ally_death"),
        ("hitting", "hit"), ("on hit", "hit"), ("hits", "hit"), ("hit an", "hit"), ("upon hit", "hit"), ("when using", "hit"), ("after using", "hit"),
        ("upon entering a shop", "shop"), ("entering a shop", "shop"), ("upon entering floor", "floor"), ("floor clear", "floor_clear"),
        ("encounter reward", "reward"), ("cost gained", "reward"),
    ]
    best = None
    for key, trig in table:
        m = re.search(r"\b" + re.escape(key.strip()) + r"\b", l)
        if m and (best is None or m.start() < best[0]):
            best = (m.start(), trig)
    return best[1] if best else "passive"


def target_of(line):
    l = line.lower()
    if re.search(r"randomly inflict a total of", l) or "between all enemies" in l:
        return "random_enemies"
    if "all enemies" in l or "all abnormality parts" in l:
        return "all_enemies"
    if "a random enemy" in l or "random enemy" in l:
        return "random_enemy"
    if "all allies" in l:
        return "all_allies"
    return "target"


def parse_line(line):
    """Turns one line of a gift's text into effects, as far as the plugin can play it."""
    effects = []
    # Lines for particular factions or Identities have no counterpart here.
    if re.search(r"faction|Branch Identit|Exclusive Effect|Identities:|Identity Exclusive|Deployed Identity\]|Bloodfeast|E\.G\.O resource|Absolute Resonance|Resonance", line):
        return effects
    trig = trigger_of(line)
    if trig == "ally_death":
        return effects
    tgt = target_of(line)
    cond = None
    m = re.search(r"(?:enemies|enemy|target|targets) (?:with|affected by|suffering) " + KW_RE, line)
    if m:
        cond = "target_has:" + m.group(1).lower()
    elif re.search(r"(?<!non-)staggered enem", line, re.I):
        cond = "target_staggered"
    # Inflicting statuses on enemies: "inflict 3 Burn Potency", "+2 Bleed Count", "inflict 1 Bleed Potency and +15 Bleed Count".
    inflict_on = "hit" if trig == "passive" else trig
    if trig == "hurt" and re.search(r"on the attacker|to the attacker", line, re.I):
        tgt = "attacker"
    for m in re.finditer(r"(?:inflict|apply|applies|inflicts)(?: a total of)?\s+" + NUM + r"\s+" + KW_RE + r"(?: Potency)?(?:\s+and\s+\+" + NUM + r"\s+" + KW_RE + r" Count)?", line, re.I):
        e = {"on": inflict_on, "act": "inflict", "kw": m.group(2).lower(), "potency": int(float(m.group(1))), "count": int(float(m.group(3))) if m.group(3) else 0, "to": tgt}
        if cond:
            e["if"] = cond
        effects.append(e)
    for m in re.finditer(r"(?:inflict|apply)\s+\+" + NUM + r"\s+" + KW_RE + r" Count", line, re.I):
        e = {"on": inflict_on, "act": "inflict", "kw": m.group(2).lower(), "potency": 0, "count": int(float(m.group(1))), "to": tgt}
        if cond:
            e["if"] = cond
        effects.append(e)
    # Gaining statuses on allies: "gain 3 Poise Potency", "+3 Charge Count", "gain 5 Poise Potency and +2 Poise Count".
    if not any(e["act"] == "inflict" for e in effects):
        for m in re.finditer(r"gains?\s+" + NUM + r"\s+(Poise|Charge)(?: Potency)?(?:\s+and\s+\+" + NUM + r"\s+(?:Poise|Charge) Count)?", line, re.I):
            kw = m.group(2).lower()
            pot, cnt = int(float(m.group(1))), int(float(m.group(3))) if m.group(3) else 0
            if kw == "charge" and not m.group(3):
                pot, cnt = 0, pot
            effects.append({"on": trig, "act": "gain", "kw": kw, "potency": pot, "count": cnt, "to": "all_allies" if "all allies" in line.lower() else "self"})
        for m in re.finditer(r"gains?\s+\+" + NUM + r"\s+(Poise|Charge) Count", line, re.I):
            effects.append({"on": trig, "act": "gain", "kw": m.group(2).lower(), "potency": 0, "count": int(float(m.group(1))),
                            "to": "all_allies" if "all allies" in line.lower() else "self"})
    # Buffs: Haste, Power Up, Damage Up, Protection, Offense/Defense Level.
    for m in re.finditer(r"gains?\s+" + NUM + r"\s+(Haste|Power Up|Clash Power Up|Damage Up|Protection|Offense Level Up|Offense Level|Defense Level Up|Defense Level|Attack Power Up)", line, re.I):
        buff = m.group(2).lower().replace(" up", "").replace("attack power", "power").replace("clash power", "power")
        effects.append({"on": trig, "act": "buff", "buff": buff.replace(" ", "_"), "amount": int(float(m.group(1))),
                        "to": "all_allies" if "all allies" in line.lower() else "self"})
    for m in re.finditer(r"(?:inflict|apply)\s+" + NUM + r"\s+(Offense Level Down|Defense Level Down|Bind|Paralysis|Fragile)", line, re.I):
        effects.append({"on": trig, "act": "debuff", "debuff": m.group(2).lower().replace(" ", "_"), "amount": int(float(m.group(1))), "to": tgt})
    # Damage: "deal +10% damage", "+35% damage against ...", "Damage +20%".
    for m in re.finditer(r"(?:deal|deals|dealing)\s+\+" + NUM + r"% (?:more )?damage|damage(?: dealt)? \+" + NUM + r"%|\+" + NUM + r"% damage", line, re.I):
        pct = float(next(g for g in m.groups() if g))
        e = {"on": "damage" if trig in ("passive", "hit", "kill") else trig, "act": "damage_pct", "pct": pct}
        if cond:
            e["if"] = cond
        effects.append(e)
    for m in re.finditer(r"(?:take|takes|taken)\s+-" + NUM + r"% (?:less )?damage|damage taken -" + NUM + r"%", line, re.I):
        effects.append({"on": "hurt", "act": "taken_pct", "pct": -float(next(g for g in m.groups() if g))})
    # Healing.
    for m in re.finditer(r"heal(?:s)?\s+" + NUM + r"(%)?\s*(HP|SP)", line, re.I):
        effects.append({"on": trig, "act": "heal_" + m.group(3).lower(), "amount": float(m.group(1)), "pct": bool(m.group(2)),
                        "to": "all_allies" if "all allies" in line.lower() else "self"})
    if re.search(r"fully heal[^.]*SP", line, re.I):
        effects.append({"on": trig, "act": "heal_sp", "amount": 45, "pct": False, "to": "self"})
    # Cost.
    for m in re.finditer(r"(?:gain|gains)\s+" + NUM + r"\s+Cost|Cost \+" + NUM + r"(%)?", line, re.I):
        amount = float(m.group(1) or m.group(2))
        effects.append({"on": "reward" if trig == "passive" else trig, "act": "cost", "amount": amount, "pct": bool(m.group(3))})
    # Critical hits and Tremor Burst.
    m = re.search(r"Critical Hit damage multiplier from x" + NUM + r" to x" + NUM, line, re.I)
    if m:
        effects.append({"on": "passive", "act": "crit_mult", "value": float(m.group(2))})
    if re.search(r"trigger Tremor Burst", line, re.I):
        effects.append({"on": trig, "act": "tremor_burst", "to": tgt})
    if re.search(r"Max Charge Count \+" + NUM, line, re.I):
        effects.append({"on": "passive", "act": "max_charge", "amount": int(re.search(r"Max Charge Count \+" + NUM, line, re.I).group(1))})
    return effects


def generic(g):
    """Gifts whose text the plugin cannot play as written get a simpler effect of the same kind and strength."""
    tier = {"I": 1, "II": 2, "III": 3, "IV": 4, "V": 5, "EX": 5}.get(g["tier"], 1)
    kw = g["keyword"]
    if kw in KW_KO and kw not in ("poise", "charge"):
        return [{"on": "hit", "act": "inflict", "kw": kw, "potency": tier, "count": 1, "to": "target", "chance": 30}]
    if kw == "poise":
        return [{"on": "combat_start", "act": "gain", "kw": "poise", "potency": 2 * tier, "count": tier, "to": "self"}]
    if kw == "charge":
        return [{"on": "turn_start", "act": "gain", "kw": "charge", "potency": 0, "count": tier, "to": "self"}]
    if kw in ("slash", "pierce", "blunt"):
        return [{"on": "damage", "act": "damage_pct", "pct": 3 * tier, "if": "kind:" + kw}]
    return [{"on": "damage", "act": "damage_pct", "pct": 2 * tier}]


TRIG_KO = {"combat_start": "전투 시작", "turn_start": "턴 시작", "turn_end": "턴 끝", "hit": "적중 시", "crit": "치명타 시", "clash_win": "합 승리 시",
           "kill": "처치 시", "stagger": "흐트러뜨릴 때", "hurt": "피해를 받을 때", "shop": "상점 입장 시", "floor": "층 입장 시",
           "floor_clear": "층 클리어 시", "reward": "전투 보상", "passive": "항상", "damage": "항상"}
TO_KO = {"attacker": "공격한 적", "all_enemies": "적 전원", "target": "대상", "random_enemy": "무작위 적", "random_enemies": "적들에게 무작위로 총", "all_allies": "아군 전원", "self": "자신"}
BUFF_KO = {"haste": "신속", "power": "위력 증가", "damage": "피해량 증가", "protection": "보호", "offense_level": "공격 레벨", "defense_level": "방어 레벨"}


def summary_ko(e):
    t = TRIG_KO.get(e["on"], e["on"])
    a = e["act"]
    cond = ""
    if e.get("if", "").startswith("target_has:"):
        cond = KW_KO[e["if"].split(":")[1]] + " 있는 "
    elif e.get("if") == "target_staggered":
        cond = "흐트러진 "
    elif e.get("if", "").startswith("kind:"):
        cond = {"slash": "참격", "pierce": "관통", "blunt": "타격"}[e["if"].split(":")[1]] + " 공격 "
    chance = ("%d%% 확률로 " % e["chance"]) if e.get("chance") else ""
    if a == "inflict":
        parts = []
        if e["potency"]:
            parts.append("%s 위력 %d" % (KW_KO[e["kw"]], e["potency"]))
        if e["count"]:
            parts.append("%s 횟수 +%d" % (KW_KO[e["kw"]], e["count"]))
        return "%s: %s%s%s %s" % (t, chance, cond, TO_KO[e["to"]], ", ".join(parts))
    if a == "gain":
        parts = []
        if e["potency"]:
            parts.append("%s 위력 %d" % (KW_KO[e["kw"]], e["potency"]))
        if e["count"]:
            parts.append("%s 횟수 +%d" % (KW_KO[e["kw"]], e["count"]))
        return "%s: %s %s" % (t, TO_KO[e["to"]], ", ".join(parts))
    if a == "buff":
        return "%s: %s %s %d" % (t, TO_KO[e["to"]], BUFF_KO.get(e["buff"], e["buff"]), e["amount"])
    if a == "debuff":
        names = {"offense_level_down": "공격 레벨 감소", "defense_level_down": "방어 레벨 감소", "bind": "속박", "paralysis": "마비", "fragile": "취약"}
        return "%s: %s %s %d" % (t, TO_KO[e["to"]], names.get(e["debuff"], e["debuff"]), e["amount"])
    if a == "damage_pct":
        return "%s대상에게 주는 피해 +%g%%" % (cond, e["pct"]) if e["on"] == "damage" else "%s: 주는 피해 +%g%%" % (t, e["pct"])
    if a == "taken_pct":
        return "받는 피해 %g%%" % e["pct"]
    if a in ("heal_hp", "heal_sp"):
        return "%s: %s %s %g%s 회복" % (t, TO_KO[e["to"]], "체력" if a == "heal_hp" else "정신력", e["amount"], "%" if e["pct"] else "")
    if a == "cost":
        return "%s: 코스트 +%g%s" % (t, e["amount"], "%" if e["pct"] else "")
    if a == "crit_mult":
        return "치명타 피해 배율 x%g" % e["value"]
    if a == "tremor_burst":
        return "%s: %s 진동 폭발" % (t, TO_KO[e["to"]])
    if a == "max_charge":
        return "최대 충전 +%d" % e["amount"]
    return a


def build_gifts(lua, themes_unique):
    gifts = read_gifts(lua)
    out, parsed = [], 0
    for key, g in gifts.items():
        levels = []
        for level in sorted(g["levels"]):
            text = g["levels"][level]
            effects = []
            for line in text.split("\n"):
                # "... instead" lines are stronger variants for conditions this server does not have (an Affinity, a faction).
                if line.strip() and not re.search(r"\binstead\b", line, re.I):
                    effects += parse_line(line)
            levels.append({"text": text, "effects": effects})
        exact = bool(levels and levels[0]["effects"])
        parsed += exact
        for lv in levels:
            if not lv["effects"]:
                lv["effects"] = generic(g)
                lv["generic"] = True
            lv["ko"] = [summary_ko(e) for e in lv["effects"]]
        out.append({"id": re.sub(r"[^a-z0-9]+", "_", key.lower()).strip("_"), "name": g["name"], "tier": g["tier"], "sin": g["sin"],
                    "keyword": g["keyword"], "cost": g["cost"], "enhance": g["enhance"], "source": g["source"],
                    "theme_only": key in themes_unique, "levels": levels})
    print("gifts", len(out), "played as written", parsed)
    return out


# --- theme packs and events ------------------------------------------------------------------

def template_fields(body):
    """Top-level |key=value fields of a template body (nested templates kept whole)."""
    fields, depth, cur, i = {}, 0, "", 0
    parts = []
    while i < len(body):
        if body.startswith("{{", i):
            depth += 1
            cur += "{{"
            i += 2
            continue
        if body.startswith("}}", i):
            depth -= 1
            cur += "}}"
            i += 2
            continue
        if body[i] == "|" and depth == 0:
            parts.append(cur)
            cur = ""
        else:
            cur += body[i]
        i += 1
    parts.append(cur)
    for p in parts[1:]:
        if "=" in p:
            k, v = p.split("=", 1)
            fields[k.strip()] = v.strip()
    return fields


def templates(text, name):
    """Each {{name ...}} in text, with its body."""
    out, start = [], 0
    while True:
        i = text.find("{{" + name, start)
        if i < 0:
            return out
        depth, j = 0, i
        while j < len(text):
            if text.startswith("{{", j):
                depth += 1
                j += 2
            elif text.startswith("}}", j):
                depth -= 1
                j += 2
                if depth == 0:
                    break
            else:
                j += 1
        out.append(text[i + 2:j - 2])
        start = j


def parse_result(raw):
    """A choice's result as actions: gift, hp/sp loss or heal (for whom), cost, battle, nothing."""
    acts = []
    r = raw
    for m in re.finditer(r"\{\{StatCon\|gift[a-z]*\|([^|}]+)\}\}", r):
        if "Win the battle" not in r:
            acts.append({"do": "gift", "name": m.group(1).strip()})
    if "{{StatCon|combat}}" in r or "Win the battle" in r:
        gifts = [m.group(1).strip() for m in re.finditer(r"\{\{StatCon\|gift[a-z]*\|([^|}]+)\}\}", r)]
        acts.append({"do": "battle", "gifts": gifts})
    for sent in re.split(r"<br\s*/?>|\n", r):
        who = "all"
        if "Selected" in sent or "selected" in sent:
            who = "selected"
        m = re.search(r"without \{\{StatCon\|sin([a-z]+)\}\}", sent)
        if m:
            who = "without:" + {"glut": "gluttony"}.get(m.group(1), m.group(1))
        m = re.search(r"with \{\{StatCon\|sin([a-z]+)\}\}", sent)
        if m and "without" not in sent:
            who = "with:" + {"glut": "gluttony"}.get(m.group(1), m.group(1))
        for kind, act in (("hplose", "hp_lose"), ("splose", "sp_lose"), ("hpheal", "hp_heal"), ("spheal", "sp_heal"), ("alllose", "all_lose"), ("allheal", "all_heal")):
            for m in re.finditer(r"\{\{StatCon\|" + kind + r"\|(\d+)\}\}", sent):
                acts.append({"do": act, "amount": int(m.group(1)), "who": who})
        m = re.search(r"Cost (\d+) gained|gain(?:ed)? (\d+) Cost", sent)
        if m:
            acts.append({"do": "cost", "amount": int(m.group(1) or m.group(2))})
    return acts


def parse_choice(body):
    f = template_fields(body)
    choice = {"label": clean(f.get("choice", "")), "desc": clean(f.get("desc", "")), "story": clean(f.get("story", "").split("{{SkillCheck")[0])}
    checks = templates(body, "SkillCheck")
    if checks:
        c = template_fields(checks[0])
        sins = [c.get("sin%d" % i, "").strip().lower() for i in (1, 2, 3)]
        choice["check"] = {
            "prompt": clean(c.get("prompt", "")), "sins": [s for s in sins if s], "adv": int(re.sub(r"\D", "", c.get("adv", "")) or 0),
            "disadv": int(re.sub(r"\D", "", c.get("disadv", "")) or 0) or None,
            "pass": {"text": clean(c.get("passtext", "")), "result": parse_result(c.get("passresult", "")), "result_text": clean(c.get("passresult", ""))},
            "fail": {"text": clean(c.get("failtext", "")), "result": parse_result(c.get("failresult", "")), "result_text": clean(c.get("failresult", ""))},
        }
    else:
        choice["result"] = parse_result(f.get("result", ""))
        choice["result_text"] = clean(f.get("result", ""))
    return choice


def parse_event(body, name):
    f = template_fields(body)
    choices = []
    for k in sorted(f, key=lambda k: int(k[6:]) if k[6:].isdigit() else 0):
        if not re.match(r"choice\d+$", k):
            continue
        inner = templates(f[k], "Choice")
        choices.append(parse_choice(inner[0][len("Choice"):] if inner else f[k]))
    choices = [c for c in choices if c["label"]]
    if not choices:
        return None
    title = clean(f.get("eventname", "")) or name
    if title.lower() in ("mirror dungeon event", "mirror dungeon"):
        title = name
    return {"name": title, "story": clean(f.get("story", "")), "choices": choices}


def build_events(abno_dir):
    events = []
    for fn in sorted(os.listdir(abno_dir)):
        text = open(os.path.join(abno_dir, fn), encoding="utf-8").read()
        title = text.split("\n", 1)[0]
        i = text.find("==Mirror Dungeon Event==")
        if i < 0:
            i = text.find("== Mirror Dungeon Event ==")
        if i < 0:
            continue
        evs = templates(text[i:], "AbEvent")
        if not evs:
            continue
        ev = parse_event(evs[0], title)
        if ev:
            ev["id"] = re.sub(r"[^a-z0-9]+", "_", title.lower()).strip("_")
            ev["abnormality"] = title
            ev["kr"] = (re.search(r"\|krtitle=([^\n|]*)", text) or [None, ""])[1].strip()
            events.append(ev)
    print("events", len(events))
    return events


def read_enemies(path):
    """Module:EnBox/data: enemy id -> (name, risk)."""
    out = {}
    for m in re.finditer(r'\["(\d+)"\]\s*=\s*\{([^\n]*)\}', open(path, encoding="utf-8").read()):
        name = re.search(r'name\s*=\s*"([^"]*)"', m.group(2))
        risk = re.search(r'risk\s*=\s*"([^"]*)"', m.group(2))
        if name:
            out[m.group(1)] = {"name": clean(name.group(1)), "risk": risk.group(1).lower() if risk else "human"}
    return out


def theme_enemies(t, enemies):
    """The Enemies table of a theme page: bosses, and the normal, risky and focused pools by name."""
    i = t.find("==Enemies==")
    if i < 0:
        return [], {}
    seg = t[i + 11:]
    j = seg.find("\n==")
    seg = seg[:j] if j >= 0 else seg
    rows = [r for r in seg.split("\n|-") if "EnBox" in r or "EnMiniBox" in r]
    if not rows:
        return [], {}
    cells = [c for c in re.split(r"\n\|", "\n" + rows[0]) if c.strip()]
    look = lambda c: [enemies[x] for x in re.findall(r"En(?:Mini)?Box\|(\d+)", c) if x in enemies]
    bosses = look(cells[0]) if cells else []
    pools = {}
    for key, cell in zip(["normal", "risky", "focused", "miniboss"], cells[1:]):
        pools[key] = look(cell)
    return bosses, pools


def build_themes(list_txt, theme_dir, events, enemies):
    rows = list_txt.split("\n|-\n")
    themes, category = [], ""
    for r in rows:
        h = re.search(r"<h4>(.*?)</h4>", r)
        if h:
            category = h.group(1)
            continue
        m = re.search(r"\[\[([^|\]]*) Theme Pack\|", r)
        if not m or "Removed" in r:
            continue
        name = m.group(1)
        cells = [c[1:].strip() for c in r.split("\n") if c.startswith("|")][1:4]

        def floors(cell):
            mm = re.match(r"(\d+)F(?:-(\d+)F)?", cell or "")
            return [int(mm.group(1)), int(mm.group(2) or mm.group(1))] if mm else None
        length = int((re.match(r"(\d+)", cells[2] or "6") or [0, 6])[1]) if len(cells) > 2 else 6
        layout = "long" if "long battle" in (cells[2] or "") else "fixed" if "fixed" in (cells[2] or "") else "random"
        page = os.path.join(theme_dir, "".join(c if c.isalnum() else "_" for c in (name + " Theme Pack"))[:80] + ".txt")
        boss, color, unique, own_events = "", "#7a1f1f", [], []
        bosses, pools = [], {}
        if os.path.exists(page):
            t = open(page, encoding="utf-8").read()
            bosses, pools = theme_enemies(t, enemies)
            mm = re.search(r"\|color=(#[0-9a-fA-F]{6})", t)
            color = mm.group(1) if mm else color
            mm = re.search(r"bossfights? against (?:the )?([^.\n,]+?)(?:,|\.|\n| and | appearing| on floor)", t)
            boss = clean(mm.group(1)) if mm else ""
            mm = re.search(r"\|unique=([^\n]*)", t)
            unique = [u.strip().replace(" (MD)", "") for u in mm.group(1).split(";") if u.strip()] if mm else []
            j = t.find("Dungeon Events")
            if j >= 0:
                for k, body in enumerate(templates(t[j:], "AbEvent")):
                    ev = parse_event(body, name)
                    if ev:
                        ev["id"] = re.sub(r"[^a-z0-9]+", "_", (name + "_" + ev["name"]).lower()).strip("_")
                        ev["theme"] = name
                        own_events.append(ev["id"])
                        events.append(ev)
        kw = None
        for k in KEYWORDS:
            if k.lower() in name.lower() or {"burn": "Flame", "bleed": "Blood", "tremor": "Seismic", "rupture": "Might", "sinking": "Sinking",
                                             "poise": "Breathing", "charge": "Thunder"}[k.lower()] in name:
                kw = k.lower()
        hk = {"Burning Haze": "burn", "Trickled Sanguine Blood": "bleed", "Dizzying Waves": "tremor", "Crushing External Force": "rupture",
              "Deep Sigh": "poise", "Rising Power Supply": "charge"}
        kw = hk.get(name, kw)
        sin = next((s for s in SINS if s in name.lower()), None)
        themes.append({"id": re.sub(r"[^a-z0-9]+", "_", name.lower()).strip("_"), "name": name, "category": category,
                       "normal": floors(cells[0] if cells else None), "hard": floors(cells[1] if len(cells) > 1 else None),
                       "length": length, "layout": layout, "boss": boss or (bosses[0]["name"] if bosses else ""), "bosses": bosses, "enemies": pools, "color": color, "keyword": kw, "sin": sin,
                       "unique": unique, "events": own_events})
    print("themes", len(themes), "with boss", sum(1 for t in themes if t["boss"]), "with enemies", sum(1 for t in themes if t["enemies"].get("normal")))
    return themes


def build_trials(sisyphe):
    lines = sisyphe.split("\n")
    out, floor = [], None
    start = sisyphe.find("==Mounting Trials==")
    end = sisyphe.find("==Mounting Adversities==")
    for l in sisyphe[start:end].split("\n"):
        m = re.search(r"'''Floor (\d+)'''", l)
        if m:
            floor = int(m.group(1))
            continue
        if floor and floor <= 4 and l.startswith("|") and "||" in l:
            cells = [re.sub(r"\{\{[^}]*\}\}", "", c).strip() for c in l[1:].split("||")]
            cells = [c for c in cells if c]
            if len(cells) >= 2:
                stats = {}
                for k, v in re.findall(r"([A-Za-z ]+?) ([+-]\d+(?:\.\d+)?)%?", cells[1]):
                    stats[k.strip().lower().replace(" ", "_")] = float(v)
                out.append({"floor": floor, "name": cells[0], "text": cells[1], "stats": stats})
    print("trials", len(out))
    return out


def gid(name):
    return re.sub(r"[^a-z0-9]+", "_", name.lower()).strip("_")


def build_recipes(text, gifts):
    """The fixed fusions (List of E.G.O Gifts/Fusion): ingredients -> result."""
    ids = {g["id"] for g in gifts}
    find = lambda n: gid(n) if gid(n) in ids else gid(n) + "_md" if gid(n) + "_md" in ids else None
    out = []
    for row in text.split("\n|-"):
        if "border-left" not in row:
            continue
        left, right = row.rsplit("border-left", 1)
        ins = [find(n) for n in re.findall(r"\{\{GiftTT\|([^|}]+)", left)]
        res = [find(n) for n in re.findall(r"\{\{GiftTT\|([^|}]+)", right)]
        if ins and res and all(ins) and res[0]:
            out.append({"ingredients": ins, "result": res[0]})
    print("recipes", len(out))
    return out


def main(raw):
    os.makedirs(OUT, exist_ok=True)
    events = build_events(os.path.join(raw, "abno"))
    enemies = read_enemies(os.path.join(raw, "enbox.lua"))
    themes = build_themes(open(os.path.join(raw, "List_of_Floor_Themes.txt"), encoding="utf-8").read(), os.path.join(raw, "themes"), events, enemies)
    unique = {u for t in themes for u in t["unique"]}
    gifts = build_gifts(os.path.join(raw, "gift_data.lua"), unique)
    trials = build_trials(open(os.path.join(raw, "Mirror_of_Sisyphe.txt"), encoding="utf-8").read())
    recipes = build_recipes(open(os.path.join(raw, "fusion.txt"), encoding="utf-8").read(), gifts)
    results = {r["result"] for r in recipes}
    for g in gifts:
        g["fusion_only"] = g["id"] in results
    note = "From the Limbus Company Wiki (limbuscompany.wiki.gg), CC BY-SA 4.0. Generated by tools/md_data.py."
    for name, data in (("gifts", gifts), ("themes", themes), ("events", events), ("trials", trials), ("recipes", recipes)):
        with open(os.path.join(OUT, name + ".json"), "w", encoding="utf-8") as fh:
            json.dump({"about": note, name: data}, fh, ensure_ascii=False, separators=(",", ":"))


if __name__ == "__main__":
    main(sys.argv[1])
