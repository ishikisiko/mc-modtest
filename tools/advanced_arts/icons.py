"""Authored vector shapes for six small native HUD sprites; no external image inputs."""
from io import BytesIO
from PIL import Image, ImageDraw

ICONS = {'qinglian_jiange':'lotus', 'huifeng_jianzhen':'wind', 'canglong_chushui':'dragon',
         'hengsao_qianjun':'sweep', 'bengshan_jin':'mountain', 'jingang_zhen':'ward'}

def icon(kind):
    im=Image.new('RGBA',(64,64));d=ImageDraw.Draw(im)
    jade='#b5ffe0';blue='#87dce9';gold='#ffdc89';white='#effff7';shadow='#396d68'
    def line(points,color,width=3):d.line(points,fill=color,width=width,joint='curve')
    def blade():
        d.polygon([(17,49),(22,49),(47,17),(48,7),(39,13)],fill=shadow)
        d.polygon([(21,47),(25,45),(47,10),(41,14)],fill=white)
        line([(16,40),(29,50)],gold,4);line([(21,47),(13,58)],'#c6a36c',5)
    if kind=='lotus':
        for pts in [[(28,36),(21,18),(30,23),(34,34)],[(27,37),(8,27),(11,39),(26,45)],[(34,36),(40,23),(48,23),(41,41)]]:
            d.polygon(pts,fill='#55bb99')
        d.polygon([(16,35),(19,48),(31,52),(44,44),(47,34),(32,44)],fill=jade)
        blade()
    elif kind=='wind':
        d.arc((5,8,56,53),175,335,fill=jade,width=5);d.arc((11,3,61,46),0,140,fill='#629e9d',width=4)
        d.polygon([(5,30),(3,44),(17,39)],fill=jade)
        blade()
    elif kind=='dragon':
        line([(9,51),(21,39),(15,29),(27,20),(43,20),(48,11)],'#399ec4',7)
        line([(12,53),(25,40),(20,30),(31,25),(47,25),(56,14)],blue,3)
        line([(22,56),(45,17)],gold,4)
        d.polygon([(39,21),(49,6),(50,22),(44,28)],fill=white)
        d.polygon([(9,25),(11,18),(18,25)],fill=blue)
    elif kind=='sweep':
        d.arc((5,12,58,56),190,355,fill=blue,width=5);d.arc((9,20,61,62),185,335,fill='#3c839c',width=3)
        line([(9,50),(46,18)],gold,4)
        d.polygon([(39,19),(57,8),(49,27),(43,25)],fill=white)
        d.polygon([(49,44),(59,40),(56,53)],fill=blue)
    elif kind=='mountain':
        d.polygon([(6,54),(20,31),(29,43),(38,30),(59,55)],fill='#77634c')
        line([(9,55),(21,37),(29,48),(39,37),(55,55)],gold,3)
        d.polygon([(19,10),(26,7),(32,9),(38,7),(45,10),(47,24),(40,34),(25,33),(18,24)],fill='#dca65e')
        line([(23,12),(23,22),(42,22),(42,13)],white,3)
        line([(28,11),(28,18)],gold,2);line([(35,11),(35,18)],gold,2)
        line([(32,37),(29,44),(36,47),(32,59)],white,3)
    elif kind=='ward':
        d.ellipse((4,4,59,59),outline=gold,width=4);d.ellipse((9,9,54,54),outline='#86764b',width=2)
        d.polygon([(17,24),(22,18),(29,20),(30,14),(36,14),(38,20),(44,19),(48,26),(45,39),(36,48),(25,43)],fill='#e8c684')
        line([(24,23),(24,32),(41,32),(41,24)],white,3)
        line([(29,22),(29,28)],'#8c6441',2);line([(35,21),(35,28)],'#8c6441',2)
    else:raise ValueError(kind)
    im=im.resize((32,32),Image.Resampling.LANCZOS)
    out=BytesIO();im.save(out,format='PNG');return out.getvalue()
