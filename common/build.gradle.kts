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

repositories {
    mavenCentral()
    // com.mojang:authlib lives on Mojang's maven; both consumers already ship
    // authlib at runtime (Minecraft dependency), so this is compile-only here.
    maven("https://libraries.minecraft.net/")
}

dependencies {
    // --- compile-only: provided by both consumers' runtime classpaths ---
    // Gson floor = Minecraft 1.12.2's bundled 2.8.0; only pre-2.8 APIs are used.
    compileOnly("com.google.code.gson:gson:2.8.0")
    // authlib floor = the 1.12.2-era jar; only Property(String,String,String)/getValue used.
    compileOnly("com.mojang:authlib:1.5.25")
    // log4j2 floor = Minecraft 1.12.2's bundled 2.8.1 (LogManager.getLogger API is stable).
    compileOnly("org.apache.logging.log4j:log4j-api:2.8.1")
    // javax.annotation.Nullable (jsr305); annotation-only, no runtime impact.
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")

    // --- test: standalone verification of the module ---
    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("net.jqwik:jqwik:1.9.0")
    testImplementation("org.mockito:mockito-core:5.12.0")
    // The module's main code paths use Gson/authlib/log4j at runtime too.
    testImplementation("com.google.code.gson:gson:2.8.0")
    testImplementation("com.mojang:authlib:1.5.25")
    testImplementation("org.apache.logging.log4j:log4j-api:2.8.1")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.3")
    // Default log4j2 config (ERROR-only) so test output stays quiet.
    testRuntimeOnly("org.apache.logging.log4j:log4j-core:2.8.1")
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
