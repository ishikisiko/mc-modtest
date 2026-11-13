package com.example.myvillage.quest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import net.neoforged.fml.loading.LoadingModList;
import static org.junit.jupiter.api.Assertions.*;

class QuestGeneratorTest {
    @BeforeAll static void bootstrap(){
        if(LoadingModList.get()==null)LoadingModList.of(List.of(),List.of(),List.of(),List.of(),Map.of());
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
    }
    @Test void decoratingOneInstanceDoesNotCorruptTheNextInstancesAirLayer(){
        var biome=Holder.direct(new Biome.BiomeBuilder().hasPrecipitation(false).temperature(.5f).downfall(0)
                .specialEffects(new BiomeSpecialEffects.Builder().fogColor(0).waterColor(0).waterFogColor(0).skyColor(0).build())
                .mobSpawnSettings(MobSpawnSettings.EMPTY).generationSettings(BiomeGenerationSettings.EMPTY).build());
        var settings=new FlatLevelGeneratorSettings(Optional.empty(),biome,List.of());
        settings.getLayersInfo().add(new FlatLayerInfo(1,Blocks.AIR));settings.updateLayers();
        var template=new FlatLevelSource(settings);
        var first=QuestRuntime.freshGenerator(template);
        first.settings().adjustGenerationSettings(biome);
        assertNull(first.settings().getLayers().getFirst());
        assertSame(Blocks.AIR.defaultBlockState(),template.settings().getLayers().getFirst());
        var second=QuestRuntime.freshGenerator(template);
        assertNotSame(first.settings(),second.settings());
        assertDoesNotThrow(()->second.settings().adjustGenerationSettings(biome));
        assertSame(Blocks.AIR.defaultBlockState(),template.settings().getLayers().getFirst());
    }
}
