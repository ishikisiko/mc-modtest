#!/usr/bin/env python3
"""Bundle the authored prototype into a self-contained HTML file."""
import argparse
from pathlib import Path

ROOT = Path(__file__).resolve().parent


def build() -> str:
    page = (ROOT / 'index.html').read_text(encoding='utf-8')
    for marker, filename in [('STYLE', 'style.css'), ('CORE', 'core.js'), ('APP', 'app.js')]:
        source = (ROOT / filename).read_text(encoding='utf-8')
        if filename.endswith('.js'):
            source = source.replace('</script', '<\\/script')
        page = page.replace('/*__' + marker + '__*/', source)
    if '/*__' in page:
        raise ValueError('Unresolved inline source marker')
    return page


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', required=True, type=Path)
    args = parser.parse_args()
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(build(), encoding='utf-8')
    print(args.out)
