// everlastingskins.forge-module — convention plugin for the ForgeGradle 7+
// (Java 21 toolchain) modules: forge-1.21 and the 1.21.x point releases.

import com.autonomousapps.tasks.AbiAnalysisTask
import com.autonomousapps.tasks.ClassListExploderTask
import java.text.SimpleDateFormat
import java.util.Date
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.language.jvm.tasks.ProcessResources

// Encapsulates the former root build.gradle of the 1.21 project; each
// subproject's build.gradle.kts is just `plugins { id("...") }` plus
// subproject-specific minecraft/forge versions in its gradle.properties.
//
// Deliberately NO mixingradle (org.spongepowered.mixin) plugin — mixins are
// handled by the annotation processor + jar manifest only (Lane C).
//
// FG version is managed in buildSrc/build.gradle.kts (implementation
// dependency) — precompiled script plugins cannot carry plugin versions.
plugins {
    `java-library`
    `maven-publish`
    jacoco
    id("net.minecraftforge.gradle")
    // no-mixin gate (buildSrc): registers verifyNoMixin, wired into `build`.
    // Any subproject applying this convention gets the Mixin-usage gate.
    id("no-mixin")
    // ErrorProne static analysis (buildSrc): hooks every JavaCompile.
    id("everlastingskins.errorprone")
}

// Shared dependency versions: single source of truth is the root build's
// version catalog (gradle/libs.versions.toml). Precompiled script plugins are
// compiled inside buildSrc (which has no catalog of its own), so the generated
// `libs` accessor is NOT available here — read the applied project's catalog
// (the root build's) through the programmatic API instead.
val libs = the<VersionCatalogsExtension>().named("libs")

// NOTE: property reads here use project.findProperty (NOT
// providers.gradleProperty) — in precompiled buildSrc plugins the latter
// resolves against the build-root scope and misses subproject-level
// gradle.properties.
val minecraftVersion: String = requireNotNull(project.findProperty("minecraft_version")?.toString()) {
    "everlastingskins.forge-module: 'minecraft_version' must be set in this subproject's gradle.properties"
}
val forgeVersion: String = requireNotNull(project.findProperty("forge_version")?.toString()) {
    "everlastingskins.forge-module: 'forge_version' must be set in this subproject's gradle.properties"
}
val modVersion: String = project.findProperty("mod_version")?.toString() ?: "1.0.0"
val modName: String = project.findProperty("mod_name")?.toString() ?: "EverlastingSkins"
val modVendor: String = project.findProperty("mod_vendor")?.toString() ?: "Levosilimo"
val toolchainVersion: Int = project.findProperty("java.toolchain.version")?.toString()?.toInt() ?: 21
val mcRunDir: String = project.findProperty("mc.runDir")?.toString() ?: "run"
val mcRunDir2: String = project.findProperty("mc.runDir2")?.toString() ?: "run2"
val gametestNamespace: String = project.findProperty("gametestNamespace")?.toString() ?: "everlastingskins"

// Minecraft 26.1+ ships unobfuscated (Forge 62.0.0); when true the
// mappings() call is skipped (see the minecraft block below) and FG
// 7.0.17's unobfuscated-UserDev wiring applies.
val unobfuscated: Boolean = project.findProperty("minecraft.unobfuscated")?.toString()?.toBoolean() == true

version = modVersion

base {
    archivesName = "everlastingskins-${minecraftVersion}"
}

jacoco {
    // 0.8.11 supports up to Java 23 class files; the Java 25 toolchain
    // lanes (26.x) need 0.8.13+. Gated by toolchain so the 1.21.x modules
    // keep the pinned 0.8.11 unchanged.
    toolVersion = if (toolchainVersion >= 25) "0.8.13" else "0.8.11"
}

