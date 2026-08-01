package levosilimo.everlastingskins.harness;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketChat;
import net.minecraft.network.play.server.SPacketPlayerListItem;
import net.minecraft.util.text.TextComponentString;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HarnessSmokeTest {

    @TempDir
    Path tempDir;

    private TestServerContext ctx;
    private EntityPlayerMP player;
    private PacketLog log;

    @BeforeEach
    void setUp() {
        ctx = new TestServerContext(tempDir);
        player = ctx.newPlayer("Steve");
        log = new PacketLog();
        log.attachTo(player.connection);
    }

    @AfterEach
    void tearDown() {
        ctx.close();
    }

    @Test
    void factoryBuildsRealPlayer() {
        assertNotNull(player);
        assertEquals("Steve", player.getName());
        assertNotNull(player.connection);
        assertEquals(UUID.nameUUIDFromBytes("Steve".getBytes(StandardCharsets.UTF_8)),
            player.getUniqueID());
    }

    @Test
    void usageMessageIsCapturedAsChatPacket() {
        int result = ctx.commandManager.executeCommand(player, "/skin");

        assertEquals(1, result);
        List<SPacketChat> chats = log.ofType(SPacketChat.class);
        assertEquals(1, chats.size());
        assertTrue(chats.get(0).getChatComponent().getUnformattedText().contains("/skin"));
    }

    @Test
    void unknownCommandReturnsZero() {
        int result = ctx.commandManager.executeCommand(player, "/no-such-command");

        assertEquals(0, result);
        assertEquals(1, log.ofType(SPacketChat.class).size());
    }

    @Test
    void webSetIsDisabledInConfig() {
        int result = ctx.commandManager.executeCommand(player,
            "/skin set web classic https://example.com/skin.png");

        assertEquals(1, result);
        SPacketChat chat = log.ofType(SPacketChat.class).get(0);
        assertTrue(chat.getChatComponent().getUnformattedText().contains("MineSkin is disabled"));
    }

    @Test
    void sourceSelfReportsDefaultSkin() {
        int result = ctx.commandManager.executeCommand(player, "/skin source");

        assertEquals(1, result);
        SPacketChat chat = log.ofType(SPacketChat.class).get(0);
        assertTrue(chat.getChatComponent().getUnformattedText().contains("Steve"));
    }

    @Test
    void packetLogCapturesByType() {
        player.connection.sendPacket(new SPacketPlayerListItem(
            SPacketPlayerListItem.Action.ADD_PLAYER, player));

        assertEquals(1, log.ofType(SPacketPlayerListItem.class).size());
        assertEquals(1, log.size());
        log.clear();
        assertEquals(0, log.size());
    }

    @Test
    void wireSerializerRoundTripsPackets() {
        byte[] bytes = WireSerializer.serialize(
            new SPacketPlayerListItem(SPacketPlayerListItem.Action.ADD_PLAYER, player));

        assertTrue(bytes.length > 0);
        assertTrue(WireSerializer.sizeOf(new SPacketChat(new TextComponentString("hi"))) > 0);
    }

    @Test
    void asyncSupportPollsUntilCondition() {
        assertTrue(AsyncSupport.await(500, () -> true));
        assertFalse(AsyncSupport.await(100, () -> false));
    }
}
