package com.example.myvillage.client.cultivation;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.cultivation.network.CultivationSnapshotReceiver;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

@EventBusSubscriber(
        modid = MyVillageMod.MOD_ID,
        value = Dist.CLIENT)
public final class ClientCultivationEvents {
    static {
        CultivationSnapshotReceiver.install(ClientCultivationState::replace);
    }

    private ClientCultivationEvents() {
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientCultivationState.clear();
    }
}
