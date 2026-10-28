package com.example.myvillage.worldplan;
import com.example.myvillage.region.runtime.RegionQueries;
import com.example.myvillage.worldplan.atlas.WorldAtlasSampler;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ProvinceCapitalV8FocusedTest {
 static WorldPlanRules v8() throws Exception {try(var in=ProvinceCapitalV8FocusedTest.class.getResourceAsStream(WorldPlanRules.V8_RESOURCE)){assertNotNull(in);return new WorldPlanRules(new String(in.readAllBytes(),StandardCharsets.UTF_8));}}
 @Test void seededCitiesKeepOwnershipAndFirstAirAcrossAllEight() throws Exception {
  var rules=v8();assertEquals(192,rules.capitals.lifePlaceLimit);
  for(long seed:new long[]{0,20261029,-42}){
   var plan=new WorldPlan(seed,rules);var again=new WorldPlan(seed,rules);assertEquals(plan.manifest(),again.manifest());assertEquals(8,plan.cities().size());
   assertEquals(Set.of("zhongzhou","donghai","lingyue","ximo","nanjiang","beiyuan","donghuang","moyu"),new HashSet<>(plan.cities().stream().map(c->c.regionId()).toList()));
   assertThrows(IllegalStateException.class,plan::city);
   for(var c:plan.cities()){
    assertEquals(0,Math.floorMod(c.x(),16));assertEquals(0,Math.floorMod(c.z(),16));if(c.regionId().equals("zhongzhou"))assertEquals(0,c.originY());
    int m=c.margin();
    for(int x:new int[]{c.x()-m,c.x(),c.x()+c.bundle().width-1,c.x()+c.bundle().width+m-1})for(int z:new int[]{c.z()-m,c.z(),c.z()+c.bundle().depth-1,c.z()+c.bundle().depth+m-1})assertEquals(c.regionId(),RegionQueries.regionAt(plan.graph(),x,z).orElseThrow(),c.cityId()+" "+x+","+z);
    for(int x:new int[]{0,15,c.bundle().width/2,c.bundle().width-1})for(int z:new int[]{0,15,c.bundle().depth/2,c.bundle().depth-1}){
      int wx=c.x()+x,wz=c.z()+z;assertEquals(c.originY()+c.bundle().topSolid(x,z)+1,plan.surfaceY(wx,wz));assertEquals(c.cityId(),plan.capitalAt(wx,wz).orElseThrow().cityId());assertTrue(plan.reserved(wx,-64,wz));assertTrue(plan.intersectsCity(wx,wz,wx,wz));
    }
    for(var tile:c.bundle().tiles){int wx=c.x()+tile.x(),wz=c.z()+tile.z();assertEquals(tile,c.bundle().tile(Math.floorDiv(wx,16)*16-c.x(),Math.floorDiv(wz,16)*16-c.z()).orElseThrow());}
   }
  }
 }
 @Test void changedManifestAndWrongAssetHashFailClosed() throws Exception {
   var b=v8().capitals.bundles.getFirst();String path="/data/myvillage/"+b.resource.substring("myvillage:".length());
   try(var in=getClass().getResourceAsStream(path)){var root=JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();b.requireManifest(root);root.getAsJsonObject("placement").addProperty("anchor_y",42);assertThrows(IllegalStateException.class,()->b.requireManifest(root));}
   assertThrows(IllegalStateException.class,()->com.example.myvillage.worldplan.capital.CapitalGameBundle.requireHash(new byte[]{1},"0".repeat(64),"tamper"));
 }
 @Test void atlasHasEightCityMarkers() throws Exception {var plan=new WorldPlan(20261029,v8());var map=WorldAtlasSampler.capture(plan);assertEquals(8,map.cities().size());assertEquals(8,map.regions().size());assertEquals(plan.fingerprint(),map.fingerprint());}
}
