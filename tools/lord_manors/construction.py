"""七府施工合同：屋面收口、挂件避让和真实锚固。所有尺寸使用 1/16 格。"""
from __future__ import annotations

from collections import defaultdict
import copy
import hashlib


def bounds(box):
    lo=[round((box[i]-box[i+3]/2)*16) for i in range(3)]
    return tuple(lo+[max(lo[i]+1,round((box[i]+box[i+3]/2)*16)) for i in range(3)])


def box_at(q,role,owner,kind):
    return [(q[i]+q[i+3])/32 for i in range(3)]+[(q[i+3]-q[i])/16 for i in range(3)]+[role,owner,kind]


def overlap(a,b):
    return [min(a[i+3],b[i+3])-max(a[i],b[i]) for i in range(3)]


def touches(a,b):
    o=overlap(a,b)
    return all(n>=0 for n in o) and sum(n>0 for n in o)>=2


def roof_edges(scene,sheet):
    sides=defaultdict(lambda:([],[]));boxes=scene['boxes']
    for index in sheet['boxes']:
        b=boxes[index]
        if b[6]!='roof' or b[7]!=sheet['owner']:raise ValueError('屋面合同引用了其他构件')
        q=bounds(b)
        for axis in (0,2):
            sides[axis,q[axis]][0].append((index,q))
            sides[axis,q[axis+3]][1].append((index,q))
    for (axis,plane),(starts,ends) in sorted(sides.items()):
        other=2-axis
        for i,a in ends:
            for j,b in starts:
                low=max(a[other],b[other]);high=min(a[other+3],b[other+3])
                if i!=j and low<high:yield i,j,axis,plane,other,low,high


def roof_joint(scene,edge,rules):
    i,j,axis,plane,other,t0,t1=edge;a=bounds(scene['boxes'][i]);b=bounds(scene['boxes'][j])
    if max(a[1],b[1])<min(a[4],b[4]):return None
    high,low=(a,b) if a[1]>b[1] else (b,a)
    depth=rules['roof_joint_depth'];lap=rules['roof_joint_overlap']
    q=[0]*6
    if high[axis]==plane:q[axis],q[axis+3]=plane,min(high[axis+3],plane+depth)
    else:q[axis],q[axis+3]=max(high[axis],plane-depth),plane
    q[1],q[4]=low[4]-lap,high[1]+lap
    q[other],q[other+3]=t0,t1
    return tuple(q)


def panel_blockers(scene,panel,ignore):
    a=bounds(scene['boxes'][panel])
    return [(i,bounds(b)) for i,b in enumerate(scene['boxes']) if i not in ignore and all(n>0 for n in overlap(a,bounds(b)))]


def title_box(sign,index):
    _,x,y,z,width=sign
    return [x,y,z,width,1.3,.16,'plaque_'+str(index),'title_board','detail']


def title_blockers(scene,index):
    q=bounds(title_box(scene['signs'][index],index));front=q[2]
    return [(i,bounds(b)) for i,b in enumerate(scene['boxes'])
            if (a:=bounds(b))[2]<front<=a[5] and all(max(a[d],q[d])<min(a[d+3],q[d+3]) for d in (0,1))]


