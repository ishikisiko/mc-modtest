package com.example.myvillage.forging;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ForgingTagCostsTest {
    @BeforeAll static void bootstrap(){
        if(LoadingModList.get()==null)LoadingModList.of(List.of(),List.of(),List.of(),List.of(),Map.of());
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
    }
    @Test void broadTagCannotStealAnExactIngredientWhenAnotherMemberIsAvailable(){
        // First stack matches both costs, second only the broad tag: greedy tag-first removal fails.
        var remaining=ForgingCosts.allocate(new int[]{2,1},new int[]{1,2},new boolean[][]{{true,true},{true,false}}).orElseThrow();
        assertArrayEquals(new int[]{0,0},remaining);
        assertTrue(ForgingCosts.allocate(new int[]{2},new int[]{1,2},new boolean[][]{{true,true}}).isEmpty());
    }
    @Test void oneTagCanConsumeMultipleDifferentItemStacks(){
        assertArrayEquals(new int[]{0,1},ForgingCosts.allocate(new int[]{1,2},new int[]{2},new boolean[][]{{true},{true}}).orElseThrow());
        assertArrayEquals(new int[]{0},ForgingCosts.allocate(new int[]{Integer.MAX_VALUE},new int[]{Integer.MAX_VALUE},new boolean[][]{{true}}).orElseThrow());
    }
    private static boolean possible(int[] counts,int[] needs,boolean[][] accepts,int requirement){
        if(requirement==needs.length)return true;
        if(needs[requirement]==0)return possible(counts,needs,accepts,requirement+1);
        for(int i=0;i<counts.length;i++)if(counts[i]>0&&accepts[i][requirement]){
            counts[i]--;needs[requirement]--;
            boolean ok=possible(counts,needs,accepts,requirement);
            counts[i]++;needs[requirement]++;
            if(ok)return true;
        }
        return false;
    }
    @Test void allSmallOverlappingTagGraphsAgreeWithExhaustiveAssignments(){
        for(int mask=0;mask<64;mask++)for(int encoded=0;encoded<27;encoded++)for(int a=1;a<=2;a++)for(int b=1;b<=2;b++){
            int[] counts={encoded%3,(encoded/3)%3,encoded/9},needs={a,b};boolean[][] accepts=new boolean[3][2];
            for(int i=0;i<3;i++)for(int j=0;j<2;j++)accepts[i][j]=(mask&(1<<(i*2+j)))!=0;
            var result=ForgingCosts.allocate(counts,needs,accepts);
            assertEquals(possible(counts.clone(),needs.clone(),accepts,0),result.isPresent());
            if(result.isPresent()){
                int[] used=new int[3];for(int i=0;i<3;i++){used[i]=counts[i]-result.get()[i];assertTrue(used[i]>=0&&used[i]<=counts[i]);}
                assertEquals(a+b,Arrays.stream(used).sum());assertTrue(possible(used,needs.clone(),accepts,0));
            }
        }
    }
    @Test void emptyTagsNeverBecomeAirOrFallbackToAConcreteMaterial(){
        var copper=new ItemStack(Items.COPPER_INGOT,4);
        assertFalse(ForgingIngredients.matches("#myvillage:test_missing",copper));
        assertTrue(ForgingIngredients.display("#myvillage:test_missing",List.of(copper)).is(Items.BARRIER));
        assertTrue(ForgingCosts.plan(List.of(copper),Map.of("#myvillage:test_missing",1)).isEmpty());
        assertEquals(4,copper.getCount());
        assertEquals("#myvillage:test_missing",ForgingIngredients.description("#myvillage:test_missing").getString());
    }
    @Test void concreteCostsRemainCompatibleAndTagSyntaxIsBounded(){
        var iron=new ItemStack(Items.IRON_INGOT,3);
        assertTrue(ForgingIngredients.matches("minecraft:iron_ingot",iron));
        assertEquals(1,ForgingCosts.plan(List.of(iron),Map.of("minecraft:iron_ingot",2)).orElseThrow().getFirst().getCount());
        assertEquals(3,iron.getCount());
        for(String bad:List.of("#","##c:ingots/copper","copper","#c:bad name","minecraft:air","#minecraft:air"))
            assertThrows(IllegalArgumentException.class,()->ForgingIngredients.validate(bad));
        assertEquals("#c:ingots/copper",ForgingIngredients.validate("#c:ingots/copper"));
    }
}
