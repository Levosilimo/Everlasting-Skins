// everlastingskins.errorprone — Tier 1 static analysis for the M2 root.
// Hooks ErrorProne into every JavaCompile task (compile-time checks, zero
// extra step). Applied by the forge-module convention (all forge-* lanes)
// and directly by :common.
//
// The plugin version is pinned in buildSrc/build.gradle.kts (apply false);
// the ErrorProne core version lives here, on the `errorprone` configuration.

import net.ltgt.gradle.errorprone.errorprone

plugins {
    id("net.ltgt.errorprone")
}

dependencies {
    // The gradle-errorprone-plugin does NOT bundle ErrorProne itself; the
    // core must be declared on the `errorprone` configuration. 2.50.0 is the
    // current stable release and requires a JDK 21 (or newer) toolchain to
    // run — this build's toolchain is 21.
    errorprone("com.google.errorprone:error_prone_core:2.50.0")
}

tasks.withType<JavaCompile>().configureEach {
    // Annotation-processor output (e.g. the Mixin AP) is not ours to lint.
    options.errorprone.disableWarningsInGeneratedCode.set(true)
    // Checks that produce false positives on this codebase; suppressed at
    // rollout rather than polluting every compile with noise.
    options.errorprone.disable("IdentityBinaryExpression", "StringSplitter")
}

// Test/gametest source sets: ErrorProne is ENABLED everywhere by default —
// :common's test debt is fixed and its -Werror gate keeps it that way.
// The forge-module test suites carry pre-existing warning debt
// (fire-and-forget futures, non-static inner classes, unclosed Files
// streams ...) owned by a parallel lane that is actively adding forge
// tests, so their test/gametest compiles stay disabled here until that
// debt is cleared. Gate is by project name: :common is the only non-forge
// applier of this plugin.
// TODO(forge-tests): delete this gate once the forge test debt is gone;
// :common's test compiles must stay ErrorProne-enabled.
if (project.name != "common") {
    tasks.matching { it.name == "compileTestJava" || it.name == "compileGametestJava" }
        .configureEach {
            (this as JavaCompile).options.errorprone.enabled.set(false)
        }
}
