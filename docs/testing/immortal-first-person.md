# 十五套仙衣与第一人称武器 IK 接入

本次只在 `/tmp/mc-immortal-armor-atlas-20261028`、`feature/immortal-armor-runtime` 实施。
未提交、推送、运行 Gradle、启动 Minecraft 或使用云端。共享工作树中已有注册、炼器、导出与资源改动属于其他工作者。
本报告描述客户端袖臂接入，不表示整个共享工作树已完成编译或原生验收。

## 实现与接口

- `client/combat/FirstPersonArmRenderer.java` 只增加一次 import 与单臂提前返回处与双臂绘制结束处各一次护甲调用，共六行；取其**已经求解的同一帧 Solution**，主手包含原 lag，副手继续使用原 offHand 解算。IK、动作、武器变换、皮肤几何均未改动。
- `ImmortalArmPoseBridge` 输出复制后的关节／旋转快照，不允许适配器改写原解算。原版 RenderArmEvent 根据相同武器、显示姿态、惯用手、offHand 资格跳过已有武器袖臂，避免重复绘制；不保存上一帧资格。
- `ImmortalFirstPersonArm` 读取当前实际穿戴的物品、身形和配置，只选择对应 arm 的胸部组件。单件、混穿、另一只手持物、放开副手、取消显示姿态、卸甲都沿用原有选择路径；没有延迟任务、粒子实体或跨帧姿态缓存。
- `ImmortalMeshRenderer` 仍是唯一材质与效果渲染管线。新增重载让 IK 绘制先在骨骼局部坐标运行原 motion／mesh／glow／flow／arc 代码，再由同一个 VertexConsumer 变形。原透明类型、材质 opacity、面排序、glint、关闭判断和局部效果预算保留。透明组件代表中心也经过相同变形与外部视图矩阵；每面的缓冲排序继续使用实际输出的顶点。双手皮肤绘制完成后，两只袖臂共用一次护甲 draw，先输出全部不透明袖面，再对两只手的透明组件统一排序，避免副手皮肤出现在主手透明袖子之后。
- `ImmortalArmTessellation` 在 Loaded 资源快照内预计算原袖面细分，原 JSON、作者几何、材质和贴图不变。每个原三角面仅作重心插值，保留平面、覆盖面积、绕序和 UV；非线性关节区沿 Y 最多间隔半个原皮肤像素。稀疏袖面原有最长边跨 8～9 像素，仅变换原顶点不足以表现弯肘，因此这一步不可省略。它没有新增分片造型或开口。
- 细分缓存跟随已有完整解析后原子发布的 Loaded；成功 reload 替换旧快照，失败 reload 仍遵循既有保留旧快照的行为。每次 draw 的位置／法线采样表仅属于该次调用，不保留玩家或旧网格引用。

## 坐标、连续性与效果

原骨骼局部臂截面并非以 X=0 为中心：standard 右／左臂中心分别为 -1／+1 像素，slim 为 -.5／+.5；导出时 slim 已完成贴身边缘与原点适配，不能再对整套缩放。
横截面只使用原武器皮肤渲染的 `thickness / 16` 像素尺度，保留左右袖子自身不对称几何。

原皮肤条肩根 Y=-2，上臂六行结束对应肘 Y=4；手行起点与原拳部重叠共同确定腕 Y=6.75。
这些入口从既有 FirstPersonArmModel／Ik 常量读取，未改其分配或解算。原皮肤分段有 UV 行重叠，无法把所有重叠行同时当作唯一连续袖子的静止关节；这里固定上述三个锚点，并将连续袖面映射到实际 solved shoulder／elbow／wrist，腕后才逐渐采用拳部旋转。

肩肘腕中心线为 C1 Hermite 曲线，截面旋转在有限关节影响带内作平滑四元数插值，避免线性矩阵混合将弯肘截面压瘪。整个原袖子共享连续映射，既不绑死手腕，也不以几个额外护甲盒替换原表面。
左臂使用解算器已经镜像的 proper rotation 和原左臂网格，不再次反射坐标或反转三角面。

法线使用包含沿臂伸缩与截面转动的变形 Jacobian 逆转置，随后保留外部 PoseStack 的 normal 变换；接近奇异处回退到当地旋转，避免 NaN。
局部效果的原作者坐标与 slim 适配仍由 Loaded.local/vector 完成，效果所有发射顶点经过同一变形；四种形状、拖尾、折线均适用。
两只袖臂共用已有局部效果预算，不引入全局粒子或随机历史。

## 已执行的轻量检查

以下目录保存精确 argv、各阶段退出码、日志、HEAD 与本次源／网格／rig／PAL 输入 SHA-256：

`/tmp/immortal-first-person-dual-arm-check/`

检查入口可从其他 cwd 执行；要求已有 `/tmp/immortal-client-api-check/classpath.txt` 的定向 API 上下文及仓库根目录精确 PAL jar。它不调用 Gradle，不初始化游戏 registry／渲染系统，不启动服务：

