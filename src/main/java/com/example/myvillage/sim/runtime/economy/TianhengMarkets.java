package com.example.myvillage.sim.runtime.economy;

import com.example.myvillage.combat.art.ArtDefinition;
import com.example.myvillage.combat.art.EarlyRealmArt;
import com.example.myvillage.combat.art.GoldenCoreArt;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.cultivation.data.TechniqueEffects;
import com.example.myvillage.item.ModItems;
import com.example.myvillage.item.TechniqueManualItem;
import com.example.myvillage.npc.NpcDialogueText;
import com.example.myvillage.sim.commerce.CommerceRules;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.example.myvillage.sim.runtime.commerce.CommerceInventory;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Public stock follows the bundled region bands and the current technique registry. */
final class TianhengMarkets {
    static final String STORE = "tianheng_store";
    static final String AUCTION = "tianheng_auction";
    static final ResourceLocation BREATHING = ResourceLocation.parse("yuanxian:basic_breathing");
    private static final List<String> GRADES = List.of("凡品", "黄品", "玄品", "地品", "天品");

    record Band(int shopGrade, int fallback, String herb, String metal, int materialPrice) { }
    record Rules(Map<String, String> regions, Map<String, Band> bands, int perRealm, int previewTicks,
                 int lotTicks, int stepPercent, int npcBudgetPercent) { }
    record Goods(String id, String title, String description, String kind, ItemStack stack, int price) {
        EconomyMenu.Row row() {
            return new EconomyMenu.Row(id, title, BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                    stack.getCount(), 0, price, description);
        }
    }
    private record Manual(ResourceLocation id, TechniqueDefinition definition, ResourceLocation realm) { }

    private TianhengMarkets() { }

    private static final class Bundled { private static final Rules RULES = load(); }
    static Rules rules() { return Bundled.RULES; }

    static List<Goods> stock(RegistryAccess registry, String region, long day, CommerceRules commerce) {
        var result = new ArrayList<Goods>();
        String ceiling = rules().regions().get(region);
        if (ceiling == null) return result;
        var manuals = catalogue(registry);
        var starter = commerce.offer("buy_starter_manual_innkeeper");
        if (starter != null) ModCultivationRegistries.technique(registry, BREATHING).ifPresent(definition ->
                result.add(new Goods(BREATHING.toString(), NpcDialogueText.of(Component.translatable(definition.translationKey())),
                        "凡品 · 心法", "manual", new ItemStack(ModItems.BASIC_BREATHING_MANUAL.get()),
                        starter.price())));
        var realmArts = ArtDefinition.all().stream().filter(ArtDefinition::usesServerParticleMarks)
                .collect(java.util.stream.Collectors.toMap(ArtDefinition::id, art -> art));
        var regionalBand = rules().bands().get(ceiling);
        var regionalRealm = ResourceLocation.parse("yuanxian:" + ceiling);
        for (var manual : manuals) {
            var art = realmArts.get(manual.id());
            if (art == null || !manual.realm().equals(regionalRealm) || manual.definition().grade() > regionalBand.shopGrade()) continue;
            boolean stocked = art.goldenCore().isPresent() && GoldenCoreArt.rules().shelfRegions().contains(region)
                    || art.earlyRealm().filter(EarlyRealmArt::foundation).isPresent() && EarlyRealmArt.shelfRegions().contains(region);
            if (stocked) result.add(goods(registry, manual, price(manual, regionalBand)));
        }
        int limit = realmOrder(registry, ResourceLocation.parse("yuanxian:" + ceiling));
        for (var band : rules().bands().entrySet()) {
            ResourceLocation realm = ResourceLocation.parse("yuanxian:" + band.getKey());
            if (realmOrder(registry, realm) > limit) continue;
            var pool = manuals.stream().filter(m -> m.realm().equals(realm) && !realmArts.containsKey(m.id())
                    && m.definition().grade() > 0 && m.definition().grade() <= band.getValue().shopGrade()).toList();
            if (pool.isEmpty()) continue;
            int offset = Math.floorMod(day + region.hashCode(), pool.size());
            for (int i = 0; i < Math.min(rules().perRealm(), pool.size()); i++) {
                var manual = pool.get((i + offset) % pool.size());
                result.add(goods(registry, manual, price(manual, band.getValue())));
            }
        }
        return List.copyOf(result);
    }

    static String buy(ServerPlayer player, String site, long day, EconomyMenu.Row row) {
        if (EconomyRuntime.day(player) != day) return "stale";
        var sim = WorldSimRuntime.sim().orElseThrow();
        String region = sim.lifeSite(site).orElseThrow().regionId();
        var goods = stock(player.registryAccess(), region, day, sim.commerceRules()).stream()
                .filter(candidate -> candidate.id().equals(row.id()) && candidate.price() == row.cost()).findFirst().orElse(null);
        if (goods == null) return "stale";
        var plan = EconomyInventory.tianhengPayment(player, goods.price(), goods.stack());
        return plan.ready() ? plan.commit() ? "purchased" : "stale" : plan.result();
    }

