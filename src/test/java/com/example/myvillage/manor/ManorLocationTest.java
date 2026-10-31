package com.example.myvillage.manor;

import com.example.myvillage.worldplan.WorldPlanRules;
import com.example.myvillage.worldplan.capital.*;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ManorLocationTest {
    private static WorldPlanRules rules() {
        return new WorldPlanRules(new String(CapitalGameBundle.readResource("myvillage:worldplan/frozen_v11.json",8*1024*1024),StandardCharsets.UTF_8));
    }
    @Test void allSevenManorsUseTheActualRotatedAndVerticallyClippedConstructionBounds() {
        int total=0;
        for(var bundle:rules().capitals.bundles) {
            if(bundle.regionId.equals("zhongzhou")) { assertTrue(bundle.manorSite.isEmpty());continue; }
            var source=JsonParser.parseString(new String(CapitalGameBundle.readResource(bundle.resource,8*1024*1024),StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("blueprint").getAsJsonObject("manor");
            var bounds=source.getAsJsonArray("bounds");var origin=source.getAsJsonArray("origin");var size=source.getAsJsonArray("size");
            var site=bundle.manorSite.orElseThrow();
            assertEquals(bounds.get(0).getAsInt(),site.x());assertEquals(bounds.get(1).getAsInt(),site.z());
            assertEquals(bounds.get(2).getAsInt()+1,site.x()+site.width());assertEquals(bounds.get(3).getAsInt()+1,site.z()+site.depth());
            assertEquals(Math.max(0,origin.get(1).getAsInt()),site.y());
            assertEquals(Math.min(bundle.height,origin.get(1).getAsInt()+size.get(1).getAsInt()),site.y()+site.height());
            if(bundle.regionId.equals("donghai"))assertEquals(size.get(2).getAsInt(),site.width());
            if(bundle.regionId.equals("moyu")) {assertTrue(origin.get(1).getAsInt()<0);assertEquals(0,site.y());}
            total++;
        }
        assertEquals(7,total);
    }
    @Test void movingComponentsOutsideTheManorOrToAnotherCityRemovesTheOwnershipLabel() {
        for(var bundle:rules().capitals.bundles) {
            if(bundle.manorSite.isEmpty())continue;
            var city=new CapitalCity(bundle,1232,64,-1968,0);var site=bundle.manorSite.orElseThrow();
            var min=new BlockPos(city.x()+site.x(),city.originY()+site.y(),city.z()+site.z());
            var max=min.offset(site.width()-1,site.height()-1,site.depth()-1);
            assertTrue(ManorLocation.contains(city,min,bundle.regionId));assertTrue(ManorLocation.contains(city,max,bundle.regionId));
            for(var outside:java.util.List.of(min.west(),min.below(),min.north(),max.east(),max.above(),max.south()))
                assertFalse(ManorLocation.contains(city,outside,bundle.regionId));
            assertFalse(ManorLocation.contains(city,min,"another_city"));
        }
    }
}
