/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.integration.discordsrv;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.JDA;
import net.minecraft.server.level.ServerPlayer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DiscordSrvHook}.
 * <p>
 * Covers lib-39 scenarios DSRV-2 through DSRV-5.
 * DSRV-1 (DiscordSRV class absent) is not tested because DiscordSRV is on the
 * test classpath; the same graceful-path is covered by DSRV-2 (null plugin).
 * <p>
 * DiscordSRV relocates JDA to {@code github.scarsz.discordsrv.dependencies.jda.api},
 * so the production code's {@code Class.forName("net.dv8tion.jda.api.JDA")} will
 * not resolve in the test classpath. DSRV-5 verifies this path fails gracefully.
 * <p>
 * {@code doReturn} is used to stub {@code DiscordSRV#getJda()} because the
 * method's declared return type is the relocated JDA type.
 */
class DiscordSrvHookTest {

    @Test
    @DisplayName("null DiscordSRV plugin instance is handled gracefully (DSRV-2)")
    void announceSkinChange_handlesNullPluginInstance() {
        try (MockedStatic<DiscordSRV> dsrv = mockStatic(DiscordSRV.class)) {
            dsrv.when(DiscordSRV::getPlugin).thenReturn(null);
            assertDoesNotThrow(() -> DiscordSrvHook.announceSkinChange(null, "TestSource"));
        }
    }

    @Test
    @DisplayName("null JDA instance is handled gracefully (DSRV-3)")
    void announceSkinChange_handlesNullJDAInstance() {
        DiscordSRV srv = mock(DiscordSRV.class);
        doReturn(null).when(srv).getJda();

        try (MockedStatic<DiscordSRV> dsrv = mockStatic(DiscordSRV.class)) {
            dsrv.when(DiscordSRV::getPlugin).thenReturn(srv);
            assertDoesNotThrow(() -> DiscordSrvHook.announceSkinChange(null, "TestSource"));
        }
    }

    @Test
    @DisplayName("empty channel ID skips announcement (DSRV-4)")
    void announceSkinChange_skipsWhenChannelIdEmpty() {
        DiscordSRV srv = mock(DiscordSRV.class);
        doReturn(mock(JDA.class)).when(srv).getJda();

        try (MockedStatic<DiscordSRV> dsrv = mockStatic(DiscordSRV.class);
             MockedStatic<DiscordSrvConfig> cfg = mockStatic(DiscordSrvConfig.class)) {
            dsrv.when(DiscordSRV::getPlugin).thenReturn(srv);
            cfg.when(DiscordSrvConfig::getChannelId).thenReturn("");
            assertDoesNotThrow(() -> DiscordSrvHook.announceSkinChange(null, "TestSource"));
        }
    }

    @Test
    @DisplayName("graceful handling when JDA classes not found at net.dv8tion.jda.api (DSRV-5)")
    void announceSkinChange_handlesJdaClassNotFound() {
        DiscordSRV srv = mock(DiscordSRV.class);
        doReturn(mock(JDA.class)).when(srv).getJda();

        try (MockedStatic<DiscordSRV> dsrv = mockStatic(DiscordSRV.class);
             MockedStatic<DiscordSrvConfig> cfg = mockStatic(DiscordSrvConfig.class)) {
            dsrv.when(DiscordSRV::getPlugin).thenReturn(srv);
            cfg.when(DiscordSrvConfig::getChannelId).thenReturn("123456789");
            // Class.forName("net.dv8tion.jda.api.JDA") will throw
            // ClassNotFoundException, caught by the catch block
            assertDoesNotThrow(() -> DiscordSrvHook.announceSkinChange(null, "TestSource"));
        }
    }
}
