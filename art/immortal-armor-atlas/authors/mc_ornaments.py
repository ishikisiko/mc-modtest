"""Authored garment-specific embroidery, inlay and borders; no random texture noise."""
from mc_common import Builder,material,color_shift
from mc_taixu import refine as refine_taixu
from mc_zixiao import refine as refine_zixiao
from mc_jiuse import refine as refine_jiuse

class Pixels:
    def __init__(self,size=16):self.size=size;self.a=[['0']*size for _ in range(size)]
    def dot(self,x,y,c):
        if 0<=x<self.size and 0<=y<self.size:self.a[y][x]=str(c)
    def rect(self,x,y,w,h,c):
        for yy in range(y,y+h):
            for xx in range(x,x+w):self.dot(xx,yy,c)
    def line(self,points,c=2):
        for (x0,y0),(x1,y1) in zip(points,points[1:]):
            dx=abs(x1-x0);sx=1 if x0<x1 else -1;dy=-abs(y1-y0);sy=1 if y0<y1 else -1;err=dx+dy
            while True:
                self.dot(x0,y0,c)
                if (x0,y0)==(x1,y1):break
                e=2*err
                if e>=dy:err+=dy;x0+=sx
                if e<=dx:err+=dx;y0+=sy
    def diamond(self,cx,cy,rx,ry,c):
        for y in range(cy-ry,cy+ry+1):
            for x in range(cx-rx,cx+rx+1):
                if abs(x-cx)*ry+abs(y-cy)*rx<=rx*ry:self.dot(x,y,c)
    def frame(self):
        for x,y,dx,dy in [(1,1,1,1),(14,1,-1,1),(1,14,1,-1),(14,14,-1,-1)]:
            self.line([(x+3*dx,y),(x,y),(x,y+3*dy)],2);self.dot(x+dx,y+dy,4)
    def rows(self):return [''.join(r)for r in self.a]

