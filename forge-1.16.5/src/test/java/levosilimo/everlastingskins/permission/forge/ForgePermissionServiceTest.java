/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.permission.forge;

import com.mojang.authlib.GameProfile;
import levosilimo.everlastingskins.permission.PermissionContext;
import levosilimo.everlastingskins.permission.PermissionServiceManager;
import levosilimo.everlastingskins.permission.TestConfigSupport;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerList;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import net.minecraftforge.server.permission.DefaultPermissionLevel;
import net.minecraftforge.server.permission.PermissionAPI;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the 1.16.5 {@link ForgePermissionService} — the legacy
 * string-node Forge permission backend (1.16.5 has no PermissionNode API;
 * nodes are registered via {@link PermissionAPI#registerNode} and checked
 * via {@code PermissionAPI.hasPermission}). Pins the online-vs-offline
 * routing (ServerLifecycleHooks → PlayerList lookup vs GameProfile fallback)
 * and the vanilla op-level fallback when the API throws.
 */
class ForgePermissionServiceTest {

    /**
     * The unit-test JVM has no running server. On 1.16.5 the vanilla
     * registry chain is self-initializing, but the order matters:
     * {@code World.<clinit>} → {@code Registry.<clinit>} →
     * {@code MemoryModuleType.<clinit>} → {@code GlobalPos.<clinit>} reads
     * {@code World.RESOURCE_KEY_CODEC} — if {@code World} started the chain
     * it is mid-clinit there and the read is null (NPE). The game initializes
     * {@code Registry} first; force the same order here so {@code World}'s
     * clinit always runs fresh. (1.16.5 has no isBootstrapped gate on
     * registry access, so no flag trick is needed — but {@link
     * Bootstrap#bootStrap()} itself must not be called: Forge patches it to
     * run GameData.vanillaSnapshot(), which needs the FML runtime.)
     */
    static {
        try {
            Class.forName("net.minecraft.util.registry.Registry");
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static final UUID TEST_UUID = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final String SKIN_NODE = "everlastingskins.command.skin";
    private static final String METRICS_NODE = "everlastingskins.command.metrics";
    private static final String SKIN_OTHER_NODE = "everlastingskins.command.skin.other";

    @BeforeAll
    static void loadConfig() {
        TestConfigSupport.loadDefaults();
    }

    /* ================================================================== */
    /*  Offline routing (no server context)                                */
    /* ================================================================== */

    @Test
    @DisplayName("hasPermission returns true when PermissionAPI grants it (offline GameProfile path)")
    void hasPermission_granted_returnsTrue() {
        try (MockedStatic<PermissionAPI> api = mockStatic(PermissionAPI.class)) {
            UUID uuid = UUID.randomUUID();
            PermissionContext ctx = PermissionContext.of(uuid, 0);
            api.when(() -> PermissionAPI.hasPermission(any(GameProfile.class), eq(SKIN_NODE), isNull()))
               .thenReturn(true);
            ForgePermissionService service = new ForgePermissionService();
            assertTrue(service.hasPermission(ctx.uuid(), ctx.opLevel(), SKIN_NODE));
        }
    }

    @Test
    @DisplayName("hasPermission returns false when PermissionAPI denies it (offline GameProfile path)")
    void hasPermission_denied_returnsFalse() {
        try (MockedStatic<PermissionAPI> api = mockStatic(PermissionAPI.class)) {
            UUID uuid = UUID.randomUUID();
            PermissionContext ctx = PermissionContext.of(uuid, 0);
            api.when(() -> PermissionAPI.hasPermission(any(GameProfile.class), eq(SKIN_NODE), isNull()))
               .thenReturn(false);
            ForgePermissionService service = new ForgePermissionService();
            assertFalse(service.hasPermission(ctx.uuid(), ctx.opLevel(), SKIN_NODE));
        }
    }

    @Test
    @DisplayName("delegates the exact permission node to the API")
    void hasPermission_delegatesNodeString() {
        try (MockedStatic<PermissionAPI> api = mockStatic(PermissionAPI.class)) {
            UUID uuid = UUID.randomUUID();
            PermissionContext ctx = PermissionContext.of(uuid, 0);
            api.when(() -> PermissionAPI.hasPermission(any(GameProfile.class), eq(SKIN_OTHER_NODE), isNull()))
               .thenReturn(true);
            ForgePermissionService service = new ForgePermissionService();
            assertTrue(service.hasPermission(ctx.uuid(), ctx.opLevel(), SKIN_OTHER_NODE));
        }
    }

    @Test
    @DisplayName(".skin.source is NOT special-cased on 1.16.5 — delegated to the API")
    void hasPermission_sourceNode_delegatesToApi() {
        // 1.21's backend short-circuits .source; the 1.16.5 legacy backend
        // passes every node to PermissionAPI — pin that delta.
        try (MockedStatic<PermissionAPI> api = mockStatic(PermissionAPI.class)) {
            UUID uuid = UUID.randomUUID();
            PermissionContext ctx = PermissionContext.of(uuid, 0);
            api.when(() -> PermissionAPI.hasPermission(any(GameProfile.class), eq("everlastingskins.command.skin.source"), isNull()))
               .thenReturn(false);
            ForgePermissionService service = new ForgePermissionService();
            assertFalse(service.hasPermission(ctx.uuid(), ctx.opLevel(), "everlastingskins.command.skin.source"));
        }
    }

    /* ================================================================== */
    /*  Online routing (live server context)                               */
    /* ================================================================== */

    @Test
    @DisplayName("online player routes through PermissionAPI.hasPermission(PlayerEntity, node)")
    void hasPermission_onlinePlayer_usesPlayerEntityVariant() {
        try (MockedStatic<ServerLifecycleHooks> hooks = mockStatic(ServerLifecycleHooks.class);
             MockedStatic<PermissionAPI> api = mockStatic(PermissionAPI.class)) {
            MinecraftServer server = mock(MinecraftServer.class);
            PlayerList playerlist = mock(PlayerList.class);
            ServerPlayerEntity player = mock(ServerPlayerEntity.class);
            when(server.getPlayerList()).thenReturn(playerlist);
            when(playerlist.getPlayer(TEST_UUID)).thenReturn(player);
            hooks.when(ServerLifecycleHooks::getCurrentServer).thenReturn(server);
            api.when(() -> PermissionAPI.hasPermission(any(ServerPlayerEntity.class), eq(SKIN_NODE)))
               .thenReturn(true);

            ForgePermissionService service = new ForgePermissionService();
            assertTrue(service.hasPermission(TEST_UUID, 0, SKIN_NODE));
            api.verify(() -> PermissionAPI.hasPermission(eq(player), eq(SKIN_NODE)), times(1));
        }
    }

    @Test
    @DisplayName("no server context routes through the GameProfile path, never the live player path")
    void hasPermission_noServer_usesGameProfilePath() {
        try (MockedStatic<ServerLifecycleHooks> hooks = mockStatic(ServerLifecycleHooks.class);
             MockedStatic<PermissionAPI> api = mockStatic(PermissionAPI.class)) {
            hooks.when(ServerLifecycleHooks::getCurrentServer).thenReturn(null);
            api.when(() -> PermissionAPI.hasPermission(any(GameProfile.class), eq(SKIN_NODE), isNull()))
               .thenReturn(false);

            ForgePermissionService service = new ForgePermissionService();
            assertFalse(service.hasPermission(TEST_UUID, 0, SKIN_NODE));
            api.verify(() -> PermissionAPI.hasPermission(any(GameProfile.class), eq(SKIN_NODE), isNull()));
            api.verify(() -> PermissionAPI.hasPermission(any(ServerPlayerEntity.class), any(String.class)), never());
        }
    }

    @Test
    @DisplayName("getPlayer throws → treated as offline (null player path)")
    void hasPermission_playerLookupThrows_usesGameProfilePath() {
        try (MockedStatic<ServerLifecycleHooks> hooks = mockStatic(ServerLifecycleHooks.class);
             MockedStatic<PermissionAPI> api = mockStatic(PermissionAPI.class)) {
            MinecraftServer server = mock(MinecraftServer.class);
            PlayerList playerlist = mock(PlayerList.class);
            when(server.getPlayerList()).thenReturn(playerlist);
            when(playerlist.getPlayer(TEST_UUID)).thenThrow(new RuntimeException("player list unavailable"));
            hooks.when(ServerLifecycleHooks::getCurrentServer).thenReturn(server);
            api.when(() -> PermissionAPI.hasPermission(any(GameProfile.class), eq(SKIN_NODE), isNull()))
               .thenReturn(true);

            ForgePermissionService service = new ForgePermissionService();
            assertTrue(service.hasPermission(TEST_UUID, 0, SKIN_NODE),
                "a failing player lookup must degrade to the offline path");
        }
    }

    /* ================================================================== */
    /*  Vanilla fallback when the API throws                               */
    /* ================================================================== */

    @Test
    @DisplayName("PermissionAPI throws → falls back to per-node op levels")
    void hasPermission_apiThrows_fallbackToVanillaOpLevels() {
        try (MockedStatic<ServerLifecycleHooks> hooks = mockStatic(ServerLifecycleHooks.class);
             MockedStatic<PermissionAPI> api = mockStatic(PermissionAPI.class)) {
            hooks.when(ServerLifecycleHooks::getCurrentServer).thenReturn(null);
            api.when(() -> PermissionAPI.hasPermission(any(GameProfile.class), eq(METRICS_NODE), isNull()))
               .thenThrow(new RuntimeException("PermissionAPI unavailable"));

            ForgePermissionService service = new ForgePermissionService();
            PermissionContext opCtx = PermissionContext.of(TEST_UUID, 2);
            assertTrue(service.hasPermission(opCtx.uuid(), opCtx.opLevel(), METRICS_NODE),
                "op 2 meets the metrics default level");
            PermissionContext nonOpCtx = PermissionContext.of(TEST_UUID, 0);
            assertFalse(service.hasPermission(nonOpCtx.uuid(), nonOpCtx.opLevel(), METRICS_NODE),
                "op 0 does not meet the metrics default level");
        }
    }

    /* ================================================================== */
    /*  Node registration                                                  */
    /* ================================================================== */

    @Test
    @DisplayName("registerNodes registers all skin, source, bypass and metrics nodes")
    void registerNodes_registersAllNodes() {
        try (MockedStatic<PermissionAPI> api = mockStatic(PermissionAPI.class);
             MockedStatic<PermissionServiceManager> manager = mockStatic(PermissionServiceManager.class)) {
            ForgePermissionService.registerNodes();

            api.verify(() -> PermissionAPI.registerNode(eq("everlastingskins.command.skin"), eq(DefaultPermissionLevel.ALL), any(String.class)));
            api.verify(() -> PermissionAPI.registerNode(eq("everlastingskins.command.skin.other"), eq(DefaultPermissionLevel.OP), any(String.class)));
            api.verify(() -> PermissionAPI.registerNode(eq("everlastingskins.command.skin.url"), eq(DefaultPermissionLevel.ALL), any(String.class)));
            api.verify(() -> PermissionAPI.registerNode(eq("everlastingskins.command.skin.clear"), eq(DefaultPermissionLevel.ALL), any(String.class)));
            api.verify(() -> PermissionAPI.registerNode(eq("everlastingskins.command.metrics"), eq(DefaultPermissionLevel.OP), any(String.class)));
            api.verify(() -> PermissionAPI.registerNode(eq("everlastingskins.command.metrics.reset"), eq(DefaultPermissionLevel.OP), any(String.class)));
            api.verify(() -> PermissionAPI.registerNode(eq("everlastingskins.command.skin.source"), eq(DefaultPermissionLevel.ALL), any(String.class)));
            api.verify(() -> PermissionAPI.registerNode(eq("everlastingskins.bypass.cooldown"), eq(DefaultPermissionLevel.OP), any(String.class)));
            manager.verify(() -> PermissionServiceManager.registerService(any(ForgePermissionService.class)));
        }
    }

    /* ================================================================== */
    /*  Metadata                                                           */
    /* ================================================================== */

    @Test
    @DisplayName("Backend name is correct")
    void getActiveBackendName() {
        assertEquals("Forge PermissionAPI (1.16.5)", new ForgePermissionService().getActiveBackendName());
    }

    @Test
    @DisplayName("Priority is 10")
    void getPriority() {
        assertEquals(10, new ForgePermissionService().getPriority());
    }

    /* ================================================================== */
    /*  Exception resilience                                               */
    /* ================================================================== */

    @Nested
    @DisplayName("Exception resilience")
    class ExceptionResilience {

        @Test
        @DisplayName("PermissionAPI throws for .skin.other → falls back to per-node op levels")
        void permissionApi_throwsOnOtherNode_fallbackToOp() {
            try (MockedStatic<ServerLifecycleHooks> hooks = mockStatic(ServerLifecycleHooks.class);
                 MockedStatic<PermissionAPI> api = mockStatic(PermissionAPI.class)) {
                hooks.when(ServerLifecycleHooks::getCurrentServer).thenReturn(null);
                api.when(() -> PermissionAPI.hasPermission(any(GameProfile.class), eq(SKIN_OTHER_NODE), isNull()))
                   .thenThrow(new RuntimeException("node not registered"));

                ForgePermissionService service = new ForgePermissionService();

                PermissionContext opCtx = PermissionContext.of(TEST_UUID, 2);
                assertTrue(service.hasPermission(opCtx.uuid(), opCtx.opLevel(), SKIN_OTHER_NODE));

                PermissionContext nonOpCtx = PermissionContext.of(TEST_UUID, 0);
                assertFalse(service.hasPermission(nonOpCtx.uuid(), nonOpCtx.opLevel(), SKIN_OTHER_NODE));
            }
        }
    }
}
