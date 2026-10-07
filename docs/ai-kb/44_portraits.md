# 44 · Portraits (命簿人物像素头像)

Status 2026-10-07: 0.45.0 + 0.45.1 (3D avatar hair and eye colours) on feat/sect-entry. Owner direction: "考虑做几十个像素风格人像，在和 NPC 交互或者控制面板点击的时候展示" → a generated first version was rejected ("不好看") → a hand-drawn 64×64 anime sample was approved ("大致方向对了") → the parts were hand-drawn per module by subagents → "接入完整".

## What the player sees

- The sect dialogue (守山执事 / 长老, `SectDialogueScreen`) shows the speaker's 64×64 bust at the left, name and role under it.
- 天下 → 人物: the person detail card opens with the bust at the top-left; every person row carries a 16×16 thumbnail.
- The same ledger record always gives the same face (deterministic), and the face changes with the person: robe by realm tier, headwear by rank, greying and age marks by age over lifespan, eye colour by the dominant root element, brows and mouth by the strongest personality trait, bandage and scar by injury, grey when dead.

## Pipeline

| stage | where | notes |
|---|---|---|
| drawing | `tools/portraitgen/parts_*.py` | hand-placed row spans and pixel lists painting **roles** (letters) on a 64×64 `pix.Grid`; one module per layer (face, eyes, bangs, back hair, body); `old/hand.py` + `old/sample.png` are the approved sample |
| choice | `tools/portraitgen/person.py` ↔ `com.example.myvillage.portrait.PortraitAssign` | ledger record → `Choice` / `PortraitSpec`; splitmix64 of the person id for the free parts; parity vectors `src/test/resources/portrait_assign_vectors.json` |
| palette | `tools/portraitgen/roles.py` ↔ `PortraitPalette` | role → colour per person (skin tone, hair ramp incl. greying, eye ramp, robe tier × gender, sect accent); `mix` rounds half to even; vectors `portrait_palette_vectors.json` |
| maps | `tools/portraitgen/export.py` → `assets/myvillage/portrait/` | 172 role maps + `manifest.json`; each map = every cell one compose step wrote, in the base person's context; keys that depend on neighbours are composite (`bangs/<front>_<back>`, `headwear/<hw>_<back>`, `marks/bandage_<front>_<back>`, `marks/scar_<back>`) |
| compose | `tools/portraitgen/replay.py` ↔ `PortraitComposer` | paint maps in order, four shared finish rules (forehead shadow, lock shade, hair outline, hair/skin line), brows through bangs, dead greying; goldens `src/test/resources/portrait_goldens/` |
| client | `com.example.myvillage.client.portrait.PortraitTextures` | compose on first use, `DynamicTexture` 64×64 + 16×16 thumbnail, LRU cache |
| network | `PersonSummary.portrait`, `SectDialoguePayload.portrait` | a 13-byte `PortraitSpec` per person, built server-side from `PersonView` (which now carries the five traits) |

Preview and regeneration: `/usr/bin/python3 -m tools.portraitgen sheet --ledger out/preview/cultivator/portraits/sample/ledger.json --out out/preview/cultivator/portraits` (48 real people, part sheets, index.html + artifact.html); `-m tools.portraitgen.export` after any drawing change, then `python-tests` and `./gradlew test --tests 'com.example.myvillage.portrait.*'`.

## 3D avatar colours (0.45.1)

Owner: "游戏建模虽然不和像素画对应 … 我想让建模的发色和瞳色与像素画的对应". The avatar's baked texture is recoloured on the client so hair and eyes match the portrait; everything else stays as baked (three looks).

- `tools/npcgen` writes a role map per look (`assets/myvillage/npc/<name>_roles.png`): which texels are hair (and at which half-step tone of a 7-step ramp) and which are the iris rows. It is derived by probing the painter with sentinel ramps, so nothing is hand-marked and an undecodable construct fails the build. `recolour.py` is the rule; `test_npcgen_roles` proves the role map reproduces the baked texture with the look's own ramps.
- Java: `NpcColours` (hair, eye of the `PortraitSpec`, packed int, `NONE`), `NpcSkinComposer` (pure; `hairRamp7` resamples the portrait's 5-step hair ramp to 7, iris rows from `PortraitPalette.eyes`), `NpcEntity.colours()` synced + NBT `Colours`, `WorldSimAvatars` sets it at spawn and reconcile, client `NpcSkins` caches one `DynamicTexture` per (look, colours) and `NpcRenderer` asks it; reload clears it (and the portrait caches).
- Why recolour at runtime rather than tint a layer: the painter bakes form light and strand streaks into the hair tones; swapping the ramp keeps all of that and gives the exact portrait colours (including greying), while a layer tint would multiply one hue over the baked shading.

## Rules that are not obvious from the code


- Variants are stamps captured in the base person's context, so a stamp may depend on neighbours. When a replay mismatch appears, key the stamp by the neighbour (that is how bandage got `<front>_<back>`); `test_portraitgen_export` is the detector.
- A back style whose hair ends at the jaw subtracts the face from its silhouette mask (`clears_face` in the manifest) so the outline follows the jaw; long styles keep the face inside the mask.
- The approved sample is the regression anchor: the default woman must keep its coverage (`test_portraitgen`), and `portrait_goldens/0.png` is that sample.
- The owner cannot see `out/preview`; an offline preview page goes out as an Artifact (`artifact.html` is the same page without a doctype), one per topic and republished. In-game results need no Artifact: the owner checks them in the game on their PC (2026-10-07).
- Known limits: a failed map load logs once and every portrait becomes a magenta 64×64 placeholder; the dialogue heading (name · role · sect) is not clipped, so a very long sect name can run past the 306 px text column.

See also: `docs/portrait-java-contract.md` (incl. the 3D avatar colours section), `tools/portraitgen/README.md`, `tools/npcgen/README.md` (role maps), [39_humanoid_npcs.md](39_humanoid_npcs.md) (the 3D cultivator look), [40_world_sim.md](40_world_sim.md) (the ledger).
