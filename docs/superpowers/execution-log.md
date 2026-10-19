# KiroxMap Execution Log

Plan: `docs/superpowers/plans/2026-10-18-stonecutter-migration.md`
Spec: `docs/superpowers/specs/2026-10-18-kiroxmap-extension-design.md`
Branch: `stonecutter-migration`
SDD ledger (gitignored scratch): `.superpowers/sdd/2026-10-18-stonecutter-migration/progress.md`
Started: 2026-10-18

## Environment

- `JAVA_HOME=C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot`
- Gradle 9.7.1; Stonecutter 0.9.8; `dev.kikugie.loom-back-compat` 0.4.2; Fabric Loom 1.18.4 via `loomx`
- Fabric Loader 0.19.5; Fabric API 0.155.3+26.1.2 / 0.161.0+26.2 / 0.163.0+26.3
- Nodes: `26.1.2`, `26.2`, `26.3`; Fabric only; Java 25
- cubiomes: `0.0.0-2026.9.22.1790089484+18edd56575a60fe7129705bf972de0a511437527`

## Progress

### 2026-10-18 — Task 1: Baseline and safety tag

- Committed spec + plan + `.gitignore` as `7322d75`.
- Tagged `pre-stonecutter-2026-10-18`.
- Baseline `./gradlew build` (Fabric + NeoForge) passed; `:common:test` passed.
- Ruling: also commit the plan file in the baseline commit.

### 2026-10-18 — Task 2: Stonecutter skeleton and 26.3 node

- Moved `common/` + `fabric/` sources into a shared `src/main` tree; removed old root build scripts and module build scripts.
- Added `settings.gradle.kts`, `build.gradle.kts`, `stonecutter.gradle.kts`, `stonecutter.properties.toml`.
- Added `BuildInfo` with Stonecutter swaps and `SeedWorldVersionTest`.
- 26.3 build and 5 tests pass; jar contains one `fabric.mod.json`, one platform service file, no NeoForge metadata; cubiomes is jar-in-jar.
- 26.3 client launched with `kirox_map 1.0.1`, Minecraft 26.3, Fabric Loader 0.19.5, Java 25; no KiroxMap exception. The interactive M/seed/map check is deferred to the user.
- Committed as `66fc47b`.

Key rulings recorded in the SDD ledger:
- `implementation(coord) + include(coord)` instead of `include(implementation(...))` (Kotlin nullability).
- `build.gradle.kts` holds the node swaps; `stonecutter parameters { }` restored for build-extension wiring.
- Generated Stonecutter sources are wired explicitly: `sourceSets.main.java = build/generated/stonecutter/main/java`; `compileJava` and `sourcesJar` depend on `stonecutterGenerate`.
- LICENSE rename uses the non-lambda `rename("LICENSE", ...)` overload for configuration-cache safety.
- Interactive M check deferred because the harness cannot press keys.

### 2026-10-18 — Task 3: 26.2 node (build green; commit pending — session paused)

- `./gradlew :26.2:build` succeeded (`BUILD SUCCESSFUL in 1m 7s`), all tests passed.
- Generated sources verified: `BuildInfo.MINECRAFT = "26.2"`, `BuildInfo.CUBIOMES_MC = Cubiomes.MC_26_2()`,
  and `KiroxKeys` generated `InputConstants.Type.KEYSYM` for 26.2.
- `SeedWorldVersionTest`: `tests="1" failures="0"`.
- `./gradlew :26.2:buildAndCollect` succeeded; `build/libs/1.0.1/kirox_map-1.0.1+26.2.jar` metadata verified:
  `"environment":"client"`, `"minecraft":"~26.2"`, `"java":">=25"`, client entrypoint only,
  cubiomes jar-in-jar, no NeoForge metadata, one platform service file.
- 26.2 client launched (`:26.2:runClient`): log shows `kirox_map 1.0.1`, `minecraft 26.2`,
  Fabric Loader 0.19.5, Java 25; no KiroxMap exception. OSHI/JNA NPEs are Minecraft's
  `CrashReport.preload` system report, same as 26.3. Interactive M/seed/map check deferred to the user.
- **Paused by the user mid-Task-3.** State: all Task 3 changes are uncommitted
  (`KiroxKeys.java`, `stonecutter.gradle.kts`, `docs/superpowers/execution-log.md`, plus the gitignored ledger).
- **Resume here:** commit Task 3 (`build: add 26.2 Stonecutter node`, with
  `Co-Authored-By: Claude Code <noreply@anthropic.com>`), run the task-done ledger script,
  then start Task 4 (26.1.2).



- Started `./gradlew :26.2:build`; log: `.superpowers/sdd/2026-10-18-stonecutter-migration/task-3-build.log`.
- First 26.2 build failed at `KiroxKeys`: `InputConstants.Type.KEYBOARD` does not exist in 26.2.
  Verified against the 26.2/26.3 merged jars: 26.2 has `KEYSYM`, 26.3 renamed it to `KEYBOARD`.
