// everlastingskins.forge-module — convention plugin for the ForgeGradle 7+
// (Java 21 toolchain) modules: forge-1.21 and the 1.21.x point releases.

import java.text.SimpleDateFormat
import java.util.Date

// Encapsulates the former root build.gradle of the 1.21 project; each
// subproject's build.gradle.kts is just `plugins { id("...") }` plus
// subproject-specific minecraft/forge versions in its gradle.properties.
//
// Deliberately NO mixingradle (org.spongepowered.mixin) plugin — mixins are
// handled by the annotation processor + jar manifest only (Lane C).
//
// FG version is managed in buildSrc/build.gradle.kts (implementation
// dependency) — precompiled script plugins cannot carry plugin versions.
plugins {
    `java-library`
    `maven-publish`
    jacoco
    id("net.minecraftforge.gradle")
}

// NOTE: property reads here use project.findProperty (NOT
// providers.gradleProperty) — in precompiled buildSrc plugins the latter
// resolves against the build-root scope and misses subproject-level
// gradle.properties.
val minecraftVersion: String = requireNotNull(project.findProperty("minecraft_version")?.toString()) {
    "everlastingskins.forge-module: 'minecraft_version' must be set in this subproject's gradle.properties"
}
val forgeVersion: String = requireNotNull(project.findProperty("forge_version")?.toString()) {
    "everlastingskins.forge-module: 'forge_version' must be set in this subproject's gradle.properties"
}
val modVersion: String = project.findProperty("mod_version")?.toString() ?: "1.0.0"
val modName: String = project.findProperty("mod_name")?.toString() ?: "EverlastingSkins"
val modVendor: String = project.findProperty("mod_vendor")?.toString() ?: "Levosilimo"
val mixinId: String = project.findProperty("mixin_id")?.toString() ?: "everlastingskins"
val toolchainVersion: Int = project.findProperty("java.toolchain.version")?.toString()?.toInt() ?: 21
val mcRunDir: String = project.findProperty("mc.runDir")?.toString() ?: "run"
val mcRunDir2: String = project.findProperty("mc.runDir2")?.toString() ?: "run2"
val gametestNamespace: String = project.findProperty("gametestNamespace")?.toString() ?: "everlastingskins"

version = modVersion

base {
    archivesName = "everlastingskins-${minecraftVersion}"
}

jacoco {
    toolVersion = "0.8.11"
}

repositories {
    minecraft.mavenizer(this)
    maven(fg.forgeMaven)
    maven(fg.minecraftLibsMaven)
    maven("https://modmaven.dev") { name = "ModMaven" }
    maven("https://minecraft.curseforge.com/api/maven/") { name = "CurseForge" }
    maven("https://www.cursemaven.com") { name = "CurseMaven" }
    maven("https://maven.minecraftforge.net/") { name = "Forge" }
    maven("https://nexus.scarsz.me/content/groups/public/") { name = "DiscordSRV" }
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/") { name = "PlaceholderAPI" }
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") { name = "Spigot" }
    maven("https://repo.papermc.io/repository/maven-public/") { name = "PaperMC" }
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(toolchainVersion))
    }
    sourceCompatibility = JavaVersion.toVersion(toolchainVersion)
    targetCompatibility = JavaVersion.toVersion(toolchainVersion)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(toolchainVersion)
}

// Game test source set: separate from unit tests because it compiles and runs
// against the real Minecraft server (game test framework), not JUnit.
// Declared before the runs block, which references it.
sourceSets {
    create("gametest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += output + sourceSets.main.get().output
    }
}

configurations {
    getByName("gametestImplementation").extendsFrom(getByName("implementation"))
    getByName("gametestCompileOnly").extendsFrom(getByName("compileOnly"))
    getByName("gametestRuntimeOnly").extendsFrom(getByName("runtimeOnly"))
}

