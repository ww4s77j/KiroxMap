# Stonecutter Build Migration (Phase 0) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert KiroxMap from a `common` + `fabric` + `neoforge` multi-module build into a Fabric-only Stonecutter build that produces the same, unchanged feature set for Minecraft 26.1.2, 26.2, and 26.3.

**Architecture:** Stonecutter creates one Fabric Loom subproject per Minecraft version from a single root source tree. `loom-back-compat` selects the correct Loom variant and mappings per node. Per-version values (Minecraft version, Fabric API, cubiomes constant) come from `stonecutter.properties.toml` and a small set of Stonecutter swaps.

**Tech Stack:** Gradle 9.7.1, Stonecutter 0.9.8, `dev.kikugie.loom-back-compat` 0.4.2, Fabric Loom 1.18-SNAPSHOT, Fabric Loader 0.19.5, Fabric API per node, Java 25, cubiomes FFM bindings, JUnit 6.

**Spec:** `docs/superpowers/specs/2026-10-18-kiroxmap-extension-design.md`

**Scope:** This plan covers **Phase 0 only** (the build migration and spike). Observation, minimap, waypoints, seed acquisition, and config get separate plans after this one is green.

## Global Constraints

- Fabric only. NeoForge and Forge are out of scope; the `neoforge` module is removed by this plan.
- Minecraft nodes: `26.1.2`, `26.2`, `26.3`. The 26.1.2 node declares compatibility with `>=26.1 <26.2`.
- Java 25 for compilation and runtime on every node.
- Mod id `kirox_map`, name `Kirox Map`, group `com.kiroxtm`, version `1.0.1`, license `CC0-1.0`.
- Stonecutter `0.9.8`; `dev.kikugie.loom-back-compat` `0.4.2`; `loomx.loom_version = "1.18-SNAPSHOT"`.
- Fabric Loader `0.19.5`.
- Fabric API: `0.155.3+26.1.2` (26.1.2 node), `0.161.0+26.2` (26.2 node), `0.163.0+26.3` (26.3 node).
- cubiomes: `0.0.0-2026.9.22.1790089484+18edd56575a60fe7129705bf972de0a511437527`, bundled via jar-in-jar.
- No feature behavior changes. This plan only moves files and changes build configuration, plus the minimum per-version constant wiring.
- Every commit message ends with:
  `Co-Authored-By: Claude Code <noreply@anthropic.com>`

## Review Focus

These are the failure modes most likely to bite a user after this migration. Each one is pinned to a task below.

1. **Wrong cubiomes version constant on a node** — the map would render structures placed by another Minecraft version. Pinned by `SeedWorldVersionTest` in Task 2.
2. **Native library fails to load on a node** — opening the map throws or shows "load failed". Pinned by the per-node runtime check in Tasks 3 and 4.
3. **Missing or renamed Fabric API class at runtime** — the game starts but the keybind or tick hook is absent. Pinned by the per-node "press M" runtime check in Tasks 3 and 4.
4. **Client-only metadata drift** — a server entrypoint or missing `environment: client` in `fabric.mod.json`. Pinned by the jar inspection in Task 2.
5. **Stale loader resources in the jar** — NeoForge metadata or duplicate resources still packaged. Pinned by the jar inspection in Task 5.

---

### Task 1: Baseline and safety tag

**Files:**
- No source changes.
- Commit: `.gitignore`, `docs/superpowers/specs/2026-10-18-kiroxmap-extension-design.md` (already written).

**Interfaces:**
- Consumes: nothing.
- Produces: a known-good commit and tag to return to if the migration fails.

- [ ] **Step 1: Confirm the working tree is understood**

Run:

```bash
git status --short
```

Expected: `M .gitignore` and `?? docs/` only.

- [ ] **Step 2: Commit the spec and gitignore as the baseline**

```bash
git add .gitignore docs/superpowers/specs/2026-10-18-kiroxmap-extension-design.md
git commit -m "docs: add extension spec and ignore generated files

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

- [ ] **Step 3: Tag the pre-migration state**

```bash
git tag pre-stonecutter-2026-10-18
```

- [ ] **Step 4: Verify the current build works**

Run:

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL`; Fabric and NeoForge jars are produced under `fabric/build/libs` and `neoforge/build/libs`, and `CoreMathTest` passes.

