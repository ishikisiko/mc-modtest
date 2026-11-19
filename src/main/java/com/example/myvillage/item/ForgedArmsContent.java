package com.example.myvillage.item;

import com.example.myvillage.resources.BuiltinResources;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Objects;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Only new weapons are registered here. Existing weapon IDs keep their original registry owners. */
final class ForgedArmsContent {
    private ForgedArmsContent() {}

    static List<DeferredItem<CombatWeaponItem>> register(DeferredRegister.Items registry) {
        try (var stream = BuiltinResources.open("data/yuanxian/combat/forged_arms.json");
             var reader = new InputStreamReader(Objects.requireNonNull(stream,"Missing forged weapon catalogue"), StandardCharsets.UTF_8)) {
            var entries = JsonParser.parseReader(reader).getAsJsonArray();
            var items = new ArrayList<DeferredItem<CombatWeaponItem>>();
            for (var entry : entries) {
                var data = entry.getAsJsonObject();
                String id = data.get("id").getAsString();
                Tiers tier = Tiers.valueOf(data.get("item_tier").getAsString().toUpperCase(Locale.ROOT));
                int attack = data.get("attack").getAsInt();
                float speed = switch (data.get("family").getAsString()) {
                    case "sword" -> -2.4F;
                    case "spear" -> -2.8F;
                    default -> throw new IllegalArgumentException("Unknown forged weapon family: " + id);
                };
                items.add(registry.registerItem(id,
                        props -> new CombatWeaponItem(tier, props.attributes(SwordItem.createAttributes(tier, attack, speed)))));
            }
            return List.copyOf(items);
        } catch (IOException e) {
            throw new IllegalStateException("Missing forged weapon catalogue", e);
        }
    }
}