    static List<Goods> lots(RegistryAccess registry, String region, long seed) {
        String ceiling = rules().regions().get(region);
        var band = rules().bands().get(ceiling);
        if (band == null) return List.of();
        var random = new Random(seed);
        var realm = ResourceLocation.parse("yuanxian:" + ceiling);
        var eligible = catalogue(registry).stream().filter(manual -> manual.realm().equals(realm)
                && manual.definition().grade() > band.shopGrade()).toList();
        var lots = new ArrayList<Goods>();
        if (!eligible.isEmpty()) {
            var manual = eligible.get(random.nextInt(eligible.size()));
            lots.add(goods(registry, manual, price(manual, band)));
        }
        lots.add(material("herb", band.herb(), band.materialPrice()));
        lots.add(material("metal", band.metal(), band.materialPrice()));
        return List.copyOf(lots);
    }

    private static int price(Manual manual, Band band) {
        return manual.definition().effects().flatMap(TechniqueEffects::core).map(TechniqueEffects.Core::manualPrice)
                .filter(price -> price > 0).orElse(band.fallback() * (manual.definition().grade() > band.shopGrade() ? 2 : 1));
    }

    private static Goods goods(RegistryAccess registry, Manual manual, int price) {
        var stack = TechniqueManualItem.manualFor(registry, manual.id());
        var realm = registry.registryOrThrow(ModCultivationRegistries.REALMS).get(manual.realm());
        var type = switch (manual.definition().category()) {
            case CORE -> "心法";
            case ACTIVE -> "绝技";
            case MOVEMENT -> "身法";
            case BODY -> "炼体";
        };
        return new Goods(manual.id().toString(), NpcDialogueText.of(Component.translatable(manual.definition().translationKey())),
                NpcDialogueText.of(Component.translatable(realm.translationKey())) + " · "
                        + GRADES.get(manual.definition().grade()) + " · " + type,
                "manual", stack, price);
    }

    private static Goods material(String kind, String id, int price) {
        var stack = new ItemStack(CommerceInventory.item(id));
        return new Goods(id, NpcDialogueText.of(stack.getHoverName()), kind.equals("herb") ? "药材或丹方" : "炼器材料", kind, stack, price);
    }

    private static int realmOrder(RegistryAccess registry, ResourceLocation id) {
        var realm = registry.registryOrThrow(ModCultivationRegistries.REALMS).get(id);
        return realm == null ? Integer.MAX_VALUE : realm.sortOrder();
    }

    private static List<Manual> catalogue(RegistryAccess registry) {
        var manuals = new ArrayList<Manual>();
        var exclusive = registry.registryOrThrow(ModCultivationRegistries.HERITAGES).stream()
                .filter(heritage -> heritage.exclusive()).flatMap(heritage -> heritage.techniques().stream())
                .collect(java.util.stream.Collectors.toSet());
        registry.registryOrThrow(ModCultivationRegistries.TECHNIQUES).entrySet().forEach(entry -> {
            var id = entry.getKey().location();
            var definition = entry.getValue();
            definition.requirements().minimumRealm().ifPresent(realm -> {
                if (!exclusive.contains(id) && definition.grade() > 0) {
                    manuals.add(new Manual(id, definition, realm));
                }
            });
        });
        manuals.sort(Comparator.comparing(Manual::id));
        return manuals;
    }

    private static Rules load() {
        try (var stream = TianhengMarkets.class.getResourceAsStream("/data/yuanxian/world_sim/tianheng_trade.json")) {
            if (stream == null) throw new IllegalStateException("天衡殿缺少交易规则");
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            var bands = new LinkedHashMap<String, Band>();
            json.getAsJsonObject("realms").entrySet().forEach(entry -> {
                var value = entry.getValue().getAsJsonObject();
                int grade = value.get("shop_grade").getAsInt();
                int price = value.get("fallback_price").getAsInt();
                int material = value.get("material_price").getAsInt();
                if (grade < 1 || grade > 3 || price <= 0 || material <= 0)
                    throw new IllegalArgumentException("天衡殿境界档位无效");
                bands.put(entry.getKey(), new Band(grade, price, value.get("herb").getAsString(),
                        value.get("ore").getAsString(), material));
            });
            var regions = new LinkedHashMap<String, String>();
            json.getAsJsonObject("regions").entrySet().forEach(entry -> {
                String band = entry.getValue().getAsString();
                if (!bands.containsKey(band)) throw new IllegalArgumentException("天衡殿州城档位无效");
                regions.put(entry.getKey(), band);
            });
            int perRealm = json.get("shelf_per_realm").getAsInt();
            int preview = json.get("preview_seconds").getAsInt();
            int lot = json.get("lot_seconds").getAsInt();
            int step = json.get("bid_step_percent").getAsInt();
            int budget = json.get("npc_budget_percent").getAsInt();
            long golden = ArtDefinition.all().stream().filter(art -> art.goldenCore().isPresent()).count();
            long foundation = ArtDefinition.all().stream().filter(art -> art.earlyRealm().filter(EarlyRealmArt::foundation).isPresent()).count();
            if (bands.size() != 5 || regions.size() != 8 || perRealm < 1
                    || 1 + bands.size() * perRealm + Math.max(golden, foundation) > EconomyMenu.MAX_ROWS
                    || preview < 1 || preview > 60 || lot < 10 || lot > 180 || step < 1 || step > 100
                    || budget <= 100 || budget > 300) throw new IllegalArgumentException("天衡殿交易规则无效");
            return new Rules(Map.copyOf(regions), java.util.Collections.unmodifiableMap(bands),
                    perRealm, preview * 20, lot * 20, step, budget);
        } catch (IOException failure) {
            throw new IllegalStateException("无法读取天衡殿交易规则", failure);
        }
    }
}
