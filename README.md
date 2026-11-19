# Mod List

A server-side Fabric mod for Minecraft 26.3 that lets operators inspect every loaded Fabric mod and enabled datapack.

## Commands

- `/mods` shows the compact, comma-separated list and excludes nested/JIJ mod dependencies.
- `/modlist` shows the complete mod and datapack lists as ASCII directory trees. Nested/JIJ mods appear beneath their containing mod in gray, and datapacks supplied by mods appear beneath a gray `Fabric Mod` node.

Both commands require Minecraft command permission level 2 (`gamemasters`), the standard operator-only level. Hover a mod or datapack name to see its metadata.

## Build

Java 25 or newer is required.

```sh
./gradlew build
```

The remapped server mod is written to `build/libs/modlist-1.0.0.jar`.
