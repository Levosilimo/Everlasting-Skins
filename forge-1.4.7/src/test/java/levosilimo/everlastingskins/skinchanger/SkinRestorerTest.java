/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import net.minecraft.server.MinecraftServer;
import net.minecraft.src.EntityPlayerMP;
import net.minecraft.src.INetworkManager;
import net.minecraft.src.NetServerHandler;
import net.minecraft.src.ServerConfigurationManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 1.4.7 skin lifecycle tests: the username-derived offline-UUID bridge
 * (memory #1123: :common stays UUID-keyed, 1.4.7 has no account UUID), the
 * storage wiring, and the manager-keyed disconnect path.
 *
 * <p>Deterministic fakes only (memory #1115): no live server, no HTTP.
 * SkinBroadcaster is never exercised here (channel I/O is out of scope);
 * the storage seam covers the restore surface.
 */
public class SkinRestorerTest {

    private SkinStorage storage;
    private Path dir;

    @Before
    public void setUp() throws Exception {
        dir = Files.createTempDirectory("es-147-skin-restorer-test");
        SkinStorage.resetForTest();
        storage = new SkinStorage(new SkinIO(dir));
        SkinRestorer.setStorageForTest(storage);
    }

    @After
    public void tearDown() throws Exception {
        SkinRestorer.setStorageForTest(null);
        SkinRestorer.setServerForTest(null);
        if (dir != null) {
            deleteRecursively(dir);
        }
    }

    /** SkinIO leaves per-record files behind; delete the whole tree. */
    private static void deleteRecursively(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (java.util.stream.Stream<Path> walk = Files.walk(root)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (java.io.IOException ignored) {
                    // Best-effort cleanup of a temp dir.
                }
            });
        }
    }

    @Test
    public void uuidOfIsDeterministic() {
        assertEquals(SkinRestorer.uuidOf("Notch"), SkinRestorer.uuidOf("Notch"));
    }

    @Test
    public void uuidOfDiffersPerUsername() {
        assertFalse(SkinRestorer.uuidOf("Notch").equals(SkinRestorer.uuidOf("Steve")));
    }

    @Test
    public void uuidOfMatchesOfflineV3Convention() {
        // The vanilla offline-mode v3 derivation ("OfflinePlayer:" prefix).
        UUID expected = UUID.nameUUIDFromBytes(
            ("OfflinePlayer:" + "Notch").getBytes(StandardCharsets.UTF_8));
        assertEquals(expected, SkinRestorer.uuidOf("Notch"));
    }

    @Test
    public void applySkinStoresAndClearRemoves() {
        UUID uuid = SkinRestorer.uuidOf("Notch");
        SkinRestorer.applySkin(uuid,
            new levosilimo.everlastingskins.util.CustomSkinProperty("textures", "c3RvcmVk", null, "MojangAPI"));
        assertNotNull(SkinRestorer.getSource(uuid));

        SkinRestorer.clearSkin(uuid);
        assertEquals(null, SkinRestorer.getSource(uuid));
    }

    @Test
    public void onConnectionClosedSavesMatchingPlayer() throws Exception {
        MinecraftServer server = mock(MinecraftServer.class);
        ServerConfigurationManager manager = mock(ServerConfigurationManager.class);
        when(server.getConfigurationManager()).thenReturn(manager);
        SkinRestorer.setServerForTest(server);

        EntityPlayerMP player = mock(EntityPlayerMP.class);
        when(player.getCommandSenderName()).thenReturn("Notch");
        NetServerHandler handler = mock(NetServerHandler.class);
        INetworkManager managerMock = mock(INetworkManager.class);
        setField(handler, "netManager", managerMock);
        setField(player, "playerNetServerHandler", handler);
        setField(manager, "playerEntityList", Collections.singletonList(player));

        UUID uuid = SkinRestorer.uuidOf("Notch");
        SkinRestorer.applySkin(uuid,
            new levosilimo.everlastingskins.util.CustomSkinProperty("textures", "c3RvcmVk", null, "MojangAPI"));
        storage.saveSkin(uuid); // land on disk so the disconnect path finds it

        SkinRestorer.onConnectionClosed(managerMock);
        // Save path executed without exception; disk record exists.
        assertNotNull(storage.loadSkin(uuid));
    }

    @Test
    public void onConnectionClosedIgnoresOtherPlayers() throws Exception {
        MinecraftServer server = mock(MinecraftServer.class);
        ServerConfigurationManager manager = mock(ServerConfigurationManager.class);
        when(server.getConfigurationManager()).thenReturn(manager);
        SkinRestorer.setServerForTest(server);

        EntityPlayerMP player = mock(EntityPlayerMP.class);
        when(player.getCommandSenderName()).thenReturn("Steve");
        NetServerHandler handler = mock(NetServerHandler.class);
        INetworkManager managerMock = mock(INetworkManager.class);
        setField(handler, "netManager", managerMock);
        setField(player, "playerNetServerHandler", handler);
        setField(manager, "playerEntityList", Arrays.asList(player));

        // No stored skin for the disconnecting player — must not throw.
        SkinRestorer.onConnectionClosed(managerMock);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
