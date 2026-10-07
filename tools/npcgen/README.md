# NPC art generator

Builds a humanoid NPC's layered cuboid model, cut-out texture and keyframe clips from one Python
definition, and renders offline previews of them. Run from the repository root:

```bash
python3 -m tools.npcgen build cultivator            # write the four runtime files
python3 -m tools.npcgen build cultivator --check    # fail when the files on disk differ
python3 -m tools.npcgen goldens [--check]           # recolour goldens for NpcSkinComposer
python3 -m tools.npcgen preview cultivator          # out/preview/cultivator/ (sheets, GIFs, index.html)
/usr/bin/python3 -m unittest tools.tests.test_npcgen tools.tests.test_npcgen_roles
```

`build` and the tests use the standard library only. `preview` needs numpy and Pillow and re-runs
itself under `.venv-preview/bin/python` (or `$MC_PREVIEW_PYTHON`), like `tools.beastgen`;
`--only views,closeups,face,atlas,sheets,gifs,index` renders a subset (the GIFs take about two
minutes; `face` is the head large from six angles plus the same views at about in-game size).

Outputs (the beast schema-1 formats, read by `NpcRenderer`):

| File | Content |
|---|---|
| `assets/myvillage/npc/<NAME>_model.json` | bones (parents first), cubes, and `scale` |
| `assets/myvillage/npc/<NAME>_animations.json` | looping `idle` and `walk` clips |
| `assets/myvillage/textures/entity/<ENTITY>/<NAME>.png` | the painted atlas; zero alpha is a hole |
| `assets/myvillage/npc/<NAME>_roles.png` | the role map: which texels are hair or iris (see below) |

`ENTITY` and `NAME` are the definition's: the default cultivator is `cultivator`/`cultivator`, a
second look of it `cultivator`/`cultivator_<look>`.

Never hand-edit them; change the definition and rebuild.

## Role map and per-person colours

A ledger person's 3D avatar wears the hair and eye colours of their portrait (`tools/portraitgen`).
The client recolours the baked atlas through the look's role map (Java `portrait/NpcSkinComposer`):

