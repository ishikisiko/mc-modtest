# Innate Fates (先天气运)

The first slice implements eight of the [retained 36-trait design](../innate-fates-design.json).
The normative contract is [innate-fates](../../openspec/specs/innate-fates/spec.md).
The browser prototype remains a design playground; it is not the game's registry.

## Entry and selection

After the testing stele awakens a root, open H → Fates/先天. Existing awakened
characters also start with an unconfirmed fate attachment. This slice rerolls
only fates, never the persisted root. The inheritance stele remains a separate
step. Pick three of six candidates and confirm twice in the page. Confirmation
is permanent for ordinary players; locking preserves one candidate, not a
mandatory selection. The server checks the seen revision and current slots.

`CultivationServerConfig` adds `[innate_fates] point_budget` in the existing
`myvillage-server.toml` server config (3..18, default 8). The owning snapshot
carries the actual server budget. A later budget reduction applies to future
confirmations; it does not revoke confirmed builds. High-tier/group constraints
remain independent. Candidate generation ensures two ordinary entries and
adds further ordinary entries if needed to guarantee a legal low-budget triple.

## Ownership and persistence

- Synced registry: `data/myvillage/myvillage/innate_fate/*.json`,
  `FateDefinition`. Numeric effects are in this data, not per-ID Java branches.
- Startup-only tier weights and request throttle: `data/myvillage/fates/rules.json`.
- `FateSelection` is the actual pure transition/roll/validation path used by
  `FateService`; client intent is action + seen revision + slot/mask only.
- `FateState` is a separate immutable `copyOnDeath` attachment. Only
  `CultivationService.replaceFates` writes it. Cultivation profile v4 and its
  old-save migration are unchanged; payload protocol 18 extends its snapshot.
- The three client caches remain profile, time, and meditation; fate state and
  budget are part of the latest profile snapshot. `FatesPage` reads PanelContext
  and uses PanelPage pointer/click hooks, without page-specific hub input code.
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

README contains normal H usage and matching `fate`/`mingge` command aliases.
`status` prints bounded `FATES` JSON for diagnostics. Only operators can target
another player or reset a selection; regular commands use the same transition
and throttle as the page. The diagnostic confirm mask uses bits 0..5, exactly
three set bits (for example 7 means the first three candidates).

Run `tools/validate_innate_fates.py` (also a release-gate step), its mutation tests,
Java `cultivation.*` and `client.cultivation.*`, the existing cultivation/combat
validators, full tests/build and `--jar` packaging inspection. Runtime evidence
uses `tools/innate_fates_evidence.py` after clean stage-1 acceptance. The README
ledger separates automated input/lifecycle evidence from owner feel/multiplayer.

The other 28 designs, root rerolls, Phoenix revival, orbiting swords, perfect
counterattacks, companions and treasure routes remain unimplemented. See
[deferred roadmap](14_deferred_roadmap.md); do not expose placeholders as effects.
