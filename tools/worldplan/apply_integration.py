"""One-time, checked integration of the isolated development clone; removes itself after use."""
from pathlib import Path
import json
import subprocess

def replace(path,old,new):
    p=Path(path);s=p.read_text();assert s.count(old)==1,(path,old[:100],s.count(old));p.write_text(s.replace(old,new))

base='src/main/java/com/example/myvillage/'
replace(base+'MyVillageMod.java','        SectStructures.register(modEventBus);','        SectStructures.register(modEventBus);\n        com.example.myvillage.worldplan.runtime.WorldPlanRuntime.register(modEventBus);')
replace(base+'region/runtime/RegionGraph.java','        List<GenEdge> edges) {','''        List<GenEdge> edges,
        RegionGeometry geometry) {

    /** Preserve the legacy constructor and byte-identical legacy canonical JSON. */
    public RegionGraph(long seed, String rulesetId, int count, IntRange tierRange, int tierStep,
                       List<GenRegion> regions, List<GenEdge> edges) {
        this(seed, rulesetId, count, tierRange, tierStep, regions, edges, null);
    }
    public RegionGraph {
        regions = List.copyOf(regions);
        edges = List.copyOf(edges);
    }''')
replace(base+'region/runtime/RegionGraph.java','        root.put("edges", edgeTree());','''        root.put("edges", edgeTree());
        if (geometry != null) {
            TreeMap<String, Object> shape = new TreeMap<>();
            shape.put("seed", geometry.seed());
            shape.put("radius", geometry.radius());
            shape.put("warp_scale", geometry.warpScale());
            shape.put("warp_amplitude", geometry.warpAmplitude());
            shape.put("reservations", geometry.reservations());
            root.put("geometry", shape);
        }''')
replace(base+'region/runtime/RegionQueries.java','if (!RegionPlacement.isWithinBoundedArea(worldX, worldZ)) {','if (Math.hypot(worldX, worldZ) > (graph.geometry() == null ? RegionPlacement.RADIUS_WORLD : graph.geometry().radius())) {')
replace(base+'region/runtime/RegionQueries.java','        GenRegion nearest = null;','''        double[] q = graph.geometry() == null ? new double[] {worldX, worldZ}
                : graph.geometry().warp(worldX, worldZ);
        GenRegion nearest = null;''')
replace(base+'region/runtime/RegionQueries.java','double dx = center[0] - worldX;','double dx = center[0] - q[0];')
replace(base+'region/runtime/RegionQueries.java','double dz = center[1] - worldZ;','double dz = center[1] - q[1];')
replace(base+'region/runtime/RegionRuntimeService.java','''            RegionCatalogLoader.RegionData data = RegionCatalogLoader.loadFromResourceManager(
                    server.getResourceManager(), MyVillageMod.MOD_ID);
            long seed = overworld.getSeed();
            RegionGraph graph = RegionTopologyGenerator.generate(seed, data.ruleset(), data.catalog());''','''            long seed = overworld.getSeed();
            var planned = com.example.myvillage.worldplan.runtime.WorldPlanRuntime.generator(overworld);
            RegionGraph graph;
            if (planned.isPresent()) {
                graph = planned.get().plan().graph();
            } else {
                RegionCatalogLoader.RegionData data = RegionCatalogLoader.loadFromResourceManager(
                        server.getResourceManager(), MyVillageMod.MOD_ID);
                graph = RegionTopologyGenerator.generate(seed, data.ruleset(), data.catalog());
            }''')
replace(base+'sim/engine/GatePlacement.java','        long minSq = (long) g.minSpacing() * g.minSpacing();','''        // Reserve planned city sites before ledger compounds are sited.
        if (ctx.graph.geometry() != null && ctx.graph.geometry().reserved(x, z, g.minSpacing())) return false;
        long minSq = (long) g.minSpacing() * g.minSpacing();''')
replace('tools/world_sim_cli.py','"RegionContract", "RegionJson", "RegionTopologyGenerator", "Ruleset", "RegionProfile",','"RegionGeometry", "RegionContract", "RegionJson", "RegionTopologyGenerator", "Ruleset", "RegionProfile",')
path=base+'wilderness/runtime/WildernessRuntime.java'
replace(path,'        return RegionRuntimeService.regionAt(pos.getX(), pos.getZ())','''        var planned = com.example.myvillage.worldplan.runtime.WorldPlanRuntime.context(level,pos.getX(),pos.getZ());
        if(planned.isPresent())return Optional.of(planned.get().profileId());
        return RegionRuntimeService.regionAt(pos.getX(), pos.getZ())''')
