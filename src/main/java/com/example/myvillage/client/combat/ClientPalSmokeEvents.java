package com.example.myvillage.client.combat;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.combat.definition.BasicSwordStyle;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventBusSubscriber(modid = MyVillageMod.MOD_ID, value = Dist.CLIENT)
public final class ClientPalSmokeEvents {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientPalSmokeEvents.class);

    private ClientPalSmokeEvents() {
    }

    @SubscribeEvent
    static void registerCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("myvillage_pal_smoke")
                .then(Commands.literal("play").executes(context -> play()))
                .then(Commands.literal("move")
                        .then(Commands.argument("index", IntegerArgumentType.integer(
                                        1, BasicSwordStyle.DEFINITION.moves().size()))
                                .executes(context -> playMove(
                                        IntegerArgumentType.getInteger(context, "index")))))
                .then(Commands.literal("first_person")
                        .then(Commands.literal("release").executes(context -> releaseFirstPerson()))
                        .then(Commands.argument("index", IntegerArgumentType.integer(
                                        1, BasicSwordStyle.DEFINITION.moves().size()))
                                .then(Commands.argument("tick", FloatArgumentType.floatArg(0.0F))
                                        .executes(context -> probeFirstPerson(
                                                IntegerArgumentType.getInteger(context, "index"),
                                                FloatArgumentType.getFloat(context, "tick"))))))
                .then(Commands.literal("third_person")
                        .then(Commands.literal("release").executes(context -> releaseThirdPerson()))
                        .then(Commands.argument("index", IntegerArgumentType.integer(
                                        1, BasicSwordStyle.DEFINITION.moves().size()))
                                .then(Commands.argument("tick", FloatArgumentType.floatArg(0.0F))
                                        .executes(context -> probeThirdPerson(
                                                IntegerArgumentType.getInteger(context, "index"),
                                                FloatArgumentType.getFloat(context, "tick"))))))
                .then(Commands.literal("transition").executes(context -> transition()))
                .then(Commands.literal("stop").executes(context -> stop()))
                .then(Commands.literal("status").executes(context -> status())));
        LOGGER.info("PAL_SMOKE client_commands_registered");
    }

    private static int play() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && CombatAnimationController.play(
                player,
                CombatAnimationController.SMOKE_ANIMATION,
                0.0F) ? 1 : 0;
    }

    private static int transition() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && CombatAnimationController.transition(
                player,
                CombatAnimationController.SMOKE_ANIMATION) ? 1 : 0;
    }

    private static int playMove(int oneBasedIndex) {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && CombatAnimationController.play(
                player,
                BasicSwordStyle.DEFINITION.move(oneBasedIndex - 1).animation().animationId(),
                0.0F) ? 1 : 0;
    }

    private static int probeFirstPerson(int oneBasedIndex, float tick) {
        QingfengFirstPersonAnimator.probe(oneBasedIndex - 1, tick);
        LOGGER.info("PAL_SMOKE first_person move={} tick={}", oneBasedIndex, tick);
        return 1;
    }

    private static int releaseFirstPerson() {
        QingfengFirstPersonAnimator.releaseProbe();
        return 1;
    }

    /**
     * Holds the local player's third-person pose of one move at one tick. The hold ends with
     * {@code third_person release}, {@code stop}, a real action, or logout. Sends nothing.
     */
    private static int probeThirdPerson(int oneBasedIndex, float tick) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return 0;
        }
        boolean held = CombatAnimationController.holdThirdPersonProbe(
                player,
                BasicSwordStyle.DEFINITION.move(oneBasedIndex - 1).animation().animationId(),
                tick);
        if (!held) {
            return 0;
        }
        ClientCombatState.clearReadyAnimation();
        LOGGER.info("PAL_SMOKE third_person move={} tick={}", oneBasedIndex, tick);
        return 1;
    }

    private static int releaseThirdPerson() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return 0;
        }
        CombatAnimationController.releaseThirdPersonProbe(player);
        ClientCombatState.clearReadyAnimation();
        LOGGER.info("PAL_SMOKE third_person release");
        return 1;
    }

    private static int stop() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && CombatAnimationController.stop(player) ? 1 : 0;
    }

    private static int status() {
        LocalPlayer player = Minecraft.getInstance().player;
        boolean active = player != null && CombatAnimationController.isActive(player);
        LOGGER.info("PAL_SMOKE status player_present={} active={}", player != null, active);
        return active ? 1 : 0;
    }
}