If this baseline fails, stop and fix the baseline first; do not start the migration on a broken tree.

---

### Task 2: Stonecutter skeleton and 26.3 node

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `stonecutter.gradle.kts`
- Create: `stonecutter.properties.toml`
- Modify: `gradle.properties`
- Create: `src/main/java/com/kiroxtm/BuildInfo.java`
- Move: `common/src/main/java/com/kiroxtm/**` → `src/main/java/com/kiroxtm/**`
- Move: `fabric/src/main/java/com/kiroxtm/fabric/**` → `src/main/java/com/kiroxtm/fabric/**`
- Move: `common/src/main/resources/assets/**` → `src/main/resources/assets/**`
- Move: `fabric/src/main/resources/META-INF/**` → `src/main/resources/META-INF/**`
- Move: `fabric/src/main/resources/fabric.mod.json` → `src/main/resources/fabric.mod.json`
- Move: `common/src/test/java/com/kiroxtm/**` → `src/test/java/com/kiroxtm/**`
- Create: `src/test/java/com/kiroxtm/world/SeedWorldVersionTest.java`
- Modify: `src/main/java/com/kiroxtm/world/SeedWorld.java`
- Delete: `settings.gradle`, `build.gradle`
- Delete: `common/build.gradle`, `fabric/build.gradle`

**Interfaces:**
- Consumes: the existing source tree from Task 1.
- Produces: `com.kiroxtm.BuildInfo.MINECRAFT` (String) and `com.kiroxtm.BuildInfo.CUBIOMES_MC` (int); `SeedWorld.mcVersion()` returns `BuildInfo.CUBIOMES_MC`; Stonecutter nodes `26.1.2`, `26.2`, `26.3`.

- [ ] **Step 1: Run the new node build before it exists (it must fail)**

Run:

```bash
./gradlew :26.3:build
```

Expected: FAIL with "Project '26.3' not found" or a missing-plugin error. This proves the old build has no such node.

- [ ] **Step 2: Move the sources with git**

Run:

```bash
mkdir -p src/main/java/com/kiroxtm src/main/resources src/test/java/com/kiroxtm
git mv common/src/main/java/com/kiroxtm/KiroxMap.java src/main/java/com/kiroxtm/KiroxMap.java
git mv common/src/main/java/com/kiroxtm/client src/main/java/com/kiroxtm/client
git mv common/src/main/java/com/kiroxtm/config src/main/java/com/kiroxtm/config
git mv common/src/main/java/com/kiroxtm/map src/main/java/com/kiroxtm/map
git mv common/src/main/java/com/kiroxtm/platform src/main/java/com/kiroxtm/platform
git mv common/src/main/java/com/kiroxtm/screen src/main/java/com/kiroxtm/screen
git mv common/src/main/java/com/kiroxtm/world src/main/java/com/kiroxtm/world
git mv fabric/src/main/java/com/kiroxtm/fabric src/main/java/com/kiroxtm/fabric
git mv common/src/main/resources/assets src/main/resources/assets
git mv fabric/src/main/resources/META-INF src/main/resources/META-INF
git mv fabric/src/main/resources/fabric.mod.json src/main/resources/fabric.mod.json
git mv common/src/test/java/com/kiroxtm/CoreMathTest.java src/test/java/com/kiroxtm/CoreMathTest.java
git rm settings.gradle build.gradle common/build.gradle fabric/build.gradle
```

- [ ] **Step 3: Write `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
    id("dev.kikugie.loom-back-compat") version "0.4.2"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        versions("26.1.2", "26.2", "26.3")
        vcsVersion = "26.3"
    }
}

rootProject.name = "kirox-map"
```

- [ ] **Step 4: Write `stonecutter.gradle.kts`**

```kotlin
plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.3"

stonecutter parameters {
    swaps["minecraft"] = "\"${node.metadata.version}\";"
    swaps["cubiomes_mc_id"] = when (node.metadata.version) {
        "26.1.2" -> "dev.xpple.cubiomes.Cubiomes.MC_26_1();"
        "26.2" -> "dev.xpple.cubiomes.Cubiomes.MC_26_2();"
        else -> "dev.xpple.cubiomes.Cubiomes.MC_26_3();"
    }
}
```

