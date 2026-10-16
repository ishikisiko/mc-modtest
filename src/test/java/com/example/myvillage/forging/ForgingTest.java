package com.example.myvillage.forging;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ForgingTest {
    @BeforeAll static void bootstrap(){
        if(LoadingModList.get()==null)LoadingModList.of(List.of(),List.of(),List.of(),List.of(),Map.of());
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
    }
    private String source() throws Exception{
        try(var input=ForgingRules.class.getResourceAsStream(ForgingRules.RESOURCE)){return new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
    }
    private ForgingRules vanillaRules() throws Exception{return ForgingRules.parse(new StringReader(source().replace("myvillage:qingfeng_sword","minecraft:diamond_sword")));}
    private DataComponentType<ForgeMark> type(){return DataComponentType.<ForgeMark>builder().persistent(ForgeMark.CODEC).networkSynchronized(ForgeMark.STREAM_CODEC).build();}
    @Test void everyWeightedOutcomeRespectsEveryCap(){
        var rules=ForgingRules.get();
        for(int cap=1;cap<=4;cap++){
            Set<Integer> outcomes=new HashSet<>();
            for(int draw=0;draw<rules.total(cap);draw++){
                int q=rules.roll(cap,draw);assertTrue(q>=1&&q<=cap);outcomes.add(q);
            }
            assertEquals(cap,outcomes.size());
        }
        assertThrows(IllegalArgumentException.class,()->rules.roll(0,0));
        assertThrows(IllegalArgumentException.class,()->rules.roll(4,-1));
        assertThrows(IllegalArgumentException.class,()->rules.roll(4,rules.total(4)));
    }
    @Test void malformedConfigurationCannotProduceFreeOrAboveCapWeapons() throws Exception{
        for(String malformed:List.of(source().replace("\"version\": 1","\"version\": 1, \"version\": 1"),
                source().replace("\"minecraft:diamond\": 2","\"minecraft:diamond\": 0"),
                source().replace("\"minecraft:diamond\": 2","\"minecraft:diamond\": 1.5"),source()+" {}"))
            assertThrows(IllegalArgumentException.class,()->ForgingRules.parse(new StringReader(malformed)));
        var json=JsonParser.parseString(source()).getAsJsonObject();json.getAsJsonArray("quality_weights").get(0).getAsJsonArray().set(3,new JsonPrimitive(1));
        assertThrows(IllegalArgumentException.class,()->ForgingRules.parse(new StringReader(json.toString())));
        assertThrows(IllegalArgumentException.class,()->ForgingRules.get().costs(ForgingRules.get().recipe("lingxiao_spear").orElseThrow(),"channeling"));
    }
    @Test void metadataSurvivesPersistentAndWireRoundTripsRejectingBadInputs(){
        for(String trait:ForgeMark.TRAITS)for(int q=1;q<=4;q++){
            var mark=new ForgeMark(trait,q);
            assertEquals(mark,ForgeMark.CODEC.parse(JsonOps.INSTANCE,ForgeMark.CODEC.encodeStart(JsonOps.INSTANCE,mark).getOrThrow()).getOrThrow());
            var bytes=Unpooled.buffer();try{ForgeMark.STREAM_CODEC.encode(bytes,mark);assertEquals(mark,ForgeMark.STREAM_CODEC.decode(bytes));assertFalse(bytes.isReadable());}finally{bytes.release();}
        }
        for(String json:List.of("{\"trait\":\"sharp\",\"quality\":0}","{\"trait\":\"sharp\",\"quality\":5}","{\"trait\":\"cheat\",\"quality\":4}"))
            assertTrue(ForgeMark.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString(json)).error().isPresent());
        var bytes=Unpooled.buffer();try{ByteBufCodecs.STRING_UTF8.encode(bytes,"sharp");ByteBufCodecs.VAR_INT.encode(bytes,99);assertThrows(IllegalArgumentException.class,()->ForgeMark.STREAM_CODEC.decode(bytes));}finally{bytes.release();}
    }
    @Test void missingLastIngredientDoesNotConsumeAnyEarlierStacks(){
        ItemStack named=new ItemStack(Items.IRON_INGOT,5);named.set(DataComponents.CUSTOM_NAME,Component.literal("kept"));
        List<ItemStack> items=List.of(new ItemStack(Items.DIAMOND,1),new ItemStack(Items.DIAMOND,2),named);
        assertTrue(ForgingCosts.plan(items,Map.of("minecraft:diamond",3,"minecraft:quartz",1)).isEmpty());
        assertEquals(1,items.get(0).getCount());assertEquals(2,items.get(1).getCount());assertEquals(5,named.getCount());
        var plan=ForgingCosts.plan(items,Map.of("minecraft:diamond",3,"minecraft:iron_ingot",2)).orElseThrow();
        assertTrue(plan.get(0).isEmpty());assertTrue(plan.get(1).isEmpty());assertEquals(3,plan.get(2).getCount());
        assertEquals(named.get(DataComponents.CUSTOM_NAME),plan.get(2).get(DataComponents.CUSTOM_NAME));
        assertEquals(5,named.getCount());
    }
    @Test void forgedAttributesAreSingleMainHandModifiersAndDoNotAlterTheOriginal() throws Exception{
        var rules=vanillaRules();var type=type();var original=new ItemStack(Items.DIAMOND_SWORD);var result=original.copy();
        var before=result.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,ItemAttributeModifiers.EMPTY);
        ForgedEquipment.apply(result,new ForgeMark("sharp",4),type,rules);
        var after=result.get(DataComponents.ATTRIBUTE_MODIFIERS);
        assertEquals(before.modifiers().size()+1,after.modifiers().size());
        var bonus=after.modifiers().getLast();assertEquals(Attributes.ATTACK_DAMAGE,bonus.attribute());
        assertEquals(EquipmentSlotGroup.MAINHAND,bonus.slot());assertEquals(rules.trait("sharp").value(4),bonus.modifier().amount());
        assertFalse(original.has(type));assertEquals(before,original.get(DataComponents.ATTRIBUTE_MODIFIERS));
        assertThrows(IllegalArgumentException.class,()->ForgedEquipment.apply(result,new ForgeMark("sharp",4),type,rules));
        assertEquals(after,result.get(DataComponents.ATTRIBUTE_MODIFIERS));
    }
    @Test void toughRepairPreservesMetadataNameAndDoesNotExceedMaximum() throws Exception{
        var rules=vanillaRules();var type=type();var sword=new ItemStack(Items.DIAMOND_SWORD);int base=sword.getMaxDamage();
        sword.set(DataComponents.CUSTOM_NAME,Component.literal("Old friend"));
        var mark=new ForgeMark("tough",4);ForgedEquipment.apply(sword,mark,type,rules);
        assertEquals((int)Math.ceil(base*rules.trait("tough").value(4)),sword.getMaxDamage());
        sword.setDamageValue(1000);var repaired=sword.copy();
        repaired.setDamageValue(Math.max(0,repaired.getDamageValue()-ForgedEquipment.repairAmount(repaired,mark,rules)));
        assertEquals(0,repaired.getDamageValue());assertEquals(mark,repaired.get(type));
        assertEquals(sword.get(DataComponents.CUSTOM_NAME),repaired.get(DataComponents.CUSTOM_NAME));assertEquals(1000,sword.getDamageValue());
    }
    @Test void channelingOnlyDiscountsItsOwnCostAndNeverMakesPaidSkillsFree(){
        var rules=ForgingRules.get();
        assertEquals(100,ForgedEquipment.qiCost(100,null,rules));assertEquals(100,ForgedEquipment.qiCost(100,new ForgeMark("sharp",4),rules));
        assertEquals(75,ForgedEquipment.qiCost(100,new ForgeMark("channeling",4),rules));
        assertEquals(1,ForgedEquipment.qiCost(1,new ForgeMark("channeling",4),rules));
        assertEquals(0,ForgedEquipment.qiCost(0,new ForgeMark("channeling",4),rules));
        assertThrows(IllegalArgumentException.class,()->ForgedEquipment.qiCost(-1,null,rules));
    }
}
