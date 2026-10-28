"""Fifteen silhouette constructions. Each consists of coherent garment/armor shells.
Local petals/feathers are deliberately limited; no tessellated plate clothing.
"""
import math
import numpy as np
from geometry import Builder,TAU
from catalog import material_set


def pair(points, side):return [[p[0]*side,p[1],p[2]] for p in points]

def crest(g, motif, x,y,z,scale=1,slot='chest',mat='trim'):
    def line(name,pts,r=.003):g.tube(name,slot,mat,[[x+u*scale,y+v*scale,z+w*scale] for u,v,w in pts],r*scale,7,5)
    if motif in ['wave','moon']:
        for j in range(2 if motif=='wave' else 1):
            pts=[]
            for a in np.linspace(-1.0,2.5,15):pts.append([.051*math.cos(a),.052*math.sin(a)-j*.021,.004])
            line('连续潮月浅嵌',pts,.0026)
    elif motif in ['flame','phoenix']:
        line('上升火髓浅嵌',[[0,-.06,0],[-.035,-.02,.003],[-.022,.02,.003],[.012,.06,0],[.006,.01,.006],[.03,-.018,.003],[0,-.06,0]])
    elif motif in ['leaf','lotus']:
        for s in [-1,1]:line('玉叶莲脉浅嵌',[[0,-.05,0],[s*.045,-.005,.004],[s*.035,.04,.004],[0,.065,0]])
        line('中心生脉',[[0,-.05,.006],[0,.06,.006]],.0025)
    elif motif=='thunder':
        g.tube('连续折线雷脊',slot,mat,[[x+u*scale,y+v*scale,z] for u,v in [[.015,.07],[-.025,.018],[.02,.016],[-.012,-.065]]],.003*scale,7,1)
    elif motif=='spire':
        for si in [-1,1]:
            line('阙纹侧框',[[si*.04,-.055,0],[si*.04,.028,0],[0,.075,0]],.0025)
        line('阙心中轴',[[0,-.045,.001],[0,.055,.001]],.002)
    elif motif in ['sun','origin']:
        g.disc('完整混元环纹',slot,mat,[x,y,z],.045*scale,.045*scale,tube=.003*scale)
        if motif=='sun':
            for a in np.linspace(0,TAU,9)[:-1]:line('短日芒',[[.052*math.cos(a),.052*math.sin(a),0],[.063*math.cos(a),.063*math.sin(a),0]],.002)
    elif motif in ['star','void']:
        line('空心星阙道印',[[0,.072,0],[.036,0,.004],[0,-.072,0],[-.036,0,.004],[0,.072,0]],.003)
        if motif=='star':line('横星轨',[[-.065,0,0],[.065,0,0]],.002)
    elif motif=='seal':
        line('方渊回纹',[[-.042,-.05,0],[-.042,.05,0],[.042,.05,0],[.042,-.05,0],[-.042,-.05,0]],.0035)
        line('渊心刻线',[[0,-.032,.003],[0,.032,.003]])


def mannequin(g):
    g.loft('陶瓷人台 · 颈','body','skin',[(1.44,.052,.047,0,0),(1.55,.055,.049,0,0)],segments=24)
    g.loft('陶瓷人台 · 面部','body','skin',[(1.525,.039,.039,0,.029),(1.55,.064,.066,0,.014),(1.62,.084,.082,0,.004),(1.70,.097,.092,0,0),(1.77,.092,.09,0,-.006),(1.82,.05,.06,0,-.01),(1.834,.009,.019,0,-.01)],segments=40,thickness=.008)
    g.ellipsoid('发束 · 后脑','body','hair',[0,1.727,-.056],[.097,.116,.067],seg=32)
    g.ellipsoid('发束 · 后髻','body','hair',[0,1.855,-.022],[.049,.052,.048],seg=24)
    for s in [-1,1]:
        g.ellipsoid('陶瓷人台 · 手','body','skin',[s*.407,.805,.007],[.039,.073,.027],seg=24,rows=14)
        g.ellipsoid('陶瓷人台 · 拇指','body','skin',[s*.376,.814,.024],[.016,.038,.017],seg=16,rows=10)


