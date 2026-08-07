// everlastingskins.dependency-analysis — WARN-only "knip" for the Forge line.
// Applies com.autonomousapps.dependency-analysis (version comes from the
// buildSrc classpath, never declared here) and runs every check at warn
// severity so the build can NEVER fail on a dependency-health finding.
//
// Why WARN-only: Forge is reflection-heavy. @ObjectHolder registry entries,
// @EventBusSubscriber static subscribers, model loaders and string-based
// resource registration are invisible to static analysis, so buildHealth
// reliably reports false positives on this codebase. Mojang's authlib/log4j
// transitives get flagged as duplicates too; that is Kombucha (in-plugin
// dedup) hygiene, reviewed separately. Mixin is a non-issue here: the
// no-mixin gate already forbids @Mixin anywhere in the repo.
//
// Conventions (the "structure rules" this plugin deliberately does NOT add —
// Forge reflection cannot be modeled as rule entries):
//  - never gate CI on buildHealth: it is a human-readable hygiene report,
//    run on demand via ./gradlew buildHealth (root) / projectHealth (module);
//  - never run fixDependencies automatically: the unused-dependency detector
//    is defeated by the reflection above, so an auto-fix would delete live
//    entries. Any fix is a manual, reviewed change (AGENTS.md policy).
//
// Note: 3.x removed the old `abortBuildOnFalsePositives` flag; the modern
// equivalent is issues { all { onAny { severity("warn") } } } below, which
// also governs the buildHealth aggregate the plugin registers on the root
// project once any module applies this convention.

import com.autonomousapps.DependencyAnalysisSubExtension
import com.autonomousapps.tasks.ProjectHealthTask

plugins {
    id("com.autonomousapps.dependency-analysis")
}

// Every issue category (unused dependencies, wrong configurations, undeclared
// transitives, duplicate classes, ...) at warn severity. "fail" is the plugin
// default; we never want a dependency-health finding to break a build.
//
// NOTE: use DependencyAnalysisSubExtension here, not
// DependencyAnalysisExtension — the latter is registered only on the ROOT
// project (RootPlugin); subprojects get the Sub extension. The lane-2 version
// used the root type and failed at apply time on :common ("Extension of type
// 'DependencyAnalysisExtension' does not exist"). The per-project issues{}
// handler is ProjectIssueHandler, which has onAny directly (no all{} wrapper
// — that lives on the root IssueHandler).
extensions.configure<DependencyAnalysisSubExtension> {
    issues {
        onAny {
            severity("warn")
        }
    }
}

// Deterministic report output: fixed relative path under build/, independent
// of the project dir name. The plugin default is already this path; pinning
// it here guards against default drift and keeps CI artifact paths stable.
// withType+configureEach (not tasks.named): the plugin registers
// projectHealth conditionally/deferred, so a named() lookup at apply time
// fails with "Task with name 'projectHealth' not found".
tasks.withType<ProjectHealthTask>().configureEach {
    consoleReport.set(layout.buildDirectory.file("reports/dependency-analysis/project-health-report.txt"))
}