- [ ] **Step 5: Write `stonecutter.properties.toml`**

```toml
mod.id = "kirox_map"
mod.name = "Kirox Map"
mod.author = "Kirox"
mod.group = "com.kiroxtm"
mod.version = "1.0.1"
mod.license = "CC0-1.0"
mod.description = "Full-screen seed map: biomes and structures computed only from the world seed, never from explored terrain."

deps.fabric_loader = "0.19.5"
deps.cubiomes = "0.0.0-2026.9.22.1790089484+18edd56575a60fe7129705bf972de0a511437527"
loomx.loom_version = "1.18-SNAPSHOT"

["26.1.2"]
mod.mc_compat = ">=26.1 <26.2"
mod.mc_releases = ["26.1", "26.1.1", "26.1.2"]
deps.fabric_api = "0.155.3+26.1.2"

["26.2"]
mod.mc_compat = "~26.2"
mod.mc_releases = ["26.2"]
deps.fabric_api = "0.161.0+26.2"

["26.3"]
mod.mc_compat = "~26.3"
mod.mc_releases = ["26.3"]
deps.fabric_api = "0.163.0+26.3"
```

- [ ] **Step 6: Write `build.gradle.kts`**

```kotlin
plugins {
    id("dev.kikugie.loom-back-compat")
}

version = "${sc.properties.get<String>("mod.version")}+${sc.current.version}"
base.archivesName = sc.properties.get<String>("mod.id")

val requiredJava = JavaVersion.VERSION_25

repositories {
    exclusiveContent {
        forRepository { maven("https://maven.xpple.dev/maven2") { name = "xpple" } }
        filter { includeGroup("dev.xpple") }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    loomx.applyMojangMappings()

    modImplementation("net.fabricmc:fabric-loader:${sc.properties.get<String>("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${sc.properties.get<String>("deps.fabric_api")}")

    // Pure-Java native-FFM library, bundled into the mod jar.
    include(implementation("dev.xpple:cubiomes:${sc.properties.get<String>("deps.cubiomes")}"))

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

loom {
    fabricModJsonPath = rootProject.file("src/main/resources/fabric.mod.json")
    runConfigs.all {
        preferGradleTask = true
        generateRunConfig = true
        runDirectory = rootProject.file("run")
    }
}

java {
    withSourcesJar()
    sourceCompatibility = requiredJava
    targetCompatibility = requiredJava
    toolchain {
        vendor = JvmVendorSpec.ADOPTIUM
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks {
    processResources {
        val props = mapOf(
            "id" to sc.properties.get<String>("mod.id"),
            "name" to sc.properties.get<String>("mod.name"),
            "author" to sc.properties.get<String>("mod.author"),
            "license" to sc.properties.get<String>("mod.license"),
            "description" to sc.properties.get<String>("mod.description"),
            "version" to sc.properties.get<String>("mod.version"),
            "minecraft" to sc.properties.get<String>("mod.mc_compat"),
            "loader" to Regex("\\d+\\.\\d+").find(sc.properties.get<String>("deps.fabric_loader"))!!.value,
            "java" to "25"
        )
        inputs.properties(props)
        filesMatching("fabric.mod.json") { expand(props) }
    }

    test {
        useJUnitPlatform()
    }

    withType<Jar> {
        from(rootProject.file("LICENSE")) { rename { "$it-${sc.properties.get<String>("mod.id")}" } }
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds mod jars and copies results to build/libs/{mod version}/"
        inputs.property("version", sc.properties.get<String>("mod.version"))
        from(loomx.modJar.flatMap { it.archiveFile }, loomx.modSourcesJar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.dir("libs/${sc.properties.get<String>("mod.version")}"))
    }
}
```

- [ ] **Step 7: Replace `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx3G
org.gradle.parallel=true
org.gradle.configuration-cache=true
```

- [ ] **Step 8: Replace `src/main/resources/fabric.mod.json`**

