# 0.95.1 功法活卷基础验证

用户临时认可活卷方案，要求控制 jar 体积，并只使用 Colab 做基础测试，视觉由用户验收。本次没有启动本机或远程 Minecraft，没有把网页预览计作原生游戏验收。

## 固定来源与结果

- 最终模组提交：`339c6852fec5ad23cc090aff13c1e084bd15af08`。
- 固定源码归档 SHA-256：`aa9eaa89777dd58962b3bae75a2a4af2ff4b2d225d9ee9b437e923e2e50f0690`，6,584 个文件。
- Colab 会话：`technique-scroll-20261027`，High-RAM CPU，实测 8 vCPU、约 51 GiB 内存，JDK 21。
- 最终目录：`/content/technique-scroll-validation/r2/339c6852fec5`。
- 定向 JUnit 共 **41 项通过**，失败、错误、跳过均为 0；同一次最终构建成功生成 jar。
- 选择范围：`TechniqueScrollTest`、`TechniqueGridLayoutTest`、`PanelReadoutsTest`、`TechniqueGrowthTest`、`TechniqueGrowthPayloadTest`。
- 新增检查涵盖首次打开不伪造反馈、服务器确认后增长／落印、准备和中断、隐藏时间不补跑、切换功法、旧快照回退、轨迹边界、实际 H 尺寸内控件分离，以及资源尺寸与字节预算。
- 本地仅执行专用资源导出 `--check`、原成长配置 `--check`、纯布局／动效源码 `javac` 和 `git diff --check`；未运行本地 Gradle。

## 资源体积与打包审计

四张运行时贴图均为 **576×384、128 色 PNG**；PNG 与 `.mcmeta` 合计 **692,831 字节（约 677 KiB）**。导出配置限制每张不超过 200 KiB、全部不超过 768 KiB。

- 最终 jar：**28,273,457 字节**。
- 相较已交付 0.95.0 的 27,553,693 字节，增加 **719,764 字节（约 703 KiB，2.61%）**。
- 1,681 个编译 class 和 4,458 个构建资源与 jar 逐字节一致；6,584 个固定源文件无变化。
- 仅复用既有 `book.ttf`、`brush.ttf` 和 `panel_body.ttf`；无新增字体。高分辨率源图、提示词、网页、视频与生成脚本未进入 jar。
- 四个新纹理路径及尺寸、单文件和总量预算均经最终 jar 审计；完整清单在 `jar-audit.json`。

交付 jar SHA-256：

`3d64897c23bf2beaca166832b48e9a1d921acd24a328500889a304f7498f918e`

## 两轮关系

首轮 `057abbce` 已通过相同 41 项检查与构建。随后补上参悟确认后的印记落下效果，仅修改 `TechniqueScrollButton.java` 和 `TechniqueGrowthPage.java`，形成最终提交 `339c6852`。

最终轮先核对这两个精确变更路径与所有其他源文件，再复用 540 个输入完全不变的结构／世界生成资源。没有复用 Java 编译输出或测试结论；最终提交重新编译、测试并打包，最终轮构建日志为 35 秒。两轮原始日志、JUnit XML 和源归档身份均保留。

首轮历史生成器只重排两份语言 JSON 的键顺序；核对语义相等后，在编译前恢复固定归档中的原始字节，前后哈希已记录。不存在放宽断言或拼接不同源码测试结果。

## 验证边界与会话保留

原生 H 点击、游戏内字体／外观、动画手感、音效、FPS 和多人均为 `not_verified`，按用户要求交给用户验收。0.95.0 的原生采集结果仅属于历史版本；其固定按钮坐标采集器尚未适配新的活卷布局，不作为本次验收入口。

用户在交付过程中明确要求保留 Colab，因此会话 **保留运行**，未执行 `colab stop`。源码、依赖缓存、最终构建目录和原始记录均留在该会话中，供后续继续使用；交付产物另行下载并校验。

公开产物目录：`https://assets.ishiki333hhh.win/technique-scroll-0.95.1/339c6852/`。本地原始产物位于忽略目录 `reports/technique-scroll-colab/`。
