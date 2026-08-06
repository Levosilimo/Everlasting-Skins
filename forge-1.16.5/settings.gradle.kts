// Standalone Gradle build for the Minecraft 1.16.5 Forge lane
// (lib-34 lane separation, implemented 2026-08-06).
//
// Why this lane is its own build: ForgeGradle 5.1.x hard-rejects Gradle 8.0+
// ("Found Gradle version ... Versions Gradle 8.0 and newer are not
// supported", verified empirically against ForgeGradle 5.1.77). The monorepo
// root runs Gradle 9.3.1, and included builds run under the root's Gradle
// version, so neither subproject inclusion nor an included build can host
// this lane. It therefore has its own wrapper (Gradle 7.6.4, run on Java 8)
// and its own ForgeGradle 5.1.77 on the buildscript classpath. The root's
// settings.gradle.kts deliberately does NOT include this directory.
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.minecraftforge.net/")
        mavenCentral()
    }
}

rootProject.name = "everlastingskins-forge-1.16.5"
