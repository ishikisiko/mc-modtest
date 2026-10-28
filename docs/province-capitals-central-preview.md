# 中州云山巨城：完整版三维白模与主世界高度提案

本批仅为可审阅预览，未激活正式生成器、未改版本或打包、未写玩家世界。所有测试均为 `not_run`；玩家外观、步行、FPS 与多人均为 `not_verified`。后续测试交 Terra xhigh。

## 来源与范围

直接执行当前父工作区 `web/yunshan-city/src/world/plan.js` 的 `buildPlan(1)`，读取 `createGeo` 和 `createField`；未执行原作 0.2 米高精度体素重建。保留分支为 `origin/claude/voxel-cloud-mountain-city-2f0j1n`，tip `35d80b0f774e0eebe8dbbd12521d24715f276d7f`；当前输入文件 SHA-256 逐项记录在 manifest。

场景 1200×1440 方块，X=[-600,600]、Z=[-840,600]；城内轮廓包围盒约 556.932×786，后者不含山顶和城外地标。水平 1 原作米=1 方块。地形采样 4 格，301×361=108661 顶点，216000 个表面三角形；地形封闭侧壁与底面形成连续山基。原作细墙、道路、水面和建筑 pad 独立生成，避免采样吞掉细街。

保留 587 民居、28 商铺、1 茶楼，以及城门、宫城各殿、寺塔、城墙敌楼、观瀑阁、揽云亭、湖心亭、山径亭、城外石柱亭和桥，共 662 个建筑/桥占位。原作全部 616 个常规 lot 均按真实实例保留；原树木 9041 株、人物、船与灯具未铺成高精细模型。

正阳门→御街→市集→宫前，三进宫城、七级水池/跌水→镜湖→水门出水、北瀑潭→观瀑阁→上台地→揽云峰均可查看。原作 tour 含飞行；宫后至瀑潭和观瀑阁阶梯间的缺口在图中用橙色虚线说明，不宣称已可步行。

现有 D1 原参考窗口 [-145,140,65,405]，源尺寸 210×82×265，按 16 格对齐后 224×82×272，在全城南门至市集西侧用青框标示。D1 保留四馆的旧版资源与这里的原作实例不是逐栋对应关系；本批没有放大 D1 冒充整城。

## 两种高度模式

原始模式：地形 Y=-236.2～427.6；峰台 404、揽云亭原标记 418；白模保守屋顶最高约 418.2。不能称作当前主世界可 1:1 竖向容纳。

适配模式仅为 `minecraft:overworld` 提案，不建新维度、不改变主世界全局高度。采用下面分段线性映射，38～134 原作城区高差保持 1:1，上部山体与台地压缩；建筑整体只重放基座，不压扁楼体。墙上敌楼保留 9 格支承偏移。所有原始水平位置和四分之一旋转保留。

| 原作地表 Y | 提案地表 Y |
|---:|---:|
| -300 | 52 |
| -210 | 60 |
| 0 | 72 |
| 38 | 84 |
| 134 | 180 |
| 178 | 204 |
| 238 | 232 |
| 320 | 252 |
| 404 | 272 |
| 480 | 280 |

本场景适配采样地形 Y=57.671～274.484，峰台 Y=272；当前白模屋顶最高约 Y=286.2，距最高可放实体方块 Y=319 约 32.8 格。长期预算为最高地表 280+未来建筑 32+顶空 7=319，不是把山顶简单压到 319。若未来山顶建筑超过 32 格，必须再降低峰台或调整该地标，不可直接上移整城。

| 水工位置 | 原落差 | 提案落差 |
|---|---:|---:|
| 飞云瀑 | 102 | 51.067 |
| 河道第 1 处跌水 | 12 | 12 |
| 河道第 2 处跌水 | 12 | 12 |
| 河道第 3 处跌水 | 16 | 16 |
| 河道第 4 处跌水 | 16 | 16 |
| 河道第 5 处跌水 | 16 | 16 |
| 河道第 6 处跌水 | 24 | 24 |
| 城南出水瀑 | 243 | 23.714 |

以上落差仅用于白模；未模拟流体。实际主世界要先选定全城 1200×1440 及外缘约 128 格过渡带的连续保留区，绑定绝对 Y 后进行山基填挖与边界融合；不能把已经固定到顶空的方案任意加 Y 偏移。海平面、洞穴、现有结构避让、材料开销及区块生成预算均未落地。

## 生成与预览

从父工作区执行：

```sh
node tools/province_capitals/central/export.mjs
```

这条命令只导出本批预览、交换 JSON、manifest 和本说明，不运行 tests/validators/lint/check/Gradle/MC/云端工作。输出脚本仅写本代理拥有的四个路径。页面是自包含 HTML，内嵌本地 Three.js、OrbitControls 和 gzip 场景数据，无 CDN、无 fetch、无服务。也附可读 JSON 和 vendor 文件。现代浏览器须有 WebGL 与 DecompressionStream。父代理统一执行 html_preview/html_render；本代理未运行浏览器或渲染测试。

- HTML：`out/preview/province-capitals/central/index.html`
- manifest：`out/preview/province-capitals/central/manifest.json`
- 全场景：`out/preview/province-capitals/central/scene.json`
- 地形：`out/preview/province-capitals/central/heightfield.json`
- 契约交换：`out/preview/province-capitals/central/layout-original.json`
- authored 提案：`blueprints/province_capitals/central/preview-spec.json`

