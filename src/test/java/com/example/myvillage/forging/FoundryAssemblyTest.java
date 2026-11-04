package com.example.myvillage.forging;

import com.example.myvillage.item.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class FoundryAssemblyTest {
    @BeforeAll static void bootstrap(){PhysicalWorkshopTest.bootstrap();}
    private ItemStack ground(String recipe,int quality){
        int target=WorkshopRules.get().formingPoints(recipe);var piece=WorkshopItems.blank(new Workpiece(recipe,4,target,WorkshopRules.get().craft(recipe).armor()?2:0));
        piece.set(ModDataComponents.FORGE_CRAFT.get(),ForgeCraft.EMPTY.work(target,quality));return piece;
    }
    private ItemStack handle(String id){return new ItemStack(ModItems.FOUNDRY_ITEMS.get(id+"_handle").get());}
    @Test void everyMaterialHasARealDistinctAssemblyAndDefaultAutomaticHandleIsWood(){
        var blank=ground("qingfeng_sword",5000);var before=blank.copy();
        for(var h:FoundryRules.get().handles()){
            var plan=AssemblyPlan.prepare(blank,handle(h.id()),3);assertTrue(plan.valid(),h.id());
            assertEquals(h.id(),FoundryEquipment.record(plan.result()).handle());assertEquals(ForgeMark.uninfused(3),plan.result().get(ModDataComponents.FORGE_MARK.get()));
            assertTrue(plan.returned().isEmpty());assertTrue(ItemStack.matches(before,blank));
        }
        assertEquals("wood",FoundryEquipment.record(PreparationBlockEntity.standard(blank,3)).handle());
    }
    @Test void rehandlingReturnsTheOldPartAndPreservesQualityInfusionWorkAndWear(){
        var original=AssemblyPlan.prepare(ground("qingfeng_sword",6500),handle("chitong"),2).result();
        original=EquipmentInfusion.prepare(original,"sharp",4).result();original.setDamageValue(original.getMaxDamage()/3);
        original.set(DataComponents.CUSTOM_NAME,Component.literal("留下工艺"));var before=original.copy();
        var plan=AssemblyPlan.prepare(original,handle("xuantie"),4);assertTrue(plan.valid());var result=plan.result();
        assertEquals(before.get(ModDataComponents.FORGE_MARK.get()),result.get(ModDataComponents.FORGE_MARK.get()));
        assertEquals(before.get(ModDataComponents.TEMPER.get()),result.get(ModDataComponents.TEMPER.get()));
        assertEquals(before.getHoverName(),result.getHoverName());assertEquals(FoundryEquipment.record(before).merit(),FoundryEquipment.record(result).merit());
        assertTrue(plan.returned().is(ModItems.FOUNDRY_ITEMS.get("chitong_handle").get()));
        assertEquals(before.getDamageValue()/(double)before.getMaxDamage(),result.getDamageValue()/(double)result.getMaxDamage(),1.0/result.getMaxDamage());
        assertTrue(ItemStack.matches(before,original));
        var registry=RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        assertTrue(ItemStack.matches(result,ItemStack.parse(registry,result.save(registry)).orElseThrow()));
    }
    @Test void swappingBackAndRepeatedRefreshNeverAccumulateStatsOrRepairTheHead(){
        var item=AssemblyPlan.prepare(ground("forged_pickaxe",7000),handle("wood"),3).result();item.setDamageValue(80);var baseline=item.copy();
        for(int i=0;i<4;i++){
            var iron=AssemblyPlan.prepare(item,handle("xuantie"),4);assertTrue(iron.valid());
            item=AssemblyPlan.prepare(iron.result(),handle("wood"),4).result();
            assertEquals(baseline.getMaxDamage(),item.getMaxDamage());assertTrue(item.getDamageValue()>=baseline.getDamageValue());
        }
        var before=item.copy();for(int i=0;i<10;i++){WorkshopEquipment.refresh(item);FoundryEquipment.refresh(item);}
        assertTrue(ItemStack.matches(before,item));
    }
    @Test void unfinishedStacksWrongMaterialsAndRepeatedSameHandleFailWithoutMutation(){
        var rough=WorkshopItems.blank(new Workpiece("qingfeng_sword",0,0,0));var before=rough.copy();
        assertFalse(AssemblyPlan.prepare(rough,handle("wood"),4).valid());assertTrue(ItemStack.matches(before,rough));
        var item=AssemblyPlan.prepare(ground("qingfeng_sword",5000),handle("wood"),2).result();
        assertFalse(AssemblyPlan.prepare(item,handle("wood"),4).valid());
        assertFalse(AssemblyPlan.prepare(item,new ItemStack(Items.DIRT),4).valid());
        assertFalse(AssemblyPlan.prepare(item.copyWithCount(2),handle("bamboo"),4).valid());
    }
    @Test void armorRequiresItsAssemblyMaterialAndCannotReceiveAWeaponHandle(){
        var blank=ground("xuantie_chestplate",5000);assertFalse(AssemblyPlan.prepare(blank,handle("chitong"),3).valid());
        assertTrue(AssemblyPlan.prepare(blank,new ItemStack(Items.LEATHER),3).valid());
    }
    @Test void handworkImprovesAllApplicableBenefitsWhileAutomaticProductionKeepsItsBaseline(){
        var manual=AssemblyPlan.prepare(ground("qingfeng_sword",8000),handle("wood"),2).result();
        var automatic=PreparationBlockEntity.standard(WorkshopItems.blank(new Workpiece("qingfeng_sword",0,0,0)),2);
        var bonuses=FoundryEquipment.benefits(manual);assertTrue(bonuses.attack()>0);assertTrue(bonuses.speed()>0);assertTrue(bonuses.durability()>0);assertTrue(bonuses.qiDiscount()>0);
        assertTrue(manual.getMaxDamage()>automatic.getMaxDamage());assertTrue(FoundryEquipment.speed(manual)>FoundryEquipment.speed(automatic));
        assertTrue(ForgedEquipment.qiCost(manual,100)<ForgedEquipment.qiCost(automatic,100));
        assertEquals(0,FoundryEquipment.benefits(automatic).attack());
        var armor=AssemblyPlan.prepare(ground("xuantie_chestplate",8000),new ItemStack(Items.LEATHER),2).result();assertTrue(FoundryEquipment.benefits(armor).armor()>0);
    }
    @Test void trainingHandlesHandworkAndFireShareTheFinalStatCaps(){
        var caps=FoundryRules.get().caps();
        for(String recipe:new String[]{"qingfeng_sword","forged_pickaxe","xuantie_chestplate"}){
            var craft=WorkshopRules.get().craft(recipe);var blank=ground(recipe,10000);
            ItemStack item=AssemblyPlan.prepare(blank,craft.armor()?new ItemStack(Items.LEATHER):handle("xuantie"),4).result();
            var refinement=AuxiliaryRefining.prepare(item,FireCaptureItem.seed("samadhi",1000,"",net.minecraft.core.BlockPos.ZERO),craft.weapon()?"sharp":"tough",4);
            assertTrue(refinement.valid(),recipe);item=refinement.result();
            int max=craft.max_strikes();item.set(ModDataComponents.TEMPER.get(),new TemperRecord(0,craft.armor()?0:max,craft.armor()?max:0,0,craft.armor()?2:0));WorkshopEquipment.refresh(item);
            var base=item.getItem().getDefaultInstance();assertTrue(item.getMaxDamage()<=Math.ceil(base.getMaxDamage()*(1+caps.durability())));
            assertTrue(FoundryEquipment.speed(item)<=1+caps.speed());
            if(!craft.armor()){
                double ordinary=attack(base),actual=attack(item);assertTrue(actual<=ordinary*(1+caps.attack())+.00001);
            }
        }
    }
    private static double attack(ItemStack stack){return 1+stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY).modifiers().stream()
            .filter(e->e.attribute().equals(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)&&e.modifier().operation()==net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE)
            .mapToDouble(e->e.modifier().amount()).sum();}
    @Test void allFourArmorPiecesCanContributeWithoutCollidingInThePlayersAttributeMap(){
        var ids=new java.util.HashSet<net.minecraft.resources.ResourceLocation>();
        for(String kind:java.util.List.of("helmet","chestplate","leggings","boots")){
            var item=AssemblyPlan.prepare(ground("xuantie_"+kind,8000),new ItemStack(Items.LEATHER),2).result();
            var modifiers=item.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().stream()
                    .filter(e->e.attribute().equals(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR)&&e.modifier().id().getNamespace().equals("myvillage")).toList();
            assertEquals(1,modifiers.size(),kind);assertTrue(modifiers.getFirst().modifier().amount()>0);
            assertTrue(ids.add(modifiers.getFirst().modifier().id()),"Each armor slot needs its own modifier identity");
            double base=item.getItem().getDefaultInstance().getAttributeModifiers().modifiers().stream().filter(e->e.attribute().equals(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR)).mapToDouble(e->e.modifier().amount()).sum();
            double finalArmor=item.getAttributeModifiers().modifiers().stream().filter(e->e.attribute().equals(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR)).mapToDouble(e->e.modifier().amount()).sum();
            assertTrue(finalArmor>base,"Handwork must improve real vanilla armor defense");
            assertTrue(finalArmor<=base*(1+FoundryRules.get().caps().armor())+.000001);
        }
        assertEquals(4,ids.size());
    }
}
