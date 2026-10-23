# 阡镇离线剧情约束生成接口

本目录提供六地点插件、公共契约和显式离线生成入口；Java 部署语义保持不变。
剧情事实仍来自 `blueprints/qianzhen/manifest.json`、`story_scene.json`、六份原蓝图；
角色/交互身份复用 `story_assets.ROLES/TARGETS`。不新增任务事实、不接入自然 worldgen
或冻结 V4、不在运行时抽样。工作方式见 [WORKFLOW](../../../docs/WORKFLOW.md)。

## 文件所有权（并行地点开发分工）

- 公共基础代理：`__init__.py`、`core.py`、本说明和
  `tools/tests/test_qianzhen_storygen.py`。
- 六个地点代理分别只拥有同目录的 `apothecary.py`、`old_relay.py`、
  `relay_inn.py`、`canal_gardens.py`、`spring_array.py`、`initiation_court.py`，
  以及各自独立命名的测试。不要改公共文件或其他地点文件。
- 集成代理负责生产入口、调用参数、输出归属、资源生成接线和相关文档。
  集成不改现有 authored 几何、打包资源、Java、Gradle 或 mod 版本。

## 用户入口：六地点同一 seed

从仓库根目录执行：

```bash
python3 -m tools.qianzhen.storygen --seed 20261024 --out out/qianzhen-seeded/20261024
python3 tools/qianzhen/build.py --source out/qianzhen-seeded/20261024/source --out out/preview/qianzhen-seeded-20261024 --source-id seeded-20261024
```

第一条通过 `generate_sites` 按 manifest 动态加载全部六个插件，再用
`compose_scene` 组合并校验整场景，失败明确报错，不回退 authored。
输出目录必须显式指定，与输入目录分离：

- `resources/data/myvillage/structure/qianzhen/`：六个确定性 gzip NBT。
- `resources/data/myvillage/story/qianzhen_scene.json`：沿用现有 schema、角色、
  目标完整 state、道路和部署资源位置；可供后续隔离验收装配，不自动进入 jar。
- `source/`：实际生成蓝图、manifest 和 `props=[]` 的 scene 快照，props 已物化。
  可直接交给现有 `build.py --source`，不得换回原始非空 props。
- `generation-report.json`：root seed、算法/契约版本、Python 版本、六插件与
  公共工具源码 SHA-256、原 authored 输入指纹、物化输入哈希、语义空间与邻接、
  各地点展开方块哈希、最终场景版本和七个资源哈希；快照内保留一份同源报告。

第二条为全部六处生成 NBT / SCHEM 双路回读、viewer、报告和 ZIP。导出的
NBT 与第一条资源逐字节相同。快照被修改后导出会拒绝沿用旧来源报告。
预览 ZIP 包含该报告及已物化的 `authored.json`（保留旧导出文件名）；其中
`authored/story_scene.json` 的 props 为空，不能当原 authored 输入重新套 props。

导出目录的 `export-provenance.json` 绑定实际 manifest、六地点输入、scene、
README 和生成报告的文件哈希，同时记录全部导出文件及已完成截图的哈希。
`--source-id` 只作说明，不能替代内容身份。复用目录前先只读核对；换 seed、
修改 authored 输入、未登记文件、导出或截图被改写，都会在写入前拒绝。
来源变化或旧目录没有此清单时请指定新的 `--out`；工具不会删除原文件。
相同来源可通过 `--only` 分次补齐地点，汇总和 ZIP 保留全部已核对地点，
`selected/completed` 记录本次实际导出的子集。已有截图的目录允许重新采集和
`--package-only`，重新构建须使用新目录，避免旧画面或报告被静默沿用。
中断采集留下的未登记或被改写文件也会拒绝打包；保留现场并用新目录重建。

后续在指定云端环境执行浏览器采集（本次集成没有启动浏览器）：

```bash
python3 tools/qianzhen/capture.py --out out/preview/qianzhen-seeded-20261024
python3 tools/qianzhen/build.py --source out/qianzhen-seeded/20261024/source --out out/preview/qianzhen-seeded-20261024 --package-only
```