```json
{
  "schemaVersion": 1,
  "id": "${id}",
  "version": "${version}",
  "name": "${name}",
  "description": "${description}",
  "authors": ["${author}"],
  "license": "${license}",
  "icon": "assets/kirox_map/icon.png",
  "environment": "client",
  "entrypoints": {
    "client": ["com.kiroxtm.fabric.KiroxMapFabricClient"]
  },
  "depends": {
    "fabricloader": ">=${loader}",
    "fabric-api": "*",
    "minecraft": "${minecraft}",
    "java": ">=${java}"
  }
}
```

- [ ] **Step 9: Add `BuildInfo.java`**

```java
package com.kiroxtm;

/** Per-node constants substituted by Stonecutter. */
public final class BuildInfo {
	public static final String MINECRAFT = /*$ minecraft*/ "26.3";
	public static final int CUBIOMES_MC = /*$ cubiomes_mc_id*/ 0;

	private BuildInfo() {
	}
}
```

- [ ] **Step 10: Make `SeedWorld` use the build constant**

In `src/main/java/com/kiroxtm/world/SeedWorld.java`, add the import:

```java
import com.kiroxtm.BuildInfo;
```

Replace the body of `mcVersion()` with:

```java
public static int mcVersion() {
	return BuildInfo.CUBIOMES_MC;
}
```

Keep the existing Javadoc.

- [ ] **Step 11: Add the version-mapping test**

Create `src/test/java/com/kiroxtm/world/SeedWorldVersionTest.java`:

```java
package com.kiroxtm.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.kiroxtm.BuildInfo;
import dev.xpple.cubiomes.Cubiomes;
import org.junit.jupiter.api.Test;

class SeedWorldVersionTest {
	@Test
	void cubiomesConstantMatchesNode() {
		int expected = switch (BuildInfo.MINECRAFT) {
			case "26.1.2" -> Cubiomes.MC_26_1();
			case "26.2" -> Cubiomes.MC_26_2();
			case "26.3" -> Cubiomes.MC_26_3();
			default -> throw new IllegalStateException("Unexpected node: " + BuildInfo.MINECRAFT);
		};
		assertEquals(expected, BuildInfo.CUBIOMES_MC);
	}
}
```

- [ ] **Step 12: Run the 26.3 build**

Run:

```bash
./gradlew :26.3:build
```

Expected: `BUILD SUCCESSFUL`. If it fails, fix only build wiring (plugin ids, repositories, dependency coordinates, swapped placeholders). Do not change feature code.

- [ ] **Step 13: Run the 26.3 tests**

Run:

```bash
./gradlew :26.3:test
```

Expected: `CoreMathTest` and `SeedWorldVersionTest` pass.

- [ ] **Step 14: Inspect the built jar**

Run:

```bash
./gradlew :26.3:buildAndCollect
unzip -l build/libs/1.0.1/kirox_map-1.0.1+26.3.jar | grep -E "fabric.mod.json|neoforge|META-INF/services"
```

Expected:
- exactly one `fabric.mod.json`;
- no `neoforge.mods.toml`;
- one `META-INF/services/com.kiroxtm.platform.services.IPlatformHelper`.

Then verify the metadata:

```bash
unzip -p build/libs/1.0.1/kirox_map-1.0.1+26.3.jar fabric.mod.json
```

Expected: `"environment": "client"`, `"minecraft": "~26.3"`, `"java": ">=25"`, and only a `client` entrypoint.

- [ ] **Step 15: Run the 26.3 client**

Run:

```bash
./gradlew :26.3:runClient
```

Expected: the game launches, pressing **M** opens the map, biomes/structures render, the seed box accepts a seed, and no native-library error appears in the log.

- [ ] **Step 16: Commit**