minecraft {
    mappings("official", minecraftVersion)

    // FG 7.0.2x+ (SlimeLauncher): accessTransformer is a ConfigurableFileCollection
    // with a boolean setter that enables the default AT file from resources
    // (src/main/resources/META-INF/accesstransformer.cfg).
    setAccessTransformer(true)

    runs {
        configureEach {
            workingDir = layout.projectDirectory.dir(mcRunDir)
            systemProperty("forge.logging.console.level", "debug")
            systemProperty("fml.earlyprogresswindow", "false")
            systemProperty("mixin.env.disableRefMap", "true")
        }

        create("client") {
            systemProperty("mixin.env.remapRefMap", "true")
            systemProperty("mixin.env.refMapRemappingFile", "${layout.projectDirectory.asFile}/build/createSrgToMcp/output.srg")
            systemProperty("forge.logging.markers", "REGISTRYDUMP")
        }

        create("server") {
            workingDir = layout.projectDirectory.dir("server")
            systemProperty("mixin.env.disableRefMap", "true")
            systemProperty("mixin.env.refMapRemappingFile", "${layout.projectDirectory.asFile}/build/createSrgToMcp/output.srg")
            systemProperty("forge.logging.markers", "SCAN,REGISTRIES,REGISTRYDUMP")
        }

        create("client2") {
            workingDir = layout.projectDirectory.dir(mcRunDir2)
            args("--username", "PlayerB")
            args("--uuid", "d1b05a3e-9909-3e16-95e5-7eef2d6c713b")
        }

        // Forge ships a built-in gameTestServer run definition (launchTarget
        // forge_userdev_server_gametest); ForgeGradle names the task
        // runGameTestServer. FG7 auto-includes every project source set
        // (main + gametest) in dev runs, so the @GameTestHolder classes are
        // scanned and structure templates load from the dev mod datapack.
        create("gameTestServer") {
            systemProperty("forge.enabledGameTestNamespaces", gametestNamespace)
            // Netty 4.1 needs reflective access into the JDK on Java 21
            // (GameTest job failed with "Reflective setAccessible(true)
            // disabled" / module exports). Applied to the forked GameTest
            // server JVM, not the Gradle daemon.
            //
            // ALL-UNNAMED alone is not enough: FG6 dev loads Netty as NAMED
            // JPMS modules (io.netty.*) inside Forge's SecureModuleClassLoader,
            // and the JVM refuses "does not open java.nio / export
            // jdk.internal.misc to module io.netty.common" for those. Open and
            // export the same packages to each Netty module the dev runtime
            // loads; flags for modules absent from the layer are ignored.
            //
            // NOTE: startup --add-opens cannot reach modules Forge defines in a
            // runtime module layer (io.netty.common lives in the SECURE-BOOTSTRAP
            // layer, created after JVM start), so PlatformDependent still cannot
            // open java.nio reflectively there. tryReflectionSetAccessible=false
            // makes Netty skip that doomed attempt entirely instead of throwing
            // and logging InaccessibleObjectException at every boot; the fallback
            // (heap buffers / no-unsafe paths) is what the current environment
            // ends up with either way.
            jvmArgs(
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.base/java.nio=ALL-UNNAMED",
                "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED",
                "--add-opens=java.base/java.nio=io.netty.common",
                "--add-opens=java.base/java.nio=io.netty.buffer",
                "--add-opens=java.base/java.nio=io.netty.transport",
                "--add-opens=java.base/java.nio=io.netty.handler",
                "--add-opens=java.base/java.nio=io.netty.codec",
                "--add-opens=java.base/java.nio=io.netty.resolver",
                "--add-opens=java.base/sun.nio.ch=io.netty.common",
                "--add-opens=java.base/sun.nio.ch=io.netty.transport",
                "--add-exports=java.base/jdk.internal.misc=io.netty.common",
                "--add-exports=java.base/jdk.internal.misc=io.netty.buffer",
                "--add-exports=java.base/jdk.internal.misc=io.netty.transport",
                "--add-exports=java.base/jdk.internal.misc=io.netty.handler",
                "--add-exports=java.base/jdk.internal.misc=io.netty.codec",
                "--add-exports=java.base/jdk.internal.misc=io.netty.resolver",
                "-Dio.netty.tryReflectionSetAccessible=false"
            )
        }
    }
}