```bash
cd /tmp
python3 /tmp/mc-immortal-armor-atlas-20261028/tools/tests/check_immortal_first_person.py \
  --api-context /tmp/immortal-client-api-check \
  --output /tmp/immortal-first-person-dual-arm-check
```

输出目录必须尚不存在；复验用新目录，保留原失败和输入身份。`commands.json` 包含展开后的精确 javac/java 命令；可执行 Java 测试也带 JUnit `@Test`，供父代理后续云端使用。

实际结果：

| 检查 | 结果及范围 |
|---|---|
| protected-source | 通过；IK、皮肤模型、武器变换、Python 解算与 parity golden 相对 HEAD 无变化 |
| javac-client／javac-test | 均 exit=0；真实 NeoForge API、根 PAL jar，定向源码及其所需依赖；不是 Gradle／完整发布构建 |
| 解算／连续性 | 156 个真实武器主／副手快照 × 两种身形；三个关节锚点、有限带连续性、不坍缩截面、左右镜像、快照隔离通过 |
| 十五套网格 | 53,060 个顶点样本、28,240 个三角面样本；有限单位法线、原 UV、材质 alpha、light、glint 与外部 pose 输出通过 |
| 原面细分 | 两种身形合计 5,648 个原臂三角面 → 54,920 个细分三角面；平面／面积／绕序／UV 与关节采样跨度检查通过 |
| 局部效果 | 四种 spark/seal/leaf/star 形状与 trail/arc 顶点使用同一变形；零预算直接停止，不请求 buffer |
| 双臂同批 | 两只袖臂共用顶点 sink，切换 bone 时各自位置／法线正确；下一次 draw 不保留已放开的副手绑定 |
| 输入稳定性 | 检查开始／结束的已列输入哈希一致；此记录是未提交的局部输入身份，不是整个共享树的固定发布候选 |
| 既有 Python parity | `python3 -m unittest tools.tests.test_combat_preview_parity` 为 skipped=3：当前 Python 缺 numpy/Pillow，树内无 `.venv-preview`。不得称 parity 测试已通过；其输入和逻辑保持不变 |

额外执行了授权文件的 whitespace／`git diff --check`，通过。

## 失败原件及观察边界

过程日志保留在 `/tmp/immortal-first-person-check/`：

- `javac.log`：最初 classpath 未带根 PAL jar，出现 94 个依赖错误；`javac-pal.log` 补入精确根 jar 后通过。最终独立目录重新编译通过。
- `test-javac.log`：测试标签误用 WeaponDefinition.id；已修正为实际字段。`geometry-fixed.log` 暴露复用局部 class 目录缺 HitboxGenerators；最终入口明确编译纯 definition 源码，不用旧的部分输出掩盖依赖。
- `geometry-dependencies.log`：反射 fixture 的 totalTicks 实际返回 Integer，已改用 Number.floatValue。相关断言、阈值未放宽。
- `geometry-pipeline.log`：尝试直接调用完整 RenderType 管线时，未初始化 registry 的 JVM 报 `Not bootstrapped`。没有继续启动游戏或调用 bootstrap；完整渲染管线检查仍未完成。最终离线测试只覆盖纯网格与真实顶点输出适配，不以 stub 或跳过异常冒充通过。

**未运行 native。** 实际 RenderArmEvent／RenderHandEvent 调用次序、效果配置切换、真实混穿／卸甲／取消／F3+T reload、透明叠加与深度遮挡、宽袖内侧穿插、FPS、其他模组兼容和玩家外观接受均为 `not_verified`。
逻辑层复用了现有过滤与生命周期，但不能据此声明这些游戏行为已经实机通过。

该实现是绑定适配，没有布料物理或碰撞解算。很宽的袖口、强弯肘内侧以及袖子与 body 绑定肩饰的接合仍需原生观察；逆转置在极端折叠附近的回退仅保证数值有限，不保证视觉或无穿插。新增细分和每帧采样的真实开销也未测量。

额外的实质约束：NeoForge `ItemInHandRenderer` 在 `renderSpecificFirstPersonHand`／RenderHandEvent 返回后才绘制主手物品，最后才 endBatch。本接入按授权保留这一事件及武器绘制方式；已统一两只袖臂自身的透明排序，但没有新增一个主武器绘制后的透明回调。因此玻璃袖与随后绘制的原版主武器／持物副手之间的跨调用透明遮挡不能由本次适配保证，需要原生重点观察；若实际失败，应由父代理决定是否扩大到武器渲染时序接口，不应修改 IK 或换成不透明材质遮掩。

建议 Terra 按固定最终源先 Colab 编译，再用映射输入观察剑／双手枪／拳套的主副手、左右惯用手和宽细臂。重点选听雨宽袖与流光、紫霄不对称护臂、九色透明袖、太虚袖，再检查取消／卸甲／局部效果关闭与 F3+T，记录 selected/completed/unrun；不得将本报告的几何通过当作这些原生场景已完成。
