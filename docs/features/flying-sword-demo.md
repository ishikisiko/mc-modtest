# 御剑飞行 demo

保留基础物品与实体 `myvillage:rideable_flying_sword`，新增真实物品「青霄飞剑」`myvillage:azure_cloud_flying_sword`。两档共享飞行规则，档次由服务器同步后选择三维模型；高阶目前提供外形区别，不暗中增加速度或免耗灵。尚未执行云端编译、JUnit 或游戏验收。

## 玩家入口

普通合成：铁锭沿右上至左下两格、木棍置左下，得到基础飞剑；基础飞剑放工作台中心，四方各放钻石，升级为青霄飞剑。完整形状见专属生成器产出的 recipe JSON。

管理员准备：

```mcfunction
/give @s myvillage:rideable_flying_sword
/give @s myvillage:azure_cloud_flying_sword
/myvillage cultivation qi fill @s
/myvillage flying_sword status
/myvillage flying_sword status <玩家名>
```

生存默认需已觉醒且达到筑基，在干燥地面、头顶及周围空间充足时手持右键唤剑。创造模式跳过修为和灵力门槛，仍拒绝渡劫、已有骑乘、水中等状态。境界门槛由 registry 定义的排序比较，不推算 ID 前缀。管理员境界准备复用现有 cultivation 命令，本模块不添加跳级或自动飞行命令。

WASD 平移，Space 升、Shift 降，疾跑键催剑。起飞短暂托升；无输入减速悬停。收剑先切空手并确保副手也为空，再右键；也可用另一把飞剑右键请求收剑。高空收剑强制缓降，触地才移除。手持丹药等物品的右键仍走原用途，不被空手召回键拦截。

物品起飞后从背包移入玩家持久 escrow，落地或生命周期清理后返回原完整物品；背包满则落物。飞行载具不保存、不出第二把物品。创造模式也暂存其源物品。来源物品飞行期间不能被丢弃、转让或重复唤出。

## 数据与权威

唯一飞行数值来源：`data/myvillage/flying_sword/rules.json`。每次唤剑读取并冻结一份，因此资源重载从下一次飞行生效。包含资格、速度、响应、阻尼、起飞花费、计费间隔、悬停/巡航/催剑花费、低灵阈值与应急操控时限；不在此文复制数值作为第二配置。

七位 C2S 仅表示前后左右升降与催剑，最高位仍非法；另有空召回意图。服务器由发送者当前 vehicle 推导实体，网络入口和实体均复查 owner、passenger、存活与维度，不接受客户端坐标或速度。输入过期中立，斜向归一化。载具碰撞体覆盖剑面至玩家头部，`Entity.move` 处理地面、墙面与头顶；未知区块和世界边界前禁止继续进入并转缓降，不主动加载飞行前方区块。水/岩浆接触也转缓降。

起飞收费，悬停/巡航持续预付计费；进入催剑立刻付较高费用，之后按间隔收费。反复切换催剑不会延长免费时间。低灵禁催剑并提示；归零或无法支付下期费用即不可逆地进入应急缓降，只保留短时、低速水平修正，之后零水平速度，不能悬停、爬升或远距滑翔。应急阶段吃丹药仍按原规则回灵，但本次飞行继续落地后才能再次起飞。自然回灵在骑剑期间暂停，丹药恢复接口不改。

飞行阶段：`TAKEOFF / HOVER / CRUISE / BOOST / LANDING / EMPTY_DESCENT`。仅乘骑与乘骑触地重置坠落距离，离开载具后的普通跌落继续原版伤害规则，不给永久免摔属性。

## 死亡、离线与恢复

死亡在掉落结算前取回 escrow，按原版 keepInventory/掉落规则处理；离线、跨维度与实体移除走同一个幂等归还入口。回执在授予背包/掉落前先移除，重复死亡/退出/登录/实体删除回调不重复归还。旧实体只有仍持有当前 UUID 绑定时才有权归还，不能清理新实体的物品。

