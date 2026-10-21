package com.example.myvillage.client.alchemy;
import com.example.myvillage.alchemy.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
public final class RecipeText {
    private RecipeText(){}
    public static Component name(AlchemyRules.Recipe r){
        Component item=BuiltInRegistries.ITEM.get(ResourceLocation.parse(r.output())).getDescription();
        return r.fixedQuality()>0?PillQuality.name(r.fixedQuality()).copy().append(item):item;
    }
    public static Component tooltip(AlchemyRules.Recipe r){
        MutableComponent text=Component.empty();
        for(int i=0;i<r.ingredients().size();i++)text.append(BuiltInRegistries.ITEM.get(ResourceLocation.parse(r.ingredients().get(i))).getDescription()).append(" ×"+r.count(i)+"\n");
        if(r.fixedQuality()>0)text.append("炼丹资质 ≥ "+r.minimumAptitude()+" · 固定产出 ").append(name(r)).append("\n");
        return text.append("点击从背包补齐一炉材料与灵石");
    }
}
