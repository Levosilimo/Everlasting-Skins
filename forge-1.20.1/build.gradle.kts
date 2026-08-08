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
    // Optional-integration libraries (compileOnly main, test for the hooks).
    maven("https://modmaven.dev") { name = "ModMaven" }
    maven("https://nexus.scarsz.me/content/groups/public/") { name = "DiscordSRV" }
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/") { name = "PlaceholderAPI" }
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") { name = "Spigot" }
    maven("https://repo.papermc.io/repository/maven-public/") { name = "PaperMC" }
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
    // Unit tests: src/test/java carries the lane's MC-bound test suite
    // (mirror of the forge-1.21 post-#268 tests). The default Gradle test
    // source set is used; its compile/runtime classpaths already include
    // main's output, which contains the shared :common classes compiled
    // into main above (same shape as the 1.21 lane, where tests compile
    // against the :common jar).
    test {
        // Shared LuckPerms API test stubs live in :common's test
        // source-set (canonical copy; this lane deleted its duplicates).
        java.srcDir(projectDir.parentFile.resolve("common/src/test/java/net/luckperms"))
        // Shared TestConfigSupport (P2-3): canonical copy in :common's
        // permission test package; this lane's copy is deleted.
        java.srcDir(projectDir.parentFile.resolve("common/src/test/java/levosilimo/everlastingskins/permission"))
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

    // --- optional integrations (compileOnly; provided at run time when the
    // hook's platform is present) ---
    compileOnly("net.luckperms:api:5.5")
    compileOnly("com.discordsrv:discordsrv:1.30.5")
    compileOnly("me.clip:placeholderapi:2.12.3")
    // PlaceholderAPI's expansion base class references org.bukkit.OfflinePlayer.
    compileOnly("org.spigotmc:spigot-api:1.20.4-R0.1-SNAPSHOT")

    // --- tests ---
    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    // Common's main code paths use Gson/authlib/log4j at runtime too.
    testImplementation("com.google.code.gson:gson:2.8.0")
    testImplementation("com.mojang:authlib:1.5.25")
    testImplementation("org.apache.logging.log4j:log4j-api:2.8.1")
    testImplementation("com.google.code.findbugs:jsr305:3.0.2")
    // slf4j-api MUST appear before discordsrv in the classpath order:
    // discordsrv bundles unrelocated SLF4J 1.x classes (org/slf4j/
    // LoggerFactory) that lack the getProvider() method required by SLF4J
    // 2.x (same ordering constraint as the forge-1.21 lane).
    testImplementation("org.slf4j:slf4j-api:2.0.9")
    testImplementation("com.discordsrv:discordsrv:1.30.5")
    testImplementation("me.clip:placeholderapi:2.12.3")
    // PlaceholderAPI expansions + DiscordSRV hook tests reference Bukkit types.
    testImplementation("org.spigotmc:spigot-api:1.20.4-R0.1-SNAPSHOT")
    testImplementation("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
    testImplementation("org.mockito:mockito-core:5.12.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.3")
}

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.slf4j") {
            useVersion("2.0.9")
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}

// --- no-mixin gate ---
// Extracted to gradle/verify-no-mixin.gradle.kts: the inlined copy
// self-reported on every run because the ban literals it scans for lived
// in the scanned build file itself (build.gradle.kts). The extracted file
// sits outside the scanned set (build.gradle(.kts), settings.gradle(.kts),
// gradle.properties), mirroring the buildSrc layout of the root build.
apply(from = rootProject.file("gradle/verify-no-mixin.gradle.kts"))

tasks.named("build") {
    dependsOn("verifyNoMixin")
}
