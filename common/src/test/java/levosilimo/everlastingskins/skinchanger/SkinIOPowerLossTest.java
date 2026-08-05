/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.util.CustomSkinProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Power-loss durability of {@link SkinIO} writes: the file content is
 * fsynced before the atomic rename, and the parent directory must be
 * fsynced after the rename so the directory entry itself survives a crash
 * (Pillai OSDI'14 safe file flush). No real power-loss simulation is
 * required — the fsync barrier is verified by intercepting the channel.
 */
class SkinIOPowerLossTest {

    @TempDir
    Path tempDir;

    private SkinIO skinIO;

    @BeforeEach
    void setUp() {
        skinIO = new SkinIO(tempDir);
    }

    @Test
    @DisplayName("saveSkin forces the parent directory after the atomic rename")
    void writeSkinFile_fsyncsParentDirectory_afterAtomicMove() throws Exception {
        FileChannel dirChannel = mock(FileChannel.class);
        SkinIO spy = spy(skinIO);
        doReturn(dirChannel).when(spy).openDirectoryChannel(tempDir);

        UUID uuid = UUID.randomUUID();
        spy.saveSkin(uuid, new CustomSkinProperty("value", "sig", "source"));

        // The durability barrier must run on the save directory, and the
        // returned channel must be forced so the rename entry is durable.
        verify(spy, atLeastOnce()).openDirectoryChannel(tempDir);
        verify(dirChannel, atLeastOnce()).force(true);
        assertTrue(Files.exists(tempDir.resolve(uuid + ".json")), "skin must still be saved");
    }
}