起飞时另存同维度待落地标记。高空断线或服务器重启后，登录先归还残留 escrow，再消耗标记，恢复只向下、不允许水平操控的应急载具；落地清标记。再次断线只会继续恢复这种缓降，不能通过重登恢复巡航。死亡及跨维度清标记，普通落地后不会残留免摔。

若离线时地形改变导致应急载具碰撞体无空间，则只在已加载同一柱寻找可站立地面；找不到才给一次有时限的缓降药效。该兜底不加载远处区块、不保存无敌开关，也不保证虚空或任意恶意改造环境存活。真实断线、死亡掉落、重启与双人验证仍待父代理执行。

## 诊断格式

permission 2，只读，可指定目标玩家，服务器输出固定前缀后接单行 JSON：

```text
FLYING_SWORD_STATUS {"owner":"玩家UUID","active":true,"phase":"CRUISE","tier":"azure","qi":120,"position":[1.0,80.0,2.0],"velocity":[0.0,0.0,0.52],"landing":false,"escrow":true}
```

`owner` 为目标 UUID；`active` 表示目标正在骑飞剑；`phase` 无载具为 `NONE`；`tier` 为 `basic / azure / NONE`；`qi` 为当前服务器灵力整数；`position` 为有载具时载具的位置，否则玩家位置；`velocity` 同样取对应实体的服务器速度，单位格/tick；`landing` 包括已请求缓降和耗尽缓降；`escrow` 为尚有源物品回执。此命令只读，不可作为玩家输入证据。

## 模型、预览与最小检查

专属生成器：`tools/gen_flying_swords.py`，拥有两档模型、共用色板和两张配方；旧占位 PNG 保留作旧版参考。模型由有厚度的长刃、剑脊、分段收尖护手、握柄、尾饰构成；青霄额外有薄玉承足小翼、细金纹。32px 色板的 UV 显式换算为 Minecraft 的 0–16 范围，测试防止采到黑色 padding。表现参数仅由 `tools/flying_sword/visuals.json` 维护，经同一生成器打包，客户端读取 bundled 配置；这些表现参数不改变服务器飞行规则。站姿通过载具 `shouldRiderSit=false` 与独立足底定位，不改 PAL 持剑动作；只施加小幅前倾/转弯倾斜、短云尾、低音量风声与缓变小幅 FOV。

```bash
python3 tools/gen_flying_swords.py --check
python3 tools/validate_rideable_flying_sword.py
python3 tools/validate_mod_items.py
python3 -m unittest tools.tests.test_flying_sword_assets
# 预览依赖 numpy、Pillow；用已有 combat_preview 光栅器，输出 ignored reports
python3 tools/gen_flying_swords.py --check --preview reports/flying-sword-demo
# 仅父代理在指定 Colab 执行，合并渡劫接口后：
./gradlew test --tests '*FlyingSwordFlightTest' --tests '*FlyingSwordInputPayloadTest'
./gradlew build
```

离线预览：`reports/flying-sword-demo/comparison.png` 与 `review.md`，不可提交 reports。Java 源码测试覆盖合法按键、未知位、斜向限速、低灵禁催剑、耗尽降落、短时水平操控、花费分类、输入过期、召回、起飞禁止、owner/passenger/维度/死亡权威、一次归还回执与应急恢复无水平速度。测试源码不等于实际死亡掉落/存档往返证据。

父代理需用真实映射键鼠验证：两档唤出/向前朝向、六向与催剑、斜向速度、站姿和模型承足、悬停耗灵/自然恢复暂停/丹药、高空右键缓降、耗尽持续按升键和移动键、头顶/墙面/地面/水/边界碰撞、输入超时、多玩家越权、满背包归还、死亡仅归还一次、断线/重启高空防摔、跨维度不复制、落地后普通摔伤，以及外观/手感/风声/FPS。上述全部运行项当前为 `not_verified`。