repositories {
    minecraft.mavenizer(this)
    maven(fg.forgeMaven)
    maven(fg.minecraftLibsMaven)
    maven("https://modmaven.dev") { name = "ModMaven" }
    maven("https://minecraft.curseforge.com/api/maven/") { name = "CurseForge" }
    maven("https://www.cursemaven.com") { name = "CurseMaven" }
    maven("https://maven.minecraftforge.net/") { name = "Forge" }
    maven("https://nexus.scarsz.me/content/groups/public/") { name = "DiscordSRV" }
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/") { name = "PlaceholderAPI" }
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") { name = "Spigot" }
    maven("https://repo.papermc.io/repository/maven-public/") { name = "PaperMC" }
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(toolchainVersion))
    }
    sourceCompatibility = JavaVersion.toVersion(toolchainVersion)
    targetCompatibility = JavaVersion.toVersion(toolchainVersion)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(toolchainVersion)
}

// FG 7.0.17 unobfuscated-UserDev wiring points compileJava.classpath at the
// raw resolvable userdev configuration; the configuration cache cannot
// serialize that configuration object (the classic userdev path materializes
// a file collection instead). Re-materialize as a plain file collection so
// :<module>:compileJava stores a valid configuration-cache entry. Gated to
// the unobfuscated lanes; 1.21.x keeps the classic wiring untouched.
if (unobfuscated) {
    gradle.projectsEvaluated {
        tasks.withType<JavaCompile>().configureEach {
            classpath = files(classpath)
            // The annotationProcessorPath is captured by a plugin's
            // configureEach lambda as part of the CompileOptions bean;
            // re-materialize it as RESOLVED files (files(config) would keep
            // the configuration as the collection origin, which is exactly
            // what the configuration cache cannot serialize).
            val apPath = options.annotationProcessorPath
            if (apPath != null) {
                options.annotationProcessorPath = files(apPath.files)
            }
        }
    }
}

// Game test source set: separate from unit tests because it compiles and runs
// against the real Minecraft server (game test framework), not JUnit.
// Declared before the runs block, which references it.
sourceSets {
    create("gametest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += output + sourceSets.main.get().output
        // Shared GameTest helper (lib-47 packet-arrival flake): PacketAssert
        // lives in its own package dir under forge-test-shared so this share
        // pulls in only version-independent classes. It imports only
        // net.minecraft.gametest.framework.* (stable across 1.21.x and 26.2)
        // — unlike I18nUtilsTest/ConfigTest it has no forge21.* imports, so
        // the share is NOT gated on minecraft.unobfuscated like the test
        // source-set share below.
        java.srcDir("$rootDir/forge-test-shared/src/test/java/levosilimo/everlastingskins/gametest")
    }
    // Shared LuckPerms API test stubs live in :common's test source-set
    // (canonical copy; the forge lanes deleted their duplicates).
    test {
        java.srcDir("$rootDir/common/src/test/java/net/luckperms")
        // Shared TestConfigSupport (P2-3): canonical copy in :common's
        // permission test package; the 7 lane copies are deleted.
        java.srcDir("$rootDir/common/src/test/java/levosilimo/everlastingskins/permission")
        // Shared forge-bound tests (P3-5): I18nUtilsTest + ConfigTest are
        // byte-identical across the 4 1.21.x lanes and import forge21.*
        // bindings (cannot live in :common); canonical copies live in the
        // root forge-test-shared dir, the 8 lane copies are deleted.
        // Gated to the mapped 1.21.x lanes: 26.x lanes bind I18nUtils into
        // their own package (forge26.*) so the forge21.* import cannot
        // resolve there (Build (26.2) failed #350 with cannot-find-symbol).
        if (!unobfuscated) {
            java.srcDir("$rootDir/forge-test-shared/src/test/java")
        }
    }
}

configurations {
    getByName("gametestImplementation").extendsFrom(getByName("implementation"))
    getByName("gametestCompileOnly").extendsFrom(getByName("compileOnly"))
    getByName("gametestRuntimeOnly").extendsFrom(getByName("runtimeOnly"))
}

