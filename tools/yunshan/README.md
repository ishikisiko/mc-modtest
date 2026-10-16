# 云山 D1：南门客栈 v1

第一栋功能建筑的独立建筑候选。它具有接待、用餐、后厨和两间客房的空间，
原版床、木门、烟熏炉和储物桶使用原版方块；尚未接入收费住宿、任务或 NPC。
本轮未修改玩家 `dev1` 存档，未加入模组 jar 或自然生成。

## 来源与另存

- 唯一 authored input：`blueprints/yunshan/south_gate_inn_v1.json`，手工 JSON DSL。
- 使用已有 NBT 与 Sponge v2 exporter；没有新增 settlement family 或屋顶分派。
- 输出：`out/preview/yunshan-inn-v1/yunshan_south_gate_inn_v1.{blueprint.json,nbt,schem}`。
- 预览：同目录 `index.html`，实际方块驱动的离线简化模型，可切换一、二层和入口视角。
- 原资料：`out/yunshan-blueprint/`、`out/yunshan-d1-build/`、`out/yunshan-scale-probe/`。
  本次另存这些目录中的 140 个文件为
  `out/preview/yunshan-inn-v1/yunshan_d1_before_inn_v1.zip`，逐文件哈希在同目录
  `baseline-sha256.json`。这些忽略目录中的备份不是 Git 交付内容。
- 原整街区 `yunshan_d1_district.schem` 的旧记录指向玩家电脑 WorldEdit 目录；
  该文件本轮未连接读取，备份 ZIP 不含玩家世界或这份原始 SCHEM。

## 重建与检查

```bash
python3 tools/yunshan/build_inn.py
python3 -m unittest discover -s tools/tests -p test_yunshan_inn.py
```

构建时检查七处目标的连通性、两格净空、无跳跃的楼梯方向，并逐格回读对比 NBT、
SCHEM 与源数据，核对招牌文字。门按打开处理，家具和薄装饰按完整障碍保守处理。
该检查不替代游戏碰撞、格窗实际外观、光照、玩家手感和性能验收。
负例测试验证封门、堵楼梯井和反向楼梯会失败。

预览使用仓库内 vendored Three.js，不依赖 CDN。仅用于人工交付时复用正确的
预览服务；不得在同端口覆盖其他接口。支持移动端和五个视角。

## 位置与安装边界

使用原 `gateN-1` 用地；位置数据以 authored input 的 `site` 为准。
当地块宽 13、深 15；地面层为 local y=0，一层脚底 y=1，二层脚底 y=6。
单格宽楼梯通向后侧走廊，两间客房分别从走廊进入。

导出为低 z 朝前的独立建筑，SCHEM Offset 为 `[0,0,0]`，并显式包含空气。
接入原 D1 时需将建筑及方块朝向整体旋转 180°，把旋转后的最小角对齐 `site`
中的位置；不要直接把默认朝向贴在南门。旋转映射是
`(x,y,z) -> (sizeX-1-x,y,sizeZ-1-z)`，旋转后范围不能超出地块。
正式替换前应保存当前世界该区域的新备份，先在副本核对角点、门朝向和屋顶边界。
替换需包含空气来清掉原屋内部，不能使用忽略空气的粘贴。

本轮提供的是另存建筑候选，尚未实机安装；不提供未经验证的自动覆盖命令。
后续要把整街区另存为 v2，应在应用建筑并验收后重新导出整街区，不能把独栋
SCHEM 称为已更新的整城蓝图。
