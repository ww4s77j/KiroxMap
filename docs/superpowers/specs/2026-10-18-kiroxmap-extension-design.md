# KiroxMap Extension — Design Spec

Date: 2026-10-18
Status: awaiting review
Owner: Kirox

## 1. Summary

KiroxMap becomes a client-side Fabric map suite. It keeps its current seed-predicted world map and
adds an observed layer, a minimap, structure-aware waypoints, optional seed cracking, and per-world
configuration. Everything is configurable per world, with global defaults.

Targets: Minecraft **26.1.x (built against 26.1.2), 26.2, 26.3**, **Fabric only**, **Java 25**.

## 2. Goals

- Show **predicted terrain** (biomes, structures, approximate relief) instantly everywhere, including
  unexplored areas, with no chunk generation and no writes to the world.
- Show **observed terrain** for chunks the client has actually loaded, so player builds and
  server/datapack divergence appear on the map.
- Render **observed where present, predicted otherwise**, with independent toggles per layer.
- Provide a **minimap HUD** and the existing **full-screen world map**, sharing one tile pipeline.
- **Structure waypoints** with configurable auto-create and auto-remove rules.
- **Optional SeedCrackerX integration** to obtain the seed on multiplayer worlds.
- **Per-server/world** seeds, observed maps, waypoints, and setting overrides.
- **Ultra-configurable** through a declarative schema that drives both JSON and the in-game UI.
- Run on three current Minecraft drops from one codebase.

## 3. Non-goals

- Pre-generating real chunks (Chunky-style). Unexplored terrain is *predicted*, never generated.
- Overwriting or editing world files. The mod is read-only with respect to levels.
- Replacing or integrating with Xaero's mods, or reading their data. This is a standalone suite, but
  it must **coexist** with Xaero's Minimap/World Map and XaeroPlus when installed: no shared state,
  no mixins into them, no conflicting keybinds by default.
- Entity radar, mob icons, cave mapping in v1.
- Forge/NeoForge support. The `neoforge` module stays dormant and is removed in a later cleanup.
- Server-side features. Everything is client-only.
- Minecraft 1.21.x. Deferred; it may return later as a separate port if demand justifies the second
  API/mapping generation.

## 4. Constraints

| Constraint | Value |
|---|---|
| Loader | Fabric only |
| Minecraft | 26.1.x (built against 26.1.2), 26.2, 26.3 |
| Java | 25 for all builds and runtime |
| Build | Gradle + Stonecutter + Fabric Loom |
| Prediction core | cubiomes via `dev.xpple:cubiomes` (FFM, LGPL-3.0 bindings, bundled) |
| Seed cracker | SeedCrackerX (MIT), soft dependency, Fabric only |
| Mod license | CC0-1.0 (unchanged) |

All three targets natively require Java 25, so there is no Java-version special case in the build.

## 5. Architecture

```
core/        Pure Java, no Minecraft types
             config schema/model, waypoint model, world identity, tile math,
             observed/predicted store formats, reconciliation rules, seed sources contract

predict/     cubiomes wrapper; one MC constant per Stonecutter node
             biomes, structures, strongholds, slime chunks, relief

capture/     chunk observation, dirty tracking, divergence detection

storage/     predicted disk cache, observed store, atomic compressed IO

render/      tile pyramid, world-map renderer, minimap HUD, graphics facade

waypoints/   waypoint engine, structure waypoints, auto-remove rules

platform/    Fabric glue: keybinds, chunk events, HUD registration, seed detection,
             SeedCrackerX bridge, config screen

build/       Stonecutter nodes: 26.1.2, 26.2, 26.3 (Fabric)
```

Dependencies point inward: `platform` → `render` / `waypoints` / `capture` → `predict` → `storage`
→ `core`. `core` depends on nothing.

### 5.1 Graphics facade

All three targets use the 26.x GUI stack (`GuiGraphicsExtractor`). A thin `KiroxGraphics` interface
(`fill`, `blit`, `text`, `centeredText`, `scissor`, `pose`, `tooltip`, `fakeItem`) still wraps the
version-specific details so that a future 1.21.x port has a single place to adapt, and so screen and
HUD code never calls a Minecraft graphics class directly. It is a small, low-risk layer rather than
the largest porting cost it would have been with 1.21.11 in scope.

## 6. Data model

### 6.1 World identity

```
worldKey = "sp:<save-folder>"  |  "mp:<server-address>"  |  "none"
```

