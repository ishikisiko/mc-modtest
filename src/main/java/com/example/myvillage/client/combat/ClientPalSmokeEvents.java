package com.example.myvillage.client.combat;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.combat.definition.AttackMoveDefinition;
import com.example.myvillage.combat.definition.CombatStyleDefinition;
import com.example.myvillage.combat.definition.CombatStyles;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Client-only PAL smoke probes. Pose probes only: none of them sends a payload, and none
 * substitutes for mapped clicks or server evidence. Move indexes are one-based within the style
 * of the weapon in the main hand; without a registered weapon there the move probes fail and
 * change nothing.
 */
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
                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                .executes(context -> playMove(
                                        context,
                                        IntegerArgumentType.getInteger(context, "index")))))
                .then(Commands.literal("first_person")
                        .then(Commands.literal("release").executes(context -> releaseFirstPerson()))
                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                .then(Commands.argument("tick", FloatArgumentType.floatArg(0.0F))
                                        .executes(context -> probeFirstPerson(
                                                context,
                                                IntegerArgumentType.getInteger(context, "index"),
                                                FloatArgumentType.getFloat(context, "tick"))))))
                .then(Commands.literal("third_person")
                        .then(Commands.literal("release").executes(context -> releaseThirdPerson()))
                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                .then(Commands.argument("tick", FloatArgumentType.floatArg(0.0F))
                                        .executes(context -> probeThirdPerson(
                                                context,
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
                CombatAnimationController.modeEnterAnimation(player),
                0.0F) ? 1 : 0;
    }

    private static int transition() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && CombatAnimationController.transition(
                player,
                CombatAnimationController.modeEnterAnimation(player)) ? 1 : 0;
    }

    private static int playMove(CommandContext<CommandSourceStack> context, int oneBasedIndex) {
        LocalPlayer player = Minecraft.getInstance().player;
        Optional<CombatStyleDefinition> style = heldStyle(context, player, "move", oneBasedIndex);
        return style.isPresent() && CombatAnimationController.play(
                player,
                style.get().move(oneBasedIndex - 1).animation().animationId(),
                0.0F) ? 1 : 0;
    }

    private static int probeFirstPerson(CommandContext<CommandSourceStack> context, int oneBasedIndex, float tick) {
        LocalPlayer player = Minecraft.getInstance().player;
        Optional<CombatStyleDefinition> style = heldStyle(context, player, "first_person", oneBasedIndex);
        if (style.isEmpty()) {
            return 0;
        }
        FirstPersonWeaponAnimator.probe(style.get(), oneBasedIndex - 1, tick);
        LOGGER.info("PAL_SMOKE first_person move={} tick={}", oneBasedIndex, tick);
        return 1;
    }

    private static int releaseFirstPerson() {
        FirstPersonWeaponAnimator.releaseProbe();
        return 1;
    }

    /**
     * Holds the local player's third-person pose of one move at one tick. The hold ends with
     * {@code third_person release}, {@code stop}, a real action, or logout. Sends nothing.
     */
    private static int probeThirdPerson(CommandContext<CommandSourceStack> context, int oneBasedIndex, float tick) {
        LocalPlayer player = Minecraft.getInstance().player;
        Optional<CombatStyleDefinition> style = heldStyle(context, player, "third_person", oneBasedIndex);
        if (style.isEmpty()) {
            return 0;
        }
        AttackMoveDefinition move = style.get().move(oneBasedIndex - 1);
        if (!CombatAnimationController.probeTickInside(tick, move.totalTicks())) {
            // At or past the end the animation would finish at once and hold nothing.
            LOGGER.info("PAL_SMOKE third_person rejected reason=tick_out_of_range move={} tick={} total_ticks={}",
                    oneBasedIndex, tick, move.totalTicks());
            context.getSource().sendFailure(Component.literal(
                    move.id() + " lasts " + move.totalTicks() + " ticks; the probe tick must be below that"));
            return 0;
        }
        boolean held = CombatAnimationController.holdThirdPersonProbe(
                player,
                move.animation().animationId(),
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
        boolean released = CombatAnimationController.releaseThirdPersonProbe(player);
        if (released) {
            // Only a real release hands the layer back for the client tick to claim the ready idle.
            ClientCombatState.clearReadyAnimation();
            LOGGER.info("PAL_SMOKE third_person release");
        } else {
            LOGGER.info("PAL_SMOKE third_person release held=false");
        }
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

    /**
     * The main-hand weapon's style when {@code oneBasedIndex} names one of its moves; otherwise
     * reports the failure and returns empty, so the probe changes nothing.
     */
    private static Optional<CombatStyleDefinition> heldStyle(
            CommandContext<CommandSourceStack> context,
            LocalPlayer player,
            String probe,
            int oneBasedIndex) {
        if (player == null) {
            return Optional.empty();
        }
        Optional<CombatStyleDefinition> style = CombatStyles.bundled().styleFor(player.getMainHandItem());
        if (style.isEmpty()) {
            LOGGER.info("PAL_SMOKE {} rejected reason=no_weapon", probe);
            context.getSource().sendFailure(Component.literal("Hold a registered combat weapon in the main hand"));
            return Optional.empty();
        }
        int moves = style.get().moves().size();
        if (oneBasedIndex > moves) {
            LOGGER.info("PAL_SMOKE {} rejected reason=index_out_of_range move={} moves={}", probe, oneBasedIndex, moves);
            context.getSource().sendFailure(Component.literal(
                    style.get().id() + " has " + moves + " moves; got " + oneBasedIndex));
            return Optional.empty();
        }
        return style;
    }
}
