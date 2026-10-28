# Minecraft 设计册接口

本版修正了旧稿的人台与造型方向。HTML 表示预览交付方式，服装仍必须保持 MC 风格。旧版修长人台的坐标和271项回归不适用于本版。

## 人台与坐标

标准宽臂方块人：头8×8×8、躯干8×12×4、四肢4×12×4，总高32模型像素。1模型像素=1/16米。Y向上、正面+Z。头y24..32、胸y12..24、腿y0..12；手臂中心x±6，腿中心x±2。四件角色仍为head/chest/legs/boots，锭使用独立ingot.components。

作者通过 mc_common.Builder 使用像素坐标，数据输出统一转成米。主体保持方块比例、真实厚度与硬边，按整身构图选择方袖、斜襟、长裾及局部甲片。**整套不能整体全部由分片构成；允许小部分分片、鳞片、叠甲和护肩提高层次感。横向纹理接缝允许。** 不得再泛化为一概禁止。

## 数据与唯一作者

- authors/mc_level1.py 至 mc_level5.py：各境三套的基础造型和说明，build()返回三个spec；mc_ornaments.py 是主代理拥有的纹样与星河/九色精修输入，统一由minecraft.py组合后生成。
- authors/mc_common.py：像素坐标构建和材质辅助；authors/minecraft.py：验证与生成；levelN.py为兼容入口。
- levels/levelN.json 与 levelN-design.md 均由作者生成，不手改输出。
- 每套保留id/name/level/realm/style/tagline/palette/description/silhouette/materialStory/progression/segmentation/parts/ingot/materials/components字段。四件说明、材质故事与进阶必须对应实际造型，合计至少500汉字。
- 本批衣甲使用box和零倒角panel。引擎仍保留旧九种primitive供兼容测试；新增造型通过作者明确选择，不把技术实现选型扩大成新的用户审美禁令。

## 像素材质与导出

材质仍有name、color、metalness、roughness及可选opacity/emissive/emissiveIntensity。新增pixelArt={kind,palette,rows,mapping}，kind为cloth/metal/jade/glass/trim，palette为2至10种十六进制颜色，rows为16或32行的等宽方形数字字符阵列。每个字符索引调色板；未知索引或尺寸明确拒绝。mapping缺省为tile，保持模型像素密度；fit把完整纹样映射到指定衣面，供刺绣、镶纹与织边使用。闭合透明定幅面只绘外表面，避免前后纹样重叠。

HTML采用实际DataTexture、最近邻采样、硬面法线与面向表面的UV；不以CSS像素化覆盖光滑旧模型。MC材质由作者给定低金属度与粗糙表面，材质亮暗主要来自像素绘制。OBJ输出v/vt/vn及f，MTL引用同源textures/*.png，PNG由相同pixelArt无损编码，不能只给有颜色而无贴图的OBJ。

## 展示、验证与边界

ArmorEngine仍提供validateDesign/buildDesign/buildIngot/buildAvatar/meshStats/exportOBJ/dispose七个接口。页面自包含，支持15款、四件、锭、视角、拖动缩放和下载；缓存缩略图核对数据与内核身份。

checks/minecraft.js检查当前MC坐标中的身体包覆、方脸无遮挡、体块边界、像素材质/UV与OBJ。core/test/test_engine.js另检查准确人台比例、PNG颜色和最近邻采样；checks/interaction.js检查真实十五款的90种模式组合。技术通过不代替主代理逐套看图，更不代替用户审美认可、实际游戏动作、FPS或多人验证。

不运行Minecraft或Gradle，不把这些设计文件冒充游戏注册、实际模型适配或动画绑定。工作保留在设计分支，不合并main。
