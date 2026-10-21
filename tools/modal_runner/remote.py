"""Uploaded independently of the tested commit; standard library only."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import signal
import subprocess
import tarfile
import time
from threading import Event, Thread

PAL = "PlayerAnimationLibNeoforge-1.1.4+mc.1.21.1.jar"
CATALOG = "exmod/mod_block_catalog.json"
DEPENDENCIES = (PAL, CATALOG)
PRESETS = {
    "exploration-build": [
        ["python3", "tools/validate_exploration.py"],
        ["python3", "tools/gen_exploration_guide.py", "--check"],
        ["python3", "tools/gen_forging_assets.py", "--check"],
        ["./gradlew", "test", "--tests", "com.example.myvillage.exploration.*",
         "--tests", "com.example.myvillage.stats.*", "--tests", "com.example.myvillage.wilderness.*",
         "--tests", "com.example.myvillage.sim.WorldSimGuideTest", "--tests", "com.example.myvillage.sim.WorldSimLifeTest",
         "--tests", "com.example.myvillage.sim.WorldSimSaveLoadTest", "--tests", "com.example.myvillage.sim.WorldSimCommerceTest",
         "--tests", "com.example.myvillage.sim.SimPurityGuardTest", "--tests", "com.example.myvillage.forging.*",
         "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx3g", "--max-workers=4"],
    ],
    "commerce-build": [
        ["./gradlew", "test", "--tests", "com.example.myvillage.sim.WorldSimCommerceTest",
         "--tests", "com.example.myvillage.worldplan.WorldPlanV4Test",
         "--tests", "com.example.myvillage.sim.WorldSimMerchantDutyTest",
         "--tests", "com.example.myvillage.sim.runtime.commerce.*",
         "--tests", "com.example.myvillage.sim.SimPurityGuardTest",
         "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx3g", "--max-workers=4"],
    ],
    "yunshan-duty-build": [
        ["./gradlew", "test", "--tests", "com.example.myvillage.sim.WorldSimMerchantDutyTest",
         "--tests", "com.example.myvillage.sim.WorldSimCommerceTest",
         "--tests", "com.example.myvillage.sim.WorldSimLifeTest",
         "--tests", "com.example.myvillage.sim.WorldSimSaveLoadTest",
         "--tests", "com.example.myvillage.sim.engine.NpcCombatLedgerTest",
         "--tests", "com.example.myvillage.sim.runtime.life.NpcLifeNavigationTest",
         "--tests", "com.example.myvillage.sim.runtime.commerce.*",
         "--tests", "com.example.myvillage.sim.SimPurityGuardTest",
         "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx3g", "--max-workers=4"],
    ],
    "vanilla-monster-realms": [
        ["./gradlew", "test", "--tests", "com.example.myvillage.stats.*", "--tests", "com.example.myvillage.wilderness.*",
         "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx2g", "--max-workers=4"],
        ["python3", "tools/vanilla_monster_realm_evidence.py"],
    ],
    "npc-life-build": [
        ["python3", "tools/gen_npc_life_places.py", "--check"],
        ["python3", "-m", "unittest", "tools.tests.test_npc_life_places"],
        ["./gradlew", "test",
         "--tests", "com.example.myvillage.sim.WorldSimLifeTest",
         "--tests", "com.example.myvillage.sim.WorldSimSocialTest",
         "--tests", "com.example.myvillage.sim.engine.NpcLifeMacroTest",
         "--tests", "com.example.myvillage.sim.WorldSimSaveLoadTest",
         "--tests", "com.example.myvillage.sim.WorldSimPlayerMembersTest",
         "--tests", "com.example.myvillage.sim.WorldSimDeterminismTest",
         "--tests", "com.example.myvillage.sim.WorldSimTasksTest",
         "--tests", "com.example.myvillage.sim.SimDataLoaderTest",
         "--tests", "com.example.myvillage.sim.SimPurityGuardTest",
         "--tests", "com.example.myvillage.sim.SettlementSchedulerTest",
         "--tests", "com.example.myvillage.block.AlchemyFurnaceNavigationTest",
         "jar", "-x", "generateAllStructures", "--no-daemon",
         "-Dorg.gradle.jvmargs=-Xmx2g", "--max-workers=4"],
    ],
    "worldplan-build": [["python3", "tools/worldplan/generate_assets.py"],
                       ["python3", "tools/worldplan/generate_assets.py", "--check"],
                       ["./gradlew", "test", "--tests", "*worldplan*", "--tests", "*region.runtime*", "--tests", "*WorldSimGenesisTest*", "--tests", "*SimPurityGuardTest*", "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx3g", "--max-workers=4"]],
    "smoke": [["java", "-version"], ["python3", "-c", "import yaml, numpy, PIL; print('python dependencies OK')"],
              ["xvfb-run", "-a", "glxinfo", "-B"]],
    "python": [["python3", "-m", "unittest", "discover", "-s", "tools/tests"]],
    "jar": [["./gradlew", "jar", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx2g", "--max-workers=4"]],
    "quality-picker-build": [["./gradlew", "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx2g", "--max-workers=4"]],
    "build": [["./gradlew", "test", "build", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx2g", "--max-workers=4"]],
    "crops-build": [["./gradlew", "test", "--tests", "com.example.myvillage.block.SpiritCropsTest", "build", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx2g", "--max-workers=4"]],
    "sword-qi-range": [["python3", "-m", "unittest", "tools.tests.test_combat_data", "tools.tests.test_sword_qi_capture"],
                       ["./gradlew", "test", "--tests", "com.example.myvillage.combat.session.JianqiJueSkillTest", "--tests", "com.example.myvillage.combat.definition.CombatDataLoaderTest", "--tests", "com.example.myvillage.combat.runtime.CombatWaveResolverTest", "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx2g", "--max-workers=4"],
                       ["python3", "-m", "tools.combat_capture", "sword-qi", "--trials", "tap_far_hit,tap_far_miss,charged_extra_hit,charged_limit_hit,charged_range_miss,wall", "--out", "out/preview/sword_qi_range"]],
    "sword-qi-waist": [["python3", "tools/gen_sword_pal_anims.py", "--check"],
                       ["./gradlew", "test", "--tests", "com.example.myvillage.client.combat.FirstPersonPreviewParityTest", "--tests", "com.example.myvillage.client.combat.FirstPersonSwingTest", "-PupdatePreviewParity", "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx2g", "--max-workers=4"],
                       ["python3", "-c", "import shutil; from pathlib import Path; Path('reports').mkdir(exist_ok=True); shutil.copy2('src/test/resources/first_person_preview_parity.json','reports/first_person_preview_parity.json')"],
                       ["python3", "-m", "unittest", "tools.tests.test_combat_preview_parity", "tools.tests.test_sword_qi_presentation"],
                       ["python3", "tools/sword_qi_charge_evidence.py"]],
    "sword-qi-charge": [["python3", "-m", "unittest", "tools.tests.test_sword_qi_capture", "tools.tests.test_sword_qi_presentation", "tools.tests.test_combat_data"],
                        ["./gradlew", "test", "--tests", "com.example.myvillage.combat.session.JianqiJueSkillTest", "--tests", "com.example.myvillage.combat.definition.CombatDataLoaderTest", "--tests", "com.example.myvillage.client.combat.CombatAnimationControllerTest", "--tests", "com.example.myvillage.client.combat.QiUseLatchTest", "--tests", "com.example.myvillage.client.combat.FirstPersonSwingTest", "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx2g", "--max-workers=4"],
                        ["python3", "tools/sword_qi_charge_evidence.py"]],
    "sword-qi-blade": [["python3", "-m", "unittest", "tools.tests.test_sword_qi_capture", "tools.tests.test_sword_qi_presentation", "tools.tests.test_combat_data"],
                       ["./gradlew", "test", "--tests", "com.example.myvillage.combat.runtime.CombatWaveResolverTest", "--tests", "com.example.myvillage.client.combat.SwordQiWaveShapeTest", "--tests", "com.example.myvillage.combat.session.JianqiJueSkillTest", "--tests", "com.example.myvillage.combat.definition.CombatDataLoaderTest", "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx2g", "--max-workers=4"],
                       ["python3", "tools/sword_qi_blade_evidence.py"]],
    "sword-qi-area": [["python3", "-m", "unittest", "tools.tests.test_sword_qi_capture"],
                      ["./gradlew", "test", "--tests", "com.example.myvillage.combat.runtime.CombatWaveResolverTest", "--tests", "com.example.myvillage.client.combat.SwordQiWaveShapeTest", "--tests", "com.example.myvillage.combat.session.JianqiJueSkillTest", "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx2g", "--max-workers=4"],
                      ["python3", "-m", "tools.combat_capture", "sword-qi", "--trials", "tap_hit,charged_hit,tap_wide_edge,tap_outside_width,charged_wide_edge,charged_outside_width,tap_high_edge,charged_high_edge,wall,edge_wall", "--out", "out/preview/sword_qi_area"]],
    "demon-trial-build": [["python3", "-m", "unittest", "tools.tests.test_demon_trial"],
                          ["python3", "tools/gen_demon_trial.py", "--check"],
                          ["./gradlew", "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx2g", "--max-workers=4"]],
    "demon-trial": [["python3", "-m", "unittest", "tools.tests.test_demon_trial"],
                    ["python3", "tools/gen_demon_trial.py", "--check"],
                    ["./gradlew", "test", "--tests", "com.example.myvillage.ruins.*", "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx2g", "--max-workers=4"],
                    ["python3", "tools/demon_trial_evidence.py"]],
    "combat-ruins-build": [["python3", "-m", "unittest", "tools.tests.test_combat_ruins", "tools.tests.test_danxia_ruin"],
                           ["python3", "tools/gen_combat_ruins.py", "--check"],
                           ["./gradlew", "test", "--tests", "com.example.myvillage.ruins.*", "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx2g", "--max-workers=4"]],
    "combat-ruins-runtime": [["python3", "-m", "unittest", "tools.tests.test_combat_ruins", "tools.tests.test_danxia_ruin"],
                             ["python3", "tools/gen_combat_ruins.py", "--check"],
                             ["./gradlew", "test", "--tests", "com.example.myvillage.ruins.*", "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx2g", "--max-workers=4"],
                             ["python3", "tools/combat_ruins_evidence.py"]],
    "danxia-build": [["python3", "-m", "unittest", "tools.tests.test_danxia_ruin", "tools.tests.test_combat_capture", "tools.tests.test_modal_runner"],
                     ["python3", "tools/gen_danxia_ruin.py", "--check"],
                     ["./gradlew", "test", "--tests", "com.example.myvillage.ruins.*", "build", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx2g", "--max-workers=4"]],
    "danxia-runtime": [["python3", "-m", "unittest", "tools.tests.test_danxia_ruin"],
                       ["python3", "tools/gen_danxia_ruin.py", "--check"],
                       ["./gradlew", "test", "--tests", "com.example.myvillage.ruins.*", "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx2g", "--max-workers=4"],
                       ["python3", "tools/danxia_ruin_evidence.py"]],
    "forging-build": [["python3", "tools/gen_forging_assets.py", "--check"],
                      ["./gradlew", "test", "--tests", "com.example.myvillage.forging.*", "--tests", "com.example.myvillage.alchemy.LearnedFormulasTest", "--tests", "com.example.myvillage.item.CombatWeaponItemTest", "--tests", "com.example.myvillage.combat.network.CombatPayloadTest", "--tests", "com.example.myvillage.combat.session.JianqiJueSkillTest", "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx2g", "--max-workers=4"]],
    "alchemy-build": [["./gradlew", "test", "--tests", "com.example.myvillage.alchemy.*", "build", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx2g", "--max-workers=4"]],
    "fates": [["./gradlew", "test", "--tests", "com.example.myvillage.cultivation.fate.FateFoundationTest", "build", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx2g", "--max-workers=4"]],
    "release": [["python3", "tools/release_gate.py"]],
    "server": [["python3", "tools/run_chunky_acceptance.py", "--stage", "1"]],
    "client": [["python3", "-m", "tools.combat_capture", "sword-qi", "--trials", "tap_hit,charged_hit", "--out", "out/preview/modal_sword_qi"]],
    "alchemy-quality": [["python3", "tools/run_chunky_acceptance.py", "--stage", "1"],
                        ["python3", "tools/alchemy_quality_evidence.py"]],
}
PRESETS["atlas-build"] = [["./gradlew", "test", "--tests", "*WorldAtlas*", "--tests", "*WorldSimPayloadCodecTest*",
     "--tests", "*WorldSimPayloadThrottleTest*", "--tests", "*CombatPayloadTest*", "jar", "-x", "generateAllStructures",
     "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx3g", "--max-workers=4"]]
PRESETS["atlas-runtime"] = PRESETS["atlas-build"] + [["python3", "tools/worldplan/atlas_capture.py"]]
PRESETS["atlas-play"] = [["./gradlew", "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx3g", "--max-workers=4"], ["python3", "tools/worldplan/atlas_capture.py"]]

PRESETS["spirit-stone-build"] = [[
    "./gradlew", "test", "--tests", "com.example.myvillage.cultivation.meditation.*",
    "--tests", "com.example.myvillage.alchemy.*", "--tests", "com.example.myvillage.forging.*",
    "--tests", "com.example.myvillage.sim.runtime.player.SectTasksTest",
    "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx2g", "--max-workers=4",
]]

PRESETS["spirit-bag-build"] = [
    ["python3", "tools/gen_spirit_stone_bag.py", "--check"],
    ["./gradlew", "test", "--tests", "com.example.myvillage.spiritstone.*",
     "--tests", "com.example.myvillage.cultivation.meditation.*", "--tests", "com.example.myvillage.alchemy.*",
     "--tests", "com.example.myvillage.forging.*", "--tests", "com.example.myvillage.sim.runtime.commerce.*",
     "--tests", "com.example.myvillage.sim.runtime.player.SectTasksTest",
     "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx2g", "--max-workers=4"],
]
PRESETS["spirit-bag-runtime"] = PRESETS["spirit-bag-build"] + [["python3", "tools/spirit_stone_bag_evidence.py"]]
PRESETS["spirit-bag-preview"] = PRESETS["spirit-bag-build"] + [["python3", "tools/spirit_stone_bag_evidence.py", "--preview-only", "--out", "out/preview/spirit_stone_bag/display"]]

PRESETS["npc-combat-build"] = [
    ["./gradlew", "test", "--tests", "com.example.myvillage.sim.engine.NpcCombatLedgerTest",
     "--tests", "com.example.myvillage.sim.engine.NpcLifeMacroTest",
     "--tests", "com.example.myvillage.sim.engine.PlayerAffairsTest",
     "--tests", "com.example.myvillage.sim.WorldSimSaveLoadTest",
     "--tests", "com.example.myvillage.sim.WorldSimLifeTest",
     "--tests", "com.example.myvillage.sim.WorldSimSocialTest",
     "--tests", "com.example.myvillage.sim.SimPurityGuardTest",
     "--tests", "com.example.myvillage.combat.network.CombatPayloadTest",
     "--tests", "com.example.myvillage.combat.session.*",
     "--tests", "com.example.myvillage.combat.runtime.*",
     "--tests", "com.example.myvillage.combat.art.*",
     "--tests", "com.example.myvillage.client.entity.npc.*",
     "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx2g", "--max-workers=4"],
]

PRESETS["npc-combat-runtime"] = [
    ["./gradlew", "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx2g", "--max-workers=4"],
    ["python3", "tools/yunshan/build_functional.py", "--manifest", "blueprints/yunshan/functional_buildings_v2.json", "--out", "out/preview/npc-life-buildings"],
    ["python3", "tools/npc_combat_evidence.py"],
]
NPC_COMBAT_CASES = ("duel", "reactions", "cross_targets", "group_hits", "collision", "retreat", "ledge", "life", "lifecycle", "death", "sources", "observers")


def select_npc_combat_cases(value=None):
    if value is None or value == "all":
        return list(NPC_COMBAT_CASES)
    names = [name.strip() for name in value.split(",")]
    if len(names) != len(set(names)) or any(name not in NPC_COMBAT_CASES for name in names):
        raise ValueError("NPC combat cases must be all or unique names from: " + ",".join(NPC_COMBAT_CASES))
    return [name for name in NPC_COMBAT_CASES if name in names]


PRESETS["advanced-arts-build"] = [
    ["python3", "tools/gen_advanced_arts.py", "--check"],
    ["python3", "tools/gen_technique_catalogue.py", "--check"],
    ["python3", "-m", "unittest", "tools.tests.test_advanced_arts", "tools.tests.test_combat_data"],
    ["./gradlew", "test", "--tests", "com.example.myvillage.combat.art.*",
     "--tests", "com.example.myvillage.combat.session.*", "--tests", "com.example.myvillage.combat.network.*",
     "--tests", "com.example.myvillage.combat.runtime.*",
    "--tests", "com.example.myvillage.cultivation.fate.*",
    "--tests", "com.example.myvillage.client.combat.AdvancedArtClientStateTest",
     "--tests", "com.example.myvillage.client.combat.ClientCombatStateTest",
     "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx2g", "--max-workers=4"],
]
PRESETS["advanced-arts-runtime"] = [
    ["./gradlew", "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx2g", "--max-workers=4"],
    ["python3", "tools/advanced_arts_evidence.py"],
]
PRESETS["advanced-arts-hud"] = [PRESETS["advanced-arts-runtime"][0],
    ["python3", "tools/advanced_arts_evidence.py", "--hud-only"]]
PRESETS["forging-ding"] = [["python3", "tools/gen_forging_assets.py", "--check"],
    ["./gradlew", "test", "--tests", "com.example.myvillage.block.ForgingDingTest", "--tests", "com.example.myvillage.forging.*", "--tests", "com.example.myvillage.combat.network.CombatPayloadTest", "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx2g", "--max-workers=4"],
    ["python3", "tools/forging_ding_evidence.py"]]
PRESETS["forging-lifecycle"] = [["./gradlew", "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx2g", "--max-workers=4"]] + [["python3", "tools/forging_evidence.py", "--lifecycle-only"]]
PRESETS["workshop-build"] = [["python3", "tools/gen_forging_assets.py", "--check"],
    ["./gradlew", "test", "--tests", "com.example.myvillage.forging.*", "--tests", "com.example.myvillage.item.CombatWeaponItemTest",
     "--tests", "com.example.myvillage.combat.network.CombatPayloadTest", "jar", "-x", "generateAllStructures",
     "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx3g", "--max-workers=4"]]
PRESETS["workshop-runtime"] = PRESETS["workshop-build"] + [["python3", "tools/forging_workshop_evidence.py"]]
PRESETS["workshop-play"] = [["./gradlew", "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx3g", "--max-workers=4"], ["python3", "tools/forging_workshop_evidence.py"]]
# Focused array checks keep the normal generated resource dependencies intact.
PRESETS["array-build"] = [
    ["python3", "tools/gen_alchemy_array_assets.py", "--check"],
    ["./gradlew", "test", "--tests", "com.example.myvillage.alchemy.*", "jar", "-x", "generateAllStructures",
     "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx3g", "--max-workers=4"],
]
PRESETS["array-runtime"] = PRESETS["array-build"] + [["python3", "tools/alchemy_array_evidence.py"]]
PRESETS["array-play"] = [
    ["./gradlew", "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx3g", "--max-workers=4"],
    ["python3", "tools/alchemy_array_evidence.py"],
]
PRESETS["gathering-build"] = [
    ["python3", "tools/gen_gathering_assets.py", "--check"],
    ["./gradlew", "test", "--tests", "com.example.myvillage.gathering.*",
     "--tests", "com.example.myvillage.cultivation.meditation.*",
     "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx3g", "--max-workers=4"],
]
PRESETS["gathering-runtime"] = PRESETS["gathering-build"] + [["python3", "tools/gathering_evidence.py"]]
PRESETS["gathering-play"] = [
    ["./gradlew", "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx3g", "--max-workers=4"],
    ["python3", "tools/gathering_evidence.py"],
]
PRESETS["forging-runtime"] = PRESETS["forging-build"] + [["python3", "tools/forging_evidence.py"]]
PRESETS["forging-tags"] = [PRESETS["forging-build"][0],PRESETS["forging-lifecycle"][0],["python3", "tools/forging_tags_evidence.py"]]
PRESETS["forging-quality-preview"] = [PRESETS["forging-build"][0],PRESETS["forging-lifecycle"][0],["python3", "tools/forging_quality_evidence.py"]]
PRESETS["pill-quality-preview"] = [["python3", "tools/gen_alchemy_assets.py", "--check"],
    ["./gradlew", "test", "--tests", "com.example.myvillage.alchemy.PillQualityTest", "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx2g", "--max-workers=4"],
    ["python3", "tools/pill_quality_frames_evidence.py"]]
PRESETS["forging-acquisition"] = PRESETS["forging-build"] + [["python3", "tools/forging_acquisition_evidence.py"]]
PRESETS["forging-acquisition-preview"] = [PRESETS["forging-lifecycle"][0],["python3", "tools/forging_acquisition_evidence.py", "--visual-only", "--out", "out/preview/forging_acquisition/showcase"]]
PRESETS["release-alchemy"] = PRESETS["release"] + PRESETS["alchemy-quality"]
PRESETS["courtyard-build"] = [
    ["python3", "tools/reference_courtyard/minecraft.py", "--check"],
    ["python3", "-m", "unittest", "tools.tests.test_reference_courtyard_mc"],
    ["python3", "tools/validate_mod_items.py"],
    ["./gradlew", "test", "--tests", "com.example.myvillage.courtyard.*", "--tests", "com.example.myvillage.combat.network.CombatPayloadTest", "jar", "-x", "generateAllStructures", "-Pcourtyard_jei_test", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx3g", "--max-workers=4"],
]
PRESETS["courtyard-runtime"] = PRESETS["courtyard-build"] + [
    ["python3", "tools/reference_courtyard/game_evidence.py"],
]
PRESETS["courtyard-gate"] = [
    *PRESETS["courtyard-build"][:3],
    ["./gradlew", "jar", "-x", "generateAllStructures", "-Pcourtyard_jei_test", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx3g", "--max-workers=4"],
    ["python3", "tools/reference_courtyard/game_evidence.py", "--gate-only"],
]
PRESETS["courtyard-interior"] = [*PRESETS["courtyard-gate"][:-1],
    ["python3", "tools/reference_courtyard/game_evidence.py", "--interior-only"],
]
PRESETS["courtyard-roof"] = [*PRESETS["courtyard-build"],
    ["python3", "tools/reference_courtyard/game_evidence.py", "--roof-only"],
]
PRESETS["courtyard-joints"] = [
    ["python3", "tools/reference_courtyard/minecraft.py", "--check"],
    ["python3", "-m", "unittest", "tools.tests.test_reference_courtyard_mc", "tools.tests.test_courtyard_supports", "tools.tests.test_courtyard_windows", "tools.tests.test_courtyard_roof_rise"],
    ["python3", "tools/validate_mod_items.py"],
    ["./gradlew", "test", "--tests", "com.example.myvillage.courtyard.*", "jar", "-x", "generateAllStructures", "-Pcourtyard_jei_test", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx3g", "--max-workers=4"],
    ["python3", "tools/reference_courtyard/game_evidence.py", "--joints-only"],
]
PRESETS["yunshan-buildings"] = [
    ["python3", "-m", "unittest", "tools.tests.test_yunshan_expanded"],
    ["python3", "tools/yunshan/build_functional.py", "--manifest", "blueprints/yunshan/functional_buildings_v2.json", "--out", "out/preview/yunshan-functional-v2"],
    ["./gradlew", "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx2g", "--max-workers=4"],
    ["python3", "tools/yunshan/game_evidence.py"],
]
PRESETS["yunshan-district"] = [
    ["python3", "-m", "unittest", "tools.tests.test_yunshan_district_evidence", "tools.tests.test_yunshan_district_build"],
    ["python3", "tools/yunshan/build_district.py"],
    ["./gradlew", "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx2g", "--max-workers=4"],
    ["python3", "tools/yunshan/district_game_evidence.py"],
]
PRESETS["yunshan-natural"] = [
    ["python3", "-m", "unittest", "tools.tests.test_yunshan_natural_evidence", "tools.tests.test_yunshan_natural_terrain", "tools.tests.test_yunshan_natural_fit"],
    ["./gradlew", "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx2g", "--max-workers=4"],
    ["python3", "tools/yunshan/natural_evidence.py"],
]
PRESETS["yunshan-natural-site"] = [list(command) for command in PRESETS["yunshan-natural"][:-1]] + [
    ["python3", "tools/yunshan/natural_evidence.py", "--site-id", "site-1-q0--96--112"],
]
PRESETS["yunshan-auto-sites"] = [
    ["python3", "-m", "unittest", "tools.tests.test_yunshan_auto_sites", "tools.tests.test_yunshan_auto_evidence",
     "tools.tests.test_yunshan_natural_terrain", "tools.tests.test_yunshan_natural_fit", "tools.tests.test_yunshan_natural_evidence", "tools.tests.test_combat_capture"],
    ["./gradlew", "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx2g", "--max-workers=4"],
    ["python3", "tools/yunshan/auto_site_evidence.py"],
]
PRESETS["yunshan-flat-fallback"] = [
    ["python3", "-m", "unittest", "tools.tests.test_yunshan_flat_fallback", "tools.tests.test_yunshan_district_build",
     "tools.tests.test_yunshan_auto_sites", "tools.tests.test_yunshan_auto_evidence", "tools.tests.test_yunshan_natural_terrain",
     "tools.tests.test_yunshan_natural_fit", "tools.tests.test_yunshan_natural_evidence"],
    list(PRESETS["yunshan-auto-sites"][1]),
    ["python3", "tools/yunshan/auto_site_evidence.py", "--seeds", "20261002", "--require-flat-fallback"],
]
PRESETS["exploration-native"] = PRESETS["exploration-build"] + [
    ["python3", "tools/vanilla_monster_realm_evidence.py", "--exploration"],
]
PRESETS["exploration-runtime"] = PRESETS["exploration-build"] + [
    ["python3", "tools/yunshan/build_functional.py", "--manifest", "blueprints/yunshan/functional_buildings_v2.json", "--out", "out/preview/npc-life-buildings"],
    ["python3", "tools/exploration_evidence.py"],
]
# Diagnostic replay for evidence-only changes after the product's focused Java gate passed.
# The fixed source is still rebuilt; reports must identify the reused Java/native producer.
PRESETS["exploration-play"] = [
    ["./gradlew", "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx3g", "--max-workers=4"],
    *PRESETS["exploration-runtime"][len(PRESETS["exploration-build"]):],
]
PRESETS["exploration-chest-restart"] = [
    list(PRESETS["exploration-play"][0]),
    ["python3", "tools/exploration_evidence.py", "--chest-restart-only"],
]
PRESETS["exploration-tooltip"] = [
    ["./gradlew", "test", "--tests", "com.example.myvillage.forging.ForgingTagCostsTest",
     "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx3g", "--max-workers=4"],
]
EXPLORATION_V2_CASES = ("progression", "bosses", "portal", "dragon", "end_chest", "two_clients", "restart")
PRESETS["exploration-v2-build"] = [
    ["python3", "tools/validate_exploration.py"],
    ["python3", "tools/gen_exploration_guide.py", "--check"],
    ["./gradlew", "test", "--tests", "com.example.myvillage.exploration.*",
     "--tests", "com.example.myvillage.combat.*", "--tests", "com.example.myvillage.stats.*",
     "--tests", "com.example.myvillage.wilderness.*", "--tests", "com.example.myvillage.cultivation.fate.*",
     "--tests", "com.example.myvillage.sim.engine.NpcCombatLedgerTest",
     "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx3g", "--max-workers=4"],
]
PRESETS["exploration-v2-native"] = PRESETS["exploration-v2-build"] + [
    ["python3", "tools/exploration_v2_native_evidence.py"],
]
PRESETS["exploration-v2-runtime"] = PRESETS["exploration-v2-native"] + [
    ["python3", "tools/exploration_v2_evidence.py"],
]
PRESETS["exploration-v2-play"] = [
    list(PRESETS["exploration-play"][0]),
    ["python3", "tools/exploration_v2_evidence.py"],
]
THUNDER_TIGER_CASES = ("entry", "visuals", "combat", "phase2", "horn", "observers", "restart", "death")
PRESETS["thunder-tiger-build"] = [
    ["python3", "tools/validate_custom_entities.py"],
    ["python3", "-m", "tools.beastgen", "build", "thunder_tiger", "--check"],
    ["python3", "tools/gen_thunder_tiger_support.py", "--check"],
    ["python3", "tools/gen_thunder_tiger_lair.py", "--check"],
    ["python3", "-m", "unittest", "tools.tests.test_beastgen", "tools.tests.test_thunder_tiger_art", "tools.tests.test_thunder_tiger_lair",
     "tools.tests.test_validate_custom_entities", "tools.tests.test_thunder_tiger_evidence"],
    ["./gradlew", "test", "--tests", "com.example.myvillage.entity.beast.*",
     "--tests", "com.example.myvillage.boss.lair.*", "--tests", "com.example.myvillage.combat.runtime.*",
     "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx3g", "--max-workers=4"],
]
PRESETS["thunder-tiger-runtime"] = PRESETS["thunder-tiger-build"] + [
    ["python3", "tools/thunder_tiger_evidence.py"],
]
PRESETS["thunder-tiger-play"] = [
    ["./gradlew", "jar", "-x", "generateAllStructures", "-x", "generateWorldPlanAssets", "-x", "generateNpcLifePlaces",
     "-x", "generateExplorationGuide", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx3g", "--max-workers=4"],
    ["python3", "tools/thunder_tiger_evidence.py"],
]
PRESETS["bossbar-a-build"] = [
    ["python3", "tools/gen_thunder_tiger_support.py", "--check"],
    ["./gradlew", "test", "--tests", "com.example.myvillage.boss.BossBarPresentationTest",
     "--tests", "com.example.myvillage.client.boss.*", "--tests", "com.example.myvillage.client.cultivation.QiBarLayoutTest",
     "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx3g", "--max-workers=4"],
]
PRESETS["bossbar-a"] = PRESETS["bossbar-a-build"] + [
    ["python3", "tools/bossbar_evidence.py"],
]
PRESETS["bossbar-fonts"] = [
    ["./gradlew", "test", "--tests", "com.example.myvillage.client.boss.*",
     "--tests", "com.example.myvillage.worldplan.WorldAtlasTest.approvedFontsAndPaperArePackagedAndRenderHeadlessly",
     "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx3g", "--max-workers=4"],
    ["python3", "tools/bossbar_evidence.py", "--typography-only"],
]
PRESETS["bossbar-fonts-play"] = [["python3", "tools/bossbar_evidence.py", "--typography-only"]]
PRESETS["bossbar-fonts-build"] = [list(PRESETS["bossbar-fonts"][0])]
PRESETS["cultivation-ink-build"] = [
    ["python3", "tools/gen_cultivation_ink_assets.py", "--check"],
    ["./gradlew", "test", "--tests", "com.example.myvillage.client.cultivation.panel.*",
     "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx3g", "--max-workers=4"],
]
PRESETS["cultivation-ink"] = PRESETS["cultivation-ink-build"] + [
    ["python3", "tools/cultivation_ink_evidence.py"],
]
PRESETS["cultivation-ink-layout"] = [
    ["python3", "tools/gen_cultivation_ink_assets.py", "--check"],
    ["./gradlew", "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx3g", "--max-workers=4"],
    ["python3", "tools/cultivation_ink_evidence.py", "--layout-only"],
]
PRESETS["npc-life-runtime"] = [
    ["python3", "tools/gen_npc_life_places.py", "--check"],
    ["./gradlew", "test", "--tests", "com.example.myvillage.block.AlchemyFurnaceNavigationTest",
     "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx2g", "--max-workers=4"],
    ["python3", "tools/yunshan/build_functional.py", "--manifest", "blueprints/yunshan/functional_buildings_v2.json", "--out", "out/preview/npc-life-buildings"],
    ["python3", "tools/npc_life_evidence.py"],
]
PRESETS["fates-purple-build"] = [
    ["python3", "tools/validate_innate_fates.py"],
    ["./gradlew", "test", "--tests", "com.example.myvillage.cultivation.fate.*",
     "--tests", "com.example.myvillage.cultivation.qi.*",
     "--tests", "com.example.myvillage.cultivation.GoldenCoreProgressionTest",
     "--tests", "com.example.myvillage.combat.session.OpenMeridiansSkillRefundTest",
     "--tests", "com.example.myvillage.combat.session.JianqiJueSkillTest",
     "--tests", "com.example.myvillage.forging.OpenMeridiansForgingRefundTest",
     "--tests", "com.example.myvillage.forging.ForgingTest",
     "--tests", "com.example.myvillage.alchemy.Medicine*Test",
     "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx2g", "--max-workers=4"],
]
PRESETS["fates-purple-runtime"] = [
    ["python3", "tools/validate_innate_fates.py"],
    ["./gradlew", "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx2g", "--max-workers=4"],
    ["python3", "tools/fates_purple_evidence.py"],
]
PRESETS["sword-fate-build"] = [
    ["python3", "tools/validate_innate_fates.py"],
    ["./gradlew", "test", "--tests", "com.example.myvillage.cultivation.fate.*",
     "--tests", "com.example.myvillage.combat.*",
     "--tests", "com.example.myvillage.client.combat.SwordQiWaveShapeTest",
     "--tests", "com.example.myvillage.client.combat.QiUseLatchTest",
     "--tests", "com.example.myvillage.client.cultivation.*",
     "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx2g", "--max-workers=4"],
]
PRESETS["sword-fate-runtime"] = [
    ["./gradlew", "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx2g", "--max-workers=4"],
    ["python3", "tools/sword_fate_evidence.py"],
]
PRESETS["advanced-fates-build"] = [
    ["python3", "tools/validate_innate_fates.py"],
    ["./gradlew", "test", "--tests", "com.example.myvillage.cultivation.fate.*",
     "--tests", "com.example.myvillage.combat.*", "--tests", "com.example.myvillage.stats.*",
     "--tests", "com.example.myvillage.client.combat.SwordQiWaveShapeTest",
     "--tests", "com.example.myvillage.client.combat.QiUseLatchTest",
     "--tests", "com.example.myvillage.client.cultivation.*",
     "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx2g", "--max-workers=4"],
]
PRESETS["advanced-fates-runtime"] = [
    ["./gradlew", "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx2g", "--max-workers=4"],
    ["python3", "tools/advanced_fates_evidence.py"],
]
ADVANCED_FATE_CASES = ("sword_heart", "wind_step", "fire_spirit")
FATE_PURPLE_CASES = ("meridians", "pill_aegis", "tempered_bones", "lifecycle")
ALCHEMY_CASES = ("brewing", "multiplayer", "medicine", "persistence", "ui")
NPC_LIFE_CASES = ("bind_idempotence", "demo_identity", "day_loop", "conversation",
                  "blocked_route", "removed_facility", "unload_return", "restart_mid_activity",
                  "two_players", "avatar_cap", "occupied_spawn", "unsafe_checkpoint", "sect_regression")


def select_npc_life_cases(value=None):
    if value is None or value == "all":
        return list(NPC_LIFE_CASES)
    names = [name.strip() for name in value.split(",")]
    if len(names) != len(set(names)) or any(name not in NPC_LIFE_CASES for name in names):
        raise ValueError("NPC cases must be all or unique names from: " + ",".join(NPC_LIFE_CASES))
    # Every fresh isolated world needs actual registration and existing identities.
    required = {"bind_idempotence", "demo_identity", *names}
    return [name for name in NPC_LIFE_CASES if name in required]


def select_cases(value=None):
    """Canonical bounded selection shared by local planning and game evidence."""
    if value is None or value == "all":
        return list(ALCHEMY_CASES)
    names = [name.strip() for name in value.split(",")]
    if not names or len(names) != len(set(names)) or any(name not in ALCHEMY_CASES for name in names):
        raise ValueError("Cases must be all or unique names from: " + ",".join(ALCHEMY_CASES))
    return [name for name in ALCHEMY_CASES if name in names]



def select_workshop_cases(value=None):
    names=("stages", "training", "tools", "combat", "two_players", "lifecycle", "ui", "trough")
    selected=list(names) if value in (None,"all") else value.split(",")
    if not selected or len(selected)!=len(set(selected)) or not set(selected)<=set(names):
        raise ValueError("Invalid workshop cases")
    return selected


ARRAY_CASES = ("structure", "controller_boundary", "mapped", "automation", "lifecycle", "visual", "legacy", "two_players")


def select_array_cases(value=None):
    selected = list(ARRAY_CASES) if value in (None, "all") else [name.strip() for name in value.split(",")]
    if not selected or len(selected) != len(set(selected)) or any(name not in ARRAY_CASES for name in selected):
        raise ValueError("Array cases must be all or unique names from: " + ",".join(ARRAY_CASES))
    return [name for name in ARRAY_CASES if name in selected]


GATHERING_CASES = ("structure", "mapped", "economy", "overlap", "two_players", "restart", "visual")


def select_gathering_cases(value=None):
    selected = list(GATHERING_CASES) if value in (None, "all") else [name.strip() for name in value.split(",")]
    if not selected or len(selected) != len(set(selected)) or any(name not in GATHERING_CASES for name in selected):
        raise ValueError("Gathering cases must be all or unique names from: " + ",".join(GATHERING_CASES))
    return [name for name in GATHERING_CASES if name in selected]


def commands_for(preset, cases=None):
    if cases is not None and preset not in ("alchemy-quality", "npc-life-runtime", "fates-purple-runtime", "advanced-fates-runtime", "npc-combat-runtime", "exploration-v2-runtime", "exploration-v2-play", "thunder-tiger-runtime", "thunder-tiger-play", "workshop-runtime", "workshop-play", "array-runtime", "array-play", "gathering-runtime", "gathering-play"):
        raise ValueError("--cases requires a runtime/play preset with selectable scenarios")
    commands = [list(command) for command in PRESETS[preset]]
    if preset == "alchemy-quality":
        commands[-1] += ["--cases", ",".join(select_cases(cases))]
    if preset == "npc-combat-runtime":
        selected = select_npc_combat_cases(cases)
        if selected == list(NPC_COMBAT_CASES):
            commands[0] = list(PRESETS["npc-combat-build"][0])
        commands[-1] += ["--cases", ",".join(selected)]
    if preset == "npc-life-runtime":
        commands[-1] += ["--cases", ",".join(select_npc_life_cases(cases))]
    if preset == "fates-purple-runtime":
        commands[-1] += ["--cases", ",".join(select_fate_purple_cases(cases))]
    if preset == "advanced-fates-runtime":
        commands[-1] += ["--cases", ",".join(select_advanced_fate_cases(cases))]
    if preset in ("exploration-v2-runtime", "exploration-v2-play"):
        commands[-1] += ["--cases", ",".join(select_exploration_v2_cases(cases))]
    if preset in ("thunder-tiger-runtime", "thunder-tiger-play"):
        commands[-1] += ["--cases", ",".join(select_thunder_tiger_cases(cases))]
    if preset in ("workshop-runtime", "workshop-play"):
        commands[-1] += ["--cases", ",".join(select_workshop_cases(cases))]
    if preset in ("array-runtime", "array-play"):
        commands[-1] += ["--cases", ",".join(select_array_cases(cases))]
    if preset in ("gathering-runtime", "gathering-play"):
        commands[-1] += ["--cases", ",".join(select_gathering_cases(cases))]
    return commands


def select_thunder_tiger_cases(value=None):
    names = list(THUNDER_TIGER_CASES) if value in (None, "all") else value.split(",")
    if not names or len(names) != len(set(names)) or any(name not in THUNDER_TIGER_CASES for name in names):
        raise ValueError("Boss cases must be all or unique names from: " + ",".join(THUNDER_TIGER_CASES))
    return [case for case in THUNDER_TIGER_CASES if case in names]


def select_exploration_v2_cases(value=None):
    names = list(EXPLORATION_V2_CASES) if value in (None, "all") else value.split(",")
    if not names or len(names) != len(set(names)) or any(name not in EXPLORATION_V2_CASES for name in names):
        raise ValueError("V2 cases must be all or unique names from: " + ",".join(EXPLORATION_V2_CASES))
    return [case for case in EXPLORATION_V2_CASES if case in names]


def select_fate_purple_cases(value=None):
    if value is None or value == "all":
        return list(FATE_PURPLE_CASES)
    names = value.split(",")
    if len(names) != len(set(names)) or any(name not in FATE_PURPLE_CASES for name in names):
        raise ValueError("Fate cases must be all or unique names from: " + ",".join(FATE_PURPLE_CASES))
    return [name for name in FATE_PURPLE_CASES if name in names]


def select_advanced_fate_cases(value=None):
    if value is None or value == "all":
        return list(ADVANCED_FATE_CASES)
    names = [name.strip() for name in value.split(",")]
    if len(names) != len(set(names)) or any(name not in ADVANCED_FATE_CASES for name in names):
        raise ValueError("Advanced fate cases must be all or unique names from: " + ",".join(ADVANCED_FATE_CASES))
    return [name for name in ADVANCED_FATE_CASES if name in names]


ARTIFACT_ROOTS = ("build/libs", "build/test-results", "build/reports", "reports",
                  "out/preview", "run-acceptance/logs", "run-acceptance/acceptance-server-stdout.log",
                  "run-combat-capture/logs", "run-combat-capture/client/options.txt")
MAX_ARTIFACT_BYTES = 2 * 1024 ** 3


def safe_extract(archive: Path, destination: Path) -> None:
    """No links/devices/absolute paths or traversal, even in a trusted Git archive."""
    with tarfile.open(archive) as tf:
        members = tf.getmembers()
        if sum(m.size for m in members) > MAX_ARTIFACT_BYTES:
            raise ValueError("Archive uncompressed content exceeds 2 GiB")
        for m in members:
            p = Path(m.name)
            if p.is_absolute() or ".." in p.parts or not (m.isfile() or m.isdir()):
                raise ValueError(f"Unsafe archive member: {m.name}")
        tf.extractall(destination, members=members, filter="data")


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def initialize_archive_git(repo: Path) -> dict:
    """Make an explicitly synthetic baseline so release generator drift is checked.

    Git archives intentionally contain no original .git history. This local-only
    commit tracks exactly their extracted source, including ignored tracked files,
    before runtime dependencies or test outputs are added. The real tested commit
    remains request['commit']; this baseline must never replace that provenance.
    """
    if (repo / ".git").exists():
        raise ValueError("Extracted archive unexpectedly contains .git")
    commands = (["git", "init", "--quiet"], ["git", "add", "--all", "--force", "--", "."],
                ["git", "-c", "user.name=Modal Archive Baseline", "-c", "user.email=modal-baseline@example.invalid",
                 "commit", "--quiet", "--no-gpg-sign", "--no-verify", "-m", "Synthetic archive validation baseline"])
    for command in commands:
        subprocess.run(command, cwd=repo, check=True, capture_output=True, timeout=120)
    commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo, text=True, timeout=10).strip()
    return {"synthetic": True, "head": commit,
            "purpose": "generator drift comparison only; tested commit is request.commit"}


def package(repo: Path, evidence: Path, output: Path) -> dict:
    paths = list(evidence.rglob("*"))
    for root in ARTIFACT_ROOTS:
        path = repo / root
        paths.extend([path] if path.is_file() else path.rglob("*"))
    selected, total = [], 0
    for p in sorted(set(paths)):
        if not p.is_file() or p.is_symlink():
            continue
        # A symlinked parent could otherwise export files outside the workspace.
        base = evidence if p.is_relative_to(evidence) else repo
        if not p.resolve().is_relative_to(base.resolve()):
            continue
        size = p.stat().st_size
        total += size
        if total > MAX_ARTIFACT_BYTES:
            raise ValueError("Artifacts exceed 2 GiB; narrow the preset/output before rerunning")
        name = ("evidence/" + str(p.relative_to(evidence))) if base == evidence else str(p.relative_to(repo))
        selected.append((p, name, size))
    with tarfile.open(output, "w:gz") as tf:
        for p, name, _ in selected:
            tf.add(p, arcname=name, recursive=False)
    return {"files": len(selected), "uncompressed_bytes": total,
            "archive_bytes": output.stat().st_size, "sha256": sha256(output)}


def run_step(command: list[str], repo: Path, log: Path, timeout: float) -> dict:
    start = time.monotonic()
    result = {"command": command, "started_utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())}
    with log.open("w") as stream:
        p = subprocess.Popen(command, cwd=repo, stdout=stream, stderr=subprocess.STDOUT,
                             start_new_session=True)
        try:
            result["exit_code"] = p.wait(timeout=max(0.1, timeout))
        except subprocess.TimeoutExpired:
            result["timed_out"] = True
            os.killpg(p.pid, signal.SIGKILL)
            p.wait()
            result["exit_code"] = 124
    result["wall_seconds"] = round(time.monotonic() - start, 3)
    return result


def sample_rss(stop: Event, measurements: dict) -> None:
    """All visible Sandbox processes, including detached Gradle JVMs; shared pages counted per process."""
    while not stop.is_set():
        total = count = 0
        for path in Path("/proc").glob("[0-9]*/status"):
            try:
                for line in path.read_text().splitlines():
                    if line.startswith("VmRSS:"):
                        total += int(line.split()[1])
                        count += 1
            except (OSError, ValueError):
                continue
        measurements["peak_rss_sum_kib"] = max(total, measurements.get("peak_rss_sum_kib", 0))
        measurements["peak_processes"] = max(count, measurements.get("peak_processes", 0))
        stop.wait(0.5)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--preset", choices=PRESETS, required=True)
    parser.add_argument("--timeout", type=int, required=True)
    parser.add_argument("--cases")
    args = parser.parse_args()
    repo, evidence = Path("/workspace/repo"), Path("/workspace/evidence")
    repo.mkdir(parents=True, exist_ok=True)
    evidence.mkdir(exist_ok=True)
    start = time.monotonic()
    result = {"preset": args.preset, "steps": [], "platform": platform.platform(),
              "python": platform.python_version(), "cpu_count": os.cpu_count(),
              "renderer": "software requested; see glxinfo for observed renderer",
              "cache": "persistent Gradle cache; workspace/worlds are ephemeral"}
    cache = Path(os.environ.get("GRADLE_USER_HOME", "/cache/gradle"))
    result["cache_before"] = {"exists": cache.exists(), "entries": sorted(p.name for p in cache.iterdir()) if cache.exists() else []}
    result["meminfo"] = Path("/proc/meminfo").read_text()
    result["java_version"] = subprocess.run(["java", "-version"], capture_output=True, text=True).stderr.strip()
    measurements = {"scope": "Sandbox /proc RSS sum; shared pages counted per process; sampled every 0.5s"}
    stop = Event()
    monitor = Thread(target=sample_rss, args=(stop, measurements), daemon=True)
    monitor.start()
    code = 1
    try:
        request = json.loads(Path("/workspace/request.json").read_text())
        result["request"] = request
        commands = commands_for(args.preset, args.cases)
        if request["commands"] != commands:
            raise ValueError("Requested commands differ from bounded preset selection")
        for name, digest in request["inputs"].items():
            if sha256(Path("/workspace") / name) != digest:
                raise ValueError(f"Input hash mismatch: {name}")
        safe_extract(Path("/workspace/source.tar"), repo)
        if args.preset in ("release", "release-alchemy"):
            result["archive_git_baseline"] = initialize_archive_git(repo)
        for name in DEPENDENCIES:
            target = repo / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes((Path("/workspace") / name).read_bytes())
        os.chmod(repo / "gradlew", 0o755)
        for i, command in enumerate(commands):
            step = run_step(command, repo, evidence / f"step-{i}.log", args.timeout - (time.monotonic() - start))
            result["steps"].append(step)
            print(json.dumps(step), flush=True)
            code = step["exit_code"]
            if code:
                break
    except Exception as exc:
        code = 1
        result["error"] = f"{type(exc).__name__}: {exc}"
    finally:
        stop.set()
        monitor.join(timeout=2)
        classes = repo / "build/classes/java/main"
        if classes.is_dir():
            hashes = {path.relative_to(classes).as_posix(): sha256(path)
                      for path in sorted(classes.rglob("*.class"))}
            (evidence / "runtime_classes.json").write_text(json.dumps(hashes, indent=2) + "\n")
            result["compiled_main_classes"] = len(hashes)
        result["resources"] = measurements
        result["exit_code"] = code
        result["wall_seconds"] = round(time.monotonic() - start, 3)
        result["ended_utc"] = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
        for name in ("memory.peak", "memory.max", "cpu.max", "cpu.stat"):
            p = Path("/sys/fs/cgroup") / name
            result["cgroup_" + name] = p.read_text().strip() if p.exists() else None
        (evidence / "result.json").write_text(json.dumps(result, indent=2) + "\n")
        meta = package(repo, evidence, Path("/workspace/artifacts.tar.gz"))
        Path("/workspace/artifacts.json").write_text(json.dumps(meta))
    return code


if __name__ == "__main__":
    raise SystemExit(main())
