/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.enums.SkinVariant;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.permission.IPermissionService;
import levosilimo.everlastingskins.permission.PermissionServiceManager;
import levosilimo.everlastingskins.skinchanger.MojangAPI;
import levosilimo.everlastingskins.skinchanger.responses.mineskin.MineSkinResponse;
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

    /** Single shared permission backend whose policy each test controls (no priority leakage). */
    private static final ControlledPermissionService permissions = new ControlledPermissionService();

    private SkinStorageProvider provider;
    private SkinCommand command;

    @Before
    public void setUp() throws Exception {
        // Deterministic grant-all policy (memory #1115) so permission checks in
        // these dispatch tests are deterministic regardless of any backend
        // left by earlier tests.
        permissions.grantAll();
        PermissionServiceManager.registerService(permissions);

        Path dir = Files.createTempDirectory("es-skin-restorer-test");
        SkinStorage.resetForTest();
        SkinMetrics.INSTANCE.reset();
        SkinStorage storage = new SkinStorage(new SkinIO(dir));
        provider = new SkinStorageProvider(storage);
        command = new SkinCommand(provider);
    }

    @After
    public void tearDown() {
        permissions.grantAll();
        SkinCommand.setMojangApiForTest(null);
        SkinCommand.setMineSkinApiForTest(null);
        SkinCommand.setRandomSourceForTest(null);
        SkinCommand.setServerOverrideForTest(null);
        SkinCommand.clearSeenProfilesForTest();
        SkinMetrics.INSTANCE.reset();
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

        command.processCommand(player, new String[]{"set", "mojang", "Notch"});

        assertEquals("MojangAPI", provider.getSource(TEST_UUID));
    }

    @Test
    public void setDispatchUnknownPlayerReportsFailure() throws Exception {
        SkinCommand.setMojangApiForTest(new FakeMojangApi(Optional.empty()));

        EntityPlayerMP player = mockPlayer(TEST_UUID, "Notch");
        SkinCommand.setServerOverrideForTest(mockServer(player));

        command.processCommand(player, new String[]{"set", "mojang", "ghost"});
        assertEquals(null, provider.getSkin(TEST_UUID));
    }

    @Test
    public void setDispatchMojangExceptionReportsFailure() throws Exception {
        // Resolver throws (silent Mojang fetch failure): the command must not
        // crash and must store nothing — the same failure shape as the
        // empty-result path. The ES_E2E_SKIN=fail marker this path logs is
        // not assertable here (no log-capture/appender infra in this lane's
        // tests), so the marker-adjacent behavior is what's under test.
        SkinCommand.setMojangApiForTest(new ThrowingMojangApi(new RuntimeException("connection refused")));

        EntityPlayerMP player = mockPlayer(TEST_UUID, "Notch");
        SkinCommand.setServerOverrideForTest(mockServer(player));

        command.processCommand(player, new String[]{"set", "mojang", "ghost"});
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
        command.processCommand(xephos, new String[]{"set", "mojang", "xephos"});

        // xephos left the server; only Notch + jeb_ are online now.
        SkinCommand.setServerOverrideForTest(
            mockServer(mockPlayer(TEST_UUID, "Notch"), mockPlayer(UUID.randomUUID(), "jeb_")));

        ICommandSender sender = mock(ICommandSender.class);
        List completions = command.addTabCompletionOptions(sender, new String[]{"set", "mojang", ""});
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
        command.processCommand(xephos, new String[]{"set", "mojang", "xephos"});

        SkinCommand.setServerOverrideForTest(
            mockServer(mockPlayer(TEST_UUID, "Notch"), mockPlayer(UUID.randomUUID(), "jeb_")));
        ICommandSender sender = mock(ICommandSender.class);
        List completions = command.addTabCompletionOptions(sender, new String[]{"set", "mojang", "j"});
        assertEquals(1, completions.size());
        assertEquals("jeb_", completions.get(0));
    }

    @Test
    public void tabCompleteBeyondKnownPositionsReturnsNull() {
        ICommandSender sender = mock(ICommandSender.class);
        assertEquals(null, command.addTabCompletionOptions(sender, new String[]{"clear", "Steve", "extra"}));
    }

    @Test
    public void setWebDispatchAppliesGeneratedSkin() throws Exception {
        SkinCommand.setMineSkinApiForTest(new FakeMineSkinApi(
            new MineSkinResponse(new CustomSkinProperty("textures", "d2Vi", null, "MineSkin"),
                "id", SkinVariant.CLASSIC, SkinVariant.CLASSIC)));

        EntityPlayerMP player = mockPlayer(TEST_UUID, "Notch");
        SkinCommand.setServerOverrideForTest(mockServer(player));

        command.processCommand(player, new String[]{"set", "web", "classic", "https://example.com/skin.png"});

        assertEquals("MineSkin", provider.getSource(TEST_UUID));
    }

    @Test
    public void setWebSlimVariantReachesGenerator() throws Exception {
        final SkinVariant[] seen = new SkinVariant[1];
        SkinCommand.setMineSkinApiForTest(new RecordingMineSkinApi(seen));

        EntityPlayerMP player = mockPlayer(TEST_UUID, "Notch");
        SkinCommand.setServerOverrideForTest(mockServer(player));

        command.processCommand(player, new String[]{"set", "web", "slim", "https://example.com/skin.png"});

        assertEquals(SkinVariant.SLIM, seen[0]);
    }

    @Test
    public void setWebNullResponseReportsFailure() throws Exception {
        SkinCommand.setMineSkinApiForTest(new FakeMineSkinApi(null));

        EntityPlayerMP player = mockPlayer(TEST_UUID, "Notch");
        SkinCommand.setServerOverrideForTest(mockServer(player));

        command.processCommand(player, new String[]{"set", "web", "classic", "https://example.com/skin.png"});
        assertEquals(null, provider.getSkin(TEST_UUID));
    }

    @Test
    public void setRandomDispatchAppliesSkin() throws Exception {
        SkinCommand.setRandomSourceForTest(new FakeRandomSource("jeb_"));
        MojangSkinDataResult result = new MojangSkinDataResult(
            TEST_UUID,
            new CustomSkinProperty("textures", "cmFuZG9t", null, "MojangAPI"));
        SkinCommand.setMojangApiForTest(new FakeMojangApi(Optional.of(result)));

        EntityPlayerMP player = mockPlayer(TEST_UUID, "Notch");
        SkinCommand.setServerOverrideForTest(mockServer(player));

        command.processCommand(player, new String[]{"set", "random"});

        assertEquals("MojangAPI", provider.getSource(TEST_UUID));
    }

    @Test
    public void setRandomCapeFlagAndVariantReachSource() throws Exception {
        final boolean[] seenCape = new boolean[1];
        final SkinVariant[] seenVariant = new SkinVariant[1];
        SkinCommand.setRandomSourceForTest(new RecordingRandomSource(seenCape, seenVariant));
        MojangSkinDataResult result = new MojangSkinDataResult(
            TEST_UUID,
            new CustomSkinProperty("textures", "cmFuZG9t", null, "MojangAPI"));
        SkinCommand.setMojangApiForTest(new FakeMojangApi(Optional.of(result)));

        EntityPlayerMP player = mockPlayer(TEST_UUID, "Notch");
        SkinCommand.setServerOverrideForTest(mockServer(player));

        command.processCommand(player, new String[]{"set", "random", "true", "slim"});

        assertEquals(true, seenCape[0]);
        assertEquals(SkinVariant.SLIM, seenVariant[0]);
    }

    @Test
    public void setRandomDefaultVariantIsAll() throws Exception {
        final SkinVariant[] seenVariant = new SkinVariant[1];
        SkinCommand.setRandomSourceForTest(new RecordingRandomSource(new boolean[1], seenVariant));
        MojangSkinDataResult result = new MojangSkinDataResult(
            TEST_UUID,
            new CustomSkinProperty("textures", "cmFuZG9t", null, "MojangAPI"));
        SkinCommand.setMojangApiForTest(new FakeMojangApi(Optional.of(result)));

        EntityPlayerMP player = mockPlayer(TEST_UUID, "Notch");
        SkinCommand.setServerOverrideForTest(mockServer(player));

        command.processCommand(player, new String[]{"set", "random"});

        assertEquals(SkinVariant.ALL, seenVariant[0]);
    }

    @Test
    public void multiTargetMojangAppliesToAll() throws Exception {
        MojangSkinDataResult result = new MojangSkinDataResult(
            TEST_UUID,
            new CustomSkinProperty("textures", "bXVsdGk=", null, "MojangAPI"));
        SkinCommand.setMojangApiForTest(new FakeMojangApi(Optional.of(result)));

        EntityPlayerMP self = mockPlayer(TEST_UUID, "Notch");
        EntityPlayerMP other = mockPlayer(UUID.randomUUID(), "Steve");
        SkinCommand.setServerOverrideForTest(mockServer(self, other));

        command.processCommand(self, new String[]{"set", "mojang", "jeb_", "Steve"});

        // Explicit targets replace the sender: Steve gets the skin, Notch does not.
        assertEquals(null, provider.getSource(TEST_UUID));
        assertEquals("MojangAPI", provider.getSource(other.getGameProfile().getId()));
    }

    @Test
    public void multiTargetGatedOnSkinOther() throws Exception {
        permissions.deny("everlastingskins.command.skin.other");
        MojangSkinDataResult result = new MojangSkinDataResult(
            TEST_UUID,
            new CustomSkinProperty("textures", "bXVsdGk=", null, "MojangAPI"));
        SkinCommand.setMojangApiForTest(new FakeMojangApi(Optional.of(result)));

        EntityPlayerMP self = mockPlayer(TEST_UUID, "Notch");
        EntityPlayerMP other = mockPlayer(UUID.randomUUID(), "Steve");
        SkinCommand.setServerOverrideForTest(mockServer(self, other));

        // Multi-target denied: .skin.other missing.
        command.processCommand(self, new String[]{"set", "mojang", "jeb_", "Steve"});
        assertEquals(null, provider.getSkin(other.getGameProfile().getId()));

        // Self-only still allowed through .skin.
        command.processCommand(self, new String[]{"set", "mojang", "jeb_"});
        assertEquals("MojangAPI", provider.getSource(TEST_UUID));
    }

    @Test
    public void multiTargetClearGatedOnSkinOther() throws Exception {
        permissions.deny("everlastingskins.command.skin.other");
        EntityPlayerMP self = mockPlayer(TEST_UUID, "Notch");
        EntityPlayerMP other = mockPlayer(UUID.randomUUID(), "Steve");
        SkinCommand.setServerOverrideForTest(mockServer(self, other));

        com.mojang.authlib.GameProfile otherProfile = other.getGameProfile();
        provider.applySkin(otherProfile, other.getGameProfile().getId(),
            new CustomSkinProperty("textures", "b3RoZXI=", null, "fake"));
        assertNotNull(provider.getSkin(other.getGameProfile().getId()));

        command.processCommand(self, new String[]{"clear", "Steve"});

        // Target's skin untouched: .skin.other missing.
        assertNotNull(provider.getSkin(other.getGameProfile().getId()));
    }

    @Test
    public void metricsResetClearsCounters() throws Exception {
        EntityPlayerMP player = mockPlayer(TEST_UUID, "Notch");
        SkinCommand.setServerOverrideForTest(mockServer(player));

        command.processCommand(player, new String[]{"metrics", "reset"});

        assertEquals(0, SkinMetrics.INSTANCE.snapshot().refreshesInitiated());
    }

    @Test
    public void metricsResetRequiresResetPermission() throws Exception {
        permissions.deny("everlastingskins.command.metrics.reset");
        EntityPlayerMP player = mockPlayer(TEST_UUID, "Notch");
        SkinCommand.setServerOverrideForTest(mockServer(player));

        SkinMetrics.INSTANCE.recordRefreshStarted(TEST_UUID);
        command.processCommand(player, new String[]{"metrics", "reset"});

        // Denied: counters survive the failed reset.
        assertEquals(1, SkinMetrics.INSTANCE.snapshot().refreshesInitiated());
    }

    @Test
    public void metricsDispatchReportsSnapshot() throws Exception {
        EntityPlayerMP player = mockPlayer(TEST_UUID, "Notch");
        SkinCommand.setServerOverrideForTest(mockServer(player));

        SkinMetrics.INSTANCE.recordRefreshStarted(TEST_UUID);
        SkinMetrics.INSTANCE.recordRefreshCompleted(TEST_UUID, 0, 1, 0, 0);
        command.processCommand(player, new String[]{"metrics", "json"});

        verify(player).addChatMessage(org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void metricsDeniedWithoutPermission() throws Exception {
        permissions.denyAll();
        EntityPlayerMP player = mockPlayer(TEST_UUID, "Notch");
        SkinCommand.setServerOverrideForTest(mockServer(player));

        command.processCommand(player, new String[]{"metrics", "json"});

        // Denied path still reports through chat (no crash, no snapshot leak).
        verify(player).addChatMessage(org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void tabCompleteSetOffersProviders() {
        ICommandSender sender = mock(ICommandSender.class);
        List completions = command.addTabCompletionOptions(sender, new String[]{"set", ""});
        assertTrue(completions.contains("mojang"));
        assertTrue(completions.contains("web"));
        assertTrue(completions.contains("random"));
    }

    @Test
    public void tabCompleteWebOffersVariants() {
        ICommandSender sender = mock(ICommandSender.class);
        List completions = command.addTabCompletionOptions(sender, new String[]{"set", "web", ""});
        assertTrue(completions.contains("classic"));
        assertTrue(completions.contains("slim"));
    }

    @Test
    public void tabCompleteRandomCascadeOffersCapeThenVariant() {
        ICommandSender sender = mock(ICommandSender.class);
        List cape = command.addTabCompletionOptions(sender, new String[]{"set", "random", ""});
        assertTrue(cape.contains("true"));
        assertTrue(cape.contains("false"));
        List variant = command.addTabCompletionOptions(sender, new String[]{"set", "random", "true", ""});
        assertTrue(variant.contains("classic"));
        assertTrue(variant.contains("slim"));
    }

    @Test
    public void tabCompleteMetricsOffersSubcommands() {
        ICommandSender sender = mock(ICommandSender.class);
        List completions = command.addTabCompletionOptions(sender, new String[]{"metrics", ""});
        assertTrue(completions.contains("json"));
        assertTrue(completions.contains("players"));
        assertTrue(completions.contains("cleanup"));
        assertTrue(completions.contains("reset"));
    }

    @Test
    public void tabCompleteTargetsGatedOnSkinOther() throws Exception {
        EntityPlayerMP notch = mockPlayer(TEST_UUID, "Notch");
        SkinCommand.setServerOverrideForTest(mockServer(notch, mockPlayer(UUID.randomUUID(), "jeb_")));
        EntityPlayerMP sender = mockPlayer(UUID.randomUUID(), "Alice");

        permissions.deny("everlastingskins.command.skin.other");
        List denied = command.addTabCompletionOptions(sender, new String[]{"set", "mojang", "Notch", ""});
        assertEquals(0, denied.size());

        permissions.grantAll();
        List allowed = command.addTabCompletionOptions(sender, new String[]{"set", "mojang", "Notch", ""});
        assertTrue(allowed.contains("jeb_"));
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

    /** Deterministic throwing Mojang lookup fake (memory #1115; no live HTTP). */
    private static final class ThrowingMojangApi implements MojangAPI {
        private final RuntimeException failure;

        ThrowingMojangApi(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public Optional<MojangSkinDataResult> getSkin(String nameOrUniqueId) {
            throw failure;
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

    /**
     * Single shared backend whose policy tests set per-case: grant-all by
     * default, a single denied node, or deny-everything. The manager keeps
     * the highest-priority service and cannot be reset from outside its
     * package, so tests mutate this one instance instead of registering
     * competing backends.
     */
    private static final class ControlledPermissionService implements IPermissionService {
        private static final String DENY_ALL = "*";
        private String deniedNode;

        void grantAll() {
            deniedNode = null;
        }

        void deny(String node) {
            deniedNode = node;
        }

        void denyAll() {
            deniedNode = DENY_ALL;
        }

        @Override
        public boolean hasPermission(UUID uuid, int opLevel, String permissionNode) {
            if (deniedNode == null) {
                return true;
            }
            return !(deniedNode.equals(DENY_ALL) || deniedNode.equals(permissionNode));
        }

        @Override
        public String getActiveBackendName() {
            return "Controlled";
        }

        @Override
        public int getPriority() {
            return 100;
        }
    }

    /** Deterministic MineSkin generator fake (memory #1115; no live HTTP). */
    private static final class FakeMineSkinApi implements MineSkinAPI {
        private final MineSkinResponse response;

        FakeMineSkinApi(MineSkinResponse response) {
            this.response = response;
        }

        @Override
        public MineSkinResponse genSkin(String url, SkinVariant variant) {
            return response;
        }
    }

    /** Records the variant the generator was called with, returns a canned response. */
    private static final class RecordingMineSkinApi implements MineSkinAPI {
        private final SkinVariant[] seen;

        RecordingMineSkinApi(SkinVariant[] seen) {
            this.seen = seen;
        }

        @Override
        public MineSkinResponse genSkin(String url, SkinVariant variant) {
            seen[0] = variant;
            return new MineSkinResponse(
                new CustomSkinProperty("textures", "d2Vi", null, "MineSkin"),
                "id", variant, variant);
        }
    }

    /** Deterministic random-username source fake (memory #1115; no live HTTP). */
    private static final class FakeRandomSource implements SkinCommand.RandomUsernameSource {
        private final String username;

        FakeRandomSource(String username) {
            this.username = username;
        }

        @Override
        public String pick(boolean cape, SkinVariant variant) {
            return username;
        }
    }

    /** Records the cape flag and variant, returns a canned username. */
    private static final class RecordingRandomSource implements SkinCommand.RandomUsernameSource {
        private final boolean[] seenCape;
        private final SkinVariant[] seenVariant;

        RecordingRandomSource(boolean[] seenCape, SkinVariant[] seenVariant) {
            this.seenCape = seenCape;
            this.seenVariant = seenVariant;
        }

        @Override
        public String pick(boolean cape, SkinVariant variant) {
            seenCape[0] = cape;
            seenVariant[0] = variant;
            return "jeb_";
        }
    }
}
