# 墨行开面轻盔修复（0.99.3-fix1）

按用户确认的实际方块几何草案替换额箍：连体低盔顶、额甲、向后收的短护颊和后颈片，去掉额玉。正式 authored 装备逐项匹配草案；胸甲、护腿、靴子的几何与材质逐项匹配 0.99.3。共享图集重新分配 UV，因此同步重导出引用它的物品和炼器工件。可见头部部件均为玄铁，热处理材质断言同步这一新设计；不添加不可见部件满足旧断言。

## 固定身份与实际检查

- 产品源码：`607ff3443b100f9fb1c1b1f230e09f9fca73d1e0`。
- jar：`myvillage-0.99.3-fix1.jar`；SHA256 `0422a386798fd08afe530b82c61e94bddf5c2121eab59e001189d6a0a5cecf51`。
- 本地 28 项装备／工件／炼灵取景 Python 测试通过；装备生成、炼器生成与物品检查通过。
- 按本会话用户要求使用 Colab；新目录 `/content/moxing-helmet-r1/`，固定源通过构建前、构建后和采集后的 7,104 文件哈希核对。
- 4 项 `EquipmentDataTest`、jar 构建通过；42 个变更资源与 jar 字节相符。
- 复用上一轮生成的 540 个世界资源，源输入比较确认相关生成输入不变，最终 jar 逐项核对其哈希；不复用旧头盔检查或截图。
- 实机 `selected = completed = [equip, poses, reload]`，`unrun = []`；11 条检查通过。穿脱、行走、蹲下、挥动、视角切换与 F3+T 使用客户端键鼠；RCON 仅准备场景／物品和读取状态。
- 本轮只采宽臂。头部在宽臂／细臂的编译结果完全一致，三件衣甲 authored 输入未改，因此未选择 `profiles`；不将上一轮细臂画面列为本轮执行。

实机确认新盔形显示、整套仍为 15 点护甲、穿脱不重复物品、重载保留装备；客户端没有相关装备／工件模型或图集加载错误。用户确认草案不等于玩家实机审美、手感、音效、FPS 或多人验收，这些仍为 `not_verified`。旧存档 ID、数值、配方、成本和服务器逻辑未改。

## 产物与清理

- 原始证据：`reports/moxing-helmet-delivery/cloud-r1/audited-artifacts.tar.gz`，含构建／测试日志、实机图／录像、源清单、最终核对与哈希。
- 离线预览：`reports/moxing-helmet-delivery/model/index.html` 与 `head-model.png`。
- 交付目录：`out/preview/moxing-helmet-0.99.3-fix1/607ff344/`。资源域名 `https://assets.ishiki333hhh.win/` 在交付检查时仍返回 HTTP 502，使用 T3 内嵌预览及本地 jar；未改动现有服务。
- 采集阶段为 `stopped`；客户端、服务器、Xvfb 存活检查全部为 false。原有用户保留的 Colab 会话继续保留，旧目录和存档未删除。

后续记录提交只改文档，不改变上述产品源码和 jar 身份。
