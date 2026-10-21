# 四方聚灵阵：0.73.0 验证记录

当前状态：实现已提交，云端认证阻塞；**尚未编译验证，不是可试玩交付**。

## 固定源与本地检查

- 实现源：`cab53f08b8c470efe3768ee8ccd932efe8cdab0b`，分支 `feature/spirit-gathering-array`。
- `python3 tools/gen_gathering_assets.py --check` 通过。
- `python3 -m unittest tools.tests.test_gathering tools.tests.test_modal_runner`：29 项通过。
  输出中的 KeyboardInterrupt、name already in use 和 upload failed 为故障清理测试的预期模拟。
- Python 采集器／生成器／云端控制器语法检查与 `git diff --check` 通过。
- 正式模型／贴图已离线渲染查看；这不包含实际世界光照、动态阵纹、菜单或操作证据。

## 保留的失败运行

- run：`20261021T144541Z-49f1ac54`。
- preset：`gathering-runtime`，固定上述实现提交。
- 实际返回：`AuthError: Token has expired`。
- `state.json`：`status=failed`、`termination=not_created`；尚未创建 Sandbox，记录执行阶段耗时 0.77 秒。
- 失败位于认证／环境准备，未运行 Java 测试、jar 构建、服务器或客户端。
- selected：`structure,mapped,economy,overlap,two_players,restart,visual`；completed 为空，七项全部 unrun。
- 原始请求、失败状态与源归档保留在忽略的 `reports/modal-runs/20261021T144541Z-49f1ac54/`。

已启动标准 Modal 浏览器认证续期流程，等待账号持有者完成。恢复后使用同一生产源或记录清楚的新固定源继续必要检查，
不得将该失败计为产品通过，也不得自动改在本机启动 Gradle／Minecraft。

## 尚未验证

Java 编译和聚灵阵／原修炼结算单元测试、实际映射放块与菜单、加成／扣费、多人及相邻阵非叠加、同世界重启、
实际运行画面均为 `not_verified`。玩家审美、长期平衡、手感、音效、原生硬件 FPS 和广泛多人体验另行验收。

操作与规则：[KB62](../ai-kb/62_spirit_gathering_array.md)。
