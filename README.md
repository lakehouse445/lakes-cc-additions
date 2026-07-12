# Lake's CC Additions

CC:Tweaked hardware for paperwork.

A ComputerCraft addon for Minecraft 1.20.1 (Forge): a scanner block that
reads printouts, books, maps, shulker boxes, Exposure photographs, and raw
NBT into Lua -- and writes documents back, per-character colours included.
Plus a pen for drawing on printed pages by hand, a customisable stamp, a
document folder, and a pocket upgrade combining an ender modem with a
speaker.

- **Download:** https://www.curseforge.com/minecraft/mc-mods/lakes-cc-additions
- **Manual:** https://lakehouse.gg/additions

## Building

Requires JDK 17. Drop `exposure-forge-1.20.1-1.9.21.jar` into `libs/`
(renamed to `exposure-1.9.21.jar`) for the optional photo integration --
see BUILDING.md for details.

```
./gradlew build
```

The jar lands in `build/libs/`.

## Repository layout

- `src/main/java` -- the mod
- `src/main/resources` -- textures, models, recipes, lang
- `lua/` -- companion ComputerCraft programs. These are NOT shipped in the
  jar; they are reference programs for servers that want them.

## License

MIT, with one exception: the scanner block textures are derived from
ComputerCraft's printer art under the ComputerCraft Public License. See
LICENSE for the full text of both.

## Changelog

- **1.3.1** — Stamp editor layout: palette is now a 2x2 grid of ink-on-paper
  chips (dark ink is visible on a dark panel again; the blank chip reads as
  the eraser), and the sidebar has proper spacing -- the preview no longer
  sits on the Clear button.

- **1.3.0** — Item frame ink FIXED: CC:T mutates the frame event's pose stack
  without popping, so the stack arrives already in page space; we now draw
  directly instead of double-transforming into the void. Glow frames light
  correctly. THE STAMP: craft (iron ingot / stick / ink sac), use alone to
  open the emblem editor -- 32x32 zoomed grid, four-ink palette, mirror
  symmetry, undo, live 1:1 paper preview -- then use with a page in the other
  hand to place: a ghost follows the cursor, click stamps (shift-click to
  repeat), merging the emblem into the page's ink layer, so stamps render
  everywhere drawings do and scan like drawings. Updated pen and folder
  sprites (community edition).

- **1.2.2** — Pen editor polish: per-stroke Undo (button + Ctrl+Z, 24 strokes
  per page) and a properly spaced, centered button row -- Erase and Done no
  longer share a chair. Button row sits lower on books to clear the cover.

- **1.2.1** — Pen fixes. The editor renders the page correctly (background is
  pushed behind the paper, matching PrintoutScreen; immediate buffer instead
  of the GUI buffer source). The drawable canvas now covers the FULL page,
  172x209, margins included -- note the text area begins at pixel (13, 11), so
  Lua checkbox math should offset accordingly. Ink now renders on printouts
  held in hand (first person, both grips, mid-swing) and in item frames, by
  extending CC:T's ItemMapLikeRenderer and listening after its render events.
  Old 150x189 test drawings are dropped on load (size mismatch, by design).

- **1.2.0** — THE PEN. Hold a printed page in one hand, use the pen with the
  other, and draw on the page by hand: a 150x189 ink layer per page (the true
  font-pixel grid -- 1px and 2px brushes, finer than the 2x3 characters), in
  dark, red, or blue ink, with an eraser and multi-page support. Drawings
  render above the printed text in the pen editor AND in CC:T's own printout
  view screen, and are stored on the item, so drawn pages stay drawn.
  scanPrintout() now returns a per-page "drawing" table (rows of 0-3) and
  createPrintout() accepts one, so computers can read handwriting off forms
  (checkbox detection, quiz grading) and photocopies preserve ink.
  Crafted: iron nugget / stick / ink sac.

