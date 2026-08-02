/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.skinchanger;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import levosilimo.everlastingskins.permission.PermissionContext;
import levosilimo.everlastingskins.permission.PermissionServiceManager;
import levosilimo.everlastingskins.permission.TestConfigSupport;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkinCommandPermissionTest {

    @BeforeAll
    static void loadConfig() {
        TestConfigSupport.loadDefaults();
    }

    @Test
    @DisplayName("register builds a command tree without error")
    void register_buildsTree() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        assertDoesNotThrow(() -> SkinCommand.register(dispatcher));
        assertNotNull(dispatcher.getRoot().getChild("skin"));
    }

    @Test
    @DisplayName("buildSetSubcommand returns a LiteralArgumentBuilder for 'set'")
    void buildSetSubcommand_returnsBuilder() throws Exception {
        Method method = SkinCommand.class.getDeclaredMethod("buildSetSubcommand");
        method.setAccessible(true);
        LiteralArgumentBuilder<CommandSourceStack> builder =
            (LiteralArgumentBuilder<CommandSourceStack>) method.invoke(null);
        assertNotNull(builder);
        assertEquals("set", builder.getLiteral());
    }

    @Test
    @DisplayName("buildClearSubcommand returns a LiteralArgumentBuilder for 'clear'")
    void buildClearSubcommand_returnsBuilder() throws Exception {
        Method method = SkinCommand.class.getDeclaredMethod("buildClearSubcommand");
        method.setAccessible(true);
        LiteralArgumentBuilder<CommandSourceStack> builder =
            (LiteralArgumentBuilder<CommandSourceStack>) method.invoke(null);
        assertNotNull(builder);
        assertEquals("clear", builder.getLiteral());
    }

    @Test
    @DisplayName("canTargetOthers is false for non-op player via PermissionServiceManager")
    void canTargetOthers_nonOp_returnsFalse() {
        PermissionContext ctx = PermissionContext.of(UUID.randomUUID(), 0);
        assertFalse(PermissionServiceManager.hasPermission(ctx, "everlastingskins.command.skin.other"));
    }

    @Test
    @DisplayName("canTargetOthers is true for op player")
    void canTargetOthers_op_returnsTrue() {
        PermissionContext ctx = PermissionContext.of(UUID.randomUUID(), 2);
        assertTrue(PermissionServiceManager.hasPermission(ctx, "everlastingskins.command.skin.other"));
    }
}
