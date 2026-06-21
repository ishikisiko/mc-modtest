## ADDED Requirements

### Requirement: Vernacular Chinese style shares the cultivation massing slots
The `PLATFORM_STONE` and `COLUMN` slots defined by this capability SHALL be reusable by the vernacular Chinese courtyard style (`chinese_courtyard`) in addition to the cultivation monumental styles. The slot definitions and the column-rendering path SHALL be the same; only the populated block lists differ (vanilla-only for the vernacular style; vanilla plus 灵材 for the cultivation sect style).

#### Scenario: Vernacular and monumental styles share slot semantics
- **WHEN** the `chinese_courtyard` and `cultivation_sect` styles are both loaded
- **THEN** both SHALL declare the `PLATFORM_STONE` and `COLUMN` slots
- **AND** the column-rendering code path SHALL be identical for both styles
- **AND** only the populated block lists SHALL differ.