- **1.1.0** — THE DOCUMENT FOLDER. A portable, unplaceable shulker-for-paperwork:
  27 slots, accepts ONLY CC printouts and plain paper, 16 per slot, crafted
  from leather/string/leather rows. Single-chest GUI in folder brown. Hover
  tooltip lists the first document titles and total count. Texture switches
  between empty and full automatically. Scanner integration: scanFolder()
  lists contents with types, titles, and per-document fingerprints;
  scanPrintout(slot) reads a document INSIDE a folder without removing it;
  createPrintout files new documents INTO a held folder (first free slot).
  Mass archive: loop scanFolder + scanPrintout(slot). Mass photocopy: scan
  folder A, swap in folder B, loop createPrintout.

- **1.0.2** — Version metadata repair: the same environment reset that hit the
  code also froze mods.toml at 0.5.0 (chained version bumps silently no-oped).
  Now stamped 1.0.2 with hard verification. Exposure dependency range
  corrected to [1.9,) to match the 1.9-backport API the code targets.

- **1.0.1** — Repair release. The 1.0.0 package accidentally shipped an old
  pre-1.9-backport ExposurePhotoScanner and a matching outdated peripheral
  Exposure section, and was missing developphoto.lua, previewphoto.lua, and
  the printphoto overhaul. All rebuilt and verified: Exposure 1.9 API, full
  prepress pipeline (auto-contrast, sharpen, atkinson, tones, poster mode),
  createPrintout restored with its sound. No new features.

- **1.0.0** — Release polish. Unique block textures: the scanner is now an
  "advanced printer" -- CC:T's printer shape remapped onto the advanced
  (gold) palette, a block CC:T never made (derived under CCPL, see
  textures/block/LICENSE.txt). Original Pocket Multitool icon (gold body,
  ender core, speaker grille). Item tooltips on both items. createPrintout
  plays a sound. citizenid secret generation now seeds from the clock.
  New lakescan program: suite overview. README/API docs completed.

- **0.9.0** — INVISIBLE SEALS. bioseal.lua embeds data in the colour channel
  of space characters: spaces render zero pixels regardless of colour, so
  sealed lines are visually blank paper carrying 4 bits/char, undetectable by
  eye or tooltip, readable by any scanner (magic + length + payload + xor
  checksum, packed bottom-up). New sha256(text) peripheral method for Lua
  signatures. citizenid.lua: full identity system -- setup creates a shared
  government secret, issue registers a citizen via biometric hand scan and
  prints an ID whose face shows NO number (identity + signature sealed
  invisibly), verify checks seal integrity, government signature, AND a live
  hand scan against the paper. Stolen IDs fail biometrics; forged IDs fail
  the signature.

- **0.8.0** — scanContainer(): read shulker box (and any BlockEntityTag.Items
  container) contents without opening -- slot, item, count, hasNbt, and nested
  container detection. Customs and contraband checkpoints are now a mechanic.
  BIOMETRICS: sneak + empty hand on the scanner fires a "scanner_biometric"
  event (side, playerName, playerUUID) on attached computers -- identity
  terminals, fingerprint locks, clock-in stations, no ID cards needed.
  New fax.lua: scan a printout on one scanner, it physically materializes in
  another over rednet (fax host NAME / fax send NAME / fax list).

- **0.5.0** — Exposure mod integration (soft dependency): scanPhoto (metadata:
  photographer, film type, camera settings), scanPhotoForMonitor (quantised
  16-colour blit rows), and scanPhotoForPrint (Floyd-Steinberg dithered 1-bit
  image packed into 2x3 teletext characters, sized to one printed page).
  Supports photograph, aged_photograph, and stacked_photographs (with index).
  New printphoto.lua: photograph in scanner -> dithered image on paper.
  Exposure is OPTIONAL at runtime; methods error politely if not installed.
  Compile-time dependency setup is in BUILDING.md.

