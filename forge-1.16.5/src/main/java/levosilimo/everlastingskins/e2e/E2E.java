/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.e2e;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Single entry point for the in-jar E2E support (master plan slice 3,
 * modern-injar pattern), invoked from
 * {@code EverlastingSkins()} (one line: {@code E2E.install();}).
 *
 * <p>Shipped-gated by {@code -Deverlastingskins.e2e=true} (never active in
 * production); side-gated afterwards via
 * {@link DistExecutor#safeRunWhenOn}:
 * <ul>
 *   <li>CLIENT → {@link E2EDriver} (phase machine: join → send
 *       {@code /skin set mojang TestPlayer} → assert the tab-list textures
 *       property carries the sentinel marker → result file +
 *       {@code System.exit});</li>
 *   <li>DEDICATED_SERVER → {@link E2ESentinelHook} (pre-seeds
 *       {@code SkinStorage} for the offline test player so the command
 *       round-trip delivers the marker).</li>
 * </ul>
 *
 * <p>On the modern line the assertion is the textures PROPERTY in the
 * client's tab-list copy of the profile
 * ({@code ClientPlayNetHandler.getPlayerInfo(uuid).getProfile()
 * .getProperties().get("textures")}) — the tab-list entry IS the received
 * packet data (SPlayerListItemPacket ADD_PLAYER), NOT a pixel injection.
 * The renderer consumes that property; asserting it is the modern
 * equivalent of the pre-1.8 injected-field check.
 */
public final class E2E {

    /** System property the e2e scripts set on both JVMs. */
    public static final String E2E_PROPERTY = "everlastingskins.e2e";

    /**
     * Sentinel marker shared by the server-side seed and the client-side
     * assertion: the hook stores a textures property whose value (base64 of
     * the vanilla textures JSON) contains a URL carrying this substring;
     * the driver asserts {@code value().contains(MARKER)}. Deterministic
     * across runs (unlike the pre-1.8 PNG sha1 tie) and greppable in both
     * logs.
     */
    public static final String MARKER = "e2e.example/e2e-sentinel";

    private E2E() {}

    /** One-line gated entry from the mod constructor. */
    public static void install() {
        if (!Boolean.getBoolean(E2E_PROPERTY)) {
            return;
        }
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> E2EDriver::install);
        DistExecutor.safeRunWhenOn(Dist.DEDICATED_SERVER, () -> E2ESentinelHook::install);
    }

    /**
     * Vanilla offline-mode UUID bridge: the same deterministic UUID the
     * server derives for an offline player name
     * ({@code UUID.nameUUIDFromBytes("OfflinePlayer:" + name)}). The e2e
     * script computes the identical value in python for the launcher
     * {@code --uuid} argument and the server's ops.json, so the seed, the
     * login session and the client-side assert all key on one UUID.
     */
    public static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }
}
