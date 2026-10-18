# 灵石公共标签与消费兼容（0.64.3）

实现提交：`dbd1efa92f69e86b3f618450b9978473a7f43a94`。后续交付说明只改文档，复用同一产品输入的证据和 jar。

## 实现范围

公共 `c:ores/spirit_stone` 覆盖普通/深层矿物的物品和方块，并进入 `c:ores`；灵石加入 `c:gems/spirit_stone` / `c:gems` / `c:spirit_stones` 分类。

`c:spirit_stones/low_grade` 默认只含本模组下品灵石，为其他模组同级物品提供数据包显式接入入口。14 个灵石工作台配方、修炼、H 面板计数、宗门供奉、丹炉燃料/补料、炼器与维修消费此分级标签。掉落与产物保留原 ID，交易继续使用配置中的具体货币，不按宽泛宝石分类等价兑换。

外部数据包示例见 [资源合同](../contracts/assets.md#灵石公共标签)。丹炉仍为单燃料槽，不合并不同物品或组件；自动补料受实际堆叠上限限制。

## 本地局部检查

以下 110 项 Python 回归全部通过：

```bash
python3 -m unittest tools.tests.test_validate_spirit_stone_resources tools.tests.test_forging tools.tests.test_validate_cultivation_gain tools.tests.test_modal_runner tools.tests.test_gen_alchemy_assets tools.tests.test_validate_alchemy tools.tests.test_gen_spirit_crops tools.tests.test_demon_trial tools.tests.test_forging_tags
```

`gen_alchemy_assets.py`、`gen_spirit_crops.py`、`gen_forging_assets.py`、`gen_demon_trial.py` 的 `--check` 全部通过；灵石资源、cultivation gain 检查通过。调整旧的具体物品 ID 断言后，仍保留对缺失分类、宽泛分类支付、非法配方/重复材料和违规产物的拒绝。

## Modal 局部构建

```bash
.venv-modal/bin/python -m tools.modal_runner run --preset spirit-stone-build --ref dbd1efa9 --timeout 1800
```

- 运行：`reports/modal-runs/20261018T070050Z-59531372/`。
- `test` + `jar -x generateAllStructures` 成功；未运行完整 release 或游戏。
- 23 个 Java suite，106 项测试，0 失败、0 错误、0 跳过。范围为修炼、炼丹、炼器和宗门任务。
- 新增 `SpiritStoneCompatibilityTest` 5 项通过：使用临时 Minecraft 标签成员，验证跨物品合计支付、缺料不扣、原组件回滚、重载移除资格、炼器副本规划、丹炉不同物品不混叠和外部堆叠上限。
- Gradle 174.455 秒；云端控制器总计 199.031 秒。没有自动重试。
- Sandbox `sb-01M56X0E3WD9MEVBFHC6H3DFCH`；产物下载并校验，`termination: confirmed`。

## 交付核对

jar：`build/libs/myvillage-0.64.3.jar`，5,611,646 bytes。

SHA-256：`9d367ad9b8266d8ac44f90a2f18e03ffc5e99ebc1c2e08bd8e8f38bfa7c2b8da`。

26 个变更资源（含 14 个配方）逐字节与 jar 一致，新 `SpiritStoneTags.class` 存在；`validate_spirit_stone_resources.py --jar ... --require-current-jar` 与 `validate_alchemy.py --jar ...` 均通过。

真实服务器 `/reload`、特定第三方模组组合、真实客户端修炼/补料/供奉和玩家验收均为 `not_verified`；本次证据是局部自动检查及实际 NeoForge 编译打包。没有安装到玩家设备，也没有启动玩家游戏。
