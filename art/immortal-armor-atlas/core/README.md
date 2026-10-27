# 三维设计册内核

本目录只负责引擎、页面与局部测试，不修改 `levels/`。实际十五套造型以各境作者的 JSON 为准。

## 构建设计册

在仓库根目录执行：

```bash
node art/immortal-armor-atlas/core/build.js \
  --output art/immortal-armor-atlas/core/test/atlas.html
```

默认读取 `levels/level1.json` 至 `level5.json`，每境必须三套，境界序号必须与文件对应。输出为完整独立 HTML，直接以 `file://` 打开，无 CDN、HTTP 请求、字体下载或服务器。可以用 `--catalogue FILE.json` 指定已经拼好的设计数组。

Three.js 原始库本身超过 `html_preview` 的 512000 字符上限。供 T3 内联预览使用的版本可执行：

```bash
node art/immortal-armor-atlas/core/build.js --compact \
  --output art/immortal-armor-atlas/core/test/atlas.compact.html
```

压缩版本将同一份完整 HTML 内嵌为 gzip，浏览器用原生 `DecompressionStream` 离线展开，无外部依赖。普通版本兼容范围更广；压缩版本需要支持该 API 的现代浏览器。将压缩 HTML 全文依次交给 `html_preview`、`html_render`。不启动服务器，不使用 `preview_*`。

`viewer.html` 保留冻结的三个占位符各一次：`__THREE_JS__`、`__ENGINE_JS__`、`__CATALOGUE_JSON__`。`build.js` 负责替换与 JSON 内嵌转义，主代理也可以自行按契约替换。所有界面脚本与样式均已在模板内，不需要额外 helper 文件。

## API 与几何

`engine.js` 从 `globalThis.THREE` 读取 Three.js，公开 `globalThis.ArmorEngine`，也支持 `module.exports`。Node 示例：

```js
globalThis.THREE = require('./tools/web/three.min.js');
const E = require('./art/immortal-armor-atlas/core/engine.js');
E.validateDesign(spec);
const armor = E.buildDesign(spec, {avatar: true});
const ingot = E.buildIngot(spec);
console.log(E.meshStats(armor));
const obj = E.exportOBJ(armor);
E.dispose(armor);
E.dispose(ingot);
```

- 支持全部九种冻结形状：`loft / tube / ribbon / panel / torus / ellipsoid / gem / box / ingot`。
- `loft` 有外壳、内腔、开口边缘厚度、局部角度与实体端盖；冠尖内腔收合时不会产生负半径。支持偏心、扭转、波纹和倒序截面。
- `tube` 支持逐点变径、Catmull-Rom 曲线和真实端面；`ribbon` 有逐点宽度、法线、实体侧面和端面。
- `panel` 支持凹多边形、倒角与真正的金属管嵌边；拒绝自交或非相邻边接触的轮廓。
- `ingot` 为低扁削角实体，顶面收拢、下缘倒角，主体、顶面、边缘分别分组。
- 每个 Mesh 均有 `componentId / role / materialKey`。锭的多材质网格另有 `materialKeys`。真实概念人台的 role 是 `avatar`，与衣甲四件区分。
- 校验无未知字段容忍分支；错误指出数组序号和字段路径。没有未知 shape 退回 box 的路径。
- OBJ 导出顶点、法线、真实三角面、role 分组及材质名，应用父级与组件世界矩阵，反射变换会翻转面序。OBJ 本身不含 MTL，配色与物理材质参数可从同套 JSON 获取。
- `dispose` 对组内共享 geometry/material/texture 去重后释放，并摘除对象。不要把不同设计组的资源人为交叉共享给彼此后再单独释放。

## 展示行为

摄影棚使用统一布光、室内软箱环境反射和地台；材质使用线性空间运算与 sRGB 输出。全套含人台，单件移去人台并自动取景，锭按自己独立的原点构建，展示时整体抬升至台面，避免下半部埋入地台。

缩略图不是图片占位：逐套构建，使用同一个 WebGLRenderer 向独立目标渲染，回读成内嵌 PNG 后立即释放临时模型与渲染目标。至多同时保留当前主模型与一个缩略图模型；不让十五套模型同时常驻。静止画面按需渲染；只有开启转台才持续请求帧。

五境选择、每境三款、完整矩阵或本境筛选、整套/四件/锭、前侧背和四分之三视角、鼠标/触屏旋转缩放、键盘视角操作、材料谱和中文四件说明均由模板消费同一套数据。文本使用 `textContent` 写入，内嵌 JSON 对 `<` 做转义。

## 局部验证

```bash
# 独立 fixture，无需等待 levels。
node art/immortal-armor-atlas/core/test/test_engine.js --fixture-only
node art/immortal-armor-atlas/core/build.js --fixture \
  --output art/immortal-armor-atlas/core/test/fixture.html

# 一次性检查当前已有的各境；不存在的记录 not_available，不等待、不轮询。
node art/immortal-armor-atlas/core/test/test_engine.js

# 指定有具体失败证据的境界复验。
node art/immortal-armor-atlas/core/test/test_engine.js --levels=3

# file:// 浏览器检查。默认复用本机已有 Chromium / Playwright，不安装依赖。
node art/immortal-armor-atlas/core/test/test_viewer.js
```

浏览器测试工具路径可通过 `ARMOR_PLAYWRIGHT_PACKAGE` 与 `ARMOR_CHROMIUM` 指定。测试矩阵把同一个校样复制为十五份，标题明确标记“接口校样 / 测试复制件”，仅用于验证矩阵、选择、生命周期与内存；不构成十五套作者设计的造型验收。

证据在 `test/engine-report*.json`、`test/viewer-report.json`。第一轮失败另存 `engine-report-initial.json`。首轮发现冠尖内腔的半径边界处理问题，已实现收合并复验；测试也曾将 Three.js 内的命名空间 URL 误判为资源依赖，现按实际资源引用检查，浏览器额外断言 HTTP 请求为零。`fixture.json` 是可直接检查和修改的作者式输入，覆盖九种几何和所有公开 API。测试生成的 HTML、PNG 和下载文件仅为可再生成证据，主代理可按交付需要取用。

用户审美认可、真实 GPU 性能、游戏内效果、游戏玩法以及 Gradle 均不由本目录测试证明。

## 集成修订

主代理在实际薄衣料上发现离屏缩略图的深度精度不足，已将缩略摄影机近远面按模型包围范围收紧。原始显示问题与诊断图保留在 reports/immortal-armor-atlas/review/，不当作造型分片问题。

可传 `--thumbnails PATH` 使用同源实拍缓存，省去首次逐套构建缩略图的等待。缓存必须包含十五个设计的内容哈希和 engine/viewer 哈希；不匹配会明确失败。此缓存由 capture.js 生成，是真实模型渲染，完整交互模型仍实时构建。普通无缓存模式继续可用。
