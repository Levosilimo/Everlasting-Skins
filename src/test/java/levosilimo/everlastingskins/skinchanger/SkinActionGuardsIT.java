/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.harness.AsyncSupport;
import levosilimo.everlastingskins.harness.PacketLog;
import levosilimo.everlastingskins.harness.TestServerContext;
import levosilimo.everlastingskins.integration.FakeMojangAPI;
import levosilimo.everlastingskins.integration.TestProperties;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.SPacketChat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Guard rails of the async apply pipeline in SkinAction: the per-player
 * cooldown rate limit plus its op bypass, the per-player refresh debounce
 * window, and the A5 stored-source skip that re-broadcasts without touching
 * the provider. The shared harness disables rate limiting and the debounce
 * for other suites, so this one re-enables them and restores the defaults.
 */
class SkinActionGuardsIT {

    /** Distinct texture payload so the debounce test can tell skins apart. */
    private static final String JEB_VALUE = Base64.getEncoder().encodeToString(
        ("{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/jeb\"}}}")
            .getBytes(StandardCharsets.UTF_8));

    @TempDir
    Path tempDir;

    private TestServerContext ctx;
    private FakeMojangAPI fake;

    @BeforeEach
    void setUp() throws Exception {
        ctx = new TestServerContext(tempDir);
        fake = new FakeMojangAPI();
        SkinCommandTestAccess.setMojangAPI(fake);
        SkinMetrics.INSTANCE.reset();
        clearStateMaps();
    }

    @AfterEach
    void tearDown() throws Exception {
        Config.RATE_LIMIT_ENABLED = false;
        Config.DEBOUNCE_MILLIS = 0;
        Config.COOLDOWN_SECONDS = 3;
        clearStateMaps();
        SkinCommandTestAccess.resetAPIs();
        ctx.close();
    }

    @Test
    void rateLimit_rejectsSecondDispatchWithinCooldown() {
        Config.RATE_LIMIT_ENABLED = true;
        Config.COOLDOWN_SECONDS = 60;
        fake.addSkin("Notch", TestProperties.NOTCH).addSkin("Dinnerbone", TestProperties.DINNERBONE);
        EntityPlayerMP alice = ctx.newPlayer("Alice"); // not op: bypass.cooldown needs op level 2
        PacketLog log = new PacketLog();
        log.attachTo(alice.connection);

        ctx.commandManager.executeCommand(alice, "/skin set mojang Notch");
        assertTrue(AsyncSupport.await(5000, () -> ctx.storage.getSkin(alice.getUniqueID()) != null),
            "first dispatch must store the skin");
        assertEquals(1, fake.lookupCount("Notch"));

        ctx.commandManager.executeCommand(alice, "/skin set mojang Dinnerbone");

        assertTrue(AsyncSupport.await(5000, () -> log.ofType(SPacketChat.class).stream()
                .anyMatch(c -> c.getChatComponent().getUnformattedText().contains("cooldown"))),
            "rate-limited sender must receive the cooldown message");
        assertEquals(0, fake.lookupCount("Dinnerbone"),
            "rejected dispatch must not fetch from the provider");
        assertEquals("Notch", sourceOf(alice),
            "rejected dispatch must not mutate storage");
        assertEquals(1, SkinMetrics.INSTANCE.snapshot().refreshesRateLimited());
    }

    @Test
    void rateLimit_bypassedForOp() {
        Config.RATE_LIMIT_ENABLED = true;
        Config.COOLDOWN_SECONDS = 60;
        fake.addSkin("Notch", TestProperties.NOTCH).addSkin("Dinnerbone", TestProperties.DINNERBONE);
        EntityPlayerMP alice = ctx.newPlayer("Alice");
        ctx.makeOp(alice); // op level 2 -> everlastingskins.bypass.cooldown

        ctx.commandManager.executeCommand(alice, "/skin set mojang Notch");
        assertTrue(AsyncSupport.await(5000, () -> "Notch".equals(sourceOf(alice))),
            "first dispatch must store the skin");
        ctx.commandManager.executeCommand(alice, "/skin set mojang Dinnerbone");
        assertTrue(AsyncSupport.await(5000, () -> "Dinnerbone".equals(sourceOf(alice))),
            "second dispatch inside the cooldown window must still apply for ops");

        assertEquals(1, fake.lookupCount("Notch"));
        assertEquals(1, fake.lookupCount("Dinnerbone"));
        assertEquals(0, SkinMetrics.INSTANCE.snapshot().refreshesRateLimited());
    }

