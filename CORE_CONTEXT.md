# KiroxMap: contexto del proyecto y del core (cubiomes)

Documento para pegar en otros chats o prompts cuando haga falta contexto: versiones, cómo funciona el core,
qué funciones se usan y cómo actualizarlo. Estado de referencia: **KiroxMap 1.0.1 + relieve del terreno**, 2026-10-18.
Todo lo de este estado compiló y funcionó en el equipo del autor (Windows, PowerShell, `./gradlew build`).

---

## 1. Resumen

- **Qué es:** mod de mapa a pantalla completa (tecla **M**) que dibuja biomas y estructuras **solo a partir de la semilla del mundo**.
  Nunca carga chunks ni lee bloques. **Solo cliente.**
- **Loader:** Fabric, un único árbol de código en `src/main/` compilado por Stonecutter para cada versión de Minecraft.
- **Autor / licencia del mod:** Kirox, CC0-1.0.
- **Idiomas del mod:** `en_us.json` y `es_es.json` en `src/main/resources/assets/kirox_map/lang/`.

## 2. Versiones (de `stonecutter.properties.toml` y de la salida de Gradle)

| Pieza | Versión |
|---|---|
| Minecraft (nodos Stonecutter) | 26.1.2 (`>=26.1 <26.2`), 26.2 (`~26.2`), 26.3 (`~26.3`) |
| Java | 25 (toolchain) |
| Gradle | 9.7.1 |
| Stonecutter | 0.9.8 |
| `dev.kikugie.loom-back-compat` | 0.4.2 (Loom 1.18-SNAPSHOT) |
| Fabric Loom | 1.18.4 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.155.3+26.1.2 / 0.161.0+26.2 / 0.163.0+26.3 |
| NeoForge | eliminado |
| **cubiomes (bindings)** | `0.0.0-2026.9.22.1790089484+18edd56575a60fe7129705bf972de0a511437527` |
| Versión del mod | 1.0.1 |

`mod_id=kirox_map` (idéntico en `fabric.mod.json` y `KiroxMap.MOD_ID`). Las versiones por nodo están en
`stonecutter.properties.toml`; los valores que la plantilla `fabric.mod.json` expande se declaran en el bloque
`processResources` de `build.gradle.kts`.

---

## 3. El core: cubiomes

### 3.1 Qué son los repositorios (esto confunde)

| Qué | Dónde | Para qué |
|---|---|---|
| cubiomes original | `github.com/Cubitect/cubiomes` | Librería en **C** de Cubitect (MIT). Base de todo. Se queda atrás en versiones nuevas de Minecraft. |
| **fork de xpple, rama `master`** | `github.com/xpple/cubiomes` | El mismo C, **mantenido y actualizado** (soporte de versiones nuevas, terreno 1.18+, etc.). |
| **fork de xpple, rama `java-bindings`** | `github.com/xpple/cubiomes/tree/java-bindings` | **Envoltura Java** (FFM, generada con jextract) de la rama `master`, con la librería nativa compilada dentro. Se actualiza sola con cada commit de `master`. |
| Artefacto Maven | `dev.xpple:cubiomes` | Lo que descarga Gradle: la rama `java-bindings` publicada. |

**El mod ya usa el fork de xpple.** No hay "otro core" al que migrar: `master` es el C, `java-bindings` es el Java que se usa.

### 3.2 Dónde se publica y cómo es la versión

- Repositorio Maven: `https://maven.xpple.dev/maven2/` (declarado en `build.gradle.kts`, solo para el grupo `dev.xpple`).
  También aparece en Maven Central.
- Formato de la versión: `0.0.0-<AAAA.M.D>.<segundos epoch>+<commit de master>`.
- **El número de segundos es la hora a la que el CI compiló el paquete**, no la del commit. **No se puede calcular**:
  hay que copiar el nombre exacto de la carpeta en el repositorio Maven (abrirlo en el navegador).
  Un intento de adivinarlo falló con `Could not find dev.xpple:cubiomes:...`.
