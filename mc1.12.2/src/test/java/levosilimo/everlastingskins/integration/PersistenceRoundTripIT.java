/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.integration;

import levosilimo.everlastingskins.skinchanger.FakeMojangAPI;
import com.mojang.authlib.properties.Property;
import levosilimo.everlastingskins.harness.AsyncSupport;
import levosilimo.everlastingskins.harness.TestServerContext;
import levosilimo.everlastingskins.skinchanger.SkinCommandTestAccess;
import levosilimo.everlastingskins.skinchanger.SkinIO;
import levosilimo.everlastingskins.skinchanger.SkinStorage;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Persistence contract: set -> logout (write) -> simulated restart (fresh
 * storage) -> login (re-apply) leaves the skin on the GameProfile.
 */
class PersistenceRoundTripIT {

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
    void persistenceRoundTrip_reappliesAfterLogout() {
        SkinCommandTestAccess.setMojangAPI(new FakeMojangAPI(TestProperties.NOTCH));
        EntityPlayerMP alice = ctx.newPlayer("Alice");
        ctx.makeOp(alice);

        ctx.commandManager.executeCommand(alice, "/skin set mojang Notch");
        assertTrue(AsyncSupport.await(5000, () -> ctx.storage.getSkin(alice.getUniqueID()) != null),
            "skin should be stored after the async apply completes");

        Path dataDir = ctx.getTempDir().resolve("EverlastingSkins");
        Path expected = dataDir.resolve(alice.getUniqueID() + ".json");
        assertTrue(AsyncSupport.await(5000, () -> Files.exists(expected)),
            "skin file should be written asynchronously during apply");

        // Logout: the harness does not register bus handlers, so invoke the
        // real event handler directly instead of posting to MinecraftForge.EVENT_BUS.
        ctx.skinRestorer.onPlayerLoggedOut(new PlayerLoggedOutEvent(alice));
        assertTrue(Files.exists(expected), "logout must flush the skin file");

        // Simulated restart: drop the static in-memory cache and rebuild
        // storage over the same directory.
        SkinStorage.resetForTest();
        ctx.storage = new SkinStorage(new SkinIO(dataDir));

        ctx.skinRestorer.onPlayerLoggedIn(new PlayerLoggedInEvent(alice));

        Collection<Property> textures = alice.getGameProfile().getProperties().get("textures");
        assertEquals(1, textures.size());
        assertEquals(TestProperties.NOTCH.getOriginalProperty().getValue(),
            textures.iterator().next().getValue());
    }
}
