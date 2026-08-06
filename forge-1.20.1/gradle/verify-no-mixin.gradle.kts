// no-mixin gate (standalone copy of the deleted buildSrc convention
// no-mixin.gradle.kts, which this standalone build cannot use).
//
// Mixin policy: this lane contains ZERO Mixin annotations and ZERO Mixin
// Gradle plugin usage. The gate scans this project's build files + all
// source roots (including the shared :common sources) and fails `build` on
// any violation.
//
// This file lives OUTSIDE the scanned file set (only build.gradle(.kts),
// settings.gradle(.kts) and gradle.properties are scanned) on purpose: the
// ban list below contains the very literal strings it detects, so a copy of
// the gate inside build.gradle.kts self-reported on every run.
import org.gradle.api.GradleException
import org.gradle.api.tasks.SourceSetContainer

val bannedLiterals = listOf(
    "mixingradle coordinate" to "org.spongepowered:mixingradle",
    "mixin plugin id" to "org.spongepowered.mixin",
    "mixin config bundling" to "everlastingskins.mixins.json",
)

val bannedPatterns = listOf(
    "Mixin annotation" to Regex("@Mixin\\b"),
    "mixin block" to Regex("^\\s*mixin\\s*\\{"),
)

fun scanFile(file: java.io.File, offenders: MutableList<String>) {
    if (!file.isFile) return
    val text = file.readText()
    for ((label, literal) in bannedLiterals) {
        if (text.contains(literal)) {
            offenders += "${file.relativeTo(projectDir)}: $label reference ('$literal')"
        }
    }
    for ((label, pattern) in bannedPatterns) {
        pattern.findAll(text).forEach { match ->
            offenders += "${file.relativeTo(projectDir)}: $label at line ${text.substring(0, match.range.first).count { it == '\n' } + 1}"
        }
    }
}

val verifyNoMixin = tasks.register("verifyNoMixin") {
    group = "verification"
    description = "Fails the build if any Mixin usage is detected: annotations, mixingradle, mixin plugin id, mixin {} blocks, or mixins.json bundling."

    val projectDir = project.projectDir
    val buildFiles = listOf(
        projectDir.resolve("build.gradle"),
        projectDir.resolve("build.gradle.kts"),
        projectDir.resolve("settings.gradle"),
        projectDir.resolve("settings.gradle.kts"),
        projectDir.resolve("gradle.properties"),
    ).filter { it.isFile }

    doLast {
        val offenders = mutableListOf<String>()

        // All source + resource files of every source set (main, test, ...
        // plus the shared :common source dirs added above).
        val sourceSets = project.extensions.findByType(SourceSetContainer::class.java)
        val sourceRoots = (sourceSets?.flatMap { it.allSource.srcDirs } ?: emptyList())
            .filter { it.exists() }
        sourceRoots.forEach { root ->
            root.walkTopDown()
                .filter { it.isFile && (it.extension == "java" || it.extension == "json" || it.extension == "properties") }
                .forEach { scanFile(it, offenders) }
        }
        buildFiles.forEach { scanFile(it, offenders) }

        if (offenders.isNotEmpty()) {
            throw GradleException(
                "Mixin policy violated:\n" +
                    offenders.joinToString("\n") +
                    "\n\nMixin policy: the lane contains ZERO Mixin usage. " +
                    "If a lane requires Mixin support, fork the lane - it is a sign " +
                    "that should not happen in the shared core."
            )
        }
        logger.lifecycle("Mixin check passed: zero Mixin annotations, zero mixingradle references.")
    }
}
