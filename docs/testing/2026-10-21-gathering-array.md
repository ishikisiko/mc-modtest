# 四方聚灵阵：0.73.0 验证记录

聚灵阵实现、编译、59 项 Java 测试及七类定向场景已完成；最终图片、运转短片、独立世界与 jar 已交付。
重型工作按用户本轮明确要求在 **Colab** 执行，未启动本机 Gradle 或 Minecraft。

## 固定源与环境

- 产品实现：`cab53f08b8c470efe3768ee8ccd932efe8cdab0b`。
- 编译／Java 测试／jar 固定源：`3c32a19f79c4c136b4ce511c39c20fb68b5dc77f`，与实现提交仅差非打包验证说明。
- 后续 `34e4fd0d`、`8697d9bf`、`a9c8a07d`、`2f93643d`、`319a2f56` 只改 `tools/gathering_evidence.py`：
  实际云端提供方标记、结构修复等待、映射 GUI Escape、外观机位与短片采集。生产源码和资源未变化。
- 复用既有 `alchemy-furnace-0722` High-RAM CPU 会话，实际 8 vCPU、约 50 GiB RAM；
  Java 21.0.12.1、NeoForge 21.1.233、Mesa 25.2.8 llvmpipe／OpenGL 4.5。
- 独立远程工程 `/content/gathering-0730`。输入来自固定提交归档，加精确 PAL 根 jar 和模组 catalogue；
  上传时逐文件校验 4,659 项。原丹鼎工程、其展示世界及玩家本地存档未修改。

## 检查结果

- `python3 tools/gen_gathering_assets.py --check` 通过。
- `python3 -m unittest tools.tests.test_gathering tools.tests.test_modal_runner`：29 项通过。
  故障清理测试输出中的 KeyboardInterrupt、name already in use、upload failed 为预期模拟。
- Colab Gradle：9 个套件／59 项 Java 测试，0 failures、0 errors、0 skipped；编译和 jar 构建通过，362.884 秒。
  范围为 `com.example.myvillage.gathering.*` 与 `com.example.myvillage.cultivation.meditation.*`。
  保留 `generateWorldPlanAssets` 等实际打包依赖，未跑全量 release。
- jar 内 2,812 项资源（含 283 项生成世界资源）逐字节匹配，全部生产 class 与实机场景运行 class 一致。
- 本地下载包再次核对 2,529 项 authored 资源，0 差异。

## 实际场景与复用边界

| 范围 | 采集源与结果 | 观察 |
|---|---|---|
| structure | `8697d9bf`，已完成，7 项断言通过 | 四向缺件、地基破坏、中央阻挡、阵枢共享拒绝；该轮后来在另一场景因 GUI 采集器问题退出，保留原始失败记录 |
| mapped、economy、overlap、two_players、restart、visual | `a9c8a07d`，selected=completed 六项，unrun=[]、failures=[]、status=pass；12 项断言通过，474.097 秒 | 真实键鼠放最后一座阵枢、补料、启停、V／B 修炼、双客户端、同世界停服重启与画面 |
| 最终外观补采 | `319a2f56`，selected=completed=[visual]，status=pass、unrun=[]、failures=[]，152.355 秒 | 固定旁观总览、抬高第三人称视角及 4 秒无声阵纹短片；不重复玩法／Java 测试 |

七类场景的汇总来自上述明确范围，不将失败轮称为整轮通过。
管理员命令只准备方块、玩家境界、固定取景位置以及读取状态，修炼／启停／装料使用真实映射输入。

已核对的关键数据：

- 普通修炼 4 批共 48 点，扣 4 次阵能；灵石模式 4 批共 248 点，扣 4 次阵能。
- 无人修炼和固本不扣阵能；一枚灵石耗尽后继续按原普通修炼速度增长。
- 拆阵枢保留 1 枚灵石＋18 次余能；停止加成期间没有额外扣费。
- 两阵重叠时扣费 `[4,0]`，玩家获得 48 点，不叠加收益。
- 两个真实客户端各获得 12 点，阵心共扣 2 次；客端进程清理检查通过。
- 同世界停服／再启动前后均为 3 枚灵石＋17 次余能、启用=true；重启后再用两次，余能正确变为 15。
- 中文菜单已人工查看，布局、燃料槽、蓝图、余能和受益人数清晰；运行画面显示低矮玉枢与贴地阵纹。

## 保留的失败与修复

1. Modal `20261021T144541Z-49f1ac54` 固定实现源 `cab53f08`，返回 `AuthError: Token has expired`。
   `status=failed`、`termination=not_created`，0.77 秒；没有分配 Sandbox，没有执行云端测试。
   用户随后明确要求改用 Colab，未再要求续期 Modal。
2. Colab 外层任务和采集器同时获取 `/content/.mc-heavy.lock`，首轮等待锁 160.325 秒后主动停止。
   调整为采集器单独拥有该锁，编译／Java 结果复用；没有把等待计作玩法成功。
3. 结构探针修复地基后马上检查下一个障碍，读到上一轮 issue=5；补上恢复成型等待后，结构七项全部通过。
4. 菜单关闭误用了只允许游戏内输入的 `Game.key`，被工具保护拒绝；改为已知菜单状态下的原始映射 Escape。
   下一轮六个剩余场景全部通过，未修改玩法来迎合测试。
5. 初次总览用创造模式空中定位，等待时相机下落，图片无法清楚展示全阵；改为固定旁观取景并仅重采 visual。
6. 外观补采 `2f93643d` 在第二次传送调整视角后立即发送修炼键，处于同步间隙，未启动修炼。
   将位置和视角合并到已有带等待的定位步骤，`319a2f56` 补采通过；未改变生产代码。

完整失败轮、输入／源哈希、Java XML、日志与场景记录保留在忽略输出
`reports/colab-runs/20261021T145411Z-spirit-gathering/`。

## 交付与限制

- `myvillage-0.73.0.jar`：18,214,541 字节。
- SHA-256：`29756e7cdb848c6a49b0eaaa7643e96a5a8c655e94c6714b5a18d60008fc4076`。
- 独立展示世界包含完整阵法、觉醒／传承石碑和补给箱；不覆盖玩家已有世界。
- 主验证及最终补采的服务器、客户端、Xvfb 和 supervisor 均已退出，共享重型锁已释放；最终状态逐 PID／启动时间核验，`capture_termination=confirmed`。
- Colab 复用用户此前要求保留的会话，主验证结束时为 IDLE；会话保留不等于游戏进程仍在运行。
- 玩家审美、长期平衡、手感、音效、原生硬件 FPS 和广泛多人体验保持 `not_verified`。
  软件渲染截图和定向双客户端证据不代替这些验收。

最终图像人工审阅：全阵四枢和嵌地阵心可辨；运转近景与 约 4.034 秒、960×540、80 帧无声短片显示贴地玉纹及向内运动的光点。
菜单燃料、余能、蓝图与按钮正常。视频编码帧率不等于玩家硬件 FPS 验收。

[交付页](http://43.156.135.198:8765/gathering/)包含实机图片、短片、jar、独立展示世界、哈希及场景记录，复用原有正确服务。

操作与规则：[KB62](../ai-kb/62_spirit_gathering_array.md)。
