# Tung v.1

Fabric client mod for Minecraft Java 26.2. Right Shift opens the Tung GUI.
Intended for private servers where the owner has agreed to it.

## Using the GUI
- Drag either panel by its header to move it. Panels stay where you leave them
  until you restart the game.
- Click a category on the left to show its modules on the right.
- Left click a module to turn it on or off.
- Right click a module (or click its three dots) to open its settings, then drag the sliders.
- Settings reset when you restart the game.

## Title screen
The mod replaces the Minecraft logo with TUNGTUNG, "Java Edition" with CLIENT,
and the splash text with "made by destructivedishwasher". These are plain
resource files under `src/main/resources/assets/minecraft/` (`textures/gui/title/`
and `texts/splashes.txt`), so you can swap them for your own art or text.

## Modules

Settings that say "N selected" (Mobs, Blocks) open a searchable picker: type to
filter, click a row to tick it, scroll with the mouse wheel. "Select all" and
"Clear" apply to whatever the search is showing.

### Render > ESP
Boxes around living entities, through walls. Every mob type is selectable in the
Mobs picker (all start selected, so villagers, zombies, animals and players are
covered; untick what you don't want). Colors: red hostile, green passive, blue
water, gold players. Settings: Range, Fill Opacity.

### Render > Tracers
A line from your crosshair to each selected mob, through walls. Starts with
players only; open the Mobs picker to add any other mob. Setting: Range.

### Render > Block ESP
Boxes around the blocks you pick, through walls. Every block in the game is
selectable in the Blocks picker (search by name). Each block type gets its own
color. Starts with spawners, chests, ender chests, ancient debris and diamond ore.
Settings: Range, Max Boxes (nearest ones are shown), Fill Opacity. Only chunks
your client has loaded can be scanned. Picking very common blocks (stone, dirt)
will hit the Max Boxes cap right away.

### Combat > AutoTotem
Keeps a Totem of Undying in your offhand by swapping one in from your inventory or
hotbar. Setting: Delay (ticks between swaps).

### Base Finding > Sus Chunks
Scans the chunks your client has loaded and reports suspicious ones in chat
with their coordinates. Each chunk gets a score:

- +3 if it contains amethyst but no budding amethyst (a geode that looks harvested)
- +2 for each kind of rarely-natural block found (hopper, piston, observer,
  dispenser, dropper, repeater, comparator, redstone lamp, TNT, beacon,
  enchanting table, anvil, glass, iron/diamond/emerald/netherite blocks)
- +1 for each kind of sometimes-natural block found (crafting table, furnaces,
  chests, ender chest, torches, lanterns, ladders, scaffolding)

Settings:
- **Sensitivity (1-10):** a chunk is reported when its score is at least 12 minus
  this number. Higher means more reports and more false positives. Default 6.
- **Simulation Distance (2-32 chunks):** how far around you to scan. Default 12.
  Your client only has the chunks the server sent, so the real reach is also
  capped by your render distance.

Expect false positives: villages, mineshafts, strongholds and ruined portals
contain some of these blocks, and a geode cut by a chunk edge can look harvested.
Chunks are rescanned every five minutes to pick up new building.

## Requirements
- JDK 25
- Fabric Loader 0.19.3+ and Fabric API 0.161.0+26.2 in your mods folder

## Build
```
gradle build
```
The jar is in `build/libs/tung-1.0.0.jar`. A GitHub Actions workflow
(`.github/workflows/build.yml`) builds it automatically on every push.

## Adding a module
1. Create a class extending `dev.tung.client.module.Module`.
2. Add sliders with `addSetting(new NumberSetting(name, min, max, step, default, suffix))`.
3. Register it in `ModuleManager.init()`.

## Notes
- Mojang mappings are used directly (26.x is unobfuscated).
- Colors live in `gui/Theme.java`.
