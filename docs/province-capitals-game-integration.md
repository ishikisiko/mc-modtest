# 八州主城游戏接入交接（0.101.0）

三个 Sol max 分别完成运行时、七州正式资源、中州完整云山资源，父代理汇总。最初实现批次按用户要求未测试；后续 Terra xhigh 已完成固定源构建和定向验证，产出 0.101.0 jar，准确范围见[验证记录](testing/2026-10-29-province-capitals.md)。下文保留原始接入及首次失败历史，不能把实现批次的 `not_run` 当作后续验证状态，也不能把局部通过当成玩家整城验收。

## 已接入的源码和资源

新建修仙大陆指向 `myvillage:planned_v8`，核心八州齐全、每州一城（含魔域）。旧 V1–V7 仍走各自冻结规则和城市资源，不自动替换既有世界建筑。八城共享服务器生成计划、城市入口、结构避让和命簿 siteId；生活位置按城幂等登记，商贸继续使用原权威流程。设施未加载或人员空缺不会被当成已营业。

七州共 490 栋民居，各有五类服务与地域布局；中州保留原作完整城形、587 民居、28 商铺、1 茶楼及地标/桥共 662 个实例。方块资源由 authored 输入和 owning generator 生成，山基来自一格 heightfield，结构按当前区块稀疏覆盖。

查询入口：`/myvillage worldplan city`（八城简洁列表），`/myvillage worldplan city zhongzhou`（指定州）；H → 天下 → 舆图显示主城标记。协议 54 要求客户端与服务器同时更新。新界面和游戏行为尚未实机观察。

## 父代理的正常资源聚合

执行：

```bash
python3 tools/province_capitals/game/compile.py \
  --out build/generated/province_capitals/resources \
  --base-rules build/province_capitals/base-0.100.2/data/myvillage/worldplan/frozen_v7.json \
  --aggregate-only
```

这是生产资源聚合，不是测试。结果 exit=0：八城、6158 个稀疏 NBT，runtime 消费的 city/heightfield/NBT 合计 15432171 字节（不包括中州额外交接 JSON 等辅助产物）。冻结规则摘要 `8fef106e9ab3ffbebd9bd61e6d72cea952476fc97e15e5f403aecd07041c1826`，NBT 传输字符串 3281 字符。

旧规则输入来自已交付 0.100.2 jar 的**字节不变缓存**：jar SHA-256 `82308d7fdaf43a63a28bda859b83a29c39fd42914633a4523787995189617aff`；frozen_v7 SHA-256 `aef9c75e066b037d297e4fa2f10a54875ab22611571d96e42a2ea05adbcdb325`。同时恢复匹配的旧 cultivation preset。这里只复用固定资源内容，未把 0.100.2 的测试证据当作 V8 通过。后续正常 Gradle 资源流程仍依赖 owning `generateWorldPlanAssets` 产物，无需这个临时缓存。

首次 runtime 聚合因缺旧 frozen_v7 退出，原文保留在 `build/province_capitals/runtime/aggregate-attempt.log`；本次完整聚合日志为 `build/province_capitals/runtime/parent-aggregate-01.log`。各资产作者的首次失败仍在其交接文档所列目录，没有覆盖或拼接结果。

## 本次实际冻结的八城身份