def sleeves(g,spec):
    w=spec['shape']['sleeve'];lvl=spec['level'];armor=spec['shape']['armor']
    if armor:w=.045
    for s in [-1,1]:
        # An unbroken sleeve from shoulder to wrist, with open sleeve edge.
        rings=[(.865,w,w*.84,s*.395,0),(.96,w*.9,w*.86,s*.363,0),(1.10,.057 if armor else .063,.058 if armor else .064,s*.329,0),(1.26,.077,.074,s*.281,-.002),(1.41,.080,.084,s*.232,-.004),(1.455,.06,.066,s*.206,-.005)]
        g.loft('完整左袖' if s<0 else '完整右袖','chest','cloth' if w>.10 else 'inner',rings,segments=36,fold=.002 if w<.1 else .006,folds=5)
        g.loft('单圈袖缘','chest','trim',[(.86,w+.004,w*.84+.004,s*.396,.0),(.88,w+.003,w*.84+.003,s*.390,0)],segments=36,thickness=.003)
        if armor or spec['id'].startswith(('03','10','14')):
            g.loft('连续弧形臂甲','chest','metal',[(.90,.052,.054,s*.386,.0),(1.01,.060,.066,s*.354,.001),(1.15,.067,.067,s*.316,.0)],segments=36,thickness=.007,power=2.35)
            g.tube('臂甲纵向嵌线','chest','trim',pair([[.386,.918,.057],[.352,1.015,.071],[.316,1.14,.07]],s),.0035,7)
        else:
            # A single long cuff seam, not stripes across the sleeve.
            g.tube('袖面长弧纹','chest','trim',pair([[.412,.879,w*.84+.004],[.379,.97,w*.78+.005],[.329,1.1,.068]],s),.0025,6)


def boots_and_trousers(g,spec):
    arm=spec['shape']['armor'];lvl=spec['level']
    for s in [-1,1]:
        bh=.43 if arm else .29
        g.loft('完整裤腿','legs','inner',[(.12,.041,.042,s*.101,0),(.24,.045,.044,s*.103,0),(bh+.016,.055,.057,s*.105,0),(.56,.072,.074,s*.100,0),(.77,.092,.095,s*.092,0),(.99,.092,.101,s*.085,0)],segments=32,fold=.0015)
        g.loft('一体靴筒','boots','metal' if arm else 'dark',[(.09,.06,.069,s*.101,.018),(.20,.056,.06,s*.101,.003),(bh-.04,.071,.076,s*.105,.0),(bh,.072,.078,s*.105,0)],segments=32,thickness=.007,power=2.4)
        g.loft('完整鞋面','boots','metal' if arm else 'inner',[(.022,.066,.143,s*.101,.056),(.052,.07,.14,s*.101,.062),(.088,.065,.12,s*.101,.058),(.123,.055,.077,s*.101,.026)],segments=36,thickness=.008,power=2.5)
        g.loft('玄色靴底','boots','dark',[(.007,.067,.145,s*.101,.057),(.024,.067,.145,s*.101,.057)],segments=36,thickness=.004,power=2.5)
        g.loft('靴口单嵌边','boots','trim',[(bh-.009,.075,.082,s*.105,0),(bh+.005,.075,.082,s*.105,0)],segments=32,thickness=.002)
        g.tube('脚背至胫骨的连续嵌线','boots','trim',pair([[.101,.066,.196],[.101,.113,.111],[.101,.21,.067],[.105,bh,.084]],s),.0035,7)
        if spec['id'].startswith(('06','12')):
            g.ribbon('局部护膝 · 不扩展为腿甲片阵列','legs','metal',pair([[.105,.60,.074],[.105,.55,.090],[.105,.49,.078]],s),[.075,.10,.045],thickness=.008,bulge=.012)


