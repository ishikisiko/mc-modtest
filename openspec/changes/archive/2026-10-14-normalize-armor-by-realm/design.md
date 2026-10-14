## Context

Minecraft 1.21.1 applies armor pressure before weapon armor-effectiveness enchantments. Realm growth already multiplies maximum health and outgoing attack attributes but does not change equipment armor. Scaling the entire incoming damage through an early damage event would also change shields, armor wear and invulnerability-frame differences. Re-evaluating armor inside a reduction callback would call enchantment behavior twice.

## Goals / Non-Goals

**Goals:** Normalize only armor pressure by the defender's validated existing realm factor, preserve native damage ordering, expose actual stage inputs, and verify both mathematical behavior and the transformed Minecraft path.

**Non-Goals:** New equipment, innate armor, changed realm coefficients, changed vanilla armor/toughness caps, elemental balance, PvP balance acceptance, or client-authoritative mitigation.

## Decisions

1. Inject narrowly into `CombatRules.getDamageAfterAbsorb` before its first clamp. Its unclamped effective-armor value is `A - D / (2 + T/4)`; replace only that value with `A - (A - original) / S_def`. Vanilla retains the lower/upper clamps, armor fraction, weapon enchantment call and final full-damage multiplication. The hook returns the original value exactly for scale one. A required injection count makes upstream target drift fail during startup rather than silently changing combat.
2. Resolve `S_def` from the defender's server-owned persisted realm assignment and existing `StatsRules` growth function. Missing, malformed, unknown or out-of-range assignment means scale one. Never infer scale from maximum health, attacker state or current armor. Armor pressure applies to every source reaching vanilla's armor branch; native bypass tags still skip it.
3. Keep diagnostics attached to the actual damage invocation. Use the current NeoForge damage container as identity when recording armor-stage inputs, so nested hits and reused damage-source objects cannot mix records. A weak container key avoids retaining canceled invocations. The final trace distinguishes armor input, defender scale, pressure input, effective armor and subsequent damage stages; a bypass has no fabricated armor result.
4. Add no balance constants or new configuration table. Existing rules own realm growth; Minecraft owns its armor constants and native attribute limits. The 30 armor / 12 toughness example is an explicit administrator fixture, not a newly available set.
5. Use pure contract tests for scale-one identity, homogeneous scaling, cross-realm pressure, cap boundaries, invalid assignments and large inputs. Use a built mod and bounded server/client evidence to prove the hook loads and native enchantment, bypass, wear, shield, iframe and absorption paths remain intact. Mapped melee and sword-qi input confirm normal attack consumers use the common path. Administrative damage fixtures remain labeled as fixtures.

## Risks / Trade-offs

- [Mixin target changes across Minecraft versions] → Pin the exact descriptor and first clamp ordinal, require exactly one injection, inspect jar metadata, and start both the acceptance server and a physical client.
- [Other mods replace the same armor method] → Preserve vanilla calls and restrict this hook to one arithmetic argument; compatibility with arbitrary damage-overhaul mods remains unverified.
- [Floating-point cancellation at extreme values] → Exercise large finite inputs and document tolerance; never clamp actual full damage to the normalized pressure.
- [Equipment feels too durable at high realms] → Numerical normalization is authorized; experiential combat balance remains an owner review surface.
- [Tests accidentally claim acquisition or pacing] → Explicitly label realm/equipment setup and keep owner feel and multiplayer verdicts `not_verified`.

## Migration Plan

No profile, realm data or world save migration is required. Build a new version according to `openspec/config.yaml` and restart both sides. Removing this change restores vanilla armor pressure without changing saved equipment or realm assignments.

## Open Questions

None about product direction. Exact access to NeoForge's current damage container and runtime hook packaging are implementation checks to resolve before testing.
