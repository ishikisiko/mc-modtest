## Context

The shipped playable loop has an immutable profile v2, one transient
server-owned meditation/advancement manager, a bounded action-only input
payload, five-second fixed-point gain batches, and a persistent reserve that is
credited from low-grade spirit stones. The H screen is currently a read-only
profile view, while V/B/G/N are the only action surfaces.

This revision crosses persistence, networking, inventory transactions,
definition data, settlement arithmetic, and client presentation. Server
authority and `CultivationService` ownership remain non-negotiable: the client
may choose an action, but may not supply affinity, rates, costs, targets,
elapsed ticks, inventory facts, progress, or results.

## Goals / Non-Goals

**Goals:**

- Make spiritual affinity a durable, validated profile attribute with a safe
  v1/v2-to-v3 migration path and default value `10`.
- Make progress and post-cap stability easy to reason about as
  ten-eligible-tick settlements while preserving the mastery clock.
- Charge spirit stones directly and atomically by the source player's current
  Qi layer, with no partial charge and no charge at a progress cap.
- Replace only the four released progress caps and keep deterministic
  one-transition advancement through Qi IV.
- Turn H into a two-tab profile and meditation surface whose buttons reuse the
  existing bounded intent protocol.
- Preserve truthful automated, visual, and real-client acceptance boundaries.

**Non-Goals:**

- Adding affinity generation, rerolls, equipment modifiers, commands, buffs,
  debuffs, derived combat attributes, or a balance progression beyond the
  default value.
- Removing the v2 reserve field from stored profiles, converting old reserve
  back into items, or using reserve for any result in v3.
- Changing advancement durations, lifespan flow, interruption penalties,
  keyboard shortcuts, ore generation, or item definitions.
- Unlocking Qi V, Foundation Establishment, random breakthroughs, offline gain,
  or client-authored automation.

## Decisions

### Profile v3 adds affinity without reinterpreting old data

`CultivationProfile` v3 adds a non-negative integer `spiritualAffinity`.
Factories and reset create `10`; both v1 and v2 migrations also assign `10`.
The v1 decoder and v1-to-v2 transformation remain intact, then migration
continues through a validated v2-to-v3 transformation. Current encoding and
snapshots write v3 only. Every immutable copy path preserves affinity and the
legacy `meditationQiReserve` value exactly.

Keeping reserve is deliberate. Removing it would either discard player data or
require an arbitrary refund policy. V3 instead treats it as inert compatibility
data: no code credits it, spends it, or renders it. A future cleanup change can
choose an explicit retirement policy with evidence from real saves.

Alternative considered: derive affinity from the spiritual-root basis-point
map. Rejected because that would reinterpret an existing elemental-composition
field as cultivation speed and make migrations dependent on datapack content.

### Progress precedes affinity-paced stability consolidation

The transient session counts only continuously eligible active ticks. At each
tenth eligible tick, normal meditation proposes exactly
`profile.spiritualAffinity` progress. Spirit meditation proposes exactly `50`
total progress. Both are clamped to the current stage's remaining cap and use
overflow-safe arithmetic; neither banks overflow.

Each cultivatable stage derives a stability cap equal to integer-floor 50% of
its cultivation cap. Stability remains unchanged while progress is below the
cap, including the settlement that first reaches the cap. Beginning with the
next ten-tick settlement, both normal and spirit meditation add exactly current
spiritual affinity to stability, clamped to the derived cap. Capped spirit
sessions consume no stones for stability. Basic Breathing mastery alone retains
its existing `10` points per configured cultivation year and transient
fixed-point remainder in either mode.

The gate is intentionally sequential rather than a second parallel progress
bar: first fill cultivation progress, then consolidate it into stability.
Successful advancement retains `floor(current stability / 2)`, so prior
consolidation contributes to the next stage without eliminating its new work.

Alternative considered: continue growing stability alongside progress.
Rejected because it makes stability a duplicate progress bar and removes the
requested post-cap consolidation phase.

### Spirit cost is a server-derived all-or-nothing batch transaction

The server derives cost from an optional positive `spirit_stone_cost` authored
on the authoritative source-stage definition at each due batch. Qi-sensed
mortal and Qi I author `1`, Qi II authors `2`, and Qi III authors `3` low-grade
spirit stones; later Qi definitions record their matching positive layer value
without gaining a cultivation cap. Any stage with a cultivation cap but no
cost is invalid rather than falling back to id-string parsing. Qi IV has no
cultivation cap, so it cannot produce or charge a batch.

If remaining cap is zero, progress gain and item removal are skipped while
stability may gain current affinity up to its derived cap and mastery continues
on its configured-year clock. Otherwise the server requires the entire batch
cost in ordinary player inventory, removes exactly that many stones, proposes
up to `min(50, remaining capacity)` progress plus any due mastery, and commits
one immutable replacement through
`CultivationService`. An incomplete removal restores anything removed and
commits nothing. A rejected profile commit restores the complete item cost and
leaves the old profile installed. No Ender Chest, container, client count, or
legacy reserve participates.

