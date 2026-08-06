// no-mixin convention plugin (precompiled script plugin, id "no-mixin").
//
// Mixin policy: this project and every consumer (forge subprojects) contain
// ZERO Mixin annotations and ZERO Mixin Gradle plugin usage. The Mixin Gradle
// plugin (mixingradle) was dropped because it is incompatible with the newer
// Gradle line used by the 1.21.x point-release builds (PR #251) and because
// there are no active Mixins anyway (empty mixins/client/server arrays).
//
// This plugin registers the verifyNoMixin gate and wires it into `build`.
// It is applied by /common itself and is the file consumers copy into their
// own buildSrc/ (M2 step 2) or apply via an included build.
//
// Note: the banned strings below are written in escaped-regex form on
// purpose so this file never contains the literal strings it scans for.
//
// Configuration-cache rules enforced here (Gradle 9):
//  - nothing script-level may be referenced from the task action (script
//    object references cannot be serialized), so the scan constants and
//    the scan logic live inside doLast as execution-time locals;
//  - `project` may not be touched at execution time, so the source-set
//    roots are captured at configuration time as plain File values.
import org.gradle.api.GradleException
import org.gradle.api.tasks.SourceSetContainer

val verifyNoMixin = tasks.register("verifyNoMixin") {
    group = "verification"
    description = "Fails the build if any Mixin usage is detected: annotations, mixingradle, mixin plugin id, mixin {} blocks, or mixins.json bundling."

    // Plain serializable values captured at configuration time.
    val projectDir = project.projectDir
    val buildFiles = listOf(
        projectDir.resolve("build.gradle"),
        projectDir.resolve("build.gradle.kts"),
        projectDir.resolve("settings.gradle"),
        projectDir.resolve("settings.gradle.kts"),
        projectDir.resolve("gradle.properties"),
    ).filter { it.isFile }

    // All source + resource files of every source set (main, test, ...).
    // Consumers are forge subprojects (always apply java/java-library),
    // but stay safe if the plugin is applied to a non-Java project.
    val sourceRoots: List<File> = (project.extensions.findByType(SourceSetContainer::class.java)
        ?.flatMap { it.allSource.srcDirs } ?: emptyList())
        .filter { it.exists() }

    doLast {
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

        val offenders = mutableListOf<String>()
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
                    "\n\nMixin policy: /common and its consumers contain ZERO Mixin usage. " +
                    "If a consumer requires Mixin support, fork the consumer - it is a sign " +
                    "that should not happen in /common."
            )
        }
        logger.lifecycle("Mixin check passed: zero Mixin annotations, zero mixingradle references.")
    }
}

tasks.named("build") {
    dependsOn(verifyNoMixin)
}
