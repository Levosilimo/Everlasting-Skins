// forge-1.20.1 — standalone Gradle build for the Minecraft 1.20.1 lane.
//
// LANE SEPARATION (lib-34, implemented 2026-08-06): ForgeGradle 6.0.x
// hard-rejects Gradle 9.0+ ("Found Gradle version ... Versions Gradle 9.0
// and newer are not supported yet", verified empirically against
// ForgeGradle 6.0.54). The monorepo root runs Gradle 9.3.1, and included
// builds run under the root's Gradle version, so this lane can neither be a
// root subproject nor an included build. It is therefore its own Gradle
// build with its own wrapper (Gradle 8.7, run on Java 21) and ForgeGradle
// 6.0.54 applied via the buildscript classpath (the 1.20.1 MDK pattern).
//
// The precompiled buildSrc convention plugin
// (everlastingskins.fg6-forge-module) was deleted when this lane left the
// root build; the scaffolding it provided (Java 17, archives name, repos,
// JUnit, jacoco, no-mixin gate) is inlined below so the lane stays
// self-contained.
import org.gradle.api.GradleException
import org.gradle.api.tasks.SourceSetContainer

buildscript {
    repositories {
        maven("https://maven.minecraftforge.net/") { name = "Forge" }
        mavenCentral()
    }
    dependencies {
        classpath("net.minecraftforge.gradle:ForgeGradle:6.0.54")
    }
}

plugins {
    `java-library`
    `maven-publish`
    jacoco
}

apply(plugin = "net.minecraftforge.gradle")

val minecraftVersion: String = requireNotNull(project.findProperty("minecraft_version")?.toString()) {
    "forge-1.20.1: 'minecraft_version' must be set in gradle.properties"
}
val forgeVersion: String = requireNotNull(project.findProperty("forge_version")?.toString()) {
    "forge-1.20.1: 'forge_version' must be set in gradle.properties"
}
val modVersion: String = project.findProperty("mod_version")?.toString() ?: "1.0.0"

version = modVersion

base {
    archivesName.set("everlastingskins-${minecraftVersion}")
}

repositories {
    // Forge userdev artifacts + Minecraft's own libraries (Mojang mappings
    // for the official channel).
    maven("https://maven.minecraftforge.net/") { name = "Forge" }
    maven("https://libraries.minecraft.net/") { name = "Minecraft" }
    mavenCentral()
}

java {
    // Forge 47 (1.20.1) ships Java 17 to end users (per the official MDK).
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(17)
    options.compilerArgs.add("-Werror")
}

// :common (the shared version-independent core, M2) is consumed by
// source-dir sharing: this is a standalone build and cannot use
// project(":common"). Common is frozen at Java 8 (-Werror), which compiles
// cleanly under the Java 17 toolchain (--release 17 is a superset).
sourceSets {
    main {
        java.srcDir(projectDir.parentFile.resolve("common/src/main/java"))
        resources.srcDir(projectDir.parentFile.resolve("common/src/main/resources"))
    }
}

// The minecraft extension is registered at apply() time above (FG 6.0:
// net.minecraftforge.gradle.userdev.UserDevExtension, extension name
// "minecraft"), so the generated type-safe accessor is unavailable in this
// script; configure the extension explicitly.
val forgeExtension: net.minecraftforge.gradle.userdev.UserDevExtension =
    extensions.getByType(net.minecraftforge.gradle.userdev.UserDevExtension::class.java)

// Official Mojang mappings (lib-35): method names match the 1.21 lane.
forgeExtension.mappings("official", minecraftVersion)

forgeExtension.runs.configureEach {
    workingDirectory = layout.projectDirectory.dir("run").asFile.absolutePath
    property("forge.logging.console.level", "debug")
    // Put the mod's source set on the dev-run classpath (FG 6.0 equivalent
    // of the MDK's Groovy `mods { ... source sourceSets.main }` block).
    source(sourceSets["main"])
}

forgeExtension.runs.create("client") {
    property("forge.logging.markers", "REGISTRIES")
}

forgeExtension.runs.create("server") {
    property("forge.logging.markers", "REGISTRIES")
}

