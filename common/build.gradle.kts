// :common — the version-independent core (Java 8 bytecode, runs on 1.12.2
// through 1.21.8 and beyond). Consumed by every forge-* module via
// implementation(project(":common")).
plugins {
    `java-library`
    // no-mixin gate (buildSrc): registers verifyNoMixin, wired into `build`.
    // Same direct application as the parent's /common (build-logic M2 step 2).
    id("no-mixin")
    // ErrorProne static analysis (buildSrc): hooks every JavaCompile.
    id("everlastingskins.errorprone")
    id("everlastingskins.dependency-analysis")
}

java {
    toolchain {
        languageVersion.set(
            JavaLanguageVersion.of(
                providers.gradleProperty("java.toolchain.version").getOrElse("21")
            )
        )
    }
    // --release 8 (below) is the single authoritative gate for both source
    // level and bytecode target; setting sourceCompatibility/targetCompatibility
    // here would make javac pass -source/-target and trip -Werror on the
    // "obsolete options" warning.
}

// TestConfigSupport (P2-3) lives in this module's test tree as the canonical
// copy for the forge lanes' selective srcDir share, but it is forge-bound
// (nightconfig's CommentedConfig arrives on forge lane classpaths only):
// :common itself must not compile it.
sourceSets {
    test {
        java {
            exclude("levosilimo/everlastingskins/permission/**")
        }
    }
}

repositories {
    mavenCentral()
    // com.mojang:authlib lives on Mojang's maven; both consumers already ship
    // authlib at runtime (Minecraft dependency), so this is compile-only here.
    maven("https://libraries.minecraft.net/")
}

dependencies {
    // --- compile-only: provided by both consumers' runtime classpaths ---
    // Version catalog (gradle/libs.versions.toml) is the single source of
    // truth for shared dependency versions; see its header for the per-library
    // version decisions.
    // Gson floor = Minecraft 1.12.2's bundled 2.8.0; only pre-2.8 APIs are used.
    compileOnly(libs.gson)
    // authlib floor = the 1.12.2-era jar; only Property(String,String,String)/getValue used.
    compileOnly(libs.authlib)
    // log4j2 floor = Minecraft 1.12.2's bundled 2.8.1 (LogManager.getLogger API is stable).
    compileOnly(libs.log4j.api)
    // javax.annotation.Nullable (jsr305); annotation-only, no runtime impact.
    compileOnly(libs.jsr305)

    // --- test: standalone verification of the module ---
    // JUnit versions come from the junit-bom platform (launcher included).
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.jqwik.api)
    testImplementation(libs.mockito.core)
    // The module's main code paths use Gson/authlib/log4j at runtime too.
    testImplementation(libs.gson)
    testImplementation(libs.authlib)
    testImplementation(libs.log4j.api)

    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.jqwik.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    // Default log4j2 config (ERROR-only) so test output stays quiet.
    testRuntimeOnly(libs.log4j.core)
}

tasks.withType<JavaCompile>().configureEach {
    // Explicit --release 8: disallows Java-9+ language features AND newer
    // java.base APIs; the same jar must run on Java 8 (mc1.12.2) and Java 21.
    options.release.set(8)
    options.compilerArgs.add("-Xlint:unchecked")
    // JDK 20+ deprecates source/target 8 and warns about obsolete options even
    // with --release 8; silence that [options]-category noise while -Werror
    // still gates real code warnings.
    options.compilerArgs.add("-Xlint:-options")
    options.compilerArgs.add("-Werror")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}