replace(path,'        if (beast.wildernessBirth().isEmpty()) assign(beast, region.get(), level.getSeed(), beast.getRandom().nextLong(), true);','''        if (beast.wildernessBirth().isEmpty()) {
            var planned = com.example.myvillage.worldplan.runtime.WorldPlanRuntime.context(level,pos.getX(),pos.getZ());
            if(planned.isPresent() && !planned.get().core()) {
                var context=planned.get();
                RealmKey realm=WildernessRules.bundled().region(context.profileId()).orElseThrow().beast()
                        .sample(level.getSeed(),context.regionId(),beast.getRandom().nextLong());
                beast.assignWildernessBirth(new BeastBirth(realm,context.regionId(),pos.getX(),pos.getY(),pos.getZ(),true));
            } else assign(beast,region.get(),level.getSeed(),beast.getRandom().nextLong(),true);
        }''')
replace(path,'        result.put("region", region.orElse(""));','''        result.put("region", region.orElse(""));
        com.example.myvillage.worldplan.runtime.WorldPlanRuntime.context(source.getLevel(),pos.getX(),pos.getZ())
                .ifPresent(c -> result.put("worldplanContext",c));''')
replace(path,'            Optional<WorldSim> sim = WorldSimRuntime.sim();','''            boolean outer=com.example.myvillage.worldplan.runtime.WorldPlanRuntime.context(source.getLevel(),pos.getX(),pos.getZ()).map(c->!c.core()).orElse(false);
            Optional<WorldSim> sim = outer?Optional.empty():WorldSimRuntime.sim();''')
replace(path,'result.put("projectedRogues", WorldSimAvatars.wildCount(id));','result.put("projectedRogues", outer?0:WorldSimAvatars.wildCount(id));')
p=Path('src/main/resources/myvillage.mixins.json');o=json.loads(p.read_text());assert 'WorldPlanReservationMixin' not in o['mixins'];o['mixins'].append('WorldPlanReservationMixin');p.write_text(json.dumps(o,indent=2)+'\n')
p=Path('build.gradle');s=p.read_text();assert 'generateWorldPlanAssets' not in s;p.write_text(s+'''
// Generated-resource ownership remains with authored worldplan and Yunshan inputs.
tasks.register('generateWorldPlanAssets', Exec) {
    workingDir = projectDir
    executable = pythonExecutable.get()
    args 'tools/worldplan/generate_assets.py'
    inputs.files(fileTree('blueprints/worldplan'), fileTree('blueprints/yunshan'),
            fileTree('src/main/resources/data/myvillage/worldgen/region_profile'),
            fileTree('tools/worldplan'), fileTree('tools/yunshan'), fileTree('tools/buildgen'),
            files('tools/json_to_nbt.py', 'tools/voxel_dump_to_structures.py'))
    outputs.dir(layout.buildDirectory.dir('generated/worldplan/resources'))
}
sourceSets.main.resources.srcDir(layout.buildDirectory.dir('generated/worldplan/resources'))
tasks.named('processResources') { dependsOn 'generateWorldPlanAssets' }
''')
# Use a multiline JS template literal rather than a single-quoted literal for the seed label.
p=Path(base+'worldplan/WorldPlanExporter.java');s=p.read_text();assert "SEED='" in s;s=s.replace("SEED='","SEED=`").replace("'.trim();const tip","`.trim();const tip");p.write_text(s)
subprocess.run(['python3','tools/bump_version.py','0.59.0'],check=True)
p=Path('CHANGELOG.md');s=p.read_text();s=s.replace('## 0.59.0\n','''## 0.59.0

- 新增可选“修仙大陆（种子蓝图）”世界类型：4000 格核心洲域、山海边界、连续外域以及存档内冻结规则。
- 同源宏观舆图自动导出；云山四栋功能建筑按原相对位置组成 35 个区块切片，按需自然生成，不强制加载整城。
- 新增只读规划/城市核验和有界开发探针；默认世界与旧存档不自动迁移。完整古城和玩家美术验收不作已完成声明。
''');p.write_text(s)
Path(__file__).unlink()
