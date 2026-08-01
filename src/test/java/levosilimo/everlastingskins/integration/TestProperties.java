package levosilimo.everlastingskins.integration;

import levosilimo.everlastingskins.util.CustomSkinProperty;

/**
 * Shared deterministic skin fixtures for the integration suite. Values are
 * base64-encoded JSON payloads shaped like real Mojang textures properties so
 * wire-level size assertions in {@code WireLevelBytesIT} reflect reality.
 */
public final class TestProperties {

    private static final String VALUE =
        "eyJ0aW1lc3RhbXAiOiIyMDI0LTAxLTAxVDAwOjAwOjAwWiIsInByb2ZpbGVJZCI6ImFiY2RlZjEyMzQ1Njc4OWFiY2RlZjEyMzQ1Njc4OTBhYmNkZWYxMjM0NTY3ODkwYWJjZGVmMTIzNDU2Nzg5MGFiY2RlZiIsInRleHR1cmVzIjp7IlNLSU4iOnsidXJsIjoiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS81YjZlOGY0OTJlMDU0NzBjYWIxODBlMzY4YmVhMDc2YyJ9fX0=";
    private static final String SIGNATURE =
        "q1w2e3r4t5y6u7i8o9p0a1s2d3f4g5h6j7k8l9z0x1c2v3b4n5m6Q1W2E3R4T5Y6U7I8O9P0A1S2D3F4G5H6J7K8L9Z0X1C2V3B4N5M6";

    public static final CustomSkinProperty NOTCH = skin("Notch");
    public static final CustomSkinProperty DINNERBONE = skin("Dinnerbone");
    public static final CustomSkinProperty ALEX = skin("Alex");

    private static CustomSkinProperty skin(String source) {
        return new CustomSkinProperty("textures", VALUE, SIGNATURE, source);
    }

    private TestProperties() {
    }
}
