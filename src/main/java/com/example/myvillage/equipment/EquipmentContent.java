package com.example.myvillage.equipment;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Adding another authored armor set needs catalogue data, not another renderer/registration class. */
public final class EquipmentContent {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("myvillage");
    private static final Map<ResourceLocation, DeferredItem<EquipmentArmorItem>> ARMOR = new LinkedHashMap<>();

    static {
        for (var entry : EquipmentCatalogue.bundled()) {
            ARMOR.put(entry.id(), ITEMS.registerItem(entry.id().getPath(), p -> new EquipmentArmorItem(p, entry)));
        }
    }

    private EquipmentContent() { }
    public static void register(IEventBus bus) { ITEMS.register(bus); }
    public static void creative(CreativeModeTab.Output output) { ARMOR.values().forEach(item -> output.accept(item.get())); }
    public static EquipmentArmorItem[] items() { return ARMOR.values().stream().map(DeferredItem::get).toArray(EquipmentArmorItem[]::new); }
}
