# 怪物掉落工作台

在线入口：<https://assets.ishiki333hhh.win/drop-workbench/>。浏览无需解锁，修改前输入私密访问密码。

工作台选择当前 Minecraft 1.21.1 与源仙资源中的物品，编辑后自动写回正式源码仓库的 `src/main/resources/data/yuanxian/drops/monster_drops.json`。下一次编译将这份表打入模组；正在运行的游戏继续使用已加载的规则，游戏中的外置覆盖及热重载见[怪物掉落说明](../../docs/features/monster-drops.md)。

## 页面功能

- 生物列表显示模型缩略图；选中后可切换斜前方、正面、斜后方并放大。全部生物图鉴支持按名称或 ID 搜索，提供三视图 ZIP 下载。
- 图片由当前真实模型和贴图离线生成，显示一种代表性外观。它们用于辨认生物；实际装备、动作、外观变种由游戏状态决定。
- 物品库按材料、丹药与药水、武器、防具、法器与符箓、秘籍与图样、工具等用途排序；装饰物和方块排在后面。分类、原版／源仙来源、中文名／ID 搜索可以组合。
- 原有掉落面板显示实际原生表的物品、每次抽取数量、无抢夺获得概率、抽取次数、条件及抢夺增量，支持查看原始 JSON。权重抽取、数量为零、着火熔炼、子表和唱片标签会保留各自语义。绵羊使用未剪毛白羊的代表表并说明颜色与剪毛差异。
- 丹药、装备和工具可在添加时选择品质或原版稀有度，卡片内可继续修改；源仙品质写入现有品质组件，原版稀有度控制名称颜色。具体档位由物品实际机制决定，默认选项保留原值。游戏端支持从0.134.2开始。
- 基础规则支持继承、追加、替换、禁用；场景覆盖从上向下匹配，额外追加规则独立配置条件。切换模式时，面板说明原有物品是否保留。
- 「场景」与「追加」共用场景筛选，野外兽群聚落按正式群落成员筛选，秘境试炼按已接入关卡和出场生物筛选。筛选后新增规则会预填来源场景及适用的生物。在游戏规则里，秘境关卡共用 `quest_realm`，修改共用规则会影响它匹配的所有关卡。
- 编辑自动保存，支持历史版本恢复、完整 JSON 下载、本地未保存草稿及并发版本冲突提示。

装备、携带物、特殊死亡回调和经验由实体状态另行处理。页面的原生表来自源码及原版资源，额外数据包对游戏战利品表的改动不会自动反映到网页。

## 生成网站与图片

需要 Python 3、NumPy、Pillow、JDK 21，以及精确版本的本地 Minecraft/NeoForm 依赖缓存。只运行模型层烘焙和离线渲染，不运行 Gradle，不创建世界或启动游戏。

```bash
python3 -m tools.drop_workbench.catalogue --out reports/drop-workbench/site
python3 -m tools.drop_workbench.portraits
python3 -m tools.drop_workbench.catalogue --out reports/drop-workbench/site
python3 -m unittest tools.tests.test_drop_workbench -v
node --check web/drop-workbench/app.js
```

首次目录生成提供生物清单；`portraits` 读取模组的提交资源，并通过 `BakeVanillaModels.java` 读取原版模型层。原版依赖严格采用 1.21.1 启动器清单中的版本。最后一次目录生成打包图片及 ZIP。更新模型、贴图或模型烘焙代码后，重新运行三步生成命令。

作者文件为 `web/drop-workbench/` 和本工具目录；生成网页、图像、烘焙几何、日志均位于忽略的 `reports/drop-workbench/`，不进入模组 JAR。`entity_models.json` 明确对应原版实体的实际模型层、贴图及渲染器来源；模组模型从现有 beast/npc 资源读取。`labels.json` 仅补充两项缺失的中文物品名。

## 部署与持久运行

部署机已有 `cloudflared`、静态 HTTPS 域名及用户 systemd。密码通过私密文件提供，不写入命令参数值、源码或网站。安装前先生成完整网站。部署命令中的源码仓库路径应指向日后实际编译的正式仓库。

```bash
python3 -m tools.drop_workbench.install \
  --repo /tmp/mc-female-npc-polish \
  --site reports/drop-workbench/site \
  --password-file /home/ubuntu/.local/state/myvillage-drop-workbench/access-password
systemctl --user status myvillage-drop-workbench.service
```

安装器只更新自己拥有的发布链接和服务。当前服务使用回环端口 8791，独立 Cloudflare Quick Tunnel 提供 API；服务将当前 API 地址写入网站的 `connection.json`。隧道退出会自动重连，重启后网页会重新读取接口地址。固定网站入口沿用既有 HTTPS 静态资源域名；现有 8765 静态服务及其原隧道配置保持原状。Quick Tunnel 为开发用途，接口连通性依赖该隧道服务。

服务程序位于 `~/.local/share/myvillage-drop-workbench/app/`，网站发布在同目录的 `releases/`。私密密码、保存前备份和隧道日志位于 `~/.local/state/myvillage-drop-workbench/`。历史备份全部保留，网页显示最近40份。

保存接口限定一个固定源码文件，校验真实物品／实体 ID、数量、概率、境界阶段和完整字段。它使用访问令牌、来源限制、文件锁、SHA-256 版本比较、保存前备份和原子替换。版本过期时返回409并保留网页草稿。外部工具同时修改源码时，应先刷新并处理页面提示。

当前工作台修改的是源码文件，因此真实编辑会出现在正式仓库的 Git diff 中。工作台不自动提交、推送或构建 JAR；按项目正常版本流程纳入后续模组更新。

品质适用族由 `variants.py` 读取实际注册输入，原生检查将全部网页物品的能力逐项与真实服务端注册表比较。用于独立测试页面时，可给目录生成器传入同域 HTTPS 的 `--asset-base`；样式与脚本带内容摘要，刷新时读取对应版本。

规则语义的固定样例位于 `src/test/resources/drops/rules_fixture.json`。Java和网页存储单元测试保留原来的精确断言，通过该样例验证，避免把用户可编辑的掉落方式和概率固定成编译要求。真实包内表仍由网页写回校验和原生服务器启动检查；原生 `item_variants` 场景读取根目录的 `drop-workbench-catalogue.json` 作为本次生成目录的检查副本，构建采集器负责准备它。

0.135.0的妖兽材料由正式目录提供56种固定身份，普通、不凡、罕见、传说保持各自名称和价格。选择具体材料可配置固定掉落；材料工厂可按目标境界生成，并编辑四档稀有度权重及已有的抢夺概率增量。参数语义与作者入口见[妖兽材料说明](../../docs/features/beast-materials.md)。
