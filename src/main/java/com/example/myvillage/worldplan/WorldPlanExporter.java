package com.example.myvillage.worldplan;

import com.google.gson.JsonObject;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Read-only atlas of the actual generator's terrain field. Never creates game chunks. */
public final class WorldPlanExporter {
    private WorldPlanExporter() {}
    public static void export(WorldPlan plan,Path directory,int resolution,int radius)throws IOException{
        if(resolution<64||resolution>512||radius<1000||radius>100000)throw new IllegalArgumentException("Atlas bounds");
        Files.createDirectories(directory);int n=resolution;
        int[] heights=new int[n*n],regions=new int[n*n],qi=new int[n*n];
        List<WorldPlan.Context> legend=new ArrayList<>();Map<String,Integer> ids=new LinkedHashMap<>();
        BufferedImage terrain=new BufferedImage(n,n,BufferedImage.TYPE_INT_RGB),political=new BufferedImage(n,n,BufferedImage.TYPE_INT_RGB),spirit=new BufferedImage(n,n,BufferedImage.TYPE_INT_RGB);
        for(int z=0;z<n;z++)for(int x=0;x<n;x++){
            int wx=(int)Math.round(-radius+x*2.0*radius/(n-1)),wz=(int)Math.round(-radius+z*2.0*radius/(n-1)),at=z*n+x;
            WorldPlan.Context c=plan.sample(wx,wz);if(!ids.containsKey(c.regionId())){ids.put(c.regionId(),ids.size());legend.add(c);}
            heights[at]=c.surfaceY();regions[at]=ids.get(c.regionId());qi[at]=(int)Math.round(c.qi()*10);
            terrain.setRGB(x,z,terrainColor(c,plan.rules().seaLevel));political.setRGB(x,z,regionColor(c.regionId()));spirit.setRGB(x,z,blend(0x15202d,0x77ebbe,Math.min(1,c.qi()/10)));
        }
        ImageIO.write(terrain,"png",directory.resolve("terrain.png").toFile());ImageIO.write(political,"png",directory.resolve("regions.png").toFile());ImageIO.write(spirit,"png",directory.resolve("qi.png").toFile());
        ByteArrayOutputStream png=new ByteArrayOutputStream();ImageIO.write(terrain,"png",png);
        String image="data:image/png;base64,"+Base64.getEncoder().encodeToString(png.toByteArray());String svg=svg(plan,image,radius);write(directory.resolve("atlas.svg"),svg);
        JsonObject data=new JsonObject();data.addProperty("resolution",n);data.addProperty("radius",radius);data.add("height",WorldPlanRules.JSON.toJsonTree(heights));
        data.add("region",WorldPlanRules.JSON.toJsonTree(regions));data.add("qi",WorldPlanRules.JSON.toJsonTree(qi));data.add("legend",WorldPlanRules.JSON.toJsonTree(legend));
        String raw=WorldPlanRules.JSON.toJson(plan.manifest());write(directory.resolve("worldplan.json"),raw+"\n");
        write(directory.resolve("atlas.html"),html(plan,data,raw,svg));
    }
    private static void write(Path target,String data)throws IOException{
        Path temp=target.resolveSibling(target.getFileName()+".tmp");Files.writeString(temp,data,StandardCharsets.UTF_8);
        try{Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}
    }
    private static int terrainColor(WorldPlan.Context c,int sea){
        if(c.surfaceY()<sea)return blend(0x123448,0x3b7c8d,(c.surfaceY()-20)/45.0);
        int low=switch(c.profileId()){case "ximo","moyu"->0xbca474;case "beiyuan"->0xa9bcae;case "donghai"->0x68936b;default->0x4f7755;};
        return blend(low,0xe0ddd2,(c.surfaceY()-70)/170.0);
    }
    private static int regionColor(String id){long h=com.example.myvillage.region.runtime.RegionGeometry.mix(id.hashCode());return((72+(int)(h&127))<<16)|((72+(int)((h>>>8)&127))<<8)|(72+(int)((h>>>16)&127));}
    private static int blend(int a,int b,double t){t=Math.max(0,Math.min(1,t));int out=0;for(int s:new int[]{16,8,0})out|=((int)Math.round(((a>>>s)&255)*(1-t)+((b>>>s)&255)*t))<<s;return out;}
    private static String xml(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
    private static double map(double v,int radius){return 40+(v+radius)*920/(2.0*radius);}
    private static String point(WorldPlan plan,WorldPlan.Point p,int radius){double[] q=plan.geometry().unwarp(p.x(),p.z());return String.format(Locale.ROOT,"%.2f,%.2f",map(q[0],radius),map(q[1],radius));}
    private static String svg(WorldPlan p,String image,int radius){
        StringBuilder s=new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 1000 1100\" role=\"img\"><rect width=\"1000\" height=\"1100\" fill=\"#14212a\"/><style>text{font-family:system-ui,'Noto Sans CJK SC',sans-serif;fill:#f2e9d2}.label{paint-order:stroke;stroke:#15232b;stroke-width:4px;stroke-linejoin:round}</style>");
        s.append("<image x=\"40\" y=\"40\" width=\"920\" height=\"920\" href=\"").append(image).append("\"/>");
        s.append(String.format(Locale.ROOT,"<circle cx=\"500\" cy=\"500\" r=\"%.2f\" fill=\"none\" stroke=\"#eadfbf\" stroke-dasharray=\"8 6\"/>",p.rules().coreRadius*920.0/(2*radius)));
        for(var e:p.borders()){
            StringBuilder path=new StringBuilder();for(int i=0;i<=24;i++){double t=i/24.0;path.append(i==0?"M":" L").append(point(p,new WorldPlan.Point(e.p().x()+(e.q().x()-e.p().x())*t,e.p().z()+(e.q().z()-e.p().z())*t),radius));}
            String color=e.passable()?"#bac7a3":e.separator().equals("sea")?"#81d2ef":"#ece7ce";
            s.append("<path d=\"").append(path).append("\" stroke=\"").append(color).append("\" fill=\"none\" stroke-opacity=\".75\" stroke-width=\"").append(e.passable()?1:3).append("\"/>");
            if(e.passable()){
                var a=p.regions().stream().filter(r->r.id().equals(e.a())).findFirst().orElseThrow();var b=p.regions().stream().filter(r->r.id().equals(e.b())).findFirst().orElseThrow();
                s.append("<polyline points=\"").append(point(p,new WorldPlan.Point(a.x(),a.z()),radius)).append(" ").append(point(p,e.crossing(),radius)).append(" ").append(point(p,new WorldPlan.Point(b.x(),b.z()),radius)).append("\" fill=\"none\" stroke=\"#edc479\" stroke-width=\"1.5\" stroke-dasharray=\"5 6\"/>");
            }
            if(e.gate()){String[] c=point(p,e.crossing(),radius).split(",");s.append("<circle cx=\"").append(c[0]).append("\" cy=\"").append(c[1]).append("\" r=\"5\" fill=\"#e9a960\"/>");}
        }
        for(var r:p.regions()){double[] q=p.geometry().unwarp(r.x(),r.z());s.append(String.format(Locale.ROOT,"<text class=\"label\" x=\"%.2f\" y=\"%.2f\" text-anchor=\"middle\" font-size=\"19\">%s · %d阶</text>",map(q[0],radius),map(q[1],radius),xml(r.profile().name()),r.tier()));}
        var c=p.city();s.append(String.format(Locale.ROOT,"<rect x=\"%.2f\" y=\"%.2f\" width=\"%.2f\" height=\"%.2f\" fill=\"#f6d274\"/><text class=\"label\" x=\"%.2f\" y=\"%.2f\" font-size=\"12\">云山街区</text>",map(c.x(),radius),map(c.z(),radius),c.width()*920.0/(2*radius),c.depth()*920.0/(2*radius),map(c.x(),radius)+12,map(c.z(),radius)+14));
        s.append(String.format(Locale.ROOT,"<circle cx=\"%.2f\" cy=\"%.2f\" r=\"5\" fill=\"#fff\"/><text class=\"label\" x=\"%.2f\" y=\"%.2f\" font-size=\"12\">初入仙途</text>",map(p.spawn().x(),radius),map(p.spawn().z(),radius),map(p.spawn().x(),radius)+10,map(p.spawn().z(),radius)+4));
        s.append("<text x=\"40\" y=\"994\" font-size=\"25\">修仙大陆 · 种子 ").append(p.seed()).append("</text><text x=\"40\" y=\"1021\" font-size=\"14\">核心半径 ").append(p.rules().coreRadius).append(" 格；边缘渐变接入无限外域。北为 -Z，东为 +X。</text><text x=\"40\" y=\"1045\" font-size=\"13\">实线是州界 / 山海屏障；金色虚线是交通关系，不代表已经铺设的道路。</text><text x=\"40\" y=\"1069\" font-size=\"12\">显示同源地形场，不含树冠、建筑、洞穴或玩家改建。规划：").append(p.fingerprint().substring(0,16)).append("</text></svg>");return s.toString();
    }
    private static String html(WorldPlan p,JsonObject data,String raw,String svg){
        String encoded=WorldPlanRules.JSON.toJson(raw).replace("<","\\u003c"),grid=WorldPlanRules.JSON.toJson(data).replace("<","\\u003c");
        return """
<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>修仙大陆 · 种子蓝图</title>
<style>*{box-sizing:border-box}body{margin:0;background:#101b24;color:#eee6d5;font:15px system-ui,'Microsoft YaHei',sans-serif}header{padding:24px 4vw;border-bottom:1px solid #40515a}h1{margin:0 0 8px}main{display:grid;grid-template-columns:minmax(400px,1fr) 330px;gap:20px;padding:24px 4vw}article svg{width:100%;display:block}aside{padding:20px;background:#1a2a34;border:1px solid #3b505a;border-radius:12px;align-self:start}button{background:#28414c;color:white;padding:10px;border:1px solid #71848b;border-radius:5px}p{line-height:1.7}pre{white-space:pre-wrap;overflow-wrap:anywhere}#tip{min-height:170px;background:#101b24;padding:14px;white-space:pre-wrap}.small{color:#b5c5c7;font-size:13px}@media(max-width:850px){main{display:block;padding:10px}aside{margin:12px}}</style>
<header><h1>修仙大陆 · 天地舆图</h1><span id="summary"></span></header><main><article id="map">
"""+svg+"""
</article><aside><h2>地图采样</h2><p>移动指针查看同源地形场。本图由实际生成器的规划直接导出，不是手工假想地图。</p><div id="tip">请将指针移到地图内。</div><h3>规划地点</h3><pre id="sites"></pre><p>浅色圆线表示核心圈，不是世界边墙。外围继续按坐标稳定生成。金色虚线仅是宏观交通关系，城内街道则会实际放置。</p><p class="small">地形层不含树冠、屋顶、洞穴和玩家改建。高度指采样格的地表第一空气格，不是实时玩家位置。此蓝图不能代替实机验收。</p><button id="save">保存完整规划 JSON</button><p class="small" id="hash"></p></aside></main><script>
const RAW=
"""+encoded+"""
;const A=
"""+grid+"""
;const D=JSON.parse(RAW),SEED='
"""+p.seed()+"""
'.trim();const tip=document.querySelector('#tip'),svg=document.querySelector('#map svg');
document.querySelector('#summary').textContent=`种子 ${SEED} · ${D.graph.count} 个核心州域 · 无限外域 · 预览半径 ${A.radius} 格`;
document.querySelector('#sites').textContent=`云山街区：${D.city.host}\n西北角：${D.city.x}, ${D.city.baseY}, ${D.city.z}\n尺寸：${D.city.width} × ${D.city.depth}\n出生点：${D.spawn.x}, ${D.spawn.y}, ${D.spawn.z}\n出生州：${D.spawn.regionId}`;
document.querySelector('#hash').textContent='规划校验 '+D.plan_sha256;
svg.addEventListener('pointermove',e=>{const p=svg.createSVGPoint();p.x=e.clientX;p.y=e.clientY;const t=p.matrixTransform(svg.getScreenCTM().inverse());if(t.x<40||t.x>960||t.y<40||t.y>960)return;const x=Math.min(A.resolution-1,Math.max(0,Math.round((t.x-40)/920*(A.resolution-1)))),z=Math.min(A.resolution-1,Math.max(0,Math.round((t.y-40)/920*(A.resolution-1)))),i=z*A.resolution+x,r=A.legend[A.region[i]],wx=Math.round(-A.radius+x*2*A.radius/(A.resolution-1)),wz=Math.round(-A.radius+z*2*A.radius/(A.resolution-1));tip.textContent=`${r.displayName}\n坐标 ${wx}, ${wz}\n区域阶层 ${r.tier} · 灵气约 ${(A.qi[i]/10).toFixed(1)}\n地表第一空气格 Y=${A.height[i]}\n${r.core?'核心文明圈':'程序生成外域'}\n精查：/myvillage worldplan at ${wx} ${wz}`;});
document.querySelector('#save').onclick=()=>{const a=document.createElement('a'),u=URL.createObjectURL(new Blob([RAW],{type:'application/json'}));a.href=u;a.download=`worldplan_${SEED}.json`;a.click();setTimeout(()=>URL.revokeObjectURL(u),1000);};
</script></html>
""";
    }
}