// Minecraft 1.21's Settings loader opens server.properties with no
// exists() check; a missing file logs NoSuchFileException + ERROR on every
// GameTest boot. Seed the run directory with a default server.properties so
// the GameTest server starts clean (vanilla defaults; the server rewrites
// the file on shutdown anyway).
tasks.matching { it.name == "runGameTestServer" }.configureEach {
    // FG7 removed the FG6 `mods {}` wiring that put the gametest source set
    // into the dev mod; put its output (classes + structure-template datapack
    // resources) on the run classpath so @GameTestHolder classes are scanned
    // and the empty.nbt template loads.
    (this as JavaExec).classpath += project.sourceSets.getByName("gametest").output

    doFirst {
        val runDir = layout.projectDirectory.dir(mcRunDir).asFile
        runDir.mkdirs()
        val props = runDir.resolve("server.properties")
        if (!props.exists()) {
            val template = layout.projectDirectory.file("test-infrastructure/server.properties")
            if (template.asFile.exists()) {
                props.writeText(template.asFile.readText())
            }
        }
    }
}

dependencies {
    implementation(project(":common"))
    implementation("org.apache.httpcomponents:httpclient:4.5.13")
    implementation(minecraft.dependency("net.minecraftforge:forge:${minecraftVersion}-${forgeVersion}"))
    annotationProcessor("org.spongepowered:mixin:0.8.7:processor")
    // Hack fix for now, force jopt-simple to be exactly 5.0.4 because Mojang
    // ships that version, but some transitive dependencies request 6.0+.
    implementation("net.sf.jopt-simple:jopt-simple:5.0.4") {
        version { strictly("5.0.4") }
    }

    compileOnly("net.luckperms:api:5.5")
    compileOnly("me.clip:placeholderapi:2.12.3")
    compileOnly("com.discordsrv:discordsrv:1.30.5")

    // slf4j-api MUST appear before discordsrv in the classpath order.
    // discordsrv bundles unrelocated SLF4J 1.x classes (org/slf4j/LoggerFactory)
    // that lack the getProvider() method required by SLF4J 2.x. The declaration
    // order within testImplementation determines classpath ordering.
    testImplementation("org.slf4j:slf4j-api:2.0.9")
    testImplementation("com.discordsrv:discordsrv:1.30.5")
    testImplementation("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
    compileOnly("org.spigotmc:spigot-api:1.20.4-R0.1-SNAPSHOT")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testImplementation("net.jqwik:jqwik:1.9.0")
    testImplementation("me.clip:placeholderapi:2.12.3")
    testImplementation("org.spigotmc:spigot-api:1.20.4-R0.1-SNAPSHOT")
    testImplementation("org.mockito:mockito-core:5.12.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.12.0")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.3")

    "gametestImplementation"(sourceSets.main.get().output)
    "gametestImplementation"("org.junit.jupiter:junit-jupiter-api:5.10.3")
    "gametestRuntimeOnly"("org.junit.platform:junit-platform-launcher:1.10.3")
}

configurations.getByName("testRuntimeClasspath") {
    exclude(group = "org.slf4j", module = "slf4j-simple")
}

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.slf4j") {
            useVersion("2.0.9")
        }
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    manifest {
        attributes(
            "Timestamp" to System.currentTimeMillis(),
            "Specification-Title" to modName,
            "Specification-Vendor" to modVendor,
            "Specification-Version" to modVersion,
            "Implementation-Title" to "${modName}-${minecraftVersion}",
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to modVendor,
            "Implementation-Timestamp" to SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(Date()),
            "Built-On-Java" to "${System.getProperty("java.vm.version")} (${System.getProperty("java.vm.vendor")})",
            "Built-On" to forgeVersion,
            "TweakClass" to "org.spongepowered.asm.launch.MixinTweaker",
            "TweakOrder" to 0,
            "MixinConfigs" to "${mixinId}.mixins.json"
        )
    }
}

tasks.processResources {
    inputs.property("version", project.findProperty("mod_version") ?: "")
}

sourceSets.all {
    val dir = layout.buildDirectory.dir("sourcesSets/${name}")
    output.setResourcesDir(dir.get().asFile)
    java.destinationDirectory = dir
}
