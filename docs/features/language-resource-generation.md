# 共享语言资源的生成与校验

`zh_cn.json` 和 `en_us.json` 由多个模块共同维护。曾经反复出现的字节差异，来自两个具体入口：

- `tools/lord_manors/generate.py` 先过滤城主府自有键，再把生成的键追加到中文文件末尾。
- `tools/reference_courtyard/minecraft.py` 对院落键采用相同操作，影响中英文文件。

新增其他模块的词条后，重新生成会移动这些既有键的位置。JSON中的文字可以完全相同，文件字节和SHA256仍会改变。Gradle会在构建时运行这两个生成器并写回 `src/main/resources`，因此在生成前固定的语言哈希与最终JAR发生差异；生成器的字节检查也会受其他模块新增键的位置影响。

## 当前规则

两个入口通过 `tools/language_assets.py` 的 `language_text(original, generated)` 合并输出：仍存在的旧键保留原位置和新值，删除生成器明确清理的过期键，再追加新键。其他模块的词条由原生成器保留。共享文件继续使用UTF-8、两格缩进和末尾换行。

后续生成器需要清理并重建自有键时，使用这一公共入口；普通既有键更新可原位赋值。新增翻译仍按所属模块维护，不通过全局排序移动其他模块条目。

## 检查入口

```bash
python3 tools/validate_language_assets.py
python3 tools/validate_language_assets.py --jar build/libs/myvillage-0.122.1.jar
python3 -m unittest tools.tests.test_language_assets
```

第一个命令检查重复键、字符串结构和序列化格式。第二个命令严格比较当前源与JAR；错误区分新增、删除、改值、键顺序及格式。键顺序不同仍返回失败，不以JSON内容相同替代字节一致。

Gradle在生成任务开始前保存两份语言输入快照，在 `processResources` 复制资源前严格比较快照，在 `jar` 写出后自动执行包内逐字节核对。构建前应先完成被修改资源族的生成；如果构建仍改变共享语言源，复制资源前就会失败并报告差异，应定位并修复所属写入入口，再同步正式输出和固定输入身份。

回归覆盖两个模块交错重建、反复生成、其他模块新词条保留、自有过期键删除、重复键拒绝，以及内容相同但顺序不同的JAR仍被拒绝。四项亲和实现的运行时测试与这次工具修复分开记录，未因语言顺序问题重复运行游戏验收。
