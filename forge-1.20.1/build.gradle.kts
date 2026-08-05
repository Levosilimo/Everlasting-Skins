// forge-1.20.1 — legacy Forge lane: Java 17 bytecode, official Mojang
// mappings (lib-35 decision), ForgeGradle 6.x as the lane's target plugin.
//
// FG VERSION NOTE (verified empirically 2026-08-05 on the 1.16.5 lane):
// FG 6.x cannot be applied inside this build. buildSrc carries FG 7.x on
// its classpath for the 1.21 lane, and FG 7 registers the SAME plugin id
// (net.minecraftforge.gradle); the id therefore resolves to FG 7's plugin
// regardless of what the buildscript classpath contains — and FG 6.0.x is
// additionally incompatible with the root Gradle 9.3.1 wrapper (removed
// APIs), so applying it by version would fail this whole monorepo's
// configuration. Making FG 6 the engine requires separating the plugin
// classpaths (lib-34's included-build / per-lane-wrapper strategy) —
// tracked as a follow-up. Until then this module configures against the
// FG 7 API that is actually applied; MC 1.20.1's userdev setup under FG 7
// is expected to fail during this initial-port PR (see PR body for the
// tracked failures).
plugins {
    id("everlastingskins.fg6-forge-module")
}
apply(plugin = "net.minecraftforge.gradle")
val minecraftVersion: String = requireNotNull(project.findProperty("minecraft_version")?.toString())
val forgeVersion: String = requireNotNull(project.findProperty("forge_version")?.toString())
// The minecraft extension is registered at runtime (apply above), so the
// generated type-safe accessor is unavailable in this script; configure the
// extension explicitly.
val forgeExtension: net.minecraftforge.gradle.MinecraftExtensionForProject =
    extensions.getByType(net.minecraftforge.gradle.MinecraftExtensionForProject::class.java)
// Official Mojang mappings (lib-35): method names match the 1.21 lane.
forgeExtension.mappings("official", minecraftVersion)
forgeExtension.runs.configureEach {
    workingDir = layout.projectDirectory.dir("run")
    systemProperty("forge.logging.console.level", "debug")
}
forgeExtension.runs.create("client") {
    systemProperty("forge.logging.markers", "REGISTRIES")
}
forgeExtension.runs.create("server") {
    systemProperty("forge.logging.markers", "REGISTRIES")
}
dependencies {
    // The artifact is the 1.20.1-47.4.10 Forge userdev build
    // (net.minecraftforge:forge:1.20.1-47.4.10), remapped by FG as a
    // normal Forge module dependency.
    implementation(forgeExtension.dependency("net.minecraftforge:forge:${minecraftVersion}-${forgeVersion}"))
}
// FG 7 (the applied engine) wires the reobfuscated jar itself; the FG 6
// pattern `jar.finalizedBy('reobfJar')` is not used because FG 7 creates
// reobfJar lazily and a finalizedBy reference fails at configuration time.
