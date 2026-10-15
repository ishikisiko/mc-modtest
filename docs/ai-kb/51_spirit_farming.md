# Spirit farming (0.53.0)

`myvillage:spirit_soil` is directly plantable, permanently fertile soil. It has no
moisture property, drying random tick, water search or farmland trampling path.
`SpiritSoilBlock.canSustainPlant` accepts only `SpiritCropBlock` above it;
spirit crops independently require spirit soil, excluding vanilla farmland.

The six existing herbs are now `ItemNameBlockItem`s pointing at `<herb>_crop`:
spirit_leaf, dew_blossom, sunroot, blood_berry, cloud_fungus and calm_lotus.
Their item IDs, alchemy recipes and icons are unchanged. No seed item exists.
Normal immature harvest returns one herb; maturity yields 2–4 of that same herb.
Loot tables own yield. Explosion decay remains vanilla. Soil itself drops soil.

Every crop has exactly `age=0,1,2`: seedling, growing, mature. Growth uses
CropBlock's server random ticks, loaded-area check, light threshold, spacing,
fertile-soil multiplier and NeoForge crop growth events. Bone meal advances one
stage. Age persists in normal blockstate/chunk saves; unloaded areas do not grow.
Spirit lotus uses the same soil and needs no standing water.

`tools/gen_spirit_crops.py` owns 18 distinct 3D stage models, six plant atlases,
soil textures/models, blockstates, loot, recipe, plant block names and tag entries.
Each plant is modeled from stems, layered leaves and its own flower/root/berry/
mushroom/lotus geometry. `tools/gen_alchemy_assets.py` still owns existing herb
inventory icons and alchemy language hints. Do not edit generated files.

Survival acquisition: one dirt + one low-grade spirit stone crafts one spirit
soil. Existing plant-plus-spirit-stone herb recipes provide starter planting
material. Wild herb worldgen is not added. README contains player steps.

Focused checks: generator `--check`, `tools.tests.test_gen_spirit_crops`, existing
item/alchemy validators, then Modal `--preset crops-build` (SpiritCropsTest + jar).
Actual-model preview: `--preview` under `.venv-preview`, output
`out/preview/spirit_crops/index.html`. Developer previews do not establish owner
appearance, growth pacing or multiplayer acceptance.

See [asset contracts](../contracts/assets.md), [cultivation contracts](../contracts/cultivation.md),
[alchemy](49_alchemy.md), [workflow](../WORKFLOW.md).

0.53.0 focused build/deployment: [verification record](../testing/2026-10-16-spirit-farming.md).

Spirit Soil item presentation uses the Minecraft 1.21.1 block defaults: GUI
rotation 30/225/0 with 0.625 scale, dropped-item scale 0.25 and normal hand/frame
transforms. Placed block geometry remains 16×16×16.
