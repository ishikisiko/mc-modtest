"""共享语言资源的读取、顺序保持与差异分类。"""
from __future__ import annotations

import json
from collections.abc import Mapping


def load_language(text: str) -> dict[str, str]:
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError('重复语言键：' + key)
            result[key] = value
        return result
    result = json.loads(text, object_pairs_hook=unique)
    if not isinstance(result, dict) or any(not isinstance(k, str) or not k
                                           or not isinstance(v, str) for k, v in result.items()):
        raise ValueError('语言文件必须是字符串键与字符串值的对象')
    return result


def language_text(original: Mapping[str, str], generated: Mapping[str, str]) -> str:
    """保留仍有效旧键的位置；自有旧键清理由生成器负责，新键按生成器输入顺序追加。"""
    result = {key: generated[key] for key in original if key in generated}
    result.update((key, value) for key, value in generated.items() if key not in result)
    if any(not isinstance(k, str) or not k or not isinstance(v, str) for k, v in result.items()):
        raise ValueError('语言文件必须是字符串键与字符串值的对象')
    return json.dumps(result, ensure_ascii=False, indent=2) + '\n'


def language_difference(expected: str, actual: str) -> dict:
    before, after = load_language(expected), load_language(actual)
    added = sorted(after.keys() - before.keys())
    removed = sorted(before.keys() - after.keys())
    changed = sorted(k for k in before.keys() & after.keys() if before[k] != after[k])
    if added or removed or changed:
        kind = 'content'
    elif list(before) != list(after):
        kind = 'key_order'
    elif expected != actual:
        kind = 'format'
    else:
        kind = 'identical'
    return {'kind': kind, 'added': added, 'removed': removed, 'changed': changed}
