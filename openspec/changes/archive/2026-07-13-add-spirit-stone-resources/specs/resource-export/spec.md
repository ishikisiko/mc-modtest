## ADDED Requirements

### Requirement: Spirit-stone resources are packaged without expanding structure export
The practical jar SHALL package the hand-authored item, blockstate, model,
texture, language, loot-table, block-tag, configured-feature, placed-feature,
and NeoForge biome-modifier resources for the three spirit-stone ids. The
existing structure NBT exporter, place/gallery functions, and generated
structure paths SHALL remain unchanged.

#### Scenario: The resource slice is packaged
- **WHEN** the practical jar is built
- **THEN** every declared spirit-stone client/data resource SHALL be present
- **AND** no generated structure template SHALL be required for ore placement

#### Scenario: Structure export is compared
- **WHEN** generated structure outputs are compared before and after this change
- **THEN** no difference SHALL be caused by the spirit-stone resources

### Requirement: Acquisition documentation accompanies usable content
README command and acceptance guidance SHALL list the three `/give` ids, the
iron-tier harvest rule, Silk Touch/Fortune behavior, and the new-chunk natural
generation limitation before handoff.

#### Scenario: A reviewer receives the artifact
- **WHEN** the spirit-stone jar is prepared for review
- **THEN** the documented command surface SHALL be sufficient to obtain and test all three entries