    @Test
    void debounce_skipsRefreshWithinWindow() {
        Config.DEBOUNCE_MILLIS = 60_000;
        fake.addSkin("Notch", TestProperties.NOTCH);
        fake.addSkin("Jeb_", new CustomSkinProperty("textures", JEB_VALUE,
            TestProperties.NOTCH.getOriginalProperty().getSignature(), "Jeb_"));
        EntityPlayerMP alice = ctx.newPlayer("Alice");

        ctx.commandManager.executeCommand(alice, "/skin set mojang Notch");
        assertTrue(AsyncSupport.await(5000, () -> texturesValue(alice) != null),
            "first dispatch must apply textures to the profile");
        assertEquals(TestProperties.NOTCH.getOriginalProperty().getValue(), texturesValue(alice));

        ctx.commandManager.executeCommand(alice, "/skin set mojang Jeb_");
        assertTrue(AsyncSupport.await(5000, () -> "Jeb_".equals(sourceOf(alice))),
            "second dispatch must store the new skin even when the refresh is debounced");

        // The fetch completed and storage mutated, but the refresh task was
        // skipped inside the debounce window: the profile keeps the first skin.
        assertEquals(TestProperties.NOTCH.getOriginalProperty().getValue(), texturesValue(alice),
            "debounced refresh must not re-apply textures to the profile");
        assertEquals(1, SkinMetrics.INSTANCE.snapshot().refreshesDebounced());
    }

    @Test
    void storedSourceMatch_skipsProviderFetchAndRebroadcasts() {
        fake.addSkin("Notch", TestProperties.NOTCH);
        EntityPlayerMP alice = ctx.newPlayer("Alice");
        List<Packet<?>> global = new CopyOnWriteArrayList<>();
        doAnswer(inv -> {
            global.add(inv.getArgument(0));
            return null;
        }).when(ctx.playerList).sendPacketToAllPlayers(any(Packet.class));

        ctx.commandManager.executeCommand(alice, "/skin set mojang Notch");
        assertTrue(AsyncSupport.await(5000, () -> "Notch".equals(sourceOf(alice))),
            "first dispatch must store the skin");
        assertTrue(AsyncSupport.await(5000, () -> global.size() >= 2),
            "first dispatch must broadcast the REMOVE+ADD pair");
        assertEquals(1, fake.lookupCount("Notch"));

        ctx.commandManager.executeCommand(alice, "/skin set mojang Notch");
        assertTrue(AsyncSupport.await(5000, () -> global.size() >= 4),
            "identical dispatch must re-broadcast one more REMOVE+ADD pair");

        assertEquals(1, fake.lookupCount("Notch"),
            "identical dispatch must skip the provider fetch");
        assertEquals(1, SkinMetrics.INSTANCE.snapshot().refreshesSkippedStored());
        assertEquals(4, global.size());
    }

    private String sourceOf(EntityPlayerMP player) {
        CustomSkinProperty skin = ctx.storage.getSkin(player.getUniqueID());
        return skin != null ? skin.getSource() : null;
    }

    private static String texturesValue(EntityPlayerMP player) {
        Collection<com.mojang.authlib.properties.Property> textures =
            player.getGameProfile().getProperties().get("textures");
        return textures.isEmpty() ? null : textures.iterator().next().getValue();
    }

    // SkinAction's cooldown/debounce maps are private statics with no reset
    // seam; tests clear them so the windows start empty.
    private static void clearStateMaps() throws Exception {
        clearMap("lastCommandByPlayer");
        clearMap("commandTimestampsByPlayer");
        clearMap("lastRefreshByPlayer");
    }

    private static void clearMap(String name) throws Exception {
        Field field = SkinAction.class.getDeclaredField(name);
        field.setAccessible(true);
        ((Map<?, ?>) field.get(null)).clear();
    }
}
