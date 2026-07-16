# Changelog

## 1.6.2-alpha
Printed books can now be placed in chiseled bookshelves. Fixed thick printed
books z-fighting when held (page edges now keep stable depth separation, also
under shaders). Fixed paging through a printout faster than the server round
trip snapping back to stale pages; the viewer is now client-authoritative and
draws pen ink directly.

## 1.6.1-alpha
Added the Camo Network Cable: joins CC:T wired networks like a cable and can
be disguised as any full opaque block (right-click with the block, sneak-punch
to strip). Uses CC:T's cable model and textures with a matching hitbox, and
the disguise renders as real terrain geometry so resource packs and shader
material maps apply. Fixed the disguise not appearing until a neighbouring
block update. Build now produces lakeccaddition-<version>.jar with the version
sourced from gradle.properties alone.

## 1.3.1
Stamp editor layout corrections: readable palette swatches, no overlapping
controls.

## 1.3.0
Added the Stamp: a customisable 32x32 emblem with an in-game editor, applied
to printed pages by clicking. Fixed pen drawings not rendering in item
frames. Updated pen and folder sprites.

## 1.2.2
Pen editor: per-stroke undo (button and Ctrl+Z) and corrected button layout.

## 1.2.1
Pen fixes: page renders correctly in the editor, the drawable area covers the
full page including margins, and drawings render on pages held in hand and in
item frames.

## 1.2.0
Added the Pen: freehand drawing on printed pages at font-pixel resolution, in
three ink colours, stored on the item. Drawings are readable and writable
through the scanner for form processing.

## 1.1.0
Added the Document Folder: 27 slots, printouts and paper only, 16 per slot.
The scanner can catalogue a folder's contents, read documents inside it, and
file new documents into it.

## 1.0.2
Corrected version metadata and the Exposure dependency range ([1.9,)).

## 1.0.1
Repaired the Exposure integration (1.9 API) and restored files lost to a
packaging error in 1.0.0.

## 1.0.0
First release. Unique scanner block textures in the CC:Tweaked advanced
style, item tooltips, printout creation sound, and documentation.

## 0.9.0
Added sha256() to the peripheral API.

## 0.8.0
Added scanContainer() for reading shulker box contents, and the biometric
scan (sneak-click with an empty hand) with its scanner_biometric event.

## 0.5.0
Added Exposure photograph scanning: metadata, monitor rendering, and the
print pipeline with dithering, tone, and poster options.

## 0.4.1
Added a pre-upgraded Deluxe Pocket Computer to the creative tab.

## 0.4.0
Added the Pocket Multitool: ender modem and speaker in one pocket upgrade.

## 0.3.0
Added map scanning: metadata, raw pixels, monitor rendering, and shared-
palette tiles for multi-map displays.

## 0.2.0
Added book scanning, NBT scanning, fingerprints, and printout creation with
per-character colour.

## 0.1.0
Initial version: the scanner block with GUI, hopper support, printout
scanning, and slot-change events.