def pattern(kind):
    p=Pixels()
    if kind=='star':
        p.diamond(8,8,5,5,1);p.diamond(8,8,4,1,2);p.diamond(8,8,1,4,2)
        p.diamond(8,8,2,2,3);p.dot(8,8,4)
        for x,y in [(3,3),(12,3),(3,12),(12,12)]:p.diamond(x,y,1,1,2);p.dot(x,y,3)
        for x,y in [(5,1),(1,8),(14,7),(8,14)]:p.dot(x,y,4)
    elif kind in ('cloud','rain'):
        p.line([(2,8),(2,5),(3,4),(6,4),(7,5),(7,7),(4,7),(4,6),(5,6)],2)
        p.line([(8,8),(8,4),(10,2),(12,3),(13,5),(12,7),(10,7),(10,5),(11,5)],3)
        p.rect(3,8,10,2,2);p.line([(2,11),(5,11),(6,10),(11,10),(13,11)],3)
        if kind=='rain':
            for x,y in [(4,13),(8,12),(12,13)]:p.diamond(x,y,0,1,4)
        else:p.line([(3,13),(5,13),(6,12),(10,12),(11,13),(13,13)],2)
    elif kind in ('sun','wing'):
        p.diamond(8,5,3,3,2);p.diamond(8,5,2,2,3);p.dot(8,5,4)
        for side in [-1,1]:
            for k in range(3):
                p.line([(8+side*2,8+k),(8+side*(5-k),7+k),(8+side*(7-k),5+k)],2)
                p.line([(8+side*2,9+k),(8+side*(5-k),8+k)],3)
        p.line([(5,13),(8,11),(11,13)],2);p.dot(8,14,4)
    elif kind=='moon':
        for y in range(1,11):
            for x in range(2,13):
                if (x-7)**2+(y-6)**2<=23 and (x-9)**2+(y-4)**2>15:p.dot(x,y,3 if x<6 else 2)
        p.diamond(12,4,1,1,4);p.dot(3,2,4);p.line([(2,12),(5,11),(8,12),(11,11),(14,12)],2)
        p.line([(2,14),(5,13),(8,14),(11,13),(14,14)],3)
    elif kind=='thunder':
        p.frame();p.line([(5,3),(11,3),(8,7),(11,7),(5,13),(7,9),(4,9),(7,5),(5,5)],2)
        p.line([(8,4),(6,7),(8,8),(6,11)],3);p.dot(3,7,4);p.dot(13,9,4)
    elif kind=='seal':
        p.frame();p.rect(4,4,8,8,1)
        for x,y in [(4,4),(9,4),(4,9),(9,9)]:p.rect(x,y,3,3,2);p.dot(x+1,y+1,0)
        p.rect(7,2,2,3,3);p.rect(7,11,2,3,3);p.rect(2,7,3,2,3);p.rect(11,7,3,2,3);p.rect(7,7,2,2,4)
    elif kind=='leaf':
        p.line([(8,14),(8,3)],2)
        for y in [5,8,11]:
            for side in [-1,1]:
                p.diamond(8+side*3,y,3,2,2);p.line([(8,y+1),(8+side*4,y-1)],3);p.dot(8+side*3,y,4)
        p.diamond(8,2,1,2,3)
    elif kind=='lotus':
        p.diamond(8,6,2,5,2);p.diamond(8,5,1,3,3)
        for side in [-1,1]:
            p.diamond(8+side*3,8,3,3,2);p.line([(8+side*5,6),(8+side*3,9),(8,11)],3)
            p.diamond(8+side*5,10,2,2,2)
        p.line([(2,12),(4,13),(11,13),(14,12)],3);p.rect(5,14,6,1,2);p.rect(7,9,2,2,4)
    elif kind=='glass':
        p.frame()
        for y in range(3,13):
            for x in range(3,13):
                if abs(x-7.5)+abs(y-7.5)<=6:
                    c=2 if x+y<15 else 4
                    if x>y:c=3
                    p.dot(x,y,c)
        p.line([(3,8),(8,3),(12,8),(8,12),(3,8)],2);p.line([(8,3),(8,12)],3);p.rect(7,7,2,2,4)
    elif kind=='border':
        p.rect(0,2,16,1,2);p.rect(0,13,16,1,2)
        for x in [0,8]:
            p.line([(x,9),(x,5),(x+5,5),(x+5,10),(x+2,10),(x+2,7),(x+3,7)],3)
            p.dot(x+7,7,4);p.dot(x+7,10,2)
    else:raise ValueError(kind)
    return p.rows()

def motif(s,key,kind,bg,ink,light,accent,name):
    m=material(name,bg,'cloth');m['pixelArt']={'kind':'cloth','mapping':'fit','palette':[bg,color_shift(bg,.68),ink,light,accent],'rows':pattern(kind)}
    s['materials'][key]=m;return key

def patch(b,id,mat,x,y,z,w,h,role='chest'):
    return b.box('orn_'+id,role,mat,[x,y,z],[w,h,.06],'按衣面定幅的'+b.s['materials'][mat]['name'])

def remove(s,*prefixes):s['components'][:]=[c for c in s['components']if not c['id'].startswith(prefixes)]

def crystal_lattice(back=False):
    p=Pixels(32)
    # Fine cloisonne frame with corner beads, large split facets and a contrasting heart.
    p.line([(5,2),(26,2),(29,5),(29,26),(26,29),(5,29),(2,26),(2,5),(5,2)],1)
    p.line([(6,4),(25,4),(27,6),(27,25),(25,27),(6,27),(4,25),(4,6),(6,4)],2)
    for y in range(6,26):
        for x in range(6,26):
            if abs(x-15.5)+abs(y-15.5)<=12:
                c=2 if x+y<31 else 4
                if x>y:c=3 if x+y<31 else 7
                p.dot(x,y,c)
    p.line([(16,5),(27,16),(16,27),(5,16),(16,5)],1)
    p.line([(16,5),(13,15),(16,27)],0);p.line([(5,16),(13,15),(27,16)],0)
    p.diamond(16,16,4,5,1);p.diamond(16,16,3,4,8);p.diamond(16,15,1,2,5)
    for x,y,c in [(7,7,6),(24,7,4),(7,24,7),(24,24,6)]:
        p.diamond(x,y,2,2,1);p.diamond(x,y,1,1,c)
    for x,y in [(16,2),(29,16),(16,29),(2,16)]:p.dot(x,y,9)
    if back:
        # Slender etched facets replace the broad cross in the first preview.
        p.line([(9,8),(13,15),(9,23)],9);p.line([(23,8),(19,17),(23,24)],9)
        for y in [7,11,20,24]:p.dot(3,y,6);p.dot(28,y,4)
    return p.rows()

