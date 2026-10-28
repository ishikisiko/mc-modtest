# 七城 v2 的 V9 世界与打包交接

本文件只记录共同约定第 4 项：世界版本、正式资源聚合、打包及必要运行时兼容。工作区为 `/tmp/mc-default-cultivation-20261028`，分支为 `feature/province-capitals-redesign-20261029`，实施起点为 `a223aafc2c150654f01b6f9f8e4ed9eedf8f8937`。本实施者继承本轮 `serviceTier=default`，未派子代理，未提交、切分支、合并或推送。

## 已落实的行为

新世界使用独立的 `myvillage:planned_v9`、`myvillage:planned_source_v9`、`myvillage:worldplan_v9`。V9 的七州主城来自 v2 owning exporter；中州直接从旧输出根读取同一份 v1 资源，不复制到新根。八城 `city_id` 保留，七个新布局族和生活身份由 [rules_v9.json](../blueprints/worldplan/rules_v9.json) 显式映射，不修改旧设计目录来冒充 v2。

V9 完整继承正式 `frozen_v8.json`；只替换 `algorithm`、`capitals.bundles` 并增加 `capitals.assets`。世界、统一选址、融合、出生、聚落/宗门避让、候选耗尽和 life 预算均继承原字段。生活位置仍限 192，原结构上限和 LifeRules/LifeCodec 断言未改。中州继续 `absolute originY=0`。

原 V1–V8 codec、注册键、frozen 与 v1 资源不迁移。V8 即使还有尚未生成的城市区块，也继续持有 v1 清单及其 terrain/NBT 引用；不会因升级而读取 v2。`WorldPlanRules` 对 V9 沿用现有 `capitals` 分支、聚落和压缩规则。`Genesis`、`Entrants.rogueHaunt`、`GatePlacement` 的原 V8 分支显式加入 V9，旧 V1–V7 分支不变。

`CapitalGameBundle` 只允许两种明确的 manifest 版本：v1、v2。terrain 与 tile 必须匹配 manifest 的版本及 `cityId`，仍拒绝任意命名空间、路径穿越、跨城或跨版本混用。现有 v1 路径语法、身份投影和摘要计算保留。清单 8 MiB、压缩地形 16 MiB、切片 8 MiB、heightfield 精确解压长度、runtime NBT 32 MiB 记账等既有边界没有扩大。

地图接口仍是 `generator.plan().cities()` / `CapitalCity.entrance()`；本实施者不修改 `ModPayloads`、地图 UI 或网络 payload。mod 版本、README/CHANGELOG/KB 由父代理维护。

## 正常生成与源集边界

正常 owning 入口顺序：

```bash
python3 tools/worldplan/generate_assets.py
python3 tools/province_capitals/game/compile_v9.py
```

第二条默认先调用未修改的旧 `game/compile.py`，保留完整 V8/v1，再调用 `regional/v2/export_game.py --out build/generated/province_capitals_v9/resources`，最后独立聚合 V9。第一条沿原实现获取其聚落前置产物；正式 Gradle 仍有对应显式依赖。

旧 V8 输出已经由正常前置任务生成时：

```bash
python3 tools/province_capitals/game/compile_v9.py --skip-legacy-export
```

七城 v2 也已经导出时，只聚合现有正式资源：

```bash
python3 tools/province_capitals/game/compile_v9.py \
  --out build/generated/province_capitals_v9/resources \
  --legacy-out build/generated/province_capitals/resources \
  --aggregate-only
```

此入口先逐一核对全部八份旧 v1 清单、terrain 和 NBT 与 `frozen_v8` 的 manifest/bundle 摘要，再读取七份 v2 及中州 v1。缺城、版本/族/生活身份不符、摘要或边界失败时抛错，不生成不完整的 V9 冻结规则。`--out` 与 `--legacy-out` 不能相同。

Gradle 的新增依赖为 `generateProvinceCapitalV9Assets → generateProvinceCapitalAssets → generateWorldPlanAssets`；原 worldplan 的湾口/阡镇前置依赖保留。新增任务声明 v2 authored 输入、V8/V9 规则、各 exporter/聚合器、公共 buildgen、NBT 工具、方块 catalogue 及旧正式资源树为输入，独立 V9 资源树为输出。Gradle 调用新入口的 `--skip-legacy-export`，避免重复执行前置 V8 导出。

| 资源根 | 打包内容 | 默认 cultivation.json |
|---|---|---|
| `build/generated/worldplan/resources` | 原 V1–V7 frozen、模板及其它原有资源完整保留 | 仅在打包时排除 |
| `build/generated/province_capitals/resources` | 原 V8 frozen、`capitals/v1`、`structure/province_capitals/v1` 保留 | 仅在打包时排除 |
| `build/generated/province_capitals_v9/resources` | 七城 `capitals/v2`、`structure/province_capitals/v2`、`frozen_v9` 和新 index | 唯一打包的默认预设 |

