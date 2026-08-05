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

// M2 monorepo: one Gradle root for the Forge line. The 1.12.2 lane
// (mc1.12.2/) is deliberately NOT included: ForgeGradle 2.3.4 needs
// Gradle 4.x + Java 8 and stays on its own wrapper, out-of-band.
include("common")
include("forge-1.21")
include("forge-1.21.1")
include("forge-1.21.4")
include("forge-1.21.8")
// future: include("forge-1.16.5"), include("forge-1.20.1"), include("forge-26.2")
