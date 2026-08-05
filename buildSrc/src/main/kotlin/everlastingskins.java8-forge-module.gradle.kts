// everlastingskins.java8-forge-module — convention plugin for the future
// Java-8-source forge modules (1.16.5 / 1.20.1).
//
// SCAFFOLD STUB: no subproject applies this yet (not included in
// settings.gradle.kts), and it deliberately does NOT apply
// net.minecraftforge.gradle: buildSrc can carry only one FG version on its
// classpath (7.x for the forge-module plugin), while 1.16.5/1.20.1 need
// FG 5.1+/6.x. When the 1.16.5/1.20.1 ports start, decide the FG-classpath
// strategy (lib-34: FG 5.1+→8.x coexist at Gradle 9.5.0+; possibly an
// included build per legacy lane) and wire the minecraft {} block against
// the FG6 API (FG6 run configs use workingDirectory/property).
plugins {
    `java-library`
    `maven-publish`
    jacoco
}

val minecraftVersion: String = requireNotNull(project.findProperty("minecraft_version")?.toString()) {
    "everlastingskins.java8-forge-module: 'minecraft_version' must be set in this subproject's gradle.properties"
}
val modVersion: String = project.findProperty("mod_version")?.toString() ?: "1.0.0"

version = modVersion

base {
    archivesName = "everlastingskins-${minecraftVersion}"
}

java {
    // Java 8 bytecode for the legacy lane, same as :common.
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(8)
    options.compilerArgs.add("-Xlint:-options")
    options.compilerArgs.add("-Werror")
}

dependencies {
    implementation(project(":common"))
    // TODO(port 1.16.5/1.20.1): apply ForgeGradle (5.1+/6.x) + minecraft {
    // mappings / runs / deps } block lands with the per-subproject port PR.
}
