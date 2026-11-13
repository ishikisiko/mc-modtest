"""从游戏现有字体导出报幕候选的文字轮廓；生成结果只用于预览。"""
from __future__ import annotations
import argparse,hashlib,json,shutil
from pathlib import Path
from xml.sax.saxutils import escape
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.boundsPen import BoundsPen

ROOT=Path(__file__).resolve().parents[3]
BACKGROUND=Path('/home/ubuntu/.local/share/myvillage-assets/www/previews/qianji-infill-20261113/headquarters-front.png')
WORDS={'region':'中州','town':'云山城'}

def title(font:TTFont,word:str,vertical:bool,color:str)->str:
    glyphs=font.getGlyphSet();cmap=font.getBestCmap();em=font['head'].unitsPerEm
    paths=[];bounds=[];cursor=0
    for ch in word:
        glyph=glyphs[cmap[ord(ch)]];pen=SVGPathPen(glyphs);glyph.draw(pen)
        box=BoundsPen(glyphs);glyph.draw(box);x0,y0,x1,y1=box.bounds
        dx=(em-glyph.width)/2 if vertical else cursor;dy=-cursor if vertical else 0
        paths.append(f'<path transform="translate({dx:g} {dy:g})" d="{pen.getCommands()}"/>')
        bounds.append((x0+dx,y0+dy,x1+dx,y1+dy));cursor+=em*1.1 if vertical else glyph.width+em*.14
    pad=em*.07;left=min(v[0] for v in bounds)-pad;top=-max(v[3] for v in bounds)-pad
    width=max(v[2] for v in bounds)-left+pad;height=-min(v[1] for v in bounds)-top+pad
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{left:g} {top:g} {width:g} {height:g}" role="img"><title>{escape(word)}</title><g fill="{color}" transform="scale(1 -1)">'+''.join(paths)+'</g></svg>'

def main():
    p=argparse.ArgumentParser();p.add_argument('--out',type=Path,required=True);a=p.parse_args();a.out.mkdir(parents=True,exist_ok=True)
    sources={}
    for family,name in (('brush','brush.ttf'),('book','book.ttf')):
        path=ROOT/'src/main/resources/assets/myvillage/font'/name;sources[name]=hashlib.sha256(path.read_bytes()).hexdigest()
        with TTFont(path) as font:
            for key,word in WORDS.items():
                for vertical,orientation in ((False,'h'),(True,'v')):
                    for ink,color in (('light','#f3ead3'),('dark','#30382e')):
                        if family=='book' and (vertical or ink=='dark'):continue
                        (a.out/f'{family}-{key}-{orientation}-{ink}.svg').write_text(title(font,word,vertical,color),encoding='utf-8')
        license=ROOT/'src/main/resources/assets/myvillage/font'/name.replace('.ttf','-ofl.txt');shutil.copy2(license,a.out/license.name)
    shutil.copy2(BACKGROUND,a.out/'background.png');sources['background.png']=hashlib.sha256(BACKGROUND.read_bytes()).hexdigest()
    (a.out/'sources.json').write_text(json.dumps({'purpose':'报幕候选叠加，不是游戏运行证据','samples':WORDS,'background_origin':str(BACKGROUND),'sha256':sources},ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({'output':str(a.out),'files':len(list(a.out.iterdir())),'sources':sources},ensure_ascii=False))

if __name__=='__main__':main()
