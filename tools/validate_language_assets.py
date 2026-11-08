#!/usr/bin/env python3
"""校验共享语言文件，并严格核对JAR字节；顺序漂移也返回失败。"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from tools.language_assets import language_difference, language_text, load_language

LANGUAGES = ('zh_cn', 'en_us')


def validate(root: Path = ROOT, jar: Path | None = None, baseline: Path | None = None) -> list[str]:
    errors = []
    archive = None
    try:
        expected = json.loads(baseline.read_text())['files'] if baseline is not None else None
        if jar is not None:
            archive = zipfile.ZipFile(jar)
        for locale in LANGUAGES:
            name = f'assets/myvillage/lang/{locale}.json'
            path = root / 'src/main/resources' / name
            try:
                raw = path.read_bytes()
                text = raw.decode('utf-8')
                doc = load_language(text)
                if language_text(doc, doc) != text:
                    errors.append(f'{locale}：JSON格式不符合UTF-8、两格缩进和末尾换行约定')
                if expected is not None and expected[name] != text:
                    diff = language_difference(expected[name], text)
                    errors.append(f'{locale}：生成阶段改写了已固定语言输入；' + json.dumps(diff, ensure_ascii=False))
                if archive is not None:
                    packaged = archive.read(name)
                    if packaged != raw:
                        diff = language_difference(text, packaged.decode('utf-8'))
                        errors.append(f'{locale}：JAR字节不一致；' + json.dumps(diff, ensure_ascii=False))
            except (ValueError, OSError, KeyError) as exc:
                errors.append(f'{locale}：{exc}')
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as exc:
        errors.append(f'语言校验输入读取失败：{exc}')
    finally:
        if archive is not None:
            archive.close()
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path)
    parser.add_argument('--baseline', type=Path)
    parser.add_argument('--snapshot', type=Path)
    args = parser.parse_args()
    errors = validate(jar=args.jar, baseline=args.baseline)
    if errors:
        print('\n'.join(errors))
        return 1
    if args.snapshot is not None:
        files = {}
        for locale in LANGUAGES:
            name = f'assets/myvillage/lang/{locale}.json'
            files[name] = (ROOT / 'src/main/resources' / name).read_text()
        args.snapshot.parent.mkdir(parents=True, exist_ok=True)
        args.snapshot.write_text(json.dumps({'version': 1, 'files': files}, ensure_ascii=False, indent=2) + '\n')
    print('语言资源检查通过' + ('，JAR与当前源文件逐字节一致' if args.jar else ''))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
