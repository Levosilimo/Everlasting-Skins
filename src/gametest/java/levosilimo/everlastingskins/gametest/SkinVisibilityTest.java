package levosilimo.everlastingskins.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.netty.channel.embedded.EmbeddedChannel;
import levosilimo.everlastingskins.EverlastingSkins;
import levosilimo.everlastingskins.skinchanger.MojangAPI;
import levosilimo.everlastingskins.skinchanger.ProfileLookup;
import levosilimo.everlastingskins.skinchanger.SkinCommand;
import levosilimo.everlastingskins.skinchanger.SkinIO;
import levosilimo.everlastingskins.skinchanger.SkinRefreshHandler;
import levosilimo.everlastingskins.skinchanger.SkinRestorer;
import levosilimo.everlastingskins.skinchanger.SkinStorage;
import levosilimo.everlastingskins.skinchanger.responses.mojang.MojangSkinDataResult;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.gametest.GameTestHolder;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Integration tests for the EverlastingSkins command pipeline: /skin dispatch
 * through the real Brigadier dispatcher, permission gating, storage mutation,
 * async fetch completion, profile mutation and the ClientboundPlayerInfoUpdate
 * broadcast tail. Players are mock ServerPlayers backed by EmbeddedChannels
 * (mirroring Forge's PacketTest pattern); the Mojang provider is a test fake
 * injected into SkinCommand via reflection so no network is required.
 *
 * Each test declares its own game-test {@code batch}: the game test server
 * runs all tests of a batch concurrently on the server thread, and these
 * tests share mod static state (SkinCommand provider, SkinStorage map, player
 * list), so one batch per test keeps execution sequential and deterministic.
 */
@GameTestHolder(value = "everlastingskins", namespace = "everlastingskins")
public class SkinVisibilityTest {

    private static final String TEST_TEXTURE_VALUE = "eyJ0aW1lc3RhbXAiOjE3MTk4NDY0MDAsInByb2ZpbGVJZCI6IjA2OWE3OWY0NDRlOTQ3MjZhNWJlZmNhOTBlMzhhYWY1IiwicHJvZmlsZU5hbWUiOiJOb3RjaCIsInRleHR1cmVzIjp7IlNLSU4iOnsidXJsIjoiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9iYmIxIn19fQ==";
    private static final String TEST_SIGNATURE = "ZmFrZVNpZ25hdHVyZUZvclRlc3Rpbmc9PQ==";
    private static final UUID TEST_UUID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @GameTest(template = "everlastingskins:empty", timeoutTicks = 200, batch = "everlastingskins:baseline")
    public void skinSetMojang_broadcastsTextureToAllClients(GameTestHelper helper) {
        // The vanilla GameTestServer never calls DedicatedServer.initServer, so
        // Forge's ServerStartingEvent does not fire and SkinRestorer never
        // initializes. Post it once so the real init path runs.
        SkinStorage storage = SkinRestorer.getSkinStorage();
        if (storage == null) {
            MinecraftForge.EVENT_BUS.post(new ServerStartingEvent(helper.getLevel().getServer()));
            storage = SkinRestorer.getSkinStorage();
        }
        if (storage == null) {
            helper.fail("SkinRestorer storage is not initialized after ServerStartingEvent");
            return;
        }

        // Players are constructed before joining so their UUIDs are known in
        // time to seed the storage: the login handler then skips the live
        // Mojang lookup for the fake profiles, and SkinRefreshHandler has a
        // skin to apply.
        ServerPlayer playerA = mockPlayer(helper, "TestPlayerA");
        ServerPlayer observer = mockPlayer(helper, "TestPlayerB");
        CustomSkinProperty testSkin = new CustomSkinProperty("textures", TEST_TEXTURE_VALUE, TEST_SIGNATURE, "gametest");
        storage.setSkin(playerA.getUUID(), testSkin);
        storage.setSkin(observer.getUUID(), testSkin);

        try {
            placePlayer(helper, playerA);
            placePlayer(helper, observer);

            SkinRefreshHandler.task(playerA);

            List<Packet<?>> observerPackets = drain(observer);
            ClientboundPlayerInfoUpdatePacket infoUpdate = observerPackets.stream()
                    .filter(ClientboundPlayerInfoUpdatePacket.class::isInstance)
                    .map(ClientboundPlayerInfoUpdatePacket.class::cast)
                    .filter(p -> p.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME))
                    .findFirst()
                    .orElse(null);
            if (infoUpdate == null) {
                helper.fail("ObserverPlayer received no ClientboundPlayerInfoUpdatePacket(UPDATE_DISPLAY_NAME); channel contents: " + observerPackets);
                return;
            }

            boolean hasTexture = infoUpdate.entries().stream()
                    .anyMatch(e -> e.profile() != null
                            && e.profile().getProperties().get("textures").stream()
                                    .anyMatch(property -> TEST_TEXTURE_VALUE.equals(property.value())));
            if (!hasTexture) {
                helper.fail("UPDATE_DISPLAY_NAME packet does not carry the expected textures property; entries: " + infoUpdate.entries());
                return;
            }

            helper.succeed();
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(playerA);
            helper.getLevel().getServer().getPlayerList().remove(observer);
        }
    }

    /**
     * Dispatches /skin set mojang Notch through the real server dispatcher as
     * an OP player and waits for the async provider fetch, storage write and
     * refresh broadcast to complete.
     */
    @GameTest(template = "everlastingskins:empty", timeoutTicks = 200, batch = "everlastingskins:cmd_set")
    public void skinCommand_setMojang_fullFlow(GameTestHelper helper) {
        FakeMojangAPI fake = installFakeMojangAPI(true);
        SkinStorage storage = ensureStorage(helper);
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer playerA = mockPlayer(helper, "TestPlayerA");
        ServerPlayer observer = mockPlayer(helper, "TestPlayerB");
        makeOp(playerA);
        UUID playerId = playerA.getUUID();
        placePlayer(helper, playerA);
        placePlayer(helper, observer);
        drain(observer);

        // From here on the provider succeeds so the command pipeline has
        // something to store and broadcast.
        fake.fail = false;
        int result = dispatch(server, "skin set mojang Notch", playerA.createCommandSourceStack(), helper);
        helper.assertTrue(result == 1, "command should report 1 target, got " + result);

        helper.succeedWhen(() -> {
            CustomSkinProperty stored = storage.getSkin(playerId);
            if (stored == null || !"Notch".equals(stored.getSource())) {
                throw new GameTestAssertException("waiting for /skin set mojang Notch to store source=Notch (got "
                        + (stored == null ? "null" : stored.getSource()) + ")");
            }
            Property textures = findTexturesFor(drain(observer), playerId);
            if (textures == null) {
                throw new GameTestAssertException("waiting for observer to receive UPDATE_DISPLAY_NAME with textures");
            }
            assertTexturePayload(textures, helper);
            removeQuietly(server, playerA);
            removeQuietly(server, observer);
        });
    }

    /**
     * A non-OP player must be rejected before any provider call, storage write
     * or broadcast happens, and must receive failure feedback.
     */
    @GameTest(template = "everlastingskins:empty", timeoutTicks = 200, batch = "everlastingskins:cmd_denied")
    public void skinCommand_permissionDenied(GameTestHelper helper) {
        installFakeMojangAPI(true);
        SkinStorage storage = ensureStorage(helper);
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer playerA = mockPlayer(helper, "TestPlayerA");
        ServerPlayer observer = mockPlayer(helper, "TestPlayerB");
        UUID playerId = playerA.getUUID();
        placePlayer(helper, playerA);
        placePlayer(helper, observer);
        drain(observer);

        int result = dispatch(server, "skin set mojang Notch", playerA.createCommandSourceStack(), helper);
        if (result != 0) {
            helper.fail("non-OP dispatch must be rejected (return 0), got " + result);
            return;
        }

        boolean feedback = drain(playerA).stream()
                .filter(ClientboundSystemChatPacket.class::isInstance)
                .map(ClientboundSystemChatPacket.class::cast)
                .anyMatch(p -> p.content().getString().contains("Permission denied"));
        if (!feedback) {
            helper.fail("non-OP player received no 'Permission denied' failure feedback");
            return;
        }
        if (storage.getSkin(playerId) != null) {
            helper.fail("permission-denied dispatch must not store a skin");
            return;
        }
        if (findTexturesFor(drain(observer), playerId) != null) {
            helper.fail("permission-denied dispatch must not broadcast textures");
            return;
        }
        removeQuietly(server, playerA);
        removeQuietly(server, observer);
        helper.succeed();
    }

    /**
     * /skin clear must remove the stored skin and its JSON file. The provider
     * is forced to fail so no Mojang restore replaces the removed skin. Note:
     * SkinRefreshHandler.task currently NPEs on a null skin (the broadcast
     * tail never runs), so no textures UPDATE_DISPLAY_NAME is expected after
     * clearing — asserted defensively in case that bug is fixed.
     */
    @GameTest(template = "everlastingskins:empty", timeoutTicks = 200, batch = "everlastingskins:cmd_clear")
    public void skinCommand_clear_removesTexture(GameTestHelper helper) {
        FakeMojangAPI fake = installFakeMojangAPI(true);
        SkinStorage storage = ensureStorage(helper);
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer playerA = mockPlayer(helper, "TestPlayerA");
        ServerPlayer observer = mockPlayer(helper, "TestPlayerB");
        makeOp(playerA);
        UUID playerId = playerA.getUUID();
        placePlayer(helper, playerA);
        placePlayer(helper, observer);
        drain(observer);

        // Set a skin through the direct path (the command path is covered by
        // skinCommand_setMojang_fullFlow) and consume its broadcast.
        storage.setSkin(playerId, new CustomSkinProperty("textures", TEST_TEXTURE_VALUE, TEST_SIGNATURE, "Notch"));
        SkinRefreshHandler.task(playerA);
        drain(observer);

        fake.fail = true;
        int result = dispatch(server, "skin clear", playerA.createCommandSourceStack(), helper);
        helper.assertTrue(result == 1, "command should report 1 target, got " + result);

        Path skinFile = server.getFile("EverlastingSkins").resolve(playerId + ".json");
        helper.succeedWhen(() -> {
            if (storage.getSkin(playerId) != null) {
                throw new GameTestAssertException("waiting for /skin clear to remove skin from storage");
            }
            if (Files.exists(skinFile)) {
                throw new GameTestAssertException("waiting for /skin clear to delete " + skinFile);
            }
            if (findTexturesFor(drain(observer), playerId) != null) {
                helper.fail("clear must not broadcast textures; task() crashes on null skins so no UPDATE_DISPLAY_NAME is expected");
            }
            removeQuietly(server, playerA);
            removeQuietly(server, observer);
        });
    }

    /**
     * /skin source must report the source stored with the current skin.
     */
    @GameTest(template = "everlastingskins:empty", timeoutTicks = 200, batch = "everlastingskins:cmd_source")
    public void skinCommand_source_reportsCurrentSource(GameTestHelper helper) {
        installFakeMojangAPI(true);
        SkinStorage storage = ensureStorage(helper);
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer playerA = mockPlayer(helper, "TestPlayerA");
        makeOp(playerA);
        UUID playerId = playerA.getUUID();
        placePlayer(helper, playerA);

        storage.setSkin(playerId, new CustomSkinProperty("textures", TEST_TEXTURE_VALUE, TEST_SIGNATURE, "Notch"));

        int result = dispatch(server, "skin source TestPlayerA", playerA.createCommandSourceStack(), helper);
        if (result != 1) {
            helper.fail("command should report 1 target, got " + result);
            return;
        }
        boolean reported = drain(playerA).stream()
                .filter(ClientboundSystemChatPacket.class::isInstance)
                .map(ClientboundSystemChatPacket.class::cast)
                .anyMatch(p -> p.content().getString().contains("Notch"));
        if (!reported) {
            helper.fail("player received no source feedback mentioning 'Notch'");
            return;
        }
        removeQuietly(server, playerA);
        helper.succeed();
    }

    /**
     * Persistence round trip: PlayerLoggedOutEvent (fired by PlayerList.remove)
     * must write the skin JSON, a fresh SkinStorage must reload it from disk,
     * and a rejoin with the same UUID must re-apply it to the profile.
     */
    @GameTest(template = "everlastingskins:empty", timeoutTicks = 200, batch = "everlastingskins:roundtrip")
    public void skinRefresh_persistence_roundTrip(GameTestHelper helper) {
        installFakeMojangAPI(true);
        SkinStorage storage = ensureStorage(helper);
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer playerA = mockPlayer(helper, TEST_UUID, "TestPlayerA");
        UUID playerId = playerA.getUUID();
        Path skinFile = server.getFile("EverlastingSkins").resolve(playerId + ".json");

        storage.setSkin(playerId, new CustomSkinProperty("textures", TEST_TEXTURE_VALUE, TEST_SIGNATURE, "Notch"));
        placePlayer(helper, playerA);
        SkinRefreshHandler.task(playerA);
        drain(playerA);

        // Logout: Forge patches PlayerList.remove to fire PlayerLoggedOutEvent,
        // which SkinRestorer uses to save the skin to disk.
        server.getPlayerList().remove(playerA);
        if (!Files.exists(skinFile)) {
            helper.fail("skin file not written on logout: " + skinFile);
            return;
        }

        // A brand-new storage instance can only read from disk, proving the
        // JSON round trip rather than the in-memory cache.
        SkinStorage freshStorage = new SkinStorage(new SkinIO(server.getFile("EverlastingSkins")));
        CustomSkinProperty reloaded = freshStorage.getSkin(playerId);
        if (reloaded == null || !TEST_TEXTURE_VALUE.equals(reloaded.getOriginalProperty().value())) {
            helper.fail("reloaded skin does not match the saved one: " + (reloaded == null ? "null" : reloaded.getOriginalProperty()));
            return;
        }

        // Rejoin with the same UUID: PlayerLoggedInEvent re-applies the skin
        // to the GameProfile so subsequent packets carry the textures.
        ServerPlayer rejoined = mockPlayer(helper, TEST_UUID, "TestPlayerA");
        placePlayer(helper, rejoined);
        boolean applied = rejoined.getGameProfile().getProperties().get("textures").stream()
                .anyMatch(p -> TEST_TEXTURE_VALUE.equals(p.value()));
        if (!applied) {
            helper.fail("login did not re-apply the saved skin to the profile; textures=" + rejoined.getGameProfile().getProperties().get("textures"));
            return;
        }
        removeQuietly(server, rejoined);
        helper.succeed();
    }

    /**
     * One refresh must produce exactly one UPDATE_DISPLAY_NAME textures
     * broadcast on an observer channel — not zero, not two.
     */
    @GameTest(template = "everlastingskins:empty", timeoutTicks = 200, batch = "everlastingskins:count")
    public void skinRefresh_broadcastExactCount(GameTestHelper helper) {
        installFakeMojangAPI(true);
        SkinStorage storage = ensureStorage(helper);
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer playerA = mockPlayer(helper, "TestPlayerA");
        ServerPlayer observer = mockPlayer(helper, "TestPlayerB");
        try {
            placePlayer(helper, playerA);
            placePlayer(helper, observer);
            drain(observer);

            storage.setSkin(playerA.getUUID(), new CustomSkinProperty("textures", TEST_TEXTURE_VALUE, TEST_SIGNATURE, "Notch"));
            SkinRefreshHandler.task(playerA);

            long count = countDisplayNameUpdatesWithTextures(drain(observer), playerA.getUUID());
            if (count != 1) {
                helper.fail("expected exactly 1 UPDATE_DISPLAY_NAME textures packet on the observer channel, got " + count);
                return;
            }
            helper.succeed();
        } finally {
            removeQuietly(server, playerA);
            removeQuietly(server, observer);
        }
    }

    /**
     * Negative control: a player with no stored skin must not get textures on
     * the profile at login and must not trigger a textures broadcast.
     */
    @GameTest(template = "everlastingskins:empty", timeoutTicks = 200, batch = "everlastingskins:negative")
    public void skinRefresh_negativeControl(GameTestHelper helper) {
        installFakeMojangAPI(true);
        ensureStorage(helper);
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer playerA = mockPlayer(helper, "TestPlayerA");
        ServerPlayer observer = mockPlayer(helper, "TestPlayerB");
        try {
            placePlayer(helper, playerA);
            placePlayer(helper, observer);
            drain(observer);

            if (!playerA.getGameProfile().getProperties().get("textures").isEmpty()) {
                helper.fail("fresh player must not have textures without a stored skin; got "
                        + playerA.getGameProfile().getProperties().get("textures"));
                return;
            }
            if (findTexturesFor(drain(observer), playerA.getUUID()) != null) {
                helper.fail("observer must not receive a textures UPDATE_DISPLAY_NAME for a skin-less player");
                return;
            }
            helper.succeed();
        } finally {
            removeQuietly(server, playerA);
            removeQuietly(server, observer);
        }
    }

    /**
     * The broadcast must carry the exact configured signature and a payload
     * whose base64-decoded JSON contains the expected texture URL and
     * profileName.
     */
    @GameTest(template = "everlastingskins:empty", timeoutTicks = 200, batch = "everlastingskins:signature")
    public void skinRefresh_signaturePropagation(GameTestHelper helper) {
        installFakeMojangAPI(true);
        SkinStorage storage = ensureStorage(helper);
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer playerA = mockPlayer(helper, "TestPlayerA");
        ServerPlayer observer = mockPlayer(helper, "TestPlayerB");
        try {
            placePlayer(helper, playerA);
            placePlayer(helper, observer);
            drain(observer);

            storage.setSkin(playerA.getUUID(), new CustomSkinProperty("textures", TEST_TEXTURE_VALUE, TEST_SIGNATURE, "Notch"));
            SkinRefreshHandler.task(playerA);

            Property textures = findTexturesFor(drain(observer), playerA.getUUID());
            if (textures == null) {
                helper.fail("observer received no textures UPDATE_DISPLAY_NAME for playerA");
                return;
            }
            if (!TEST_SIGNATURE.equals(textures.signature())) {
                helper.fail("signature mismatch: expected " + TEST_SIGNATURE + " got " + textures.signature());
                return;
            }
            assertTexturePayload(textures, helper);
            helper.succeed();
        } finally {
            removeQuietly(server, playerA);
            removeQuietly(server, observer);
        }
    }

    /**
     * One refresh must reach every other connected client.
     */
    @GameTest(template = "everlastingskins:empty", timeoutTicks = 200, batch = "everlastingskins:multiobserver")
    public void skinRefresh_propagatesToMultipleObservers(GameTestHelper helper) {
        installFakeMojangAPI(true);
        SkinStorage storage = ensureStorage(helper);
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer playerA = mockPlayer(helper, "TestPlayerA");
        ServerPlayer observer1 = mockPlayer(helper, "TestPlayerB");
        ServerPlayer observer2 = mockPlayer(helper, "TestPlayerC");
        try {
            placePlayer(helper, playerA);
            placePlayer(helper, observer1);
            placePlayer(helper, observer2);
            drain(observer1);
            drain(observer2);

            storage.setSkin(playerA.getUUID(), new CustomSkinProperty("textures", TEST_TEXTURE_VALUE, TEST_SIGNATURE, "Notch"));
            SkinRefreshHandler.task(playerA);

            if (findTexturesFor(drain(observer1), playerA.getUUID()) == null) {
                helper.fail("observer1 received no textures UPDATE_DISPLAY_NAME for playerA");
                return;
            }
            if (findTexturesFor(drain(observer2), playerA.getUUID()) == null) {
                helper.fail("observer2 received no textures UPDATE_DISPLAY_NAME for playerA");
                return;
            }
            helper.succeed();
        } finally {
            removeQuietly(server, playerA);
            removeQuietly(server, observer1);
            removeQuietly(server, observer2);
        }
    }

    /**
     * Full dispatcher path including the requires() gate: an OP player must be
     * able to run /skin set mojang Notch <target> with an explicit entity
     * argument, exercising EntityArgument parsing and canTargetOthers.
     */
    @GameTest(template = "everlastingskins:empty", timeoutTicks = 200, batch = "everlastingskins:successpath")
    public void skinRefresh_runCommandSuccessPath(GameTestHelper helper) {
        FakeMojangAPI fake = installFakeMojangAPI(true);
        SkinStorage storage = ensureStorage(helper);
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer playerA = mockPlayer(helper, "TestPlayerA");
        ServerPlayer observer = mockPlayer(helper, "TestPlayerB");
        makeOp(playerA);
        UUID playerId = playerA.getUUID();
        placePlayer(helper, playerA);
        placePlayer(helper, observer);
        drain(observer);

        fake.fail = false;
        int result = dispatch(server, "skin set mojang Notch TestPlayerA", playerA.createCommandSourceStack(), helper);
        helper.assertTrue(result == 1, "command should report 1 target, got " + result);

        helper.succeedWhen(() -> {
            CustomSkinProperty stored = storage.getSkin(playerId);
            if (stored == null || !"Notch".equals(stored.getSource())) {
                throw new GameTestAssertException("waiting for /skin set mojang Notch TestPlayerA to store source=Notch (got "
                        + (stored == null ? "null" : stored.getSource()) + ")");
            }
            Property textures = findTexturesFor(drain(observer), playerId);
            if (textures == null) {
                throw new GameTestAssertException("waiting for observer to receive UPDATE_DISPLAY_NAME with textures");
            }
            removeQuietly(server, playerA);
            removeQuietly(server, observer);
        });
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Test-only Mojang provider: returns the canned test skin for any lookup
     * unless {@link #fail} is set, in which case every lookup is empty. This
     * keeps login and command fetches deterministic and offline.
     */
    private static final class FakeMojangAPI implements MojangAPI {
        boolean fail;

        @Override
        public Optional<MojangSkinDataResult> getSkin(String nameOrUniqueId) {
            if (fail) return Optional.empty();
            return Optional.of(new MojangSkinDataResult(
                    UUID.nameUUIDFromBytes(nameOrUniqueId.getBytes(StandardCharsets.UTF_8)),
                    new CustomSkinProperty(TEST_TEXTURE_VALUE, TEST_SIGNATURE, nameOrUniqueId)));
        }

        @Override
        public Optional<UUID> getUUID(String playerName) {
            if (fail) return Optional.empty();
            return Optional.of(UUID.nameUUIDFromBytes(playerName.getBytes(StandardCharsets.UTF_8)));
        }

        @Override
        public Optional<CustomSkinProperty> getProfile(ProfileLookup lookup) {
            if (fail) return Optional.empty();
            return Optional.of(new CustomSkinProperty(TEST_TEXTURE_VALUE, TEST_SIGNATURE, lookup.username()));
        }
    }

    /**
     * Injects the fake into SkinCommand's private static field via reflection.
     * Test-only; keeps production visibility untouched.
     */
    private static FakeMojangAPI installFakeMojangAPI(boolean fail) {
        FakeMojangAPI fake = new FakeMojangAPI();
        fake.fail = fail;
        try {
            Field field = SkinCommand.class.getDeclaredField("mojangAPIInstance");
            field.setAccessible(true);
            field.set(null, fake);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not install fake MojangAPI", e);
        }
        return fake;
    }

    private static SkinStorage ensureStorage(GameTestHelper helper) {
        SkinStorage storage = SkinRestorer.getSkinStorage();
        if (storage == null) {
            MinecraftForge.EVENT_BUS.post(new ServerStartingEvent(helper.getLevel().getServer()));
            storage = SkinRestorer.getSkinStorage();
        }
        if (storage == null) {
            helper.fail("SkinRestorer storage is not initialized after ServerStartingEvent");
            throw new GameTestAssertException("storage not initialized");
        }
        return storage;
    }

    /**
     * Puts the player into the server's ops list with level 4 so
     * player.hasPermissions(2) is true — the gate the /skin command actually
     * checks (ServerPlayer.getPermissionLevel -> getProfilePermissions).
     */
    private static void makeOp(ServerPlayer player) {
        player.getServer().getPlayerList().getOps().add(new ServerOpListEntry(player.getGameProfile(), 4, false));
    }

    private static int dispatch(MinecraftServer server, String command, CommandSourceStack source, GameTestHelper helper) {
        try {
            return server.getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException e) {
            helper.fail("command dispatch failed: " + command + " -> " + e.getMessage());
            throw new GameTestAssertException("dispatch failed: " + e.getMessage());
        }
    }

    private static ServerPlayer mockPlayer(GameTestHelper helper, String name) {
        return mockPlayer(helper, UUID.randomUUID(), name);
    }

    private static ServerPlayer mockPlayer(GameTestHelper helper, UUID uuid, String name) {
        return new ServerPlayer(
                helper.getLevel().getServer(),
                helper.getLevel(),
                new GameProfile(uuid, name),
                ClientInformation.createDefault());
    }

    /**
     * Joins a mock player with a real ServerGamePacketListenerImpl backed by
     * an EmbeddedChannel and drains the packets sent during login so the test
     * only sees traffic that happens afterwards.
     */
    private static void placePlayer(GameTestHelper helper, ServerPlayer player) {
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        // The Connection expects channelActive to be fired by its netty
        // pipeline; EmbeddedChannel does that during construction.
        new EmbeddedChannel(connection);
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(player.getGameProfile(), false);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        drain(player);
    }

    private static List<Packet<?>> drain(ServerPlayer player) {
        List<Packet<?>> packets = new ArrayList<>();
        EmbeddedChannel channel = (EmbeddedChannel) player.connection.getConnection().channel();
        Object msg;
        while ((msg = channel.readOutbound()) != null) {
            if (msg instanceof Packet<?> p) {
                packets.add(p);
            }
        }
        return packets;
    }

    private static void removeQuietly(MinecraftServer server, ServerPlayer player) {
        try {
            server.getPlayerList().remove(player);
        } catch (RuntimeException e) {
            EverlastingSkins.logger.warn("Gametest cleanup: failed to remove player {}", player.getGameProfile().getName(), e);
        }
    }

    /**
     * Returns the textures Property for the given profile in any
     * UPDATE_DISPLAY_NAME packet, or null if absent.
     */
    private static Property findTexturesFor(List<Packet<?>> packets, UUID profileId) {
        for (Packet<?> packet : packets) {
            if (!(packet instanceof ClientboundPlayerInfoUpdatePacket infoUpdate)) continue;
            if (!infoUpdate.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME)) continue;
            for (ClientboundPlayerInfoUpdatePacket.Entry entry : infoUpdate.entries()) {
                if (!entry.profileId().equals(profileId)) continue;
                if (entry.profile() == null) continue;
                Collection<Property> properties = entry.profile().getProperties().get("textures");
                if (properties == null) continue;
                for (Property property : properties) {
                    if (TEST_TEXTURE_VALUE.equals(property.value())) return property;
                }
            }
        }
        return null;
    }

    private static long countDisplayNameUpdatesWithTextures(List<Packet<?>> packets, UUID profileId) {
        return packets.stream()
                .filter(ClientboundPlayerInfoUpdatePacket.class::isInstance)
                .map(ClientboundPlayerInfoUpdatePacket.class::cast)
                .filter(p -> p.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME))
                .filter(p -> p.entries().stream().anyMatch(e -> e.profileId().equals(profileId)
                        && e.profile() != null
                        && e.profile().getProperties().get("textures").stream()
                                .anyMatch(property -> TEST_TEXTURE_VALUE.equals(property.value()))))
                .count();
    }

    /**
     * Decodes the textures property and asserts the payload carries a
     * non-empty SKIN.url and profileName "Notch". Marks the test failed via
     * the helper (safe to call from succeedWhen lambdas).
     */
    private static void assertTexturePayload(Property textures, GameTestHelper helper) {
        try {
            JsonObject json = JsonParser.parseString(
                    new String(Base64.getDecoder().decode(textures.value()), StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject skin = json.getAsJsonObject("textures").getAsJsonObject("SKIN");
            String url = skin.has("url") ? skin.get("url").getAsString() : null;
            String profileName = json.has("profileName") ? json.get("profileName").getAsString() : null;
            if (url == null || url.isEmpty() || !"Notch".equals(profileName)) {
                helper.fail("decoded textures payload missing non-empty SKIN.url or profileName=Notch: " + json);
            }
        } catch (RuntimeException e) {
            helper.fail("textures payload is not valid base64 JSON: " + e.getMessage());
        }
    }
}