旧根若留有作者早期用默认 `--out` 生成的 v2 文件，不从旧源集打包这些路径，防止影子资源。新根只打包上表列出的 V9 路径，不能夹带另一个中州 v1 或旧 frozen。磁盘上的两个旧 preset 不删除、不覆盖；`processResources` 的来源过滤只决定 jar 中唯一默认项。

开发阶段缺少正常 `frozen_v7` 时，可先核验已批准的缓存，再使用旧聚合器的现有 `--base-rules`，不得改写旧聚合器或生产默认参数：

```bash
sha256sum build/province_capitals/base-0.100.2/data/myvillage/worldplan/frozen_v7.json
python3 tools/province_capitals/game/compile.py \
  --out build/generated/province_capitals/resources \
  --base-rules build/province_capitals/base-0.100.2/data/myvillage/worldplan/frozen_v7.json \
  --aggregate-only
```

缓存 V7 应为 `aef9c75e066b037d297e4fa2f10a54875ab22611571d96e42a2ea05adbcdb325`，同缓存根的 `source.json` 记录来源 jar。本实施批次已只读核对缓存 SHA 和来源记录存在，未运行缓存聚合、未重新导出旧八城。缓存仅提供已核验旧资源，不是当前源码正常构建证据。正式 Gradle 不读取该缓存，仍正常生成旧资源。

## 原始基线与诊断差异

修改前保留了 `build/province_capitals/v9-integration/legacy-before.json`，逐文件记录当前已有旧资源 SHA；`legacy-json-before/` 另保留 13 份原始 JSON，供后续逐字段比较。该基线实际含 6179 个文件：6158 个 v1 NBT、20 个 v1 城市/辅助文件、1 个 `frozen_v8.json`。本地正常 worldplan 输出当时没有 V1–V7 frozen，因此不能宣称这份基线覆盖了全部旧版本；这些旧版本仍须最终正常生成/打包检查。

现有 V8 冻结规则的原始文件 SHA 是 `c157eaddb2238c9ee15bb03a35afa5559e62230e1020c1fa9dd101a49aae5d35`，规范化规则身份为 `8fef106e9ab3ffbebd9bd61e6d72cea952476fc97e15e5f403aecd07041c1826`。Java 回归源固定后一身份。V9 聚合始终只读旧根。

本批 V9 authored 规则文件 SHA 为 `81c4f09bee32990f4d0f0f1b760f4f444832184593db1f717f2e4b4ffbb983de`。这只是本实现输入身份，不是尚未聚合成功的 `frozen_v9` 身份；共享未提交源码不能等同最终固定验收候选。

只读比较入口：

```bash
python3 tools/province_capitals/game/compile_v9.py \
  --audit-only \
  --legacy-baseline build/province_capitals/v9-integration/legacy-before.json
```

比较器逐文件核对集合及原始 SHA。旧 frozen、terrain、NBT 和其它辅助资源一律要求原始字节相同。仅对下面两类 JSON 差异逐字段出具 `before/after`：

- `city.json` 的 `source.hashes[实际源文件路径]`：原有键不能删除，变更值必须与当前源文件 SHA 相符，其余内容和运行时身份投影相同。新增 v2 注册引起的 `tools/buildgen/groups.py` 或 `tools/buildgen/ops.py` 摘要变化属于这里；不能顺带允许几何、入口或生活锚点变化。
- 唯一可显式核验的新增来源是原 v1 exporter 已有的可选 `exmod/mod_block_catalog.json`。必须传入 `--catalogue-sha256 <已核验摘要>`，实际文件与声明摘要完全一致，前后均为 `vanilla` 且 `external_catalogue_required=false`，并且新增 hash 与 `source.modset.external_catalogue: null → 固定路径` 成对出现。未给摘要、任意其它来源增删、来源标记不配对均拒绝。
- 旧 `capitals/v1/index.json` 的 `raw_city_json_sha256` 必须对应保留原件及当前实际清单。逐城 `bytes` 必须分别等于该版本清单长度加实际 terrain/NBT 长度，`total_bytes` 必须等于逐城之和；来源记录增大清单时只报告这个可精确重算的差值，其余 index 字段不变。manifest/bundle 及旧 frozen 身份不随诊断摘要和文件计数变化。

本实施批次在没有重新导出旧资源的情况下执行过此比较：6179 个文件字节相同，`diagnostic_changed_files=[]`。这只是当时共享产物的观察，不能覆盖后续正常导出。后续若来源诊断变化，应保留比较器逐字段输出，不能继续把全部文件称为字节一致。

