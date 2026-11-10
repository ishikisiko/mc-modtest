package com.example.myvillage.client.alchemy;
import com.example.myvillage.alchemy.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
public final class RecipeText {
    private RecipeText(){}
    public static Component name(AlchemyRules.Recipe r){
        Component item=BuiltInRegistries.ITEM.get(ResourceLocation.parse(r.output())).getDescription();
        return item.copy().append(" · 难度"+r.difficulty());
    }
    public static Component tooltip(AlchemyRules.Recipe r){
        MutableComponent text=Component.empty();
        for(int i=0;i<r.ingredients().size();i++)text.append(BuiltInRegistries.ITEM.get(ResourceLocation.parse(r.ingredients().get(i))).getDescription()).append(" ×"+r.count(i)+"\n");
        text.append("炼丹资质 ≥ "+r.requiredAptitude()+" · 难度 "+r.difficulty()+"\n每颗分别判定成败与品质\n");
        return text.append("点击从背包补齐一炉材料与灵石");
    }
}
