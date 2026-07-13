## Why

The first playable cultivation loop uses year-scaled fixed rates, a persistent
spirit-reserve indirection, and keyboard-only controls, so the player's innate
aptitude and immediate resource cost are hard to understand or act on. This
change makes each ten-tick cultivation result explicit, introduces a durable
spiritual-affinity attribute, and turns the H screen into the primary readable
control surface without weakening server authority.

## What Changes

- Add profile schema v3 with integer `spiritualAffinity`, defaulting to `10`,
  explicit v1/v2 migration, and lossless preservation of all legacy fields.
- **BREAKING** Replace normal cultivation output with exactly the current
  server-owned affinity in progress per ten eligible meditation ticks.
- **BREAKING** Replace reserve-funded spirit acceleration with an atomic direct
  cost of the current Qi layer's low-grade spirit stones per ten eligible ticks
  for exactly `50` total progress; qi-sensed mortal costs one stone.
- Preserve legacy `meditationQiReserve` in schema v3 for compatibility, but stop
  crediting, spending, or presenting it.
- **BREAKING** Replace the shipped source-stage caps with `1000`, `1100`,
  `1200`, and `1300` for advancement into Qi Refining I through IV, while
  retaining Qi IV as the release ceiling.
- **BREAKING** Derive each cultivatable stage's stability cap and advancement
  requirement as 50% of its cultivation cap. Stability gains nothing before
  progress is full, then gains current affinity per ten eligible ticks in
  either meditation mode without consuming spirit stones.
- **BREAKING** Successful advancement retains half of current stability
  (integer floor) instead of deducting a fixed absolute amount.
- Add Profile and Meditation tabs to H, with server-intent buttons for normal
  meditation, spirit meditation, stop, and advancement; retain the bounded
  action-only payload and server-derived cost/rate/result.
- Update focused validation, regression coverage, documentation, visual/manual
  acceptance, and synchronized release metadata for version `0.25.0`.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `cultivation-player-profile`: Make v3 current, add spiritual affinity, and
  define explicit v1/v2 migration and legacy-reserve preservation.
- `cultivation-meditation`: Allow H-screen controls to submit the existing
  bounded server-owned meditation and advancement intents.
- `cultivation-gain`: Replace year-scaled progress and reserve conversion with
  ten-tick affinity gain and atomic layer-priced spirit-stone settlements.
- `cultivation-advancement`: Replace the four shipped target-layer progress
  thresholds and stability gates, retain half of stability on success, and
  preserve deterministic one-layer advancement and Qi IV's release ceiling.
- `cultivation-state-synchronization`: Synchronize and present affinity, add
  Profile/Meditation tabs and intent buttons, and remove reserve presentation.
- `cultivation-core-validation`: Pin v3 migration, ten-tick arithmetic,
  inventory/profile rollback, UI authority, and full playable-loop regression.
- `validation`: Require focused validators, complete automated gates, visual
  evidence, and truthful real-client acceptance for the revised loop.

## Impact

The change affects cultivation profile codecs and snapshots, immutable service
updates, meditation settlement and inventory transactions, stage definition
caps and stability rules, H-screen layout/widgets, client localization,
validators/tests, README and knowledge-base guidance, acceptance evidence, and
synchronized `0.25.0` release metadata. It adds no client-authored rates,
costs, targets, profile values, or results, and introduces no new item or
world-generation content.
