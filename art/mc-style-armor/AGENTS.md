# MC 防具设计入口

先读 ART_RULES.md。用户已否定写实人体和任意曲面建模方向，必须按 Minecraft 方块人比例、硬边 cuboid 和像素贴图制作。不能通过“后续再适配”绕过。

保留主体完整，允许小部分分片增加层次。高级感与神仙气质应在 MC 造型语言中表达，不回退为朴素补丁衣。

src/catalog.py 是十五套说明与配色来源；src/build.py 是几何、像素图集与导出来源。仅改 authored 源再重建，不手改生成模型。

index.html、models/、previews/ 是生成交付；Git 保存可重建源码。下载完整包包含生成文件。先交付下载链接，再尝试推送独立分支，不合并 main。

tests/VALIDATION.md 明确实际验证边界；页面渲染、几何回读不等于 Blockbench 应用导入或 Minecraft 游戏内验收。用户审美未确认。
