package com.example.myvillage.dropprobe;

import com.example.myvillage.block.ModBlocks;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.drops.BeastMaterialRules;
import com.example.myvillage.forging.PreparationBlockEntity;
import com.example.myvillage.forging.array.ForgingArrayBlock;
import com.example.myvillage.forging.array.ForgingArrayBlockEntity;
import com.example.myvillage.forging.array.ForgingArrayContent;
import com.example.myvillage.forging.array.ForgingArrayPattern;
import com.example.myvillage.forging.array.ForgingArrayRules;
import com.example.myvillage.storagebag.StorageBagForgeMenu;
import com.example.myvillage.storagebag.StorageBagItem;
import com.example.myvillage.storagebag.StorageBagMenu;
import com.example.myvillage.storagebag.StorageBagRules;
import com.google.gson.Gson;
import com.mojang.authlib.GameProfile;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Consumer;

/** Runs the release code on a real server using an isolated command-driven player fixture. */
final class StorageBagProbe {
    private static final BlockPos CHEST = new BlockPos(96, 82, 96);
    private static final BlockPos DING = CHEST.east(2);
    private static final BlockPos ARRAY = CHEST.south(8);
    private static final String MATERIAL = "raw_xuantie", EXTRA = "ningyuan_log";
    private final List<String> passed = new ArrayList<>();

    static void register() { NeoForge.EVENT_BUS.addListener(StorageBagProbe::commands); }

