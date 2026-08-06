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
    // ErrorProne static analysis plugin (net.ltgt.errorprone), 5.1.0 — latest
    // stable (2026-02): requires Gradle >= 7.1 and JDK >= 11, compatible with
    // Gradle 9.3.1 + the JDK 21 toolchain. Declared as a classpath dependency
    // (NOT `apply false`) so the precompiled script plugins can apply
    // id("net.ltgt.errorprone") versionlessly — the plugins-block variant
    // does not land on the convention-plugin resolution classpath on Gradle 9.
    implementation("net.ltgt.errorprone:net.ltgt.errorprone.gradle.plugin:5.1.0")

    // Single FG version for the whole buildSrc classpath; applied
    // versionlessly by everlastingskins.forge-module. Same range as the
    // legacy 1.21 build.gradle ([7.0.3,8) — FG 7.x, Gradle 9.3.1 verified
    // on the point-release branches).
    //
    // The legacy lanes (forge-1.16.5, forge-1.20.1) are NOT on this
    // classpath: they left the root build entirely (lib-34 lane
    // separation) and run their own FG 5.1.77 / FG 6.0.54 on their own
    // wrapper classpaths. Keeping a second FG version here would collide
    // on the shared plugin id and break the 1.21 lane.
    implementation("net.minecraftforge:forgegradle:[7.0.3,8)")
}
