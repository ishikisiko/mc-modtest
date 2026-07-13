## Why

The v1 profile has no personal time cost and the server has no shared cultivation calendar, so later meditation cannot express the proposal's central tradeoff between progress and finite lifespan. This change establishes those clocks and persistence rules before adding an active meditation session.

## What Changes

- **BREAKING**: Advance `CultivationProfile` to schema version `2` with non-negative `lifespanConsumedTicks` and `meditationQiReserve`, retain an explicit v1 decoder, migrate both new values to zero, and encode only v2 afterward.
- Add required realm-defined maximum lifespan values: mortal `80`, qi refining `120`, and foundation establishment `240` cultivation years.
- Add Overworld `SavedData` for shared `elapsedCalendarTicks`; advance it once per effective server tick only while a survival/adventure player is online.
- Advance each living online survival/adventure player's lifespan independently, batch profile commits every 600 ticks, and force pending commits before logout, death, dimension change, and server stopping.
- Add server configuration defaults of `24000` effective ticks per cultivation day and `6` days per year. Changing either setting intentionally reinterprets previously accumulated raw ticks and therefore requires an operator warning.
- Add relative warnings at ten, five, and one remaining year (matching mortal ages 70/75/79), an exhausted state at the realm maximum, and no automatic death, reset, reincarnation, or item handling.
- Keep the H screen read-only while adding shared year/day, consumed/remaining lifespan, reserve, and exhausted-state display from clientbound-only snapshots.

## Capabilities

### New Capabilities
- `cultivation-lifespan-calendar`: Effective-tick calendar, personal lifespan accounting, realm limits, batching, warnings, exhaustion, configuration, and read-only display.

### Modified Capabilities
- `cultivation-player-profile`: Replace v1 as the current schema with explicit v1-to-v2 migration and two new persistent counters.
- `cultivation-definition-registries`: Make maximum lifespan required realm definition data rather than a Java constant or profile field.
- `cultivation-persistence-lifecycle`: Batch and force-flush lifespan through the existing attachment owner without adding a clone path.
- `cultivation-state-synchronization`: Send calendar/time-scale and runtime status only clientbound and not every tick.
- `cultivation-debug-commands`: Make `info` and `reset` cover the complete v2 profile without adding player-facing clock mutation commands.
- `cultivation-initiation-ritual`: Preserve both v2 counters across awakening and inheritance and narrow the ritual's historical schema-v1 exclusion.
- `cultivation-core-validation`: Cover migration, time arithmetic, SavedData, lifecycle flush, config, UI, and dedicated-server side safety.
- `validation`: Add focused lifespan/calendar validation and truthful lifecycle/manual evidence.

## Impact

This affects the profile codec and all profile-preserving services/tests, realm definitions, server config, tick/lifecycle events, Overworld SavedData, clientbound status networking, H-screen layout, validators, docs, and the coordinated release.