def garments(g,spec):
    p=spec['shape'];level=spec['level'];arm=p['armor'];bottom=p['robeY'];r=p['robeR'];mode=p['cut']
    rings=[(.985,.15,.108,0,0),(1.07,.143,.108,0,0),(1.20,.172,.125,0,0),(1.34,.212,.137,0,0),(1.425,.214,.116,0,-.004),(1.48,.078,.072,0,-.004)]
    g.loft('完整主身 · 不由碎片拼成','chest','metal' if arm else 'cloth',rings,segments=48,thickness=.008,power=2.3 if arm else 2.0,fold=0 if arm else .0015)
    # High collar and one coherent front plate/overlap.
    g.loft('完整贴颈立领','chest','inner',[(1.428,.084,.077,0,-.002),(1.51,.069,.067,0,-.002)],segments=40,thickness=.005,arc=(.2,TAU-.2))
    if not arm and not spec['id'].startswith(('05','10','13','14')):
        g.ribbon('整幅护心襟 · 单一连续曲面','chest','metal',[[0,1.43,.125],[0,1.35,.148],[0,1.22,.132],[0,1.07,.116]],[.11,.19 if level<3 else .155,.14 if level<3 else .105,.095],thickness=.005,bulge=.006)
    if spec['id'].startswith(('01','04','05','07','08','10','14')):
        g.ribbon('完整交领','chest','inner',[[-.070,1.49,.078],[-.134,1.40,.118],[-.045,1.29,.154],[.071,1.135,.123]],[.027,.038,.032,.018],thickness=.004)
        g.tube('交领细金边','chest','trim',[[-.073,1.495,.082],[-.151,1.405,.118],[-.061,1.29,.160],[.061,1.132,.129]],.003,7)
    else:
        for s in [-1,1]:
            g.tube('胸壳连续轮廓嵌线','chest','trim',pair([[.067,1.48,.073],[.169,1.405,.112],[.145,1.30,.123],[.105,1.16,.11],[.075,1.056,.1]],s),.004,7)
    g.loft('整条腰封','chest','dark',[(.982,.155,.116,0,0),(1.049,.154,.116,0,0)],segments=48,thickness=.006,power=2.2)
    g.loft('腰封单圈金属边','chest','trim',[(1.037,.159,.121,0,0),(1.052,.159,.121,0,0)],segments=48,thickness=.003,power=2.2)
    g.gem('腰心灵髓','chest','gem',[0,1.01,.129],[.025,.036,.012])
    # One continuous wraparound outer skirt with an intentional front opening.
    gap=.40 if mode in ['robe','wide'] else .59
    lo=[(bottom,r,r*.59,0,-.018),(bottom+(1-bottom)*.32,r*.88,r*.53,0,-.006),(.84,.207,.144,0,0),(1.016,.145,.105,0,0)]
    # Ensure monotonic height even in shorter skirts.
    lo=sorted(lo,key=lambda t:t[0])
    g.loft('完整侧后外裳','legs','cloth',lo,segments=56,arc=(gap,TAU-gap),thickness=.005,fold=.007 if not arm else .003,folds=8,hem=.06 if mode in ['flow','asym'] else .015,steps=5)
    # A single broad front skirt panel. No strips or repeated lamellae.
    front_x=-.035 if mode=='asym' else 0
    fl=bottom+.11 if mode not in ['battle','asym'] else bottom+.035
    g.ribbon('整幅前裙','legs','inner' if not arm else 'cloth',[[front_x,1.015,.114],[front_x,.86,.157],[front_x,(fl+.86)/2,.171],[front_x,fl,.16]],[.24,.245,.26 if mode=='wide' else .20,.22 if mode=='wide' else .15],thickness=.006,bulge=.01)
    # Connected hem line, follows the actual shell ring instead of painted false plates.
    pts=[]
    for a in np.linspace(gap,TAU-gap,60):
        rr=r+.007*math.cos(8*a) if not arm else r+.003*math.cos(8*a)
        pts.append([rr*math.sin(a),bottom-.015*(.5-.5*math.cos(a)),(r*.59+.004)*math.cos(a)-.018])
    g.tube('外裳一圈完整裙缘','legs','trim',pts,.0032,6,1)
    for s in [-1,1]:
        # Long, sparse embroidery, not mesh segmentation.
        g.tube('裙面纵向纹样','legs','trim',pair([[.055,1.0,.145],[.064,.85,.177],[.069,(fl+.85)/2,.186],[.053,fl+.025,.173]],s),.0024,6)
    crest(g,spec['motif'],0,1.323,.145 if spec['id'].startswith(('05','10','13','14')) else .167,scale=.74 if not arm else 1.0)
    g.gem('胸心灵髓','chest','gem',[0,1.314,.158 if spec['id'].startswith(('05','10','13','14')) else .181],[.018,.032,.012])


