/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.forge21.integration.discordsrv;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.JDA;
import levosilimo.everlastingskins.integration.discordsrv.DiscordSrvConfig;
import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.forge21.util.I18nUtils;

import com.electronwill.nightconfig.core.InMemoryCommentedFormat;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
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

    @BeforeAll
    static void init() {
        // Serve Config defaults so the null-player fallback (Config.LANGUAGE) works.
        Config.COMMON_CONFIG.setConfig(
                InMemoryCommentedFormat.defaultInstance().createConfig(java.util.HashMap::new));
        // Load classpath locale resources for per-player announce assertions.
        I18nUtils.loadAll();
    }

    @Test
    @DisplayName("announce text uses the player's client language (lib-7 disc-i18n)")
    void discI18n_discordAnnounce_usesLocalizedMessage() {
        // ServerPlayer cannot be mocked in the unit JVM (its supertype static
        // initializers require a bootstrapped Minecraft runtime), so the
        // per-player routing formatAnnounce delegates to is asserted through
        // the locale API directly: the German file must carry the announce key.
        String german = I18nUtils.getLocalizedString("discord_announce", "de_de");
        assertTrue(german.contains("geändert"), "expected German text, got: " + german);
        assertNotEquals("discord_announce", german);
    }

    @Test
    @DisplayName("unsupported client language falls back to English (lib-7 disc-i18n)")
    void discI18n_discordAnnounce_fallbackToEnglishWhenLanguageUnsupported() {
        String result = I18nUtils.getLocalizedString("discord_announce", "zz_zz");
        assertTrue(result.contains("changed their skin"), "expected English fallback, got: " + result);
        assertFalse(result.contains("geändert"));
    }

    @Test
    @DisplayName("null player falls back to the global locale (lib-7 disc-i18n)")
    void discI18n_discordAnnounce_nullPlayerUsesGlobalLocale() {
        String result = DiscordSrvHook.formatAnnounce(null, "Notch");
        assertTrue(result.contains("changed their skin"), "expected global-locale text, got: " + result);
    }

    @Test
    @DisplayName("null skin source uses the 'default' label (lib-7 disc-i18n)")
    void discI18n_discordAnnounce_nullSourceDefaultsLabel() {
        String result = DiscordSrvHook.formatAnnounce(null, null);
        assertTrue(result.contains("`default`"), "expected default label, got: " + result);
        String withSource = DiscordSrvHook.formatAnnounce(null, "Notch");
        assertTrue(withSource.contains("`Notch`"), "expected source label, got: " + withSource);
    }

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