def star_cape_weave():
    p=Pixels(32)
    # One large eight-point star, small companion stars and separate lower embroidery.
    p.diamond(16,13,7,2,2);p.diamond(16,13,2,7,2)
    p.diamond(16,13,4,4,3);p.diamond(16,13,2,2,4)
    for x,y,r in [(6,6,2),(25,8,1),(8,19,1),(25,21,2),(17,3,1)]:
        p.diamond(x,y,r,0,2);p.diamond(x,y,0,r,2);p.dot(x,y,3)
    for x,y in [(3,12),(11,3),(22,4),(28,15),(3,22),(19,21)]:p.dot(x,y,4)
    for x in range(2,30):p.dot(x,25,2);p.dot(x,30,2)
    for x in [3,12,21]:p.line([(x,28),(x,26),(x+6,26),(x+6,29),(x+2,29),(x+2,28),(x+4,28)],3)
    return p.rows()

def refine_star(s):
    remove(s,'star_sash_','star_back_map_','star_rear_hem_mark_')
    b=Builder(s);m=s['materials']
    motif(s,'orn_star_chart','star',m['night']['color'],m['silver']['color'],m['star']['color'],'#91b9cf','主星辅星与星点群的天文绣')
    motif(s,'orn_star_border','border',m['blue']['color'],m['silver']['color'],'#718fb5',m['star']['color'],'双层回纹星锦织边')
    m['star_mantle']=material('蓝金色密织披衣','#294365','cloth',accent='#8eaac8')
    motif(s,'orn_star_cape','star','#294365',m['silver']['color'],m['star']['color'],'#91b9cf','定幅星图与下缘回纹的整幅星锦')
    m['orn_star_cape']['pixelArt']['rows']=star_cape_weave()
    b.panel('star_shoulder_mantle','chest','star_mantle',[(-5,24.3),(5,24.3),(7.4,22.5),(6.3,20),(3.6,20.7),(0,21.5),(-3.6,20.7),(-6.3,20),(-7.4,22.5)],pos=(0,0,-4.3),depth=.3,name='贴肩展开的完整星锦短披肩')
    b.panel('star_continuous_cape','chest','orn_star_cape',[(-5.1,23.4),(5.1,23.4),(7.2,19.5),(7.4,12.5),(5.4,5.2),(1,4),(-2,5.6),(-6.3,3.2),(-7.3,12.5),(-6.7,19.5)],pos=(0,0,-3.9),depth=.35,name='从肩背连贯垂落的非对称整幅星锦披衣')
    for sign in [-1,1]:
        b.panel(f'star_cape_fold_{sign}','chest','star_mantle',[(sign*5.1,23.4),(sign*6.5,19.5),(sign*7.2,12.5),(sign*5.9,6.5),(sign*5.5,12.5),(sign*5.4,19.5)],pos=(0,0,-4.12),depth=.1,name='贴合整幅披衣的宽折侧面')
    patch(b,'star_front','orn_star_chart',1.55,18.7,2.49,4.3,6.3)
    for c in s['components']:
        if c['id'].startswith('star_cuff_'):c['material']='orn_star_border'
    m.pop('veil',None)
    s['style']='星锦披肩、连续长披衣、天文刺绣'
    s['tagline']='星图落在衣上，披衣沿肩背展开；长短折面与暗蓝留白共同形成天衣。'
    s['description']='星河天衣保留标准方头宽臂比例、深蓝交领和长襟，背部改为贴肩短披肩与一件连贯的宽幅星锦披衣。整幅披衣从肩胛向下垂落，腰侧略收，左下缘比右侧更长；侧面宽折贴合主披衣，轮廓完整，身体两旁没有游离长条或支撑横杆。背上的天文绣直接织在这件披衣中，主星、辅星和散点分出不同尺度，最下缘另有回纹收边。前胸的小星绣让出交领的主要斜线，袖口只留低位织锦。人物转身时先读到完整衣料，再读到有主次的星图，细节属于衣服本身。'
    s['silhouette']='一件完整披衣从肩背垂落，外缘带宽折与左长右短的下摆，肩外横杆和两根垂直条状构件完全移除。披衣宽度略过躯干而仍收在双臂附近，方斗冠、完整袖和前襟维持MC修士的穿戴比例。'
    s['parts']['chest']['description']='完整窄袖交领承衣配贴肩短披肩和连续宽披衣。披衣主面直接织入大主星、数颗不同尺度的辅星、散点及下缘回纹，侧面只有贴合主面的宽折。前胸和袖口使用更小的星绣与织边，背部没有独立长条、支架或杆件。'
    s['parts']['legs']['description']='独立方裤腿连接两幅外展前襟与完整夜穹后裳，保留步行开口和真实厚度。下装以完整暗蓝衣面与银色裁边衬托胸件长披衣，下摆与披衣的回纹绣边形成高低错落，不再增加单独的蛇形长线。'
    s['materialStory']='夜穹玄绡负责贴身衣料与前襟，沉星蓝金负责方冠和履壳，新增密织星锦负责整幅披衣。星锦使用暗蓝底、哑银主纹、浅青辅星和少量凝星色高光；32像素定幅星图与16像素袖口织边按不同部位排布。前胸、后背与下摆各有尺度，保留大面积暗蓝留白。锭本体及其原有折阶星河、方髓与引用材质保持不变，穿戴纹样的细化没有改写灵材锭。'

