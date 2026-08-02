package levosilimo.everlastingskins.integration;

import levosilimo.everlastingskins.harness.AsyncSupport;
import levosilimo.everlastingskins.harness.PacketLog;
import levosilimo.everlastingskins.harness.TestServerContext;
import levosilimo.everlastingskins.skinchanger.SkinCommandTestAccess;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.SPacketPlayerAbilities;
import net.minecraft.network.play.server.SPacketPlayerListItem;
import net.minecraft.network.play.server.SPacketRespawn;
import net.minecraft.network.play.server.SPacketServerDifficulty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Packet ordering contract of the 1.12.2 refresh cascade: the tab-list update
 * (REMOVE then ADD) is broadcast globally via PlayerList, then the target's own
 * connection receives the respawn cascade.
 */
class ObserverPacketIT {

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
    void skinSet_observerCapturesRemoveAddAndSpawn() {
        SkinCommandTestAccess.setMojangAPI(new FakeMojangAPI(TestProperties.NOTCH));
        EntityPlayerMP target = ctx.newPlayer("Target");
        EntityPlayerMP observer = ctx.newPlayer("Observer");
        ctx.makeOp(target);

        List<Packet<?>> global = new ArrayList<Packet<?>>();
        doAnswer(inv -> {
            global.add(inv.getArgument(0));
            return null;
        }).when(ctx.playerList).sendPacketToAllPlayers(any(Packet.class));
        PacketLog targetLog = new PacketLog();
        targetLog.attachTo(target.connection);
        PacketLog observerLog = new PacketLog();
        observerLog.attachTo(observer.connection);

        ctx.commandManager.executeCommand(target, "/skin set mojang Notch");
        assertTrue(AsyncSupport.await(5000, () -> ctx.storage.getSkin(target.getUniqueID()) != null),
            "skin should be stored after the async apply completes");

        // Tab-list update is broadcast globally to all online players.
        assertTrue(AsyncSupport.await(5000, () -> global.size() >= 2),
            "REMOVE+ADD tab-list broadcast must reach the global list");
        assertEquals(2, global.size());
        assertEquals(SPacketPlayerListItem.Action.REMOVE_PLAYER,
            ((SPacketPlayerListItem) global.get(0)).getAction());
        assertEquals(SPacketPlayerListItem.Action.ADD_PLAYER,
            ((SPacketPlayerListItem) global.get(1)).getAction());

        // The target's own connection gets the respawn cascade after the
        // "Processing..." / "Skin applied" chat packets.
        List<Packet<?>> self = targetLog.all();
        int respawn = indexOfType(self, SPacketRespawn.class);
        int difficulty = indexOfType(self, SPacketServerDifficulty.class);
        int abilities = indexOfType(self, SPacketPlayerAbilities.class);
        assertTrue(respawn >= 0 && respawn < difficulty && difficulty < abilities,
            "respawn must precede difficulty, which must precede abilities");

        // Observers have no direct per-viewer packets on 1.12.2; they receive
        // the update through the global sendPacketToAllPlayers broadcast above.
        assertEquals(0, observerLog.size());
    }

    private static int indexOfType(List<Packet<?>> packets, Class<? extends Packet<?>> type) {
        for (int i = 0; i < packets.size(); i++) {
            if (type.isInstance(packets.get(i))) {
                return i;
            }
        }
        return -1;
    }
}
