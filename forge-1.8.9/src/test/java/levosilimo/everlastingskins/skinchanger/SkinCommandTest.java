/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import levosilimo.everlastingskins.enums.SkinVariant;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.permission.PermissionServiceManager;
import levosilimo.everlastingskins.permission.forge.ForgePermissionService;
import levosilimo.everlastingskins.skinchanger.responses.mineskin.MineSkinResponse;
import levosilimo.everlastingskins.skinchanger.responses.mojang.MojangSkinDataResult;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure-JUnit tests for the 1.8.9 {@link SkinCommand} (memory #1115:
 * deterministic fakes only — fake Mojang/MineSkin APIs, a direct executor
 * for the async pipeline, mocked server surface; no live HTTP, no threads).
 */
class SkinCommandTest {

    private static final UUID PLAYER = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");

    /** Backend stub with a mutable op level; highest priority always wins. */
    private static final class FixedOpService extends ForgePermissionService {
        private static int level = 2;

        static void level(int opLevel) {
            level = opLevel;
        }

        @Override
        protected int resolveOpLevel(UUID uuid) {
            return level;
        }

        @Override
        public int getPriority() {
            return 100;
        }
    }

    /** Fake Mojang API recording requested usernames. */
    private static final class FakeMojangAPI implements MojangAPI {
        private final Optional<MojangSkinDataResult> result;

        FakeMojangAPI(@Nullable CustomSkinProperty skin) {
            this.result = skin != null
                ? Optional.of(new MojangSkinDataResult(PLAYER, skin))
                : Optional.empty();
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

    /** Fake MineSkin API recording the requested variant. */
    private static final class FakeMineSkinAPI implements MineSkinAPI {
        private final MineSkinResponse response;
        private SkinVariant capturedVariant;

        FakeMineSkinAPI(@Nullable MineSkinResponse response) {
            this.response = response;
        }

        @Override
        public MineSkinResponse genSkin(String url, @Nullable SkinVariant variant) {
            capturedVariant = variant;
            return response;
        }
    }

    /** Direct executor: fetch + apply run inline, fully deterministic. */
    private static final ExecutorService DIRECT = new AbstractExecutorService() {
        @Override
        public void execute(Runnable command) {
            command.run();
        }

        @Override
        public void shutdown() {
        }

        @Override
        public List<Runnable> shutdownNow() {
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return true;
        }

        @Override
        public boolean isTerminated() {
            return true;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    };

    @TempDir
    Path tempDir;

    private SkinCommand command;
    private EntityPlayerMP player;
    private ICommandSender console;
    private MinecraftServer server;
    private SkinStorage storage;
    private GameProfile profile;

    @BeforeEach
    void setUp() {
        command = new SkinCommand();
        profile = new GameProfile(PLAYER, "TestPlayer");
        player = mock(EntityPlayerMP.class);
        when(player.getPersistentID()).thenReturn(PLAYER);
        when(player.getGameProfile()).thenReturn(profile);
        World world = mock(World.class);
        when(world.getPlayerEntityByName(anyString())).thenReturn(null);
        when(player.getEntityWorld()).thenReturn(world);
        console = mock(ICommandSender.class);
        server = mock(MinecraftServer.class);
        when(server.addScheduledTask(any(Runnable.class))).thenAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        });
        SkinRestorer.setServerForTest(server);
        storage = new SkinStorage(new SkinIO(tempDir));
        SkinRestorer.setStorageForTest(storage);
        SkinAction.setExecutorForTest(DIRECT);
        FixedOpService.level(2);
        PermissionServiceManager.registerService(new FixedOpService());
        SkinMetrics.INSTANCE.reset();
    }

    @AfterEach
    void tearDown() {
        SkinCommand.resetAPIs();
        SkinStorage.resetForTest();
    }

    private static CustomSkinProperty skin(String name, String source) {
        return new CustomSkinProperty(name, "value", "signature", source);
    }

    @Test
    void exposesLegacySkinCommandSurface() {
        assertEquals("skin", command.getCommandName());
        assertTrue(command.getCommandAliases().contains("eskin"));
        assertEquals("/skin <set|clear|source|metrics> ...", command.getCommandUsage(console));
    }

    @Test
    void dispatchesNoArgsAsUsage() throws Exception {
        command.processCommand(console, new String[]{});
        verify(console).addChatMessage(any(IChatComponent.class));
    }

    @Test
    void setMojangAppliesSkinToSelf() throws Exception {
        CustomSkinProperty fetched = skin("Notch", "MojangAPI");
        SkinCommand.setMojangAPI(new FakeMojangAPI(fetched));
        command.processCommand(player, new String[]{"set", "mojang", "Notch"});
        // Stored + applied to the GameProfile textures property.
        assertEquals(fetched, storage.getSkin(PLAYER));
        assertTrue(profile.getProperties().get("textures").contains(fetched.getOriginalProperty()));
        verify(player).addChatMessage(any(IChatComponent.class)); // "Skin applied"
    }

    @Test
    void setMojangDeniedWithoutPermissionNeverFetches() throws Exception {
        FixedOpService.level(0);
        SkinCommand.setMojangAPI(new FakeMojangAPI(skin("Notch", "MojangAPI")));
        command.processCommand(player, new String[]{"set", "mojang", "Notch"});
        assertNull(storage.getSkin(PLAYER));
        verify(server, never()).addScheduledTask(any(Runnable.class));
        verify(player).addChatMessage(any(IChatComponent.class)); // denial message
    }

