# Black God (검은 신) — reference

Fan-made content after Limbus Company (the Sisyphus System "Black God" boss,
around Brand Manager Don Quixote / the Bride). This is the reference the
plugin's Black God mode is built from, transcribed from the screenshots the
server owner sent. "수감자" = the player characters (here: other players and
mobs), "간수" = the Black God (here: the player in Black God mode),
"신부" = the Bride.

`~` (물결) stands for any of 못[Exception], 못[Breakpoint], 못[Console] together.

## Skills (9, one per hotbar slot)

| # | Name | Type / sin | Skill power | Coin power | Coins | Weight |
|---|------|-----------|-------------|-----------|-------|--------|
| 1 | 하아…이제 좀 돌아가 주지? | Blunt / Pride (오만) | 3 | +2 | 2 | 1 |
| 2 | 퇴근 좀 하자고. | Blunt / Gloom (우울) | 4 | +3 | 2 | 1 |
| 3 | '심판' | Blunt / Sloth (나태) | 3 | +3 | 3 | 2 |
| 4 | '신 앞에 뉘우쳐라'던가? | Guard (방어) / Envy (질투) | 5 | +5 | 1 | 1 |
| 5 | '속죄하라' | Blunt / Pride | 3 | +2 | 3 | 1 |
| 6 | '형벌받으라' | Blunt / Gloom | 3 | +2 | 3 | 1 |
| 7 | 시지프 시스템을 진짜 망가트리려고? | Blunt / Sloth | 4 | +2 | 5 | 7 |
| 8 | 강제 퇴장 | Blunt / Gluttony (탐식) | 23 | +8 | 1 (execution) | 1 |
| 9 | Shift+Delete | Blunt / Sloth | 23 | +8 | 3 (execution) | 1 |

1. **하아…이제 좀 돌아가 주지?** — Target has ~ ≥5: this skill's first coin
   becomes unbreakable. Target ~ 9: final power +2. On use: own Preservation
   count +3. On use: Poise +2, own Poise count +1.
   Coin I: on hit, Tremor 2. Coin II: on hit, Tremor 2.
2. **퇴근 좀 하자고.** — Target ~ ≥5: first coin unbreakable. Target ~ 6/9+:
   final power +2/+3. On use: own Preservation count +5. On use: Poise +2,
   own Poise count +2.
   Coin I: on hit, 못[Console] 1, Tremor count +2.
   Coin II (unbreakable): on hit, 못[Console] 2; if hit without being
   destroyed, Tremor count +2.
3. **'심판'** — Can clash regardless of speed. Main target ~ 3/6/9+: final
   power +2/+3/+5. [Combat start] main target ~ 5/9+: attack weight +1/+3.
   On use: if own Preservation exists, consume all, coin power +1.
   [Clash lose] Paralysis 3, damage -75%; the target is not staggered by
   this skill's damage.
   Coins I, II: unbreakable. Coin III (unbreakable): hits only the main
   target; damage + (own lost HP %) (max 100%); on hit, 못[Breakpoint] 4.
4. **'신 앞에 뉘우쳐라'던가?** (guard) — At the phase when a prisoner holding
   Stop (정지) gives an order, if this skill is equipped it attacks the main
   target; skill damage +100% (an E.G.O skill +200% instead).
   [Turn end] Black God's Voice +100 if under 100 (up to 100); the Bride loses
   Sanity equal to the amount gained (max 45).
5. **'속죄하라'** — Target ~ 9: final power +2. On use: consume up to 15 Black
   God's Authority → final power +1, damage +15%. [Clash lose] the target is
   not staggered by this skill.
   Coin I: on hit, 못[Console] 1; if not destroyed and hit, Tremor 2.
   Coin II: 못[Console] 1; if not destroyed and hit, Tremor 3.
   Coin III: 못[Console] 1; if not destroyed and hit, Tremor Burst; target's
   Tremor count -1.
6. **'형벌받으라'** — Target ~ 6/9+: final power +2/+3. On use: consume up to
   15 Authority → final power +2, damage +30%. On use: own Preservation count
   +6. [Clash lose] the target is not staggered by this skill.
   Coin I: 못[Console] 1; if not destroyed and hit, Tremor 2.
   Coin II: 못[Console] 1; Tremor count +3.
   Coin III: 못[Console] 1; Tremor Burst; target's Tremor count -1.
