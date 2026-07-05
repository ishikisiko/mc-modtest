## 1. Reference And Contract

- [x] 1.1 Validate `candidate_003/breakdown.json` and preserve it as reference
  provenance only; do not copy source geometry into shipped resources.
- [x] 1.2 Add CRAFT/front-door evidence for the implementation slice and keep
  owner-facing output limited to direction, validation state, and verdict.

## 2. Generator Implementation

- [x] 2.1 Add a `chinese_huipai_mansion` style/profile and settlement group
  binding with a dedicated Hui-style tianjing layout strategy.
- [x] 2.2 Implement the original Hui-style compound slice: closed street facade,
  stepped 马头墙 cue, 门堂 → 天井一 → 享堂 → 天井二 → 寝堂 sequence, and no garden
  parcel.
- [x] 2.3 Route stepped-gable and closed-facade cues through registry-backed form
  hooks or equivalent metadata-driven operations, not style-prefix branching.

## 3. Validation And Tests

- [x] 3.1 Add Hui-style validation checks for sequence, small sky-wells, closed
  facade, stepped gable cue, no-garden constraint, and reference provenance.
- [x] 3.2 Add focused tests covering group binding, validation failures, and
  generation of at least one passing Hui-style sample.

## 4. Resources And Documentation

- [x] 4.1 Generate the Hui-style sample structures, place functions, gallery
  function, and report under the implemented `myvillage` namespace.
- [x] 4.2 Update README command/usage docs, KB notes, and relevant acceptance
  docs for the new sample family.
- [x] 4.3 Bump the small-feature version and update `gradle.properties`,
  `src/main/resources/META-INF/neoforge.mods.toml`, README jar-name examples,
  and `CHANGELOG.md` together.

## 5. Verification And Handoff

- [x] 5.1 Run focused generator tests and Hui-style validation.
- [x] 5.2 Run generated-structure validation, preview generation, and visual
  acceptance report.
- [x] 5.3 Run a practical jar build.
- [x] 5.4 Inspect representative preview PNGs and stop for owner visual verdict
  before claiming acceptance.
