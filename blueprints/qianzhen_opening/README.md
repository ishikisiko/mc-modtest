# 私人阡镇学徒设施

从 `feature/qianzhen-apprentice-opening` 的已提交源 `d612f854` 整合药庐灵土与场景设施定位。这里只覆盖 `apothecary.json` 和 `story_scene.json`，其余五处建筑和道路沿用 `blueprints/qianzhen`。

`tools/qianzhen/story_assets.py --opening` 在临时目录组合 authored 输入再生成私人场景。普通生成调用保持原六处模板不变，供冻结 V6 主世界聚落使用。不得手改生成的 NBT 或场景 JSON。未合入该分支的主世界招募入口及无关生成器试验。
