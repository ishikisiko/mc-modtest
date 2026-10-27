package com.example.myvillage.forging;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InfusionVisualsTest {
    @Test void geometryStaysNearTheDingAndConvergesWithoutChangingTheItem(){
        var rules=InfusionVisuals.get();assertEquals(0,rules.visibility(0));assertEquals(0,rules.visibility(1));
        assertEquals(rules.item_height(),rules.height(0));assertEquals(rules.item_height(),rules.height(1));
        for(int tick=0;tick<=100;tick++)for(int stream=0;stream<rules.streams();stream++)for(int step=0;step<=rules.segments();step++){
            var p=rules.stream(tick/100.0,step/(double)rules.segments(),stream);
            assertTrue(Math.hypot(p[0],p[2])<=rules.ring_radius()+1e-6);assertTrue(p[1]>=rules.hearth_height()&&p[1]<=rules.item_height()+rules.lift()+1e-6);
        }
        var end=rules.stream(1,1,0);assertEquals(0,Math.hypot(end[0],end[2]),1e-9);
    }
    @Test void everySupportedCraftHasAnOwnedModelPose(){for(var craft:WorkshopRules.get().crafts())assertNotNull(InfusionVisuals.get().items().get(craft.recipe()),craft.recipe());}
}