7. **시지프 시스템을 진짜 망가트리려고?** — Can clash regardless of speed. Does
   not trigger the targets' defense skills. Cannot be staggered until used.
   Per target's ~ 1: final power +1, damage +5% (per target). Main target ~
   3/6/9+: final power +2/+3/+5. On use: if the main target has Stop, final
   power -5, clash power -5. Every target of this skill gains a shield of 50%
   of max HP (at attack end, a prisoner's shield loses what this gave).
   On use: consume up to 40 Authority → final power +3, damage +50%.
   On use: damage + (held Authority)%; at attack end consume all Authority.
   On clash lose this does not trigger. If own Preservation exists, consume
   all, coin power +2. [Clash lose] Paralysis 5, damage -90%; the target is
   not staggered by this skill.
   Coin III: on hit, Tremor → Tremor-Preservation conversion.
   Coin IV: on hit, triggers and consumes all of the target's ~ effects.
   Coin V: if it lost the clash, hits only the main target; [clash lost, then
   hit] 못[Exception] 9; on hit, if main target, Sin (죄악감) 3.
8. **강제 퇴장** — [Target cannot change]. Prioritises prisoners with Pulse
   (박동), clashing regardless of speed. Does not trigger the target's defense
   skills. Cannot be staggered until used. If the target has Pulse, clash
   power -15. [Clash lose] next turn Fragile 5. On use: consume all
   Authority. Execution coin: on hit, fixed damage of 200% of the target's
   max HP.
9. **Shift+Delete** — Does not trigger the target's defense skills.
   Prioritises prisoners other than Meursault (뫼르소); if it clashes with
   Meursault, clash power -20. Cannot be
   staggered until used. [Clash lose] Fragile 10 this and next turn.
   [Attack end] if the Bride lost the clash, the Bride's current shield -50%
   (min 0). Execution coins I, II: fixed damage 50% of max HP. Execution coin
   III: fixed damage 200% of max HP.

## Keywords

- **정지 (Stop)** — Turn start: a random prisoner gains it. Final power +2,
  damage dealt +25%. On hit, Defense Level -1 (max 9). Removed at turn end.
  "Authority of death; collision of Preservation and Stop."
- **박동 (Pulse)** — Turn start: a random prisoner gains it. If its holder wins
  a clash against the Bride's 'Forced Exit', gains a shield of 250% max HP
  (removed by that amount at the end of Forced Exit). Clash power +3, damage
  taken -50%. On hit, Attack Level -1 (max 9). Removed at turn end.
- **~** — 못[Exception], 못[Breakpoint], 못[Console] together.
- **못[Exception]** — max 9. At turn start, if present: take fixed damage of
  50% current HP and become Staggered; removed at turn end once triggered.
  With Pulse: not staggered, damage taken halved, removed that turn.
- **못[Breakpoint]** — max 9. When triggered by 'Sisyphus System…', take fixed
  damage of (count × 10)% max HP (min 10). If a defense skill was equipped at
  battle start, damage taken halved and cannot die or be staggered by it.
- **못[Console]** — At battle start, if the count is 9, this turn the holder's
  hits on the Bride deal fixed damage of 2% of the holder's max HP. When
  triggered by 'Sisyphus System…', gain Sinking equal to the count and take
  (count × 2) stagger damage.
- **검은 신의 목소리 (Black God's Voice)** — max 100. Turn start: per 20, Attack
  Level +1 (max 4). Rises by the amount of Preservation the Black God's skill
  effects consume or gain (max 40 per turn, including gains past the cap).
  Turn end +10. At 100, becomes Black God's Authority next turn.
- **검은 신의 권능 (Black God's Authority)** — max 100. Turn start: per 20, Attack
  Level +1 and Defense Level +1 (max 4). Every skill coin becomes unbreakable
  (except execution coins). At 0, becomes Black God's Voice next turn.
- **시지프 형벌의 신부 (Bride of Sisyphus Punishment)** — max 5. Per 1: Attack
  Level +2, Defense Level +3. At 3+, 못[Console] inflicted by skills +1.
- **시지프 시스템 보존 (Sisyphus System Preservation)** — max 25. Damage dealt
  +x% (max 25%), damage taken -x% (max 25%).
- **진동 - 보존 (Tremor - Preservation)** — On Tremor Burst: Sloth damage of
  (60 + attacker's Charge or Special Charge potency × 8)% of Tremor potency,
  count -1 (max 120%). Renoir characters' skills/passives triggering Tremor
  Burst do not reduce the count. Taking a Tremor Burst skill: stagger damage
  equal to effect potency. Turn end: count -1.

## Passives

- **젠장, 그런 글리치는 어떻게 찾아낸 거야.** — Turn start: Pulse and Stop go to
  random prisoners. When the manager acts in certain ways, the Bride deals
  max-HP-proportional damage to a random prisoner and inflicts 못[Console].
  Below 50% HP, the Bride interferes with the system: fully restores own HP
  and Sanity and removes all negative effects.
- **시지프 시스템 유지보수 툴** — When the warden inflicts ~ on prisoners with
  its skills: 못[Exception], [Breakpoint], [Console] are called ~ together;
  when the Bride inflicts nails on a prisoner, the last-inflicted effect
  applies and the counts add up; 못[Exception] and [Breakpoint] do not turn
  into [Console]. On hit against a prisoner: damage +5% per ~ the target holds
  (max 45%, per target). Turn start: if the warden's HP is 50% or less, every
  ~ inflicted by skills +1.
- **'보존의 규율대로'** — When the clash count reaches 10+, that skill's clash
  power + (number of clashes). Burn, Bleed, Tremor, Rupture, Sinking potency
  accumulate up to 50 and count up to 15. Damage taken from Burn, Bleed,
  Tremor, Rupture, Sinking -50%. Panic-type cannot change; Sanity does not go
  below 0. On the first turn after appearing: Sanity becomes 45; Black God's
  Voice 0, Bride of Sisyphus Punishment 1; the previous phase's Preservation
  potency and count carry over.
- **'심판의 날'** — Turn start, if the Black God has Authority: the pattern
  changes and the strong skill 'Sisyphus System…' is used; that turn speed
  is the minimum, max +20. If the warden loses a clash in that pattern, next
  turn the pattern changes again and the strong skill 'Forced Exit' is used.
  Whenever Black God's Authority / Voice switches: recover 45 Sanity and gain
  1 Bride of Sisyphus Punishment.
- **눈물은 천을 적시고, 천은 목을 조르며… 말도 안 떠오르네.** — Sinking does not
  lower own Sanity; instead takes that much Gloom damage. When hit by a
  prisoner, that prisoner's Sinking count +1 (max 2 per prisoner per turn).
  Gains Poise potency up to 20 only; the excess Poise potency recovers Sanity.
  On Tremor Burst on a prisoner without a stagger threshold: instead of
  stagger damage, Sloth damage of half the target's Tremor potency (not for
  staggered targets, per target).
- **완벽 보존** — Every 10 Preservation consumed in total in battle:
  Preservation potency +1. Turn start: if own Preservation potency is 10 and
  count under 10, count +5. Preservation's turn-end count -1 does not apply.
  At Preservation potency 5/10+, Preservation max count +5/+10.
- **자, 이제 퇴근해 볼까** — Turn end: if Preservation potency exceeds 10,
  consume the excess and gain that much Sisyphus System Preservation (stops
  after all Sisyphus System Preservation was consumed by this passive).
  When own HP first falls to 33% or less, the pattern changes: consume all
  Sisyphus System Preservation and gain a non-decaying shield of
  (25 + Sisyphus System Preservation)% of max HP; permanently lose Defense
  Level equal to the Sisyphus System Preservation consumed.

## Black God mode (in game)

- Toggle: `/ip blackgod`, or right-click the Black God item (`/ip give blackgod`).
  On: the whole inventory (hotbar, storage, armour, off hand) is saved on the
  player and cleared; the mode's hotbar is given. Off: the saved inventory
  comes back (anything picked up meanwhile is kept too).
- Mode hotbar: slot 1 "스킬 사용" (use skills), slot 9 "검은 신 해제" (toggle off).
- Right-click "스킬 사용": the 9 skills fill the hotbar; then switching to a
  slot (number key, scroll, or tapping a slot on Bedrock) uses that skill
  (right-click uses the one in hand), and the hotbar goes back.
