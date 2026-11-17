"""仅把独立设计和浏览器渲染器内联为预览，不生成任何游戏资源。"""
from pathlib import Path
import json
import subprocess

ROOT = Path(__file__).resolve().parent
frame = (ROOT / 'frame.html').read_text()
html = frame.replace('/* GEOMETRY */', (ROOT / 'geometry.js').read_text()).replace('/* RENDERER */', (ROOT / 'renderer.js').read_text())
(ROOT / 'index.html').write_text(html)
model = json.loads(subprocess.check_output(['node', '-e', f'process.stdout.write(JSON.stringify(require({json.dumps(str(ROOT / "geometry.js"))})))'], text=True))
(ROOT / 'design-data.json').write_text(json.dumps([{k: v for k, v in d.items() if k != 'boxes'} for d in model], ensure_ascii=False, indent=2))
print(json.dumps({'html_bytes': len(html.encode()), 'designs': [{'name': d['name'], 'boxes': len(d['boxes'])} for d in model]}, ensure_ascii=False))
