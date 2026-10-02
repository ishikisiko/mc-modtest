## ADDED Requirements

### Requirement: Lingxiao Spear item
The mod SHALL register `myvillage:lingxiao_spear` (en_us `Lingxiao Spear`, zh_cn `凌霄枪`) as a `CombatWeaponItem` on `Tiers.DIAMOND` with attack bonus 4 and attack speed −2.8, in the `#minecraft:swords` item tag, and in the `myvillage:main` creative tab directly after the four MyVillage swords. It SHALL have no recipe. In vanilla combat mode it SHALL behave as an ordinary diamond-tier sword item.

#### Scenario: Tooltip shows spear attributes
- **WHEN** a player obtains the spear with `/give @s myvillage:lingxiao_spear`
- **THEN** its tooltip shows 8 attack damage and 1.2 attack speed

#### Scenario: Creative tab order
- **WHEN** the `myvillage:main` tab is opened
- **THEN** the spear appears after `myvillage:qingxiao_liuyun_sword` and before `myvillage:low_grade_spirit_stone`

### Requirement: Generated spear model and geometry contract
`tools/gen_lingxiao_spear_model.py` SHALL be the only writer of the spear's `separate_transforms` item model, 3D element model, model texture, inventory icon, and geometry contract, and `--check` SHALL fail when any of them differs from its output or a self-check fails. The model SHALL run along +Y on the contract frame from `y = -16` to `y = 32`, the inventory perspective SHALL keep the 2D icon, the head and inlays SHALL be fullbright through element light data, and no element other than bare shaft SHALL lie in either hand's grip zone. The contract SHALL give `grip_center`, `off_hand_grip_center` ahead of it on the shaft, and a `trail` span.

#### Scenario: Hand edit of a generated file is caught
- **WHEN** the committed spear contract is edited by hand
- **THEN** `python3 tools/gen_lingxiao_spear_model.py --check` exits non-zero

#### Scenario: Left-hand display is not pre-mirrored
- **WHEN** the generator writes the third- and first-person left-hand display entries
- **THEN** each equals the matching right-hand entry, because the game mirrors the left hand when it applies the transform

### Requirement: Basic spear style
The bundled style `myvillage:basic_spear` SHALL define, in combo order, `basic_spear_01_mid_thrust` (thrust), `basic_spear_02_sweep` (cut), `basic_spear_03_rising_flick` (cut), `basic_spear_04_overhead_smash` (cut), and `basic_spear_05_dragon_lunge` (thrust, cannot chain), with translations in both languages and the existing combat sound events. The thrusts SHALL use the `thrust` generator; the sweep, flick, and smash SHALL use explicit samples with the same number per active tick (three for the sweep, four for the flick and smash), whose far ends reach at least the spear's world-trail tip radius. The weapon file SHALL bind `myvillage:lingxiao_spear` to this style, its first-person rig, and its geometry contract. Adding the style and weapon SHALL need no change to `combat/session`, `combat/runtime`, `combat/network`, or the payload protocol.

#### Scenario: Combo runs through a real session
- **WHEN** a `CombatSession` for `myvillage:basic_spear` receives an intent and then one buffered intent per move
- **THEN** it starts the five moves in order, chains moves one to four at their chain ticks, and restarts at move one only after the finisher's full duration

#### Scenario: Held spear selects the spear style
- **WHEN** a player in cultivation mode holding the spear sends an attack intent
- **THEN** the server starts `myvillage:basic_spear_01_mid_thrust`

### Requirement: Two-handed third-person poses
`player_animations/spear_combat.json` SHALL be generated from the `myvillage:basic_spear` pose table, which uses the spear's contract and 3D model and is marked two-handed. In the guard, ready idle, mode entry, and mid thrust the left hand SHALL be solved onto the shaft; the sweep, flick, smash, and lunge MAY release it for the swing and SHALL regrip it in recovery. The generator SHALL fail when an on-shaft key leaves the left fist off the shaft beyond its tolerance, when the weapon model's lowest corner goes below the ground clearance, or when the shaft enters the body, neck, or skin layers.

#### Scenario: Unreachable shaft fails the check
- **WHEN** a spear key asks for the left hand on the shaft at a pose where the rigid left arm cannot reach it
- **THEN** `python3 tools/gen_sword_pal_anims.py --check` reports the animation, the tick, and the left-hand offset

### Requirement: Two-handed first-person presentation
The spear's first-person rig SHALL declare `rig.off_hand`, so that with an empty off-hand slot the first-person view draws the left arm with its hand on the shaft. The thrust, sweep, and flick SHALL keep both hands on the shaft; the smash and lunge MAY release the off hand and SHALL regrip it before the move ends. Every move's `strike` window SHALL cover its active ticks within three ticks.

#### Scenario: Off-hand item hides the second arm
- **WHEN** the player holds the spear in the main hand and any item in the off hand
- **THEN** the first-person view draws the main arm and the vanilla off-hand item, and no second arm on the shaft

### Requirement: Spear evidence boundary
Capture sets and offline previews of the spear SHALL be recorded as developer evidence. Sound, real keyboard play, a second client, and every owner verdict on the spear SHALL stay `not_verified` until observed, and no document SHALL infer an owner verdict from developer evidence.

#### Scenario: Ledger keeps unobserved items open
- **WHEN** the README real-client ledger lists a spear surface that no capture or client observed
- **THEN** its result is `not_verified`
