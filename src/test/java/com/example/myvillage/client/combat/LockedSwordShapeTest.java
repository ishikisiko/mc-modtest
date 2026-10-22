package com.example.myvillage.client.combat;
import com.example.myvillage.combat.art.ArtDefinition;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LockedSwordShapeTest {
    @Test void everyCueHasBoundedFiniteSwordGeometry() {
        for(var art:ArtDefinition.all())art.lock().ifPresent(lock->{
            for(boolean warning:new boolean[]{true,false})for(int age:new int[]{0,lock.firstStrike(),lock.lastStrike()}) {
                var mesh=LockedSwordShape.mesh(lock,warning,age);assertFalse(mesh.isEmpty());assertTrue(mesh.size()<100);
                for(var quad:mesh)for(var p:java.util.List.of(quad.a(),quad.b(),quad.c(),quad.d())) {
                    assertTrue(Double.isFinite(p.x())&&Double.isFinite(p.y())&&Double.isFinite(p.z()));
                    assertTrue(Math.abs(p.x())<=8 && Math.abs(p.z())<=8 && p.y()>=0 && p.y()<=5);
                }
            }
        });
    }
}
