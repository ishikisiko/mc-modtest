package com.example.myvillage.client.sim;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.sim.runtime.net.ScriptureHallPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client side of the scripture hall: installs the receiver of {@link ScriptureHallPayload} (common
 * code hands halls to it, as for the sect dialogue) and opens a {@link ScriptureHallScreen}, or
 * refreshes the open one when the hall is from the same shelf. A hall that arrives while another
 * kind of screen is open is dropped, never shown over it. Closes the hall when the player
 * has walked away from the shelf ({@value #CLOSE_DISTANCE} blocks); the server checks the real
 * range on every borrow.
 */
@EventBusSubscriber(modid = MyVillageMod.MOD_ID, value = Dist.CLIENT)
public final class ClientScriptureHall {
    static final double CLOSE_DISTANCE = 10.0;
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientScriptureHall.class);

    static {
        ScriptureHallPayload.installReceiver(ClientScriptureHall::receive);
    }

    private ClientScriptureHall() {
    }

    static void receive(ScriptureHallPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof ScriptureHallScreen open && open.pos().equals(payload.pos())) {
            open.update(payload);
        } else if (minecraft.screen == null || minecraft.screen instanceof ScriptureHallScreen) {
            minecraft.setScreen(new ScriptureHallScreen(payload));
        } else {
            LOGGER.debug("Scripture hall of shelf {} dropped: {} is open", payload.pos().toShortString(),
                    minecraft.screen.getClass().getSimpleName());
        }
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof ScriptureHallScreen open)) {
            return;
        }
        if (minecraft.level == null || minecraft.player == null) {
            open.onClose();
            return;
        }
        if (minecraft.player.distanceToSqr(Vec3.atCenterOf(open.pos())) > CLOSE_DISTANCE * CLOSE_DISTANCE) {
            open.onClose();
        }
    }
}
