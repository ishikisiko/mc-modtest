package com.example.myvillage.sim.runtime.life;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NpcGardenRuntimeTest {
    @BeforeAll static void bootstrap() {
        if (LoadingModList.get() == null) LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void actualLootMustFitWithoutDiscardingOtherItemsOrComponents() {
        assertEquals(3, NpcGardenRuntime.harvestUnits(List.of(new ItemStack(Items.WHEAT, 1),
                new ItemStack(Items.WHEAT, 2)), Items.WHEAT, 3));
        assertEquals(-1, NpcGardenRuntime.harvestUnits(List.of(new ItemStack(Items.WHEAT, 4)), Items.WHEAT, 3));
        assertEquals(-1, NpcGardenRuntime.harvestUnits(List.of(new ItemStack(Items.WHEAT, 2),
                new ItemStack(Items.WHEAT_SEEDS)), Items.WHEAT, 32));
        var named = new ItemStack(Items.WHEAT);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("特殊药材"));
        assertEquals(-1, NpcGardenRuntime.harvestUnits(List.of(named), Items.WHEAT, 32));
        assertEquals(0, NpcGardenRuntime.harvestUnits(List.of(ItemStack.EMPTY), Items.WHEAT, 32));
    }

    @Test void retriesAndReplacementWorkersCannotRerollOneHarvest() {
        long same = NpcGardenRuntime.harvestSeed(20261026, 123456, 7);
        for (int retry = 0; retry < 20; retry++)
            assertEquals(same, NpcGardenRuntime.harvestSeed(20261026, 123456, 7));
        assertNotEquals(same, NpcGardenRuntime.harvestSeed(20261026, 123456, 8));
        assertNotEquals(0, NpcGardenRuntime.harvestSeed(0, 0, 0), "zero opts into random loot-table seeds");
    }
}
