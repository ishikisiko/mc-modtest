## ADDED Requirements

### Requirement: GuideME cultivation resources and handbook ship in the practical jar
The practical MyVillage jar SHALL package the `myvillage:cultivation` guide definition, the three Chinese default pages and three path-matched `_en_us` pages copied from authoritative root `guidebook/`, the `myvillage:cultivation_handbook` item model, and bilingual handbook language entries with the registered item implementation. Gradle SHALL map `guidebook/` into generated output at `assets/myvillage/guides/myvillage/cultivation/` without maintaining the Markdown pages under `src/main/resources`. The handbook model SHALL reuse `guideme:item/guide_base`; the jar SHALL NOT package a MyVillage handbook texture, the untracked root-level GuideME jar, or copied GuideME classes. These hand-authored client resources SHALL NOT change the structure-NBT exporter, generated structure libraries, place/gallery functions, or worldgen resources.

#### Scenario: The practical jar is inspected
- **WHEN** `./gradlew build` completes for the GuideME slice
- **THEN** the jar SHALL contain `assets/myvillage/guideme_guides/cultivation.json`
- **AND** it SHALL contain only `index.md`, `getting_started/initiation.md`, `getting_started/cultivation_loop.md`, and their three path-matched `_en_us` counterparts below the packaged cultivation guide root
- **AND** it SHALL contain the handbook model and bilingual item text
- **AND** it SHALL contain no nested GuideME artifact or MyVillage handbook texture

#### Scenario: Structure export is compared before and after guide integration
- **WHEN** the guide and handbook resources are added
- **THEN** generated NBT, settlement metadata, and place/gallery outputs SHALL remain unchanged
- **AND** no guide structure template or worldgen entry SHALL be implied

### Requirement: Guide dependency and acquisition documentation accompany the artifact
README usage and acceptance guidance SHALL identify GuideME as a required separately installed mod, document `/give @s myvillage:cultivation_handbook`, use the GuideME 21.1.17 direct-open command order `/guidemec myvillage:cultivation open`, document the `runGuide` author-preview entry point, and keep the released cultivation sequence and Qi Refining IV ceiling consistent with the packaged pages. As a small feature from the 0.25.0 baseline, the release files SHALL be synchronized at 0.25.1 under the authoritative `openspec/config.yaml` task rule before handoff.

#### Scenario: A player prepares the compatible mod set
- **WHEN** the current MyVillage jar is handed off
- **THEN** README SHALL state that a compatible GuideME installation is required on client and server
- **AND** it SHALL provide the handbook acquisition command
- **AND** its direct-open troubleshooting command SHALL place the guide id before `open`

#### Scenario: An author prepares guide preview
- **WHEN** the author follows README guidance
- **THEN** the documented Gradle entry point SHALL launch `runGuide` against authoritative root `guidebook/` without requiring a checked-in `src/main/resources` mirror
