package com.example.myvillage.compat.waystones;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.item.ModItems;
import com.example.myvillage.spiritstone.SpiritStoneSources;
import com.example.myvillage.spiritstone.SpiritStoneStorage;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.waystones.api.MutableWaystone;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystoneStyles;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.blay09.mods.waystones.api.error.WaystoneTeleportError;
import net.blay09.mods.waystones.api.event.WaystoneTeleportEvent;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Developer fixture and readback. Actual menu use must come from mapped client input. */
@EventBusSubscriber(modid = MyVillageMod.MOD_ID)
public final class WaystonesProbe {
    private record Fixture(Waystone from, Waystone to) { }
    private static final Map<UUID, Fixture> FIXTURES = new HashMap<>();

    @SubscribeEvent
    static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("waystonesprobe").requires(source -> source.hasPermission(2))
                .then(Commands.literal("fixture").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("balance", StringArgumentType.word()).executes(context -> {
                            var player = EntityArgument.getPlayer(context, "player");
                            fixture(player, new BigInteger(StringArgumentType.getString(context, "balance")));
                            context.getSource().sendSuccess(() -> Component.literal("WAYSTONES_PROBE " + status(player)), false);
                            return 1;
                        }))))
                .then(Commands.literal("status").then(Commands.argument("player", EntityArgument.player()).executes(context -> {
                    var player = EntityArgument.getPlayer(context, "player");
                    context.getSource().sendSuccess(() -> Component.literal("WAYSTONES_PROBE " + status(player)), false);
                    return 1;
                })))
                .then(Commands.literal("failure").then(Commands.argument("player", EntityArgument.player()).executes(context -> {
                    var player = EntityArgument.getPlayer(context, "player");
                    context.getSource().sendSuccess(() -> Component.literal("WAYSTONES_PROBE " + failures(player)), false);
                    return 1;
                }))));
    }

    private static ItemStack currency() { return new ItemStack(ModItems.LOW_GRADE_SPIRIT_STONE.get()); }

    private static void fixture(ServerPlayer player, BigInteger amount) {
        if (amount.signum() < 0 || amount.toString().length() > 120) throw new IllegalArgumentException("Invalid probe balance");
        var level = player.serverLevel();
        for (var center : List.of(new BlockPos(0, 80, 0), new BlockPos(64, 80, 0))) {
            for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
                level.setBlockAndUpdate(center.offset(x, -1, z), Blocks.STONE.defaultBlockState());
                for (int y = 0; y <= 5; y++) level.setBlockAndUpdate(center.offset(x, y, z), Blocks.AIR.defaultBlockState());
            }
        }
        var from = WaystonesAPI.placeWaystone(level, new BlockPos(0, 80, 0), WaystoneStyles.DEFAULT).orElseThrow();
        var to = WaystonesAPI.placeWaystone(level, new BlockPos(64, 80, 0), WaystoneStyles.DEFAULT).orElseThrow();
        FIXTURES.put(player.getUUID(), new Fixture(from, to));
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        var bag = new ItemStack(ModItems.SPIRIT_STONE_BAG.get());
        SpiritStoneStorage.insert(bag, currency(), amount, false);
        SpiritStoneStorage.autoCollect(bag, false);
        player.getInventory().setItem(0, bag);
        player.getInventory().selected = 1;
        player.getInventory().setChanged();
        player.teleportTo(level, .5, 80, 2.5, 180, 10);
        WaystonesAPI.activateWaystone(player, from);
        WaystonesAPI.activateWaystone(player, to);
    }

    private static JsonObject status(ServerPlayer player) {
        var data = new JsonObject();
        data.addProperty("balance", SpiritStonePayment.balance(SpiritStoneSources.open(player), currency()).toString());
        data.addProperty("x", player.getX());
        data.addProperty("y", player.getY());
        data.addProperty("z", player.getZ());
        var fixture = FIXTURES.get(player.getUUID());
        if (fixture != null) {
            var context = WaystonesAPI.createUncheckedDefaultTeleportContext(player, fixture.to(), value -> value.setFromWaystone(fixture.from()))
                    .left().orElseThrow();
            data.addProperty("requirement_class", context.getRequirements().getClass().getName());
            data.addProperty("can_afford", context.getRequirements().canAfford(player));
        }
        return data;
    }

    @SuppressWarnings("deprecation")
    private static JsonObject failures(ServerPlayer player) {
        fixture(player, BigInteger.valueOf(17));
        var fixture = FIXTURES.get(player.getUUID());
        var context = WaystonesAPI.createUncheckedDefaultTeleportContext(player, fixture.to(), value -> value.setFromWaystone(fixture.from()))
                .left().orElseThrow();
        check(context.getRequirements() instanceof BagAwareItemRequirement, "Standard fee type was not replaced");
        var fee = (BagAwareItemRequirement) context.getRequirements();
        check(fee.getCount() == 1 && fee.getItemStack().is(ModItems.LOW_GRADE_SPIRIT_STONE.get()), "Failure probe must use the configured paid fee");
        check(fee.canAfford(player), "Failure fixture cannot afford the configured fee");
        var before = player.getInventory().getItem(0).copy();
        // The synchronous API may resolve a destination even after the block was removed.
        // An unavailable dimension fails during destination resolution, after the native debit.
        var target = (MutableWaystone) fixture.to();
        var originalDimension = fixture.to().getDimension();
        target.setDimension(ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "waystones_probe_missing")));
        WaystoneTeleportError failure;
        try {
            var failed = WaystonesAPI.tryTeleport(context);
            check(failed.right().isPresent(), "Unavailable target dimension unexpectedly teleported");
            failure = failed.right().orElseThrow();
            check(failure instanceof WaystoneTeleportError.InvalidDimension, "Unexpected failure did not exercise destination resolution");
            check(!fee.canAfford(player), "Failure path did not create a single-use payment receipt");
            check(ItemStack.matches(before, player.getInventory().getItem(0)), "Failed teleport changed the bag");
        } finally {
            target.setDimension(originalDimension);
        }
        Balm.getEvents().fireEvent(new WaystoneTeleportEvent.Complete(context, null, List.of(), failure));
        check(ItemStack.matches(before, player.getInventory().getItem(0)), "Repeated completion refunded twice");
        fixture(player, BigInteger.ZERO);
        var empty = FIXTURES.get(player.getUUID());
        var shortContext = WaystonesAPI.createUncheckedDefaultTeleportContext(player, empty.to(), value -> value.setFromWaystone(empty.from()))
                .left().orElseThrow();
        check(!shortContext.getRequirements().canAfford(player), "Empty bag can afford a paid teleport");
        check(WaystonesAPI.tryTeleport(shortContext).right().isPresent(), "Short payment allowed a teleport");
        check(player.getX() < 4, "Short payment changed location");
        var ordinary = new BagAwareItemRequirement();
        ordinary.setItemStack(new ItemStack(Items.DIAMOND));
        ordinary.setCount(2);
        player.getInventory().setItem(1, new ItemStack(Items.DIAMOND, 2));
        check(ordinary.canAfford(player), "Native non-spirit cost failed");
        ordinary.consume(player);
        check(player.getInventory().getItem(1).isEmpty(), "Native non-spirit cost did not consume");
        var result = status(player);
        result.addProperty("failure_error", failure.getClass().getName());
        result.addProperty("charged_before_failure", true);
        result.addProperty("sync_failure_refund", true);
        result.addProperty("repeated_completion", true);
        result.addProperty("insufficient_funds", true);
        result.addProperty("ordinary_item_cost", true);
        return result;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
