# Last Hope: Injuries and Wounds

Physical wounds for NeoForge 1.21.1, with optional Legendary Survival Overhaul (LSO) integration.

## Wounds

- Bruises, scratches, lacerations, deep lacerations, punctures, burns and bites can affect the head, chest, left/right arms, left/right legs and left/right feet. A bite is a physical wound; zombie infection belongs to The Hordes.
- A wound is rolled after actual health damage. Falls favor feet and legs, projectiles cause punctures, fire causes burns, explosions can bruise or cut, zombie attacks can bite, and sharp weapons favor cuts. Physiological and magical damage does not create a physical wound. When LSO assigns limb damage, its body part takes precedence over our location roll.
- The armor item on the affected region lowers wound risk based on its armor attribute and remaining durability. Armor may also reduce wound severity. This is separate from the vanilla reduction in incoming health damage and LSO's limb damage resistance.
- Bleeding drains a persistent blood reserve. Light wounds clot naturally, while deep cuts remain dangerous. Dressings slow bleeding, soak over time and become dirty. Exposure can contaminate wounds; infection slows healing. Food, LSO hydration and temperature, sleep and wound infection affect recovery.
- The client HUD shows bleeding, blood loss, pain and sickness moodles. Pain also reduces outgoing damage. LSO morphine suppresses pain while active.

## Treatment

Press H (remappable) to open or close the medical screen without pausing the game. Select a wound to see available treatments. Items may be anywhere in the player's inventory; the server validates and consumes them. Bandages, plaster, antiseptics, antibiotics, medkits and herbs include optional LSO items. This mod also supplies sutures for cleaned deep cuts.

The medical item tags live under `src/main/resources/data/lasthopeinjuries/tags/item`. Datapacks can add suitable items to those tags. The medical screen treats this mod's wounds; it does not directly heal LSO limb health.

## Development

Use JDK 21 and `./gradlew build`. The GitHub Actions workflow runs the build and GameTests on pushes. Operators can inspect and inject test wounds with `/wounds list` and `/wounds add <part> <type> <severity>`; `/wounds bandage <uuid>` and `/wounds clean <uuid>` also remain available.
