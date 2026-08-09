# Crossbow Plus

A client-side Fabric mod for Minecraft 1.21.11. While carrying a crossbow, hold
the use key to invoke the vanilla use action once per client tick. This matches
Tweakeroo's periodic use behavior with `periodicUseInterval` set to `0` and
intentionally reproduces the 1.21.11 rapid-fire bug.

## Requirements

- Minecraft 1.21.11
- Fabric Loader 0.19.3 or newer
- Java 21 or newer

Fabric API is not required. Install the built jar in the client `mods` folder.

## Configuration

Install [Mod Menu](https://modrinth.com/mod/modmenu) to open the Crossbow Plus
configuration screen. Mod Menu is optional and is not required for rapid fire.

- **Enable Crossbow Plus** enables or disables all features.
- **AxShulkers arrow refill** automatically takes one stack of arrows from an
  AxShulkers shulker box in the player inventory when the held crossbow runs out
  of ammunition. The shulker screen is handled in the background and is not
  displayed.
- **Take one arrow per shot** changes AxShulkers refill to take exactly one
  arrow for each shot instead of moving a whole stack. It is disabled by
  default, so stack refill remains the default behavior. One empty inventory
  slot is required temporarily while the arrow is used.

AxShulkers refill requires the server to run
[AxShulkers](https://github.com/Artillex-Studios/AxShulkers) with
`opening-from-inventory.enabled` enabled. The player must have the
`axshulkers.use` and `axshulkers.modify` permissions. AxShulkers does not expose
a client API, so this compatibility uses its standard inventory right-click and
quick-move behavior.

## Build

```powershell
.\gradlew.bat build
```

The distributable jar is written to `build/libs/crossbow-plus-1.0.jar`.
