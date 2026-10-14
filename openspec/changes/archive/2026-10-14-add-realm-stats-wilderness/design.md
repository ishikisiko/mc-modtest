## Context

Move damage currently delegates vanilla mitigation to hurt; beasts have a separate outgoing path. Player realm IDs, ledger stage indexes and generated region tiers differ. Only sect avatars are currently projected and beast natural spawning is explicitly disabled.

## Goals / Non-Goals

Goals: inspectable shared numerical rules; exponential cultivated major realms and linear stages; effective server attributes; one extra resistance pass; bounded regional wildlife; deterministic and runtime evidence.

Non-goals: new art, autonomous rogue hostility, player breakthrough-chain expansion, rewriting ledger abstract duel outcomes, or replacing vanilla armor and enchantments.

## Decisions

- Pure stats rules use explicit canonical realm IDs and zero-based minor indexes. Cultivated growth is entry_multiplier * major_ratio^major_index * (1 + minor_slope * minor_index); mortal is an explicit baseline. Player stages map by data, never parsed numeric suffixes. Data validation rejects nonfinite values, missing aliases, realm boundary reversals and shipped health/attack growth beyond native attribute limits.
- Grow health and attack, not speed, armor percentages or every resource indiscriminately. Existing spiritual power caps keep their service-owned contract. Stable saved attribute modifiers replace prior contributions so vanilla restores maximum health before loading current Health; logout/reload cannot clamp a high-realm entity to its unscaled maximum. Refresh clamps health down when necessary and never heals; new natural entities start full.
- Pure damage calculation exposes base, flat, move, additive-bonus and total factors. Existing item/enchantment behavior stays explicit. The additional resistance stage runs only once, after vanilla armor/enchantments in the server damage event. Defaults preserve damage with no bonuses/resistance. No second custom network authority.
- Typed resistance and generic damage bonus attributes are registered and inspectable. A damage trace distinguishes pre-vanilla calculation, post-vanilla mitigation and actual health loss; it must not label requested damage as actual health loss.
- Regional range configuration names region IDs and canonical realm-stage endpoints separately for beasts and rogues. Selection is bounded and deterministic given seed/input; invalid ranges fail. Beasts receive a persistent birth realm only at natural generation. Reloads and movement never reroll it; command/egg wolves remain legacy baseline unless explicitly assigned for diagnostics.
- Rogue encounters project existing eligible living rogue ledger people in the current region. WorldSimAvatars remains the only avatar spawner; no phantom persons or alternate ledger is introduced. A region with no eligible people shows none. Existing immutable ledger roles and invulnerability remain. One person has at most one projection and caps apply globally; no chunk force loading.
- Data is bundled and validated at startup; changes require restart, which is documented rather than pretending hot reload exists.

## Risks / Trade-offs

- Exponential numbers can hit native limits: validate the full shipped ladder and reject unsupported tuning, not silent clipping.
- Sparse eligible rogues: report candidate counts and distinguish absence from failed generation.
- Existing validators forbid natural wolves: update their contracts and negative tests together.
- Existing player progression ends early: stats support ledger tiers without inventing new advancement gameplay.

## Migration Plan

Old beasts remain unassigned baseline; new naturally spawned beasts save their realm. Player attributes derive from existing profiles without changing profile wire format. Avatars remain transient projections. Release follows openspec/config.yaml rules.tasks. Owner feel and encounter-density verdicts remain not_verified until observed.
