/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.command;

import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure-JUnit tests for the 1.10.2 {@link SkinRestorerCommand} over the
 * modern {@link ICommand} surface (memory #1115: deterministic mocks —
 * ICommandSender/MinecraftServer are Mockito fakes; no live server,
 * no HTTP). NOTE: 1.10.2 + stable_29 already uses the getName/execute/
 * checkPermission names (the MCP rename landed in the 1.10.2 era, not
 * 1.11 — verified against the deobf'd sources during the lane spike).
 */
class SkinRestorerCommandTest {

    private SkinRestorerCommand command;
    private ICommandSender sender;
    private MinecraftServer server;

    @BeforeEach
    void setUp() {
        command = new SkinRestorerCommand();
        sender = mock(ICommandSender.class);
        server = mock(MinecraftServer.class);
        when(sender.canUseCommand(2, "everlastingskins")).thenReturn(true);
    }

    @Test
    void exposesLegacyICommandSurface() {
        assertEquals("everlastingskins", command.getName());
        assertTrue(command.getAliases().contains("eskins"));
        assertEquals("/everlastingskins <status|reload|help>", command.getUsage(sender));
    }

    @Test
    void dispatchesStatusToSender() throws Exception {
        command.execute(server, sender, new String[]{"status"});
        verify(sender).sendMessage(any(ITextComponent.class));
    }

    @Test
    void dispatchesNoArgsAsUsage() throws Exception {
        command.execute(server, sender, new String[]{});
        verify(sender).sendMessage(any(ITextComponent.class));
    }

    @Test
    void permissionGateDelegatesToSender() {
        assertTrue(command.checkPermission(server, sender));
        when(sender.canUseCommand(2, "everlastingskins")).thenReturn(false);
        assertFalse(command.checkPermission(server, sender));
    }

    @Test
    void permissionGateDeniedSenderNeverDispatches() throws Exception {
        when(sender.canUseCommand(2, "everlastingskins")).thenReturn(false);
        if (command.checkPermission(server, sender)) {
            command.execute(server, sender, new String[]{"status"});
        }
        verify(sender, never()).sendMessage(any(ITextComponent.class));
    }

    @Test
    void tabCompletesSubcommands() {
        List<String> matches = command.getTabCompletions(server, sender, new String[]{"s"}, new BlockPos(0, 0, 0));
        assertTrue(matches.contains("status"));
        assertTrue(command.getTabCompletions(server, sender, new String[]{"x"}, new BlockPos(0, 0, 0)).isEmpty());
    }

    @Test
    void compareToOrdersByName() {
        ICommand other = mock(ICommand.class);
        when(other.getName()).thenReturn("zzz");
        assertTrue(command.compareTo(other) < 0);
    }
}
