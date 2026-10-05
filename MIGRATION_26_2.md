# MCTRPG Rebuild — Paper 26.2 migration

- Target: Minecraft/Paper 26.2
- Java: 25
- `plugin.yml` api-version: 26.2
- Maven build retained for existing GitHub Actions; Gradle Kotlin DSL added for current Paper development.
- Dreamlands managers, teleport command logic, world generator, mobs/moon integration, and Dreamlands-only artifact were removed.
- Ordinary SURVIVAL/ADVENTURE handling used by the generic TRPG engine remains; it is not the removed scenario-specific Survival mode.
- Phase 1 systems (10-second skill cooldown, dice animation/visibility, KP tool/editor foundation) remain in place.

## Build
Use JDK 25. GitHub Actions is configured for JDK 25.

Maven: `mvn -B clean package`
Gradle: `gradle build`
