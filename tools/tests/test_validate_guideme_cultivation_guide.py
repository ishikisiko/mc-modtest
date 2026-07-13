import json
import tempfile
import textwrap
import unittest
import zipfile
from pathlib import Path

from tools.validate_guideme_cultivation_guide import (
    GuideMECultivationGuideValidator,
    PACKAGED_GUIDE_ROOT,
)


class GuideMECultivationGuideValidationTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.build_fixture()

    def write(self, relative: str, content: str) -> None:
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(textwrap.dedent(content).strip() + "\n", encoding="utf-8")

    def write_json(self, relative: str, value: object) -> None:
        self.write(relative, json.dumps(value, ensure_ascii=False, indent=2))

    def build_fixture(self) -> None:
        self.write(
            "gradle.properties",
            """
            mod_id=myvillage
            mod_version=0.25.1
            guideme_version=21.1.17
            """,
        )
        self.write(
            "build.gradle",
            """
            repositories {
                mavenCentral()
            }
            dependencies {
                compileOnly "org.appliedenergistics:guideme:${guideme_version}:api"
                runtimeOnly "org.appliedenergistics:guideme:${guideme_version}"
            }
            neoForge {
                runs {
                    guide {
                        client()
                        systemProperty 'guideme.myvillage.cultivation.sources', file('guidebook').absolutePath
                        systemProperty 'guideme.myvillage.cultivation.sourcesNamespace', 'myvillage'
                        systemProperty 'guideme.showOnStartup', 'myvillage:cultivation'
                        systemProperty 'guideme.validateAtStartup', 'myvillage:cultivation'
                    }
                }
            }
            tasks.named('processResources') {
                from('guidebook') {
                    into 'assets/myvillage/guides/myvillage/cultivation'
                }
            }
            """,
        )
        self.write(
            "src/main/resources/META-INF/neoforge.mods.toml",
            """
            modLoader = "javafml"
            loaderVersion = "[4,)"
            [[mods]]
            modId = "myvillage"
            version = "0.25.1"

            [[dependencies.myvillage]]
            modId = "guideme"
            type = "required"
            versionRange = "[21.1.17,22)"
            ordering = "AFTER"
            side = "BOTH"
            """,
        )
        self.write_json(
            "src/main/resources/assets/myvillage/guideme_guides/cultivation.json",
            {
                "default_language": "zh_cn",
                "item_settings": {
                    "display_name": {
                        "type": "translatable",
                        "translate": "item.myvillage.cultivation_handbook",
                    },
                    "tooltip_lines": [
                        {
                            "type": "translatable",
                            "translate": "item.myvillage.cultivation_handbook.tooltip",
                        }
                    ],
                    "model": "myvillage:item/cultivation_handbook",
                },
            },
        )
        self.write_json(
            "src/main/resources/assets/myvillage/models/item/cultivation_handbook.json",
            {"parent": "guideme:item/guide_base"},
        )
        keys = {
            "key.myvillage.open_cultivation_profile": "Profile",
            "key.myvillage.start_normal_meditation": "Normal",
            "key.myvillage.start_spirit_meditation": "Spirit",
            "key.myvillage.stop_meditation": "Stop",
            "key.myvillage.start_advancement": "Advance",
        }
        for locale, name, tooltip in (
            ("en_us", "Cultivation Primer", "Open the cultivation guide"),
            ("zh_cn", "修行入门录", "打开修行指南"),
        ):
            self.write_json(
                f"src/main/resources/assets/myvillage/lang/{locale}.json",
                {
                    **keys,
                    "item.myvillage.cultivation_handbook": name,
                    "item.myvillage.cultivation_handbook.tooltip": tooltip,
                },
            )

        self.write(
            "src/main/java/com/example/myvillage/block/ModBlocks.java",
            """
            final class ModBlocks {
                static final Object SPIRIT_TESTING_STELE =
                    BLOCKS.registerBlock("spirit_testing_stele", Block::new, properties());
                static final Object TECHNIQUE_INHERITANCE_STELE =
                    BLOCKS.registerBlock("technique_inheritance_stele", Block::new, properties());
                static final Object SPIRIT_STONE_ORE =
                    BLOCKS.registerBlock("spirit_stone_ore", Block::new, properties());
                static final Object DEEPSLATE_SPIRIT_STONE_ORE =
                    BLOCKS.registerBlock("deepslate_spirit_stone_ore", Block::new, properties());
            }
            """,
        )
        self.write(
            "src/main/java/com/example/myvillage/item/ModItems.java",
            """
            final class ModItems {
                static final Object SPIRIT_TESTING_STELE_ITEM =
                    ITEMS.registerItem("spirit_testing_stele", props -> new BlockItem(block, props));
                static final Object TECHNIQUE_INHERITANCE_STELE_ITEM =
                    ITEMS.registerItem("technique_inheritance_stele", props -> new BlockItem(block, props));
                static final Object LOW_GRADE_SPIRIT_STONE =
                    ITEMS.registerSimpleItem("low_grade_spirit_stone");
                static final Object SPIRIT_STONE_ORE_ITEM =
                    ITEMS.registerItem("spirit_stone_ore", props -> new BlockItem(block, props));
                static final Object DEEPSLATE_SPIRIT_STONE_ORE_ITEM =
                    ITEMS.registerItem("deepslate_spirit_stone_ore", props -> new BlockItem(block, props));
                static final Object CULTIVATION_HANDBOOK =
                    ITEMS.registerItem("cultivation_handbook",
                        props -> new CultivationHandbookItem(props.stacksTo(1)));
                void display(Object output) {
                    output.accept(TECHNIQUE_INHERITANCE_STELE_ITEM.get());
                    output.accept(CULTIVATION_HANDBOOK.get());
                }
            }
            """,
        )
        self.write(
            "src/main/java/com/example/myvillage/item/CultivationHandbookItem.java",
            """
            final class CultivationHandbookItem extends Item {
                static final ResourceLocation GUIDE = ResourceLocation.fromNamespaceAndPath(
                    MyVillageMod.MOD_ID, "cultivation");
                InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
                    ItemStack stack = player.getItemInHand(hand);
                    if (level.isClientSide()) {
                        GuidesCommon.openGuide(player, GUIDE);
                    }
                    return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
                }
                void appendHoverText(ItemStack stack, TooltipContext context,
                        List<Component> tooltip, TooltipFlag flag) {
                    tooltip.add(Component.translatable(
                        "item.myvillage.cultivation_handbook.tooltip"));
                }
            }
            """,
        )
        self.write(
            "src/main/java/com/example/myvillage/client/cultivation/ClientCultivationKeyMappings.java",
            "\n".join(f'key("{key}");' for key in keys),
        )

        index_zh = """
            ---
            navigation:
              title: 修行入门录
              icon: myvillage:cultivation_handbook
            item_ids:
              - myvillage:cultivation_handbook
            ---
            # 修行入门录
            先测灵唤醒灵根，再传承吐纳诀。之后依次积累进度、巩固稳定度并确定性冲关。
            当前发布上限是炼气四层；炼气五层与筑基尚未实现，属于后续内容。
            [入门仪式](getting_started/initiation.md)
            [打坐与冲关](getting_started/cultivation_loop.md)
            <SubPages icons={true} />
        """
        index_en = """
            ---
            navigation:
              title: Cultivation Primer
              icon: myvillage:cultivation_handbook
            item_ids:
              - myvillage:cultivation_handbook
            ---
            # Cultivation Primer
            Awaken a spiritual root, inherit Basic Breathing, fill progress, consolidate stability,
            and use deterministic advancement. Qi Refining IV is the release ceiling.
            Qi Refining V and Foundation Establishment are deferred and not yet playable.
            [Initiation](getting_started/initiation.md)
            [Cultivation Loop](getting_started/cultivation_loop.md)
            <SubPages icons={true} />
        """
        initiation_zh = """
            ---
            navigation:
              title: 入门仪式
              parent: index.md
              position: 10
            item_ids:
              - myvillage:spirit_testing_stele
              - myvillage:technique_inheritance_stele
            ---
            # 入门仪式
            分别右键测灵碑和传承碑。重复测灵不会重抽灵根，重复传承不会重置吐纳诀熟练度。
            <Row><BlockImage id="myvillage:spirit_testing_stele" />
            <ItemLink id="myvillage:technique_inheritance_stele" /></Row>
        """
        initiation_en = """
            ---
            navigation:
              title: Initiation
              parent: index.md
              position: 10
            item_ids:
              - myvillage:spirit_testing_stele
              - myvillage:technique_inheritance_stele
            ---
            # Initiation
            Right-click the testing stele and inheritance stele separately. Repeating testing does not
            reroll the root, and repeating inheritance does not reset Basic Breathing mastery.
            <Row><BlockImage id="myvillage:spirit_testing_stele" />
            <ItemLink id="myvillage:technique_inheritance_stele" /></Row>
        """
        key_tags = " ".join(f'<KeyBind id="{key}" />' for key in keys)
        loop_zh = f"""
            ---
            navigation:
              title: 打坐与冲关
              parent: index.md
              position: 20
            item_ids:
              - myvillage:low_grade_spirit_stone
              - myvillage:spirit_stone_ore
              - myvillage:deepslate_spirit_stone_ore
            ---
            # 打坐与冲关
            控制：{key_tags}
            满足资格后准备 40 tick。普通打坐每 10 tick 按亲和度增长；灵石模式每 10 tick 增长 50。
            阶段灵石成本为 1 / 1 / 2 / 3。进度达到上限后才增加稳定度。
            上限依次为 1000/500、1100/550、1200/600、1300/650；冲关时长 100/100/120/200。
            成功后稳定度整数除以 2；最后一次中断损失 5。凡人寿元 80，炼气寿元 120。
            移动、跳跃、受伤或互动会中断。寿元耗尽会阻止开始。炼气四层是当前上限。
            <ItemLink id="myvillage:low_grade_spirit_stone" />
            <Row><BlockImage id="myvillage:spirit_stone_ore" />
            <BlockImage id="myvillage:deepslate_spirit_stone_ore" /></Row>
        """
        loop_en = f"""
            ---
            navigation:
              title: Cultivation Loop
              parent: index.md
              position: 20
            item_ids:
              - myvillage:low_grade_spirit_stone
              - myvillage:spirit_stone_ore
              - myvillage:deepslate_spirit_stone_ore
            ---
            # Cultivation Loop
            Controls: {key_tags}
            After eligibility, preparation takes 40 ticks. Normal meditation gains affinity every 10 ticks;
            spirit-stone mode gains 50 every 10 ticks. Stage costs are 1 / 1 / 2 / 3.
            Progress must be full before stability grows. Caps are 1000/500, 1100/550, 1200/600,
            and 1300/650; durations are 100/100/120/200. Success retains integer-floor half stability.
            The last interruption costs 5. Mortal lifespan is 80 and Qi Refining lifespan is 120.
            Movement, jumping, damage, or interaction interrupts the session. Qi Refining IV is the ceiling.
            <ItemLink id="myvillage:low_grade_spirit_stone" />
            <Row><BlockImage id="myvillage:spirit_stone_ore" />
            <BlockImage id="myvillage:deepslate_spirit_stone_ore" /></Row>
        """
        for relative, content in (
            ("guidebook/index.md", index_zh),
            ("guidebook/getting_started/initiation.md", initiation_zh),
            ("guidebook/getting_started/cultivation_loop.md", loop_zh),
            ("guidebook/_en_us/index.md", index_en),
            ("guidebook/_en_us/getting_started/initiation.md", initiation_en),
            ("guidebook/_en_us/getting_started/cultivation_loop.md", loop_en),
        ):
            self.write(relative, content)

        self.write(
            "README.md",
            """
            GuideME is required on the client and required on the server.
            Use `/give @s myvillage:cultivation_handbook` or
            `/guidemec myvillage:cultivation open`. Authors run `./gradlew runGuide`.
            Run `python3 tools/validate_guideme_cultivation_guide.py`.
            Manual rendering and interaction remain `not_verified`.
            Artifact: `build/libs/myvillage-0.25.1.jar`.
            """,
        )
        self.write(
            "CHANGELOG.md",
            """
            # Changelog
            ## 0.25.1
            Added GuideME integration and `myvillage:cultivation_handbook`.
            """,
        )
        self.write(
            "docs/ai-kb/31_guideme_cultivation_guide.md",
            """
            # GuideME Cultivation Guide
            Root `guidebook/` is the source for `myvillage:cultivation`.
            Preview with `/guidemec myvillage:cultivation open`.
            See change `add-guideme-cultivation-guide-slice`.
            """,
        )
        self.write("docs/ai-kb/INDEX.md", "[GuideME](31_guideme_cultivation_guide.md)")
        self.write(
            "AGENTS.md",
            "Run `tools/validate_guideme_cultivation_guide.py`; see `31_guideme_cultivation_guide.md`.",
        )

    def validate(self, *, jar: Path | None = None):
        return GuideMECultivationGuideValidator(self.root).validate(jar=jar)

    def assert_error_contains(self, result, expected: str) -> None:
        self.assertTrue(
            any(expected in error for error in result.errors),
            f"expected error containing {expected!r}, got {result.errors!r}",
        )

    def build_jar(
            self,
            *,
            omit: str | None = None,
            embed_guideme: bool = False,
            overrides: dict[str, bytes] | None = None) -> Path:
        jar = self.root / "fixture.jar"
        overrides = overrides or {}
        source_entries = {
            "META-INF/neoforge.mods.toml":
                "src/main/resources/META-INF/neoforge.mods.toml",
            "assets/myvillage/guideme_guides/cultivation.json":
                "src/main/resources/assets/myvillage/guideme_guides/cultivation.json",
            "assets/myvillage/models/item/cultivation_handbook.json":
                "src/main/resources/assets/myvillage/models/item/cultivation_handbook.json",
            "assets/myvillage/lang/en_us.json":
                "src/main/resources/assets/myvillage/lang/en_us.json",
            "assets/myvillage/lang/zh_cn.json":
                "src/main/resources/assets/myvillage/lang/zh_cn.json",
        }
        with zipfile.ZipFile(jar, "w") as archive:
            for entry, relative in source_entries.items():
                if entry != omit:
                    if entry in overrides:
                        archive.writestr(entry, overrides[entry])
                    else:
                        archive.write(self.root / relative, entry)
            for page in sorted((self.root / "guidebook").rglob("*.md")):
                relative = page.relative_to(self.root / "guidebook").as_posix()
                entry = f"{PACKAGED_GUIDE_ROOT}/{relative}"
                if entry != omit:
                    archive.write(page, entry)
            for entry in (
                "com/example/myvillage/item/ModItems.class",
                "com/example/myvillage/item/CultivationHandbookItem.class",
            ):
                if entry != omit:
                    archive.writestr(entry, b"class")
            if embed_guideme:
                archive.writestr("guideme/GuidesCommon.class", b"class")
                archive.writestr("META-INF/jarjar/guideme-21.1.17.jar", b"jar")
        return jar

    def test_complete_fixture_passes_without_jar(self) -> None:
        result = self.validate()
        self.assertEqual((), result.errors)
        self.assertIn("skipped_missing", result.jar_status)

    def test_local_jar_dependency_is_rejected(self) -> None:
        path = self.root / "build.gradle"
        path.write_text(
            path.read_text(encoding="utf-8")
            + '\nruntimeOnly files("guideme-21.1.17.jar")\n',
            encoding="utf-8",
        )
        self.assert_error_contains(self.validate(), "local GuideME jar wiring is forbidden")

    def test_missing_english_page_is_rejected(self) -> None:
        (self.root / "guidebook/_en_us/getting_started/initiation.md").unlink()
        self.assert_error_contains(self.validate(), "page topology must be exactly six paired pages")

    def test_broken_internal_link_is_rejected(self) -> None:
        path = self.root / "guidebook/index.md"
        path.write_text(
            path.read_text(encoding="utf-8").replace(
                "getting_started/initiation.md", "getting_started/missing.md"
            ),
            encoding="utf-8",
        )
        self.assert_error_contains(self.validate(), "internal page link does not resolve")

    def test_unknown_item_reference_is_rejected(self) -> None:
        path = self.root / "guidebook/getting_started/cultivation_loop.md"
        path.write_text(
            path.read_text(encoding="utf-8").replace(
                'id="myvillage:low_grade_spirit_stone"', 'id="myvillage:missing_stone"', 1
            ),
            encoding="utf-8",
        )
        self.assert_error_contains(self.validate(), "ItemLink references unknown registered item")

    def test_model_parent_drift_is_rejected(self) -> None:
        self.write_json(
            "src/main/resources/assets/myvillage/models/item/cultivation_handbook.json",
            {"parent": "minecraft:item/generated"},
        )
        self.assert_error_contains(self.validate(), "parent guideme:item/guide_base")

    def test_preview_id_and_command_order_drift_are_rejected(self) -> None:
        build = self.root / "build.gradle"
        build.write_text(
            build.read_text(encoding="utf-8").replace(
                "'guideme.showOnStartup', 'myvillage:cultivation'",
                "'guideme.showOnStartup', 'myvillage:cultivation!index.md'",
            ),
            encoding="utf-8",
        )
        readme = self.root / "README.md"
        readme.write_text(
            readme.read_text(encoding="utf-8").replace(
                "/guidemec myvillage:cultivation open",
                "/guidemec open myvillage:cultivation",
            ),
            encoding="utf-8",
        )
        result = self.validate()
        self.assert_error_contains(result, "runGuide missing exact preview setting")
        self.assert_error_contains(result, "preview command order is wrong")

    def test_server_unguarded_open_is_rejected(self) -> None:
        path = self.root / "src/main/java/com/example/myvillage/item/CultivationHandbookItem.java"
        text = path.read_text(encoding="utf-8")
        text = text.replace("if (level.isClientSide()) {", "if (true) {", 1)
        path.write_text(text, encoding="utf-8")
        self.assert_error_contains(self.validate(), "must be guarded by level.isClientSide()")

    def test_practical_jar_with_exact_entries_passes(self) -> None:
        jar = self.build_jar()
        result = self.validate(jar=jar)
        self.assertEqual((), result.errors)
        self.assertIn("checked", result.jar_status)

    def test_jar_missing_page_and_embedding_guideme_is_rejected(self) -> None:
        omitted = f"{PACKAGED_GUIDE_ROOT}/_en_us/index.md"
        jar = self.build_jar(omit=omitted, embed_guideme=True)
        result = self.validate(jar=jar)
        self.assert_error_contains(result, f"missing packaged entry {omitted}")
        self.assert_error_contains(result, "must not copy GuideME class")
        self.assert_error_contains(result, "must not embed GuideME jar")

    def test_jar_missing_mod_metadata_is_rejected(self) -> None:
        omitted = "META-INF/neoforge.mods.toml"
        result = self.validate(jar=self.build_jar(omit=omitted))
        self.assert_error_contains(result, f"missing packaged entry {omitted}")

    def test_jar_mod_metadata_drift_is_rejected(self) -> None:
        entry = "META-INF/neoforge.mods.toml"
        result = self.validate(jar=self.build_jar(overrides={entry: b"stale metadata\n"}))
        self.assert_error_contains(result, f"packaged entry differs from source truth: {entry}")


if __name__ == "__main__":
    unittest.main()
