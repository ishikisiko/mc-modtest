## ADDED Requirements

### Requirement: Equivalent crafting aptitude diagnostics
Administrator crafting status and single-profession setters SHALL be structurally equivalent under cultivation/xiulian and English/pinyin aliases. Setters SHALL validate the target, initialized state, profession and score bounds, preserve the other profession and replace state only through CultivationService.

#### Scenario: Invalid crafting mutation
- **WHEN** a setter supplies an invalid score or a target without initialized aptitudes
- **THEN** the command SHALL fail without changing either value
