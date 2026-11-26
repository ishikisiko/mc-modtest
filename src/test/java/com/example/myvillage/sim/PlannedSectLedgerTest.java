package com.example.myvillage.sim;

import com.example.myvillage.region.runtime.SectSite;
import com.example.myvillage.worldplan.*;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlannedSectLedgerTest {
    @Test void openingAndLaterSimulationKeepFoundingChoicesUnoccupied()throws Exception{
        try(var input=WorldPlanRules.class.getResourceAsStream(WorldPlanRules.RESOURCE)){
            var rules=new WorldPlanRules(new String(Objects.requireNonNull(input).readAllBytes(),StandardCharsets.UTF_8));
            SimData.ResourceOpener opener=p->getClass().getResourceAsStream("/"+p);
            var data=WorldSim.loadData(rules.capitals.lifeOpener(opener));
            for(String tier:List.of("small","medium","large")){
                var plan=new WorldPlan(20261102,rules);var sim=WorldSim.genesis(plan.seed(),plan.graph(),data,tier,24);
                assertEquals(20,sim.sects(false).size());
                for(int i=0;i<120;i++)sim.step(24);
                assertEquals(20,sim.sects(true).size());
                Set<String> occupied=new HashSet<>();
                for(var sect:sim.sects(true)){
                    var site=plan.sectAtGate(sect.gateX(),sect.gateZ()).orElseThrow();
                    assertEquals(sect.regionId(),site.regionId());assertTrue(occupied.add(site.id()));
                    assertFalse(site.foundingOnly());
                }
                var first=sim.sects(false).getFirst();byte[] before=sim.toBytes();
                assertThrows(IllegalArgumentException.class,()->sim.moveGate(first.id(),first.gateX()+1,first.gateZ()));
                assertArrayEquals(before,sim.toBytes());
            }
        }
    }

    @Test void allPresetsShareHeritageThroughTheMainSectAndKeepItAfterReload() throws Exception {
        for (String preset : List.of("cultivation", "cultivation_medium", "cultivation_small")) {
            var rules = presetRules(preset);
            var plan = new WorldPlan(0,rules);
            var data = WorldSim.loadData(rules.capitals.lifeOpener(path->getClass().getResourceAsStream("/"+path)));
            var sim = WorldSim.genesis(plan.seed(),plan.graph(),data,"small",24);
            assertEquals(80,sim.overview(24).population());
            assertEquals(20,sim.sects(false).size());
            assertEquals(3,plan.sectSites().stream().filter(SectSite::foundingOnly).count());
            Map<Integer,SectSite> sites = new HashMap<>();
            for (var sect : sim.sects(false)) {
                assertTrue(sect.memberCount()>=3);
                sites.put(sect.id(),plan.sectAtGate(sect.gateX(),sect.gateZ()).orElseThrow());
            }
            Set<Integer> roots = new HashSet<>();
            Set<String> heritages = new HashSet<>();
            int groups = 0;
            for (var branch : sim.sects(false)) {
                if (!branch.tierId().equals("branch") || branch.parentSectId()<0) continue;
                var headquarters = sim.sect(branch.parentSectId()).orElseThrow();
                var main = sim.sect(headquarters.parentSectId()).orElseThrow();
                assertEquals("headquarters",headquarters.tierId());
                assertEquals("main",main.tierId());
                assertTrue(roots.add(main.id()));
                assertFalse(main.heritageId().isEmpty());
                assertTrue(heritages.add(main.heritageId()));
                assertEquals(main.heritageId(),branch.heritageId());
                assertEquals(main.heritageId(),headquarters.heritageId());
                assertEquals(main.signatureTechniqueId(),branch.signatureTechniqueId());
                assertTrue(headquarters.name().startsWith(main.name()+"·"));
                assertTrue(branch.name().startsWith(main.name()+"·"));
                assertTrue(main.foundedDay()<headquarters.foundedDay()&&headquarters.foundedDay()<branch.foundedDay());
                assertEquals(sites.get(main.id()).id(),sites.get(headquarters.id()).parentSiteId());
                assertEquals(sites.get(headquarters.id()).id(),sites.get(branch.id()).parentSiteId());
                var family = rules.sects.layout(sites.get(main.id()).blueprintId()).family;
                for (int id : List.of(branch.id(),headquarters.id()))
                    assertEquals(family,rules.sects.layout(sites.get(id).blueprintId()).family);
                var raw=JsonParser.parseString(new String(sim.toBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
                for(var element:raw.getAsJsonArray("sects")){
                    var row=element.getAsJsonObject();
                    if(row.get("id").getAsInt()==branch.id()){
                        assertEquals(main.id(),row.get("heritage_root").getAsInt());
                        assertTrue(row.get("heritage").getAsString().isEmpty());
                    }
                }
                groups++;
            }
            assertEquals(2,groups);
            assertArrayEquals(sim.toBytes(),WorldSim.fromBytes(sim.toBytes(),plan.graph(),data).toBytes());

            var branch = sim.sects(false).stream().filter(s->s.tierId().equals("branch")&&s.parentSectId()>=0).findFirst().orElseThrow();
            var headquarters = sim.sect(branch.parentSectId()).orElseThrow();
            var main = sim.sect(headquarters.parentSectId()).orElseThrow();
            String player = "10000000-0000-0000-0000-000000000001";
            sim.joinSect(player,"藏书测试",branch.id(),new PlayerQualification("mortal",0,false,0),true);
            assertTrue(sim.borrowable(player).contains(main.basicTechniqueId()));
            sim.destroySect(main.id());
            sim.destroySect(headquarters.id());
            assertFalse(sim.lostHeritageIds().contains(main.heritageId()));
            var restored = WorldSim.fromBytes(sim.toBytes(),plan.graph(),data);
            assertTrue(restored.borrowable(player).contains(main.basicTechniqueId()));
            restored.destroySect(branch.id());
            assertEquals(1,restored.lostHeritageIds().stream().filter(main.heritageId()::equals).count());
        }
    }

    private WorldPlanRules presetRules(String preset) throws Exception {
        String resource="/data/yuanxian/worldgen/world_preset/"+preset+".json";
        try(var stream=getClass().getResourceAsStream(resource)){
            var document=JsonParser.parseString(new String(Objects.requireNonNull(stream).readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
            var generator=document.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld").getAsJsonObject("generator");
            return new WorldPlanRules(FrozenRulesEncoding.decode(generator.get("rules").getAsString()));
        }
    }
}
