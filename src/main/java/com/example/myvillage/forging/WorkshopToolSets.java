package com.example.myvillage.forging;

import com.example.myvillage.resources.BuiltinResources;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Material variants use normal DiggerItems, including their original action hooks and quality. */
public final class WorkshopToolSets {
    static final String RESOURCE = "data/yuanxian/forging/tool_sets.json";

    private WorkshopToolSets() {}

    record Material(String prefix, Tier tier) {}

    static List<Material> materials() {
        try (var stream = BuiltinResources.open(RESOURCE);
             var reader = new InputStreamReader(Objects.requireNonNull(stream, "Missing forged tool sets"),
                     StandardCharsets.UTF_8)) {
            var materials = new ArrayList<Material>();
            var prefixes = new HashSet<String>();
            for (var entry : JsonParser.parseReader(reader).getAsJsonArray()) {
                var row = entry.getAsJsonObject();
                String prefix = row.get("prefix").getAsString();
                if (!prefix.matches("[a-z0-9_]{1,48}") || !prefixes.add(prefix)) {
                    throw new IllegalArgumentException("Invalid forged tool prefix: " + prefix);
                }
                var vanilla = Tiers.valueOf(row.get("item_tier").getAsString().toUpperCase(Locale.ROOT));
                var material = ResourceLocation.parse(row.get("ingot").getAsString());
                float baseSpeed = row.get("base_speed").getAsFloat();
                if (!Float.isFinite(baseSpeed) || baseSpeed <= 1) {
                    throw new IllegalArgumentException("Invalid forged tool speed: " + prefix);
                }
                materials.add(new Material(prefix, new WorkshopTier(vanilla, baseSpeed, material)));
            }
            return List.copyOf(materials);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read forged tool sets", e);
        }
    }

    public static List<DeferredItem<Item>> register(DeferredRegister.Items registry) {
        var items = new ArrayList<DeferredItem<Item>>();
        for (var material : materials()) {
            for (String kind : WorkshopRules.TOOL_KINDS) {
                String id = material.prefix() + "_" + kind;
                var craft = WorkshopRules.get().craft(id);
                if (!kind.equals(craft.kind()) || !craft.requires_pattern()
                        || !ForgingRules.get().recipe(id).orElseThrow().output().equals("yuanxian:" + id)) {
                    throw new IllegalArgumentException("Forged tool and workshop rules disagree: " + id);
                }
                items.add(registry.registerItem(id, props -> create(kind, material.tier(), props)));
            }
        }
        return List.copyOf(items);
    }

    static Item create(String kind, Tier tier, Item.Properties props) {
        return switch (kind) {
            case "pickaxe" -> new WorkshopTools.Pickaxe(tier, props.attributes(DiggerItem.createAttributes(tier,1,-2.8F)));
            case "axe" -> new WorkshopTools.Axe(tier, props.attributes(DiggerItem.createAttributes(tier,5,-3)));
            case "shovel" -> new WorkshopTools.Shovel(tier, props.attributes(DiggerItem.createAttributes(tier,1.5F,-3)));
            case "hoe" -> new WorkshopTools.Hoe(tier, props.attributes(DiggerItem.createAttributes(tier,-tier.getAttackDamageBonus(),0)));
            default -> throw new IllegalArgumentException("Unknown forged tool type: " + kind);
        };
    }

    private record WorkshopTier(Tiers vanilla, float speed, ResourceLocation ingot) implements Tier {
        @Override public int getUses() { return vanilla.getUses(); }
        @Override public float getSpeed() { return speed; }
        @Override public float getAttackDamageBonus() { return vanilla.getAttackDamageBonus(); }
        @Override public TagKey<Block> getIncorrectBlocksForDrops() { return vanilla.getIncorrectBlocksForDrops(); }
        @Override public int getEnchantmentValue() { return vanilla.getEnchantmentValue(); }
        @Override public Ingredient getRepairIngredient() {
            return Ingredient.of(BuiltInRegistries.ITEM.getOptional(ingot).orElseThrow());
        }
    }
}
