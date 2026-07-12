# Lake's CC Additions

CC:Tweaked hardware for paperwork.

A ComputerCraft addon for Minecraft 1.20.1 (Forge). It adds a scanner that
turns physical items into Lua data and documents back into physical items,
along with tools for working with paper by hand.

## Features

- **Scanner** — reads printouts, books, maps, shulker boxes, Exposure
  photographs, and item NBT into Lua. Creates printouts from Lua, including
  greyscale photo prints. Fires events for automation and biometric scans.
- **Pen** — draw on printed pages by hand: signatures, form-filling,
  corrections. Drawings render in hand, in item frames, and on scanned data.
- **Stamp** — a customisable emblem, designed in-game and stamped onto pages.
- **Document Folder** — a portable container that holds only paperwork.
- **Pocket Multitool** — an ender modem and a speaker in a single pocket
  computer upgrade.

Full documentation: https://lakehouse.gg/additions

## Requirements

Minecraft 1.20.1, Forge, CC:Tweaked 1.119.0 or later. Exposure 1.9+ is
optional and enables photograph scanning.

Download: https://www.curseforge.com/minecraft/mc-mods/lakes-cc-additions

## Building

JDK 17 is required. For the optional Exposure integration,
`exposure-forge-1.20.1-1.9.21.jar` must be placed in `libs/` as
`exposure-1.9.21.jar` before the build will succeed; see BUILDING.md.

```
./gradlew build
```

The jar is produced in `build/libs/`.

## Repository layout

- `src/` — the mod
- `lua/` — companion ComputerCraft programs, not shipped in the jar

## License

MIT. The scanner block textures are derived from ComputerCraft's printer
art and remain under the ComputerCraft Public License; see LICENSE.

Version history: see CHANGELOG.md.
