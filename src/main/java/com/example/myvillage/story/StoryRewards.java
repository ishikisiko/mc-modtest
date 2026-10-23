package com.example.myvillage.story;

import java.util.*;
import net.minecraft.world.item.ItemStack;
import com.example.myvillage.sim.runtime.commerce.CommerceInventory;

/** Never mutates the input. Rewards use ordinary existing items, with no currency side channel. */
public final class StoryRewards {
    public record Plan(boolean ready,List<ItemStack> items) {}
    private StoryRewards() {}
    public static Plan plan(List<ItemStack> original,List<StoryDefinition.Reward> rewards) {
        var copy=new ArrayList<ItemStack>();original.forEach(s->copy.add(s.copy()));
        for(var reward:rewards) {
            ItemStack type=new ItemStack(CommerceInventory.item(reward.item()));int left=reward.count();
            for(var s:copy)if(!s.isEmpty()&&ItemStack.isSameItemSameComponents(s,type)) {
                int n=Math.min(left,Math.max(0,s.getMaxStackSize()-s.getCount()));s.grow(n);left-=n;if(left==0)break;
            }
            for(int i=0;i<copy.size()&&left>0;i++)if(copy.get(i).isEmpty()) {
                int n=Math.min(left,type.getMaxStackSize());copy.set(i,type.copyWithCount(n));left-=n;
            }
            if(left>0)return new Plan(false,List.of());
        }
        return new Plan(true,List.copyOf(copy));
    }
}
