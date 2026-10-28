"""紫霄雷宸整套重作：雷府持仪者，低冠、异袖、护臂与折云披衣。"""
from mc_common import Builder, material


def refine(s):
    if s['id'] != 'jindan_zixiao':
        return s
    # Keep the authored ingot and its material definitions byte-for-byte.
    used={c[k] for c in s['ingot']['components'] for k in
          ('material','topMaterial','edgeMaterial','trimMaterial') if k in c}
    s['materials']={k:v for k,v in s['materials'].items() if k in used}
    m=s['materials'];s['components']=[]
    colors={
        'zx_ink':('玄紫内袍','#2c2d3e','cloth','#444457'),
        'zx_silk':('烟紫法绫','#51465f','cloth','#73617f'),
        'zx_mist':('浅烟紫折领','#7b6e87','cloth','#a49aaf'),
        'zx_steel':('乌银雷钢','#414655','metal','#777f95'),
        'zx_edge':('冷银磨缘','#9ba4b6','trim','#c8ced7'),
        'zx_gold':('雷府沉金','#a28f64','trim','#c5b58b'),
        'zx_crystal':('澄蓝雷髓','#a2b5d8','jade','#dde2eb'),
        'zx_lining':('沉靛内衬','#252c3d','cloth','#474d62'),
    }
    for key,(name,color,kind,accent)in colors.items():m[key]=material(name,color,kind,accent)
    def art(key,name,palette,draw):
        grid=[['0']*32 for _ in range(32)]
        def rect(x,y,w,h,c):
            for yy in range(max(0,y),min(32,y+h)):
                for xx in range(max(0,x),min(32,x+w)):grid[yy][xx]=str(c)
        def line(points,c):
            for (x,y),(xx,yy)in zip(points,points[1:]):
                dx,dy=xx-x,yy-y;n=max(abs(dx),abs(dy))
                for i in range(n+1):rect(round(x+dx*i/max(n,1)),round(y+dy*i/max(n,1)),1,1,c)
        draw(rect,line)
        m[key]=material(name,palette[0],'cloth');m[key]['pixelArt']={'kind':'cloth','mapping':'fit','palette':palette,'rows':[''.join(row)for row in grid]}
    def thunder_cloud(r,l):
        # Offset, branching calligraphy; no paired arches that read as eyes.
        l([(4,9),(4,7),(8,3),(15,3),(18,6),(18,8),(14,8),(14,5),(10,5)],1)
        l([(20,4),(25,4),(28,7),(28,10),(25,10)],1)
        l([(7,11),(13,11),(16,9),(22,11)],2)
        l([(12,13),(20,13),(20,15),(15,15),(15,18),(21,18),(21,20),(12,20)],2)
        l([(17,12),(17,22),(14,24)],3)
        l([(9,15),(7,17),(9,19)],1);l([(24,16),(26,18),(24,20)],1)
        for x,y in [(10,8),(23,13),(9,23),(24,24)]:r(x,y,1,1,4)
        l([(5,26),(9,26),(12,28),(20,28),(24,25),(28,25)],1)
        r(11,30,10,1,2)
    def border(r,l):
        r(0,3,32,2,2);r(0,27,32,2,2)
        for x in [0,16]:
            l([(x,20),(x+4,20),(x+4,11),(x+9,11),(x+9,18),(x+13,18),(x+13,8)],1)
            l([(x+1,22),(x+6,22),(x+6,14),(x+7,14)],2)
            r(x+10,22,2,2,3);r(x+1,8,2,2,4)
    def channel(r,l):
        l([(6,3),(3,6),(3,24),(7,28),(24,28),(28,24),(28,8),(24,4)],1)
        l([(10,5),(10,10),(20,10),(20,16),(13,16),(13,24)],2)
        l([(13,6),(13,8),(23,8),(23,19),(16,19),(16,25)],3)
        r(8,23,3,3,4);r(22,23,3,3,4);r(15,3,3,3,4)
    def silk(r,l):
        l([(3,28),(3,21),(6,18),(6,10)],1)
        l([(5,28),(5,22),(8,19),(8,12)],1)
        l([(23,4),(26,7),(26,13),(29,16),(29,24)],1)
        r(11,3,2,1,2);r(19,29,2,1,2)
    art('zx_cloud_weave','雷云偏置敕绣',['#51465f','#63556f','#8d7b98','#a08d70','#b0bfd6'],thunder_cloud)
    art('zx_cuff_weave','沉银折雷细锦',['#343443','#79768a','#a9a7b6','#b19c73','#c8d1e1'],border)
    art('zx_arm_channel','雷钢内嵌导雷槽',['#414655','#687286','#a8b5ca','#d5e0ec','#b6a57b'],channel)
    art('zx_quiet_silk','烟紫暗折法绫',['#51465f','#5c506a','#80718b','#b3a17c','#cbd7e5'],silk)
    b=Builder(s)
    b.cap('zx_ink','zx_edge')
    # A continuous low mountain crown with backward depth, no literal Z antenna.
    b.panel('zx_crown','head','zx_steel',
            [(-3.7,32.4),(-3.7,33.5),(-2.4,35.3),(-.9,34.1),(.3,35.6),
             (1.6,34.1),(3.7,34.7),(3.7,32.4)],pos=(0,0,-1.4),depth=2.1,name='低伏三折雷山冠')
    b.panel('zx_crown_face','head','zx_edge',
            [(-3.3,32.9),(-3.3,33.5),(-2.35,34.7),(-.85,33.5),(.3,35),
             (1.65,33.5),(3.3,34.1),(3.3,32.9)],pos=(0,0,-.28),depth=.12,name='折山冠窄银切面')
    b.box('zx_brow','head','zx_crystal',[0,31.25,4.61],[1.35,.65,.28],'小幅澄蓝额心')
    b.box('zx_crown_pin','head','zx_gold',[0,32.9,-2.1],[9.4,.32,.45],'低位贯冠沉金簪')
    b.box('zx_cap_rear','head','zx_steel',[0,28.7,-4.57],[2.6,3.7,.16],'后脑收束雷钢面')
    b.box('zx_rear_mark','head','zx_edge',[0,28.7,-4.69],[.3,2.2,.08],'冠后细银定线')
    b.box('zx_body','chest','zx_ink',[0,18,0],[8.55,12.4,4.75],'完整玄紫胸背')
    b.panel('zx_front_wrap','chest','zx_quiet_silk',
            [(-4.2,23.8),(-1.6,23.8),(3.9,19.1),(4.2,13),(-4.2,13)],
            pos=(0,0,2.57),depth=.46,name='烟紫完整斜交外襟')
    b.panel('zx_inner_collar','chest','zx_mist',[(-3.4,24.1),(-1.8,24.1),(1.2,20.7),(3.6,23.7),
            (4.1,23.4),(1.25,19.8)],pos=(0,0,2.85),depth=.18,name='浅烟紫内领衬面')
    b.bar('zx_cross_edge','chest','zx_edge',[-3.65,23.8,3.0],[3.25,13.4,3.0],.22,.15,'窄银斜襟收边')
    b.bar('zx_right_edge','chest','zx_edge',[3.7,23.8,2.98],[1.3,20.7,2.98],.22,.15,'窄银短领缘')
    b.box('zx_collar_shadow','chest','zx_lining',[0,23.1,2.6],[3.5,1.6,.25],'交领内的深色留白')
    b.panel('zx_wide_sleeve','chest','zx_quiet_silk',
            [(-4,24.3),(-8.2,24.3),(-9.6,17),(-9.1,15),(-4,15)],depth=4.9,name='左侧完整宽法袖')
    b.box('zx_left_cuff','chest','zx_cuff_weave',[-6.6,15.8,0],[5.4,1.5,5.04],'宽法袖沉银细锦口')
    b.panel('zx_left_shoulder','chest','zx_silk',[(-4,24.7),(-8.3,24.7),(-8.8,22.5),(-4.2,22.9)],
            pos=(0,0,-.1),depth=5.1,name='低伏左肩整幅折云领')
    b.box('zx_right_sleeve','chest','zx_lining',[6.12,19.6,0],[4.55,9.4,4.7],'右侧完整窄执雷袖')
    b.panel('zx_right_shoulder','chest','zx_steel',[(4.1,25),(7.9,25),(9,23.2),(8.2,21.9),(4.1,23)],
            pos=(0,0,-.1),depth=5.2,name='右肩单件低位折雷护肩')
    b.panel('zx_right_shoulder_face','chest','zx_edge',[(4.4,24.8),(7.8,24.8),(8.6,23.4),(8.2,23.0),(7.6,24.0),(4.4,24.0)],
            pos=(0,0,2.57),depth=.16,name='护肩上缘窄磨面')
    b.panel('zx_gauntlet','chest','zx_steel',[(4.25,21.4),(7.9,21.1),(8.5,18.1),(7.8,15),(4.55,15)],
            pos=(0,0,2.6),depth=.72,name='右臂完整收腕雷钢护面')
    b.panel('zx_gauntlet_ridge','chest','zx_arm_channel',[(5.15,20.5),(7.25,20.5),(7.7,18),(7.1,15.7),(5.4,15.7),(4.95,18)],
            pos=(0,0,3.12),depth=.7,name='有厚度与内嵌导雷槽的收尖护臂中脊')
    b.box('zx_right_cuff','chest','zx_cuff_weave',[6.1,15.35,0],[4.7,.75,4.9],'右腕窄锦口')
    b.panel('zx_chest_clasp','chest','zx_steel',[(1.65,20.6),(3.1,20.6),(3.3,18.1),(2.4,17.4),(1.55,18.1)],
            pos=(0,0,3.03),depth=.38,name='右襟小型敕印扣座')
    b.box('zx_clasp_core','chest','zx_crystal',[2.42,19.2,3.27],[.55,1.15,.18],'敕印扣中的冷色雷髓')
    b.box('zx_waist','legs','zx_lining',[0,12.65,0],[8.8,1.5,5.0],'深色束腰')
    b.box('zx_belt_weave','legs','zx_cuff_weave',[0,12.8,2.59],[8.7,.85,.14],'腰前细银锦')
    b.panel('zx_buckle','legs','zx_steel',[(-.9,13.4),(.9,13.4),(1.2,12.65),(.65,12),(-.65,12),(-1.2,12.65)],
            pos=(0,0,2.8),depth=.42,name='低扁折角雷府束扣')
    b.box('zx_buckle_core','legs','zx_gold',[0,12.7,3.04],[.6,.6,.12],'腰心唯一沉金方印')
    b.box('zx_hip','legs','zx_lining',[0,11.5,0],[8.35,2.2,4.5],'完整腰胯内衬')
    for sign in (-1,1):
        b.box(f'zx_pants_{sign}','legs','zx_lining',[sign*2.04,8.3,0],[4.15,8.0,4.4],'两条独立完整方裤腿')
        b.panel(f'zx_side_robe_{sign}','legs','zx_silk',[(-2.5,12.7),(2.5,12.7),(3.0,5.9),(-2.8,6.4)],
                pos=(sign*4.4,0,0),depth=.45,rot=(0,90,0),name='接合前后衣幅的整侧裾')
    b.panel('zx_front_long','legs','zx_quiet_silk',[(-4.25,12.7),(-.3,12.7),(-.65,3.9),(-4.9,4.6)],
            pos=(0,0,2.76),depth=.5,name='左侧收尖长前襟')
    b.panel('zx_front_short','legs','zx_ink',[(.4,12.7),(4.25,12.7),(4.8,6.1),(1.1,5.8)],
            pos=(0,0,2.78),depth=.5,name='右侧短襟显露执雷步势')
    b.box('zx_front_hem','legs','zx_cuff_weave',[-2.75,5.4,3.07],[3.65,1.1,.08],'长襟细锦收边')
    b.box('zx_short_hem','legs','zx_cuff_weave',[2.8,6.8,3.09],[3.4,1.0,.08],'短襟细锦收边')
    b.panel('zx_back_skirt','legs','zx_ink',[(-4.3,12.7),(4.3,12.7),(5,5.2),(-5,5.2)],
            pos=(0,0,-2.65),depth=.5,name='披衣内的完整后裾')
    # The cape grows out of a broad shoulder fold, then separates into two wide
    # overlapping cloth fields. It is not a narrow vertical plaque.
    b.panel('zx_back_shawl','chest','zx_steel',[(-7.8,24.5),(-4,25.1),(4.3,25.1),(8.3,24.3),
            (7.2,21.7),(3.5,22.4),(0,21.7),(-3.5,22.4),(-7.2,21.7)],
            pos=(0,0,-3.0),depth=.75,name='肩背相接的宽折雷领')
    b.panel('zx_cape_long','chest','zx_cloud_weave',[(-4.3,23.5),(2.2,23.5),(3.2,18),(1.8,12),
            (2.8,5.2),(-1.7,3.9),(-5.8,5.2),(-5.2,12),(-6.3,18)],
            pos=(0,0,-3.85),depth=.5,name='左长雷云披衣与分尺度敕绣')
    b.panel('zx_cape_short','chest','zx_ink',[(1.8,23.5),(4.4,23.5),(6.2,18.3),(5.4,12.2),(6.5,7.1),(2.7,8.2),(1.8,12)],
            pos=(0,0,-3.6),depth=.5,name='右侧较短的完整折披与深色内衬')
    b.panel('zx_cape_fold','chest','zx_mist',[(-4.3,23.5),(-3.7,23.0),(-5.5,18),(-4.5,12),
            (-5.1,5.5),(-1.7,4.4),(-1.7,3.9),(-5.8,5.2),(-5.2,12),(-6.3,18)],
            pos=(0,0,-4.16),depth=.1,name='披衣左缘真实翻折出的窄浅烟紫里料')
    b.box('zx_cape_short_border','chest','zx_cuff_weave',[4.4,9.3,-3.9],[2.5,1.0,.08],'短披的独立收口')
    for sign in (-1,1):
        x=sign*2.1
        b.box(f'zx_boot_{sign}','boots','zx_lining',[x,2.85,.22],[4.35,5.7,5.05],'深靛完整方履')
        b.box(f'zx_sole_{sign}','boots','zx_ink',[x,.3,.3],[4.5,.6,5.25],'低薄墨底')
        b.panel(f'zx_boot_guard_{sign}','boots','zx_steel',[(x-1.55,5.5),(x+1.55,5.5),(x+1.8,2),(x,1.3),(x-1.8,2)],
                pos=(0,0,2.84),depth=.35,name='完整收尖雷钢履面')
        b.box(f'zx_boot_trim_{sign}','boots','zx_edge',[x,4.75,3.06],[2.6,.28,.1],'履面单道窄银收边')
        b.box(f'zx_boot_rear_{sign}','boots','zx_silk',[x,2.9,-2.38],[2.2,3.8,.12],'靴后烟紫衬面')
    s['palette']=['#2c2d3e','#51465f','#7b6e87','#414655','#9ba4b6','#a2b5d8']
    s['style']='折山低冠 · 雷府战仪'
    s['tagline']='左袖持仪，右臂执雷；雷威收在衣势与钢骨之间。'
    s['description']='紫霄雷宸保留雷府执仪者的理念，整套以玄紫、烟紫和乌银建立统一色域。头顶改成低伏三折雷山冠，窄银切面与冷色额心抬起面部，取消直立的闪电标志。完整烟紫外襟下露出浅烟内领，左侧宽法袖与右侧窄执雷袖仍各司其职，但材质不再像两套衣服拼接。右护肩压低并向后收，完整护臂中央有真实凸起的收尖钢脊，雷髓和导雷槽集中在此。腰带改为深色窄束，长短前襟分别收口，步行开口与方靴清楚。背部由宽折雷领承接两幅宽衣料，左长右短、前后错位；雷云和偏置雷篆按上、中、下三个尺度织入主披，侧边翻出一线浅烟里料。'
    s['silhouette']='整套保留标准方头、宽臂、短身与独立方腿，脸和手保持可见。冠高低于旧折雷天线，肩部围绕颈根形成低而稳的折面。左宽袖与右收腕护臂造成轻重对照，胸襟的斜势由细领缘带出，不再用大块高反差斜色带切开躯干。前襟有真实开衩，后披有肩根、折回和长短落差，背面整体宽于旧直条披风。侧面能读到护臂中脊、雷钢肩领和布料之间的厚度，金丹的威仪保持在人体附近。'
    s['materialStory']='玄紫内袍和烟紫法绫使用低对比暗折像素，乌银雷钢与它们相邻而不抢占大色面。银色只落在冠切面、领缘、肩缘及细锦，沉金集中于腰印与少量绣点。澄蓝雷髓集中在额心、襟扣与右护臂导雷槽，形成少数冷色焦点。雷云偏置敕绣、袖腰折雷细锦、钢脊导雷槽分别为独立三十二像素手绘纹样，避免全身复制一个闪电。原有紫霄雷髓锭及其全部材质保持原样；穿戴端采用独立命名的材料，不回写锭。'
    s['progression']='金丹阶段的仙器感来自法衣与实体护具的结合：持仪侧完整宽袖，执雷侧有压低的护肩和收尖钢脊，背后则是可以看清连接与折面的雷云披衣。能量的视觉位置被集中到护臂与雷纹内，不使用覆盖全身的大光环。相比炼气和筑基，轮廓更有秩序，材质有主从，前后都具有独立构图；同时为元婴与化神的离身法相保留进阶空间。'
    s['parts']['head']={'name':'紫霄折山冠','description':'低位墨紫冠包住后脑与两鬓，方脸完全开放。上方是一件有前后厚度的三折雷山冠，窄银磨面沿山折形成节奏，低位沉金横簪将其压住。额心只用一小幅澄蓝雷髓，后脑钢面与细银线回应正面，不再把闪电图标竖在头顶。'}
    s['parts']['chest']={'name':'雷府异袖战仪','description':'完整玄紫胸背上覆烟紫斜交外襟，浅烟内领和窄银领缘让头胸之间保持清楚层次。左阔法袖的袖口有定幅细锦，右窄袖外是一件收腕雷钢护面与实体中脊，导雷槽集中于中脊。低肩领在背后连续展开，承接两幅宽而有厚度的雷云披衣，左幅有分尺度敕绣和翻衬，右幅较短并独立收口。'}
    s['parts']['legs']={'name':'斜敕分襟云裳','description':'完整腰胯与两条深靛方裤腿保持覆盖。腰束采用深色主体、窄银锦与低扁折角扣，沉金仅作腰心小印。左长烟紫前襟与右短玄紫前襟留出开衩，侧裾接实前后；下缘细锦分别按各自宽度绘制，整衣不再横切成一块斜三角围裙。'}
    s['parts']['boots']={'name':'镇雷乌银履','description':'深靛方靴降低到合适高度，完整乌银履面从上口收向脚背，银色仅留一条窄边。低薄墨底与方形脚趾保持Minecraft体量，靴后烟紫衬面接住后披。靴履支撑整套的暗色下盘，取消醒目的黄色竖条与大蓝色靴帮。'}
    return s
