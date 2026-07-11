package com.example.myvillage.client;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.entity.RideableFlyingSwordEntity;
import com.example.myvillage.network.FlyingSwordInputPayload;
import net.minecraft.client.player.Input;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = MyVillageMod.MOD_ID, value = Dist.CLIENT)
public final class FlyingSwordClientInput {
    private FlyingSwordClientInput() {
    }

    @SubscribeEvent
    static void sendRidingInput(MovementInputUpdateEvent event) {
        if (!(event.getEntity().getVehicle() instanceof RideableFlyingSwordEntity)) {
            return;
        }

        Input input = event.getInput();
        FlyingSwordInputPayload payload = FlyingSwordInputPayload.fromKeys(
                input.up,
                input.down,
                input.left,
                input.right,
                input.jumping,
                input.shiftKeyDown);
        PacketDistributor.sendToServer(payload);

        // Shift controls descent here, so do not let vanilla treat it as dismount input.
        input.shiftKeyDown = false;
    }
}
