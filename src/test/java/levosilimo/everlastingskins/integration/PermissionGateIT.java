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
import net.minecraft.network.play.server.SPacketChat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Op gate contract: EntityPlayerMP.canUseCommand(2, ...) drives the vanilla
 * permission check inside SkinCommand; non-op senders are rejected with
 * "Permission denied" before any provider call.
 */
class PermissionGateIT {

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
    void nonOp_cannotSetSkinForOthers() {
        SkinCommandTestAccess.setMojangAPI(new FakeMojangAPI(TestProperties.NOTCH));
        EntityPlayerMP alice = ctx.newPlayer("Alice"); // not op
        EntityPlayerMP bob = ctx.newPlayer("Bob");
        PacketLog log = new PacketLog();
        log.attachTo(alice.connection);

        int result = ctx.commandManager.executeCommand(alice, "/skin clear Alice Bob");

        assertEquals(1, result);
        List<SPacketChat> chats = log.ofType(SPacketChat.class);
        assertTrue(chats.stream()
            .anyMatch(c -> c.getChatComponent().getUnformattedText().contains("Permission denied")));
        assertNull(ctx.storage.getSkin(bob.getUniqueID()));
    }

    @Test
    void op_canSetSkin() {
        SkinCommandTestAccess.setMojangAPI(new FakeMojangAPI(TestProperties.NOTCH));
        EntityPlayerMP alice = ctx.newPlayer("Alice");
        ctx.makeOp(alice);

        ctx.commandManager.executeCommand(alice, "/skin set mojang Notch");

        assertTrue(AsyncSupport.await(5000, () -> ctx.storage.getSkin(alice.getUniqueID()) != null),
            "skin should be stored after the async apply completes");
        assertNotNull(ctx.storage.getSkin(alice.getUniqueID()));
    }
}
