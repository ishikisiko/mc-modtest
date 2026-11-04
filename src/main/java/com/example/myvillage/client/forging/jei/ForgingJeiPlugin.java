package com.example.myvillage.client.forging.jei;

import com.example.myvillage.block.ModBlocks;
import com.example.myvillage.forging.ForgingRules;
import com.example.myvillage.forging.WorkshopRules;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.slf4j.LoggerFactory;

/** Optional client leaf, discovered by JEI only when it is installed. */
@JeiPlugin
public final class ForgingJeiPlugin implements IModPlugin {
    @Override public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath("myvillage", "forging");
    }
    @Override public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new ForgingJeiCategory(registration.getJeiHelpers().getGuiHelper()));
    }
    @Override public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(ForgingJeiCategory.TYPE,
                ForgingJeiRecipe.create(ForgingRules.get(), WorkshopRules.get()));
    }
    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.FORGING_ANVIL.get()), ForgingJeiCategory.TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.FORGING_TABLE.get()), ForgingJeiCategory.TYPE);
    }
    @Override public void onRuntimeAvailable(IJeiRuntime runtime) {
        var recipes = runtime.getRecipeManager().createRecipeLookup(ForgingJeiCategory.TYPE).get().toList();
        LoggerFactory.getLogger(ForgingJeiPlugin.class).info("FORGING_JEI recipes={} outputs={}",
                recipes.size(), recipes.stream().map(ForgingJeiRecipe::output).toList());
    }
}
