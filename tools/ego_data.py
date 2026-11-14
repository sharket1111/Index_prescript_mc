"""Builds paper/src/main/resources/ego/egos.json: the Sinners' E.G.O.

Numbers (grade, sin, costs, Sanity cost, the Awakening skill's power, coins and
coin effects) come from the Limbus Company Wiki's E.G.O pages (limbuscompany.wiki.gg,
CC BY-SA 4.0); Korean names, quotes, passives and coin texts from NamuWiki
(namu.wiki, each Sinner's "인게임 정보" page, CC BY-NC-SA 2.0 KR).

    python3 tools/ego_data.py <raw dir>

<raw dir> holds pages.json (page title -> wikitext of each E.G.O page) and
namu_ego.json (title -> Korean name, quote, passive and coin texts).

Each coin's effects are also read into simple actions the plugin plays
(effects): inflict/gain a keyword, Tremor Burst, heal HP or Sanity, more damage.
Anything else stays in the text only.
"""
import json
import os
import re
import sys

OUT = os.path.join("paper", "src", "main", "resources", "ego", "egos.json")
SINNER_KO = {"Yi Sang": "이상", "Faust": "파우스트", "Don Quixote": "돈키호테", "Ryōshū": "료슈", "Meursault": "뫼르소", "Hong Lu": "홍루",
             "Heathcliff": "히스클리프", "Ishmael": "이스마엘", "Rodion": "로쟈", "Sinclair": "싱클레어", "Outis": "오티스", "Gregor": "그레고르"}
SINS = ["wrath", "lust", "sloth", "gluttony", "gloom", "pride", "envy"]
KEYWORDS = {"burn": "burn", "bleed": "bleed", "tremor": "tremor", "rupture": "rupture", "sinking": "sinking", "poise": "poise", "charge": "charge"}
DEBUFFS = {"bind": "bind", "paralyze": "paralysis", "paralysis": "paralysis", "fragile": "fragile",
           "offense level down": "offense_level_down", "defense level down": "defense_level_down"}
BUFFS = {"haste": "haste", "damage up": "damage", "protection": "protection", "power up": "power", "attack power up": "power",
         "offense level up": "offense_level", "defense level up": "defense_level"}
WHEN = {"on hit": "hit", "heads hit": "heads", "tails hit": "tails", "attack end": "end", "heads attack end": "end_heads",
        "clash win": "clash_win", "on use": "use", "crit": "crit", "on kill": "kill", "on crit": "crit", "after attack": "end"}


def field(text, name):
    m = re.search(r"\|" + name + r"=([^\n|]*)", text)
    return m.group(1).strip() if m else ""


def template(text, name):
    """The body of |name={{Skill ...}} (nested templates kept whole)."""
    start = text.find("|" + name + "={{")
    if start < 0:
        return None
    i = text.index("{{", start)
    depth = 0
    for j in range(i, len(text)):
        if text.startswith("{{", j):
            depth += 1
        elif text.startswith("}}", j):
            depth -= 1
            if depth == 0:
                return text[i + 2:j]
    return None


def plain(text):
    text = re.sub(r"\{\{SkillCon\|([^}|]+)[^}]*\}\}", r"[\1]", text)
    text = re.sub(r"\{\{StatusEffect\|([^}|]+)[^}]*\}\}", r"\1", text)
    text = re.sub(r"\{\{[^}|]+\|([^}|]+)[^}]*\}\}", r"\1", text)
    text = re.sub(r"'''?", "", text)
    text = re.sub(r"<[^>]+>", " ", text)
    return re.sub(r"\s+", " ", text).strip()


def actions(part):
    """One effect line in English -> plain actions (or none)."""
    line = plain(part)
    m = re.match(r"^\[([^\]]+)\]\s*(.*)$", line)
    when = "hit"
    if m:
        when = WHEN.get(m.group(1).lower().strip(), m.group(1).lower().strip())
        line = m.group(2)
    low = line.lower()
    out = []
    to = "target"
    if "all enemies" in low:
        to = "all"
    elif re.search(r"on \d+ random enem", low) or "random enem" in low:
        to = "random"
    for m in re.finditer(r"inflict (\d+) ([a-z ]+?)(?: count)?(?: next turn)?(?= on| and|,|\.|$)", low):
        n, what = int(m.group(1)), m.group(2).strip()
        count = (" count" in low[m.end(2):m.end(2) + 7])
        if what in KEYWORDS:
            out.append({"when": when, "do": "inflict", "kw": what, "potency": 0 if count else n, "count": n if count else 0, "to": to})
        elif what in DEBUFFS:
            out.append({"when": when, "do": "debuff", "what": DEBUFFS[what], "amount": n, "to": to})
    for m in re.finditer(r"inflict \+(\d+) ([a-z]+) count", low):
        if m.group(2) in KEYWORDS:
            out.append({"when": when, "do": "inflict", "kw": m.group(2), "potency": 0, "count": int(m.group(1)), "to": to})
    for m in re.finditer(r"gain (\d+) ([a-z ]+?)(?: count)?(?: next turn)?(?= and|,|\.|$)", low):
        n, what = int(m.group(1)), m.group(2).strip()
        count = " count" in low[m.end(2):m.end(2) + 7]
        if what in KEYWORDS:
            out.append({"when": when, "do": "gain", "kw": what, "potency": 0 if count else n, "count": n if count else 0})
        elif what in BUFFS:
            out.append({"when": when, "do": "buff", "what": BUFFS[what], "amount": n})
    if "tremor burst" in low and "trigger" in low:
        out.append({"when": when, "do": "burst"})
    m = re.search(r"heal (\d+) sp", low)
    if m:
        out.append({"when": when, "do": "sp", "amount": int(m.group(1))})
    m = re.search(r"heal (\d+)% of max hp", low) or re.search(r"heal hp by (\d+)% of max hp", low)
    if m:
        out.append({"when": when, "do": "heal", "pct": int(m.group(1))})
    m = re.search(r"deal \+(\d+)% (?:more )?damage", low)
    if m:
        out.append({"when": when, "do": "damage", "pct": int(m.group(1))})
    return out


