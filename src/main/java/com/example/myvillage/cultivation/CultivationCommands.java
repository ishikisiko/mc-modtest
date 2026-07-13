package com.example.myvillage.cultivation;

import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.RealmDefinition;
import com.example.myvillage.cultivation.data.SpiritualElementDefinition;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.cultivation.root.SpiritualRootAwakeningService;
import com.example.myvillage.cultivation.technique.TechniqueInheritanceService;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class CultivationCommands {
    private CultivationCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return commandTree("cultivation");
    }

    public static LiteralArgumentBuilder<CommandSourceStack> pinyinCommand() {
        return commandTree("xiulian");
    }

    private static LiteralArgumentBuilder<CommandSourceStack> commandTree(String rootLiteral) {
        return Commands.literal(rootLiteral)
                .then(infoCommand("info"))
                .then(infoCommand("chakan"))
                .then(resetCommand("reset"))
                .then(resetCommand("chongzhi"))
                .then(realmCommand("setrealm"))
                .then(realmCommand("shezhijingjie"))
                .then(progressCommand("setprogress"))
                .then(progressCommand("shezhixiuwei"))
                .then(stabilityCommand("setstability"))
                .then(stabilityCommand("shezhiwendingdu"))
                .then(powerCommand("setpower"))
                .then(powerCommand("shezhilingli"))
                .then(setRootCommand("setroot"))
                .then(setRootCommand("shezhilinggen"))
                .then(clearRootCommand("clearroot"))
                .then(clearRootCommand("qingchulinggen"))
                .then(techniqueCommand("learn", CultivationCommands::learnTechnique))
                .then(techniqueCommand("xuexi", CultivationCommands::learnTechnique))
                .then(techniqueCommand("forget", CultivationCommands::forgetTechnique))
                .then(techniqueCommand("yiwang", CultivationCommands::forgetTechnique))
                .then(masteryCommand("setmastery"))
                .then(masteryCommand("shezhishuliandu"))
                .then(awakenCommand("awaken"))
                .then(awakenCommand("juexing"))
                .then(initiateCommand("initiate"))
                .then(initiateCommand("rumen"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> infoCommand(String literal) {
        return Commands.literal(literal)
                .executes(context -> info(
                        context.getSource(), context.getSource().getPlayerOrException()))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(context -> info(
                                context.getSource(), EntityArgument.getPlayer(context, "target"))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> resetCommand(String literal) {
        return Commands.literal(literal)
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(context -> report(
                                context.getSource(),
                                EntityArgument.getPlayer(context, "target"),
                                CultivationService.resetProfile(
                                        EntityArgument.getPlayer(context, "target")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> awakenCommand(String literal) {
        return Commands.literal(literal)
                .executes(context -> awaken(
                        context.getSource(), context.getSource().getPlayerOrException()))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(context -> awaken(
                                context.getSource(),
                                EntityArgument.getPlayer(context, "target"))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> initiateCommand(String literal) {
        return Commands.literal(literal)
                .executes(context -> initiate(
                        context.getSource(), context.getSource().getPlayerOrException()))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(context -> initiate(
                                context.getSource(),
                                EntityArgument.getPlayer(context, "target"))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> realmCommand(String literal) {
        return Commands.literal(literal)
                .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("realm_id", ResourceLocationArgument.id())
                                .suggests(CultivationCommands::suggestRealms)
                                .then(Commands.argument("stage_id", ResourceLocationArgument.id())
                                        .suggests(CultivationCommands::suggestStages)
                                        .executes(context -> {
                                            ServerPlayer target = EntityArgument.getPlayer(context, "target");
                                            return report(
                                                    context.getSource(),
                                                    target,
                                                    CultivationService.setRealmAndStage(
                                                            target,
                                                            ResourceLocationArgument.getId(context, "realm_id"),
                                                            ResourceLocationArgument.getId(context, "stage_id")));
                                        }))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> progressCommand(String literal) {
        return Commands.literal(literal)
                .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                .executes(context -> {
                                    ServerPlayer target = EntityArgument.getPlayer(context, "target");
                                    return report(
                                            context.getSource(),
                                            target,
                                            CultivationService.setProgress(
                                                    target, LongArgumentType.getLong(context, "amount")));
                                })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> stabilityCommand(String literal) {
        return Commands.literal(literal)
                .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0, 100))
                                .executes(context -> {
                                    ServerPlayer target = EntityArgument.getPlayer(context, "target");
                                    return report(
                                            context.getSource(),
                                            target,
                                            CultivationService.setStability(
                                                    target, IntegerArgumentType.getInteger(context, "amount")));
                                })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> powerCommand(String literal) {
        return Commands.literal(literal)
                .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                .executes(context -> {
                                    ServerPlayer target = EntityArgument.getPlayer(context, "target");
                                    return report(
                                            context.getSource(),
                                            target,
                                            CultivationService.setSpiritualPower(
                                                    target, LongArgumentType.getLong(context, "amount")));
                                })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> setRootCommand(String literal) {
        return Commands.literal(literal)
                .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("metal", IntegerArgumentType.integer(0, 10_000))
                                .then(Commands.argument("wood", IntegerArgumentType.integer(0, 10_000))
                                        .then(Commands.argument("water", IntegerArgumentType.integer(0, 10_000))
                                                .then(Commands.argument("fire", IntegerArgumentType.integer(0, 10_000))
                                                        .then(Commands.argument(
                                                                        "earth",
                                                                        IntegerArgumentType.integer(0, 10_000))
                                                                .executes(CultivationCommands::setRoot)))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> clearRootCommand(String literal) {
        return Commands.literal(literal)
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(context -> {
                            ServerPlayer target = EntityArgument.getPlayer(context, "target");
                            return report(
                                    context.getSource(),
                                    target,
                                    CultivationService.clearSpiritualRoot(target));
                        }));
    }

    private static int setRoot(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int metal = IntegerArgumentType.getInteger(context, "metal");
        int wood = IntegerArgumentType.getInteger(context, "wood");
        int water = IntegerArgumentType.getInteger(context, "water");
        int fire = IntegerArgumentType.getInteger(context, "fire");
        int earth = IntegerArgumentType.getInteger(context, "earth");
        long total = (long) metal + wood + water + fire + earth;
        if (total != SpiritualRoot.TOTAL_BASIS_POINTS) {
            context.getSource().sendFailure(Component.literal(
                    "Spiritual-root affinities must total 10000 basis points; got " + total));
            return 0;
        }

        Map<ResourceLocation, Integer> affinities = new LinkedHashMap<>();
        affinities.put(ModCultivationRegistries.METAL_ELEMENT_ID, metal);
        affinities.put(ModCultivationRegistries.WOOD_ELEMENT_ID, wood);
        affinities.put(ModCultivationRegistries.WATER_ELEMENT_ID, water);
        affinities.put(ModCultivationRegistries.FIRE_ELEMENT_ID, fire);
        affinities.put(ModCultivationRegistries.EARTH_ELEMENT_ID, earth);
        ServerPlayer target = EntityArgument.getPlayer(context, "target");
        return report(
                context.getSource(),
                target,
                CultivationService.setSpiritualRoot(target, new SpiritualRoot(affinities)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> techniqueCommand(
            String literal,
            TechniqueMutation mutation) {
        return Commands.literal(literal)
                .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("technique_id", ResourceLocationArgument.id())
                                .suggests(CultivationCommands::suggestTechniques)
                                .executes(context -> {
                                    ServerPlayer target = EntityArgument.getPlayer(context, "target");
                                    return report(
                                            context.getSource(),
                                            target,
                                            mutation.apply(
                                                    target,
                                                    ResourceLocationArgument.getId(
                                                            context, "technique_id")));
                                })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> masteryCommand(String literal) {
        return Commands.literal(literal)
                .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("technique_id", ResourceLocationArgument.id())
                                .suggests(CultivationCommands::suggestTechniques)
                                .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                        .executes(context -> {
                                            ServerPlayer target = EntityArgument.getPlayer(context, "target");
                                            return report(
                                                    context.getSource(),
                                                    target,
                                                    CultivationService.setTechniqueMastery(
                                                            target,
                                                            ResourceLocationArgument.getId(
                                                                    context, "technique_id"),
                                                            LongArgumentType.getLong(context, "amount")));
                                        }))));
    }

    private static CultivationService.Result learnTechnique(
            ServerPlayer player,
            ResourceLocation techniqueId) {
        return CultivationService.learnTechnique(player, techniqueId);
    }

    private static CultivationService.Result forgetTechnique(
            ServerPlayer player,
            ResourceLocation techniqueId) {
        return CultivationService.forgetTechnique(player, techniqueId);
    }

    private static int awaken(CommandSourceStack source, ServerPlayer target) {
        SpiritualRootAwakeningService.Outcome outcome =
                SpiritualRootAwakeningService.awaken(target);
        return reportInitiation(
                source,
                target,
                outcome.success(),
                CultivationInitiationMessages.awakening(target, outcome));
    }

    private static int initiate(CommandSourceStack source, ServerPlayer target) {
        TechniqueInheritanceService.Outcome outcome =
                TechniqueInheritanceService.inheritBasicBreathing(target);
        return reportInitiation(
                source,
                target,
                outcome.success(),
                CultivationInitiationMessages.inheritance(target, outcome));
    }

    private static int info(CommandSourceStack source, ServerPlayer target) {
        CultivationProfile profile = CultivationService.getProfile(target);
        Optional<Registry<RealmDefinition>> realms =
                source.registryAccess().registry(ModCultivationRegistries.REALMS);
        Optional<Registry<SpiritualElementDefinition>> elements =
                source.registryAccess().registry(ModCultivationRegistries.SPIRITUAL_ELEMENTS);
        Optional<Registry<TechniqueDefinition>> techniques =
                source.registryAccess().registry(ModCultivationRegistries.TECHNIQUES);

        RealmDefinition realm = realms.map(registry -> registry.get(profile.realmId())).orElse(null);
        String root = profile.spiritualRoot()
                .map(value -> value.affinitiesBasisPoints().entrySet().stream()
                        .map(entry -> availability(
                                entry.getKey(),
                                elements.map(registry -> registry.containsKey(entry.getKey())).orElse(false))
                                + "=" + entry.getValue())
                        .collect(Collectors.joining(", ")))
                .orElse("unawakened");
        String learned = profile.learnedTechniques().isEmpty()
                ? "none"
                : profile.learnedTechniques().entrySet().stream()
                        .map(entry -> availability(
                                entry.getKey(),
                                techniques.map(registry -> registry.containsKey(entry.getKey())).orElse(false))
                                + "=" + entry.getValue().masteryPoints())
                        .collect(Collectors.joining(", "));

        String output = "Cultivation profile for " + target.getGameProfile().getName()
                + "\nschema version: " + profile.schemaVersion()
                + "\nrealm: " + availability(profile.realmId(), realm != null)
                + "\nstage: " + availability(
                        profile.stageId(), realm != null && realm.hasStage(profile.stageId()))
                + "\ncultivation progress: " + profile.cultivationProgress()
                + "\nstability: " + profile.stability()
                + "\ncurrent spiritual power: " + profile.currentSpiritualPower()
                + "\nspiritual root: " + root
                + "\nlearned techniques: " + learned;
        source.sendSuccess(() -> Component.literal(output), false);
        return 1;
    }

    private static String availability(ResourceLocation id, boolean available) {
        return id + (available ? "" : " (unavailable)");
    }

    private static int report(
            CommandSourceStack source,
            ServerPlayer target,
            CultivationService.Result result) {
        if (!result.success()) {
            source.sendFailure(Component.literal(
                    "Cultivation update failed for " + target.getGameProfile().getName()
                            + ": " + result.message()));
            return 0;
        }
        source.sendSuccess(
                () -> Component.literal(
                        "Cultivation update for " + target.getGameProfile().getName()
                                + ": " + result.message()),
                true);
        return 1;
    }

    private static int reportInitiation(
            CommandSourceStack source,
            ServerPlayer target,
            boolean success,
            java.util.List<Component> messages) {
        for (Component message : messages) {
            Component output = Component.translatable(
                    "commands.myvillage.cultivation.initiation_result",
                    target.getDisplayName(),
                    message);
            if (success) {
                source.sendSuccess(() -> output, true);
            } else {
                source.sendFailure(output);
            }
        }
        return success ? 1 : 0;
    }

    private static CompletableFuture<Suggestions> suggestRealms(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder) {
        return suggestRegistry(context, builder, ModCultivationRegistries.REALMS);
    }

    private static CompletableFuture<Suggestions> suggestTechniques(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder) {
        return suggestRegistry(context, builder, ModCultivationRegistries.TECHNIQUES);
    }

    private static CompletableFuture<Suggestions> suggestStages(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder) {
        ResourceLocation realmId = ResourceLocationArgument.getId(context, "realm_id");
        return context.getSource().registryAccess().registry(ModCultivationRegistries.REALMS)
                .map(registry -> registry.getOptional(realmId)
                        .map(realm -> SharedSuggestionProvider.suggestResource(
                                realm.stages().stream().map(stage -> stage.id()), builder))
                        .orElseGet(builder::buildFuture))
                .orElseGet(builder::buildFuture);
    }

    private static <T> CompletableFuture<Suggestions> suggestRegistry(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder,
            ResourceKey<Registry<T>> key) {
        return context.getSource().registryAccess().registry(key)
                .map(registry -> SharedSuggestionProvider.suggestResource(registry.keySet(), builder))
                .orElseGet(builder::buildFuture);
    }

    @FunctionalInterface
    private interface TechniqueMutation {
        CultivationService.Result apply(ServerPlayer player, ResourceLocation techniqueId);
    }
}