- **Pendiente / opcional:** hay una build más nueva, **2026-10-02, commit `4f04235f3e6e25491a02a830e4f500f68045be17`**
  (añade soporte ARM en Linux y Windows). La API que usa el mod no cambia entre las dos (solo cambió `CubiomesInit.load()`).
  Su nombre exacto no se ha podido ver desde fuera: buscar la carpeta que empiece por `0.0.0-2026.10.2` y termine en `+4f04235f…`.

### 3.3 Requisitos de los bindings

- **JDK 23 o superior** (usan la API Foreign Function & Memory; el proyecto usa Java 25, así que no hay problema).
- Hay que llamar a `CubiomesInit.load()` antes de usar nada. En el mod lo hace `world/NativeLib.java`
  (que además carga la librería nativa con el nombre correcto según plataforma).
- La dependencia se mete en el jar con `implementation(cubiomes)` + `include(cubiomes)` en `build.gradle.kts`
  (jar-in-jar de Loom); `include(implementation(...))` no compila en el DSL de Kotlin.
- Licencias: bindings LGPL-3.0; cubiomes (Cubitect) MIT; el código propio del mod CC0-1.0.

### 3.4 Cómo ver la API real (sin tener el jar)

Los bindings son código Java generado y **está en el repositorio**, así que se puede leer:

```bash
git clone --depth 1 --branch java-bindings https://github.com/xpple/cubiomes
# funciones:   src/main/java/dev/xpple/cubiomes/Cubiomes.java, Cubiomes_1.java, Cubiomes$shared.java
# structs:     src/main/java/dev/xpple/cubiomes/Generator.java, Range.java, Pos.java, SurfaceNoise.java, TerrainNoise.java ...
git clone --depth 1 https://github.com/xpple/cubiomes     # el C (master): generator.c, finders.c, biomenoise.c, terrainnoise.c
```

- Con el jar ya descargado: `javap -public -cp <jar> dev.xpple.cubiomes.Cubiomes`
  (el jar está en `~/.gradle/caches/modules-2/files-2.1/dev.xpple/cubiomes/`).
- Nota para asistentes: la API de la web de GitHub se limita por uso (rate limit) y las páginas HTML pueden bloquear
  accesos automáticos; `git clone` por HTTPS funciona.
- Convención de los nombres: la función C `foo(...)` es `Cubiomes.foo(...)` en Java; los punteros son `MemorySegment`
  (`MemorySegment.NULL` para `NULL`); los structs tienen `allocate()` y accesores `Struct.campo(segmento)`.

### 3.5 Lo que usa el mod hoy

Funciones y structs (sacado del código fuente):

- Arranque y generador: `CubiomesInit.load`, `Generator.allocate`, `Cubiomes.setupGenerator`, `applySeed`, `getDimension`,
  `isOverworld`, `MC_26_3`, `DIM_OVERWORLD` / `DIM_NETHER` / `DIM_END`.
- Biomas: `Range.allocate` (+ `scale`, `x`, `y`, `z`, `sx`, `sy`, `sz`), `genBiomes`, `getMinCacheSize`, `getBiomeAt`,
  `biome2str`, `initBiomeColors`.
- Estructuras: `getStructureConfig` (`StructureConfig`), `getStructurePos` (`Pos`), `isViableStructurePos`,
  `isViableStructureTerrain`, `isViableEndCityTerrain`, `END_SHIP`, `End_City`, `getEndCityPieces` (`Piece`, `END_CITY_PIECES_MAX`).
- Strongholds: `StrongholdIter.allocate`, `initFirstStronghold`, `nextStronghold`.
- Ruido de superficie del End: `SurfaceNoise.allocate`, `initSurfaceNoise`.
- **Relieve (nuevo):** `Cubiomes.mapApproxHeight`, `Cubiomes.mapEndSurfaceHeight`, `Generator.en(...)`.

