# 护泉古阵取景复验

范围仅为 `spring_array.py` 生成的审阅元数据。基线为
`29d4b4fd63ede4c4492046f892f85996ac634344`，原验收目录为
`reports/colab-runs/qianzhen-29d4b4f-20261023T203843Z/`。

已读取该次总结、索引与两份视觉审阅记录，查看原生 `interior.png`、
`entry.png`、行走录像代表帧，以及离线 `cut.png`、`top.png`、
`underground_side_cut.png`。根因是审查相机：固定室内相机贴近东阶脚的
维护室外墙，地下侧剖距离过近，未给上下构造留出画幅。入口照壁的遮泉、
维护室的围合以及高廊近侧楼板遮挡均是实有构造，不能为截图拆除。
目前未发现原路线或剧情 approach 点击视线被堵的证据。

本修复没有改动任何实体格。专项回归固定六个 seed 的修复前展开方块哈希
（含空气和完整 state），继续校验全部剧情位置、NPC 占位、approach、
点击视线、双侧独立路线、四阵脚、封闭泉池及地下支室三种位置。

## 复验视角

所有坐标均为地点局部坐标，eye 是眼睛而非脚部。viewer 中可直接选择，
`capture.py` 会按这些 ID 输出独立文件，不需改公共 viewer 或采集器。

- `entry`：eye `(23.5,4.62,34.5)`，target `(23.5,4.5,26.5)`。
  真实脚部为阵庭南岸 `(23,3,34)`，看泉眼与北侧阵脚。完整结构中焦点可见，
  前方四格、左右各 35° 的近景没有贴墙遮挡；四阵脚全貌另看 cut/top。
- `cut`：沿用 `(36,18,16)` 朝 `(23,3,28)`；改为去除 `y>=7`。
  展示四阵脚、阵庭、双阶和维护室去顶后的关系。四个真实铜阵脚位于
  `x=16/30,z=22/34`，剧情脚部 `x=15/31` 在柱旁，不能混作柱心。
- `maintenance_interior`：eye `(38.5,4.62,30.5)`，target `(40.5,3.5,33.5)`。
  真实脚部为现有 `maintenance_bench` approach，朝后方工作区和空容器。
  离线仅去除 `y>=7`；焦点在完整未剖结构中也可见，因此同一眼位可拍原生室内。
- `maintenance_plan_cut`：去除 `y>=7`，eye `(39.5,31,28.5)`，
  target `(39.5,2.5,29.5)`。从上方展示维护长室、家具、三宽开口及随 seed
  位移的支室。支室位于 `x=41..45`，起始 z 的三种值均有专项测试覆盖。
- `underground_side_cut`：仍去除 `x>=41`，eye 拉远至 `(77,22,14)`，
  target `(34,8,29.5)`。保留地基、维护室、环廊顶板与屋面的高差。
  该切面会切掉东侧支室，必须结合 `maintenance_plan_cut` 阅读，不能单独
  用它判断支室是否存在。测试检查画幅留白和地坪、顶板、屋面断面的可见性。

新增两个视角都使用现有 viewer 支持的轴向 remove 语法；viewer 对新增剖面
统一显示“地下侧剖”，采集文件名用上述唯一 ID 区分。未添加消费者忽略的相机字段。

## fresh Colab 交接

按最终固定提交重新生成 root seed `20261024`，并用 `17` 对照；不要沿用旧
source/provenance/request。原生 `entry.png` 会读取新的 presentation.eye。
现有 `game_evidence.INTERIORS` 的 `interior.png` 仍硬编码为
`east_descent_foot -> (23,4,27)`，它不读 review_views，本地点插件无法覆盖。
集成采集器已保留并标注该旧镜头，同时按 `maintenance_interior` 眼位另拍
`maintenance_interior_uncut.png`，只读取 eye/target，不执行离线 remove。
`entry.png` 对应上述新阵庭眼位；`day_frame_plan` 保存原生局部脚部位置与焦点。
复验代理必须实际审阅这两张新图，不能把旧文件改名、替换成离线剖面，
或把额外相机拍摄当作映射行动证据。离线 `maintenance_interior.png`
与原生 `maintenance_interior_uncut.png` 属于不同证据，不能互换。

离线剖切用来阅读地下关系，原生未剖镜头用来核对实际空间；通行与剧情点击
仍按原目标和断言执行。原首轮建筑通过不代表第一章九个目标已获原生点击验收。
本次只运行离线测试与导出，没有启动游戏、云端或浏览器，没有制作新截图。
重拍画面审阅、玩家外观、手感、音效、FPS 和多人仍为 `not_verified`。

必要检查：

```bash
python3 -m unittest tools.tests.test_qianzhen_storygen_spring_array
python3 -m unittest tools.tests.test_qianzhen_storygen tools.tests.test_qianzhen tools.tests.test_qianzhen_story_assets tools.tests.test_qianzhen_build_capture
```

专项覆盖 `0,1,17,42,-7,20261024`，包括同 seed 重现、保护格与几何基线、
原路线与点击视线，以及现有 43° viewer 透视在 16:9 画幅中的关键点留白。
这类数值检查不能代替 fresh 原生截图的视觉审阅。
