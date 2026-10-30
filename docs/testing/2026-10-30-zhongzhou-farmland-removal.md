# 中州南门外农田删除

按用户要求，从 `web/yunshan-city/src/world/plan.js` 删除南门外三条 `PADDY` 农田定义。对应范围恢复原自然地形与植被；原有房屋、道路、院落及树木不变，原农田范围内恢复 92 棵自然树木。

删除源特征后，同步 `blueprints/province_capitals/blueprint_plan/zhongzhou.json` 的当前院场序号引用和相关输入指纹，保留院场归属及道路方案。没有增加旧 ID 别名，也没有修改道路审计断言。

本次通过原生成器更新中州基础资源和同源蓝图预览。源码对照确认，除三块农田及其自然植被恢复外，原方案保持不变。生成输出分别位于：

- `reports/zhongzhou-remove-farmland-20261030/base/resources/`
- `out/preview/zhongzhou-no-farmland-20261030/preview/`

不重新制作整城原生试验包，不构建或安装 JAR，不运行 Minecraft、严格审计或行走测试。已启动的整城试验包导出中途停止，部分输出保留且不作为完整包交付。既有游戏存档和之前交付的旧包不会自动更新；后续从当前生成源重导的中州不再包含这三块农田。
