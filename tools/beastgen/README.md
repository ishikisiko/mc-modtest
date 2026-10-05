# Beast art generator

Builds a beast's cuboid model, texture, glow layer and keyframe clips from one Python definition,
and renders offline previews of them. Run from the repository root:

```bash
python3 -m tools.beastgen build demon_wolf            # write the four runtime files
python3 -m tools.beastgen build demon_wolf --check    # fail when the files on disk differ
python3 -m tools.beastgen preview demon_wolf          # out/preview/demon_wolf/ (sheets, GIFs, index.html)
/usr/bin/python3 -m unittest tools.tests.test_beastgen
```

`build` and the tests use the standard library only. `preview` needs numpy and Pillow and re-runs
itself under `.venv-preview/bin/python` (or `$MC_PREVIEW_PYTHON`), like `tools.combat_preview`;
`--only views,atlas,sheets,gifs,index` renders a subset (the GIFs take about a minute).

Outputs (schema 1, see the beast contract):

| File | Content |
|---|---|
| `assets/myvillage/beast/<beast>_model.json` | bones (parents first) and cubes, vanilla `ModelPart` semantics |
| `assets/myvillage/beast/<beast>_animations.json` | clips as vanilla `AnimationDefinition` keyframes |
| `assets/myvillage/textures/entity/<beast>/<beast>.png` | the painted atlas |
| `assets/myvillage/textures/entity/<beast>/<beast>_eyes.png` | glow layer, transparent except emissive texels (the wolf: eyes, mane blade top edges, tail tip) |

Never hand-edit them; change the definition and rebuild. A model may set `model.scale` (written as
the optional `scale` field, omitted at 1): the renderer scales the whole model by it, which is how
`tools/npcgen` authors a humanoid at twice the texel density.

## Modules

| Module | Role |
|---|---|
| `cuboid.py` | bones, cubes, a port of `ModelPart.Cube` (box unwrap, mirror), the skyline UV packer, model JSON |
| `builder.py` | writes definitions in one design space (ground y = 0, up -Y, front -Z, the beast's left +X) |
| `paint.py` | one record per texel: bone, cube, face, bone-local and rest-pose model-space point, normal, distance to the nearest other cube; colour helpers |
| `anim.py` | clips and keys, the animations JSON, a port of `KeyframeAnimations.animate` and the pose chain |
| `quadruped.py` | planar two-bone leg IK with a held paw angle, foot paths and looping gait sampling; `lunge_flight`/`move_flight`, a port of the server's `BeastMotion` (vanilla gravity and drag), which gives the landing tick the clips key on and the path the previews move the beast along |
| `preview.py` | numpy renders through `tools/combat_preview`'s rasteriser, with `LivingEntityRenderer`'s placement |
| `defs/<beast>.py` | the beast: `build_model()`, `legs()`, `paint(texel)`, `glow(texel)`, `clips(model, server)` |

A second quadruped is a new `defs/<name>.py` plus its name in `build.DEFINITIONS`. The painter
colours texels from where they sit in 3D, so markings continue across cube seams; details (eyes,
nose, teeth, claws) are pinned by bone-local coordinates plus face. Mirrored twins (`uv_from`) share
the source island and get `mirror: true`, which vanilla turns into the mirror image.

Move clips read their ticks from `data/myvillage/beast/<beast>.json` at build time; every key pose of
a move sits on a server tick (`turn_lock_tick`, `lunge.tick`, `active_ticks`, `total_ticks`, and for a
lunge with `up` > 0 the landing tick `move_flight` computes: 28 for the pounce, apex 1.057), and the
clip length is `total_ticks / 20`. A retune of those ticks or of `lunge.up` needs only a rebuild. Move
clips carry posture only: the server's lunge moves the entity, so a clip never adds height or forward
travel of its own.

Previews move the entity along the server's path (aimed at the middle of `use_range`), with the
feet path drawn in orange. A move with a jump also gets `clip_<move>_arc.png` (a strobe of every tick
from the coil to touch-down in one fixed window) and `clip_<move>_landing.png` (close side views of the
touch-down tick by tick; the bottom row models the vanilla client before `BeastEntity` drew moves
tick-for-tick, kept as the reference for that fix). A ground lunge (`up` 0) gets
`clip_<move>_footwork.png` instead: close side views through the slide and every recorded step.

## Notes for the Java loader

- Every channel of a clip has a keyframe at every key time of that clip; times lie in
  `[0, length]`. Loops repeat their first key at `length`.
- Rotations are degrees (`degreeVec`), positions use `posVec` (+y up), scales are absolute
  (`scaleVec`, 1 = unchanged). Scale channels exist (ribcage breathing, mane plates bristling).
- Bones have rest rotations (neck -10, head +10, legs, ears, plates, tail). Apply look yaw and
  pitch on top of the head's rest pose (`initialPose` plus the look angles); assigning
  `head.xRot = pitch` would drop the head's rest pitch.
- Reset every part to its initial pose each frame before applying clips, as vanilla
  `HierarchicalModel` subclasses do; clips are offsets and add up.
- `idle` animates no body or leg bone, so it adds cleanly under everything. The move and stagger
  clips solve the legs themselves; running `walk`/`run` at the same time adds a second set of leg
  angles, so suppress `animateWalk` while a move or stagger clip plays.
- `animateWalk(clip, limbSwing, limbSwingAmount, maxAnimationSpeed, scaleFactor)`: one cycle covers
  `5 * length / maxAnimationSpeed` blocks of travel (while `4 * speed < 1` block per tick). Feet do
  not slide when that equals the stance travel `stride / 16 / duty` (`WALK`/`RUN` in the
  definition): about 5.3 for `walk` and 1.6 for `run` on the demon wolf. A scale factor around 2.5
  gives full weight at walking speed.
- The glow layer is designed for additive blending (`RenderType.eyes`): the base texture's eye
  texels are dark teal, and the sum reads pale cyan; the mane and tail-tip texels add a faint teal so
  the outline still reads in low light.
- During a move the drawn position and the clip are on the same server tick, which is what the move
  clips are authored for (pose keys on server ticks, the entity's own travel and arc supplied by the
  server): `BeastEntity.lerpTo` applies each position packet in one step while a move is shown
  (vanilla's 3-step lerp would trail the arc and leave the beast about 0.7 blocks up on the landing
  tick), and move clips are sampled `MOVE_CLIP_DELAY_TICKS` (2) behind the newest synced move tick,
  the tick whose position is being drawn; under `/tick freeze` the clip shows the server's move tick
  exactly. A clip author can therefore put a paw down on the tick the server lands or stops the
  beast and keep a planted paw fixed in the world by moving it back exactly as the server moves the
  entity (`quadruped.move_flight`).
- Previews light the model with the game's two fixed entity lights for a body yaw of 135 degrees;
  `lighting.png` shows the brightest and darkest headings and dim light.
