package com.example.myvillage.storagebag;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.item.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class StorageBagContent {
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MyVillageMod.MOD_ID);
    public static final Supplier<MenuType<StorageBagMenu>> BAG_MENU = MENUS.register("storage_bag",
            () -> IMenuTypeExtension.create(StorageBagMenu::new));
    public static final Supplier<MenuType<StorageBagForgeMenu>> FORGE_MENU = MENUS.register("storage_bag_forge",
            () -> IMenuTypeExtension.create(StorageBagForgeMenu::new));
    private static final Map<StorageBagRules.Kind, List<DeferredItem<StorageBagItem>>> ITEMS =
            new EnumMap<>(StorageBagRules.Kind.class);

    private StorageBagContent() {}

    public static void register(IEventBus bus) {
        var tiers = StorageBagRules.tiers();
        for (var kind : StorageBagRules.Kind.values()) {
            ITEMS.put(kind, tiers.stream().map(tier -> ModItems.ITEMS.registerItem(tier.itemId(kind),
                    properties -> new StorageBagItem(properties, kind, tier))).toList());
        }
        MENUS.register(bus);
    }

    public static void creative(CreativeModeTab.Output output) {
        for (var list : ITEMS.values()) list.forEach(item -> output.accept(item.get()));
    }

    static ItemStack result(StorageBagRules.Kind kind, int tier, ItemStack original) {
        var item = ITEMS.get(kind).get(tier).get();
        return original.isEmpty() ? new ItemStack(item) : original.transmuteCopy(item);
    }
}
