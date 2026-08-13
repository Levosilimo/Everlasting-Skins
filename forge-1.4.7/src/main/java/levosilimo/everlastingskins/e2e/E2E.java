/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.e2e;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;

/**
 * Single entry point for the in-jar E2E support, invoked from
 * {@code EverlastingSkins.init()} (one line: {@code E2E.install();}).
 *
 * <p>Shipped-gated by {@code -Deverlastingskins.e2e=true} (never active in
 * production); side-gated afterwards:
 * <ul>
 *   <li>CLIENT → {@link E2EDriver} (phase machine, channel receiver,
 *       auto-connect, renderer assertion, result file + {@code System.exit});</li>
 *   <li>CLIENT + {@code -Deverlastingskins.e2e.observer=true} →
 *       {@link E2EObserverDriver} (second-observer client: no commands,
 *       asserts the REAL handler's wire injection — lib-23 gap (d));</li>
 *   <li>SERVER → {@link E2ESentinelHook} (sentinel pre-seed so the login
 *       re-broadcast delivers it).</li>
 * </ul>
 */
public final class E2E {

    /** System property the e2e scripts set on both JVMs. */
    public static final String E2E_PROPERTY = "everlastingskins.e2e";

    private E2E() {}

    /** One-line gated entry from the mod's {@code @Mod.Init init}. */
    public static void install() {
        Side side = FMLCommonHandler.instance().getSide();
        // Observer role is a client-only, explicitly-gated alternative to the
        // actor driver (the observer never runs /skin).
        if (side == Side.CLIENT && Boolean.getBoolean(E2EObserverDriver.OBSERVER_PROPERTY)) {
            E2EObserverDriver.install();
            return;
        }
        if (!Boolean.getBoolean(E2E_PROPERTY)) {
            return;
        }
        if (side == Side.CLIENT) {
            E2EDriver.install();
        } else if (side == Side.SERVER) {
            E2ESentinelHook.install();
        }
    }
}
