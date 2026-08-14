/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins;

import levosilimo.everlastingskins.skinchanger.SkinIO;
import levosilimo.everlastingskins.skinchanger.SkinStorage;
import levosilimo.everlastingskins.util.EndpointsConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Regression contract for the resource-packaging decision: {@code :common}
 * owns {@code endpoints.properties} and
 * {@code /everlastingskins/default-skin.properties} — both load via the
 * classloader from the bundled {@code :common} jar
 * ({@link SkinStorage} reads {@code /everlastingskins/default-skin.properties}
 * in its constructor, {@link EndpointsConfig} reads
 * {@code endpoints.properties} in its static block).
 *
 * <p>This lane must NOT ship its own copies. A lane-local
 * {@code default-skin.properties} at the resources root would land at
 * {@code /default-skin.properties} — a path nothing reads — and a
 * lane-local {@code endpoints.properties} would silently duplicate the
 * {@code :common} copy. The tests pin both halves: the classpath serves the
 * {@code :common} paths (so runtime never depends on lane copies), and the
 * root-level lane path stays absent.
 */
class OwnedResourceContractTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("default-skin.properties is served from /everlastingskins/ and the lane ships no root-level copy")
    void defaultSkin_loadsFromCommonPath() {
        assertNotNull(getClass().getClassLoader().getResource("everlastingskins/default-skin.properties"),
                "SkinStorage must find the default skin on the classpath (:common owns it)");
        assertNull(getClass().getClassLoader().getResource("default-skin.properties"),
                "lane must not ship a root-level default-skin.properties copy (never read, dead)");

        // SkinStorage's constructor calls loadDefaultSkin(); it must not throw.
        Path skinDir = tempDir.resolve("EverlastingSkins");
        assertDoesNotThrow(() -> {
            Files.createDirectories(skinDir);
            new SkinStorage(new SkinIO(skinDir));
        }, "SkinStorage must construct without a lane-local default-skin.properties");
    }

    @Test
    @DisplayName("endpoints.properties loads from the classpath (:common ownership)")
    void endpoints_loadFromClasspath() {
        assertNotNull(getClass().getClassLoader().getResource("endpoints.properties"),
                "EndpointsConfig must find endpoints.properties on the classpath (:common owns it)");
        assertNotNull(EndpointsConfig.getURI("endpoint.mineskin.generate"),
                "the classpath endpoints.properties must carry the production endpoint keys");
    }
}