Seeds, waypoints, observed tiles, and setting overrides are keyed by `worldKey`.
Predicted tile caches are keyed by `(mcVersion, seed, dimension)` because they are a pure function
of the seed and are safe to share between worlds.

### 6.2 Tiles

- 128 x 128 texels per tile at LOD scales 4, 16, 64, 256 blocks/texel.
- Predicted tile: biome id per texel + optional relief shade byte.
- Observed tile: surface color per texel + height per texel + divergence flag.
- Rendering selects observed over predicted per tile; a diff view renders both.

### 6.3 Observed chunk record

Per chunk (16x16), at a configurable sample resolution (1 or 4 blocks per sample):

- surface color samples
- surface height samples
- dominant biome id
- `diverges` flag: observed surface differs from prediction beyond a threshold
- `dirty` flag, persisted until finalized

Storage modes:
- `off` — no observation
- `diff-only` — persist only chunks whose observed surface diverges from prediction
- `all` — persist every observed chunk

`diff-only` is the recommended default: it captures builds and server divergence with minimal disk
use, which is the "save chunks if it isn't the same as what was predicted" requirement.

### 6.4 Storage layout

```
config/kirox-map.json
config/kirox-map-worlds/<worldKey>/settings.json
config/kirox-map-worlds/<worldKey>/waypoints.json
kirox-map-cache/predicted/<mcVersion>/<seed>/<dimension>/...
kirox-map-cache/observed/<worldKey>/<dimension>/...
```

All stores are versioned (`FORMAT`), written to a temporary file and atomically moved, read
defensively (corrupt file deleted and recomputed), and bounded by a configurable size cap with
least-recently-used trimming.

**Migration:** the existing `config/kirox-map.json` already holds per-world seeds
(`Map<String, String> seeds`) and per-world waypoints keyed by `sp:`/`mp:`. On first run after the
upgrade, these must be migrated into the new per-world files in place, without data loss. The old
keys and fields are read once and then marked migrated; a backup of the old file is written.

## 7. Subsystems

### 7.1 Prediction

- cubiomes constant per node: `MC_26_1`, `MC_26_2`, `MC_26_3`.
- Restore `DiskCache` and `Relief` from commit `e54510b` (reverted in `7f07d64`), updating `FORMAT`.
- Structure index, strongholds, End cities/gateways, slime chunks as today.
- Structure list comes from the version's own cubiomes data where possible; unknown structures for a
  node are simply absent.

### 7.2 Observation

- Hook chunk load/unload and block updates on the client.
- Samples are taken on the client thread only where required; heavy work goes to the existing worker
  pool.
- A chunk is finalized on unload or after a debounce interval, then written.
- Prediction is computed lazily for the same chunk to set the divergence flag.
- Observation never blocks rendering; the map falls back to predicted while a tile is missing.

### 7.3 Rendering

- One tile pipeline serves both the world map and the minimap.
- The minimap is a HUD renderer sampling the same in-memory tiles around the player, refreshing the
  player's chunk live while moving.
- The world map keeps the existing LOD fallback behavior (nearest coarser ancestor while a tile loads).
- Layer order: terrain (observed/predicted) → grid → slime chunks → structures → waypoints → player.

### 7.4 Minimap requirements

Configurable, independently as needed: enabled, anchor corner and offset, size, zoom level,
square or circle, north-up or facing-up, border and opacity, coordinate readout, biome readout,
chunk grid, structure markers, waypoints, layer selection, FPS cap, hide when a screen is open.
Default: top-right, 128 px, north-up, enabled.

### 7.5 Waypoints

Extended model:

```
id, name, x, y, z, dimension, color, icon, set, visible,
source: user | structure | seedcracker | import,
autoRemoveRadius (0 = never)
```

Structure waypoints:
- Per structure type: `off`, `nearest`, or `all in radius`.
- Per type: auto-create toggle, auto-remove radius, color, icon.
- Created waypoints are tagged `source = structure` and carry the structure kind.

Safety rule: auto-removal only ever applies to waypoints with `autoRemoveRadius > 0` and
`source != user` unless the user explicitly opts in. The mod never enumerates and deletes unknown
waypoints.

Waypoints are stored per `worldKey`, with JSON import/export.

### 7.6 Seed acquisition

`SeedSource` implementations, tried in priority order:

1. Stored per-world seed.
2. Integrated server seed (singleplayer).
3. SeedCrackerX result (Fabric, soft dependency): poll
   `SeedCracker.getDataStorage().getTimeMachine().worldSeeds`.
