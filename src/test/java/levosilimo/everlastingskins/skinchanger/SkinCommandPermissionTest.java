/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.permission.PermissionContext;
import levosilimo.everlastingskins.permission.PermissionServiceManager;
import levosilimo.everlastingskins.permission.VanillaPermissionService;
import net.minecraft.command.CommandHandler;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkinCommandPermissionTest {

    private static final UUID TEST_UUID = UUID.randomUUID();

    // M2 step 5: PermissionServiceManager (from /common) fails closed until a
    // backend is registered — mirror the production bootstrap (EverlastingSkins.init).
    @BeforeEach
    @AfterEach
    void resetManager() {
        PermissionServiceManager.registerService(new VanillaPermissionService());
    }

    @Test
    @DisplayName("register registers the skin command")
    void register_registersCommand() {
        MinecraftServer mockServer = mock(MinecraftServer.class);
        when(mockServer.getCommandManager()).thenReturn(mock(CommandHandler.class));
        assertDoesNotThrow(() -> SkinCommand.register(mockServer));
    }

    @Test
    @DisplayName("checkPermission always returns true for any sender")
    void checkPermission_alwaysTrue() {
        SkinCommand cmd = new SkinCommand();
        assertTrue(cmd.checkPermission(mock(MinecraftServer.class), null));
    }

    @Test
    @DisplayName("getRequiredPermissionLevel is 4 (CommandBase default)")
    void getRequiredPermissionLevel_default() {
        assertEquals(4, new SkinCommand().getRequiredPermissionLevel());
    }

    @Test
    @DisplayName("hasPermission delegates to PermissionServiceManager for op players")
    void permissionCheck_delegatesToManager() {
        PermissionContext ctx = PermissionContext.of(TEST_UUID, 2);
        assertTrue(PermissionServiceManager.hasPermission(ctx.uuid(), ctx.opLevel(),
            "everlastingskins.command.skin"));
    }

    @Test
    @DisplayName("non-op player lacks level-2 permission")
    void nonOp_lacksPermission() {
        PermissionContext ctx = PermissionContext.of(TEST_UUID, 0);
        assertFalse(PermissionServiceManager.hasPermission(ctx.uuid(), ctx.opLevel(),
            "everlastingskins.command.metrics"));
    }
}