```bash
git add -A
git commit -m "build: migrate to Stonecutter with 26.3 node

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 3: Prove the 26.2 node

**Files:**
- Modify: any source file that needs a Stonecutter condition for 26.2.
- Test: `src/test/java/com/kiroxtm/CoreMathTest.java`, `src/test/java/com/kiroxtm/world/SeedWorldVersionTest.java` (unchanged; they run on the node).

**Interfaces:**
- Consumes: the Stonecutter build and `BuildInfo` from Task 2.
- Produces: a passing `26.2` node with `BuildInfo.MINECRAFT == "26.2"` and `BuildInfo.CUBIOMES_MC == Cubiomes.MC_26_2()`.

- [ ] **Step 1: Run the 26.2 build**

Run:

```bash
./gradlew :26.2:build
```

Expected: `BUILD SUCCESSFUL`. The three 26.x nodes are expected to share the same GUI/render API; if the build passes, no source change is needed.

- [ ] **Step 2: If the build fails, apply a Stonecutter condition at the failing site**

Capture the first compiler error. Add the smallest condition using this exact syntax, replacing the two calls with the real ones from the error:

```java
//? if <26.3 {
/*oldApiCall();
*///?} else {
newApiCall();
//?}
```

Rules:
- Put the condition around the smallest expression or statement that differs.
- Do not duplicate whole files.
- Do not change behavior on 26.3.
- Re-run `./gradlew :26.2:build` after each fix.

- [ ] **Step 3: Run the 26.2 tests**

```bash
./gradlew :26.2:test
```

Expected: both test classes pass. `SeedWorldVersionTest` proves the node uses `MC_26_2`.

- [ ] **Step 4: Run the 26.2 client**

```bash
./gradlew :26.2:runClient
```

Expected: the game launches, **M** opens the map, biomes/structures render, and no native-library error appears.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "build: add 26.2 Stonecutter node

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 4: Prove the 26.1.2 node

**Files:**
- Modify: any source file that needs a Stonecutter condition for 26.1.2.
- Test: `src/test/java/com/kiroxtm/CoreMathTest.java`, `src/test/java/com/kiroxtm/world/SeedWorldVersionTest.java` (unchanged; they run on the node).

**Interfaces:**
- Consumes: the Stonecutter build and `BuildInfo` from Task 2.
- Produces: a passing `26.1.2` node with `BuildInfo.MINECRAFT == "26.1.2"` and `BuildInfo.CUBIOMES_MC == Cubiomes.MC_26_1()`.

- [ ] **Step 1: Run the 26.1.2 build**

```bash
./gradlew :26.1.2:build
```

Expected: `BUILD SUCCESSFUL`. If it fails, capture the first compiler error and add the smallest condition at that site:

```java
//? if <26.2 {
/*oldApiCall();
*///?} else {
newApiCall();
//?}
```

Use `//? if <26.2` to select the 26.1.2 branch. Do not duplicate whole files and do not change behavior on 26.2 or 26.3.

- [ ] **Step 2: Run the 26.1.2 tests**

```bash
./gradlew :26.1.2:test
```

Expected: both test classes pass. `SeedWorldVersionTest` proves the node uses `MC_26_1`.

- [ ] **Step 3: Run the 26.1.2 client**

```bash
./gradlew :26.1.2:runClient
```

Expected: the game launches, **M** opens the map, biomes/structures render, and no native-library error appears.

- [ ] **Step 4: Verify the declared compatibility range**

```bash
./gradlew :26.1.2:buildAndCollect
unzip -p build/libs/1.0.1/kirox_map-1.0.1+26.1.2.jar fabric.mod.json
```

Expected: `"minecraft": ">=26.1 <26.2"` and `"java": ">=25"`.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "build: add 26.1.2 Stonecutter node

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 5: Remove the old modules, update CI and docs, final verification

**Files:**
- Delete: `neoforge/`
- Delete: `build-logic/`
- Delete: leftover `common/` and `fabric/` build files and empty directories.
- Modify: `.github/workflows/build.yml`
- Modify: `README.md`
- Modify: `CORE_CONTEXT.md`

**Interfaces:**
- Consumes: all three passing nodes from Tasks 2–4.
- Produces: a repository with only the Stonecutter build, green CI for all three nodes, and documentation that matches.

- [ ] **Step 1: Remove the old loader and build-logic trees**

```bash
git rm -r neoforge build-logic
find common fabric -type f 2>/dev/null
```

If any files remain under `common/` or `fabric/`, confirm they are not referenced and remove them:

```bash
git rm -r common fabric
```

- [ ] **Step 2: Verify no build reference remains**

```bash
grep -rn "neoforge\|build-logic\|:common\|:fabric" settings.gradle.kts build.gradle.kts stonecutter.gradle.kts gradle.properties .github/ || true
```

