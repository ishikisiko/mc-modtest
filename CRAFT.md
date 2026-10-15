# 可选 GenOps / CRAFT 工具

日常工作只需 [docs/WORKFLOW.md](docs/WORKFLOW.md)。CRAFT 不再是源码、文档、发布或验收的强制前门，也不要求 OpenSpec 或自动子代理。

用户明确选择 GenOps 时，可复用 `tools/genops/` 的局部流水线、任务范围、patch guard、命令日志、证据索引和状态查询。未开启时无需 run id、manifest、角色分工或审批。

- `python3 tools/genops/run_pipeline.py <pipeline> --executor manual` 仅准备所选工具任务；没有执行的 gate 不标通过。
- `tools/genops/check_frontdoor.py` 仅检查所选 GenOps run 的归属，不用于阻塞普通仓库编辑。
- `genops/contracts/` 中物品/实体资源契约仍由对应 validator 使用，不因停用流程而删除。
- `genops/schemas/`、视觉缺陷词典、参考拆解和已有专用生成工具保留。
- `.genops/` 是可重建运行索引；历史记录不代表当前源码或玩家验收。
- 路由结果只建议可选 pipeline；普通“修复/物品/构建/验收”字样不等于用户选择 CRAFT。

技术边界见 [tooling](docs/contracts/tooling.md)，具体可选命令见 [genops/README.md](genops/README.md)。
