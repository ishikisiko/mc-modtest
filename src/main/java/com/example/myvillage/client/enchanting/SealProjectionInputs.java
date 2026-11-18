package com.example.myvillage.client.enchanting;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.enchanting.EnchantingDevice;
import com.example.myvillage.enchanting.SealProjection;
import com.example.myvillage.enchanting.SealProjectionIntent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = MyVillageMod.MOD_ID, value = Dist.CLIENT)
public final class SealProjectionInputs {
    private SealProjectionInputs() {}

    @SubscribeEvent
    public static void interact(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND) return;
        var client = Minecraft.getInstance();
        var player = client.player;
        if (client.level == null || player == null || player.isSpectator() || client.screen != null) return;
        var pos = player.blockPosition();
        EnchantingDevice owner = null;
        SealProjection.Hit best = null;
        for (int x = (pos.getX() - 14) >> 4; x <= (pos.getX() + 14) >> 4; x++) {
            for (int z = (pos.getZ() - 14) >> 4; z <= (pos.getZ() + 14) >> 4; z++) {
                if (!(client.level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false) instanceof LevelChunk chunk)) continue;
                for (var entity : chunk.getBlockEntities().values()) {
                    if (!(entity instanceof EnchantingDevice seal) || !seal.formed() || !seal.seal()) continue;
                    var hit = SealProjection.pick(player, seal.getBlockPos(), seal.front());
                    if (hit != null && (best == null || hit.distance() < best.distance())) {
                        owner = seal;
                        best = hit;
                    }
                }
            }
        }
        if (owner == null) return;
        event.setCanceled(true);
        event.setSwingHand(true);
        PacketDistributor.sendToServer(new SealProjectionIntent(owner.getBlockPos(), best.slot()));
    }
}
