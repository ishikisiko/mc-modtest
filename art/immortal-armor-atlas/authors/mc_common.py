"""Minecraft style authoring: input coordinates in model pixels, 16 pixels/metre."""
import math

P=1/16
REALMS=['炼气','筑基','金丹','元婴','化神']

def color_shift(c,f):
    return '#'+''.join(f'{max(0,min(255,round(int(c[i:i+2],16)*f))):02x}' for i in (1,3,5))

def material(name,color,kind='cloth',accent=None,opacity=1):
    """Authored 16x16 pixel swatch, hard-edged ramps, no smoothing/no random noise."""
    palette=[color_shift(color,.74),color_shift(color,.9),color,color_shift(color,1.13),accent or color_shift(color,1.3)]
    rows=[]
    for y in range(16):
        row=[]
        for x in range(16):
            if kind=='cloth': k=1 if x%4==3 else 2
            elif kind=='metal': k=3 if y in (2,3) else 1 if y in (13,14) else 2
            elif kind=='jade': k=3 if (x+y//3)%13 in (0,1) else 2
            elif kind=='glass': k=3 if (x-y)%12 in (0,1) else 2
            else:k=3 if y<3 else 2
            if kind=='cloth' and y%8==7 and x%4==3:k=0
            row.append(str(k))
        rows.append(''.join(row))
    return dict(name=name,color=color,metalness=0,
                roughness=.9 if kind in ('metal','trim') else 1 if kind=='cloth' else .72,
                opacity=opacity,pixelArt=dict(kind=kind,palette=palette,rows=rows))

def spec(id,name,level,style,materials,**text):
    required=['tagline','description','silhouette','materialStory','progression','parts','ingot']
    for k in required:assert k in text,(id,k)
    return dict(id=id,name=name,level=level,realm=REALMS[level-1],style=style,
        palette=[m['color'] for m in list(materials.values())[:5]],materials=materials,components=[],
        segmentation='整套不能整体全部由分片构成；允许局部碎片、鳞片、叠甲或护肩增强层次。主体、衣襟和像素纹样共同形成造型，横向纹理接缝允许。',**text)

class Builder:
    def __init__(self,s):self.s=s;self.items=s['components']
    def box(self,id,role,mat,pos,size,name=None,rot=None):
        c=dict(id=id,name=name or id,role=role,material=mat,shape='box',position=[v*P for v in pos],size=[v*P for v in size])
        if rot:c['rotation']=rot
        self.items.append(c);return c
    def panel(self,id,role,mat,poly,pos=(0,0,0),depth=.4,name=None,rot=None):
        c=dict(id=id,name=name or id,role=role,material=mat,shape='panel',polygon=[[x*P,y*P] for x,y in poly],position=[v*P for v in pos],depth=depth*P,bevel=0)
        if rot:c['rotation']=rot
        self.items.append(c);return c
    def bar(self,id,role,mat,a,b,width=.4,depth=.25,name=None):
        assert abs(a[2]-b[2])<1e-8,'bar is in XY plane; use box rotation for other planes'
        dx,dy=b[0]-a[0],b[1]-a[1]
        return self.box(id,role,mat,[(a[i]+b[i])/2 for i in range(3)],[width,math.hypot(dx,dy),depth],name,rot=[0,0,-math.degrees(math.atan2(dx,dy))])
    def outline(self,id,role,mat,points,z,width=.35,depth=.25,closed=False,name='连续硬边纹'):
        p=points+[points[0]] if closed else points
        for i,(a,b) in enumerate(zip(p,p[1:])):self.bar(f'{id}_{i}',role,mat,[*a,z],[*b,z],width,depth,name)
    def cap(self,mat,trim,top=32.4):
        self.box('cap_top','head',mat,[0,top,0],[8.6,.8,8.6],'平顶方冠基')
        self.box('cap_back','head',mat,[0,28.2,-4.25],[8.6,7.6,.5],'完整方形后脑护面')
        for sign in (-1,1):self.box(f'cap_side_{sign}','head',mat,[sign*4.25,29,0],[.5,6,8.5],'方形鬓侧')
        self.box('cap_brow','head',trim,[0,31.2,4.3],[8.7,.85,.45],'低额箍')
    def foundation(self,body,sleeve,pants,boot,body_width=8.5,sleeve_width=4.5,sleeve_bottom=15):
        self.box('body','chest',body,[0,18,0],[body_width,12.4,4.6],'完整方形胸背')
        for sign in (-1,1):
            self.box(f'sleeve_{sign}','chest',sleeve,[sign*6,(24.3+sleeve_bottom)/2,0],[sleeve_width,24.3-sleeve_bottom,4.5],'完整方袖')
            self.box(f'pants_{sign}','legs',pants,[sign*2,8.4,0],[4.15,7.8,4.3],'独立方裤腿')
            self.box(f'boot_{sign}','boots',boot,[sign*2,2.45,.25],[4.35,4.9,5.0],'完整方靴')
    def ingot(self,body,top,edge,name,pattern='line'):
        self.items=self.s['ingot'].setdefault('components',[])
        self.items.append(dict(id='ingot_body',name=name,role='ingot',material=body,shape='ingot',size=[.38,.065,.215],topMaterial=top,edgeMaterial=edge))
        # Low, pixel-width engravings on the original flat ingot family.
        self.box('ingot_inlay','ingot',edge,[0,.55,0],[1.5,.12,.75],'锭面矩形芯')
        for sign in (-1,1):self.box(f'ingot_mark_{sign}','ingot',top,[sign*1.8,.57,sign*.5],[1.6,.1,.18],'横卧像素锻纹')
        self.items=self.s['components']
