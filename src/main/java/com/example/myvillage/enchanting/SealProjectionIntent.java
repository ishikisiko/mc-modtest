package com.example.myvillage.enchanting;

import com.example.myvillage.estate.EstateRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public record SealProjectionIntent(BlockPos controller, int slot) implements CustomPacketPayload {
    public static final Type<SealProjectionIntent> TYPE = new Type<>(EnchantingRules.id("seal_projection"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SealProjectionIntent> STREAM_CODEC = StreamCodec.of(
            (buf, intent) -> { buf.writeBlockPos(intent.controller()); buf.writeByte(intent.slot()); },
            buf -> new SealProjectionIntent(buf.readBlockPos(), buf.readUnsignedByte()));

    @Override public Type<SealProjectionIntent> type() { return TYPE; }

    public void handle(ServerPlayer player) {
        var level = player.serverLevel();
        if (!player.isAlive() || player.isSpectator() || player.containerMenu != player.inventoryMenu
                || slot < 0 || slot >= SealProjection.SLOTS.size() || !level.isLoaded(controller)
                || player.distanceToSqr(controller.getX() + .5, controller.getY() + .5, controller.getZ() + .5) > 196
                || !level.mayInteract(player, controller)
                || EstateRuntime.blocked(player, level, controller.getX() + .5, controller.getZ() + .5)
                || !(level.getBlockEntity(controller) instanceof EnchantingDevice device) || !device.seal()
                || !device.formed()) return;
        var hit = SealProjection.pick(player, controller, device.front());
        if (hit != null && hit.slot() == slot && device.verifyStructure()) device.useDisc(player, slot);
    }
}