Expected: no matches except intentional historical text in docs.

- [ ] **Step 3: Replace the CI build steps**

Replace `.github/workflows/build.yml` with:

```yaml
# Builds every Stonecutter node on every push and pull request.
name: build
on: [pull_request, push]

jobs:
  build:
    runs-on: ubuntu-24.04
    steps:
      - name: checkout repository
        uses: actions/checkout@v6
      - name: validate gradle wrapper
        uses: gradle/actions/wrapper-validation@v6
      - name: setup jdk
        uses: actions/setup-java@v5
        with:
          java-version: '25'
          distribution: 'microsoft'
      - name: make gradle wrapper executable
        run: chmod +x ./gradlew
      - name: build all nodes
        run: ./gradlew build
      - name: collect jars
        run: ./gradlew buildAndCollect
      - name: upload artifacts
        uses: actions/upload-artifact@v7
        with:
          name: Artifacts
          path: build/libs/**
```

- [ ] **Step 4: Update the build section of `CORE_CONTEXT.md`**

Replace the Versions table and build commands with the new values:

- Minecraft nodes: 26.1.2, 26.2, 26.3.
- Java: 25.
- Gradle: 9.7.1.
- Stonecutter: 0.9.8, `loom-back-compat` 0.4.2.
- Fabric Loader: 0.19.5.
- Fabric API: the three versions from Global Constraints.
- NeoForge: removed.
- Commands:
  ```bash
  ./gradlew :26.3:build
  ./gradlew :26.2:build
  ./gradlew :26.1.2:build
  ./gradlew :26.3:runClient
  ./gradlew buildAndCollect
  ```

Also update section 7 ("Notas para asistentes") to say the build is Stonecutter-based and that feature changes must stay out of the build-migration commits.

- [ ] **Step 5: Update `README.md`**

- Badges: remove NeoForge, keep Fabric, set Minecraft `26.1.2 / 26.2 / 26.3`, Java 25.
- Build instructions: the commands from Step 4.
- Note that 1.21.x and NeoForge are not supported.

- [ ] **Step 6: Run the full build and tests**

```bash
./gradlew build
./gradlew buildAndCollect
```

Expected: `BUILD SUCCESSFUL`; three jars under `build/libs/1.0.1/`.

- [ ] **Step 7: Inspect every jar**

```bash
for v in 26.1.2 26.2 26.3; do
  echo "== $v =="
  unzip -l build/libs/1.0.1/kirox_map-1.0.1+$v.jar | grep -E "fabric.mod.json|neoforge|META-INF/services"
done
```

Expected for each: one `fabric.mod.json`, no NeoForge metadata, one platform-helper service file.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "build: remove NeoForge and finalize Stonecutter CI

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

## Self-Review

**Spec coverage (Phase 0 only):**
- Fabric-only targets 26.1.2/26.2/26.3: Tasks 2–5.
- Java 25 on every node: `build.gradle.kts` toolchain in Task 2.
- Per-node cubiomes constant: `BuildInfo`, swap in Task 2, tests in Tasks 2–4.
- No feature changes: enforced by Global Constraints and Task 2 instructions.
- Stonecutter source layout (spec option A): Task 2 moves `common` + `fabric` into `src/main`.
- NeoForge removal: Task 5.
- CI for all nodes: Task 5.
- Deferred phases: observation, minimap, waypoints, seed acquisition, config are explicitly out of scope.

**Placeholder scan:** no TBD/TODO; every build file and test is written out; the only conditional procedure is Task 3 Step 2, which gives the exact syntax and rules for an unknown compiler error rather than a vague instruction.

**Type consistency:** `BuildInfo.MINECRAFT` and `BuildInfo.CUBIOMES_MC` are defined once and used by `SeedWorld.mcVersion()` and `SeedWorldVersionTest`. Node names in `stonecutter.properties.toml`, the swap mapping, and the test switch are identical: `26.1.2`, `26.2`, `26.3`.

**Review Focus tests:** wrong constant → Task 2 Step 11 + Tasks 3/4 Step 2; native load → Tasks 2/3/4 client runs; missing Fabric API → Tasks 3/4 client runs; client-only metadata → Task 2 Step 14; stale loader resources → Task 5 Step 7.