def shoulders(g,spec):
    extra=spec['shape']['shoulder'];lvl=spec['level'];arm=spec['shape']['armor'];sid=spec['id']
    for s in [-1,1]:
        if sid.startswith(('05','13')):continue
        # One complete shell on each shoulder, never a stack of flakes.
        g.loft('左整肩壳' if s<0 else '右整肩壳','chest','metal',[(1.335,.058,.069,s*.269,-.012),(1.40,.082,.091,s*.247,-.014),(1.463,.068,.082,s*.223,-.016),(1.49,.021,.044,s*.204,-.022)],segments=36,thickness=.007,power=2.65)
        if sid.startswith(('01','03','10')):continue
        if sid.startswith('08'):continue
        if sid.startswith(('11','12')):continue
        a=.30+extra
        g.ribbon('单体上扬肩翼','chest','metal',pair([[.18,1.475,.015],[.275,1.50,-.008],[a,1.54+extra*.37,-.085],[a-.012,1.47,-.14]],s),[.055,.125,.070,.003],normal=(0,.55,1),thickness=.008,bulge=.005)
        g.tube('肩翼单道嵌边','chest','trim',pair([[.18,1.493,.044],[.28,1.535,.02],[a,1.57+extra*.37,-.075]],s),.0045,7)
        if sid.startswith(('04','05','06','09','14')):
            g.gem('局部肩端灵髓','chest','gem',[s*(a-.035),1.51+extra*.3,-.035],[.021,.047,.019])


def crown(g,spec):
    sid=spec['id'];height=spec['shape']['crown'];lvl=spec['level']
    # Unbroken diadem band around the head.
    g.loft('完整额冠基座','head','metal',[(1.755,.101,.096,0,-.003),(1.786,.099,.095,0,-.005)],segments=48,thickness=.004)
    g.tube('额冠下沿','head','trim',[[.099*math.sin(a),1.757,.095*math.cos(a)-.003] for a in np.linspace(-1.55,1.55,35)],.0035,7,1)
    if sid.startswith('15'):
        for s in [-1,1]:
            g.ribbon('无相双弧空冠','head','metal',pair([[.06,1.775,.085],[.095,1.89,.06],[.065,2.04,.01],[.012,2.11,-.012]],s),[.035,.041,.027,.002],thickness=.006)
            g.tube('空冠内沿','head','trim',pair([[.04,1.785,.104],[.068,1.90,.075],[.045,2.04,.02],[.009,2.102,.0]],s),.003,7)
    else:
        if sid.startswith('03'):
            g.ribbon('偏峰夜冠','head','metal',[[.043,1.77,.085],[.075,1.84,.065],[.093,1.92,.015]],[.045,.035,.002],thickness=.005)
        else:
            w=.045 if lvl<3 else .070
            g.ribbon('冠中一体主峰','head','metal',[[0,1.774,.101],[0,1.83,.095],[0,1.79+height,.025]],[w,w*.65,.002],thickness=.007,bulge=.006)
            for s in [-1,1]:
                g.ribbon('冠侧完整弧翼','head','trim',pair([[.032,1.77,.098],[.095,1.815,.065],[.12,1.82+height*.36,-.015],[.066,1.78+height*.55,-.054]],s),[.018,.032,.019,.003],thickness=.005)
        if lvl>=3:
            g.gem('冠心独立灵髓','head','gem',[0,1.817,.110],[.014,.039,.014])
        else:g.gem('额心灵髓','head','gem',[0,1.773,.103],[.012,.020,.008])
    if sid.startswith(('01','05')):
        g.tube('一根完整后发簪','head','trim',[[-.15,1.842,-.042],[0,1.846,-.025],[.15,1.842,-.042]],.005,8)
    if sid.startswith(('10','08')):
        for s in [-1,1]:
            g.tube('侧额垂链','head','trim',pair([[.096,1.78,.043],[.11,1.71,.032],[.108,1.648,.03]],s),.0017,6)
            g.gem('侧额月珠','head','gem',[s*.108,1.636,.03],[.008,.019,.008])


def mantle(g,name,mat,rx,rz,drop=.09):
    # A single annular shoulder drape, not a bent tube or a pile of pauldrons.
    seg=64;rows=8;v=[];f=[];uv=[];th=.006
    for side in range(2):
        for j in range(rows+1):
            t=j/rows
            for k in range(seg):
                a=TAU*k/seg;x=(.081+(rx-.081)*t)*math.sin(a);z=(.075+(rz-.075)*t)*math.cos(a)
                y=1.495-drop*t+(.065*math.sin(a)**2)*t-.012*math.sin(math.pi*t)
                v.append([x,y-side*th,z]);uv.append([k/seg,t])
    M=seg*(rows+1)
    for side in range(2):
        for j in range(rows):
            for k in range(seg):
                a=side*M+j*seg+k;b=side*M+j*seg+(k+1)%seg;c=b+seg;d=a+seg
                f.extend([[a,c,b],[a,d,c]] if side==0 else [[a,b,c],[a,c,d]])
    for j in [0,rows]:
        for k in range(seg):
            a=j*seg+k;b=j*seg+(k+1)%seg;f.extend([[a,b,a+M],[a+M,b,b+M]])
    g.add(name,'chest',mat,v,f,uv)
    edge=[]
    for a in np.linspace(0,TAU,65):edge.append([rx*math.sin(a),1.495-drop+.065*math.sin(a)**2+.003,rz*math.cos(a)])
    g.tube(name+' · 外缘细嵌','chest','trim',edge,.003,7,1)
    # Paired fine cloud lines flowing over the drape, not panel outlines.
    for si in [-1,1]:
        g.tube(name+' · 云肩浅纹','chest','trim',pair([[.10,1.462,.108],[.19,1.47,.112],[.27,1.473,.08],[.30,1.475,.015]],si),.0022,6)


