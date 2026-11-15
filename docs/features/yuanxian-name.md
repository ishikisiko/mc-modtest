# 源仙名称与指令入口

0.133.1起，模组正式显示名为“源仙”。模组列表、主创造栏、按键分类和资源包说明使用同一名称。唯一名称输入为 `tools/branding.json`；`python3 tools/gen_branding.py` 同步元数据、共享语言和Java常量，`--check` 只核对输出。Gradle编译与资源复制接入同一生成器。

主命令为 `/yuanxian`，快捷入口为 `/yx`。两者共用同一棵命令树，参数、执行结果、补全和权限一致。直接输入任一入口，或输入 `帮助` / `help`，可查看自己有权限使用的一级子命令。

```text
/yuanxian
/yx
/yx 帮助
/yx drops status
/yx drops reload
/yx drops export
/yx world sects
```

现有子命令保持原名称。内部注册ID、物品、方块、网络和资源命名空间仍为 `myvillage`，例如 `/summon myvillage:cultivator`；服务器配置目录仍为 `config/myvillage/`。新版本不再注册旧的 `/myvillage` 根入口。旧版本验收记录保留当时使用的命令。

命令注册使用 `ModCommands.root()` 或 `adminRoot()`。管理员入口将权限放在各模块的一级子命令上，原子命令更高的权限要求也会保留，避免共享根节点合并时丢失限制。快捷入口直接重定向到完整入口。帮助内容按当前执行者权限过滤；Brigadier服务器补全接口与Minecraft客户端命令树过滤分别处理。

本次必要检查为名称生成一致、共享语言与命名空间、根节点合并顺序、快捷入口的参数和补全一致性、管理员限制，以及独立服务器中的实际命令执行。检查不涉及世界生成、战斗手感或外观设计变更。
