package com.example.myvillage.client.courtyard;

import com.example.myvillage.courtyard.CourtyardAssemblyItem;
import com.example.myvillage.courtyard.CourtyardContent;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.LoggerFactory;

/** Optional JEI leaf. Variants stay behind the native selector, not separate ingredients. */
@JeiPlugin
public final class CourtyardJeiPlugin implements IModPlugin {
    @Override public ResourceLocation getPluginUid(){return ResourceLocation.fromNamespaceAndPath("myvillage","courtyard");}
    @Override public void registerRecipes(IRecipeRegistration registration){
        CourtyardContent.ITEMS_BY_FAMILY.values().forEach(item->registration.addIngredientInfo(item.get(),Component.translatable("tooltip.myvillage.courtyard.select")));
    }
    @Override public void onRuntimeAvailable(IJeiRuntime runtime){
        var entries=runtime.getIngredientManager().getAllIngredients(VanillaTypes.ITEM_STACK).stream().filter(s->s.getItem() instanceof CourtyardAssemblyItem).toList();
        var ids=entries.stream().map(s->BuiltInRegistries.ITEM.getKey(s.getItem()).toString()).distinct().sorted().toList();
        LoggerFactory.getLogger(CourtyardJeiPlugin.class).info("COURTYARD_JEI entries={} families={} ids={}",entries.size(),ids.size(),ids);
    }
}
