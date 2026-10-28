"""Crop actual renderer frames into consistently framed thumbnails and contact sheet."""
from pathlib import Path
import sys,subprocess
from PIL import Image,ImageDraw,ImageFont
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'src'))
from catalog import DATA,REALMS
font=subprocess.check_output(['fc-match','-f','%{file}','sans:lang=zh']).decode()
f=ImageFont.truetype(font,28);small=ImageFont.truetype(font,18);title=ImageFont.truetype(font,44)
sheet=Image.new('RGB',(1200,2600),(22,31,36));d=ImageDraw.Draw(sheet)
d.text((34,20),'仙铸 · Minecraft 方块防具十五式',font=title,fill=(229,221,196));d.text((37,81),'方块人比例 / 硬边立方体 / 像素纹样 / 五境三式',font=small,fill=(153,177,184))
for i,s in enumerate(DATA):
 im=Image.open(ROOT/'previews'/f'{s["id"]}-front.png').convert('RGB').crop((120,12,780,708))
 im.resize((330,348),Image.Resampling.LANCZOS).save(ROOT/'previews'/f'{s["id"]}.png')
 x=24+(i%3)*394;y=135+(i//3)*490;sheet.paste(im.resize((370,390),Image.Resampling.LANCZOS),(x,y))
 d.text((x+6,y+398),f'{i+1:02d}  {s["name"]}  ·  {REALMS[s["level"]-1]}',font=f,fill=(231,217,180));d.text((x+6,y+440),s['material']+'锭',font=small,fill=(160,180,188))
sheet.save(ROOT/'previews/overview.jpg',quality=93)
a=Image.new('RGB',(1140,401))
for j,i in enumerate([2,8,13]):a.paste(Image.open(ROOT/'previews'/f'{DATA[i]["id"]}-front.png').crop((120,12,780,708)).resize((380,401)),(j*380,0))
a.save(ROOT/'previews/three-samples.jpg',quality=94)
