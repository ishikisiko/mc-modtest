package com.example.myvillage.forging;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Plans against copies. No partial consumption, including when two costs name the same item. */
public final class ForgingCosts {
    private ForgingCosts(){}
    public static Optional<List<ItemStack>> plan(List<ItemStack> inventory,Map<String,Integer> costs){
        List<ItemStack> result=new ArrayList<>();inventory.forEach(s->result.add(s.copy()));
        for(var cost:costs.entrySet()){
            if(cost.getValue()<=0)throw new IllegalArgumentException("Nonpositive cost");
            int left=cost.getValue();
            for(var stack:result){
                if(!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(cost.getKey())){
                    int take=Math.min(left,stack.getCount());stack.shrink(take);left-=take;
                }
                if(left==0)break;
            }
            if(left!=0)return Optional.empty();
        }
        return Optional.of(List.copyOf(result));
    }
}
