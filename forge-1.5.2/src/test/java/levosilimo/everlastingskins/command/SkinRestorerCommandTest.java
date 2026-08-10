/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.command;

import levosilimo.everlastingskins.enums.SkinVariant;
import levosilimo.everlastingskins.metrics.SkinMetrics;
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

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 1.5.2 ICommand lifecycle + dispatch tests.
 *
 * <p>Deterministic fakes only (memory #1115): the Mojang lookup is a fake
 * resolver (no live HTTP); the random pick is an injected fake picker; the
 * sender/player/server are Mockito mocks over the MCP 7.51 surface. The
 * 1.6.4 command surface under test is
 * {@code getCommandName}/{@code getCommandAliases}/{@code processCommand} —
 * no {@code getName} (1.8+) and no LiteralArgumentBuilder (1.13+). Sender
 * chat goes through {@code sendChatToPlayer(String)} (1.5.2 predates {@code ChatMessageComponent}, which arrived in 1.6).
 *
 * <p>Covers the command/storage parity surface (set random, metrics,
 * multi-target + permission gating, web rejection) and the extended tab
 * completion.
 */
public class SkinRestorerCommandTest {

    private SkinRestorerCommand command;

    @Before
    public void setUp() throws Exception {
        // Highest-priority deterministic grant-all backend (memory #1115) so
        // permission checks in these dispatch tests are deterministic
        // regardless of any backend left by earlier tests. A sticky
        // DenyNodeService (priority 1000) grants everything once its deny
        // set is cleared, so behaviour stays grant-all across tests.
        DenyNodeService.deny(null);
        PermissionServiceManager.registerService(new GrantAllService());
        SkinMetrics.INSTANCE.reset();

        Path dir = Files.createTempDirectory("es-152-skin-restorer-test");
        SkinStorage.resetForTest();
        SkinRestorer.setStorageForTest(new SkinStorage(new SkinIO(dir)));
        command = new SkinRestorerCommand();
    }

    @After
    public void tearDown() {
        SkinRestorerCommand.setMojangApiForTest(null);
        SkinRestorerCommand.setServerOverrideForTest(null);
        SkinRestorerCommand.clearSeenProfilesForTest();
        SkinRestorerCommand.setRandomPickSourceForTest(null);
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
    public void setDispatchWebRejectsWithEraMessage() throws Exception {
        // Pre-GameProfile era limitation: no MineSkin/URL pipeline exists,
        // so set web is honestly rejected and nothing is stored.
        MojangSkinDataResult result = new MojangSkinDataResult(
            UUID.randomUUID(),
            new CustomSkinProperty("textures", "d2Vi", null, "MojangAPI"));
        SkinRestorerCommand.setMojangApiForTest(new FakeMojangApi(Optional.of(result)));

        EntityPlayerMP player = mockPlayer("Notch");
        SkinRestorerCommand.setServerOverrideForTest(mockServer(player));

        command.processCommand(player, new String[]{"set", "web"});

        assertEquals(null, SkinRestorer.getSource(SkinRestorer.uuidOf("Notch")));
        verify(player).sendChatToPlayer(org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void setDispatchMultiTargetAppliesToAllNamed() throws Exception {
        MojangSkinDataResult result = new MojangSkinDataResult(
            UUID.randomUUID(),
            new CustomSkinProperty("textures", "bXVsdGk=", null, "MojangAPI"));
        SkinRestorerCommand.setMojangApiForTest(new FakeMojangApi(Optional.of(result)));

        EntityPlayerMP self = mockPlayer("Notch");
        EntityPlayerMP other = mockPlayer("jeb_");
        EntityPlayerMP third = mockPlayer("xephos");
        SkinRestorerCommand.setServerOverrideForTest(mockServer(self, other, third));

        command.processCommand(self, new String[]{"set", "Notch", "jeb_", "xephos"});

        assertEquals("MojangAPI", SkinRestorer.getSource(SkinRestorer.uuidOf("jeb_")));
        assertEquals("MojangAPI", SkinRestorer.getSource(SkinRestorer.uuidOf("xephos")));
        assertEquals(null, SkinRestorer.getSource(SkinRestorer.uuidOf("Notch")));
    }

    @Test
    public void setDispatchTargetingOthersRequiresOtherPermission() throws Exception {
        MojangSkinDataResult result = new MojangSkinDataResult(
            UUID.randomUUID(),
            new CustomSkinProperty("textures", "b3RoZXI=", null, "MojangAPI"));
        SkinRestorerCommand.setMojangApiForTest(new FakeMojangApi(Optional.of(result)));
        DenyNodeService.deny("everlastingskins.command.skin.other");
        PermissionServiceManager.registerService(new DenyNodeService());

        EntityPlayerMP self = mockPlayer("Notch");
        EntityPlayerMP other = mockPlayer("jeb_");
        SkinRestorerCommand.setServerOverrideForTest(mockServer(self, other));

        command.processCommand(self, new String[]{"set", "Notch", "jeb_"});

        assertEquals(null, SkinRestorer.getSource(SkinRestorer.uuidOf("jeb_")));
    }

    @Test
    public void clearDispatchMultiTargetClearsOnlyNamed() throws Exception {
        UUID selfUuid = SkinRestorer.uuidOf("Notch");
        UUID otherUuid = SkinRestorer.uuidOf("jeb_");
        SkinRestorer.applySkin(selfUuid,
            new CustomSkinProperty("textures", "YQ==", null, "fake"));
        SkinRestorer.applySkin(otherUuid,
            new CustomSkinProperty("textures", "Yg==", null, "fake"));

        EntityPlayerMP self = mockPlayer("Notch");
        EntityPlayerMP other = mockPlayer("jeb_");
        SkinRestorerCommand.setServerOverrideForTest(mockServer(self, other));

        command.processCommand(self, new String[]{"clear", "jeb_"});

        assertEquals(null, SkinRestorer.getSource(otherUuid));
        assertNotNull(SkinRestorer.getSource(selfUuid));
    }

    @Test
    public void setRandomDispatchAppliesPickedSkin() throws Exception {
        MojangSkinDataResult result = new MojangSkinDataResult(
            UUID.randomUUID(),
            new CustomSkinProperty("textures", "cmFuZG9t", null, "MojangAPI"));
        SkinRestorerCommand.setMojangApiForTest(new FakeMojangApi(Optional.of(result)));
        SkinRestorerCommand.setRandomPickSourceForTest(new FixedRandomPickSource("pickedUser"));

        EntityPlayerMP player = mockPlayer("Notch");
        SkinRestorerCommand.setServerOverrideForTest(mockServer(player));

        command.processCommand(player, new String[]{"set", "random"});

        assertEquals("MojangAPI", SkinRestorer.getSource(SkinRestorer.uuidOf("Notch")));
    }

    @Test
    public void setRandomCapeFlagReachesPicker() throws Exception {
        MojangSkinDataResult result = new MojangSkinDataResult(
            UUID.randomUUID(),
            new CustomSkinProperty("textures", "Y2FwZQ==", null, "MojangAPI"));
        SkinRestorerCommand.setMojangApiForTest(new FakeMojangApi(Optional.of(result)));
        RecordingRandomPickSource picker = new RecordingRandomPickSource("pickedUser");
        SkinRestorerCommand.setRandomPickSourceForTest(picker);

        EntityPlayerMP player = mockPlayer("Notch");
        SkinRestorerCommand.setServerOverrideForTest(mockServer(player));

        command.processCommand(player, new String[]{"set", "random", "true"});

        assertTrue(picker.lastCape);
        assertEquals(SkinVariant.ALL, picker.lastVariant);
    }

    @Test
    public void setRandomVariantParsingReachesPicker() throws Exception {
        MojangSkinDataResult result = new MojangSkinDataResult(
            UUID.randomUUID(),
            new CustomSkinProperty("textures", "dmFyaWFudA==", null, "MojangAPI"));
        SkinRestorerCommand.setMojangApiForTest(new FakeMojangApi(Optional.of(result)));
        RecordingRandomPickSource picker = new RecordingRandomPickSource("pickedUser");
        SkinRestorerCommand.setRandomPickSourceForTest(picker);

        EntityPlayerMP player = mockPlayer("Notch");
        SkinRestorerCommand.setServerOverrideForTest(mockServer(player));

        command.processCommand(player, new String[]{"set", "random", "false", "slim"});

        assertFalse(picker.lastCape);
        assertEquals(SkinVariant.SLIM, picker.lastVariant);
    }

    @Test
    public void setRandomPickerNetworkFailureReportsError() throws Exception {
        SkinRestorerCommand.setRandomPickSourceForTest(new FailingRandomPickSource());

        EntityPlayerMP player = mockPlayer("Notch");
        SkinRestorerCommand.setServerOverrideForTest(mockServer(player));

        command.processCommand(player, new String[]{"set", "random"});

        assertEquals(null, SkinRestorer.getSource(SkinRestorer.uuidOf("Notch")));
    }

    @Test
    public void setRandomPickerEmptyPickReportsFailure() throws Exception {
        SkinRestorerCommand.setRandomPickSourceForTest(new FixedRandomPickSource(null));

        EntityPlayerMP player = mockPlayer("Notch");
        SkinRestorerCommand.setServerOverrideForTest(mockServer(player));

        command.processCommand(player, new String[]{"set", "random"});

        assertEquals(null, SkinRestorer.getSource(SkinRestorer.uuidOf("Notch")));
    }

    @Test
    public void setRandomDispatchTargetsOthers() throws Exception {
        MojangSkinDataResult result = new MojangSkinDataResult(
            UUID.randomUUID(),
            new CustomSkinProperty("textures", "dGFyZ2V0", null, "MojangAPI"));
        SkinRestorerCommand.setMojangApiForTest(new FakeMojangApi(Optional.of(result)));
        SkinRestorerCommand.setRandomPickSourceForTest(new FixedRandomPickSource("pickedUser"));

        EntityPlayerMP self = mockPlayer("Notch");
        EntityPlayerMP other = mockPlayer("jeb_");
        SkinRestorerCommand.setServerOverrideForTest(mockServer(self, other));

        command.processCommand(self, new String[]{"set", "random", "false", "all", "jeb_"});

        assertEquals("MojangAPI", SkinRestorer.getSource(SkinRestorer.uuidOf("jeb_")));
        assertEquals(null, SkinRestorer.getSource(SkinRestorer.uuidOf("Notch")));
    }

    @Test
    public void setRandomTargetingOthersRequiresOtherPermission() throws Exception {
        MojangSkinDataResult result = new MojangSkinDataResult(
            UUID.randomUUID(),
            new CustomSkinProperty("textures", "ZGVueQ==", null, "MojangAPI"));
        SkinRestorerCommand.setMojangApiForTest(new FakeMojangApi(Optional.of(result)));
        SkinRestorerCommand.setRandomPickSourceForTest(new FixedRandomPickSource("pickedUser"));
        DenyNodeService.deny("everlastingskins.command.skin.other");
        PermissionServiceManager.registerService(new DenyNodeService());

        EntityPlayerMP self = mockPlayer("Notch");
        EntityPlayerMP other = mockPlayer("jeb_");
        SkinRestorerCommand.setServerOverrideForTest(mockServer(self, other));

        command.processCommand(self, new String[]{"set", "random", "false", "all", "jeb_"});

        assertEquals(null, SkinRestorer.getSource(SkinRestorer.uuidOf("jeb_")));
    }

    @Test
    public void metricsDispatchReportsSnapshot() throws Exception {
        EntityPlayerMP player = mockPlayer("Notch");
        SkinRestorerCommand.setServerOverrideForTest(mockServer(player));

        command.processCommand(player, new String[]{"metrics"});

        verify(player).sendChatToPlayer(org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void metricsJsonDispatchReportsJson() throws Exception {
        EntityPlayerMP player = mockPlayer("Notch");
        SkinRestorerCommand.setServerOverrideForTest(mockServer(player));

        command.processCommand(player, new String[]{"metrics", "json"});

        verify(player).sendChatToPlayer(org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void metricsPlayersDispatchReportsTopPlayers() throws Exception {
        SkinMetrics.INSTANCE.recordRefreshCompleted(SkinRestorer.uuidOf("Notch"), 0, 1, 0, 0);
        EntityPlayerMP player = mockPlayer("Notch");
        SkinRestorerCommand.setServerOverrideForTest(mockServer(player));

        command.processCommand(player, new String[]{"metrics", "players"});

        verify(player).sendChatToPlayer(org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void metricsResetDispatchClearsCounters() throws Exception {
        SkinMetrics.INSTANCE.recordRefreshCompleted(SkinRestorer.uuidOf("Notch"), 0, 1, 0, 0);
        assertEquals(1, SkinMetrics.INSTANCE.snapshot().refreshesCompleted());

        EntityPlayerMP player = mockPlayer("Notch");
        SkinRestorerCommand.setServerOverrideForTest(mockServer(player));

        command.processCommand(player, new String[]{"metrics", "reset"});

        assertEquals(0, SkinMetrics.INSTANCE.snapshot().refreshesCompleted());
    }

    @Test
    public void metricsCleanupDispatchReportsRemoved() throws Exception {
        EntityPlayerMP player = mockPlayer("Notch");
        SkinRestorerCommand.setServerOverrideForTest(mockServer(player));

        command.processCommand(player, new String[]{"metrics", "cleanup"});

        verify(player).sendChatToPlayer(org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void metricsResetRequiresResetPermission() throws Exception {
        SkinMetrics.INSTANCE.recordRefreshCompleted(SkinRestorer.uuidOf("Notch"), 0, 1, 0, 0);
        DenyNodeService.deny("everlastingskins.command.metrics.reset");
        PermissionServiceManager.registerService(new DenyNodeService());

        EntityPlayerMP player = mockPlayer("Notch");
        SkinRestorerCommand.setServerOverrideForTest(mockServer(player));

        command.processCommand(player, new String[]{"metrics", "reset"});

        // Denied: counters survive the attempted reset.
        assertEquals(1, SkinMetrics.INSTANCE.snapshot().refreshesCompleted());
    }

    @Test
    public void tabCompleteOffersSubcommands() {
        ICommandSender sender = mock(ICommandSender.class);
        List completions = command.addTabCompletionOptions(sender, new String[]{""});
        assertTrue(completions.contains("set"));
        assertTrue(completions.contains("clear"));
        assertTrue(completions.contains("source"));
        assertTrue(completions.contains("metrics"));
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
        assertTrue(completions.contains("random")); // the set sub-mode
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
    public void tabCompleteMetricsSubcommands() {
        ICommandSender sender = mock(ICommandSender.class);
        List completions = command.addTabCompletionOptions(sender, new String[]{"metrics", ""});
        assertTrue(completions.contains("json"));
        assertTrue(completions.contains("players"));
        assertTrue(completions.contains("cleanup"));
        assertTrue(completions.contains("reset"));
    }

    @Test
    public void tabCompleteMetricsHideResetWithoutPermission() {
        DenyNodeService.deny("everlastingskins.command.metrics.reset");
        PermissionServiceManager.registerService(new DenyNodeService());
        EntityPlayerMP player = mockPlayer("Notch");

        List completions = command.addTabCompletionOptions(player, new String[]{"metrics", ""});

        assertTrue(completions.contains("json"));
        assertFalse(completions.contains("cleanup"));
        assertFalse(completions.contains("reset"));
    }

    @Test
    public void tabCompleteFirstArgHidesMetricsWithoutPermission() {
        DenyNodeService.deny("everlastingskins.command.metrics");
        PermissionServiceManager.registerService(new DenyNodeService());
        EntityPlayerMP player = mockPlayer("Notch");

        List completions = command.addTabCompletionOptions(player, new String[]{""});

        assertTrue(completions.contains("set"));
        assertFalse(completions.contains("metrics"));
    }

    @Test
    public void tabCompleteSetRandomOffersCapeFlags() {
        ICommandSender sender = mock(ICommandSender.class);
        List completions = command.addTabCompletionOptions(sender, new String[]{"set", "random", ""});
        assertTrue(completions.contains("true"));
        assertTrue(completions.contains("false"));
    }

    @Test
    public void tabCompleteSetRandomOffersVariants() {
        ICommandSender sender = mock(ICommandSender.class);
        List completions = command.addTabCompletionOptions(sender, new String[]{"set", "random", "false", ""});
        assertTrue(completions.contains("classic"));
        assertTrue(completions.contains("slim"));
    }

    @Test
    public void tabCompleteSetRandomOffersTargetsAfterFlags() throws Exception {
        EntityPlayerMP self = mockPlayer("Notch");
        EntityPlayerMP other = mockPlayer("jeb_");
        SkinRestorerCommand.setServerOverrideForTest(mockServer(self, other));

        ICommandSender sender = mock(ICommandSender.class);
        List completions = command.addTabCompletionOptions(sender, new String[]{"set", "random", "false", "all", ""});
        assertTrue(completions.contains("jeb_"));
    }

    @Test
    public void tabCompleteThirdArgOffersTargets() throws Exception {
        EntityPlayerMP self = mockPlayer("Notch");
        EntityPlayerMP other = mockPlayer("jeb_");
        SkinRestorerCommand.setServerOverrideForTest(mockServer(self, other));

        ICommandSender sender = mock(ICommandSender.class);
        List completions = command.addTabCompletionOptions(sender, new String[]{"set", "Notch", ""});
        assertTrue(completions.contains("jeb_"));
        assertTrue(completions.contains("Notch"));
    }

    @Test
    public void tabCompleteUnknownActionReturnsNull() {
        ICommandSender sender = mock(ICommandSender.class);
        assertEquals(null, command.addTabCompletionOptions(sender, new String[]{"bogus", "x", "y"}));
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

    /**
     * Deterministic deny-one-node backend (memory #1115). The denied node is
     * a static so the sticky registration (priority 1000) can be re-targeted
     * across tests; {@link #deny(String)} with null restores grant-all.
     */
    private static final class DenyNodeService implements IPermissionService {
        private static volatile String deniedNode;

        static void deny(String node) {
            deniedNode = node;
        }

        @Override
        public boolean hasPermission(UUID uuid, int opLevel, String permissionNode) {
            return deniedNode == null || !permissionNode.equals(deniedNode);
        }

        @Override
        public String getActiveBackendName() {
            return "DenyNode";
        }

        @Override
        public int getPriority() {
            return 1000;
        }
    }

    /** Deterministic random-pick fake (memory #1115). */
    private static final class FixedRandomPickSource implements SkinRestorerCommand.RandomPickSource {
        private final String username;

        FixedRandomPickSource(String username) {
            this.username = username;
        }

        @Override
        public String pick(boolean cape, SkinVariant variant) throws IOException {
            return username;
        }
    }

    /** Records picker arguments for the parse tests. */
    private static final class RecordingRandomPickSource implements SkinRestorerCommand.RandomPickSource {
        private final String username;
        private boolean lastCape;
        private SkinVariant lastVariant;

        RecordingRandomPickSource(String username) {
            this.username = username;
        }

        @Override
        public String pick(boolean cape, SkinVariant variant) throws IOException {
            lastCape = cape;
            lastVariant = variant;
            return username;
        }
    }

    /** Picker that fails like a network error. */
    private static final class FailingRandomPickSource implements SkinRestorerCommand.RandomPickSource {
        @Override
        public String pick(boolean cape, SkinVariant variant) throws IOException {
            throw new IOException("offline");
        }
    }
}
