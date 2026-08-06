/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.integration.discordsrv;

import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.util.I18nUtils;
import net.minecraft.entity.player.ServerPlayerEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.electronwill.nightconfig.core.InMemoryCommentedFormat;


import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the 1.16.5 {@link DiscordSrvHook}.
 *
 * <p>Unlike the 1.21 lane (which compiles against the DiscordSRV API), the
 * 1.16.5 hook is fully reflective: {@code Class.forName} is the only entry
 * point, and DiscordSRV is not on this lane's (test) classpath. That means
 * every DSRV scenario — plugin absent, JDA absent, channel unset — reduces
 * to one graceful path: the class lookup fails and the hook skips. The
 * localized announcement text (per-player i18n, null fallbacks) is the
 * part with real logic and is pinned here directly.
 */
class DiscordSrvHookTest {

    /**
     * The unit-test JVM has no running server. On 1.16.5 the vanilla
     * registry chain is self-initializing, but the order matters:
     * {@code World.<clinit>} → {@code Registry.<clinit>} →
     * {@code MemoryModuleType.<clinit>} → {@code GlobalPos.<clinit>} reads
     * {@code World.RESOURCE_KEY_CODEC} — if {@code World} started the chain
     * it is mid-clinit there and the read is null (NPE). The game initializes
     * {@code Registry} first; force the same order here so {@code World}'s
     * clinit always runs fresh. (1.16.5 has no isBootstrapped gate on
     * registry access, so no flag trick is needed.)
     */
    static {
        try {
            Class.forName("net.minecraft.util.registry.Registry");
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @BeforeAll
    static void init() {
        // Serve Config defaults so the null-player fallback (Config.LANGUAGE) works.
        Config.COMMON_CONFIG.setConfig(
                InMemoryCommentedFormat.defaultInstance().createConfig(java.util.HashMap::new));
        // Load classpath locale resources for the announce assertions.
        I18nUtils.loadAll();
    }

    @Test
    @DisplayName("announce text uses the requested client language")
    void discI18n_discordAnnounce_usesLocalizedMessage() {
        String german = I18nUtils.getLocalizedString("discord_announce", "de_de");
        assertTrue(german.contains("geändert"), "expected German text, got: " + german);
        assertNotEquals("discord_announce", german);
    }

    @Test
    @DisplayName("unsupported client language falls back to English")
    void discI18n_discordAnnounce_fallbackToEnglishWhenLanguageUnsupported() {
        String result = I18nUtils.getLocalizedString("discord_announce", "zz_zz");
        assertTrue(result.contains("changed their skin"), "expected English fallback, got: " + result);
    }

    @Test
    @DisplayName("null player falls back to the global locale")
    void discI18n_discordAnnounce_nullPlayerUsesGlobalLocale() {
        String result = DiscordSrvHook.formatAnnounce(null, "Notch");
        assertTrue(result.contains("changed their skin"), "expected global-locale text, got: " + result);
    }

    @Test
    @DisplayName("null skin source uses the 'default' label")
    void discI18n_discordAnnounce_nullSourceDefaultsLabel() {
        String result = DiscordSrvHook.formatAnnounce(null, null);
        assertTrue(result.contains("`default`"), "expected default label, got: " + result);
        String withSource = DiscordSrvHook.formatAnnounce(null, "Notch");
        assertTrue(withSource.contains("`Notch`"), "expected source label, got: " + withSource);
    }

    @Test
    @DisplayName("per-player format includes the scoreboard name")
    void discI18n_discordAnnounce_includesPlayerName() {
        ServerPlayerEntity player = mock(ServerPlayerEntity.class);
        when(player.getScoreboardName()).thenReturn("Steve");
        String result = DiscordSrvHook.formatAnnounce(player, "Notch");
        assertTrue(result.contains("Steve"), "expected player name in text, got: " + result);
    }

    @Test
    @DisplayName("DiscordSRV absent on the classpath is handled gracefully (reflective chain)")
    void announceSkinChange_handlesMissingDiscordSrvClass() {
        // Class.forName("github.scarsz.discordsrv.DiscordSRV") cannot resolve
        // in this JVM; the hook must skip without throwing.
        assertDoesNotThrow(() -> DiscordSrvHook.announceSkinChange(null, "TestSource"));
    }
}
