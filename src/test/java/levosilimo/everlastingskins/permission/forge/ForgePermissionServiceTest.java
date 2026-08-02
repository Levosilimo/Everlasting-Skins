/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.permission.forge;

import com.mojang.authlib.GameProfile;
import levosilimo.everlastingskins.permission.PermissionContext;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerList;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.IFMLSidedHandler;
import net.minecraftforge.server.permission.DefaultPermissionHandler;
import net.minecraftforge.server.permission.DefaultPermissionLevel;
import net.minecraftforge.server.permission.IPermissionHandler;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.context.IContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ForgePermissionServiceTest {

    private static final UUID TEST_UUID = UUID.randomUUID();

    @Test
    @DisplayName("hasPermission returns true for .source nodes")
    void hasPermission_sourceNode_returnsTrue() {
        PermissionContext ctx = PermissionContext.of(TEST_UUID, false);
        ForgePermissionService service = new ForgePermissionService();
        assertTrue(service.hasPermission(ctx, "everlastingskins.command.skin.source"));
    }

    @Test
    @DisplayName("hasPermission falls back to context.isOp() when no server context exists")
    void hasPermission_noServer_usesContextIsOp() {
        ForgePermissionService service = new ForgePermissionService();
        assertTrue(service.hasPermission(PermissionContext.of(TEST_UUID, true), "everlastingskins.command.skin"));
        assertFalse(service.hasPermission(PermissionContext.of(TEST_UUID, false), "everlastingskins.command.skin"));
    }

    @Test
    @DisplayName("hasPermission queries the Forge PermissionAPI when the player is online")
    void hasPermission_usesForgePermissionApi_whenPlayerOnline() throws Exception {
        MinecraftServer server = mock(MinecraftServer.class);
        PlayerList list = mock(PlayerList.class);
        when(server.getPlayerList()).thenReturn(list);
        EntityPlayerMP player = mock(EntityPlayerMP.class);
        when(player.getGameProfile()).thenReturn(new GameProfile(TEST_UUID, "Notch"));
        when(list.getPlayerByUUID(TEST_UUID)).thenReturn(player);

        // A stub handler that grants the node: simulates a player granted the
        // permission (e.g. an ALL-level node) without op status.
        PermissionAPI.setPermissionHandler(new IPermissionHandler() {
            @Override
            public boolean hasPermission(GameProfile profile, String node, IContext context) {
                return true;
            }

            @Override
            public void registerNode(String node, DefaultPermissionLevel level, String desc) {
            }

            @Override
            public String getNodeDescription(String node) {
                return "";
            }

            @Override
            public java.util.Collection<String> getRegisteredNodes() {
                return java.util.Collections.emptyList();
            }
        });
        try {
            setServerInstance(server);
            ForgePermissionService service = new ForgePermissionService();
            assertTrue(service.hasPermission(PermissionContext.of(TEST_UUID, false),
                    "everlastingskins.command.skin"),
                "a non-op granted the node via the Forge PermissionAPI must be allowed");
        } finally {
            setServerInstance(null);
            PermissionAPI.setPermissionHandler(DefaultPermissionHandler.INSTANCE);
        }
    }

    @Test
    @DisplayName("Backend name is correct")
    void getActiveBackendName() {
        assertEquals("Forge PermissionAPI (1.12)", new ForgePermissionService().getActiveBackendName());
    }

    @Test
    @DisplayName("Priority is 10")
    void getPriority() {
        assertEquals(10, new ForgePermissionService().getPriority());
    }

    private static void setServerInstance(MinecraftServer server) throws Exception {
        FMLCommonHandler instance = FMLCommonHandler.instance();
        Field delegateField = FMLCommonHandler.class.getDeclaredField("sidedDelegate");
        delegateField.setAccessible(true);
        IFMLSidedHandler handler = mock(IFMLSidedHandler.class);
        when(handler.getServer()).thenReturn(server);
        delegateField.set(instance, handler);
    }
}
