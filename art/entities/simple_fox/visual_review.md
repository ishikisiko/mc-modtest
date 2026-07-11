# Simple Fox Visual Review

Status: `accepted`

## Evidence

- `atlas_preview.png`: nearest-neighbor 16x atlas enlargement on a checkerboard.
- `texture_validation.json`: dimensions, alpha, UV occupancy and palette checks.
- `references/concept_builtin.png`: adopted Codex built-in imagegen direction sheet.
- `texture_faces.json`: cuboid `part.face` map used to review directional details.
- `texture_patches.json`: face-local native coordinates for the paired eyes and nose; the validator confirms the runtime pixels exactly.
- Runtime target: `assets/myvillage/textures/entity/simple_fox/simple_fox.png`.

## Critical Self-Review

- The enlarged atlas was inspected with the local image viewer. Orange, cream and dark regions are distinct and hard-edged, but this is not a model render.
- The cream body underside is a large uninterrupted rectangle. It may look mechanically flat rather than like a soft belly once wrapped onto the rotated body.
- The face combines two dark eyes, two lower dark cheek pixels and a separate two-pixel nose. At model scale those marks may collapse into an over-busy or moustache-like face.
- The tail transition uses a regular alternating row before a solid cream band. It may read as a sawtooth stripe, and seam continuity has not been demonstrated on the rotated tail cuboid.
- A single texture means the inherited sleeping pose does not receive closed-eye pixels like the vanilla sleep texture.
- These source-atlas risks were carried into the in-game checklist rather than treated as automatically resolved by mechanical validation.

## Built-in Imagegen Verdict

- Codex built-in imagegen produced one coherent four-view concept sheet without an API key or SDK; it is retained as the approved color and proportion reference.
- The first atlas pass used the exact work base, concept, semantic overlay, and binary guide. It passed size, alpha, mask, and palette checks after deterministic composite, but treated the complete head UV island as one planar face. The paired eyes landed on different cuboid faces, so the candidate was rejected.
- A second atlas pass added a 48-face `part.face` overlay and explicit `head.north`, `muzzle.north`, belly, paw, and tail instructions. Tail and paw direction improved, but the head details still crossed the front and adjacent faces. It also passed mechanical validation and was rejected on UV semantics.
- This test shows why mechanical atlas validation cannot replace model-space judgment. For a native 48x32 atlas, imagegen is useful for concept and broad material direction, while one-to-three-pixel eyes, nose, seams, and other directional marks require deterministic native-resolution placement or Blockbench painting.
- The runtime texture therefore remains the deterministic local atlas. Rejected raw candidates are identified by SHA-256 in `art_provenance.json` but are not tracked.

Automated texture checks alone do not resolve these aesthetic risks. The owner completed the in-game review and accepted the result on 2026-07-12.

## Runtime Smoke Evidence

- `./gradlew test` and `./gradlew build` passed for version `0.21.0`; the jar contains the runtime/client classes and every declared entity resource.
- `runAcceptanceServer` reached `Done` with `MyVillage 0.21.0`, loaded recipes/data packs, and did not resolve client classes on the server.
- RCON summoned a named `myvillage:simple_fox`; the server selector found exactly one entity and read inherited `Type: "red"` data.
- After `save-all flush`, a clean server stop, and a second startup, the same UUID and inherited fox type were present. The test entity and temporary force-load ticket were then removed and the server stopped cleanly.
- The owner reported the Minecraft client validation passed, closing spawn-egg, natural-frequency, multiplayer, and rendered model/pose acceptance.
