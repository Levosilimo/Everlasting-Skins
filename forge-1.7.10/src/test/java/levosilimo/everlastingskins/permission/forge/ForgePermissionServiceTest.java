/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.permission.forge;

import levosilimo.everlastingskins.permission.VanillaPermissionService;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;
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
 * 1.7.10 permission adapter tests.
 *
 * <p>Deterministic fakes only (memory #1115): server/player objects are
 * Mockito mocks — no live server, no HTTP. The delegation contract under
 * test is {@code canCommandSenderUseCommand(requiredLevel, node)} — the
 * 1.7.10 ops model (there is no PermissionAPI / UserListOps on this line).
 */
public class ForgePermissionServiceTest {

    private static final UUID TEST_UUID = UUID.randomUUID();

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
        when(player.getUniqueID()).thenReturn(TEST_UUID);
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
        when(player.getUniqueID()).thenReturn(TEST_UUID);
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
    public void backendNameIsCorrect() {
        assertEquals("Forge ops (1.7.10)", new ForgePermissionService().getActiveBackendName());
    }

    @Test
    public void priorityIs10() {
        assertEquals(10, new ForgePermissionService().getPriority());
    }

    @Test
    public void vanillaFallbackIsSharedContract() {
        // The fallback path reuses the lane's vanilla service: same node
        // table, so behavior is identical to a direct vanilla check.
        ForgePermissionService service = new ForgePermissionService();
        VanillaPermissionService vanilla = new VanillaPermissionService();
        assertEquals(
            vanilla.hasPermission(TEST_UUID, 0, "everlastingskins.command.skin"),
            service.hasPermission(TEST_UUID, 0, "everlastingskins.command.skin"));
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
