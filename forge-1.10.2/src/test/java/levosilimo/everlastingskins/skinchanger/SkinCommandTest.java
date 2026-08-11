/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import com.mojang.authlib.GameProfile;
import levosilimo.everlastingskins.enums.SkinVariant;
import levosilimo.everlastingskins.permission.PermissionTestSupport;
import levosilimo.everlastingskins.skinchanger.responses.mojang.MojangSkinDataResult;
import levosilimo.everlastingskins.skinchanger.responses.mineskin.MineSkinResponse;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure-JUnit tests for the 1.10.2 {@link SkinCommand} full-parity surface
 * (memory #1115: deterministic fakes only — fake Mojang/MineSkin APIs via
 * the package-private static seams, mocked storage via the SkinRestorer
 * test seam, permission stub via the ForgePermissionService protected
 * resolveOpLevel seam; no live server, no HTTP).
 *
 * <p>The /skin set/clear apply path is async (SkinAction executor); storage
 * mutations and player messages are asserted with Mockito timeout verifies.
 */
class SkinCommandTest {

    private static final UUID PLAYER_UUID = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
    private static final String FAKE_VALUE = "validTextureValue";
    private static final String FAKE_SIG = "validSignature";

    private SkinCommand command;
    private MinecraftServer server;
    private PlayerList playerList;
    private EntityPlayerMP player;
    private SkinStorage storage;
    private FakeMojangAPI fakeMojang;
    private FakeMineSkinAPI fakeMineSkin;

    static class FakeMojangAPI implements MojangAPI {
        final Map<String, CustomSkinProperty> skins = new HashMap<>();

        void addSkin(String name, CustomSkinProperty skin) {
            skins.put(name.toLowerCase(), skin);
        }

        @Override
        public Optional<MojangSkinDataResult> getSkin(String nameOrUniqueId) {
            CustomSkinProperty skin = skins.get(nameOrUniqueId.toLowerCase());
            if (skin == null) return Optional.empty();
            return Optional.of(new MojangSkinDataResult(UUID.randomUUID(), skin));
        }

        @Override
        public Optional<UUID> getUUID(String playerName) {
            return Optional.empty();
        }

        @Override
        public Optional<CustomSkinProperty> getProfile(ProfileLookup lookup) {
            return Optional.empty();
        }
    }

    static class FakeMineSkinAPI implements MineSkinAPI {
        CustomSkinProperty property = new CustomSkinProperty(FAKE_VALUE, FAKE_SIG, "MineSkin");

        @Override
        public MineSkinResponse genSkin(String url, SkinVariant variant) {
            return new MineSkinResponse(property, "fake-id", variant, variant);
        }
    }

    @BeforeEach
    void setUp() {
        command = new SkinCommand();
        server = mock(MinecraftServer.class);
        playerList = mock(PlayerList.class);
        when(server.getPlayerList()).thenReturn(playerList);

        player = mock(EntityPlayerMP.class);
        when(player.getUniqueID()).thenReturn(PLAYER_UUID);
        when(player.getGameProfile()).thenReturn(new GameProfile(PLAYER_UUID, "Steve"));

        storage = mock(SkinStorage.class);
        SkinRestorer.setSkinStorageForTest(storage);
        SkinRestorer.setServerForTest(server);

        fakeMojang = new FakeMojangAPI();
        fakeMineSkin = new FakeMineSkinAPI();
        SkinCommand.setMojangAPI(fakeMojang);
        SkinCommand.setMineSkinAPI(fakeMineSkin);

        grantOpLevel(4);
    }

    @AfterEach
    void tearDown() {
        PermissionTestSupport.resetManager();
        SkinRestorer.setSkinStorageForTest(null);
        SkinRestorer.setServerForTest(null);
        SkinCommand.resetAPIs();
    }

    private void grantOpLevel(int level) {
        PermissionTestSupport.grantOpLevel(level);
    }

    private static ITextComponent textContaining(String fragment) {
        return argThat(c -> c != null && c.getUnformattedText().contains(fragment));
    }

    @Test
    void exposesModernICommandSurface() {
        assertEquals("skin", command.getName());
        assertTrue(command.getAliases().contains("eskin"));
        assertTrue(command.getUsage(player).contains("set"));
        assertTrue(command.checkPermission(server, player));
    }

