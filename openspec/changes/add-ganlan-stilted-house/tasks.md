## 1. Reference And Contract

- [x] 1.1 Validate `candidate_005/breakdown.json` and preserve it as reference
  provenance only; do not copy upstream Ganlan NBT or jigsaw resources into
  shipped outputs.
- [x] 1.2 Prepare owner-facing visual-review evidence for the Ganlan reference
  slice and keep the acceptance state pending until owner verdict.

## 2. Generator Implementation

- [x] 2.1 Add a `ganlan_stilted_house` style/profile and settlement group binding
  with a dedicated raised-floor stilt-house layout strategy.
- [x] 2.2 Implement the original Ganlan stilt-house sample generator with raised
  floor, support posts, open underside, veranda/entry access, deep-eave roof,
  and wet-ground context.
- [x] 2.3 Route stilt support, veranda edge, and deep-eave cues through
  registry-backed form hooks or equivalent metadata-driven operations, not
  style-prefix branching.

## 3. Validation And Tests

- [x] 3.1 Add Ganlan validation checks for raised floor, support posts,
  underside openness, raised-entry access, veranda/deep-eave cues, and reference
  provenance.
- [x] 3.2 Add focused tests covering group binding, validation failures, and
  generation of at least one passing Ganlan sample.

## 4. Resources And Documentation

- [x] 4.1 Generate the Ganlan sample structures, place functions, gallery
  function, and report under the implemented `myvillage` namespace.
- [x] 4.2 Update README command/usage docs, KB notes, and relevant acceptance
  docs for the new sample family.
- [x] 4.3 Bump the small-feature version and update `gradle.properties`,
  `src/main/resources/META-INF/neoforge.mods.toml`, README jar-name examples,
  and `CHANGELOG.md` together.

## 5. Verification And Handoff

- [x] 5.1 Run focused generator tests and Ganlan validation.
- [x] 5.2 Run generated-structure validation, preview generation, and visual
  acceptance report.
- [x] 5.3 Run a practical jar build.
- [x] 5.4 Inspect representative preview PNGs and stop for owner visual verdict
  before claiming acceptance.