def refine_glass(s):
    return refine_jiuse(s)

# Each field has its own subject, placement and scale; these are not interchangeable stamps.
FIELDS={
 'qi_moqing':('cloud','silk','silver','lining','jade',(0,18.8,-2.76,5.8,6.8),('back_cloud_',),'双卷云与分层云脚'),
 'qi_tingyu':('rain','silk','edge','lining','plum',(0,18.8,-2.74,5.8,6.8),('back_window_',),'云盖、雨珠与水纹'),
 'qi_chixia':('sun','silk','gold','lining','jade',(0,18.6,-3.27,6.2,7.4),('back_dawn_',),'霞日、羽光与下承霞脚'),
 'zhuji_baiqing_lingyun':('cloud','white','teal','silver','gold',(0,18.7,-2.6,6.0,7.2),('cloud_back_glyph_',),'成对云头与青银回边'),
 'zhuji_yuheng_yizhuang':('seal','green','gold','ivory','red',(0,18.5,-2.96,5.8,7.0),('yuheng_back_seal_',),'四向玉衡印与朱心'),
 'zhuji_wuyao_yeque':('seal','obsidian','silver','bronze','jade',(0,18.5,-2.7,5.8,7.2),('night_back_glyph_',),'阙门角印与内嵌紫玉'),
 'jindan_jinwu':('wing','red_silk','sun_gold','pale_gold','amber',(0,18.2,-3.16,7,8),('rear_folded_wing_',),'日心与三列金乌羽纹'),
 'jindan_taiyin':('moon','night','silver','frost','ice',(0,18,-2.9,5.8,7.0),('moon_rear_seal',),'缺月、辅星与双层霜云'),
 'jindan_zixiao':('thunder','purple','bronze','glass','blue',(0,18.5,-3.16,5.8,8),('rear_thunder_seal_',),'有角印边界的雷篆'),
 'huashen_taixu':('cloud','robe','mist','silver','violet',(0,18.6,-2.55,6.0,7.2),('back_window_',),'淡青双卷云与紫色云心'),
 'huashen_xuanji':('seal','armor','gold','seal','red',(0,18.0,-3.03,6.0,7.5),('back_court_mark_',),'四向御宸铭与帝朱中心'),
 'huashen_dari':('lotus','red','gold','ivory','amber',(0,19.7,-3.18,5.2,6.4),('sun_back_mark_','sun_back_void'),'日心莲瓣与双层承莲'),
}

