package com.example.myvillage.cultivation.fate;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SpiritBodyAttributesTest {
    @BeforeAll static void bootstrap(){if(LoadingModList.get()==null)LoadingModList.of(List.of(),List.of(),List.of(),List.of(),Map.of());SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    private ResourceLocation id(String n){return ResourceLocation.parse("myvillage:"+n);}
    @Test void independentContributionDoesNotMagnifyGearOrReceiveOtherMultipliers() {
        var attribute=new AttributeInstance(Holder.direct(new RangedAttribute("test",20,1,1024)),ignored->{});
        attribute.setBaseValue(20);
        attribute.addPermanentModifier(new AttributeModifier(id("gear"),10,AttributeModifier.Operation.ADD_VALUE));
        attribute.addPermanentModifier(new AttributeModifier(id("other_fate"),.2,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        assertEquals(36,attribute.getValue(),1e-9);
        SpiritBodyAttributes.addition(attribute,id("body"),6);assertEquals(42,attribute.getValue(),1e-9);
        SpiritBodyAttributes.addition(attribute,id("body"),6);assertEquals(42,attribute.getValue(),1e-9);
        attribute.addPermanentModifier(new AttributeModifier(id("temporary"),.5,AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        SpiritBodyAttributes.addition(attribute,id("body"),6);assertEquals(60,attribute.getValue(),1e-9);
        SpiritBodyAttributes.addition(attribute,id("body"),0);assertEquals(54,attribute.getValue(),1e-9);
    }
    @Test void independentContributionHandlesZeroOrInvalidMultipliersConservatively() {
        assertEquals(0,SpiritBodyAttributes.isolatedAddition(6,0,1));
        assertEquals(5,SpiritBodyAttributes.isolatedAddition(6,1,1.2));
        assertThrows(IllegalArgumentException.class,()->SpiritBodyAttributes.isolatedAddition(Double.NaN,1,1));
    }
}
