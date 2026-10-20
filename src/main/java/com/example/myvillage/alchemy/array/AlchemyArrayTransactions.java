package com.example.myvillage.alchemy.array;

import java.util.*;
import net.minecraft.world.item.ItemStack;

/** Pure copy-based inventory planning; callers commit exactly once on the server thread. */
public final class AlchemyArrayTransactions {
    private AlchemyArrayTransactions() { }
    public static boolean reservedPresent(List<ItemStack> items,List<ItemStack> reserved) {
        if(items.size()<reserved.size())return false;
        for(int i=0;i<reserved.size();i++)if(reserved.get(i).isEmpty()||!ItemStack.isSameItemSameComponents(reserved.get(i),items.get(i))||items.get(i).getCount()<reserved.get(i).getCount())return false;
        return true;
    }
    public static List<ItemStack> outputPlan(List<ItemStack> outputs,ItemStack result,int maxStackSize) {
        if(result.isEmpty())return null;
        List<ItemStack> next=new ArrayList<>();outputs.forEach(stack->next.add(stack.copy()));
        int left=result.getCount();
        for(int pass=0;pass<2;pass++)for(int i=0;i<next.size()&&left>0;i++) {
            ItemStack target=next.get(i);boolean empty=target.isEmpty();
            if((pass==0&&empty)||(pass==1&&!empty)||(!empty&&!ItemStack.isSameItemSameComponents(target,result)))continue;
            int room=Math.min(maxStackSize,result.getMaxStackSize())-(empty?0:target.getCount()),add=Math.min(Math.max(0,room),left);
            if(add>0){if(empty)next.set(i,result.copyWithCount(add));else target.grow(add);left-=add;}
        }
        return left==0?next:null;
    }
}
