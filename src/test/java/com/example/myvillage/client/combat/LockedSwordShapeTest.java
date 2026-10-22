package com.example.myvillage.client.combat;
import com.example.myvillage.combat.art.ArtDefinition;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LockedSwordShapeTest {
    @Test void everyCueHasBoundedFiniteSwordGeometry() {
        for(var art:ArtDefinition.all())art.lock().ifPresent(lock->{
            for(boolean warning:new boolean[]{true,false})for(int age:new int[]{0,lock.firstStrike(),lock.lastStrike()}) {
                var mesh=LockedSwordShape.mesh(lock,warning,age);assertFalse(mesh.isEmpty());assertTrue(mesh.size()<400);
                for(var quad:mesh)for(var p:java.util.List.of(quad.a(),quad.b(),quad.c(),quad.d())) {
                    assertTrue(Double.isFinite(p.x())&&Double.isFinite(p.y())&&Double.isFinite(p.z()));
                    assertTrue(Math.abs(p.x())<=Math.max(lock.radius(),lock.visual().swordHalfWidth()*2.7)+1e-8
                            && Math.abs(p.z())<=Math.max(lock.radius(),lock.visual().pierceLength())+1e-8
                            && p.y()>=0 && p.y()<=lock.visual().swordTop()+lock.visual().swordHalfWidth()*1.8+1e-8);
                }
            }
        });
    }
}
