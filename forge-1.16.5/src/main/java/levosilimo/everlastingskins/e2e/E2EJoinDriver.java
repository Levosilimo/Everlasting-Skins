/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.e2e;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.ConnectingScreen;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * In-jar client JOIN driver for the real-client boot-smoke E2E (master plan
 * slice 4, {@code scripts/e2e/drivers/modern-smoke.sh}, 1.16.5 lane).
 *
 * <p>The 1.16.5 launcher-arg auto-connect (--server/--port) is gated on
 * {@code Minecraft.s()} = {@code !disableMultiplayer && serversAllowed()}
 * (bytecode-verified 2026-08-12): the authlib privileges request with an
 * offline token returns 401 and the YggdrasilSocialInteractionsService is
 * created with serversAllowed=false, so the vanilla ctor's deferred connect
 * never fires for an offline session — the client sits at the title screen.
 * This driver is the same remedy the slice-3 driver uses for the 1.21 line
 * (dial the test server from the main menu, {@code ConnectingScreen} here):
 * the boot-smoke asserts the join on the SERVER log, so the driver's only
 * job is to get in-world.
 *
 * <p>Shipped in the mod jar, gated at runtime by
 * {@code -Deverlastingskins.e2e=true} + client side (see
 * {@link E2EJoinDriver#install()} — the gate runs inside a
 * {@code DistExecutor.runWhenOn(Dist.CLIENT)} block in
 * {@code levosilimo.everlastingskins.EverlastingSkins}, so this class is
 * never loaded on a dedicated server). Lifecycle: install on the FORGE bus,
 * dial {@code E2E_HOST:E2E_PORT} (defaults 127.0.0.1:25565, overridable via
 * the e2e system properties) from the title screen, then log
 * {@code ES_E2E_JOIN=TestPlayer} when the local player spawns and stop.
 */
public final class E2EJoinDriver {

    private static final Logger LOGGER = LogManager.getLogger("everlastingskins.e2e");

    /** Offline test player (matches the e2e scripts' --username). */
    public static final String TEST_PLAYER = "TestPlayer";

    /** Join target (defaults; the e2e scripts can override via -D). */
    static final String DEFAULT_HOST = "127.0.0.1";
    static final int DEFAULT_PORT = 25565;

    private static final long JOIN_TIMEOUT_MS = 240_000L;

    private final String host;
    private final int port;
    private final long installedAt = System.currentTimeMillis();
    private boolean dialed;
    private boolean done;

    private E2EJoinDriver(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /** Installs the driver (property + side gates live in EverlastingSkins). */
    public static void install() {
        MinecraftForge.EVENT_BUS.register(new E2EJoinDriver(
            System.getProperty("everlastingskins.e2e.host", DEFAULT_HOST),
            Integer.parseInt(System.getProperty("everlastingskins.e2e.port", String.valueOf(DEFAULT_PORT)))
        ));
        LOGGER.info("ES_E2E_JOIN_DRIVER=installed side=client");
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done) {
            return;
        }
        try {
            tick();
        } catch (Throwable t) {
            LOGGER.error("ES_E2E_JOIN_DRIVER=crash ({})", t);
            done = true;
        }
    }

    private void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.screen == null) {
            return;
        }
        if (!dialed) {
            // First screen after the loading overlay is the main menu; the
            // vanilla deferred connect never ran (privileges gate), so dial
            // the test server from the menu. The current screen is the
            // parent ConnectingScreen returns to on disconnect.
            mc.setScreen(new ConnectingScreen(mc.screen, mc, host, port));
            dialed = true;
            LOGGER.info("ES_E2E_JOIN_DIAL={}:{}", host, port);
            return;
        }
        if (mc.player != null) {
            LOGGER.info("ES_E2E_JOIN={}", TEST_PLAYER);
            done = true;
            return;
        }
        if (System.currentTimeMillis() - installedAt > JOIN_TIMEOUT_MS) {
            LOGGER.error("ES_E2E_JOIN_DRIVER=join timeout");
            done = true;
        }
    }
}