    @Test
    void setWebAppliesWithRequestedVariant() throws Exception {
        CustomSkinProperty fetched = skin("web", "MineSkin");
        FakeMineSkinAPI mineSkin = new FakeMineSkinAPI(new MineSkinResponse(fetched, "id", null, null));
        SkinCommand.setMineSkinAPI(mineSkin);
        command.processCommand(player, new String[]{"set", "web", "slim", "https://example.com/skin.png"});
        assertEquals(SkinVariant.SLIM, mineSkin.capturedVariant);
        assertEquals(fetched, storage.getSkin(PLAYER));
        assertTrue(profile.getProperties().get("textures").contains(fetched.getOriginalProperty()));
    }

    @Test
    void setWebRequiresUrlNode() throws Exception {
        FixedOpService.level(0);
        SkinCommand.setMineSkinAPI(new FakeMineSkinAPI(null));
        command.processCommand(player, new String[]{"set", "web", "classic", "https://example.com/skin.png"});
        assertNull(storage.getSkin(PLAYER));
        verify(server, never()).addScheduledTask(any(Runnable.class));
    }

    @Test
    void setRandomDeniedNeverFetches() throws Exception {
        // The granted random path hits the live RandomMojangSkin source, so
        // only the permission gate is exercised here (never touches HTTP).
        FixedOpService.level(0);
        SkinCommand.setMojangAPI(new FakeMojangAPI(skin("random", "MojangAPI")));
        command.processCommand(player, new String[]{"set", "random", "true", "slim"});
        assertNull(storage.getSkin(PLAYER));
        verify(server, never()).addScheduledTask(any(Runnable.class));
    }

    @Test
    void clearStripsStoredSkinAndTextures() throws Exception {
        storage.setSkin(PLAYER, skin("Notch", "MojangAPI"));
        profile.getProperties().put("textures", new Property("textures", "value", "signature"));
        SkinCommand.setMojangAPI(new FakeMojangAPI(null));
        command.processCommand(player, new String[]{"clear"});
        assertNull(storage.getSkin(PLAYER));
        assertTrue(profile.getProperties().get("textures").isEmpty());
    }

    @Test
    void clearDeniedWithoutClearNode() throws Exception {
        FixedOpService.level(0);
        storage.setSkin(PLAYER, skin("Notch", "MojangAPI"));
        SkinCommand.setMojangAPI(new FakeMojangAPI(null));
        command.processCommand(player, new String[]{"clear"});
        assertNotNull(storage.getSkin(PLAYER));
        verify(server, never()).addScheduledTask(any(Runnable.class));
    }

    @Test
    void targetingOthersRequiresOtherNode() throws Exception {
        FixedOpService.level(0);
        SkinCommand.setMojangAPI(new FakeMojangAPI(skin("Notch", "MojangAPI")));
        command.processCommand(player, new String[]{"set", "mojang", "Notch", "A", "B"});
        assertNull(storage.getSkin(PLAYER));
        verify(server, never()).addScheduledTask(any(Runnable.class));
    }

    @Test
    void sourceReportsStoredSource() throws Exception {
        storage.setSkin(PLAYER, skin("Notch", "CustomSource"));
        command.processCommand(player, new String[]{"source"});
        // The message must carry the stored source label.
        org.mockito.ArgumentCaptor<IChatComponent> captor =
            org.mockito.ArgumentCaptor.forClass(IChatComponent.class);
        verify(player).addChatMessage(captor.capture());
        assertTrue(captor.getValue().getUnformattedText().contains("CustomSource"));
    }

    @Test
    void sourceReportsOwnNameWhenUnset() throws Exception {
        // mc1.12.2 parity: a player with no stored source is reported by
        // name (the default-skin case).
        command.processCommand(player, new String[]{"source"});
        org.mockito.ArgumentCaptor<IChatComponent> captor =
            org.mockito.ArgumentCaptor.forClass(IChatComponent.class);
        verify(player).addChatMessage(captor.capture());
        assertTrue(captor.getValue().getUnformattedText().contains("TestPlayer"));
    }

    @Test
    void metricsJsonAllowedForConsole() throws Exception {
        command.processCommand(console, new String[]{"metrics", "json"});
        verify(console).addChatMessage(any(IChatComponent.class));
    }

    @Test
    void metricsResetRequiresResetNode() throws Exception {
        FixedOpService.level(0);
        command.processCommand(player, new String[]{"metrics", "reset"});
        verify(player).addChatMessage(any(IChatComponent.class)); // denial message
    }

    @Test
    void tabCompletesSubcommandsByPermission() {
        List<String> completions = command.addTabCompletionOptions(player, new String[]{"se"}, null);
        assertEquals(Collections.singletonList("set"), completions);
        FixedOpService.level(0);
        // With no op level only the unconditional source node completes.
        List<String> denied = command.addTabCompletionOptions(player, new String[]{""}, null);
        assertEquals(Collections.singletonList("source"), denied);
    }

    @Test
    void tabCompletesSetProvidersAndCascade() {
        assertEquals(Collections.singletonList("mojang"),
            command.addTabCompletionOptions(player, new String[]{"set", "m"}, null));
        assertEquals(Collections.singletonList("slim"),
            command.addTabCompletionOptions(player, new String[]{"set", "web", "s"}, null));
        assertEquals(Collections.singletonList("true"),
            command.addTabCompletionOptions(player, new String[]{"set", "random", "t"}, null));
        assertEquals(Collections.singletonList("classic"),
            command.addTabCompletionOptions(player, new String[]{"set", "random", "true", "c"}, null));
        assertEquals(Collections.singletonList("cleanup"),
            command.addTabCompletionOptions(player, new String[]{"metrics", "c"}, null));
    }

    @Test
    void tabCompletesMetricsSubcommands() {
        List<String> completions = command.addTabCompletionOptions(player, new String[]{"metrics", "c"}, null);
        assertEquals(Collections.singletonList("cleanup"), completions);
    }
}
