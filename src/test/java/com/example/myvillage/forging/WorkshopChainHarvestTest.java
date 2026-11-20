package com.example.myvillage.forging;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class WorkshopChainHarvestTest {
    @Test void readFiveRealmDefaultsAndScaleConfiguredLimits() {
        var realms=List.of("qi_refining","foundation_establishment","golden_core","nascent_soul","spirit_transformation");
        int[] intervals={10,6,4,2,1};
        for (int rank=0;rank<realms.size();rank++){
            assertEquals(rank,WorkshopChainConfig.rank("yuanxian:"+realms.get(rank)));
            assertEquals(new WorkshopChainConfig.PickaxeLimits(3+3*rank,8<<rank),WorkshopChainConfig.pickaxe(rank));
            assertEquals(intervals[rank],WorkshopChainConfig.axeInterval(rank));
        }
        assertEquals(new WorkshopChainConfig.PickaxeLimits(17,45),WorkshopChainConfig.pickaxe(2,3,7,5,3));
        assertEquals(-1,WorkshopChainConfig.rank("yuanxian:unsupported"));
    }

    @Test void oreSearchUsesConnectedNeighboursAndLimitsIncludingTheInitialBlock() {
        var origin=BlockPos.ZERO;
        var network=Set.of(origin.east(),origin.east(2),origin.east(3),origin.east(4),origin.west(2));
        var broken=new HashSet<BlockPos>();
        assertEquals(3,WorkshopHarvestSearch.mine(origin,new WorkshopChainConfig.PickaxeLimits(3,3),
                p -> network.contains(p)&&broken.add(p)));
        assertEquals(Set.of(origin.east(),origin.east(2)),broken);
        broken.clear();
        assertEquals(4,WorkshopHarvestSearch.mine(origin,new WorkshopChainConfig.PickaxeLimits(3,10),
                p -> network.contains(p)&&broken.add(p)));
        assertEquals(Set.of(origin.east(),origin.east(2),origin.east(3)),broken);
        assertEquals(1,WorkshopHarvestSearch.mine(origin,new WorkshopChainConfig.PickaxeLimits(6,10),
                p -> !p.equals(origin.east())&&network.contains(p)));
    }

    @Test void treesKeepTouchingBranchesWithTheirOwnRoot() {
        var root=BlockPos.ZERO;
        var neighbor=root.east(4);
        var branch=root.above(2).east();
        var neighborBranch=neighbor.above(2).west();
        var bridge=root.above(2).east(2);
        var forest=Set.of(root,root.above(),root.above(2),branch,bridge,neighborBranch,
                neighbor,neighbor.above(),neighbor.above(2));
        assertEquals(Set.of(root.above(),root.above(2),branch),Set.copyOf(WorkshopHarvestSearch.tree(root,
                forest::contains,Set.of(root,neighbor)::contains,p -> p.getY()==2,false)));
        assertEquals(List.of(),WorkshopHarvestSearch.tree(root,forest::contains,
                Set.of(root,neighbor)::contains,p -> false,false));
    }

    @Test void giantTreeFellsAllTrunksAndContinuesFromAHighBranch() {
        var base=BlockPos.ZERO;
        var roots=Set.of(base,base.east(),base.south(),base.east().south());
        var tree=new HashSet<BlockPos>();
        for (BlockPos root:roots) for(int y=0;y<12;y++) tree.add(root.above(y));
        var first=base.above(11);
        assertEquals(47,WorkshopHarvestSearch.tree(first,tree::contains,roots::contains,p -> p.getY()==11,true).size());
    }

    @Test void infusionUsesOneRecipeMaterialAndKeepsSpecialtyCatalysts() {
        var rules=ForgingRules.get();
        String spirit=WorkshopRules.get().spirit_ingredient();
        Map<String,String> families=Map.of("forged","minecraft:iron_ingot","zhuji_baiqing_lingyun",
                "yuanxian:zhuji_baiqing_lingyun_ingot","jindan_jinwu","yuanxian:jindan_jinwu_ingot");
        families.forEach((prefix,ingot) -> {
            for (String type : WorkshopRules.TOOL_KINDS){
                var recipe=rules.recipe(prefix+"_"+type).orElseThrow();
                assertEquals(Map.of(ingot,1,spirit,2),WorkshopRules.get().infusionCosts(recipe,"tough"));
            }
        });
        var sword=rules.recipe("jindan_jinwu_sword").orElseThrow();
        String ingot="yuanxian:jindan_jinwu_ingot";
        assertEquals(Map.of(ingot,1,spirit,2),WorkshopRules.get().infusionCosts(sword,"channeling"));
        assertEquals(Map.of(ingot,1,spirit,2,"#yuanxian:forging_materials/edge_tempering_sand",1),
                WorkshopRules.get().infusionCosts(sword,"sharp"));
    }
}
