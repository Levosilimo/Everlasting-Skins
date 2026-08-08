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
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.skinchanger.SkinCommandTestAccess;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.SPacketPlayerListItem;
import net.minecraft.network.play.server.SPacketRespawn;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Negative control for the refresh cascade (analog of the 1.21
 * skinRefresh_negativeControl and skinCommand_clear_removesTexture GameTests):
 * a player with no fetchable skin must not trigger a tab-list broadcast or a
 * respawn cascade, and clearing into a dead Mojang profile must delete the
 * stored skin and its file without broadcasting.
 */
class NegativeControlIT {

    @TempDir
    Path tempDir;

    private TestServerContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new TestServerContext(tempDir);
        // Reset the shared metrics so failure counts below cannot be satisfied
        // vacuously by earlier tests.
        SkinMetrics.INSTANCE.reset();
    }

    @AfterEach
    void tearDown() {
        SkinCommandTestAccess.resetAPIs();
        ctx.close();
    }

    @Test
    void skinlessPlayer_clearProducesNoBroadcastOrCascade() {
        SkinCommandTestAccess.setMojangAPI(new FakeMojangAPI()); // no registered skins
        EntityPlayerMP alice = ctx.newPlayer("Alice");
        List<Packet<?>> global = new CopyOnWriteArrayList<>();
        doAnswer(inv -> {
            global.add(inv.getArgument(0));
            return null;
        }).when(ctx.playerList).sendPacketToAllPlayers(any(Packet.class));
        PacketLog log = new PacketLog();
        log.attachTo(alice.connection);
        long initiatedBefore = SkinMetrics.INSTANCE.snapshot().refreshesInitiated();
        long failedBefore = SkinMetrics.INSTANCE.snapshot().refreshesFailed();

        ctx.commandManager.executeCommand(alice, "/skin clear");
        // The clear pipeline ends in task(player, null) on an already
        // textureless profile, which is a successful no-op: awaiting the
        // initiated counter is the completion barrier (the task runs inline
        // via addScheduledTask) without assuming a failure was recorded.
        assertTrue(AsyncSupport.await(5000,
                () -> SkinMetrics.INSTANCE.snapshot().refreshesInitiated() > initiatedBefore),
            "clear with no Mojang profile must reach the null-property refresh task");

        assertEquals(0, global.size(), "skin-less clear must not broadcast tab-list packets");
        assertEquals(0, log.ofType(SPacketPlayerListItem.class).size());
        assertEquals(0, log.ofType(SPacketRespawn.class).size());
        assertEquals(failedBefore, SkinMetrics.INSTANCE.snapshot().refreshesFailed(),
            "clear with nothing applied is a successful no-op — must not record a refresh failure");
        assertEquals(0, alice.getGameProfile().getProperties().get("textures").size());
        assertNull(ctx.storage.getSkin(alice.getUniqueID()));
    }

    @Test
    void clearWithNoMojangProfile_removesSkinAndDeletesFile() {
        SkinCommandTestAccess.setMojangAPI(new FakeMojangAPI(TestProperties.NOTCH));
        EntityPlayerMP alice = ctx.newPlayer("Alice");
        Path file = tempDir.resolve("EverlastingSkins").resolve(alice.getUniqueID() + ".json");

        ctx.commandManager.executeCommand(alice, "/skin set mojang Notch");
        assertTrue(AsyncSupport.await(5000, () -> Files.exists(file)),
            "set must persist the skin file before clear");

        // The restore fetch now finds no profile for the stored source.
        SkinCommandTestAccess.setMojangAPI(new FakeMojangAPI());
        ctx.commandManager.executeCommand(alice, "/skin clear");

        assertTrue(AsyncSupport.await(5000, () -> ctx.storage.getSkin(alice.getUniqueID()) == null),
            "clear with no Mojang profile must remove the stored skin");
        assertTrue(AsyncSupport.await(5000, () -> !Files.exists(file)),
            "clear with no Mojang profile must delete the skin file");
    }
}