同 seed / 输入 / 源码 / Python 环境可在另一目录重跑并逐文件比较；报告记录
输入和源码身份，但不内嵌整个仓库，复现前须保留相应固定提交。跨 seed 差异
应比较 `generation.sites.*.geometry_sha256` 或实际 NBT，而非 root seed 或报告哈希；
有限语法可能使不同 seed 命中同一布局，不承诺每对 seed 都有六处不同。
最终 scene version 沿用生产内容指纹，覆盖模板字节与布局输入，不包含单独的
root seed / 源码证据元数据；同几何时仍须区分招牌等实际资源内容变化。

原命令 `python3 tools/qianzhen/story_assets.py`（以及 Gradle 的无 seed 调用）
始终使用默认 authored 输入；不加载六插件，默认资源和现有场景版本保持原字节。
该工具也支持 `--seed <整数> --out <独立资源目录> --report <报告路径>` 和
对应 `--check`，不额外写 source 快照；完整导出推荐上面的模块入口。
`--source` 仅显式读取指定来源，不触发随机生成。全部抽样发生在离线工具，
不运行时重抽、不接自然 worldgen 或冻结 V4，不迁移任何已部署存档。

插件按 manifest 地点 ID 导入，不需要公共注册表或 import 清单。每个文件公开：

```python
from . import GenerationContext, SiteBuilder, SiteResult, SemanticPlan, SemanticSpace, Adjacency

SITE_ID = 'apothecary'  # 必须与文件名、既有地点 ID 一致

def generate(context: GenerationContext) -> SiteResult:
    contract = context.contract
    rng = context.rng('massing')
    # 先形成实际语义空间与邻接布局，再生成结构；不要只随机换材质。
    # builder = SiteBuilder(contract) 仅初始化保护格，其余从空场地生成。
    # builder = SiteBuilder(contract, from_authored=True) 可用于渐进改造。
    # builder.apply_ops([...], palette={...}) 接受原 set/fill/line DSL。
    # return builder.finish(SemanticPlan(spaces=(...), adjacencies=(...)))
```

上面是接口示意，不是可以直接执行的地点实现。地点模块应声明真实的诊室、前店、
院落等空间；不要用一个覆盖全场的虚构空间冒充具体布局设计。

## 坐标、剧情契约和保护

`load_contract(source=...) -> SceneContract` 先用现有 `story_assets.assemble`
校验事实源，再提取每个 `SiteContract`。契约保存不可变 JSON 快照，
`contract.blueprint()`、`.roles`、`.targets` 每次返回独立副本。

- 所有坐标都是地点局部整数 XYZ，尺寸为半开范围 `[0, size)`；
  x 向东、低 z 为前、y=0 为基础。语义空间的 minimum/maximum 是闭区间。
- 本阶段固定已有 size、origin、方位、蓝图 ID、site 元数据、全部 walk target、
  story anchor、service block 和 entity 声明。最大尺寸来自 manifest。
  固定 footprint 可保留既有道路接驳；不自动裁剪、重心归零或旋转。
- `.roles` 包含 NPC（以及 spawn）的脚部 `pos` 和玩家 `approach`；
  `.targets` 包含既有交互 ID、精确 `pos`、玩家 `approach` 和完整 `state`。
  全部取自现有 scene 和真实展开后的方块，不只检查 block 名。
- 所有 walk target、NPC 脚部和玩家 approach 的支撑、脚部、头部格，
  以及 story anchor、service block 的 pos、交互方块和目标视线中间格均受保护。
  保护保存基线完整 state，包括空气；后续 op 不得覆盖为其他 state。
  其余几何可以改变，但最终仍须保持可达。
- `SiteBuilder` 复用 buildgen `BlockGrid/PROTECTED`；`apply_ops` 整批预检，
  冲突报错且不留下半次写入，同批先破坏再恢复同样拒绝。
  可使用 `.grid` 接入 buildgen 几何帮助，但不能 force/remove/shift 保护格。
  `finish` 与 `validate_result` 会再次核对最终展开方块，阻止直接 grid/ops 绕过。
- `finish(plan, metadata=...)` 可更新 description、presentation、signs、
  roof_checks、wall_roof_joins 等非剧情元数据。修改结构时应同步真实屋顶检查，
  不应删除检查来掩盖缺陷；不可变剧情字段仍会拒绝。

