# 五境十五套装备检查与原生验收

本次仅在 `/tmp/mc-immortal-armor-atlas-20261028` 的共享工作树准备测试，不提交、切分支或启动游戏。
美术基线为 `c376d8e4`，旧资源基线为 bootstrap `fe1a7e69`。
本次实装仅限模型、贴图、穿戴与动效；用户明确不做来源及玩法，故不采集获取或炼器流程。

## 本地轻量入口

```bash
python3 -m unittest tools.tests.test_immortal_equipment -v
python3 tools/validate_immortal_equipment.py --report reports/immortal-equipment/resource-validation-final.json
python3 tools/immortal_equipment_evidence.py --plan --out reports/immortal-equipment/native-plan.json
```

验证器只读正式输出，不调用导出器、不写资源。失败返回非零并写出具体字段或路径。
导出器修改后由其拥有者重新生成，再复验；不要修改输出或放宽断言。
报告目录受 Git 忽略，不提交。

检查实际十五个设计ID、五份相同的铁甲基础数值与 `balance.json` 一致、60件四槽防具和15种锭、中文名与物品模型。
逐个检查75个OBJ的原组件集合、三角索引、UV、单位法线、MTL与PNG引用。
逐套检查宽/细臂完整骨骼、槽位、组件材质、透明单/双面类型和 `rig.json` 显式归属；
细臂只调整手臂，固定内侧边缘、独立原点和UV，其他组件不变。
PNG解码校验CRC、尺寸、每个像素与作者行序、原色和不重复乘透明度；`.mcmeta` 明确关闭模糊。

原模型对照直接加载已确认的 Three.js 引擎并关闭 avatar，不调用新导出器的几何函数。
逐组件比较真实原三角面的边界、表面积、面积重心；逐个输出三角面重心回查原面UV与法线。
允许为周期UV裁切三角面，禁止替换主体、丢组件、串图、翻转纹样或混入预览人台。
这些数值对照用于防资源回归，不能证明玩家动画、脸手可见性、遮挡排序或审美。

`effects` 必须等于作者配置；flow/arc逐项绑定到合法槽位骨骼；motion目标在同一骨骼内；
glow目标必须存在。`provenance.json` 的输入散列必须匹配当前文件。
28份已确认作者/level/effect/engine文件与美术提交逐字节比较；403份旧玄铁、器坯、共享装备与武器资源
与bootstrap逐字节比较；旧中文键不删除或改写。

## Java专用检查（由父代理统一云端执行）

测试只调用当前真实公共入口：
`ImmortalEquipmentCatalogue.parse(Reader)` 返回 `Parsed`；
`ImmortalMeshData.parse(Reader)` 返回 `ImmortalMeshData`。
没有猜测不存在的record或初始化客户端。

```bash
./gradlew --no-daemon --console=plain -x generateAllStructures test \
  --tests 'com.example.myvillage.equipment.immortal.*'
```

该命令只供父代理纳入其固定源Colab构建，不由本检查代理运行。
`ImmortalEquipmentCatalogueTest` 覆盖75个ID、四槽、五级、从输入读数值、坏身份/数值与不可变集合。
`ImmortalMeshDataTest` 覆盖十五套真实网格、两身形、身份/骨骼/材质/UV/法线/索引错误、重复组件、退化面、
槽位骨骼约束、动态未知目标和跨骨骼目标。当前共14个Java测试方法，尚未编译或运行。
此前源码检查发现的槽位骨骼与退化面拒绝逻辑已在共享实现中出现修复；不能据此声称Java检查通过。

## 最短固定源Colab原生采集

父代理冻结完成整合的源，保留 `source-manifest.json`、`source-files.json`、唯一交付jar及其SHA256，
完成专用Java检查和jar构建后，在同一Colab VM复用构建产物。
不要把bootstrap提交号当作包含未提交实装的完整源身份；每次证据记录冻结文件散列。
采集器复用旧 `equipment_evidence.py` 的X11输入、RCON读回与会话清理，不修改旧脚本。
其启动前后校验冻结源和唯一jar；额外逐条核对实际dev启动读取的模组classes/resources与该jar，
并核对十五份穿戴JSON与冻结源，避免只报告jar散列却运行另一份松散产物。

