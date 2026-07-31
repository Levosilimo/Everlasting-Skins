package levosilimo.everlastingskins.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import levosilimo.everlastingskins.skinchanger.SkinRefreshHandler;
import levosilimo.everlastingskins.skinchanger.SkinRestorer;
import levosilimo.everlastingskins.skinchanger.SkinStorage;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.gametest.GameTestHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Verifies that applying a skin to one player broadcasts a
 * ClientboundPlayerInfoUpdatePacket(UPDATE_DISPLAY_NAME) carrying the textures
 * property to every other connected client. Uses two mock server players
 * backed by EmbeddedChannels, mirroring Forge's PacketTest pattern, so no
 * real client or Mojang API is required.
 */
@GameTestHolder(value = "everlastingskins", namespace = "everlastingskins")
public class SkinVisibilityTest {

    private static final String TEST_TEXTURE_VALUE = "eyJ0aW1lc3RhbXAiOjE3MTk4NDY0MDAsInByb2ZpbGVJZCI6IjA2OWE3OWY0NDRlOTQ3MjZhNWJlZmNhOTBlMzhhYWY1IiwicHJvZmlsZU5hbWUiOiJOb3RjaCIsInRleHR1cmVzIjp7IlNLSU4iOnsidXJsIjoiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9iYmIxIn19fQ==";
    private static final String TEST_SIGNATURE = "ZmFrZVNpZ25hdHVyZUZvclRlc3Rpbmc9PQ==";

    @GameTest(template = "everlastingskins:empty", timeoutTicks = 200)
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

    private static ServerPlayer mockPlayer(GameTestHelper helper, String name) {
        return new ServerPlayer(
                helper.getLevel().getServer(),
                helper.getLevel(),
                new GameProfile(UUID.randomUUID(), name),
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
}
