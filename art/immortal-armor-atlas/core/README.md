# Minecraft 设计册内核

当前人台恢复MC标准宽臂比例：头8立方、胸8×12×4、四肢4×12×4，总高32模型像素。旧版真人雕塑人台已移除。

接口与像素数据以 ../CONTRACT.md 为准。build.js仍提供普通和--compact离线HTML、--catalogue指定目录、--thumbnails同源缓存。engine.js保留七个公共API及旧primitive兼容；当前十五款由MC作者采用硬边体块重新建立。

pixelArt由DataTexture最近邻过滤呈现；OBJ写出UV，pixel_png.js将同一调色板和像素行编码为PNG。材质释放会回收共享纹理，导出包MTL以textures/*.png引用。

node art/immortal-armor-atlas/core/test/test_engine.js 运行内核与各境几何检查。test_viewer.js是接口fixture测试，不能冒充十五款实际验收；当前完整目录交互由checks/interaction.js检查。

无外部CDN、不启动服务；内联视觉仍通过html_preview与html_render。用户外观、真实设备性能与游戏适配尚未验证。