`SemanticSpace(id, purpose, minimum, maximum, walk_targets)` 必须引用现有
walk target 名，且包含对应坐标。空间可重叠（例如共享廊道）。所有既有
walk target 必须被覆盖；所有剧情脚部、approach、目标、设施与 anchor 坐标
必须属于至少一个空间。它们是几何设计声明，不是第二套任务事实。

`Adjacency(source, target, path)` 引用两个不同空间 ID。path 为包含两端的
局部脚部坐标序列，起终点分别落在两个空间；每一步必须是原 `walk_graph`
认可的边，包括台阶规则。邻接不是欧氏距离或只含名称的承诺。
`SemanticPlan(spaces, adjacencies=())` 统一提交这些要求；生成器须声明自己实际
需要的邻接关系，公共层逐条校验，不凭空推断地点剧情规则。

## 隔离种子与渐进组合

`derive_seed(root_seed, scene_version, site, stream='geometry')` 使用带域分离的
SHA-256 和规范 JSON，返回 256 位整数。版本来自 scene 的显式 version，
不是包含全部地点内容的 hash。一个地点的随机抽取次数、内容变化或调用顺序
不会改变另一个地点的派生种子。场景版本变更会显式改变全部子种子。

`GenerationContext.seed` 是默认流种子；`.rng(stream)` 每次创建新 Random，
应在某阶段保存返回的实例。不要用全局 random、Python hash、当前时间、模块级
Random 或共享顺序流。seed 负责可复现，最终内容指纹仍由原生产生成器计算；
算法变化由 `pipeline.ALGORITHM_VERSION` 和实际源码身份共同记录，
契约/派生种子版本由 core 管理；不承诺跨 Python 版本的完整输出一致。

```python
from qianzhen.storygen import load_contract, generate_sites, compose_scene

scene = load_contract()
generated = generate_sites(scene, seed=20261024, sites=['apothecary'])
layout, blueprints, report = compose_scene(scene, generated)
# 另五处仍为 authored；选中的插件不存在/报错时明确失败，不静默回退。
```

可在单地点阶段调用 `validate_result(scene.site(site), result)`，它复用
`qianzhen.build.validate/check_walks`，再检查保护格、所有角色/目标接近点、
精确 state、视线、距离、语义空间和邻接。

`compose_scene` 接受任意地点子集（包括空集），经临时 source 复用原
`assemble` 的道路不覆盖、整场景连通和全部 NPC 占位检查。返回值与
`assemble` 一致：layout、按地点索引的可导出 blueprint JSON、report。
临时目录会释放，不写生产路径；结果可通过现有 `json_to_nbt` 写出 NBT。

注意：契约/结果的蓝图已应用 scene props（例如清水釜）。组合时将 props
清空后只应用一次。集成代理不能将这些蓝图直接配上原始非空 props 再
调用 `assemble`，否则原有 replace 保护会正确报错。要么使用 `compose_scene`，
要么明确移交“props 已物化”的 source。`pipeline.py` 已将 seed、生成版本、
插件源码与输入身份纳入报告，并复用原 NBT gzip/回读与内容指纹规则。

本公共层不提供跨 seed 美术差异或具体地点完成的证明。所有检查均为离线保守
拓扑，原生碰撞、点击轮廓、映射输入、外观、FPS、多人和存档运行时保持
`not_verified`；最终是否满足六处设计仍需地点测试和集成验收。
现有 `game_evidence.py` / `story_evidence.py` 仍读取 authored 默认来源；后续
seeded 云端验收须显式接通对应快照、资源与路线，不能直接复用旧 preset 结果。

## 最小检查

```bash
python3 -m unittest tools.tests.test_qianzhen_storygen tools.tests.test_qianzhen_storygen_apothecary tools.tests.test_qianzhen_storygen_old_relay tools.tests.test_qianzhen_storygen_relay_inn tools.tests.test_qianzhen_storygen_canal_gardens tools.tests.test_qianzhen_storygen_spring_array tools.tests.test_qianzhen_storygen_initiation_court tools.tests.test_qianzhen_storygen_pipeline tools.tests.test_qianzhen_build_capture tools.tests.test_qianzhen tools.tests.test_qianzhen_story_assets
```

不需要 Minecraft、Modal、Colab、Gradle、jar 或版本升级。