def skill(text, name):
    body = template(text, name)
    if body is None:
        return None
    cp = field(body, "cpower").replace(" ", "")
    coins = int(field(body, "coin") or 1)
    lines, effects = [], []
    se = field(body, "se")
    if se:
        for part in re.split(r"<br\s*/?>", se):
            if plain(part) and not plain(part).startswith("{{"):
                lines.append(plain(part))
                effects += [dict(a, coin=-1) for a in actions(part)]
    for c in range(1, coins + 1):
        ce = re.search(r"\|ce" + str(c) + r"=(.*?)(?=\n\||\n\}\}|$)", body, re.S)
        if not ce:
            continue
        for part in re.split(r"<br\s*/?>", ce.group(1)):
            if plain(part):
                lines.append("코인 %d: %s" % (c, plain(part)))
                effects += [dict(a, coin=c - 1) for a in actions(part)]
    return {"type": field(body, "type").lower() or "slash", "sin": field(body, "sin").lower() or "wrath",
            "power": int(field(body, "spower") or 0), "coin_power": int(cp or 0), "coins": coins,
            "weight": int(field(body, "atkweight") or 1), "text": lines, "effects": effects}


def slug(name):
    return re.sub(r"[^a-z0-9]+", "_", name.lower().replace("ō", "o")).strip("_")


def passive_effects(eid):
    """The passive as effects and Korean lines (tools/ego_passive_specs.py)."""
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from ego_passive_specs import SPECS
    from md_gift_dsl import parse
    spec = SPECS.get(eid)
    if not spec:
        return [], []
    effects, ko = parse(spec, 1)[0]
    return effects, ko


def main(raw):
    pages = json.load(open(os.path.join(raw, "pages.json"), encoding="utf-8"))
    namu = json.load(open(os.path.join(raw, "namu_ego.json"), encoding="utf-8"))
    out = []
    for title, text in pages.items():
        n = namu.get(title, {})
        awakening = skill(text, "askill")
        if awakening is None:
            continue
        costs = {}
        for s in SINS:
            v = field(text, s + "cost")
            if v.isdigit() and int(v) > 0:
                costs[s] = int(v)
        ptext = (n.get("passive") or {}).get("text") or ""
        # The PDF text runs on into the skill table: cut it there.
        ptext = re.split(r"\s*\S*\s*공격 유형", ptext)[0].strip()
        passive = re.search(r"\|passive=\{\{Passive\|([^|]+)\|(.*?)\}\}\n", text, re.S)
        out.append({
            "id": slug(title), "name": n.get("name") or field(text, "prefix"), "name_ko": n.get("kr") or field(text, "prefix"),
            "sinner": field(text, "sinner"), "sinner_ko": SINNER_KO.get(field(text, "sinner"), field(text, "sinner")),
            "risk": field(text, "risk"), "sin": field(text, "affinity").lower(), "abnormality": n.get("abno") or field(text, "abnormality"),
            "quote": n.get("quote") or plain(field(text, "aquote")), "sanity": int(field(text, "asanity") or 0), "costs": costs,
            "skill": awakening, "text_ko": n.get("effects") or [],
            "passive_effects": passive_effects(slug(title))[0], "passive_ko": passive_effects(slug(title))[1],
            "passive": {"name": (n.get("passive") or {}).get("name") or (passive.group(1) if passive else ""),
                        "text": ptext or (plain(passive.group(2)) if passive else "")},
        })
    out.sort(key=lambda e: (["ZAYIN", "TETH", "HE", "WAW", "ALEPH"].index(e["risk"]) if e["risk"] in ("ZAYIN", "TETH", "HE", "WAW", "ALEPH") else 9,
                            e["sinner"], e["name"]))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    note = ("From the Limbus Company Wiki (limbuscompany.wiki.gg), CC BY-SA 4.0, and NamuWiki (namu.wiki), CC BY-NC-SA 2.0 KR. "
            "Generated by tools/ego_data.py.")
    with open(OUT, "w", encoding="utf-8") as fh:
        json.dump({"about": note, "egos": out}, fh, ensure_ascii=False, separators=(",", ":"))
    print("egos", len(out), "with effects", sum(1 for e in out if e["skill"]["effects"]), "passives", sum(1 for e in out if e["passive_effects"]))
    missing = [e["id"] for e in out if not e["passive_effects"]]
    assert not missing, ("passives without a spec", missing)


if __name__ == "__main__":
    main(sys.argv[1])
