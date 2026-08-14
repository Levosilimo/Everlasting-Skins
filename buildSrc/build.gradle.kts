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
    // versionlessly by everlastingskins.forge-module. PINNED EXACTLY to
    // 7.0.17 — the version AGENTS.md documents as consumed (first FG with
    // explicit unobfuscated-UserDev support, used by the 26.x lanes).
    // The former range [7.0.3,8) floated (it resolves to 7.0.34 today);
    // exact pinning kills that drift: buildSrc classpath changes must be
    // deliberate, not a side effect of a maven-metadata refresh.
    //
    // The legacy lanes (forge-1.16.5, forge-1.20.1) are NOT on this
    // classpath: they left the root build entirely (lib-34 lane
    // separation) and run their own FG 5.1.77 / FG 6.0.54 on their own
    // wrapper classpaths. Keeping a second FG version here would collide
    // on the shared plugin id and break the 1.21 lane.
    implementation("net.minecraftforge:forgegradle:7.0.17")

    // Dependency-analysis-gradle-plugin (com.autonomousapps.dependency-analysis),
    // 3.18.0 — the "knip" analog for Gradle Java builds: its buildHealth task
    // detects unused dependencies, wrong configurations (api vs implementation),
    // undeclared transitive dependencies, and duplicate class files. Declared
    // as a classpath dependency so everlastingskins.forge-module can apply
    // id("com.autonomousapps.dependency-analysis") versionlessly (same reason
    // as the ErrorProne line above).
    //
    // Version 3.18.0: latest stable as of 2026, verified compatible with
    // Gradle 9.3.1; minimum supported Gradle is 8.11 (root build is 9.3.1).
    // Requires a Java 11 runtime, which is fine: the build JVM is 17+ and
    // :common's --release 8 is a source/target level, not the build JVM.
    //
    // Caution: Forge's runtime reflection (e.g. @EventBusSubscriber, model
    // loaders) can produce false positives in buildHealth. We run it at
    // severity=WARN only, never FAIL, and review before any auto-fix.
    implementation("com.autonomousapps:dependency-analysis-gradle-plugin:3.18.0")
}
