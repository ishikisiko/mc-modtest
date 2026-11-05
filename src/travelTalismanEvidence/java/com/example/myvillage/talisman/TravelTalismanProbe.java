package com.example.myvillage.talisman;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.SpiritualRoot;
import com.example.myvillage.cultivation.crafting.CraftingAptitudes;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.Map;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** 仅隔离云端夹具和读回；正常施符必须来自真实客户端输入。 */
@EventBusSubscriber(modid = MyVillageMod.MOD_ID)
public final class TravelTalismanProbe {
    private static final Map<java.util.UUID, JsonObject> INPUTS = new java.util.HashMap<>();
    private static long inputSequence;
    @SubscribeEvent(receiveCanceled = true) static void itemInput(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem e) {
        input(e, "item");
    }
    @SubscribeEvent(receiveCanceled = true) static void blockInput(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock e) {
        input(e, "block");
    }
    private static void input(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent e, String kind) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !p.getGameProfile().getName().startsWith("TravelTalisman")) return;
        var j = new JsonObject(); j.addProperty("sequence", ++inputSequence); j.addProperty("kind", kind);
        j.addProperty("shift", p.isShiftKeyDown()); j.addProperty("onGround", p.onGround());
        j.addProperty("held", BuiltInRegistries.ITEM.getKey(e.getItemStack().getItem()).toString());
        INPUTS.put(p.getUUID(), j);
    }
    @SubscribeEvent static void commands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("travelroute").requires(s -> s.hasPermission(2)).executes(c -> {
            if (!ModList.get().isLoaded("waystones")) return 0;
            var p = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(c.getSource().getServer().overworld());
            p.setPos(40.5, 80, .5);
            c.getSource().sendSuccess(() -> Component.literal("TRAVEL_ROUTES " + WaystonesTravelProbe.inspect(p)), false);
            return 1;
        }));
        e.getDispatcher().register(Commands.literal("travelprobe").requires(s -> s.hasPermission(2))
                .then(Commands.argument("action", StringArgumentType.word())
                        .then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                            ServerPlayer p = EntityArgument.getPlayer(c, "player");
                            action(p, StringArgumentType.getString(c, "action"));
                            c.getSource().sendSuccess(() -> Component.literal("TRAVEL_PROBE " + status(p)), false);
                            return 1;
                        }))));
    }
    private static void action(ServerPlayer p, String action) {
        switch (action) {
            case "fixture" -> fixture(p);
            case "status" -> { }
            case "away", "origin", "second", "nether", "desk" -> {
                ServerLevel level = p.getServer().getLevel(action.equals("nether") ? Level.NETHER : Level.OVERWORLD);
                int x = switch (action) { case "away" -> 40; case "second" -> 20; default -> 0; };
                if (!action.equals("desk")) platform(level, new BlockPos(x, 80, 0));
                p.closeContainer(); p.teleportTo(level, x + .5, 80, .5, 180, action.equals("desk") ? 20 : 0);
            }
            case "block", "unblock" -> {
                var at = p.getData(TalismanContent.RECALL_MARK.get()).point().orElseThrow();
                var level = p.getServer().getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, at.dimension()));
                var pos = BlockPos.containing(at.position());
                level.setBlockAndUpdate(pos, action.equals("block") ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(pos.above(), action.equals("block") ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
            case "restore_power" -> require(CultivationService.setSpiritualPower(p, CultivationService.spiritualPowerCap(p)).success(), "power");
            case "remove_first", "revoke_first", "cancel_next" -> {
                if (!ModList.get().isLoaded("waystones")) throw new IllegalStateException("Waystones absent");
                WaystonesTravelProbe.action(p, action);
            }
            default -> throw new IllegalArgumentException("Unknown fixture action " + action);
        }
    }
    static void platform(ServerLevel level, BlockPos center) {
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
            level.setBlockAndUpdate(center.offset(x, -1, z), Blocks.STONE.defaultBlockState());
            for (int y = 0; y <= 4; y++) level.setBlockAndUpdate(center.offset(x, y, z), Blocks.AIR.defaultBlockState());
        }
    }
    private static void fixture(ServerPlayer p) {
        var level = p.getServer().overworld();
        for (int x : new int[]{0, 20, 40}) platform(level, new BlockPos(x, 80, 0));
        p.closeContainer(); p.setGameMode(GameType.SURVIVAL);
        require(CultivationService.setSpiritualRoot(p, new SpiritualRoot(Map.of(ResourceLocation.parse("myvillage:metal"), 10000))).success(), "root");
        require(CultivationService.setRealmAndStage(p, ResourceLocation.parse("myvillage:foundation_establishment"), ResourceLocation.parse("myvillage:foundation_early")).success(), "realm");
        require(CultivationService.setCraftingAptitude(p, CraftingAptitudes.Profession.TALISMAN, 90).success(), "aptitude");
        require(CultivationService.setSpiritualPower(p, CultivationService.spiritualPowerCap(p)).success(), "power");
        p.getInventory().clearContent();
        p.getInventory().setItem(0, new ItemStack(TalismanContent.RECALL.get(), 12));
        p.getInventory().setItem(1, new ItemStack(TalismanContent.HOME.get(), 12));
        p.getInventory().selected = 0;
        p.setData(TalismanContent.RECALL_MARK.get(), RecallMark.EMPTY);
        var deskPos = new BlockPos(0, 80, -3);
        level.setBlockAndUpdate(deskPos, TalismanContent.DESK.get().defaultBlockState());
        var desk = (TalismanDeskBlockEntity) level.getBlockEntity(deskPos);
        desk.setItem(0, new ItemStack(TalismanContent.PAPER.get(), 8));
        desk.setItem(1, new ItemStack(TalismanContent.INK.get(), 8));
        if (ModList.get().isLoaded("waystones")) WaystonesTravelProbe.fixture(p);
        p.teleportTo(level, .5, 80, .5, 180, 0);
        p.getInventory().setChanged(); p.containerMenu.broadcastChanges();
    }
    private static void require(boolean condition, String name) {
        if (!condition) throw new IllegalStateException("Fixture " + name + " failed");
    }
    public static JsonObject status(ServerPlayer p) {
        var j = new JsonObject();
        j.addProperty("player", p.getGameProfile().getName());
        j.addProperty("dimension", p.level().dimension().location().toString());
        j.addProperty("x", p.getX()); j.addProperty("y", p.getY()); j.addProperty("z", p.getZ());
        j.addProperty("power", CultivationService.getProfile(p).currentSpiritualPower());
        j.addProperty("menu", p.containerMenu.getClass().getSimpleName());
        j.addProperty("selected", p.getInventory().selected);
        j.addProperty("held", BuiltInRegistries.ITEM.getKey(p.getMainHandItem().getItem()).toString());
        j.addProperty("onGround", p.onGround());
        j.add("lastInput", INPUTS.containsKey(p.getUUID()) ? INPUTS.get(p.getUUID()).deepCopy() : JsonNull.INSTANCE);
        j.addProperty("cooldown", TalismanRuntime.coolingDown(p));
        var totals = new JsonObject();
        for (var item : new net.minecraft.world.item.Item[]{TalismanContent.HOME.get(), TalismanContent.RECALL.get()}) {
            int count = 0;
            for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (p.getInventory().getItem(i).is(item)) count += p.getInventory().getItem(i).getCount();
            totals.addProperty(BuiltInRegistries.ITEM.getKey(item).getPath(), count);
        }
        j.add("counts", totals);
        j.add("mark", p.getData(TalismanContent.RECALL_MARK.get()).point()
                .map(point -> TravelPoint.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, point).getOrThrow()).orElse(JsonNull.INSTANCE));
        var destinations = new JsonArray();
        for (var entry : TalismanTravel.activated(p)) destinations.add(entry.name());
        j.add("destinations", destinations);
        if (p.containerMenu instanceof TalismanTravelMenu menu) {
            j.addProperty("page", menu.page()); j.addProperty("pages", menu.pages());
            var rows = new JsonArray();
            for (var entry : menu.entries()) { var row = new JsonObject(); row.addProperty("name", entry.name()); row.addProperty("x", entry.pos().getX()); rows.add(row); }
            j.add("rows", rows);
        }
        return j;
    }
}
