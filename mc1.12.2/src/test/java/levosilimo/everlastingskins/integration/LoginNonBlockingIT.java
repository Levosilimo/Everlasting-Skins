/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.integration;

import levosilimo.everlastingskins.harness.AsyncSupport;
import levosilimo.everlastingskins.harness.TestServerContext;
import levosilimo.everlastingskins.skinchanger.SkinCommandTestAccess;
import levosilimo.everlastingskins.skinchanger.SkinRestorer;
import levosilimo.everlastingskins.skinchanger.responses.mojang.MojangSkinDataResult;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Login contract: the 3-provider Mojang fetch for a fresh player must run
 * off the login thread. A blocked provider must not stall onPlayerLoggedIn.
 */
class LoginNonBlockingIT {

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

    private static final class BlockingMojangAPI extends FakeMojangAPI {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        public Optional<MojangSkinDataResult> getSkin(String nameOrUniqueId) {
            entered.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return super.getSkin(nameOrUniqueId);
        }

        void release() {
            release.countDown();
        }
    }

    @Test
    @DisplayName("login returns immediately while the provider fetch runs async")
    void loginDoesNotBlockOnProviderFetch() throws Exception {
        BlockingMojangAPI api = new BlockingMojangAPI();
        api.addSkin("Alice", TestProperties.NOTCH);
        SkinCommandTestAccess.setMojangAPI(api);
        EntityPlayerMP alice = ctx.newPlayer("Alice");

        long start = System.nanoTime();
        new SkinRestorer().onPlayerLoggedIn(new PlayerEvent.PlayerLoggedInEvent(alice));
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertTrue(api.entered.await(2, TimeUnit.SECONDS),
            "the provider fetch should be offloaded to the executor");
        assertTrue(elapsedMs < 250,
            "login returned in " + elapsedMs + "ms; the provider fetch must not block it");

        api.release();
        assertTrue(AsyncSupport.await(5000,
                () -> alice.getGameProfile().getProperties().get("textures").size() == 1),
            "fetched skin should be applied to the profile once the fetch completes");
        assertNotNull(ctx.storage.getSkin(alice.getUniqueID()));
    }
}
