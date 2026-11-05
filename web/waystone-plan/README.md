# 八城传送石碑规划预览

这份方案为中州、灵岳、东荒、东海、西漠、南疆、北原和魔域规划 25 处传送点。`sites.json` 保存点位、完整装饰占地、三格宽接路及逐城说明，`viewer.html` / `viewer.js` 提供平面蓝图和方块结构示例。它们不被模组资源生成器读取，也不修改正式城市布局或游戏存档。

网页中的全部位置都是城市局部方块坐标。占地 `bounds` 为包含最大端点的 `[x0,z0,x1,z1]`；`center` 与 `approach` 均为 `[x,y,z]`，Y 表示站立高度。转换世界坐标还需要实际城市原点与旋转。蓝图标记、下载 SVG、选址 JSON 和装饰示例共用同一份点位数据。

选址预览使用七城当前编译蓝图。中州复用已核对的空间数据：布局、道路调整、地块及原作几何输入一致，旧输入与当前输入的差别属于原生材质部分；旧施工验收不作为本次证据。中州水体和保守的原树木、灯柱范围另外从当前 `source_game.mjs` 读取。检查记录保留所用源文件与产物摘要。运行时家具、树木最终生成裁切、实际方块净空和传送行为仍须接入后验证。

## 生成页面

先准备八个 `plan.json`，按地区重命名到同一目录，附上本轮核对后的 `planning_source_hashes`。七城可通过现有 `province_capitals.blueprint_plan.generate.generate(source)` 获取；中州必须提供已经核对空间输入的蓝图，不能绕过原生基础资源身份检查。中州重新读取当前水体及树木：

```bash
node tools/province_capitals/central/source_game.mjs \
  --out reports/waystone-planning-20261106/central-source

python3 tools/waystone_plan_preview.py \
  --plans reports/waystone-planning-20261106/base \
  --central-source reports/waystone-planning-20261106/central-source \
  --out reports/waystone-planning-20261106/site \
  --base https://assets.ishiki333hhh.win/previews/eight-capital-waystones-20261106/
```

工具依赖 Python 与 NumPy；逐城检查占地、保护用地、道路、接路连续性、标高与城市覆盖，生成 `checks.json`。失败退出非零并保留具体点位原因。页面还提供每城带编号的 SVG 和可下载的选址清单。所有外部资源链接使用既定 HTTPS 域名，发布只向现有静态目录增加独立页面，不启动或重启服务。

本次没有修改正式生成器、构建 JAR 或启动 Minecraft。当前产物是供用户选择外观和位置的规划稿。