    @Test
    void noArgsSendsUsage() throws Exception {
        command.execute(server, player, new String[]{});
        verify(player).sendMessage(textContaining("/skin"));
    }

    @Test
    void setMojangSelfAppliesSkin() throws Exception {
        fakeMojang.addSkin("Notch", new CustomSkinProperty(FAKE_VALUE, FAKE_SIG, "MojangAPI"));

        command.execute(server, player, new String[]{"set", "mojang", "Notch"});

        verify(storage, timeout(3000)).setSkin(eq(PLAYER_UUID), any(CustomSkinProperty.class));
        verify(player, timeout(3000)).sendMessage(textContaining("Skin applied!"));
    }

    @Test
    void setMojangUnknownNameReportsNoSkin() throws Exception {
        command.execute(server, player, new String[]{"set", "mojang", "Nobody"});

        // The timeout-verify on the message proves the async pipeline settled
        // before the never-verify on the storage (mockito 2.x has no
        // timeout().never() combinator).
        verify(player, timeout(3000)).sendMessage(textContaining("No skin found."));
        verify(storage, never()).setSkin(any(UUID.class), any(CustomSkinProperty.class));
    }

    @Test
    void setWebSelfAppliesSkin() throws Exception {
        command.execute(server, player,
            new String[]{"set", "web", "classic", "http://imgur.com/x.png"});

        verify(storage, timeout(3000)).setSkin(eq(PLAYER_UUID), any(CustomSkinProperty.class));
        verify(player, timeout(3000)).sendMessage(textContaining("Skin applied!"));
    }

    @Test
    void setWebRequiresOpLevelTwoForSelf() throws Exception {
        grantOpLevel(1);

        command.execute(server, player,
            new String[]{"set", "web", "slim", "http://imgur.com/x.png"});

        verify(player).sendMessage(textContaining("permission"));
        verify(storage, never()).setSkin(any(UUID.class), any(CustomSkinProperty.class));
    }

    @Test
    void setMojangTargetingOthersRequiresOtherPermission() throws Exception {
        grantOpLevel(1);
        EntityPlayerMP other = mock(EntityPlayerMP.class);
        when(other.getUniqueID()).thenReturn(UUID.randomUUID());
        when(playerList.getPlayerByUsername("Alice")).thenReturn(other);

        command.execute(server, player, new String[]{"set", "mojang", "Notch", "Alice"});

        verify(player).sendMessage(textContaining("permission"));
        verify(storage, never()).setSkin(any(UUID.class), any(CustomSkinProperty.class));
    }

    @Test
    void clearRestoresFromStoredSource() throws Exception {
        fakeMojang.addSkin("Steve", new CustomSkinProperty(FAKE_VALUE, FAKE_SIG, "MojangAPI"));
        when(storage.getSource(PLAYER_UUID)).thenReturn("Steve");

        command.execute(server, player, new String[]{"clear"});

        verify(storage, timeout(3000)).setSkin(eq(PLAYER_UUID), any(CustomSkinProperty.class));
        verify(player, timeout(3000)).sendMessage(textContaining("Restored"));
    }

    @Test
    void sourceReportsStoredSource() throws Exception {
        when(storage.hasDefaultSkin(PLAYER_UUID)).thenReturn(false);
        when(storage.getSource(PLAYER_UUID)).thenReturn("MojangAPI");

        command.execute(server, player, new String[]{"source"});

        verify(player).sendMessage(textContaining("MojangAPI"));
    }

    @Test
    void sourceWithoutStoredSkinReportsDefault() throws Exception {
        when(storage.hasDefaultSkin(PLAYER_UUID)).thenReturn(true);

        command.execute(server, player, new String[]{"source"});

        verify(player).sendMessage(textContaining("Steve"));
    }

    @Test
    void metricsJsonSendsSnapshot() throws Exception {
        command.execute(server, player, new String[]{"metrics", "json"});
        verify(player).sendMessage(textContaining("{"));
    }

