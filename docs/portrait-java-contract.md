# Portraits in the game: the contract between tools/portraitgen and the Java side

Owner 2026-10-07: "接入完整". The 64×64 anime busts (see `tools/portraitgen/README.md`) go into the
sect dialogue and the 天下 page. The drawing stays in Python; Java composes from exported part maps.

## Pieces

| piece | where | owner |
|---|---|---|
| `PortraitSpec` record (enums, 13-byte buf codec) | `com.example.myvillage.portrait.PortraitSpec` | done |
| `PortraitAssign`: ledger record → spec | `com.example.myvillage.portrait.PortraitAssign` | server agent |
| traits on `PersonView`; spec on `PersonSummary`, in `SectDialoguePayload` | sim / net | server agent |
| part maps + manifest + goldens | `src/main/resources/assets/myvillage/portrait/`, `src/test/resources/portrait_goldens/` | exporter (`tools/portraitgen/export.py`) |
| `PortraitPalette`, `PortraitComposer` (pure), `PortraitTextures` (client cache) | `com.example.myvillage.portrait` / `client.portrait` | client agent |
| dialogue screen, 天下 person detail and rows | `client.sim.SectDialogueScreen`, `client.cultivation.panel.WorldPage` | UI agent |

## PortraitAssign (port of `tools/portraitgen/person.py`)

Inputs: id, female, realm id, rank, sect id, age in years (`(day - birthDay) / daysPerYear`; for the
dead `(deathDay - birthDay) / daysPerYear`), root (5 basis points, metal wood water fire earth; the
dead have none → treat as all equal), traits (ambition aggression caution wanderlust loyalty; the
dead → all 50), injury, alive. Constants exactly as in person.py: REALMS order
`mortal, qi_refining, foundation_establishment, golden_core, nascent_soul`; tier = clamp(index-1, 0, 3)
(unknown realm → index 1); LIFESPAN 80/120/240/500/1000 (unknown → 120); `age_fraction = age / lifespan`;
dominant element = argmax of root, ties to the lowest index, `spirit` for nascent_soul; mood = the
argmax trait when ≥ 70 (ties to the lowest index) else `calm`. `mix(seed, salt)` is splitmix64:
```
z = seed * 0x9E3779B97F4A7C15 + salt * 0xBF58476D1CE4E5B9      (64-bit wrapping)
z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9
z = (z ^ (z >>> 27)) * 0x94D049BB133111EB
return (z ^ (z >>> 31)) & 0x7FFFFFFFFFFFFFFF
pick(seed, salt, options) = options[mix(seed, salt) % len(options)]
```
Then the steps of `assign()` in person.py, salts 1..13, in order, with the same option lists
(`FACE_SHAPES`, `SKIN_TONES`, `HAIR_COLOURS` = ink_blue, ink_violet, dark_brown, chestnut, `FRONTS_M/F`,
`BACKS_M/F`, `EYE_SHAPES_F/M`, the mood tables). Parity vectors: `src/test/resources/portrait_assign_vectors.json`
(240 cases; a JUnit test must pass every field).

## Part maps (written by the exporter)

`assets/myvillage/portrait/manifest.json`:
```
{"roles": "SsTKLBbHhDIJRWwEe1234PXxMmnOcCQgGuNaAZzVFfY",   // index i+1 in the PNG's red channel = roles[i]
 "layers": {"behind": ["short", ...], "face": ["oval_f", "oval_f_old", ...], ...},
 "clears_face": ["short", "ponytail", "twin_buns", "bun"]}   // back styles whose silhouette mask excludes the face
```
Each map is `assets/myvillage/portrait/<layer>/<variant>.png`, 64×64 RGBA: **R** = role index + 1
(0 = this step painted nothing here), **G** = 255 when the cell is in the step's *mask* (face mask,
locks mask, bangs mask, silhouette), **A** = 255 where R or G is set. A map holds every cell one step of
`render.compose` wrote (whether or not the role changed), captured in the context of the base person; painting it means
"for every cell with R > 0, set the role".

Layers and variant keys (`<g>` = f|m; `<old>` suffix `_old` when the spec is old):
`behind/<back>` · `face/<face>_<g>[_old]` · `blush/normal|old` · `nose/<g>` · `mouth/<mouth>[_old]` ·
`eyes/<eye_shape>_<g>` · `locks/<back>` · `bangs/<front>_<back>` · `brows/<brow>_<g>` (R channel = role R marker) ·
`coat/<robe>_<g>` · `knot/<back>` · `headwear/<headwear>_<back>` (none → no file) · `marks/bandage_<front>_<back>`, `marks/scar_<back>`.

## Compose (port of `tools/portraitgen/render.py` + `replay.py`, which is the executable spec)

Order, on a 64×64 grid of roles ('.' = transparent):
1. `behind` → silhouette mask = its G channel (not its painted cells: a style may paint a few cells outside its mask), minus the face mask when the back style is in `clears_face`.
2. `face` (+`_old`) → face mask = G channel. 3. `blush` (if blush). 4. `nose` (if nose). 5. `mouth`.
6. `eyes`. 7. `locks` → locks mask = G. 8. `bangs` → bangs mask = G.
9. finish_hair: (a) for each bangs-mask cell whose cell below is not in `front = locks ∪ bangs` and is in the
   face mask: set that cell to `s` if it holds one of `S L B b`; the cell two below likewise if in the face and
   not in front. (b) for each locks-mask cell with y ≥ 30: the cell one step toward the centre
   (x+1 when x < 32 else x−1), if in the face and not in front and holding `S`, becomes `s`.
   (c) every cell of `all_hair = silhouette ∪ front` with a 4-neighbour outside all_hair becomes `D`.
   (d) every front cell with a neighbour below, right or left that is in the face and not in front becomes `D`.
