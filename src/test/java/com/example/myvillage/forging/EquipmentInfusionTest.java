package com.example.myvillage.forging;

import com.example.myvillage.item.ModDataComponents;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EquipmentInfusionTest {
    @BeforeAll @SuppressWarnings("deprecation") static void bootstrap() {
        if(LoadingModList.get()==null)LoadingModList.of(List.of(),List.of(),List.of(),List.of(),Map.of());
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        // Exercise the public API against the real authored recipe IDs. Standalone unit tests
        // bootstrap vanilla only, so provide damageable fixtures in bounded registry windows.
        var items=(MappedRegistry<Item>)BuiltInRegistries.ITEM;
        synchronized(items) {
            items.unfreeze();
            try {
                for(var recipe:ForgingRules.get().recipes()) {
                    var id=ResourceLocation.parse(recipe.output());
                    if(!items.containsKey(id)){
                        var armor=com.example.myvillage.equipment.EquipmentCatalogue.bundled().stream().filter(a->a.id().equals(id)).findFirst();
                        Registry.register(items,id,armor.<Item>map(a->new com.example.myvillage.equipment.EquipmentArmorItem(new Item.Properties(),a))
                                .orElseGet(()->new Item(new Item.Properties().durability(250))));
                    }
                }
            } finally { items.freeze(); }
        }
        var components=(MappedRegistry<DataComponentType<?>>)BuiltInRegistries.DATA_COMPONENT_TYPE;
        synchronized(components) {
            components.unfreeze();
            try {
                var mark=ModDataComponents.FORGE_MARK.getId();
                if(!components.containsKey(mark))Registry.register(components,mark,DataComponentType.<ForgeMark>builder()
                        .persistent(ForgeMark.CODEC).networkSynchronized(ForgeMark.STREAM_CODEC).build());
                var temper=ModDataComponents.TEMPER.getId();
                if(!components.containsKey(temper))Registry.register(components,temper,DataComponentType.<TemperRecord>builder()
                        .persistent(TemperRecord.CODEC).networkSynchronized(TemperRecord.STREAM_CODEC).build());
            } finally { components.freeze(); }
        }
    }

    private static ItemStack equipment(String recipe) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(ForgingRules.get().recipe(recipe).orElseThrow().output())));
    }

    private static ItemStack decoratedSword() {
        var input=equipment("qingfeng_sword");
        input.set(DataComponents.CUSTOM_NAME,Component.literal("旧友·青锋"));
        input.set(DataComponents.LORE,new net.minecraft.world.item.component.ItemLore(List.of(Component.literal("保留来历"))));
        input.setDamageValue(37);
        var data=new CompoundTag();data.putString("owner_note","不可丢失");
        input.set(DataComponents.CUSTOM_DATA,CustomData.of(data));
        var enchantment=Holder.direct(new Enchantment(Component.literal("测试附魔"),
                Enchantment.definition(HolderSet.direct(input.getItem().builtInRegistryHolder()),1,3,
                        Enchantment.constantCost(1),Enchantment.constantCost(10),1,EquipmentSlotGroup.MAINHAND),
                HolderSet.direct(),DataComponentMap.EMPTY));
        var enchantments=new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(enchantment,3);input.set(DataComponents.ENCHANTMENTS,enchantments.toImmutable());
        return input;
    }

    private static void rejected(ItemStack input,String trait,int cap,int reason) {
        var before=input.copy();
        var preparation=EquipmentInfusion.prepare(input,trait,cap);
        assertFalse(preparation.valid());assertEquals(reason,preparation.reason());
        assertTrue(preparation.result().isEmpty());assertTrue(preparation.costs().isEmpty());
        assertTrue(ItemStack.matches(before,input));
    }

    @Test void untemperedFinishedItemKeepsEveryUnrelatedComponentAndNeverChangesTheInput() {
        var input=decoratedSword();var before=input.copy();
        var preparation=EquipmentInfusion.prepare(input,"channeling",4);
        assertTrue(preparation.valid());assertEquals(0,preparation.reason());
        var result=preparation.result();assertNotSame(input,result);assertEquals(1,result.getCount());
        assertEquals(new ForgeMark("channeling",4),result.get(ModDataComponents.FORGE_MARK.get()));
        assertFalse(result.has(ModDataComponents.TEMPER.get()));
        // Channeling adds only the forge mark; equality checks the complete original patch,
        // including a nonempty enchantment map, name, lore, custom data and existing damage.
        var withoutMark=result.copy();withoutMark.remove(ModDataComponents.FORGE_MARK.get());
        assertTrue(ItemStack.matches(before,withoutMark));assertTrue(ItemStack.matches(before,input));
        assertEquals(1,result.get(DataComponents.ENCHANTMENTS).size());
        assertEquals(WorkshopRules.get().infusionCosts(ForgingRules.get().recipe("qingfeng_sword").orElseThrow(),"channeling"),preparation.costs());
        result.setDamageValue(1);assertEquals(37,input.getDamageValue());
    }

    @Test void temperedItemRetainsPaidTrainingAndRefreshesItsCombinedDurability() {
        var input=decoratedSword();var temper=new TemperRecord(123,2,3,1,2);
        input.set(ModDataComponents.TEMPER.get(),temper);WorkshopEquipment.refresh(input);
        var before=input.copy();var preparation=EquipmentInfusion.prepare(input,"tough",3);
        assertTrue(preparation.valid());var result=preparation.result();
        assertEquals(temper,result.get(ModDataComponents.TEMPER.get()));
        var rules=WorkshopRules.get();
        assertEquals((int)Math.ceil(input.getItem().getDefaultInstance().getMaxDamage()*ForgingRules.get().trait("tough").value(3)
                *(1+temper.endurance()*rules.durability_per_strike()+WorkshopEquipment.toughTreatment(temper)*rules.treatment_durability())),result.getMaxDamage());
        var expected=before.copy();expected.set(ModDataComponents.FORGE_MARK.get(),new ForgeMark("tough",3));
        expected.set(DataComponents.MAX_DAMAGE,result.getMaxDamage());
        assertTrue(ItemStack.matches(expected,result));assertTrue(ItemStack.matches(before,input));
    }

    @Test void repeatedInfusionIsRejectedWithoutChangingTheExistingMark() {
        var first=EquipmentInfusion.prepare(equipment("qingfeng_sword"),"sharp",2);
        assertTrue(first.valid());rejected(first.result(),"tough",4,2);
    }

    @Test void invalidItemsAndStackCountsAreRejected() {
        rejected(ItemStack.EMPTY,"sharp",1,2);
        rejected(new ItemStack(Items.DIAMOND_SWORD),"sharp",1,2);
        rejected(new ItemStack(Items.IRON_INGOT),"tough",1,2);
        rejected(equipment("qingfeng_sword").copyWithCount(2),"sharp",1,2);
    }

    @Test void invalidCapsAndUnsupportedTraitsReturnTheMenuReasons() {
        for(int cap:new int[]{Integer.MIN_VALUE,-1,0,5,Integer.MAX_VALUE})rejected(equipment("qingfeng_sword"),"sharp",cap,7);
        for(String trait:new String[]{"sharp","channeling","unknown","",null})rejected(equipment("forged_pickaxe"),trait,4,11);
        rejected(equipment("lingxiao_spear"),"channeling",4,11);
    }

    @Test void eachActualInputRecipeUsesItsOwnTraitsAndCostsAtEveryCap() {
        for(var craft:WorkshopRules.get().crafts()) {
            var recipe=ForgingRules.get().recipe(craft.recipe()).orElseThrow();
            for(var trait:recipe.traits())for(int cap=1;cap<=4;cap++) {
                // No pattern knowledge or TEMPER component is required for a finished item.
                var input=equipment(recipe.id());var preparation=EquipmentInfusion.prepare(input,trait,cap);
                assertTrue(preparation.valid(),recipe.id()+"/"+trait+"/"+cap);
                assertEquals(new ForgeMark(trait,cap),preparation.result().get(ModDataComponents.FORGE_MARK.get()));
                assertEquals(WorkshopRules.get().infusionCosts(recipe,trait),preparation.costs());
                assertFalse(input.has(ModDataComponents.FORGE_MARK.get()));
                assertFalse(preparation.result().has(ModDataComponents.TEMPER.get()));
            }
        }
    }
    @Test void armorInfusionAndTrainingPreserveVanillaProtectionAndAllUnrelatedMetadata() {
        double totalArmor=0;
        for(var entry:com.example.myvillage.equipment.EquipmentCatalogue.bundled()){
            var input=equipment(entry.id().getPath());
            assertInstanceOf(com.example.myvillage.equipment.EquipmentArmorItem.class,input.getItem());
            var attributes=input.get(DataComponents.ATTRIBUTE_MODIFIERS);
            totalArmor+=attributes.modifiers().stream().filter(a->a.attribute().equals(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR))
                    .mapToDouble(a->a.modifier().amount()).sum();
            input.set(DataComponents.CUSTOM_NAME,Component.literal("久经战阵"));
            input.set(DataComponents.ENCHANTMENTS,decoratedSword().get(DataComponents.ENCHANTMENTS));
            input.setDamageValue(20);
            var craft=WorkshopEquipment.craft(input).orElseThrow();assertTrue(craft.armor());
            var record=new TemperRecord(10000,0,0,0,2).strike(2,craft,WorkshopRules.get());
            input.set(ModDataComponents.TEMPER.get(),record);WorkshopEquipment.refresh(input);
            var before=input.copy();var prepared=EquipmentInfusion.prepare(input,"tough",4);assertTrue(prepared.valid());
            var result=prepared.result();int durable=result.getMaxDamage();
            for(int i=0;i<3;i++)WorkshopEquipment.refresh(result);
            assertEquals(durable,result.getMaxDamage());assertEquals(attributes,result.get(DataComponents.ATTRIBUTE_MODIFIERS));
            assertEquals(20,result.getDamageValue());assertEquals(record,result.get(ModDataComponents.TEMPER.get()));
            var expected=before.copy();expected.set(DataComponents.MAX_DAMAGE,durable);expected.set(ModDataComponents.FORGE_MARK.get(),new ForgeMark("tough",4));
            assertTrue(ItemStack.matches(expected,result));assertTrue(ItemStack.matches(before,input));
            rejected(input,"sharp",4,11);rejected(input,"channeling",4,11);
            assertTrue(ForgedEquipment.repairAmount(result)>0);
            assertThrows(IllegalArgumentException.class,()->record.strike(1,craft,WorkshopRules.get()));
            assertThrows(IllegalArgumentException.class,()->record.strike(3,craft,WorkshopRules.get()));
            assertFalse(craft.treatment(0));assertFalse(craft.treatment(1));assertTrue(craft.treatment(2));
        }
        assertEquals(15,totalArmor);
    }

}