def prepare(scene,city_rules,rules):
    out=copy.deepcopy(scene);boxes=out['boxes'];roof_seen=[];added=[];joins=[];panels=[];titles=[]
    for sheet in city_rules['roof_sheets']:
        roof_seen.extend(sheet['boxes'])
        for edge in roof_edges(scene,sheet):
            q=roof_joint(scene,edge,rules)
            if q is None:continue
            i,j,*_=edge;index=len(boxes)
            boxes.append(box_at(q,'roof',sheet['owner'],'roof_joint'))
            added.append(index);joins.append({'sheet':sheet['id'],'source':[i,j],'joint':index})
    expected={i for i,b in enumerate(scene['boxes']) if b[6]=='roof'}
    if len(roof_seen)!=len(set(roof_seen)) or set(roof_seen)!=expected:raise ValueError('屋面合同必须完整且唯一地覆盖全部屋瓦')
    for panel in city_rules['hanging_panels']:
        index=panel['box'];support=panel['support'];axis=panel['axis'];direction=panel['direction']
        if boxes[index][6]!='banner' or boxes[support][7]!='banner':raise ValueError('挂幡合同引用不符')
        original=bounds(boxes[index]);q=list(original);old_support=bounds(boxes[support]);ignore={index,support}
        # 沿合同声明的外法线逐一避开真实交叠体，绝不把布面埋回墙/檐。
        for _ in range(len(boxes)+1):
            blockers=panel_blockers(out,index,ignore)
            if not blockers:break
            if direction>0:
                shift=max(b[axis+3] for _,b in blockers)+rules['sign_clearance']-q[axis]
            else:shift=min(b[axis] for _,b in blockers)-rules['sign_clearance']-q[axis+3]
            q[axis]+=shift;q[axis+3]+=shift
            boxes[index]=box_at(q,*boxes[index][6:9])
        else:raise ValueError('挂幡避让无法收敛')
        rod=list(old_support)
        if direction<0:rod[axis]=q[axis]
        else:rod[axis+3]=q[axis+3]
        boxes[support]=box_at(rod,*boxes[support][6:9])
        # 原吊杆也必须真实落在建筑上；从内端向下寻找合同所属建筑的最高承点。
        span=min(old_support[4]-old_support[1],old_support[5]-old_support[2]);peg=list(old_support)
        if direction<0:peg[axis]=old_support[axis+3]-span
        else:peg[axis+3]=old_support[axis]+span
        candidates=[]
        for i,b in enumerate(scene['boxes']):
            if b[7]!=panel['anchor_owner']:continue
            a=bounds(b)
            if a[4]<=rod[1] and all(max(a[k],peg[k])<min(a[k+3],peg[k+3]) for k in (0,2)):
                candidates.append((a[4],i,a))
        if not candidates:raise ValueError('挂幡吊杆没有实际建筑承点')
        top,anchor,a=max(candidates)
        peg[1],peg[4]=top-rules['roof_joint_overlap'],rod[4]
        peg_index=len(boxes);boxes.append(box_at(peg,boxes[support][6],'banner','structure'));added.append(peg_index)
        panels.append({'id':panel['id'],'panel':index,'support':support,'peg':peg_index,'anchor':anchor,'shift_16ths':q[axis]-original[axis]})
    for title in city_rules.get('title_boards',[]):
        index=title['sign'];axis=title['axis'];direction=title['direction'];initial=bounds(title_box(out['signs'][index],index))
        if axis!=1 or direction!=-1:raise ValueError('当前题匾合同只允许沿承梁下调，不允许无锚固地悬空前移')
        for _ in range(len(boxes)+1):
            blockers=title_blockers(out,index)
            if not blockers:break
            current=bounds(title_box(out['signs'][index],index))
            shift=min(q[axis] for _,q in blockers)-rules['sign_clearance']-current[axis+3]
            out['signs'][index][2]+=shift/16
        else:raise ValueError('题匾避让无法收敛')
        final=bounds(title_box(out['signs'][index],index))
        titles.append({'id':title['id'],'sign':index,'mount':title['mount'],'shift_16ths':final[axis]-initial[axis]})
    result={'roof_sheets':len(city_rules['roof_sheets']),'roof_joints':joins,'hanging_panels':panels,'title_boards':titles,'added_boxes':added}
    issues=check(out,scene,city_rules,rules,result)
    if issues:raise ValueError('七府施工合同失败：'+str(issues[:6]))
    return out,result


def check(scene,source,city_rules,rules,result):
    """用独立接触关系检查施工结果，不能以单个构件封闭代替整屋连续。"""
    issues=[];by_source={tuple(row['source']):row for row in result['roof_joints']}
    for sheet in city_rules['roof_sheets']:
        for edge in roof_edges(source,sheet):
            i,j,axis,plane,other,t0,t1=edge;a=bounds(scene['boxes'][i]);b=bounds(scene['boxes'][j])
            if touches(a,b):continue
            row=by_source.get((i,j))
            if row is None:issues.append(('roof_open',sheet['id'],i,j));continue
            q=bounds(scene['boxes'][row['joint']])
            # 收口必须覆盖整条共享边，并同时面接触两块屋瓦；不接受只搭角点。
            if q[other]>t0 or q[other+3]<t1 or not touches(q,a) or not touches(q,b):issues.append(('roof_joint_incomplete',sheet['id'],i,j))
            high=a if a[1]>b[1] else b
            if q[0]<high[0] or q[3]>high[3] or q[2]<high[2] or q[5]>high[5]:issues.append(('roof_joint_outside_footprint',sheet['id']))
    for row in result['hanging_panels']:
        panel=row['panel'];support=row['support'];peg=row['peg'];anchor=row['anchor']
        collisions=panel_blockers(scene,panel,{panel,support,peg})
        if collisions:issues.append(('panel_embedded',row['id'],[i for i,_ in collisions]))
        for a,b in ((panel,support),(support,peg),(peg,anchor)):
            if not touches(bounds(scene['boxes'][a]),bounds(scene['boxes'][b])):issues.append(('panel_unanchored',row['id'],a,b))
    for index,_ in enumerate(scene.get('signs',[])):
        blockers=title_blockers(scene,index)
        if blockers:issues.append(('title_obstructed',index,[i for i,_ in blockers]))
    for row in result['title_boards']:
        if not touches(bounds(title_box(scene['signs'][row['sign']],row['sign'])),bounds(scene['boxes'][row['mount']])):
            issues.append(('title_unmounted',row['id']))
    return issues


def source_identity(path,city_rules):
    if hashlib.sha256(path.read_bytes()).hexdigest()!=city_rules['source_sha256']:
        raise ValueError('屋面/挂件合同与 authored 构造不一致：'+str(path))