minecraft {
    // Minecraft 26.1+ ships unobfuscated (Forge 62.0.0): mappings are a
    // no-op there and there is no official-mappings artifact for 26.x to
    // resolve. Gate the mappings() call behind minecraft.unobfuscated
    // (default false) so the 1.21.x modules keep official mappings and the
    // 26.2 lane skips the call entirely (FG 7.0.15+ defaults the channel to
    // official anyway; FG 7.0.17 added explicit unobfuscated-UserDev support).
    if (!unobfuscated) {
        mappings("official", minecraftVersion)
    }

    // FG 7.0.2x+ (SlimeLauncher): accessTransformer is a ConfigurableFileCollection
    // with a boolean setter that enables the default AT file from resources
    // (src/main/resources/META-INF/accesstransformer.cfg).
    setAccessTransformer(true)

    runs {
        configureEach {
            workingDir = layout.projectDirectory.dir(mcRunDir)
            systemProperty("forge.logging.console.level", "debug")
            systemProperty("fml.earlyprogresswindow", "false")
            systemProperty("mixin.env.disableRefMap", "true")
        }

        create("client") {
            systemProperty("mixin.env.remapRefMap", "true")
            systemProperty("mixin.env.refMapRemappingFile", "${layout.projectDirectory.asFile}/build/createSrgToMcp/output.srg")
            systemProperty("forge.logging.markers", "REGISTRYDUMP")

            // Real-client E2E (master plan slice 3, modern-injar pattern):
            // the in-jar driver/hook are shipped-gated by
            // -Deverlastingskins.e2e=true; the E2E wrapper passes
            // -Peverlastingskins.e2e=true and the run definition forwards it
            // to the forked client JVM (default false — never active in
            // normal dev runs). Same Netty reflective-access flags as the
            // gameTestServer run: the dev client also runs Netty on Java
            // 21+ and hits the same InaccessibleObjectException without them.
            systemProperty(
                "everlastingskins.e2e",
                providers.gradleProperty("everlastingskins.e2e").orElse("false").get()
            )
            jvmArgs(
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.base/java.nio=ALL-UNNAMED",
                "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED",
                "--add-opens=java.base/java.nio=io.netty.common",
                "--add-opens=java.base/java.nio=io.netty.buffer",
                "--add-opens=java.base/java.nio=io.netty.transport",
                "--add-opens=java.base/java.nio=io.netty.handler",
                "--add-opens=java.base/java.nio=io.netty.codec",
                "--add-opens=java.base/java.nio=io.netty.resolver",
                "--add-opens=java.base/sun.nio.ch=io.netty.common",
                "--add-opens=java.base/sun.nio.ch=io.netty.transport",
                "--add-exports=java.base/jdk.internal.misc=io.netty.common",
                "--add-exports=java.base/jdk.internal.misc=io.netty.buffer",
                "--add-exports=java.base/jdk.internal.misc=io.netty.transport",
                "--add-exports=java.base/jdk.internal.misc=io.netty.handler",
                "--add-exports=java.base/jdk.internal.misc=io.netty.codec",
                "--add-exports=java.base/jdk.internal.misc=io.netty.resolver",
                "-Dio.netty.tryReflectionSetAccessible=false"
            )
        }

        create("server") {
            workingDir = layout.projectDirectory.dir("server")
            systemProperty("mixin.env.disableRefMap", "true")
            systemProperty("mixin.env.refMapRemappingFile", "${layout.projectDirectory.asFile}/build/createSrgToMcp/output.srg")
            systemProperty("forge.logging.markers", "SCAN,REGISTRIES,REGISTRYDUMP")
        }

        create("client2") {
            workingDir = layout.projectDirectory.dir(mcRunDir2)
            args("--username", "PlayerB")
            args("--uuid", "d1b05a3e-9909-3e16-95e5-7eef2d6c713b")
        }

        // Forge ships a built-in gameTestServer run definition (launchTarget
        // forge_userdev_server_gametest); ForgeGradle names the task
        // runGameTestServer. FG7 auto-includes every project source set
        // (main + gametest) in dev runs, so the @GameTestHolder classes are
        // scanned and structure templates load from the dev mod datapack.
        create("gameTestServer") {
            systemProperty("forge.enabledGameTestNamespaces", gametestNamespace)
            // Netty 4.1 needs reflective access into the JDK on Java 21
            // (GameTest job failed with "Reflective setAccessible(true)
            // disabled" / module exports). Applied to the forked GameTest
            // server JVM, not the Gradle daemon.
            //
            // ALL-UNNAMED alone is not enough: FG6 dev loads Netty as NAMED
            // JPMS modules (io.netty.*) inside Forge's SecureModuleClassLoader,
            // and the JVM refuses "does not open java.nio / export
            // jdk.internal.misc to module io.netty.common" for those. Open and
            // export the same packages to each Netty module the dev runtime
            // loads; flags for modules absent from the layer are ignored.
            //
            // NOTE: startup --add-opens cannot reach modules Forge defines in a
            // runtime module layer (io.netty.common lives in the SECURE-BOOTSTRAP
            // layer, created after JVM start), so PlatformDependent still cannot
            // open java.nio reflectively there. tryReflectionSetAccessible=false
            // makes Netty skip that doomed attempt entirely instead of throwing
            // and logging InaccessibleObjectException at every boot; the fallback
            // (heap buffers / no-unsafe paths) is what the current environment
            // ends up with either way.
            jvmArgs(
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.base/java.nio=ALL-UNNAMED",
                "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED",
                "--add-opens=java.base/java.nio=io.netty.common",
                "--add-opens=java.base/java.nio=io.netty.buffer",
                "--add-opens=java.base/java.nio=io.netty.transport",
                "--add-opens=java.base/java.nio=io.netty.handler",
                "--add-opens=java.base/java.nio=io.netty.codec",
                "--add-opens=java.base/java.nio=io.netty.resolver",
                "--add-opens=java.base/sun.nio.ch=io.netty.common",
                "--add-opens=java.base/sun.nio.ch=io.netty.transport",
                "--add-exports=java.base/jdk.internal.misc=io.netty.common",
                "--add-exports=java.base/jdk.internal.misc=io.netty.buffer",
                "--add-exports=java.base/jdk.internal.misc=io.netty.transport",
                "--add-exports=java.base/jdk.internal.misc=io.netty.handler",
                "--add-exports=java.base/jdk.internal.misc=io.netty.codec",
                "--add-exports=java.base/jdk.internal.misc=io.netty.resolver",
                "-Dio.netty.tryReflectionSetAccessible=false"
            )
        }
    }
}

