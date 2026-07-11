## Why

The shipped `pagoda_001..003` resources satisfy the current mechanical tower
contract but read as closely related stacked pavilions: all three use the same
three-storey massing, their upper eaves are thin bands, and their silhouette
scores do not separate. The accepted next direction is a materially larger
visual correction that makes the pagoda a convincing vertical landmark rather
than adding another building family.

## What Changes

- Rebuild the existing `pagoda` variants as three deterministic large landmark
  profiles with distinct footprint, storey count, taper, roof rhythm, and
  height-to-width proportion.
- Give every occupied storey an independently legible projecting eave with
  brackets and lifted corners, then finish the tower with a pyramidal crown and
  taller finial.
- Preserve the existing `pagoda_001..003` ids, form-registry route, usable
  internal stairs, town/sect placement roles, and `myvillage` resource paths.
- Expand the planner/runtime footprint tables only as needed to contain the new
  resources without parcel overlap.
- Add focused pagoda validation and variant-spread checks, regenerate previews,
  and stop for a new owner visual verdict.
- Treat `candidate_006` as calibration-only reference provenance. No external
  NBT, datapack structure, palette, or layout is copied.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `vertical-landmark`: Require the standalone pagoda family to provide large,
  visibly tapering, storey-rhythmed landmark variants with distinct silhouettes.
- `cultivation-massing-grammar`: Apply the independent-storey-eave and tapered
  pagoda grammar to the standalone `pagoda` archetype as well as scripture
  pagoda forms.
- `validation`: Add pagoda-specific structural, scale, navigability, and
  three-variant distinctness checks against generated reports/resources.

## Impact

- Generator and validation code under `tools/buildgen/` plus town/sect footprint
  tables and their Java runtime mirrors.
- Regenerated `pagoda_001..003.nbt`, placement/gallery functions, building
  reports, previews, and visual-acceptance evidence.
- README and a concise KB note describing the accepted scope and manual review
  boundary.
- No resource-id rename, new external dependency, temple-family change, or new
  world-generation behavior.
