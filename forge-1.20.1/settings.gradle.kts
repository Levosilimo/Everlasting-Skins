// Standalone Gradle build for the Minecraft 1.20.1 Forge lane
// (lib-34 lane separation, implemented 2026-08-06).
//
// Why this lane is its own build: ForgeGradle 6.0.x hard-rejects Gradle 9.0+
// ("Found Gradle version ... Versions Gradle 9.0 and newer are not
// supported yet", verified empirically against ForgeGradle 6.0.54). The
// monorepo root runs Gradle 9.3.1, and included builds run under the root's
// Gradle version, so neither subproject inclusion nor an included build can
// host this lane. It therefore has its own wrapper (Gradle 8.7, run on Java
// 21) and its own ForgeGradle 6.0.54 on the buildscript classpath. The
// root's settings.gradle.kts deliberately does NOT include this directory.
//
// foojay resolver: the lane compiles with the Java 17 toolchain (Forge 47
// ships Java 17); on machines without a local JDK 17 this resolver
// auto-provisions one.
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.minecraftforge.net/")
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "everlastingskins-forge-1.20.1"
