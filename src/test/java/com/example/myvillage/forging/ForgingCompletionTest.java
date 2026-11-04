package com.example.myvillage.forging;

import com.example.myvillage.item.ModDataComponents;
import com.example.myvillage.item.QualityDisplay;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ForgingCompletionTest {
    @BeforeAll static void bootstrap(){EquipmentInfusionTest.bootstrap();}
    private Workpiece piece(String recipe,int treatment){return new Workpiece(recipe,4,WorkshopRules.get().formingPoints(recipe),treatment);}

    @Test void completedSwordToolAndArmorAlreadyHaveEveryQualityWithoutAnAffinity(){
        for(String recipe:List.of("qingfeng_sword","forged_pickaxe","xuantie_chestplate"))for(int q=1;q<=4;q++){
            var result=WorkshopEquipment.complete(piece(recipe,2),q);
            assertEquals(ForgeMark.uninfused(q),result.get(ModDataComponents.FORGE_MARK.get()));
            assertFalse(ForgedEquipment.infused(result));
            assertEquals(q,ForgingQualityDisplay.quality(result));assertEquals(q,QualityDisplay.quality(result));
            assertEquals(2,WorkshopEquipment.record(result).treatment());
            assertFalse(WorkshopStriking.direction(result,3));
            assertEquals(100,ForgedEquipment.qiCost(result,100));
        }
    }
    @Test void unfinishedStagesAndInvalidQualityCannotComplete(){
        for(int stage=0;stage<4;stage++){
            var piece=new Workpiece("qingfeng_sword",stage,0,0);
            assertThrows(IllegalArgumentException.class,()->WorkshopEquipment.complete(piece,4));
        }
        for(int q:new int[]{-1,0,5})assertThrows(IllegalArgumentException.class,()->WorkshopEquipment.complete(piece("qingfeng_sword",0),q));
    }
    @Test void infusionOperatorCannotUpgradeOrDowngradeTheCraftedQuality(){
        for(int crafted=1;crafted<=4;crafted++)for(int operator=1;operator<=4;operator++){
            var item=WorkshopEquipment.complete(piece("qingfeng_sword",1),crafted);var original=item.copy();
            var infused=EquipmentInfusion.prepare(item,"channeling",operator);
            assertTrue(infused.valid());assertEquals(new ForgeMark("channeling",crafted),infused.result().get(ModDataComponents.FORGE_MARK.get()));
            assertTrue(ItemStack.matches(original,item));
            assertFalse(EquipmentInfusion.prepare(infused.result(),"tough",operator).valid());
        }
    }
    @Test void rawCommandItemsCannotHaveQualityAssignedByInfusion(){
        var raw=WorkshopEquipment.complete(piece("qingfeng_sword",0),1);raw.remove(ModDataComponents.FORGE_MARK.get());
        var result=EquipmentInfusion.prepare(raw,"sharp",4);
        assertFalse(result.valid());assertEquals(20,result.reason());assertTrue(result.result().isEmpty());assertTrue(result.costs().isEmpty());
        assertFalse(raw.has(ModDataComponents.FORGE_MARK.get()));
    }
    @Test void recipeCompletionStampIsIdempotentAndLeavesOtherItemsAlone(){
        var raw=WorkshopEquipment.complete(piece("forged_pickaxe",0),1);raw.remove(ModDataComponents.FORGE_MARK.get());
        raw.set(DataComponents.CUSTOM_NAME,Component.literal("成器留名"));
        ForgingCompletion.stamp(raw,2);var before=raw.copy();ForgingCompletion.stamp(raw,4);
        assertTrue(ItemStack.matches(before,raw));assertEquals(ForgeMark.uninfused(2),raw.get(ModDataComponents.FORGE_MARK.get()));
        var vanilla=new ItemStack(Items.IRON_PICKAXE);ForgingCompletion.stamp(vanilla,4);assertFalse(vanilla.has(ModDataComponents.FORGE_MARK.get()));
    }
    @Test void qualityOnlyMetadataSurvivesItemPersistenceAndWireRoundTrip(){
        var registries=RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        for(int q=1;q<=4;q++){
            var item=WorkshopEquipment.complete(piece("forged_pickaxe",2),q);
            var mark=item.get(ModDataComponents.FORGE_MARK.get());
            assertEquals(mark,ForgeMark.CODEC.parse(JsonOps.INSTANCE,ForgeMark.CODEC.encodeStart(JsonOps.INSTANCE,mark).getOrThrow()).getOrThrow());
            var bytes=Unpooled.buffer();try{ForgeMark.STREAM_CODEC.encode(bytes,mark);assertEquals(mark,ForgeMark.STREAM_CODEC.decode(bytes));assertFalse(bytes.isReadable());}finally{bytes.release();}
            assertTrue(ItemStack.matches(item,ItemStack.parse(registries,item.save(registries)).orElseThrow()));
        }
    }
    @Test void temperingRepairAndRefreshPreserveUninfusedQuality(){
        var item=WorkshopEquipment.complete(piece("forged_pickaxe",2),3);
        item.setDamageValue(80);item.set(DataComponents.CUSTOM_NAME,Component.literal("长用旧镐"));
        var craft=WorkshopEquipment.craft(item).orElseThrow();
        item.set(ModDataComponents.TEMPER.get(),WorkshopEquipment.record(item).earn(100,WorkshopRules.get()));
        var result=WorkshopStriking.result(item,2,FoundryRules.get().hammer("forging_hammer"),0,700).stack();
        assertEquals(ForgeMark.uninfused(3),result.get(ModDataComponents.FORGE_MARK.get()));
        assertEquals(80,result.getDamageValue());assertEquals(item.getHoverName(),result.getHoverName());
        int maximum=result.getMaxDamage();WorkshopEquipment.refresh(result);assertEquals(maximum,result.getMaxDamage());
        result.setDamageValue(Math.max(0,result.getDamageValue()-ForgedEquipment.repairAmount(result)));
        assertEquals(ForgeMark.uninfused(3),result.get(ModDataComponents.FORGE_MARK.get()));
        assertTrue(result.getDamageValue()<80);
    }
    @Test void directInfusionCannotReplaceAnExistingCraftedQuality(){
        var item=WorkshopEquipment.complete(piece("qingfeng_sword",0),2);var before=item.copy();
        assertThrows(IllegalArgumentException.class,()->ForgedEquipment.apply(item,new ForgeMark("sharp",4)));
        assertTrue(ItemStack.matches(before,item));
    }
}