页面提供原始/适配高度、全景/俯视/城门/御街/宫城/瀑布/山顶/D1 机位、旋转平移缩放、图层、建筑点选、层高与尺度说明、顶高参考和原作步道逐点巡览。巡览只用于空间审阅，摄像机不是角色或碰撞模拟。

## 接口偏差与未完成项

- 原始 original 与适配 adapted 是独立 profile、manifest、layout_id 与 SHA-256；各布局 position / points / bounds 仅属于本 profile，高度不可混用。
- exchange rotation_quarters=q 表示 +X→+Z 的正四分之一圈，绕 author_origin 再平移且不归零；Three.js 使用 -q*PI/2。原作实际渲染 r=1:(x,z)→(z,-x)，故 q=(4-source_rot)%4，并保留 source_rotation_quarters。
- bounds 是 closed_geometric_envelope，不是含首尾的体素索引；terrain.data 均相对各自 manifest 文件目录。source.ref 与 source.sha256 的路径相对 repository_root。
- source 的水门不是步行入口；service 仅候选语义岗位，原作商铺没有客栈/药铺/锻坊的既定服务绑定。
- 全城 road / building 清单没有套用现有 D1 的 512 水平、128 高度等旧限制；接入前须新资源契约和空间索引。
- 原作桥梁注释称 rot=1 沿 X，buildArchBridge 的几何 span 实际已沿本地 X；本白模忠实保留 source rotation，并将桥位逻辑连接单独标为待协调，未悄悄改原作朝向。

- 主世界基址与 128 格外缘融合带尚未选址或生成；海平面、地质填挖和原生结构避让需下轮确定。
- 4 格地形网格不能代表一格台阶、墙洞或可碰撞表面；街巷须重新量化为台阶、半砖、平台和挡墙。
- 宫城御街保留三台地原始高差；殿宇门槛、台基与两段御阶仍须正式建筑资源衔接。
- 原作 summit 路线的崖阶、观瀑阁两段阶梯接驳和宫后至瀑潭接驳须重建；原作 tour 含飞行。
- 飞云瀑、各段跌水和城南出水瀑须按新落差重建水口、河床、堰、落水体及防漏边界；当前仅展示水面与落差占位。
- 凌霄塔、观瀑阁、揽云亭及墙上敌楼须在正式模板确定后重新核算屋脊与塔刹；白模高度为保守包络。
- 城外石柱亭保留原作独立地标位置，原作未给出全部陆路；本轮不伪造可步行到达的桥。

地标高度是源码公式的保守包络；原作建筑 roof/gate 细部、真实内室和方块碰撞均未重建。source 中桥长轴与旋转注释存在偏差，已在 manifest 的 bridge_orientation_issues 单列；不得将本白模当作已可遍历的桥梁证据。

## 后续整城资源化与 NPC 就近投影建议

1. 先确认全城轮廓、尺度、台地映射与水工设计，再按南门镜湖坊、市集、两坡住区、宫城、瀑潭崖阶、上台地与峰顶分批资源化。每个正式建筑族按 groups.py→modset/catalogue→roof/motif 注册表接入；预览 template ID 不直接注册为游戏资源。
2. 整城规划与高度映射版本冻结为一份来源，资源按区块柱或有界街区切片、空间索引；每片维护边界道路、水口、基础连接锚点，保证跨片连续。不沿用 D1 的全城尺寸上限，也不在玩家接近时重算整个 buildPlan。
3. 常驻人物身份、职业、资产和工作计划留在 WorldSim 命簿，由现有 facade 修改；场景只维护 district/building/place 锚点与可见半径。多玩家用同一人物唯一投影租约，近场实体化、远场冷模拟，离场回收投影而不复制身份。
4. NPC 导航按街区道路图和高度量化后的入口/楼梯连接分层寻路；只有附近区块已生成且真实入口有效时才投影在安全锚点，水门、石柱亭、未补阶梯均不能先假设可达。服务语义需重新确认后绑定已有权威交易/补给流程。
5. 下轮任何检查与游戏证据由 Terra xhigh 承担；仍需确认源身份、街区切片、顶空、实际步行/水系边界、NPC 租约、多人及性能。当前全部 not_run / not_verified 不因生成文件而变为通过。

## 协调补充的采纳

city_id 已统一为 `capital_zhongzhou`。bounds 采用 `closed_geometric_envelope`，包含连续山基底面和真实白模几何，不把最大值减一。terrain.data 相对各自 manifest 所在目录；其余路径基准在 path_bases 明确。

| 高度 profile | manifest | 布局 | 布局身份 |
|---|---|---|---|
| original | manifest-original.json | layout-original.json | capital_zhongzhou/original/4b18022fce12988d |
| adapted | manifest-adapted.json | layout-adapted.json | capital_zhongzhou/adapted/5421bfe20aaf09cf |

两份 manifest、布局 JSON 与 terrain JSON 均在 `out/preview/province-capitals/central/`。原始蓝图交换为 `city-plan.preview.json`，适配蓝图交换为 `city-plan.adapted.preview.json`。各自 position/points/bounds 只有该 profile 的高度，source_position/source_points 保留来源；不能混用布局身份。

原作 `render/structures.js:114` 的实际 r=1 变换为 `(x,z)→(z,-x)`；已转换 `exchange q=(4-source_rot)%4`，并保留 `source_rotation_quarters`、`author_origin=[0,0,0]`，不自动归零。页面按 `rotation.y=-q*Math.PI/2` 使用。桥长轴与原注释的偏差单列披露，不通过改旋转掩盖。

这些都是交付生成和来源说明，不构成任何新增检查；测试仍全部 not_run。
