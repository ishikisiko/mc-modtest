package com.example.myvillage.dropprobe;

import com.example.myvillage.drops.*;
import com.example.myvillage.item.*;
import com.example.myvillage.sim.commerce.CommerceRules;
import com.example.myvillage.sim.runtime.commerce.CommerceInventory;
import com.example.myvillage.spiritstone.SpiritStoneStorage;
import com.google.gson.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Test-only real registry, inventory transaction, codec and vanilla barrel restart evidence. */
final class MaterialProbe {
    private static final BigInteger START=new BigInteger("10000000000000000000000000000000000000017");
    private static final BigInteger PAID=START.add(BigInteger.valueOf(76_800));
    private static final Gson JSON=new Gson();
    static void register(){NeoForge.EVENT_BUS.addListener(MaterialProbe::commands);}
    private static void check(JsonArray checks,String name,boolean passed) {
        var row=new JsonObject();row.addProperty("name",name);row.addProperty("passed",passed);checks.add(row);
        if(!passed)throw new IllegalStateException(name);
    }
    private static Container barrel(ServerLevel level,int index,boolean create) {
        var pos=new BlockPos(8+index*2,80,8);level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);level.getChunkAt(pos);
        if(create)level.setBlockAndUpdate(pos,Blocks.BARREL.defaultBlockState());
        if(!(level.getBlockEntity(pos) instanceof Container container))throw new IllegalStateException("缺少原生存档容器");
        return container;
    }
    private static void commands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("material_probe").requires(s->s.hasPermission(4))
            .then(Commands.literal("save").executes(c->run(c.getSource(),false)))
            .then(Commands.literal("restore").executes(c->run(c.getSource(),true))));
    }
    private static int run(net.minecraft.commands.CommandSourceStack source,boolean restore) {
        var level=source.getServer().overworld();var checks=new JsonArray();var result=new JsonObject();result.add("checks",checks);
        try {
            var rules=BeastMaterialRules.bundled();var rows=rules.variants();
            check(checks,"registered_effective_combinations",rows.size()==56);
            var ops=level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
            var serialized=new ArrayList<ItemStack>();
            for(var v:rows) {
                var item=BuiltInRegistries.ITEM.get(ResourceLocation.parse(v.id()));
                check(checks,"registered_"+v.id(),item instanceof BeastMaterialItem material&&material.variant().equals(v)&&new ItemStack(item).getMaxStackSize()==64);
                var stack=new ItemStack(item,37);var json=ItemStack.CODEC.encodeStart(ops,stack).getOrThrow();
                var decoded=ItemStack.CODEC.parse(ops,json).getOrThrow();
                check(checks,"codec_"+v.id(),ItemStack.matches(stack,decoded)&&!DropItemVariants.allowsRarity(v.id()));
                serialized.add(stack);
            }
            if(restore) {
                for(int i=0;i<serialized.size();i++)check(checks,"world_restart_"+rows.get(i).id(),ItemStack.matches(serialized.get(i),barrel(level,i/27,false).getItem(i%27)));
                var bag=barrel(level,56/27,false).getItem(56%27);
                check(checks,"world_restart_exact_bag_balance",SpiritStoneStorage.count(bag,SpiritStoneStorage.ordinary(new ItemStack(ModItems.LOW_GRADE_SPIRIT_STONE.get()))).equals(PAID));
                check(checks,"world_restart_disabled_autocollect_preserved",!SpiritStoneStorage.autoCollect(bag));
            } else {
                CommerceRules commerce;
                try(var stream=com.example.myvillage.resources.BuiltinResources.open("data/yuanxian/world_sim/commerce_rules.json")) {
                    commerce=CommerceRules.parse("probe",JsonParser.parseString(new String(stream.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject());
                }
                var offer=commerce.offer("recycle_beast_core_spirit_transformation_legendary");
                check(checks,"real_recycle_offer",offer.recycling()&&offer.price()==25_600&&offer.role().equals("smith"));
                var stock=new ArrayList<ItemStack>();for(int i=0;i<36;i++)stock.add(new ItemStack(Items.STONE,64));
                var core=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(offer.item())),64);stock.set(0,core);
                var bag=new ItemStack(ModItems.SPIRIT_STONE_BAG.get());SpiritStoneStorage.autoCollect(bag,false);
                SpiritStoneStorage.insert(bag,new ItemStack(ModItems.LOW_GRADE_SPIRIT_STONE.get()),START,false);stock.set(1,bag);
                var plan=CommerceInventory.plan(stock,commerce.currency(),offer,3,36);
                check(checks,"recycle_into_paused_bag_with_full_backpack",plan.ready()&&plan.items().get(0).getCount()==61);
                check(checks,"exact_unbounded_bag_payment",SpiritStoneStorage.count(plan.items().get(1),SpiritStoneStorage.ordinary(new ItemStack(ModItems.LOW_GRADE_SPIRIT_STONE.get()))).equals(PAID));
                check(checks,"input_inventory_unchanged",core.getCount()==64&&SpiritStoneStorage.count(bag,SpiritStoneStorage.ordinary(new ItemStack(ModItems.LOW_GRADE_SPIRIT_STONE.get()))).equals(START));
                var full=new ArrayList<ItemStack>();for(int i=0;i<36;i++)full.add(new ItemStack(Items.STONE,64));full.set(0,core.copy());
                check(checks,"insufficient_output_space_is_atomic",CommerceInventory.plan(full,commerce.currency(),offer,3,36).result().equals("inventory_full")&&full.get(0).getCount()==64);
                var lower=offer.item().replace("_legendary","_common");full.set(0,new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(lower)),64));
                check(checks,"wrong_rarity_cannot_be_sold_as_legendary",CommerceInventory.plan(full,commerce.currency(),offer,3,36).result().equals("missing_items")&&full.get(0).getCount()==64);
                var named=core.copy();named.set(DataComponents.CUSTOM_NAME,Component.literal("保留的妖丹"));full.set(0,named);
                check(checks,"custom_components_are_not_silently_removed",CommerceInventory.plan(full,commerce.currency(),offer,3,36).result().equals("missing_items")&&named.getCount()==64);
                serialized.add(plan.items().get(1));
                for(int i=0;i<3;i++)barrel(level,i,true).clearContent();
                for(int i=0;i<serialized.size();i++){var b=barrel(level,i/27,false);b.setItem(i%27,serialized.get(i));b.setChanged();}
                check(checks,"vanilla_barrels_written_for_restart",serialized.size()==57);
            }
            result.addProperty("status","passed");
        }catch(Exception error){result.addProperty("status","failed");result.addProperty("error",error.toString());}
        try{java.nio.file.Files.writeString(java.nio.file.Path.of("material-probe-"+(restore?"restore":"save")+".json"),JSON.toJson(result));}
        catch(java.io.IOException error){throw new IllegalStateException(error);}
        var summary=new JsonObject();summary.addProperty("status",result.get("status").getAsString());summary.addProperty("checks",checks.size());
        if(result.has("error"))summary.add("error",result.get("error"));
        source.sendSuccess(()->Component.literal("MATERIAL_PROBE "+JSON.toJson(summary)),false);
        return result.get("status").getAsString().equals("passed")?1:0;
    }
}
