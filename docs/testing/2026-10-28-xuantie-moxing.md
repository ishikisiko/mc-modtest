# 玄铁甲「墨行」重制（0.99.3）

用户从三套概念中选定 B「墨行」后，重做四件玄铁甲：低额箍、米白交领、蓝灰衣衬、左侧叠肩、局部玄铁护臂和短分片衣摆。头发与脸仍由玩家皮肤提供；衣摆随左右腿运动，没有布料物理。既有 registry ID、整套 15 点护甲、耐久、炼器配方、成本与已存物品组件不变。

## 固定身份与检查

- 最终产品源码：`7cdf482e4eed55bf6cbed89ff6366235ee01bd93`。
- 最终 jar：`myvillage-0.99.3.jar`；SHA256 `cc9a60e812ab28c9b3fb97f028c60f202efb751792ef029883aa25b868fd7f2e`。
- 依用户要求使用 Colab，复用既有 `armor-forging-20261027` High-RAM CPU 会话；实测 53,467,180 KiB 内存、8 CPU。新源码和场景位于独立 `/content/xuantie-moxing-r2/`，未覆盖旧轮次或玩家存档。
- 28 项本地 Python 测试通过，覆盖装备材质参数、宽细臂外层皮肤间隙、生成身份与 UV、OBJ 同源、工件几何／材料和鼎外炼灵取景；装备生成检查、炼器生成检查、物品校验通过。
- Colab 实际执行 4 项 `EquipmentDataTest`，全部通过，并完成 jar 构建。92 个本次变更资源与源码逐字节相符。
- 构建复用 r1 的 540 个已生成世界资源；完整源码哈希比较确认两个候选间没有改动其生成输入，最终 jar 又逐项核对这 540 个文件。四个世界生成任务不重复运行；Java、资源处理、NPC 打包、测试、jar 和实机采集均在最终候选上执行。
- 构建前、构建后与采集后均核对 7,103 个固定源码文件。

实机 `selected = completed = [equip, poses, reload, profiles]`，`unrun = []`，21 条检查通过。穿脱、行走、蹲下、挥动、切换视角和 F3+T 使用实际客户端键鼠；RCON 仅用于独立场景／物品准备及状态读取。覆盖宽臂与细臂默认皮肤、装备唯一性、15 点原版护甲与重载保留。两次客户端日志均检查装备／工件模型和图集加载错误。

开发者画面确认米白衣袖不再被原版外层皮肤遮住。玩家最终审美、实时手感、音效、FPS、多人组合和广泛第三方动画兼容仍为 `not_verified`，本次不重复炼器玩法全流程。

## 保留的首轮问题

首轮 `64840d0ac0bd600570efae1799325aa812cf4a6f` 构建和 21 条实机功能检查通过，但实机画面显示玩家外层袖子盖住了过薄的衣料。修正版根据原版 `PlayerModel` 的 0.25 像素外套／袖子／裤子层补足间隙，并新增宽细臂覆盖测试；额箍位于 0.5 像素帽层之外。

首轮外部汇总脚本还把采集器实际返回的 `passed` 错当作 `pass` 检查，因此该轮顶层报告保持 `failed`，原始采集报告和失败日志保留。r2 修正了这处状态解析，并对新源码完整重采四项；交付不使用首轮截图或检查冒充最终候选。

## 产物与清理

- 原始首轮：`reports/xuantie-moxing/colab-r1/artifacts.tar.gz`。
- 最终证据：`reports/xuantie-moxing/colab-r2/audited-artifacts.tar.gz`；含原生图、录像、源码清单、构建／测试日志、最终核对和文件哈希。
- 最终离线试衣间：`reports/xuantie-moxing/skin-clearance/index.html`；同源炼器预览：`reports/xuantie-moxing/skin-clearance-forging/index.html`。
- 对外交付目录为 `out/preview/xuantie-moxing-0.99.3/7cdf482e/`，对应资源域名 `https://assets.ishiki333hhh.win/`。交付检查时该域名返回 HTTP 502，因此使用 T3 内嵌展示和本地产物，未重启或修改预览服务。
- 两次采集会话均进入 `stopped`，本轮客户端、服务器、Xvfb 进程另做存活检查。既有按用户要求保留的 Colab VM 保留，旧证据和存档不删除。

后续文档提交不改变上述产品源码或 jar 身份。