    @Test
    void metricsCleanupRequiresResetPermission() throws Exception {
        grantOpLevel(1);

        command.execute(server, player, new String[]{"metrics", "cleanup"});

        verify(player).sendMessage(textContaining("permission"));
    }

    @Test
    void tabCompletesRandomCascade() {
        List<String> capes = command.getTabCompletions(server, player, new String[]{"set", "random", ""}, new BlockPos(0, 0, 0));
        assertTrue(capes.contains("true"));
        assertTrue(capes.contains("false"));

        List<String> variants = command.getTabCompletions(server, player, new String[]{"set", "random", "true", ""}, new BlockPos(0, 0, 0));
        assertTrue(variants.contains("classic"));
        assertTrue(variants.contains("slim"));
    }

    @Test
    void tabCompletesProviders() {
        List<String> matches = command.getTabCompletions(server, player, new String[]{"set", ""}, new BlockPos(0, 0, 0));
        assertTrue(matches.contains("mojang"));
        assertTrue(matches.contains("web"));
        assertTrue(matches.contains("random"));
    }

    @Test
    void tabCompletesWebVariantAndUrls() {
        List<String> variants = command.getTabCompletions(server, player, new String[]{"set", "web", ""}, new BlockPos(0, 0, 0));
        assertTrue(variants.contains("classic"));
        assertTrue(variants.contains("slim"));

        List<String> urls = command.getTabCompletions(server, player, new String[]{"set", "web", "classic", ""}, new BlockPos(0, 0, 0));
        assertTrue(urls.contains("https://imgur.com"));
        assertTrue(urls.contains("http://crafatar.com"));
    }

    @Test
    void tabCompletesOnlinePlayersForClearWhenCanTargetOthers() {
        when(playerList.getOnlinePlayerNames()).thenReturn(new String[]{"Alice", "Bob"});

        List<String> matches = command.getTabCompletions(server, player, new String[]{"clear", ""}, new BlockPos(0, 0, 0));
        assertTrue(matches.contains("Alice"));
        assertTrue(matches.contains("Bob"));
    }

    @Test
    void tabCompletesOnlinePlayersOnlyWithOtherPermission() {
        grantOpLevel(1);
        when(playerList.getOnlinePlayerNames()).thenReturn(new String[]{"Alice"});

        List<String> matches = command.getTabCompletions(server, player, new String[]{"clear", ""}, new BlockPos(0, 0, 0));
        assertFalse(matches.contains("Alice"));
    }

    @Test
    void tabCompletesMetricsResetOnlyWithPermission() {
        List<String> allowed = command.getTabCompletions(server, player, new String[]{"metrics", ""}, new BlockPos(0, 0, 0));
        assertTrue(allowed.contains("reset"));
        assertTrue(allowed.contains("json"));

        grantOpLevel(1);
        List<String> denied = command.getTabCompletions(server, player, new String[]{"metrics", ""}, new BlockPos(0, 0, 0));
        assertFalse(denied.contains("reset"));
        assertTrue(denied.contains("json"));
    }

    @Test
    void tryRestoreFromMojangRestoresStoredSource() {
        fakeMojang.addSkin("Notch", new CustomSkinProperty(FAKE_VALUE, FAKE_SIG, "MojangAPI"));

        SkinCommand.MojangRestoreResult result =
            SkinCommand.tryRestoreFromMojang(fakeMojang, "Notch", "Steve");

        assertNotNull(result);
        assertEquals(FAKE_VALUE, result.skin.getOriginalProperty().getValue());
        assertEquals("Notch", result.licensedUsername);
    }

    @Test
    void tryRestoreFromMojangFallsBackToPlayerName() {
        fakeMojang.addSkin("Steve", new CustomSkinProperty(FAKE_VALUE, FAKE_SIG, "MojangAPI"));

        SkinCommand.MojangRestoreResult result =
            SkinCommand.tryRestoreFromMojang(fakeMojang, null, "Steve");

        assertNotNull(result);
        assertEquals("Steve", result.licensedUsername);
    }

    @Test
    void tryRestoreFromMojangReturnsNullWhenNoProfile() {
        assertNull(SkinCommand.tryRestoreFromMojang(fakeMojang, "Nobody", "Steve"));
    }
}
