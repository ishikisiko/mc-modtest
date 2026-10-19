"""Own the tiger's egg, empty loot, bilingual names and vanilla-audio cue aliases."""
from __future__ import annotations

import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
CUES = {
    "ambient": ("entity.ravager.ambient", "山君低吼", "Thunder Tiger growls"),
    "hurt": ("entity.ravager.hurt", "山君受伤", "Thunder Tiger hurts"),
    "death": ("entity.ravager.death", "山君倒下", "Thunder Tiger falls"),
    "attack": ("entity.ravager.attack", "山君扑杀", "Thunder Tiger attacks"),
    "charge": ("entity.warden.sonic_charge", "雷角蓄电", "Thunder horn charges"),
    "thunder": ("entity.lightning_bolt.thunder", "山君落雷", "Thunder Tiger calls lightning"),
    "roar": ("entity.ravager.roar", "山君咆哮", "Thunder Tiger roars"),
    "horn_break": ("block.anvil.break", "雷角断裂", "Thunder horn shatters"),
    "step": ("entity.ravager.step", "山君踏地", "Thunder Tiger stomps"),
}
GUIDE = {
    "hint": ("[山君传闻] 了解雷角山君与破角之法", "[Tiger rumours] Learn about the Thunderhorn Tiger"),
    "title": ("雷角山君 · %s：建议准备护甲、疗伤手段和可用身法后挑战。", "Thunderhorn Tiger · %s: prepare armour, healing and a movement technique."),
    "where": ("在主世界山地与草甸寻找开阔石谷。进入巢穴深处会惊动山君；旧世界需探索尚未生成的区块。", "Seek an open rocky valley in Overworld mountains or meadows. Entering its lair awakens the tiger; existing worlds need new chunks."),
    "combat": ("抬肩将扫爪，伏低将扑击，雷纹亮起后避开落雷范围。躲过重招，再趁收招反击。", "A raised shoulder warns of claws; a crouch warns of a pounce. Leave glowing thunder marks, then punish the recovery."),
    "horn": ("诱导扑击撞向巢穴石柱，趁山君低头失衡攻击头部。打身体不能破角；断角会压制其雷法。", "Lure a pounce into the lair pillars, then strike the lowered head. Body hits cannot break the horn; breaking it suppresses lightning."),
    "phase": ("受重创后山君狂暴，扑击落点会追加延迟雷击。侧闪后先看清雷痕，再靠近。", "When badly wounded, the tiger enrages and leaves delayed lightning after its pounce. Watch the marks before closing in."),
    "retreat": ("可以撤出巢穴再来。正常退出与重启会保留山君伤势和断角；已击杀的巢穴不会重新生成山君。", "You may retreat and return. Normal saves preserve wounds and the broken horn; a cleared lair will not respawn its tiger."),
    "reward": ("本轮暂未配置山君掉落奖励。", "This encounter currently has no configured loot rewards."),
}


def outputs():
    result = {
        RES / "assets/myvillage/models/item/thunder_tiger_spawn_egg.json": {
            "parent": "minecraft:item/template_spawn_egg"},
        RES / "data/myvillage/loot_table/entities/thunder_tiger.json": {
            "type": "minecraft:entity", "pools": []},
    }
    for lang, name, egg in (
        ("zh_cn", "雷角山君", "雷角山君刷怪蛋"),
        ("en_us", "Thunderhorn Tiger", "Thunderhorn Tiger Spawn Egg"),
    ):
        path = RES / f"assets/myvillage/lang/{lang}.json"
        doc = json.loads(path.read_text(encoding="utf-8"))
        doc["entity.myvillage.thunder_tiger"] = name
        doc["entity.myvillage.thunder_tiger.bossbar"] = "%1$s · %2$s · %3$s · %4$s"
        for key, zh, en in (
            ("phase_one", "一阶段", "Phase I"), ("phase_two", "二阶段·狂怒", "Phase II"),
            ("horn_intact", "雷角完整", "Horn intact"), ("horn_broken", "雷角已断", "Horn broken"),
            ("seal", "煞", "!")
        ):
            doc["hud.myvillage.boss." + key] = zh if lang == "zh_cn" else en
        doc["item.myvillage.thunder_tiger_spawn_egg"] = egg
        doc["block.myvillage.thunder_tiger_lair_anchor"] = (
            "山君巢穴基石" if lang == "zh_cn" else "Thunderhorn Lair Anchor")
        doc["structure.myvillage.thunder_tiger_lair"] = (
            "雷角山君洞府" if lang == "zh_cn" else "Thunderhorn Tiger Lair")
        for cue, (_, zh, en) in CUES.items():
            doc[f"subtitles.myvillage.thunder_tiger.{cue}"] = zh if lang == "zh_cn" else en
        for topic, (zh, en) in GUIDE.items():
            doc[f"exploration.myvillage.tiger.{topic}"] = zh if lang == "zh_cn" else en
        result[path] = doc
    path = RES / "assets/myvillage/sounds.json"
    sounds = json.loads(path.read_text(encoding="utf-8"))
    for cue, (event, _, _) in CUES.items():
        sounds[f"thunder_tiger.{cue}"] = {
            "subtitle": f"subtitles.myvillage.thunder_tiger.{cue}",
            "sounds": [{"name": f"minecraft:{event}", "type": "event"}],
        }
    result[path] = sounds
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    stale = []
    for path, doc in outputs().items():
        data = json.dumps(doc, ensure_ascii=False, indent=2) + "\n"
        if path.exists() and path.read_text(encoding="utf-8") == data:
            continue
        if args.check:
            stale.append(str(path.relative_to(ROOT)))
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(data, encoding="utf-8")
    if stale:
        raise SystemExit("stale tiger support: " + ", ".join(stale))
    print("thunder_tiger support: up to date" if args.check else "thunder_tiger support: generated")


if __name__ == "__main__":
    main()
