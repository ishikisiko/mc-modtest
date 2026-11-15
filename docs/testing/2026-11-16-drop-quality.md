# 掉落物品质与稀有度（0.134.2）

工作台在添加丹药、武器、防具和工具时提供适用档位，加入规则后可继续修改；同一物品的不同品质可以独立配置数量和概率。游戏端使用现有丹药品质、未炼灵成器品质及 Minecraft 稀有度组件。

[在线工作台](https://assets.ishiki333hhh.win/drop-workbench/)，[下载 yuanxian-0.134.2.jar](https://assets.ishiki333hhh.win/previews/drop-quality-0.134.2-20261116/yuanxian-0.134.2.jar)。JAR为53,572,544字节，SHA-256：`1163abf0ec88777aa3ada6cc06db5c654fcd32e2e127f3f2d79a5addbbb0dc54`。

生产代码提交为 `975f00e41e3988f54e9d46a7a25c93ec0dc67811`；测试样例修正后固定源提交为 `a7242d493`。构建另固定了工作台当前统一表的快照，SHA-256为 `6e599a7a3c2c2fce1a1b71a83437e2b3ed3ecca13e6593854423e2d646d12e89`。该快照保留用户把赤甲蜥改为追加模式的编辑，原工作区修改继续保持未提交状态。完整逐文件身份及输入覆盖记录位于证据档案。

复用已有High-RAM Colab及未变的生成结果，全部生产Java从本次已核对源码重新编译。首轮测试有两项因赤甲蜥的用户编辑触发固定模式断言；日志和JUnit失败保留。修正将规则语义测试移到独立样例，原有精确断言保持不变。第二轮复验通过；生产代码未因这次样例修正改变，最终JAR内2,445个生产类与原生运行目录逐字节一致。JAR不包含测试探针或测试样例。

| 检查 | 结果 |
| --- | --- |
| Python工作台与资源检查 | 36项通过 |
| Java掉落规则、来源切换及稀有度组件 | 23项通过 |
| 原生选中场景 | candidate_identity、item_variants、invalid_item_variants、restore_defaults_and_export 全部完成，failed=[]、unrun=[] |
| 原生检查 | 28条通过 |
| 网页物品能力 | 3067个ID的品质档位、稀有度与真实注册表逐项一致 |
| 浏览器写回 | 丹药、武器、防具、工具、原版剑和修仙衣甲的7项条目与实际测试文件一致 |
| 手机布局 | 390px视口无横向溢出，档位选择及取消正常 |

原生检查观察真实死亡回调与落地物品：17颗极品回灵丹拆为16颗和1颗后保留品质，并与下品丹药区分；极品筑基丹、中品青锋剑、上品玄铁盔、下品精铁镐、凡品精铁锄、稀有钻石剑和史诗修仙冠盔的组件与配置一致。成器记录没有额外炼灵专长；无效品质或稀有度重载保留原快照。结束后测试世界和端口已释放，复用的Colab会话保持原状。

浏览器检查使用独立测试表，验证添加前选择、卡片修改、刷新回读、同物品不同品质及取消不写入。正在运行的游戏需要使用0.134.2 JAR后再按原来的包内规则或外置覆盖方式加载；工作台仍自动写回源码统一表。

本次证据是浏览器交互和服务器夹具检查，玩家实战及客户端表现保持not_verified。

证据：[汇总](https://assets.ishiki333hhh.win/previews/drop-quality-0.134.2-20261116/acceptance.json)、[JAR回读](https://assets.ishiki333hhh.win/previews/drop-quality-0.134.2-20261116/jar-readback.json)、[原生记录](https://assets.ishiki333hhh.win/previews/drop-quality-0.134.2-20261116/native-report.json)、[浏览器记录](https://assets.ishiki333hhh.win/previews/drop-quality-0.134.2-20261116/browser-checks.json)、[完整证据档案](https://assets.ishiki333hhh.win/previews/drop-quality-0.134.2-20261116/drop-quality-evidence.tar.gz)及[SHA-256清单](https://assets.ishiki333hhh.win/previews/drop-quality-0.134.2-20261116/SHA256SUMS)。
