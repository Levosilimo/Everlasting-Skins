package levosilimo.everlastingskins.integration;

import levosilimo.everlastingskins.harness.AsyncSupport;
import levosilimo.everlastingskins.harness.TestServerContext;
import levosilimo.everlastingskins.harness.WireSerializer;
import levosilimo.everlastingskins.skinchanger.SkinCommandTestAccess;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketPlayerListItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wire-level contract for SPacketPlayerListItem on 1.12.2: ADD_PLAYER carries
 * the full GameProfile (UUID + name + textures property + signature), while
 * UPDATE_DISPLAY_NAME carries only the UUID.
 */
class WireLevelBytesIT {

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
    void wireSerialize_addPlayer_includesProfile() {
        SkinCommandTestAccess.setMojangAPI(new FakeMojangAPI(TestProperties.NOTCH));
        EntityPlayerMP alice = ctx.newPlayer("Alice");
        ctx.makeOp(alice);

        ctx.commandManager.executeCommand(alice, "/skin set mojang Notch");
        assertTrue(AsyncSupport.await(5000,
            () -> alice.getGameProfile().getProperties().get("textures").size() == 1),
            "profile should carry the applied textures property");

        byte[] bytes = WireSerializer.serialize(
            new SPacketPlayerListItem(SPacketPlayerListItem.Action.ADD_PLAYER, alice));
        // ADD_PLAYER payload is >100 bytes (profile + textures); UPDATE_DISPLAY_NAME is much smaller.
        assertTrue(bytes.length > 100,
            "ADD_PLAYER must carry the full profile (size was " + bytes.length + ")");
    }

    @Test
    void wireSerialize_updateDisplayName_omitsProfile() {
        EntityPlayerMP alice = ctx.newPlayer("Alice");

        byte[] bytes = WireSerializer.serialize(
            new SPacketPlayerListItem(SPacketPlayerListItem.Action.UPDATE_DISPLAY_NAME, alice));
        // UPDATE_DISPLAY_NAME only carries UUID + optional displayName — no profile.
        assertTrue(bytes.length < 50,
            "UPDATE_DISPLAY_NAME must NOT carry the profile (size was " + bytes.length + ")");
    }
}
