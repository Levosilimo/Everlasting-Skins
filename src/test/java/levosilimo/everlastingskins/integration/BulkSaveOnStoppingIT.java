/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.integration;

import levosilimo.everlastingskins.harness.AsyncSupport;
import levosilimo.everlastingskins.harness.TestServerContext;
import levosilimo.everlastingskins.skinchanger.SkinCommandTestAccess;
import levosilimo.everlastingskins.skinchanger.SkinRestorer;
import net.minecraft.entity.player.EntityPlayerMP;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Graceful-shutdown contract (analog of the 1.21 serverStoppingEvent_bulkSave
 * and ioAsyncWriterSurvivesSecondShutdown GameTests): onServerStopping saves
 * every online player's skin and flushes the drain-coalesce writer, and the
 * writer thread stays alive for saves submitted after the stop events.
 */
class BulkSaveOnStoppingIT {

    @TempDir
    Path tempDir;

    private TestServerContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new TestServerContext(tempDir);
    }

    @AfterEach
    void tearDown() {
        SkinCommandTestAccess.resetAPIs();
        ctx.close();
    }

    @Test
    void bulkSaveOnStopping_writesEveryOnlinePlayer() {
        EntityPlayerMP alice = ctx.newPlayer("Alice");
        EntityPlayerMP bob = ctx.newPlayer("Bob");
        ctx.storage.setSkin(alice.getUniqueID(), TestProperties.NOTCH);
        ctx.storage.setSkin(bob.getUniqueID(), TestProperties.DINNERBONE);
        when(ctx.playerList.getPlayers()).thenReturn(Arrays.asList(alice, bob));

        SkinRestorer.onServerStopping();

        assertTrue(Files.exists(skinFile(alice)), "Alice's skin must be written on server stop");
        assertTrue(Files.exists(skinFile(bob)), "Bob's skin must be written on server stop");
        assertFalse(ctx.storage.hasPendingWrites(), "stop must flush the async writer");
    }

    @Test
    void repeatedStopping_doesNotKillWriter() {
        EntityPlayerMP alice = ctx.newPlayer("Alice");
        ctx.storage.setSkin(alice.getUniqueID(), TestProperties.NOTCH);
        when(ctx.playerList.getPlayers()).thenReturn(Collections.singletonList(alice));

        SkinRestorer.onServerStopping();
        SkinRestorer.onServerStopping();

        // A save submitted after the stop events must still land on disk.
        EntityPlayerMP carol = ctx.newPlayer("Carol");
        ctx.storage.setSkin(carol.getUniqueID(), TestProperties.ALEX);
        ctx.storage.saveSkinAsync(carol.getUniqueID(), TestProperties.ALEX);
        assertTrue(AsyncSupport.await(5000, () -> Files.exists(skinFile(carol))),
            "the writer must survive repeated stop events and drain later saves");
    }

    private Path skinFile(EntityPlayerMP player) {
        return tempDir.resolve("EverlastingSkins").resolve(player.getUniqueID() + ".json");
    }
}
