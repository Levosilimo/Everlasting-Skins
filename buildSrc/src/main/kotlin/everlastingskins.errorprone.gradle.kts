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

// Test/gametest source sets: main-only gate for this rollout. The test
// suite carries pre-existing warning debt (fire-and-forget futures,
// non-static inner classes, unclosed Files streams ...) that trips
// :common's -Werror and would block the integration entirely. Cleaning
// that debt is a follow-up, not part of this change.
tasks.matching { it.name == "compileTestJava" || it.name == "compileGametestJava" }
    .configureEach {
        (this as JavaCompile).options.errorprone.enabled.set(false)
    }
