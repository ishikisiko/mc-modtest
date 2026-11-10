package com.example.myvillage.alchemy;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** A failed pill creates no stack; successful pills are grouped by their independently rolled quality. */
public final class AlchemyOutputs {
    private AlchemyOutputs(){}
    public static List<ItemStack> of(AlchemyRules.Recipe recipe,AlchemyBatch batch){
        if(!recipe.acceptsBatch(batch))throw new IllegalArgumentException("Wrong alchemy batch");
        var result=new ArrayList<ItemStack>();
        for(int quality=1;quality<=4;quality++){
            int count=batch.count(quality);if(count==0)continue;
            var stack=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(recipe.output())),count);
            PillQuality.apply(stack,quality);result.add(stack);
        }
        return List.copyOf(result);
    }
}
