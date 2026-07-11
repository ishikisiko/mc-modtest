## 1. Contracts And Scope

- [x] 1.1 Classify `myvillage:rideable_flying_sword` as a functional item and schema-validate its Item Contract.
- [x] 1.2 Record the non-living vehicle contract for ownership, payload shape, movement, collision, cleanup, rendering, side safety, and first-version non-goals.
- [x] 1.3 Add and strictly validate the `rideable-flying-sword` OpenSpec capability before runtime edits.

## 2. Server Runtime

- [x] 2.1 Register the transient flying-sword entity and the stack-size-one functional item, then expose the item in `myvillage:main`.
- [x] 2.2 Implement server-only summon/recall, owner binding, one-passenger enforcement, duplicate prevention, fall reset, and lifecycle cleanup.
- [x] 2.3 Implement the six-key payload and server validation without client-provided entity identity, position, rotation, velocity, or speed.
- [x] 2.4 Implement server tick movement, input timeout, acceleration/drag, independent speed caps, horizontal yaw following, no gravity, and normal block collision.

## 3. Client And Assets

- [x] 3.1 Send bounded key-state payloads from the client and reserve Shift for descent without enabling vanilla dismount or vehicle-coordinate control.
- [x] 3.2 Register a client-only entity renderer that draws the item model horizontally without GeckoLib or custom animation.
- [x] 3.3 Add the item model, placeholder texture, and English/Chinese item and entity names.

## 4. Validation And Documentation

- [x] 4.1 Add focused unit/source/resource checks for input flags, authorization boundaries, common/client separation, registrations, and packaged assets.
- [x] 4.2 Update README command/control guidance, the validation checklist, the KB index, and a concise flying-sword technical note.
- [x] 4.3 Run strict change validation, item/entity validators, `./gradlew test`, `./gradlew build`, `./gradlew runAcceptanceServer`, and jar resource checks.

## 5. Release And Handoff

- [x] 5.1 Apply the feature version rule atomically: bump to `0.22.0` in `gradle.properties` and mod metadata, update README jar-name examples, and add the matching CHANGELOG entry.
- [x] 5.2 Re-run the release-sensitive build and dedicated-server gates after the version update.
- [x] 5.3 Prepare manual in-game checks for all controls, Shift descent, hover/drag, collision, orientation, fall safety, singleton/recall, every cleanup condition, multiplayer authority, and placeholder appearance; keep the verdict pending until observed.
