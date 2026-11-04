package com.example.myvillage.forging;

import com.example.myvillage.item.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class FoundryFireTest {
    @BeforeAll static void bootstrap(){PhysicalWorkshopTest.bootstrap();}
    private ItemStack equipment(){
        int points=WorkshopRules.get().formingPoints("qingfeng_sword");
        var blank=WorkshopItems.blank(new Workpiece("qingfeng_sword",4,points,0));
        blank.set(ModDataComponents.FORGE_CRAFT.get(),ForgeCraft.EMPTY.work(points,6000));return WorkshopEquipment.complete(blank,2,"chitong");
    }
    @Test void everyFirePreservesTheInputAndAddsOneBoundedContribution(){
        for(var fire:FoundryRules.get().fires()){
            var input=equipment();input.setDamageValue(20);input.set(DataComponents.CUSTOM_NAME,Component.literal("原器"));var before=input.copy();
            var seed=FireCaptureItem.seed(fire.id(),500,"minecraft:overworld",BlockPos.ZERO);
            var result=AuxiliaryRefining.prepare(input,seed,"sharp",4);assertTrue(result.valid(),fire.id());
            assertTrue(ItemStack.matches(before,input));assertEquals(2,result.result().get(ModDataComponents.FORGE_MARK.get()).quality());
            assertEquals("chitong",FoundryEquipment.record(result.result()).handle());assertEquals(FoundryEquipment.record(input).merit(),FoundryEquipment.record(result.result()).merit());
            assertEquals(20,result.result().getDamageValue());assertEquals(before.getHoverName(),result.result().getHoverName());
            assertTrue(FoundryEquipment.benefits(result.result()).attack()>FoundryEquipment.benefits(input).attack());
            assertFalse(AuxiliaryRefining.prepare(result.result(),seed,"sharp",4).valid());
        }
    }
    @Test void anAlreadyInfusedItemCanReceiveItsFirstFireWithoutChangingItsTrait(){
        var item=EquipmentInfusion.prepare(equipment(),"channeling",4).result();
        var result=AuxiliaryRefining.prepare(item,FireCaptureItem.seed("beast",100,"",BlockPos.ZERO),"sharp",4);
        assertTrue(result.valid());assertEquals(item.get(ModDataComponents.FORGE_MARK.get()),result.result().get(ModDataComponents.FORGE_MARK.get()));
    }
    @Test void maturityIsSaturatedAndUnknownOrMismatchedFireMetadataCannotBeUsed(){
        var seed=new FireSeed("beast",0,"minecraft:overworld",0);assertEquals(FoundryRules.get().fire().max_maturity(),seed.grow(Integer.MAX_VALUE).maturity());
        var item=FireCaptureItem.seed("beast",0,"",BlockPos.ZERO);item.set(ModDataComponents.FIRE_SEED.get(),new FireSeed("earth",0,"",0));assertFalse(AuxiliaryRefining.validSeed(item));
        assertFalse(AuxiliaryRefining.prepare(equipment(),item,"sharp",4).valid());
    }
    @Test void firstApplicationDoesNotAllowRerollingOrStackingDifferentFireTypes(){
        var original=equipment();var refined=AuxiliaryRefining.prepare(original,FireCaptureItem.seed("earth",1000,"",BlockPos.ZERO),"sharp",4).result();
        for(var fire:FoundryRules.get().fires())assertFalse(AuxiliaryRefining.prepare(refined,FireCaptureItem.seed(fire.id(),1000,"",BlockPos.ZERO),"sharp",4).valid());
        var before=refined.copy();for(int i=0;i<20;i++)WorkshopEquipment.refresh(refined);assertTrue(ItemStack.matches(before,refined));
    }
}
