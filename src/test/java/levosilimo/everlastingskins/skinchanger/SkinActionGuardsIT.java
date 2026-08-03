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
import levosilimo.everlastingskins.util.I18nUtils;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.SPacketChat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
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
    void setUp() {
        ctx = new TestServerContext(tempDir);
        fake = new FakeMojangAPI();
        SkinCommandTestAccess.setMojangAPI(fake);
        SkinMetrics.INSTANCE.reset();
        SkinActionTestAccess.clearGuardState();
    }

    @AfterEach
    void tearDown() {
        Config.RATE_LIMIT_ENABLED = false;
        Config.DEBOUNCE_MILLIS = 0;
        Config.COOLDOWN_SECONDS = 3;
        SkinActionTestAccess.clearGuardState();
        SkinActionTestAccess.clearI18n();
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
        // Load the real language files (as serverStarting does) so the cooldown
        // message is the localized template, not the raw i18n key fallback.
        I18nUtils.loadAll();

        ctx.commandManager.executeCommand(alice, "/skin set mojang Notch");
        assertTrue(AsyncSupport.await(5000,
                () -> alice.getGameProfile().getProperties().get("textures").size() == 1),
            "first dispatch must apply textures to the profile");
        assertEquals(1, fake.lookupCount("Notch"));

        ctx.commandManager.executeCommand(alice, "/skin set mojang Dinnerbone");

        // Localization was initialized (I18nUtils.loadAll) iff the message is
        // not the raw fallback key; every locale template embeds the remaining
        // wait as a number, so a digit proves the duration payload reached the
        // user without coupling to any prose or timing-dependent value.
        assertTrue(AsyncSupport.await(5000, () -> log.ofType(SPacketChat.class).stream()
                .map(c -> c.getChatComponent().getUnformattedText())
                .anyMatch(text -> !"cooldown".equals(text) && text.matches(".*\\d.*"))),
            "rate-limited sender must receive the localized cooldown message with its duration");
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
        List<Packet<?>> global = new CopyOnWriteArrayList<>();
        doAnswer(inv -> {
            global.add(inv.getArgument(0));
            return null;
        }).when(ctx.playerList).sendPacketToAllPlayers(any(Packet.class));

        ctx.commandManager.executeCommand(alice, "/skin set mojang Notch");
        assertTrue(AsyncSupport.await(5000, () -> global.size() >= 2),
            "first dispatch must broadcast the REMOVE+ADD pair");
        ctx.commandManager.executeCommand(alice, "/skin set mojang Dinnerbone");
        assertTrue(AsyncSupport.await(5000, () -> global.size() >= 4),
            "second dispatch inside the cooldown window must still apply for ops");

        assertTrue(AsyncSupport.await(5000, () -> "Dinnerbone".equals(sourceOf(alice))),
            "second dispatch must store the new skin");
        assertEquals(1, fake.lookupCount("Notch"));
        assertEquals(1, fake.lookupCount("Dinnerbone"));
        assertEquals(0, SkinMetrics.INSTANCE.snapshot().refreshesRateLimited());
    }

    @Test
    void debounce_skipsRefreshTaskWithinWindow() {
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
        // The debounce record is the last effect of the second fetch's
        // completion callback, so awaiting it guarantees the pipeline finished.
        assertTrue(AsyncSupport.await(5000,
                () -> SkinMetrics.INSTANCE.snapshot().refreshesDebounced() >= 1),
            "second dispatch inside the debounce window must be skipped");

        // The fetch callback stores the fetched skin before the debounce gate,
        // so storage reflects the new source while the refresh TASK is skipped
        // and the profile keeps the first skin. This stored/applied divergence
        // is tracked in the separate production debounce lane; this test only
        // guards the skip itself.
        assertEquals("Jeb_", sourceOf(alice),
            "fetch callback must store the fetched skin (pre-debounce mutation)");
        assertEquals(TestProperties.NOTCH.getOriginalProperty().getValue(), texturesValue(alice),
            "refresh task must be skipped inside the debounce window (profile untouched)");
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

    @Test
    void noSkinProperty_taskBroadcastsNothing() {
        EntityPlayerMP alice = ctx.newPlayer("Alice");
        List<Packet<?>> global = new CopyOnWriteArrayList<>();
        doAnswer(inv -> {
            global.add(inv.getArgument(0));
            return null;
        }).when(ctx.playerList).sendPacketToAllPlayers(any(Packet.class));
        long failedBefore = SkinMetrics.INSTANCE.snapshot().refreshesFailed();

        // Direct baseline analog of the 1.21 negativeControl: the refresh task
        // with a null or empty property must fail soft — no tab-list broadcast,
        // no profile mutation, exactly one recorded failure per call.
        SkinRefreshTask.task(alice, null, 0L);
        SkinRefreshTask.task(alice, new CustomSkinProperty("textures", "", null, "none"), 0L);

        assertEquals(0, global.size(), "null/empty property must not broadcast");
        assertEquals(0, alice.getGameProfile().getProperties().get("textures").size(),
            "null/empty property must not mutate the profile");
        assertTrue(SkinMetrics.INSTANCE.snapshot().refreshesFailed() >= failedBefore + 2,
            "both no-skin calls must record a failed refresh");
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
}
