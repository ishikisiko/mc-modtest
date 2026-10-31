package com.example.myvillage.compat.jade;

import com.example.myvillage.client.manor.ManorJadeProvider;
import com.example.myvillage.manor.*;
import java.lang.reflect.Proxy;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import snownee.jade.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ManorJadeTest {
    @BeforeAll static void bootstrap() {
        if(LoadingModList.get()==null)LoadingModList.of(List.of(),List.of(),List.of(),List.of(),Map.of());
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
    }
    @Test void defaultHintShowsTheSemanticNameAndOnlyAnAvailableLocation() {
        var info=ManorPresentation.get().component("manor_lingyue_carved_7",21);
        var alone=ManorJadeProvider.hint(info,Optional.empty(),false,false,21);
        assertEquals("manor_part.myvillage.window",((TranslatableContents)alone.title().getContents()).getKey());
        assertTrue(alone.lines().isEmpty());
        var owned=ManorJadeProvider.hint(info,Optional.of(new ManorLocation.Name("灵岳·镇岳城主府",false)),false,false,21);
        assertEquals(List.of("灵岳·镇岳城主府"),owned.lines().stream().map(c->c.getString()).toList());
    }
    @Test void materialsAndStyleRequireDetailsAndShapeNumbersRequireDebug() {
        var info=ManorPresentation.get().component("manor_lingyue_carved_7",21);
        var detailed=ManorJadeProvider.hint(info,Optional.empty(),true,false,21);
        assertEquals(List.of("tooltip.myvillage.manor_materials","tooltip.myvillage.manor_style"),
                detailed.lines().stream().map(c->((TranslatableContents)c.getContents()).getKey()).toList());
        var debug=ManorJadeProvider.hint(info,Optional.empty(),false,true,21);
        assertEquals("tooltip.myvillage.manor_debug",((TranslatableContents)debug.lines().getFirst().getContents()).getKey());
    }
    @Test void cachedServerLocationCannotLeakAcrossPositionsDimensionsOrBlockStates() {
        var pos=new BlockPos(12,63,-25);var dimension=ResourceLocation.withDefaultNamespace("overworld");
        var state=Blocks.OAK_PLANKS.defaultBlockState();var root=new CompoundTag();
        var name=new ManorLocation.Name("灵岳·镇岳城主府",false);
        root.put(ManorJadeDataProvider.DATA,ManorJadeDataProvider.encode(dimension,pos,state,name));
        assertEquals(Optional.of(name),ManorJadeDataProvider.read(root,dimension,pos,state));
        assertTrue(ManorJadeDataProvider.read(root,dimension,pos.east(),state).isEmpty());
        assertTrue(ManorJadeDataProvider.read(root,ResourceLocation.withDefaultNamespace("the_nether"),pos,state).isEmpty());
        assertTrue(ManorJadeDataProvider.read(root,dimension,pos,Blocks.STONE.defaultBlockState()).isEmpty());
        assertTrue(ManorJadeDataProvider.read(new CompoundTag(),dimension,pos,state).isEmpty());
    }
    @Test void actualJadePluginRegistersTheOptionalProvidersAndLocationSetting() {
        var plugin=new ManorJadePlugin();assertNotNull(plugin.getClass().getAnnotation(WailaPlugin.class));
        List<String> calls=new ArrayList<>();
        var common=(IWailaCommonRegistration)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{IWailaCommonRegistration.class},(p,m,a)->{
            assertEquals("registerBlockDataProvider",m.getName());assertInstanceOf(ManorJadeDataProvider.class,a[0]);assertEquals(Block.class,a[1]);calls.add("server");return null;
        });
        var client=(IWailaClientRegistration)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{IWailaClientRegistration.class},(p,m,a)->{
            if(m.getName().equals("registerBlockComponent")) {assertInstanceOf(ManorJadeProvider.class,a[0]);assertEquals(Block.class,a[1]);calls.add("client");}
            else {assertEquals("addConfig",m.getName());assertEquals(ManorJadePlugin.LOCATION,a[0]);assertEquals(true,a[1]);calls.add("config");}return null;
        });
        plugin.register(common);plugin.registerClient(client);assertEquals(List.of("server","client","config"),calls);
        assertTrue(new ManorJadeProvider().getDefaultPriority()>TooltipPosition.HEAD-100,"名称替换必须在 Jade 默认标题之后执行");
    }
    @Test void unrelatedFunctionalBlocksDoNotRequestManorData() {
        var accessor=(BlockAccessor)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{BlockAccessor.class},(p,m,a)->{
            if(m.getName().equals("getBlockState"))return Blocks.FURNACE.defaultBlockState();
            throw new AssertionError("不应读取无关方块的世界或实体："+m.getName());
        });
        assertFalse(new ManorJadeDataProvider().shouldRequestData(accessor));
    }
}
