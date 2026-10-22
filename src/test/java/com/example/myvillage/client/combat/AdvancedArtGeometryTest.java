package com.example.myvillage.client.combat;

import com.example.myvillage.combat.art.ArtDefinition;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AdvancedArtGeometryTest {
    @Test void enlargedWeaponCuesStayInsideTheirAuthoredReachAndLineWidth() {
        for(var art:ArtDefinition.all())if(art.slot()>=0)for(var phase:art.phases()) {
            var mesh=ArtRibbonShape.mesh(phase.fx(),phase.reach(),phase.arc(),phase.height(),phase.width(),art.particleCount(),false);
            assertTrue(mesh.size()<=art.particleCount()*3);
            double farthest=0;
            for(var q:mesh)for(var p:List.of(q.a(),q.b(),q.c(),q.d())) {
                assertTrue(Double.isFinite(p.x())&&Double.isFinite(p.y())&&Double.isFinite(p.z()));
                double distance=Math.hypot(p.x(),p.z());farthest=Math.max(farthest,distance);
                assertTrue(distance<=phase.reach()+1e-8,art.id().toString());
                if(phase.shape().equals("line"))assertTrue(Math.abs(p.x())<=phase.width()*.5+1e-8);
            }
            assertEquals(phase.reach(),farthest,1e-8,"The main outline must reach the actual hit boundary");
        }
    }
}
