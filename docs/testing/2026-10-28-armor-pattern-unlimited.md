# 器谱不限库存与卷轴设计稿（0.99.5）

药商四本玄铁甲器谱改为不限库存，每本仍为 12 枚下品灵石。配置 `pool: unlimited` 表示只售不收、不使用有限货栈、不参与丹药生产。交易仍核对身份、岗位、单次份数、库存替换计划、余额和商店账款容量；成功后收款并推进 revision 和交易次数。旧档器谱数量即使为零也不再限制购买，原记录保留；丹药等有限货物不变。界面显示“库存不限”。

保留用户电脑上已使用的 NPC 对话修复：`conversation_pause_ticks` 为 100。对应旧单测将时间推进改为读取配置，仍严格断言暂停中不增加进度、重复暂停不延长、结束后准确继续。

## 验证身份

- 产品源码：`e6307f66c1b50a9dfceadc2ed47622c0b3017071`。
- jar：`myvillage-0.99.5.jar`，SHA256 `494e700fbd234352d160a4d476b92e6a587e62f314d5b31190ea3ab0c05f90ba`。
- 本地 20 项 forging/workshop Python 测试通过；炼器生成器 `--check`、物品校验、命簿数据校验通过。commerce 的行为约束由 Java 测试验证，不把 Python 的 schema 检查视为交易验收。
- Colab `armor-forging-20261027`，独立目录 `/content/armor-pattern-unlimited-r2/`。构建前后 7,102 个源码文件哈希一致；退役的四个工作台配方不存在。
- `WorldSimCommerceTest`、`WorldSimLifeTest`、`WorldSimMerchantDutyTest`、`sim.runtime.commerce.*` 共 52 项 Java 测试通过，零失败／错误／跳过；同次 jar 构建成功。
- 新器谱测试覆盖超过旧库存的反复购买、正常收费、并发报价与重复请求拒绝、保存重载、旧数量为 0／4 的迁移以及非买入／混合库存／丹药生产配置拒绝。丹药售罄等旧有限库存断言仍通过。
- jar 中货单、生活规则、中文文案、版本元数据与源码字节一致；四个器谱模型存在，旧四个配方不存在。复用同输入生成的 540 个世界资源，逐项核对最终 jar 哈希。
- 本轮不启动服务器、客户端；鼠标购买、“库存不限”的客户端画面、手感与多人玩家验收为 `not_verified`。未操作或重启用户电脑游戏。

## 设计稿边界

用户要求先看设计稿。预览采用通用纸卷、品质边饰、中央部件图案，第二稿按现有墨行轻盔／衣甲／腿甲／靴子修正轮廓；第一稿高顶重盔已被指出不符合当前装备。白绿蓝紫金只展示可复用外观规则，不增加新的品质玩法。正式落图建议进一步减少纸纹、角纹和印章细节以保留小尺寸辨识度。

设计稿尚待用户确认，未改正式物品模型、贴图、装备几何或对应生成器。预览在 `out/preview/armor-pattern-design-20261028/concept-v2.png` 和 `handoff.html`，已通过 T3 内嵌展示；资源域名本轮返回 HTTP 502，未改服务或启动新服务。

## 失败、证据与清理

r1 仅在准备阶段失败：`/content/armor-pattern-merchant-r1/source` 的 `life_rules.json` 被上一轮对话修复改过，固定源检查拒绝复用。保留 r1 目录和失败记录，未降低哈希断言。r2 改从仍匹配源码清单的 `moxing-helmet-r1` 准备独立源码，再覆盖完整差异；本轮通过结论只来自 r2。

证据位于 `reports/armor-pattern-unlimited/artifacts-r2.tar.gz`，SHA256 `6ff3bb8bc920bbd2eefbfdac2bdb70851380d0a86b9047856155e2ace990058a`；含源码身份、构建日志、测试 XML 和 jar。最终审计 `final-audit-r2.json` 确认构建进程退出并回收，原用户保留的 Colab 会话继续保留。后续文档提交不改变上述产品源码与 jar 身份。
