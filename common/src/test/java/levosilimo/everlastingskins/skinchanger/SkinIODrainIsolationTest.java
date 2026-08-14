/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.util.CustomSkinProperty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Per-instance drain isolation: the debounce latch must be per-{@link SkinIO}
 * instance. With a shared (static) latch, instance A's drain resets the latch
 * that instance B's scheduled drain depended on, so B's payload is stranded —
 * B's file never appears until another save or flush on B.
 */
class SkinIODrainIsolationTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("two instances: A's drain must not suppress B's scheduled drain")
    void instanceADrainDoesNotSuppressInstanceB() throws Exception {
        Path dirA = Files.createTempDirectory("skinio-isolation-a-");
        Path dirB = Files.createTempDirectory("skinio-isolation-b-");
        try {
            SkinIO a = new SkinIO(dirA);
            SkinIO b = new SkinIO(dirB);
            UUID ua = UUID.randomUUID();
            UUID ub = UUID.randomUUID();

            // B's save lands inside A's 50ms debounce window: with a static
            // latch, B's scheduleDrain() sees the latch already held by A and
            // skips, and A's drain only resets it after draining A — stranding
            // B's payload with no drain ever scheduled.
            CompletableFuture<Void> unusedA = a.saveSkinAsync(ua, skin("QQ=="));
            CompletableFuture<Void> unusedB = b.saveSkinAsync(ub, skin("Qg=="));

            assertTrue(awaitFile(dirA.resolve(ua + ".json")), "A's payload must drain");
            assertTrue(awaitFile(dirB.resolve(ub + ".json")),
                    "B's scheduled drain was skipped by A's drain (shared-latch bug)");
            // Flush barriers: the file becomes visible a moment before the drain
            // removes the map entry, so assert quiescence only after a drain.
            a.flushPending();
            b.flushPending();
            assertFalse(a.hasPendingWrites(), "A must be quiescent");
            assertFalse(b.hasPendingWrites(), "B must be quiescent");
        } finally {
            deleteRecursively(dirA);
            deleteRecursively(dirB);
        }
    }

    @Test
    @DisplayName("same-instance saves still coalesce into one drain (debounce preserved)")
    void sameInstanceSavesCoalesce() throws Exception {
        Path dir = Files.createTempDirectory("skinio-isolation-c-");
        try {
            SkinIO io = new SkinIO(dir);
            UUID u = UUID.randomUUID();
            Path target = dir.resolve(u + ".json");

            CompletableFuture<Void> unused1 = io.saveSkinAsync(u, skin("djE="));
            CompletableFuture<Void> unused2 = io.saveSkinAsync(u, skin("djI="));
            CompletableFuture<Void> unused3 = io.saveSkinAsync(u, skin("djM="));

            assertTrue(awaitFile(target), "coalesced save must land");
            CustomSkinProperty loaded = io.loadSkin(u);
            assertNotNull(loaded);
            assertEquals("djM=", loaded.getValue(), "only the latest payload may survive coalescing");
        } finally {
            deleteRecursively(dir);
        }
    }

    private static CustomSkinProperty skin(String value) {
        return new CustomSkinProperty(value, "sig", "src");
    }

    /** Polls for a file to appear (50ms debounce + headroom, bounded 2s). */
    private static boolean awaitFile(Path file) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000;
        long pollMs = 1;
        while (System.currentTimeMillis() < deadline) {
            if (Files.exists(file)) return true;
            Thread.sleep(pollMs);
            pollMs = Math.min(100, pollMs * 2);
        }
        return Files.exists(file);
    }

    private static void deleteRecursively(Path dir) {
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best-effort temp cleanup
                }
            });
        } catch (IOException ignored) {
            // best-effort temp cleanup
        }
    }
}
