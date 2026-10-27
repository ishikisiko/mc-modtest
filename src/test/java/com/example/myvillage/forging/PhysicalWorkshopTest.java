package com.example.myvillage.forging;

import com.example.myvillage.item.ForgingHammerItem;
import com.example.myvillage.item.ModDataComponents;
import com.example.myvillage.item.ModItems;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PhysicalWorkshopTest {
    @BeforeAll @SuppressWarnings("deprecation") static void bootstrap() {
        EquipmentInfusionTest.bootstrap();
        var components=(MappedRegistry<DataComponentType<?>>)BuiltInRegistries.DATA_COMPONENT_TYPE;
        synchronized(components) {
            components.unfreeze();
            try {
                var id=ModDataComponents.WORKPIECE.getId();
                if(!components.containsKey(id))Registry.register(components,id,DataComponentType.<Workpiece>builder()
                        .persistent(Workpiece.CODEC).networkSynchronized(Workpiece.STREAM_CODEC).build());
            } finally { components.freeze(); }
        }
        var items=(MappedRegistry<Item>)BuiltInRegistries.ITEM;
        synchronized(items) {
            items.unfreeze();
            try {
                for(var craft:WorkshopRules.get().crafts())for(int stage=0;stage<WorkshopItems.STAGES.size();stage++) {
                    var id=ResourceLocation.fromNamespaceAndPath("myvillage","workpiece_"+craft.recipe()+"_"+WorkshopItems.STAGES.get(stage));
                    if(!items.containsKey(id))Registry.register(items,id,new WorkpieceItem(new Item.Properties(),craft.recipe(),stage));
                }
                if(!items.containsKey(ModItems.FORGING_HAMMER.getId()))Registry.register(items,ModItems.FORGING_HAMMER.getId(),new ForgingHammerItem(new Item.Properties()));
            } finally { items.freeze(); }
        }
    }
    private ItemStack namedBlank() {
        var stack=WorkshopItems.blank(new Workpiece("lingxiao_spear",0,2,0));
        stack.set(DataComponents.CUSTOM_NAME,Component.literal("试作·长枪"));
        var note=new CompoundTag();note.putString("provenance","原器坯，不得重造");stack.set(DataComponents.CUSTOM_DATA,CustomData.of(note));
        return stack;
    }
    private ItemStack equipment(String recipe) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(ForgingRules.get().recipe(recipe).orElseThrow().output())));
    }
    @Test void actualInventoryAndHeatSurviveSaveWithoutRecreatingTheStackOrRefreshingHeat() {
        var state=new WorkshopStorage();var input=namedBlank();var output=new ItemStack(Items.DIAMOND,7);
        state.items.setItem(0,input);state.items.setItem(1,output);state.selection(1,2,3,1);state.heat(180,1000);
        var registries=RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);var tag=new CompoundTag();state.save(tag,registries);
        var restored=new WorkshopStorage();restored.load(tag,registries);
        assertTrue(ItemStack.matches(input,restored.items.getItem(0)));assertTrue(ItemStack.matches(output,restored.items.getItem(1)));
        assertEquals(110,restored.heat(1070));assertEquals(0,restored.heat(1180));
        assertEquals(1,restored.recipe());assertEquals(2,restored.trait());assertEquals(3,restored.direction());assertEquals(1,restored.treatment());
        var taken=restored.items.removeItemNoUpdate(0);assertTrue(ItemStack.matches(input,taken));assertTrue(restored.items.getItem(0).isEmpty());
        assertTrue(restored.items.removeItemNoUpdate(0).isEmpty());
    }
    @Test void legacyDisplayOnlyTagDoesNotCreateAnItemOrWarmth() {
        var tag=new CompoundTag();tag.putString("Kind","spear");tag.putInt("Stage",4);tag.putBoolean("Hot",true);tag.putLong("StrikeStart",90);
        var state=new WorkshopStorage();state.load(tag,RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        assertTrue(state.items.isEmpty());assertEquals(0,state.heat(100));assertEquals(1,state.direction());
    }
    @Test void oneSwingHasOneContactAndAFullMinimumCycleEvenUnderDuplicatePolling() {
        var swing=new WorkshopStrikeCycle();var rules=WorkshopRules.get();assertTrue(swing.begin(100));
        for(int tick=0;tick<rules.strike_ticks();tick++) {
            assertFalse(swing.begin(100+tick));
            assertEquals(tick==rules.contact_tick()?WorkshopStrikeCycle.Step.CONTACT:WorkshopStrikeCycle.Step.NONE,swing.tick(100+tick,true));
            assertEquals(WorkshopStrikeCycle.Step.NONE,swing.tick(100+tick,true));
        }
        assertEquals(WorkshopStrikeCycle.Step.FINISHED,swing.tick(100+rules.strike_ticks(),true));
        assertTrue(swing.begin(100+rules.strike_ticks()));
    }
    @Test void switchingItemDyingOrLeavingBeforeContactCancelsWithoutADeferredPayment() {
        var swing=new WorkshopStrikeCycle();assertTrue(swing.begin(10));
        assertEquals(WorkshopStrikeCycle.Step.CANCELLED,swing.tick(11,false));
        assertFalse(swing.begin(12));assertEquals(WorkshopStrikeCycle.Step.NONE,swing.tick(30,true));
        assertTrue(swing.begin(30));swing.cancel();assertEquals(WorkshopStrikeCycle.Step.NONE,swing.tick(34,true));
    }
    @Test void expiredSwingCannotCatchUpAsAContactAndNewSessionHasNoPendingSwing() {
        var swing=new WorkshopStrikeCycle();assertTrue(swing.begin(10));
        assertEquals(WorkshopStrikeCycle.Step.FINISHED,swing.tick(10+WorkshopRules.get().strike_ticks(),true));
        assertFalse(swing.active());assertFalse(new WorkshopStrikeCycle().active());
    }
    @Test void everyCraftFormsThroughItsOwnItemIdsWithoutLosingNameOrProvenance() {
        for(var craft:WorkshopRules.get().crafts()) {
            var stack=WorkshopItems.blank(new Workpiece(craft.recipe(),0,0,0));
            stack.set(DataComponents.CUSTOM_NAME,Component.literal(craft.recipe()));
            var note=new CompoundTag();note.putString("recipe",craft.recipe());stack.set(DataComponents.CUSTOM_DATA,CustomData.of(note));
            for(int i=1;i<=WorkshopRules.get().forming_strikes();i++) {
                var before=stack.copy();assertEquals(0,WorkshopStriking.reason(stack,ItemStack.EMPTY,1,2));
                var after=WorkshopStriking.result(stack,2);assertTrue(ItemStack.matches(before,stack));
                assertEquals(i,after.get(ModDataComponents.WORKPIECE.get()).blows());assertTrue(WorkshopItems.valid(after));
                assertEquals(before.get(DataComponents.CUSTOM_NAME),after.get(DataComponents.CUSTOM_NAME));
                assertEquals(before.get(DataComponents.CUSTOM_DATA),after.get(DataComponents.CUSTOM_DATA));stack=after;
            }
            assertEquals(2,WorkshopStriking.reason(stack,ItemStack.EMPTY,1,2));
        }
    }
    @Test void invalidColdBlockedAndCompletedWorkCannotReachContact() {
        var input=namedBlank();assertEquals(3,WorkshopStriking.reason(input,ItemStack.EMPTY,0,1));
        assertEquals(1,WorkshopStriking.reason(input,new ItemStack(Items.STICK),180,1));
        assertEquals(9,WorkshopStriking.reason(new ItemStack(Items.STICK),ItemStack.EMPTY,180,1));
        var malformed=input.copy();malformed.set(ModDataComponents.WORKPIECE.get(),new Workpiece("lingxiao_spear",1,0,0));
        assertEquals(9,WorkshopStriking.reason(malformed,ItemStack.EMPTY,180,1));
        for(int stage=2;stage<5;stage++)assertEquals(2,WorkshopStriking.reason(WorkshopItems.blank(new Workpiece("lingxiao_spear",stage,12,0)),ItemStack.EMPTY,180,1));
    }
    @Test void trainingKeepsPreviousCostsCapsAndArmorDirectionBoundaries() {
        for(String recipe:new String[]{"qingfeng_sword","forged_pickaxe","xuantie_chestplate"}) {
            var item=equipment(recipe);var craft=WorkshopEquipment.craft(item).orElseThrow();
            assertEquals(5,WorkshopStriking.reason(item,ItemStack.EMPTY,180,2));
            item.set(ModDataComponents.TEMPER.get(),new TemperRecord(6,0,0,0,2));
            item.set(DataComponents.CUSTOM_NAME,Component.literal("旧器"));item.setDamageValue(3);
            assertEquals(0,WorkshopStriking.reason(item,ItemStack.EMPTY,180,2));
            assertEquals(WorkshopRules.get().training_cost(),WorkshopStriking.costs(item));
            if(craft.armor())assertEquals(11,WorkshopStriking.reason(item,ItemStack.EMPTY,180,1));
            var result=WorkshopStriking.result(item,2);var record=WorkshopEquipment.record(result);
            assertEquals(0,record.experience());assertEquals(1,record.endurance());assertEquals(0,record.edge());assertEquals(0,record.channeling());
            assertEquals(item.get(DataComponents.CUSTOM_NAME),result.get(DataComponents.CUSTOM_NAME));assertEquals(3,result.getDamageValue());
            assertEquals(6,WorkshopEquipment.record(item).experience());
            result.set(ModDataComponents.TEMPER.get(),new TemperRecord(10000,0,craft.max_strikes(),0,2));
            assertEquals(6,WorkshopStriking.reason(result,ItemStack.EMPTY,180,2));
        }
    }
    @Test void hammerWearDoesNotReplayEquipButDifferentToolOrMetadataDoes() {
        var hammer=new ItemStack(ModItems.FORGING_HAMMER.get());var changed=hammer.copy();changed.setDamageValue(1);
        assertEquals(WorkshopRules.get().hammer_durability(),hammer.getMaxDamage());
        assertFalse(hammer.getItem().shouldCauseReequipAnimation(hammer,changed,false));
        assertTrue(hammer.getItem().shouldCauseReequipAnimation(hammer,changed,true));
        changed.set(DataComponents.CUSTOM_NAME,Component.literal("新锤"));assertTrue(hammer.getItem().shouldCauseReequipAnimation(hammer,changed,false));
    }
    @Test void panelShortcutWorksWithTheWorkpieceHammerAndFuelButKeepsBlockPlacement() {
        var hand=net.minecraft.world.InteractionHand.MAIN_HAND;
        for(var held:new ItemStack[]{namedBlank(),new ItemStack(ModItems.FORGING_HAMMER.get()),new ItemStack(Items.COAL),ItemStack.EMPTY}) {
            assertTrue(PhysicalForgeInteractions.requestsPanel(true,hand,held));
            assertFalse(PhysicalForgeInteractions.requestsPanel(false,hand,held));
            assertFalse(PhysicalForgeInteractions.requestsPanel(true,net.minecraft.world.InteractionHand.OFF_HAND,held));
        }
        assertFalse(PhysicalForgeInteractions.requestsPanel(true,hand,new ItemStack(Items.STONE)));
    }
}