def decorate(s):
    if s['id']=='yuanying_xinghe':refine_star(s)
    if s['id']=='yuanying_jiuse':return refine_glass(s)
    if s['id'] in FIELDS:
        kind,bg,ink,light,accent,pos,prefixes,label=FIELDS[s['id']];remove(s,*prefixes)
        if s['id']=='qi_tingyu':s['components'][:]=[c for c in s['components']if c['id']!='back_rain']
        if s['id']=='zhuji_yuheng_yizhuang':s['components'][:]=[c for c in s['components']if c['id']!='yuheng_back_axis']
        if s['id']=='qi_chixia':
            cloth=next(c for c in s['components']if c['id']=='whole_back_mantle')
            x,y,z,w,h=pos;pos=(x,y,(cloth['position'][2]-cloth['depth']/2)*16-.06,w,h)
        colors=[s['materials'][k]['color']for k in (bg,ink,light,accent)];m=motif(s,'orn_back',kind,*colors,label)
        patch(Builder(s),'back',m,*pos)
        chest=s['parts']['chest']['description'].split('。')[0]
        s['parts']['chest']['description']=chest+'。背面采用'+label+'组成的完整织纹，主纹、角纹和辅纹分别占据清楚的位置，细节按衣面排布，不再用孤立的长折线代替设计。'
        sentences=s['description'].split('。')
        s['description']='。'.join(t for t in sentences if not (any(k in t for k in ['背上','背面','背部','后背','后肩']) and any(k in t for k in ['纹','印'])))
        s['description']+='。背面以'+label+'组织完整织纹，远看有清晰主形，近看能看到内外边、色阶与辅纹。'
    if s['id']=='jindan_zixiao':
        remove(s,'front_thunder_seal_');patch(Builder(s),'chest_seal','orn_back',.6,20.2,3.2,3.2,5)
    if s['id']=='huashen_xuanji':
        remove(s,'imperial_seal_path_','imperial_seal');patch(Builder(s),'chest_seal','orn_back',0,18.7,3.4,5.5,7.3)
    if s['id']=='yuanying_qingdi':
        remove(s,'wood_front_branch_');m=motif(s,'orn_leaf','leaf',s['materials']['jade']['color'],s['materials']['bronze']['color'],s['materials']['leaf']['color'],s['materials']['dew']['color'],'分叶、主脉与叶心玉绣')
        patch(Builder(s),'leaf',m,0,17.6,3.17,5.3,7.4)
        s['parts']['chest']['description']+='胸面主脉改成有对生叶片、叶内留白和浅色叶心的成组玉绣，保留肩叶搭接的实体层次。'
    # Borders follow existing cuff/belt surfaces; never stretch a winding line over the garment.
    for c in s['components']:
        size=c.get('size',[])
        if c['shape']!='box' or len(size)!=3 or c['material'].startswith('orn_'):continue
        if not any(word in c['id']for word in ('cuff','belt','waist','obi')) or size[0]<2*size[1] or size[1]>2/16:continue
        original=c['material'];key='orn_border_'+original
        if key not in s['materials']:
            base=s['materials'][original];bg=base['color'];ink=color_shift(bg,.52);light=color_shift(bg,1.35)
            motif(s,key,'border',bg,ink,light,s['palette'][-1],base['name']+'的双层绣边')
            s['materials'][key]['pixelArt']['kind']=base['pixelArt'].get('kind','cloth')
        c['material']=key
    s['materialStory']+='已有袖口与腰封按各自面宽绘入双层回纹织边，细节属于衣料收口和金属錾边，主纹与边纹保持不同尺度。'
    return refine_zixiao(refine_taixu(s))
