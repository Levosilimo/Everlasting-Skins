/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.permission.forge;

import levosilimo.everlastingskins.permission.PermissionServiceManager;
import levosilimo.everlastingskins.skinchanger.SkinRestorer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.src.EntityPlayerMP;
import net.minecraft.src.ServerConfigurationManager;
import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 1.6.4 permission adapter tests.
 *
 * <p>Deterministic fakes only (memory #1115): server/player objects are
 * Mockito mocks — no live server, no HTTP. The delegation contract under
 * test is {@code canCommandSenderUseCommand(requiredLevel, node)} — the
 * 1.6.4 ops model (there is no PermissionAPI / UserListOps on this line).
 * Player identity is the username-derived offline UUID bridge
 * ({@link SkinRestorer#uuidOf(String)}): 1.6.4 has no account UUID.
 */
public class ForgePermissionServiceTest {

    private static final UUID TEST_UUID = SkinRestorer.uuidOf("Notch");

    @After
    public void clearSeams() {
        ForgePermissionService.setServerOverride(null);
    }

    @Test
    public void sourceNodeAlwaysTrue() {
        ForgePermissionService service = new ForgePermissionService();
        assertTrue(service.hasPermission(TEST_UUID, 0, "everlastingskins.command.skin.source"));
    }

    @Test
    public void noServerFallsBackToVanillaOpLevels() {
        ForgePermissionService service = new ForgePermissionService();
        // No server override + MinecraftServer.getServer() is null in tests:
        // the adapter must fall back to the vanilla per-node op levels.
        assertTrue(service.hasPermission(TEST_UUID, 2, "everlastingskins.command.metrics"));
        assertFalse(service.hasPermission(TEST_UUID, 0, "everlastingskins.command.metrics"));
    }

    @Test
    public void onlinePlayerDelegatesToCanCommandSenderUseCommand() throws Exception {
        MinecraftServer server = mock(MinecraftServer.class);
        ServerConfigurationManager manager = mock(ServerConfigurationManager.class);
        when(server.getConfigurationManager()).thenReturn(manager);
        EntityPlayerMP player = mock(EntityPlayerMP.class);
        when(player.getCommandSenderName()).thenReturn("Notch");
        when(player.canCommandSenderUseCommand(0, "everlastingskins.command.skin")).thenReturn(true);
        setPlayerEntityList(manager, player);

        ForgePermissionService.setServerOverride(server);
        ForgePermissionService service = new ForgePermissionService();

        assertTrue(service.hasPermission(TEST_UUID, 4, "everlastingskins.command.skin"));
        // The node's required level is what reaches the ops model (not the
        // caller's opLevel — the player's own level is authoritative).
        verify(player).canCommandSenderUseCommand(0, "everlastingskins.command.skin");
    }

    @Test
    public void onlinePlayerDeniedWhenOpLevelTooLow() throws Exception {
        MinecraftServer server = mock(MinecraftServer.class);
        ServerConfigurationManager manager = mock(ServerConfigurationManager.class);
        when(server.getConfigurationManager()).thenReturn(manager);
        EntityPlayerMP player = mock(EntityPlayerMP.class);
        when(player.getCommandSenderName()).thenReturn("Notch");
        when(player.canCommandSenderUseCommand(2, "everlastingskins.command.metrics")).thenReturn(false);
        setPlayerEntityList(manager, player);

        ForgePermissionService.setServerOverride(server);
        ForgePermissionService service = new ForgePermissionService();

        assertFalse(service.hasPermission(TEST_UUID, 0, "everlastingskins.command.metrics"));
    }

    @Test
    public void offlinePlayerFallsBackToVanilla() throws Exception {
        MinecraftServer server = mock(MinecraftServer.class);
        ServerConfigurationManager manager = mock(ServerConfigurationManager.class);
        when(server.getConfigurationManager()).thenReturn(manager);
        setPlayerEntityList(manager); // empty list

        ForgePermissionService.setServerOverride(server);
        ForgePermissionService service = new ForgePermissionService();

        assertTrue(service.hasPermission(TEST_UUID, 2, "everlastingskins.command.metrics"));
        assertFalse(service.hasPermission(TEST_UUID, 0, "everlastingskins.command.metrics"));
    }

    @Test
    public void usernameBridgeKeysMatchPlayerResolution() throws Exception {
        // The derived UUID for the online player's name must resolve back to
        // that player through the ops backend.
        MinecraftServer server = mock(MinecraftServer.class);
        ServerConfigurationManager manager = mock(ServerConfigurationManager.class);
        when(server.getConfigurationManager()).thenReturn(manager);
        EntityPlayerMP player = mock(EntityPlayerMP.class);
        when(player.getCommandSenderName()).thenReturn("Steve");
        when(player.canCommandSenderUseCommand(0, "everlastingskins.command.skin")).thenReturn(true);
        setPlayerEntityList(manager, player);

        ForgePermissionService.setServerOverride(server);
        ForgePermissionService service = new ForgePermissionService();

        assertTrue(service.hasPermission(SkinRestorer.uuidOf("Steve"), 4, "everlastingskins.command.skin"));
    }

    @Test
    public void backendNameIsCorrect() {
        assertEquals("Forge ops (1.6.4)", new ForgePermissionService().getActiveBackendName());
    }

    @Test
    public void priorityIs10() {
        assertEquals(10, new ForgePermissionService().getPriority());
    }

    /** Mirrors the mc1.12.2 lane's reflective field injection for the mock manager. */
    private static void setPlayerEntityList(ServerConfigurationManager manager, Object... players)
        throws Exception {
        Field field = ServerConfigurationManager.class.getField("playerEntityList");
        field.setAccessible(true);
        field.set(manager, players.length == 0 ? Collections.emptyList()
            : java.util.Arrays.asList(players));
    }
}
