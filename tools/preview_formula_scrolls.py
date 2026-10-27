"""Preview generated alchemy scrolls beside the established forging template."""
from pathlib import Path
import argparse
import html
import json
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]


def generate(out, root=ROOT):
    out=Path(out);out.mkdir(parents=True,exist_ok=True)
    base=root/'src/main/resources/assets/myvillage'
    rules=json.loads((root/'src/main/resources/data/myvillage/alchemy/rules.json').read_text())['recipes']
    names=json.loads((base/'lang/zh_cn.json').read_text())
    def card(item,label=None):
        path=base/f'textures/item/{item}.png';dest=out/path.name;dest.write_bytes(path.read_bytes())
        label=label or names['item.myvillage.'+item]
        return f'<figure><div class="icon"><img src="{dest.resolve()}" alt="{html.escape(label)}"></div><figcaption>{html.escape(label)}</figcaption></figure>'
    reference=card('pattern_xuantie_chestplate','器谱 · 木轴金属箍')+card('formula_huiling','丹方 · 竹轴短绢结')
    cards=[];sheet=Image.new('RGB',(1000,660),'#252c30');draw=ImageDraw.Draw(sheet);font=ImageFont.truetype(str(base/'font/panel_body.ttf'),18)
    for index,recipe in enumerate(rules):
        item=recipe['formula'].split(':')[1];cards.append(card(item))
        image=Image.open(base/f'textures/item/{item}.png').convert('RGBA');x=(index%5)*200+36;y=(index//5)*220+12
        big=image.resize((128,128),Image.Resampling.NEAREST);sheet.paste(big,(x,y),big)
        draw.text((x-12,y+136),names['item.myvillage.'+item],font=font,fill='#ded6be')
        small=image.resize((32,32),Image.Resampling.NEAREST);sheet.paste(small,(x+45,y+170),small)
    sheet.save(out/'sheet.png')
    page='''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><style>:root{font-family:var(--font-sans);color:var(--foreground);--icon-size:96px}body{margin:0}nav{display:flex;gap:8px;align-items:center;flex-wrap:wrap;margin-bottom:16px}button{font:inherit;border:1px solid var(--border);border-radius:var(--radius);background:var(--secondary);color:var(--foreground);padding:7px 12px;cursor:pointer}button.selected{background:var(--primary);color:var(--primary-foreground)}.grid{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:12px}.reference{grid-template-columns:repeat(2,minmax(0,1fr));margin-bottom:16px}figure{margin:0;padding:16px 4px;background:var(--secondary);border-radius:var(--radius);text-align:center}.icon{height:100px;display:grid;place-items:center}img{width:var(--icon-size);height:var(--icon-size);image-rendering:pixelated}figcaption{font-size:12px;margin-top:10px}p{font-size:13px;line-height:1.6;color:var(--muted-foreground);margin:16px 0 12px}@media(max-width:520px){.grid{grid-template-columns:repeat(2,minmax(0,1fr))}}</style><nav><span>图标大小</span><button onclick="size(16,this)">16 像素</button><button onclick="size(32,this)">32 像素</button><button class="selected" onclick="size(96,this)">放大</button></nav><div class="grid reference">'''+reference+'''</div><p>以下为正式生成的 14 种丹方贴图。筑基、补基丹方按配方的固定品质显示绿、蓝、紫边饰，其他丹方使用素绢边饰。</p><div class="grid">'''+''.join(cards)+'''</div><script>function size(n,b){document.documentElement.style.setProperty('--icon-size',n+'px');document.querySelectorAll('button').forEach(e=>e.classList.toggle('selected',e===b))}</script></html>'''
    (out/'index.html').write_text(page)
    return out/'index.html'


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();print(generate(args.out))
