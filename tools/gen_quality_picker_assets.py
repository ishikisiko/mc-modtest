#!/usr/bin/env python3
"""Authored item model and bilingual text for the creative-acquired quality picker."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LANG = {
    'item.myvillage.quality_picker': ('Quality Picker', '品质取物器'),
    'tooltip.myvillage.quality_picker': ('Creative tab acquisition. Hold and use in any mode to obtain mod items.', '创造栏获取；手持右键，任意模式取出模组物品。'),
    'screen.myvillage.quality_picker.search': ('Search name or item ID', '搜索名称或物品 ID'),
    'screen.myvillage.quality_picker.empty': ('No matching items.', '没有匹配的物品。'),
    'screen.myvillage.quality_picker.one': ('Take 1', '取一个'),
    'screen.myvillage.quality_picker.stack': ('Take stack', '取一组'),
    'screen.myvillage.quality_picker.requested': ('Request sent', '请求已发送'),
    'message.myvillage.quality_picker.full': ('Inventory full. Nothing was added.', '背包空间不足，未取出物品。'),
    'message.myvillage.quality_picker.given': ('Received %s ×%s', '已取出 %s ×%s'),
}
for quality, en, zh, hint_en, hint_zh in [
    (0, 'Base', '原样', 'Original item / Mundane weapon', '原始物品／凡品兵器'),
    (1, 'Low', '下品', 'Lower quality', '下品'),
    (2, 'Mid', '中品', 'Middle quality', '中品'),
    (3, 'Upp', '上品', 'Upper quality', '上品'),
    (4, 'Top', '极品', 'Perfect quality', '极品'),
]:
    LANG[f'screen.myvillage.quality_picker.quality.{quality}'] = (en, zh)
    LANG[f'screen.myvillage.quality_picker.quality_hint.{quality}'] = (hint_en, hint_zh)


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')


def main():
    base = ROOT / 'src/main/resources/assets/myvillage'
    write(base / 'models/item/quality_picker.json', {'parent': 'minecraft:item/knowledge_book'})
    for lang, index in [('en_us', 0), ('zh_cn', 1)]:
        path = base / f'lang/{lang}.json'
        data = json.loads(path.read_text())
        data.update({key: value[index] for key, value in LANG.items()})
        write(path, data)


if __name__ == '__main__':
    main()
