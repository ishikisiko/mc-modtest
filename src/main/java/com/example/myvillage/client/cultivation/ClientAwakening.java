package com.example.myvillage.client.cultivation;

import com.example.myvillage.cultivation.network.AwakeningOpenPayload;
import net.minecraft.client.Minecraft;

/** No pending opener: an invalid lifecycle cannot reopen an old awakening screen later. */
public final class ClientAwakening {
    private static long latestToken;
    private ClientAwakening() { }
    static void open(AwakeningOpenPayload payload) {
        if (payload.sessionToken() <= latestToken || payload.sessionToken() <= 0) return;
        latestToken = payload.sessionToken();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || !minecraft.player.isAlive()
                || minecraft.getConnection() == null
                || !minecraft.player.canInteractWithBlock(payload.stelePos(), 0)
                || !minecraft.level.hasChunkAt(payload.stelePos())
                || !minecraft.level.getBlockState(payload.stelePos()).is(com.example.myvillage.block.ModBlocks.SPIRIT_TESTING_STELE.get())) {
            ClientCultivationIntentSender.sendAwakeningClose(payload.sessionToken());
            return;
        }
        minecraft.setScreen(new AwakeningScreen(payload.stelePos(), payload.sessionToken()));
    }
    static void clear() { latestToken = 0; }
}
