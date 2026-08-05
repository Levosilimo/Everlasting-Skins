plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
    // ForgeGradle (see dependencies below) lives on the Forge maven.
    maven("https://maven.minecraftforge.net/")
}

dependencies {
    // Single FG version for the whole buildSrc classpath; applied
    // versionlessly by everlastingskins.forge-module. Same range as the
    // legacy 1.21 build.gradle ([7.0.3,8) — FG 7.x, Gradle 9.3.1 verified
    // on the point-release branches). The java8-forge-module stub must NOT
    // add a second FG version here (classpath conflict) — see its header.
    implementation("net.minecraftforge:forgegradle:[7.0.3,8)")
}