Estructuras del mod (`world/StructureKind.java`): village, stronghold, pillager outpost, ocean monument, woodland mansion,
ruined portal, desert pyramid, jungle temple, swamp hut, igloo, shipwreck, ocean ruin, buried treasure, ancient city,
trail ruins, trial chambers, abandoned camp, mineshaft; Nether: fortress, bastion remnant, ruined portal; End: end city,
end city ship, end gateway.

### 3.6 Límites del core (decisiones ya tomadas)

- Todo sale de la **semilla**: no hay terreno real. Las posiciones de estructuras y las alturas pueden diferir un poco del juego.
- **Altura (Y) de estructuras: no disponible** (el índice solo guarda X y Z); por eso al copiar coordenadas no se copia Y.
- **Relieve:** alturas **aproximadas**. Overworld: `mapApproxHeight` (parámetro de clima "depth", una muestra cada 4 bloques;
  en tiles más lejanos, una muestra por texel). End: `mapEndSurfaceHeight`, solo al zoom más cercano (4 bloques/texel).
  Nether: cubiomes no da alturas, sin relieve.
- Cubiomes también tiene generación de terreno más exacta (`TerrainNoise`, `samplePreliminarySurfaceLevel`, `getSurfaceHeight`),
  más cara de calcular: sería el siguiente paso si se quiere un relieve más fiel. No está usada.

---

## 4. Arquitectura del mod (`src/main/java/com/kiroxtm/`)

| Paquete | Archivos clave | Responsabilidad |
|---|---|---|
| `world/` | `NativeLib`, `SeedWorld`, `StructureIndex`, `StructureKind`, `Dim`, `BiomeData`, `SlimeChunks` | Único sitio que habla con cubiomes: biomas, estructuras, strongholds, alturas, slime chunks. |
| `map/` | `TileCache`, `TileKey`, `MapView`, `StructureLayer`, `DiskCache`, `Relief` | Tiles de 128x128 texels en escalas 4, 16, 64 y 256 bloques/texel, generados en segundo plano; caché en disco; relieve. |
| `screen/` | `AtlasScreen`, `AtlasSession`, `MapCanvas`, `SidePanel`, `WaypointScreen`, `IconButton`, `ToolIcon`, `Icons`, `KiroxConfigScreen` | Interfaz: barra de botones, lienzo del mapa, panel lateral, waypoints. |
| `config/` | `AtlasConfig`, `Waypoint` | Ajustes y waypoints. |
| `client/` | `KiroxClient`, `KiroxKeys`, `ErrorLog` | Arranque, tecla M, registro de errores. |
| `platform/` | `Services` | Puente al loader (solo Fabric). |

Zoom: `MapView.DEFAULT_ZOOM = 2`, `MAX_BLOCKS_PER_PIXEL = 8`. Los iconos del mapa se reducen linealmente hasta el 25% al
alejar desde el zoom base. El código común ya no importa nada de un loader concreto; el arranque específico de Fabric está en
`src/main/java/com/kiroxtm/fabric/`.

### Relieve (`map/Relief.java`, matemática pura y con tests)

- Entrada: alturas de (128+2)x(128+2) muestras (un texel de borde, para que los tiles vecinos coincidan).
- Salida: un `byte` con signo por texel (-127 oscuro, 0 plano, +127 claro), con luz desde el noroeste.
- Se guarda aparte del color del bioma, así sobrevive a los cambios de paleta.
- Ajustes: `GAIN` (fuerza), `MAX_DARKEN` / `MAX_LIGHTEN`, `WATER_FACTOR` (mar más suave), `strength(scale)` (fuerza por zoom:
  4 y 16 = 1.0, 64 = 0.7, 256 = 0.35).

### Caché en disco (`map/DiskCache.java`)

- Carpeta `kirox-map-cache/<semilla>/<dimensión>/` junto a la carpeta de config; límite `MAX_BYTES` = 512 MB.
- Archivos: `t<escala>_<tx>_<tz>.z` (biomas), `r<escala>_<tx>_<tz>.z` (relieve), `s_<estructura>.z`, `strongholds.z`.
- **`FORMAT` hay que subirlo siempre que cambie el core o lo que se guarda** (ahora `2`); borra las cachés viejas una vez.
- Se desactiva con `"diskCache": false` en `config/kirox-map.json`.