If the full cost is unavailable, no stone is removed and the existing session
transitions to normal mode. The due tick may then use the normal affinity result
without restarting preparation, so resource exhaustion never blocks the free
loop. One transition-only downgrade status is sent.

Alternative considered: consume one stone into a reusable reserve. Rejected
because it obscures the requested per-layer cost and is precisely the behavior
being retired. Prorated costs near the cap were also rejected because they
would create fractional or special-case item pricing; the final nonempty batch
costs one complete layer-priced batch and cap-clamps only its output.

### Progress caps describe target layers but do not unlock future layers

The four released source-stage caps become:

| Source stage | Immediate target | Progress cap | Stability cap/requirement |
|---|---|---:|---:|
| mortal qi-sensed | Qi I | 1000 | 500 |
| Qi I | Qi II | 1100 | 550 |
| Qi II | Qi III | 1200 | 600 |
| Qi III | Qi IV | 1300 | 650 |

The documented progression is `900 + 100 * target Qi layer`. It is descriptive
for future balancing only. Qi IV remains without a cap or advancement rule, so
the formula must not synthesize runtime definitions or unlock Qi V. Existing
durations, interruption penalties, progress reset, and no-chaining rules remain
unchanged. Authored stability requirements equal the derived caps, compatibility
cost values equal half those requirements, and successful runtime transition
retains half of the actual current stability.

### H adds controls without becoming an authority boundary

The H screen keeps one stable panel and adds `Profile` and `Meditation` tabs.
The Profile tab presents the existing profile information plus spiritual
affinity and omits legacy reserve. The Meditation tab presents the latest
server status, progress/cap, affinity-based normal output, the fixed spirit
output, the source stage's displayed stone cost, dynamic stability cap and
locked/active consolidation rate, mastery context, and four widgets: normal,
spirit, stop, and advance.

Each button sends only its existing enumerated action through the same bounded
payload used by V/B/G/N. It sends no displayed number. Displayed eligibility,
cost, or rate may be stale and is never trusted: the server re-resolves the
profile, registry definitions, inventory, cap, mode, and advancement rule.
Buttons may be presentation-disabled when the latest cache clearly makes an
action irrelevant, but this is not a security check. Existing configurable
shortcuts remain available.

The screen uses fixed responsive bounds, stable tab/button dimensions,
translatable labels, and the established sharp-content rendering order. Client
cache state clears on disconnect; missing profile/registry/status data renders
an unavailable state rather than fabricating authority.

Alternative considered: create a menu/container protocol for the screen.
Rejected because no slot transfer or durable UI session is needed and it would
duplicate the established intent/state architecture.

## Risks / Trade-offs

- **[The loop has two sequential bars]** At default affinity, qi-sensed normal
  meditation fills 1000 progress in 1000 eligible ticks, then 500 stability in
  another 500 ticks. Spirit meditation shortens only the first segment, so it
  remains acceleration rather than a replacement for consolidation. Real-client
  acceptance must still judge whether this cadence feels too short or repetitive.
- **[Spirit mode is inventory-expensive]** Direct per-half-second costs can
  consume stacks quickly, especially at Qi III. The UI must show the current
  server-derived expectation clearly, and acceptance must inspect exact counts.
- **[Near-cap spirit batches waste nominal output]** A nonempty final batch pays
  full cost even if fewer than 50 points fit. This keeps pricing deterministic;
  the UI and docs must not promise prorating.
- **[Legacy reserve becomes stranded data]** Preserving but hiding reserve avoids
  loss today, at the cost of carrying an inert field until a separate migration
  policy is approved.
- **[Client presentation can lag server truth]** Buttons and displayed values are
  advisory. Server revalidation and transition/rejection feedback prevent stale
  UI from becoming an exploit or silent mutation.
- **[Ten-tick commits increase mutation frequency]** Settlement must skip writes
  when no profile field changes and send only meaningful snapshots; profiling
  and bounded server smoke must guard against avoidable packet/write churn.

## Migration Plan

1. Add v3 domain/codec/snapshot support and exhaustive v1/v2 migration tests
   before switching the current encoder.
2. Update every profile constructor, copy, initiation, lifespan, advancement,
   debug, and settlement path to preserve affinity and reserve.
3. Replace caps and settlement logic, then update focused validators and tests
   so mixed old/new arithmetic cannot pass.
4. Add the tabbed H presentation and intent buttons after the action protocol is
   pinned; retain keyboard controls as a fallback.
5. Update README, KB/index, AGENTS/spec guidance, visual acceptance evidence,
   real-client ledger, and synchronized version metadata to `0.25.0`.
6. Run strict OpenSpec, all five playable-loop validators plus foundation and
   initiation validators, validator tests, Gradle tests/build, jar inspection,
   CRAFT/front-door checks, and bounded stage-1 server smoke before review.

Rollback of code may return to v2 behavior, but saves already written as v3
cannot be read by v2 code. Therefore release rollback requires either retaining
the v3 decoder in the rollback build or restoring pre-upgrade backups; silently
resetting v3 profiles is forbidden.

## Open Questions

None for implementation. The deliberately unresolved product question is
whether the sequential progress/consolidation cadence feels good in real play;
that requires recorded client acceptance rather than another unverified formula
change.