- First fix attempt used an inline `key_type` swap; Stonecutter did not recognize a swap marker placed
  mid-expression, so the generated source kept the comment and failed to parse.
- Final fix: a real Stonecutter conditional in `KiroxKeys`:
  `//? if >=26.3 { ... KEYBOARD ... }` and `//? if <26.3 { /* ... KEYSYM ... */ }`.
- Rebuilding 26.2.

### 2026-10-18 — Task 3: complete

- Committed as `ef8ccb5` (`build: add 26.2 Stonecutter node`); verification rerun `./gradlew :26.2:build` → BUILD SUCCESSFUL, 5 tests pass.
- Ledger: `Task 3: complete (commits 66fc47b..ef8ccb5, tests: ./gradlew :26.2:build)`.

### 2026-10-18 — Task 4: 26.1.2 node (in progress)

- First `./gradlew :26.1.2:build` failed with 7 errors: `Minecraft.gui` has no `screen()`/`setScreen(...)` in 26.1.2.
  Verified with javap against the merged jars: 26.1.2 has the public field `Minecraft.screen` and `Minecraft.setScreen(Screen)`;
  26.2/26.3 moved them to `Gui.screen()` / `Gui.setScreen(...)`.
- Fix: new `com.kiroxtm.client.Screens` helper (`current`, `open`) with a `//? if >=26.2` conditional; the six call sites
  (`KiroxClient`, `AtlasScreen` ×2, `KiroxConfigScreen`, `WaypointScreen`) now use it. Rebuilding 26.1.2.

- Second build: **BUILD SUCCESSFUL in 59s**; 5 tests pass (`CoreMathTest` 4, `SeedWorldVersionTest` 1).
  Generated `BuildInfo` for the node: `MINECRAFT = "26.1.2"`, `CUBIOMES_MC = Cubiomes.MC_26_1()`.
- Client launch: `kirox_map 1.0.1`, Minecraft 26.1.2, Fabric Loader 0.19.5, Java 25;
  `Kirox Map loaded on Fabric`; LWJGL 3.4.1-snapshot, OpenAL and all atlases initialized.
  The JVM then exited with NTSTATUS 0xCFFFFFFF (-805306369) with no crash report and no hs_err.
  This happened while the user's own PrismLauncher 26.3 client was running; startup took 12 minutes.
  The interactive M/seed/map check stays deferred to the user.

- Correction: the NTSTATUS 0xCFFFFFFF exit was the user closing the 26.1.2 window, not a crash. The launch was clean
  (mod loaded, LWJGL, OpenAL and all atlases initialized). Interactive M/seed/map check remains deferred to the user.

### 2026-10-18 — Task 4: complete

- Committed as `e23e249` (`build: add 26.1.2 Stonecutter node`).
- 26.1.2 jar `build/libs/1.0.1/kirox_map-1.0.1+26.1.2.jar`: `"minecraft":">=26.1 <26.2"`, `"java":">=25"`,
  `"environment":"client"`, client entrypoint only, one `META-INF/services` file, cubiomes jar-in-jar, no NeoForge metadata.
- Ledger: `Task 4: complete (commits ef8ccb5..e23e249, tests: ./gradlew :26.1.2:test)`.

### 2026-10-18 — Task 5: remove old modules, CI and docs (in progress)

- Deleted `neoforge/` and `build-logic/` (9 tracked files); removed the leftover untracked `common/` and `fabric/` build folders.
- Replaced `.github/workflows/build.yml` with the all-node Stonecutter workflow.
- Updated `CORE_CONTEXT.md` (versions table, paths, cubiomes inclusion, commands, assistant notes) and `README.md`
  (badges, Fabric-only install, node build commands, 1.21.x/NeoForge unsupported).
- Running `./gradlew build` + `./gradlew buildAndCollect`; log: `task-5-build.log`.

### 2026-10-18 — Final whole-branch review

- Fresh reviewer (Opus) reviewed `0ddcd15..34baf54`, read the plan/spec/ledger, built a detached worktree and reran `./gradlew build`
  (all 31 tasks, 5 tests per node), and inspected the three jars and the bundled native library.
- Verdict: ready with fixes. No Critical. One Important: the spec's Phase 0 exit criterion ("launches and renders the existing
  world map") and Review Focus #2 (native library) were not evidenced; `SeedWorldVersionTest` touches only pure constants.
- Fix: added `NativeLibTest`, which calls `NativeLib.load()` and generates a 4x4 biome area through `SeedWorld`.
  `./gradlew test` on all three nodes: BUILD SUCCESSFUL, `NativeLibTest` 1/1 per node, 6 tests per node, 18/18 total.
  Committed as `4777838`.
- The reviewer's remaining findings are Minor and deferred in the ledger (shared `run/` folder, generated-source wiring,
  `fabricloader >=0.19` truncation, stale NeoForge comments, plan/spec removal-timing contradiction, dropped layering check).
- Still required from the user before merge: press M and confirm biomes/structures render on 26.1.2, 26.2 and 26.3.
