/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

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
 * Packet contract of the 1.12.2 refresh cascade: the tab-list update
 * (REMOVE then ADD) is broadcast globally via PlayerList, then the target's own
 * connection receives the respawn cascade — each packet type exactly once,
 * in respawn < difficulty < abilities order.
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
        // 'change' / 'fulfilled' chat packets. Wait for all three packets to
        // arrive before snapshotting, otherwise the ordering assertion below
        // races with the cascade being recorded on another thread.
        assertTrue(AsyncSupport.await(5000, () -> {
            List<Packet<?>> s = targetLog.all();
            return indexOfType(s, SPacketRespawn.class) >= 0
                && indexOfType(s, SPacketServerDifficulty.class) >= 0
                && indexOfType(s, SPacketPlayerAbilities.class) >= 0;
        }), "respawn/difficulty/abilities cascade packets must arrive within 5s");

        List<Packet<?>> self = targetLog.all();
        int respawn = indexOfType(self, SPacketRespawn.class);
        int difficulty = indexOfType(self, SPacketServerDifficulty.class);
        int abilities = indexOfType(self, SPacketPlayerAbilities.class);
        assertTrue(respawn < difficulty && difficulty < abilities,
            "respawn must precede difficulty, which must precede abilities");

        // Each cascade packet is sent exactly once (the 1.21 suite guards the
        // same regression for abilities and permission-level packets). The
        // cascade is complete once all three types have arrived, so the counts
        // below are stable.
        assertEquals(1, targetLog.ofType(SPacketRespawn.class).size(),
            "respawn must be sent exactly once");
        assertEquals(1, targetLog.ofType(SPacketServerDifficulty.class).size(),
            "difficulty must be sent exactly once");
        assertEquals(1, targetLog.ofType(SPacketPlayerAbilities.class).size(),
            "abilities must be sent exactly once");

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
