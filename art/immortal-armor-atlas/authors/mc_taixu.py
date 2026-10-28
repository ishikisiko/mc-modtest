"""太虚无相：缺角玉璧与穿璧云衣，保留方块骨架与真实镂空。"""
from mc_common import Builder, material


def refine(s):
    if s['id'] != 'huashen_taixu':
        return s
    # Only garment components are replaced. The independent ingot is immutable.
    removed = ('fold_', 'back_ligament', 'window_post_', 'window_lintel',
               'crown_violet', 'back_window_', 'back_fall_', 'orn_back', 'brow_jade')
    s['components'][:] = [c for c in s['components'] if not c['id'].startswith(removed)]
    m = s['materials']
    m.pop('orn_back', None)
    m['void_silk'] = material('凝月白天绫', '#d8e2d7', 'cloth', '#eef0dd')
    m['void_deep'] = material('寂青织锦内襟', '#35575c', 'cloth', '#91b8ab')
    m['void_jade'] = material('太虚温青玉胎', '#6c9d96', 'jade', '#c6d6bd')
    m['void_face'] = material('温玉磨面', '#bdcdb8', 'jade', '#e5e6cb')
    m['void_silver'] = material('月白细银錾边', '#a9b9af', 'trim', '#e8e6ce')
    m['void_amethyst'] = material('凝虚紫髓', '#877eaa', 'jade', '#bfb5cc')
    # Three authored scales: small seal details, a woven border, a quiet brocade.
    def texture(key, name, palette, draw, kind='cloth'):
        a = [['0'] * 32 for _ in range(32)]
        def rect(x,y,w,h,c):
            for yy in range(max(0,y),min(32,y+h)):
                for xx in range(max(0,x),min(32,x+w)):a[yy][xx]=str(c)
        def line(points,c):
            for (x,y),(xx,yy) in zip(points,points[1:]):
                dx,dy=xx-x,yy-y;n=max(abs(dx),abs(dy))
                for i in range(n+1):rect(round(x+dx*i/max(1,n)),round(y+dy*i/max(1,n)),1,1,c)
        draw(rect,line)
        mat=material(name,palette[0],kind)
        mat['pixelArt']={'kind':kind,'mapping':'fit','palette':palette,'rows':[''.join(row)for row in a]}
        m[key]=mat
    def seal(r,l):
        l([(7,4),(4,7),(4,24),(8,28),(24,28),(28,24),(28,8),(24,4),(18,4)],2)
        l([(9,7),(7,9),(7,22),(10,25),(22,25),(25,22),(25,10),(22,7),(18,7)],1)
        l([(11,11),(15,8),(20,11),(20,16),(16,19),(12,16),(12,12)],3)
        l([(15,11),(17,11),(17,15),(15,15)],2)
        l([(10,19),(10,22),(13,22)],2);l([(19,21),(22,21),(22,18)],2)
        r(14,21,3,3,4);r(15,22,1,1,3);r(4,3,2,2,3);r(26,27,2,2,3)
    def border(r,l):
        r(0,2,32,2,2);r(0,28,32,2,2);r(0,6,32,1,1);r(0,25,32,1,1)
        for x in (0,16):
            l([(x,16),(x+6,10),(x+12,16),(x+6,22),(x,16)],2)
            l([(x+4,16),(x+6,14),(x+8,16),(x+6,18),(x+4,16)],3)
            r(x+13,15,2,2,4)
    def brocade(r,l):
        for x,y in [(2,5),(18,21)]:
            l([(x,y+5),(x+4,y+1),(x+9,y+1),(x+12,y+4)],1)
            l([(x,y+7),(x+7,y+7),(x+10,y+4),(x+8,y+2)],2)
        l([(2,27),(6,27),(6,29)],3);r(27,3,2,2,3)
    texture('void_seal','无相缺角印锦',['#35575c','#608781','#a4bca4','#e4e4ca','#a299bb'],seal)
    texture('void_rear_weave','云衣同底银青印绣',['#d8e2d7','#acc4b6','#739e95','#b8cbb8','#a299bb'],seal)
    texture('void_border','双层菱目银锦',['#4c716e','#71968c','#becbb5','#e4e7d2','#a398b6'],border)
    texture('void_brocade','云根暗花天绫',['#d8e2d7','#c2d4c7','#aec6be','#b8b9c6','#edf0df'],brocade)
    def jade_grain(r,l):
        l([(2,27),(7,23),(8,17),(12,14),(14,8),(20,3)],1)
        l([(4,28),(8,24),(9,18),(13,15)],2)
        l([(21,28),(24,24),(23,19),(28,13)],1)
        r(9,21,1,2,3);r(24,20,1,1,3)
    texture('void_face','温玉磨面与浅色玉脉',['#bdcdb8','#b1c4b0','#cad5bd','#dde0c6','#bdcdb8'],jade_grain,'jade')
    def incision(r,l):
        l([(8,4),(5,7),(5,25),(9,28),(23,28),(26,25),(26,7),(23,4),(8,4)],1)
        l([(10,8),(10,23),(15,23),(15,18),(20,18),(20,9)],2)
        l([(14,10),(19,10),(19,13),(14,13),(14,16)],3)
        r(19,22,3,3,4)
    texture('void_incised','玉璧细阴刻',['#bdcdb8','#73988c','#456f69','#e1dfc6','#9c90ad'],incision,'jade')
    b=Builder(s)
    for c in s['components']:
        if c['material']=='robe':c['material']='void_silk'
        if c['id']=='cross_front':c['material']='void_brocade'
        if c['id'] in ('collar_left','collar_right'):c['material']='void_deep'
        if c['id'] in ('left_cuff','right_cuff'):
            c['material']='void_border';c['size'][1]=1.5/16
        if c['id']=='cloud_waist':c['material']='void_border'
        if c['id']=='cloud_buckle':c['material']='void_amethyst'
        if c['id']=='cloud_front_left':c['material']='void_brocade'
        if c['id']=='cloud_front_right':c['material']='void_deep'
        if c['id']=='cloud_back':c['material']='void_brocade'
        if c['id'] in ('cap_back','cap_side_-1','cap_side_1'):c['material']='void_deep'
        if c['id'].startswith('silent_boot'):c['material']='void_deep'
    # Low, asymmetric open crown: a cut jade contour, not a rectangular handle.
    b.panel('void_crown','head','void_jade',
            [(-3.7,32.4),(-3.7,34.8),(-1.7,37.1),(1.1,37.1),(1.1,35.9),(-1,35.9),
             (-2.3,34.3),(-2.3,33.4),(2.6,33.4),(2.6,35.2),(3.7,35.2),(3.7,32.4)],
            pos=(0,0,-1.4),depth=1.25,name='缺角折玉小冠与真实透空')
    b.box('void_brow','head','void_amethyst',[-1.2,31.25,4.62],[1.2,.65,.26],'偏位紫髓额钿')
    b.box('void_crown_pin','head','void_silver',[0,33.0,-1.4],[10.1,.35,.42],'贯冠细银横簪')
    # One open concave polygon provides a genuine gap; no solid background panel.
    contour=[(3,29),(-4,29),(-9,24),(-9,14),(-3,8),(4,8),(9,13),(9,23),(6,26),
             (4,24),(6,21),(6,15),(3,11),(-2,11),(-6,15),(-6,23),(-3,26),(3,26)]
    b.panel('void_open_bi','chest','void_jade',contour,pos=(0,0,-6.1),depth=1.7,
            name='单件缺角八折玉璧，内孔与右上缺口均为真实镂空')
    face=[(2.4,28.55),(-3.8,28.55),(-8.5,23.8),(-8.5,14.2),(-2.8,8.5),(3.8,8.5),
          (8.5,13.2),(8.5,22.8),(6,25.35),(4.75,24),(6.5,21.2),(6.5,14.8),
          (3.2,10.5),(-2.2,10.5),(-6.5,14.8),(-6.5,23.2),(-3.2,26.5),(2.4,26.5)]
    b.panel('void_bi_polished_face','chest','void_face',face,pos=(0,0,-7.01),depth=.12,
            name='玉璧背面磨白切面，外壁和内孔保留深青玉胎')
    # Wide cloth approaches the hole from the shoulders, folds through it, then
    # falls behind the bottom rim. The gap beside it stays visibly open.
    b.panel('void_cloud_root','chest','void_deep',[(-4.3,24.3),(4.3,24.3),(3,21.8),(-3.4,20.8)],
            pos=(0,0,-3.25),depth=.65,name='肩背低伏云衣承口')
    b.box('void_cloud_bridge','chest','void_silk',[-1.4,22.1,-4.8],[5.8,1.4,4.5],
          name='宽幅云衣穿过玉璧内孔的实体折面',rot=(-15,0,0))
    b.panel('void_cloud_fall','chest','void_brocade',[(-4.3,22.2),(1.4,22.2),(2.2,17.2),
            (.8,12.2),(1.8,6.1),(-1.5,3.8),(-5.1,5.1),(-5.6,11.1),(-4.1,16.4)],
            pos=(0,0,-7.5),depth=.45,name='穿璧后垂落的宽幅偏心云衣')
    b.panel('void_cloud_turn','chest','void_deep',[(-4.3,22.2),(-3.6,21.3),(-3.5,16.3),
            (-5,11.1),(-4.5,5.4),(-1.5,4.4),(-1.5,3.8),(-5.1,5.1),(-5.6,11.1),(-4.1,16.4)],
            pos=(0,0,-7.78),depth=.09,name='云衣一侧翻出的深青内衬')
    b.box('void_rear_seal','chest','void_rear_weave',[-1.2,17.8,-7.8],[3.8,5.6,.10],'云衣同底色上的无相银青印绣')
    b.box('void_rear_border','chest','void_border',[-1.6,7.4,-7.81],[5.8,1.55,.08],'云衣下缘定幅银锦')
    # Deliberately sparse chiselled cells on the solid rim, never an all-over grid.
    for x,y in [(-7.55,20.5),(7.5,17),(1.8,9.5)]:
        b.box(f'void_jade_etch_{x}_{y}','chest','void_incised',[x,y,-7.11],[1.2,1.9,.08],'玉璧窄面三处细阴刻')
    b.panel('void_gap_gem','chest','void_amethyst',[(5.4,26.2),(6.1,27.5),(7.2,26.8),(6.5,25.6)],
            pos=(0,0,-6.5),depth=1.4,name='缺口内离座的单枚紫髓，不封闭透空')
    # Front and side hierarchy: neckline, shoulder fold, seal tag and hem.
    b.panel('void_shoulder_fold','chest','void_deep',[(-4.2,24.5),(-8.1,24.5),(-8.8,22),(-4.4,22.7)],
            pos=(0,0,0),depth=5.0,name='左肩浅折云领与寂青内衬')
    b.panel('void_shoulder_face','chest','void_silk',[(-4.2,24.7),(-7.9,24.7),(-8.5,22.9),(-4.4,23.4)],
            pos=(0,0,.12),depth=5.05,name='浅折肩领的月白外面')
    b.box('void_chest_tag','chest','void_seal',[2.1,19.3,2.92],[1.65,3.4,.16],'偏襟右侧窄印锦')
    b.box('void_front_hem','legs','void_border',[-3,5.3,3.13],[3.5,1.4,.09],'左长襟细锦收口')
    b.box('void_right_hem','legs','void_border',[3,6.9,3.13],[3.6,1.35,.09],'右短襟深锦收口')
    for sign in (-1,1):
        b.box(f'void_boot_face_{sign}','boots','void_face',[sign*2.15,2.8,2.83],[2.1,1.9,.22],'步寂履前温玉护面')
    # Keep the upper rim below the back of the head; compress only the jade
    # assembly, while the cloth still physically crosses its aperture.
    for c in s['components']:
        if c['id'] in ('void_open_bi','void_bi_polished_face','void_gap_gem'):
            c['polygon']=[[x,y*.81+2.5/16]for x,y in c['polygon']]
        if c['id'].startswith('void_jade_etch_'):
            c['position'][1]=c['position'][1]*.81+2.5/16
    s['palette']=['#d8e2d7','#35575c','#6c9d96','#bdcdb8','#877eaa']
    s['style']='缺角玉璧 · 穿璧云衣'
    s['tagline']='玉不求圆，云不系身；一口真实的空处，托住化神天衣。'
    s['description']='太虚无相以缺角玉璧和穿璧云衣重新组织整套形制。月白偏襟之下露出寂青内领，左肩只有低伏的翻折云领，袖口、腰封和长短前襟以细银菱目锦收束。正面主次来自乳白、寂青与温玉的面积关系，紫髓仅落在额钿、腰扣和玉璧缺口。背后取消两条窄折帛和普通卷云贴花，改为一件有内孔、厚壁与斜切转角的开口玉璧。宽幅云衣从肩背伸入璧孔，再越过玉面落至小腿；衣幅偏向一侧，另一侧保留能透视的空处。云衣的上段织无相印，下段收菱目锦，中间只留浅色暗花，因此远看先见法相和衣料，近看才见玉胎、磨面、银线和织纹。'
    s['silhouette']='方头、宽臂和短腿仍遵守Minecraft比例。冠从方框改为小型缺角玉冠，横簪压低视觉重心；左右袖和长短襟延续不对称，但胸肩不向外堆高。玉璧宽度略小于阔袖总宽，顶端止于后脑中段，下端落在腰腿之间，不把整个人包进巨大法器。八折外沿、内孔和右上缺口共同构成三维轮廓，侧面能看到玉璧厚度与衣料前后穿行。宽披衣跨过璧孔后偏向左侧下落，与右侧透空形成虚实对照。'
    s['materialStory']='温青玉胎用低对比像素色阶表现厚度，后向磨面偏乳白，内孔与外侧保持深青。几处阴刻内嵌细银，缺口的一枚紫髓与主体留有真实距离。衣料采用月白暗花、寂青内衬和定幅银锦，暗花仅在近处可辨；主印和织边采用独立32像素手绘图，不以噪点或高光代替细节。穿戴端新增材料独立命名，不改变无相凝虚锭及锭用调色数据。锭的缺角语汇在玉璧和冠中变成立体形制。'
    s['progression']='化神身份由完整的玉质法相和衣料穿行关系承担。背部的空处是真实几何，离身厚度可从侧面观察；衣料和法器各有重量与连接位置。其形制区别于星河的整幅星披、九色的琉璃长瀑，以及玄极的垂圭与大日的背莲。前侧仍是一套有领、袖、腰和独立下装的天衣，衣纹按主印、织边、暗花三个尺度排列，保持远近两种阅读层次。'
    s['parts']['head']['description']='低冠保留开放方脸，寂青后脑与侧鬓衬出月白额缘。缺角玉冠有斜折外沿和真实内孔，细银横簪穿过冠座；偏位紫髓额钿呼应背璧缺口。冠不再是高举的方形把手，背面与侧面仍能读到玉胎厚度。'
    s['parts']['chest']['description']='完整方胸背承接暗花偏襟、深青内领和不等宽袖。左肩为低位折领，袖口是细银菱目锦，右襟有一小幅无相印。背部为单件缺角八折玉璧，宽幅云衣从肩背穿过璧孔并在后面下垂，保留可见的深青翻衬、印锦与下缘织边；右侧留空，玉璧磨面、阴刻和紫髓各自有实体厚度。'
    s['parts']['legs']['description']='独立寂青裤腿和完整腰胯内衬保持包覆。前襟一长一短，左幅月白暗花、右幅深青，两个下缘分别收细锦，浅深面积呼应后方披衣。后裾采用低对比云根暗花，取消孤立长折线。腰封与紫玉方扣连接上身和长裳。'
    s['parts']['boots']['description']='沉青低方履以完整靴筒、平实脚背和薄底承托长衣；履前一小块温玉护面延续背璧的磨面材质。原有方折收边和后跟定线保留，靴履的明暗低于胸背主形，方块脚趾与左右独立结构清楚。'
    return s