// Minecraft 1.21's Settings loader opens server.properties with no
// exists() check; a missing file logs NoSuchFileException + ERROR on every
// GameTest boot. Seed the run directory with a default server.properties so
// the GameTest server starts clean (vanilla defaults; the server rewrites
// the file on shutdown anyway).
tasks.matching { it.name == "runGameTestServer" }.configureEach {
    // FG7 removed the FG6 `mods {}` wiring that put the gametest source set
    // into the dev mod; put its output (classes + structure-template datapack
    // resources) on the run classpath so @GameTestHolder classes are scanned
    // and the empty.nbt template loads.
    (this as JavaExec).classpath += project.sourceSets.getByName("gametest").output

    // Configuration-cache compat (#289): the doFirst action is serialized
    // and replayed at execution time, where `project`/`layout` (script
    // receiver) are unavailable. Resolve the run dir and the
    // server.properties template eagerly here as plain serializable
    // File values instead of touching layout inside doFirst.
    val runDir = layout.projectDirectory.dir(mcRunDir).asFile
    val serverPropertiesTemplate = layout.projectDirectory.file("test-infrastructure/server.properties").asFile

    doFirst {
        runDir.mkdirs()
        val props = runDir.resolve("server.properties")
        if (!props.exists() && serverPropertiesTemplate.exists()) {
            props.writeText(serverPropertiesTemplate.readText())
        }
    }
}

