# V10 八州环境接入（0.103.0 候选）

本轮将已确认的八州非房屋地标和城镇环境设计接入正式资源。八城均使用独立 `capitals/v3` / `structure/province_capitals/v3`；新世界为 `planned_v10` / `planned_source_v10` / `worldplan_v10`。V1–V9 预设注册、frozen、地形和模板继续保留，旧世界未生成的区块仍读取旧资源。

## 资源来源与施工

- `blueprints/province_capitals/environment_design/catalog.json` 保持主地标造型配方的来源。
- `blueprints/province_capitals/environment_v3/materials.json` 管理正式方块材质、台阶映射、庭院配置及功能范围。
- `environment_v3/cities/*.json` 明确每城地标位置、直角朝向、需要移出的旧体量、庭园、街边陈设、改线路段和登城通道。原 v1/v2 authored 输入未改。地标整体旋转时，预留空间、地基与清单包围盒使用同一份旋转后的几何，不裁掉底座来避开道路。
- `tools/province_capitals/environment_v3/{world,export_game}.py` 只读旧 owning 生成结果，写入独立 v3 产物。未变化的分块逐字节复制；需要变化的分块重新生成。中州继承的无效海晶灯属性按材质配置只在 v3 调色板中修正，保留该分块其余 NBT 字节，并记录影响分块和方块数；旧资源不改。读取含实体 NBT 的旧块不丢弃数据，若要重新施工这类分块则拒绝扁平化重写。
- `tools/buildgen/groups.py` 追加八个 v3 settlement family；原家族、合法建筑类型与样式保持。

小数尺寸设计转换为正式方块时，薄铺地回到地表，低院墙使用墙方块，药圃采用既有灵土和三阶段灵植。树冠与屋檐冲突通过明确位置/朝向修正。地火玄鼎前庭的操作口复用现有 `forging_table` 与器物炼灵服务，不建立第二套成本、状态或交易逻辑。镇潮、护城战斗等仅为世界观用途，未增加新机制。

七州新城防的墙顶沿地形生成闭环，原始高差过大时加高墙体过渡；实际墙顶逐点保持支撑、净空和不超过一格的相邻高差。登城阶有明确接墙落地段。原有可走道路按 owning v2 声明的完整宽度保护，包括偶数路宽、转角与桥边；最终逐格检查原高度的支撑和净空，接路不能挖低旧路边。生活位置、功能方块以及保留房屋的实体方块也受保护；覆盖它们会使生成失败，不静默丢掉陈设来通过检查。

中州保留完整云山城体、既有园林和城防；浑仪位于上层水域的支墩承台，通过明确步道连接既有路线，继续使用 `absolute originY=0`，不把云山压缩成七州小城尺度。

## 版本、聚合与打包

`compile_v10.py` 从正常生成的 `frozen_v9.json` 继承世界、选址、出生、避让和生活预算，只替换新算法及八城资源引用。聚合前逐城复核 V9 清单、地形和模板身份，要求新八城全部为 v3。每城 192 个生活位置等既有边界不变。

正常入口：

```bash
python3 tools/province_capitals/game/compile_v10.py
```

在旧 owning 任务已经完整生成时，可使用 `--skip-base-export`；只有八城 v3 都完成后才使用 `--aggregate-only`。缺城拒绝聚合。

Gradle 链为 `generateWorldPlanAssets → generateProvinceCapitalAssets → generateProvinceCapitalV9Assets → generateProvinceCapitalV10Assets`。新输出根为 `build/generated/province_capitals_v10/resources`，旧根照常打包，但只有 V10 根提供当前默认 `cultivation.json`。

Java 新增 V10 codec 与注册项，V10 显式只接受 v3 配置；V9 仍只接受 v1/v2，V8 仍只接受 v1。冻结状态、旧 registry ID、原地区出生规则与 WorldSim 权威路径保留。网络协议仍为 55，未修改传送 payload。

## 验收状态与证据

本地开发轮次已完成八城 v3 导出、聚合及针对施工/旧资源审计的 Python 回归。开发中真实的台阶、屋檐、门框和路口冲突及修复日志保留在忽略目录 `reports/capital-environment-v10-20261029/`；各轮不是同一个固定源候选，不能拼作正式验收通过。

Terra 首轮固定候选 `c333e487` 已执行正式生成、Gradle、JAR 审计与原生检查，因版本测试存在相互矛盾的 v3 预期和五城共 117 处旧路边通行损失，结论为未通过；原始证据保留在 `reports/capital-v10-terra-20261029/`。当前修复扩大到完整路宽保护，调整明确冲突的陈设、地标朝向、城墙与接路标高；版本测试同时保留 v1/v2/v3 接受、跨版本拒绝和未知版本拒绝。修复后的候选需重新固定、生成、打包与复验，不能沿用首轮的通过标签。

用户要求后续不做视觉评判并保留 Colab 会话；逻辑和生成检查继续。旧设计首版的 20 张图只证明设计预览采集，不是本轮原生结果。

旧 jar 对照审计入口：

```bash
python3 tools/province_capitals/game/audit_legacy_jar.py \
  --old <0.102.0固定jar> --new <0.103.0候选jar> --out <审计结果.json>
```

旧模板、地形和 frozen 必须字节一致。只允许经当前源码摘要验证的清单来源 hash 变化，以及由真实清单/模板长度重算的索引摘要和字节计数；其余变化拒绝。

玩家外观、整城手感、真实硬件 FPS 与多人接受不能由开发者截图替代。未观察项保持 `not_verified`；最终报告分别列出 selected/completed/unrun、源/依赖/jar身份、实际失败与会话回收结果。
