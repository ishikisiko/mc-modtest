"""九色琉光减法精修：一颗主棱晶、完整琉璃轮廓、单条折光脊。"""
from mc_common import Builder, material


def refine(s):
    if s['id'] != 'yuanying_jiuse':
        return s
    removed=('glass_chest_face','glass_collar','glass_back_fall','glass_fall_end',
             'glass_knee_weave_','glass_crown_screen','glass_screen_stamp_',
             'glass_boot_stamp_')
    s['components'][:]=[c for c in s['components'] if not c['id'].startswith(removed)]
    m=s['materials'];b=Builder(s)
    m['jiu_cloth']=material('静青整幅承衣','#36545e','cloth','#5b7880')
    m['jiu_lining']=material('深青内襟','#243e48','cloth','#44616a')
    m['jiu_pearl']=material('月白磨砂玉衬','#baccc8','jade','#dce2d6')
    m['jiu_silver']=material('柔银细收口','#9ab7b8','trim','#cedbd4')
    m['jiu_glass']=material('清青完整琉璃','#7fa4ab','glass','#bed3d1',opacity=.69)
    m['jiu_core']=material('九色凝于一棱晶','#9c9fbd','jade','#d4d5df')
    palette=['#8e9caf','#d8dfda','#a9caca','#89b4c0','#9d91ba','#b59bae','#c1b794','#a4c1b1','#a8b6d2']
    rows=[]
    for y in range(16):
        row=[]
        for x in range(16):
            c=4 if x<8 else 0
            if x in (7,8):c=1
            if x<3:c=3 if y<8 else 7
            if x>12:c=8 if y<8 else 5
            if y<2:c=2
            if y>13:c=6
            row.append(str(c))
        rows.append(''.join(row))
    m['jiu_core']['pixelArt']={'kind':'jade','mapping':'fit','palette':palette,'rows':rows}
    # Large quiet fields, not a repeated jewel stamp or all-over crystal grid.
    m['jiu_waterfall']=material('留白琉瀑与三面折光','#648d97','glass',opacity=.64)
    rows=[]
    for y in range(32):
        row=[]
        for x in range(32):
            c=0
            if x<5+y//12:c=1
            elif x>27-y//12:c=4
            if 14+y//12<=x<=16+y//12:c=2
            if x==16+y//12:c=3
            row.append(str(c))
        rows.append(''.join(row))
    m['jiu_waterfall']['pixelArt']={'kind':'glass','mapping':'fit','palette':['#648d97','#729ba2','#9ab7bc','#c1d1ce','#587f8c'],'rows':rows}
    for c in s['components']:
        if c['material']=='cloth':c['material']='jiu_cloth'
        if c['material']=='dark':c['material']='jiu_lining'
        if c['material']=='porcelain':c['material']='jiu_pearl'
        if c['id'].startswith('glass_boot_shell_'):c['material']='jiu_glass'
        if c['id']=='glass_buckle':c['material']='jiu_silver';c['size']=[1.8/16,.75/16,.22/16]
        if c['id']=='glass_crown_root':c['position']=[0,32.8/16,-1.8/16];c['size']=[2.1/16,.8/16,1.2/16]
        if c['id']=='glass_fall_root':c['position']=[0,23.6/16,-3.9/16];c['size']=[7.4/16,.65/16,2.7/16]
    # A narrow open collar frames the single raised main stone.
    b.panel('jiu_inner_collar','chest','jiu_pearl',[(-3.7,24),(-2,24),(0,21.8),(2.1,24),(3.7,24),(0,20.9)],
            pos=(0,0,2.63),depth=.19,name='素净月白内领')
    b.panel('jiu_heart_seat','chest','jiu_silver',[(0,22.1),(1.52,20.7),(1.38,17.2),(0,16.05),(-1.38,17.2),(-1.52,20.7)],
            pos=(0,0,2.8),depth=.45,name='单颗主石的窄银切角底座')
    b.panel('jiu_heart','chest','jiu_core',[(0,21.85),(1.25,20.6),(1.12,17.4),(0,16.35),(-1.12,17.4),(-1.25,20.6)],
            pos=(0,0,3.18),depth=.72,name='有厚度的九色主棱晶，细色只在切面中出现')
    b.box('jiu_new_collar_root','chest','jiu_lining',[0,24.0,-1.4],[14.8,.6,2.7],'完整琉璃肩领的低伏根座')
    for sign in (-1,1):
        poly=[(sign*x,y)for x,y in [(4,24.2),(5.4,25.3),(8.9,24.8),(9.4,23.8),(6.8,23.3)]]
        b.panel(f'jiu_shoulder_{sign}','chest','jiu_glass',poly,pos=(0,0,-.5),depth=4.5,name='取消彩色键块的完整清青肩领')
        b.bar(f'jiu_shoulder_edge_{sign}','chest','jiu_silver',[sign*5.4,25.3,1.8],[sign*8.9,24.8,1.8],.13,.1,'肩领单道柔银切缘')
        b.box(f'jiu_cuff_edge_{sign}','chest','jiu_silver',[sign*6.2,16.7,2.82],[5.1,.28,.08],'袖口单道细银收边')
        b.box(f'jiu_cuff_shadow_{sign}','chest','jiu_lining',[sign*6.2,16.25,2.83],[5.1,.3,.08],'袖口低对比内沿')
    waterfall=[(-4.5,23.7),(4.5,23.7),(5.2,19),(4.5,12),(3,8),(.6,7.2),(-2.4,4.9),(-4.1,6.1),(-4.6,12),(-5.2,18)]
    b.panel('glass_cut_waterfall','chest','jiu_waterfall',waterfall,pos=(0,0,-5.2),depth=.64,
            name='大面留白的完整三折琉瀑')
    b.panel('jiu_back_ridge','chest','jiu_glass',[(.1,22.7),(1,22.7),(1.7,16.3),(1.15,10.4),(1.1,8.4),(.5,9),(.45,12.2),(.65,16.3)],
            pos=(0,0,-5.67),depth=.34,name='琉瀑偏心单条实体折光脊')
    b.outline('jiu_back_edge_left','chest','jiu_silver',[(-4.5,23.7),(-5.2,18),(-4.6,12),(-4.1,6.1),(-2.4,4.9)],-5.57,width=.08,depth=.06,name='琉瀑左侧极细切缘')
    b.outline('jiu_back_edge_right','chest','jiu_silver',[(5.2,19),(4.5,12),(3,8)],-5.57,width=.08,depth=.06,name='琉瀑右下局部切缘')
    b.panel('jiu_crown','head','jiu_glass',[(-3.6,32.7),(-3.25,34.6),(0,36.2),(3.25,34.6),(3.6,32.7)],
            pos=(0,0,-1.8),depth=1.15,name='完整单峰琉璃冠')
    b.panel('jiu_crown_heart','head','jiu_core',[(0,35.4),(.62,34.6),(0,33.6),(-.62,34.6)],
            pos=(0,0,-1.13),depth=.24,name='低位小棱晶呼应胸前主石')
    s['palette']=['#36545e','#7fa4ab','#baccc8','#9d91ba','#9ab7b8']
    s['style']='一晶凝九色 · 素面分光'
    s['tagline']='九色藏于一颗棱晶，清光沿完整琉瀑缓缓流动。'
    s['description']='九色琉光保留宽短袖、独立双腿和离背琉瀑，重新收束整套的视觉主次。清青承衣与月白内领围住一颗有厚度的主棱晶，柔银底座只作窄边；九种微色藏在主石的切面内，远看先见紫青主色，近看才读出细色。双膝取消重复菱花，袖口只留一深一浅的细收边。肩领是一件连续清青琉璃轮廓，取消成排彩色键块；冠改为单峰完整形制，只有一颗较小棱晶回应胸前。背瀑以三面低对比折光建立体积，大面积留白中只有一条偏心光脊。前后不再重复完整花窗图案，光色与身体轮廓各有清楚的位置。'
    s['silhouette']='仍以Minecraft的方头宽臂比例为基础。冠从多峰与浮晶收成一件低位单峰琉璃冠，额面开放。肩领压低收窄，宽袖、短襟和独立裤腿保留原有轻捷形制。主石缩到胸部中心，四周显露完整承衣与内领。琉瀑上宽下收，末端带轻微偏切，侧面可见离身距离、实体厚度和偏心折光脊；装饰不会让衣服读成满身镶满徽章的硬板。'
    s['materialStory']='主色为静青承衣、清青琉璃和月白内衬，紫青棱晶承担唯一强色焦点。九色不再分散成冠顶、肩头和双膝的多组彩块，而收进主石切面的细小色阶中；冠心以更小尺度呼应。背瀑用同一青色系的三面明暗表达透明体积，细银切缘只留在两侧局部，宽幅留白允许透光与动态折光被看见。所有穿戴新材质独立命名，原九格锭及其引用材质保持原样。'
    s['progression']='元婴气质来自凝而不散的主石、真正有厚度的琉璃肩领和离身琉瀑。细节分为胸前主晶、冠上小晶与衣缘微线三个尺度，双膝和宽袖的大面留给材质本身。分光动效集中到背瀑的一条光脊，前胸只有缓慢明灭；动态与静态共同遵守同一主次，不用多路彩色印记增加热闹。'
    s['parts']['head']['description']='完整单峰琉璃冠贴合低位冠座，一颗小棱晶位于中央。原有细密晶格、多峰轮廓和三枚浮晶移除，方脸与额缘保持开放；小冠有真实切面与厚度，颜色从属于胸前主石。'
    s['parts']['chest']['description']='清青完整承衣配月白内领，胸前只保留一颗有厚度的主棱晶与窄银座。肩领是完整透光切面，袖口仅留低对比细收边。背后保留离身琉瀑，三面同色折光与单条实体光脊形成层次，宽幅留白中不再叠放花窗、菱格和边角彩石。'
    s['parts']['legs']['description']='完整方裤与高位短襟保留，双膝恢复无镶花的整洁衣面。深青腰封配缩小的柔银扣，正、背与侧面均保持完整包覆。下装负责衬托胸前主石，避免再次复制上身图案。'
    s['parts']['boots']['description']='两只完整方履保留玻璃外壳与磨砂内芯，色差降低，移除正面亮白方印。低薄深色鞋底压住轮廓，脚背的透光和材质厚度承担细节，不再添加彩色纹章。'
    # Retain all ingot materials and only materials actually used by this design.
    used={c[k]for c in s['components']+s['ingot']['components']for k in ('material','topMaterial','edgeMaterial','trimMaterial')if k in c}
    s['materials']={k:v for k,v in m.items()if k in used}
    return s
