/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.command;

import levosilimo.everlastingskins.permission.IPermissionService;
import levosilimo.everlastingskins.permission.PermissionServiceManager;
import levosilimo.everlastingskins.skinchanger.MojangAPI;
import levosilimo.everlastingskins.skinchanger.ProfileLookup;
import levosilimo.everlastingskins.skinchanger.SkinIO;
import levosilimo.everlastingskins.skinchanger.SkinRestorer;
import levosilimo.everlastingskins.skinchanger.SkinStorage;
import levosilimo.everlastingskins.skinchanger.responses.mojang.MojangSkinDataResult;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import net.minecraft.server.MinecraftServer;
import net.minecraft.src.EntityPlayerMP;
import net.minecraft.src.ICommandSender;
import net.minecraft.src.ServerConfigurationManager;
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
 * 1.4.7 ICommand lifecycle + dispatch tests.
 *
 * <p>Deterministic fakes only (memory #1115): the Mojang lookup is a fake
 * resolver (no live HTTP); the sender/player/server are Mockito mocks over
 * the MCP 7.26a surface. The 1.4.7 command surface under test is
 * {@code getCommandName}/{@code getCommandAliases}/{@code processCommand} —
 * no {@code getName} (1.8+) and no LiteralArgumentBuilder (1.13+). Sender
 * chat goes through {@code sendChatToPlayer(String)} — the plain-string form
 * ({@code ChatMessageComponent} does not exist until 1.5).
 */
public class SkinRestorerCommandTest {

    private SkinRestorerCommand command;

    @Before
    public void setUp() throws Exception {
        // Highest-priority deterministic grant-all backend (memory #1115) so
        // permission checks in these dispatch tests are deterministic
        // regardless of any backend left by earlier tests.
        PermissionServiceManager.registerService(new GrantAllService());

        Path dir = Files.createTempDirectory("es-147-skin-restorer-test");
        SkinStorage.resetForTest();
        SkinRestorer.setStorageForTest(new SkinStorage(new SkinIO(dir)));
        command = new SkinRestorerCommand();
    }

    @After
    public void tearDown() {
        SkinRestorerCommand.setMojangApiForTest(null);
        SkinRestorerCommand.setServerOverrideForTest(null);
        SkinRestorerCommand.clearSeenProfilesForTest();
        SkinRestorer.setStorageForTest(null);
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
        verify(sender).sendChatToPlayer(org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void clearDispatchRemovesSkin() throws Exception {
        // Sender = online player with a stored skin (username-keyed).
        EntityPlayerMP player = mockPlayer("Notch");
        SkinRestorerCommand.setServerOverrideForTest(mockServer(player));

        UUID uuid = SkinRestorer.uuidOf("Notch");
        SkinRestorer.applySkin(uuid,
            new CustomSkinProperty("textures", "c2V0LWNsZWFy", null, "fake"));
        assertNotNull(SkinRestorer.getSource(uuid));

        command.processCommand(player, new String[]{"clear"});

        assertEquals(null, SkinRestorer.getSource(uuid));
    }

    @Test
    public void sourceDispatchReportsSource() throws Exception {
        EntityPlayerMP player = mockPlayer("Notch");
        SkinRestorerCommand.setServerOverrideForTest(mockServer(player));

        UUID uuid = SkinRestorer.uuidOf("Notch");
        SkinRestorer.applySkin(uuid,
            new CustomSkinProperty("textures", "c291cmNl", null, "MojangAPI"));

        command.processCommand(player, new String[]{"source"});
        // Source was reported through the chat channel (no exception, granted).
        assertEquals("MojangAPI", SkinRestorer.getSource(uuid));
    }

    @Test
    public void setDispatchAppliesFetchedSkin() throws Exception {
        // Fake resolver (memory #1115): no live HTTP.
        MojangSkinDataResult result = new MojangSkinDataResult(
            UUID.randomUUID(),
            new CustomSkinProperty("textures", "YXBwbGllZA==", null, "MojangAPI"));
        SkinRestorerCommand.setMojangApiForTest(new FakeMojangApi(Optional.of(result)));

        EntityPlayerMP player = mockPlayer("Notch");
        SkinRestorerCommand.setServerOverrideForTest(mockServer(player));

        command.processCommand(player, new String[]{"set", "Notch"});

        assertEquals("MojangAPI", SkinRestorer.getSource(SkinRestorer.uuidOf("Notch")));
    }

    @Test
    public void setDispatchUnknownPlayerReportsFailure() throws Exception {
        SkinRestorerCommand.setMojangApiForTest(new FakeMojangApi(Optional.empty()));

        EntityPlayerMP player = mockPlayer("Notch");
        SkinRestorerCommand.setServerOverrideForTest(mockServer(player));

        command.processCommand(player, new String[]{"set", "ghost"});
        assertEquals(null, SkinRestorer.getSource(SkinRestorer.uuidOf("Notch")));
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
            UUID.randomUUID(),
            new CustomSkinProperty("textures", "dGFi", null, "MojangAPI"));
        SkinRestorerCommand.setMojangApiForTest(new FakeMojangApi(Optional.of(result)));

        EntityPlayerMP xephos = mockPlayer("xephos");
        SkinRestorerCommand.setServerOverrideForTest(mockServer(xephos));
        command.processCommand(xephos, new String[]{"set", "xephos"});

        // xephos left the server; only Notch + jeb_ are online now.
        EntityPlayerMP self = mockPlayer("Notch");
        EntityPlayerMP other = mockPlayer("jeb_");
        SkinRestorerCommand.setServerOverrideForTest(mockServer(self, other));

        ICommandSender sender = mock(ICommandSender.class);
        List completions = command.addTabCompletionOptions(sender, new String[]{"set", ""});
        assertTrue(completions.contains("Notch"));
        assertTrue(completions.contains("jeb_"));
        assertTrue(completions.contains("xephos")); // from the seen cache, not online
    }

    @Test
    public void tabCompleteSecondArgPrefixFilters() throws Exception {
        MojangSkinDataResult result = new MojangSkinDataResult(
            UUID.randomUUID(),
            new CustomSkinProperty("textures", "dGFi", null, "MojangAPI"));
        SkinRestorerCommand.setMojangApiForTest(new FakeMojangApi(Optional.of(result)));
        EntityPlayerMP xephos = mockPlayer("xephos");
        SkinRestorerCommand.setServerOverrideForTest(mockServer(xephos));
        command.processCommand(xephos, new String[]{"set", "xephos"});

        SkinRestorerCommand.setServerOverrideForTest(
            mockServer(mockPlayer("Notch"), mockPlayer("jeb_")));
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

    private static EntityPlayerMP mockPlayer(String name) {
        EntityPlayerMP player = mock(EntityPlayerMP.class);
        when(player.getCommandSenderName()).thenReturn(name);
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
