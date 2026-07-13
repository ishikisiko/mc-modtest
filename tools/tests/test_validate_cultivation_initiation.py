import json
import tempfile
import unittest
from pathlib import Path

from tools.validate_cultivation_initiation import CultivationInitiationValidator


VERSION = "0.23.0"


class CultivationInitiationValidationTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.build_complete_fixture()

    def write(self, relative: str, content: str) -> None:
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content.strip() + "\n", encoding="utf-8")

    def write_json(self, relative: str, value: object) -> None:
        self.write(relative, json.dumps(value, indent=2))

    def validate(self):
        return CultivationInitiationValidator(self.root).validate()

    def assert_error_contains(self, result, expected: str) -> None:
        self.assertTrue(
            any(expected in error for error in result.errors),
            f"expected error containing {expected!r}, got {result.errors!r}",
        )

    def build_complete_fixture(self) -> None:
        self.write(
            "openspec/specs/cultivation-initiation-ritual/spec.md",
            """
            # cultivation-initiation-ritual Specification

            ## Requirements

            ### Requirement: Server-authoritative initiation
            The system SHALL provide deterministic awakening and inheritance.
            """,
        )
        self.write(
            "docs/ai-kb/29_cultivation_initiation_ritual.md",
            """
            # Cultivation Initiation Ritual

            See `cultivation-initiation-ritual`. The two independent facilities are
            `myvillage:spirit_testing_stele` and
            `myvillage:technique_inheritance_stele`.
            """,
        )
        self.write(
            "docs/ai-kb/INDEX.md",
            "29. [Cultivation Initiation](29_cultivation_initiation_ritual.md)",
        )

        for index, element in enumerate(("metal", "wood", "water", "fire", "earth")):
            self.write_json(
                f"src/main/resources/data/myvillage/myvillage/spiritual_element/{element}.json",
                {
                    "translation_key": f"cultivation.element.myvillage.{element}",
                    "sort_order": index,
                    "awakening_weight": 1,
                },
            )
        self.write_json(
            "src/main/resources/data/myvillage/myvillage/technique/basic_breathing.json",
            {
                "translation_key": "cultivation.technique.myvillage.basic_breathing",
                "category": "core",
                "grade": 0,
                "elements": [],
                "requirements": {
                    "minimum_realm": "myvillage:mortal",
                    "minimum_stage": "myvillage:mortal_qi_sensed",
                },
            },
        )

        self.write(
            "src/main/java/com/example/myvillage/cultivation/data/SpiritualElementDefinition.java",
            """
            package com.example.myvillage.cultivation.data;
            import com.mojang.serialization.Codec;
            public record SpiritualElementDefinition(
                    String translationKey, int sortOrder, int awakeningWeight) {
                public static final int DEFAULT_AWAKENING_WEIGHT = 1;
                public static final int MAX_AWAKENING_WEIGHT = 1_000_000;
                public static final Codec<Integer> CODEC = Codec.intRange(0, MAX_AWAKENING_WEIGHT)
                        .optionalFieldOf("awakening_weight", DEFAULT_AWAKENING_WEIGHT).codec();
            }
            """,
        )
        self.write(
            "src/main/java/com/example/myvillage/block/ModBlocks.java",
            """
            package com.example.myvillage.block;
            import java.util.List;
            public final class ModBlocks {
                public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("myvillage");
                public static final DeferredBlock<SpiritTestingSteleBlock> SPIRIT_TESTING_STELE =
                        BLOCKS.registerBlock("spirit_testing_stele", SpiritTestingSteleBlock::new, properties());
                public static final DeferredBlock<TechniqueInheritanceSteleBlock> TECHNIQUE_INHERITANCE_STELE =
                        BLOCKS.registerBlock("technique_inheritance_stele", TechniqueInheritanceSteleBlock::new, properties());
                private static final List<String> BLOCK_IDS = List.of(
                        "spirit_testing_stele", "technique_inheritance_stele");
                public static void verifyRegistered() {
                    for (String id : BLOCK_IDS) { verify(id); }
                }
            }
            """,
        )
        self.write(
            "src/main/java/com/example/myvillage/item/ModItems.java",
            """
            package com.example.myvillage.item;
            import com.example.myvillage.block.ModBlocks;
            public final class ModItems {
                public static final DeferredItem<BlockItem> SPIRIT_TESTING_STELE_ITEM =
                        ITEMS.registerItem("spirit_testing_stele",
                                props -> new BlockItem(ModBlocks.SPIRIT_TESTING_STELE.get(), props));
                public static final DeferredItem<BlockItem> TECHNIQUE_INHERITANCE_STELE_ITEM =
                        ITEMS.registerItem("technique_inheritance_stele",
                                props -> new BlockItem(ModBlocks.TECHNIQUE_INHERITANCE_STELE.get(), props));
                static void fill(Output output) {
                    output.accept(SPIRIT_TESTING_STELE_ITEM.get());
                    output.accept(TECHNIQUE_INHERITANCE_STELE_ITEM.get());
                }
            }
            """,
        )
        self.write(
            "src/main/java/com/example/myvillage/block/SpiritTestingSteleBlock.java",
            """
            package com.example.myvillage.block;
            import com.example.myvillage.cultivation.root.SpiritualRootAwakeningService;
            public final class SpiritTestingSteleBlock extends Block {
                protected InteractionResult useWithoutItem(BlockState state,
                        Level level, BlockPos pos, Player player,
                        BlockHitResult hit) {
                    if (level.isClientSide()) return InteractionResult.SUCCESS;
                    SpiritualRootAwakeningService.awaken((ServerPlayer) player);
                    return InteractionResult.CONSUME;
                }
            }
            """,
        )
        self.write(
            "src/main/java/com/example/myvillage/block/TechniqueInheritanceSteleBlock.java",
            """
            package com.example.myvillage.block;
            import com.example.myvillage.cultivation.technique.TechniqueInheritanceService;
            public final class TechniqueInheritanceSteleBlock extends Block {
                protected InteractionResult useWithoutItem(BlockState state,
                        Level level, BlockPos pos, Player player,
                        BlockHitResult hit) {
                    if (level.isClientSide()) return InteractionResult.SUCCESS;
                    TechniqueInheritanceService.initiate((ServerPlayer) player);
                    return InteractionResult.CONSUME;
                }
            }
            """,
        )
        self.write(
            "src/main/java/com/example/myvillage/cultivation/root/SpiritualRootGenerator.java",
            """
            package com.example.myvillage.cultivation.root;
            import java.util.Comparator;
            import java.util.List;
            import java.util.UUID;
            public final class SpiritualRootGenerator {
                private static final long GENERATOR_SALT = 0x6A09E667F3BCC909L;
                public static SpiritualRoot generate(long overworldSeed, UUID uuid,
                        List<ElementCandidate> candidates) {
                    var stable = candidates.stream().sorted(Comparator.comparing(
                            candidate -> candidate.id().toString())).toList();
                    long total = 0;
                    for (var candidate : stable) total = Math.addExact(total, candidate.weight());
                    return mix(overworldSeed, uuid, GENERATOR_SALT, total);
                }
            }
            """,
        )
        self.write(
            "src/main/java/com/example/myvillage/cultivation/root/SpiritualRootAwakeningService.java",
            """
            package com.example.myvillage.cultivation.root;
            import com.example.myvillage.cultivation.CultivationService;
            public final class SpiritualRootAwakeningService {
                public static Result awaken(ServerPlayer player) {
                    var profile = CultivationService.getProfile(player);
                    long seed = player.getServer().overworld().getSeed();
                    var root = SpiritualRootGenerator.generate(seed, player.getUUID(), candidates(player));
                    return CultivationService.replaceProfile(player, profile.withSpiritualRoot(root));
                }
                static final String SUCCESS = "message.myvillage.cultivation.awakening.success";
            }
            """,
        )
        self.write(
            "src/main/java/com/example/myvillage/cultivation/technique/TechniqueRequirementEvaluator.java",
            """
            package com.example.myvillage.cultivation.technique;
            public final class TechniqueRequirementEvaluator {
                public static boolean evaluate(Profile profile, Requirements requirements) {
                    return requirements.minimumRealm() != null
                            && requirements.minimumStage() != null
                            && requirements.minimumElementAffinity() != null;
                }
            }
            """,
        )
        self.write(
            "src/main/java/com/example/myvillage/cultivation/technique/TechniqueInheritanceService.java",
            """
            package com.example.myvillage.cultivation.technique;
            import com.example.myvillage.cultivation.CultivationService;
            public final class TechniqueInheritanceService {
                public static Result initiate(ServerPlayer player) {
                    var profile = CultivationService.getProfile(player);
                    if (!TechniqueRequirementEvaluator.evaluate(profile, definition(player).requirements())) {
                        return Result.REQUIREMENTS_NOT_MET;
                    }
                    return CultivationService.replaceProfile(player, profile.learnTechnique(BASIC_BREATHING));
                }
                static final String SUCCESS = "message.myvillage.cultivation.inheritance.success";
            }
            """,
        )
        self.write(
            "src/main/java/com/example/myvillage/cultivation/CultivationCommands.java",
            """
            package com.example.myvillage.cultivation;
            import com.example.myvillage.cultivation.root.SpiritualRootAwakeningService;
            import com.example.myvillage.cultivation.technique.TechniqueInheritanceService;
            public final class CultivationCommands {
                public static LiteralArgumentBuilder<CommandSourceStack> command() {
                    return commandTree("cultivation");
                }
                public static LiteralArgumentBuilder<CommandSourceStack> pinyinCommand() {
                    return commandTree("xiulian");
                }
                private static LiteralArgumentBuilder<CommandSourceStack> commandTree(String root) {
                    return Commands.literal(root)
                            .then(awakeningCommand("awaken"))
                            .then(awakeningCommand("juexing"))
                            .then(initiationCommand("initiate"))
                            .then(initiationCommand("rumen"));
                }
                private static LiteralArgumentBuilder<CommandSourceStack> awakeningCommand(String literal) {
                    return Commands.literal(literal).executes(context -> awaken(context.getSource().getPlayerOrException()))
                            .then(Commands.argument("target", EntityArgument.player())
                                    .executes(context -> awaken(EntityArgument.getPlayer(context, "target"))));
                }
                private static int awaken(ServerPlayer target) {
                    return SpiritualRootAwakeningService.awaken(target).success() ? 1 : 0;
                }
                private static LiteralArgumentBuilder<CommandSourceStack> initiationCommand(String literal) {
                    return Commands.literal(literal).executes(context -> initiate(context.getSource().getPlayerOrException()))
                            .then(Commands.argument("target", EntityArgument.player())
                                    .executes(context -> initiate(EntityArgument.getPlayer(context, "target"))));
                }
                private static int initiate(ServerPlayer target) {
                    return TechniqueInheritanceService.initiate(target).success() ? 1 : 0;
                }
            }
            """,
        )
        self.write(
            "src/main/java/com/example/myvillage/cultivation/CultivationProfile.java",
            """
            package com.example.myvillage.cultivation;
            public record CultivationProfile(
                    int schemaVersion,
                    ResourceLocation realmId,
                    ResourceLocation stageId,
                    long cultivationProgress,
                    int stability,
                    long currentSpiritualPower,
                    Optional<SpiritualRoot> spiritualRoot,
                    Map<ResourceLocation, TechniqueProgress> learnedTechniques) {
                public static final int CURRENT_SCHEMA_VERSION = 1;
            }
            """,
        )
        self.write(
            "src/main/java/com/example/myvillage/cultivation/network/CultivationPayloads.java",
            """
            package com.example.myvillage.cultivation.network;
            public final class CultivationPayloads {
                public static void register(PayloadRegistrar registrar) {
                    registrar.playToClient(CultivationSnapshotPayload.TYPE,
                            CultivationSnapshotPayload.STREAM_CODEC, CultivationPayloads::handle);
                }
            }
            """,
        )
        self.write(
            "src/main/java/com/example/myvillage/cultivation/network/CultivationSnapshotPayload.java",
            """
            package com.example.myvillage.cultivation.network;
            public record CultivationSnapshotPayload(Profile profile) implements CustomPacketPayload {}
            """,
        )
        self.write(
            "src/main/java/com/example/myvillage/client/cultivation/ClientCultivationState.java",
            """
            package com.example.myvillage.client.cultivation;
            public final class ClientCultivationState {
                public static Optional<Profile> latest() { return Optional.empty(); }
                static void replace(Profile profile) {}
                static void clear() {}
            }
            """,
        )
        self.write(
            "src/main/java/com/example/myvillage/client/cultivation/CultivationProfileScreen.java",
            """
            package com.example.myvillage.client.cultivation;
            public final class CultivationProfileScreen extends Screen {
                public void render(GuiGraphics graphics, int x, int y, float tick) {
                    ClientCultivationState.latest();
                }
            }
            """,
        )

        languages = {
            "block.myvillage.spirit_testing_stele": "Spirit Testing Stele",
            "block.myvillage.technique_inheritance_stele": "Technique Inheritance Stele",
            "message.myvillage.cultivation.awakening.success": "Awakened",
            "message.myvillage.cultivation.inheritance.success": "Inherited",
        }
        for locale in ("en_us", "zh_cn"):
            self.write_json(f"src/main/resources/assets/myvillage/lang/{locale}.json", languages)
        for identifier in ("spirit_testing_stele", "technique_inheritance_stele"):
            self.write_json(
                f"src/main/resources/assets/myvillage/blockstates/{identifier}.json",
                {"variants": {"": {"model": f"myvillage:block/{identifier}"}}},
            )
            self.write_json(
                f"src/main/resources/assets/myvillage/models/block/{identifier}.json",
                {
                    "parent": "minecraft:block/cube_all",
                    "textures": {"all": "minecraft:block/deepslate_tiles"},
                },
            )
            self.write_json(
                f"src/main/resources/assets/myvillage/models/item/{identifier}.json",
                {"parent": f"myvillage:block/{identifier}"},
            )
            self.write_json(
                f"src/main/resources/data/myvillage/loot_table/blocks/{identifier}.json",
                {
                    "type": "minecraft:block",
                    "pools": [
                        {
                            "rolls": 1,
                            "entries": [
                                {"type": "minecraft:item", "name": f"myvillage:{identifier}"}
                            ],
                        }
                    ],
                },
            )
        self.write_json(
            "src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json",
            {
                "replace": False,
                "values": [
                    "myvillage:spirit_testing_stele",
                    "myvillage:technique_inheritance_stele",
                ],
            },
        )

        self.write("gradle.properties", f"mod_version={VERSION}")
        self.write(
            "src/main/resources/META-INF/neoforge.mods.toml",
            f'''[[mods]]
            modId = "myvillage"
            version = "{VERSION}"''',
        )
        self.write(
            "README.md",
            f"""
            # MyVillage

            Use `myvillage:spirit_testing_stele` to `awaken` into
            `mortal_qi_sensed`, then use `myvillage:technique_inheritance_stele`
            to `initiate`. H is a read-only profile view. Run
            `python3 tools/validate_cultivation_initiation.py`.
            Expected jar: `build/libs/myvillage-{VERSION}.jar`.
            """,
        )
        self.write(
            "CHANGELOG.md",
            f"""
            # Changelog

            ## {VERSION}

            Added `myvillage:spirit_testing_stele` and
            `myvillage:technique_inheritance_stele`.
            """,
        )

    def test_complete_fixture_passes(self) -> None:
        result = self.validate()

        self.assertEqual((), result.errors)
        self.assertGreaterEqual(result.checked_files, 30)

    def test_missing_block_resource_is_rejected(self) -> None:
        (self.root / "src/main/resources/assets/myvillage/models/item/spirit_testing_stele.json").unlink()

        result = self.validate()

        self.assert_error_contains(result, "missing stele item model")

    def test_invalid_shipped_awakening_weight_is_rejected(self) -> None:
        path = self.root / "src/main/resources/data/myvillage/myvillage/spiritual_element/fire.json"
        value = json.loads(path.read_text(encoding="utf-8"))
        value["awakening_weight"] = -1
        path.write_text(json.dumps(value), encoding="utf-8")

        result = self.validate()

        self.assert_error_contains(result, "awakening_weight must be an integer in 0..1000000")

    def test_generator_time_dependency_is_rejected(self) -> None:
        path = self.root / "src/main/java/com/example/myvillage/cultivation/root/SpiritualRootGenerator.java"
        path.write_text(path.read_text(encoding="utf-8") + "long bad = System.nanoTime();\n", encoding="utf-8")

        result = self.validate()

        self.assert_error_contains(result, "must not depend on wall-clock time")

    def test_generator_hardcoded_element_is_rejected(self) -> None:
        path = self.root / "src/main/java/com/example/myvillage/cultivation/root/SpiritualRootGenerator.java"
        path.write_text(path.read_text(encoding="utf-8") + 'String bad = "myvillage:fire";\n', encoding="utf-8")

        result = self.validate()

        self.assert_error_contains(result, "hard-codes shipped element ids: fire")

    def test_direct_service_attachment_write_is_rejected(self) -> None:
        path = self.root / "src/main/java/com/example/myvillage/cultivation/root/SpiritualRootAwakeningService.java"
        source = path.read_text(encoding="utf-8").replace(
            "return CultivationService.replaceProfile(player, profile.withSpiritualRoot(root));",
            "player.setData(PROFILE, profile.withSpiritualRoot(root)); return Result.SUCCESS;",
        )
        path.write_text(source, encoding="utf-8")

        result = self.validate()

        self.assert_error_contains(result, "must not write player attachment data directly")

    def test_block_entity_stele_is_rejected(self) -> None:
        path = self.root / "src/main/java/com/example/myvillage/block/SpiritTestingSteleBlock.java"
        path.write_text(
            path.read_text(encoding="utf-8").replace("extends Block", "extends BaseEntityBlock"),
            encoding="utf-8",
        )

        result = self.validate()

        self.assert_error_contains(result, "must not use a BlockEntity or menu")

    def test_legacy_item_interaction_hook_is_rejected(self) -> None:
        path = self.root / "src/main/java/com/example/myvillage/block/SpiritTestingSteleBlock.java"
        path.write_text(
            path.read_text(encoding="utf-8").replace("useWithoutItem", "useItemOn"),
            encoding="utf-8",
        )

        result = self.validate()

        self.assert_error_contains(result, "must not use legacy/useItemOn interaction hooks")

    def test_new_cultivation_c2s_payload_is_rejected(self) -> None:
        self.write(
            "src/main/java/com/example/myvillage/cultivation/network/AwakenPayload.java",
            """
            package com.example.myvillage.cultivation.network;
            public record AwakenPayload() implements CustomPacketPayload {}
            """,
        )

        result = self.validate()

        self.assert_error_contains(result, "only the existing cultivation snapshot payload is allowed")

    def test_profile_schema_or_shape_change_is_rejected(self) -> None:
        path = self.root / "src/main/java/com/example/myvillage/cultivation/CultivationProfile.java"
        source = path.read_text(encoding="utf-8").replace(
            "Map<ResourceLocation, TechniqueProgress> learnedTechniques)",
            "Map<ResourceLocation, TechniqueProgress> learnedTechniques, boolean awakened)",
        ).replace("CURRENT_SCHEMA_VERSION = 1", "CURRENT_SCHEMA_VERSION = 2")
        path.write_text(source, encoding="utf-8")

        result = self.validate()

        self.assert_error_contains(result, "cultivation profile schema must remain 1")
        self.assert_error_contains(result, "v1 profile components changed")

    def test_basic_breathing_requirement_drift_is_rejected(self) -> None:
        path = self.root / "src/main/resources/data/myvillage/myvillage/technique/basic_breathing.json"
        value = json.loads(path.read_text(encoding="utf-8"))
        value["requirements"]["minimum_stage"] = "myvillage:qi_refining_1"
        path.write_text(json.dumps(value), encoding="utf-8")

        result = self.validate()

        self.assert_error_contains(result, "minimum_stage must be myvillage:mortal_qi_sensed")

    def test_readme_jar_version_drift_is_rejected(self) -> None:
        path = self.root / "README.md"
        path.write_text(path.read_text(encoding="utf-8").replace(VERSION, "0.22.2"), encoding="utf-8")

        result = self.validate()

        self.assert_error_contains(result, "jar-name example version 0.22.2 does not match 0.23.0")


if __name__ == "__main__":
    unittest.main()
