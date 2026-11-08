# 四项元素亲和与共享语言生成修复

版本0.122.1，协议66；基于 main `5fe1827997d3abeed866f918ee6f6f4a04b15329`。功能说明见[五行亲和](../features/elemental-affinity-fates.md)，生成规则见[共享语言资源](../features/language-resource-generation.md)。

## 输入与检查范围

- 复用用户明确保留的 Colab `aptitude-points-20261108`、Gradle依赖和生成缓存。
- 四项亲和的首次运行输入摘要为 `20f3abec84fd3f3d21b574ba7a6ee632967a3f63be9e20aecd4a42b6ec5aaa01`；生成前逐项校验完整清单。
- 语言根因修复后的输入摘要为 `185c78f610f68b9d0c29fe7a46185f6ab68322521b4af9af674434d11d5eccb9`。
- 天命静态校验与49项相关Python检查通过；另有7项语言顺序、重复键、内容差异和严格JAR比较回归通过。
- Java仅选择6类：`ElementAffinityTest`、`StrongestTimedEffectTest`、`ControlStateTest`、`FateAbilityCooldownsTest`、`HealingWindowTest`、`ElementalArtsTest`。首次测试与JAR构建成功，用时5分39秒。

语言修复只涉及Python生成、构建检查和文本顺序。第二轮复用第一次已通过的Java检查；构建前核对所有Java运行及测试输入未变，交付前比较两轮JAR的全部class文件字节哈希。

## 保留的首次语言失败

首次JAR内容核对在中文语言文件SHA256检查处失败。继续对两份语言文件逐键比较，新增、删除、改值均为0，差异为键顺序。原始差异记录保存在证据包的 `cloud/lang-mismatch-1.json`。

| 文件 | 生成前SHA256 | 首次JAR中的SHA256 |
|---|---|---|
| zh_cn.json | `71a9be47db5c1c12e5ad5fff16c3fbea242cea272413248530f7c857c0d553f5` | `278b14e8d067eaed2e8d812aaf4b5909e37f2a72712ce3884cf8c1b5685bbef1` |
| en_us.json | `7c587e914f40bfe606cd2cd82b88fcc529b3e1302ea5cc4044e087f9b4dd5dbd` | `d6aacc58aef70068936344909106fa4184ab38804121376204722f61749a47a7` |

根因是城主府和院落生成器先过滤自有键，再追加回共享文件；正常构建又会执行这两个入口并写回源文件。修复为公共保序合并，保留仍有效旧键的位置，继续允许更新自有值和删除过期键。未将字节检查改为JSON相同即可通过。

Gradle现在先捕获语言输入，在 `processResources` 复制前严格核对快照，在 `jar` 完成后严格核对包内字节。回归专门确认：内容相同但键顺序不同仍会失败，真实改值和删除也不会被忽略。

## 最终结果与交付

- Java selected=6类／38项，completed=6类／38项，failed=0，skipped=0。
- 49项天命Python检查、7项语言回归通过。语言修复后未重复Java测试；两轮JAR的全部class文件逐字节一致，Java运行与测试输入也一致。
- 第二轮资源构建6分36秒通过；生成前后的中英文语言SHA256均保持首次输入值，复制前快照校验与JAR结束校验均通过。
- 包内30项天命定义与两份语言文件共32项逐字节核对通过：23项已实现、7项占位；其余6项设计候选保留在设计目录。
- 最终 `myvillage-0.122.1.jar` 为55,101,319字节，SHA256 `086f81e7de5c6ad11fd21c03ef2ed27137cb1a6c45ef313a8705a16a9a06ebbf`。

构建日志、JUnit XML、首次语言差异、生成前快照、完整输入清单和class一致性证据保存于 `build/element-affinity-evidence-0.122.1.zip`。本记录在构建后补写，其余受控输入与最终清单一致。

## 验收边界

本轮没有运行真实玩家交互、多人或长期平衡验收，相关结论保持 `not_verified`。Colab会话和缓存按用户要求保留。
