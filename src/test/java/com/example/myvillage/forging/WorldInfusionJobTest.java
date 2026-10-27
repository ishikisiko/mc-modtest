package com.example.myvillage.forging;

import com.example.myvillage.item.ModDataComponents;
import com.example.myvillage.spiritstone.SpiritStoneTags;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class WorldInfusionJobTest {
    private static final TagKey<Item> MATERIAL=TagKey.create(Registries.ITEM,ResourceLocation.parse("myvillage:forging_materials/xuantie"));
    @BeforeAll static void bootstrap(){EquipmentInfusionTest.bootstrap();}
    @BeforeEach void tags(){BuiltInRegistries.ITEM.bindTags(Map.of(MATERIAL,List.of(Items.IRON_INGOT.builtInRegistryHolder()),SpiritStoneTags.LOW_GRADE_SPIRIT_STONE,List.of(Items.EMERALD.builtInRegistryHolder())));}
    @AfterEach void clearTags(){BuiltInRegistries.ITEM.bindTags(Map.of(MATERIAL,List.of(),SpiritStoneTags.LOW_GRADE_SPIRIT_STONE,List.of()));}
    private ItemStack input(){
        var item=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("myvillage:forged_pickaxe")));
        item.set(DataComponents.CUSTOM_NAME,Component.literal("旧器·留名"));item.setDamageValue(3);
        item.set(ModDataComponents.TEMPER.get(),new TemperRecord(14,0,2,0,2));return item;
    }
    private List<ItemStack> escrow(){var material=new ItemStack(Items.IRON_INGOT);material.set(DataComponents.CUSTOM_NAME,Component.literal("原辅材"));return List.of(material,new ItemStack(Items.EMERALD,2));}
    private WorldInfusionJob job(){var input=input();var job=new WorldInfusionJob();job.begin(UUID.randomUUID(),input,EquipmentInfusion.prepare(input,"tough",2),escrow(),"tough",2,50);return job;}
    private RegistryAccess registries(){return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);}
    @Test void completionOccursOnceAndDrainingNeverDuplicatesInputOrProspectiveResult(){
        var job=job();for(int i=0;i<49;i++)assertFalse(job.tick());assertTrue(job.output().isEmpty());assertTrue(job.tick());
        for(int i=0;i<100;i++)assertFalse(job.tick());
        assertTrue(job.input().isEmpty());assertTrue(job.escrow().isEmpty());
        var output=job.output();assertEquals(new ForgeMark("tough",2),output.get(ModDataComponents.FORGE_MARK.get()));
        assertEquals(input().getHoverName(),output.getHoverName());assertEquals(3,output.getDamageValue());assertEquals(input().get(ModDataComponents.TEMPER.get()),output.get(ModDataComponents.TEMPER.get()));
        var taken=job.drain();assertEquals(1,taken.size());assertTrue(ItemStack.matches(output,taken.getFirst()));assertTrue(job.drain().isEmpty());assertFalse(job.occupied());
    }
    @Test void unfinishedBreakRefundsExactOriginalAndMaterialIdentitiesOnly(){
        var job=job();job.tick();var refund=job.drain();assertEquals(3,refund.size());assertTrue(ItemStack.matches(input(),refund.getFirst()));
        assertTrue(ItemStack.matches(escrow().getFirst(),refund.get(1)));assertTrue(ItemStack.matches(escrow().get(1),refund.get(2)));assertTrue(job.drain().isEmpty());
    }
    @Test void restartResumesOnlySavedTicksAndKeepsFrozenQuality(){
        var job=job();for(int i=0;i<19;i++)job.tick();var loaded=new WorldInfusionJob();loaded.load(job.save(registries()),registries());
        assertEquals(job.owner(),loaded.owner());assertEquals(19,loaded.elapsed());assertTrue(ItemStack.matches(input(),loaded.display()));
        for(int i=0;i<30;i++)assertFalse(loaded.tick());assertTrue(loaded.tick());assertEquals(2,loaded.output().get(ModDataComponents.FORGE_MARK.get()).quality());
        var complete=new WorldInfusionJob();complete.load(loaded.save(registries()),registries());assertFalse(complete.running());assertEquals(1,complete.drain().size());
    }
    @Test void staleOrCorruptFrozenStateCannotMintAnItemButRealEscrowRemainsRefundable(){
        for(String field:List.of("Frozen","Owner","Duration")){
            var job=job();var saved=job.save(registries());saved.remove(field);var loaded=new WorldInfusionJob();loaded.load(saved,registries());
            assertTrue(loaded.invalid());assertFalse(loaded.tick());assertTrue(loaded.output().isEmpty());assertEquals(3,loaded.drain().size());
        }
    }
    @Test void startRejectsPartialOrExcessEscrowAndOccupiedDing(){
        var input=input();var prepared=EquipmentInfusion.prepare(input,"tough",2);var job=new WorldInfusionJob();
        assertThrows(IllegalArgumentException.class,()->job.begin(UUID.randomUUID(),input,prepared,List.of(new ItemStack(Items.IRON_INGOT)),"tough",2,50));
        assertThrows(IllegalArgumentException.class,()->job.begin(UUID.randomUUID(),input,prepared,List.of(new ItemStack(Items.IRON_INGOT,2),new ItemStack(Items.EMERALD,2)),"tough",2,50));
        var active=job();assertThrows(IllegalArgumentException.class,()->active.begin(UUID.randomUUID(),input,prepared,escrow(),"tough",2,50));
    }
    @Test void oldEmptyDingHasNoJobOrPhantomOutput(){var job=new WorldInfusionJob();job.load(new CompoundTag(),registries());assertFalse(job.occupied());assertFalse(job.tick());assertTrue(job.drain().isEmpty());}
}
