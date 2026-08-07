// forge-26.2 lane (Minecraft 26.2, Forge 65.0.9, Java 25, unobfuscated MC).
// Mirror of forge-1.21/build.gradle.kts: all versioning lives in
// gradle.properties, never in this script (project convention).
plugins {
    id("everlastingskins.forge-module")
    id("everlastingskins.dependency-analysis")
}

// EventBus 7 validator annotation processor (Rule 4 lane decision, build-only):
// EventBus 7.0.x (Forge 61.0.13 'Bump EventBus to 7.0.1') requires the
// net.minecraftforge:eventbus-validator AP so invalid @SubscribeEvent
// signatures fail at build time instead of at runtime. Module-local ONLY —
// the 1.21.x modules run EventBus 6 and must not get the validator.
// Pinned to 7.0.5, the version the 26.2-65.0.9 MDK ships (7.0.1 was the
// 26.1-era pin).
dependencies {
    annotationProcessor("net.minecraftforge:eventbus-validator:7.0.5")
}

// EventBus 7 strict runtime checks (26.x MDK parity): the MDK sets
// eventbus.api.strictRuntimeChecks=true on every run; the forge-module
// convention's shared runs block must stay untouched so 1.21.x keeps its
// EventBus 6 behavior, hence the lane-local reconfiguration.
minecraft {
    runs {
        configureEach {
            systemProperty("eventbus.api.strictRuntimeChecks", "true")
        }
    }
}