dependencies {
    // :common is the shared version-independent core (M2); every forge-*
    // module consumes it unconditionally. An opt-out gate existed as a
    // safety valve for forge-1.21's JPMS split-package issue (#265); it
    // became dead after the Option B1 relocation to forge21.* (#268)
    // resolved the conflict — no module ever opted out. If a future
    // forge-* lane needs vendored :common copies for tooling reasons,
    // re-add the gate + opt-out property here.
    api(project(":common"))
    api(minecraft.dependency("net.minecraftforge:forge:${minecraftVersion}-${forgeVersion}"))
    // Guava on the annotation processor path keeps ErrorProne stable (it
    // previously shadowed the OLD Guava bundled in mixin-0.8.7-processor.jar).
    annotationProcessor(libs.findLibrary("guava").get())

    compileOnly(libs.findLibrary("luckperms-api").get())

    // DiscordSRV + PlaceholderAPI hook deps are 1.21.x-only (FIX-3c): neither
    // mod ships a Forge build for MC 26.1/26.2 (RES-3), so the 26.x lanes
    // carry no hook code and must not resolve these. The four 1.21.x lanes
    // DO use them (DiscordSrvHook / PlaceholderApiHook + integration tests).
    if (project.name != "forge-26.1" && project.name != "forge-26.2") {
        compileOnly(libs.findLibrary("placeholderapi").get())
        compileOnly(libs.findLibrary("discordsrv").get())
    }

    // slf4j-api MUST appear before discordsrv in the classpath order.
    // discordsrv bundles unrelocated SLF4J 1.x classes (org/slf4j/LoggerFactory)
    // that lack the getProvider() method required by SLF4J 2.x. The declaration
    // order within testImplementation determines classpath ordering.
    testImplementation(libs.findLibrary("slf4j-api").get())
    if (project.name != "forge-26.1" && project.name != "forge-26.2") {
        testImplementation(libs.findLibrary("discordsrv").get())
        testImplementation(libs.findLibrary("placeholderapi").get())
    }
    testImplementation(libs.findLibrary("paper-api").get())
    compileOnly(libs.findLibrary("spigot-api").get())

    // JUnit versions come from the junit-bom platform (launcher included).
    testImplementation(platform(libs.findLibrary("junit-bom").get()))
    testImplementation(libs.findLibrary("junit-jupiter-api").get())
    testImplementation(libs.findLibrary("junit-jupiter-params").get())
    testImplementation(libs.findLibrary("jqwik-api").get())
    testImplementation(libs.findLibrary("mockito-core").get())
    testImplementation(libs.findLibrary("mockito-junit-jupiter").get())
    // mockito's stock byte-buddy can lag Java 25 class-file support (the
    // 5.12.0 pin in main bundles 1.14.x, which parses only up to Java 22 /
    // major 66). The unobfuscated 26.x lanes run their tests on the Java 25
    // toolchain (major 69), so every mock of a Minecraft class dies with
    // "Java 25 (69) is not supported by the current version of Byte Buddy"
    // (verified on forge-26.1, 2026-08-13). Override both byte-buddy
    // artifacts to 1.17.7 (Java 25 support) on those lanes only; 1.21.x
    // (Java 21 toolchain) keeps the stock byte-buddy. Version bump of an
    // already-transitive coordinate, no new Maven coords.
    if (unobfuscated) {
        testImplementation("net.bytebuddy:byte-buddy:1.17.7")
        testImplementation("net.bytebuddy:byte-buddy-agent:1.17.7")
    }

    testRuntimeOnly(libs.findLibrary("junit-jupiter-engine").get())
    testRuntimeOnly(libs.findLibrary("jqwik-engine").get())
    testRuntimeOnly(libs.findLibrary("junit-platform-launcher").get())

    // junit-bom platform here too: gametestImplementation/gametestRuntimeOnly
    // extend implementation/runtimeOnly (not test*), so the BOM declared on
    // testImplementation does not reach them — without it the versionless
    // junit entries below would fail resolution.
    "gametestImplementation"(sourceSets.main.get().output)
    "gametestImplementation"(platform(libs.findLibrary("junit-bom").get()))
    "gametestImplementation"(libs.findLibrary("junit-jupiter-api").get())
    "gametestRuntimeOnly"(platform(libs.findLibrary("junit-bom").get()))
    "gametestRuntimeOnly"(libs.findLibrary("junit-platform-launcher").get())
}

