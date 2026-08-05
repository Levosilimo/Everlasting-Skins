/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.skinchanger.SkinStorage.DeleteFailedException;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Failure semantics of the serialized delete: when the delete cannot be
 * confirmed to have landed, the exception must propagate so the in-memory
 * map entry survives — file and map move together, or a later getSkin()
 * reload resurrects the cleared skin.
 */
class SkinDeleteFailureTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("failed delete propagates and keeps the map entry and the file")
    void failedDeletePropagatesAndKeepsMapEntry() throws Exception {
        IOException injected = new IOException("injected delete failure");
        SkinStorage failingStorage = new SkinStorage(new FailingDeleteSkinIO(tempDir, injected));
        UUID u = UUID.randomUUID();
        Path target = tempDir.resolve(u + ".json");
        failingStorage.setSkin(u, skin("persisted"));
        failingStorage.saveSkin(u);
        assertTrue(Files.exists(target));

        DeleteFailedException ex = assertThrows(DeleteFailedException.class, () -> failingStorage.removeSkin(u));

        assertTrue(hasCause(ex, IOException.class, "injected delete failure"),
                "the delete failure must surface with its original cause");
        assertNotNull(failingStorage.getSkin(u), "the map entry must survive a failed delete");
        assertTrue(Files.exists(target), "the file must survive a failed delete");
    }

    @Test
    @DisplayName("setSkin(null) on a failing delete propagates instead of clearing")
    void setSkinNullOnFailingDeletePropagates() throws Exception {
        SkinStorage failingStorage = new SkinStorage(new FailingDeleteSkinIO(tempDir, new IOException("injected delete failure")));
        UUID u = UUID.randomUUID();
        failingStorage.setSkin(u, skin("persisted"));

        assertThrows(DeleteFailedException.class, () -> failingStorage.setSkin(u, null));
        assertNotNull(failingStorage.getSkin(u), "the map entry must survive a failed clear");
    }

    @Test
    @DisplayName("writer crash on delete surfaces as DeleteFailedException, not a silent drop")
    void writerCrashOnDeletePropagates() throws Exception {
        SkinStorage failingStorage = new SkinStorage(new FailingDeleteSkinIO(tempDir, new IllegalStateException("writer crashed")));
        UUID u = UUID.randomUUID();
        failingStorage.setSkin(u, skin("persisted"));

        assertThrows(DeleteFailedException.class, () -> failingStorage.removeSkin(u));
        assertNotNull(failingStorage.getSkin(u));
    }

    private static boolean hasCause(Throwable t, Class<? extends Throwable> type, String messagePart) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (type.isInstance(c) && String.valueOf(c.getMessage()).contains(messagePart)) {
                return true;
            }
        }
        return false;
    }

    private static CustomSkinProperty skin(String value) {
        return new CustomSkinProperty(value, "sig", "src");
    }

    /** SkinIO whose delete always fails with the injected exception. */
    private static final class FailingDeleteSkinIO extends SkinIO {

        private final Throwable failure;

        FailingDeleteSkinIO(Path savePath, Throwable failure) {
            super(savePath);
            this.failure = failure;
        }

        @Override
        public void deleteSkin(UUID uuid) throws IOException {
            if (failure instanceof IOException) {
                throw (IOException) failure;
            }
            if (failure instanceof RuntimeException) {
                throw (RuntimeException) failure;
            }
            throw new IOException(failure);
        }
    }
}
