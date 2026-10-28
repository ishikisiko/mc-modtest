# 实际验证记录

日期：2026-10-28。验证对象：本目录由 authored 来源生成的十五套设计，不是先前丢失的原稿。

## 已执行

| 检查 | 实际结果 | 证据 |
|---|---|---|
| 十五套、五境、每套四个防具槽位与锭 | 通过 | `geometry-report.json` |
| 有限顶点／法线、合法索引、非退化三角面 | 全部模型通过 | `src/build.py`、`geometry-report.json` |
| 整套、冠、胸衣、护腿、靴、锭 | 15 × 6 = 90 种非空真实几何视图通过 | 三份 `browser-report-*.json` |
| 正斜侧整套、背面与锭截图 | 45 张源网格渲染，另有 15 个缩略图 | `previews/` |
| 4 种着色、4 个视角、法相／人台／旋转、深浅棚光、同光比较 | 已触发与验证状态 | `ui-report.json` |
| 浏览器 GLB、OBJ、PNG 导出构造 | 字节与格式检查通过，GLB 冠部槽位检查通过 | `ui_check.py` |
| 30 份导出 GLB 独立回读 | trimesh 全部读取，四槽与锭分组正确，不含人台 | `portable-report.json` |
| 30 个内联缩略图引用 | 全部成功解码 | `ui-report.json` |
| 鼠标拖动与滚轮缩放 | 状态变化检查通过 | `ui-report.json` |
| 390px 宽度的试衣台／总览／规则三页面 | 均无文档横向溢出 | `ui-report.json` |
| 浏览器错误及网络依赖 | 0 JavaScript 未捕获错误，0 外部网络请求 | 浏览器报告 |
| 查看器 JavaScript 语法 | `node --check` 通过 | `viewer.js`、`software.js` |

## 测试环境限制

当前受管 Chromium 禁止 URL／file 导航，测试使用 Playwright `page.set_content` 载入**最终自包含 HTML 的原始文本**。这验证了文档与实际模型的浏览器执行，但不是对用户设备“双击文件”的实机验收。

该 Chromium 未提供可用 WebGL 上下文，因此上述渲染和截图全部来自查看器内置的 **CPU 深度缓冲三维光栅器**。WebGL 着色路径已编写并通过 JavaScript 语法检查，实际 GPU 执行保持 `not_verified`。两种路径共用模型数据，软件模式不是以静态图片替代模型。

浏览器内导出的二进制内容已验证。操作系统下载对话框、文件保存权限与用户浏览器版本未验证。触屏双指代码已实现，但当前只验证鼠标交互与窄屏布局，没有真实触屏设备验收。

## 美术与游戏边界

实际查看了统一灯光的整套与背面渲染，并修正了过于圆鼓的肩领、袖口／靴口露出和层次衔接、部分过浮的锭纹。细节或局部穿插仍可能存在，静态截图不能保证全部角度及动画无穿模。没有把技术通过写成用户审美认可。

未验证或未实现：用户审美接受、Minecraft 方块人比例、正式装备骨骼与 UV 图集、动作穿模、玩家实时战斗联动、LOD、目标设备帧率、游戏注册／配方／数值／套装效果。本次不进行 JAR 构建，不修改模组版本。

## 复现

构建依赖：Python + NumPy。浏览器验证额外依赖 Pillow、Playwright 与可用 Chromium；独立 GLB 回读额外依赖 trimesh。默认脚本中的 Chromium 路径为 `/usr/bin/chromium`，其他环境可修改这一行。

```bash
python src/build.py
python tests/browser_check.py 0 5
python tests/browser_check.py 5 10
python tests/browser_check.py 10 15
python src/build.py --assemble-only
python tests/ui_check.py
python tests/portable_check.py
node --check src/viewer.js
node --check src/software.js
```
