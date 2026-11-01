# 驿道原生检查

仅在 Colab 使用固定源码、匹配 JAR 和已核对资源运行。测试工具不会加载 RoadWeaver 外部模组，不将证据代码放入生产 source set。

```bash
python3 tools/roads_smoke/run.py --jar build/libs/myvillage-0.105.5.jar \
  --source <固定源码身份> --out reports/roads/native --capture
```

创建仅由该工具拥有的新世界，种子与原包试验相同。依次运行纯原生规划、施工前读回、箱子覆盖拒绝、暂停与重启续建、整幅地坪／净空／铺装读回、门内原方块保持、完成后重启。可选真实客户端使用映射前进键，从两座门内分别走过城界；截图附实际维度和位置读回。短距离映射步行不表示走完城际全程，也不代表玩家外观或 FPS 验收。

`result.json` 保留 selected/completed/failed/unrun；`before.json`、`after.json`、`after-restart.json` 保留所有列与全部失败位置。构建 JAR 与启动目录逐文件相等后才会启动。
