"""Manual preview of the generated scroll textures at inventory and enlarged sizes."""
from pathlib import Path
import argparse
import html
import json

from PIL import Image, ImageDraw, ImageFont

from tools.forging.patterns import PatternArt, ROOT


def generate(out, root=ROOT):
    out = Path(out); out.mkdir(parents=True, exist_ok=True)
    base = root / 'src/main/resources/assets/myvillage'
    rules = json.loads((root / 'src/main/resources/data/myvillage/forging/rules.json').read_text())['recipes']
    names = json.loads((base / 'lang/zh_cn.json').read_text())
    art = PatternArt(root)
    # Show armor first: this was the acceptance request, with the rest sharing its template.
    rules.sort(key=lambda r: not art.catalogue.armor(r['id']))
    cards = []
    sheet = Image.new('RGB', (1000,440), '#252c30')
    draw = ImageDraw.Draw(sheet); font = ImageFont.truetype(str(base / 'font/book.ttf'), 18)
    for index, recipe in enumerate(rules):
        item = recipe['pattern'].split(':')[1]
        source = base / f'textures/item/{item}.png'
        destination = out / source.name
        destination.write_bytes(source.read_bytes())
        label = names['item.myvillage.' + item]
        cards.append(f'<figure><div class="icon"><img src="{destination.resolve()}" alt="{html.escape(label)}"></div><figcaption>{html.escape(label)}</figcaption></figure>')
        icon = Image.open(source).convert('RGBA')
        x, y = (index % 5)*200+36, (index//5)*220+14
        enlarged = icon.resize((128,128), Image.Resampling.NEAREST)
        sheet.paste(enlarged, (x,y), enlarged)
        draw.text((x-12,y+136), label, font=font, fill='#ded6be')
        small = icon.resize((32,32), Image.Resampling.NEAREST)
        sheet.paste(small, (x+45,y+170), small)
    sheet.save(out / 'sheet.png')
    variants = []
    for quality, spec in art.config['qualities'].items():
        path = out / f'quality_{quality}.png'
        art.icon('xuantie_helmet',quality).save(path)
        variants.append(f'<figure><div class="icon"><img src="{path.resolve()}" alt="{spec["name"]}"></div><figcaption>{spec["name"]}</figcaption></figure>')
    page = '''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<style>:root{font-family:var(--font-sans);color:var(--foreground);--icon-size:96px}body{margin:0}nav{display:flex;gap:8px;align-items:center;flex-wrap:wrap;margin-bottom:16px}button{font:inherit;border:1px solid var(--border);border-radius:var(--radius);background:var(--secondary);color:var(--foreground);padding:7px 12px;cursor:pointer}button.selected{background:var(--primary);color:var(--primary-foreground)}.grid{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:12px}figure{margin:0;padding:16px 4px;background:var(--secondary);border-radius:var(--radius);text-align:center}.icon{height:100px;display:grid;place-items:center}img{width:var(--icon-size);height:var(--icon-size);image-rendering:pixelated}figcaption{font-size:12px;margin-top:10px}p{font-size:13px;line-height:1.6;color:var(--muted-foreground);margin:16px 0 12px}@media(max-width:520px){.grid{grid-template-columns:repeat(2,minmax(0,1fr))}}</style>
<nav><span>图标大小</span><button onclick="size(16,this)">16 像素</button><button onclick="size(32,this)">32 像素</button><button class="selected" onclick="size(96,this)">放大</button></nav>
<div class="grid">''' + ''.join(cards) + '''</div>
<p>以上是正式生成的十本器谱贴图。下方展示共用边饰的五种配色；现有器谱均使用凡品，不增加新的品质玩法。</p><div class="grid">''' + ''.join(variants) + '''</div>
<script>function size(n,b){document.documentElement.style.setProperty('--icon-size',n+'px');document.querySelectorAll('button').forEach(e=>e.classList.toggle('selected',e===b))}</script></html>'''
    (out / 'index.html').write_text(page)
    return out / 'index.html'


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    print(generate(args.out))
