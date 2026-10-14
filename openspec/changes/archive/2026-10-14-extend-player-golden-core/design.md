## Context

The existing server settlement/advancement engine already follows per-stage data. Four shipped transitions end at Qi IV; higher registered stages lack gain/advancement data. The new statistics runtime validates every player stage against explicit aliases at server startup. Current panels still identify Qi IV directly as the terminal stage.

## Goals / Non-Goals

**Goals:** Make all fifteen transitions from sensed mortal through Golden Core late normally playable; preserve the original first four transitions and saved IDs; present synchronized requirements and the true terminal; verify data, service transitions, input and restart behavior.

**Non-Goals:** Nascent Soul player access, random breakthrough failure, tribulation, new pills/items, changed artwork, new network authority or changed world-ledger progression.

## Decisions

1. Extend the existing authored realm JSON. Keep `foundation_early`; add `foundation_middle`, `foundation_late`, `golden_early`, `golden_middle`, `golden_late`. Golden Core late has neither progress cap nor advancement, matching the existing terminal representation. Keeping the schema avoids a profile or payload migration.
2. Preserve existing mortal/Qi I-III values, all Qi spiritual capacities and Foundation early's 200 capacity. Continue Qi IV-IX caps at 1400..1900 in steps of 100. Foundation caps are 3000/3600/4200; Golden early/middle caps are 6000/7200. These are initial tunable defaults, not a claim of owner-approved pacing.
3. Reuse progress-then-stability settlement, full-cap eligibility and integer-floor half retention. Authored required stability remains half the source cap, and compatibility cost is half that requirement. Cross-major transitions and Qi VI->VII are bottlenecks; existing Qi III->IV is unchanged. Durations and interruption losses stay explicit stage data. No random or item gate is introduced.
4. Ship Foundation power caps 200/250/300, Golden 400/500/600 and Golden lifespan 500 years, preserving spent power and consumed lifespan across transitions. Realm health/attack continues to use the existing exponential-major/linear-minor policy with complete player aliases.
5. Derive terminal readouts from synchronized progression definitions where feasible instead of replacing one hard-coded terminal ID with another. Required registry/validator baselines describe the shipped complete ladder and protect it against cycles, missing stages, wrong targets and premature terminals.
6. Synchronize the existing active GuideME change's current-content requirement as well as the new validation delta, without archiving or claiming completion of that older integration change. This prevents a later archive from reintroducing its superseded Qi-IV boundary.
7. The first four transitions remain regression anchors. New data tests follow the entire graph and check every transition, including cross-realm health/power/lifespan behavior. Physical-client checks use mapped meditation/advance input with clearly labeled administrative preconditions, then directly restart the same capture world.

## Risks / Trade-offs

- [Higher levels could become inaccessible through a stale whitelist] -> Validate shipped realm/stage definitions, all statistics aliases, labels, panel readouts and the bilingual guide together.
- [Advancement could refill power, reset age or duplicate attributes] -> Preserve service commit semantics and test cross-realm state preservation and maximum changes.
- [Tuning is playable but not owner-tested] -> Expose exact authored tables and keep pacing/feel acceptance `not_verified`.
- [The final stage has no further progress] -> Reject gain and advancement at Golden late while keeping existing non-progression activities usable; show an explicit release ceiling.
- [Downgrade to an older jar cannot recognize new IDs] -> Keep old IDs compatible on upgrade; do not promise downgrade support for characters saved in new stages.

## Migration Plan

No profile codec or network schema changes. Existing characters keep their realm, stage, progress, stability, consumed age and current spiritual power. Former Qi-IV characters gain the newly authored progression rules on startup. Build and install the updated jar on server and clients; retain a world backup when changing mod versions.