### Ajustes (`config/kirox-map.json`)

`showGrid`, `showSlimeChunks`, `showLabels`, `showWaypointLabels`, `showRelief` (por defecto `true`), `diskCache`,
`uiScale` (1, 2 o 3; 3 = tamaño original), `panelOpen`, `showHelp` (por defecto `false`), `panelTab`, más estructuras y biomas
activos y waypoints.

### Iconos de la interfaz

PNG blancos de 64x64 en `assets/kirox_map/textures/gui/icons/<nombre>.png` (+ `.png.mcmeta` con `blur: true`), dibujados
escalados por `ToolIcon`. Los genera `tools/generate_icons.py`: `python3 tools/generate_icons.py relief` regenera solo ese
(los demás, como `scale` y `regen`, se pueden haber sustituido a mano). Para un icono nuevo: añadir su constante en `ToolIcon`
y su PNG con el mismo nombre en minúsculas.

---

## 5. Checklist: actualizar el core o Minecraft

1. Elegir la build en `https://maven.xpple.dev/maven2/dev/xpple/cubiomes/` y copiar el nombre **exacto** a `deps.cubiomes`
   en `stonecutter.properties.toml`.
2. Si sube Minecraft: añadir el nodo en `settings.gradle.kts` (`versions(...)`), su sección en `stonecutter.properties.toml`
   (`mod.mc_compat`, `mod.mc_releases`, `deps.fabric_api`), el swap `cubiomes_mc_id` en `stonecutter.gradle.kts`
   y comprobar que existe la constante `Cubiomes.MC_<versión>()` en los bindings.
3. Subir `DiskCache.FORMAT`.
4. Comparar la API usada (sección 3.5) con los bindings nuevos: `git diff <commit viejo> <commit nuevo> -- src/main/java` en la rama
   `java-bindings` (en la actualización a `4f04235` solo cambió `CubiomesInit.java`).
5. Revisar que `StructureKind` sigue al día (estructuras nuevas del juego) y que `NativeLib` sigue cargando bien en Windows, Linux y macOS.
6. `./gradlew build` (compila los tres nodos y pasa los tests); probar con `./gradlew :26.3:runClient` (o el nodo que toque).
7. Anotar el cambio en `REVERT_NOTES.md` (lleva una sección por cambio con archivos tocados y cómo revertirlo).

## 6. Comandos útiles

```bash
./gradlew build                # los tres nodos + tests
./gradlew :26.1.2:build        # un nodo concreto
./gradlew :26.2:build
./gradlew :26.3:build
./gradlew :26.3:runClient      # probar un nodo
./gradlew buildAndCollect      # copiar los tres jars a build/libs/<versión>/
```

Los jars quedan en `build/libs/<versión del mod>/` (los que no llevan `-sources`).

## 7. Notas para asistentes (Claude u otros)

- El proyecto usa **Stonecutter**: un único árbol en `src/main/` y un subproyecto por versión en `versions/`.
  Las diferencias de API entre versiones se resuelven con condicionales de Stonecutter (`//? if >=26.2 { ... //?}`),
  no duplicando archivos. Los nodos son `26.1.2`, `26.2` y `26.3`; `stonecutter active` marca el que edita el IDE.
- Los commits de la migración de build no deben incluir cambios de comportamiento; las features van en commits aparte.
- En el entorno del asistente no siempre hay JDK ni las librerías de Minecraft: lo que no se pueda compilar hay que compilarlo
  en el equipo del autor; si algo falla, pedir el error de Gradle.
- No inventar nombres de la API de Minecraft 26.x ni de cubiomes: comprobar los de cubiomes en los bindings (sección 3.4) y los de
  Minecraft en el código existente del mod, que ya compila.
- El autor escribe en español; el README y el código están en inglés.
- Historial de cambios y cómo revertirlos: `REVERT_NOTES.md`.
