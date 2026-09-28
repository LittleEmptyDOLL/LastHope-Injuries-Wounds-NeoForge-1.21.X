# Last Hope: Injuries and Wounds

NeoForge 1.21.1 mod, currently an early server-side implementation of persistent physical wounds.

## Implemented

- Eight body regions: head, chest, left/right arms, left/right legs and left/right feet. These correspond to LSO's `BodyPartEnum` names.
- Bruises, scratches, lacerations, deep lacerations, punctures, burns and bites. A bite here is a physical wound; zombie infection remains with The Hordes.
- Wounds are rolled after actual player health damage, including armor and absorption. Equipped armor in the corresponding slot reduces wound probability according to wound type. This is separate from LSO's limb damage resistance.
- Persistent wounds accumulate blood loss; bandaging reduces bleeding and cleaning reduces contamination. Dirty wounds can develop local infection. Wounds heal over time when infection is low. Blood loss causes periodic health damage and recovers after bleeding stops.
- Server API: `WoundService.add`, `bandage`, `clean` and `get`. State is serialized as a NeoForge entity data attachment and is cleared on death.
- Press H (remappable) to open the wound screen. The server sends the current state on opening and whenever it changes. Select a wound to treat it with an item held in either hand; the server validates the wound and item before consuming one item.

These mechanics are provisional balance values. The screen displays this mod's wounds only; LSO limb health and Moodles are not yet displayed.

Treatment items are configured through the item tags `lasthopeinjuries:bandages` and `lasthopeinjuries:antiseptics`. The bandage tag optionally includes LSO's `bandage` and `plaster` IDs. The antiseptic tag starts empty and can be populated by a datapack. Treatment through this screen affects the wound; it does not yet trigger LSO limb healing.

## Development commands

Operators can run `/wounds list`, `/wounds add <part> <type> <severity>`, `/wounds bandage <uuid>` and `/wounds clean <uuid>`. The commands are for testing the mechanics until the treatment interface is implemented. Parts and wound types use the uppercase enum names in the code; input is case insensitive.

## LSO compatibility

The `1.21.1` branch of [LegendarySurvivalOverhaul](https://github.com/sfiomn/LegendarySurvivalOverhaul/tree/1.21.1) currently declares Minecraft 1.20.1 and Forge 47.4.0 in `gradle.properties`. This repository therefore does not compile against that branch. Once a compatible NeoForge 1.21.1 build is available, the planned integration will connect LSO medical items and body health to a combined medical screen, while keeping this wound data and Moodles independent.

## Build

Use JDK 21 and `./gradlew build`. The GitHub Actions workflow also runs this build on pushes.