- **0.4.1** — Ready-made "Deluxe Pocket Computer" (advanced pocket + multitool
  pre-installed) added to the Lake's CC Additions creative tab. Grab and go.

- **0.4.0** — Rebranded to "Lake's CC Additions" (mod id stays `lakescanner` so
  existing worlds, scripts, and recipes keep working). New: the Pocket Multitool,
  a pocket upgrade combining an ENDER MODEM and a SPEAKER in one upgrade slot.
  Craft it from an ender modem + speaker (shapeless), then craft it above any
  pocket computer to get a "Deluxe Pocket Computer". The peripheral answers to
  both peripheral.find("modem") and peripheral.find("speaker"); rednet works as
  normal. Item light glows blue while playing sound, red while a channel is open.
  IMPORTANT: build.gradle dependency change required (see below).

- **0.3.0** — Disk-drive style inventory GUI (reuses CC:T's GUI texture); hopper
  automation works free via Container. New peripheral methods: scanNBT (full NBT
  of any item as a Lua table), scanBook (book & quill / written books),
  fingerprint (SHA-256 of item id+NBT for authenticity checks), scanMapTile
  (fixed shared palette + grid coords for multi-map atlases). "scanner" event
  fires on attached computers when the slot's contents change. Crafting recipe
  added. Dedicated creative tab. New mapatlas.lua program: scan maps to files,
  auto-arrange by world grid, render mosaic on any monitor wall.

- **0.2.0** — Scanner block now has a horizontal `facing` property (faces you on
  placement, like a furnace; supports rotate/mirror for WorldEdit and structure
  blocks). Full resources tree included: mods.toml, pack.mcmeta, blockstate with
  facing variants, models referencing CC:T printer textures, lang, loot table.
  See BUILDING.md for a from-zero build tutorial.
- **0.1.0** — Initial version: scanPrintout, scanMap, scanMapPixels,
  scanMapForMonitor, printview.lua, mapview.lua.

## Where everything goes

Merge this zip's `src/` into a Forge 1.20.1 MDK project root (see BUILDING.md):

```
<MDK project root>/
├── build.gradle                <- MDK's own; add CC:T maven + deps (BUILDING.md step 3)
├── gradle.properties           <- MDK's own; set mod_version=0.2.0
├── gradlew / gradlew.bat       <- MDK's own; this is your build command
└── src/main/
    ├── java/gg/lakehouse/scanner/
    │   ├── ScannerMod.java             (mod entrypoint + peripheral capability wiring)
    │   ├── ScannerRegistry.java        (block/item/BE/menu/creative tab registration)
    │   ├── ScannerBlock.java           (facing, right-click opens GUI)
    │   ├── ScannerBlockEntity.java     (Container + MenuProvider + computer events)
    │   ├── ScannerMenu.java            (disk-drive style single-slot menu)
    │   ├── ScannerPeripheral.java      (the Lua-facing API)
    │   └── client/
    │       ├── ScannerClient.java      (screen registration, client only)
    │       └── ScannerScreen.java      (GUI, reuses CC:T disk drive texture)
    └── resources/
        ├── META-INF/mods.toml          (mod metadata, version, CC:T dependency)
        ├── pack.mcmeta
        ├── assets/lakescanner/
        │   ├── blockstates/scanner.json        (4 facing variants)
        │   ├── models/block/scanner.json       (reuses CC:T printer textures)
        │   ├── models/item/scanner.json
        │   └── lang/en_us.json
        └── data/lakescanner/
            ├── loot_tables/blocks/scanner.json (block drops itself)
            └── recipes/scanner.json            (spyglass/glass/redstone/stone)
```

The `lua/` folder is NOT part of the jar — copy `printview.lua` and `mapview.lua`
onto in-game computers (disk, pastebin, wget from your file host, etc.).

No textures need creating: the model JSON references CC:T's printer textures
cross-namespace, which load from CC:T's jar at runtime. If you want the scanner
to look distinct, copy printer_top.png / printer_front_empty.png from CC:T into
assets/lakescanner/textures/block/, recolor them, and point the model at
lakescanner:block/... instead (those files are CCPL — credit Daniel Ratcliffe,
keep the mod non-commercial and source-available).

## In-game usage

Right-click the scanner with an item to place it on the bed; right-click empty-handed
to take it back. Attach a computer (directly or via wired modem).

## Lua API

| Method | Returns |
|---|---|
| `hasItem()` | boolean |
| `getItem()` | `{ name, count, displayName, damage?, maxDamage? }` or nil |
| `scanNBT()` | `{ name, count, nbt? }` — full NBT as nested Lua tables |
| `fingerprint()` | SHA-256 hex of item id + NBT (count excluded) — authenticity checks |
| `scanPrintout(folderSlot?)` | `{ type, title, pageCount, width, height, pages = { { text, color } } }` |
| `scanBook()` | `{ type = "writable"/"written", pageCount, pages, title?, author?, generation? }` |
| `sha256(text)` | SHA-256 hex of a string (signatures, seals) |
| `createPrintout(pages, title?)` | CREATE a printout in the empty slot, per-char colours supported |
| `scanFolder()` | folder contents: slot, name, count, type, title?, fingerprint per document |
| `scanContainer()` | shulker/container item contents: slot, name, count, hasNbt, nested |
| `scanMap()` | `{ centerX, centerZ, scale, locked, dimension, decorations }` |
| `scanMapPixels()` | `{ width, height, rows }` — raw packed colour bytes |
| `scanMapForMonitor()` | per-map best palette + blit rows (single-map display) |
| `scanPhoto(index?)` | Exposure photo metadata: dimensions, film type, photographer, settings |
| `scanPhotoForMonitor(index?)` | quantised palette + blit rows of the photo |
| `scanPhotoForPrint(index?)` | dithered teletext lines sized for one printed page |
| `scanMapTile()` | FIXED shared palette + blit rows + `gridX`/`gridZ` (multi-map atlases) |

**Events:** sneak + empty hand fires `scanner_biometric` -- `event, side, playerName, playerUUID` -- turning any scanner into an identity terminal. Attached computers also receive `scanner` events — `os.pullEvent("scanner")`
returns `event, side, hasItem` whenever the slot's contents change (player, hopper,
or GUI). Build reactive kiosks: insert a document, the computer scans it instantly.

