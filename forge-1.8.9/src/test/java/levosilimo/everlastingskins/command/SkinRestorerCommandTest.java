/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.command;

import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;
import net.minecraft.util.IChatComponent;
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
 * Pure-JUnit tests for the 1.8.9 {@link SkinRestorerCommand} over the
 * legacy {@link ICommand} surface (memory #1115: deterministic mocks —
 * ICommandSender is a Mockito fake; no live server, no HTTP).
 */
class SkinRestorerCommandTest {

    private SkinRestorerCommand command;
    private ICommandSender sender;

    @BeforeEach
    void setUp() {
        command = new SkinRestorerCommand();
        sender = mock(ICommandSender.class);
        when(sender.canCommandSenderUseCommand(2, "everlastingskins")).thenReturn(true);
    }

    @Test
    void exposesLegacyICommandSurface() {
        assertEquals("everlastingskins", command.getCommandName());
        assertTrue(command.getCommandAliases().contains("eskins"));
        assertEquals("/everlastingskins <status|reload|help>", command.getCommandUsage(sender));
    }

    @Test
    void dispatchesStatusToSender() throws Exception {
        command.processCommand(sender, new String[]{"status"});
        verify(sender).addChatMessage(any(IChatComponent.class));
    }

    @Test
    void dispatchesNoArgsAsUsage() throws Exception {
        command.processCommand(sender, new String[]{});
        verify(sender).addChatMessage(any(IChatComponent.class));
    }

    @Test
    void permissionGateDelegatesToSender() {
        assertTrue(command.canCommandSenderUseCommand(sender));
        when(sender.canCommandSenderUseCommand(2, "everlastingskins")).thenReturn(false);
        assertFalse(command.canCommandSenderUseCommand(sender));
    }

    @Test
    void permissionGateDeniedSenderNeverDispatches() throws Exception {
        when(sender.canCommandSenderUseCommand(2, "everlastingskins")).thenReturn(false);
        if (command.canCommandSenderUseCommand(sender)) {
            command.processCommand(sender, new String[]{"status"});
        }
        verify(sender, never()).addChatMessage(any(IChatComponent.class));
    }

    @Test
    void tabCompletesSubcommands() {
        List<String> matches = command.addTabCompletionOptions(sender, new String[]{"s"}, new BlockPos(0, 0, 0));
        assertTrue(matches.contains("status"));
        assertTrue(command.addTabCompletionOptions(sender, new String[]{"x"}, new BlockPos(0, 0, 0)).isEmpty());
    }

    @Test
    void compareToOrdersByName() {
        ICommand other = mock(ICommand.class);
        when(other.getCommandName()).thenReturn("zzz");
        assertTrue(command.compareTo(other) < 0);
    }
}
