/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.integration.discordsrv;

import levosilimo.everlastingskins.skinchanger.SkinRestorer;
import levosilimo.everlastingskins.util.I18nUtils;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DiscordSrvHook}.
 * <p>
 * Covers lib-39 scenario DSRV-2 (null plugin) which is testable without
 * mocking — {@code DiscordSRV.getPlugin()} returns {@code null} naturally
 * when Bukkit is not running.
 * <p>
 * DSRV-3 through DSRV-5 require Mockito static mocking of DiscordSRV, which
 * is unavailable here because:
 * <ol>
 *   <li>DiscordSRV's constant pool references PaperMC types not available
 *       on the mc1.12.2 classpath — ByteBuddy retransformation fails.</li>
 *   <li>Mockito 2.x (the version compatible with Java 8 / ForgeGradle 2.3)
 *       does not support {@code mockStatic}.</li>
 * </ol>
 * These scenarios rely on the same reflection chain and are covered by the
 * 1.21 target where PaperMC API is available.
 * <p>
 * {@code doReturn} cannot bypass the type mismatch for {@code getJda()} return
 * type because the relocated JDA type is not imported and returning {@code null}
 * is the only value that satisfies both the compiler and Mockito's type check.
 * <p>
 * The announce-text tests exercise {@code formatAnnounce}, which is a pure
 * function (no DiscordSRV interaction). Locale resources are loaded via a
 * fake server through {@link I18nUtils#loadAll()} and unloaded afterwards so
 * other test classes still see the empty-map regime.
 */
class DiscordSrvHookTest {

    private static MinecraftServer fakeServer;

    @BeforeAll
    static void loadLocales() throws Exception {
        fakeServer = mock(MinecraftServer.class);
        File configDir = Files.createTempDirectory("es1122-discord-i18n-test").toFile();
        when(fakeServer.getFile(anyString())).thenReturn(configDir);
        SkinRestorer.setServer(fakeServer);
        I18nUtils.loadAll();
    }

    @AfterAll
    static void unloadLocales() {
        SkinRestorer.setServer(null);
        I18nUtils.loadAll(); // clears the locale map back to the empty regime
    }

    @Test
    @DisplayName("null DiscordSRV plugin instance is handled gracefully (DSRV-2)")
    void announceSkinChange_handlesNullPluginInstance() {
        // DiscordSRV.getPlugin() returns null when Bukkit is not running.
        // No mocking required — the reflection chain hits null, logs, and returns.
        assertDoesNotThrow(() -> DiscordSrvHook.announceSkinChange(null, "TestSource"));
    }

    @Test
    @DisplayName("announce text uses the player's client language (lib-7 disc-i18n)")
    void discI18n_discordAnnounce_usesLocalizedMessage() {
        EntityPlayerMP player = mock(EntityPlayerMP.class);
        player.language = "ru_ru";
        when(player.getDisplayNameString()).thenReturn("Notch");

        String result = DiscordSrvHook.formatAnnounce(player, "Notch");

        assertTrue(result.contains("сменил(а) скин"), "expected Russian text, got: " + result);
        assertNotEquals("discord_announce", result);
        assertTrue(result.contains("Notch"));
    }

    @Test
    @DisplayName("unsupported client language falls back to English (lib-7 disc-i18n)")
    void discI18n_discordAnnounce_fallbackToEnglishWhenLanguageUnsupported() {
        EntityPlayerMP player = mock(EntityPlayerMP.class);
        player.language = "zz_zz";
        when(player.getDisplayNameString()).thenReturn("Notch");

        String result = DiscordSrvHook.formatAnnounce(player, "Notch");

        assertTrue(result.contains("changed their skin"), "expected English fallback, got: " + result);
        assertFalse(result.contains("сменил"));
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
        EntityPlayerMP player = mock(EntityPlayerMP.class);
        player.language = "en_us";
        when(player.getDisplayNameString()).thenReturn("Notch");

        String result = DiscordSrvHook.formatAnnounce(player, null);

        assertTrue(result.contains("`default`"), "expected default label, got: " + result);
    }
}
