---
navigation:
  title: Initiation
  parent: index.md
  position: 10
  icon: myvillage:spirit_testing_stele
item_ids:
  - myvillage:spirit_testing_stele
  - myvillage:technique_inheritance_stele
---

# Initiation

Spirit testing and technique inheritance are separate interactions. Right-click the two steles separately, in order.

<Row>
  <BlockImage id="myvillage:spirit_testing_stele" scale="1.5" />
  <BlockImage id="myvillage:technique_inheritance_stele" scale="1.5" />
</Row>

## Step One: Awaken Your Spiritual Root

Right-click the <ItemLink id="myvillage:spirit_testing_stele" />. For an unawakened mortal, the server generates a deterministic spiritual root and advances the profile to Qi-Sensing Mortal. The result remains the same while the Overworld seed, player UUID, eligible element definitions, and algorithm version remain unchanged.

Repeating spirit testing **does not reroll, overwrite, or wash an existing root**. It also does not inherit a technique or grant progress, stability, spiritual power, or mastery.

## Step Two: Inherit Basic Breathing

After awakening, right-click the <ItemLink id="myvillage:technique_inheritance_stele" />. If the current definition requirements are met, you learn Basic Breathing at mastery `0`.

Repeating inheritance only reports that the technique is already learned; it **does not reset existing mastery to `0`**. Inheritance itself does not execute the technique, grant progress, or advance a stage.

The two steles are currently available only from the creative inventory or `/give`. They have no recipes, natural generation, or sect-structure placement.

Once both steps are complete, continue to the [Cultivation Loop](./cultivation_loop.md).
