package com.example.myvillage.enchanting;

import static com.example.myvillage.enchanting.EnchantingRules.RULES;
import static com.example.myvillage.enchanting.EnchantingRules.id;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.block.ForgingTableBlock;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.*;

public final class EnchantingContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MyVillageMod.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MyVillageMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MyVillageMod.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MyVillageMod.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MyVillageMod.MOD_ID);
    static final Map<String, DeferredBlock<? extends Block>> PARTS = new LinkedHashMap<>();
    static final Map<String, DeferredItem<Item>> STONES = new LinkedHashMap<>();

    public static final DeferredBlock<EnchantingBlock> SOCKET = device("enchanting_socket");
    public static final DeferredBlock<EnchantingBlock> SEAL = device("seal_table");
    public static final DeferredBlock<EnchantingBlock> PORT = device("library_port");
    public static final DeferredItem<Item> SLIP = ITEMS.registerSimpleItem("loadout_slip", new Item.Properties().stacksTo(1));
    static {
        for (var stone : RULES.stones()) {
            STONES.put(stone.id(), ITEMS.registerSimpleItem(stone.id()));
        }
        for (String part : new String[]{"crystal_shelf", "focus_shelf", "seal_tile", "seal_rack", "seal_pivot",
                "library_base", "library_frame", "library_outer", "library_equator", "library_oblique",
                "library_core", "library_crown", "library_joint"}) {
            String name = part;
            var block = BLOCKS.register(name, () -> new EnchantingPartBlock(properties(), name));
            PARTS.put(name, block);
            ITEMS.registerSimpleBlockItem(name, block);
        }
    }
    public static final Supplier<BlockEntityType<EnchantingDevice>> DEVICE = ENTITIES.register("enchanting_device",
            () -> BlockEntityType.Builder.of(EnchantingDevice::new, SOCKET.get(), SEAL.get(), PORT.get()).build(null));
    public static final Supplier<MenuType<EnchantingMenu>> MENU = MENUS.register("enchanting",
            () -> new MenuType<>(EnchantingMenu::new, FeatureFlags.DEFAULT_FLAGS));
    private static final Supplier<CreativeModeTab> TAB = TABS.register("enchanting", () -> CreativeModeTab.builder()
            .title(Component.literal("阵法附魔")).icon(() -> new ItemStack(SEAL.get()))
            .displayItems((params, out) -> {
                PARTS.values().forEach(part -> out.accept(part.get()));
                STONES.values().forEach(stone -> out.accept(stone.get()));
                out.accept(SLIP.get());
            }).build());

    private EnchantingContent() {}

    private static BlockBehaviour.Properties properties() {
        return BlockBehaviour.Properties.of().strength(2.8F, 6).sound(SoundType.STONE)
                .noOcclusion().pushReaction(PushReaction.BLOCK);
    }

    private static DeferredBlock<EnchantingBlock> device(String name) {
        var block = BLOCKS.register(name, () -> new EnchantingBlock(name, properties()));
        PARTS.put(name, block);
        ITEMS.registerSimpleBlockItem(name, block);
        return block;
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        ENTITIES.register(bus);
        MENUS.register(bus);
        TABS.register(bus);
        NeoForge.EVENT_BUS.addListener(EnchantingContent::useVanillaTable);
        NeoForge.EVENT_BUS.addListener(EnchantingContent::openStoneCraft);
    }

    static boolean isInk(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id("spiritual_ink"));
    }

    static int stoneStrength(ItemStack stack) {
        for (var stone : RULES.stones()) {
            if (stack.is(STONES.get(stone.id()).get())) return stone.strength();
        }
        return 0;
    }

    static void open(ServerPlayer player, EnchantingMenu.Mode mode, net.minecraft.core.BlockPos pos, EnchantingDevice device) {
        player.openMenu(new SimpleMenuProvider((i, inv, p) -> new EnchantingMenu(i, inv, mode, pos, device),
                Component.literal(mode.title)));
    }

    private static void useVanillaTable(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getEntity().isShiftKeyDown()
                || !event.getLevel().getBlockState(event.getPos()).is(Blocks.ENCHANTING_TABLE)
                || !(event.getLevel().getBlockEntity(event.getPos().below()) instanceof EnchantingDevice socket)
                || !socket.getBlockState().is(SOCKET.get())) return;
        if (event.getEntity() instanceof ServerPlayer player && !player.isSpectator()) {
            if (stoneStrength(event.getItemStack()) > 0) socket.exchangeStone(player);
            else open(player, EnchantingMenu.Mode.TABLE, event.getPos(), socket);
        }
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
        event.setCanceled(true);
    }

    private static void openStoneCraft(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getEntity().isShiftKeyDown()
                || !(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof ForgingTableBlock)
                || RULES.stones().stream().noneMatch(stone -> event.getItemStack().is(
                        BuiltInRegistries.ITEM.get(ResourceLocation.parse(stone.ingredient()))))) return;
        if (event.getEntity() instanceof ServerPlayer player && !player.isSpectator()) {
            open(player, EnchantingMenu.Mode.STONE, event.getPos(), null);
        }
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
        event.setCanceled(true);
    }

    static boolean forgeStone(ServerPlayer player, EnchantingRules.Stone stone, ItemStack input) {
        if (!input.is(BuiltInRegistries.ITEM.get(ResourceLocation.parse(stone.ingredient()))) || input.getCount() < stone.count())
            return false;
        var realms = player.registryAccess().registryOrThrow(ModCultivationRegistries.REALMS);
        var profile = CultivationService.getProfile(player);
        var realm = realms.get(profile.realmId());
        var qiRefining = realms.get(id("qi_refining"));
        if (realm == null || qiRefining == null || profile.cultivationProgress() < stone.progress()) return false;
        if (realm.sortOrder() < qiRefining.sortOrder()) return false;
        if (realm.sortOrder() == qiRefining.sortOrder()) {
            int current = realm.stages().stream().filter(stage -> stage.id().equals(profile.stageId()))
                    .mapToInt(stage -> stage.sortOrder()).findFirst().orElse(-1);
            int required = realm.stages().stream().filter(stage -> stage.id().equals(id(stone.stage())))
                    .mapToInt(stage -> stage.sortOrder()).findFirst().orElse(Integer.MAX_VALUE);
            if (current < required) return false;
        }
        if (!CultivationService.setProgress(player, profile.cultivationProgress() - stone.progress()).success()) return false;
        input.shrink(stone.count());
        return true;
    }
}
