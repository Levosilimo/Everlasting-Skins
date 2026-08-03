/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.harness;

import levosilimo.everlastingskins.permission.PermissionContext;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketEntityStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression: attachPermissionLevelSeam() and makeOp() must stay coherent in
 * either call order. Both read the same ops list: PermissionContext's
 * effectiveOpLevel reads getEntry, the packet seam reads getPermissionLevel.
 * A seam that replaces the ops mock with one missing the getEntry stub
 * degrades PermissionContext.effectiveOpLevel to 0; neither helper may
 * clobber the other's stub.
 */
class PermissionSeamCoherenceTest {

    @TempDir
    Path tempDir;

    private TestServerContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new TestServerContext(tempDir);
    }

    @AfterEach
    void tearDown() {
        ctx.close();
    }

    @Test
    void permissionContextAndPacketSeam_coherentWhenSeamAttachedAfterMakeOp() {
        EntityPlayerMP player = ctx.newPlayer("Target");
        ctx.makeOp(player);
        ctx.attachPermissionLevelSeam(2);

        assertEquals(2, PermissionContext.of(player.getUniqueID(), player).opLevel(),
            "the packet seam must not degrade PermissionContext.effectiveOpLevel to 0");
        assertStatusPacketByte(player, 26);
    }

    @Test
    void permissionContextAndPacketSeam_coherentWhenSeamAttachedBeforeMakeOp() {
        EntityPlayerMP player = ctx.newPlayer("Target");
        ctx.attachPermissionLevelSeam(2);
        ctx.makeOp(player);

        assertEquals(2, PermissionContext.of(player.getUniqueID(), player).opLevel(),
            "makeOp() after the seam must keep PermissionContext.effectiveOpLevel coherent");
        assertStatusPacketByte(player, 26);
    }

    /** The vanilla packet byte is 24+level; op level 2 -> 26. */
    private void assertStatusPacketByte(EntityPlayerMP player, int expectedByte) {
        PacketLog log = new PacketLog();
        log.attachTo(player.connection);
        ctx.playerList.updatePermissionLevel(player);
        assertEquals(1, log.ofType(SPacketEntityStatus.class).size(),
            "the seam must emit exactly one permission-level packet");
        assertEquals(expectedByte,
            ((SPacketEntityStatus) log.ofType(SPacketEntityStatus.class).get(0)).getOpCode(),
            "the status byte must be 24+level");
    }
}
