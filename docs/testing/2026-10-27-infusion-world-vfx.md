# 0.99.2 鼎外炼灵与字体并发修复

## 来源与范围

用户确认“装备悬浮慢转、青白灵气绕器、淡金光纹收束”，要求灵气和符纹更明显。普通炼器鼎开始装备炼灵后自动退出界面，鼎持有真实炉次，成品留鼎待本人／管理员右键取回。普通赤铜灌灵仍用原菜单；不新增排队生产。

最终构建及实机源为 `3532673911e837107bb6d1cf9ef966389cb42934`，7,102 个跟踪文件逐一校验。jar 为 `myvillage-0.99.2.jar`，22,913,496 字节，SHA-256 `46c1035c335f61d12facec60f5e64718adddc673c3d2de69af68d9cb741a56d1`，协议 52。

本会话用户授权的 High-RAM CPU Colab `armor-forging-20261027` 承担全部 Gradle 和 Minecraft 运行。没有操作用户当前客户端或存档。

## 检查

- 本地原工坊资源 8 项、新动效几何／资源 2 项通过；字体修复后新动效专项增加字体隔离检查，3 项通过。
- 生成器、装备目录、剑枪模型及物品引用检查通过。
- 最终云端编译、打包和相关 Java 测试 87 项通过，失败／错误／跳过均为 0。包括炉次精确暂存、唯一结算、拆毁退料、保存恢复、坏数据拒绝派生成品，以及表现几何边界。
- 原鼎与器物几何复用其专用生成器；离线几何验证所有支持器物的居中、尺度与鼎耳净空。

实机采集器为 `tools/infusion_world_evidence.py`，选择 `visual_flow,interruption,two_players`。开炉、取物、拆鼎、旁观者右键均为实际客户端映射输入；命令仅准备独立测试世界、物品／资质／拍摄位置及只读状态。正常速度录像与冻结竞争／退料边界探针分别标记；后者不代表自然玩家手感。

`infusion-r4` 最终结果：`selected=3`、`completed=3`、`unrun=[]`，21 项检查全部通过。保存 10 张原始截图、2 段正常速度无音轨录像，均来自上述同一最终源与 jar。

- 缺料不开炉、不丢输入；开炉确实关闭菜单并将原装备与精确成本转入鼎。
- 青锋剑和玄铁甲均在界面外按真实进度完成，品质、历练、损伤与命名保留；成品取回一次，暂存清空。
- 中途以真实攻击拆鼎，掉落与保存的原装备／精确材料完全匹配，不掉落尚未完成的派生成品。
- 保存并重启后完整炉次 NBT 和背包保持一致，成品仍只能领取一次。
- 实际第二客户端看见同一器物与动效；非管理员旁观者无法右键取走进行中或已完成的装备。本人取回后，两端世界展示清空。旁观截图同时保留拒绝领取的原版动作栏提示。
- 最终版本两次主客户端启动及一次旁观客户端启动均完成；字体加载不再报上述 FreeType 错误。

成功归档为 `reports/infusion-world-vfx/r4-artifacts.tar.gz`，26,285,411 字节，SHA-256 `4108bc41db04682d70cc7c9e51a6e1dc72d7cfc705663b4b2216be6fb0e95178`。jar、构建摘要、逐项实机记录、原录像与清理状态在 `r4/`，对应 `build-summary.json`、`native/evidence.json`、`cleanup.json`。

## 保留失败与修复依据

1. `infusion-r1`：产品编译成功，新增测试把 `SpiritStoneTags` 导入到错误包，测试编译失败。修正导入后重跑测试。原始失败归档 `reports/infusion-world-vfx/r1-build-failure.tar.gz`，SHA-256 `54a398a6deb647765ed1069d66a7b3b99de71f349094dbe024181d37f1445c7e`。
2. `infusion-r2` 与同源同 jar 重试 `infusion-r3`：客户端加载阶段均出现 `FreeType error 0x14 (Loading glyph)`，未进入测试世界，`completed=[]`、`unrun` 为全部三个场景。保留原始日志，不作为玩法通过。
3. 检查 Minecraft 实际源码发现 `FontManager` 并行执行各字体的 `finalizeProviderLoading`，引用字体会复用 provider；`TrueTypeGlyphProvider.getGlyph` 对同一 FreeType face 调用没有互斥。此前 `forge_detail` 引用 `panel_body` 引入了共享 TTF face。修复改为在 `forge_detail` 内独立加载同一 `panel_body.ttf`，保留书字体跳过“回”和相同字形／参数；未更换全局字体或 TTF。
4. 最终渲染先写不透明器物，再写透明灵气，避免透明片先于真实物体写入造成错误遮挡。

`r2-artifacts.tar.gz` SHA-256 `b94d5ee1abfc79fd9d683dc6b30fd19db5290238c6ea36c4df8a4aff4476e470`；`r3-artifacts.tar.gz` SHA-256 `73afea5afcb2aa424c1b866b3bffa9452ecf1a996b3d7499f6555c093fe2049b`。两次失败的所有自有进程都已停止；最终结果不与旧运行的部分结果拼接。

## 证据边界

录像无音轨；玩家的外观接受、操作手感、音效偏好、FPS 和长期平衡均为 `not_verified`。普通破坏方块仍按 Minecraft 掉落实际物品，本次所有权校验限定右键领取，不声称防拆毁盗取。

最终主客户端、服务器、Xvfb 和旁观客户端的自有进程均确认停止；用户明确要求保留 Colab VM。
