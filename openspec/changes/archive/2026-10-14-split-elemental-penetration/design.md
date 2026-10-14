## Context

The statistics adapter already classifies physical and fire damage, and registers all five elemental resistance channels. It currently reads one shared penetration attribute after vanilla mitigation. The requested change concerns independent penetration values, not new elemental attack sources.

## Goals / Non-Goals

**Goals:** Five independent elemental penetration attributes; physical penetration remains independent; type-local selection, synchronized diagnostics and persisted attribute values; unchanged subtraction and armor ordering.

**Non-Goals:** Elemental attacks, new source classification, equipment bonuses, additional panels, armor changes, or altered numerical bounds.

## Decisions

- Register `metal_resistance_penetration`, `wood_resistance_penetration`, `water_resistance_penetration`, `fire_resistance_penetration` and `earth_resistance_penetration` using one helper with the existing data-owned bounds. Each value defaults to zero and joins the existing synchronized attribute list.
- Retain `myvillage:resistance_penetration` as the physical-only registry ID and rename its Java field/language label accordingly. This keeps old physical bases and modifiers readable without a world migration. Adding a replacement physical ID would require copying arbitrary saved modifiers; copying old penetration to all elements would defeat independent defaults. Old generic values intentionally cease to penetrate fire.
- Select a single penetration holder from the already classified damage type, alongside the corresponding resistance holder. Do not sum general and elemental values or apply multiple resistance passes. An absent living attacker contributes zero. `DamageMath.mitigate` keeps its existing percentage-point subtraction and zero floor.
- Existing `/attribute` and `stats info` expose the independent values; `stats trace` already records the classified type and selected scalar penetration. No payload, profile format or diagnostics-only authoritative override is needed.
- Unit tests cover every enum channel, distinct registry IDs, defaults/bounds, nonmatching-channel isolation and no cross-channel fallback. Bounded real-client evidence checks current physical/fire routes, independent stored values, zero floor, legacy physical-only behavior, mapped melee and save/restart. Unimplemented elemental attacks remain explicitly unverified.

## Risks / Trade-offs

- [Legacy ID still looks generic] → Keep it only for save compatibility and label/document it as physical; expose explicit elemental IDs.
- [Old generic bonuses previously reduced fire resistance] → Document that fire now uses its independent zero-default attribute; do not silently duplicate bonuses.
- [Registered attributes mistaken for implemented elemental combat] → Preserve current source mapping and make the remaining four attack routes explicit in docs/evidence.

## Migration Plan

Build/restart both sides with the version rule from `openspec/config.yaml`. Existing physical attribute data keeps its original registry ID; new elemental values start at zero and save normally. No profile migration or attribute-copy side effect is introduced.
