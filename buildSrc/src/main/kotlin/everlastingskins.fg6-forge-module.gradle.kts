// everlastingskins.fg6-forge-module — convention plugin for the legacy
// Java-17 Forge lane (forge-1.20.1), targeted at ForgeGradle 6.x.
//
// FG classpath strategy (lib-34 / lib-35, verified empirically on the
// 1.16.5 lane 2026-08-05): buildSrc deliberately carries a SINGLE
// ForgeGradle version on its classpath (7.x, for
// everlastingskins.forge-module). A second FG version here would collide on
// the same plugin id and break the 1.21 lane — and FG 6.0.x additionally
// cannot run on the root Gradle 9.3.1 wrapper (removed APIs), so applying
// it by version would fail this whole monorepo's configuration. FG is
// therefore applied by the subproject's own build.gradle.kts (buildscript
// classpath + apply(plugin = ...), the 1.20.1 MDK pattern); real FG 6 needs
// lib-34's included-build / per-lane-wrapper strategy — tracked as a
// follow-up.
//
// This plugin wires only the version-independent module scaffolding:
// Java 17 source level/bytecode, publishing, coverage, and the :common dep.
// The FG-typed minecraft {} block (mappings / runs / userdev dep) lives in
// the subproject script next to the FG application.
plugins {
    `java-library`
    `maven-publish`
    jacoco
    // no-mixin gate (buildSrc): registers verifyNoMixin, wired into `build`.
    id("no-mixin")
}

val minecraftVersion: String = requireNotNull(project.findProperty("minecraft_version")?.toString()) {
    "everlastingskins.fg6-forge-module: 'minecraft_version' must be set in this subproject's gradle.properties"
}
val modVersion: String = project.findProperty("mod_version")?.toString() ?: "1.0.0"

version = modVersion

base {
    archivesName = "everlastingskins-${minecraftVersion}"
}

repositories {
    // Forge userdev artifacts + Minecraft's own libraries; the FG 7 lane
    // pulls the same via its mavenizer, but this plugin must not depend on
    // the FG extension (FG is applied by the subproject script, after this
    // plugin runs).
    maven("https://maven.minecraftforge.net/") { name = "Forge" }
    maven("https://libraries.minecraft.net/") { name = "Minecraft" }
    mavenCentral()
}

java {
    // Forge 47 (1.20.1) ships Java 17 to end users (per the official MDK);
    // the root java.toolchain.version=21 is NOT used for this lane.
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

dependencies {
    implementation(project(":common"))

    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.3")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}
