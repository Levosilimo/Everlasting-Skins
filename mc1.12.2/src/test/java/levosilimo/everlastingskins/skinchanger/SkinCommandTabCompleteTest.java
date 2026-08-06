/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.permission.PermissionTestSupport;
import levosilimo.everlastingskins.util.CompletionSources;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerList;
import net.minecraft.server.management.UserListOps;
import net.minecraft.server.management.UserListOpsEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tab-completion behavior of the 1.12.2 getTabCompletions switch: the
 * candidate lists mirror the 1.21 Brigadier tree and permission gates hide
 * subcommands, other-player targets, and metrics cleanup/reset from
 * unauthorized senders.
 */
class SkinCommandTabCompleteTest {

    private static final String[] ONLINE_PLAYERS = {"Alex", "Bob"};

    private final SkinCommand command = new SkinCommand();
    private MinecraftServer server;

    @BeforeEach
    void setUp() {
        Config.MINESKIN_ENABLED = false;
        Config.permissionsOpLevelMetrics = 2;
        Config.permissionsOpLevelMetricsReset = 2;
        // :common's manager registers no backend by itself (fail-closed); the
        // per-version bootstrap registers these — tests mirror the bootstrap.
        PermissionTestSupport.installVanilla();
        CompletionSources.setMojangProfileCache(new MojangProfileCache());

        server = mock(MinecraftServer.class);
        PlayerList playerList = mock(PlayerList.class);
        when(server.getPlayerList()).thenReturn(playerList);
        when(playerList.getOnlinePlayerNames()).thenReturn(ONLINE_PLAYERS);
    }

    @AfterEach
    void tearDown() {
        Config.MINESKIN_ENABLED = false;
        Config.permissionsOpLevelMetrics = 2;
        Config.permissionsOpLevelMetricsReset = 2;
        PermissionTestSupport.uninstall();
    }

    @Test
    @DisplayName("root completion filters subcommands by permission")
    void subcommand_completion_filtersByPermission() {
        assertEquals(Arrays.asList("set", "clear", "source"),
                command.getTabCompletions(server, playerSender(0), new String[]{""}, null));
        assertEquals(Arrays.asList("set", "clear", "source", "metrics"),
                command.getTabCompletions(server, playerSender(2), new String[]{""}, null));
    }

    @Test
    @DisplayName("set provider completion returns the configured providers")
    void set_provider_completion_returnsConfiguredProviders() {
        assertEquals(Arrays.asList("mojang", "random"),
                command.getTabCompletions(server, playerSender(0), new String[]{"set", ""}, null));

        Config.MINESKIN_ENABLED = true;
        assertEquals(Arrays.asList("mojang", "random", "web"),
                command.getTabCompletions(server, playerSender(0), new String[]{"set", ""}, null));
    }

    @Test
    @DisplayName("set mojang skin_name completion returns cached profiles and default skins")
    void set_mojang_skinName_completion_returnsRecentUsernames() {
        MojangProfileCache cache = new MojangProfileCache();
        cache.put("Notch", new CustomSkinProperty("value", "signature", "Notch"));
        CompletionSources.setMojangProfileCache(cache);

        List<String> suggestions = command.getTabCompletions(
                server, playerSender(0), new String[]{"set", "mojang", ""}, null);

        assertTrue(suggestions.contains("notch"));
        assertTrue(suggestions.contains("Steve"));
        assertFalse(suggestions.contains("<random>"));
    }

    @Test
    @DisplayName("set web variant completion returns classic and slim")
    void set_web_variant_completion_returnsClassicSlim() {
        assertEquals(Arrays.asList("classic", "slim"),
                command.getTabCompletions(server, playerSender(0), new String[]{"set", "web", ""}, null));
    }

    @Test
    @DisplayName("set web url completion returns allowlisted domains with scheme prefixes")
    void set_web_url_completion_returnsAllowlistDomains() {
        List<String> urls = command.getTabCompletions(
                server, playerSender(0), new String[]{"set", "web", "classic", ""}, null);

        assertTrue(urls.contains("https://imgur.com"));
        assertTrue(urls.contains("http://imgur.com"));
        assertTrue(urls.contains("https://textures.minecraft.net"));
        assertTrue(urls.contains("http://textures.minecraft.net"));
    }

    @Test
    @DisplayName("set random completion cascades bool, variant and permission-gated players")
    void set_random_completion_cascade() {
        assertEquals(Arrays.asList("true", "false"),
                command.getTabCompletions(server, playerSender(0), new String[]{"set", "random", ""}, null));
        assertEquals(Arrays.asList("classic", "slim"),
                command.getTabCompletions(server, playerSender(0), new String[]{"set", "random", "true", ""}, null));

        assertEquals(Arrays.asList("Alex", "Bob"), command.getTabCompletions(
                server, playerSender(2), new String[]{"set", "random", "true", "classic", ""}, null));
        assertEquals(Collections.emptyList(), command.getTabCompletions(
                server, playerSender(0), new String[]{"set", "random", "true", "classic", ""}, null));
    }

    @Test
    @DisplayName("clear and source target completion returns online players for authorized senders")
    void clear_source_target_completion_returnsOnlinePlayers() {
        assertEquals(Arrays.asList("Alex", "Bob"),
                command.getTabCompletions(server, playerSender(2), new String[]{"clear", ""}, null));
        assertEquals(Arrays.asList("Alex", "Bob"),
                command.getTabCompletions(server, playerSender(2), new String[]{"source", ""}, null));

        assertEquals(Collections.emptyList(), command.getTabCompletions(server, playerSender(0), new String[]{"clear", ""}, null));
    }

    @Test
    @DisplayName("metrics completion hides cleanup and reset from senders without the reset permission")
    void metrics_completion_filtersByResetPermission() {
        Config.permissionsOpLevelMetrics = 0;
        Config.permissionsOpLevelMetricsReset = 2;

        assertEquals(Arrays.asList("human", "json", "players"),
                command.getTabCompletions(server, playerSender(0), new String[]{"metrics", ""}, null));
        assertEquals(Arrays.asList("human", "json", "players", "cleanup", "reset"),
                command.getTabCompletions(server, playerSender(2), new String[]{"metrics", ""}, null));
    }

    private static EntityPlayerMP playerSender(int opLevel) {
        EntityPlayerMP player = mock(EntityPlayerMP.class);
        when(player.getUniqueID()).thenReturn(UUID.randomUUID());

        MinecraftServer mcServer = mock(MinecraftServer.class);
        PlayerList playerList = mock(PlayerList.class);
        when(mcServer.getPlayerList()).thenReturn(playerList);
        UserListOps ops = mock(UserListOps.class);
        when(playerList.getOppedPlayers()).thenReturn(ops);
        UserListOpsEntry entry = mock(UserListOpsEntry.class);
        when(entry.getPermissionLevel()).thenReturn(opLevel);
        when(ops.getEntry(any())).thenReturn(entry);

        setField(player, "mcServer", mcServer);
        return player;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = EntityPlayerMP.class.getField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot set EntityPlayerMP." + fieldName, e);
        }
    }
}
