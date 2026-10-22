package com.example.myvillage.forging.array;

import java.util.*;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ForgingArrayEscrowTest {
    @BeforeAll static void bootstrap(){
        if(LoadingModList.get()==null)LoadingModList.of(List.of(),List.of(),List.of(),List.of(),Map.of());
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test void incompleteAndSurplusSavedEscrowCannotFinish(){
        var costs=Map.of("minecraft:iron_ingot",3,"minecraft:diamond",1);
        assertFalse(ForgingArrayBlockEntity.escrowMatches(List.of(),costs));
        assertFalse(ForgingArrayBlockEntity.escrowMatches(List.of(new ItemStack(Items.IRON_INGOT,2),new ItemStack(Items.DIAMOND)),costs));
        assertFalse(ForgingArrayBlockEntity.escrowMatches(List.of(new ItemStack(Items.IRON_INGOT,4),new ItemStack(Items.DIAMOND)),costs));
        assertFalse(ForgingArrayBlockEntity.escrowMatches(List.of(new ItemStack(Items.IRON_INGOT,3),new ItemStack(Items.DIAMOND),new ItemStack(Items.DIRT)),costs));
    }
    @Test void exactComponentStacksRemainUntouchedDuringLoadValidation(){
        var named=new ItemStack(Items.IRON_INGOT,2);named.set(DataComponents.CUSTOM_NAME,Component.literal("暂存旧料"));var before=named.copy();
        assertTrue(ForgingArrayBlockEntity.escrowMatches(List.of(named,new ItemStack(Items.IRON_INGOT),new ItemStack(Items.DIAMOND)),Map.of("minecraft:iron_ingot",3,"minecraft:diamond",1)));
        assertTrue(ItemStack.matches(before,named));
    }
}