父会话固定源 `098e8ad1` 的正常 Colab 构建实际带有 catalogue，而原本地基线没有该可选文件；原审计因此退出1，原日志保留在 `reports/province-capitals-v9-validation-20261029/legacy-audit-first.log`。九份JSON的来源及字节统计差异已独立核对，旧几何与运行时身份不变。以上显式 catalogue 核验和字节重算是针对该实际失败的工具修正；默认严格拒绝未经声明的新增 catalogue。修正后的原入口仍须在固定源下复验，不能把补充诊断当作此前入口通过。

```bash
python3 tools/province_capitals/game/compile_v9.py \
  --audit-only --legacy-baseline build/province_capitals/v9-integration/legacy-before.json \
  --catalogue-sha256 56f6844e00d975f01483332590ee62bb5399b62c92a0e74618a35baf6f9f7c24
```

## 已执行与待执行

已执行的轻量检查：

- 新 Python 聚合器可解析；focused Python 回归最终 6 项通过。范围为独立输出、七 v2/一 v1、旧字节保护、诊断字段不改变运行时身份、跨城/跨版本拒绝、缺城拒绝、精确解压长度与 192 life 上限、逐字段诊断审阅和二进制漂移拒绝。小 fixture 的切片字节仅用于摘要测试，不是 NBT 格式或游戏证据。
- 一次真实 `--aggregate-only` 尝试退出 1：缺少 `build/generated/province_capitals_v9/resources/data/myvillage/capitals/v2/capital_beiyuan/city.json`。到该失败点前，全部旧 V8 资源身份已核对。原始完整失败保存在 `build/province_capitals/v9-integration/aggregate-attempt-01.log`，没有覆盖或写成通过。
- 旧资源只读比较 6179 文件一致；改动 diff 未发现空白错误。

轻量回归命令：

```bash
python3 -m unittest tools.province_capitals.game.test_compile_v9 -v
```

新增 Java 回归源留给父代理安排的最终 Terra 检查：

- `ProvinceCapitalV9FocusedTest`：V8 精确规范化身份、V9 继承字段、中州相同引用、七个 v2 身份、有界入口、冻结保存回读、旧 frozen/资源存在、默认预设唯一、所有 tile/terrain 实际摘要、错误版本映射拒绝。
- `CapitalGameBundleVersionTest`：保留 v1 路径语法，同时验证 v1/v2 白名单与跨城/跨版本/穿越路径拒绝。
- `ProvinceCapitalV9AuthorityTest`：V9 的 192 life 创建/恢复与溢出拒绝、旧 128 容量、Genesis/Entrants/山门的八州语义；不增加交易测试。

本批次 Java 单测、javac、Gradle、jar、Minecraft、云端全部 `not_run`。V9 正式冻结规则/index/preset 尚未成功聚合，不能给出最终七城摘要、声称已打包或称新世界通过。七城 owning exporter 和资源齐备后，父代理可执行上面的正常导出/聚合；若本实现者在交接前取得资源，则补记一次真实结果，不借用其它候选证据。

父代理的必要基础范围仍是固定源编译/JAR、相关资源身份与边界、代表世界生成与入口安全、旧版本资源/旧档保持，以及独立地图作者的确认/取消传送。交易细节不扩展。外观、整城步行、手感、音效、FPS、多人和玩家接受均保持 `not_verified`；本文件没有截图交付，逐城最终画面由城市/生成作者及父代理负责。

## 本实施者写入文件

以下清单不包含共享工作区其他作者的城市、style、groups、地图、payload、版本或文档修改。

- `build.gradle`
- `blueprints/worldplan/rules_v9.json`
- `tools/province_capitals/game/compile_v9.py`
- `tools/province_capitals/game/test_compile_v9.py`
- `src/main/java/com/example/myvillage/worldplan/WorldPlanRules.java`
- `src/main/java/com/example/myvillage/worldplan/capital/CapitalGameBundle.java`
- `src/main/java/com/example/myvillage/worldplan/capital/CapitalGameRules.java`
- `src/main/java/com/example/myvillage/worldplan/runtime/PlannedBiomeSource.java`
- `src/main/java/com/example/myvillage/worldplan/runtime/PlannedChunkGenerator.java`
- `src/main/java/com/example/myvillage/worldplan/runtime/WorldPlanRuntime.java`
- `src/main/java/com/example/myvillage/sim/engine/Genesis.java`
- `src/main/java/com/example/myvillage/sim/engine/Entrants.java`
- `src/main/java/com/example/myvillage/sim/engine/GatePlacement.java`
- `src/test/java/com/example/myvillage/worldplan/ProvinceCapitalV9FocusedTest.java`
- `src/test/java/com/example/myvillage/worldplan/capital/CapitalGameBundleVersionTest.java`
- `src/test/java/com/example/myvillage/sim/engine/ProvinceCapitalV9AuthorityTest.java`
- `docs/province-capitals-v9-integration.md`

忽略产物仅包括上述旧资源基线/原始 JSON 副本、失败日志、Python 缓存；未编辑旧 v8 聚合器、旧城市生成器或派生城体。
