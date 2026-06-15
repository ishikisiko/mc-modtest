## ADDED Requirements

### Requirement: Slot loading is namespace-aware under a modset profile
The generator SHALL support loading a style profile against an active set of namespaces (a modset profile). `load_style(style_id, available_namespaces)` SHALL filter every material slot list to entries whose block id namespace is in `available_namespaces`, at load time, before any downstream resolution. The existing single-argument `load_style(style_id)` behavior SHALL be preserved as the default (all listed entries active). The `primary()`, `alternates()`, `pick()`, `slot_entry()`, and related slot-resolution contracts SHALL behave identically on the filtered profile, so downstream build operations require no changes.

#### Scenario: Loading under the vanilla profile
- **WHEN** a style is loaded with `available_namespaces = {"minecraft"}`
- **THEN** every slot list SHALL retain only its `minecraft:` entries
- **AND** `primary()`, `alternates()`, and `pick()` SHALL operate over the filtered entries with their existing contracts.

#### Scenario: Loading under the full profile
- **WHEN** a style is loaded with `available_namespaces` containing the confirmed external mod namespaces plus `minecraft`
- **THEN** slot lists SHALL retain both mod and vanilla entries in their declared order.

#### Scenario: Default loading is unchanged
- **WHEN** `load_style(style_id)` is called without an `available_namespaces` argument
- **THEN** it SHALL load the profile with all listed slot entries active
- **AND** existing callers SHALL observe no behavior change.

### Requirement: Every material slot ends with a vanilla fallback
Each material slot list in every style profile SHALL end with a guaranteed `minecraft:` (vanilla) block id. After namespace filtering, a slot SHALL therefore always retain at least its vanilla fallback entry, so resolution under any modset profile never yields an empty required slot or places air. Optional slots that a style legitimately omits remain governed by the existing omit-and-skip behavior.

#### Scenario: A slot resolves under an empty mod set
- **WHEN** a style is loaded with `available_namespaces = {"minecraft"}` and a build operation resolves a slot that also lists mod entries
- **THEN** the slot SHALL resolve to its trailing vanilla fallback id
- **AND** no resolution SHALL return air or raise an empty-slot error.

#### Scenario: A style profile omits the trailing vanilla fallback
- **WHEN** a style profile defines a required material slot whose last entry is not a `minecraft:` id
- **THEN** the fallback-convention check SHALL flag that slot
- **AND** the violation SHALL identify the style id and slot name.

### Requirement: Style profile schema recognizes mod-target decoration slots
The style profile schema SHALL recognize additional optional material slots `ROOF_TILE`, `PAPER_LANTERN`, `RITUAL_ANCHOR`, and `MARKET_FITTINGS` alongside the existing slots. In this change these slots are declared with only their vanilla fallback entry (no mod ids yet). A style profile MAY omit any of these slots, in which case generators referencing the missing slot SHALL skip placement of that slot's blocks, consistent with existing optional-slot handling.

#### Scenario: A style declares the new decoration slots with vanilla fallbacks
- **WHEN** a style profile that defines the new slots is loaded
- **THEN** `ROOF_TILE`, `PAPER_LANTERN`, `RITUAL_ANCHOR`, and `MARKET_FITTINGS` SHALL each be present
- **AND** each SHALL contain at least one trailing `minecraft:` fallback entry
- **AND** generation under the `vanilla` profile SHALL place only those vanilla fallbacks.

#### Scenario: A style omits a new decoration slot
- **WHEN** a style profile does not define `RITUAL_ANCHOR` and a generator requests it
- **THEN** placement using that optional slot SHALL be skipped rather than failing style loading.
