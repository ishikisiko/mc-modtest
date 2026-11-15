package com.example.myvillage.drops;

import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DropItemVariantsTest {
    @BeforeAll static void bootstrap() {
        if (LoadingModList.get() == null)
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
    }
    @Test void omittedVariantPreservesComponentsAndRarityDoesNotChangeAttributes() {
        var original=new ItemStack(Items.DIAMOND_SWORD);var copy=original.copy();
        DropItemVariants.apply(copy,null,null);
        assertTrue(ItemStack.isSameItemSameComponents(original,copy));
        for(var rarity:Rarity.values()){
            var stack=original.copy();DropItemVariants.apply(stack,null,rarity.name().toLowerCase(java.util.Locale.ROOT));
            assertEquals(rarity,stack.get(DataComponents.RARITY));
            assertEquals(original.getAttributeModifiers(),stack.getAttributeModifiers());
            assertEquals(original.getMaxDamage(),stack.getMaxDamage());
        }
    }
    @Test void unsupportedQualityAndInvalidRarityFailWithoutChangingStack() {
        var stack=new ItemStack(Items.DIAMOND_SWORD);var original=stack.copy();
        assertThrows(IllegalArgumentException.class,()->DropItemVariants.apply(stack,4,null));
        assertThrows(IllegalArgumentException.class,()->DropItemVariants.apply(stack,null,"legendary"));
        assertThrows(IllegalArgumentException.class,()->DropItemVariants.apply(stack,1,"rare"));
        assertTrue(ItemStack.isSameItemSameComponents(original,stack));
    }
}