def embroidery(g,spec):
    level=spec['level'];p=spec['shape'];bottom=p['robeY'];fl=bottom+.11 if p['cut'] not in ['battle','asym'] else bottom+.035
    x0=-.035 if p['cut']=='asym' else 0
    # One coherent motif at the skirt hem. Density rises by deliberate focal areas.
    if level>=2:
        y0=fl+.09
        for si in [-1,1]:
            points=[[x0+si*.015,y0-.025,.179],[x0+si*.055,y0,.187],[x0+si*.082,y0+.055,.19],[x0+si*.062,y0+.09,.19],[x0+si*.04,y0+.06,.19]]
            g.tube('整裙下缘云纹 · 浅嵌非分片','legs','trim',points,.0018,6,5)
        g.gem('整裙下缘小灵髓','legs','gem',[x0,y0+.024,.193],[.006,.015,.004])
    if level>=3:
        for si in [-1,1]:
            # Chest-level ornament follows one continuous trajectory on each side.
            for k in range(2):
                pts=pair([[.08,1.17+k*.034,.119],[.115,1.20+k*.034,.119],[.141,1.26+k*.028,.113],[.128,1.29+k*.027,.124]],si)
                g.tube('胸侧流云浅纹','chest','trim',pts,.0017,6,4)
            g.tube('腰侧单佩连线','chest','trim',pair([[.13,1.02,.085],[.17,.94,.11],[.17,.865,.12]],si),.0018,6,5)
            g.gem('腰侧一枚小佩','chest','gem',[si*.17,.848,.12],[.013,.031,.007])
    if level>=4:
        # A long curved embroidered margin gives high-tier cloth richness without flakes.
        for si in [-1,1]:
            pts=[]
            for t in np.linspace(0,1,30):
                yy=fl+.035+t*(.78-fl);xx=x0+si*(.083+.009*math.sin(t*math.pi*3));zz=.181+.009*math.sin(t*math.pi)
                pts.append([xx,yy,zz])
            g.tube('高阶整裙长绣边','legs','trim',pts,.0016,6,1)

def cape(g,name,mat,top=1.43,bottom=.18,width=.40,side=0):
    # One full cloak shell. Rotating its source around the torso introduces asymmetry.
    start=len(g.meshes)
    g.loft(name,'chest',mat,[(bottom,width,.25,0,-.11),(.57,width*.87,.22,0,-.09),(1.10,.25,.15,0,-.02),(top,.23,.14,0,-.018)],segments=40,arc=(1.58,4.70),fold=.010,folds=6,thickness=.004,hem=.035)
    if side:
        a=side*.48
        g.transform_last(start,lambda p:[p[0]*math.cos(a)+p[2]*math.sin(a),p[1],p[2]*math.cos(a)-p[0]*math.sin(a)])


