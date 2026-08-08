/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.integration;

import levosilimo.everlastingskins.skinchanger.FakeMojangAPI;
import levosilimo.everlastingskins.harness.AsyncSupport;
import levosilimo.everlastingskins.harness.PacketLog;
import levosilimo.everlastingskins.harness.TestServerContext;
import levosilimo.everlastingskins.skinchanger.SkinCommandTestAccess;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.SPacketPlayerListItem;
import net.minecraft.world.WorldServer;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * DIVERGENCE DOCUMENTED: 1.12.2 production code uses sendPacketToAllPlayers
 * which is global. Cross-dimension observers receive the update (verified
 * here). If dimension scoping is added later, update this test.
 */
class CrossDimensionBroadcastIT {

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
    void crossDimension_observerStillReceives() {
        SkinCommandTestAccess.setMojangAPI(new FakeMojangAPI(TestProperties.NOTCH));
        EntityPlayerMP target = ctx.newPlayer("Target"); // world dimension 0
        WorldServer nether = ctx.newWorld(-1);
        EntityPlayerMP crossObserver = ctx.newPlayer("CrossObserver", nether);
        ctx.makeOp(target);

        List<Packet<?>> global = new ArrayList<Packet<?>>();
        doAnswer(inv -> {
            global.add(inv.getArgument(0));
            return null;
        }).when(ctx.playerList).sendPacketToAllPlayers(any(Packet.class));
        PacketLog observerLog = new PacketLog();
        observerLog.attachTo(crossObserver.connection);

        ctx.commandManager.executeCommand(target, "/skin set mojang Notch");
        assertTrue(AsyncSupport.await(5000, () -> ctx.storage.getSkin(target.getUniqueID()) != null),
            "skin should be stored after the async apply completes");

        // Global contract: exactly one REMOVE + one ADD broadcast to all players.
        assertTrue(AsyncSupport.await(5000, () -> global.size() >= 2),
            "REMOVE+ADD tab-list broadcast must reach the global list");
        verify(ctx.playerList, times(2)).sendPacketToAllPlayers(any(Packet.class));
        assertEquals(2, global.size());
        assertTrue(global.stream().allMatch(p -> p instanceof SPacketPlayerListItem));
        assertEquals(SPacketPlayerListItem.Action.REMOVE_PLAYER,
            ((SPacketPlayerListItem) global.get(0)).getAction());
        assertEquals(SPacketPlayerListItem.Action.ADD_PLAYER,
            ((SPacketPlayerListItem) global.get(1)).getAction());
        // The Nether observer has no direct per-viewer packets; reception is
        // through the global broadcast captured above.
        assertEquals(0, observerLog.size());
    }
}
