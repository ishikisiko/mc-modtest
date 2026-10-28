package com.example.myvillage.client.cultivation.motion;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.example.myvillage.client.cultivation.motion.MeditationSurface.Vertex;

class MeditationSurfaceTest {
    @Test void subdivisionPreservesAreaAndUvInterpolation() {
        var result=MeditationSurface.tessellate(List.of(v(0,0),v(4,12),v(0,12)));
        double area=0;
        for(int i=0;i<result.size();i+=3) {
            Vertex a=result.get(i),b=result.get(i+1),c=result.get(i+2);
            area+=Math.abs((b.x()-a.x())*(c.y()-a.y())-(b.y()-a.y())*(c.x()-a.x()))/2;
        }
        assertEquals(24,area,1e-4);
        for(Vertex v:result) {
            assertEquals(v.x()/4,v.u(),1e-6);assertEquals(v.y()/12,v.v(),1e-6);
            assertEquals(-1,v.nz());
        }
        assertTrue(result.stream().anyMatch(v->v.y()==6));
    }
    private static Vertex v(float x,float y){return new Vertex(x,y,0,x/4,y/12,0,0,-1);}
}