def specifics(g,spec):
    sid=spec['id'];lv=spec['level']
    if sid.startswith('01'):
        g.ribbon('听澜 · 单幅斜肩绢','chest','inner',[[-.21,1.46,.08],[-.10,1.37,.15],[.05,1.19,.14],[.12,1.07,.09]],[.085,.064,.05,.04],thickness=.004)
    elif sid.startswith('02'):
        for s in [-1,1]:
            g.ribbon('赤霄 · 仅一枚短霞翎','chest','trim',pair([[.28,1.49,.035],[.37,1.54,-.04],[.40,1.58,-.10]],s),[.025,.040,.002],normal=(0,.5,1),thickness=.005)
        cape(g,'赤霄 · 完整短后绫','cloth',bottom=.65,width=.27)
    elif sid.startswith('03'):
        g.ribbon('照夜 · 一体偏肩披','chest','inner',[[.07,1.44,.11],[.26,1.46,.035],[.30,1.31,-.14],[.22,.99,-.22],[.12,.64,-.23]],[.12,.22,.26,.23,.17],normal=(0,.15,1),thickness=.006)
        g.tube('照夜 · 披肩银线','chest','trim',[[.05,1.46,.13],[.28,1.50,.035],[.37,1.32,-.15],[.31,1.00,-.235],[.20,.65,-.25]],.0038,7)
    elif sid.startswith('04'):
        g.disc('雪阙 · 初生半霜弧','aura','trim',[0,1.65,-.26],.24,.31,start=.15,end=math.pi-.15,tube=.005)
        cape(g,'雪阙 · 完整浅霜后裳','veil',bottom=.25,width=.30)
    elif sid.startswith('05'):
        mantle(g,'碧衡 · 整块玉护领','metal',.325,.163,drop=.09)
        for s in [-1,1]:g.disc('碧衡 · 近肩碧光弧','aura','glow',[s*.26,1.45,-.16],.16,.16,start=.2,end=2.5,tube=.003)
    elif sid.startswith('06'):
        g.disc('金乌 · 初生半日轮','aura','trim',[0,1.64,-.245],.285,.285,start=-.1,end=math.pi+.1,tube=.009)
        for a in np.linspace(.22,math.pi-.22,7):
            g.tube('金乌 · 日轮短芒','aura','trim',[[.29*math.cos(a),1.64+.29*math.sin(a),-.245],[.33*math.cos(a),1.64+.33*math.sin(a),-.245]],.004,7,2)
    elif sid.startswith('07'):
        g.ribbon('沧溟 · 单条完整绕身潮帛','chest','inner',[[-.16,1.45,-.09],[-.51,1.44,-.04],[-.63,1.12,.08],[-.52,.69,.13],[-.31,.32,.18],[.17,.22,.26],[.51,.44,.11]],[.12,.17,.14,.12,.10,.08,.002],normal=(0,0,1),thickness=.005,bulge=.008)
        g.disc('沧溟 · 不闭合潮环','aura','trim',[0,1.28,-.31],.57,.69,start=-1.3,end=2.4,tube=.009,tilt=.25)
        g.disc('沧溟 · 潮环内光','aura','glow',[0,1.28,-.309],.552,.672,start=-1.3,end=2.4,tube=.003,tilt=.25)
    elif sid.startswith('08'):
        # Four deliberately limited petals on a continuous collar, not a petal bodysuit.
        for s in [-1,1]:
            for k in range(2):
                g.ribbon('莲华 · 局部领瓣','chest','metal',pair([[.07+k*.10,1.40,.105],[.15+k*.10,1.50,.075],[.19+k*.115,1.59+k*.015,-.015]],s),[.12,.14,.002],thickness=.007,bulge=.025)
        g.disc('莲华 · 小莲轮','aura','trim',[0,1.68,-.26],.32,.38,start=0,end=TAU,tube=.007)
        cape(g,'莲华 · 完整暗紫后衬','inner',bottom=.12,width=.37)
    elif sid.startswith('09'):
        cape(g,'天律 · 整幅偏置短披风','inner',bottom=.61,width=.36,side=-1)
        pts=[[0,2.14,-.34],[-.18,1.70,-.34],[.06,1.69,-.34],[-.06,1.14,-.34]]
        g.ribbon('天律 · 竖向雷法印','aura','glow',pts,[.012,.022,.023,.002],thickness=.005)
        g.tube('天律 · 菱形背印','aura','trim',[[0,2.10,-.36],[.39,1.63,-.36],[0,1.10,-.36],[-.39,1.63,-.36],[0,2.10,-.36]],.007,8,1)
    elif sid.startswith('10'):
        g.disc('太阴 · 大弦月外缘','aura','metal',[.10,1.47,-.35],.59,.80,start=-1.6,end=1.6,tube=.026)
        g.disc('太阴 · 大弦月内光','aura','glow',[.10,1.47,-.345],.557,.765,start=-1.6,end=1.6,tube=.006)
        g.ribbon('太阴 · 一幅月绢','chest','inner',[[-.15,1.46,-.04],[-.43,1.40,-.02],[-.55,1.11,.0],[-.60,.65,.08],[-.44,.25,.15]],[.18,.23,.18,.13,.008],thickness=.005)
        cape(g,'太阴 · 完整月白内裳','inner',bottom=.10,width=.29)
    elif sid.startswith('11'):
        for s in [-1,1]:
            g.ribbon('朱凰 · 单体大凰翼壳','chest','metal',pair([[.18,1.43,.02],[.33,1.49,-.025],[.54,1.64,-.10],[.67,1.81,-.17]],s),[.14,.25,.21,.003],normal=(0,.3,1),thickness=.011,bulge=.012)
            for k in range(3):
                g.tube('朱凰 · 翼面浅浮雕羽脉','chest','trim',pair([[.25,1.47+k*.023,.08],[.38,1.55+k*.025,.04],[.51,1.65+k*.024,-.055],[.64,1.78,-.15]],s),.0032,6)
            g.ribbon('朱凰 · 整幅凰尾帛','chest','cloth',pair([[.12,1.04,-.14],[.25,.76,-.26],[.40,.42,-.30],[.37,.13,-.23],[.48,.23,-.20]],s),[.12,.18,.17,.10,.003],thickness=.005)
            g.tube('朱凰 · 上升火弧','aura','glow',pair([[.23,.50,-.39],[.59,1.06,-.4],[.59,1.78,-.41],[.30,2.23,-.37]],s),.006,8)
    elif sid.startswith('12'):
        for s in [-1,1]:
            g.ribbon('玄冥 · 单体厚肩架','chest','metal',pair([[.16,1.45,.04],[.30,1.52,-.015],[.49,1.50,-.065],[.48,1.36,-.09]],s),[.16,.26,.23,.10],normal=(0,.5,1),thickness=.02,bulge=.013)
            g.tube('玄冥 · 肩架外刻线','chest','trim',pair([[.18,1.51,.1],[.32,1.59,.045],[.49,1.57,-.01],[.53,1.42,-.055]],s),.0045,7)
        pts=[[-.42,1.10,-.39],[-.47,1.17,-.39],[-.47,1.98,-.39],[-.40,2.05,-.39],[.40,2.05,-.39],[.47,1.98,-.39],[.47,1.17,-.39],[.40,1.10,-.39],[-.42,1.10,-.39]]
        g.tube('玄冥 · 圆角方形镇渊印','aura','metal',pts,.018,10,4)
        g.tube('玄冥 · 背印内青光','aura','glow',[[x*.95,1.58+(y-1.58)*.95,z+.006] for x,y,z in pts],.004,7,4)
    elif sid.startswith('13'):
        cape(g,'太初 · 完整神衣后裳','inner',bottom=.095,width=.41)
        mantle(g,'太初 · 连续环抱护领','metal',.37,.175,drop=.095)
        g.disc('太初 · 外天环','aura','trim',[0,1.40,-.42],.75,.92,tube=.012)
        g.disc('太初 · 内天环','aura','metal',[0,1.66,-.44],.46,.55,tube=.009)
        g.disc('太初 · 外天环内沿','aura','glow',[0,1.40,-.419],.732,.90,tube=.0035)
        for s in [-1,1]:
            g.ribbon('太初 · 完整悬浮礼帛','aura','veil',pair([[.45,1.73,-.34],[.62,1.37,-.20],[.66,.75,.015],[.54,.20,.05]],s),[.08,.12,.11,.002],thickness=.004)
        g.gem('太初 · 顶部神核','aura','gem',[0,2.38,-.42],[.022,.065,.021])
    elif sid.startswith('14'):
        cape(g,'星阙 · 整幅偏置星纱披衣','veil',bottom=.105,width=.48,side=1)
        g.ribbon('星阙 · 肩至腰单一星轨','chest','trim',[[-.30,1.45,.08],[-.14,1.37,.15],[.10,1.20,.143],[.17,1.01,.06]],[.016,.014,.010,.004],thickness=.003)
        g.disc('星阙 · 第一倾斜星轨','aura','trim',[0,1.48,-.36],.73,.85,tilt=.60,tube=.008)
        g.disc('星阙 · 第二倾斜星轨','aura','metal',[0,1.48,-.38],.61,.65,tilt=-.72,tube=.008)
        for j,a in enumerate([.25,1.0,1.85,2.7,3.8,4.6,5.5]):
            x=.73*math.cos(a);y=1.48+.85*math.sin(a);z=-.36+x*math.sin(.6)
            g.gem('星阙 · 有序轨道节点','aura','gem',[x*math.cos(.6),y,z],[.012,.024 if j%2 else .035,.013])
        for s in [-1,1]:
            g.ribbon('星阙 · 单体肩端晶棱','chest','gem',pair([[.24,1.50,.0],[.38,1.61,-.06],[.42,1.73,-.10]],s),[.07,.065,.002],thickness=.012,bulge=.015)
    elif sid.startswith('15'):
        g.disc('无相 · 外虚环','aura','metal',[0,1.40,-.43],.68,.93,tube=.009,tilt=.1)
        g.disc('无相 · 偏轴内环','aura','glow',[0,1.48,-.44],.48,.69,tube=.004,tilt=-.35)
        for s in [-1,1]:
            g.ribbon('无相 · 单体悬浮大弧衣','aura','metal',pair([[.30,1.93,-.35],[.56,1.65,-.24],[.61,1.19,-.12],[.47,.61,.01],[.27,.23,.04]],s),[.003,.105,.11,.07,.002],thickness=.008,bulge=.009)
            g.tube('无相 · 大弧内缘','aura','trim',pair([[.30,1.93,-.34],[.51,1.65,-.21],[.56,1.19,-.1],[.43,.61,.025],[.27,.23,.05]],s),.0035,7)
        crest(g,'void',0,2.25,-.43,1.1,'aura','trim')
        cape(g,'无相 · 完整玄黑后裳','cloth',bottom=.10,width=.30)


