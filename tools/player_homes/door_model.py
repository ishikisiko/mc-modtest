"""木格双开大门的同源几何，供设计预览与游戏资源生成使用。"""
import math

def box(parts,name,bounds,material='timber'):
    assert len(bounds)==6 and all(bounds[i]<bounds[i+3]for i in range(3)),(name,bounds)
    parts.append({'name':name,'bounds':bounds,'material':material})


def door_parts(config):
    frame=[];leaf=[];o=config['opening'];s=config['leaf'];bottom,top=s['bottom'],s['top'];w=s['width'];d=s['thickness'];bar=s['stile_width']
    # 后侧退口容纳开启后的门板，正面仍保留三格净宽。
    for side in (0,1):
        def mirrored(b):return b if side==0 else[5-b[3],b[1],b[2],5-b[0],b[4],b[5]]
        box(frame,'粉墙前收边',mirrored([0,.125,0,.5,5,.5]),'plaster')
        box(frame,'粉墙后退口',mirrored([0,.125,.5,.25,5,1]),'plaster')
        box(frame,'门框石脚',mirrored([.375,.125,0,1,.375,.5625]),'stone')
        box(frame,'木门立框',mirrored([.5,.375,.125,1,4.625,.5]))
        box(frame,'止口',mirrored([.9375,.375,.5,1,4.125,.625]))
        for h in s['hinge_heights']:
            box(frame,'门轴座',mirrored([.375,h-.0625,.4375,.5625,h+.0625,.75]),'bronze')
    box(frame,'低门槛',[0,0,0,5,o[1],1],'stone')
    box(frame,'上门楣',[.5,o[3],.125,4.5,4.625,.875])
    box(frame,'楣上粉墙',[.5,4.625,0,4.5,5,1],'plaster')
    box(frame,'浅木收边',[.5,4.5,.0625,4.5,4.625,.125])
    for x in (0,w-bar):box(leaf,'门扇边梃',[x,bottom,0,x+bar,top,d])
    for y,height in ((bottom,.25),(2,.25),(top-.25,.25)):
        box(leaf,'门扇横档',[bar,y,0,w-bar,y+height,d])
    for i in range(4):
        x=round((bar+i*(w-2*bar)/4)*16)/16;end=round((bar+(i+1)*(w-2*bar)/4)*16)/16
        box(leaf,'下部实心木板',[x,bottom+.25,.0625,end,2,.1875],'panel')
    for x in (.5625,1.0,1.4375):box(leaf,'上部门棂竖杆',[x,2.25,.0625,x+.0625,top-.25,.125])
    for y in (2.75,3.25):box(leaf,'上部门棂横杆',[bar,y,.0625,w-bar,y+.0625,.125])
    for h in s['hinge_heights']:
        box(leaf,'门轴铜箍',[0,h-.0625,-.0625,.4375,h+.0625,0],'bronze')
    hx,hy=s['ring_center'];front=s['ring_front']
    box(leaf,'门环背板',[hx-.1875,hy-.1875,-.0625,hx+.1875,hy+.25,0],'bronze')
    box(leaf,'门环固定销',[hx-.0625,hy+.125,front+.0625,hx+.0625,hy+.25,-.0625],'bronze')
    # 八段硬边铜环，不使用光滑圆环或发光材质。
    ring=[[-2,2,2,3],[-3,1,-1,2],[1,1,3,2],[-3,-2,-2,1],[2,-2,3,1],[-3,-3,-1,-2],[1,-3,3,-2],[-2,-4,2,-3]]
    for a,b,c,e in ring:box(leaf,'铜门环',[hx+a/16,hy+b/16,front,hx+c/16,hy+e/16,front+.0625],'bronze')
    return frame,leaf


def transformed_bounds(part,pivot,angle,mirror=False):
    b=part['bounds'];co,si=math.cos(angle),math.sin(angle);points=[]
    for x in (b[0],b[3]):
        for y in (b[1],b[4]):
            for z in (b[2],b[5]):
                local_x=-x if mirror else x
                points.append([pivot[0]+local_x*co+z*si,pivot[1]+y,pivot[2]-local_x*si+z*co])
    return [*[min(p[i]for p in points)for i in range(3)],*[max(p[i]for p in points)for i in range(3)]]


def check_design(config,frame,leaf):
    o=config['opening'];s=config['leaf'];maximum=-1;central_intrusions=[];leaf_intersections=[]
    def intersects(a,b):return all(min(a[i+3],b[i+3])-max(a[i],b[i])>1e-7 for i in range(3))
    portal=[o[0],o[1],0,o[2],o[3],s['swing_reservation_depth']]
    frame_intrusions=[p['name']for p in frame if intersects(p['bounds'],portal)]
    assert not frame_intrusions,('门框侵入净通道',frame_intrusions)
    for degrees in range(0,91):
        a=math.radians(degrees)
        left=[transformed_bounds(p,s['left_pivot'],-a)for p in leaf]
        right=[transformed_bounds(p,s['right_pivot'],a,True)for p in leaf]
        maximum=max(maximum,max(b[5]for b in left+right))
        if any(intersects(l,r)for l in left for r in right):leaf_intersections.append(degrees)
        if degrees==90:
            central_intrusions=[b for b in left+right if intersects(b,portal)]
    assert not leaf_intersections,('门扇开合相交',leaf_intersections)
    assert not central_intrusions,('全开门扇侵入净通道',central_intrusions)
    assert maximum<=s['swing_reservation_depth'],('开合区不足',maximum)
    assert all(all(abs(v*16-round(v*16))<1e-7 for v in p['bounds'])for p in frame+leaf),'构件没有对齐1/16格'
    return {'status':'design_geometry_checked','opening_width':o[2]-o[0],'opening_height':o[3]-o[1],
            'sampled_angles':91,'left_right_leaf_intersections':0,'fully_open_intrusions':0,'frame_intrusions':0,
            'swing_maximum_z':maximum,'reserved_depth':s['swing_reservation_depth'],
            'scope':'offline_design_geometry'}


