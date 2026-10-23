package com.example.myvillage.story;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.*;
import net.neoforged.fml.loading.LoadingModList;
import java.util.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StoryRewardsTest {
    @BeforeAll static void bootstrap(){
        if(LoadingModList.get()==null)LoadingModList.of(List.of(),List.of(),List.of(),List.of(),Map.of());
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
    }
    @Test void fullInventoryDoesNotMutateAnythingAndKeepsClaimPending() {
        var original=List.of(new ItemStack(Items.COBBLESTONE,64));var rewards=List.of(new StoryDefinition.Reward("minecraft:apple",1));
        var plan=StoryRewards.plan(original,rewards);assertFalse(plan.ready());assertEquals(64,original.get(0).getCount());assertTrue(original.get(0).is(Items.COBBLESTONE));
        var pending=StoryTestData.run("accept","commission","inspect_water_sample","inspect_withered_crop","choose_medical","examine_sample","read_record","return_zhou_he","hear_warden","inspect_recut_array","inspect_fresh_cut","inspect_old_array_text","regulate_spring");
        assertEquals("report",pending.stage());assertTrue(pending.receipts().isEmpty());
    }
    @Test void allRewardsMustFitTogetherAndOriginalStacksAreUntouched() {
        var input=List.of(new ItemStack(Items.APPLE,63),ItemStack.EMPTY);
        var rewards=List.of(new StoryDefinition.Reward("minecraft:apple",2),new StoryDefinition.Reward("minecraft:bread",1));
        assertFalse(StoryRewards.plan(input,rewards).ready());assertEquals(63,input.get(0).getCount());assertTrue(input.get(1).isEmpty());
        var success=StoryRewards.plan(List.of(new ItemStack(Items.APPLE,63),ItemStack.EMPTY,ItemStack.EMPTY),rewards);
        assertTrue(success.ready());assertEquals(64,success.items().get(0).getCount());assertEquals(1,success.items().get(1).getCount());assertTrue(success.items().get(2).is(Items.BREAD));
    }
}
