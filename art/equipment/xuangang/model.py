"""玄罡完整模型草案；唯一 authored 形状、材质与表面轮廓来源。"""
MATERIALS = {
 'xg_steel': {'pattern':'metal','base':'#425b68','light':'#9caaad','dark':'#253945','noise_scale':.08,'rivets':False},
 'xg_edge': {'pattern':'metal','base':'#82969f','light':'#c6d0ca','dark':'#49616e','noise_scale':.03,'rivets':False},
 'xg_dark': {'pattern':'metal','base':'#2b414f','light':'#6b8491','dark':'#192d39','noise_scale':.06,'rivets':False},
 'xg_cloth': {'pattern':'cloth','base':'#263b4a','light':'#506473','dark':'#152938','noise_scale':.10},
 'xg_linen': {'pattern':'cloth','base':'#bfc1b3','light':'#dcdbca','dark':'#969f99','noise_scale':.10},
 'xg_leather': {'pattern':'leather','base':'#3a3632','light':'#736b59','dark':'#262722','noise_scale':.08,'stitches':False},
 'xg_wood': {'pattern':'leather','base':'#6d5140','light':'#a68861','dark':'#3f3029','noise_scale':.08,'stitches':False},
 'xg_pin': {'pattern':'metal','base':'#8d8570','light':'#b8b39b','dark':'#5b5a50','noise_scale':.03,'rivets':False},
}

def box(name,bone,xyz,size,material='xg_steel',**kw):
 return dict(name=name,bone=bone,**{'from':xyz,'size':size,'material':material},**kw)

SURFACES={}
def shell(name,bone,polygon,z,depth,material='xg_steel',step=.25,**kw):
 """A joined solid silhouette, tessellated for the existing cuboid runtime.

 Adjacent volumes touch exactly, their front texture uses shared world coordinates,
 and the internal boundaries receive no trim/shadow. They are not visual armor plates.
 """
 SURFACES[name]={'polygon':polygon,'material':material}
 y0=min(p[1] for p in polygon);y1=max(p[1] for p in polygon);result=[];row=0
 while y0<y1-1e-7:
  end=min(y1,y0+step);mid=(y0+end)/2;xs=[]
  for a,b in zip(polygon,polygon[1:]+polygon[:1]):
   if (a[1]>mid)!=(b[1]>mid):xs.append(a[0]+(mid-a[1])*(b[0]-a[0])/(b[1]-a[1]))
  xs.sort()
  for col in range(0,len(xs),2):
   l,r=xs[col:col+2]
   if r-l<.03:continue
   result.append(box(f'{name}-体积{row}-{col//2}',bone,[l,y0,z],[r-l,end-y0,depth],material,surface_group=name,**kw))
  y0=end;row+=1
 return result

HEAD=[
 box('低盔顶','head',[-3.6,-8.77,-4.4],[7.2,.34,8.8]),
 box('左顶倒角','head',[-4.5,-8.35,-4.4],[1.05,.32,8.8],rotation=[0,0,-22.5],pivot=[-4.5,-8.35,0]),
 box('右顶倒角','head',[3.55,-8.75,-4.4],[1.05,.32,8.8],rotation=[0,0,22.5],pivot=[3.55,-8.75,0]),
 box('后颅连壳','head',[-4.4,-8.35,4.12],[8.8,4.9,.30]),
 box('左颅连壳','head',[-4.45,-8.1,-3.65],[.30,3.5,7.85]),
 box('右颅连壳','head',[4.15,-8.1,-3.65],[.30,3.5,7.85]),
]
HEAD += shell('一体云额','head',[[-4.42,-8.2],[-2.3,-8.3],[0,-8.5],[2.3,-8.3],[4.42,-8.2],[4.42,-7.45],[1.1,-7.45],[0,-7.12],[-1.1,-7.45],[-4.42,-7.45]],-4.56,.30,step=.10)
for sign in [-1,1]:
 p=[[sign*x,y] for x,y in [(3.65,-7.55),(4.4,-7.55),(4.35,-4.3),(3.9,-3.7),(3.65,-4.1)]]
 HEAD+=shell('护颊'+str(sign),'head',p,-4.4,.27,step=.25)
