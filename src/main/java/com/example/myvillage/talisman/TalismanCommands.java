package com.example.myvillage.talisman;

import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.crafting.CraftingAptitudes;
import com.example.myvillage.cultivation.crafting.CraftingRules;
import com.google.gson.*;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** 权限2只读诊断；不推进符稿，不补料，不替代真实绘制输入。 */
public final class TalismanCommands {
    public static final String PREFIX = "TALISMAN_STATUS ";
    private TalismanCommands() { }
    public static void register(RegisterCommandsEvent e) {
        var status = Commands.literal("status").executes(c -> status(c.getSource(), c.getSource().getPlayerOrException(), null))
                .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> status(c.getSource(), c.getSource().getPlayerOrException(), BlockPosArgument.getBlockPos(c, "pos"))))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(c -> status(c.getSource(), EntityArgument.getPlayer(c, "player"), null))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> status(c.getSource(), EntityArgument.getPlayer(c, "player"), BlockPosArgument.getBlockPos(c, "pos")))));
        e.getDispatcher().register(Commands.literal("myvillage").then(Commands.literal("talisman").requires(s -> s.hasPermission(2)).then(status)));
    }
    private static int status(CommandSourceStack source, ServerPlayer player, BlockPos requested) {
        BlockPos pos = requested;
        if (pos == null && player.containerMenu instanceof TalismanMenu) {
            // Find the real nearby desk even while the cursor is hidden by its menu.
            for (BlockPos near : BlockPos.betweenClosed(player.blockPosition().offset(-4, -3, -4), player.blockPosition().offset(4, 3, 4))) {
                if (player.serverLevel().hasChunkAt(near) && player.serverLevel().getBlockEntity(near) instanceof TalismanDeskBlockEntity d
                        && ((TalismanMenu) player.containerMenu).isFor(d)) { pos = near.immutable(); break; }
            }
        }
        if (pos == null && !TalismanRuntime.loadedLine(player.serverLevel(), player.getEyePosition(),
                player.getEyePosition().add(player.getLookAngle().scale(TalismanRules.get().use("range"))))) pos = player.blockPosition();
        if (pos == null) {
            var hit = player.serverLevel().clip(new ClipContext(player.getEyePosition(), player.getEyePosition().add(player.getLookAngle().scale(TalismanRules.get().use("range"))),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            pos = hit.getBlockPos();
        }
        JsonObject j = new JsonObject(); j.addProperty("schema", 1); j.addProperty("player", player.getUUID().toString());
        j.addProperty("playerName", player.getGameProfile().getName()); j.addProperty("dimension", player.level().dimension().location().toString());
        JsonArray coords = new JsonArray(); coords.add(pos.getX()); coords.add(pos.getY()); coords.add(pos.getZ()); j.add("pos", coords);
        var apt = CultivationService.craftingAptitudes(player);
        j.addProperty("talismanAptitude", apt.talisman()); j.addProperty("qualityCap", apt.qualityCap(CraftingAptitudes.Profession.TALISMAN, CraftingRules.get()));
        j.addProperty("currentPower", CultivationService.getProfile(player).currentSpiritualPower());
        j.addProperty("shieldTotal", com.example.myvillage.combat.foundation.ShieldRuntime.total(player));
        j.addProperty("useCooldown", player.getCooldowns().isOnCooldown(TalismanContent.WARD.get()));
        j.addProperty("activeSeals", TalismanRuntime.count(player)); j.addProperty("shieldSource", TalismanRuntime.SHIELD_SOURCE.toString());
        boolean loaded = player.serverLevel().hasChunkAt(pos); j.addProperty("loaded", loaded);
        if (loaded && player.serverLevel().getBlockEntity(pos) instanceof TalismanDeskBlockEntity desk) desk.diagnostic(j);
        else { j.addProperty("desk", false); j.addProperty("stage", "missing"); }
        String line = PREFIX + j; source.sendSuccess(() -> Component.literal(line), false); return 1;
    }
}