10. `brows`: each marker cell becomes `R` if it currently holds one of `H h D I J`, else `h`.
11. `coat`. 12. `knot`. 13. `headwear` (if not none). 14. `marks/bandage_<front>_<back>` (if bandage), `marks/scar_<back>` (if scar).
15. colours: role → RGBA from the palette (port of `roles.py`: SKINS, HAIRS incl. `grey_*` = mix 0.38 to silver,
    EYES, ROBES, ACCENTS, FIXED, and the derived B b R M m n). `mix` rounds half to even (`Math.rint`).
16. dead: grey = 0.3r + 0.59g + 0.11b (int), colour = (grey*0.72+8, grey*0.72+10, grey*0.72+18).
Goldens: `src/test/resources/portrait_goldens/<n>.png` with `portrait_goldens.json` (spec per golden);
`0.png` is the owner-approved sample. A JUnit test composes each spec and compares pixel for pixel.

## Client

`com.example.myvillage.client.portrait.PortraitTextures`: `ResourceLocation texture(PortraitSpec)` (64×64) and
`ResourceLocation thumbnail(PortraitSpec)` (16×16, box-filtered 4×4 over opaque pixels, transparent where
none), composed on first use and cached (LRU, ~256), `DynamicTexture` registered with the texture manager.
Part maps are read once from the resource manager (`NativeImage.read`), roles kept as `byte[64*64]` per map.
Drawing: `VectorBrush.texture(...)` or `GuiGraphics.blit` at integer GUI pixels, no smoothing.

## Network

`PersonSummary` gains `PortraitSpec portrait` (last field; codec writes 13 bytes); `PersonDetail` carries it in
its summary; `SectDialoguePayload` gains `PortraitSpec portrait` after `avatarName`. The server builds it in
`WorldSimSnapshots.personSummary` and `SectDialogue.send` via `PortraitAssign.of(PersonView, day, daysPerYear)`.

## UI

Dialogue: panel width up to 400; left column = portrait 64×64 at the panel's top-left padding, the name
(gold) and the role (muted) under it; right column = the existing heading, subheading, divider and lines;
buttons unchanged; the `SECT_DIALOGUE option=` log lines unchanged (the evidence scripts click them).
天下 person detail: portrait at the top-left of the 人物 card, the first rows (name line, realm bar, root)
start 70 px to the right and the rest continues full width below the portrait. Person rows: a 16×16
thumbnail at the left, row height 18, text shifted 20 px.

## 3D avatar colours (0.45.1)

The cultivator avatar of a ledger person wears the portrait's hair colour and eye colour (owner 2026-10-07:
"我想让建模的发色和瞳色与像素画的对应"). Hair style, headwear, robe and skin stay as baked into the look.

- **Role map.** `tools/npcgen` writes, next to each look's model files, `assets/myvillage/npc/<name>_roles.png`
  (same size as the texture `textures/entity/cultivator/<name>.png`, RGBA). Alpha 0 = keep the baked texel.
  Alpha 255 = recolour: R = material, G = index, B = 0. Material 1 hair: G = the painter's half-step tone
  0..12 on a 7-step ramp, colour `mix(ramp7[i/2], ramp7[i/2+1], (i%2)*0.5)` (12 = the last step). Material 2
  iris: G = 0..2 = the iris row dark → pale = the portrait's iris ramp (`PortraitPalette.eyes`) at that row;
  G = 3 = the pale row mixed half way toward the eye white `#F8FAFF`. The exporter (`tools/npcgen/roles.py`)
  finds the texels by probing the painter with sentinel ramps, so a construct it cannot decode fails the build
  instead of being missed; `tools/npcgen/recolour.py` is the executable rule and `test_npcgen_roles` proves
  that recolouring with the look's own ramps reproduces the baked texture pixel for pixel.
- **ramp7.** The portrait's 5-step hair ramp (`PortraitPalette.hair`, greying forms included) resampled to the
  painter's 7 steps: step `j` samples at `p = 2j/3`, `mix(ramp5[floor p], ramp5[floor p + 1], p − floor p)`
  (`NpcSkinComposer.hairRamp7`). No darkening: npcgen keeps base tones high because entity faces draw at
  50–74 % brightness. Every mix rounds half to even (`PortraitPalette.mix`).
- **Wire.** `NpcColours(hair, eye)` packs to `hair.ordinal() << 4 | eye.ordinal()`; `NpcEntity` syncs it as one
  int (`colours()`, `packedColours()`, `setColours`), `-1` = none (keep the baked colours: every summoned NPC);
  saved as NBT `Colours` when set, so `/summon myvillage:cultivator ~ ~ ~ {Colours:68}` works for stills.
  `WorldSimAvatars` sets it at spawn and on reconcile from `NpcColours.of(PortraitAssign.of(person, day,
  daysPerYear))`, so greying follows age exactly as in the portrait.
- **Client.** `NpcRenderer.getTextureLocation` → `NpcSkins.texture(npcId, look, packed, baked)`: a
  `DynamicTexture` per (look, packed) composed by `NpcSkinComposer.compose(base, roles, colours)` (pure, in
  `com.example.myvillage.portrait`; goldens under `src/test/resources/npc_skin_goldens/` pinned from Python),
  the baked texture when there are no colours, no role map for the look, or any failure (logged once). A
  resource reload (`F3+T`) drops the composed textures and the portrait caches (`PortraitTextures.clear`).
