package com.example.myvillage.forging;

import com.example.myvillage.block.ModBlocks;
import com.example.myvillage.item.ModDataComponents;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FoundryLifecycleTest {
    private static final TagKey<Item> FUEL=TagKey.create(net.minecraft.core.registries.Registries.ITEM,ResourceLocation.parse("myvillage:forging_fuels"));
    private static final HolderLookup.Provider REGISTRIES=RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    @BeforeAll @SuppressWarnings("deprecation") static void bootstrap(){
        PhysicalWorkshopTest.bootstrap();
        var blocks=(MappedRegistry<Block>)BuiltInRegistries.BLOCK;blocks.unfreeze();
        try{
            if(!blocks.containsKey(ModBlocks.FORGING_TABLE.getId()))Registry.register(blocks,ModBlocks.FORGING_TABLE.getId(),new com.example.myvillage.block.ForgingTableBlock(Block.Properties.of()));
            for(var kind:List.of(FoundryMenu.Kind.KILN,FoundryMenu.Kind.ASSEMBLY)){
                var id=ResourceLocation.parse("myvillage:"+(kind==FoundryMenu.Kind.KILN?"heating_kiln":"assembly_table"));
                if(!blocks.containsKey(id))Registry.register(blocks,id,new FoundryStationBlock(Block.Properties.of(),kind));
            }
        }finally{blocks.freeze();}
        var types=(MappedRegistry<BlockEntityType<?>>)BuiltInRegistries.BLOCK_ENTITY_TYPE;types.unfreeze();
        try{
            Registry.register(types,ResourceLocation.parse("myvillage:forging_ding"),BlockEntityType.Builder.of(PreparationBlockEntity::new,ModBlocks.FORGING_TABLE.get()).build(null));
            Registry.register(types,ResourceLocation.parse("myvillage:heating_kiln"),BlockEntityType.Builder.of(HeatingKilnBlockEntity::new,ModBlocks.HEATING_KILN.get()).build(null));
            Registry.register(types,ResourceLocation.parse("myvillage:assembly_table"),BlockEntityType.Builder.of(AssemblyBlockEntity::new,ModBlocks.ASSEMBLY_TABLE.get()).build(null));
        }finally{types.freeze();}
    }
    @BeforeEach void fuel(){BuiltInRegistries.ITEM.bindTags(Map.of(FUEL,List.of(Items.COAL.builtInRegistryHolder())));}
    @AfterEach void clear(){BuiltInRegistries.ITEM.bindTags(Map.of(FUEL,List.of()));}
    private static void field(Object object,String name,Object value)throws Exception{
        Class<?> owner=object.getClass();java.lang.reflect.Field field=null;
        while(owner!=null){try{field=owner.getDeclaredField(name);break;}catch(NoSuchFieldException e){owner=owner.getSuperclass();}}
        assertNotNull(field,name);field.setAccessible(true);field.set(object,value);
    }
    private static CompoundTag save(FoundryBlockEntity block){var tag=new CompoundTag();block.saveAdditional(tag,REGISTRIES);return tag;}
    private static PreparationBlockEntity preparation(){return new PreparationBlockEntity(BlockPos.ZERO,ModBlocks.FORGING_TABLE.get().defaultBlockState());}
    private static AssemblyBlockEntity assembly(){return new AssemblyBlockEntity(BlockPos.ZERO,ModBlocks.ASSEMBLY_TABLE.get().defaultBlockState());}
    private static ItemStack blank(int stage){return WorkshopItems.blank(new Workpiece("forged_shovel",stage,stage==0?0:WorkshopRules.get().formingPoints("forged_shovel"),0));}
    private static PreparationBlockEntity batch()throws Exception{
        var block=preparation();var input=blank(0);block.setItem(0,input);
        var costs=PreparationBlockEntity.automaticCosts(input.get(ModDataComponents.WORKPIECE.get()),true);
        var reserved=new ArrayList<ItemStack>();costs.forEach((ref,count)->reserved.add(new ItemStack(ref.startsWith("#")?Items.COAL:BuiltInRegistries.ITEM.get(ResourceLocation.parse(ref)),count)));
        field(block,"snapshot",input.copy());field(block,"frozen",PreparationBlockEntity.standard(input,2));field(block,"owner",UUID.randomUUID());
        field(block,"paidRaw",true);field(block,"reserved",reserved);field(block,"quality",2);field(block,"duration",400);field(block,"progress",17);return block;
    }
    @Test void automaticBatchRestoresItsExactEscrowAndFrozenItemWithoutAddingOfflineProgress()throws Exception{
        var original=save(batch());var restored=preparation();restored.loadAdditional(original,REGISTRIES);var actual=save(restored);
        assertTrue(restored.active());assertTrue(restored.locked(0));assertEquals(17,restored.data.get(1));assertEquals(400,restored.data.get(2));
        for(String key:List.of("Items","Snapshot","Frozen","Reserved","Owner","PaidRaw"))assertEquals(original.get(key),actual.get(key),key);
        assertFalse(actual.getBoolean("Invalid"));assertTrue(restored.getItem(2).isEmpty());
    }
    @Test void damagedAutomaticSnapshotOrEscrowCannotProduceAFrozenReward()throws Exception{
        var original=save(batch());
        for(String key:List.of("Snapshot","Reserved","Owner")){
            var corrupt=original.copy();corrupt.remove(key);var restored=preparation();restored.loadAdditional(corrupt,REGISTRIES);
            assertTrue(restored.active(),key);assertEquals(9,restored.data.get(3));assertTrue(save(restored).getBoolean("Invalid"));
            assertFalse(save(restored).contains("Frozen"));assertTrue(restored.getItem(2).isEmpty());
        }
    }
    @Test void assemblySaveKeepsTheOriginalAndRejectsTamperingWithEitherPart()throws Exception{
        var block=assembly();var input=blank(4);var material=new ItemStack(com.example.myvillage.item.ModItems.FOUNDRY_ITEMS.get("bamboo_handle").get());
        block.setItem(0,input);block.setItem(1,material);var plan=AssemblyPlan.prepare(input,material,2);assertTrue(plan.valid());
        field(block,"snapshot",input.copy());field(block,"material",material.copy());field(block,"frozen",plan.result());field(block,"owner",UUID.randomUUID());
        field(block,"quality",2);field(block,"duration",FoundryRules.get().assembly().ticks());field(block,"progress",3);
        var original=save(block);var restored=assembly();restored.loadAdditional(original,REGISTRIES);
        assertTrue(restored.active());assertFalse(save(restored).getBoolean("Invalid"));assertEquals(3,restored.data.get(1));assertTrue(ItemStack.matches(input,restored.getItem(0)));
        var corrupt=original.copy();corrupt.remove("Material");var invalid=assembly();invalid.loadAdditional(corrupt,REGISTRIES);
        assertTrue(invalid.active());assertEquals(7,invalid.data.get(3));assertTrue(ItemStack.matches(input,invalid.getItem(0)));assertTrue(ItemStack.matches(material,invalid.getItem(1)));assertTrue(invalid.getItem(2).isEmpty());
    }
    @Test void kilnRetainsItemHeatAndFuelAcrossSaveAndKeepsOnlyOneItem(){
        var block=new HeatingKilnBlockEntity(BlockPos.ZERO,ModBlocks.HEATING_KILN.get().defaultBlockState());var input=blank(0);ForgeHeat.set(input,900,1000);
        block.setItem(0,input);block.setItem(1,new ItemStack(Items.COAL,5));var saved=save(block);saved.putInt("Burn",765);
        var restored=new HeatingKilnBlockEntity(BlockPos.ZERO,ModBlocks.HEATING_KILN.get().defaultBlockState());restored.loadAdditional(saved,REGISTRIES);
        assertEquals(765,restored.fuel());var taken=restored.removeItemNoUpdate(0);assertTrue(ItemStack.matches(input,taken));assertEquals(890,ForgeHeat.get(taken,1100));assertTrue(restored.removeItemNoUpdate(0).isEmpty());
        assertEquals(5,restored.getItem(1).getCount());
    }
}
