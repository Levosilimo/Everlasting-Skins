package levosilimo.everlastingskins.skinchanger;

import net.minecraft.command.CommandHandler;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkinCommandPermissionTest {

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
        EntityPlayerMP mockPlayer = mock(EntityPlayerMP.class);
        when(mockPlayer.canUseCommand(2, "everlastingskins")).thenReturn(true);
        assertTrue(levosilimo.everlastingskins.permission.PermissionServiceManager
            .hasPermission(mockPlayer, "everlastingskins.command.skin"));
    }

    @Test
    @DisplayName("non-op player lacks permission")
    void nonOp_lacksPermission() {
        EntityPlayerMP mockPlayer = mock(EntityPlayerMP.class);
        when(mockPlayer.canUseCommand(2, "everlastingskins")).thenReturn(false);
        assertFalse(levosilimo.everlastingskins.permission.PermissionServiceManager
            .hasPermission(mockPlayer, "everlastingskins.command.skin"));
    }
}