dependencies {
    // The artifact is the 1.20.1-47.4.10 Forge userdev build; FG 6.0 wires
    // it into the userdev pipeline via its own `minecraft` configuration
    // (the MDK pattern). reobfJar is auto-wired onto `jar` by FG 6.0.
    "minecraft"("net.minecraftforge:forge:${minecraftVersion}-${forgeVersion}")

    // --- :common compile-only deps (mirror common/build.gradle.kts;
    // provided by the Forge runtime at run time) ---
    compileOnly("com.google.code.gson:gson:2.8.0")
    compileOnly("com.mojang:authlib:1.5.25")
    compileOnly("org.apache.logging.log4j:log4j-api:2.8.1")
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")

    // --- tests ---
    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    // Common's main code paths use Gson/authlib/log4j at runtime too.
    testImplementation("com.google.code.gson:gson:2.8.0")
    testImplementation("com.mojang:authlib:1.5.25")
    testImplementation("org.apache.logging.log4j:log4j-api:2.8.1")
    testImplementation("com.google.code.findbugs:jsr305:3.0.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.3")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}

// --- no-mixin gate (inlined from the deleted buildSrc convention
// no-mixin.gradle.kts, which this standalone build cannot use) ---
// Mixin policy: this lane contains ZERO Mixin annotations and ZERO Mixin
// Gradle plugin usage. The gate scans this project's build files + all
// source roots (including the shared :common sources) and fails `build` on
// any violation. Banned strings are written in escaped-regex form on
// purpose so this file never contains the literal strings it scans for.
val bannedLiterals = listOf(
    "mixingradle coordinate" to "org.spongepowered:mixingradle",
    "mixin plugin id" to "org.spongepowered.mixin",
    "mixin config bundling" to "everlastingskins.mixins.json",
)

val bannedPatterns = listOf(
    "Mixin annotation" to Regex("@Mixin\\b"),
    "mixin block" to Regex("^\\s*mixin\\s*\\{"),
)

fun scanFile(file: java.io.File, offenders: MutableList<String>) {
    if (!file.isFile) return
    val text = file.readText()
    for ((label, literal) in bannedLiterals) {
        if (text.contains(literal)) {
            offenders += "${file.relativeTo(projectDir)}: $label reference ('$literal')"
        }
    }
    for ((label, pattern) in bannedPatterns) {
        pattern.findAll(text).forEach { match ->
            offenders += "${file.relativeTo(projectDir)}: $label at line ${text.substring(0, match.range.first).count { it == '\n' } + 1}"
        }
    }
}

val verifyNoMixin = tasks.register("verifyNoMixin") {
    group = "verification"
    description = "Fails the build if any Mixin usage is detected: annotations, mixingradle, mixin plugin id, mixin {} blocks, or mixins.json bundling."

    val projectDir = project.projectDir
    val buildFiles = listOf(
        projectDir.resolve("build.gradle"),
        projectDir.resolve("build.gradle.kts"),
        projectDir.resolve("settings.gradle"),
        projectDir.resolve("settings.gradle.kts"),
        projectDir.resolve("gradle.properties"),
    ).filter { it.isFile }

    doLast {
        val offenders = mutableListOf<String>()

        // All source + resource files of every source set (main, test, ...
        // plus the shared :common source dirs added above).
        val sourceSets = project.extensions.findByType(SourceSetContainer::class.java)
        val sourceRoots = (sourceSets?.flatMap { it.allSource.srcDirs } ?: emptyList())
            .filter { it.exists() }
        sourceRoots.forEach { root ->
            root.walkTopDown()
                .filter { it.isFile && (it.extension == "java" || it.extension == "json" || it.extension == "properties") }
                .forEach { scanFile(it, offenders) }
        }
        buildFiles.forEach { scanFile(it, offenders) }

        if (offenders.isNotEmpty()) {
            throw GradleException(
                "Mixin policy violated:\n" +
                    offenders.joinToString("\n") +
                    "\n\nMixin policy: the lane contains ZERO Mixin usage. " +
                    "If a lane requires Mixin support, fork the lane - it is a sign " +
                    "that should not happen in the shared core."
            )
        }
        logger.lifecycle("Mixin check passed: zero Mixin annotations, zero mixingradle references.")
    }
}

tasks.named("build") {
    dependsOn(verifyNoMixin)
}
