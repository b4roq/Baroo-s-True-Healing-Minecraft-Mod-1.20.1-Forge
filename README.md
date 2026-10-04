# Baroo's True Healing
Project Zomboid-style healing for Forge 1.20.1 (mod id `truehealing`).
Press H (rebindable), click the + in your inventory, or right-click any medical item.

## Injuries (one per limb; a worse injury replaces a milder one)
- Scratch: never bleeds, heals in 5 min (bandage or rag optional). Can still get infected.
- Laceration: bleeds until covered; a bandage or rag stops the damage (it seeps into the cloth). Heals in 15 min, 10 if stitched.
- Deep wound: bleeds out; a bandage only slows it. Stitch it. Heals in 15 min no matter what.
- Fracture (legs, from falls of 7+ blocks): slows you down; splint it.
- A dressing left seeping for 4 minutes turns dirty (more infection risk) but still works.

## Items
Rag, Rag (Dirty), Bandage, Bandage (Dirty), Bandaid, Alcohol Wipes, Suture Needle, Splint, Antibiotics.
Wash a dirty rag or bandage with a water bottle in the crafting grid (the bottle comes back empty).

## Moodles
Nourishment, Panic, Bleeding, Injured, Pain, Restricted movement. Shown on the right side of the screen,
beside the inventory, and in the medical screen. Config: config/truehealing-client.toml
(moodleOffsetY, moodleOffsetX, moodleScale, moodlesEnabled).

## Commands (op)
/truehealing wound <head|torso|left_arm|right_arm|left_leg|right_leg> <scratch|laceration|deep_wound|fracture>
/truehealing infect
/truehealing clear

## Config
config/truehealing-common.toml
