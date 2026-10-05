# NPC art generator

Builds a humanoid NPC's layered cuboid model, cut-out texture and keyframe clips from one Python
definition, and renders offline previews of them. Run from the repository root:

```bash
python3 -m tools.npcgen build cultivator            # write the three runtime files
python3 -m tools.npcgen build cultivator --check    # fail when the files on disk differ
python3 -m tools.npcgen preview cultivator          # out/preview/cultivator/ (sheets, GIFs, index.html)
/usr/bin/python3 -m unittest tools.tests.test_npcgen
```

`build` and the tests use the standard library only. `preview` needs numpy and Pillow and re-runs
itself under `.venv-preview/bin/python` (or `$MC_PREVIEW_PYTHON`), like `tools.beastgen`;
`--only views,closeups,atlas,sheets,gifs,index` renders a subset (the GIFs take about two minutes).

Outputs (the beast schema-1 formats, read by `NpcRenderer`):

| File | Content |
|---|---|
| `assets/myvillage/npc/<npc>_model.json` | bones (parents first), cubes, and `scale` |
| `assets/myvillage/npc/<npc>_animations.json` | looping `idle` and `walk` clips |
| `assets/myvillage/textures/entity/<npc>/<npc>.png` | the painted atlas; zero alpha is a hole |

Never hand-edit them; change the definition and rebuild.

## Modules

| Module | Role |
|---|---|
| `build.py` | builds a definition in memory, writes or checks the three files; `DEFINITIONS` lists the NPCs |
| `shade.py` | `Occluders`: baked contact shading from the rest-pose geometry (`overhang`: what sticks out above a texel; `contact`: raised geometry beside it); cubes named hollow cast nothing |
| `preview.py` | turnaround, close-ups, scale beside the player, atlas, clip sheets, GIFs, index page, through `tools/beastgen/preview.py`'s rasteriser |
| `defs/<npc>.py` | the NPC: `build_model()`, `painter(model)`, `clips(model)`, and `ID`, `HITBOX`, `HOLLOW` |

The cuboid model, box-UV packer, texel sampler and clip classes are `tools/beastgen`'s
(`cuboid.py`, `builder.py`, `paint.py`, `anim.py`).

## Writing a definition

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
- **Odd widths give a centre column.** The cultivator's head is 13 texels wide so the nose, the
  mark between the brows and the mouth sit on one centre column.
- **Walk on a planted foot.** `NpcRenderer` measures how fast the lowest sole corner moves back and
  sets the `animateWalk` rate from it. Author the walk so the planted foot stays on the ground and
  travels evenly (`_leg_angle`, `_sole_low` in the cultivator), and turn hanging panels with the leg
  under them.
