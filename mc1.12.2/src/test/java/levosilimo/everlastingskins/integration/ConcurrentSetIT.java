/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.integration;

import levosilimo.everlastingskins.harness.AsyncSupport;
import levosilimo.everlastingskins.harness.TestServerContext;
import levosilimo.everlastingskins.skinchanger.SkinCommandTestAccess;
import net.minecraft.entity.player.EntityPlayerMP;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Race contract: two concurrent /skin set commands for the same player must
 * not throw; storage ends with one of the two skins and the GameProfile holds
 * exactly one textures property (last write wins, single-entry map).
 */
class ConcurrentSetIT {

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
    void concurrentSet_consistentState() throws Exception {
        SkinCommandTestAccess.setMojangAPI(
            new FakeMojangAPI(TestProperties.NOTCH, TestProperties.DINNERBONE));
        EntityPlayerMP alice = ctx.newPlayer("Alice");
        ctx.makeOp(alice);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> f1 = pool.submit(() ->
                ctx.commandManager.executeCommand(alice, "/skin set mojang Notch"));
            Future<?> f2 = pool.submit(() ->
                ctx.commandManager.executeCommand(alice, "/skin set mojang Dinnerbone"));
            f1.get(5, TimeUnit.SECONDS);
            f2.get(5, TimeUnit.SECONDS);

            assertTrue(AsyncSupport.await(5000, () -> ctx.storage.getSkin(alice.getUniqueID()) != null),
                "skin should be stored after the async applies complete");
            assertTrue(AsyncSupport.await(5000,
                () -> alice.getGameProfile().getProperties().get("textures").size() == 1),
                "profile should settle to exactly one textures property");

            // The drain-coalesce writer's enqueue order is independent of the
            // in-memory skinMap.put order, so last-enqueued wins on disk and may
            // differ from the in-memory winner. Force a drain, then wait for
            // quiescence before reading disk.
            ctx.storage.flushPending();
            assertTrue(AsyncSupport.await(20000, () -> !ctx.storage.hasPendingWrites()),
                "drain-coalesce writer must drain all pending writes");

            // Disk state must settle to ONE of the two raced skins (the
            // last-enqueued payload wins); asserting a specific winner would
            // race the writer.
            Path skinFile = tempDir.resolve("EverlastingSkins").resolve(alice.getUniqueID() + ".json");
            assertTrue(AsyncSupport.await(20000, () -> skinFile.toFile().exists()),
                "skin file must exist after concurrent set");
            String diskSource = readSource(skinFile);
            assertTrue("Notch".equals(diskSource) || "Dinnerbone".equals(diskSource),
                "disk source must settle to one of the raced skins, was " + diskSource);

            // Alice's GameProfile has exactly one textures property.
            assertEquals(1, alice.getGameProfile().getProperties().get("textures").size());
        } finally {
            pool.shutdownNow();
        }
    }

    private static String readSource(Path skinFile) throws Exception {
        String json = new String(java.nio.file.Files.readAllBytes(skinFile), java.nio.charset.StandardCharsets.UTF_8);
        return new com.google.gson.JsonParser().parse(json).getAsJsonObject().get("source").getAsString();
    }
}
