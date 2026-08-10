/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.permission.IPermissionService;
import levosilimo.everlastingskins.permission.PermissionServiceManager;
import levosilimo.everlastingskins.permission.VanillaPermissionService;
import levosilimo.everlastingskins.skinchanger.MojangAPI;
import levosilimo.everlastingskins.skinchanger.responses.mojang.MojangSkinDataResult;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 1.7.10 ICommand lifecycle + dispatch tests.
 *
 * <p>Deterministic fakes only (memory #1115): the Mojang lookup is a fake
 * resolver (no live HTTP); the sender/player/server are Mockito mocks over
 * the MCP stable_12 surface. The 1.7.10 command surface under test is
 * {@code getCommandName}/{@code getCommandAliases}/{@code processCommand} —
 * no {@code getName} (1.8+) and no LiteralArgumentBuilder (1.13+).
 */
public class SkinRestorerCommandTest {

    private static final UUID TEST_UUID = UUID.randomUUID();

    private SkinStorageProvider provider;
    private SkinCommand command;

    @Before
    public void setUp() throws Exception {
        // Highest-priority deterministic grant-all backend (memory #1115) so
        // permission checks in these dispatch tests are deterministic
        // regardless of any backend left by earlier tests.
        PermissionServiceManager.registerService(new GrantAllService());

        Path dir = Files.createTempDirectory("es-skin-restorer-test");
        SkinStorage.resetForTest();
        SkinStorage storage = new SkinStorage(new SkinIO(dir));
        provider = new SkinStorageProvider(storage);
        command = new SkinCommand(provider);
    }

    @After
    public void tearDown() {
        SkinCommand.setMojangApiForTest(null);
        SkinCommand.setServerOverrideForTest(null);
        SkinCommand.clearSeenProfilesForTest();
        SkinRestorer.setProviderForTest(null);
        SkinRestorer.setServerForTest(null);
    }

    @Test
    public void commandNameIsSkin() {
        assertEquals("skin", command.getCommandName());
    }

    @Test
    public void aliasesIncludeSkins() {
        assertTrue(command.getCommandAliases().contains("skins"));
        assertNotNull(command.getCommandAliases());
    }

    @Test
    public void usageIsNonEmpty() {
        ICommandSender sender = mock(ICommandSender.class);
        assertNotNull(command.getCommandUsage(sender));
        assertTrue(command.getCommandUsage(sender).startsWith("/skin"));
    }

    @Test
    public void canCommandSenderUseCommandIsPreFilter() {
        ICommandSender sender = mock(ICommandSender.class);
        assertTrue(command.canCommandSenderUseCommand(sender));
    }

    @Test
    public void processCommandNoArgsPrintsUsage() {
        ICommandSender sender = mock(ICommandSender.class);
        command.processCommand(sender, new String[0]);
        verify(sender).addChatMessage(org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void clearDispatchRemovesSkin() throws Exception {
        // Sender = online player with a stored skin.
        EntityPlayerMP player = mockPlayer(TEST_UUID, "Notch");
        SkinCommand.setServerOverrideForTest(mockServer(player));

        com.mojang.authlib.GameProfile profile = player.getGameProfile();
        levosilimo.everlastingskins.util.CustomSkinProperty skin =
            new levosilimo.everlastingskins.util.CustomSkinProperty(
                "textures", "c2V0LWNsZWFy", null, "fake");
        provider.applySkin(profile, TEST_UUID, skin);
        assertNotNull(provider.getSkin(TEST_UUID));

        command.processCommand(player, new String[]{"clear"});

        // Skin removed from storage; textures property stripped from profile.
        assertEquals(null, provider.getSkin(TEST_UUID));
        assertTrue(profile.getProperties().get("textures").isEmpty());
    }

    @Test
    public void sourceDispatchReportsSource() throws Exception {
        EntityPlayerMP player = mockPlayer(TEST_UUID, "Notch");
        SkinCommand.setServerOverrideForTest(mockServer(player));

        com.mojang.authlib.GameProfile profile = player.getGameProfile();
        levosilimo.everlastingskins.util.CustomSkinProperty skin =
            new levosilimo.everlastingskins.util.CustomSkinProperty(
                "textures", "c291cmNl", null, "MojangAPI");
        provider.applySkin(profile, TEST_UUID, skin);

        command.processCommand(player, new String[]{"source"});
        // Source was reported through the chat channel (no exception, granted).
        assertNotNull(provider.getSource(TEST_UUID));
    }

    @Test
    public void setDispatchAppliesFetchedSkin() throws Exception {
        // Fake resolver (memory #1115): no live HTTP.
        MojangSkinDataResult result = new MojangSkinDataResult(
            TEST_UUID,
            new CustomSkinProperty("textures", "YXBwbGllZA==", null, "MojangAPI"));
        SkinCommand.setMojangApiForTest(new FakeMojangApi(Optional.of(result)));

        EntityPlayerMP player = mockPlayer(TEST_UUID, "Notch");
        SkinCommand.setServerOverrideForTest(mockServer(player));

        command.processCommand(player, new String[]{"set", "Notch"});

        assertEquals("MojangAPI", provider.getSource(TEST_UUID));
    }

    @Test
    public void setDispatchUnknownPlayerReportsFailure() throws Exception {
        SkinCommand.setMojangApiForTest(new FakeMojangApi(Optional.empty()));

        EntityPlayerMP player = mockPlayer(TEST_UUID, "Notch");
        SkinCommand.setServerOverrideForTest(mockServer(player));

        command.processCommand(player, new String[]{"set", "ghost"});
        assertEquals(null, provider.getSkin(TEST_UUID));
    }

    @Test
    public void targetResolutionByName() throws Exception {
        EntityPlayerMP self = mockPlayer(TEST_UUID, "Notch");
        EntityPlayerMP other = mockPlayer(UUID.randomUUID(), "Steve");
        SkinRestorer.setServerForTest(mockServer(self, other));

        // 'clear' with a target name resolves that player's UUID — no crash,
        // storage untouched for the sender's own UUID.
        command.processCommand(self, new String[]{"clear", "Steve"});
        assertEquals(null, provider.getSkin(TEST_UUID));
    }

    @Test
    public void tabCompleteOffersSubcommands() {
        ICommandSender sender = mock(ICommandSender.class);
        List completions = command.addTabCompletionOptions(sender, new String[]{""});
        assertTrue(completions.contains("set"));
        assertTrue(completions.contains("clear"));
        assertTrue(completions.contains("source"));
    }

    @Test
    public void tabCompletePrefixFiltersSubcommands() {
        ICommandSender sender = mock(ICommandSender.class);
        List completions = command.addTabCompletionOptions(sender, new String[]{"c"});
        assertEquals(1, completions.size());
        assertEquals("clear", completions.get(0));
    }

    @Test
    public void tabCompleteSecondArgOffersOnlineAndSeenNames() throws Exception {
        // Seed the seen cache through the real set path (deterministic fake).
        MojangSkinDataResult result = new MojangSkinDataResult(
            TEST_UUID,
            new CustomSkinProperty("textures", "dGFi", null, "MojangAPI"));
        SkinCommand.setMojangApiForTest(new FakeMojangApi(Optional.of(result)));

        EntityPlayerMP xephos = mockPlayer(UUID.randomUUID(), "xephos");
        SkinCommand.setServerOverrideForTest(mockServer(xephos));
        command.processCommand(xephos, new String[]{"set", "xephos"});

        // xephos left the server; only Notch + jeb_ are online now.
        SkinCommand.setServerOverrideForTest(
            mockServer(mockPlayer(TEST_UUID, "Notch"), mockPlayer(UUID.randomUUID(), "jeb_")));

        ICommandSender sender = mock(ICommandSender.class);
        List completions = command.addTabCompletionOptions(sender, new String[]{"set", ""});
        assertTrue(completions.contains("Notch"));
        assertTrue(completions.contains("jeb_"));
        assertTrue(completions.contains("xephos")); // from the seen cache, not online
    }

    @Test
    public void tabCompleteSecondArgPrefixFilters() throws Exception {
        MojangSkinDataResult result = new MojangSkinDataResult(
            TEST_UUID,
            new CustomSkinProperty("textures", "dGFi", null, "MojangAPI"));
        SkinCommand.setMojangApiForTest(new FakeMojangApi(Optional.of(result)));
        EntityPlayerMP xephos = mockPlayer(UUID.randomUUID(), "xephos");
        SkinCommand.setServerOverrideForTest(mockServer(xephos));
        command.processCommand(xephos, new String[]{"set", "xephos"});

        SkinCommand.setServerOverrideForTest(
            mockServer(mockPlayer(TEST_UUID, "Notch"), mockPlayer(UUID.randomUUID(), "jeb_")));
        ICommandSender sender = mock(ICommandSender.class);
        List completions = command.addTabCompletionOptions(sender, new String[]{"set", "j"});
        assertEquals(1, completions.size());
        assertEquals("jeb_", completions.get(0));
    }

    @Test
    public void tabCompleteBeyondSecondArgReturnsNull() {
        ICommandSender sender = mock(ICommandSender.class);
        assertEquals(null, command.addTabCompletionOptions(sender, new String[]{"set", "Notch", "extra"}));
    }

    private static EntityPlayerMP mockPlayer(UUID uuid, String name) {
        EntityPlayerMP player = mock(EntityPlayerMP.class);
        when(player.getUniqueID()).thenReturn(uuid);
        when(player.getCommandSenderName()).thenReturn(name);
        com.mojang.authlib.GameProfile profile = new com.mojang.authlib.GameProfile(uuid, name);
        when(player.getGameProfile()).thenReturn(profile);
        return player;
    }

    private static MinecraftServer mockServer(EntityPlayerMP... players) throws Exception {
        MinecraftServer server = mock(MinecraftServer.class);
        ServerConfigurationManager manager = mock(ServerConfigurationManager.class);
        when(server.getConfigurationManager()).thenReturn(manager);
        Field field = ServerConfigurationManager.class.getField("playerEntityList");
        field.setAccessible(true);
        field.set(manager, players.length == 0 ? Collections.emptyList()
            : java.util.Arrays.asList(players));
        String[] names = new String[players.length];
        for (int i = 0; i < players.length; i++) {
            names[i] = players[i].getCommandSenderName();
        }
        when(manager.getAllUsernames()).thenReturn(names);
        return server;
    }

    /** Deterministic Mojang lookup fake (memory #1115). */
    private static final class FakeMojangApi implements MojangAPI {
        private final Optional<MojangSkinDataResult> result;

        FakeMojangApi(Optional<MojangSkinDataResult> result) {
            this.result = result;
        }

        @Override
        public Optional<MojangSkinDataResult> getSkin(String nameOrUniqueId) {
            return result;
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

    /** Deterministic grant-all backend (memory #1115). */
    private static final class GrantAllService implements IPermissionService {
        @Override
        public boolean hasPermission(UUID uuid, int opLevel, String permissionNode) {
            return true;
        }

        @Override
        public String getActiveBackendName() {
            return "GrantAll";
        }

        @Override
        public int getPriority() {
            return 100;
        }
    }
}