4. Manual entry.

On success the seed is stored for the world and prediction regenerates. Observed data and waypoints
survive a seed change; predicted caches do not.

SeedCrackerX is never required. If absent or unsupported, manual entry works normally. Its telemetry
database feature is not used or triggered.

### 7.7 Configuration

A declarative schema in `core`:

```
Option(section, key, type, default, min/max or allowed values,
       description, perWorldOverride, requiresRestart)
```

Types: boolean, int, float, enum, string, color, keybind, radius.

One schema drives:
- the JSON file (global and per-world),
- validation and sanitisation of unknown/missing fields,
- the in-game configuration screen (scrollable, searchable, sectioned, vanilla widgets only).

Per-world override resolution: world value if set, else global value.

## 8. Build and version strategy

Stonecutter generates one subproject per Minecraft version. Fabric-only removes Modstitch and
ModDevGradle from the picture.

Node list:

```
versions/26.1.2-fabric
versions/26.2-fabric
versions/26.3-fabric
```

The 26.1.2 node declares compatibility with 26.1–26.1.2 (26.1.1 and 26.1.2 were client-only hotfixes
that supersede 26.1). 26.2 and 26.3 have no hotfixes and are their own nodes.

Two candidate source layouts, to be decided by the phase-0 spike:

- **A (recommended): single source tree.** Merge `common/` and `fabric/` into one Stonecutter-managed
  project. Per-version differences are expressed with Stonecutter comments. Simpler mental model,
  but requires a one-time restructure.
- **B: keep `common/` + `fabric/`.** The version node depends on `common`, but because `common`
  imports Minecraft types it must itself be version-aware, which Stonecutter does not directly
  support across modules. Higher risk of duplicated build configuration.

The spike builds the *current, unmodified* feature set on all three nodes before any new subsystem
is written. If a node proves infeasible, drop it and continue.

## 9. Phasing

| Phase | Deliverable | Exit criteria |
|---|---|---|
| 0 | Stonecutter + 3 Fabric nodes, current features only | All three nodes build; each launches and renders the existing world map |
| 1 | Disk cache + Relief restored; graphics facade; tile pipeline refactor | Existing behavior unchanged on all nodes; no direct Minecraft graphics calls outside the facade |
| 2 | Observation subsystem + observed store | Builds appear on the map; diff-only mode persists only divergent chunks |
| 3 | Minimap HUD | Minimap renders and updates while moving; configurable |
| 4 | Waypoint engine, structure waypoints, auto-remove | Structure markers can become waypoints and disappear on approach |
| 5 | Seed acquisition + SeedCrackerX | Multiplayer seed auto-fills when SeedCrackerX cracks; manual path unaffected |
| 6 | Config schema, UI, per-world overrides | Every existing and new option is in the schema and UI |
| 7 | Polish: commands, import/export, performance, docs | Size caps, cache clearing, language strings |

Each phase ends with all three nodes building and at least one runtime check per version.

## 10. Test matrix

- **Build:** all three nodes in CI.
- **Runtime smoke:** 26.3 (primary), 26.2, 26.1.2; singleplayer and one multiplayer server.
- **Observation:** buildings appear; chunk unloading persists; diff-only drops matching chunks.
- **Per-world isolation:** two servers with different seeds never share observed tiles or waypoints.
- **SeedCrackerX:** present and absent; cracked and not cracked.
- **Config:** defaults, partial files, unknown fields, per-world overrides.
- **Performance:** minimap frame cost; observation cost while flying; cache size caps.

## 11. Risks

| Risk | Impact | Mitigation |
|---|---|---|
| 26.1 → 26.3 API differences | Moderate porting cost | Graphics facade; phase 0 spike first |
| Stonecutter restructure of `common`/`fabric` | Build churn | Spike decides layout before features |
| Observation cost and block-change tracking | Frame drops, disk growth | Worker threads, debounce, diff-only default, size caps |
| SeedCrackerX internals change | Integration breaks | Soft dependency; poll defensively; manual fallback |
| Config scope creep | Unbounded UI work | Schema-driven generation; every option must earn its place |
| Predicted vs observed divergence | Confusing map | Diff toggle; legend; per-world toggle for each layer |

## 12. Open questions

- Whether `observed` tiles should also be usable as a standalone minimap source when no seed is known
  (likely yes; it makes the mod useful before a seed is entered).
- Exact minimap default placement and size, to be tuned during phase 3.
- Whether structure waypoints should respect a global cap to avoid thousands of waypoints.
