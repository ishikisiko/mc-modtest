# Forging material tags — 0.58.1

## Implemented

- Common copper input tag: `c:ingots/copper`. Own spirit copper is not added to it.
- Independent additive item tags: `myvillage:forging_materials/xuantie`, `myvillage:forging_materials/chitong`, and `myvillage:forging_materials/edge_tempering_sand`.
- Dark-iron ore block/item classification: `c:ores/xuantie` and `c:ores`. Sand-deposit block/item classification: `myvillage:deposits/edge_tempering_sand`.
- Cost schema v2 accepts concrete IDs and `#tag` references, with v1 concrete costs retained. Inventory is allocated across overlapping requirements before any copies are committed; one unit cannot be counted twice. Client counting/display uses the same live membership.
- All infusion entry points use the common copper tag. Progress remains on the actual original input item and survives tag removal/reload and restart. Different item IDs or progress values remain separate stacks.
- Protocol 27 prevents older clients from interpreting tagged costs as item IDs.

## Evidence

Java/build source: `dfc39734c7ad897177bd75f6b95c88fbabd65de0`.
Run `20261016T181441Z-6f78d5a2`, preset `forging-build`: 41 Java tests passed, including exhaustive comparison of 6,912 small allocation graphs with an independent search, mixed-stack costs, empty tags, malformed references and legacy concrete costs. Generator checks passed. Gradle test/build took 37.982 s.

Runtime source: `45e06334672202334409e755512ef4f69391a977`.
Run `20261016T182047Z-78d1d889`, preset `forging-tags`: all 39 checks and all seven selected scenarios passed. The source delta only adds/improves test tooling and generator path validation. Every uncompressed jar entry matches the Java-tested jar exactly.

The disposable capture world's datapack added vanilla stand-ins to real item tags:
brick as copper, quartz/diamond as sharp additive, amethyst shard as tough additive,
and emerald as channeling additive. These are test fixtures, not shipped recipes or
members, and no external mod was installed or claimed as tested.

Verified through mapped menus and authoritative readback:

- Default special materials are members of their own tags; ordinary iron/copper/sand do not qualify as those special materials. Ore item and block both satisfy `c:ores`.
- An untagged input is rejected. The same item becomes eligible after tag reload, retains paid qi on pause/restart, is rejected without erasing progress when removed from the tag, and resumes with only the remaining qi after restoration.
- Infused output then supplies the channeling material tag. All three alternative additives produce their expected forged traits.
- A diamond stack matching both an exact cost and a broad additive tag is allocated correctly when quartz supplies the flexible requirement. Without quartz, the same diamonds cannot pay both requirements; the complete inventory remains unchanged.
- An emptied tag rejects even the normal MyVillage additive without fallback or partial consumption. The UI shows a missing-tag placeholder and remains usable.

Relevant local Python checks passed: forging generator/resources (11), protocol validator (27), tag output/path/preset tests (3) and Modal fixtures (20), 61 unique tests across the focused invocations. No natural-worldgen, combat or broad multiplayer rerun was needed for this change.

Both Modal runs downloaded verified evidence and confirmed Sandbox termination. The successful runtime run spent 2.494 s creating the Sandbox/image, 12.804 s uploading, 213.875 s in the remote job, 3.328 s downloading and 1.108 s terminating. Inside the remote job, jar build took 33.266 s and runtime collection 178.590 s; these are nested durations. SDK exit-status polling retried a transient connection/service error without losing the run.

## Delivery and limits

`build/libs/myvillage-0.58.1.jar`

SHA-256: `4d8aca7b5a3115987e9297c616af247a227657509d52772018286f2639a747b0`.
Generated resources match the packaged bytes; old material crafting recipes remain
absent, and the temporary alias datapack is not in the jar. Subsequent commits only
update documentation.

This proves the tag API path, not compatibility with every third-party mod's custom
item behavior. Owner visual/feel/balance/FPS and broad modpack interoperability remain
`not_verified`. UI evidence is in `out/preview/forging_tags/runtime/`.
