/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.metrics.Snapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Durability guarantees of {@link SkinIO} writes: the rename must not be
 * silently downgraded, and every failure path must surface in metrics and
 * leave no half-written state behind.
 */
class SkinIODurabilityTest {

    @TempDir
    Path tempDir;

    private SkinIO skinIO;

    @BeforeEach
    void setUp() {
        skinIO = new SkinIO(tempDir);
        SkinMetrics.INSTANCE.reset();
    }

    @Test
    @DisplayName("save survives a simulated crash: temp file plus target rename")
    void saveIsAtomicAndDurable() throws Exception {
        UUID u = UUID.randomUUID();
        Path target = tempDir.resolve(u + ".json");

        skinIO.saveSkin(u, "payload".getBytes(StandardCharsets.UTF_8));

        assertTrue(Files.exists(target));
        assertEquals("payload", new String(Files.readAllBytes(target), StandardCharsets.UTF_8));
        assertFalse(Files.exists(tempDir.resolve(u + ".json.tmp")), "temp file must be gone after the move");
        assertEquals(0, SkinMetrics.INSTANCE.snapshot().ioFailures(), "clean save must not record I/O failures");
    }

    @Test
    @DisplayName("rename failure is recorded, logged and rethrown, never silently swallowed")
    void renameFailureIsSurfacedNotSwallowed() throws Exception {
        UUID u = UUID.randomUUID();
        Path target = tempDir.resolve(u + ".json");
        Files.createDirectories(target);
        Files.write(target.resolve("occupied"), new byte[]{1}); // non-empty dir: rename cannot replace it

        assertThrows(IOException.class, () -> skinIO.saveSkin(u, "payload".getBytes(StandardCharsets.UTF_8)),
                "the write hook must propagate the failure so the drain can retry it");

        assertTrue(Files.isDirectory(target), "target directory must be untouched");
        assertTrue(Files.exists(target.resolve("occupied")), "target contents must survive");
        assertFalse(Files.exists(tempDir.resolve(u + ".json.tmp")), "temp file must be cleaned up");
        Snapshot snap = SkinMetrics.INSTANCE.snapshot();
        assertTrue(snap.ioFailures() >= 1, "rename failure must increment the I/O failure metric");
        assertFalse(snap.ioFailuresByType().isEmpty(), "failure type must be recorded, not swallowed");
    }
}