// jsr305 is brought in transitively by Forge AND bundled by discordsrv;
// both publish javax/annotation/Nullable to the same FQN. Exclude the
// transitive copy so discordsrv's bundled class is the only one on the
// classpath (same split-package pattern as the historical #265 forge21.*
// fix). dep-analysis 3.18.0 surfaces this as a duplicate-class warning.
// The exclude applies only where discordsrv is declared (1.21.x, FIX-3c);
// the 26.x lanes resolve Forge's transitive jsr305 instead — the 26.x main
// code imports javax.annotation.Nullable, which the discordsrv hook dep
// used to bundle.
if (project.name != "forge-26.1" && project.name != "forge-26.2") {
    configurations.getByName("compileOnly").exclude(group = "com.google.code.findbugs", module = "jsr305")
    configurations.getByName("testImplementation").exclude(group = "com.google.code.findbugs", module = "jsr305")
}

configurations.getByName("testRuntimeClasspath") {
    exclude(group = "org.slf4j", module = "slf4j-simple")
}

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "net.sf.jopt-simple") {
            useVersion("5.0.4")
        }
        if (requested.group == "org.slf4j") {
            useVersion(libs.findVersion("slf4j").get().requiredVersion)
        }
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

// Force :common's evaluation whenever this module configures: under
// --configure-on-demand the jar task is realized at command-line task-name
// resolution — BEFORE :common is configured — so a from() reference to
// :common's tasks would fail with "Task with name 'classes' not found"
// (observed take-3, CoD + no-config-cache probe). evaluationDependsOn is
// the standard CoD-safe mechanism: it pulls :common's evaluation into the
// consumer's configuration phase, making the TaskProvider lookups below
// safe and keeping the implicit task dependency wired (no
// WorkValidationException).
evaluationDependsOn(":common")

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    // M2 regression fix (caught by the real-client E2E slice-3 server
    // boot): the pre-M2 single-module build produced a self-contained
    // mod jar, but the M2 split (:common as a separate module, api
    // dependency) made the jar THIN — :common classes/resources are
    // absent, so the mod throws NoClassDefFoundError
    // (IPermissionService etc.) on any production server (Forge loads
    // only mods/ + the game classpath). Bundle :common's compiled
    // output + resources into the shipped jar so every in-root forge-*
    // lane is self-contained again (the out-of-band lanes get this for
    // free via source-dir share).
    //
    // Lazy TaskProvider: realized at execution-graph time → forces
    // :common's configuration on demand and auto-wires the implicit
    // task dependency (fixes the WorkValidationException; safe under
    // --configure-on-demand + config cache). Do NOT revert to
    // from(project(":common").sourceSets...) — eager sourceSets access
    // fails at configuration time (Extension 'sourceSets' does not
    // exist: :common's Java plugin is not yet applied) and even a
    // project.provider{} wrap is forced by
    // ProviderBackedFileCollection.visitDependencies at graph-query
    // time (takes 1 and 2, both CI-red; the config-order-gate script
    // guards this regression signature).
    //
    // Content-bearing producers only: the lifecycle `classes` task has
    // NO outputs (verified on Gradle 9.3.1: from(classes) contributes
    // zero files — take-3 jars came out thin; a singleFile check on
    // classes.outputs.files fails the build outright), so compileJava +
    // processResources carry the actual outputs. The TaskProvider form
    // + evaluationDependsOn(:common) above is what makes the
    // configuration order safe; the producers here are what make the
    // jar fat.
    from(project(":common").tasks.named("compileJava"))
    from(project(":common").tasks.named("processResources")) // resources flattened too
    // Thin-jar tripwire: compileJava's outputs may carry more than one
    // declared output (errorprone wiring), so require ANY declared output
    // to exist rather than pinning a single file. Local val (not
    // script-level): the doFirst closure is replayed from the
    // configuration-cache entry where the script receiver is null (same
    // constraint as #289's runGameTestServer wiring) — a local captures
    // the serializable List<File> directly. evaluationDependsOn above
    // guarantees :common is configured, so the task lookup is safe.
    val commonClassesOutput: List<File> =
        project(":common").tasks.named("compileJava").get().outputs.files.toList()
    doFirst {
        check(commonClassesOutput.any { it.exists() }) {
            ":common:compileJava produced no output — thin-jar risk"
        }
    }
    manifest {
        attributes(
            "Timestamp" to System.currentTimeMillis(),
            "Specification-Title" to modName,
            "Specification-Vendor" to modVendor,
            "Specification-Version" to modVersion,
            "Implementation-Title" to "${modName}-${minecraftVersion}",
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to modVendor,
            "Implementation-Timestamp" to SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(Date()),
            "Built-On-Java" to "${System.getProperty("java.vm.version")} (${System.getProperty("java.vm.vendor")})",
            "Built-On" to forgeVersion
        )
    }
}

