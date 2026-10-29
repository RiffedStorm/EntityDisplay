# Entity Display

Spawn and edit block, item and text displays with your mouse. No commands needed.

**Minecraft 1.21.1 · NeoForge 21.1.200+**

## Install

Download the jar from [Releases](../../releases) and put it in your `mods` folder.

- **Server:** needs the mod.
- **Builders:** need the mod.
- **Everyone else:** doesn't. Displays are vanilla entities, so all players see them.

## Controls

| Key | Action |
|---|---|
| `Y` | Turn the tool on/off (all other keys only work while it's on) |
| `U` | Spawn palette (blocks, items, text) |
| `I` | Inspector for the selected display |
| `R` | Switch gizmo: move / rotate / scale |
| Left click | Select a display, or drag a gizmo handle |
| Hold `Left Alt` | Drag without snapping |
| Hold `Shift` | Scale all sides together (when dragging a scale handle) |
| `N` | Duplicate selected |
| `Delete` | Delete selected |
| `Ctrl + Z` | Undo |

All keys can be changed in Options → Controls → Entity Display.

## Permissions

By default, operators and anyone in creative mode can edit displays.
Change this in `serverconfig/entitydisplay-server.toml` inside your world folder.

## Build

```
./gradlew build
```

The jar ends up in `build/libs/`.
