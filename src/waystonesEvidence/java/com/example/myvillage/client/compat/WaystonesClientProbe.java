package com.example.myvillage.client.compat;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.client.cultivation.ClientCultivationState;
import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import net.blay09.mods.waystones.client.gui.screen.WaystoneSelectionScreenBase;
import net.blay09.mods.waystones.client.gui.widget.WaystoneButton;
import net.blay09.mods.waystones.requirement.ItemRequirement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.LoggerFactory;

/** Evidence-only observation of the actual native button requirement, never a player input. */
@EventBusSubscriber(modid = MyVillageMod.MOD_ID, value = Dist.CLIENT)
public final class WaystonesClientProbe {
    private static final Set<String> SEEN = new HashSet<>();
    private static final Field REQUIREMENT = requirementField();

    private static Field requirementField() {
        try {
            var field = WaystoneButton.class.getDeclaredField("warpRequirement");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.screen instanceof WaystoneSelectionScreenBase) observe(minecraft.screen);
    }

    private static void observe(GuiEventListener widget) {
        if (widget instanceof WaystoneButton button) {
            try {
                if (REQUIREMENT.get(button) instanceof ItemRequirement fee) {
                    var result = new JsonObject();
                    result.addProperty("fee_count", fee.getCount());
                    result.addProperty("can_afford", fee.canAfford(Minecraft.getInstance().player));
                    result.addProperty("active", button.active);
                    result.addProperty("realm", ClientCultivationState.latest().map(value -> value.realmId().toString()).orElse("missing"));
                    if (SEEN.add(result.toString())) LoggerFactory.getLogger(WaystonesClientProbe.class).info("WAYSTONES_MENU_PRICE {}", result);
                }
            } catch (IllegalAccessException exception) { throw new IllegalStateException(exception); }
        }
        if (widget instanceof ContainerEventHandler parent) for (var child : parent.children()) observe(child);
    }
}
