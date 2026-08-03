/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.integration;

import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.harness.AsyncSupport;
import levosilimo.everlastingskins.harness.PacketLog;
import levosilimo.everlastingskins.harness.TestServerContext;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.skinchanger.SkinCommandTestAccess;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketChat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stored/applied consistency across the per-player refresh debounce window.
 * A debounced request must not persist a skin that was never applied to the
 * GameProfile; once the window expires, the same request applies normally.
 */
class DebounceStateConsistencyIT {

    private static final String NOTCH_VALUE = TestProperties.NOTCH.getOriginalProperty().getValue();
    private static final String JEB_VALUE = "jebTextureValue";

    @TempDir
    Path tempDir;

    private TestServerContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new TestServerContext(tempDir);
        // TestServerContext disables the debounce; each test opts into a window.
        Config.RATE_LIMIT_ENABLED = false;
    }

    @AfterEach
    void tearDown() {
        SkinCommandTestAccess.resetAPIs();
        Config.DEBOUNCE_MILLIS = 0;
        ctx.close();
    }

    @Test
    void debouncedRequest_keepsStoredEqualToApplied() {
        Config.DEBOUNCE_MILLIS = 60_000;
        FakeMojangAPI api = new FakeMojangAPI(TestProperties.NOTCH)
            .addSkin("Jeb_", new CustomSkinProperty("textures", JEB_VALUE, "jebSignature", "Jeb_"));
        SkinCommandTestAccess.setMojangAPI(api);
        EntityPlayerMP player = ctx.newPlayer("DebAlice");
        ctx.makeOp(player);

        ctx.commandManager.executeCommand(player, "/skin set mojang Notch");
        assertTrue(AsyncSupport.await(5000, () -> ctx.storage.getSkin(player.getUniqueID()) != null),
            "first request must store the skin");
        assertTrue(AsyncSupport.await(5000, () -> player.getGameProfile().getProperties().get("textures").size() == 1),
            "first request must apply the skin to the GameProfile");
        assertEquals(NOTCH_VALUE, profileValue(player), "first request applies the requested skin");

        long debouncedBefore = SkinMetrics.INSTANCE.snapshot().refreshesDebounced();
        long completedBefore = SkinMetrics.INSTANCE.snapshot().refreshesCompleted();
        PacketLog log = new PacketLog();
        log.attachTo(player.connection);

        // Second request lands inside the debounce window: the refresh is
        // skipped, so the storage must keep the applied skin untouched.
        ctx.commandManager.executeCommand(player, "/skin set mojang Jeb_");
        assertTrue(AsyncSupport.await(5000,
            () -> SkinMetrics.INSTANCE.snapshot().refreshesDebounced() > debouncedBefore),
            "second request must be recorded as debounced");
        assertEquals("Notch", ctx.storage.getSource(player.getUniqueID()),
            "debounced request must not overwrite the stored source");
        assertEquals(NOTCH_VALUE, ctx.storage.getSkin(player.getUniqueID()).getOriginalProperty().getValue(),
            "debounced request must not overwrite the stored skin");
        assertEquals(NOTCH_VALUE, profileValue(player),
            "debounced request must not change the applied GameProfile");
        assertEquals(0, SkinMetrics.INSTANCE.snapshot().refreshesCompleted() - completedBefore,
            "debounced request must not run a profile refresh");
        assertTrue(log.ofType(SPacketChat.class).stream()
            .noneMatch(c -> c.getChatComponent().getUnformattedText().contains("fulfilled")),
            "debounced request must not claim fulfilment");
    }

    @Test
    void expiredDebounceWindow_appliesNormally() throws InterruptedException {
        Config.DEBOUNCE_MILLIS = 100;
        FakeMojangAPI api = new FakeMojangAPI(TestProperties.NOTCH)
            .addSkin("Jeb_", new CustomSkinProperty("textures", JEB_VALUE, "jebSignature", "Jeb_"));
        SkinCommandTestAccess.setMojangAPI(api);
        EntityPlayerMP player = ctx.newPlayer("DebBob");
        ctx.makeOp(player);

        ctx.commandManager.executeCommand(player, "/skin set mojang Notch");
        assertTrue(AsyncSupport.await(5000, () -> ctx.storage.getSkin(player.getUniqueID()) != null),
            "first request must store the skin");
        assertTrue(AsyncSupport.await(5000, () -> player.getGameProfile().getProperties().get("textures").size() == 1),
            "first request must apply the skin to the GameProfile");

        Thread.sleep(300);
        long debouncedBefore = SkinMetrics.INSTANCE.snapshot().refreshesDebounced();
        long completedBefore = SkinMetrics.INSTANCE.snapshot().refreshesCompleted();

        ctx.commandManager.executeCommand(player, "/skin set mojang Jeb_");
        assertTrue(AsyncSupport.await(5000,
            () -> "Jeb_".equals(ctx.storage.getSource(player.getUniqueID()))),
            "request after the window must store the new skin");
        assertTrue(AsyncSupport.await(5000,
            () -> SkinMetrics.INSTANCE.snapshot().refreshesCompleted() > completedBefore),
            "request after the window must run a profile refresh");
        assertEquals(0, SkinMetrics.INSTANCE.snapshot().refreshesDebounced() - debouncedBefore,
            "request after the window must not be debounced");
        assertEquals(JEB_VALUE, profileValue(player), "request after the window must apply the new skin");
    }

    private static String profileValue(EntityPlayerMP player) {
        return player.getGameProfile().getProperties().get("textures").iterator().next().getValue();
    }
}
