package com.example.myvillage.compat.waystones;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.item.ModItems;
import com.example.myvillage.spiritstone.SpiritStoneSources;
import com.example.myvillage.spiritstone.SpiritStoneStorage;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
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
                .then(Commands.literal("pricing").then(Commands.argument("player", EntityArgument.player()).executes(context -> {
                    var player = EntityArgument.getPlayer(context, "player");
                    context.getSource().sendSuccess(() -> Component.literal("WAYSTONES_PROBE " + pricing(player)), false);
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
        for (var center : List.of(new BlockPos(0, 80, 0), new BlockPos(5000, 80, 0))) {
            for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
                level.setBlockAndUpdate(center.offset(x, -1, z), Blocks.STONE.defaultBlockState());
                for (int y = 0; y <= 5; y++) level.setBlockAndUpdate(center.offset(x, y, z), Blocks.AIR.defaultBlockState());
            }
        }
        var from = WaystonesAPI.placeWaystone(level, new BlockPos(0, 80, 0), WaystoneStyles.DEFAULT).orElseThrow();
        var to = WaystonesAPI.placeWaystone(level, new BlockPos(5000, 80, 0), WaystoneStyles.DEFAULT).orElseThrow();
        ((MutableWaystone) from).setName(Component.literal("计费起点"));
        ((MutableWaystone) to).setName(Component.literal("计费目标"));
        FIXTURES.put(player.getUUID(), new Fixture(from, to));
        setRealm(player, "foundation_establishment", "foundation_early");
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
        data.addProperty("realm", CultivationService.getProfile(player).realmId().toString());
        var fixture = FIXTURES.get(player.getUUID());
        if (fixture != null) {
            var context = WaystonesAPI.createUncheckedDefaultTeleportContext(player, fixture.to(), value -> value.setFromWaystone(fixture.from()))
                    .left().orElseThrow();
            data.addProperty("requirement_class", context.getRequirements().getClass().getName());
            data.addProperty("can_afford", context.getRequirements().canAfford(player));
            if (context.getRequirements() instanceof BagAwareItemRequirement fee) data.addProperty("fee_count", fee.getCount());
        }
        return data;
    }

    private static void setRealm(ServerPlayer player, String realm, String stage) {
        var result = CultivationService.setRealmAndStage(player,
                ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, realm),
                ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, stage));
        check(result.success(), result.message());
    }

    private static JsonObject pricing(ServerPlayer player) {
        var fixture = FIXTURES.get(player.getUUID());
        check(fixture != null, "Create the isolated fixture first");
        var target = (MutableWaystone) fixture.to();
        var originalPosition = fixture.to().getPos();
        var originalDimension = fixture.to().getDimension();
        var originalProfile = CultivationService.getProfile(player);
        var rows = new JsonArray();
        String[][] stages = {{"mortal", "mortal_unawakened", "1"}, {"qi_refining", "qi_refining_1", "1"},
                {"qi_refining", "qi_refining_9", "1"}, {"foundation_establishment", "foundation_early", "3"},
                {"foundation_establishment", "foundation_late", "3"}, {"golden_core", "golden_early", "10"},
                {"golden_core", "golden_late", "10"}};
        int[] distances = {64, 1000, 1001, 5000, 10000, 20000};
        int[] coefficients = {1, 1, 2, 5, 10, 10};
        try {
            for (String[] stage : stages) {
                setRealm(player, stage[0], stage[1]);
                for (int i = 0; i < distances.length; i++) {
                    target.setPos(new BlockPos(distances[i], 200, 0)); // Height must not affect the horizontal price.
                    rows.add(quote(player, fixture, Integer.parseInt(stage[2]) * coefficients[i]));
                }
                target.setDimension(net.minecraft.world.level.Level.NETHER);
                rows.add(quote(player, fixture, Integer.parseInt(stage[2]) * 10));
                target.setDimension(originalDimension);
            }
            target.setPos(new BlockPos(3000, 80, 4000));
            rows.add(quote(player, fixture, 50)); // Golden late: diagonal distance is exactly 5000.
        } finally {
            target.setPos(originalPosition);
            target.setDimension(originalDimension);
            check(CultivationService.replaceProfile(player, originalProfile).success(), "Could not restore fixture profile");
        }
        var result = new JsonObject();
        result.addProperty("status", "passed");
        result.addProperty("checked_quotes", rows.size());
        org.slf4j.LoggerFactory.getLogger(WaystonesProbe.class).info("WAYSTONES_PRICING_TABLE {}", rows);
        return result;
    }

    private static JsonObject quote(ServerPlayer player, Fixture fixture, int expected) {
        var context = WaystonesAPI.createUncheckedDefaultTeleportContext(player, fixture.to(), value -> value.setFromWaystone(fixture.from()))
                .left().orElseThrow();
        check(context.getRequirements() instanceof BagAwareItemRequirement, "Native price did not use the bag-aware type");
        var fee = (BagAwareItemRequirement) context.getRequirements();
        check(fee.getCount() == expected, "Expected price " + expected + " but got " + fee.getCount());
        var row = new JsonObject();
        row.addProperty("realm", CultivationService.getProfile(player).realmId().toString());
        row.addProperty("stage", CultivationService.getProfile(player).stageId().toString());
        row.addProperty("target", fixture.to().getPos().toShortString());
        row.addProperty("interdimensional", context.isDimensionalTeleport());
        row.addProperty("fee_count", fee.getCount());
        return row;
    }

    @SuppressWarnings("deprecation")
    private static JsonObject failures(ServerPlayer player) {
        fixture(player, BigInteger.valueOf(17));
        var fixture = FIXTURES.get(player.getUUID());
        var context = WaystonesAPI.createUncheckedDefaultTeleportContext(player, fixture.to(), value -> value.setFromWaystone(fixture.from()))
                .left().orElseThrow();
        check(context.getRequirements() instanceof BagAwareItemRequirement, "Standard fee type was not replaced");
        var fee = (BagAwareItemRequirement) context.getRequirements();
        check(fee.getCount() == 15 && fee.getItemStack().is(ModItems.LOW_GRADE_SPIRIT_STONE.get()), "Failure probe must use the configured paid fee");
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
