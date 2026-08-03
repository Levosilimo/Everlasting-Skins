/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.integration;

import levosilimo.everlastingskins.EverlastingSkins;
import levosilimo.everlastingskins.harness.AsyncSupport;
import levosilimo.everlastingskins.harness.TestServerContext;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.metrics.Snapshot;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 */
class BulkSaveOnStoppingIT {

    @TempDir
    Path tempDir;

    private TestServerContext ctx;

    @BeforeEach
    void setUp() throws InterruptedException {
        ctx = new TestServerContext(tempDir);
        // Earlier test classes' refresh tasks enqueue async saves whose drains
        // fire up to 50ms (the writer debounce window) later, decrementing the
        // shared pendingAsyncWrites counter while this class runs. Wait the
        // window out so the metrics reset below measures only this test's work.
        Thread.sleep(60);
        SkinMetrics.INSTANCE.reset();
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
        // work; whether the 50ms scheduled drain or the stop flush wins the
        // race, the completed==submitted invariant below must hold.
        ctx.storage.saveSkinAsync(alice.getUniqueID(), TestProperties.NOTCH);
        ctx.storage.saveSkinAsync(bob.getUniqueID(), TestProperties.DINNERBONE);

        new EverlastingSkins().serverStopping(new FMLServerStoppingEvent());

        // Carol only exists in memory: only the stop loop's synchronous save
        // can have written her file, so this pins the per-player loop.
        assertTrue(Files.exists(skinFile(carol)),
            "the stop loop must persist in-memory skins without a queued write");
        assertTrue(Files.exists(skinFile(alice)), "Alice's skin must be written on server stop");
        assertTrue(Files.exists(skinFile(bob)), "Bob's skin must be written on server stop");
        Snapshot snap = SkinMetrics.INSTANCE.snapshot();
        assertEquals(2, snap.savesSubmitted(), "stop must not drop queued async saves");
        assertEquals(2, snap.savesCompleted(),
            "stop must drain every submitted save (flush, not drop)");
        assertEquals(0, snap.pendingAsyncWrites(), "stop must leave no save in flight");
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
        assertEquals(0, SkinMetrics.INSTANCE.snapshot().pendingAsyncWrites(),
            "the post-stop save must drain completely");
    }

    private Path skinFile(EntityPlayerMP player) {
        return tempDir.resolve("EverlastingSkins").resolve(player.getUniqueID() + ".json");
    }
}
