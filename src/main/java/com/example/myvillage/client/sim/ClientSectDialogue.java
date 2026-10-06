package com.example.myvillage.client.sim;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.sim.runtime.net.SectDialoguePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Client side of the sect dialogue: installs the receiver of {@link SectDialoguePayload} (common
 * code hands pages to it, as {@code CombatDodgeReceiver} does for combat) and opens a
 * {@link SectDialogueScreen}, or refreshes the open one when the page is from the same avatar.
 * Closes the dialogue when its avatar is gone or the player has walked away
 * ({@value #CLOSE_DISTANCE} blocks); the server checks the real range on every choice.
 */
@EventBusSubscriber(modid = MyVillageMod.MOD_ID, value = Dist.CLIENT)
public final class ClientSectDialogue {
    static final double CLOSE_DISTANCE = 10.0;

    static {
        SectDialoguePayload.installReceiver(ClientSectDialogue::receive);
    }

    private ClientSectDialogue() {
    }

    static void receive(SectDialoguePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof SectDialogueScreen open && open.entityId() == payload.entityId()) {
            open.update(payload);
        } else {
            minecraft.setScreen(new SectDialogueScreen(payload));
        }
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof SectDialogueScreen open)) {
            return;
        }
        if (minecraft.level == null || minecraft.player == null) {
            open.onClose();
            return;
        }
        Entity avatar = minecraft.level.getEntity(open.entityId());
        if (avatar == null || avatar.isRemoved()
                || avatar.distanceToSqr(minecraft.player) > CLOSE_DISTANCE * CLOSE_DISTANCE) {
            open.onClose();
        }
    }
}
