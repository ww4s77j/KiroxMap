plugins {
    id("dev.kikugie.loom-back-compat")
}

val mcVersion = sc.current.version
val modId = sc.properties.get<String>("mod.id")
val modVersion = sc.properties.get<String>("mod.version")
val modName = sc.properties.get<String>("mod.name")
val modAuthor = sc.properties.get<String>("mod.author")
val modLicense = sc.properties.get<String>("mod.license")
val modDescription = sc.properties.get<String>("mod.description")
val mcCompat = sc.properties.get<String>("mod.mc_compat")
val fabricLoader = sc.properties.get<String>("deps.fabric_loader")
val fabricApi = sc.properties.get<String>("deps.fabric_api")
val cubiomes = "dev.xpple:cubiomes:${sc.properties.get<String>("deps.cubiomes")}"

version = "$modVersion+$mcVersion"
base.archivesName = modId

val requiredJava = JavaVersion.VERSION_25

repositories {
    exclusiveContent {
        forRepository { maven("https://maven.xpple.dev/maven2") { name = "xpple" } }
        filter { includeGroup("dev.xpple") }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")
    loomx.applyMojangMappings()

    modImplementation("net.fabricmc:fabric-loader:$fabricLoader")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApi")

    // Pure-Java native-FFM library, bundled into the mod jar.
    implementation(cubiomes)
    include(cubiomes)

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

sourceSets {
    main {
        java.setSrcDirs(listOf(layout.buildDirectory.dir("generated/stonecutter/main/java").get().asFile))
    }
}

tasks.named("compileJava") {
    dependsOn("stonecutterGenerate")
}

tasks.named("sourcesJar") {
    dependsOn("stonecutterGenerate")
}

tasks {
    processResources {
        val props = mapOf(
            "id" to modId,
            "name" to modName,
            "author" to modAuthor,
            "license" to modLicense,
            "description" to modDescription,
            "version" to modVersion,
            "minecraft" to mcCompat,
            "loader" to Regex("\\d+\\.\\d+").find(fabricLoader)!!.value,
            "java" to "25"
        )
        inputs.properties(props)
        filesMatching("fabric.mod.json") { expand(props) }
    }

    test {
        useJUnitPlatform()
    }

    withType<Jar> {
        from(rootProject.file("LICENSE"))
        rename("LICENSE", "LICENSE-$modId")
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds mod jars and copies results to build/libs/{mod version}/"
        inputs.property("version", modVersion)
        from(loomx.modJar.flatMap { it.archiveFile }, loomx.modSourcesJar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.dir("libs/$modVersion"))
    }
}
