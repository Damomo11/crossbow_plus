# Crossbow Plus

A client-side Fabric mod for Minecraft 26.1.x and 26.2. While carrying a
crossbow, hold the use key to invoke the vanilla use action once per client
tick. This matches Tweakeroo's periodic use behavior with
`periodicUseInterval` set to `0` and intentionally reproduces the 26.1.x
rapid-fire bug.

## Requirements

- Minecraft 26.1.x or 26.2
- Fabric Loader 0.19.5 or newer
- Java 25 or newer

Fabric API is not required. Install the built jar in the client `mods` folder.

## Configuration

Install [Mod Menu](https://modrinth.com/mod/modmenu) to open the Crossbow Plus
configuration screen. Mod Menu is optional and is not required for rapid fire.

- **Enable Crossbow Plus** enables or disables all features.
- **AxShulkers projectile refill** automatically takes projectiles from
  AxShulkers shulker boxes in the player inventory when the held crossbow runs
  out of ammunition. The shulker screen is handled in the background and is
  not displayed.

Projectile priority is fully positional, so it can be rearranged by moving items
around: shulker boxes are opened in visual inventory order (main inventory rows
left to right, top to bottom, then the hotbar), and inside each box the first
projectile in box slot order wins — whichever type it is. The first box being a
firework box makes fireworks the highest priority. Each refill takes one whole
container slot from the box (a full stack when available), regardless of the
stack size. Arrows are quick-moved into the inventory; firework rockets are
swapped onto the free hand, matching vanilla crossbow loading. Loose firework
rockets in the inventory are only used when no projectile shulker box remains.

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

The distributable jar is written to
`build/libs/crossbow-plus-1.0+mc<version>.jar`.
