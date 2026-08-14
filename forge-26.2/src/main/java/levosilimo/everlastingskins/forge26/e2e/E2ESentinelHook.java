/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.forge26.e2e;

import levosilimo.everlastingskins.EverlastingSkins;
import levosilimo.everlastingskins.forge26.skinchanger.SkinRestorer;
import levosilimo.everlastingskins.skinchanger.SkinStorage;
import levosilimo.everlastingskins.skinchanger.command.SkinActionCommand;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import net.minecraftforge.event.server.ServerStartedEvent;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * Server-side E2E sentinel hook (master plan lib-13, modern variant — 26.x
 * port): with {@code -Deverlastingskins.e2e=true} on the SERVER, pre-seeds
 * {@code SkinStorage} for the offline test player before the test client
 * logs in, so the {@code /skin set mojang TestPlayer} round-trip re-applies
 * and re-broadcasts the sentinel textures property to the in-jar client
 * driver.
 *
 * <p>MODERN-LINE delta vs the pre-1.8 hooks: the sentinel is NOT a PNG —
 * it is a well-formed vanilla textures property whose value (base64 of the
 * textures JSON) carries the marker URL {@link E2E#MARKER}. The seeded
 * {@code CustomSkinProperty} uses source "MojangAPI" + username
 * "TestPlayer", which makes {@code SkinActionCommand.storedSourceMatches}
 * true for {@code /skin set mojang TestPlayer}: the command skips the
 * Mojang HTTP fetch (deterministic in a sandboxed network) and runs
 * {@code SkinRefreshHandler.task}, which mutates the GameProfile and
 * re-broadcasts it as REMOVE+ADD tab-list packets.
 *
 * <p>26.x delta vs the 1.21 reference: EventBus 7 typed-bus registration
 * ({@code ServerStartedEvent.BUS} — no {@code MinecraftForge.EVENT_BUS}
 * addListener path on the 26.x line).
 *
 * <p>Timing contract: {@link #install()} is invoked from the mod
 * constructor; seeding happens on {@link ServerStartedEvent} — after
 * {@code SkinRestorer.onInitializeServer} (ServerStartingEvent) created
 * {@code SkinStorage}, before any player can join.
 */
public final class E2ESentinelHook {

    public static final String TEST_PLAYER = E2EDriver.TEST_PLAYER;

    /** Sentinel texture URL (carries {@link E2E#MARKER}; never fetched in the sandbox). */
    public static final String SKIN_URL = "https://e2e.example/e2e-sentinel.png";

    private static volatile boolean seeded;

    private E2ESentinelHook() {}

    /** Called from {@code E2E.install()} (one line; gates live here). */
    public static void install() {
        if (!Boolean.getBoolean(E2E.E2E_PROPERTY)) {
            return;
        }
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist
            == net.minecraftforge.api.distmarker.Dist.CLIENT) {
            return;
        }
        ServerStartedEvent.BUS.addListener(E2ESentinelHook::onServerStarted);
        EverlastingSkins.logger.info("ES_E2E_SENTINEL=hook installed");
    }

    private static void onServerStarted(ServerStartedEvent event) {
        seedOnce();
    }

    /** Idempotent seed; test-visible for the pure-value tests. */
    static void seedOnce() {
        if (seeded) {
            return;
        }
        seeded = true;
        try {
            SkinStorage storage = SkinRestorer.getSkinStorage();
            if (storage == null) {
                EverlastingSkins.logger.warn("ES_E2E_SENTINEL=no storage, seed skipped");
                return;
            }
            UUID uuid = E2E.offlineUuid(TEST_PLAYER);
            String value = buildPropertyValue(uuid);
            CustomSkinProperty skin = new CustomSkinProperty(
                "textures", value, "", SkinActionCommand.SOURCE_MOJANG, TEST_PLAYER);
            storage.setSkin(uuid, skin);
            EverlastingSkins.logger.info("ES_E2E_SENTINEL=seeded player={} uuid={} url={}",
                TEST_PLAYER, uuid, SKIN_URL);
        } catch (Throwable t) {
            EverlastingSkins.logger.error("ES_E2E_SENTINEL=FAIL ({})", t.toString());
        }
    }

    /**
     * Builds the base64 textures-property value: the vanilla textures JSON
     * with a SKIN entry pointing at the sentinel URL. Pure + deterministic,
     * so the unit tests can decode and assert the marker without a server.
     */
    public static String buildPropertyValue(UUID profileId) {
        String profileIdHex = profileId.toString().replace("-", "");
        String json = "{\"timestamp\":0,\"profileId\":\"" + profileIdHex
            + "\",\"profileName\":\"" + TEST_PLAYER
            + "\",\"textures\":{\"SKIN\":{\"url\":\"" + SKIN_URL + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
