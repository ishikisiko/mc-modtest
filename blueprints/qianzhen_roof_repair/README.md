# 阡镇旧取样亭定点支架补丁

`sample_pavilion.json` 是本补丁唯一 authored 输入；其中 `targets` 为45格空气→云杉木支架，`guards` 为取样亭完整包络内其余1341格旧块态。JSON 每格一行，便于审核。`provenance` 固定实际来源哈希，`supported` 固定精确历史 scene.version 与 canal_gardens 的 origin/size/template/NBT SHA256。不得手改生成的资源。

正常生成与检查不读取 reports、Git、网络或完整历史：

```sh
python3 tools/qianzhen/roof_repair.py
python3 tools/qianzhen/roof_repair.py --check
python3 -m unittest tools.tests.test_qianzhen_roof_repair -v
```

Gradle 的独立 `generateQianzhenRoofRepair` 将资源写入 `build/generated/qianzhen-roof-repair/resources/data/myvillage/story/qianzhen_roof_repair.json`，由 `processResources` 打包。Java 只读取 jar 内这份已生成资源；不接受数据包覆写。无需修改现有建筑生成器或历史模板，也不在登录时运行。

管理员在**场景记录对应的真实维度**显式执行：

```text
/myvillage qianzhen repair_roof preview
/myvillage qianzhen repair_roof apply
```

权限等级2。preview 完整只读核对后逐格列中文坐标、前后块态与原因。apply 不复用旧预览，必须在服务器线程重新检查完整场景记录、精确旧版本/冻结地点元数据、世界边界、高度、全部区块已加载、全部1341格保护状态与45格目标。任一未知差异或玩家改建会拒绝整次修复，零方块写入；目标只允许原空气或最终支架，已有最终支架不重写。没有 force/覆盖开关，不加载区块、不改 scene 保存版本、不动人物或任务。

写入只使用无邻居/形状传播与无掉落标志，目标拒绝任何方块实体；任何运行时写入异常都反向恢复本次已尝试方块并逐格检查回滚，完整因果诊断写服务器日志。进程崩溃、磁盘故障和原生事件行为仍需独立副本原生验证；不会把纯逻辑回滚测试当作这类保证。

## 实际来源与支持范围

1. 将固定父 baseline `97b37a991350961290ab37bb5a254103da1a8c2d` 的 tools、blueprints、block registry references 和资源解到独立临时目录。用该目录的 `qianzhen.story_assets.generate` 实际重放 authored 与根 seed `20261024`：完整布局分别为 `1-93b7ddbc5c59392e` 和 `1-f795e60913419e05`，与历史归档逐字相同。
2. 用同一 baseline 的生产导出器重放已物化 `out/qianzhen-owner-review/20261024/source` 与 `out/qianzhen-seeded/20261024/source`：完整布局分别与 `1-f795e60913419e05`、`1-56da335c3dd1262f` 精确归档逐字相同。后者是已存在的原始 seed 候选，未冒称以当前插件重新生成同一完整布局。
3. 三次 seeded NBT 回读的完整 canal_gardens 逐格一致，NBT SHA256 为 `f9c90f21a201cec9da1f05617b2c7a2f2323c78ecc9d453fc18d80331bc4dcb8`。两种完整 scene.version 不同，均分别证明过布局与 site 绑定，支持这两个精确版本。
4. 从实际修前和 `roof/after-final` 候选逐格对比，仅提取 local `[31,0,7]..[44,10,15]` 内真实差异，精确45格全部为空气→云杉木；目标实际包络 `[33,6,9]..[43,9,13]`。其余1341格旧状态固化为保护格。当前固定插件再次生成与所有目标/保护格一致；全渠圃814格 `deepslate_tile*` 坐标/状态与修前完全相同。
5. authored `1-93b7ddbc5c59392e` 已证明真实来源，但其实际修后差异为71格（66格新增、5格旧木梁轴向变化），与本次45格纯增加补丁不一致，因此明确 **unsupported**。未知版本和所有新生成版本也不支持。不能仅凭同一 scene id 扩大范围。

历史 reports/out 仅是本次首次提取与校验的证据位置，已经固化的 patch 足以独立生成、检查和运行；删除这些忽略输出不会破坏构建。固定前后蓝图/插件/导出器哈希、scene/site 身份均在 JSON 内。以后若要支持其他来源，需单独证明真实差异并重新审核允许范围。

首次原生验收交测试代理：只能复制旧失败世界，保留原件；副本加载全部保护区后 preview 应45、apply 后 preview0、再次apply0；逐格核对原瓦面及所有非目标格，改一格guard/target后整次拒绝，另测错版本/错维度/缺chunk/重启。未运行这些原生检查前，命令注册、运行时资源打包、服务器原生回滚、保存重启与玩家画面均 `not_verified`。