```bash
MYVILLAGE_CLOUD_PROVIDER=Colab python3 tools/immortal_equipment_evidence.py \
  --out reports/immortal-equipment/native-fixed-source \
  --cases equip,poses,glass,mix,first_person,reload,profiles
```

此命令会启动游戏，仅供父代理在已授权Colab中执行，检查代理没有运行。
输出路径必须不存在，失败保留日志与截图；报告明确 `selected/completed/unrun`，以及完整范围的 `scope_unrun`。
输出只有实际完成的场景才进入completed，未执行场景不能跨jar或跨冻结源拼成通过。

1. `equip`：RCON只补热栏和读回；十五套逐套用映射右键穿四件，原版Inventory确认ID和槽位，截图图标与穿戴；再映射选中对应锭，捕获手持与背包立体物品。
2. `poses`：选墨青、紫霄、九色、太虚、大日，映射W行走、Shift蹲下、左键持剑举臂，录制前后视与读回位移。
3. `glass`：九色正背视捕获，保留玻璃/琉瀑背面、遮挡与透明层次供审看。
4. `mix`：映射背包Shift点击脱下，分别单穿四槽；再将四个设计混穿，读回实际槽位并录制动作。
5. `first_person`：先映射 R 并读回修仙战斗模式，确保武器 IK 实际启用；听雨、紫霄、九色、太虚分别持剑、双手枪和拳套，映射左键挥动、F 换至副手并换回、切空手。按 SelectedItemSlot 与 Inventory 的 -106 副手槽读回，不以热栏选择替代换手；记录两袖透明与连续变形。左惯用手仍需单独观察。
6. `reload`：映射F3+T，等待真实日志 `仙衣网格已原子加载：15 套`，核对装备不变，保留重载日志和画面。
7. `profiles`：以另一个offline用户名重启客户端，记录UUID和默认皮肤索引确认另一身形，重复上述采样。

脚本复用旧采集器的专用测试存档与survival穿戴流程；不改用户存档。
取景、场地、物品补给用RCON属于fixture；行走、蹲下、举臂、穿脱和F3+T必须经映射输入。
默认身形算法只能证明选中了标准/细臂皮肤配置，实际袖宽、脸手可见与动作跟随仍需检查捕获画面。
没有将命令装备、固定人台或开发姿态probe计作玩家动作。

## 两个观察者与视觉边界

`two_observers` 需要穿戴者加两个同时在线的真实客户端。现有session的 `max-players=2`，
旧采集器最多启动一个guest，不能直接声称满足。新脚本拒绝自动执行此项，并在完整范围中保持unrun。
父代理须在其云端独立三客户端场景把临时服务器容量设为至少3，保持同一源、jar和世界身份，
分别录制两个观察者看到的同一穿戴者行走、蹲下、举臂、单件切换、混穿与重载；
记录三名身份、映射输入日志、观察者各自视频/截图、实际穿戴槽位和两个观察者的客户端日志。
RCON只准备和读回。顺序连接两个单观察者、盔甲架或FakePlayer均不满足同时两个观察者。

外观、脸手/皮肤、玻璃背面、粒子骨骼、披衣动作、资源重载后的渲染、非玩家实体支持范围，
都需从实际画面评估。捕获完成只标 `captured_for_review`，不自动判玩家审美或手感通过。
FPS、音效、其他模组动画和广泛多人组合仍为 `not_verified`。来源、锻造及数值进阶明确不在本次范围。
本脚本的给予物品只能证明物品可获取与可穿戴，不证明自然材料来源或完整炼器配方闭环。

## 本次真实运行记录

- Python故障注入：18项通过，`reports/immortal-equipment/python-tests.log`。
- 实际资源校验：15套/60件防具/15种锭/30身形网格通过；403份旧资源和28份已确认美术文件未变。
  最终输出为 `reports/immortal-equipment/resource-validation-final.json` 与同名 `.log`。
- 最初正式目录尚未生成，真实失败保留在 `reports/immortal-equipment/resource-validation-initial-missing.json`；
  后续由导出代理生成资源后才重新检查。未用空fixture替代缺失资源取得通过。
- 离线采集计划：`reports/immortal-equipment/native-plan.json`，completed为空，七项全为unrun。
- Java/Gradle、Minecraft、Colab、Modal均未由本代理运行；原生与玩家接受保持 `not_verified`。