- `roles.py` finds the role texels by probing: it paints the look again with `HAIR` (the def's
  global and the painter's attribute) swapped for grey probe ramps and with `IRIS_TOP`/`IRIS`/
  `IRIS_LOW` swapped for probe colours, and records which texels changed and to what. A role pixel
  with alpha 0 keeps the baked texel; `(1, i, 0, 255)` is hair at half-step tone `i` (0..12) on the
  7-step ramp; `(2, i, 0, 255)` is iris row `i` (0..2, dark to pale), or 3 for the pale row mixed
  half way toward `EYE_WHITE` (`#F8FAFF` in every def). Hair must only ever be `_tone(HAIR, ...)`
  and the iris only the three constants (or that one white mix): anything else raises.
- `recolour.py` is the rule the Java composer implements: the portrait's 5-step hair ramp resampled
  to 7 steps (`hair_ramp7`), the iris rows from its eye ramp, every mix rounding half to even.
- `python3 -m tools.npcgen goldens` writes `src/test/resources/npc_skin_goldens/` (12 recoloured
  atlases covering every hair and eye colour, plus the `ramp7`/`iris` vectors) that pin the Java
  side pixel for pixel. Rebuild them after any change to a look's texture.

## Modules

| Module | Role |
|---|---|
| `build.py` | builds a definition in memory, writes or checks the four files; `DEFINITIONS` lists the definitions (one per look), and the file paths come from each definition's `ENTITY` and `NAME` |
| `humanoid.py` | parts shared by every humanoid look: `_box`, `_pair`, `_rgb`; paint maths (`_clamp`, `_mix`, `_tone`, `_hash`, `_xhz`, `_form`, `_weave`, `_fret`); clip helpers `_rx`, `_loop` and the gait `_leg_angle(phase, swing)`, `_sole_low(angle, hip, sole_z)`; `HumanoidPaint`, the painter base with the skin materials (`light`, `skin`, `neck`, `hand`, `nose`, `skull`, `jaw`) |
| `roles.py` | the role map of a built look, by probing the painter (`export`), and `repaint` with other ramps |
| `recolour.py` | the per-person recolour (`compose`, `hair_ramp7`, `iris`) and the goldens writer |
| `shade.py` | `Occluders`: baked contact shading from the rest-pose geometry (`overhang`: what sticks out above a texel; `contact`: raised geometry beside it); cubes named hollow cast nothing |
| `preview.py` | turnaround, close-ups, face sheet, scale beside the player, atlas, clip sheets, GIFs, index page, through `tools/beastgen/preview.py`'s rasteriser with `cull=False`: `NpcModel` draws with `entityCutoutNoCull`, so back faces are drawn and the inside of a cut-out shell shows through its holes as in game |
| `defs/<npc>.py` | one look of an NPC: `build_model()`, `painter(model)`, `clips(model)`, and `ID`, `ENTITY`, `NAME`, `LOOK`, `HITBOX`, `HOLLOW` |

The cuboid model, box-UV packer, texel sampler and clip classes are `tools/beastgen`'s
(`cuboid.py`, `builder.py`, `paint.py`, `anim.py`).

## Writing a definition

- **Start from the shared parts.** A new look imports `_box`, `_pair`, the paint maths, the gait
  functions and `HumanoidPaint` from `humanoid` instead of copying them, and declares `ENTITY` (the
  entity's texture directory), `NAME` (the files' prefix, also its entry in `build.DEFINITIONS`) and
  `LOOK` (the `NpcEntity` look id). Its painter subclasses `HumanoidPaint`, sets the `SKIN`/`HAIR`
  ramps and head measurements, and adds the face, the hair and the clothes.
- **Units.** Set `model.scale = 0.5` and author in units of 1/32 block: a 60-unit figure is 1.875
  blocks tall. Cube sizes stay whole numbers (one texel per unit); origins may be fractional, which
  is how a band sits a fraction behind the shell in front of it without two faces sharing a plane.
- **Layers are cubes.** Anything that should stand proud (a collar band, a border, a belt, a cuff
  drape, a nose, a strand of hair) is its own cube, on its own bone when it should swing. Keep at
  least a quarter unit between faces that would otherwise share a plane, so they do not flicker at a
  distance.
- **Shells are cut out.** A cube whose painter returns `None` for some texels is a shell (hair round
  a face, a vest open at the front). List it in `HOLLOW`: it then casts no baked shadow, and its
  painter shades what shows through. The texture's alpha is always 0 or 255.
- **Right owns, left mirrors.** Paired parts are built with `_pair`: the right (-X) cube owns the UV
  island and the left reuses it with `mirror`. The painter only ever sees right-side texels.
- **Paint from position.** The painter gets each texel's rest-pose point, normal, bone-local point
  and face (`tools/beastgen/paint.py`). Shade with `_form` (a box lit as if round), `Occluders`
  (shadows from the layer above, crevices), then add folds, dye and trim as functions of position so
  they run on across cube seams. Entity faces are drawn at 50 to 74 % brightness unless they face
  up: keep base tones high, and paint up-facing ledges darker than the cloth beside them.
- **Odd widths give a centre column.** The cultivator's head is 13 texels wide so the nose and
  the mouth sit on one centre column, and the jaw keeps it as it narrows in odd widths (11, 9, 7)
  toward the chin.
- **Judge a face at in-game size.** `preview <npc> --only face` draws the head large from six
  angles and again at about 70 pixels wide. What reads up close can turn into something else a few
  blocks away: a brow stepping up over three texels reads as an angry zigzag, a lash bar under a
  brow bar as heavy lids, a dot between the brows as a Buddha's urna. Faces facing up (the top of a
  nose) draw at full brightness, so paint them darker than the face.
- **Walk on a planted foot.** `NpcRenderer` measures how fast the lowest sole corner moves back and
  sets the `animateWalk` rate from it. Author the walk so the planted foot stays on the ground and
  travels evenly (`_leg_angle`, `_sole_low` in `humanoid`), and turn hanging panels with the leg
  under them.
