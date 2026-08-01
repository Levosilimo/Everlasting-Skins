package levosilimo.everlastingskins.integration;

import levosilimo.everlastingskins.harness.AsyncSupport;
import levosilimo.everlastingskins.harness.PacketLog;
import levosilimo.everlastingskins.harness.TestServerContext;
import levosilimo.everlastingskins.skinchanger.SkinCommandTestAccess;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketChat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end: a real /skin set mojang command dispatched through the real
 * CommandHandler with a fake Mojang provider.
 */
class CommandDispatchIT {

    @TempDir
    Path tempDir;

    private TestServerContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new TestServerContext(tempDir);
    }

    @AfterEach
    void tearDown() {
        SkinCommandTestAccess.resetAPIs();
        ctx.close();
    }

    @Test
    void skinSetMojang_dispatchesViaCommandHandler() {
        SkinCommandTestAccess.setMojangAPI(new FakeMojangAPI(TestProperties.NOTCH));
        EntityPlayerMP alice = ctx.newPlayer("Alice");
        ctx.makeOp(alice);
        PacketLog log = new PacketLog();
        log.attachTo(alice.connection);

        int result = ctx.commandManager.executeCommand(alice, "/skin set mojang Notch");

        assertEquals(1, result);
        assertTrue(AsyncSupport.await(5000, () -> ctx.storage.getSkin(alice.getUniqueID()) != null),
            "skin should be stored after the async apply completes");
        CustomSkinProperty stored = ctx.storage.getSkin(alice.getUniqueID());
        assertNotNull(stored);
        assertEquals("Notch", stored.getSource());
        assertEquals(1, alice.getGameProfile().getProperties().get("textures").size());

        List<SPacketChat> chats = log.ofType(SPacketChat.class);
        assertTrue(chats.stream()
            .anyMatch(c -> c.getChatComponent().getUnformattedText().contains("Skin applied")));
    }
}