// mods.toml version templating: when gradle.properties sets mod_version, it is
// the single source of truth for the mods.toml version field — expand the
// ${version} placeholder in META-INF/mods.toml at processResources time (covers
// the gametest source set's resources too). Lanes whose mods.toml still
// hardcodes a literal version (no placeholder) are untouched: expand() is a
// no-op there and the file's existing value stands. Lanes without mod_version
// are likewise untouched (their file's existing value stands).
//
// Configuration-cache compat (same constraint as the #289 doFirst wiring and
// the thin-jar tripwire below): the filesMatching action is serialized and
// replayed at execution time, where the script receiver (this$0) is null — a
// script-level val would NPE there (CI-wide processResources failure, fixed
// here). Resolve the property eagerly into a plain lambda-local and capture
// only that, never the script instance.
tasks.withType<ProcessResources>().configureEach {
    val modVersion: String? = project.findProperty("mod_version")?.toString()
    if (modVersion != null) {
        inputs.property("version", modVersion)
        filesMatching("META-INF/mods.toml") {
            expand("version" to modVersion)
        }
    }
}

sourceSets.all {
    val dir = layout.buildDirectory.dir("sourcesSets/${name}")
    output.setResourcesDir(dir.get().asFile)
    java.destinationDirectory = dir
}

// Workaround for dependency-analysis-gradle-plugin #960:
// FG redirects every sourceSet's classes+resources output to
// build/sourcesSets/<name> (this convention's lines 321-325). The
// plugin's explodeByteCodeSourceMain + abiAnalysisMain tasks read
// build/sourcesSets/<name> but don't declare dependsOn processResources,
// so Gradle 9.3.1's strict implicit-dependency detection hard-fails
// :<module>:projectHealth. Maintainer-verified fix per #960: inject
// the missing wiring at projectsEvaluated time. Only fires when the
// dep-analysis plugin is actually applied to this module — its tasks
// won't exist otherwise and `withType` is a no-op.
gradle.projectsEvaluated {
    tasks.withType<ClassListExploderTask>().configureEach {
        dependsOn("processResources")
    }
    tasks.withType<AbiAnalysisTask>().configureEach {
        dependsOn("processResources")
    }
}
