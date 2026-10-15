# Innate Fates (先天气运)

The first slice implements eight of the [retained 36-trait design](../innate-fates-design.json).
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
`alchemist_descendant`, `beast_hunter`, `unyielding`, and `burning_star`.
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

The other 28 designs, root rerolls, Phoenix revival, orbiting swords, perfect
counterattacks, companions and treasure routes remain unimplemented. See
[deferred roadmap](14_deferred_roadmap.md); do not expose placeholders as effects.

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
