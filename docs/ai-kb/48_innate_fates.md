# Innate Fates (先天气运)

There are twelve implemented effects, 13 selectable purple/gold/red placeholders,
and 11 design-only traits in the [36-trait catalogue](../innate-fates-design.json).
Placeholders use explicit `placeholder: true`, empty effects and bilingual no-effect
labels. They keep tier costs and exclusive groups but temporarily omit root gates.
All tier weights are equal; the two ordinary guarantees still bias the final draft.
Restart and explicitly reroll to use the new pool; existing saved choices are preserved.
The normative contract is [innate-fates](../contracts/cultivation.md#innate-fates).
The browser prototype remains a design playground; it is not the game's registry.

## Entry and selection

Right-click the testing stele to open the independent awakening screen. It
presents the synchronized spiritual root first, then the six candidates in the
same screen. An empty unconfirmed pool is rolled automatically once. Choose
three and confirm twice; reopening preserves the root, candidates, revision and
lock. Existing awakened saves may choose once; confirmed saves show their stored
result. H → Fates/先天 is a read-only result page, with a stele instruction for
unconfirmed players. Inheritance remains a separate stele step. Locking preserves
one candidate, not a mandatory selection.

Ordinary network and self-command roll/lock/confirm require a live server-owned
stele session: same dimension, alive, not removed/spectating, exact loaded block
and vanilla interaction range. Close, teleport, logout, death, dimension change,
reset and server stop invalidate it. The server issues a positive session token;
requests carry it, and an old close token cannot end a newer session.

`CultivationServerConfig` adds `[innate_fates] point_budget` in the existing
`myvillage-server.toml` server config (3..18, default 8). The owning snapshot
carries the actual server budget. A later budget reduction applies to future
confirmations; it does not revoke confirmed builds. Since 0.49.2 there is no high-tier count cap; the three-choice count,
point budget and exclusive-group constraints still apply. See the
[0.49.2 rule-change validation](../testing/2026-10-15-fate-tier-cap.md). Candidate generation ensures two ordinary entries and
adds further ordinary entries if needed to guarantee a legal low-budget triple.

## Ownership and persistence

- Synced registry: `data/myvillage/myvillage/innate_fate/*.json`,
  `FateDefinition`. Numeric effects are in this data, not per-ID Java branches.
- Startup-only tier weights and request throttle: `data/myvillage/fates/rules.json`.
- `FateSelection` is the actual pure transition/roll/validation path used by
  `FateService`; client intent is action + seen revision + bounded slot/mask +
  server-issued session token only.
- `FateState` is a separate immutable `copyOnDeath` attachment. Only
  `CultivationService.replaceFates` writes it. Cultivation profile v4 and its
  old-save migration are unchanged; protocol 19 retains its snapshot and adds
  the server-authorized opening/close session path.
- The three client caches remain profile, time, and meditation; fate state and
  budget are part of the latest profile snapshot. `FatesPage` reads PanelContext
  and uses PanelPage pointer/click hooks for result viewing, without
  page-specific hub input code. `AwakeningScreen` owns the interactive choice.
- Confirmed unknown/disabled definitions are retained by ID and stop granting
  effects; they do not create a new roll. Restore the data or use operator reset.

## Effects and boundaries

The implemented IDs are `robust`, `lightfoot`, `bright`, `fire_affinity`,
`alchemist_descendant`, `beast_hunter`, `unyielding`, `burning_star`,
`open_meridians`, `pill_aegis`, `tempered_bones`, and `sword_obsession`.
Definitions and bilingual effect labels determine what the page shows.

Health/speed use stable permanent attribute modifiers, composed with realm
modifiers; refresh does not heal and only clamps if a maximum falls. Knockback
and fall reduction use their server events. Direct outgoing bonuses add once
before vanilla mitigation. Low-health damage reduction multiplies at that same
incoming stage; typed resistance still uses its existing post-vanilla stage.
Bypass-invulnerability sources receive no fate damage adjustment.

Environmental fire reduction requires a fire-tagged source without an attacker
or direct entity. Fire affinity grants no fire attack: existing physical/fire
mappings remain as described in [47](47_realm_stats_wilderness.md).

Alchemist recovery triggers after positive nonlethal damage below its threshold.
It stores both remaining healing ticks and a cooldown deadline on the persistent
overworld clock; only online ticks heal, with no offline catch-up. Death cancels
residual healing and preserves the cooldown. Reset is an explicit operator tool,
and even it retains cooldown deadlines. Healing cannot revive a dead player.

Hunter damage/recovery recognizes hostile `Enemy` entities not allied to the
player. Kill recovery runs at accepted-death drops, requires the source player
itself (no pet/summon attribution), and uses CultivationService's bounded power
recovery. It does not reward assistants, neutral creatures or players.

Bright aptitude multiplies cultivation progress gain, not stability, and adds
its study bonus to the existing study bonus basis points. Existing integer
rounding, study gates and atomic profile/manual commit ordering remain intact.

## Purple batch (0.60.1)

`open_meridians` applies a definition-owned bonus to the realm qi cap. The effective
cap is shared by all player spend/recovery/commit paths and the owning H/HUD reads;
raising a cap never fills the extra capacity. A sword-qi cast freezes the actual
post-forging payment and refund fraction at release, then claims at most one
refund on a successful hit. No hit, zero payment, or derived attack grants a refund.

`pill_aegis` enhances only `yangyuan` (Vitality/养元丹) healing, composed once with
pill quality. Successful normal pill consumption grants an independent shield;
failed uses, Huiling and Ningshen pills do not grant it. The shield is applied after
armor/magic/typed resistance and before vanilla absorption. It neither overwrites
nor removes absorption from other sources, never blocks void/admin damage, and
retains its cooldown when it expires, breaks, or the player dies. Existing medicine
cooldowns and damage-interrupted healing still apply. Fully shielded hits do not
lose health and therefore retain the pre-existing no-health-damage healing rule.

`tempered_bones` accumulates actual health lost to direct enemy attacks; environment,
self, allied, reflected, and bonus retaliation damage do not charge it. Hostile mobs,
mobs actively attacking the player, and non-allied PvP opponents are recognized.
Ready resolve adds one base weapon-attribute damage contribution to the next
accepted melee hit, then releases an enemy-only knockback pulse. Vanilla melee and
authored sword/spear melee are supported; sword qi is explicitly excluded. A real
shield/absorption hit consumes ready resolve too, avoiding repeated free bonuses.
The enhanced hit uses one vanilla mitigation pass; its primary-damage proportion
alone can charge the defender's own resolve. Knockback composes with an authored
hit's delayed freeze reaction instead of being overwritten by it.

Both combat states are optional nested fields in the existing immutable `FateState`
codec. Old confirmed IDs keep their choices and gain the new effect definitions;
missing states decode empty. `CultivationService.replaceFates` remains the only
writer. Death discards shield and resolve while retaining cooldown deadlines;
operator reset also preserves cooldowns. Remaining resolve persists across ordinary
logout/restart. Network protocol is 29 for the expanded snapshot/effect contract.
No new selection/root gates, rarity weights, point budgets or exclusivity groups
are added. The two body traits still exclude each other.

Dedicated cloud presets are `fates-purple-build` (106 focused tests at initial
validation plus jar) and `fates-purple-runtime` (mapped confirmation, cast/pill/melee,
private two-client state and lifecycle probes). Runtime accepts `--cases` with
`meridians,pill_aegis,tempered_bones,lifecycle`; each prepares its own trait. Use
[the batch verification record](../testing/2026-10-17-purple-fates.md) for actual
run IDs, commits, failed attempts and owner-acceptance limits.

## Sword Obsession (0.60.2)

`sword_obsession` uses the authored `sword_stack_bonus`, `sword_max_stacks`, and
`sword_timeout_ticks` fields. Registered sword-family ordinary combo hits earn
one stack per move, after an accepted hurt call. All targets in a sweep receive
the same pre-hit stack bonus. At full stacks, an accepted last-move hit emits
one wave, including when that hit earns the final stack; the proc does not consume
stacks. Empty swings do not refresh the timer. Active sword qi and vanilla-mode
attacks do not earn stacks. Changing the exact held stack/slot, death, logout,
dimension, or eligibility clears the streak; restart never restores it. No
persistent attachment or owner snapshot fields were added. Operator `fate status`
adds `sword_stacks` as a read-only diagnostic.

The fate's `derived_skill` references the separately authored combat skill
`sword_obsession_wave`. `DerivedSwordWaves` owns flight and target ledgers separate
from `CombatSessionManager`'s active action. It shares the continuous collision,
wall/unloaded-chunk stopping, allied/PvP filters and single mitigation path.
A derivative consumes neither qi nor extra weapon durability, has no cast refund,
and cannot build sword stacks, trigger outgoing fates/enchantment post-effects,
or charge a defender's Tempered Bones. Defensive reductions still apply normally.
Server safety caps bound instances and target counts. Weapon changes/death/offline
or dimension changes cancel remaining flight.

Protocol 30 adds a derived-skill registry reference and three server-only passive
wave phases. `ClientSwordQi` routes those phases directly to instances keyed by
owner and wave ID; they do not mutate active charge/action revision or pose state.
The existing crescent mesh is reused, with dimensions from the derived skill.
Ordinary `jianqi_jue` data and its charge-scaled range are unchanged.

Focused presets: `sword-fate-build` and `sword-fate-runtime`. The latter uses real
mapped selection, five-move sword combos and active skill input in an isolated
world; see [the validation record](../testing/2026-10-17-sword-obsession.md).

## Commands and checks

README contains testing-stele entry, read-only H usage and matching
`fate`/`mingge` command aliases. `status` prints bounded `FATES` JSON for
diagnostics. Only permission-level-2 command sources can use the targeted roll/confirm
diagnostic fixture paths or reset. Those targeted forms bypass the stele session
only; normal fate eligibility, immutable confirmation and throttle still apply.
Ordinary self forms require a live session even for operators. Reset invalidates
the session; status remains read-only. The diagnostic confirm mask uses bits
0..5, exactly
three set bits (for example 7 means the first three candidates).

Run `tools/validate_innate_fates.py` (also a release-gate step), its mutation tests,
Java `cultivation.*` and `client.cultivation.*`, the existing cultivation/combat
validators, full tests/build and `--jar` packaging inspection. After clean
stage-1 acceptance, the recommended awakening handoff is:

```bash
python3 tools/innate_fates_evidence.py --ui-only --out out/preview/stele_awakening
```

This uses mapped input for a fresh testing-stele interaction, root-first flow,
draft resumption, confirmation and read-only H results; it captures 18 images
across English/Chinese and the three supported GUI sizes. It checks session
closure, leaving range, removing the stele and death. The save/restart phase
checks the root and confirmed draft stay unchanged and the transient session
returns to zero. `--ui-only` skips the existing numerical effects suite; the
default command without that flag runs the full stele UI, existing effects and
restart suite. The README ledger separates automated input/lifecycle evidence
from owner feel/multiplayer.

The other 24 effects remain unimplemented (13 have selectable placeholders). Root
rerolls, Phoenix revival, orbiting swords, counterattacks, companions and treasure
routes have no runtime effect. See [deferred roadmap](14_deferred_roadmap.md).

## Awakening protocol and evidence

`AwakeningOpenPayload` is clientbound and carries the server anchor position and
session token after the current profile snapshot. `AwakeningClosePayload` carries
only the token. `FateIntentPayload` contains action, seen revision, bounded
slot/mask and that token; it never carries a root, balance value or client anchor.
Common code remains physical-side safe. The independent screen is reviewed at
480×270, 427×240 and 320×240 in English and Chinese with fixed candidate/action
regions. Root generation and inheritance remain documented in
[29](29_cultivation_initiation_ritual.md).

The 0.49.0 H-selection screenshots and runtime report are historical evidence.
They do not validate this revised entry, session lifecycle or awakening layout.
The current README ledger records those surfaces separately. The
[0.49.1 awakening report](../testing/2026-10-15-stele-awakening.md) records the
29-check mapped capture, bilingual layout review, full test/build results,
phase timing, retained failures and jar parity. Owner appearance/usability,
feel and multiplayer remain `not_verified`; the separate inheritance-stele
real-click regression was not observed in this capture.

See also: [cultivation-initiation-ritual](../contracts/cultivation.md#cultivation-initiation-ritual),
[cultivation-state-synchronization](../contracts/cultivation.md#cultivation-state-synchronization),
[cultivation-core-validation](../contracts/tooling.md#cultivation-core-validation).

0.52.2 focused build and owner-PC deployment: [verification record](../testing/2026-10-16-fate-choice-pool.md).
