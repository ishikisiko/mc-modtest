package com.example.myvillage.orchard;

import com.example.myvillage.MyVillageMod;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import net.neoforged.neoforge.registries.*;

public final class OrchardContent {
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(MyVillageMod.MOD_ID);
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(MyVillageMod.MOD_ID);
    private static final DeferredRegister<Feature<?>> FEATURES=DeferredRegister.create(Registries.FEATURE,MyVillageMod.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,MyVillageMod.MOD_ID);
    private static final List<Supplier<? extends Item>> DISPLAY=new ArrayList<>();
    public static final BlockSetType SET=BlockSetType.register(new BlockSetType("myvillage:ningyuan"));
    public static final WoodType WOOD_TYPE=WoodType.register(new WoodType("myvillage:ningyuan",SET));
    public static final ResourceKey<ConfiguredFeature<?,?>> TREE=ResourceKey.create(Registries.CONFIGURED_FEATURE,ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID,"ningyuan_tree"));
    public static final TreeGrower TREE_GROWER=new TreeGrower("myvillage:ningyuan",Optional.empty(),Optional.of(TREE),Optional.empty());
    private static BlockBehaviour.Properties wood() { return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2,3).sound(SoundType.WOOD).ignitedByLava(); }
    private static <B extends Block> DeferredBlock<B> block(String name,Supplier<B> factory) {
        var block=BLOCKS.register(name,factory);DISPLAY.add(ITEMS.registerItem(name,p->new OrchardItems.PlantBlockItem(block.get(),p)));return block;
    }
    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_LOG=block("stripped_ningyuan_log",()->new RotatedPillarBlock(wood()));
    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_WOOD=block("stripped_ningyuan_wood",()->new RotatedPillarBlock(wood()));
    public static final DeferredBlock<NingyuanLogBlock> LOG=block("ningyuan_log",()->new NingyuanLogBlock(wood(),STRIPPED_LOG));
    public static final DeferredBlock<NingyuanLogBlock> WOOD=block("ningyuan_wood",()->new NingyuanLogBlock(wood(),STRIPPED_WOOD));
    public static final DeferredBlock<Block> PLANKS=block("ningyuan_planks",()->new Block(wood()));
    public static final DeferredBlock<StairBlock> STAIRS=block("ningyuan_stairs",()->new StairBlock(PLANKS.get().defaultBlockState(),wood()));
    public static final DeferredBlock<SlabBlock> SLAB=block("ningyuan_slab",()->new SlabBlock(wood()));
    public static final DeferredBlock<FenceBlock> FENCE=block("ningyuan_fence",()->new FenceBlock(wood()));
    public static final DeferredBlock<FenceGateBlock> FENCE_GATE=block("ningyuan_fence_gate",()->new FenceGateBlock(WOOD_TYPE,wood()));
    public static final DeferredBlock<DoorBlock> DOOR=block("ningyuan_door",()->new DoorBlock(SET,wood().noOcclusion()));
    public static final DeferredBlock<TrapDoorBlock> TRAPDOOR=block("ningyuan_trapdoor",()->new TrapDoorBlock(SET,wood().noOcclusion()));
    public static final DeferredBlock<PressurePlateBlock> PRESSURE_PLATE=block("ningyuan_pressure_plate",()->new PressurePlateBlock(SET,wood().noCollission().strength(.5F)));
    public static final DeferredBlock<ButtonBlock> BUTTON=block("ningyuan_button",()->new ButtonBlock(SET,30,wood().noCollission().strength(.5F)));
    public static final DeferredBlock<NingyuanSaplingBlock> SAPLING=block("ningyuan_sapling",()->new NingyuanSaplingBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission().randomTicks().instabreak().sound(SoundType.GRASS).pushReaction(PushReaction.DESTROY)));
    public static final DeferredBlock<NingyuanLeavesBlock> LEAVES=block("ningyuan_leaves",()->new NingyuanLeavesBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(.2F).randomTicks().sound(SoundType.GRASS).noOcclusion().ignitedByLava().isValidSpawn((s,l,p,e)->false).isSuffocating((s,l,p)->false).isViewBlocking((s,l,p)->false)));
    public static final DeferredBlock<NingyuanFruitBlock> FRUIT=block("ningyuan_fruit_cluster",()->new NingyuanFruitBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission().randomTicks().instabreak().sound(SoundType.CROP).pushReaction(PushReaction.DESTROY)));
    public static final DeferredBlock<FlowerPotBlock> POTTED=BLOCKS.register("potted_ningyuan_sapling",()->new FlowerPotBlock(()->(FlowerPotBlock)Blocks.FLOWER_POT,SAPLING,BlockBehaviour.Properties.of().instabreak().noOcclusion().pushReaction(PushReaction.DESTROY)));
    public static final DeferredBlock<StandingSignBlock> SIGN=BLOCKS.register("ningyuan_sign",()->new StandingSignBlock(WOOD_TYPE,wood().noCollission().strength(1)));
    public static final DeferredBlock<WallSignBlock> WALL_SIGN=BLOCKS.register("ningyuan_wall_sign",()->new WallSignBlock(WOOD_TYPE,wood().noCollission().strength(1)));
    public static final DeferredBlock<CeilingHangingSignBlock> HANGING_SIGN=BLOCKS.register("ningyuan_hanging_sign",()->new CeilingHangingSignBlock(WOOD_TYPE,wood().noCollission().strength(1)));
    public static final DeferredBlock<WallHangingSignBlock> WALL_HANGING_SIGN=BLOCKS.register("ningyuan_wall_hanging_sign",()->new WallHangingSignBlock(WOOD_TYPE,wood().noCollission().strength(1)));
    public static final DeferredItem<SignItem> SIGN_ITEM=ITEMS.registerItem("ningyuan_sign",p->new SignItem(p.stacksTo(16),SIGN.get(),WALL_SIGN.get()));
    public static final DeferredItem<HangingSignItem> HANGING_SIGN_ITEM=ITEMS.registerItem("ningyuan_hanging_sign",p->new HangingSignItem(HANGING_SIGN.get(),WALL_HANGING_SIGN.get(),p.stacksTo(16)));
    public static final DeferredItem<BoatItem> BOAT=ITEMS.registerItem("ningyuan_boat",p->new BoatItem(false,OrchardBoatType.NINGYUAN.getValue(),p.stacksTo(1)));
    public static final DeferredItem<BoatItem> CHEST_BOAT=ITEMS.registerItem("ningyuan_chest_boat",p->new BoatItem(true,OrchardBoatType.NINGYUAN.getValue(),p.stacksTo(1)));
    public static final DeferredItem<OrchardItems.FertilizerItem> FERTILIZER=ITEMS.registerItem("ningyuan_fertilizer",OrchardItems.FertilizerItem::new);
    public static final DeferredBlock<IronBarsBlock> LATTICE=block("ningyuan_lattice",()->new IronBarsBlock(wood().noOcclusion()));
    public static final DeferredBlock<SlabBlock> BORDER=block("jade_tree_pool_border",()->new SlabBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(2.5F,6).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final DeferredBlock<NingyuanLanternBlock> LANTERN=block("ningyuan_fruit_lantern",()->new NingyuanLanternBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(2).sound(SoundType.LANTERN).noOcclusion().lightLevel(s->OrchardRules.get().lanternLight()).pushReaction(PushReaction.DESTROY)));
    static {
        FEATURES.register("ningyuan_tree",NingyuanTreeFeature::new);
        DISPLAY.addAll(List.of(SIGN_ITEM,HANGING_SIGN_ITEM,BOAT,CHEST_BOAT,FERTILIZER));
        TABS.register("ningyuan_orchard",()->CreativeModeTab.builder().title(Component.literal("凝元灵木"))
                .icon(()->new ItemStack(SAPLING.get())).displayItems((parameters,output)->{
                    DISPLAY.forEach(item->output.accept(item.get()));
                    output.accept(com.example.myvillage.item.ModItems.NINGYUAN_FRUIT_LOW.get());
                    output.accept(com.example.myvillage.item.ModItems.NINGYUAN_FRUIT_HIGH.get());
                }).build());
    }
    public static void register(IEventBus bus) {
        BLOCKS.register(bus);ITEMS.register(bus);FEATURES.register(bus);TABS.register(bus);
        bus.addListener(OrchardContent::setup);bus.addListener(OrchardContent::signTypes);
    }
    private static void signTypes(BlockEntityTypeAddBlocksEvent event) {
        event.modify(BlockEntityType.SIGN,SIGN.get(),WALL_SIGN.get());
        event.modify(BlockEntityType.HANGING_SIGN,HANGING_SIGN.get(),WALL_HANGING_SIGN.get());
    }
    private static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(()->{
            OrchardRules.get();
            ((FlowerPotBlock)Blocks.FLOWER_POT).addPlant(SAPLING.getId(),POTTED);
            var fire=(FireBlock)Blocks.FIRE;
            for(var entry:BLOCKS.getEntries()) {
                Block b=entry.get();
                if(b==BORDER.get() || b==LANTERN.get() || b==POTTED.get()) continue;
                fire.setFlammable(b,b==LEAVES.get()?30:5,b==LEAVES.get()?60:20);
            }
            ComposterBlock.COMPOSTABLES.put(SAPLING.get(),.3F);ComposterBlock.COMPOSTABLES.put(LEAVES.get(),.3F);
        });
    }
    private OrchardContent() { }
}
