package com.example.myvillage.legacy;

import com.example.myvillage.entity.ModEntities;
import com.example.myvillage.item.*;
import com.example.myvillage.block.*;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.*;

public final class LegacyContent {
    public static final Map<String,DeferredHolder<EntityType<?>,EntityType<LegacyBeastEntity>>> BEASTS=new LinkedHashMap<>();
    public static final Map<String,DeferredItem<? extends Item>> ITEMS=new LinkedHashMap<>();
    public static final DeferredHolder<EntityType<?>,EntityType<LegacyProjectionEntity>> PROJECTION=ModEntities.ENTITY_TYPES.register("legacy_projection",
            id->EntityType.Builder.<LegacyProjectionEntity>of(LegacyProjectionEntity::new,MobCategory.MISC).sized(.4F,.4F).clientTrackingRange(12).updateInterval(1).noSave().noSummon().build(id.toString()));
    public static final DeferredBlock<LegacySteleBlock> SWORD_STELE=ModBlocks.BLOCKS.registerBlock("sword_inscription",LegacySteleBlock::new,steleProperties());
    public static final DeferredBlock<LegacySteleBlock> MOUNTAIN_STELE=ModBlocks.BLOCKS.registerBlock("mountain_stele",LegacySteleBlock::new,steleProperties());
    public static final Supplier<BlockEntityType<LegacySteleEntity>> STELE_ENTITY=ModBlockEntities.BLOCK_ENTITIES.register("legacy_stele",
            ()->BlockEntityType.Builder.of(LegacySteleEntity::new,SWORD_STELE.get(),MOUNTAIN_STELE.get()).build(null));
    static {
        for(var b:LegacyRules.get().beasts()) {
            var holder=ModEntities.ENTITY_TYPES.register(b.id(),id->EntityType.Builder.<LegacyBeastEntity>of(LegacyBeastEntity::new,MobCategory.MONSTER)
                    .sized((float)b.physical("width"),(float)b.physical("height")).eyeHeight((float)b.physical("eye_height"))
                    .clientTrackingRange(12).updateInterval(1).build(id.toString()));
            BEASTS.put(b.id(),holder);
            var colors=b.data().getAsJsonArray("palette");
            ITEMS.put(b.id()+"_spawn_egg",ModItems.ITEMS.registerItem(b.id()+"_spawn_egg",p->new net.neoforged.neoforge.common.DeferredSpawnEggItem(holder,
                    Integer.parseInt(colors.get(0).getAsString(),16),Integer.parseInt(colors.get(1).getAsString(),16),p)));
            ITEMS.put(b.material(),ModItems.ITEMS.registerItem(b.material(),p->new LegacyArtifactItem(p,b.material())));
        }
        for(var e:LegacyRules.get().items()) {
            var row=e.getAsJsonObject();String id=row.get("id").getAsString(),kind=row.get("kind").getAsString();
            if(Set.of("sword","ruler").contains(kind))ITEMS.put(id,ModItems.ITEMS.registerItem(id,
                    p->new CombatWeaponItem(Tiers.valueOf(row.get("tier").getAsString()),p.attributes(SwordItem.createAttributes(Tiers.valueOf(row.get("tier").getAsString()),row.get("attack_damage").getAsInt(),row.get("attack_speed").getAsFloat())))));
            else ITEMS.put(id,ModItems.ITEMS.registerItem(id,p->new LegacyArtifactItem(p.stacksTo(1),kind)));
        }
        for(String id:List.of("sword_rubbing","mountain_rubbing"))ITEMS.put(id,ModItems.ITEMS.registerItem(id,Item::new));
        ITEMS.put("sword_inscription",ModItems.ITEMS.registerItem("sword_inscription",p->new BlockItem(SWORD_STELE.get(),p)));
        ITEMS.put("mountain_stele",ModItems.ITEMS.registerItem("mountain_stele",p->new BlockItem(MOUNTAIN_STELE.get(),p)));
    }
    private LegacyContent() {}
    private static BlockBehaviour.Properties steleProperties() {return BlockBehaviour.Properties.of().strength(-1,3600000).noLootTable().sound(SoundType.STONE).pushReaction(PushReaction.BLOCK);}
    public static void initialize() {}
    public static Item item(String id) {return ITEMS.get(id).get();}
    public static void creative(CreativeModeTab.Output output) {ITEMS.values().forEach(i->output.accept(i.get()));}
}
