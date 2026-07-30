package levosilimo.everlastingskins.skinchanger;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkinCommandPermissionTest {

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
        CommandSourceStack mockSource = mock(CommandSourceStack.class);
        ServerPlayer mockPlayer = mock(ServerPlayer.class);
        when(mockSource.getPlayer()).thenReturn(mockPlayer);
        when(mockPlayer.hasPermissions(2)).thenReturn(false);

        assertFalse(levosilimo.everlastingskins.permission.PermissionServiceManager
            .hasPermission(mockPlayer, "everlastingskins.command.skin.other"));
    }

    @Test
    @DisplayName("canTargetOthers is true for op player")
    void canTargetOthers_op_returnsTrue() {
        CommandSourceStack mockSource = mock(CommandSourceStack.class);
        ServerPlayer mockPlayer = mock(ServerPlayer.class);
        when(mockSource.getPlayer()).thenReturn(mockPlayer);
        when(mockPlayer.hasPermissions(2)).thenReturn(true);

        assertTrue(levosilimo.everlastingskins.permission.PermissionServiceManager
            .hasPermission(mockPlayer, "everlastingskins.command.skin.other"));
    }
}
