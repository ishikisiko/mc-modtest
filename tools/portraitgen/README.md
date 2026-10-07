# portraitgen — 64×64 anime pixel busts for the world ledger's people

Owner 2026-10-07: "考虑做几十个像素风格人像…" → generated preview rejected ("不好看") → a hand-drawn
sample (`old/hand.py`, rendered as `out/preview/cultivator/portraits/hand/hand3.png`) was approved:
"大致方向对了，好多了，但是可以优化一点，然后你让子代理画吧". The generator is now that sample
cut into parts; every variant is hand-placed pixels in the same style, and the person's palette
colours them.

## Pipeline

`person.py` turns a ledger record into a `Choice` (deterministic, splitmix64 of the id) →
`render.compose` paints parts onto a `pix.Grid` of **roles** (letters) → `roles.palette` resolves
roles to colours for this person → PNG. Order: hair behind → neck and face → blush, nose, mouth →
eyes → side locks → bangs → shared finish (forehead shadow, hair outline, hair/skin line) → brows
through the bangs → coat → knot → headwear → marks → grey for the dead.

| module | owns | variants |
|---|---|---|
| `parts_face.py` | face shape, neck, skin shade, blush, nose, mouth | face oval/round/sharp (× male jaw), mouth smile/neutral/small/smirk/frown/open, old-age marks |
| `parts_eyes.py` | eyes, brows | eye round/almond/sharp/droopy (× gender), brow arched/straight/angry/worried |
| `parts_hair_front.py` | bangs | hime/split/sweep/curtain/spiky |
| `parts_hair_back.py` | silhouette, dome + highlight, side locks, ends, knot/buns, ears | long/half_up/short/ponytail/twin_buns/bun |
| `parts_body.py` | coat + collar, headwear, marks | robe plain/dyed/brocade/court × m/f, headwear none/cloth/headband/ribbon/crown/phoenix_pin/jade_pin, bandage, scar |

The default variant of every module reproduces the approved sample pixel for pixel
(`tools/tests/test_portraitgen.py`).

## Roles (see `roles.py`)

skin `S s T K L`, blush `B b`, hair `H h D I J`, brow-through-bangs `R`, eyes `W w E e 1 2 3 4 P X x`,
mouth `M m`, coat `O c C Q`, trim `g G`, inner `u N`, sect accent `a A`, jade `Z z`, cord `V`,
bandage `F f`, scar `Y`. `.` is transparent. Parts never choose colours.

## Geometry contract (canvas 64×64, centre x = 31.5)

- Hair dome top y 1 (y 0 for a knot); face skin x 16..47 between y 14 and the chin tip at y 50;
  cheeks widest y 24..38; neck x 27..36 from y 47; coat from y 54, full width by y 60.
- Eyes: outer corners x 19 and x 44, rows 28 (crease) .. 38 (lower lid); highlights at the
  top-left of each iris in canvas terms (light from the top-left). Brows rows 25..27, drawn after
  the bangs in role `R`. Nose (33, 41..42). Mouth rows 44..46. Blush rows 39..41.
- Bangs: from y 10 inside the dome to pointed tips at y 23..30 over x 19..44. Side locks cover
  x ≤ 18 and x ≥ 45 from y 12. The dome is solid to y 13.
- Headwear: knot area y 0..3 x 27..36; right flank (40..46, 2..6); brow band rows 11..13.

## Style rules (what the owner approved; measured from 37 reference portraits in `../../out/preview/cultivator/portraits/refs/NOTES.md`)

- 二次元: big eyes (a third of the face height), thick lash line with an outer flick, iris in four
  tones dark→pale with a 2×2 highlight and a glint, eye white all round; pointed chin; blush ovals;
  tiny nose (two shade pixels); small mouth.
- Hair: five tones. Lit stripes on the left of locks, deep crevices on the right, a zigzag angel
  ring on the dome, shadow with a thin rim light on the right flank. Locks end in points.
- Outlines are the material's own deep tone (`D`, `K`, `O`), never black. Skin three tones plus
  outline; shade under the bangs, on the right cheek, under the jaw; a hard dark band on the neck.
- Read as the same person across variants: a variant changes one thing (the shape of the eye, the
  cut of the bangs) and keeps the rest of the contract.

## Checking your work

```
/usr/bin/python3 -m tools.portraitgen.preview_layer face|eyes|hair_front|hair_back|body|all --out out/preview/cultivator/portraits/v2/<layer>.png
/usr/bin/python3 -m unittest tools.tests.test_portraitgen tools.tests.test_portraitgen_face tools.tests.test_portraitgen_eyes tools.tests.test_portraitgen_hair_front tools.tests.test_portraitgen_hair_back tools.tests.test_portraitgen_body
/usr/bin/python3 -m tools.portraitgen sheet --ledger out/preview/cultivator/portraits/sample/ledger.json --out out/preview/cultivator/portraits
```
The sheet command writes the people PNGs, the sheets, `index.html` (served on 8766) and `artifact.html`
(the same page as an Artifact body; the owner cannot see the server, so publish it).

## Shared finish (render.py), decided after the agents' reports

- The forehead shadow under bang tips only paints skin roles and never under front hair.
- Side locks shade the jaw beside them by one pixel from row 30 down.
- Brows are role `R` (hair light mixed with sheen) where they lie on hair and `h` on skin.
- `n` is the inside of an open mouth. Greying hair mixes 38% towards silver.
- A style whose back hair ends at the jaw subtracts the face from `ctx["hair_back"]` in its `locks`
  hook so the outline follows the jaw (parts_hair_back `_clear_face`).
Open the PNG and look at it at 4× before calling a variant done. `python3` without PIL: use
`/usr/bin/python3`.

## Into the game (0.45.0)

The drawing stays here; Java composes. `export.py` writes every part variant as a role map
(`src/main/resources/assets/myvillage/portrait/<layer>/<variant>.png`, R = role index + 1, G = the
step's mask, plus `manifest.json`) by replaying `render.compose` with a journal of every cell a step
writes, in the context of the base person. `replay.py` composes from those maps with the shared
rules and is the executable spec of the Java `PortraitComposer`; `tools/tests/test_portraitgen_export.py`
proves it equals `render.render` pixel for pixel (base people, every layer variant, 80 random people).
Goldens for the Java tests: `src/test/resources/portrait_goldens/` (8 specs, `0.png` = the approved
sample), `portrait_assign_vectors.json` (240 cases for `PortraitAssign`), `portrait_palette_vectors.json`
(60 for `PortraitPalette`). After changing any part or rule: re-export, rerun the Python tests, then the
Java tests. The full contract: `docs/portrait-java-contract.md`.

```
/usr/bin/python3 -m tools.portraitgen.export          # maps + goldens
```
