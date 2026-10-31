package com.example.myvillage.manor;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlockItemStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ManorPresentationTest {
    @BeforeAll static void bootstrap() {
        if(net.neoforged.fml.loading.LoadingModList.get()==null)
            net.neoforged.fml.loading.LoadingModList.of(java.util.List.of(),java.util.List.of(),java.util.List.of(),java.util.List.of(),Map.of());
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
    }
    @Test void realWindowNameComesFromItsShapeAndIncludesEveryMaterial() {
        var info=ManorPresentation.get().component("manor_lingyue_carved_7",21);
        assertEquals("lingyue",info.city());
        assertEquals("manor_part.myvillage.window",((TranslatableContents)info.name().getContents()).getKey());
        assertEquals(java.util.List.of("manor_material.myvillage.frame","manor_material.myvillage.window","manor_material.myvillage.wood"),info.materials());
        assertNotEquals(info.nameKey(),ManorPresentation.get().component("manor_lingyue_carved_7",0).nameKey());
    }
    @Test void everyRegisteredShapeHasAnIndependentTranslatedDescription() throws Exception {
        try(var input=getClass().getResourceAsStream("/assets/myvillage/lang/zh_cn.json")) {
            assertNotNull(input);var lang=JsonParser.parseString(new String(input.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
            int total=0;
            for(var bank:ManorCatalog.get().banks)for(int shape=0;shape<32;shape++) {
                var info=ManorPresentation.get().component(bank.id(),shape);assertTrue(lang.has(info.nameKey()));
                String name=lang.get(info.nameKey()).getAsString();assertFalse(name.contains("精雕"));assertFalse(name.matches(".*[0-9].*"));
                assertFalse(info.materials().isEmpty());info.materials().forEach(material -> assertTrue(lang.has(material)));total++;
            }
            assertEquals(15776,total);
        }
    }
    @Test void itemStateSelectionMatchesPlacementAndRejectsInvalidShapeValues() {
        var stack=new ItemStack(Items.STICK);assertEquals(0,ManorPresentation.shape(stack));
        for(int shape=0;shape<32;shape++) {
            stack.set(DataComponents.BLOCK_STATE,new BlockItemStateProperties(Map.of("shape",Integer.toString(shape),"facing","east")));
            assertEquals(shape,ManorPresentation.shape(stack));
        }
        for(String value:java.util.List.of("-1","32","99999999999999999","window","")) {
            stack.set(DataComponents.BLOCK_STATE,new BlockItemStateProperties(Map.of("shape",value)));
            assertEquals(0,ManorPresentation.shape(stack));
        }
    }
    @Test void unrelatedBlocksAndItemsKeepTheirOwnNamesAndTooltips() {
        assertTrue(ManorPresentation.get().describe(net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState()).isEmpty());
        assertTrue(ManorPresentation.get().describe(new ItemStack(Items.STICK)).isEmpty());
        assertThrows(IllegalArgumentException.class,() -> ManorPresentation.get().component("unknown",0));
        assertThrows(IllegalArgumentException.class,() -> ManorPresentation.get().component("manor_lingyue_carved_7",32));
    }
}
