## Context

The current `pagoda` builder emits three three-storey towers selected from two
similar footprints. `pagoda_story_insets` carves the upper wall bodies inward,
but intermediate roofs are represented by a single slab ring; the registered
`pagoda` roof then places a two-tier roof only at the top. The result is tall on
paper but visually reads as one large box with a doubled crown. The three
exports currently report the same silhouette score.

The existing form registry, building passes, NBT exporter, town/sect placement
tables, internal stair contract, preview tooling, and full validation pipeline
remain the right delivery path. The change should strengthen that path rather
than introduce an imported prefab or a second pagoda family.

## Goals / Non-Goals

**Goals:**

- Make every pagoda storey legible through its own projecting eave, bracket
  rhythm, corner lift, and recessed wall body.
- Expand the family to deterministic five- and seven-storey profiles with
  larger platforms and visibly different proportions.
- Keep all upper floors reachable through the existing protected stairwell and
  landing contract.
- Keep town and sect placement collision-free after horizontal expansion.
- Produce focused metrics and previews that make visual acceptance auditable.

**Non-Goals:**

- No copy or conversion of the `candidate_006` structure resources.
- No redesign of temples, main halls, pavilions, bell/drum towers, town layout,
  sect ritual ordering, or world-generation frequency.
- No resource-id rename and no new runtime command.
- No claim of acceptance until the owner reviews the regenerated pagodas.

## Decisions

### D1: Three hand-authored pagoda profiles replace random footprint rolls

`pagoda_v1` is a compact five-storey tower on a `15x15` body, `pagoda_v2` is a
broad five-storey tower on a `19x19` body, and `pagoda_v3` is a slender
seven-storey tower on a `17x17` body. Each profile owns foundation height,
storey height, inset schedule, eave projection, crown overhang, platform pad,
and finial height.

This is preferred to increasing every tower uniformly: deterministic profiles
guarantee a small/monumental/slender comparison and give validation a stable
shape signature.

### D2: Intermediate eaves become a shallow pagoda-specific operation

`pagoda_story_insets` remains the wall-taper operation but also emits a shallow
two-band roof skirt at every occupied-storey boundary. Each skirt projects past
the lower wall, carries brackets under the edge, and lifts its four corners.
The registered `pagoda` roof handles only the final pyramidal crown and finial.

This is preferred to calling the full roof generator once per storey: stacked
full pyramids consume too much vertical space, intersect the next floor, and
make the tower look like disconnected huts.

### D3: Taper is stepped rather than forced at every storey

Inset schedules are non-decreasing and contain at least two real reductions,
but a footprint may repeat for one adjacent storey. A one-block reduction every
second level preserves usable rooms and stair landings while still producing a
clear long-distance taper.

### D4: Scale expansion propagates through existing footprint mirrors

After regeneration, measured NBT width/depth replaces the existing pagoda
entries in Python town/sect tables and their Java runtime mirrors. Civic-core
and terrace parcels grow only enough to contain the largest shipped profile;
the placement roles and structure ids remain unchanged.

### D5: Pagoda validation uses explicit geometry metrics

Focused checks require five or seven storeys, a complete eave-level set, at
least two inset reductions, a pyramidal crown and finial, protected stair
openings at every boundary, minimum scale, three unique profile signatures,
unique NBT hashes, and a meaningful height spread. The normal quality and full
resource validators remain in force.

## Risks / Trade-offs

- **Large eaves collide with the next storey** -> Keep intermediate skirts to
  two shallow bands and derive their bounds from the lower-storey footprint.
- **Taper removes stair or landing cells** -> Reserve the stairwell using the
  maximum inset margin and retain the existing protected opening checks.
- **Expanded NBT overlaps planned parcels** -> Measure regenerated resources
  and update Python/Java footprint mirrors together before town/sect tests.
- **All tall variants saturate the generic silhouette score** -> Add pagoda
  profile, height, ratio, and hash spread metrics instead of relying on the
  clamped generic score alone.
- **Offline colors obscure the roof rhythm** -> Inspect both isometric PNGs and
  interactive slice viewers, then require an owner verdict before closeout.

## Migration Plan

1. Add deterministic profile data and the intermediate-eave operation.
2. Add focused pagoda metrics/tests and regenerate only the cultivation-town
   library to learn final bounds.
3. Update planner/runtime footprint mirrors and regenerate town/sect outputs.
4. Run full generation, validation, previews, visual report, and jar build.
5. Stop for owner visual verdict; rollback is the scoped generator/resource
   diff if the new scale or silhouette is rejected.

## Open Questions

None for implementation. The remaining decision is the final visual verdict on
the regenerated three-profile family.
