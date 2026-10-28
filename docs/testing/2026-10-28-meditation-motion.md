# 0.100.1 修炼人物动作定向验证

本次交付为入座、盘坐调息、收功起身、强制中断恢复和旁观者同步。玩家外观、手感与 FPS 接受仍为 `not_verified`。

## 源码与产物

- Java/资源编译提交：`22c0aa3287bf8877f205064a0afb4a7b47ec603c`。
- 最终采集器提交：`8e091455a1bb0f100a4d928e6ef14c08586b5542`；后续改动仅修复采集准备和映射输入，没有修改游戏 Java/资源。
- 最终源清单逐文件核对 Java/资源与编译提交一致；不是把不同运行时代码的证据拼接为通过。
- jar：`myvillage-0.100.1.jar`，24966038 bytes，SHA-256 `fb339e99d8738105c60d5c834dd5083d3a11648dffefec4c9e38d88c2b334623`。
- 实际开发客户端启动文件与 jar 逐项比较：采集前 7253 项、采集后 7253 项完全一致。
- protocol 53；旧 registry ID 和存档数据未改。

## 实际检查

- `python3 tools/gen_meditation_motion.py --check`、Python 编译检查、`git diff --check` 通过。
- Colab Java 局部检查：75 tests，0 failures，0 errors，0 skipped；jar 构建通过。
- 选择 `client.cultivation.motion.*`、`cultivation.network.Meditation*`、`cultivation.meditation.*` 和相邻 `ImmortalFirstPersonArmTest`。
- 最终原生采集：selected=3，completed=3，unrun=0；13 个检查记录全部通过，14 张截图，1 段 10 秒录像。

| 场景 | 实际证据 |
|---|---|
| lifecycle | 映射 V/B 启动真实结算，X 收功；按住/松开 W 并读取实际位移；受击与收功中移动中断；第一人称相机/手持显示。 |
| armor | 当前默认细臂皮肤、原版铁甲、墨青巡霄仙衣在同一真实动作下采集；已查看入座/盘坐/收功帧及装备截图。 |
| late_observer | 第二个真实客户端中途追踪：已有 epoch 直接显示盘坐，正侧面/背面画面；主动收功与移动中断均收到了对应快照，停止后修为不再增长。 |

RCON 仅准备隔离平地、开灵/境界/物品/进度、受击 fixture 和观察位置，并读取状态；修炼开始、停止和移动使用真实 X11 映射输入。没有 FakePlayer、命令直调修炼或姿态 probe。

## 保留的失败

- r2：云端缺少 ImageMagick `import` 截图程序，环境依赖补齐后重跑。
- r2b：旁观者首次登录被阡镇秘境流程传走；采集器加入首次入口完成、同维度和可见距离核对。
- r3：等待旁观客户端启动期间修为到达阶段上限；在闲置时重置隔离测试进度，保留真实增长断言。
- r4：短按 W 没有产生移动，旁观者中断快照超时；改为明确按住/松开，并新增服务器位移读回。
- 上述未完成场景没有计入最终通过；最终结论使用 r5 的完整采集。
- 旧 `tools/validate_cultivation_meditation.py` 的两项失败在未修改的 `main` 同样存在：意图包计数误含功法成长包、STOP 文字计数为 2。本次保留失败，不放宽断言、不扩大修复范围。

## 范围与产物

- 原生角色为当前默认 SLIM 皮肤；两种皮肤宽度的几何单元测试通过。其余仙衣、外部自定义装备、死亡/重连活动态没有逐项原生采集。
- Colab 使用 llvmpipe；其画面不能代表玩家机器 FPS，开发者查看不代替玩家接受。
- 证据目录：`reports/meditation-motion/`（忽略的生成产物，不提交）；`r5/evidence.json`、`r5/delivery-summary.json`、原图/录像、全部失败日志与测试 XML 一并保留。
- 录像与原图取自同一 jar；HTML 仅内嵌展示，不修改游戏画面。没有启动或改动 8765 预览服务。
- 云端会话 `meditation-motion-20261028` 在产物下载和哈希校验后已回收。

交付镜像：`out/preview/meditation-motion-01001/`，包含 jar、自包含 HTML、录像/原图、最终 JSON 和完整历史证据压缩包。
