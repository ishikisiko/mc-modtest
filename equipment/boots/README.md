# 靴子外观输入

`styles.json` 保存十五套靴子的尺寸、靴口形制、既有材质引用和逐像素GUI图稿。
`tools/equipment/boots.py` 将这些参数转换成完整方靴身、分级脚背、前伸鞋头、
贯通鞋底、低方跟和保留内孔的靴口。MC脚部与外层皮肤仍按原有左右腿骨骼包覆。
`mc_common.finish_boots` 在服饰细化完成后生成靴子，避免紫霄、九色和太虚的旧细化覆盖新造型。
作者调用链仍通过 `authors/minecraft.py` 生成五份 levels，再由原导出器提取真实网格。

旧玄铁靴的穿戴几何由 `equipment/xuantie_armor/equipment.json` 拥有；
`styles.json` 中的 `xuantie_boots` 只提供材质引用和GUI图稿，不重复维护穿戴尺寸。

本次16套整体协调继续使用这里的16张靴图像素行，由
`tools/equipment/armor_icons.py` 与另外48张图稿统一调色、轮廓和光向，见
[四槽图标规范](../icons/README.md)。全部16双通过 `neoforge:separate_transforms`
仅在 `gui` 视角使用 `minecraft:item/generated` 的16×16图稿；
`base` 保留实际新OBJ与其地面、展示框、第一／第三人称显示变换。
像素行中的 `b/s/l` 为靴筒色阶，`v/t` 为脚背与护头，`c` 为靴口，
`e/a` 为饰边与嵌色，`r` 为鞋底，`o/i` 为轮廓与口内阴影，`.` 为透明。
图稿使用对应套装的既有材质调色，不导入其他模组的像素资源。
轮廓与孔内阴影倍率由 `equipment/icons/armor.json` 统一拥有，不在靴型模块保留第二套调色逻辑。
实际查看的官方图标、模型源码和采用的轮廓原则见 [参考记录](REFERENCES.md)。

离线对照必须读取导出的OBJ、MTL、PNG与物品JSON。三维图统一正交比例，
GUI图固定16×16格，并同时展示原生16像素、32像素及最近邻放大图。
离线预览不能代替原生客户端、动作、光照、透明排序或玩家外观接受。
