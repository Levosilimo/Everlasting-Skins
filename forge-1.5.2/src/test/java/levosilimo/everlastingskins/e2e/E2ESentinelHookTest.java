/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.e2e;

import levosilimo.everlastingskins.skinchanger.SkinRestorer;
import net.minecraft.server.MinecraftServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * E2ESentinelHook unit tests (audit lib-20): the pure logic — sentinel PNG
 * read (valid/missing/override), the offline-UUID seed key, and the
 * {@code -Deverlastingskins.e2e} gate — is exercised without a live server.
 *
 * <p>Deterministic fakes only (memory #1115): {@link MinecraftServer} is a
 * mockito stub; system properties are saved/restored around each test.
 */
public class E2ESentinelHookTest {

    private String savedE2e;
    private String savedSentinel;

    @Before
    public void saveProperties() {
        savedE2e = System.getProperty(E2ESentinelHook.E2E_PROPERTY);
        savedSentinel = System.getProperty(E2ESentinelHook.SENTINEL_PROPERTY);
    }

    @After
    public void restoreProperties() {
        restore(E2ESentinelHook.E2E_PROPERTY, savedE2e);
        restore(E2ESentinelHook.SENTINEL_PROPERTY, savedSentinel);
    }

    private static void restore(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    @Test
    public void readSentinelPngReturnsBytesForValidFile() throws Exception {
        File dir = tempDir("e2e-hook-read");
        try {
            byte[] expected = sentinelBytes();
            File png = new File(dir, "e2e-sentinel.png");
            Files.write(png.toPath(), expected);

            MinecraftServer server = mock(MinecraftServer.class);
            when(server.getFile("e2e-sentinel.png")).thenReturn(png);

            byte[] got = E2ESentinelHook.readSentinelPng(server);
            assertNotNull(got);
            assertArrayEquals(expected, got);
        } finally {
            deleteRecursively(dir);
        }
    }

    @Test
    public void readSentinelPngFailsSoftOnMissingFile() {
        MinecraftServer server = mock(MinecraftServer.class);
        when(server.getFile("e2e-sentinel.png")).thenReturn(new File("/nonexistent/e2e-sentinel.png"));
        assertNull(E2ESentinelHook.readSentinelPng(server));
    }

    @Test
    public void sentinelPropertyOverridesServerDir() throws Exception {
        File dir = tempDir("e2e-hook-override");
        try {
            byte[] expected = sentinelBytes();
            File png = new File(dir, "e2e-sentinel.png");
            Files.write(png.toPath(), expected);
            System.setProperty(E2ESentinelHook.SENTINEL_PROPERTY, png.getAbsolutePath());

            // The override wins even when the server dir would resolve elsewhere.
            MinecraftServer server = mock(MinecraftServer.class);
            when(server.getFile("e2e-sentinel.png")).thenReturn(new File("/nonexistent/e2e-sentinel.png"));

            assertEquals(png, E2ESentinelHook.sentinelFile(server));
            byte[] got = E2ESentinelHook.readSentinelPng(server);
            assertNotNull(got);
            assertArrayEquals(expected, got);
        } finally {
            deleteRecursively(dir);
        }
    }

    @Test
    public void offlineUuidMatchesVanillaV3Convention() {
        // The seed key the hook stores under (SkinRestorer.uuidOf bridge);
        // UUID.nameUUIDFromBytes("OfflinePlayer:TestPlayer") — the vanilla
        // offline-mode v3 convention, verified against Java 8.
        UUID expected = UUID.nameUUIDFromBytes(
            "OfflinePlayer:TestPlayer".getBytes(StandardCharsets.UTF_8));
        assertEquals("bb77495a-a740-3169-a238-69654c8bd2c1", expected.toString());
        assertEquals(expected, SkinRestorer.uuidOf(E2ESentinelHook.TEST_PLAYER));
    }

    @Test
    public void installSkipsWhenE2ePropertyUnset() {
        System.clearProperty(E2ESentinelHook.E2E_PROPERTY);
        assertFalse(E2ESentinelHook.isEnabled());
        // install() returns before touching FMLCommonHandler/NetworkRegistry
        // (no server-side registration when the gate is closed).
        E2ESentinelHook.install();
    }

    @Test
    public void installEnabledWhenE2ePropertySet() {
        System.setProperty(E2ESentinelHook.E2E_PROPERTY, "true");
        assertTrue(E2ESentinelHook.isEnabled());
    }

    @Test
    public void rebroadcastThreadIsDaemonNamedAndBursts() throws Exception {
        Thread t = E2ESentinelHook.rebroadcastThread(60_000L, 40);
        assertTrue(t.isDaemon());
        assertEquals("ES-E2E-rebroadcast", t.getName());
        // A short-period burst must run to completion (count shots) and
        // finish; interruption aborts the burst early.
        Thread t2 = E2ESentinelHook.rebroadcastThread(10L, 3);
        t2.start();
        t2.join(5_000L);
        assertFalse(t2.isAlive());
        // The broadcast action itself is exercised on the wire by the live
        // E2E (the observer's fan-out assertion is the end-to-end proof).
    }

    @Test
    public void scheduleRebroadcastIsNoopBeforeSeed() {
        // No cached seed yet — scheduling must not throw or spawn threads.
        E2ESentinelHook.scheduleRebroadcast();
    }

    private static byte[] sentinelBytes() throws Exception {
        InputStream in = E2ESentinelHookTest.class.getResourceAsStream("/e2e/sentinel-64x32.png");
        assertNotNull("sentinel PNG must be on the test classpath", in);
        try {
            return readAll(in);
        } finally {
            in.close();
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) {
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    private static File tempDir(String prefix) {
        File dir = new File(System.getProperty("java.io.tmpdir"), prefix + "-" + System.nanoTime());
        assertTrue(dir.mkdirs());
        return dir;
    }

    private static void deleteRecursively(File f) {
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) {
                deleteRecursively(c);
            }
        }
        f.delete();
    }
}
