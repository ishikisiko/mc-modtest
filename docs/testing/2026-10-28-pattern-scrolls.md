# 通用卷轴器谱（0.99.6）

按已确认的卷轴设计实现十本炼器器谱：青锋剑、凌霄枪、四种工具和四件玄铁甲。通用底板、纸张与印章由 `tools/forging/patterns.py` 生成，边饰和布局由 `patterns.json` 持有；中央器物从现有专用模型及材质派生。腿甲和靴子的左右部件只在纸面上分开排布，不改任何装备本体。正式 PNG 为 64×64，由 32 像素网格绘制；现有器谱均为凡品边饰，五种品质样例仅进入预览目录，不新增品质玩法。

## 固定身份和实际检查

- 产品源码：`cd0ea7bf447ae7a687795ec293f4bad7f590e2c2`。
- jar：`myvillage-0.99.6.jar`，SHA256 `97c1ac346c3dc6922cb63ec8b15f8135d9d92c2a6f41dcd3ad0dde72f4fca819`。
- 本地 Python 3.13.13、numpy 2.2.6、Pillow 11.2.1；`test_forging_patterns`、`test_forging`、`test_forging_workshop` 共 24 项通过，炼器生成器 `--check` 与物品校验通过。
- 检查十本器谱均引用各自生成的 RGBA 贴图，外部纸卷逐像素共用，五种边饰保持中央器物与透明轮廓不变，16 像素缩略图仍为十个不同输出；双件图案绘制不修改原始装备网格。开发者已看放大图与 32 像素图，腿甲和靴子的并列排布修正后保留。
- Colab `armor-forging-20261027`，独立 `/content/pattern-scrolls-r1/`。构建前后核对全部 7,118 个受控文件；与已验证的不限库存候选相比，Java、玩法配置、装备 authored 模型均未变化。
- 仅执行 jar 构建，35 秒成功。jar 内十本器谱引用和尺寸正确，21 项改变的打包资源与源码字节相同；四本玄铁甲货单仍为 unlimited，旧合成配方仍不存在，对话暂停配置仍为 100。
- 复用相同输入生成的 540 个世界资源，核对最终 jar 每项哈希。没有借用旧 Java 测试或旧实机结果充作本轮执行。
- 本轮未运行 Java 测试、服务器或客户端，玩家实机外观、持握、重载、多人和 FPS 仍为 `not_verified`。未重启或更换用户电脑上的游戏实例。

## 环境记录和产物

默认 Python 缺少 numpy/Pillow，第一次生成在写资源前失败。独立 uv 环境最初选中 Python 3.11，造成已有模型 JSON 的浮点尾数差异；随后显式使用与原导出一致的 Python 3.13，全部无关模型恢复原字节，没有提交这些尾数变化。依赖固定在 `tools/forging/requirements.txt`，失败与处理记录在 `reports/pattern-scrolls/environment-notes.txt`。

正式资源预览在 `out/preview/pattern-scrolls-0.99.6/cd0ea7bf/`，含十本器谱、五种共用边饰样例、16／32 像素切换及放大查看。T3 内嵌预览检查无控制台错误。资源域名本轮仍返回 HTTP 502，未修改现有服务。

云端证据归档 `reports/pattern-scrolls/artifacts.tar.gz`，SHA256 `7233ab8f14a9afba7a260539b474548c0d49a512d0dfc3f364564d893bd2312b`，包含构建日志、源码清单与 jar。最终审计确认构建进程退出码为 0 并已回收；原用户保留的 Colab 会话继续保留。后续纯文档提交不改变产品源码和产物身份。