- `capital_beiyuan`：233 个 NBT；冻结包摘要 `06dc78c1cb14d87ab7e14b3c3b071b14e1d8eece5d5e1e3b94f64a4076ce1ca9`。
- `capital_donghai`：237 个 NBT；冻结包摘要 `59a7f5c27969217a37cc92e9a31f451891d98b09dabf7016e20cb87ed2f80afd`。
- `capital_donghuang`：238 个 NBT；冻结包摘要 `20e903b5ca3f8eba394bf27242a2a334968691e21fe87c65a3b351db4f67c551`。
- `capital_lingyue`：156 个 NBT；冻结包摘要 `370f62f16a0eec8bb40c579a8e22d876ceded043726a07fd00460de227bf3e2a`。
- `capital_moyu`：202 个 NBT；冻结包摘要 `d8e77418effabc531fb999202591869c6ac8638b7750cbd5165210ee49edc8c4`。
- `capital_nanjiang`：230 个 NBT；冻结包摘要 `b8d57f13b22f170270fd916271647ba38ad264b176542a4660d6518406e6cebf`。
- `capital_ximo`：226 个 NBT；冻结包摘要 `00b348f84c3332c4bc2f334b08da72c7531548ae70f7c0dc36105aab86c45a9c`。
- `capital_zhongzhou`：4636 个 NBT；冻结包摘要 `0d832ad428cbeedae2fa0f34dbc5c0e67cecec105aa23e91d4657f63f85921ea`。

## 原始实现批次的未运行项与遗留细节

单测、validator/check/lint、Java 编译、Gradle、Minecraft、旧档加载、客户端交互、多人、性能、玩家通行与水流均未运行或未观察。不能把资源导出成功改写为“游戏可玩通过”。严格记录 tests/compile/minecraft=`not_run`，player_appearance/traversal/FPS/multiplayer=`not_verified`。

中州的船只细模型、部分细灯饰/题字、逐户专属装修仍可继续细化；独立石柱亭不虚构陆路。大瀑布与城南出水按当前主世界高度预算压缩。七州药圃生产、个性装修和动态水文不是本批新增玩法。

旧 `audit_city` 不支持 V8 稀疏结构与 heightfield，会明确拒绝；后续 Terra 的验收应使用对应八城采集/读取方案，不能把旧单城审计结果复用为 V8 通过。

后续 Terra xhigh 的必要范围：固定源编译与打包；八州/城址/外缘与结构保护；协议54和城市查询/地图；真实床位、服务位置、NPC岗位与跨城库存隔离；保存重启和旧 V1–V7 兼容。先按相关范围定位问题，不自动重跑无关全量玩法。

源码说明：[运行时](province-capitals-game-runtime.md)、[七州资源](province-capitals-game-regional.md)、[中州资源](province-capitals-game-central.md)。生成产物保持忽略，不提交。原始实现批次将源版本同步为0.101.0，当时没有对应jar；后续产物身份和实际验证范围见下方记录。

## 2026-10-29 定向验证与构建修复

Terra xhigh 对固定源 `f5ef7c38bcbfcaff97e088f30a1fbb25b177a4f6` 的首轮验证已执行。八城资源回读、三个种子的纯逻辑探针、Java 编译及独立八城资源生成完成；正常 jar 构建在 `generateAllStructures` 阶段失败：通用风格加载器无法识别仅由区域导出器导入时注册的 `province_oasis_terrace`。Java 单测、jar、原生世界及旧档升级仍未执行，首轮原始结果保留于 `reports/province-capitals-validation-20261029/RESULTS.md`。

父代理将原屋顶函数原样迁入 `buildgen.ops`，与其它屋顶在同一注册入口初始化；区域导出器继续从公共注册表取处理器，`regional.forms` 保留兼容导入。未改屋顶几何、风格输入、运行时或版本号；新增独立解释器回归用例，后续由 Terra 对固定修复提交执行。首轮结果不能算作修复提交的构建或游戏通过。

首轮另有一项既有 Python 断言失败：四个玄铁器谱缺少英文物品名。器谱在八城改动前的 `93f60034` 即仅有中文名；现有中文优先政策与旧双语断言的差异单独保留，本次不修改断言或补做英文资源，不计为八城注册修复通过。

修复后的 `37985bd5` 在 R2 正常构建与所选 91 项 Java 测试通过；R3 使用同一 jar 补齐八城登记与权威检查，新增 6 项测试独立通过。R3 原生交易后的重启回读在停止测试时未完成，保持未完成。最终整合只归档这些已执行测试源码和验证文档，不改生产输入、不重建 jar、不升版本。详见[固定源验证记录](testing/2026-10-29-province-capitals.md)。
