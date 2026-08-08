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
import net.minecraft.network.play.server.SPacketEntityStatus;
import net.minecraft.network.play.server.SPacketPlayerAbilities;
import net.minecraft.network.play.server.SPacketPlayerListItem;
import net.minecraft.network.play.server.SPacketRespawn;
import net.minecraft.network.play.server.SPacketServerDifficulty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Packet contract of the 1.12.2 refresh cascade: the tab-list update
 * (REMOVE then ADD) is broadcast globally via PlayerList — reaching the
 * target's own connection too — then the target receives the respawn
 * cascade: each packet type exactly once, in respawn < difficulty <
 * permission-level < abilities order.
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

        List<Packet<?>> global = new CopyOnWriteArrayList<>();
        ctx.recordAndDeliverBroadcast(global);
        PacketLog targetLog = new PacketLog();
        targetLog.attachTo(target.connection);
        PacketLog observerLog = new PacketLog();
        observerLog.attachTo(observer.connection);
        // Self-contained vanilla seam: the permission-level packet is
        // observable without depending on makeOp() stubbing order.
        ctx.attachPermissionLevelSeam(2);

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
        // 'change' / 'fulfilled' chat packets. Wait for all four packets to
        // arrive before snapshotting, otherwise the ordering assertion below
        // races with the cascade being recorded on another thread.
        assertTrue(AsyncSupport.await(5000, () -> {
            List<Packet<?>> s = targetLog.all();
            return indexOfType(s, SPacketRespawn.class) >= 0
                && indexOfType(s, SPacketServerDifficulty.class) >= 0
                && indexOfType(s, SPacketEntityStatus.class) >= 0
                && indexOfType(s, SPacketPlayerAbilities.class) >= 0;
        }), "respawn/difficulty/permission/abilities cascade packets must arrive within 5s");

        List<Packet<?>> self = targetLog.all();
        int respawn = indexOfType(self, SPacketRespawn.class);
        int difficulty = indexOfType(self, SPacketServerDifficulty.class);
        int permission = indexOfType(self, SPacketEntityStatus.class);
        int abilities = indexOfType(self, SPacketPlayerAbilities.class);
        assertTrue(respawn < difficulty && difficulty < permission && permission < abilities,
            "respawn < difficulty < permission-level < abilities order expected");
        // The seam must emit the vanilla byte 24+level; op level 2 -> 26. This
        // is the exact byte a real PlayerList.updatePermissionLevel sends.
        assertEquals(26, ((SPacketEntityStatus) self.get(permission)).getOpCode(),
            "permission-level packet must carry byte 24+level (26 for op level 2)");

        // Each cascade packet is sent exactly once (the 1.21 suite guards the
        // same regressions for the abilities and permission-level packets;
        // here the permission-level packet is observable through the faithful
        // PlayerList seam above). The cascade is complete once all packet
        // types have arrived, so the counts below are stable.
        assertEquals(1, targetLog.ofType(SPacketRespawn.class).size(),
            "respawn must be sent exactly once");
        assertEquals(1, targetLog.ofType(SPacketServerDifficulty.class).size(),
            "difficulty must be sent exactly once");
        assertEquals(1, targetLog.ofType(SPacketEntityStatus.class).size(),
            "permission-level packet must be sent exactly once");
        assertEquals(1, targetLog.ofType(SPacketPlayerAbilities.class).size(),
            "abilities must be sent exactly once");

        // Observers receive the tab-list update through the global broadcast:
        // sendPacketToAllPlayers delivers REMOVE then ADD to every online
        // player's connection, this observer included.
        assertTrue(AsyncSupport.await(5000,
                () -> observerLog.ofType(SPacketPlayerListItem.class).size() == 2),
            "observer must receive the REMOVE+ADD tab-list broadcast");
        List<SPacketPlayerListItem> observerTabList = observerLog.ofType(SPacketPlayerListItem.class);
        assertEquals(SPacketPlayerListItem.Action.REMOVE_PLAYER, observerTabList.get(0).getAction());
        assertEquals(SPacketPlayerListItem.Action.ADD_PLAYER, observerTabList.get(1).getAction());
    }

    @Test
    void skinSet_targetConnectionReceivesTabListUpdate() {
        SkinCommandTestAccess.setMojangAPI(new FakeMojangAPI(TestProperties.NOTCH));
        EntityPlayerMP target = ctx.newPlayer("Target");
        ctx.makeOp(target);
        List<Packet<?>> global = new CopyOnWriteArrayList<>();
        ctx.recordAndDeliverBroadcast(global);
        PacketLog targetLog = new PacketLog();
        targetLog.attachTo(target.connection);

        ctx.commandManager.executeCommand(target, "/skin set mojang Notch");
        assertTrue(AsyncSupport.await(5000, () -> global.size() >= 2),
            "REMOVE+ADD tab-list broadcast must reach the global list");

        // sendPacketToAllPlayers iterates every online player, the target
        // included, so the target's own connection receives the tab-list
        // update (the 1.21 self-reception analog).
        assertTrue(AsyncSupport.await(5000,
                () -> targetLog.ofType(SPacketPlayerListItem.class).size() == 2),
            "target must receive the REMOVE+ADD pair on its own connection");
        List<SPacketPlayerListItem> tabList = targetLog.ofType(SPacketPlayerListItem.class);
        assertEquals(SPacketPlayerListItem.Action.REMOVE_PLAYER, tabList.get(0).getAction());
        assertEquals(SPacketPlayerListItem.Action.ADD_PLAYER, tabList.get(1).getAction());
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
