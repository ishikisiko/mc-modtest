# 丹药共享托座交付（0.84.3）

实现源：`3e792485d3e00b350d9cff0c34ff6b1bb4252cd5`。
交付后的说明提交不改变构建输入。

## 范围与结构

- 基础三丹使用灵叶；六种战备丹使用浅瓷碟；筑基丹、补基丹使用内衬丹匣。
- `tools/pill_art/catalogue.json` 定义主体配色、纹路及已有托座选择。
- `support_layers()`、`body_layer()`、`compose()` 分开，统一画布和丹体位置。
- 原炼丹与野外生成器保留资源所有权，烘焙回原 11 个 PNG 路径。
- 无 Java、配方、品质数值或存档变更。不同炼制品质仍共用本体，使用现有名称／槽框提示；品质丹体变体未实施。

## 已完成检查

- `python3 -m unittest tools.tests.test_gen_alchemy_assets tools.tests.test_validate_alchemy tools.tests.test_wilderness_roster -v`：36 项通过。
- `python3 -m unittest tools.tests.test_pill_art -v`：6 项通过；覆盖换主体不改托座、输入不被修改、同一主体可换三种托座、锚点范围、两生成族输出一致及非法输入。
- `python3 tools/gen_alchemy_assets.py --check`、`python3 -m tools.gen_wilderness_roster --check`：通过。
- `python3 tools/validate_alchemy.py`：70 项资源通过。
- `python3 tools/preview_pill_art.py` 导出实际资源与分层拆解；728／375 宽度预览无横向溢出，所有图片加载成功。已检查 16／32 像素离线缩略图；这不是玩家人工验收。

## 远程构建

按用户持续授权复用 `wilderness-roster-highmem` Colab high-memory 会话。
上传固定源及 SHA-256 清单，远端逐文件核对后执行：

```sh
./gradlew jar -x generateAllStructures --no-daemon -Dorg.gradle.jvmargs=-Xmx6g --max-workers=2
python3 tools/validate_alchemy.py --jar build/libs/myvillage-0.84.3.jar
```

`selected=completed=[jar,resource_bytes]`，`unrun=[]`；构建及校验约 11.16 秒。
附加 ZIP 检查确认 11 张丹药 PNG 均与当前源逐字节一致。

jar SHA-256：`ba94d8b7628a0d2a3454b222bd108b233e6c5f96722354e8eebdb2b53fb1c2da`。

未启动服务器或客户端；构建作业已结束，Colab VM 按用户要求保留，不能记为已销毁。
本轮未观察的游戏内手持、掉落、光照表现及玩家观感保持 `not_verified`；没有重新执行玩法或多人验收。

预览与下载：[交付目录](https://assets.ishiki333hhh.win/pill-support-0843/index.html)。