# Continuous bib-shaped cuirass, cut around the collar, tapering toward the waist.
CHEST=[
 box('浅衬衣身','body',[-4.3,-.24,-2.3],[8.6,12.48,4.6],'xg_linen'),
 box('深交领衣面','body',[-3.9,0,-2.39],[7.8,10.8,.15],'xg_cloth'),
 box('后背衣面','body',[-3.9,0,2.3],[7.8,10.8,.15],'xg_cloth'),
 box('右内交领','body',[2.15,-.05,-2.53],[.70,4.2,.16],'xg_linen',rotation=[0,0,32],pivot=[2.5,-.05,-2.5]),
 box('左外交领','body',[-2.8,-.05,-2.55],[.70,4.65,.16],'xg_linen',rotation=[0,0,-32],pivot=[-2.45,-.05,-2.5]),
 box('低窄腰封','body',[-4.34,9.7,-2.42],[8.68,2.30,4.84],'xg_leather'),
 box('腰封细嵌线','body',[-3.98,10.25,-2.48],[7.96,.18,.10],'xg_cloth'),
 box('内嵌扣座','body',[-.67,10.06,-2.58],[1.34,1.28,.22],'xg_dark'),
 box('扣框','body',[-.48,10.26,-2.82],[.96,.88,.14],'xg_pin'),
 box('扣芯','body',[-.31,10.40,-2.98],[.62,.58,.10],'xg_dark'),
]
CHEST += shell('连体胸腹护面','body',[[-4.15,1.0],[-2.35,.75],[0,3.65],[2.35,.75],[4.15,1.0],[4.15,4.1],[3.55,6.1],[3.30,9.75],[-3.30,9.75],[-3.55,6.1],[-4.15,4.1]],-2.86,.42)
CHEST += shell('连续背护面','body',[[-3.75,1.1],[-2.0,.6],[2.0,.6],[3.75,1.1],[3.6,5.3],[3.15,9.75],[-3.15,9.75],[-3.6,5.3]],2.39,.38)
for bone,x in [('right_arm',-3.30),('left_arm',-1.30)]:
 CHEST += [
  box(bone+'整袖',bone,[x,-2.24,-2.30],[4.60,10.9,4.6],'xg_linen',fit='arm'),
  box(bone+'肩下衣面',bone,[x-.02,-2.26,-2.32],[4.64,2.4,4.64],'xg_cloth',fit='arm'),
  box(bone+'低肩连面',bone,[x-.08,-2.36,-2.37],[4.76,1.05,4.74],'xg_steel',fit='arm'),
  box(bone+'腕内革套',bone,[x-.02,4.5,-2.32],[4.64,4.4,4.64],'xg_leather',fit='arm'),
 ]
 poly=[[x+.15,4.4],[x+4.45,4.4],[x+4.28,8.75],[x+.33,8.75]]
 CHEST += shell(bone+'整面腕甲',bone,poly,-2.62,.30,'xg_dark',step=.5,fit='arm')
 # Real side fasteners on a continuous cuff, not stacked decorative bands.
 for yy in [5.0,8.0]:CHEST.append(box(bone+'腕扣'+str(yy),bone,[x+.12,yy,-2.70],[.28,.28,.13],'xg_pin',fit='arm'))
LEGS=[box('腰下整衣','body',[-4.22,11.3,-2.22],[8.44,1.25,4.44],'xg_cloth')]
FEET=[]
for bone in ['right_leg','left_leg']:
 LEGS += [box(bone+'整裤',bone,[-2.24,0,-2.24],[4.48,11.7,4.48],'xg_cloth')]
 LEGS += shell(bone+'一体护腿',bone,[[-1.95,.6],[1.95,.6],[1.84,3.5],[1.60,6.55],[1.18,7.35],[-1.18,7.35],[-1.60,6.55],[-1.84,3.5]],-2.55,.28,step=.25)
 FEET += [
  box(bone+'整靴筒',bone,[-2.30,7.2,-2.3],[4.60,5.0,4.6],'xg_leather'),
  box(bone+'连续靴头',bone,[-2.30,10.7,-2.9],[4.60,1.45,.65],'xg_dark'),
  box(bone+'靴头斜面',bone,[-2.30,10.7,-2.91],[4.60,.25,1.4],'xg_steel',rotation=[-12,0,0],pivot=[0,10.7,-2.91]),
  box(bone+'薄靴底',bone,[-2.36,12.12,-2.96],[4.72,.28,5.35],'xg_dark'),
 ]
 FEET+=shell(bone+'整面胫甲',bone,[[-1.65,7.5],[1.65,7.5],[1.93,10.95],[1.75,11.7],[-1.75,11.7],[-1.93,10.95]],-2.57,.24,'xg_dark',step=.25)
ARMOR={'version':1,'kind':'armor_set','enabled':False,'id':'myvillage:xuangang_armor','name':'玄罡甲 · 完整模型草案',
 'material':'minecraft:iron','durability_multiplier':15,'texture_size':512,'texel_density':4,
 'items':[dict(id='myvillage:xuangang_'+id,name=name,slot=slot,boxes=boxes) for id,name,slot,boxes in [
 ('helmet','玄罡盔','head',HEAD),('chestplate','玄罡胸甲','chest',CHEST),('leggings','玄罡护腿','legs',LEGS),('boots','玄罡靴','feet',FEET)]]}
TOOLS={
 'pickaxe':{'name':'玄罡镐','polygon':[[-9.0,17.2],[-7.8,20.7],[-4.4,22.6],[-1.8,23.2],[1.8,23.2],[4.4,22.6],[7.8,20.7],[9.0,17.2],[6.4,19.4],[3.2,20.8],[0,21.25],[-3.2,20.8],[-6.4,19.4]],'thickness':2.5,'motif':True},
 'axe':{'name':'玄罡斧','polygon':[[-2.6,18.6],[-2.6,21.8],[0,22.25],[4.4,23.2],[7.1,23.0],[8.1,20.2],[7.9,16.2],[5.9,14.9],[2.6,16.7],[.6,18.65]],'thickness':2.8,'motif':True},
 'hoe':{'name':'玄罡锄','socket':[0,20,0],'bridge_end':[0,18.9,5.2],'blade_top':[0,18.9,5.2],'blade_bottom':[0,14.2,6.2],'blade_width':6.7,'thickness':.72,'motif':True},
}
