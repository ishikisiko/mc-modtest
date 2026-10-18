package com.example.myvillage.worldplan;

import com.example.myvillage.worldplan.atlas.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class WorldAtlasTest {
    @Test void frozenVersionsMatchTheirOwnPlannerWithoutChangingThePlan() throws Exception {
        for(int version:new int[]{1,3,4}){
            String path="/data/myvillage/worldplan/frozen_v"+version+".json";
            try(var in=getClass().getResourceAsStream(path)){
                assertNotNull(in);var plan=new WorldPlan(20261017,new WorldPlanRules(new String(in.readAllBytes(),StandardCharsets.UTF_8)));
                String before=plan.manifest().toString();var atlas=WorldAtlasSampler.capture(plan);byte[] cells=atlas.cells();
                assertEquals(plan.fingerprint(),atlas.fingerprint());assertEquals(before,plan.manifest().toString());
                assertEquals(plan.regions().size(),atlas.regions().size());
                for(int z=0;z<256;z+=17)for(int x=0;x<256;x+=17){
                    int wx=(int)Math.round(-atlas.radius()+x*2.0*atlas.radius()/255),wz=(int)Math.round(-atlas.radius()+z*2.0*atlas.radius()/255);
                    var c=plan.sample(wx,wz);int cell=cells[z*256+x]&255;
                    assertEquals(c.surfaceY()<plan.rules().seaLevel,(cell&128)!=0);
                    assertEquals(c.core(),(cell&127)!=0);
                    if(c.core())assertEquals(c.regionId(),atlas.regions().get((cell&127)-1).id());
                }
            }
        }
    }
    @Test void malformedAndOversizedSnapshotsFailClosed() {
        var region=new WorldAtlas.Region("test","中州",0,0);String grid=Base64.getEncoder().encodeToString(new byte[65536]);
        assertThrows(IllegalArgumentException.class,()->new WorldAtlas(WorldAtlas.Status.READY,"a".repeat(64),1024,4640,4000,List.of(region),grid));
        assertThrows(IllegalArgumentException.class,()->new WorldAtlas(WorldAtlas.Status.READY,"a".repeat(64),256,4640,4000,List.of(region),"x"));
        byte[] unknown=new byte[65536];unknown[0]=2;
        assertThrows(IllegalArgumentException.class,()->new WorldAtlas(WorldAtlas.Status.READY,"a".repeat(64),256,4640,4000,List.of(region),Base64.getEncoder().encodeToString(unknown)));
        assertThrows(IllegalArgumentException.class,()->new WorldAtlas.Region("x","x",Double.NaN,0));
        assertEquals(WorldAtlas.Status.UNAVAILABLE,WorldAtlas.state(WorldAtlas.Status.UNAVAILABLE).status());
    }
    @Test void panZoomAndLocationAreBoundedAndInvertible() {
        var v=new AtlasViewport();v.scale(2,.3,.4);assertEquals(.3,v.mapX(-1856,4640),1e-10);assertEquals(.4,v.mapZ(-928,4640),1e-10);
        v.pan(-100,100);assertEquals(-1,v.dx());assertEquals(0,v.dy());
        v.reset();assertEquals(1,v.zoom());assertEquals(0,v.dx());
        v.locate(0,0,4640);assertEquals(.5,v.mapX(0,4640),1e-10);assertEquals(.5,v.mapZ(0,4640),1e-10);
        v.scale(100,.5,.5);assertEquals(2.8,v.zoom());v.scale(.0001,.5,.5);assertEquals(1,v.zoom());assertEquals(0,v.dx());
    }
    @Test void approvedFontsAndPaperArePackagedAndRenderHeadlessly() {
        var painter=new AtlasInkPainter();var paper=painter.paper(true);
        assertEquals(1440,paper.getWidth());assertEquals(1024,paper.getHeight());
        assertEquals(0,paper.getRGB(0,0)>>>24);assertEquals(255,paper.getRGB(100,100)>>>24);
        assertNotEquals(paper.getRGB(1100,150),paper.getRGB(1160,150));
        assertEquals(220,painter.label("中州").getWidth());
        var ruler=painter.scaleLabel(4640,true);assertEquals(220,ruler.getWidth());
        assertTrue(java.util.stream.IntStream.range(0,220*60).anyMatch(i->(ruler.getRGB(i%220,i/220)>>>24)!=0));
    }
}