def ingot(g,spec):
    # This is the canonical FLAT_TOP polygon from the latest project's ingot_shape.py.
    # Affine 3D study, not a promise of a pixel-identical inventory icon.
    canonical=[(3,17),(9,10),(24,8),(29,14),(23,18),(7,22)]
    base=np.array([[(x-16)*.017,-(y-15)*.021] for x,y in canonical])
    v=[];f=[];n=len(base)
    for y,scale in [(0,1.0),(.010,1.02),(.065,.91),(.073,.86)]:
        for x,z in base:v.append([x*scale,y,z*scale])
    for j in range(3):
        for k in range(n):
            a=j*n+k;b=j*n+(k+1)%n;c=b+n;d=a+n;f.extend([[a,b,c],[a,c,d]])
    # Determine top orientation in XZ and correct all faces if needed.
    for k in range(1,n-1):f.append([0,k+1,k])
    v=np.asarray(v);f=np.asarray(f)
    if np.cross(v[f[-1,1]]-v[f[-1,0]],v[f[-1,2]]-v[f[-1,0]])[1]>0:f=f[:,[0,2,1]]
    # Keep sharp bevel normals by duplicating each face's vertices.
    vv=[];ff=[]
    for face in f:ff.append(list(range(len(vv),len(vv)+3)));vv.extend(v[face])
    g.add('共用完整低扁锭体','ingot','metal',vv,ff)
    # Fine coplanar top tessellation improves vertex-lit fallback highlights.
    tv=[[0,.073,0]];tf=[]
    perimeter=[]
    for k in range(n):
        for t in np.linspace(0,1,7,endpoint=False):perimeter.append((base[k]*(1-t)+base[(k+1)%n]*t)*.86)
    pn=len(perimeter)
    for j in range(1,9):
        for xx,zz in perimeter:tv.append([xx*j/8,.073,zz*j/8])
    for k in range(pn):tf.append([0,1+k,1+(k+1)%pn])
    for j in range(7):
        for k in range(pn):
            a=1+j*pn+k;b=1+j*pn+(k+1)%pn;c=b+pn;d=a+pn;tf.extend([[a,b,c],[a,c,d]])
    tv=np.array(tv);tf=np.array(tf)
    if np.cross(tv[tf[0,1]]-tv[tf[0,0]],tv[tf[0,2]]-tv[tf[0,0]])[1]<0:tf=tf[:,[0,2,1]]
    g.add('同一锭体的完整精抛顶面','ingot','metal',tv,tf)
    start=len(g.meshes)
    crest(g,spec['motif'],0,0,0,1.3,'ingot','trim')
    # XY emblem becomes XZ on the top surface.
    g.transform_last(start,lambda p:[p[0],.0736+p[2]*.12,-p[1]])
    g.gem('锭中灵髓 · 同套装冠心材质','ingot','gem',[0,.074,.0],[.017,.0025,.024])
    # One sparse perimeter inlay, well inside the unbroken outer silhouette.
    pts=[[x*.79,.0737,z*.79] for x,z in base];pts.append(pts[0])
    g.tube('顶面单一浅嵌边','ingot','trim',pts,.0008,6,1)


def make_set(spec):
    g=Builder(material_set(spec));mannequin(g);boots_and_trousers(g,spec);garments(g,spec);sleeves(g,spec);shoulders(g,spec);crown(g,spec);specifics(g,spec);embroidery(g,spec);ingot(g,spec)
    return dict(id=spec['id'],materials=g.mats,meshes=g.meshes)
