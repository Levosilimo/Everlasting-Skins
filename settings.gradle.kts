pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.minecraftforge.net/")
        maven("https://repo.spongepowered.org/repository/maven-public/")
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "everlasting-skins-parent"

// M2 monorepo: one Gradle root for the Forge line. Two lanes are
// deliberately NOT included — each runs its own Gradle build out-of-band:
//
// 1. The 1.12.2 lane (mc1.12.2/): ForgeGradle 2.3.4 needs Gradle 4.x +
//    Java 8 and stays on its own wrapper.
// 2. The 1.16.5 and 1.20.1 lanes (lib-34 lane separation, PR #2xx):
//    ForgeGradle 5.1.x hard-rejects Gradle 8.0+ and ForgeGradle 6.0.x
//    hard-rejects Gradle 9.0+ (verified empirically 2026-08-06 against
//    5.1.77 / 6.0.54), while this root runs Gradle 9.3.1. Included builds
//    run under the root's Gradle version, so neither subproject inclusion
//    nor an included build can host them. Each lane is its own build with
//    its own wrapper:
//      forge-1.16.5/  → Gradle 7.6.4 (run on Java 8)   + ForgeGradle 5.1.77
//      forge-1.20.1/  → Gradle 8.7   (run on Java 21)  + ForgeGradle 6.0.54
//    Build them from the lane dir: `cd forge-1.16.5 && ./gradlew build`.
include("common")
include("forge-1.21")
include("forge-1.21.1")
include("forge-1.21.4")
include("forge-1.21.8")
include("forge-26.2")
include("forge-26.1")
