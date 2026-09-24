package dev.devbridge.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

/** Converters from game objects to JSON. */
public final class Json {
    private Json() {}

    public static JsonObject obj() {
        return new JsonObject();
    }

    public static JsonElement of(Object v) {
        if (v == null) return JsonNull.INSTANCE;
        if (v instanceof JsonElement e) return e;
        if (v instanceof Number n) return new JsonPrimitive(n);
        if (v instanceof Boolean b) return new JsonPrimitive(b);
        if (v instanceof Character c) return new JsonPrimitive(c);
        if (v instanceof CharSequence s) return new JsonPrimitive(s.toString());
        if (v instanceof Enum<?> e) return new JsonPrimitive(e.name());
        if (v instanceof ResourceLocation rl) return new JsonPrimitive(rl.toString());
        if (v instanceof Component c) return new JsonPrimitive(c.getString());
        if (v instanceof Tag t) return new JsonPrimitive(t.toString());
        if (v instanceof BlockPos p) return pos(p);
        if (v instanceof Vec3 p) return vec(p);
        if (v instanceof BlockState s) return blockState(s);
        if (v instanceof ItemStack s) return new JsonPrimitive(s.toString());
        if (v instanceof Entity e) return entity(e, false);
        if (v instanceof Iterable<?> it) {
            JsonArray arr = new JsonArray();
            for (Object o : it) arr.add(of(o));
            return arr;
        }
        if (v instanceof Object[] array) {
            JsonArray arr = new JsonArray();
            for (Object o : array) arr.add(of(o));
            return arr;
        }
        if (v instanceof Map<?, ?> map) {
            JsonObject o = new JsonObject();
            for (var e : map.entrySet()) o.add(String.valueOf(e.getKey()), of(e.getValue()));
            return o;
        }
        return new JsonPrimitive(String.valueOf(v));
    }

    public static JsonObject pos(BlockPos p) {
        JsonObject o = new JsonObject();
        o.addProperty("x", p.getX());
        o.addProperty("y", p.getY());
        o.addProperty("z", p.getZ());
        return o;
    }

    public static JsonObject vec(Vec3 p) {
        JsonObject o = new JsonObject();
        o.addProperty("x", p.x);
        o.addProperty("y", p.y);
        o.addProperty("z", p.z);
        return o;
    }

    public static JsonObject blockState(BlockState state) {
        JsonObject o = new JsonObject();
        o.addProperty("block", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        o.addProperty("state", BlockStateParser.serialize(state));
        JsonObject props = new JsonObject();
        for (Map.Entry<Property<?>, Comparable<?>> e : state.getValues().entrySet()) {
            props.addProperty(e.getKey().getName(), propertyValue(e.getKey(), e.getValue()));
        }
        o.add("properties", props);
        return o;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String propertyValue(Property property, Comparable value) {
        return property.getName(value);
    }

    public static JsonObject blockEntity(BlockEntity be, HolderLookup.Provider registries) {
        JsonObject o = new JsonObject();
        o.addProperty("type", BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).toString());
        o.addProperty("class", be.getClass().getName());
        CompoundTag tag = be.saveWithFullMetadata(registries);
        o.addProperty("nbt", tag.toString());
        return o;
    }

    public static JsonObject entity(Entity e, boolean withNbt) {
        JsonObject o = new JsonObject();
        o.addProperty("id", e.getId());
        o.addProperty("uuid", e.getUUID().toString());
        o.addProperty("type", BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString());
        o.addProperty("class", e.getClass().getName());
        o.addProperty("name", e.getName().getString());
        o.add("pos", vec(e.position()));
        o.addProperty("yaw", e.getYRot());
        o.addProperty("pitch", e.getXRot());
        o.addProperty("dimension", e.level().dimension().location().toString());
        if (e instanceof LivingEntity living) {
            o.addProperty("health", living.getHealth());
            o.addProperty("maxHealth", living.getMaxHealth());
        }
        if (withNbt) {
            o.addProperty("nbt", e.saveWithoutId(new CompoundTag()).toString());
        }
        return o;
    }

    public static JsonObject itemStack(ItemStack stack, HolderLookup.Provider registries) {
        JsonObject o = new JsonObject();
        if (stack.isEmpty()) {
            o.addProperty("empty", true);
            return o;
        }
        o.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        o.addProperty("count", stack.getCount());
        o.addProperty("name", stack.getHoverName().getString());
        o.addProperty("nbt", stack.save(registries).toString());
        return o;
    }
}