**Automation:** the scanner implements Container, so hoppers can push items in and
pull them out. Hopper in the top, hopper out the side, computer scanning on the
"scanner" event = automatic document digitization line.

## Where everything goes

Merge this zip's `src/` into a Forge 1.20.1 MDK project root (see BUILDING.md):

```
<MDK project root>/
├── build.gradle                <- MDK's own; add CC:T maven + deps (BUILDING.md step 3)
├── gradle.properties           <- MDK's own; set mod_version=0.2.0
├── gradlew / gradlew.bat       <- MDK's own; this is your build command
└── src/main/
    ├── java/gg/lakehouse/scanner/
    │   ├── ScannerMod.java             (mod entrypoint + peripheral capability wiring)
    │   ├── ScannerRegistry.java        (block/item/BE/menu/creative tab registration)
    │   ├── ScannerBlock.java           (facing, right-click opens GUI)
    │   ├── ScannerBlockEntity.java     (Container + MenuProvider + computer events)
    │   ├── ScannerMenu.java            (disk-drive style single-slot menu)
    │   ├── ScannerPeripheral.java      (the Lua-facing API)
    │   └── client/
    │       ├── ScannerClient.java      (screen registration, client only)
    │       └── ScannerScreen.java      (GUI, reuses CC:T disk drive texture)
    └── resources/
        ├── META-INF/mods.toml          (mod metadata, version, CC:T dependency)
        ├── pack.mcmeta
        ├── assets/lakescanner/
        │   ├── blockstates/scanner.json        (4 facing variants)
        │   ├── models/block/scanner.json       (reuses CC:T printer textures)
        │   ├── models/item/scanner.json
        │   └── lang/en_us.json
        └── data/lakescanner/
            ├── loot_tables/blocks/scanner.json (block drops itself)
            └── recipes/scanner.json            (spyglass/glass/redstone/stone)
```

