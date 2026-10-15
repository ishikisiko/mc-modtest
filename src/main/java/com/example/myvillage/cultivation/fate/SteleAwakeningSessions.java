package com.example.myvillage.cultivation.fate;

import com.example.myvillage.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A right click grants access only while the player remains at that exact testing stele. */
public final class SteleAwakeningSessions {
    private static final Logger LOGGER = LoggerFactory.getLogger(SteleAwakeningSessions.class);
    private static final SteleSessionBook SESSIONS = new SteleSessionBook();
    private SteleAwakeningSessions() { }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(SteleAwakeningSessions::tick);
        NeoForge.EVENT_BUS.addListener(SteleAwakeningSessions::logout);
        NeoForge.EVENT_BUS.addListener(SteleAwakeningSessions::dimension);
        NeoForge.EVENT_BUS.addListener(SteleAwakeningSessions::death);
        NeoForge.EVENT_BUS.addListener(SteleAwakeningSessions::teleport);
        NeoForge.EVENT_BUS.addListener(SteleAwakeningSessions::stop);
    }

    public static long open(ServerPlayer player, BlockPos pos) {
        if (!playerValid(player) || !testingStele(player, pos) || !player.canInteractWithBlock(pos, 0)) return 0;
        var session = SESSIONS.open(player.getUUID(), player.level().dimension().location().toString(), pos.asLong());
        LOGGER.info("AWAKENING_SESSION open player={} token={} pos={}", player.getScoreboardName(), session.token(), pos.toShortString());
        return session.token();
    }

    public static boolean valid(ServerPlayer player, long token) {
        var session = SESSIONS.current(player.getUUID());
        if (session == null) return false;
        BlockPos pos = BlockPos.of(session.position());
        boolean sameDimension = session.dimension().equals(player.level().dimension().location().toString());
        return SESSIONS.validate(player.getUUID(), token, player.level().dimension().location().toString(),
                playerValid(player), sameDimension && testingStele(player, pos), player.canInteractWithBlock(pos, 0));
    }

    public static long currentToken(ServerPlayer player) {
        var session = SESSIONS.current(player.getUUID());
        return session == null ? 0 : session.token();
    }

    public static void close(ServerPlayer player, long token) {
        if (SESSIONS.close(player.getUUID(), token))
            LOGGER.info("AWAKENING_SESSION close player={} token={}", player.getScoreboardName(), token);
    }

    public static void forget(ServerPlayer player) { SESSIONS.forget(player.getUUID()); }

    private static boolean testingStele(ServerPlayer player, BlockPos pos) {
        return player.serverLevel().hasChunkAt(pos)
                && player.serverLevel().getBlockState(pos).is(ModBlocks.SPIRIT_TESTING_STELE.get());
    }

    private static boolean playerValid(ServerPlayer player) {
        return player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && player.getServer().getPlayerList().getPlayer(player.getUUID()) == player;
    }

    private static void tick(ServerTickEvent.Post event) {
        SESSIONS.snapshot().forEach((id, session) -> {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(id);
            if (player == null) SESSIONS.forget(id);
            else valid(player, session.token());
        });
    }
    private static void logout(PlayerEvent.PlayerLoggedOutEvent event) { SESSIONS.forget(event.getEntity().getUUID()); }
    private static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { SESSIONS.forget(event.getEntity().getUUID()); }
    private static void death(LivingDeathEvent event) { SESSIONS.forget(event.getEntity().getUUID()); }
    private static void teleport(EntityTeleportEvent event) { SESSIONS.forget(event.getEntity().getUUID()); }
    private static void stop(ServerStoppingEvent event) { SESSIONS.clear(); }
}
