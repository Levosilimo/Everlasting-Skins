package levosilimo.everlastingskins.integration;

import levosilimo.everlastingskins.harness.AsyncSupport;
import levosilimo.everlastingskins.harness.TestServerContext;
import levosilimo.everlastingskins.skinchanger.SkinCommandTestAccess;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import net.minecraft.entity.player.EntityPlayerMP;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Multi-target clear contract: each target must be restored from its OWN
 * stored source. The historical bug applied the FIRST target's Mojang skin
 * to every target in the command.
 */
class MultiTargetClearIT {

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
    @DisplayName("/skin clear restores each target from its own stored source")
    void clearRestoresEachTargetFromItsOwnStoredSource() {
        SkinCommandTestAccess.setMojangAPI(
            new FakeMojangAPI(TestProperties.NOTCH, TestProperties.DINNERBONE));
        EntityPlayerMP alice = ctx.newPlayer("Alice");
        EntityPlayerMP bob = ctx.newPlayer("Bob");
        ctx.makeOp(alice);

        ctx.commandManager.executeCommand(alice, "/skin set mojang Notch");
        ctx.commandManager.executeCommand(bob, "/skin set mojang Dinnerbone");
        assertTrue(AsyncSupport.await(5000, () ->
                "Notch".equals(sourceOf(alice)) && "Dinnerbone".equals(sourceOf(bob))),
            "both skins should be stored before clear");

        ctx.commandManager.executeCommand(alice, "/skin clear Alice Bob");

        assertTrue(AsyncSupport.await(5000, () ->
                "Notch".equals(sourceOf(alice)) && "Dinnerbone".equals(sourceOf(bob))),
            "each target must be restored from its OWN stored source, got "
                + sourceOf(alice) + " / " + sourceOf(bob));
        assertNotNull(ctx.storage.getSkin(alice.getUniqueID()));
        assertNotNull(ctx.storage.getSkin(bob.getUniqueID()));
        assertEquals("Notch", sourceOf(alice));
        assertEquals("Dinnerbone", sourceOf(bob));
    }

    private String sourceOf(EntityPlayerMP player) {
        CustomSkinProperty skin = ctx.storage.getSkin(player.getUniqueID());
        return skin != null ? skin.getSource() : null;
    }
}
