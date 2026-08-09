# Crossbow Plus

A client-side Fabric mod for Minecraft 26.1.x. While carrying a crossbow, hold
the use key to invoke the vanilla use action once per client tick. This matches
Tweakeroo's periodic use behavior with `periodicUseInterval` set to `0` and
intentionally reproduces the 26.1.x rapid-fire bug.

## Requirements

- Minecraft 26.1.x
- Fabric Loader 0.19.3 or newer
- Java 25 or newer

Fabric API is not required. Install the built jar in the client `mods` folder.

## Build

```powershell
.\gradlew.bat build
```

The distributable jar is written to `build/libs/crossbow-plus-1.1.0.jar`.
