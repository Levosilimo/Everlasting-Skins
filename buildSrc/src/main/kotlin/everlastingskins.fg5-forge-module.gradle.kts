// everlastingskins.fg5-forge-module — convention plugin for the legacy
// Java-8 Forge lane (forge-1.16.5), built with ForgeGradle 5.1.x.
//
// FG classpath strategy (lib-34 / lib-35): buildSrc deliberately carries a
// SINGLE ForgeGradle version on its classpath (7.x, for
// everlastingskins.forge-module). A second FG version here would collide on
// the same plugin id and break the 1.21 lane, so FG 5.1 is applied by the
// subproject's own build.gradle.kts (buildscript classpath +
// apply(plugin = ...), the 1.16.5 MDK pattern) — never from this plugin's
// plugins {} block (which cannot carry plugin versions anyway).
//
// This plugin wires only the version-independent module scaffolding:
// Java 8 source level/bytecode, publishing, coverage, and the :common dep.
// The FG-typed minecraft {} block (mappings / runs / userdev dep) lives in
// the subproject script next to the FG 5.1 application.
plugins {
    `java-library`
    `maven-publish`
    jacoco
    // no-mixin gate (buildSrc): registers verifyNoMixin, wired into `build`.
    id("no-mixin")
}

val minecraftVersion: String = requireNotNull(project.findProperty("minecraft_version")?.toString()) {
    "everlastingskins.fg5-forge-module: 'minecraft_version' must be set in this subproject's gradle.properties"
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
    // 1.16.5 ships Java 8 to end users; keep :forge-1.16.5 on Java 8
    // bytecode like :common. Note: javac 8 has no --release flag, so the
    // legacy lane uses -source/-target 8 (the 1.21 lane uses --release 8
    // on the JDK 21 toolchain instead).
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:-options")
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