    private static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("storage_bag_probe").requires(source -> source.hasPermission(4))
                .then(Commands.literal("save").executes(c -> new StorageBagProbe().run(c.getSource(), false)))
                .then(Commands.literal("restore").executes(c -> new StorageBagProbe().run(c.getSource(), true))));
    }

    private int run(CommandSourceStack source, boolean restore) {
        var level = source.getServer().overworld();
        try {
            if (restore) {
                var persisted = ((ChestBlockEntity) level.getBlockEntity(CHEST)).getItem(26);
                check("原生存档重启", bagCapacity(persisted) == 63 && stored(persisted, MATERIAL) == 9
                        && persisted.getHoverName().getString().equals("随身材料") && paused(persisted));
            } else {
                verify(level);
            }
            source.sendSuccess(() -> json("passed", ""), false);
            return 1;
        } catch (RuntimeException exception) {
            source.sendFailure(json("failed", exception.toString()));
            throw exception;
        }
    }

    private Component json(String status, String error) {
        return Component.literal("STORAGE_BAG_PROBE " + new Gson().toJson(Map.of("status", status, "checks", passed, "error", error)));
    }

    private void verify(ServerLevel level) {
        var player = new ProbePlayer(level);
        var inventory = player.getInventory();
        var materialTag = TagKey.create(net.minecraft.core.registries.Registries.ITEM, id("storage_bag_materials"));
        for (var tier : StorageBagRules.tiers()) for (var kind : StorageBagRules.Kind.values()) {
            var stack = stack(tier.itemId(kind), 1);
            check(tier.itemId(kind), stack.getItem() instanceof StorageBagItem bag
                    && bag.kind() == kind && bag.tier().equals(tier) && !stack.canFitInsideContainerItems());
        }
        check("妖兽词典全部变体", BeastMaterialRules.bundled().variants().stream().allMatch(variant ->
                new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(variant.id()))).is(materialTag)));

        player.setPos(Vec3.atCenterOf(CHEST));
        var bag = stack("storage_bag_material_qi_refining", 1);
        inventory.setItem(Inventory.SLOT_OFFHAND, bag);
        var delayed = new ItemEntity(level, 96.5, 82, 96.5, stack(MATERIAL, 3));
        delayed.setPickUpDelay(100);
        delayed.playerTouch(player);
        check("拾取延迟", stored(bag, MATERIAL) == 0 && !delayed.isRemoved());
        delayed.setNoPickUpDelay();
        delayed.setTarget(new UUID(3, 4));
        delayed.playerTouch(player);
        check("拾取归属", stored(bag, MATERIAL) == 0 && !delayed.isRemoved());
        delayed.discard();
        check("副手实际拾取", touch(player, stack(MATERIAL, 9)).isRemoved() && stored(bag, MATERIAL) == 9);
        check("数据包扩展材料", touch(player, stack(EXTRA, 4)).isRemoved() && stored(bag, EXTRA) == 4);
        touch(player, new ItemStack(Items.COBBLESTONE, 2));
        check("原版不入袋", stored(bag, "cobblestone") == 0 && inventory.countItem(Items.COBBLESTONE) == 2);

        inventory.setItem(Inventory.SLOT_OFFHAND, ItemStack.EMPTY);
        inventory.setItem(0, bag);
        bag.getItem().use(level, player, InteractionHand.MAIN_HAND);
        var bagMenu = (StorageBagMenu) player.containerMenu;
        check("手持袋子锁定", bagMenu.pages() == 1 && bagMenu.visibleSlots() == 27);
        bagMenu.clicked(27 + 27, 1, ClickType.THROW, player);
        check("袋内操作不能丢出本袋", inventory.getItem(0) == bag);
        touch(player, stack(MATERIAL, 1));
        check("开袋期间拾取同步", bagMenu.getSlot(0).getItem().getCount() == 10);
        check("暂停拾取开关", bagMenu.clickMenuButton(player, StorageBagMenu.TOGGLE_PICKUP) && paused(bag));
        touch(player, stack(MATERIAL, 2));
        check("暂停后遵守普通拾取", stored(bag, MATERIAL) == 10 && inventory.countItem(stack(MATERIAL, 1).getItem()) == 2);
        player.finishMenu();

        level.setBlockAndUpdate(CHEST, Blocks.CHEST.defaultBlockState());
        var chest = (ChestBlockEntity) level.getBlockEntity(CHEST);
        for (int i = 1; i < chest.getContainerSize(); i++) chest.setItem(i, new ItemStack(Items.DIRT, 64));
        chest.setItem(0, stack(MATERIAL, 63));
        player.setShiftKeyDown(true);
        bag.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(CHEST), Direction.UP, CHEST, false)));
        player.setShiftKeyDown(false);
        check("真实箱子定向卸货", chest.getItem(0).getCount() == 64 && stored(bag, MATERIAL) == 9 && stored(bag, EXTRA) == 4);
        bag.set(DataComponents.CUSTOM_NAME, Component.literal("随身材料"));

        level.setBlockAndUpdate(DING, ModBlocks.FORGING_TABLE.get().defaultBlockState());
        player.setPos(Vec3.atCenterOf(DING));
        var ordinary = (PreparationBlockEntity) level.getBlockEntity(DING);
        player.containerMenu = ordinary.createMenu(14, inventory, player);
        check("普通鼎菜单入口", player.containerMenu.clickMenuButton(player, StorageBagForgeMenu.OPEN_BUTTON));
        var forge = (StorageBagForgeMenu) player.containerMenu;
        inventory.setItem(10, new ItemStack(Items.LEATHER, 4));
        inventory.setItem(11, new ItemStack(Items.STRING, 2));
        inventory.setItem(12, new ItemStack(Items.COPPER_INGOT));
        inventory.setItem(13, stack("low_grade_spirit_stone", 2));
        setRealm(player, "qi_refining", "qi_refining_1");
        forge.clickMenuButton(player, StorageBagForgeMenu.SELECT_KIND + StorageBagRules.Kind.MANUAL.ordinal());
        forge.clickMenuButton(player, StorageBagForgeMenu.START);
        var manualBag = forge.getSlot(1).remove(1);
        check("炼气一层制袋", bagCapacity(manualBag) == 27
                && ((StorageBagItem) manualBag.getItem()).kind() == StorageBagRules.Kind.MANUAL
                && inventory.getItem(10).isEmpty() && inventory.getItem(13).isEmpty());
        forge.getSlot(0).set(new ItemStack(bag.getItem(), 1));
        inventory.setItem(1, stack("xuantie_ingot", 2));
        inventory.setItem(2, stack("low_grade_spirit_stone", 8));
        setRealm(player, "qi_refining", "qi_refining_9");
        forge.clickMenuButton(player, StorageBagForgeMenu.START);
        check("未筑基整批拒绝", forge.status() == 2 && forge.output().isEmpty()
                && !forge.input().isEmpty() && inventory.getItem(1).getCount() == 2);
        forge.getSlot(0).set(bag);
        inventory.setItem(0, ItemStack.EMPTY);
        setRealm(player, "foundation_establishment", "foundation_early");
        forge.clickMenuButton(player, StorageBagForgeMenu.START);
        var upgraded = forge.getSlot(1).remove(1);
        check("筑基实际扣料扩容", bagCapacity(upgraded) == 45 && inventory.getItem(1).isEmpty()
                && inventory.getItem(2).isEmpty() && forge.input().isEmpty()
                && stored(upgraded, MATERIAL) == 9 && stored(upgraded, EXTRA) == 4 && paused(upgraded));
        player.finishMenu();

        for (var cell : ForgingArrayRules.get().cells()) {
            var block = cell.role() == ForgingArrayRules.Role.AIR ? Blocks.AIR.defaultBlockState()
                    : ForgingArrayContent.PARTS.get(cell.role()).get().defaultBlockState()
                            .setValue(ForgingArrayBlock.FACING, Direction.NORTH);
            level.setBlockAndUpdate(ForgingArrayPattern.worldPos(ARRAY, Direction.NORTH, cell), block);
        }
        var array = (ForgingArrayBlockEntity) level.getBlockEntity(ARRAY);
        player.setPos(Vec3.atCenterOf(ARRAY));
        player.containerMenu = array.createMenu(15, inventory, player);
        check("玄枢鼎菜单入口", player.containerMenu.clickMenuButton(player, StorageBagForgeMenu.OPEN_BUTTON));
        forge = (StorageBagForgeMenu) player.containerMenu;
        forge.getSlot(0).set(upgraded);
        inventory.setItem(1, new ItemStack(Items.AMETHYST_SHARD, 4));
        inventory.setItem(2, stack("low_grade_spirit_stone", 24));
        setRealm(player, "golden_core", "golden_early");
        forge.clickMenuButton(player, StorageBagForgeMenu.START);
        var golden = forge.getSlot(1).remove(1);
        check("金丹扩容和完整组件", bagCapacity(golden) == 63 && stored(golden, MATERIAL) == 9
                && stored(golden, EXTRA) == 4 && paused(golden) && inventory.getItem(2).isEmpty());
        player.finishMenu();
        inventory.setItem(0, golden);
        golden.getItem().use(level, player, InteractionHand.MAIN_HAND);
        bagMenu = (StorageBagMenu) player.containerMenu;
        check("金丹袋翻页", bagMenu.pages() == 2 && bagMenu.clickMenuButton(player, StorageBagMenu.NEXT_PAGE)
                && bagMenu.visibleSlots() == 9);
        bagMenu.clicked(0, 0, ClickType.PICKUP, player);
        check("不可操作其他页的槽位", bagMenu.getCarried().isEmpty() && stored(golden, MATERIAL) == 9);
        player.finishMenu();
        chest.setItem(26, golden);
        chest.setChanged();

        inventory.setItem(0, manualBag);
        manualBag.getItem().use(level, player, InteractionHand.MAIN_HAND);
        bagMenu = (StorageBagMenu) player.containerMenu;
        var manual = stack("manual_core_huang", 1);
        manual.set(com.example.myvillage.item.ModDataComponents.COMPREHENSION.get(), 37);
        check("秘籍袋拒绝普通书", !bagMenu.getSlot(0).mayPlace(new ItemStack(Items.BOOK)));
        bagMenu.setCarried(manual);
        bagMenu.clicked(0, 0, ClickType.PICKUP, player);
        check("秘籍的参悟组件保留", manualBag.get(DataComponents.CONTAINER).copyOne()
                .get(com.example.myvillage.item.ModDataComponents.COMPREHENSION.get()) == 37);
        player.finishMenu();

        var nearlyFull = stack("storage_bag_material_qi_refining", 1);
        var storage = new ArrayList<ItemStack>();
        for (int i = 0; i < 27; i++) storage.add(stack(MATERIAL, i == 0 ? 63 : 64));
        nearlyFull.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(storage));
        inventory.setItem(0, nearlyFull);
        for (int i = 1; i < Inventory.INVENTORY_SIZE; i++) inventory.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        var overflow = touch(player, stack(MATERIAL, 4));
        check("两处皆满时剩余掉落留在原实体", !overflow.isRemoved() && overflow.getItem().getCount() == 3
                && stored(nearlyFull, MATERIAL) == 27 * 64);
        overflow.discard();
    }

    private static ItemEntity touch(ProbePlayer player, ItemStack drop) {
        var entity = new ItemEntity(player.serverLevel(), player.getX(), player.getY(), player.getZ(), drop);
        entity.setNoPickUpDelay();
        entity.playerTouch(player);
        return entity;
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("yuanxian", path); }

    private static ItemStack stack(String path, int count) { return new ItemStack(BuiltInRegistries.ITEM.get(id(path)), count); }

    private static int stored(ItemStack bag, String path) {
        return bag.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyStream()
                .filter(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().equals(path)).mapToInt(ItemStack::getCount).sum();
    }

    private static boolean paused(ItemStack bag) {
        var data = bag.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean("yuanxian:storage_bag_pickup_paused");
    }

    private static int bagCapacity(ItemStack bag) {
        return bag.getItem() instanceof StorageBagItem item ? item.tier().capacity() : 0;
    }

    private void setRealm(ProbePlayer player, String realm, String stage) {
        check("配置测试修为 " + stage, CultivationService.setRealmAndStage(player, id(realm), id(stage)).success());
    }

    private void check(String what, boolean pass) {
        if (!pass) throw new IllegalStateException(what);
        passed.add(what);
    }

    private static final class ProbePlayer extends FakePlayer {
        private int nextId = 25;

        ProbePlayer(ServerLevel level) { super(level, new GameProfile(new UUID(4, 1), "BagProbe")); }

        void finishMenu() {
            if (containerMenu != inventoryMenu) containerMenu.removed(this);
            containerMenu = inventoryMenu;
        }

        @Override
        public OptionalInt openMenu(MenuProvider provider, Consumer<RegistryFriendlyByteBuf> writer) {
            finishMenu();
            int id = nextId++;
            containerMenu = provider.createMenu(id, getInventory(), this);
            return OptionalInt.of(id);
        }
    }
}
