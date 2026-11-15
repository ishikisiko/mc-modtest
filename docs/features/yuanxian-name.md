# 源仙名称与指令入口

0.133.1起，模组正式显示名为“源仙”。模组列表、主创造栏、按键分类和资源包说明使用同一名称。名称、命名空间和指令入口由 `tools/branding.json` 维护；`python3 tools/gen_branding.py` 同步元数据、Gradle标识、共享语言和Java常量，`--check` 只核对输出。Gradle编译与资源复制接入同一生成器。

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

现有子命令保持原名称。0.134.0将mod ID、内部注册ID、物品、方块、实体、网络、数据组件、翻译键、资源目录与持久化标识统一改为 `yuanxian`，例如 `/summon yuanxian:cultivator`；服务器配置目录为 `config/yuanxian/`。新版本不再注册旧的 `/myvillage` 根入口。旧版本验收记录保留当时使用的命令。

本版需要新建世界，不提供 `myvillage:` 别名或旧存档迁移。Java实现包 `com.example.myvillage` 保持现有路径；它用于加载实现类。所有生成资源均由所属生成器从作者输入重新导出，包含NBT结构、压缩大陆规则及运行时共享资源包。`tools/validate_namespace.py` 同时检查源资源或最终JAR中的旧ID、压缩载荷和模型引用。

NPC设施按当前布局的精确摘要绑定，不再导出或接受历史布局摘要别名。森林候选需容纳完整树冠与树干；越界候选不会进入宗门施工。

命令注册使用 `ModCommands.root()` 或 `adminRoot()`。管理员入口将权限放在各模块的一级子命令上，原子命令更高的权限要求也会保留，避免共享根节点合并时丢失限制。快捷入口直接重定向到完整入口。帮助内容按当前执行者权限过滤；Brigadier服务器补全接口与Minecraft客户端命令树过滤分别处理。

命名空间变更需要检查注册表、资源引用、打包结果、新大陆生成、网络意图以及保存重启。战斗手感、外观设计和真实硬件FPS由玩家验收。

构建、原生检查与下载见 [0.134.0命名空间交付记录](../testing/2026-11-16-yuanxian-namespace.md)。
