#!/usr/bin/env python3
"""Export approved ink originals at bounded runtime resolution, preserving premultiplied alpha."""
import argparse
import hashlib
import io
import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "tools/cultivation_ink/source"
DESTINATION = ROOT / "src/main/resources/assets/myvillage/textures/gui/cultivation/ink"
NAMES = ("qi", "foundation", "core", "soul")
METADATA = b'{\n  "texture": {\n    "blur": true,\n    "clamp": true\n  }\n}\n'


def outputs():
    config = json.loads((SOURCE.parent/'export.json').read_text())
    size = config['size']
    if not 256 <= size <= 768:
        raise ValueError('Runtime ink dimensions must remain bounded')
    result = {}
    for name in NAMES:
        source = SOURCE/f'{name}.png'
        if hashlib.sha256(source.read_bytes()).hexdigest() != config['source_sha256'][name]:
            raise ValueError(f'Approved original changed: {name}')
        with Image.open(source) as original:
            if original.mode != 'RGBA': raise ValueError(f'Expected transparent RGBA original: {name}')
            image = original.convert('RGBa').resize((size,size), Image.Resampling.LANCZOS).convert('RGBA')
            if image.getchannel('A').getextrema() != (0,255): raise ValueError(f'Alpha lost: {name}')
            out = io.BytesIO(); image.save(out,format='PNG',optimize=True,compress_level=9)
        data = out.getvalue()
        if len(data) > config['max_texture_bytes']: raise ValueError(f'Texture budget exceeded: {name}')
        result[DESTINATION/f'{name}.png'] = data
        result[DESTINATION/f'{name}.png.mcmeta'] = METADATA
    if sum(map(len,result.values())) > config['max_total_bytes']:
        raise ValueError('Combined runtime ink budget exceeded')
    return result, config


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    failed = []
    result, config = outputs()
    for target, data in result.items():
        same = target.exists() and target.read_bytes() == data
        if args.check and target.exists() and target.suffix == '.png':
            with Image.open(target) as actual, Image.open(io.BytesIO(data)) as expected:
                same = actual.mode == expected.mode and actual.size == expected.size and actual.tobytes() == expected.tobytes()
            same = same and target.stat().st_size <= config['max_texture_bytes']
        if not same:
            if args.check: failed.append(str(target.relative_to(ROOT)))
            else:
                target.parent.mkdir(parents=True,exist_ok=True); target.write_bytes(data)
    total = sum(p.stat().st_size for p in result if p.exists())
    if total > config['max_total_bytes']: failed.append('combined byte budget')
    if failed:
        raise SystemExit("Ink asset drift: " + ", ".join(failed))
    print(json.dumps({'runtime_bytes':total,'size':config['size'],'originals_unchanged':True}))


if __name__ == "__main__":
    main()