The `lua/` folder is NOT part of the jar — copy `printview.lua` and `mapview.lua`
onto in-game computers (disk, pastebin, wget from your file host, etc.).

No textures need creating: the model JSON references CC:T's printer textures
cross-namespace, which load from CC:T's jar at runtime. If you want the scanner
to look distinct, copy printer_top.png / printer_front_empty.png from CC:T into
assets/lakescanner/textures/block/, recolor them, and point the model at
lakescanner:block/... instead (those files are CCPL — credit Daniel Ratcliffe,
keep the mod non-commercial and source-available).

## In-game usage

Right-click the scanner with an item to place it on the bed; right-click empty-handed
to take it back. Attach a computer (directly or via wired modem).

## Lua API

| Method | Returns |
|---|---|
| `hasItem()` | boolean |
| `getItem()` | `{ name, count, displayName }` or nil |
| `scanPrintout(folderSlot?)` | `{ type, title, pageCount, width, height, pages = { { text = {21 lines}, color = {21 blit strings} } } }` |
| `sha256(text)` | SHA-256 hex of a string (signatures, seals) |
| `createPrintout(pages, title?)` | CREATE a printout in the empty slot, per-char colours supported |
| `scanFolder()` | folder contents: slot, name, count, type, title?, fingerprint per document |
| `scanContainer()` | shulker/container item contents: slot, name, count, hasNbt, nested |
| `scanMap()` | `{ centerX, centerZ, scale, locked, dimension, decorations = { { type, x, y, rotation, name? } } }` |
| `scanMapPixels()` | `{ width, height, rows }` — 128 binary strings of raw packed colour bytes (`string.byte` them; value = colourId * 4 + brightness) |
| `scanMapForMonitor()` | `{ width, height, palette = {16 RGB ints}, rows = {128 blit colour strings} }` |

Text/colour lines from `scanPrintout()` are padded to exactly 25 chars and are directly
`term.blit`-compatible. Redraw a page pixel-perfect with:

```lua
for i = 1, doc.height do
    term.setCursorPos(x, y + i - 1)
    term.blit(page.text[i], page.color[i], string.rep("0", doc.width))
end
```

`lua/printview.lua` and `lua/mapview.lua` are ready-to-use client programs.

## Notes and gotchas

- All scan methods run with `mainThread = true` since they touch world/item state.
- `scanMap()` errors gracefully if the map data isn't loaded (unexplored map, or a map
  from another world). Decoration accessor names (`getType`/`getX`/`getRot`/`getName`)
  are from 1.20.1 mappings; if your IDE complains, check `MapDecoration` in your
  mappings set — they occasionally differ between official/Parchment.
- Printout NBT (`Title`, `Pages`, `Text0..N`, `Color0..N`) is read directly off the
  ItemStack, so nothing outside `dan200.computercraft.api` is touched — safe across
  CC:T updates per their addon guidelines.
- The colour strings CC:T stores are already blit format: text colour per character.
  A printed page is 25x21; `printed_pages` and `printed_book` just have more pages.
- `scanMapForMonitor()` uses popularity quantisation (top 16 colours + nearest match).
  Terrain maps look good; maps with many gradient colours will posterize a bit. If you
  want nicer results later, swap in median-cut in `ScannerPeripheral.scanMapForMonitor`.
- A 128x128 map at one pixel per character wants a wide monitor (text scale 0.5, roughly
  a 4x4 monitor array or larger); `mapview.lua` downsamples to fit whatever it finds.
