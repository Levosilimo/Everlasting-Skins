/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.integration;

import levosilimo.everlastingskins.EverlastingSkins;
import levosilimo.everlastingskins.harness.AsyncSupport;
import levosilimo.everlastingskins.harness.TestServerContext;
import levosilimo.everlastingskins.skinchanger.SkinCommandTestAccess;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
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
 * Graceful-shutdown contract exercised through the real @Mod
 * serverStopping handler (FML invokes it once per orderly shutdown): every
 * online player's in-memory skin is persisted synchronously and the
 * drain-coalesce writer is flushed. Stop is idempotent, and because the
 * 1.12.2 writer executor is never shut down, saves submitted after the stop
 * events still land on disk (the 1.21 ioAsyncWriterSurvivesSecondShutdown
 * analog has no shutdown call on this branch to guard).
 *
 * <p>All assertions are contamination-proof: they read only disk files and
 * this test's own SkinStorage instance, never the shared metrics counters
 * that earlier test classes' delayed writer drains may still touch.
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
    void bulkSaveOnStopping_persistsOnlinePlayersAndFlushesWriter() {
        EntityPlayerMP alice = ctx.newPlayer("Alice");
        EntityPlayerMP bob = ctx.newPlayer("Bob");
        EntityPlayerMP carol = ctx.newPlayer("Carol");
        ctx.storage.setSkin(alice.getUniqueID(), TestProperties.NOTCH);
        ctx.storage.setSkin(bob.getUniqueID(), TestProperties.DINNERBONE);
        ctx.storage.setSkin(carol.getUniqueID(), TestProperties.ALEX);
        when(ctx.playerList.getPlayers()).thenReturn(Arrays.asList(alice, bob, carol));
        // Enqueue async writes for two players so the stop-time flush has real
        // work. The stop handler flushes synchronously (flushPending blocks on
        // the drain), so right after it returns — before the 50ms scheduled
        // drain can fire — the files must exist and the queue must be empty.
        ctx.storage.saveSkinAsync(alice.getUniqueID(), TestProperties.NOTCH);
        ctx.storage.saveSkinAsync(bob.getUniqueID(), TestProperties.DINNERBONE);

        new EverlastingSkins().serverStopping(new FMLServerStoppingEvent());

        // Carol only exists in memory: only the stop loop's synchronous save
        // can have written her file, so this pins the per-player loop.
        assertTrue(Files.exists(skinFile(carol)),
            "the stop loop must persist in-memory skins without a queued write");
        assertTrue(Files.exists(skinFile(alice)),
            "the stop flush must have written Alice's queued save to disk");
        assertTrue(Files.exists(skinFile(bob)),
            "the stop flush must have written Bob's queued save to disk");
        assertFalse(ctx.storage.hasPendingWrites(),
            "the stop flush must drain the writer queue synchronously, not leave it to the 50ms drain");
    }

    @Test
    void serverStoppingTwice_isIdempotentAndWriterStillDrains() {
        EntityPlayerMP alice = ctx.newPlayer("Alice");
        ctx.storage.setSkin(alice.getUniqueID(), TestProperties.NOTCH);
        when(ctx.playerList.getPlayers()).thenReturn(Collections.singletonList(alice));

        new EverlastingSkins().serverStopping(new FMLServerStoppingEvent());
        new EverlastingSkins().serverStopping(new FMLServerStoppingEvent());

        // A save submitted after the stop events must still land on disk; the
        // 1.12.2 writer is a daemon executor that stop never shuts down.
        EntityPlayerMP carol = ctx.newPlayer("Carol");
        ctx.storage.setSkin(carol.getUniqueID(), TestProperties.ALEX);
        ctx.storage.saveSkinAsync(carol.getUniqueID(), TestProperties.ALEX);
        assertTrue(AsyncSupport.await(5000, () -> Files.exists(skinFile(carol))),
            "the writer must survive repeated stop events and drain later saves");
        // The file-exists poll above can observe the write before the drain's
        // pendingWrites cleanup lands (the entry is removed only after the write
        // hook returns), so wait for queue quiescence instead of asserting it
        // instantly — the instant assert raced the cleanup under CI load.
        assertTrue(AsyncSupport.await(5000, () -> !ctx.storage.hasPendingWrites()),
            "the post-stop save must drain completely");
    }

    private Path skinFile(EntityPlayerMP player) {
        return tempDir.resolve("EverlastingSkins").resolve(player.getUniqueID() + ".json");
    }
}
